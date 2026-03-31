package com.stopforfuel.backend.dto;

import com.stopforfuel.backend.entity.*;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class EAdvanceDTO {
    private Long id;
    private LocalDateTime transactionDate;
    private BigDecimal amount;
    private String advanceType;
    private String remarks;
    private String batchId;
    private String tid;
    private String customerName;
    private String customerPhone;
    private String cardLast4Digit;
    private String bankName;
    private String chequeNo;
    private LocalDate chequeDate;
    private String inFavorOf;
    private String ccmsNumber;
    private UpiCompanySummary upiCompany;
    private InvoiceBillSummary invoiceBill;
    private PaymentSummary payment;
    private Long shiftId;
    private Long scid;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static EAdvanceDTO from(EAdvance e) {
        return EAdvanceDTO.builder()
                .id(e.getId())
                .transactionDate(e.getTransactionDate())
                .amount(e.getAmount())
                .advanceType(e.getAdvanceType() != null ? e.getAdvanceType().name() : null)
                .remarks(e.getRemarks())
                .batchId(e.getBatchId())
                .tid(e.getTid())
                .customerName(e.getCustomerName())
                .customerPhone(e.getCustomerPhone())
                .cardLast4Digit(e.getCardLast4Digit())
                .bankName(e.getBankName())
                .chequeNo(e.getChequeNo())
                .chequeDate(e.getChequeDate())
                .inFavorOf(e.getInFavorOf())
                .ccmsNumber(e.getCcmsNumber())
                .upiCompany(UpiCompanySummary.from(e.getUpiCompany()))
                .invoiceBill(InvoiceBillSummary.from(e.getInvoiceBill()))
                .payment(PaymentSummary.from(e.getPayment()))
                .shiftId(e.getShiftId())
                .scid(e.getScid())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    @Getter
    @Builder
    public static class UpiCompanySummary {
        private Long id;
        private String name;

        public static UpiCompanySummary from(UpiCompany u) {
            if (u == null) return null;
            return UpiCompanySummary.builder().id(u.getId()).name(u.getCompanyName()).build();
        }
    }

    @Getter
    @Builder
    public static class InvoiceBillSummary {
        private Long id;
        private String billNo;

        public static InvoiceBillSummary from(InvoiceBill b) {
            if (b == null) return null;
            return InvoiceBillSummary.builder().id(b.getId()).billNo(b.getBillNo()).build();
        }
    }

    @Getter
    @Builder
    public static class PaymentSummary {
        private Long id;
        private BigDecimal amount;

        public static PaymentSummary from(Payment p) {
            if (p == null) return null;
            return PaymentSummary.builder().id(p.getId()).amount(p.getAmount()).build();
        }
    }
}
