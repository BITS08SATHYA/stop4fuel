package com.stopforfuel.backend.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class TankAnalyticsDTO {

    private String fromDate;
    private String toDate;
    private int rangeDays;
    private int leadTimeDays;
    private double tankerLoadLiters;

    private double totalStock;
    private double totalCapacity;
    private double totalAvgDailySales;
    private double totalDeliveredInRange;
    /** Soonest projected empty date across tanks, null when no tank is depleting */
    private String nextEmptyDate;

    private List<TankRow> tanks;
    private List<DailyPoint> dailyProductSales;
    private List<DailyStockPoint> dailyTankStock;
    private List<MonthlyPoint> monthlyPurchases;

    @Getter
    @Builder
    public static class TankRow {
        private Long tankId;
        private String name;
        private String productName;
        private Double capacity;
        private Double currentStock;
        private Double thresholdStock;
        private Double fillPercent;
        private Double avgDailySales;
        private Double daysToEmpty;
        private Double daysToThreshold;
        private String projectedEmptyDate;
        private String thresholdHitDate;
        private String recommendedOrderDate;
        private Double suggestedOrderLiters;
        private String lastReadingDate;
        /** OK | ORDER_SOON | ORDER_NOW | STAGNANT (no sales in range) */
        private String status;
    }

    @Getter
    @Builder
    public static class DailyPoint {
        private String date;
        private String product;
        private double liters;
    }

    @Getter
    @Builder
    public static class DailyStockPoint {
        private String date;
        private String tank;
        private Double openStock;
        private double delivered;
    }

    @Getter
    @Builder
    public static class MonthlyPoint {
        private String month;
        private double liters;
    }
}
