package com.stealadeal.web;

import com.stealadeal.domain.ParticipantType;
import com.stealadeal.service.InboxService;
import jakarta.validation.constraints.Email;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Validated
public class InboxController {

    private final InboxService inboxService;

    public InboxController(InboxService inboxService) {
        this.inboxService = inboxService;
    }

    @GetMapping("/inbox/buyers/{buyerEmail}")
    @PreAuthorize("@accessControl.canAccessBuyer(authentication, #buyerEmail)")
    public ParticipantInboxResponse getBuyerInbox(@Email @PathVariable String buyerEmail) {
        return ParticipantInboxResponse.from(inboxService.getBuyerInbox(buyerEmail));
    }

    @GetMapping("/inbox/dealers/{dealerId}")
    @PreAuthorize("@accessControl.canAccessDealer(authentication, #dealerId)")
    public DealerInboxResponse getDealerInbox(@PathVariable Long dealerId) {
        return DealerInboxResponse.from(inboxService.getDealerInbox(dealerId));
    }

    @GetMapping("/dealers/{dealerId}/deal-queue")
    @PreAuthorize("@accessControl.canAccessDealer(authentication, #dealerId)")
    public DealerQueueResponse getDealerQueue(@PathVariable Long dealerId) {
        return DealerQueueResponse.from(inboxService.getDealerQueue(dealerId));
    }

    public record ParticipantInboxResponse(
            ParticipantType participantType,
            String participantReference,
            InboxSummaryResponse summary,
            List<InboxDealResponse> deals,
            List<TaskNotificationController.DealTaskResponse> tasks,
            List<TaskNotificationController.NotificationResponse> notifications
    ) {

        static ParticipantInboxResponse from(InboxService.ParticipantInbox inbox) {
            return new ParticipantInboxResponse(
                    inbox.participantType(),
                    inbox.participantReference(),
                    InboxSummaryResponse.from(inbox.summary()),
                    inbox.deals().stream().map(InboxDealResponse::from).toList(),
                    inbox.tasks().stream().map(TaskNotificationController.DealTaskResponse::from).toList(),
                    inbox.notifications().stream().map(TaskNotificationController.NotificationResponse::from).toList()
            );
        }
    }

    public record DealerInboxResponse(
            ParticipantInboxResponse inbox,
            QueueSummaryResponse queueSummary
    ) {

        static DealerInboxResponse from(InboxService.DealerInbox dealerInbox) {
            return new DealerInboxResponse(
                    ParticipantInboxResponse.from(dealerInbox.inbox()),
                    QueueSummaryResponse.from(dealerInbox.queueSummary())
            );
        }
    }

    public record DealerQueueResponse(
            Long dealerId,
            QueueSummaryResponse summary,
            List<InboxDealResponse> awaitingBuyer,
            List<InboxDealResponse> needsDocumentReview,
            List<InboxDealResponse> readyForHandoff,
            List<InboxDealResponse> stalled
    ) {

        static DealerQueueResponse from(InboxService.DealerQueue queue) {
            return new DealerQueueResponse(
                    queue.dealerId(),
                    QueueSummaryResponse.from(queue.summary()),
                    queue.awaitingBuyer().stream().map(InboxDealResponse::from).toList(),
                    queue.needsDocumentReview().stream().map(InboxDealResponse::from).toList(),
                    queue.readyForHandoff().stream().map(InboxDealResponse::from).toList(),
                    queue.stalled().stream().map(InboxDealResponse::from).toList()
            );
        }
    }

    public record InboxSummaryResponse(
            int totalDeals,
            int activeDeals,
            int completedDeals,
            int openTaskCount,
            int inProgressTaskCount,
            int unreadNotificationCount,
            int readyForHandoffCount
    ) {

        static InboxSummaryResponse from(InboxService.InboxSummary summary) {
            return new InboxSummaryResponse(
                    summary.totalDeals(),
                    summary.activeDeals(),
                    summary.completedDeals(),
                    summary.openTaskCount(),
                    summary.inProgressTaskCount(),
                    summary.unreadNotificationCount(),
                    summary.readyForHandoffCount()
            );
        }
    }

    public record QueueSummaryResponse(
            int awaitingBuyerCount,
            int needsDocumentReviewCount,
            int readyForHandoffCount,
            int stalledCount
    ) {

        static QueueSummaryResponse from(InboxService.QueueSummary summary) {
            return new QueueSummaryResponse(
                    summary.awaitingBuyerCount(),
                    summary.needsDocumentReviewCount(),
                    summary.readyForHandoffCount(),
                    summary.stalledCount()
            );
        }
    }

    public record InboxDealResponse(
            Long dealId,
            Long vehicleId,
            Long dealerId,
            String vehicleTitle,
            String stage,
            boolean depositPaid,
            OffsetDateTime fulfillmentScheduledAt,
            String fulfillmentLocation,
            boolean readyForHandoff,
            boolean readyForCompletion,
            List<String> blockers,
            int openTaskCount,
            int unreadNotificationCount,
            String nextAction,
            OffsetDateTime updatedAt
    ) {

        static InboxDealResponse from(InboxService.InboxDealItem item) {
            return new InboxDealResponse(
                    item.dealId(),
                    item.vehicleId(),
                    item.dealerId(),
                    item.vehicleTitle(),
                    item.stage().name(),
                    item.depositPaid(),
                    item.fulfillmentScheduledAt(),
                    item.fulfillmentLocation(),
                    item.readyForHandoff(),
                    item.readyForCompletion(),
                    item.blockers(),
                    item.openTaskCount(),
                    item.unreadNotificationCount(),
                    item.nextAction(),
                    item.updatedAt()
            );
        }
    }
}
