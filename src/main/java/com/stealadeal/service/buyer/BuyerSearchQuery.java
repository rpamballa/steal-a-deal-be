package com.stealadeal.service.buyer;

import com.stealadeal.domain.VehicleStatus;
import java.math.BigDecimal;

/** Mirrors the frontend inventory filter shape exactly. All fields optional. */
public record BuyerSearchQuery(
        String q,
        String make,
        String model,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Integer minYear,
        Integer maxMileage,
        VehicleStatus status
) {
}
