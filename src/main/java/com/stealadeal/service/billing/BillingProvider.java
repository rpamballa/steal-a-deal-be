package com.stealadeal.service.billing;

import java.math.BigDecimal;

/**
 * SPI for the billing backend that owns the dealer SaaS subscription.
 * The default {@link LoggingBillingProvider} is a no-op suitable for dev
 * and CI; a Stripe-backed implementation is added by registering a bean
 * that returns a different {@link #name()} and is selected via
 * {@code app.billing.provider}.
 */
public interface BillingProvider {

    record BillingCustomerRequest(Long dealerId, String dealerName, String contactEmail) {
    }

    record BillingCustomerRef(String customerId) {
    }

    record BillingActivationRequest(
            Long dealerId,
            String customerId,
            String planCode,
            BigDecimal monthlyPrice,
            String paymentMethodId
    ) {
    }

    record BillingSubscriptionRef(String subscriptionId, String paymentMethodId) {
    }

    record BillingCancellationRequest(Long dealerId, String subscriptionId) {
    }

    record DepositIntentRequest(Long dealId, String buyerEmail, BigDecimal amount, String currency) {
    }

    /** {@code status} is one of REQUIRES_PAYMENT, SUCCEEDED, FAILED. */
    record DepositIntent(String intentId, String clientSecret, String status) {
    }

    record DepositWebhook(String intentId, String status) {
    }

    String name();

    BillingCustomerRef ensureCustomer(BillingCustomerRequest request);

    BillingSubscriptionRef activateSubscription(BillingActivationRequest request);

    void cancelSubscription(BillingCancellationRequest request);

    DepositIntent createDepositIntent(DepositIntentRequest request);

    /**
     * Parse a deposit-related webhook event. Returns {@code null} when the
     * payload is not a deposit event or cannot be verified.
     */
    DepositWebhook parseDepositEvent(String signatureHeader, String rawBody);

    /**
     * Verify a webhook signature/body. Implementations that do not support
     * signed webhooks return {@code true} when no signature is present and
     * {@code false} when a signature is provided but cannot be verified.
     */
    boolean verifyWebhookSignature(String signatureHeader, String rawBody);
}
