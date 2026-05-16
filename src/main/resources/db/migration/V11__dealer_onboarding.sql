-- Automated dealer onboarding tracker. Milestones are derived from
-- observable system state by DealerOnboardingProcessor; nudges fire
-- through the existing notification outbox when a dealer is stuck.

CREATE TABLE dealer_onboarding (
    id BIGSERIAL PRIMARY KEY,
    dealer_id BIGINT NOT NULL UNIQUE REFERENCES dealer (id),
    stage VARCHAR(32) NOT NULL,
    current_stage_since TIMESTAMP WITH TIME ZONE NOT NULL,
    registered_at TIMESTAMP WITH TIME ZONE,
    approved_at TIMESTAMP WITH TIME ZONE,
    user_created_at TIMESTAMP WITH TIME ZONE,
    subscription_active_at TIMESTAMP WITH TIME ZONE,
    inventory_live_at TIMESTAMP WITH TIME ZONE,
    first_lead_at TIMESTAMP WITH TIME ZONE,
    first_deal_at TIMESTAMP WITH TIME ZONE,
    activated_at TIMESTAMP WITH TIME ZONE,
    last_nudged_stage VARCHAR(32),
    last_nudged_at TIMESTAMP WITH TIME ZONE,
    nudge_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_dealer_onboarding_stage ON dealer_onboarding (stage);
