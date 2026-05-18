-- Additive, nullable vehicle classification fields. Existing rows
-- backfill to NULL; the frontend falls back to model-name heuristics
-- when these are null, so partial adoption is safe.
ALTER TABLE vehicle ADD COLUMN body_type VARCHAR(16);
ALTER TABLE vehicle ADD COLUMN fuel_type VARCHAR(16);
ALTER TABLE vehicle ADD COLUMN combined_mpg INTEGER;
ALTER TABLE vehicle ADD COLUMN market_value_cents BIGINT;
