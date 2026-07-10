package co.za.tveco.bff.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record QuoteDto(
        UUID id,
        String quoteNumber,
        String status,
        LocalDate issueDate,
        LocalDate expiryDate,
        UUID clientId,
        UUID inquiryId,
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
        Instant clientDecisionAt,
        String clientDecisionNote,
        Instant createdAt,
        Instant updatedAt
) {}
