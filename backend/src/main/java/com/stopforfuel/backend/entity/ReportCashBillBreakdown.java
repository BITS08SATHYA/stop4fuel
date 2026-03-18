package com.stopforfuel.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "report_cash_bill_breakdowns")
@Getter
@Setter
public class ReportCashBillBreakdown extends SimpleBaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private ShiftClosingReport report;

    @Column(name = "product_name")
    private String productName;

    @Column(name = "cash_litres")
    private Double cashLitres = 0.0;

    @Column(name = "card_litres")
    private Double cardLitres = 0.0;

    @Column(name = "ccms_litres")
    private Double ccmsLitres = 0.0;

    @Column(name = "upi_litres")
    private Double upiLitres = 0.0;

    @Column(name = "cheque_litres")
    private Double chequeLitres = 0.0;

    @Column(name = "total_litres")
    private Double totalLitres = 0.0;
}
