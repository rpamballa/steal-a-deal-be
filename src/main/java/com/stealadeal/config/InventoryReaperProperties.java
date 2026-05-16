package com.stealadeal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(InventoryReaperProperties.InventoryReaper.class)
public class InventoryReaperProperties {

    @ConfigurationProperties(prefix = "app.inventory.reaper")
    public record InventoryReaper(Integer staleDays, Long pollMs) {

        public InventoryReaper {
            if (staleDays == null || staleDays < 0) {
                staleDays = 30;
            }
            if (pollMs == null || pollMs < 1000) {
                pollMs = 86_400_000L;
            }
        }
    }
}
