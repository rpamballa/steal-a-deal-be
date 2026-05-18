package com.stealadeal.service.buyer;

import com.stealadeal.domain.Vehicle;
import java.math.BigDecimal;

/**
 * Seam invoked by InventoryService when a LIVE vehicle's price
 * decreases. The real implementation notifies buyers who favorited the
 * vehicle or have a matching price-drop saved search. A no-op default
 * keeps InventoryService usable when buyer engagement is not wired.
 */
public interface PriceDropWatcher {
    void onPriceDrop(Vehicle vehicle, BigDecimal previousPrice, BigDecimal newPrice);
}
