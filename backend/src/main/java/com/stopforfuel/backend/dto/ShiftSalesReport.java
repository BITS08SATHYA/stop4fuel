package com.stopforfuel.backend.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class ShiftSalesReport {

    private Long shiftId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String attendantName;
    private String status;

    private List<MeterReading> meterwise;
    private List<TankReading> tankwise;
    private List<ProductSale> items;
    private TurnoverSummary turnover;
    private Map<String, BigDecimal> advances;
    private List<CashBillBreakdown> cashBillLiters;
    private List<SalesBreakdown> salesBreakdown;
    private List<TankMeterDiff> salesDifference;
    private List<StockSummary> stockSummary;

    @Data
    public static class MeterReading {
        private String pumpName;
        private String nozzleName;
        private String productName;
        private Double openReading;
        private Double closeReading;
        private Double sales;
    }

    @Data
    public static class TankReading {
        private String tankName;
        private String productName;
        private String openDip;
        private Double openStock;
        private Double incomeStock;
        private Double totalStock;
        private String closeDip;
        private Double closeStock;
        private Double saleStock;
    }

    @Data
    public static class ProductSale {
        private String productName;
        private Double liters;
        private BigDecimal rate;
        private BigDecimal amount;
    }

    @Data
    public static class TurnoverSummary {
        private BigDecimal totalRevenue = BigDecimal.ZERO;
        private BigDecimal balance = BigDecimal.ZERO;
        private BigDecimal cashBillAmount = BigDecimal.ZERO;
        private BigDecimal creditBillAmount = BigDecimal.ZERO;
    }

    @Data
    public static class CashBillBreakdown {
        private String productName;
        private Double cashLiters = 0.0;
        private Double ccmsLiters = 0.0;
        private Double cardLiters = 0.0;
        private Double upiLiters = 0.0;
        private Double totalLiters = 0.0;
    }

    @Data
    public static class SalesBreakdown {
        private String productName;
        private Double creditQty = 0.0;
        private BigDecimal creditAmt = BigDecimal.ZERO;
        private Double cashQty = 0.0;
        private BigDecimal cashAmt = BigDecimal.ZERO;
        private Double grossQty = 0.0;
        private BigDecimal grossAmt = BigDecimal.ZERO;
        private Double netQty = 0.0;
        private BigDecimal netAmt = BigDecimal.ZERO;
    }

    @Data
    public static class TankMeterDiff {
        private String tankName;
        private String productName;
        private Double tankSale;
        private Double meterSale;
        private Double difference;
    }

    @Data
    public static class StockSummary {
        private String productName;
        private BigDecimal price;
        private Double sales;
        private Double availableStock;
    }
}
