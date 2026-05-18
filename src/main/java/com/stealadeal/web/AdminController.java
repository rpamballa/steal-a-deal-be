package com.stealadeal.web;

import com.stealadeal.domain.Dealer;
import com.stealadeal.domain.ParticipantType;
import com.stealadeal.service.AuditService;
import com.stealadeal.service.DealerService;
import com.stealadeal.service.TaskNotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin dealer-approval queue actions (§6). The legacy
 * PATCH /api/dealers/{id}/approval is retained for backward
 * compatibility; these are the spec-named endpoints with a rejection
 * reason + dealer notification.
 */
@RestController
@RequestMapping("/api/admin")
@Validated
public class AdminController {

    private final DealerService dealerService;
    private final AuditService auditService;
    private final TaskNotificationService taskNotificationService;

    public AdminController(
            DealerService dealerService,
            AuditService auditService,
            TaskNotificationService taskNotificationService
    ) {
        this.dealerService = dealerService;
        this.auditService = auditService;
        this.taskNotificationService = taskNotificationService;
    }

    @PostMapping("/dealers/{dealerId}/approve")
    @PreAuthorize("@accessControl.isAdmin(authentication)")
    public DealerDecisionResponse approve(@PathVariable Long dealerId) {
        Dealer dealer = dealerService.updateDealerApproval(dealerId, true);
        auditService.record("DEALER_APPROVED", "Dealer", dealerId, null, "Approved by admin");
        taskNotificationService.createNotification(
                null, ParticipantType.DEALER, String.valueOf(dealerId),
                "Dealership approved",
                "Your dealership has been approved. You can now publish inventory.");
        return DealerDecisionResponse.from(dealer);
    }

    @PostMapping("/dealers/{dealerId}/reject")
    @PreAuthorize("@accessControl.isAdmin(authentication)")
    public DealerDecisionResponse reject(
            @PathVariable Long dealerId,
            @Valid @RequestBody RejectDealerRequest request
    ) {
        Dealer dealer = dealerService.updateDealerApproval(dealerId, false);
        auditService.record("DEALER_REJECTED", "Dealer", dealerId, null,
                "Rejected by admin: " + request.reason());
        taskNotificationService.createNotification(
                null, ParticipantType.DEALER, String.valueOf(dealerId),
                "Dealership application rejected",
                "Your dealership application was not approved. Reason: " + request.reason());
        return DealerDecisionResponse.from(dealer);
    }

    public record RejectDealerRequest(@NotBlank String reason) {
    }

    public record DealerDecisionResponse(Long id, String name, boolean approved) {

        static DealerDecisionResponse from(Dealer dealer) {
            return new DealerDecisionResponse(dealer.getId(), dealer.getName(), dealer.isApproved());
        }
    }
}
