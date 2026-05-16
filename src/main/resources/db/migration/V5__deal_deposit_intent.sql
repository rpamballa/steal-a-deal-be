-- Track the external payment-intent reference for a deal deposit so the
-- buyer's deposit can be collected through a real PSP (Stripe
-- PaymentIntent or equivalent) and confirmed asynchronously via webhook
-- instead of being marked paid synchronously.

ALTER TABLE deal
    ADD COLUMN deposit_intent_id VARCHAR(255);

CREATE INDEX idx_deal_deposit_intent ON deal (deposit_intent_id);
