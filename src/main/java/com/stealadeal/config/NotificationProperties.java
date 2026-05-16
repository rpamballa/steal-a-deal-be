package com.stealadeal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.notifications")
public record NotificationProperties(
        String provider,
        String sesFromAddress,
        String twilioFromNumber,
        Integer maxDispatchAttempts
) {

    public NotificationProperties {
        if (provider == null || provider.isBlank()) {
            provider = "stub";
        }
        if (maxDispatchAttempts == null || maxDispatchAttempts < 1) {
            maxDispatchAttempts = 5;
        }
    }
}
