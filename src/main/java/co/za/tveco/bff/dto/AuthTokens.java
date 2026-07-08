package co.za.tveco.bff.dto;

public record AuthTokens(
        String email,
        String role,
        String accessToken,
        long expiresInSeconds,
        String refreshToken,
        long refreshExpiresInSeconds
) {}
