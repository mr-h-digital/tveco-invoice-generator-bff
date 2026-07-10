package co.za.tveco.bff.dto;

import jakarta.validation.constraints.NotBlank;

public record InquiryStatusUpdateRequest(
        @NotBlank String status
) {}
