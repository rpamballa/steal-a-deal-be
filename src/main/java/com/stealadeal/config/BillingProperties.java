package com.stealadeal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Selects the billing backend at runtime. Defaults to the in-process
 * stub. Set {@code app.billing.provider=stripe} and supply API keys
 * once the Stripe-backed BillingProvider bean is registered.
 */
@ConfigurationProperties(prefix = "app.billing")
public record BillingProperties(
        String provider,
        String stripeApiKey,
        String stripeWebhookSecret
) {

    public BillingProperties {
        if (provider == null || provider.isBlank()) {
            provider = "stub";
        }
    }
}
