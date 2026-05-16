-- Persist external billing-provider identifiers alongside the local
-- subscription record so the dealer SaaS billing path can call out to
-- Stripe (or any future provider) and round-trip the references.

ALTER TABLE dealer_subscription
    ADD COLUMN billing_customer_id VARCHAR(255),
    ADD COLUMN billing_subscription_id VARCHAR(255),
    ADD COLUMN payment_method_id VARCHAR(255);

CREATE INDEX idx_dealer_subscription_billing_customer ON dealer_subscription (billing_customer_id);
CREATE INDEX idx_dealer_subscription_billing_subscription ON dealer_subscription (billing_subscription_id);
