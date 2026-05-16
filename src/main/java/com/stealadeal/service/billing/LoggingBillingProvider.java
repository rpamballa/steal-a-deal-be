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
    public DepositIntent createDepositIntent(DepositIntentRequest request) {
        String intentId = "stub_pi_" + UUID.randomUUID().toString().substring(0, 12);
        log.info("[billing/stub] createDepositIntent deal={} amount={} {} -> {}",
                request.dealId(), request.amount(), request.currency(), intentId);
        return new DepositIntent(intentId, intentId + "_secret", "REQUIRES_PAYMENT");
    }

    @Override
    public TransactionFeeRef chargeTransactionFee(TransactionFeeRequest request) {
        String chargeId = "stub_fee_" + UUID.randomUUID().toString().substring(0, 12);
        log.info("[billing/stub] chargeTransactionFee deal={} dealer={} amount={} {} -> {}",
                request.dealId(), request.dealerId(), request.amount(), request.currency(), chargeId);
        return new TransactionFeeRef(chargeId, "SETTLED");
    }

    @Override
    public DepositWebhook parseDepositEvent(String signatureHeader, String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return null;
        }
        String intentId = extract(rawBody, "intentId");
        String status = extract(rawBody, "status");
        if (intentId == null || status == null) {
            return null;
        }
        return new DepositWebhook(intentId, status);
    }

    private String extract(String json, String key) {
        String marker = "\"" + key + "\"";
        int idx = json.indexOf(marker);
        if (idx < 0) {
            return null;
        }
        int colon = json.indexOf(':', idx + marker.length());
        if (colon < 0) {
            return null;
        }
        int start = json.indexOf('"', colon);
        if (start < 0) {
            return null;
        }
        int end = json.indexOf('"', start + 1);
        if (end < 0) {
            return null;
        }
        return json.substring(start + 1, end);
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
