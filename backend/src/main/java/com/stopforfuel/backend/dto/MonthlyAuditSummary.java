package com.stopforfuel.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Compact per-month roll-up for the yearly bunk-audit scorecard.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyAuditSummary {
    private int year;
    private int month;         // 1-12
    private int shiftCount;
    private BigDecimal grossRevenue;
    private BigDecimal totalCogs;
    private BigDecimal grossProfit;
    private BigDecimal operatingExpenses;
    private BigDecimal netProfit;
    private BigDecimal marginPct;
}
