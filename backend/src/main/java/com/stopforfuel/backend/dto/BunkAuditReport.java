package com.stopforfuel.backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Bunk Audit / P&L report. Aggregates one or more shifts into a single money view:
 * everything that came IN, everything that went OUT, physical variance, and a
 * profit verdict. Returned by BunkAuditService#compute.
 */
@Getter
@Setter
public class BunkAuditReport {

    public enum Granularity { SHIFT, DAY, RANGE, MONTH }

    private LocalDate fromDate;
    private LocalDate toDate;
    private Granularity granularity;
    private int shiftCount;

    private Inputs inputs = new Inputs();
    private Outputs outputs = new Outputs();
    private List<ProductVariance> variance = new ArrayList<>();
    private Profitability profitability = new Profitability();

    @Getter @Setter
    public static class Inputs {
        private List<FuelReceived> fuelReceived = new ArrayList<>();
        private List<AmountByMode> cashReceived = new ArrayList<>();
        private BigDecimal creditBilled = BigDecimal.ZERO;
        private BigDecimal creditCollected = BigDecimal.ZERO;
        private BigDecimal externalInflow = BigDecimal.ZERO;
        private List<AmountByMode> eAdvances = new ArrayList<>();
    }

    @Getter @Setter
    public static class Outputs {
        private List<ProductSale> fuelSold = new ArrayList<>();
        private List<ProductSale> oilSold = new ArrayList<>();
        private List<AmountByType> opAdvances = new ArrayList<>();
        private List<AmountByType> expenses = new ArrayList<>();
        private BigDecimal stationExpenses = BigDecimal.ZERO;
        private BigDecimal incentives = BigDecimal.ZERO;
        private TestQuantity testQuantity = new TestQuantity();
    }

    @Getter @Setter
    public static class Profitability {
        private BigDecimal grossRevenue = BigDecimal.ZERO;
        private BigDecimal totalCogs = BigDecimal.ZERO;
        private BigDecimal grossProfit = BigDecimal.ZERO;
        private BigDecimal operatingExpenses = BigDecimal.ZERO;
        private BigDecimal netProfit = BigDecimal.ZERO;
        private BigDecimal marginPct = BigDecimal.ZERO;
    }

    @Getter @Setter
    public static class FuelReceived {
        private String productName;
        private double litres;
        private BigDecimal purchaseAmount = BigDecimal.ZERO;
    }

    @Getter @Setter
    public static class ProductSale {
        private String productName;
        private double quantity;
        private BigDecimal revenue = BigDecimal.ZERO;
        private BigDecimal cogs = BigDecimal.ZERO;
        private BigDecimal margin = BigDecimal.ZERO;
        private BigDecimal marginPct = BigDecimal.ZERO;
    }

    @Getter @Setter
    public static class AmountByMode {
        private String mode;
        private BigDecimal amount = BigDecimal.ZERO;
        private int count;
    }

    @Getter @Setter
    public static class AmountByType {
        private String type;
        private BigDecimal amount = BigDecimal.ZERO;
    }

    @Getter @Setter
    public static class TestQuantity {
        private double litres;
        private BigDecimal amount = BigDecimal.ZERO;
    }

    @Getter @Setter
    public static class ProductVariance {
        private String productName;
        private double expectedLitres; // tank sale
        private double actualLitres;   // meter sale
        private double shrinkageLitres;
        private BigDecimal shrinkagePct = BigDecimal.ZERO;
        private boolean flagged;
    }
}
