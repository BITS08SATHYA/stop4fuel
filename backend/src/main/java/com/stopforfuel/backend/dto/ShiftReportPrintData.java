package com.stopforfuel.backend.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class ShiftReportPrintData {

    // Header info
    private String companyName;
    private String companyAddress;
    private String companyGstNo;
    private String companyPhone;
    private String companyEmail;
    private String employeeName;
    private Long shiftId;
    private LocalDateTime shiftStart;
    private LocalDateTime shiftEnd;
    private String reportStatus;

    // Front Page - Left Column
    private List<MeterReading> meterReadings = new ArrayList<>();
    private List<TankReading> tankReadings = new ArrayList<>();
    private List<SalesDifference> salesDifferences = new ArrayList<>();

    // Front Page - Right Column (from ShiftClosingReport line items - cumulatives)
    // These come from the existing report entity

    // Front Page - Cash Bill Breakdown (from ShiftClosingReport)
    // These come from the existing report entity

    // Back Page - Left Column
    private List<CashBillDetail> cashBillDetails = new ArrayList<>();
    private List<CreditBillDetail> creditBillDetails = new ArrayList<>();
    private List<StockSummaryRow> stockSummary = new ArrayList<>();

    // Stock Position (godown + cashier balances at shift close)
    private List<StockPositionRow> stockPosition = new ArrayList<>();

    // Payment mode breakdown (cash bill amounts by payment mode)
    private List<PaymentModeBreakdown> paymentModeBreakdown = new ArrayList<>();

    // Back Page - Right Column
    private List<AdvanceEntryDetail> advanceEntries = new ArrayList<>();
    private List<PaymentEntryDetail> paymentEntries = new ArrayList<>();

    @Data
    public static class MeterReading {
        private String pumpName;
        private String nozzleName;
        private String productName;
        private Double openReading;
        private Double closeReading;
        private Double sales;
        private Double testQuantity;
        private Double rate;
        private Double amount;
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
    public static class SalesDifference {
        private String productName;
        private Double tankSale;
        private Double meterSale;
        private Double difference;
    }

    @Data
    public static class CashBillDetail {
        private String billNo;
        private String vehicleNo;
        private String driverName;
        private String products; // compact format: "P:500 HSD:200"
        private String paymentMode;
        private BigDecimal amount;
    }

    @Data
    public static class CreditBillDetail {
        private String customerName;
        private String billNo;
        private String vehicleNo;
        private String products; // compact format: "P:500 HSD:200"
        private BigDecimal amount;
    }

    @Data
    public static class StockSummaryRow {
        private String productName;
        private Double openStock;
        private Double receipt;
        private Double totalStock;
        private Double sales;
        private BigDecimal rate;
        private BigDecimal amount; // sales x rate
    }

    @Data
    public static class AdvanceEntryDetail {
        private String type; // CARD, UPI, CCMS, BANK, CHEQUE, CASH_ADVANCE, HOME_ADVANCE, EXPENSE, INCENTIVE, SALARY_ADVANCE, INFLOW_REPAYMENT
        private String description;
        private BigDecimal amount;
        private String reference;
    }

    @Data
    public static class StockPositionRow {
        private String productName;
        private Double godownStock;
        private Double cashierStock;
        private Double totalStock;
        private boolean lowStock; // true if godown stock <= reorder level
    }

    @Data
    public static class PaymentModeBreakdown {
        private String mode; // CASH, CARD, UPI, CCMS, CHEQUE, BANK
        private BigDecimal amount = BigDecimal.ZERO;
        private int billCount;
    }

    @Data
    public static class PaymentEntryDetail {
        private String type; // BILL or STATEMENT
        private String customerName;
        private String reference; // bill no or statement no
        private String paymentMode;
        private BigDecimal amount;
    }
}
