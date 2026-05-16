package com.stealadeal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.esign")
public record ESignProperties(
        String provider,
        String docusignApiKey,
        String docusignWebhookSecret
) {

    public ESignProperties {
        if (provider == null || provider.isBlank()) {
            provider = "stub";
        }
    }
}
