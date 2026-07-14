package co.za.tveco.bff.service;

import co.za.tveco.bff.dto.DocumentDownloadUrlResponse;
import co.za.tveco.bff.dto.DocumentUploadInitRequest;
import co.za.tveco.bff.dto.DocumentUploadInitResponse;
import co.za.tveco.bff.entity.AppUser;
import co.za.tveco.bff.entity.ExportJob;
import co.za.tveco.bff.entity.ExportJobDocument;
import co.za.tveco.bff.exception.ResourceNotFoundException;
import co.za.tveco.bff.repository.AppUserRepository;
import co.za.tveco.bff.repository.ExportJobDocumentRepository;
import co.za.tveco.bff.repository.ExportJobRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExportJobDocumentServiceTest {

    @Mock
    private ExportJobRepository exportJobRepository;

    @Mock
    private ExportJobDocumentRepository exportJobDocumentRepository;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private R2DocumentStorageService r2DocumentStorageService;

    @InjectMocks
    private ExportJobDocumentService exportJobDocumentService;

    @BeforeEach
    void setUp() {
        exportJobDocumentService = new ExportJobDocumentService(
                exportJobRepository,
                exportJobDocumentRepository,
                appUserRepository,
                r2DocumentStorageService,
                new ObjectMapper()
        );
    }

    @Test
    void initClientUpload_shouldCreatePendingDocumentAndReturnUploadUrl() {
        AppUser clientUser = AppUser.builder()
                .id(UUID.randomUUID())
                .email("client@example.com")
                .role("client")
                .clientId(UUID.randomUUID())
                .active(true)
                .build();
        ExportJob job = sampleJob(clientUser.getClientId());
        Instant expiresAt = Instant.parse("2026-07-14T12:15:00Z");

        when(appUserRepository.findByEmailIgnoreCase("client@example.com")).thenReturn(Optional.of(clientUser));
        when(exportJobRepository.findByIdAndClientId(job.getId(), clientUser.getClientId())).thenReturn(Optional.of(job));
        when(r2DocumentStorageService.isConfigured()).thenReturn(true);
        when(r2DocumentStorageService.bucket()).thenReturn("tveco-docs");
        when(exportJobDocumentRepository.save(any())).thenAnswer(invocation -> {
            ExportJobDocument document = invocation.getArgument(0);
            document.setId(UUID.randomUUID());
            return document;
        });
        when(r2DocumentStorageService.createUpload(anyString(), anyString()))
                .thenReturn(new R2PresignedUpload(
                        "https://example-upload-url",
                        expiresAt,
                        Map.of("Content-Type", "application/pdf")
                ));

        DocumentUploadInitResponse response = exportJobDocumentService.initClientUpload(
                "client@example.com",
                job.getId(),
                new DocumentUploadInitRequest("Bill of Lading.pdf", "application/pdf", 1024L, "Shipping", true)
        );

        ArgumentCaptor<ExportJobDocument> captor = ArgumentCaptor.forClass(ExportJobDocument.class);
        verify(exportJobDocumentRepository).save(captor.capture());

        ExportJobDocument saved = captor.getValue();
        assertThat(saved.getExportJobId()).isEqualTo(job.getId());
        assertThat(saved.getUploadedByUserId()).isEqualTo(clientUser.getId());
        assertThat(saved.getStorageProvider()).isEqualTo("R2");
        assertThat(saved.getBucketName()).isEqualTo("tveco-docs");
        assertThat(saved.isVisibleToClient()).isTrue();
        assertThat(saved.getStatus()).isEqualTo("PENDING_UPLOAD");
        assertThat(saved.getObjectKey()).contains(job.getId().toString());

        assertThat(response.uploadUrl()).isEqualTo("https://example-upload-url");
        assertThat(response.objectKey()).isEqualTo(saved.getObjectKey());
        assertThat(response.expiresAt()).isEqualTo(expiresAt);
        assertThat(response.requiredHeaders()).containsEntry("Content-Type", "application/pdf");
    }

    @Test
    void createTrackingDownloadUrl_shouldRejectHiddenDocuments() {
        ExportJob job = sampleJob(null);
        ExportJobDocument hiddenDocument = ExportJobDocument.builder()
                .id(UUID.randomUUID())
                .exportJobId(job.getId())
                .uploadedByUserId(UUID.randomUUID())
                .originalName("secret.pdf")
                .mimeType("application/pdf")
                .sizeBytes(2048)
                .category("Compliance")
                .storageProvider("R2")
                .bucketName("tveco-docs")
                .objectKey("export-jobs/object-key")
                .visibleToClient(false)
                .status("ACTIVE")
                .build();

        when(exportJobRepository.findByPublicTrackingTokenIgnoreCase("TVC-ABC123")).thenReturn(Optional.of(job));
        when(exportJobDocumentRepository.findByIdAndExportJobId(hiddenDocument.getId(), job.getId())).thenReturn(Optional.of(hiddenDocument));

        assertThatThrownBy(() -> exportJobDocumentService.createTrackingDownloadUrl("TVC-ABC123", hiddenDocument.getId()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Document not found");
    }

    @Test
    void createTrackingDownloadUrl_shouldReturnSignedUrlForVisibleActiveDocument() {
        ExportJob job = sampleJob(null);
        ExportJobDocument visibleDocument = ExportJobDocument.builder()
                .id(UUID.randomUUID())
                .exportJobId(job.getId())
                .uploadedByUserId(UUID.randomUUID())
                .originalName("invoice.pdf")
                .mimeType("application/pdf")
                .sizeBytes(2048)
                .category("Payment")
                .storageProvider("R2")
                .bucketName("tveco-docs")
                .objectKey("export-jobs/object-key")
                .visibleToClient(true)
                .status("ACTIVE")
                .build();
        Instant expiresAt = Instant.parse("2026-07-14T13:00:00Z");

        when(exportJobRepository.findByPublicTrackingTokenIgnoreCase("TVC-ABC123")).thenReturn(Optional.of(job));
        when(exportJobDocumentRepository.findByIdAndExportJobId(visibleDocument.getId(), job.getId())).thenReturn(Optional.of(visibleDocument));
        when(r2DocumentStorageService.createDownload(visibleDocument.getObjectKey(), visibleDocument.getOriginalName()))
                .thenReturn(new R2PresignedDownload("https://example-download-url", expiresAt));

        DocumentDownloadUrlResponse response = exportJobDocumentService.createTrackingDownloadUrl("TVC-ABC123", visibleDocument.getId());

        assertThat(response.url()).isEqualTo("https://example-download-url");
        assertThat(response.expiresAt()).isEqualTo(expiresAt);
    }

    private ExportJob sampleJob(UUID clientId) {
        return ExportJob.builder()
                .id(UUID.randomUUID())
                .jobNumber("TVECO-EXP-2026-001")
                .publicTrackingToken("TVC-ABC123")
                .clientId(clientId)
                .clientSnapshot("{\"companyName\":\"ACME\"}")
                .destinationCountry("Namibia")
                .vehicleDescription("Toyota Hilux")
                .sourceChannel("Website")
                .projectValue(BigDecimal.valueOf(250000))
                .status("ENQUIRY")
                .milestones("[]")
                .documents("[]")
                .paymentMilestones("[]")
                .vaultDocuments("[]")
                .estimatedDepartureDate(LocalDate.of(2026, 7, 14))
                .estimatedArrivalDate(LocalDate.of(2026, 8, 14))
                .notes("Test job")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}