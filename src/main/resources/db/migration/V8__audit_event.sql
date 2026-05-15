-- Append-only audit trail for high-value mutations. Required for the
-- F&I/financing compliance story and Series-A diligence.

CREATE TABLE audit_event (
    id BIGSERIAL PRIMARY KEY,
    actor_type VARCHAR(32) NOT NULL,
    actor_reference VARCHAR(255) NOT NULL,
    action VARCHAR(255) NOT NULL,
    entity_type VARCHAR(255) NOT NULL,
    entity_id BIGINT,
    deal_id BIGINT,
    detail VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_audit_event_deal ON audit_event (deal_id);
CREATE INDEX idx_audit_event_actor ON audit_event (actor_reference);
CREATE INDEX idx_audit_event_created ON audit_event (created_at);
