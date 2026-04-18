package com.stopforfuel.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Entity
@Table(name = "report_line_items", indexes = {
    @Index(name = "idx_rli_report_id", columnList = "report_id"),
    @Index(name = "idx_rli_report_section", columnList = "report_id, section")
})
@Getter
@Setter
public class ReportLineItem extends SimpleBaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private ShiftClosingReport report;

    @Column(name = "section", nullable = false)
    private String section; // REVENUE, ADVANCE

    @Column(name = "category", nullable = false)
    private String category; // FUEL_SALES, OIL_SALES, BILL_PAYMENT, STATEMENT_PAYMENT, EXTERNAL_INFLOW,
                             // CREDIT_BILLS, CARD, CCMS, UPI, BANK, CHEQUE, CASH_ADVANCE, HOME_ADVANCE,
                             // EXPENSES, INCENTIVE, SALARY_ADVANCE, INFLOW_REPAYMENT

    @Column(name = "label")
    private String label;

    @Column(name = "quantity")
    private Double quantity; // litres for fuel

    @Column(name = "rate", precision = 19, scale = 4)
    private BigDecimal rate;

    @Column(name = "amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;

    /** COGS snapshot for REVENUE lines (FUEL_SALES/OIL_SALES). Null for non-cost lines. */
    @Column(name = "cost_amount", precision = 19, scale = 4)
    private BigDecimal costAmount;

    @Column(name = "source_entity_type")
    private String sourceEntityType;

    @Column(name = "source_entity_id")
    private Long sourceEntityId;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "original_amount", precision = 19, scale = 4)
    private BigDecimal originalAmount;

    @Column(name = "transferred_from_report_id")
    private Long transferredFromReportId;

    @Column(name = "transferred_to_report_id")
    private Long transferredToReportId;
}
