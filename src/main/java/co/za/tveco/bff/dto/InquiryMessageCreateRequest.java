package co.za.tveco.bff.dto;

import jakarta.validation.constraints.NotBlank;

public record InquiryMessageCreateRequest(
        @NotBlank String message,
        Boolean requiresClientResponse
) {}
