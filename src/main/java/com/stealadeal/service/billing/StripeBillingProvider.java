package com.stealadeal.service.billing;

import com.stealadeal.config.BillingProperties;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Price;
import com.stripe.model.Subscription;
import com.stripe.model.Customer;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.PriceCreateParams;
import com.stripe.param.SubscriptionCreateParams;
import com.stripe.param.SubscriptionCancelParams;
import java.math.BigDecimal;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Stripe-backed billing. Selected with {@code app.billing.provider=stripe}.
 * The API key and webhook secret are read only from configuration
 * (env / secrets manager) — never embedded in code or committed. The
 * key is fail-fast validated at startup so a misconfigured prod deploy
 * does not silently fall back.
 */
@Component("billingProvider")
@ConditionalOnProperty(name = "app.billing.provider", havingValue = "stripe")
public class StripeBillingProvider implements BillingProvider {

    private static final Logger log = LoggerFactory.getLogger(StripeBillingProvider.class);

    private final String apiKey;
    private final String webhookSecret;

    public StripeBillingProvider(BillingProperties properties) {
        if (properties.stripeApiKey() == null || properties.stripeApiKey().isBlank()) {
            throw new IllegalStateException(
                    "app.billing.provider=stripe but app.billing.stripe-api-key is not set "
                            + "(supply STRIPE_API_KEY via the secrets manager — never in source)");
        }
        this.apiKey = properties.stripeApiKey();
        this.webhookSecret = properties.stripeWebhookSecret();
        if (this.webhookSecret == null || this.webhookSecret.isBlank()) {
            log.warn("[billing/stripe] no webhook secret configured — webhook events cannot be verified");
        }
    }

    private RequestOptions opts() {
        return RequestOptions.builder().setApiKey(apiKey).build();
    }

    private long toMinorUnits(BigDecimal amount) {
        return amount.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact();
    }

    @Override
    public String name() {
        return "stripe";
    }

    @Override
    public BillingCustomerRef ensureCustomer(BillingCustomerRequest request) {
        try {
            Customer customer = Customer.create(
                    CustomerCreateParams.builder()
                            .setName(request.dealerName())
                            .setEmail(request.contactEmail())
                            .putMetadata("dealerId", String.valueOf(request.dealerId()))
                            .build(),
                    opts());
            return new BillingCustomerRef(customer.getId());
        } catch (StripeException e) {
            throw stripeError("ensureCustomer", e);
        }
    }

    @Override
    public BillingSubscriptionRef activateSubscription(BillingActivationRequest request) {
        try {
            Price price = Price.create(
                    PriceCreateParams.builder()
                            .setCurrency("usd")
                            .setUnitAmount(toMinorUnits(request.monthlyPrice()))
                            .setRecurring(PriceCreateParams.Recurring.builder()
                                    .setInterval(PriceCreateParams.Recurring.Interval.MONTH)
                                    .build())
                            .setProductData(PriceCreateParams.ProductData.builder()
                                    .setName("StealADeal " + request.planCode())
                                    .build())
                            .build(),
                    opts());

            SubscriptionCreateParams.Builder sub = SubscriptionCreateParams.builder()
                    .setCustomer(request.customerId())
                    .addItem(SubscriptionCreateParams.Item.builder()
                            .setPrice(price.getId())
                            .build());
            if (request.paymentMethodId() != null && !request.paymentMethodId().isBlank()) {
                sub.setDefaultPaymentMethod(request.paymentMethodId());
            }
            Subscription subscription = Subscription.create(sub.build(), opts());
            return new BillingSubscriptionRef(subscription.getId(), request.paymentMethodId());
        } catch (StripeException e) {
            throw stripeError("activateSubscription", e);
        }
    }

    @Override
    public void cancelSubscription(BillingCancellationRequest request) {
        try {
            Subscription subscription = Subscription.retrieve(request.subscriptionId(), opts());
            subscription.cancel(SubscriptionCancelParams.builder().build(), opts());
        } catch (StripeException e) {
            throw stripeError("cancelSubscription", e);
        }
    }

