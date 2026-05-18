package com.stealadeal.service.buyer;

import com.stealadeal.domain.ParticipantType;
import com.stealadeal.domain.PriceDropAlertLog;
import com.stealadeal.domain.SavedSearch;
import com.stealadeal.domain.UserAccount;
import com.stealadeal.domain.Vehicle;
import com.stealadeal.repository.FavoriteRepository;
import com.stealadeal.repository.PriceDropAlertLogRepository;
import com.stealadeal.repository.SavedSearchRepository;
import com.stealadeal.repository.UserAccountRepository;
import com.stealadeal.service.TaskNotificationService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Producer: when a LIVE vehicle's price drops, notify every BUYER who
 * favorited it or has an active price-drop saved search matching it.
 * Debounced to at most one notification per (buyer, vehicle) per 24h
 * and idempotent. Never throws back into the price update.
 */
@Component("priceDropWatcher")
public class BuyerPriceDropWatcher implements PriceDropWatcher {

    private static final Logger log = LoggerFactory.getLogger(BuyerPriceDropWatcher.class);
    private static final long DEBOUNCE_HOURS = 24;

    private final FavoriteRepository favoriteRepository;
    private final SavedSearchRepository savedSearchRepository;
    private final PriceDropAlertLogRepository alertLogRepository;
    private final UserAccountRepository userAccountRepository;
    private final VehicleSearchMatcher matcher;
    private final SavedSearchService savedSearchService;
    private final TaskNotificationService taskNotificationService;

    public BuyerPriceDropWatcher(FavoriteRepository favoriteRepository,
                                 SavedSearchRepository savedSearchRepository,
                                 PriceDropAlertLogRepository alertLogRepository,
                                 UserAccountRepository userAccountRepository,
                                 VehicleSearchMatcher matcher,
                                 SavedSearchService savedSearchService,
                                 TaskNotificationService taskNotificationService) {
        this.favoriteRepository = favoriteRepository;
        this.savedSearchRepository = savedSearchRepository;
        this.alertLogRepository = alertLogRepository;
        this.userAccountRepository = userAccountRepository;
        this.matcher = matcher;
        this.savedSearchService = savedSearchService;
        this.taskNotificationService = taskNotificationService;
    }

    @Override
    @Transactional
    public void onPriceDrop(Vehicle vehicle, BigDecimal previousPrice, BigDecimal newPrice) {
        try {
            Set<Long> watcherUserIds = new LinkedHashSet<>();
            favoriteRepository.findByVehicleId(vehicle.getId())
                    .forEach(f -> watcherUserIds.add(f.getUserId()));
            for (SavedSearch s : savedSearchRepository.findByAlertOnPriceDropTrue()) {
                if (matcher.matches(vehicle, savedSearchService.toQuery(s))) {
                    watcherUserIds.add(s.getUserId());
                }
            }
            if (watcherUserIds.isEmpty()) {
                return;
            }
            String title = "Price drop on a car you're watching";
            String message = vehicle.getModelYear() + " " + vehicle.getMake() + " " + vehicle.getModel()
                    + " dropped from " + money(previousPrice) + " to " + money(newPrice);
            OffsetDateTime now = OffsetDateTime.now();
            OffsetDateTime cutoff = now.minusHours(DEBOUNCE_HOURS);

            for (Long userId : watcherUserIds) {
                PriceDropAlertLog logRow = alertLogRepository
                        .findByUserIdAndVehicleId(userId, vehicle.getId()).orElse(null);
                if (logRow != null && logRow.getLastNotifiedAt().isAfter(cutoff)) {
                    continue; // debounced within 24h
                }
                UserAccount buyer = userAccountRepository.findById(userId).orElse(null);
                if (buyer == null) {
                    continue;
                }
                taskNotificationService.createNotification(
                        null, ParticipantType.BUYER, buyer.getEmail(), title, message);
                if (logRow == null) {
                    logRow = new PriceDropAlertLog();
                    logRow.setUserId(userId);
                    logRow.setVehicleId(vehicle.getId());
                }
                logRow.setLastNotifiedAt(now);
                alertLogRepository.save(logRow);
            }
        } catch (RuntimeException e) {
            // Must never break the price update that triggered us.
            log.warn("[price-drop] producer failed for vehicle {}: {}", vehicle.getId(), e.getMessage());
        }
    }

    private String money(BigDecimal v) {
        return "$" + (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }
}
