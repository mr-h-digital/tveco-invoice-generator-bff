package co.za.tveco.bff.dto;

import java.time.Instant;
import java.util.UUID;

public record AppNotificationDto(
        UUID id,
        String title,
        String message,
        Instant createdAt,
        boolean read,
        String eventType,
        String referenceId
) {}