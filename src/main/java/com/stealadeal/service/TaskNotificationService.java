package com.stealadeal.service;

import com.stealadeal.domain.Deal;
import com.stealadeal.domain.DealDocument;
import com.stealadeal.domain.DealStage;
import com.stealadeal.domain.DealTask;
import com.stealadeal.domain.DealTaskStatus;
import com.stealadeal.domain.DocumentStatus;
import com.stealadeal.domain.DocumentType;
import com.stealadeal.domain.FulfillmentStatus;
import com.stealadeal.domain.Notification;
import com.stealadeal.domain.ParticipantType;
import com.stealadeal.repository.DealDocumentRepository;
import com.stealadeal.repository.DealTaskRepository;
import com.stealadeal.repository.NotificationRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class TaskNotificationService {

    private static final String BUYER_DEPOSIT = "buyer-pay-deposit";
    private static final String BUYER_AGREEMENT = "buyer-review-agreement";
    private static final String BUYER_LICENSE = "buyer-upload-driver-license";
    private static final String BUYER_INSURANCE = "buyer-upload-insurance-proof";
    private static final String DEALER_REVIEW_DOCS = "dealer-review-documents";
    private static final String DEALER_SCHEDULE = "dealer-schedule-fulfillment";
    private static final String DEALER_HANDOFF = "dealer-complete-handoff";

    private final DealTaskRepository dealTaskRepository;
    private final NotificationRepository notificationRepository;
    private final DealDocumentRepository dealDocumentRepository;

    public TaskNotificationService(
            DealTaskRepository dealTaskRepository,
            NotificationRepository notificationRepository,
            DealDocumentRepository dealDocumentRepository
    ) {
        this.dealTaskRepository = dealTaskRepository;
        this.notificationRepository = notificationRepository;
        this.dealDocumentRepository = dealDocumentRepository;
    }

    public void syncForDeal(Deal deal) {
        List<DealDocument> documents = dealDocumentRepository.findByDealId(deal.getId());
        boolean closed = deal.getStage() == DealStage.CANCELED || deal.getStage() == DealStage.COMPLETED;

        upsertTask(
                deal, BUYER_DEPOSIT, ParticipantType.BUYER, deal.getBuyerEmail(),
                "Pay reservation deposit", "Complete the required deposit to reserve the vehicle.",
                closed ? terminalStatus(deal) : statusForBoolean(deal.isDepositPaid(), false)
        );
        upsertTask(
                deal, BUYER_AGREEMENT, ParticipantType.BUYER, deal.getBuyerEmail(),
                "Review buyer agreement", "Review and complete the buyer agreement document.",
                closed ? terminalStatus(deal) : statusForDocument(documents, DocumentType.BUYER_AGREEMENT)
        );
        upsertTask(
                deal, BUYER_LICENSE, ParticipantType.BUYER, deal.getBuyerEmail(),
                "Upload driver license", "Upload a valid driver license for verification.",
                closed ? terminalStatus(deal) : statusForDocument(documents, DocumentType.DRIVER_LICENSE)
        );
        upsertTask(
                deal, BUYER_INSURANCE, ParticipantType.BUYER, deal.getBuyerEmail(),
                "Upload insurance proof", "Upload valid proof of insurance before handoff.",
                closed ? terminalStatus(deal) : statusForDocument(documents, DocumentType.INSURANCE_PROOF)
        );

        boolean allDocsApproved = documents.stream().allMatch(document -> document.getStatus() == DocumentStatus.APPROVED);
        upsertTask(
                deal, DEALER_REVIEW_DOCS, ParticipantType.DEALER, String.valueOf(deal.getVehicle().getDealer().getId()),
                "Review buyer documents", "Verify uploaded buyer documents and approve the set.",
                closed ? terminalStatus(deal) : statusForBoolean(allDocsApproved, false)
        );
        boolean fulfillmentScheduled = deal.getFulfillmentScheduledAt() != null
                && deal.getFulfillmentStatus() != FulfillmentStatus.UNSCHEDULED;
        upsertTask(
                deal, DEALER_SCHEDULE, ParticipantType.DEALER, String.valueOf(deal.getVehicle().getDealer().getId()),
                "Schedule fulfillment", "Set the pickup or delivery schedule for this deal.",
                closed ? terminalStatus(deal) : statusForBoolean(fulfillmentScheduled, false)
        );
        boolean handoffComplete = deal.getStage() == DealStage.COMPLETED || deal.getFulfillmentStatus() == FulfillmentStatus.COMPLETED;
        boolean handoffOpen = deal.getStage() == DealStage.READY_FOR_HANDOFF || deal.getFulfillmentStatus() == FulfillmentStatus.READY;
        upsertTask(
                deal, DEALER_HANDOFF, ParticipantType.DEALER, String.valueOf(deal.getVehicle().getDealer().getId()),
                "Complete vehicle handoff", "Confirm the buyer has received the vehicle and close the deal.",
                closed ? terminalStatus(deal) : handoffComplete ? DealTaskStatus.COMPLETED : handoffOpen ? DealTaskStatus.OPEN : DealTaskStatus.CANCELED
        );
    }

    @Transactional(readOnly = true)
    public List<DealTask> getDealTasks(Long dealId) {
        return dealTaskRepository.findByDealIdOrderByCreatedAtAsc(dealId);
    }

    @Transactional(readOnly = true)
    public List<DealTask> getTasksForAssignee(ParticipantType assigneeType, String assigneeReference, DealTaskStatus status) {
        return status == null
                ? dealTaskRepository.findByAssigneeTypeAndAssigneeReferenceOrderByCreatedAtDesc(assigneeType, assigneeReference)
                : dealTaskRepository.findByAssigneeTypeAndAssigneeReferenceAndStatusOrderByCreatedAtDesc(assigneeType, assigneeReference, status);
    }

    public DealTask updateTaskStatus(Long taskId, DealTaskStatus status) {
        DealTask task = dealTaskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
        task.setStatus(status);
        task.setUpdatedAt(OffsetDateTime.now());
        return dealTaskRepository.save(task);
    }

    @Transactional(readOnly = true)
    public List<Notification> getNotifications(ParticipantType recipientType, String recipientReference, Boolean unreadOnly) {
        if (Boolean.TRUE.equals(unreadOnly)) {
            return notificationRepository.findByRecipientTypeAndRecipientReferenceAndReadOrderByCreatedAtDesc(
                    recipientType, recipientReference, false
            );
        }
        return notificationRepository.findByRecipientTypeAndRecipientReferenceOrderByCreatedAtDesc(recipientType, recipientReference);
    }

    public Notification markNotificationRead(Long notificationId, boolean read) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        notification.setRead(read);
        return notificationRepository.save(notification);
    }

    public void createNotification(Deal deal, ParticipantType recipientType, String recipientReference, String title, String message) {
        Notification notification = new Notification();
        notification.setDeal(deal);
        notification.setRecipientType(recipientType);
        notification.setRecipientReference(recipientReference);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setRead(false);
        notification.setCreatedAt(OffsetDateTime.now());
        notificationRepository.save(notification);
    }

    private void upsertTask(
            Deal deal,
            String code,
            ParticipantType assigneeType,
            String assigneeReference,
            String title,
            String description,
            DealTaskStatus targetStatus
    ) {
        DealTask task = dealTaskRepository.findByDealIdAndCode(deal.getId(), code).orElse(null);
        boolean created = false;
        if (task == null) {
            task = new DealTask();
            task.setDeal(deal);
            task.setCode(code);
            task.setCreatedAt(OffsetDateTime.now());
            created = true;
        }
        DealTaskStatus currentStatus = task.getStatus();
        task.setAssigneeType(assigneeType);
        task.setAssigneeReference(assigneeReference);
        task.setTitle(title);
        task.setDescription(description);
        task.setUpdatedAt(OffsetDateTime.now());
        if (currentStatus == DealTaskStatus.IN_PROGRESS && targetStatus == DealTaskStatus.OPEN) {
            task.setStatus(DealTaskStatus.IN_PROGRESS);
        } else {
            task.setStatus(targetStatus);
        }
        DealTask savedTask = dealTaskRepository.save(task);
        if ((created || currentStatus != savedTask.getStatus()) && savedTask.getStatus() == DealTaskStatus.OPEN) {
            createNotification(
                    deal,
                    assigneeType,
                    assigneeReference,
                    savedTask.getTitle(),
                    savedTask.getDescription()
            );
        }
    }

    private DealTaskStatus statusForDocument(List<DealDocument> documents, DocumentType type) {
        return documents.stream()
                .filter(document -> document.getType() == type)
                .findFirst()
                .map(document -> document.getStatus() == DocumentStatus.APPROVED ? DealTaskStatus.COMPLETED : DealTaskStatus.OPEN)
                .orElse(DealTaskStatus.OPEN);
    }

    private DealTaskStatus statusForBoolean(boolean done, boolean canceled) {
        if (done) {
            return DealTaskStatus.COMPLETED;
        }
        if (canceled) {
            return DealTaskStatus.CANCELED;
        }
        return DealTaskStatus.OPEN;
    }

    private DealTaskStatus terminalStatus(Deal deal) {
        return deal.getStage() == DealStage.CANCELED ? DealTaskStatus.CANCELED : DealTaskStatus.COMPLETED;
    }
}
