package com.stealadeal.service.notify;

import com.stealadeal.domain.ParticipantType;
import java.util.List;

/**
 * SPI for delivering a persisted notification over external channels
 * (email, SMS, push). The default {@link LoggingNotificationDispatcher}
 * logs the delivery and reports the channels it "sent" on so the
 * persistence path is exercised. SES/Twilio-backed beans drop in via
 * {@code app.notifications.provider}.
 */
public interface NotificationDispatcher {

    record NotificationMessage(
            Long notificationId,
            ParticipantType recipientType,
            String recipientReference,
            String title,
            String body,
            Long dealId
    ) {
    }

    String name();

    /**
     * Deliver the message. Returns the list of channel identifiers the
     * message was accepted on (e.g. "email", "sms"); empty means nothing
     * was dispatched.
     */
    List<String> dispatch(NotificationMessage message);
}
