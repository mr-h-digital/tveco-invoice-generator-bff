package co.za.tveco.bff.service;

import java.time.Instant;

public record R2PresignedDownload(
        String url,
        Instant expiresAt
) {}