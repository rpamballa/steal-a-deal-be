package com.stealadeal.service;

import com.stealadeal.config.NotificationProperties;
import com.stealadeal.domain.Notification;
import com.stealadeal.domain.NotificationDispatchStatus;
import com.stealadeal.repository.NotificationRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drains the notification outbox: any notification still PENDING (the
 * inline fast path failed) is retried with a bounded attempt count.
 * Once attempts reach the configured max it is marked FAILED so it
 * stops being retried but remains visible for diagnostics.
 */
@Component
public class NotificationOutboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(NotificationOutboxProcessor.class);
    private static final int BATCH_SIZE = 50;

    private final NotificationRepository notificationRepository;
    private final TaskNotificationService taskNotificationService;
    private final NotificationProperties notificationProperties;

    public NotificationOutboxProcessor(
            NotificationRepository notificationRepository,
            TaskNotificationService taskNotificationService,
            NotificationProperties notificationProperties
    ) {
        this.notificationRepository = notificationRepository;
        this.taskNotificationService = taskNotificationService;
        this.notificationProperties = notificationProperties;
    }

    @Scheduled(fixedDelayString = "${app.notifications.outbox-poll-ms:30000}")
    @Transactional
    public int drainOutbox() {
        int maxAttempts = notificationProperties.maxDispatchAttempts();
        List<Notification> pending = notificationRepository
                .findByDispatchStatusAndDispatchAttemptsLessThanOrderByCreatedAtAsc(
                        NotificationDispatchStatus.PENDING,
                        maxAttempts,
                        PageRequest.of(0, BATCH_SIZE)
                );
        if (pending.isEmpty()) {
            return 0;
        }
        int dispatched = 0;
        for (Notification notification : pending) {
            boolean ok = taskNotificationService.attemptDispatch(notification);
            if (ok) {
                dispatched++;
            } else if (notification.getDispatchAttempts() >= maxAttempts) {
                notification.setDispatchStatus(NotificationDispatchStatus.FAILED);
                notificationRepository.save(notification);
                log.warn("[notify/outbox] notification {} marked FAILED after {} attempts",
                        notification.getId(), notification.getDispatchAttempts());
            }
        }
        log.info("[notify/outbox] processed {} pending, {} delivered", pending.size(), dispatched);
        return dispatched;
    }
}
