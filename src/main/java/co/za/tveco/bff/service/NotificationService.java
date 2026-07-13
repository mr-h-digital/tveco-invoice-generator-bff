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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final AppNotificationRepository appNotificationRepository;
    private final EmailOutboxRepository emailOutboxRepository;
    private final NotificationDeliveryProvider notificationDeliveryProvider;

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
        if (!notificationDeliveryProvider.isConfigured()) {
            return new OutboxDispatchResultDto(0, 0, true);
        }

        List<EmailOutboxMessage> candidates = emailOutboxRepository
                .findByStatusInAndAttemptsLessThan(List.of("PENDING", "FAILED"), 5);
        if (candidates.isEmpty()) {
            return new OutboxDispatchResultDto(0, 0, false);
        }

        int sent = 0;
        int failed = 0;

        for (EmailOutboxMessage msg : candidates) {
            NotificationDeliveryProvider.DeliveryResult result = notificationDeliveryProvider.deliver(msg);
            msg.setAttempts(msg.getAttempts() + 1);
            if (result.success()) {
                sent += 1;
                msg.setStatus("SENT");
                msg.setSentAt(Instant.now());
                msg.setLastError(null);
            } else {
                failed += 1;
                msg.setStatus("FAILED");
                msg.setLastError(result.error());
            }
            emailOutboxRepository.save(msg);
        }

        return new OutboxDispatchResultDto(sent, failed, false);
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