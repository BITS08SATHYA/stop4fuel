package com.stopforfuel.backend.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class PaytmReconSummaryDTO {
    private long totalTransactions;
    private long matchedCount;
    private long unmatchedPaytmCount;
    private long unmatchedInvoiceCount;
    private long disputedCount;
    private BigDecimal totalPaytmAmount;
    private BigDecimal totalInvoiceAmount;
    private BigDecimal settledAmount;
    private BigDecimal pendingSettlementAmount;
}
