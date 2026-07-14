package co.za.tveco.bff.dto;

import java.time.Instant;

public record DocumentDownloadUrlResponse(
        String url,
        Instant expiresAt
) {}