package com.stealadeal.service.esign;

import com.stealadeal.domain.SigningStatus;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(name = "eSignProvider")
@ConditionalOnProperty(name = "app.esign.provider", havingValue = "stub", matchIfMissing = true)
public class StubESignProvider implements ESignProvider {

    private static final Logger log = LoggerFactory.getLogger(StubESignProvider.class);

    private final ConcurrentMap<String, SigningStatus> envelopes = new ConcurrentHashMap<>();

    @Override
    public String name() {
        return "stub";
    }

    @Override
    public EnvelopeRef createEnvelope(CreateEnvelopeRequest request) {
        String envelopeId = "stub_env_" + UUID.randomUUID().toString().substring(0, 12);
        envelopes.put(envelopeId, SigningStatus.SENT);
        log.info("[esign/stub] envelope created id={} deal={} document={} signer={}",
                envelopeId, request.dealId(), request.documentId(), request.signerEmail());
        return new EnvelopeRef(envelopeId, SigningStatus.SENT);
    }

    @Override
    public EnvelopeRef getEnvelopeStatus(String envelopeId) {
        SigningStatus status = envelopes.getOrDefault(envelopeId, SigningStatus.SENT);
        return new EnvelopeRef(envelopeId, status);
    }

    @Override
    public void cancelEnvelope(String envelopeId) {
        envelopes.put(envelopeId, SigningStatus.CANCELED);
        log.info("[esign/stub] envelope canceled id={}", envelopeId);
    }

    @Override
    public EnvelopeWebhook parseWebhookEvent(String signatureHeader, String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return null;
        }
        // Expected stub payload: {"envelopeId":"...","status":"SIGNED"}
        String envelopeId = extract(rawBody, "envelopeId");
        String statusName = extract(rawBody, "status");
        if (envelopeId == null || statusName == null) {
            return null;
        }
        SigningStatus status;
        try {
            status = SigningStatus.valueOf(statusName);
        } catch (IllegalArgumentException ex) {
            return null;
        }
        envelopes.put(envelopeId, status);
        return new EnvelopeWebhook(envelopeId, status);
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
}
