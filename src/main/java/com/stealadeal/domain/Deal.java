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
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
public class Deal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Column(nullable = false)
    private String buyerName;

    @Column(nullable = false)
    private String buyerEmail;

    @Column(nullable = false)
    private String buyerPhone;

    @Column(nullable = false)
    private String buyerAddressLine1;

    @Column
    private String buyerAddressLine2;

    @Column(nullable = false)
    private String buyerCity;

    @Column(nullable = false, length = 2)
    private String buyerState;

    @Column(nullable = false)
    private String buyerPostalCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FulfillmentType fulfillmentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FulfillmentStatus fulfillmentStatus;

    @Column
    private OffsetDateTime fulfillmentScheduledAt;

    @Column
    private String fulfillmentLocation;

    @Column(length = 1000)
    private String fulfillmentNotes;

    @Column(nullable = false)
    private boolean tradeIn;

    @Column
    private String tradeInVin;

    @Column
    private Integer tradeInMileage;

    @Column(precision = 12, scale = 2)
    private BigDecimal tradeInOffer;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal vehiclePrice;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal taxAmount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal registrationFee;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal documentationFee;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal deliveryFee;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal depositAmount;

    @Column(nullable = false)
    private boolean depositPaid;

    @Column
    private String depositIntentId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DealStage stage;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public void setVehicle(Vehicle vehicle) {
        this.vehicle = vehicle;
    }

    public String getBuyerName() {
        return buyerName;
    }

    public void setBuyerName(String buyerName) {
        this.buyerName = buyerName;
    }

    public String getBuyerEmail() {
        return buyerEmail;
    }

    public void setBuyerEmail(String buyerEmail) {
        this.buyerEmail = buyerEmail;
    }

    public String getBuyerPhone() {
        return buyerPhone;
    }

    public void setBuyerPhone(String buyerPhone) {
        this.buyerPhone = buyerPhone;
    }

    public String getBuyerAddressLine1() {
        return buyerAddressLine1;
    }

    public void setBuyerAddressLine1(String buyerAddressLine1) {
        this.buyerAddressLine1 = buyerAddressLine1;
    }

    public String getBuyerAddressLine2() {
        return buyerAddressLine2;
    }

    public void setBuyerAddressLine2(String buyerAddressLine2) {
        this.buyerAddressLine2 = buyerAddressLine2;
    }

    public String getBuyerCity() {
        return buyerCity;
    }

    public void setBuyerCity(String buyerCity) {
        this.buyerCity = buyerCity;
    }

    public String getBuyerState() {
        return buyerState;
    }

    public void setBuyerState(String buyerState) {
        this.buyerState = buyerState;
    }

    public String getBuyerPostalCode() {
        return buyerPostalCode;
    }

    public void setBuyerPostalCode(String buyerPostalCode) {
        this.buyerPostalCode = buyerPostalCode;
    }

    public FulfillmentType getFulfillmentType() {
        return fulfillmentType;
    }

    public void setFulfillmentType(FulfillmentType fulfillmentType) {
        this.fulfillmentType = fulfillmentType;
    }

    public FulfillmentStatus getFulfillmentStatus() {
        return fulfillmentStatus;
    }

    public void setFulfillmentStatus(FulfillmentStatus fulfillmentStatus) {
        this.fulfillmentStatus = fulfillmentStatus;
    }

    public OffsetDateTime getFulfillmentScheduledAt() {
        return fulfillmentScheduledAt;
    }

    public void setFulfillmentScheduledAt(OffsetDateTime fulfillmentScheduledAt) {
        this.fulfillmentScheduledAt = fulfillmentScheduledAt;
    }

    public String getFulfillmentLocation() {
        return fulfillmentLocation;
    }

    public void setFulfillmentLocation(String fulfillmentLocation) {
        this.fulfillmentLocation = fulfillmentLocation;
    }

    public String getFulfillmentNotes() {
        return fulfillmentNotes;
    }

    public void setFulfillmentNotes(String fulfillmentNotes) {
        this.fulfillmentNotes = fulfillmentNotes;
    }

    public boolean isTradeIn() {
        return tradeIn;
    }

    public void setTradeIn(boolean tradeIn) {
        this.tradeIn = tradeIn;
    }

    public String getTradeInVin() {
        return tradeInVin;
    }

    public void setTradeInVin(String tradeInVin) {
        this.tradeInVin = tradeInVin;
    }

    public Integer getTradeInMileage() {
        return tradeInMileage;
    }

    public void setTradeInMileage(Integer tradeInMileage) {
        this.tradeInMileage = tradeInMileage;
    }

    public BigDecimal getTradeInOffer() {
        return tradeInOffer;
    }

    public void setTradeInOffer(BigDecimal tradeInOffer) {
        this.tradeInOffer = tradeInOffer;
    }

    public BigDecimal getVehiclePrice() {
        return vehiclePrice;
    }

    public void setVehiclePrice(BigDecimal vehiclePrice) {
        this.vehiclePrice = vehiclePrice;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public void setTaxAmount(BigDecimal taxAmount) {
        this.taxAmount = taxAmount;
    }

    public BigDecimal getRegistrationFee() {
        return registrationFee;
    }

    public void setRegistrationFee(BigDecimal registrationFee) {
        this.registrationFee = registrationFee;
    }

    public BigDecimal getDocumentationFee() {
        return documentationFee;
    }

    public void setDocumentationFee(BigDecimal documentationFee) {
        this.documentationFee = documentationFee;
    }

    public BigDecimal getDeliveryFee() {
        return deliveryFee;
    }

    public void setDeliveryFee(BigDecimal deliveryFee) {
        this.deliveryFee = deliveryFee;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }

    public BigDecimal getDepositAmount() {
        return depositAmount;
    }

    public void setDepositAmount(BigDecimal depositAmount) {
        this.depositAmount = depositAmount;
    }

    public boolean isDepositPaid() {
        return depositPaid;
    }

    public void setDepositPaid(boolean depositPaid) {
        this.depositPaid = depositPaid;
    }

    public String getDepositIntentId() {
        return depositIntentId;
    }

    public void setDepositIntentId(String depositIntentId) {
        this.depositIntentId = depositIntentId;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public DealStage getStage() {
        return stage;
    }

    public void setStage(DealStage stage) {
        this.stage = stage;
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
