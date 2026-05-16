package com.stealadeal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * An F&I product attached to a deal. Price and revenue-share rate are
 * snapshotted at attach time so later catalog edits do not retroactively
 * change a buyer's signed deal or the booked platform revenue.
 */
@Entity
@Table(name = "deal_f_and_i_product")
public class DealFAndIProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "deal_id", nullable = false)
    private Deal deal;

    @ManyToOne(optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private FAndIProduct product;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, precision = 6, scale = 5)
    private BigDecimal revenueShareRate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal revenueShareAmount;

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

    public FAndIProduct getProduct() {
        return product;
    }

    public void setProduct(FAndIProduct product) {
        this.product = product;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getRevenueShareRate() {
        return revenueShareRate;
    }

    public void setRevenueShareRate(BigDecimal revenueShareRate) {
        this.revenueShareRate = revenueShareRate;
    }

    public BigDecimal getRevenueShareAmount() {
        return revenueShareAmount;
    }

    public void setRevenueShareAmount(BigDecimal revenueShareAmount) {
        this.revenueShareAmount = revenueShareAmount;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
