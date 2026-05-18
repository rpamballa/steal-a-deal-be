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

/**
 * A vehicle history summary surfaced to buyers BEFORE purchase. At most
 * one row per vehicle. The summary columns are all nullable: a
 * dealer-uploaded PDF carries the artifact without a parsed summary,
 * while a provider-sourced report can populate the structured fields.
 */
@Entity
public class VehicleHistoryReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false, unique = true)
    private Vehicle vehicle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private HistoryReportSource source;

    @Column(length = 64)
    private String providerName;

    /** Storage key for a dealer-uploaded PDF (null for provider links). */
    @Column(length = 128)
    private String storageKey;

    /** Externally hosted full-report URL (provider) when applicable. */
    @Column(length = 512)
    private String externalReportUrl;

    @Column
    private OffsetDateTime generatedAt;

    @Column
    private Integer ownerCount;

    @Column
    private Integer accidentCount;

    @Column(length = 32)
    private String titleBrand;

    @Column
    private Integer lastReportedOdometer;

    @Column
    private Boolean odometerRollbackSuspected;

    @Column
    private Integer openRecallCount;

    @Column
    private Integer serviceRecordCount;

    public Long getId() {
        return id;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public void setVehicle(Vehicle vehicle) {
        this.vehicle = vehicle;
    }

    public HistoryReportSource getSource() {
        return source;
    }

    public void setSource(HistoryReportSource source) {
        this.source = source;
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public String getExternalReportUrl() {
        return externalReportUrl;
    }

    public void setExternalReportUrl(String externalReportUrl) {
        this.externalReportUrl = externalReportUrl;
    }

    public OffsetDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(OffsetDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }

    public Integer getOwnerCount() {
        return ownerCount;
    }

    public void setOwnerCount(Integer ownerCount) {
        this.ownerCount = ownerCount;
    }

    public Integer getAccidentCount() {
        return accidentCount;
    }

    public void setAccidentCount(Integer accidentCount) {
        this.accidentCount = accidentCount;
    }

    public String getTitleBrand() {
        return titleBrand;
    }

    public void setTitleBrand(String titleBrand) {
        this.titleBrand = titleBrand;
    }

    public Integer getLastReportedOdometer() {
        return lastReportedOdometer;
    }

    public void setLastReportedOdometer(Integer lastReportedOdometer) {
        this.lastReportedOdometer = lastReportedOdometer;
    }

    public Boolean getOdometerRollbackSuspected() {
        return odometerRollbackSuspected;
    }

    public void setOdometerRollbackSuspected(Boolean odometerRollbackSuspected) {
        this.odometerRollbackSuspected = odometerRollbackSuspected;
    }

    public Integer getOpenRecallCount() {
        return openRecallCount;
    }

    public void setOpenRecallCount(Integer openRecallCount) {
        this.openRecallCount = openRecallCount;
    }

    public Integer getServiceRecordCount() {
        return serviceRecordCount;
    }

    public void setServiceRecordCount(Integer serviceRecordCount) {
        this.serviceRecordCount = serviceRecordCount;
    }
}
