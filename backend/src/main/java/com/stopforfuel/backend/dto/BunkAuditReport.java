package com.stopforfuel.backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Bunk Audit report. Two parallel views of the same period:
 *
 *  - CashFlow: "did the till gain or lose cash?" — classifies every rupee as
 *    IN / OUT / INTERNAL_TRANSFER. Rolls up from invoices (now including
 *    synthetic cash residuals) and shift-level transactions.
 *  - Profitability: accrual P&L from invoice revenue and per-product WAC cost.
 *
 * Variance, per-product margin, and fuel-received (delivery info) stay as
 * supporting panels.
 */
@Getter
@Setter
public class BunkAuditReport {

    public enum Granularity { SHIFT, DAY, RANGE, MONTH }

    private LocalDate fromDate;
    private LocalDate toDate;
    private Granularity granularity;
    private int shiftCount;

    private CashFlow cashFlow = new CashFlow();
    private Profitability profitability = new Profitability();
    private List<ProductSale> productSales = new ArrayList<>();
    private List<ProductVariance> variance = new ArrayList<>();
    private List<FuelReceived> fuelReceived = new ArrayList<>();

    // ============================== CASH FLOW ==============================

    @Getter @Setter
    public static class CashFlow {
        private CashIn in = new CashIn();
        private CashOut out = new CashOut();
        private InternalTransfers internalTransfers = new InternalTransfers();
        private BigDecimal netPosition = BigDecimal.ZERO; // in − out
    }

    @Getter @Setter
    public static class CashIn {
        /** Sum of cash InvoiceBill.netAmount (explicit cashier-raised + synthetic). */
        private BigDecimal cashInvoices = BigDecimal.ZERO;
        /** Payments against individual credit invoices (money settling receivables). */
        private BigDecimal billPayments = BigDecimal.ZERO;
        /** Payments against statements. */
        private BigDecimal statementPayments = BigDecimal.ZERO;
        /** Owner/manager adding cash to the till (external inflow). */
        private BigDecimal externalInflow = BigDecimal.ZERO;
    }

    @Getter @Setter
    public static class CashOut {
        /** Sum of credit InvoiceBill.netAmount — fuel/stock left, cash not in yet. */
        private BigDecimal creditInvoices = BigDecimal.ZERO;
        /** Electronic payments (card/UPI/etc) carved out of cash receipts at the till. */
        private List<AmountByMode> eAdvances = new ArrayList<>();
        /** Per-type shift expenses. */
        private List<AmountByType> expenses = new ArrayList<>();
        private BigDecimal stationExpenses = BigDecimal.ZERO;
        private BigDecimal incentives = BigDecimal.ZERO;
        private BigDecimal salaryAdvance = BigDecimal.ZERO;
        /** CASH operational advances whose destination is SPENT (or null historic). */
        private BigDecimal cashAdvanceSpent = BigDecimal.ZERO;
        /** Cash returned to external lenders. */
        private BigDecimal inflowRepayments = BigDecimal.ZERO;
        /** Test fuel dispensed — informational; no cash moved. Shown for context. */
        private TestQuantity testQuantity = new TestQuantity();
    }

    @Getter @Setter
    public static class InternalTransfers {
        /** Money taken by management — stays with the business. */
        private BigDecimal managementAdvance = BigDecimal.ZERO;
        /** CASH advances deposited to bank — still with the business, just not in the till. */
        private BigDecimal cashAdvanceBankDeposit = BigDecimal.ZERO;
    }

    // ============================ PROFITABILITY ============================

    @Getter @Setter
    public static class Profitability {
        private BigDecimal grossRevenue = BigDecimal.ZERO;
        private BigDecimal totalCogs = BigDecimal.ZERO;
        private BigDecimal grossProfit = BigDecimal.ZERO;
        private BigDecimal operatingExpenses = BigDecimal.ZERO;
        private BigDecimal netProfit = BigDecimal.ZERO;
        private BigDecimal marginPct = BigDecimal.ZERO;
    }

    // ============================ SUPPORTING SHAPES ============================

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
