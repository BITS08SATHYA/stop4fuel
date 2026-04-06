package com.stopforfuel.backend.dto;

import com.stopforfuel.backend.entity.PaytmTransaction;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class PaytmTransactionDTO {
    private Long id;
    private String paytmOrderId;
    private String paytmTxnId;
    private LocalDateTime txnDate;
    private BigDecimal txnAmount;
    private String txnStatus;
    private String paytmPaymentMode;
    private String bankName;
    private String bankTxnId;
    private String settlementStatus;
    private LocalDate settlementDate;
    private BigDecimal settlementAmount;
    private String reconStatus;
    private String matchConfidence;
    private Long matchedInvoiceId;
    private Long matchedEAdvanceId;
    private String reconNote;
    private LocalDateTime fetchedAt;
    private LocalDateTime createdAt;

    public static PaytmTransactionDTO from(PaytmTransaction t) {
        return PaytmTransactionDTO.builder()
                .id(t.getId())
                .paytmOrderId(t.getPaytmOrderId())
                .paytmTxnId(t.getPaytmTxnId())
                .txnDate(t.getTxnDate())
                .txnAmount(t.getTxnAmount())
                .txnStatus(t.getTxnStatus())
                .paytmPaymentMode(t.getPaytmPaymentMode())
                .bankName(t.getBankName())
                .bankTxnId(t.getBankTxnId())
                .settlementStatus(t.getSettlementStatus() != null ? t.getSettlementStatus().name() : null)
                .settlementDate(t.getSettlementDate())
                .settlementAmount(t.getSettlementAmount())
                .reconStatus(t.getReconStatus() != null ? t.getReconStatus().name() : null)
                .matchConfidence(t.getMatchConfidence() != null ? t.getMatchConfidence().name() : null)
                .matchedInvoiceId(t.getMatchedInvoiceId())
                .matchedEAdvanceId(t.getMatchedEAdvanceId())
                .reconNote(t.getReconNote())
                .fetchedAt(t.getFetchedAt())
                .createdAt(t.getCreatedAt())
                .build();
    }
}
