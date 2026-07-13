package co.za.tveco.bff.service;

import co.za.tveco.bff.dto.OutboxDispatchResultDto;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.notification.dispatch.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxDispatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatchScheduler.class);

    private final NotificationService notificationService;

    @Scheduled(fixedDelayString = "${app.notification.dispatch.interval-ms:120000}")
    public void dispatchPendingOutbox() {
        OutboxDispatchResultDto result = notificationService.dispatchPendingOutbox();
        if (result.skipped() || (result.sent() == 0 && result.failed() == 0)) {
            return;
        }
        log.info("Outbox dispatch completed: sent={}, failed={}", result.sent(), result.failed());
    }
}
