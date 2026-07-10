package co.za.tveco.bff.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record InquiryConvertToJobRequest(
        @NotNull UUID quoteId
) {}
