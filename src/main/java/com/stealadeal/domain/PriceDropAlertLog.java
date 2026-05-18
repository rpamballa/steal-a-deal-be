package com.stealadeal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;

/** Debounce ledger: at most one price-drop notification per (buyer, vehicle) per 24h. */
@Entity
@Table(name = "price_drop_alert_log",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "vehicle_id"}))
public class PriceDropAlertLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "vehicle_id", nullable = false)
    private Long vehicleId;

    @Column(name = "last_notified_at", nullable = false)
    private OffsetDateTime lastNotifiedAt;

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getVehicleId() { return vehicleId; }
    public void setVehicleId(Long vehicleId) { this.vehicleId = vehicleId; }
    public OffsetDateTime getLastNotifiedAt() { return lastNotifiedAt; }
    public void setLastNotifiedAt(OffsetDateTime lastNotifiedAt) { this.lastNotifiedAt = lastNotifiedAt; }
}
