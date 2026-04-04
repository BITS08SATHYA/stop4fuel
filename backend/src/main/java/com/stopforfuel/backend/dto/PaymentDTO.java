package com.stopforfuel.backend.dto;

import com.stopforfuel.backend.entity.*;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class PaymentDTO {
    private Long id;
    private LocalDateTime paymentDate;
    private BigDecimal amount;
    private String referenceNo;
    private String remarks;
    private String proofImageKey;
    private Long shiftId;
    private String paymentMode;
    private CustomerSummary customer;
    private StatementSummary statement;
    private InvoiceBillSummary invoiceBill;
    private UserSummary receivedBy;
    private Long scid;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static PaymentDTO from(Payment p) {
        return PaymentDTO.builder()
                .id(p.getId())
                .paymentDate(p.getPaymentDate())
                .amount(p.getAmount())
                .referenceNo(p.getReferenceNo())
                .remarks(p.getRemarks())
                .proofImageKey(p.getProofImageKey())
                .shiftId(p.getShiftId())
                .paymentMode(p.getPaymentMode() != null ? p.getPaymentMode().name() : null)
                .customer(CustomerSummary.from(p.getCustomer()))
                .statement(StatementSummary.from(p.getStatement()))
                .invoiceBill(InvoiceBillSummary.from(p.getInvoiceBill()))
                .receivedBy(UserSummary.from(p.getReceivedBy()))
                .scid(p.getScid())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }

    @Getter
    @Builder
    public static class CustomerSummary {
        private Long id;
        private String name;
        private String username;

        public static CustomerSummary from(Customer c) {
            if (c == null) return null;
            return CustomerSummary.builder().id(c.getId()).name(c.getName()).username(c.getUsername()).build();
        }
    }

    @Getter
    @Builder
    public static class StatementSummary {
        private Long id;
        private String statementNo;
        private BigDecimal netAmount;
        private BigDecimal receivedAmount;
        private BigDecimal balanceAmount;

        public static StatementSummary from(Statement s) {
            if (s == null) return null;
            return StatementSummary.builder()
                    .id(s.getId())
                    .statementNo(s.getStatementNo())
                    .netAmount(s.getNetAmount())
                    .receivedAmount(s.getReceivedAmount())
                    .balanceAmount(s.getBalanceAmount())
                    .build();
        }
    }

    @Getter
    @Builder
    public static class InvoiceBillSummary {
        private Long id;
        private String billNo;
        private BigDecimal netAmount;

        public static InvoiceBillSummary from(InvoiceBill b) {
            if (b == null) return null;
            return InvoiceBillSummary.builder()
                    .id(b.getId())
                    .billNo(b.getBillNo())
                    .netAmount(b.getNetAmount())
                    .build();
        }
    }

    @Getter
    @Builder
    public static class UserSummary {
        private Long id;
        private String name;
        private String username;

        public static UserSummary from(User u) {
            if (u == null) return null;
            return UserSummary.builder().id(u.getId()).name(u.getName()).username(u.getUsername()).build();
        }
    }
}
