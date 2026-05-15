package com.stealadeal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.OffsetDateTime;

@Entity
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "deal_id")
    private Deal deal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ParticipantType recipientType;

    @Column(nullable = false)
    private String recipientReference;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 1000)
    private String message;

    @Column(nullable = false)
    private boolean read;

    @Column
    private OffsetDateTime dispatchedAt;

    @Column
    private String dispatchChannels;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    public Long getId() {
        return id;
    }

    public Deal getDeal() {
        return deal;
    }

    public void setDeal(Deal deal) {
        this.deal = deal;
    }

    public ParticipantType getRecipientType() {
        return recipientType;
    }

    public void setRecipientType(ParticipantType recipientType) {
        this.recipientType = recipientType;
    }

    public String getRecipientReference() {
        return recipientReference;
    }

    public void setRecipientReference(String recipientReference) {
        this.recipientReference = recipientReference;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public OffsetDateTime getDispatchedAt() {
        return dispatchedAt;
    }

    public void setDispatchedAt(OffsetDateTime dispatchedAt) {
        this.dispatchedAt = dispatchedAt;
    }

    public String getDispatchChannels() {
        return dispatchChannels;
    }

    public void setDispatchChannels(String dispatchChannels) {
        this.dispatchChannels = dispatchChannels;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
