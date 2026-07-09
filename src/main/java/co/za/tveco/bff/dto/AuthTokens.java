package co.za.tveco.bff.dto;

import java.util.UUID;

public record AuthTokens(
        String email,
        String role,
        UUID clientId,
        String accessToken,
        long expiresInSeconds,
        String refreshToken,
        long refreshExpiresInSeconds
) {}
