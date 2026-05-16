package com.stealadeal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(InventoryFeedProperties.InventoryFeed.class)
public class InventoryFeedProperties {

    @ConfigurationProperties(prefix = "app.inventory.feed")
    public record InventoryFeed(Long pollMs) {

        public InventoryFeed {
            if (pollMs == null || pollMs < 1000) {
                pollMs = 3_600_000L;
            }
        }
    }
}
