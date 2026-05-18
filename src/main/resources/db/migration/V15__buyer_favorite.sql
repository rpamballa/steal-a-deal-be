CREATE TABLE favorite (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES user_account (id),
    vehicle_id BIGINT NOT NULL REFERENCES vehicle (id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_favorite_user_vehicle UNIQUE (user_id, vehicle_id)
);
CREATE INDEX ix_favorite_user ON favorite (user_id);
CREATE INDEX ix_favorite_vehicle ON favorite (vehicle_id);
