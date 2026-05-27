package com.stopforfuel.backend.event;

import lombok.Value;

import java.math.BigDecimal;

/**
 * Published after a FUEL product's price changes and a snapshot row is written.
 * Consumed (AFTER_COMMIT) to auto-recompute any non-FINALIZED shift closing
 * reports for the same tenant so cashiers don't see stale rates.
 */
@Value
public class FuelPriceChangedEvent {
    Long scid;
    Long productId;
    BigDecimal previousPrice;
    BigDecimal newPrice;
}
