-- Per-dealer automated inventory feed. A scheduled processor fetches
-- the feed and runs it through the existing CSV upsert pipeline.

CREATE TABLE dealer_inventory_feed (
    id BIGSERIAL PRIMARY KEY,
    dealer_id BIGINT NOT NULL UNIQUE REFERENCES dealer (id),
    feed_url VARCHAR(1000) NOT NULL,
    mode VARCHAR(16) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    last_synced_at TIMESTAMP WITH TIME ZONE,
    last_sync_status VARCHAR(16),
    last_sync_detail VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_dealer_inventory_feed_enabled ON dealer_inventory_feed (enabled);
