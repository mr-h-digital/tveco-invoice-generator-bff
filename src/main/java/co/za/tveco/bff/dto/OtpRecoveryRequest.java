package co.za.tveco.bff.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OtpRecoveryRequest(
        @NotBlank(message = "Purpose is required")
        @Pattern(regexp = "USERNAME_RECOVERY|PASSWORD_RESET", message = "Purpose must be USERNAME_RECOVERY or PASSWORD_RESET")
        String purpose,

        @NotBlank(message = "Channel is required")
        @Pattern(regexp = "EMAIL|SMS|WHATSAPP", message = "Channel must be EMAIL, SMS, or WHATSAPP")
        String channel,

        @NotBlank(message = "Identifier is required")
        @Size(max = 255, message = "Identifier is too long")
        String identifier
) {
}
