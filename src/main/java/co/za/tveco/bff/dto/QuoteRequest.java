package co.za.tveco.bff.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record QuoteRequest(
        @NotBlank(message = "Quote number is required")
        String quoteNumber,

        @NotBlank(message = "Status is required")
        @Pattern(regexp = "DRAFT|SENT|ACCEPTED|REJECTED|EXPIRED", message = "Status must be DRAFT, SENT, ACCEPTED, REJECTED or EXPIRED")
        String status,

        @NotBlank(message = "Issue date is required")
        String issueDate,

        @NotBlank(message = "Expiry date is required")
        String expiryDate,

        UUID clientId,

        UUID inquiryId,

        @NotNull @Valid
        ClientSnapshotDto clientSnapshot,

        @NotEmpty(message = "At least one line item is required")
        @Valid
        List<LineItemRequest> lineItems,

        @Pattern(regexp = "AMOUNT|PERCENT", message = "Discount type must be AMOUNT or PERCENT")
        String discountType,

        BigDecimal discountValue,

        boolean vatEnabled,
        BigDecimal vatRate,

        String notes
) {}
