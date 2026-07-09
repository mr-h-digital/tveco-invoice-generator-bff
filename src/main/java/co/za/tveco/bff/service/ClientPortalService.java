package co.za.tveco.bff.service;

import co.za.tveco.bff.dto.ClientDocumentUploadRequest;
import co.za.tveco.bff.dto.ClientExportJobRequest;
import co.za.tveco.bff.dto.ExportJobClientSnapshotDto;
import co.za.tveco.bff.dto.ExportJobCreateRequest;
import co.za.tveco.bff.dto.ExportJobDto;
import co.za.tveco.bff.entity.AppUser;
import co.za.tveco.bff.entity.Client;
import co.za.tveco.bff.entity.ExportJob;
import co.za.tveco.bff.exception.ForbiddenException;
import co.za.tveco.bff.exception.ResourceNotFoundException;
import co.za.tveco.bff.repository.AppUserRepository;
import co.za.tveco.bff.repository.ClientRepository;
import co.za.tveco.bff.repository.ExportJobRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClientPortalService {

    private static final Set<String> ALLOWED_CATEGORIES = Set.of("Compliance", "Shipping", "Customs", "Payment", "General");
    private static final int MAX_DATA_URL_LENGTH = 7_000_000;

    private final AppUserRepository appUserRepository;
    private final ClientRepository clientRepository;
    private final ExportJobRepository exportJobRepository;
    private final ExportJobService exportJobService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<ExportJobDto> getMyJobs(String email) {
        AppUser user = getClientUser(email);
        return exportJobRepository.findByClientIdOrderByCreatedAtDesc(user.getClientId()).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public ExportJobDto createRequest(String email, ClientExportJobRequest req) {
        AppUser user = getClientUser(email);
        Client client = clientRepository.findById(user.getClientId())
                .orElseThrow(() -> new ResourceNotFoundException("Client profile not found"));

        ExportJobCreateRequest createReq = new ExportJobCreateRequest(
                client.getId(),
                new ExportJobClientSnapshotDto(
                        client.getCompanyName(),
                        client.getContactName(),
                        client.getEmail(),
                        client.getPhone()
                ),
                req.destinationCountry().trim(),
                req.vehicleDescription().trim(),
                "Website",
                req.projectValue(),
                null,
                null,
                null,
                req.estimatedDepartureDate(),
                req.estimatedArrivalDate(),
                req.notes()
        );

        return exportJobService.create(createReq);
    }

    @Transactional
    public ExportJobDto uploadDocument(String email, UUID jobId, ClientDocumentUploadRequest req) {
        AppUser user = getClientUser(email);
        ExportJob job = exportJobRepository.findByIdAndClientId(jobId, user.getClientId())
                .orElseThrow(() -> new ResourceNotFoundException("Export job not found"));

        String category = req.category().trim();
        if (!ALLOWED_CATEGORIES.contains(category)) {
            throw new IllegalArgumentException("Invalid document category");
        }

        String dataUrl = req.dataUrl() == null ? "" : req.dataUrl().trim();
        String fileUrl = req.fileUrl() == null ? "" : req.fileUrl().trim();
        if (dataUrl.isBlank() && fileUrl.isBlank()) {
            throw new IllegalArgumentException("Either dataUrl or fileUrl is required");
        }
        if (!dataUrl.isBlank() && dataUrl.length() > MAX_DATA_URL_LENGTH) {
            throw new IllegalArgumentException("Uploaded file is too large");
        }

        ArrayNode vaultDocuments = asArray(readJson(job.getVaultDocuments(), objectMapper.createArrayNode()));
        ObjectNode document = objectMapper.createObjectNode();
        document.put("id", UUID.randomUUID().toString());
        document.put("name", req.name().trim());
        document.put("mimeType", req.mimeType().trim());
        document.put("sizeBytes", req.sizeBytes());
        document.put("category", category);
        document.put("uploadedAt", Instant.now().toString());
        document.put("visibleToClient", true);
        if (!dataUrl.isBlank()) {
            document.put("storageProvider", "LOCAL");
            document.put("dataUrl", dataUrl);
        } else {
            document.put("storageProvider", "REMOTE");
            document.put("fileUrl", fileUrl);
        }
        vaultDocuments.add(document);

        job.setVaultDocuments(writeJson(vaultDocuments));
        return toDto(exportJobRepository.save(job));
    }

    private AppUser getClientUser(String email) {
        AppUser user = appUserRepository.findByEmailIgnoreCase(email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ForbiddenException("Client account not found"));

        if (!"client".equalsIgnoreCase(user.getRole()) || user.getClientId() == null) {
            throw new ForbiddenException("Client portal is only available for client accounts");
        }

        return user;
    }

    private ArrayNode asArray(JsonNode node) {
        return node != null && node.isArray() ? (ArrayNode) node : objectMapper.createArrayNode();
    }

    private String writeJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to serialize JSON payload", e);
        }
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

    private ExportJobDto toDto(ExportJob e) {
        return new ExportJobDto(
                e.getId(),
                e.getJobNumber(),
                e.getPublicTrackingToken(),
                e.getClientId(),
                readJson(e.getClientSnapshot(), objectMapper.createObjectNode()),
                e.getDestinationCountry(),
                e.getVehicleDescription(),
                e.getSourceChannel(),
                e.getProjectValue(),
                e.getStatus(),
                readJson(e.getMilestones(), objectMapper.createArrayNode()),
                readJson(e.getDocuments(), objectMapper.createArrayNode()),
                readJson(e.getPaymentMilestones(), objectMapper.createArrayNode()),
                readJson(e.getVaultDocuments(), objectMapper.createArrayNode()),
                e.getEstimatedDepartureDate(),
                e.getEstimatedArrivalDate(),
                e.getNotes(),
                e.getCancellationReason(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
