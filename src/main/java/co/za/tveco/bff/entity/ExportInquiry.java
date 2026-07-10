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
@Table(name = "export_inquiries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExportInquiry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "inquiry_number", nullable = false, unique = true)
    private String inquiryNumber;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "inquiry_type", nullable = false)
    private String inquiryType;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "source_channel", nullable = false)
    private String sourceChannel;

    @Column(name = "destination_country", nullable = false)
    private String destinationCountry;

    @Column(name = "vehicle_description", nullable = false)
    private String vehicleDescription;

    @Column(name = "project_value", nullable = false)
    private BigDecimal projectValue;

    @Column(name = "estimated_departure_date")
    private LocalDate estimatedDepartureDate;

    @Column(name = "estimated_arrival_date")
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
