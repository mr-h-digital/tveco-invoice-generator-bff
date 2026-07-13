package co.za.tveco.bff.service.messaging.meta;

import co.za.tveco.bff.service.RecoveryMessagingProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * Implementation of RecoveryMessagingProvider using Meta WhatsApp Cloud API.
 *
 * Setup:
 * 1. Create Meta Business Account: https://business.facebook.com/
 * 2. Register WhatsApp Business Account
 * 3. Set up app with WhatsApp product: https://developers.facebook.com/docs/whatsapp/cloud-api/get-started
 * 4. Create message templates: https://developers.facebook.com/docs/whatsapp/message-templates/guidelines
 * 5. Get Phone Number ID and API token from Meta Business Manager
 * 6. Set environment variables: META_WHATSAPP_API_TOKEN, META_WHATSAPP_PHONE_NUMBER_ID
 *
 * Pricing:
 * - Free: First 1000 messages/month
 * - Pay-as-you-go: ~$0.01-$0.50 per message depending on template type and region
 * - Volume discounts available at scale
 *
 * Template Examples:
 * - otp_recovery_username: "Use this code to recover your username: {{1}}"
 * - otp_recovery_password: "Use this code to reset your password: {{1}}"
 */
@Service
@ConditionalOnProperty(
        name = "app.recovery.messaging.provider",
        havingValue = "meta-whatsapp"
)
public class MetaWhatsAppProvider implements RecoveryMessagingProvider {

    private static final Logger log = LoggerFactory.getLogger(MetaWhatsAppProvider.class);

    private final RestTemplate restTemplate;
    private final String apiToken;
    private final String phoneNumberId;
    private final String apiBaseUrl;

    public MetaWhatsAppProvider(
            RestTemplate restTemplate,
            @Value("${app.recovery.messaging.meta.api-token:}") String apiToken,
            @Value("${app.recovery.messaging.meta.phone-number-id:}") String phoneNumberId,
            @Value("${app.recovery.messaging.meta.api-base-url:https://graph.instagram.com/v18.0}") String apiBaseUrl
    ) {
        this.restTemplate = restTemplate;
        this.apiToken = apiToken;
        this.phoneNumberId = phoneNumberId;
        this.apiBaseUrl = apiBaseUrl;

        if (apiToken == null || apiToken.isBlank() || phoneNumberId == null || phoneNumberId.isBlank()) {
            log.warn("MetaWhatsAppProvider initialized with missing credentials. " +
                    "Set META_WHATSAPP_API_TOKEN and META_WHATSAPP_PHONE_NUMBER_ID environment variables.");
        }
    }

    @Override
    public void sendPasswordResetEmail(String toEmail, String resetLink, String userName) {
        // Password reset via WhatsApp uses a template message with the reset link
        log.debug("Sending password reset email to: {}", toEmail);

        // Note: This method is typically for email delivery. For WhatsApp password reset,
        // use sendOtp() with channel=WHATSAPP instead, as WhatsApp OTP templates
        // are designed for one-time codes, not long links.

        // As a fallback, we could send via email provider or SMS provider
        // For now, log that this is not implemented for WhatsApp
        log.info("Password reset via WhatsApp direct link not implemented. Use OTP-based recovery instead.");
    }

    @Override
    public void sendOtp(String channel, String destination, String otp, String purpose) {
        if (!"WHATSAPP".equalsIgnoreCase(channel)) {
            // This provider only handles WhatsApp; other channels should use different providers
            throw new IllegalArgumentException("MetaWhatsAppProvider only supports WHATSAPP channel");
        }

        if (destination == null || destination.isBlank()) {
            throw new IllegalArgumentException("WhatsApp destination (phone number) is required");
        }

        if (otp == null || !otp.matches("\\d{6}")) {
            throw new IllegalArgumentException("OTP must be a 6-digit code");
        }

        String templateName = "USERNAME_RECOVERY".equals(purpose)
                ? "otp_recovery_username"
                : "otp_recovery_password";

        try {
            sendTemplateMessage(normalizePhoneNumber(destination), templateName, List.of(otp));
            log.info("OTP sent via WhatsApp to phone ending in: ...{}, purpose: {}", 
                    destination.substring(Math.max(0, destination.length() - 4)), purpose);
        } catch (Exception e) {
            log.error("Failed to send WhatsApp OTP to {}: {}", destination, e.getMessage(), e);
            throw new RuntimeException("Failed to send WhatsApp OTP: " + e.getMessage(), e);
        }
    }

    private void sendTemplateMessage(String phoneNumber, String templateName, List<String> parameters) throws RestClientException {
        String url = apiBaseUrl + "/" + phoneNumberId + "/messages";

        MetaWhatsAppRequest request = new MetaWhatsAppRequest(phoneNumber, templateName, parameters);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiToken);

        HttpEntity<MetaWhatsAppRequest> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<MetaWhatsAppResponse> response = restTemplate.postForEntity(url, entity, MetaWhatsAppResponse.class);

            if (response.getBody() == null || !response.getBody().isSuccess()) {
                String errorMsg = response.getBody() != null
                        ? response.getBody().getErrorMessage()
                        : "Unknown error";
                log.error("Meta WhatsApp API error: {}", errorMsg);
                throw new RuntimeException("Meta API error: " + errorMsg);
            }

            String messageId = response.getBody().getMessageId();
            log.debug("WhatsApp message sent successfully. Message ID: {}", messageId);
        } catch (RestClientException e) {
            log.error("RestTemplate error calling Meta WhatsApp API: {}", e.getMessage(), e);
            throw e;
        }
    }

    private String normalizePhoneNumber(String phoneNumber) {
        // Remove all non-digit characters
        String digits = phoneNumber.replaceAll("\\D", "");

        // If it doesn't start with country code, assume South Africa (+27)
        if (!digits.startsWith("27")) {
            // If it starts with 0, replace with 27 (South African convention)
            if (digits.startsWith("0")) {
                digits = "27" + digits.substring(1);
            } else {
                // Assume it's a full number without country code
                digits = "27" + digits;
            }
        }

        return digits;
    }
}
