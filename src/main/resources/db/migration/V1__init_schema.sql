-- StealADeal initial schema baseline (PostgreSQL)
-- Mirrors the Hibernate-managed schema used by the dev (H2) profile.
-- Snake-case naming follows Spring Boot's default PhysicalNamingStrategy.

CREATE TABLE dealer (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    license_number VARCHAR(255) NOT NULL UNIQUE,
    city VARCHAR(255) NOT NULL,
    state VARCHAR(2) NOT NULL,
    approved BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE user_account (
    id BIGSERIAL PRIMARY KEY,
    display_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL,
    dealer_id BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_user_account_dealer ON user_account (dealer_id);

CREATE TABLE auth_token (
    id BIGSERIAL PRIMARY KEY,
    token VARCHAR(128) NOT NULL UNIQUE,
    user_account_id BIGINT NOT NULL REFERENCES user_account (id),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_used_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_auth_token_user ON auth_token (user_account_id);

CREATE TABLE vehicle (
    id BIGSERIAL PRIMARY KEY,
    dealer_id BIGINT NOT NULL REFERENCES dealer (id),
    vin VARCHAR(17) NOT NULL UNIQUE,
    model_year INTEGER NOT NULL,
    make VARCHAR(255) NOT NULL,
    model VARCHAR(255) NOT NULL,
    trim VARCHAR(255) NOT NULL,
    mileage INTEGER NOT NULL,
    price NUMERIC(12, 2) NOT NULL,
    status VARCHAR(32) NOT NULL
);
CREATE INDEX idx_vehicle_dealer ON vehicle (dealer_id);
CREATE INDEX idx_vehicle_status ON vehicle (status);

CREATE TABLE vehicle_image_urls (
    vehicle_id BIGINT NOT NULL REFERENCES vehicle (id) ON DELETE CASCADE,
    image_urls VARCHAR(2000) NOT NULL
);
CREATE INDEX idx_vehicle_image_urls_vehicle ON vehicle_image_urls (vehicle_id);

CREATE TABLE lead (
    id BIGSERIAL PRIMARY KEY,
    vehicle_id BIGINT NOT NULL REFERENCES vehicle (id),
    buyer_name VARCHAR(255) NOT NULL,
    buyer_email VARCHAR(255) NOT NULL,
    buyer_phone VARCHAR(255) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_lead_vehicle ON lead (vehicle_id);
CREATE INDEX idx_lead_status ON lead (status);

CREATE TABLE appointment (
    id BIGSERIAL PRIMARY KEY,
    vehicle_id BIGINT NOT NULL REFERENCES vehicle (id),
    buyer_name VARCHAR(255) NOT NULL,
    buyer_email VARCHAR(255) NOT NULL,
    type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    scheduled_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_appointment_vehicle ON appointment (vehicle_id);
CREATE INDEX idx_appointment_status ON appointment (status);

CREATE TABLE deal (
    id BIGSERIAL PRIMARY KEY,
    vehicle_id BIGINT NOT NULL REFERENCES vehicle (id),
    buyer_name VARCHAR(255) NOT NULL,
    buyer_email VARCHAR(255) NOT NULL,
    buyer_phone VARCHAR(255) NOT NULL,
    buyer_address_line1 VARCHAR(255) NOT NULL,
    buyer_address_line2 VARCHAR(255),
    buyer_city VARCHAR(255) NOT NULL,
    buyer_state VARCHAR(2) NOT NULL,
    buyer_postal_code VARCHAR(255) NOT NULL,
    fulfillment_type VARCHAR(32) NOT NULL,
    fulfillment_status VARCHAR(32) NOT NULL,
    fulfillment_scheduled_at TIMESTAMP WITH TIME ZONE,
    fulfillment_location VARCHAR(255),
    fulfillment_notes VARCHAR(1000),
    trade_in BOOLEAN NOT NULL,
    trade_in_vin VARCHAR(255),
    trade_in_mileage INTEGER,
    trade_in_offer NUMERIC(12, 2),
    vehicle_price NUMERIC(12, 2) NOT NULL,
    tax_amount NUMERIC(12, 2) NOT NULL,
    registration_fee NUMERIC(12, 2) NOT NULL,
    documentation_fee NUMERIC(12, 2) NOT NULL,
    delivery_fee NUMERIC(12, 2) NOT NULL,
    discount_amount NUMERIC(12, 2) NOT NULL,
    deposit_amount NUMERIC(12, 2) NOT NULL,
    deposit_paid BOOLEAN NOT NULL,
    total_amount NUMERIC(12, 2) NOT NULL,
    stage VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_deal_vehicle ON deal (vehicle_id);
CREATE INDEX idx_deal_stage ON deal (stage);
CREATE INDEX idx_deal_buyer_email ON deal (buyer_email);

CREATE TABLE deal_document (
    id BIGSERIAL PRIMARY KEY,
    deal_id BIGINT NOT NULL REFERENCES deal (id),
    type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_deal_document_deal ON deal_document (deal_id);
CREATE INDEX idx_deal_document_status ON deal_document (status);

CREATE TABLE deal_activity (
    id BIGSERIAL PRIMARY KEY,
    deal_id BIGINT NOT NULL REFERENCES deal (id),
    event_type VARCHAR(255) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_deal_activity_deal ON deal_activity (deal_id);

CREATE TABLE deal_task (
    id BIGSERIAL PRIMARY KEY,
    deal_id BIGINT NOT NULL REFERENCES deal (id),
    code VARCHAR(255) NOT NULL,
    assignee_type VARCHAR(32) NOT NULL,
    assignee_reference VARCHAR(255) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    status VARCHAR(32) NOT NULL,
    due_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_deal_task_deal ON deal_task (deal_id);
CREATE INDEX idx_deal_task_assignee ON deal_task (assignee_type, assignee_reference);

CREATE TABLE notification (
    id BIGSERIAL PRIMARY KEY,
    deal_id BIGINT REFERENCES deal (id),
    recipient_type VARCHAR(32) NOT NULL,
    recipient_reference VARCHAR(255) NOT NULL,
    title VARCHAR(255) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    read BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_notification_recipient ON notification (recipient_type, recipient_reference);
CREATE INDEX idx_notification_deal ON notification (deal_id);

CREATE TABLE dealer_subscription (
    id BIGSERIAL PRIMARY KEY,
    dealer_id BIGINT NOT NULL UNIQUE REFERENCES dealer (id),
    plan VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    monthly_price NUMERIC(12, 2) NOT NULL,
    current_period_start TIMESTAMP WITH TIME ZONE NOT NULL,
    current_period_end TIMESTAMP WITH TIME ZONE NOT NULL,
    auto_renew BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE dealer_invoice (
    id BIGSERIAL PRIMARY KEY,
    dealer_id BIGINT NOT NULL REFERENCES dealer (id),
    invoice_number VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    period_start TIMESTAMP WITH TIME ZONE NOT NULL,
    period_end TIMESTAMP WITH TIME ZONE NOT NULL,
    due_at TIMESTAMP WITH TIME ZONE NOT NULL,
    paid_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_dealer_invoice_dealer ON dealer_invoice (dealer_id);
