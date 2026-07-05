package com.stopforfuel.backend.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class ProductSalesHistoryDTO {

    private Long productId;
    private String productName;
    private String unit;
    private String fromDate;
    private String toDate;
    private String granularity;
    private BigDecimal totalQuantity;
    private BigDecimal totalAmount;
    private List<Point> points;

    @Getter
    @Builder
    public static class Point {
        private String date;
        private BigDecimal quantity;
        private BigDecimal amount;
    }
}
