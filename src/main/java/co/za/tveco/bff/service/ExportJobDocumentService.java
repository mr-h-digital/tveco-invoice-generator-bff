package co.za.tveco.bff.service;

import co.za.tveco.bff.dto.DocumentDownloadUrlResponse;
import co.za.tveco.bff.dto.DocumentUploadCompleteRequest;
import co.za.tveco.bff.dto.DocumentUploadInitRequest;
import co.za.tveco.bff.dto.DocumentUploadInitResponse;
import co.za.tveco.bff.entity.AppUser;
import co.za.tveco.bff.entity.ExportJob;
import co.za.tveco.bff.entity.ExportJobDocument;
import co.za.tveco.bff.exception.ForbiddenException;
import co.za.tveco.bff.exception.ResourceNotFoundException;
import co.za.tveco.bff.repository.AppUserRepository;
import co.za.tveco.bff.repository.ExportJobDocumentRepository;
import co.za.tveco.bff.repository.ExportJobRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExportJobDocumentService {

    private static final Set<String> ALLOWED_CATEGORIES = Set.of("Compliance", "Shipping", "Customs", "Payment", "General");
    private static final long MAX_UPLOAD_SIZE_BYTES = 25L * 1024 * 1024;
    private static final List<String> ALLOWED_CLIENT_MIME_PREFIXES = List.of(
            "application/pdf",
            "image/",
            "application/msword",
            "application/vnd.openxmlformats-officedocument",
            "text/"
    );

    private final ExportJobRepository exportJobRepository;
    private final ExportJobDocumentRepository exportJobDocumentRepository;
    private final AppUserRepository appUserRepository;
    private final R2DocumentStorageService r2DocumentStorageService;
    private final ObjectMapper objectMapper;

    @Transactional
    public DocumentUploadInitResponse initAdminUpload(String email, UUID jobId, DocumentUploadInitRequest req) {
        AppUser user = requireAdminUser(email);
        ExportJob job = findJob(jobId);
        validateRequest(req, false);

        ExportJobDocument document = createPendingDocument(job.getId(), user.getId(), req, Boolean.TRUE.equals(req.visibleToClient()));
        ExportJobDocument saved = exportJobDocumentRepository.save(document);
        R2PresignedUpload upload = r2DocumentStorageService.createUpload(saved.getObjectKey(), saved.getMimeType());

        return new DocumentUploadInitResponse(saved.getId(), saved.getObjectKey(), upload.url(), upload.expiresAt(), upload.requiredHeaders());
    }

    @Transactional
    public DocumentUploadInitResponse initClientUpload(String email, UUID jobId, DocumentUploadInitRequest req) {
        AppUser user = requireClientUser(email);
        ExportJob job = exportJobRepository.findByIdAndClientId(jobId, user.getClientId())
                .orElseThrow(() -> new ResourceNotFoundException("Export job not found"));
        validateRequest(req, true);

        ExportJobDocument document = createPendingDocument(job.getId(), user.getId(), req, true);
        ExportJobDocument saved = exportJobDocumentRepository.save(document);
        R2PresignedUpload upload = r2DocumentStorageService.createUpload(saved.getObjectKey(), saved.getMimeType());

        return new DocumentUploadInitResponse(saved.getId(), saved.getObjectKey(), upload.url(), upload.expiresAt(), upload.requiredHeaders());
    }

    @Transactional
    public void completeAdminUpload(String email, UUID jobId, UUID documentId, DocumentUploadCompleteRequest req) {
        requireAdminUser(email);
        completeUpload(jobId, documentId, req, false, null);
    }

    @Transactional
    public void completeClientUpload(String email, UUID jobId, UUID documentId, DocumentUploadCompleteRequest req) {
        AppUser user = requireClientUser(email);
        completeUpload(jobId, documentId, req, true, user.getClientId());
    }

    @Transactional(readOnly = true)
    public DocumentDownloadUrlResponse createAdminDownloadUrl(String email, UUID jobId, UUID documentId) {
        requireAdminUser(email);
        ExportJobDocument document = findDocument(jobId, documentId);
        R2PresignedDownload download = r2DocumentStorageService.createDownload(document.getObjectKey(), document.getOriginalName());
        return new DocumentDownloadUrlResponse(download.url(), download.expiresAt());
    }

    @Transactional(readOnly = true)
    public DocumentDownloadUrlResponse createClientDownloadUrl(String email, UUID jobId, UUID documentId) {
        AppUser user = requireClientUser(email);
        exportJobRepository.findByIdAndClientId(jobId, user.getClientId())
                .orElseThrow(() -> new ResourceNotFoundException("Export job not found"));

        ExportJobDocument document = findDocument(jobId, documentId);
        if (!document.isVisibleToClient() || !"ACTIVE".equals(document.getStatus())) {
            throw new ForbiddenException("Document is not available to this client");
        }

        R2PresignedDownload download = r2DocumentStorageService.createDownload(document.getObjectKey(), document.getOriginalName());
        return new DocumentDownloadUrlResponse(download.url(), download.expiresAt());
    }

    @Transactional(readOnly = true)
    public DocumentDownloadUrlResponse createTrackingDownloadUrl(String token, UUID documentId) {
        ExportJob job = exportJobRepository.findByPublicTrackingTokenIgnoreCase(token.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Export job not found for tracking token: " + token));

        ExportJobDocument document = findDocument(job.getId(), documentId);
        if (!document.isVisibleToClient() || !"ACTIVE".equals(document.getStatus())) {
            throw new ResourceNotFoundException("Document not found");
        }

        R2PresignedDownload download = r2DocumentStorageService.createDownload(document.getObjectKey(), document.getOriginalName());
        return new DocumentDownloadUrlResponse(download.url(), download.expiresAt());
    }

    private void completeUpload(UUID jobId, UUID documentId, DocumentUploadCompleteRequest req, boolean clientScoped, UUID clientId) {
        ExportJob job = clientScoped
                ? exportJobRepository.findByIdAndClientId(jobId, clientId).orElseThrow(() -> new ResourceNotFoundException("Export job not found"))
                : findJob(jobId);

        ExportJobDocument document = findDocument(job.getId(), documentId);
        if (!"PENDING_UPLOAD".equals(document.getStatus())) {
            throw new IllegalArgumentException("Document upload has already been finalized");
        }

        HeadObjectResponse head = r2DocumentStorageService.headObject(document.getObjectKey());
        if (head.contentLength() == null || head.contentLength() <= 0) {
            throw new IllegalArgumentException("Uploaded object is empty or unavailable");
        }
        if (head.contentLength() != document.getSizeBytes()) {
            throw new IllegalArgumentException("Uploaded file size does not match the initialized upload");
        }

        document.setStatus("ACTIVE");
        document.setCompletedAt(Instant.now());
        document.setEtag(normalizeEtag(head.eTag()));
        document.setChecksumSha256(req == null || req.checksumSha256() == null ? null : req.checksumSha256().trim());
        exportJobDocumentRepository.save(document);
        mirrorDocumentIntoLegacyVault(job, document);
        exportJobRepository.save(job);
    }

    private ExportJobDocument createPendingDocument(UUID jobId, UUID userId, DocumentUploadInitRequest req, boolean visibleToClient) {
        String sanitizedName = req.name().trim();
        String objectKey = "export-jobs/" + jobId + "/" + UUID.randomUUID() + "/" + sanitizeObjectKeySegment(sanitizedName);

        return ExportJobDocument.builder()
                .exportJobId(jobId)
                .uploadedByUserId(userId)
                .originalName(sanitizedName)
                .mimeType(req.mimeType().trim())
                .sizeBytes(req.sizeBytes())
                .category(req.category().trim())
                .storageProvider("R2")
                .bucketName(r2DocumentStorageService.bucket())
                .objectKey(objectKey)
                .visibleToClient(visibleToClient)
                .status("PENDING_UPLOAD")
                .build();
    }

    private void validateRequest(DocumentUploadInitRequest req, boolean clientUpload) {
        if (!r2DocumentStorageService.isConfigured()) {
            throw new IllegalArgumentException("Document storage is not configured");
        }

        String category = req.category() == null ? "" : req.category().trim();
        if (!ALLOWED_CATEGORIES.contains(category)) {
            throw new IllegalArgumentException("Invalid document category");
        }

        String fileName = req.name() == null ? "" : req.name().trim();
        if (fileName.isBlank()) {
            throw new IllegalArgumentException("Document name is required");
        }

        String mimeType = req.mimeType() == null ? "" : req.mimeType().trim().toLowerCase(Locale.ROOT);
        if (mimeType.isBlank()) {
            throw new IllegalArgumentException("Document mimeType is required");
        }

        if (req.sizeBytes() == null || req.sizeBytes() <= 0) {
            throw new IllegalArgumentException("Document sizeBytes must be greater than 0");
        }
        if (req.sizeBytes() > MAX_UPLOAD_SIZE_BYTES) {
            throw new IllegalArgumentException("Uploaded file exceeds the 25 MB limit");
        }

        if (clientUpload && ALLOWED_CLIENT_MIME_PREFIXES.stream().noneMatch(mimeType::startsWith)) {
            throw new IllegalArgumentException("This file type is not allowed in the client portal");
        }
    }

    private ExportJob findJob(UUID jobId) {
        return exportJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Export job not found: " + jobId));
    }

    private ExportJobDocument findDocument(UUID jobId, UUID documentId) {
        return exportJobDocumentRepository.findByIdAndExportJobId(documentId, jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Export job document not found"));
    }

    private AppUser requireAdminUser(String email) {
        AppUser user = getUserByEmail(email);
        if (!"admin".equalsIgnoreCase(user.getRole())) {
            throw new ForbiddenException("This action is only available to admin users");
        }
        return user;
    }

    private AppUser requireClientUser(String email) {
        AppUser user = getUserByEmail(email);
        if (!"client".equalsIgnoreCase(user.getRole()) || user.getClientId() == null) {
            throw new ForbiddenException("This action is only available to client users");
        }
        return user;
    }

    private AppUser getUserByEmail(String email) {
        return appUserRepository.findByEmailIgnoreCase(email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ForbiddenException("User account not found"));
    }

    private String sanitizeObjectKeySegment(String value) {
        String collapsed = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-");
        String trimmed = collapsed.replaceAll("^-+|-+$", "");
        return trimmed.isBlank() ? "document" : trimmed;
    }

    private String normalizeEtag(String etag) {
        if (etag == null) {
            return null;
        }
        return etag.replace("\"", "").trim();
    }

    private void mirrorDocumentIntoLegacyVault(ExportJob job, ExportJobDocument document) {
        ArrayNode vaultDocuments = asArray(readJson(job.getVaultDocuments(), objectMapper.createArrayNode()));
        boolean alreadyMirrored = false;

        for (JsonNode node : vaultDocuments) {
            if (node.isObject() && document.getId().toString().equals(node.path("id").asText())) {
                ObjectNode existing = (ObjectNode) node;
                existing.put("name", document.getOriginalName());
                existing.put("mimeType", document.getMimeType());
                existing.put("sizeBytes", document.getSizeBytes());
                existing.put("category", document.getCategory());
                existing.put("uploadedAt", document.getCompletedAt() == null ? document.getCreatedAt().toString() : document.getCompletedAt().toString());
                existing.put("visibleToClient", document.isVisibleToClient());
                existing.put("storageProvider", "REMOTE");
                existing.put("objectKey", document.getObjectKey());
                alreadyMirrored = true;
                break;
            }
        }

        if (!alreadyMirrored) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("id", document.getId().toString());
            node.put("name", document.getOriginalName());
            node.put("mimeType", document.getMimeType());
            node.put("sizeBytes", document.getSizeBytes());
            node.put("category", document.getCategory());
            node.put("uploadedAt", document.getCompletedAt() == null ? document.getCreatedAt().toString() : document.getCompletedAt().toString());
            node.put("visibleToClient", document.isVisibleToClient());
            node.put("storageProvider", "REMOTE");
            node.put("objectKey", document.getObjectKey());
            vaultDocuments.add(node);
        }

        job.setVaultDocuments(writeJson(vaultDocuments));
    }

    private ArrayNode asArray(JsonNode node) {
        return node != null && node.isArray() ? (ArrayNode) node : objectMapper.createArrayNode();
    }

    private JsonNode readJson(String raw, JsonNode fallback) {
        try {
            if (raw == null || raw.isBlank()) {
                return fallback;
            }
            return objectMapper.readTree(raw);
        } catch (JsonProcessingException e) {
            return fallback;
        }
    }

    private String writeJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to serialize JSON payload", e);
        }
    }
}