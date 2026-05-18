package com.stealadeal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "saved_search")
public class SavedSearch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String name;

    @Column(name = "q")
    private String q;

    @Column(name = "search_make")
    private String make;

    @Column(name = "search_model")
    private String model;

    @Column(name = "min_price", precision = 12, scale = 2)
    private BigDecimal minPrice;

    @Column(name = "max_price", precision = 12, scale = 2)
    private BigDecimal maxPrice;

    @Column(name = "min_year")
    private Integer minYear;

    @Column(name = "max_mileage")
    private Integer maxMileage;

    @Enumerated(EnumType.STRING)
    @Column(name = "search_status")
    private VehicleStatus status;

    @Column(name = "alert_on_price_drop", nullable = false)
    private boolean alertOnPriceDrop;

    @Column(name = "last_matched_count", nullable = false)
    private int lastMatchedCount;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getQ() { return q; }
    public void setQ(String q) { this.q = q; }
    public String getMake() { return make; }
    public void setMake(String make) { this.make = make; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public BigDecimal getMinPrice() { return minPrice; }
    public void setMinPrice(BigDecimal minPrice) { this.minPrice = minPrice; }
    public BigDecimal getMaxPrice() { return maxPrice; }
    public void setMaxPrice(BigDecimal maxPrice) { this.maxPrice = maxPrice; }
    public Integer getMinYear() { return minYear; }
    public void setMinYear(Integer minYear) { this.minYear = minYear; }
    public Integer getMaxMileage() { return maxMileage; }
    public void setMaxMileage(Integer maxMileage) { this.maxMileage = maxMileage; }
    public VehicleStatus getStatus() { return status; }
    public void setStatus(VehicleStatus status) { this.status = status; }
    public boolean isAlertOnPriceDrop() { return alertOnPriceDrop; }
    public void setAlertOnPriceDrop(boolean alertOnPriceDrop) { this.alertOnPriceDrop = alertOnPriceDrop; }
    public int getLastMatchedCount() { return lastMatchedCount; }
    public void setLastMatchedCount(int lastMatchedCount) { this.lastMatchedCount = lastMatchedCount; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
