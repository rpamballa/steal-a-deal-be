package com.stealadeal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import java.time.OffsetDateTime;

@Entity
public class DealerOnboarding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "dealer_id", nullable = false, unique = true)
    private Dealer dealer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OnboardingStage stage = OnboardingStage.REGISTERED;

    @Column(nullable = false)
    private OffsetDateTime currentStageSince;

    @Column
    private OffsetDateTime registeredAt;

    @Column
    private OffsetDateTime approvedAt;

    @Column
    private OffsetDateTime userCreatedAt;

    @Column
    private OffsetDateTime subscriptionActiveAt;

    @Column
    private OffsetDateTime inventoryLiveAt;

    @Column
    private OffsetDateTime firstLeadAt;

    @Column
    private OffsetDateTime firstDealAt;

    @Column
    private OffsetDateTime activatedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private OnboardingStage lastNudgedStage;

    @Column
    private OffsetDateTime lastNudgedAt;

    @Column(nullable = false)
    private int nudgeCount;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public Dealer getDealer() {
        return dealer;
    }

    public void setDealer(Dealer dealer) {
        this.dealer = dealer;
    }

    public OnboardingStage getStage() {
        return stage;
    }

    public void setStage(OnboardingStage stage) {
        this.stage = stage;
    }

    public OffsetDateTime getCurrentStageSince() {
        return currentStageSince;
    }

    public void setCurrentStageSince(OffsetDateTime currentStageSince) {
        this.currentStageSince = currentStageSince;
    }

    public OffsetDateTime getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(OffsetDateTime registeredAt) {
        this.registeredAt = registeredAt;
    }

    public OffsetDateTime getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(OffsetDateTime approvedAt) {
        this.approvedAt = approvedAt;
    }

    public OffsetDateTime getUserCreatedAt() {
        return userCreatedAt;
    }

    public void setUserCreatedAt(OffsetDateTime userCreatedAt) {
        this.userCreatedAt = userCreatedAt;
    }

    public OffsetDateTime getSubscriptionActiveAt() {
        return subscriptionActiveAt;
    }

    public void setSubscriptionActiveAt(OffsetDateTime subscriptionActiveAt) {
        this.subscriptionActiveAt = subscriptionActiveAt;
    }

    public OffsetDateTime getInventoryLiveAt() {
        return inventoryLiveAt;
    }

    public void setInventoryLiveAt(OffsetDateTime inventoryLiveAt) {
        this.inventoryLiveAt = inventoryLiveAt;
    }

    public OffsetDateTime getFirstLeadAt() {
        return firstLeadAt;
    }

    public void setFirstLeadAt(OffsetDateTime firstLeadAt) {
        this.firstLeadAt = firstLeadAt;
    }

    public OffsetDateTime getFirstDealAt() {
        return firstDealAt;
    }

    public void setFirstDealAt(OffsetDateTime firstDealAt) {
        this.firstDealAt = firstDealAt;
    }

    public OffsetDateTime getActivatedAt() {
        return activatedAt;
    }

    public void setActivatedAt(OffsetDateTime activatedAt) {
        this.activatedAt = activatedAt;
    }

    public OnboardingStage getLastNudgedStage() {
        return lastNudgedStage;
    }

    public void setLastNudgedStage(OnboardingStage lastNudgedStage) {
        this.lastNudgedStage = lastNudgedStage;
    }

    public OffsetDateTime getLastNudgedAt() {
        return lastNudgedAt;
    }

    public void setLastNudgedAt(OffsetDateTime lastNudgedAt) {
        this.lastNudgedAt = lastNudgedAt;
    }

    public int getNudgeCount() {
        return nudgeCount;
    }

    public void setNudgeCount(int nudgeCount) {
        this.nudgeCount = nudgeCount;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
