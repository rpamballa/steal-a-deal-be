-- Rotating refresh tokens for JWT auth (access token is a stateless
-- JWT; the long-lived refresh token is an opaque, revocable row).

CREATE TABLE refresh_token (
    id BIGSERIAL PRIMARY KEY,
    token VARCHAR(128) NOT NULL UNIQUE,
    user_account_id BIGINT NOT NULL REFERENCES user_account (id),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_refresh_token_user ON refresh_token (user_account_id);
