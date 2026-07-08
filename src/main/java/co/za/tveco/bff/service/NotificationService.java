package co.za.tveco.bff.service;

import co.za.tveco.bff.dto.AppNotificationDto;
import co.za.tveco.bff.dto.EmailOutboxMessageDto;
import co.za.tveco.bff.dto.EmitNotificationRequest;
import co.za.tveco.bff.dto.OutboxDispatchResultDto;
import co.za.tveco.bff.dto.OutboxStatsDto;
import co.za.tveco.bff.entity.AppNotification;
import co.za.tveco.bff.entity.EmailOutboxMessage;
import co.za.tveco.bff.exception.ResourceNotFoundException;
import co.za.tveco.bff.repository.AppNotificationRepository;
import co.za.tveco.bff.repository.EmailOutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final AppNotificationRepository appNotificationRepository;
    private final EmailOutboxRepository emailOutboxRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.notification.webhook-url:}")
    private String webhookUrl;

    @Value("${app.notification.webhook-secret:}")
    private String webhookSecret;

    @Transactional(readOnly = true)
    public List<AppNotificationDto> getNotifications() {
        return appNotificationRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public void markAsRead(UUID id) {
        AppNotification notification = appNotificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + id));
        notification.setRead(true);
        appNotificationRepository.save(notification);
    }

    @Transactional
    public AppNotificationDto emit(EmitNotificationRequest req) {
        AppNotification notification = AppNotification.builder()
                .title(req.title())
                .message(req.message())
                .eventType(req.eventType())
                .referenceId(req.referenceId())
                .read(false)
                .build();

        AppNotification saved = appNotificationRepository.save(notification);

        if (isNotBlank(req.emailTo()) && isNotBlank(req.emailSubject()) && isNotBlank(req.emailBody())) {
            EmailOutboxMessage outbox = EmailOutboxMessage.builder()
                    .recipient(req.emailTo())
                    .subject(req.emailSubject())
                    .body(req.emailBody())
                    .bodyHtml(req.emailHtmlBody())
                    .status("PENDING")
                    .attempts(0)
                    .build();
            emailOutboxRepository.save(outbox);
        }

        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return appNotificationRepository.countByReadFalse();
    }

    @Transactional(readOnly = true)
    public OutboxStatsDto outboxStats() {
        return new OutboxStatsDto(
                emailOutboxRepository.countByStatus("PENDING"),
                emailOutboxRepository.countByStatus("FAILED"),
                emailOutboxRepository.countByStatus("SENT")
        );
    }

    @Transactional(readOnly = true)
    public List<EmailOutboxMessageDto> getOutbox() {
        return emailOutboxRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public void retryOutboxMessage(UUID id) {
        EmailOutboxMessage message = emailOutboxRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Outbox message not found: " + id));
        message.setStatus("PENDING");
        message.setLastError(null);
        emailOutboxRepository.save(message);
    }

    @Transactional
    public long clearSentOutbox() {
        return emailOutboxRepository.deleteByStatus("SENT");
    }

    @Transactional
    public OutboxDispatchResultDto dispatchPendingOutbox() {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            return new OutboxDispatchResultDto(0, 0, true);
        }

        List<EmailOutboxMessage> candidates = emailOutboxRepository
                .findByStatusInAndAttemptsLessThan(List.of("PENDING", "FAILED"), 5);
        if (candidates.isEmpty()) {
            return new OutboxDispatchResultDto(0, 0, false);
        }

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();

        int sent = 0;
        int failed = 0;

        for (EmailOutboxMessage msg : candidates) {
            boolean ok = sendOutboxMessage(client, msg);
            msg.setAttempts(msg.getAttempts() + 1);
            if (ok) {
                sent += 1;
                msg.setStatus("SENT");
                msg.setSentAt(Instant.now());
                msg.setLastError(null);
            } else {
                failed += 1;
                msg.setStatus("FAILED");
            }
            emailOutboxRepository.save(msg);
        }

        return new OutboxDispatchResultDto(sent, failed, false);
    }

    private boolean sendOutboxMessage(HttpClient client, EmailOutboxMessage msg) {
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

            if (isNotBlank(webhookSecret)) {
                requestBuilder.header("x-tveco-webhook-secret", webhookSecret);
            }

            HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return true;
            }
            msg.setLastError("HTTP " + response.statusCode());
            return false;
        } catch (JsonProcessingException e) {
            msg.setLastError("JSON serialization failed");
            return false;
        } catch (IOException | InterruptedException e) {
            msg.setLastError(e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }

    private AppNotificationDto toDto(AppNotification n) {
        return new AppNotificationDto(
                n.getId(),
                n.getTitle(),
                n.getMessage(),
                n.getCreatedAt(),
                n.isRead(),
                n.getEventType(),
                n.getReferenceId()
        );
    }

    private EmailOutboxMessageDto toDto(EmailOutboxMessage m) {
        return new EmailOutboxMessageDto(
                m.getId(),
                m.getRecipient(),
                m.getSubject(),
                m.getBody(),
                m.getBodyHtml(),
                m.getCreatedAt(),
                m.getStatus(),
                m.getAttempts(),
                m.getSentAt(),
                m.getLastError()
        );
    }
}