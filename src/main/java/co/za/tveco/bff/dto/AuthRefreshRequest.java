package co.za.tveco.bff.dto;

import jakarta.validation.constraints.NotBlank;

public record AuthRefreshRequest(
        @NotBlank(message = "Refresh token is required")
        String refreshToken
) {}
