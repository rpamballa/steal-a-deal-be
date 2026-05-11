package com.stealadeal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        String bootstrapAdminEmail,
        String bootstrapAdminPassword,
        long tokenTtlHours
) {
}
