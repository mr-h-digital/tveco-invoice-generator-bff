package co.za.tveco.bff.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record EmitNotificationRequest(
        @NotBlank
        @Pattern(regexp = "EXPORT_STATUS_CHANGED|EXPORT_DOCUMENT_COMPLETED|EXPORT_PAYMENT_REMINDER")
        String eventType,

        @NotBlank
        String title,

        @NotBlank
        String message,

        String referenceId,
        String emailTo,
        String emailSubject,
        String emailBody,
        String emailHtmlBody
) {}