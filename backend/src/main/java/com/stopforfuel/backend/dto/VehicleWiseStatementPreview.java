package com.stopforfuel.backend.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Preview of a customer's unlinked credit bills grouped by vehicle, with each vehicle further
 * split into statement-sized groups so no group exceeds its liter ceiling. The UI renders the
 * suggested splits, lets the user adjust/exclude them, then submits the chosen billId groups to
 * the existing generate-batch endpoint (reportLayout = VEHICLE_WISE).
 */
@Getter
@Builder
public class VehicleWiseStatementPreview {
    private Long customerId;
    private String customerName;
    private LocalDate fromDate;
    private LocalDate toDate;
    /** Customer-level default ceiling (for display); per-vehicle effectiveCeiling may differ. */
    private BigDecimal defaultCeiling;
    private int totalBills;
    private BigDecimal grandTotal;
    private List<VehicleBucket> vehicles;

    @Getter
    @Builder
    public static class VehicleBucket {
        private Long vehicleId;        // null for bills with no vehicle
        private String vehicleNumber;  // "(no vehicle)" when vehicleId is null
        private BigDecimal effectiveCeiling; // null = no cap for this vehicle
        private int billCount;
        private BigDecimal totalLiters;
        private BigDecimal total;       // net amount across all the vehicle's bills (rounded)
        private List<InvoiceBillDTO> bills;
        private List<SplitGroup> suggestedSplits;
    }

    @Getter
    @Builder
    public static class SplitGroup {
        private int index;
        private int billCount;
        private List<Long> billIds;
        private BigDecimal total;        // net amount (rounded)
        private BigDecimal totalLiters;
        private boolean exceedsCeiling;  // true when a lone bill's own liters exceed the ceiling
    }
}
