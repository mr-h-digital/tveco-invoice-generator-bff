package co.za.tveco.bff.dto;

import jakarta.validation.constraints.NotBlank;

public record ClientQuoteDecisionRequest(
        @NotBlank String status,
        String note
) {}