    @Override
    public DepositIntent createDepositIntent(DepositIntentRequest request) {
        try {
            PaymentIntent intent = PaymentIntent.create(
                    PaymentIntentCreateParams.builder()
                            .setAmount(toMinorUnits(request.amount()))
                            .setCurrency(request.currency() == null ? "usd" : request.currency())
                            .setReceiptEmail(request.buyerEmail())
                            .putMetadata("dealId", String.valueOf(request.dealId()))
                            .putMetadata("purpose", "deposit")
                            .setAutomaticPaymentMethods(
                                    PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                            .setEnabled(true)
                                            .build())
                            .build(),
                    opts());
            return new DepositIntent(intent.getId(), intent.getClientSecret(),
                    mapIntentStatus(intent.getStatus()));
        } catch (StripeException e) {
            throw stripeError("createDepositIntent", e);
        }
    }

    @Override
    public TransactionFeeRef chargeTransactionFee(TransactionFeeRequest request) {
        try {
            PaymentIntent intent = PaymentIntent.create(
                    PaymentIntentCreateParams.builder()
                            .setAmount(toMinorUnits(request.amount()))
                            .setCurrency(request.currency() == null ? "usd" : request.currency())
                            .putMetadata("dealId", String.valueOf(request.dealId()))
                            .putMetadata("dealerId", String.valueOf(request.dealerId()))
                            .putMetadata("purpose", "transaction_fee")
                            .build(),
                    opts());
            String status = "succeeded".equals(intent.getStatus()) ? "SETTLED" : "PENDING";
            return new TransactionFeeRef(intent.getId(), status);
        } catch (StripeException e) {
            throw stripeError("chargeTransactionFee", e);
        }
    }

    @Override
    public DepositWebhook parseDepositEvent(String signatureHeader, String rawBody) {
        Optional<Event> event = verifiedEvent(signatureHeader, rawBody);
        if (event.isEmpty()) {
            return null;
        }
        Event e = event.get();
        String type = e.getType();
        if (!"payment_intent.succeeded".equals(type) && !"payment_intent.payment_failed".equals(type)) {
            return null;
        }
        return e.getDataObjectDeserializer().getObject()
                .filter(PaymentIntent.class::isInstance)
                .map(PaymentIntent.class::cast)
                .filter(pi -> "deposit".equals(pi.getMetadata().get("purpose")))
                .map(pi -> new DepositWebhook(
                        pi.getId(),
                        "payment_intent.succeeded".equals(type) ? "SUCCEEDED" : "FAILED"))
                .orElse(null);
    }

    @Override
    public boolean verifyWebhookSignature(String signatureHeader, String rawBody) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            return true;
        }
        return verifiedEvent(signatureHeader, rawBody).isPresent();
    }

    private Optional<Event> verifiedEvent(String signatureHeader, String rawBody) {
        if (signatureHeader == null || signatureHeader.isBlank()
                || webhookSecret == null || webhookSecret.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Webhook.constructEvent(rawBody, signatureHeader, webhookSecret));
        } catch (SignatureVerificationException e) {
            log.warn("[billing/stripe] webhook signature verification failed");
            return Optional.empty();
        } catch (RuntimeException e) {
            log.warn("[billing/stripe] webhook parse failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String mapIntentStatus(String stripeStatus) {
        if ("succeeded".equals(stripeStatus)) {
            return "SUCCEEDED";
        }
        if ("canceled".equals(stripeStatus)) {
            return "FAILED";
        }
        return "REQUIRES_PAYMENT";
    }

    private ResponseStatusException stripeError(String op, StripeException e) {
        log.error("[billing/stripe] {} failed: {}", op, e.getMessage());
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Stripe " + op + " failed");
    }
}
