package com.stealadeal.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically syncs every enabled dealer inventory feed. Per-feed
 * failures are isolated by {@link InventoryFeedService#runAllEnabled()}.
 */
@Component
public class InventoryFeedProcessor {

    private static final Logger log = LoggerFactory.getLogger(InventoryFeedProcessor.class);

    private final InventoryFeedService inventoryFeedService;

    public InventoryFeedProcessor(InventoryFeedService inventoryFeedService) {
        this.inventoryFeedService = inventoryFeedService;
    }

    @Scheduled(fixedDelayString = "${app.inventory.feed.poll-ms:3600000}")
    public int runOnce() {
        int synced = inventoryFeedService.runAllEnabled();
        if (synced > 0) {
            log.info("[inventory-feed] synced {} dealer feeds", synced);
        }
        return synced;
    }
}
