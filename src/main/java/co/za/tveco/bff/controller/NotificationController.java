package co.za.tveco.bff.controller;

import co.za.tveco.bff.dto.ApiResponse;
import co.za.tveco.bff.dto.AppNotificationDto;
import co.za.tveco.bff.dto.EmailOutboxMessageDto;
import co.za.tveco.bff.dto.EmitNotificationRequest;
import co.za.tveco.bff.dto.OutboxDispatchResultDto;
import co.za.tveco.bff.dto.OutboxStatsDto;
import co.za.tveco.bff.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ApiResponse<List<AppNotificationDto>> getNotifications() {
        return ApiResponse.of(notificationService.getNotifications());
    }

    @PatchMapping("/{id}/read")
    public ApiResponse<Map<String, Boolean>> markAsRead(@PathVariable UUID id) {
        notificationService.markAsRead(id);
        return ApiResponse.of(Map.of("ok", true));
    }

    @PostMapping("/emit")
    public ApiResponse<AppNotificationDto> emit(@Valid @RequestBody EmitNotificationRequest req) {
        return ApiResponse.of(notificationService.emit(req));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> unreadCount() {
        return ApiResponse.of(Map.of("count", notificationService.unreadCount()));
    }

    @GetMapping("/outbox/stats")
    public ApiResponse<OutboxStatsDto> outboxStats() {
        return ApiResponse.of(notificationService.outboxStats());
    }

    @GetMapping("/outbox")
    public ApiResponse<List<EmailOutboxMessageDto>> getOutbox() {
        return ApiResponse.of(notificationService.getOutbox());
    }

    @PostMapping("/outbox/{id}/retry")
    public ApiResponse<Map<String, Boolean>> retryOutboxMessage(@PathVariable UUID id) {
        notificationService.retryOutboxMessage(id);
        return ApiResponse.of(Map.of("ok", true));
    }

    @DeleteMapping("/outbox/sent")
    public ApiResponse<Map<String, Long>> clearSentOutbox() {
        return ApiResponse.of(Map.of("removed", notificationService.clearSentOutbox()));
    }

    @PostMapping("/outbox/dispatch")
    public ApiResponse<OutboxDispatchResultDto> dispatchPendingOutbox() {
        return ApiResponse.of(notificationService.dispatchPendingOutbox());
    }
}