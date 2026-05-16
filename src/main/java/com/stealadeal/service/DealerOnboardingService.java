package com.stealadeal.service;

import com.stealadeal.config.OnboardingProperties;
import com.stealadeal.domain.Deal;
import com.stealadeal.domain.DealStage;
import com.stealadeal.domain.Dealer;
import com.stealadeal.domain.DealerOnboarding;
import com.stealadeal.domain.DealerSubscription;
import com.stealadeal.domain.OnboardingStage;
import com.stealadeal.domain.ParticipantType;
import com.stealadeal.domain.SubscriptionStatus;
import com.stealadeal.domain.UserRole;
import com.stealadeal.domain.VehicleStatus;
import com.stealadeal.repository.DealRepository;
import com.stealadeal.repository.DealerOnboardingRepository;
import com.stealadeal.repository.DealerRepository;
import com.stealadeal.repository.DealerSubscriptionRepository;
import com.stealadeal.repository.LeadRepository;
import com.stealadeal.repository.UserAccountRepository;
import com.stealadeal.repository.VehicleRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Automates dealer onboarding. Milestones are derived from observable
 * system state (never set manually), the furthest reached milestone is
 * the current stage, and a dealer stuck on an actionable step beyond a
 * staleness threshold is auto-nudged through the notification outbox.
 */
@Service
@Transactional
public class DealerOnboardingService {

    private static final Logger log = LoggerFactory.getLogger(DealerOnboardingService.class);

    public record OnboardingView(
            Long dealerId,
            OnboardingStage stage,
            int percentComplete,
            boolean complete,
            OnboardingStage nextActionStage,
            String nextAction,
            OffsetDateTime currentStageSince,
            int nudgeCount,
            OffsetDateTime registeredAt,
            OffsetDateTime approvedAt,
            OffsetDateTime userCreatedAt,
            OffsetDateTime subscriptionActiveAt,
            OffsetDateTime inventoryLiveAt,
            OffsetDateTime firstLeadAt,
            OffsetDateTime firstDealAt,
            OffsetDateTime activatedAt
    ) {
    }

    private final DealerRepository dealerRepository;
    private final DealerOnboardingRepository onboardingRepository;
    private final UserAccountRepository userAccountRepository;
    private final DealerSubscriptionRepository dealerSubscriptionRepository;
    private final VehicleRepository vehicleRepository;
    private final LeadRepository leadRepository;
    private final DealRepository dealRepository;
    private final TaskNotificationService taskNotificationService;
    private final AuditService auditService;
    private final OnboardingProperties properties;

    public DealerOnboardingService(
            DealerRepository dealerRepository,
            DealerOnboardingRepository onboardingRepository,
            UserAccountRepository userAccountRepository,
            DealerSubscriptionRepository dealerSubscriptionRepository,
            VehicleRepository vehicleRepository,
            LeadRepository leadRepository,
            DealRepository dealRepository,
            TaskNotificationService taskNotificationService,
            AuditService auditService,
            OnboardingProperties properties
    ) {
        this.dealerRepository = dealerRepository;
        this.onboardingRepository = onboardingRepository;
        this.userAccountRepository = userAccountRepository;
        this.dealerSubscriptionRepository = dealerSubscriptionRepository;
        this.vehicleRepository = vehicleRepository;
        this.leadRepository = leadRepository;
        this.dealRepository = dealRepository;
        this.taskNotificationService = taskNotificationService;
        this.auditService = auditService;
        this.properties = properties;
    }

