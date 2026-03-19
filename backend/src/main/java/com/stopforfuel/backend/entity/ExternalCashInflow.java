package com.stopforfuel.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "external_cash_inflows", indexes = {
    @Index(name = "idx_ext_inflow_shift_id", columnList = "shift_id"),
    @Index(name = "idx_ext_inflow_status", columnList = "status")
})
@Getter
@Setter
public class ExternalCashInflow extends BaseEntity {

    @Column(name = "amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;

    @Column(name = "inflow_date")
    private LocalDateTime inflowDate;

    @Column(name = "source")
    private String source;

    @Column(name = "purpose")
    private String purpose;

    @Column(name = "remarks")
    private String remarks;

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE"; // ACTIVE, FULLY_REPAID, PARTIALLY_REPAID

    @Column(name = "repaid_amount", precision = 19, scale = 4)
    private BigDecimal repaidAmount;

    @PrePersist
    @Override
    protected void onCreate() {
        super.onCreate();
        if (this.inflowDate == null) {
            this.inflowDate = LocalDateTime.now();
        }
        if (this.repaidAmount == null) {
            this.repaidAmount = BigDecimal.ZERO;
        }
    }
}
