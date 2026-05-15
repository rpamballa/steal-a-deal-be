package com.stealadeal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.notifications")
public record NotificationProperties(
        String provider,
        String sesFromAddress,
        String twilioFromNumber
) {

    public NotificationProperties {
        if (provider == null || provider.isBlank()) {
            provider = "stub";
        }
    }
}
