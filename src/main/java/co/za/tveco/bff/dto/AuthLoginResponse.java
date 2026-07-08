package co.za.tveco.bff.dto;

public record AuthLoginResponse(
        String email,
        String role,
        String accessToken,
        long expiresInSeconds,
        String refreshToken,
        long refreshExpiresInSeconds
) {}
