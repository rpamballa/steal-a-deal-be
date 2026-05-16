package com.stealadeal;

import com.stealadeal.service.billing.BillingProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies the Stripe provider is selected and wired when configured,
 * without making any network call to Stripe. A dummy key is used only
 * to satisfy the fail-fast constructor check; no Stripe API is invoked.
 */
@SpringBootTest(properties = {
        "app.billing.provider=stripe",
        "app.billing.stripe-api-key=sk_test_dummy_not_a_real_key",
        "app.billing.stripe-webhook-secret="
})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class StripeBillingProviderTest {

    @Autowired
    private BillingProvider billingProvider;

    @Test
    void stripeProviderIsSelectedWhenConfigured() {
        Assertions.assertEquals("stripe", billingProvider.name());
        Assertions.assertEquals("StripeBillingProvider",
                billingProvider.getClass().getSimpleName());
    }

    @Test
    void unsignedWebhookIsTreatedAsNoOpWithoutNetworkCall() {
        // No signature header -> contract says return true, no Stripe call.
        Assertions.assertTrue(billingProvider.verifyWebhookSignature(null, ""));
        // Signature present but no secret configured -> cannot verify.
        Assertions.assertFalse(billingProvider.verifyWebhookSignature("t=1,v1=abc", "{}"));
    }
}
