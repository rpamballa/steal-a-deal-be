package com.stealadeal.service;

import com.stealadeal.domain.Deal;
import com.stealadeal.domain.DealStage;
import com.stealadeal.domain.DealTask;
import com.stealadeal.domain.DealTaskStatus;
import com.stealadeal.domain.Notification;
import com.stealadeal.domain.ParticipantType;
import com.stealadeal.repository.DealRepository;
import com.stealadeal.repository.DealTaskRepository;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class InboxService {

    private static final String DEALER_REVIEW_DOCS = "dealer-review-documents";

    public record InboxSummary(
            int totalDeals,
            int activeDeals,
            int completedDeals,
            int openTaskCount,
            int inProgressTaskCount,
            int unreadNotificationCount,
            int readyForHandoffCount
    ) {
    }

    public record QueueSummary(
            int awaitingBuyerCount,
            int needsDocumentReviewCount,
            int readyForHandoffCount,
            int stalledCount
    ) {
    }

    public record InboxDealItem(
            Long dealId,
            Long vehicleId,
            Long dealerId,
            String vehicleTitle,
            DealStage stage,
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
    }

    public record ParticipantInbox(
            ParticipantType participantType,
            String participantReference,
            InboxSummary summary,
            List<InboxDealItem> deals,
            List<DealTask> tasks,
            List<Notification> notifications
    ) {
    }

    public record DealerInbox(
            ParticipantInbox inbox,
            QueueSummary queueSummary
    ) {
    }

    public record DealerQueue(
            Long dealerId,
            QueueSummary summary,
            List<InboxDealItem> awaitingBuyer,
            List<InboxDealItem> needsDocumentReview,
            List<InboxDealItem> readyForHandoff,
            List<InboxDealItem> stalled
    ) {
    }

    private final DealRepository dealRepository;
    private final DealTaskRepository dealTaskRepository;
    private final TaskNotificationService taskNotificationService;
    private final DealService dealService;
    private final DealerService dealerService;

    public InboxService(
            DealRepository dealRepository,
            DealTaskRepository dealTaskRepository,
            TaskNotificationService taskNotificationService,
            DealService dealService,
            DealerService dealerService
    ) {
        this.dealRepository = dealRepository;
        this.dealTaskRepository = dealTaskRepository;
        this.taskNotificationService = taskNotificationService;
        this.dealService = dealService;
        this.dealerService = dealerService;
    }

    public ParticipantInbox getBuyerInbox(String buyerEmail) {
        List<Deal> deals = dealRepository.findByBuyerEmailOrderByUpdatedAtDesc(buyerEmail);
        List<DealTask> tasks = taskNotificationService.getTasksForAssignee(ParticipantType.BUYER, buyerEmail, null);
        List<Notification> notifications = taskNotificationService.getNotifications(ParticipantType.BUYER, buyerEmail, null);

        return new ParticipantInbox(
                ParticipantType.BUYER,
                buyerEmail,
                buildSummary(deals, tasks, notifications),
                buildDealItems(deals, ParticipantType.BUYER, tasks, notifications),
                tasks,
                notifications
        );
    }

    public DealerInbox getDealerInbox(Long dealerId) {
        dealerService.getDealer(dealerId);
        String dealerReference = String.valueOf(dealerId);
        List<Deal> deals = dealRepository.findByVehicleDealerIdOrderByUpdatedAtDesc(dealerId);
        List<DealTask> tasks = taskNotificationService.getTasksForAssignee(ParticipantType.DEALER, dealerReference, null);
        List<Notification> notifications = taskNotificationService.getNotifications(ParticipantType.DEALER, dealerReference, null);
        QueueSummary queueSummary = getDealerQueue(dealerId).summary();

        return new DealerInbox(
                new ParticipantInbox(
                        ParticipantType.DEALER,
                        dealerReference,
                        buildSummary(deals, tasks, notifications),
                        buildDealItems(deals, ParticipantType.DEALER, tasks, notifications),
                        tasks,
                        notifications
                ),
                queueSummary
        );
    }

    public DealerQueue getDealerQueue(Long dealerId) {
        dealerService.getDealer(dealerId);
        List<Deal> deals = dealRepository.findByVehicleDealerIdOrderByUpdatedAtDesc(dealerId);
        List<DealTask> allTasks = deals.isEmpty()
                ? List.of()
                : dealTaskRepository.findByDealIdInOrderByUpdatedAtDesc(deals.stream().map(Deal::getId).toList());

        Map<Long, List<DealTask>> actionableTasksByDeal = allTasks.stream()
                .filter(task -> task.getStatus() == DealTaskStatus.OPEN || task.getStatus() == DealTaskStatus.IN_PROGRESS)
                .collect(Collectors.groupingBy(task -> task.getDeal().getId()));

        List<InboxDealItem> queueItems = buildDealItems(
                deals,
                ParticipantType.DEALER,
                taskNotificationService.getTasksForAssignee(ParticipantType.DEALER, String.valueOf(dealerId), null),
                taskNotificationService.getNotifications(ParticipantType.DEALER, String.valueOf(dealerId), null)
        );
        Map<Long, InboxDealItem> queueItemsByDealId = queueItems.stream()
                .collect(Collectors.toMap(InboxDealItem::dealId, Function.identity()));

        List<InboxDealItem> awaitingBuyer = deals.stream()
                .filter(this::isActiveDeal)
                .filter(deal -> actionableTasksByDeal.getOrDefault(deal.getId(), List.of()).stream()
                        .anyMatch(task -> task.getAssigneeType() == ParticipantType.BUYER))
                .map(deal -> queueItemsByDealId.get(deal.getId()))
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(InboxDealItem::updatedAt).reversed())
                .toList();

        List<InboxDealItem> needsDocumentReview = deals.stream()
                .filter(this::isActiveDeal)
                .filter(deal -> actionableTasksByDeal.getOrDefault(deal.getId(), List.of()).stream()
                        .anyMatch(task -> task.getAssigneeType() == ParticipantType.DEALER && DEALER_REVIEW_DOCS.equals(task.getCode())))
                .map(deal -> queueItemsByDealId.get(deal.getId()))
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(InboxDealItem::updatedAt).reversed())
                .toList();

        List<InboxDealItem> readyForHandoff = queueItems.stream()
                .filter(item -> item.readyForHandoff() && item.stage() != DealStage.COMPLETED && item.stage() != DealStage.CANCELED)
                .sorted(Comparator.comparing(InboxDealItem::updatedAt).reversed())
                .toList();

        List<InboxDealItem> stalled = queueItems.stream()
                .filter(item -> item.stage() != DealStage.COMPLETED && item.stage() != DealStage.CANCELED)
                .filter(item -> item.updatedAt().isBefore(OffsetDateTime.now().minusHours(72)))
                .sorted(Comparator.comparing(InboxDealItem::updatedAt).reversed())
                .toList();

        return new DealerQueue(
                dealerId,
                new QueueSummary(awaitingBuyer.size(), needsDocumentReview.size(), readyForHandoff.size(), stalled.size()),
                awaitingBuyer,
                needsDocumentReview,
                readyForHandoff,
                stalled
        );
    }

    private InboxSummary buildSummary(List<Deal> deals, List<DealTask> tasks, List<Notification> notifications) {
        int openTaskCount = (int) tasks.stream().filter(task -> task.getStatus() == DealTaskStatus.OPEN).count();
        int inProgressTaskCount = (int) tasks.stream().filter(task -> task.getStatus() == DealTaskStatus.IN_PROGRESS).count();
        int activeDeals = (int) deals.stream().filter(this::isActiveDeal).count();
        int completedDeals = (int) deals.stream().filter(deal -> deal.getStage() == DealStage.COMPLETED).count();
        int readyForHandoffCount = (int) deals.stream()
                .filter(this::isActiveDeal)
                .map(deal -> dealService.getDealReadiness(deal.getId()))
                .filter(DealService.DealReadiness::readyForHandoff)
                .count();

        return new InboxSummary(
                deals.size(),
                activeDeals,
                completedDeals,
                openTaskCount,
                inProgressTaskCount,
                (int) notifications.stream().filter(notification -> !notification.isRead()).count(),
                readyForHandoffCount
        );
    }

    private List<InboxDealItem> buildDealItems(
            List<Deal> deals,
            ParticipantType participantType,
            List<DealTask> participantTasks,
            List<Notification> participantNotifications
    ) {
        Map<Long, List<DealTask>> tasksByDeal = participantTasks.stream()
                .filter(task -> task.getStatus() == DealTaskStatus.OPEN || task.getStatus() == DealTaskStatus.IN_PROGRESS)
                .collect(Collectors.groupingBy(task -> task.getDeal().getId()));
        Map<Long, Long> unreadNotificationCountByDeal = participantNotifications.stream()
                .filter(notification -> !notification.isRead() && notification.getDeal() != null)
                .collect(Collectors.groupingBy(notification -> notification.getDeal().getId(), Collectors.counting()));

        return deals.stream()
                .map(deal -> {
                    DealService.DealReadiness readiness = dealService.getDealReadiness(deal.getId());
                    List<DealTask> tasks = tasksByDeal.getOrDefault(deal.getId(), List.of()).stream()
                            .sorted(Comparator.comparing(DealTask::getUpdatedAt).reversed())
                            .toList();
                    return new InboxDealItem(
                            deal.getId(),
                            deal.getVehicle().getId(),
                            deal.getVehicle().getDealer().getId(),
                            deal.getVehicle().getModelYear() + " " + deal.getVehicle().getMake() + " " + deal.getVehicle().getModel(),
                            deal.getStage(),
                            deal.isDepositPaid(),
                            deal.getFulfillmentScheduledAt(),
                            deal.getFulfillmentLocation(),
                            readiness.readyForHandoff(),
                            readiness.readyForCompletion(),
                            readiness.blockers(),
                            tasks.size(),
                            unreadNotificationCountByDeal.getOrDefault(deal.getId(), 0L).intValue(),
                            determineNextAction(participantType, tasks, readiness),
                            deal.getUpdatedAt()
                    );
                })
                .toList();
    }

    private String determineNextAction(
            ParticipantType participantType,
            List<DealTask> tasks,
            DealService.DealReadiness readiness
    ) {
        if (!tasks.isEmpty()) {
            return tasks.getFirst().getTitle();
        }
        if (participantType == ParticipantType.DEALER && readiness.readyForHandoff()) {
            return "Complete vehicle handoff";
        }
        if (participantType == ParticipantType.BUYER && readiness.readyForHandoff()) {
            return "Prepare for vehicle handoff";
        }
        return readiness.blockers().isEmpty() ? "Monitor deal progress" : readiness.blockers().getFirst();
    }

    private boolean isActiveDeal(Deal deal) {
        return deal.getStage() != DealStage.CANCELED && deal.getStage() != DealStage.COMPLETED;
    }
}
