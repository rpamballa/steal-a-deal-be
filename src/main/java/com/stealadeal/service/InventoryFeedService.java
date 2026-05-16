package com.stealadeal.service;

import com.stealadeal.domain.Dealer;
import com.stealadeal.domain.DealerInventoryFeed;
import com.stealadeal.repository.DealerInventoryFeedRepository;
import com.stealadeal.repository.DealerRepository;
import com.stealadeal.service.feed.InventoryFeedSource;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Configures and runs per-dealer automated inventory feeds. Sync
 * reuses the existing CSV upsert pipeline, so all validation, VIN
 * dedupe, and the approved-dealer gate still apply.
 */
@Service
@Transactional
public class InventoryFeedService {

    private static final Logger log = LoggerFactory.getLogger(InventoryFeedService.class);

    private final DealerRepository dealerRepository;
    private final DealerInventoryFeedRepository feedRepository;
    private final InventoryService inventoryService;
    private final List<InventoryFeedSource> feedSources;
    private final AuditService auditService;

    public InventoryFeedService(
            DealerRepository dealerRepository,
            DealerInventoryFeedRepository feedRepository,
            InventoryService inventoryService,
            List<InventoryFeedSource> feedSources,
            AuditService auditService
    ) {
        this.dealerRepository = dealerRepository;
        this.feedRepository = feedRepository;
        this.inventoryService = inventoryService;
        this.feedSources = feedSources;
        this.auditService = auditService;
    }

    public DealerInventoryFeed configureFeed(Long dealerId, String feedUrl, String mode, boolean enabled) {
        Dealer dealer = dealerRepository.findById(dealerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dealer not found"));
        InventoryService.InventoryUploadMode parsedMode;
        try {
            parsedMode = InventoryService.InventoryUploadMode.valueOf(mode);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "mode must be CREATE_ONLY or UPSERT");
        }
        if (feedSources.stream().noneMatch(s -> s.supports(feedUrl))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported feed location: " + feedUrl);
        }
        OffsetDateTime now = OffsetDateTime.now();
        DealerInventoryFeed feed = feedRepository.findByDealerId(dealerId).orElseGet(() -> {
            DealerInventoryFeed created = new DealerInventoryFeed();
            created.setDealer(dealer);
            created.setCreatedAt(now);
            created.setLastSyncStatus("NEVER");
            return created;
        });
        feed.setFeedUrl(feedUrl);
        feed.setMode(parsedMode.name());
        feed.setEnabled(enabled);
        feed.setUpdatedAt(now);
        DealerInventoryFeed saved = feedRepository.save(feed);
        auditService.record("INVENTORY_FEED_CONFIGURED", "DealerInventoryFeed", saved.getId(), null,
                "feedUrl=" + feedUrl + " mode=" + parsedMode + " enabled=" + enabled);
        return saved;
    }

    @Transactional(readOnly = true)
    public DealerInventoryFeed getFeed(Long dealerId) {
        return feedRepository.findByDealerId(dealerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No feed configured"));
    }

    public InventoryService.InventoryUploadResult syncFeed(Long dealerId) {
        DealerInventoryFeed feed = getFeed(dealerId);
        return runSync(feed);
    }

    public int runAllEnabled() {
        List<DealerInventoryFeed> feeds = feedRepository.findByEnabledTrue();
        int ok = 0;
        for (DealerInventoryFeed feed : feeds) {
            try {
                runSync(feed);
                ok++;
            } catch (RuntimeException exception) {
                log.warn("[inventory-feed] sync failed for dealer {}: {}",
                        feed.getDealer().getId(), exception.getMessage());
            }
        }
        return ok;
    }

    private InventoryService.InventoryUploadResult runSync(DealerInventoryFeed feed) {
        InventoryFeedSource source = feedSources.stream()
                .filter(s -> s.supports(feed.getFeedUrl()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No feed source supports " + feed.getFeedUrl()));
        OffsetDateTime now = OffsetDateTime.now();
        try (InputStream content = source.open(feed.getFeedUrl())) {
            InventoryService.InventoryUploadResult result = inventoryService.ingestCsvStream(
                    feed.getDealer().getId(),
                    InventoryService.InventoryUploadMode.valueOf(feed.getMode()),
                    content
            );
            feed.setLastSyncedAt(now);
            feed.setLastSyncStatus("SUCCESS");
            feed.setLastSyncDetail(String.format("created=%d updated=%d rejected=%d",
                    result.createdCount(), result.updatedCount(), result.rejectedCount()));
            feed.setUpdatedAt(now);
            feedRepository.save(feed);
            auditService.record("INVENTORY_FEED_SYNCED", "DealerInventoryFeed", feed.getId(), null,
                    feed.getLastSyncDetail());
            return result;
        } catch (ResponseStatusException exception) {
            recordFailure(feed, now, exception.getReason());
            throw exception;
        } catch (Exception exception) {
            recordFailure(feed, now, exception.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Feed sync failed: " + exception.getMessage());
        }
    }

    private void recordFailure(DealerInventoryFeed feed, OffsetDateTime now, String detail) {
        feed.setLastSyncedAt(now);
        feed.setLastSyncStatus("FAILED");
        feed.setLastSyncDetail(detail == null ? "unknown error" : detail.substring(0, Math.min(detail.length(), 1000)));
        feed.setUpdatedAt(now);
        feedRepository.save(feed);
        auditService.record("INVENTORY_FEED_FAILED", "DealerInventoryFeed", feed.getId(), null,
                feed.getLastSyncDetail());
    }
}
