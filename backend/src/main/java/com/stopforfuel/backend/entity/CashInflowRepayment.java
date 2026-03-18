package com.stopforfuel.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cash_inflow_repayments")
@Getter
@Setter
public class CashInflowRepayment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cash_inflow_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private ExternalCashInflow cashInflow;

    @Column(name = "amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;

    @Column(name = "repayment_date")
    private LocalDateTime repaymentDate;

    @Column(name = "remarks")
    private String remarks;

    @PrePersist
    @Override
    protected void onCreate() {
        super.onCreate();
        if (this.repaymentDate == null) {
            this.repaymentDate = LocalDateTime.now();
        }
    }
}
