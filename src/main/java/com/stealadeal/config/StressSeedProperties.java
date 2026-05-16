package com.stealadeal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Opt-in stress-test data seeding. Off by default; enable with
 * {@code app.seed.stress.enabled=true} to provision a dedicated dealer
 * and a configurable number of synthetic vehicle listings for load
 * testing the catalog/search/portal endpoints.
 */
@ConfigurationProperties(prefix = "app.seed.stress")
public record StressSeedProperties(
        boolean enabled,
        Integer vehicleCount
) {

    public StressSeedProperties {
        if (vehicleCount == null || vehicleCount < 1) {
            vehicleCount = 100;
        }
    }
}
