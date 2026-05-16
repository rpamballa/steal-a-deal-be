package com.stealadeal.web;

import com.stealadeal.service.DealService;
import com.stealadeal.service.esign.ESignProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/webhooks")
public class ESignWebhookController {

    private static final Logger log = LoggerFactory.getLogger(ESignWebhookController.class);

    private final ESignProvider eSignProvider;
    private final DealService dealService;

    public ESignWebhookController(ESignProvider eSignProvider, DealService dealService) {
        this.eSignProvider = eSignProvider;
        this.dealService = dealService;
    }

    @PostMapping("/esign")
    public ResponseEntity<WebhookAck> esignEvent(
            @RequestHeader(name = "X-Signature", required = false) String signature,
            @RequestBody(required = false) String body
    ) {
        ESignProvider.EnvelopeWebhook event = eSignProvider.parseWebhookEvent(signature, body == null ? "" : body);
        if (event == null) {
            log.warn("[esign/webhook] rejected event: signature/body did not parse");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new WebhookAck("rejected", eSignProvider.name()));
        }
        try {
            dealService.applyEnvelopeStatusUpdate(event.envelopeId(), event.status());
        } catch (Exception exception) {
            log.warn("[esign/webhook] could not apply envelope {} status {}: {}",
                    event.envelopeId(), event.status(), exception.getMessage());
            // Still ack to provider so it doesn't retry indefinitely; surface in audit log.
        }
        return ResponseEntity.ok(new WebhookAck("accepted", eSignProvider.name()));
    }

    public record WebhookAck(String status, String provider) {
    }
}
