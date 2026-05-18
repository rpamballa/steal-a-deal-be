package com.stealadeal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        String bootstrapAdminEmail,
        String bootstrapAdminPassword,
        long tokenTtlHours,
        String jwtSecret,
        Integer accessTtlMinutes,
        Integer refreshTtlDays,
        Integer rateLimitPerMinute
) {

    public AuthProperties {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            // Dev-only fallback (>=32 bytes for HS256). Production MUST
            // set JWT_SECRET via the secrets manager.
            jwtSecret = "dev-only-insecure-jwt-secret-change-me-0123456789";
        }
        if (accessTtlMinutes == null || accessTtlMinutes < 1) {
            accessTtlMinutes = 15;
        }
        if (refreshTtlDays == null || refreshTtlDays < 1) {
            refreshTtlDays = 30;
        }
        if (rateLimitPerMinute == null || rateLimitPerMinute < 1) {
            rateLimitPerMinute = 5;
        }
    }
}
