package com.stealadeal.domain;

/**
 * Where a {@link VehicleHistoryReport} came from. A third-party VIN
 * provider (Carfax/AutoCheck/...) or a PDF the dealer uploaded. The
 * buyer-facing contract is identical for both.
 */
public enum HistoryReportSource {
    PROVIDER,
    DEALER_UPLOAD
}
