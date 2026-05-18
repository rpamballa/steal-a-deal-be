package com.stealadeal.service.buyer;

import com.stealadeal.domain.Vehicle;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Pure server-side evaluation of a buyer search query against vehicles.
 * Deliberately depends on nothing else (no InventoryService) so it can
 * be reused by saved-search counting and the price-drop producer
 * without creating a dependency cycle. Semantics mirror the frontend
 * inventory filters: make/model = case-insensitive substring; price /
 * year / mileage = inclusive bounds; q = all whitespace tokens must
 * appear in "year make model trim"; status = exact when present.
 */
@Component
public class VehicleSearchMatcher {

    public boolean matches(Vehicle v, BuyerSearchQuery q) {
        if (q == null) {
            return true;
        }
        if (notBlank(q.make()) && !contains(v.getMake(), q.make())) {
            return false;
        }
        if (notBlank(q.model()) && !contains(v.getModel(), q.model())) {
            return false;
        }
        if (q.minPrice() != null && (v.getPrice() == null || v.getPrice().compareTo(q.minPrice()) < 0)) {
            return false;
        }
        if (q.maxPrice() != null && (v.getPrice() == null || v.getPrice().compareTo(q.maxPrice()) > 0)) {
            return false;
        }
        if (q.minYear() != null && v.getModelYear() < q.minYear()) {
            return false;
        }
        if (q.maxMileage() != null && v.getMileage() > q.maxMileage()) {
            return false;
        }
        if (q.status() != null && v.getStatus() != q.status()) {
            return false;
        }
        if (notBlank(q.q())) {
            String hay = (v.getModelYear() + " " + nz(v.getMake()) + " " + nz(v.getModel())
                    + " " + nz(v.getTrim())).toLowerCase();
            for (String token : q.q().toLowerCase().trim().split("\\s+")) {
                if (!token.isBlank() && !hay.contains(token)) {
                    return false;
                }
            }
        }
        return true;
    }

    public long count(List<Vehicle> vehicles, BuyerSearchQuery q) {
        return vehicles.stream().filter(v -> matches(v, q)).count();
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private String nz(String s) {
        return s == null ? "" : s;
    }

    private boolean contains(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase().contains(needle.toLowerCase());
    }
}
