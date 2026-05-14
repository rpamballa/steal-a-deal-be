package com.stealadeal.web;

import com.stealadeal.service.billing.BillingProvider;
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
public class BillingWebhookController {

    private static final Logger log = LoggerFactory.getLogger(BillingWebhookController.class);

    private final BillingProvider billingProvider;

    public BillingWebhookController(BillingProvider billingProvider) {
        this.billingProvider = billingProvider;
    }

    @PostMapping("/billing")
    public ResponseEntity<WebhookAck> billingEvent(
            @RequestHeader(name = "X-Signature", required = false) String signature,
            @RequestBody(required = false) String body
    ) {
        if (!billingProvider.verifyWebhookSignature(signature, body == null ? "" : body)) {
            log.warn("[billing/webhook] rejected event: signature did not verify");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new WebhookAck("rejected", "signature did not verify"));
        }
        log.info("[billing/webhook] received provider={} bytes={}",
                billingProvider.name(), body == null ? 0 : body.length());
        return ResponseEntity.ok(new WebhookAck("accepted", billingProvider.name()));
    }

    public record WebhookAck(String status, String provider) {
    }
}
