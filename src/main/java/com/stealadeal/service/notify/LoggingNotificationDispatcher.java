package com.stealadeal.service.notify;

import com.stealadeal.domain.ParticipantType;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Default dispatcher. Simulates an email channel for every recipient
 * and an SMS channel for buyers (who have a contactable email/phone
 * reference), logging each "delivery". Replace with SES/Twilio-backed
 * channels by registering a bean selected via
 * {@code app.notifications.provider}.
 */
@Component
@ConditionalOnMissingBean(name = "notificationDispatcher")
@ConditionalOnProperty(name = "app.notifications.provider", havingValue = "stub", matchIfMissing = true)
public class LoggingNotificationDispatcher implements NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationDispatcher.class);

    @Override
    public String name() {
        return "stub";
    }

    @Override
    public List<String> dispatch(NotificationMessage message) {
        List<String> channels = new ArrayList<>();
        channels.add("email");
        log.info("[notify/stub] email -> {}:{} \"{}\"",
                message.recipientType(), message.recipientReference(), message.title());
        if (message.recipientType() == ParticipantType.BUYER) {
            channels.add("sms");
            log.info("[notify/stub] sms -> {} \"{}\"", message.recipientReference(), message.title());
        }
        return channels;
    }
}
