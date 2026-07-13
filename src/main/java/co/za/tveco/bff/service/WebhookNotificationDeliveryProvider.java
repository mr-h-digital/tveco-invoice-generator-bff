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
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "app.notification.delivery.provider", havingValue = "webhook", matchIfMissing = true)
public class WebhookNotificationDeliveryProvider implements NotificationDeliveryProvider {

    private final ObjectMapper objectMapper;

    @Value("${app.notification.webhook-url:}")
    private String webhookUrl;

    @Value("${app.notification.webhook-secret:}")
    private String webhookSecret;

    public WebhookNotificationDeliveryProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean isConfigured() {
        return webhookUrl != null && !webhookUrl.isBlank();
    }

    @Override
    public DeliveryResult deliver(EmailOutboxMessage msg) {
        if (!isConfigured()) {
            return new DeliveryResult(false, "Webhook URL is not configured");
        }

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();

        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("id", msg.getId().toString());
            payload.put("to", msg.getRecipient());
            payload.put("subject", msg.getSubject());
            payload.put("body", msg.getBody());
            payload.put("bodyHtml", msg.getBodyHtml());
            payload.put("createdAt", msg.getCreatedAt() != null ? msg.getCreatedAt().toString() : Instant.now().toString());
            String body = objectMapper.writeValueAsString(payload);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .timeout(Duration.ofSeconds(15))
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(body));

            if (webhookSecret != null && !webhookSecret.isBlank()) {
                requestBuilder.header("x-tveco-webhook-secret", webhookSecret);
            }

            HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return new DeliveryResult(true, null);
            }

            return new DeliveryResult(false, "HTTP " + response.statusCode());
        } catch (JsonProcessingException e) {
            return new DeliveryResult(false, "JSON serialization failed");
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new DeliveryResult(false, e.getMessage());
        }
    }
}
