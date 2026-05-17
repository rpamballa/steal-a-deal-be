package com.stealadeal.service.esign;

import com.stealadeal.domain.SigningStatus;
import java.io.InputStream;
import java.util.Map;

/**
 * SPI for the e-signature backend that handles signed agreements for
 * deal documents (e.g. BUYER_AGREEMENT). The default
 * {@link StubESignProvider} simulates the lifecycle in-process. A
 * DocuSign-, Dropbox-Sign-, or Adobe-Sign-backed implementation is
 * added by registering a bean and selecting it via
 * {@code app.esign.provider}.
 */
public interface ESignProvider {

    record CreateEnvelopeRequest(
            Long dealId,
            Long documentId,
            String documentTitle,
            String signerName,
            String signerEmail,
            String contentType,
            long sizeBytes,
            InputStream content,
            /**
             * Prefilled field values for template-based providers
             * (e.g. free DocuSeal). PDF/upload-based providers ignore
             * this; template-based providers ignore {@link #content}.
             */
            Map<String, String> fieldValues
    ) {
    }

    record EnvelopeRef(String envelopeId, SigningStatus status) {
    }

    record EnvelopeWebhook(String envelopeId, SigningStatus status) {
    }

    String name();

    EnvelopeRef createEnvelope(CreateEnvelopeRequest request);

    EnvelopeRef getEnvelopeStatus(String envelopeId);

    void cancelEnvelope(String envelopeId);

    EnvelopeWebhook parseWebhookEvent(String signatureHeader, String rawBody);

    default boolean verifyWebhookSignature(String signatureHeader, String rawBody) {
        return parseWebhookEvent(signatureHeader, rawBody) != null;
    }
}
