package com.stopforfuel.backend.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class DayWiseStatementPreview {
    private Long customerId;
    private String customerName;
    private LocalDate fromDate;
    private LocalDate toDate;
    private BigDecimal maxAmount;
    private int totalBills;
    private BigDecimal grandTotal;
    private List<DayBucket> days;
    private List<SplitGroup> suggestedSplits;

    @Getter
    @Builder
    public static class DayBucket {
        private LocalDate date;
        private int billCount;
        private BigDecimal dayTotal;
        private BigDecimal cumulativeTotal;
        private List<InvoiceBillDTO> bills;
    }

    @Getter
    @Builder
    public static class SplitGroup {
        private int index;
        private LocalDate fromDate;
        private LocalDate toDate;
        private int billCount;
        private List<Long> billIds;
        private BigDecimal total;
        private boolean exceedsCap;
    }
}
