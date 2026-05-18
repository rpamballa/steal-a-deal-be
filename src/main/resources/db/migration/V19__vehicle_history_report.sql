-- Buyer-facing vehicle history report. At most one row per vehicle
-- (unique vehicle_id). Source is either a third-party provider or a
-- dealer-uploaded PDF; all summary columns are nullable so a bare
-- PDF upload (no parsed summary) is a valid, complete report.
CREATE TABLE vehicle_history_report (
    id BIGSERIAL PRIMARY KEY,
    vehicle_id BIGINT NOT NULL UNIQUE REFERENCES vehicle (id),
    source VARCHAR(16) NOT NULL,
    provider_name VARCHAR(64),
    storage_key VARCHAR(128),
    external_report_url VARCHAR(512),
    generated_at TIMESTAMP WITH TIME ZONE,
    owner_count INTEGER,
    accident_count INTEGER,
    title_brand VARCHAR(32),
    last_reported_odometer INTEGER,
    odometer_rollback_suspected BOOLEAN,
    open_recall_count INTEGER,
    service_record_count INTEGER
);
