package co.za.tveco.bff.dto;

import java.time.Instant;
import java.util.UUID;

public record ExportInquiryMessageDto(
        UUID id,
        UUID inquiryId,
        String senderRole,
        String senderEmail,
        String message,
        boolean requiresClientResponse,
        boolean clientResponded,
        Instant createdAt
) {}
