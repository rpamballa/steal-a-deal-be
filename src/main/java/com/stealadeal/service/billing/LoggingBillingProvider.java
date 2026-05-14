package com.stealadeal.service.billing;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Default billing provider used until a real payment processor is wired.
 * Generates deterministic-looking synthetic identifiers so persistence
 * exercises the same code path that the production provider will. A
 * Stripe-backed bean overrides this by being selected via
 * {@code app.billing.provider=stripe}.
 */
@Component
@ConditionalOnMissingBean(name = "billingProvider")
@ConditionalOnProperty(name = "app.billing.provider", havingValue = "stub", matchIfMissing = true)
public class LoggingBillingProvider implements BillingProvider {

    private static final Logger log = LoggerFactory.getLogger(LoggingBillingProvider.class);

    @Override
    public String name() {
        return "stub";
    }

    @Override
    public BillingCustomerRef ensureCustomer(BillingCustomerRequest request) {
        String customerId = "stub_cus_" + UUID.randomUUID().toString().substring(0, 12);
        log.info("[billing/stub] ensureCustomer dealer={} email={} -> {}",
                request.dealerId(), request.contactEmail(), customerId);
        return new BillingCustomerRef(customerId);
    }

    @Override
    public BillingSubscriptionRef activateSubscription(BillingActivationRequest request) {
        String subscriptionId = "stub_sub_" + UUID.randomUUID().toString().substring(0, 12);
        String paymentMethodId = request.paymentMethodId() != null
                ? request.paymentMethodId()
                : "stub_pm_" + UUID.randomUUID().toString().substring(0, 12);
        log.info("[billing/stub] activateSubscription dealer={} plan={} price={} -> {}",
                request.dealerId(), request.planCode(), request.monthlyPrice(), subscriptionId);
        return new BillingSubscriptionRef(subscriptionId, paymentMethodId);
    }

    @Override
    public void cancelSubscription(BillingCancellationRequest request) {
        log.info("[billing/stub] cancelSubscription dealer={} subscription={}",
                request.dealerId(), request.subscriptionId());
    }

    @Override
    public boolean verifyWebhookSignature(String signatureHeader, String rawBody) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            return true;
        }
        log.warn("[billing/stub] ignoring webhook signature header: {}", signatureHeader);
        return true;
    }
}
