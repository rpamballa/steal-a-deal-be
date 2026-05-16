package com.stealadeal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.vin")
public record VinProperties(String provider) {

    public VinProperties {
        if (provider == null || provider.isBlank()) {
            provider = "stub";
        }
    }
}
