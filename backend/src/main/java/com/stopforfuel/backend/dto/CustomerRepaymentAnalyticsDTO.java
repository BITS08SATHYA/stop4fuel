package com.stopforfuel.backend.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class CustomerRepaymentAnalyticsDTO {

    private String fromDate;
    private String toDate;
    private int rangeDays;

    private BigDecimal totalBilled;
    private BigDecimal totalCollected;
    private BigDecimal totalOutstanding;
    private Double avgRepaymentLagDays;
    private int overdueCustomers;
    private int activeCreditCustomers;

    private int quietCustomers;

    /** Calendar dates (ISO) for the daily activity strips — same length for every row's dailyLiters. */
    private List<String> dailyDates;

    private List<MonthlyPoint> monthlyTurnover;
    private List<LagBucket> lagHistogram;
    private List<Row> customers;

    @Getter
    @Builder
    public static class MonthlyPoint {
        private String month;
        private BigDecimal billed;
        private BigDecimal collected;
    }

    @Getter
    @Builder
    public static class LagBucket {
        private String bucket;
        private int count;
    }

    @Getter
    @Builder
    public static class Row {
        private Long customerId;
        private String name;
        /** STATEMENT | LOCAL — Party.partyType, with statements as backstop (matches LedgerService) */
        private String partyType;
        private Integer repaymentDaysAllowed;
        private BigDecimal billedInRange;
        private BigDecimal litersInRange;
        private long billCount;
        private BigDecimal outstanding;
        private Integer oldestUnpaidDays;
        /** amount-weighted average days from statement date to payment, within range */
        private Double avgRepaymentLagDays;
        /** % of statement payments made within the allowed window */
        private Double onTimePercent;
        /** avg billed per equal-length period over the 3 periods before the range */
        private BigDecimal prevAvgBilled;
        /** % change of current billed vs prevAvgBilled; null without a baseline */
        private Double changePercent;
        /** MORE | LESS | NORMAL | NEW (no baseline) */
        private String consumptionTrend;
        private boolean overdue;
        /** Liters billed per day, aligned to the top-level dailyDates */
        private List<BigDecimal> dailyLiters;
        private String lastBillDate;
        private Integer daysSinceLastBill;
        /** Median days between fills over the cadence window; null with too little history */
        private Double typicalIntervalDays;
        /** True when silent for ≥2× the customer's own typical fill interval */
        private boolean quiet;
    }
}
