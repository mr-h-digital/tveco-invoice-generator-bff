package co.za.tveco.bff.service;

import co.za.tveco.bff.entity.EmailOutboxMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "app.notification.delivery.provider", havingValue = "brevo")
public class BrevoNotificationDeliveryProvider implements NotificationDeliveryProvider {

    private static final String BREVO_SEND_ENDPOINT = "/v3/smtp/email";

    private final ObjectMapper objectMapper;

    @Value("${app.notification.brevo.api-key:}")
    private String brevoApiKey;

    @Value("${app.notification.brevo.base-url:https://api.brevo.com}")
    private String brevoBaseUrl;

    @Value("${app.notification.brevo.sender-email:}")
    private String senderEmail;

    @Value("${app.notification.brevo.sender-name:TVECO Operations}")
    private String senderName;

    public BrevoNotificationDeliveryProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean isConfigured() {
        return isNotBlank(brevoApiKey) && isNotBlank(senderEmail);
    }

    @Override
    public DeliveryResult deliver(EmailOutboxMessage msg) {
        if (!isConfigured()) {
            return new DeliveryResult(false, "Brevo provider is not configured (api-key and sender-email required)");
        }

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();

        String payload;
        try {
            payload = buildPayload(msg);
        } catch (JsonProcessingException e) {
            return new DeliveryResult(false, "JSON serialization failed");
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(stripTrailingSlash(brevoBaseUrl) + BREVO_SEND_ENDPOINT))
                .timeout(Duration.ofSeconds(20))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header("api-key", brevoApiKey)
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return new DeliveryResult(true, null);
            }

            String bodySnippet = response.body() == null ? "" : response.body();
            if (bodySnippet.length() > 280) {
                bodySnippet = bodySnippet.substring(0, 280) + "...";
            }
            return new DeliveryResult(false, "Brevo HTTP " + response.statusCode() + " - " + bodySnippet);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new DeliveryResult(false, e.getMessage());
        }
    }

    private String buildPayload(EmailOutboxMessage msg) throws JsonProcessingException {
        Map<String, Object> payload = new LinkedHashMap<>();

        payload.put("sender", Map.of(
                "name", senderName,
                "email", senderEmail
        ));
        payload.put("to", List.of(Map.of("email", msg.getRecipient())));
        payload.put("subject", msg.getSubject());

        if (isNotBlank(msg.getBody())) {
            payload.put("textContent", msg.getBody());
        }
        if (isNotBlank(msg.getBodyHtml())) {
            payload.put("htmlContent", msg.getBodyHtml());
        }
        if (!isNotBlank(msg.getBody()) && !isNotBlank(msg.getBodyHtml())) {
            payload.put("textContent", "(empty message)");
        }

        return objectMapper.writeValueAsString(payload);
    }

    private String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "https://api.brevo.com";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
