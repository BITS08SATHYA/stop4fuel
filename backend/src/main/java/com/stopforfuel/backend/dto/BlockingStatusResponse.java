package com.stopforfuel.backend.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Aggregated "why is this customer blocked?" evaluation for the cashier UI.
 * Composes every gate an invoice must pass through (customer status, credit ₹,
 * credit liters, aging, vehicle status, vehicle monthly liters) into a single
 * structured payload the frontend renders as a gate-checklist panel.
 */
@Getter
@Builder
public class BlockingStatusResponse {

    private Long customerId;
    private String customerName;
    private String overall;            // PASS | WARN | BLOCKED | OVERRIDE
    private boolean forceUnblocked;
    private String primaryReason;
    private String suggestedAction;
    private List<Gate> gates;

    @Getter
    @Builder
    public static class Gate {
        private String key;            // CUSTOMER_STATUS | CREDIT_AMOUNT | CREDIT_LITERS | AGING | VEHICLE_STATUS | VEHICLE_MONTHLY_LITERS
        private String label;
        private String state;          // PASS | WARN | FAIL | SKIPPED
        private Object value;          // BigDecimal, String (status), or Long (days) — serialized raw
        private Object limit;
        private String detail;
        private Integer progressPercent; // null if not applicable
    }
}
