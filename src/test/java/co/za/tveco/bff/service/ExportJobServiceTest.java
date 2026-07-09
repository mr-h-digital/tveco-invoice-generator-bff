package co.za.tveco.bff.service;

import co.za.tveco.bff.entity.ExportJob;
import co.za.tveco.bff.exception.ConflictException;
import co.za.tveco.bff.repository.ExportJobRepository;
import co.za.tveco.bff.repository.InvoiceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExportJobServiceTest {

    @Mock
    private ExportJobRepository exportJobRepository;

    @Mock
    private InvoiceRepository invoiceRepository;

    @InjectMocks
    private ExportJobService exportJobService;

    @BeforeEach
    void setUp() {
        exportJobService = new ExportJobService(exportJobRepository, invoiceRepository, new ObjectMapper());
    }

    @Test
    void patch_shouldRejectSkippingLifecycleStages() {
        ExportJob job = sampleJob("ENQUIRY");
        when(exportJobRepository.findById(job.getId())).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> exportJobService.patch(job.getId(), Map.of("status", "SHIPPING")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Status can only move forward one stage at a time");

        verify(exportJobRepository, never()).save(any());
    }

    @Test
    void patch_shouldRequireReasonWhenCancelling() {
        ExportJob job = sampleJob("SOURCING");
        when(exportJobRepository.findById(job.getId())).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> exportJobService.patch(job.getId(), Map.of("status", "CANCELLED")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cancellation reason is required");

        verify(exportJobRepository, never()).save(any());
    }

    @Test
    void patch_shouldBlockCoreFieldEditsAfterShippingStarted() {
        ExportJob job = sampleJob("SHIPPING");
        when(exportJobRepository.findById(job.getId())).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> exportJobService.patch(job.getId(), Map.of("destinationCountry", "Botswana")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("cannot be edited once shipping has started");

        verify(exportJobRepository, never()).save(any());
    }

    @Test
    void delete_shouldRejectNonEnquiryJobs() {
        ExportJob job = sampleJob("DOCUMENTATION");
        when(exportJobRepository.findById(job.getId())).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> exportJobService.delete(job.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Only ENQUIRY export jobs can be deleted");

        verify(exportJobRepository, never()).deleteById(any());
    }

    @Test
    void create_shouldUseCustomPaymentSplitWhenProvided() {
        when(exportJobRepository.findByJobNumberStartingWith(any())).thenReturn(java.util.List.of());
        when(exportJobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var request = new co.za.tveco.bff.dto.ExportJobCreateRequest(
                null,
                new co.za.tveco.bff.dto.ExportJobClientSnapshotDto("ACME", "Jane Doe", "jane@example.com", "+27110000000"),
                "Namibia",
                "Toyota Land Cruiser 300",
                "Website",
                BigDecimal.valueOf(100000),
                BigDecimal.valueOf(25),
                BigDecimal.valueOf(50),
                BigDecimal.valueOf(25),
                null,
                null,
                "Custom split"
        );

        var created = exportJobService.create(request);

        assertThat(created.paymentMilestones().get(0).get("label").asText()).contains("25%");
        assertThat(created.paymentMilestones().get(1).get("label").asText()).contains("50%");
        assertThat(created.paymentMilestones().get(2).get("label").asText()).contains("25%");
        assertThat(created.paymentMilestones().get(0).get("amount").asText()).isEqualTo("25000.00");
        assertThat(created.paymentMilestones().get(1).get("amount").asText()).isEqualTo("50000.00");
        assertThat(created.paymentMilestones().get(2).get("amount").asText()).isEqualTo("25000.00");
    }

    @Test
    void delete_shouldRejectJobsWithLinkedInvoices() {
        ExportJob job = sampleJob("ENQUIRY");
        when(exportJobRepository.findById(job.getId())).thenReturn(Optional.of(job));
        when(invoiceRepository.countByExportJobId(job.getId())).thenReturn(1L);

        assertThatThrownBy(() -> exportJobService.delete(job.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("linked invoices");

        verify(exportJobRepository, never()).deleteById(any());
    }

    private ExportJob sampleJob(String status) {
        return ExportJob.builder()
                .id(UUID.randomUUID())
                .jobNumber("TVECO-EXP-2026-001")
                .publicTrackingToken("TVC-ABC123")
                .clientSnapshot("{\"companyName\":\"ACME\"}")
                .destinationCountry("Zambia")
                .vehicleDescription("Toyota Land Cruiser 200")
                .sourceChannel("Website")
                .projectValue(BigDecimal.valueOf(52700))
                .status(status)
                .milestones("[]")
                .documents("[]")
                .paymentMilestones("[]")
                .vaultDocuments("[]")
                .estimatedDepartureDate(LocalDate.of(2026, 7, 12))
                .estimatedArrivalDate(LocalDate.of(2026, 8, 5))
                .notes("Test notes")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
