package com.stealadeal.web;

import com.stealadeal.service.DealService;
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
    private final DealService dealService;

    public BillingWebhookController(BillingProvider billingProvider, DealService dealService) {
        this.billingProvider = billingProvider;
        this.dealService = dealService;
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
        BillingProvider.DepositWebhook deposit = billingProvider.parseDepositEvent(signature, body == null ? "" : body);
        if (deposit != null) {
            try {
                dealService.confirmDepositByIntent(deposit.intentId(), deposit.status());
            } catch (Exception exception) {
                log.warn("[billing/webhook] could not apply deposit intent {} status {}: {}",
                        deposit.intentId(), deposit.status(), exception.getMessage());
            }
        }
        log.info("[billing/webhook] received provider={} bytes={}",
                billingProvider.name(), body == null ? 0 : body.length());
        return ResponseEntity.ok(new WebhookAck("accepted", billingProvider.name()));
    }

    public record WebhookAck(String status, String provider) {
    }
}
