CREATE TABLE price_drop_alert_log (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES user_account (id),
    vehicle_id BIGINT NOT NULL REFERENCES vehicle (id),
    last_notified_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_pricedrop_user_vehicle UNIQUE (user_id, vehicle_id)
);