    /**
     * Read-side entry point: re-evaluates from live state (auto-creating
     * the tracker on first call) so the dealer always sees current
     * truth. Nudge cadence is time-gated, so frequent reads do not spam.
     */
    public OnboardingView getOrEvaluate(Long dealerId) {
        Dealer dealer = dealerRepository.findById(dealerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dealer not found"));
        return toView(evaluate(dealer));
    }

    /**
     * Recompute onboarding for one dealer from live state, advance the
     * stage if it changed, and nudge if the dealer is stuck. Idempotent.
     */
    public DealerOnboarding evaluate(Dealer dealer) {
        OffsetDateTime now = OffsetDateTime.now();
        DealerOnboarding onboarding = onboardingRepository.findByDealerId(dealer.getId())
                .orElseGet(() -> {
                    DealerOnboarding created = new DealerOnboarding();
                    created.setDealer(dealer);
                    created.setStage(OnboardingStage.REGISTERED);
                    created.setCurrentStageSince(now);
                    created.setRegisteredAt(now);
                    created.setNudgeCount(0);
                    created.setCreatedAt(now);
                    created.setUpdatedAt(now);
                    return onboardingRepository.save(created);
                });

        if (onboarding.getRegisteredAt() == null) {
            onboarding.setRegisteredAt(now);
        }

        boolean approved = dealer.isApproved();
        boolean userCreated = userAccountRepository.existsByDealerIdAndRole(dealer.getId(), UserRole.DEALER);
        boolean subscriptionActive = dealerSubscriptionRepository.findByDealerId(dealer.getId())
                .map(DealerSubscription::getStatus)
                .filter(status -> status == SubscriptionStatus.ACTIVE)
                .isPresent();
        boolean inventoryLive = vehicleRepository.existsByDealerIdAndStatus(dealer.getId(), VehicleStatus.LIVE);
        boolean firstLead = !leadRepository.findByVehicleDealerId(dealer.getId()).isEmpty();
        List<Deal> deals = dealRepository.findByVehicleDealerIdOrderByUpdatedAtDesc(dealer.getId());
        boolean firstDeal = !deals.isEmpty();
        boolean activated = deals.stream().anyMatch(d -> d.getStage() == DealStage.COMPLETED);

        stamp(onboarding::getApprovedAt, onboarding::setApprovedAt, approved, now);
        stamp(onboarding::getUserCreatedAt, onboarding::setUserCreatedAt, userCreated, now);
        stamp(onboarding::getSubscriptionActiveAt, onboarding::setSubscriptionActiveAt, subscriptionActive, now);
        stamp(onboarding::getInventoryLiveAt, onboarding::setInventoryLiveAt, inventoryLive, now);
        stamp(onboarding::getFirstLeadAt, onboarding::setFirstLeadAt, firstLead, now);
        stamp(onboarding::getFirstDealAt, onboarding::setFirstDealAt, firstDeal, now);
        stamp(onboarding::getActivatedAt, onboarding::setActivatedAt, activated, now);

        OnboardingStage furthest = OnboardingStage.REGISTERED;
        if (approved) {
            furthest = OnboardingStage.APPROVED;
        }
        if (userCreated) {
            furthest = OnboardingStage.USER_CREATED;
        }
        if (subscriptionActive) {
            furthest = OnboardingStage.SUBSCRIPTION_ACTIVE;
        }
        if (inventoryLive) {
            furthest = OnboardingStage.INVENTORY_LIVE;
        }
        if (firstLead) {
            furthest = OnboardingStage.FIRST_LEAD;
        }
        if (firstDeal) {
            furthest = OnboardingStage.FIRST_DEAL;
        }
        if (activated) {
            furthest = OnboardingStage.ACTIVATED;
        }

        if (furthest != onboarding.getStage()) {
            OnboardingStage previous = onboarding.getStage();
            onboarding.setStage(furthest);
            onboarding.setCurrentStageSince(now);
            auditService.record("ONBOARDING_STAGE_ADVANCED", "Dealer", dealer.getId(), null,
                    previous + " -> " + furthest);
        }

        OnboardingStage blocking = blockingStage(approved, userCreated, subscriptionActive, inventoryLive,
                firstDeal, activated);
        maybeNudge(onboarding, dealer, blocking, now);

        onboarding.setUpdatedAt(now);
        return onboardingRepository.save(onboarding);
    }

    private void maybeNudge(DealerOnboarding onboarding, Dealer dealer, OnboardingStage blocking, OffsetDateTime now) {
        if (blocking == null || !isDealerActionable(blocking)) {
            return;
        }
        if (onboarding.getLastNudgedStage() != blocking) {
            onboarding.setNudgeCount(0);
        }
        Duration stale = Duration.ofHours(properties.staleHours());
        boolean stuckLongEnough = onboarding.getCurrentStageSince() == null
                || Duration.between(onboarding.getCurrentStageSince(), now).compareTo(stale) >= 0;
        boolean nudgedRecently = onboarding.getLastNudgedAt() != null
                && Duration.between(onboarding.getLastNudgedAt(), now).compareTo(stale) < 0;
        if (!stuckLongEnough || nudgedRecently) {
            return;
        }
        if (onboarding.getNudgeCount() >= properties.maxNudgesPerStage()) {
            return;
        }
        taskNotificationService.createNotification(
                null,
                ParticipantType.DEALER,
                String.valueOf(dealer.getId()),
                "Finish setting up your StealADeal portal",
                nextActionText(blocking)
        );
        onboarding.setLastNudgedStage(blocking);
        onboarding.setLastNudgedAt(now);
        onboarding.setNudgeCount(onboarding.getNudgeCount() + 1);
        auditService.record("ONBOARDING_NUDGE_SENT", "Dealer", dealer.getId(), null,
                blocking + " nudge #" + onboarding.getNudgeCount());
        log.info("[onboarding] nudged dealer {} for stage {} (#{})",
                dealer.getId(), blocking, onboarding.getNudgeCount());
    }

    private OnboardingStage blockingStage(
            boolean approved,
            boolean userCreated,
            boolean subscriptionActive,
            boolean inventoryLive,
            boolean firstDeal,
            boolean activated
    ) {
        if (!approved) {
            return OnboardingStage.APPROVED;
        }
        if (!userCreated) {
            return OnboardingStage.USER_CREATED;
        }
        if (!subscriptionActive) {
            return OnboardingStage.SUBSCRIPTION_ACTIVE;
        }
        if (!inventoryLive) {
            return OnboardingStage.INVENTORY_LIVE;
        }
        if (!firstDeal) {
            return OnboardingStage.FIRST_DEAL;
        }
        if (!activated) {
            return OnboardingStage.ACTIVATED;
        }
        return null;
    }

    private boolean isDealerActionable(OnboardingStage blocking) {
        return blocking == OnboardingStage.USER_CREATED
                || blocking == OnboardingStage.SUBSCRIPTION_ACTIVE
                || blocking == OnboardingStage.INVENTORY_LIVE
                || blocking == OnboardingStage.FIRST_DEAL;
    }

    private String nextActionText(OnboardingStage blocking) {
        if (blocking == null) {
            return "Onboarding complete — you have closed your first deal.";
        }
        return switch (blocking) {
            case APPROVED -> "Your dealership is awaiting StealADeal approval. "
                    + "Ensure your dealer license is on file.";
            case USER_CREATED -> "Create your dealer login (POST /api/auth/register with role=DEALER) "
                    + "to access the portal.";
            case SUBSCRIPTION_ACTIVE -> "Activate your subscription plan to start billing and unlock "
                    + "the full portal and deal room.";
            case INVENTORY_LIVE -> "Upload your inventory so buyers can find your vehicles "
                    + "(bulk CSV upload is supported).";
            case FIRST_DEAL -> "Work your first lead into a deal from the deal queue to start "
                    + "capturing transactions.";
            case ACTIVATED -> "Close your first deal end-to-end (deposit, documents, handoff) "
                    + "to complete onboarding.";
            default -> "Continue onboarding from your portal.";
        };
    }

    private OnboardingView toView(DealerOnboarding o) {
        OnboardingStage blocking = blockingStage(
                o.getApprovedAt() != null,
                o.getUserCreatedAt() != null,
                o.getSubscriptionActiveAt() != null,
                o.getInventoryLiveAt() != null,
                o.getFirstDealAt() != null,
                o.getActivatedAt() != null
        );
        boolean complete = blocking == null;
        int total = OnboardingStage.ACTIVATED.ordinal();
        int percent = Math.min(100, Math.round((o.getStage().ordinal() * 100f) / total));
        return new OnboardingView(
                o.getDealer().getId(),
                o.getStage(),
                percent,
                complete,
                blocking,
                nextActionText(blocking),
                o.getCurrentStageSince(),
                o.getNudgeCount(),
                o.getRegisteredAt(),
                o.getApprovedAt(),
                o.getUserCreatedAt(),
                o.getSubscriptionActiveAt(),
                o.getInventoryLiveAt(),
                o.getFirstLeadAt(),
                o.getFirstDealAt(),
                o.getActivatedAt()
        );
    }

    private void stamp(
            java.util.function.Supplier<OffsetDateTime> getter,
            java.util.function.Consumer<OffsetDateTime> setter,
            boolean reached,
            OffsetDateTime now
    ) {
        if (reached && getter.get() == null) {
            setter.accept(now);
        }
    }
}
