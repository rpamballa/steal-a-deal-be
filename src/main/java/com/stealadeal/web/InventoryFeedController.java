package com.stealadeal.web;

import com.stealadeal.domain.DealerInventoryFeed;
import com.stealadeal.service.InventoryFeedService;
import com.stealadeal.service.InventoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dealers")
@Validated
public class InventoryFeedController {

    private final InventoryFeedService inventoryFeedService;

    public InventoryFeedController(InventoryFeedService inventoryFeedService) {
        this.inventoryFeedService = inventoryFeedService;
    }

    @PutMapping("/{dealerId}/inventory/feed")
    @PreAuthorize("@accessControl.canAccessDealer(authentication, #dealerId)")
    public FeedResponse configure(
            @PathVariable Long dealerId,
            @Valid @RequestBody ConfigureFeedRequest request
    ) {
        return FeedResponse.from(inventoryFeedService.configureFeed(
                dealerId, request.feedUrl(), request.mode(), request.enabled()));
    }

    @GetMapping("/{dealerId}/inventory/feed")
    @PreAuthorize("@accessControl.canAccessDealer(authentication, #dealerId)")
    public FeedResponse get(@PathVariable Long dealerId) {
        return FeedResponse.from(inventoryFeedService.getFeed(dealerId));
    }

    @PostMapping("/{dealerId}/inventory/feed/sync")
    @PreAuthorize("@accessControl.canAccessDealer(authentication, #dealerId)")
    public SyncResultResponse sync(@PathVariable Long dealerId) {
        InventoryService.InventoryUploadResult result = inventoryFeedService.syncFeed(dealerId);
        return new SyncResultResponse(
                result.totalRows(),
                result.createdCount(),
                result.updatedCount(),
                result.rejectedCount()
        );
    }

    public record ConfigureFeedRequest(
            @NotBlank String feedUrl,
            @NotBlank String mode,
            @NotNull Boolean enabled
    ) {
    }

    public record FeedResponse(
            Long dealerId,
            String feedUrl,
            String mode,
            boolean enabled,
            OffsetDateTime lastSyncedAt,
            String lastSyncStatus,
            String lastSyncDetail
    ) {

        static FeedResponse from(DealerInventoryFeed feed) {
            return new FeedResponse(
                    feed.getDealer().getId(),
                    feed.getFeedUrl(),
                    feed.getMode(),
                    feed.isEnabled(),
                    feed.getLastSyncedAt(),
                    feed.getLastSyncStatus(),
                    feed.getLastSyncDetail()
            );
        }
    }

    public record SyncResultResponse(int totalRows, int createdCount, int updatedCount, int rejectedCount) {
    }
}
