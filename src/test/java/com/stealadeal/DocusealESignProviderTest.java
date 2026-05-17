package com.stealadeal;

import com.stealadeal.domain.SigningStatus;
import com.stealadeal.service.esign.ESignProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies the DocuSeal provider is selected and its webhook parsing /
 * status mapping are correct. Fully offline — no DocuSeal instance is
 * contacted (createEnvelope/getEnvelopeStatus are not invoked here;
 * those require a live instance and are a documented go-live step).
 */
@SpringBootTest(properties = {
        "app.esign.provider=docuseal",
        "app.esign.docuseal-base-url=http://docuseal.invalid",
        "app.esign.docuseal-api-token=test_token_not_real",
        "app.esign.docuseal-webhook-secret=whtest"
})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DocusealESignProviderTest {

    @Autowired
    private ESignProvider eSignProvider;

    @Test
    void docusealProviderIsSelected() {
        Assertions.assertEquals("docuseal", eSignProvider.name());
        Assertions.assertEquals("DocusealESignProvider",
                eSignProvider.getClass().getSimpleName());
    }

    @Test
    void webhookCompletedMapsToSigned() {
        var ev = eSignProvider.parseWebhookEvent("whtest",
                "{\"event_type\":\"submission.completed\",\"data\":{\"id\":4321,\"status\":\"completed\"}}");
        Assertions.assertNotNull(ev);
        Assertions.assertEquals("4321", ev.envelopeId());
        Assertions.assertEquals(SigningStatus.SIGNED, ev.status());
    }

    @Test
    void webhookDeclinedMapsToDeclined() {
        var ev = eSignProvider.parseWebhookEvent("whtest",
                "{\"event_type\":\"form.declined\",\"data\":{\"id\":99,\"status\":\"declined\"}}");
        Assertions.assertNotNull(ev);
        Assertions.assertEquals(SigningStatus.DECLINED, ev.status());
    }

    @Test
    void webhookWithWrongSecretIsRejected() {
        Assertions.assertNull(eSignProvider.parseWebhookEvent("wrong-secret",
                "{\"event_type\":\"submission.completed\",\"data\":{\"id\":1,\"status\":\"completed\"}}"));
    }

    @Test
    void blankBodyIsRejected() {
        Assertions.assertNull(eSignProvider.parseWebhookEvent("whtest", ""));
    }

    @Test
    void createEnvelopeFailsFastWhenTemplateIdMissing() {
        // No app.esign.docuseal-template-id configured in this context.
        // Must fail before any network call (free DocuSeal needs a
        // UI-built template id).
        var req = new ESignProvider.CreateEnvelopeRequest(
                1L, 1L, "BUYER_AGREEMENT", "Buyer", "buyer@example.com",
                "application/pdf", 0L,
                new java.io.ByteArrayInputStream(new byte[0]),
                java.util.Map.of("buyer_name", "Buyer"));
        org.springframework.web.server.ResponseStatusException ex =
                Assertions.assertThrows(
                        org.springframework.web.server.ResponseStatusException.class,
                        () -> eSignProvider.createEnvelope(req));
        Assertions.assertEquals(503, ex.getStatusCode().value());
    }
}
