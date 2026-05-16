package com.stealadeal.service;

import com.stealadeal.config.InventoryReaperProperties;
import com.stealadeal.domain.DealStage;
import com.stealadeal.domain.Vehicle;
import com.stealadeal.domain.VehicleStatus;
import com.stealadeal.repository.DealRepository;
import com.stealadeal.repository.VehicleRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Delists inventory that has gone stale: a LIVE vehicle untouched
 * (not re-seen in any feed, not edited) past the staleness window is
 * moved to DRAFT so it drops out of the buyer catalog. Vehicles tied
 * to an in-progress deal are never reaped.
 */
@Component
public class StaleInventoryReaper {

    private static final Logger log = LoggerFactory.getLogger(StaleInventoryReaper.class);

    private final VehicleRepository vehicleRepository;
    private final DealRepository dealRepository;
    private final AuditService auditService;
    private final InventoryReaperProperties.InventoryReaper properties;

    public StaleInventoryReaper(
            VehicleRepository vehicleRepository,
            DealRepository dealRepository,
            AuditService auditService,
            InventoryReaperProperties.InventoryReaper properties
    ) {
        this.vehicleRepository = vehicleRepository;
        this.dealRepository = dealRepository;
        this.auditService = auditService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${app.inventory.reaper.poll-ms:86400000}")
    @Transactional
    public int runOnce() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(properties.staleDays());
        List<Vehicle> stale = vehicleRepository.findByStatusAndLastSeenAtBefore(VehicleStatus.LIVE, cutoff);
        int delisted = 0;
        for (Vehicle vehicle : stale) {
            if (hasActiveDeal(vehicle.getId())) {
                continue;
            }
            vehicle.setStatus(VehicleStatus.DRAFT);
            vehicleRepository.save(vehicle);
            auditService.record("INVENTORY_DELISTED_STALE", "Vehicle", vehicle.getId(), null,
                    "VIN " + vehicle.getVin() + " delisted; last seen " + vehicle.getLastSeenAt());
            delisted++;
        }
        if (delisted > 0) {
            log.info("[inventory-reaper] delisted {} stale vehicles (cutoff {})", delisted, cutoff);
        }
        return delisted;
    }

    private boolean hasActiveDeal(Long vehicleId) {
        return dealRepository.findByVehicleId(vehicleId).stream()
                .anyMatch(deal -> deal.getStage() != DealStage.COMPLETED
                        && deal.getStage() != DealStage.CANCELED);
    }
}
