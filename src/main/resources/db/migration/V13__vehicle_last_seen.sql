-- Tracks when a vehicle was last created/updated/seen in a feed so a
-- scheduled reaper can delist inventory that has gone stale (no longer
-- in any feed and untouched past the staleness window).

ALTER TABLE vehicle
    ADD COLUMN last_seen_at TIMESTAMP WITH TIME ZONE;

-- Existing rows: treat as just-seen so they aren't immediately delisted.
UPDATE vehicle SET last_seen_at = now() WHERE last_seen_at IS NULL;

CREATE INDEX idx_vehicle_status_last_seen ON vehicle (status, last_seen_at);
