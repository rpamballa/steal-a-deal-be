-- F&I product catalog + per-deal attachments (pitch revenue stream #3:
-- warranty / GAP / protection products with a platform revenue share).

CREATE TABLE f_and_i_product (
    id BIGSERIAL PRIMARY KEY,
    type VARCHAR(32) NOT NULL,
    name VARCHAR(255) NOT NULL,
    retail_price NUMERIC(12, 2) NOT NULL,
    revenue_share_rate NUMERIC(6, 5) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE deal_f_and_i_product (
    id BIGSERIAL PRIMARY KEY,
    deal_id BIGINT NOT NULL REFERENCES deal (id),
    product_id BIGINT NOT NULL REFERENCES f_and_i_product (id),
    price NUMERIC(12, 2) NOT NULL,
    revenue_share_rate NUMERIC(6, 5) NOT NULL,
    revenue_share_amount NUMERIC(12, 2) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_deal_fni_deal ON deal_f_and_i_product (deal_id);
CREATE INDEX idx_deal_fni_product ON deal_f_and_i_product (product_id);
