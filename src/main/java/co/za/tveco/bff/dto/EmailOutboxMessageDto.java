package co.za.tveco.bff.dto;

import java.time.Instant;
import java.util.UUID;

public record EmailOutboxMessageDto(
        UUID id,
        String to,
        String subject,
        String body,
        String bodyHtml,
        Instant createdAt,
        String status,
        int attempts,
        Instant sentAt,
        String lastError
) {}