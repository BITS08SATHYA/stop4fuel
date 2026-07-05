package com.stopforfuel.backend.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class CustomerConsumptionDTO {

    private Long customerId;
    private String name;
    private String fromDate;
    private String toDate;
    private List<MonthlyPoint> monthly;
    private List<ProductShare> productMix;

    @Getter
    @Builder
    public static class MonthlyPoint {
        private String month;
        private BigDecimal quantity;
        private BigDecimal amount;
    }

    @Getter
    @Builder
    public static class ProductShare {
        private String product;
        private BigDecimal quantity;
        private BigDecimal amount;
    }
}
