package co.za.tveco.bff.dto;

import java.util.UUID;

public record AuthLoginResponse(
        String email,
        String role,
        UUID clientId,
        String accessToken,
        long expiresInSeconds
) {}
