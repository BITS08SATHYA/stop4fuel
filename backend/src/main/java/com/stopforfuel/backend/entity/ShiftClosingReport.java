package com.stopforfuel.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "shift_closing_reports")
@Getter
@Setter
public class ShiftClosingReport extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shift_id_ref", unique = true)
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Shift shift;

    @Column(name = "report_date")
    private LocalDateTime reportDate;

    @Column(name = "status", nullable = false)
    private String status = "DRAFT"; // DRAFT, FINALIZED

    @Column(name = "finalized_by")
    private String finalizedBy;

    @Column(name = "finalized_at")
    private LocalDateTime finalizedAt;

    @Column(name = "total_revenue", precision = 19, scale = 4)
    private BigDecimal totalRevenue = BigDecimal.ZERO;

    @Column(name = "total_advances", precision = 19, scale = 4)
    private BigDecimal totalAdvances = BigDecimal.ZERO;

    @Column(name = "balance", precision = 19, scale = 4)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "cash_bill_amount", precision = 19, scale = 4)
    private BigDecimal cashBillAmount = BigDecimal.ZERO;

    @Column(name = "credit_bill_amount", precision = 19, scale = 4)
    private BigDecimal creditBillAmount = BigDecimal.ZERO;

    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReportLineItem> lineItems = new ArrayList<>();

    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReportCashBillBreakdown> cashBillBreakdowns = new ArrayList<>();

    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReportAuditLog> auditLogs = new ArrayList<>();

    @PrePersist
    @Override
    protected void onCreate() {
        super.onCreate();
        if (this.reportDate == null) {
            this.reportDate = LocalDateTime.now();
        }
    }
}
