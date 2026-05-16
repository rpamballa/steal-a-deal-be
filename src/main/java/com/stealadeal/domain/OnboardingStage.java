package com.stealadeal.domain;

/**
 * Dealer onboarding milestones, ordered. Each stage is derived from
 * observable system state, never set manually. ACTIVATED is terminal
 * (the dealer has closed at least one deal end-to-end).
 */
public enum OnboardingStage {
    REGISTERED,
    APPROVED,
    USER_CREATED,
    SUBSCRIPTION_ACTIVE,
    INVENTORY_LIVE,
    FIRST_LEAD,
    FIRST_DEAL,
    ACTIVATED
}
