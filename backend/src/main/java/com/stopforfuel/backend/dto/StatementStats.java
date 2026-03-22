package com.stopforfuel.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StatementStats {
    private long statementsLastMonth;
    private long paidLastMonth;
    private BigDecimal amountGeneratedLastMonth;
    private BigDecimal amountCollectedLastMonth;
    private long totalStatements;
    private long totalPaid;
    private double paidPercentage;
    private BigDecimal totalUnpaidAmount;
    private BigDecimal totalNetAmount;
    private BigDecimal totalReceivedAmount;
    private double collectionRate;
    private BigDecimal avgStatementAmount;
}
