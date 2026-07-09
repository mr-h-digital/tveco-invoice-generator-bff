package co.za.tveco.bff.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record InvoiceDto(
        UUID id,
        String invoiceNumber,
        String status,
        LocalDate issueDate,
        LocalDate dueDate,
        UUID clientId,
        UUID exportJobId,
        String paymentMilestoneKey,
        ClientSnapshotDto clientSnapshot,
        List<LineItemDto> lineItems,
        BigDecimal subtotal,
        String discountType,
        BigDecimal discountValue,
        BigDecimal discountAmount,
        boolean vatEnabled,
        BigDecimal vatRate,
        BigDecimal vatAmount,
        BigDecimal total,
        String notes,
        PaymentDetailsDto paymentDetails,
        Instant createdAt,
        Instant updatedAt
) {}
