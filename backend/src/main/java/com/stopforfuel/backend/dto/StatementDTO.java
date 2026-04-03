package com.stopforfuel.backend.dto;

import com.stopforfuel.backend.entity.Customer;
import com.stopforfuel.backend.entity.Statement;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class StatementDTO {
    private Long id;
    private String statementNo;
    private LocalDate fromDate;
    private LocalDate toDate;
    private LocalDate statementDate;
    private Integer numberOfBills;
    private BigDecimal totalAmount;
    private BigDecimal roundingAmount;
    private BigDecimal netAmount;
    private BigDecimal receivedAmount;
    private BigDecimal balanceAmount;
    private String status;
    private String statementPdfUrl;
    private CustomerSummary customer;
    private Long scid;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static StatementDTO from(Statement s) {
        return StatementDTO.builder()
                .id(s.getId())
                .statementNo(s.getStatementNo())
                .fromDate(s.getFromDate())
                .toDate(s.getToDate())
                .statementDate(s.getStatementDate())
                .numberOfBills(s.getNumberOfBills())
                .totalAmount(s.getTotalAmount())
                .roundingAmount(s.getRoundingAmount())
                .netAmount(s.getNetAmount())
                .receivedAmount(s.getReceivedAmount())
                .balanceAmount(s.getBalanceAmount())
                .status(s.getStatus())
                .statementPdfUrl(s.getStatementPdfUrl())
                .customer(CustomerSummary.from(s.getCustomer()))
                .scid(s.getScid())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
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
}
