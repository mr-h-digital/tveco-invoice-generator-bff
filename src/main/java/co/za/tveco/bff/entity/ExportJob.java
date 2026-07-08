package co.za.tveco.bff.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "export_jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExportJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "job_number", nullable = false, unique = true)
    private String jobNumber;

    @Column(name = "public_tracking_token", nullable = false, unique = true)
    private String publicTrackingToken;

    @Column(name = "client_id")
    private UUID clientId;

    @Column(name = "client_snapshot", nullable = false, columnDefinition = "TEXT")
    private String clientSnapshot;

    @Column(name = "destination_country", nullable = false)
    private String destinationCountry;

    @Column(name = "vehicle_description", nullable = false)
    private String vehicleDescription;

    @Column(name = "source_channel", nullable = false)
    private String sourceChannel;

    @Column(name = "project_value", nullable = false)
    private BigDecimal projectValue;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "milestones", nullable = false, columnDefinition = "TEXT")
    private String milestones;

    @Column(name = "documents", nullable = false, columnDefinition = "TEXT")
    private String documents;

    @Column(name = "payment_milestones", nullable = false, columnDefinition = "TEXT")
    private String paymentMilestones;

    @Column(name = "vault_documents", nullable = false, columnDefinition = "TEXT")
    private String vaultDocuments;

    @Column(name = "estimated_departure_date", nullable = false)
    private LocalDate estimatedDepartureDate;

    @Column(name = "estimated_arrival_date", nullable = false)
    private LocalDate estimatedArrivalDate;

    @Column(name = "notes", nullable = false, columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}