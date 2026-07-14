package co.za.tveco.bff.service;

import java.time.Instant;
import java.util.Map;

public record R2PresignedUpload(
        String url,
        Instant expiresAt,
        Map<String, String> requiredHeaders
) {}