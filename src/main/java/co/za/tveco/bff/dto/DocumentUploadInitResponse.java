package co.za.tveco.bff.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record DocumentUploadInitResponse(
        UUID documentId,
        String objectKey,
        String uploadUrl,
        Instant expiresAt,
        Map<String, String> requiredHeaders
) {}