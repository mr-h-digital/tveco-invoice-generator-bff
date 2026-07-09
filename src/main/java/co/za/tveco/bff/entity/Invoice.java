package co.za.tveco.bff.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "invoices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "invoice_number", nullable = false, unique = true)
    private String invoiceNumber;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "client_id")
    private UUID clientId;

    @Column(name = "export_job_id")
    private UUID exportJobId;

    @Column(name = "payment_milestone_key")
    private String paymentMilestoneKey;

    // Client snapshot
    @Column(name = "snap_company_name")
    private String snapCompanyName;

    @Column(name = "snap_contact_name")
    private String snapContactName;

    @Column(name = "snap_email")
    private String snapEmail;

    @Column(name = "snap_phone")
    private String snapPhone;

    @Column(name = "snap_address", columnDefinition = "TEXT")
    private String snapAddress;

    // Totals
    @Column(name = "subtotal", nullable = false)
    private BigDecimal subtotal;

    @Column(name = "discount_type")
    private String discountType;

    @Column(name = "discount_value", nullable = false)
    private BigDecimal discountValue;

    @Column(name = "discount_amount", nullable = false)
    private BigDecimal discountAmount;

    @Column(name = "vat_enabled", nullable = false)
    private boolean vatEnabled;

    @Column(name = "vat_rate", nullable = false)
    private BigDecimal vatRate;

    @Column(name = "vat_amount", nullable = false)
    private BigDecimal vatAmount;

    @Column(name = "total", nullable = false)
    private BigDecimal total;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    // Payment details
    @Column(name = "pay_bank")
    private String payBank;

    @Column(name = "pay_account_name")
    private String payAccountName;

    @Column(name = "pay_account_number")
    private String payAccountNumber;

    @Column(name = "pay_account_type")
    private String payAccountType;

    @Column(name = "pay_branch_code")
    private String payBranchCode;

    @Column(name = "pay_reference")
    private String payReference;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sort_order ASC, id ASC")
    @Builder.Default
    private List<LineItem> lineItems = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
