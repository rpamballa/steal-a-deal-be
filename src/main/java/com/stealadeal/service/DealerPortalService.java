package com.stealadeal.service;

import com.stealadeal.domain.Appointment;
import com.stealadeal.domain.AppointmentStatus;
import com.stealadeal.domain.Deal;
import com.stealadeal.domain.DealActivity;
import com.stealadeal.domain.DealDocument;
import com.stealadeal.domain.DealStage;
import com.stealadeal.domain.DealTask;
import com.stealadeal.domain.DealTaskStatus;
import com.stealadeal.domain.Dealer;
import com.stealadeal.domain.DealerInvoice;
import com.stealadeal.domain.DealerSubscription;
import com.stealadeal.domain.DocumentStatus;
import com.stealadeal.domain.InvoiceStatus;
import com.stealadeal.domain.Lead;
import com.stealadeal.domain.LeadStatus;
import com.stealadeal.domain.Notification;
import com.stealadeal.domain.ParticipantType;
import com.stealadeal.domain.SubscriptionPlan;
import com.stealadeal.domain.SubscriptionStatus;
import com.stealadeal.domain.Vehicle;
import com.stealadeal.domain.VehicleStatus;
import com.stealadeal.repository.AppointmentRepository;
import com.stealadeal.repository.DealActivityRepository;
import com.stealadeal.repository.DealDocumentRepository;
import com.stealadeal.repository.DealRepository;
import com.stealadeal.repository.DealerInvoiceRepository;
import com.stealadeal.repository.DealerSubscriptionRepository;
import com.stealadeal.repository.LeadRepository;
import com.stealadeal.repository.VehicleRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DealerPortalService {

    public record MetricCount(String code, long count) {
    }

    public record PortalOverview(
            Long dealerId,
            String dealerName,
            long totalInventoryCount,
            long liveInventoryCount,
            long reservedInventoryCount,
            long soldInventoryCount,
            BigDecimal totalInventoryValue,
            long leadCount,
            long newLeadCount,
            long qualifiedLeadCount,
            long appointmentCount,
            long requestedAppointmentCount,
            long activeDealCount,
            long completedDealCount,
            long readyForHandoffCount,
            long openTaskCount,
            long unreadNotificationCount,
            long stalledDealCount
    ) {
    }

    public record PipelineSnapshot(
            List<MetricCount> inventoryStatusCounts,
            List<MetricCount> leadStatusCounts,
            List<MetricCount> appointmentStatusCounts,
            List<MetricCount> dealStageCounts
    ) {
    }

    public record PortalDealItem(
            Long dealId,
            Long vehicleId,
            String vehicleTitle,
            String buyerName,
            String stage,
            boolean depositPaid,
            boolean readyForHandoff,
            List<String> blockers,
            String nextAction,
            OffsetDateTime updatedAt
    ) {
    }

    public record PortalActivityItem(
            Long dealId,
            String vehicleTitle,
            String eventType,
            String message,
            OffsetDateTime createdAt
    ) {
    }

    public record DealerPortal(
            PortalOverview overview,
            PipelineSnapshot pipeline,
            InboxService.QueueSummary queueSummary,
            List<PortalDealItem> recentDeals,
            List<PortalActivityItem> recentActivity
    ) {
    }

    public record PortalLeadItem(
            Long id,
            Long vehicleId,
            String vehicleTitle,
            String buyerName,
            String buyerEmail,
            LeadStatus status,
            String message,
            OffsetDateTime createdAt
    ) {
    }

    public record PortalAppointmentItem(
            Long id,
            Long vehicleId,
            String vehicleTitle,
            String buyerName,
            String buyerEmail,
            String type,
            AppointmentStatus status,
            OffsetDateTime scheduledAt,
            OffsetDateTime createdAt
    ) {
    }

    public record PortalDocumentItem(
            Long id,
            Long dealId,
            String vehicleTitle,
            String buyerName,
            String type,
            DocumentStatus status,
            String fileName,
            OffsetDateTime updatedAt
    ) {
    }

    public record PortalSubscription(
            Long id,
            Long dealerId,
            SubscriptionPlan plan,
            SubscriptionStatus status,
            BigDecimal monthlyPrice,
            OffsetDateTime currentPeriodStart,
            OffsetDateTime currentPeriodEnd,
            boolean autoRenew,
            OffsetDateTime updatedAt
    ) {
    }

    public record PortalInvoice(
            Long id,
            String invoiceNumber,
            InvoiceStatus status,
            BigDecimal amount,
            OffsetDateTime periodStart,
            OffsetDateTime periodEnd,
            OffsetDateTime dueAt,
            OffsetDateTime paidAt,
            OffsetDateTime createdAt
    ) {
    }

    private final DealerService dealerService;
    private final VehicleRepository vehicleRepository;
    private final LeadRepository leadRepository;
    private final AppointmentRepository appointmentRepository;
    private final DealRepository dealRepository;
    private final DealActivityRepository dealActivityRepository;
    private final DealDocumentRepository dealDocumentRepository;
    private final DealerSubscriptionRepository dealerSubscriptionRepository;
    private final DealerInvoiceRepository dealerInvoiceRepository;
    private final TaskNotificationService taskNotificationService;
    private final DealService dealService;
    private final InboxService inboxService;

    public DealerPortalService(
            DealerService dealerService,
            VehicleRepository vehicleRepository,
            LeadRepository leadRepository,
            AppointmentRepository appointmentRepository,
            DealRepository dealRepository,
            DealActivityRepository dealActivityRepository,
            DealDocumentRepository dealDocumentRepository,
            DealerSubscriptionRepository dealerSubscriptionRepository,
            DealerInvoiceRepository dealerInvoiceRepository,
            TaskNotificationService taskNotificationService,
            DealService dealService,
            InboxService inboxService
    ) {
        this.dealerService = dealerService;
        this.vehicleRepository = vehicleRepository;
        this.leadRepository = leadRepository;
        this.appointmentRepository = appointmentRepository;
        this.dealRepository = dealRepository;
        this.dealActivityRepository = dealActivityRepository;
        this.dealDocumentRepository = dealDocumentRepository;
        this.dealerSubscriptionRepository = dealerSubscriptionRepository;
        this.dealerInvoiceRepository = dealerInvoiceRepository;
        this.taskNotificationService = taskNotificationService;
        this.dealService = dealService;
        this.inboxService = inboxService;
    }

    public DealerPortal getDealerPortal(Long dealerId) {
        Dealer dealer = dealerService.getDealer(dealerId);
        String dealerName = dealer.getName();
        List<Vehicle> inventory = vehicleRepository.findByDealerIdOrderByIdDesc(dealerId);
        List<Lead> leads = leadRepository.findByVehicleDealerId(dealerId);
        List<Appointment> appointments = appointmentRepository.findByVehicleDealerId(dealerId);
        List<Deal> deals = dealRepository.findByVehicleDealerIdOrderByUpdatedAtDesc(dealerId);
        List<Long> dealIds = deals.stream().map(Deal::getId).toList();
        List<DealTask> tasks = taskNotificationService.getTasksForAssignee(ParticipantType.DEALER, String.valueOf(dealerId), null);
        List<Notification> notifications = taskNotificationService.getNotifications(ParticipantType.DEALER, String.valueOf(dealerId), null);
        InboxService.QueueSummary queueSummary = inboxService.getDealerQueue(dealerId).summary();

        PortalOverview overview = new PortalOverview(
                dealerId,
                dealerName,
                inventory.size(),
                countVehicles(inventory, VehicleStatus.LIVE),
                countVehicles(inventory, VehicleStatus.RESERVED),
                countVehicles(inventory, VehicleStatus.SOLD),
                inventory.stream().map(Vehicle::getPrice).reduce(BigDecimal.ZERO, BigDecimal::add),
                leads.size(),
                countLeads(leads, LeadStatus.NEW),
                countLeads(leads, LeadStatus.QUALIFIED),
                appointments.size(),
                countAppointments(appointments, AppointmentStatus.REQUESTED),
                deals.stream().filter(this::isActiveDeal).count(),
                deals.stream().filter(deal -> deal.getStage() == DealStage.COMPLETED).count(),
                deals.stream().map(Deal::getId).map(dealService::getDealReadiness).filter(DealService.DealReadiness::readyForHandoff).count(),
                tasks.stream().filter(task -> task.getStatus() == DealTaskStatus.OPEN || task.getStatus() == DealTaskStatus.IN_PROGRESS).count(),
                notifications.stream().filter(notification -> !notification.isRead()).count(),
                queueSummary.stalledCount()
        );

        PipelineSnapshot pipeline = new PipelineSnapshot(
                countsForStatuses(inventory.stream().map(Vehicle::getStatus).toList(), Arrays.asList(VehicleStatus.values())),
                countsForStatuses(leads.stream().map(Lead::getStatus).toList(), Arrays.asList(LeadStatus.values())),
                countsForStatuses(appointments.stream().map(Appointment::getStatus).toList(), Arrays.asList(AppointmentStatus.values())),
                countsForStatuses(deals.stream().map(Deal::getStage).toList(), Arrays.asList(DealStage.values()))
        );

        Map<Long, List<DealTask>> tasksByDeal = tasks.stream()
                .filter(task -> task.getStatus() == DealTaskStatus.OPEN || task.getStatus() == DealTaskStatus.IN_PROGRESS)
                .collect(Collectors.groupingBy(task -> task.getDeal().getId()));

        List<PortalDealItem> recentDeals = deals.stream()
                .limit(10)
                .map(deal -> {
                    DealService.DealReadiness readiness = dealService.getDealReadiness(deal.getId());
                    List<DealTask> dealTasks = tasksByDeal.getOrDefault(deal.getId(), List.of()).stream()
                            .sorted(Comparator.comparing(DealTask::getUpdatedAt).reversed())
                            .toList();
                    return new PortalDealItem(
                            deal.getId(),
                            deal.getVehicle().getId(),
                            deal.getVehicle().getModelYear() + " " + deal.getVehicle().getMake() + " " + deal.getVehicle().getModel(),
                            deal.getBuyerName(),
                            deal.getStage().name(),
                            deal.isDepositPaid(),
                            readiness.readyForHandoff(),
                            readiness.blockers(),
                            dealTasks.isEmpty() ? (readiness.blockers().isEmpty() ? "Monitor deal progress" : readiness.blockers().getFirst()) : dealTasks.getFirst().getTitle(),
                            deal.getUpdatedAt()
                    );
                })
                .toList();

        List<PortalActivityItem> recentActivity = dealIds.isEmpty()
                ? List.of()
                : dealActivityRepository.findByDealIdInOrderByCreatedAtDesc(dealIds).stream()
                .limit(12)
                .map(activity -> new PortalActivityItem(
                        activity.getDeal().getId(),
                        activity.getDeal().getVehicle().getModelYear() + " " + activity.getDeal().getVehicle().getMake() + " " + activity.getDeal().getVehicle().getModel(),
                        activity.getEventType(),
                        activity.getMessage(),
                        activity.getCreatedAt()
                ))
                .toList();

        return new DealerPortal(overview, pipeline, queueSummary, recentDeals, recentActivity);
    }

    public List<PortalDealItem> getDealerDeals(Long dealerId, DealStage stage) {
        dealerService.getDealer(dealerId);
        return dealRepository.findByVehicleDealerIdOrderByUpdatedAtDesc(dealerId).stream()
                .filter(deal -> stage == null || deal.getStage() == stage)
                .map(this::toPortalDealItem)
                .toList();
    }

    public List<PortalLeadItem> getDealerLeads(Long dealerId, LeadStatus status) {
        dealerService.getDealer(dealerId);
        return leadRepository.findByVehicleDealerId(dealerId).stream()
                .filter(lead -> status == null || lead.getStatus() == status)
                .sorted(Comparator.comparing(Lead::getCreatedAt).reversed())
                .map(lead -> new PortalLeadItem(
                        lead.getId(),
                        lead.getVehicle().getId(),
                        vehicleTitle(lead.getVehicle()),
                        lead.getBuyerName(),
                        lead.getBuyerEmail(),
                        lead.getStatus(),
                        lead.getMessage(),
                        lead.getCreatedAt()
                ))
                .toList();
    }

    public List<PortalAppointmentItem> getDealerAppointments(Long dealerId, AppointmentStatus status) {
        dealerService.getDealer(dealerId);
        return appointmentRepository.findByVehicleDealerId(dealerId).stream()
                .filter(appointment -> status == null || appointment.getStatus() == status)
                .sorted(Comparator.comparing(Appointment::getScheduledAt))
                .map(appointment -> new PortalAppointmentItem(
                        appointment.getId(),
                        appointment.getVehicle().getId(),
                        vehicleTitle(appointment.getVehicle()),
                        appointment.getBuyerName(),
                        appointment.getBuyerEmail(),
                        appointment.getType().name(),
                        appointment.getStatus(),
                        appointment.getScheduledAt(),
                        appointment.getCreatedAt()
                ))
                .toList();
    }

    public List<PortalDocumentItem> getDealerDocuments(Long dealerId, DocumentStatus status) {
        dealerService.getDealer(dealerId);
        List<DealDocument> documents = status == null
                ? dealDocumentRepository.findByDealVehicleDealerIdOrderByUpdatedAtDesc(dealerId)
                : dealDocumentRepository.findByDealVehicleDealerIdAndStatusOrderByUpdatedAtDesc(dealerId, status);
        return documents.stream()
                .map(document -> new PortalDocumentItem(
                        document.getId(),
                        document.getDeal().getId(),
                        vehicleTitle(document.getDeal().getVehicle()),
                        document.getDeal().getBuyerName(),
                        document.getType().name(),
                        document.getStatus(),
                        document.getFileName(),
                        document.getUpdatedAt()
                ))
                .toList();
    }

    public PortalSubscription getDealerSubscription(Long dealerId) {
        Dealer dealer = dealerService.getDealer(dealerId);
        return toPortalSubscription(ensureSubscription(dealer));
    }

    @Transactional
    public PortalSubscription updateDealerSubscription(
            Long dealerId,
            SubscriptionPlan plan,
            SubscriptionStatus status,
            Boolean autoRenew
    ) {
        Dealer dealer = dealerService.getDealer(dealerId);
        DealerSubscription subscription = ensureSubscription(dealer);
        subscription.setPlan(plan);
        subscription.setMonthlyPrice(priceForPlan(plan));
        subscription.setStatus(status);
        subscription.setAutoRenew(autoRenew);
        subscription.setUpdatedAt(OffsetDateTime.now());
        DealerSubscription savedSubscription = dealerSubscriptionRepository.save(subscription);

        if (status == SubscriptionStatus.ACTIVE && dealerInvoiceRepository.findByDealerIdOrderByCreatedAtDesc(dealerId).isEmpty()) {
            dealerInvoiceRepository.save(createInvoice(
                    dealer,
                    "INV-" + dealerId + "-0001",
                    InvoiceStatus.OPEN,
                    savedSubscription.getMonthlyPrice(),
                    savedSubscription.getCurrentPeriodStart(),
                    savedSubscription.getCurrentPeriodEnd(),
                    savedSubscription.getCurrentPeriodStart().plusDays(7),
                    null
            ));
        }

        return toPortalSubscription(savedSubscription);
    }

    public List<PortalInvoice> getDealerInvoices(Long dealerId) {
        Dealer dealer = dealerService.getDealer(dealerId);
        ensureSubscription(dealer);
        return dealerInvoiceRepository.findByDealerIdOrderByCreatedAtDesc(dealerId).stream()
                .map(invoice -> new PortalInvoice(
                        invoice.getId(),
                        invoice.getInvoiceNumber(),
                        invoice.getStatus(),
                        invoice.getAmount(),
                        invoice.getPeriodStart(),
                        invoice.getPeriodEnd(),
                        invoice.getDueAt(),
                        invoice.getPaidAt(),
                        invoice.getCreatedAt()
                ))
                .toList();
    }

    private long countVehicles(List<Vehicle> vehicles, VehicleStatus status) {
        return vehicles.stream().filter(vehicle -> vehicle.getStatus() == status).count();
    }

    private long countLeads(List<Lead> leads, LeadStatus status) {
        return leads.stream().filter(lead -> lead.getStatus() == status).count();
    }

    private long countAppointments(List<Appointment> appointments, AppointmentStatus status) {
        return appointments.stream().filter(appointment -> appointment.getStatus() == status).count();
    }

    private boolean isActiveDeal(Deal deal) {
        return deal.getStage() != DealStage.CANCELED && deal.getStage() != DealStage.COMPLETED;
    }

    private <T extends Enum<T>> List<MetricCount> countsForStatuses(List<T> values, List<T> orderedStatuses) {
        Map<T, Long> counts = values.stream().collect(Collectors.groupingBy(status -> status, Collectors.counting()));
        return orderedStatuses.stream()
                .map(status -> new MetricCount(status.name(), counts.getOrDefault(status, 0L)))
                .toList();
    }

    private PortalDealItem toPortalDealItem(Deal deal) {
        DealService.DealReadiness readiness = dealService.getDealReadiness(deal.getId());
        List<DealTask> dealerTasks = taskNotificationService.getTasksForAssignee(
                ParticipantType.DEALER,
                String.valueOf(deal.getVehicle().getDealer().getId()),
                null
        ).stream()
                .filter(task -> task.getDeal().getId().equals(deal.getId()))
                .filter(task -> task.getStatus() == DealTaskStatus.OPEN || task.getStatus() == DealTaskStatus.IN_PROGRESS)
                .sorted(Comparator.comparing(DealTask::getUpdatedAt).reversed())
                .toList();
        return new PortalDealItem(
                deal.getId(),
                deal.getVehicle().getId(),
                vehicleTitle(deal.getVehicle()),
                deal.getBuyerName(),
                deal.getStage().name(),
                deal.isDepositPaid(),
                readiness.readyForHandoff(),
                readiness.blockers(),
                dealerTasks.isEmpty() ? (readiness.blockers().isEmpty() ? "Monitor deal progress" : readiness.blockers().getFirst()) : dealerTasks.getFirst().getTitle(),
                deal.getUpdatedAt()
        );
    }

    private String vehicleTitle(Vehicle vehicle) {
        return vehicle.getModelYear() + " " + vehicle.getMake() + " " + vehicle.getModel();
    }

    private PortalSubscription toPortalSubscription(DealerSubscription subscription) {
        return new PortalSubscription(
                subscription.getId(),
                subscription.getDealer().getId(),
                subscription.getPlan(),
                subscription.getStatus(),
                subscription.getMonthlyPrice(),
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd(),
                subscription.isAutoRenew(),
                subscription.getUpdatedAt()
        );
    }

    private DealerSubscription ensureSubscription(Dealer dealer) {
        return dealerSubscriptionRepository.findByDealerId(dealer.getId())
                .orElseGet(() -> dealerSubscriptionRepository.save(createDefaultSubscription(dealer)));
    }

    private DealerSubscription createDefaultSubscription(Dealer dealer) {
        DealerSubscription subscription = new DealerSubscription();
        OffsetDateTime now = OffsetDateTime.now();
        subscription.setDealer(dealer);
        subscription.setPlan(SubscriptionPlan.GROWTH);
        subscription.setStatus(SubscriptionStatus.TRIALING);
        subscription.setMonthlyPrice(priceForPlan(SubscriptionPlan.GROWTH));
        subscription.setCurrentPeriodStart(now.minusDays(7));
        subscription.setCurrentPeriodEnd(now.plusDays(23));
        subscription.setAutoRenew(true);
        subscription.setCreatedAt(now);
        subscription.setUpdatedAt(now);
        return subscription;
    }

    private DealerInvoice createInvoice(
            Dealer dealer,
            String invoiceNumber,
            InvoiceStatus status,
            BigDecimal amount,
            OffsetDateTime periodStart,
            OffsetDateTime periodEnd,
            OffsetDateTime dueAt,
            OffsetDateTime paidAt
    ) {
        DealerInvoice invoice = new DealerInvoice();
        invoice.setDealer(dealer);
        invoice.setInvoiceNumber(invoiceNumber);
        invoice.setStatus(status);
        invoice.setAmount(amount);
        invoice.setPeriodStart(periodStart);
        invoice.setPeriodEnd(periodEnd);
        invoice.setDueAt(dueAt);
        invoice.setPaidAt(paidAt);
        invoice.setCreatedAt(periodStart);
        return invoice;
    }

    private BigDecimal priceForPlan(SubscriptionPlan plan) {
        return switch (plan) {
            case STARTER -> new BigDecimal("699.00");
            case GROWTH -> new BigDecimal("1100.00");
            case PERFORMANCE -> new BigDecimal("1499.00");
        };
    }
}
