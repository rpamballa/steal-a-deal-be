package com.stealadeal.repository;

import com.stealadeal.domain.Notification;
import com.stealadeal.domain.ParticipantType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipientTypeAndRecipientReferenceOrderByCreatedAtDesc(
            ParticipantType recipientType,
            String recipientReference
    );

    List<Notification> findByRecipientTypeAndRecipientReferenceAndReadOrderByCreatedAtDesc(
            ParticipantType recipientType,
            String recipientReference,
            boolean read
    );

    List<Notification> findByDealIdOrderByCreatedAtAsc(Long dealId);
}
