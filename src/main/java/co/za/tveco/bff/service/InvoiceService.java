package co.za.tveco.bff.service;

import co.za.tveco.bff.dto.*;
import co.za.tveco.bff.entity.ExportJob;
import co.za.tveco.bff.entity.Invoice;
import co.za.tveco.bff.entity.LineItem;
import co.za.tveco.bff.exception.ConflictException;
import co.za.tveco.bff.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import co.za.tveco.bff.repository.ExportJobRepository;
import co.za.tveco.bff.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
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
        validateExportJobInvoiceBudget(req.exportJobId(), req.paymentMilestoneKey(), invoice.getSubtotal(), null, null, null, null);
        return toDto(invoiceRepository.save(invoice));
    }

    @Transactional
    public InvoiceDto update(UUID id, InvoiceRequest req) {
        Invoice invoice = findOrThrow(id);
        if (invoiceRepository.existsByInvoiceNumberAndIdNot(req.invoiceNumber(), id)) {
            throw new ConflictException("Invoice number '" + req.invoiceNumber() + "' is already used by another invoice");
        }

        UUID originalExportJobId = invoice.getExportJobId();
        String originalMilestoneKey = normalizeScopeKey(invoice.getPaymentMilestoneKey());
        BigDecimal originalSubtotal = scaleAmount(invoice.getSubtotal());

        invoice.getLineItems().clear();
        buildInvoice(invoice, req);
        validateExportJobInvoiceBudget(
                req.exportJobId(),
                req.paymentMilestoneKey(),
                invoice.getSubtotal(),
                id,
                originalExportJobId,
                originalMilestoneKey,
                originalSubtotal
        );
        return toDto(invoiceRepository.save(invoice));
    }

    @Transactional
    public InvoiceDto updateStatus(UUID id, String status) {
        Invoice invoice = findOrThrow(id);
        invoice.setStatus(status);
        Invoice saved = invoiceRepository.save(invoice);

        if ("PAID".equalsIgnoreCase(status) && saved.getExportJobId() != null) {
            synchronizePaymentMilestones(saved);
        }

        return toDto(saved);
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
            .paymentMilestoneKey(original.getPaymentMilestoneKey())
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
        invoice.setPaymentMilestoneKey(req.paymentMilestoneKey() == null || req.paymentMilestoneKey().isBlank() ? null : req.paymentMilestoneKey().trim());

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

    private void validateExportJobInvoiceBudget(
            UUID exportJobId,
            String paymentMilestoneKey,
            BigDecimal invoiceSubtotal,
            UUID invoiceIdToExclude,
            UUID originalExportJobId,
            String originalMilestoneKey,
            BigDecimal originalSubtotal
    ) {
        if (exportJobId == null) {
            return;
        }

        BigDecimal normalizedSubtotal = scaleAmount(invoiceSubtotal);
        String normalizedMilestoneKey = normalizeScopeKey(paymentMilestoneKey);

        // Backward compatibility: allow metadata/status edits on existing legacy invoices
        // that were linked before strict subtotal correspondence was introduced.
        boolean unchangedExistingLinkedInvoice = invoiceIdToExclude != null
                && Objects.equals(originalExportJobId, exportJobId)
                && Objects.equals(originalMilestoneKey, normalizedMilestoneKey)
                && originalSubtotal != null
                && normalizedSubtotal.compareTo(originalSubtotal) == 0;
        if (unchangedExistingLinkedInvoice) {
            return;
        }

        ExportJob exportJob = exportJobRepository.findById(exportJobId)
                .orElseThrow(() -> new ResourceNotFoundException("Export job not found: " + exportJobId));

        BigDecimal allowedSubtotal = resolveAllowedSubtotal(exportJob, normalizedMilestoneKey);
        if (normalizedSubtotal.compareTo(allowedSubtotal) != 0) {
            throw new ConflictException(
                    "Invoice subtotal must match the linked export job amount (%s != %s)"
                            .formatted(normalizedSubtotal.stripTrailingZeros().toPlainString(), allowedSubtotal.stripTrailingZeros().toPlainString())
            );
        }

        BigDecimal existingSubtotal = invoiceIdToExclude == null
                ? invoiceRepository.sumSubtotalByExportJobId(exportJobId)
                : invoiceRepository.sumSubtotalByExportJobIdAndIdNot(exportJobId, invoiceIdToExclude);

        BigDecimal nextSubtotal = existingSubtotal.add(normalizedSubtotal);
        if (nextSubtotal.compareTo(exportJob.getProjectValue().setScale(2, RoundingMode.HALF_UP)) > 0) {
            throw new ConflictException(
                    "Linked invoice subtotals exceed export job project value (%s > %s)"
                            .formatted(nextSubtotal.stripTrailingZeros().toPlainString(), exportJob.getProjectValue().stripTrailingZeros().toPlainString())
            );
        }
    }

    private String normalizeScopeKey(String paymentMilestoneKey) {
        if (paymentMilestoneKey == null || paymentMilestoneKey.isBlank()) {
            return null;
        }
        return paymentMilestoneKey.trim();
    }

    private void synchronizePaymentMilestones(Invoice invoice) {
        ExportJob exportJob = exportJobRepository.findById(invoice.getExportJobId())
                .orElseThrow(() -> new ResourceNotFoundException("Export job not found: " + invoice.getExportJobId()));

        JsonNode parsed = readJsonSafe(exportJob.getPaymentMilestones());
        if (!(parsed instanceof ArrayNode milestones)) {
            return;
        }

        String now = java.time.Instant.now().toString();
        String milestoneKey = normalizeScopeKey(invoice.getPaymentMilestoneKey());

        if (milestoneKey == null) {
            for (JsonNode node : milestones) {
                if (node instanceof ObjectNode objectNode) {
                    objectNode.put("paid", true);
                    objectNode.put("paidAt", now);
                }
            }
        } else {
            boolean found = false;
            for (JsonNode node : milestones) {
                if (node instanceof ObjectNode objectNode && milestoneKey.equals(objectNode.path("key").asText(null))) {
                    objectNode.put("paid", true);
                    objectNode.put("paidAt", now);
                    found = true;
                    break;
                }
            }

            if (!found) {
                throw new ConflictException("Payment milestone not found on export job: " + milestoneKey);
            }
        }

        try {
            exportJob.setPaymentMilestones(objectMapper.writeValueAsString(milestones));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to serialize payment milestones", e);
        }
        exportJobRepository.save(exportJob);
    }

    private JsonNode readJsonSafe(String raw) {
        try {
            if (raw == null || raw.isBlank()) {
                return objectMapper.createArrayNode();
            }
            return objectMapper.readTree(raw);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return objectMapper.createArrayNode();
        }
    }

    private BigDecimal scaleAmount(BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveAllowedSubtotal(ExportJob exportJob, String paymentMilestoneKey) {
        try {
            JsonNode root = objectMapper.readTree(exportJob.getPaymentMilestones());
            if (!root.isArray()) {
                return exportJob.getProjectValue().setScale(2, RoundingMode.HALF_UP);
            }

            List<JsonNode> milestones = new java.util.ArrayList<>();
            root.forEach(milestones::add);

            if (paymentMilestoneKey != null && !paymentMilestoneKey.isBlank()) {
                return milestones.stream()
                        .filter(node -> paymentMilestoneKey.equals(node.path("key").asText(null)))
                        .findFirst()
                        .map(node -> node.path("amount").decimalValue().setScale(2, RoundingMode.HALF_UP))
                        .orElseThrow(() -> new ConflictException("Payment milestone not found on export job: " + paymentMilestoneKey));
            }

            BigDecimal sum = BigDecimal.ZERO;
            for (JsonNode node : milestones) {
                JsonNode amountNode = node.get("amount");
                if (amountNode == null || amountNode.isNull()) {
                    continue;
                }
                sum = sum.add(amountNode.decimalValue());
            }

            if (sum.compareTo(BigDecimal.ZERO) <= 0) {
                return exportJob.getProjectValue().setScale(2, RoundingMode.HALF_UP);
            }
            return sum.setScale(2, RoundingMode.HALF_UP);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) {
            return exportJob.getProjectValue().setScale(2, RoundingMode.HALF_UP);
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
                inv.getPaymentMilestoneKey(),
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
