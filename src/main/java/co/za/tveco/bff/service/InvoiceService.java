package co.za.tveco.bff.service;

import co.za.tveco.bff.dto.*;
import co.za.tveco.bff.entity.ExportJob;
import co.za.tveco.bff.entity.Invoice;
import co.za.tveco.bff.entity.LineItem;
import co.za.tveco.bff.exception.ConflictException;
import co.za.tveco.bff.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import co.za.tveco.bff.repository.ExportJobRepository;
import co.za.tveco.bff.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final ExportJobRepository exportJobRepository;
    private final ObjectMapper objectMapper;

    // ── Read ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<InvoiceDto> getAll(Pageable pageable) {
        return invoiceRepository.findAll(pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public InvoiceDto getById(UUID id) {
        return toDto(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public String getNextInvoiceNumber() {
        String year = String.valueOf(LocalDate.now().getYear());
        int max = invoiceRepository.findMaxSequenceForYear(year);
        return String.format("TVECO-%s-%03d", year, max + 1);
    }

    // ── Write ────────────────────────────────────────────────────────────────

    @Transactional
    public InvoiceDto create(InvoiceRequest req) {
        if (invoiceRepository.existsByInvoiceNumber(req.invoiceNumber())) {
            throw new ConflictException("Invoice number '" + req.invoiceNumber() + "' already exists");
        }
        Invoice invoice = buildInvoice(new Invoice(), req);
        validateExportJobInvoiceBudget(req.exportJobId(), invoice.getTotal(), null);
        return toDto(invoiceRepository.save(invoice));
    }

    @Transactional
    public InvoiceDto update(UUID id, InvoiceRequest req) {
        Invoice invoice = findOrThrow(id);
        if (invoiceRepository.existsByInvoiceNumberAndIdNot(req.invoiceNumber(), id)) {
            throw new ConflictException("Invoice number '" + req.invoiceNumber() + "' is already used by another invoice");
        }
        invoice.getLineItems().clear();
        buildInvoice(invoice, req);
        validateExportJobInvoiceBudget(req.exportJobId(), invoice.getTotal(), id);
        return toDto(invoiceRepository.save(invoice));
    }

    @Transactional
    public InvoiceDto updateStatus(UUID id, String status) {
        Invoice invoice = findOrThrow(id);
        invoice.setStatus(status);
        return toDto(invoiceRepository.save(invoice));
    }

    @Transactional
    public void delete(UUID id) {
        if (!invoiceRepository.existsById(id)) {
            throw new ResourceNotFoundException("Invoice not found: " + id);
        }
        invoiceRepository.deleteById(id);
    }

    @Transactional
    public InvoiceDto duplicate(UUID id) {
        Invoice original = findOrThrow(id);
        String newNumber = getNextInvoiceNumber();

        Invoice copy = Invoice.builder()
                .invoiceNumber(newNumber)
                .status("DRAFT")
                .issueDate(LocalDate.now())
                .dueDate(original.getDueDate())
                .clientId(original.getClientId())
                .exportJobId(original.getExportJobId())
                .snapCompanyName(original.getSnapCompanyName())
                .snapContactName(original.getSnapContactName())
                .snapEmail(original.getSnapEmail())
                .snapPhone(original.getSnapPhone())
                .snapAddress(original.getSnapAddress())
                .subtotal(original.getSubtotal())
                .discountType(original.getDiscountType())
                .discountValue(original.getDiscountValue())
                .discountAmount(original.getDiscountAmount())
                .vatEnabled(original.isVatEnabled())
                .vatRate(original.getVatRate())
                .vatAmount(original.getVatAmount())
                .total(original.getTotal())
                .notes(original.getNotes())
                .payBank(original.getPayBank())
                .payAccountName(original.getPayAccountName())
                .payAccountNumber(original.getPayAccountNumber())
                .payAccountType(original.getPayAccountType())
                .payBranchCode(original.getPayBranchCode())
                .payReference(newNumber)
                .build();

        original.getLineItems().forEach(li -> {
            LineItem copiedItem = LineItem.builder()
                    .invoice(copy)
                    .name(li.getName())
                    .description(li.getDescription())
                    .quantity(li.getQuantity())
                    .unitPrice(li.getUnitPrice())
                    .amount(li.getAmount())
                    .sortOrder(li.getSortOrder())
                    .build();
            copy.getLineItems().add(copiedItem);
        });

        return toDto(invoiceRepository.save(copy));
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private Invoice buildInvoice(Invoice invoice, InvoiceRequest req) {
        InvoiceCalculator.Totals totals = InvoiceCalculator.calculate(
                req.lineItems(),
                req.discountType(),
                req.discountValue() == null ? BigDecimal.ZERO : req.discountValue(),
                req.vatEnabled(),
                req.vatRate() == null ? BigDecimal.valueOf(0.15) : req.vatRate()
        );

        invoice.setInvoiceNumber(req.invoiceNumber());
        invoice.setStatus(req.status());
        invoice.setIssueDate(LocalDate.parse(req.issueDate()));
        invoice.setDueDate(LocalDate.parse(req.dueDate()));
        invoice.setClientId(req.clientId());
        invoice.setExportJobId(req.exportJobId());

        ClientSnapshotDto snap = req.clientSnapshot();
        invoice.setSnapCompanyName(nvl(snap.companyName()));
        invoice.setSnapContactName(nvl(snap.contactName()));
        invoice.setSnapEmail(nvl(snap.email()));
        invoice.setSnapPhone(nvl(snap.phone()));
        invoice.setSnapAddress(nvl(snap.address()));

        invoice.setSubtotal(totals.subtotal());
        invoice.setDiscountType(req.discountType());
        invoice.setDiscountValue(req.discountValue() == null ? BigDecimal.ZERO : req.discountValue());
        invoice.setDiscountAmount(totals.discountAmount());
        invoice.setVatEnabled(req.vatEnabled());
        invoice.setVatRate(req.vatRate() == null ? BigDecimal.valueOf(0.15) : req.vatRate());
        invoice.setVatAmount(totals.vatAmount());
        invoice.setTotal(totals.total());
        invoice.setNotes(nvl(req.notes()));

        PaymentDetailsDto pay = req.paymentDetails();
        if (pay != null) {
            invoice.setPayBank(nvl(pay.bank()));
            invoice.setPayAccountName(nvl(pay.accountName()));
            invoice.setPayAccountNumber(nvl(pay.accountNumber()));
            invoice.setPayAccountType(nvl(pay.accountType()));
            invoice.setPayBranchCode(nvl(pay.branchCode()));
            invoice.setPayReference(nvl(pay.reference()));
        }

        for (int i = 0; i < req.lineItems().size(); i++) {
            LineItemRequest li = req.lineItems().get(i);
            LineItem item = LineItem.builder()
                    .invoice(invoice)
                    .name(li.name())
                    .description(nvl(li.description()))
                    .quantity(li.quantity())
                    .unitPrice(li.unitPrice())
                    .amount(InvoiceCalculator.lineAmount(li.unitPrice(), li.quantity()))
                    .sortOrder(li.sortOrder() == 0 ? i : li.sortOrder())
                    .build();
            invoice.getLineItems().add(item);
        }

        return invoice;
    }

    private Invoice findOrThrow(UUID id) {
        return invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + id));
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }

    private void validateExportJobInvoiceBudget(UUID exportJobId, BigDecimal invoiceTotal, UUID invoiceIdToExclude) {
        if (exportJobId == null) {
            return;
        }

        ExportJob exportJob = exportJobRepository.findById(exportJobId)
                .orElseThrow(() -> new ResourceNotFoundException("Export job not found: " + exportJobId));

        BigDecimal allowedTotal = resolveAllowedTotal(exportJob);
        BigDecimal existingTotal = invoiceIdToExclude == null
                ? invoiceRepository.sumTotalByExportJobId(exportJobId)
                : invoiceRepository.sumTotalByExportJobIdAndIdNot(exportJobId, invoiceIdToExclude);

        BigDecimal nextTotal = existingTotal.add(invoiceTotal);
        if (nextTotal.compareTo(allowedTotal) > 0) {
            throw new ConflictException(
                    "Linked invoice totals exceed export job payment milestones total (%s > %s)"
                            .formatted(nextTotal.stripTrailingZeros().toPlainString(), allowedTotal.stripTrailingZeros().toPlainString())
            );
        }
    }

    private BigDecimal resolveAllowedTotal(ExportJob exportJob) {
        try {
            JsonNode root = objectMapper.readTree(exportJob.getPaymentMilestones());
            if (!root.isArray()) {
                return exportJob.getProjectValue();
            }

            BigDecimal sum = BigDecimal.ZERO;
            for (JsonNode node : root) {
                JsonNode amountNode = node.get("amount");
                if (amountNode == null || amountNode.isNull()) {
                    continue;
                }
                sum = sum.add(amountNode.decimalValue());
            }

            if (sum.compareTo(BigDecimal.ZERO) <= 0) {
                return exportJob.getProjectValue();
            }
            return sum;
        } catch (Exception ignored) {
            return exportJob.getProjectValue();
        }
    }

    // ── Mapper ───────────────────────────────────────────────────────────────

    InvoiceDto toDto(Invoice inv) {
        List<LineItemDto> items = inv.getLineItems().stream()
                .map(li -> new LineItemDto(
                        li.getId(),
                        li.getName(),
                        li.getDescription(),
                        li.getQuantity(),
                        li.getUnitPrice(),
                        li.getAmount(),
                        li.getSortOrder()
                ))
                .toList();

        return new InvoiceDto(
                inv.getId(),
                inv.getInvoiceNumber(),
                inv.getStatus(),
                inv.getIssueDate(),
                inv.getDueDate(),
                inv.getClientId(),
                inv.getExportJobId(),
                new ClientSnapshotDto(
                        inv.getSnapCompanyName(),
                        inv.getSnapContactName(),
                        inv.getSnapEmail(),
                        inv.getSnapPhone(),
                        inv.getSnapAddress()
                ),
                items,
                inv.getSubtotal(),
                inv.getDiscountType(),
                inv.getDiscountValue(),
                inv.getDiscountAmount(),
                inv.isVatEnabled(),
                inv.getVatRate(),
                inv.getVatAmount(),
                inv.getTotal(),
                inv.getNotes(),
                new PaymentDetailsDto(
                        inv.getPayBank(),
                        inv.getPayAccountName(),
                        inv.getPayAccountNumber(),
                        inv.getPayAccountType(),
                        inv.getPayBranchCode(),
                        inv.getPayReference()
                ),
                inv.getCreatedAt(),
                inv.getUpdatedAt()
        );
    }
}
