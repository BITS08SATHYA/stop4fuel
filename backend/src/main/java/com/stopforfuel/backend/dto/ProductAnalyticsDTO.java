package com.stopforfuel.backend.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class ProductAnalyticsDTO {

    private String fromDate;
    private String toDate;
    private int rangeDays;

    private int totalProducts;
    private int belowReorderCount;
    private int outOfStockCount;
    private BigDecimal totalStockValue;
    private BigDecimal totalSoldQuantity;
    private BigDecimal totalSoldAmount;

    private List<Row> products;

    @Getter
    @Builder
    public static class Row {
        private Long productId;
        private String name;
        private String brand;
        private String category;
        private String unit;
        private BigDecimal price;

        private Double godownStock;
        private Double cashierStock;
        private Double totalStock;
        private Double reorderLevel;
        private Double maxStock;
        private BigDecimal stockValue;

        private BigDecimal soldQuantity;
        private BigDecimal soldAmount;
        private BigDecimal avgDailyQuantity;
        /** null when the product has never sold in the range (cannot project) */
        private Double daysOfStock;
        private Double suggestedOrderQty;
        private String lastSaleDate;
        /** OK | LOW | OUT | STALE (no sales in 30+ days) */
        private String stockStatus;
    }
}
