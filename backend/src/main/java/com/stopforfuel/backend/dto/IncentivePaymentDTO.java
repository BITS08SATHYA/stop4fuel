package com.stopforfuel.backend.dto;

import com.stopforfuel.backend.entity.*;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class IncentivePaymentDTO {
    private Long id;
    private LocalDateTime paymentDate;
    private BigDecimal amount;
    private String description;
    private CustomerSummary customer;
    private InvoiceBillSummary invoiceBill;
    private StatementSummary statement;
    private Long shiftId;
    private Long scid;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static IncentivePaymentDTO from(IncentivePayment ip) {
        return IncentivePaymentDTO.builder()
                .id(ip.getId())
                .paymentDate(ip.getPaymentDate())
                .amount(ip.getAmount())
                .description(ip.getDescription())
                .customer(CustomerSummary.from(ip.getCustomer()))
                .invoiceBill(InvoiceBillSummary.from(ip.getInvoiceBill()))
                .statement(StatementSummary.from(ip.getStatement()))
                .shiftId(ip.getShiftId())
                .scid(ip.getScid())
                .createdAt(ip.getCreatedAt())
                .updatedAt(ip.getUpdatedAt())
                .build();
    }

    @Getter
    @Builder
    public static class CustomerSummary {
        private Long id;
        private String name;

        public static CustomerSummary from(Customer c) {
            if (c == null) return null;
            return CustomerSummary.builder().id(c.getId()).name(c.getName()).build();
        }
    }

    @Getter
    @Builder
    public static class InvoiceBillSummary {
        private Long id;
        private String billNo;
        private String signatoryName;
        private String billDesc;
        private String customerName;

        public static InvoiceBillSummary from(InvoiceBill b) {
            if (b == null) return null;
            return InvoiceBillSummary.builder()
                    .id(b.getId())
                    .billNo(b.getBillNo())
                    .signatoryName(b.getSignatoryName())
                    .billDesc(b.getBillDesc())
                    .customerName(b.getCustomer() != null ? b.getCustomer().getName() : null)
                    .build();
        }
    }

    @Getter
    @Builder
    public static class StatementSummary {
        private Long id;
        private String statementNo;

        public static StatementSummary from(Statement s) {
            if (s == null) return null;
            return StatementSummary.builder().id(s.getId()).statementNo(s.getStatementNo()).build();
        }
    }
}
