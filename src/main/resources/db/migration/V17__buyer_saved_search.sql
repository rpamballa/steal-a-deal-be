CREATE TABLE saved_search (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES user_account (id),
    name VARCHAR(255) NOT NULL,
    q VARCHAR(255),
    search_make VARCHAR(255),
    search_model VARCHAR(255),
    min_price NUMERIC(12,2),
    max_price NUMERIC(12,2),
    min_year INTEGER,
    max_mileage INTEGER,
    search_status VARCHAR(16),
    alert_on_price_drop BOOLEAN NOT NULL DEFAULT FALSE,
    last_matched_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_saved_search_user ON saved_search (user_id);
CREATE INDEX ix_saved_search_alert ON saved_search (alert_on_price_drop);
