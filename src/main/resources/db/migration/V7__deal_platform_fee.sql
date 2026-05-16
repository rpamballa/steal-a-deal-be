-- Per-deal platform transaction fee (the pitch's revenue stream #2:
-- 0.75% of sale). Computed and settled when the deal completes.

ALTER TABLE deal
    ADD COLUMN platform_fee_rate NUMERIC(6, 5),
    ADD COLUMN platform_fee_amount NUMERIC(12, 2),
    ADD COLUMN platform_fee_settled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN platform_fee_charge_id VARCHAR(255),
    ADD COLUMN platform_fee_settled_at TIMESTAMP WITH TIME ZONE;
