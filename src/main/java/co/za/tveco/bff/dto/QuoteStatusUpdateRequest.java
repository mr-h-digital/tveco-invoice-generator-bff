package co.za.tveco.bff.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record QuoteStatusUpdateRequest(
        @NotBlank
        @Pattern(regexp = "DRAFT|SENT|ACCEPTED|REJECTED|EXPIRED", message = "Status must be DRAFT, SENT, ACCEPTED, REJECTED or EXPIRED")
        String status
) {}
