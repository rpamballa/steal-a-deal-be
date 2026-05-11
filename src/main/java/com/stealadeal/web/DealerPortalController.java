package com.stealadeal.web;

import com.stealadeal.service.DealerPortalService;
import com.stealadeal.service.InboxService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DealerPortalController {

    private final DealerPortalService dealerPortalService;

    public DealerPortalController(DealerPortalService dealerPortalService) {
        this.dealerPortalService = dealerPortalService;
    }

    @GetMapping("/dealers/{dealerId}/portal")
    @PreAuthorize("@accessControl.canAccessDealer(authentication, #dealerId)")
    public DealerPortalResponse getDealerPortal(@PathVariable Long dealerId) {
        return DealerPortalResponse.from(dealerPortalService.getDealerPortal(dealerId));
    }

    @GetMapping("/dealers/{dealerId}/portal/deals")
    @PreAuthorize("@accessControl.canAccessDealer(authentication, #dealerId)")
    public List<PortalDealItemResponse> getDealerDeals(
            @PathVariable Long dealerId,
            @RequestParam(required = false) com.stealadeal.domain.DealStage stage
    ) {
        return dealerPortalService.getDealerDeals(dealerId, stage).stream().map(PortalDealItemResponse::from).toList();
    }

    @GetMapping("/dealers/{dealerId}/portal/leads")
    @PreAuthorize("@accessControl.canAccessDealer(authentication, #dealerId)")
    public List<PortalLeadItemResponse> getDealerLeads(
            @PathVariable Long dealerId,
            @RequestParam(required = false) com.stealadeal.domain.LeadStatus status
    ) {
        return dealerPortalService.getDealerLeads(dealerId, status).stream().map(PortalLeadItemResponse::from).toList();
    }

    @GetMapping("/dealers/{dealerId}/portal/appointments")
    @PreAuthorize("@accessControl.canAccessDealer(authentication, #dealerId)")
    public List<PortalAppointmentItemResponse> getDealerAppointments(
            @PathVariable Long dealerId,
            @RequestParam(required = false) com.stealadeal.domain.AppointmentStatus status
    ) {
        return dealerPortalService.getDealerAppointments(dealerId, status).stream().map(PortalAppointmentItemResponse::from).toList();
    }

    @GetMapping("/dealers/{dealerId}/portal/documents")
    @PreAuthorize("@accessControl.canAccessDealer(authentication, #dealerId)")
    public List<PortalDocumentItemResponse> getDealerDocuments(
            @PathVariable Long dealerId,
            @RequestParam(required = false) com.stealadeal.domain.DocumentStatus status
    ) {
        return dealerPortalService.getDealerDocuments(dealerId, status).stream().map(PortalDocumentItemResponse::from).toList();
    }

    @GetMapping("/dealers/{dealerId}/portal/subscription")
    @PreAuthorize("@accessControl.canAccessDealer(authentication, #dealerId)")
    public PortalSubscriptionResponse getDealerSubscription(@PathVariable Long dealerId) {
        return PortalSubscriptionResponse.from(dealerPortalService.getDealerSubscription(dealerId));
    }

    @PatchMapping("/dealers/{dealerId}/portal/subscription")
    @PreAuthorize("@accessControl.canAccessDealer(authentication, #dealerId)")
    public PortalSubscriptionResponse updateDealerSubscription(
            @PathVariable Long dealerId,
            @Valid @RequestBody UpdateSubscriptionRequest request
    ) {
        return PortalSubscriptionResponse.from(dealerPortalService.updateDealerSubscription(
                dealerId,
                request.plan(),
                request.status(),
                request.autoRenew()
        ));
    }

    @GetMapping("/dealers/{dealerId}/portal/invoices")
    @PreAuthorize("@accessControl.canAccessDealer(authentication, #dealerId)")
    public List<PortalInvoiceResponse> getDealerInvoices(@PathVariable Long dealerId) {
        return dealerPortalService.getDealerInvoices(dealerId).stream().map(PortalInvoiceResponse::from).toList();
    }

    public record DealerPortalResponse(
            PortalOverviewResponse overview,
            PipelineSnapshotResponse pipeline,
            QueueSummaryResponse queueSummary,
            List<PortalDealItemResponse> recentDeals,
            List<PortalActivityItemResponse> recentActivity
    ) {

        static DealerPortalResponse from(DealerPortalService.DealerPortal portal) {
            return new DealerPortalResponse(
                    PortalOverviewResponse.from(portal.overview()),
                    PipelineSnapshotResponse.from(portal.pipeline()),
                    QueueSummaryResponse.from(portal.queueSummary()),
                    portal.recentDeals().stream().map(PortalDealItemResponse::from).toList(),
                    portal.recentActivity().stream().map(PortalActivityItemResponse::from).toList()
            );
        }
    }

    public record PortalOverviewResponse(
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

        static PortalOverviewResponse from(DealerPortalService.PortalOverview overview) {
            return new PortalOverviewResponse(
                    overview.dealerId(),
                    overview.dealerName(),
                    overview.totalInventoryCount(),
                    overview.liveInventoryCount(),
                    overview.reservedInventoryCount(),
                    overview.soldInventoryCount(),
                    overview.totalInventoryValue(),
                    overview.leadCount(),
                    overview.newLeadCount(),
                    overview.qualifiedLeadCount(),
                    overview.appointmentCount(),
                    overview.requestedAppointmentCount(),
                    overview.activeDealCount(),
                    overview.completedDealCount(),
                    overview.readyForHandoffCount(),
                    overview.openTaskCount(),
                    overview.unreadNotificationCount(),
                    overview.stalledDealCount()
            );
        }
    }

    public record MetricCountResponse(String code, long count) {

        static MetricCountResponse from(DealerPortalService.MetricCount metricCount) {
            return new MetricCountResponse(metricCount.code(), metricCount.count());
        }
    }

    public record PipelineSnapshotResponse(
            List<MetricCountResponse> inventoryStatusCounts,
            List<MetricCountResponse> leadStatusCounts,
            List<MetricCountResponse> appointmentStatusCounts,
            List<MetricCountResponse> dealStageCounts
    ) {

        static PipelineSnapshotResponse from(DealerPortalService.PipelineSnapshot pipeline) {
            return new PipelineSnapshotResponse(
                    pipeline.inventoryStatusCounts().stream().map(MetricCountResponse::from).toList(),
                    pipeline.leadStatusCounts().stream().map(MetricCountResponse::from).toList(),
                    pipeline.appointmentStatusCounts().stream().map(MetricCountResponse::from).toList(),
                    pipeline.dealStageCounts().stream().map(MetricCountResponse::from).toList()
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

    public record PortalDealItemResponse(
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

        static PortalDealItemResponse from(DealerPortalService.PortalDealItem deal) {
            return new PortalDealItemResponse(
                    deal.dealId(),
                    deal.vehicleId(),
                    deal.vehicleTitle(),
                    deal.buyerName(),
                    deal.stage(),
                    deal.depositPaid(),
                    deal.readyForHandoff(),
                    deal.blockers(),
                    deal.nextAction(),
                    deal.updatedAt()
            );
        }
    }

    public record PortalActivityItemResponse(
            Long dealId,
            String vehicleTitle,
            String eventType,
            String message,
            OffsetDateTime createdAt
    ) {

        static PortalActivityItemResponse from(DealerPortalService.PortalActivityItem activity) {
            return new PortalActivityItemResponse(
                    activity.dealId(),
                    activity.vehicleTitle(),
                    activity.eventType(),
                    activity.message(),
                    activity.createdAt()
            );
        }
    }

    public record PortalLeadItemResponse(
            Long id,
            Long vehicleId,
            String vehicleTitle,
            String buyerName,
            String buyerEmail,
            String status,
            String message,
            OffsetDateTime createdAt
    ) {

        static PortalLeadItemResponse from(DealerPortalService.PortalLeadItem lead) {
            return new PortalLeadItemResponse(
                    lead.id(),
                    lead.vehicleId(),
                    lead.vehicleTitle(),
                    lead.buyerName(),
                    lead.buyerEmail(),
                    lead.status().name(),
                    lead.message(),
                    lead.createdAt()
            );
        }
    }

    public record PortalAppointmentItemResponse(
            Long id,
            Long vehicleId,
            String vehicleTitle,
            String buyerName,
            String buyerEmail,
            String type,
            String status,
            OffsetDateTime scheduledAt,
            OffsetDateTime createdAt
    ) {

        static PortalAppointmentItemResponse from(DealerPortalService.PortalAppointmentItem appointment) {
            return new PortalAppointmentItemResponse(
                    appointment.id(),
                    appointment.vehicleId(),
                    appointment.vehicleTitle(),
                    appointment.buyerName(),
                    appointment.buyerEmail(),
                    appointment.type(),
                    appointment.status().name(),
                    appointment.scheduledAt(),
                    appointment.createdAt()
            );
        }
    }

    public record PortalDocumentItemResponse(
            Long id,
            Long dealId,
            String vehicleTitle,
            String buyerName,
            String type,
            String status,
            String fileName,
            OffsetDateTime updatedAt
    ) {

        static PortalDocumentItemResponse from(DealerPortalService.PortalDocumentItem document) {
            return new PortalDocumentItemResponse(
                    document.id(),
                    document.dealId(),
                    document.vehicleTitle(),
                    document.buyerName(),
                    document.type(),
                    document.status().name(),
                    document.fileName(),
                    document.updatedAt()
            );
        }
    }

    public record PortalSubscriptionResponse(
            Long id,
            Long dealerId,
            String plan,
            String status,
            BigDecimal monthlyPrice,
            OffsetDateTime currentPeriodStart,
            OffsetDateTime currentPeriodEnd,
            boolean autoRenew,
            OffsetDateTime updatedAt
    ) {

        static PortalSubscriptionResponse from(DealerPortalService.PortalSubscription subscription) {
            return new PortalSubscriptionResponse(
                    subscription.id(),
                    subscription.dealerId(),
                    subscription.plan().name(),
                    subscription.status().name(),
                    subscription.monthlyPrice(),
                    subscription.currentPeriodStart(),
                    subscription.currentPeriodEnd(),
                    subscription.autoRenew(),
                    subscription.updatedAt()
            );
        }
    }

    public record PortalInvoiceResponse(
            Long id,
            String invoiceNumber,
            String status,
            BigDecimal amount,
            OffsetDateTime periodStart,
            OffsetDateTime periodEnd,
            OffsetDateTime dueAt,
            OffsetDateTime paidAt,
            OffsetDateTime createdAt
    ) {

        static PortalInvoiceResponse from(DealerPortalService.PortalInvoice invoice) {
            return new PortalInvoiceResponse(
                    invoice.id(),
                    invoice.invoiceNumber(),
                    invoice.status().name(),
                    invoice.amount(),
                    invoice.periodStart(),
                    invoice.periodEnd(),
                    invoice.dueAt(),
                    invoice.paidAt(),
                    invoice.createdAt()
            );
        }
    }

    public record UpdateSubscriptionRequest(
            @NotNull com.stealadeal.domain.SubscriptionPlan plan,
            @NotNull com.stealadeal.domain.SubscriptionStatus status,
            @NotNull Boolean autoRenew
    ) {
    }
}
