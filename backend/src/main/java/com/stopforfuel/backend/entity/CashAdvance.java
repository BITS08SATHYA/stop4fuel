package com.stopforfuel.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cash_advances")
@Getter
@Setter
public class CashAdvance extends BaseEntity {

    @Column(name = "advance_date")
    private LocalDateTime advanceDate;

    @Column(name = "amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;

    @Column(name = "advance_type", nullable = false)
    private String advanceType; // HOME_ADVANCE, NIGHT_ADVANCE, REGULAR_ADVANCE

    @Column(name = "recipient_name")
    private String recipientName;

    @Column(name = "recipient_phone")
    private String recipientPhone;

    @Column(name = "purpose")
    private String purpose;

    @Column(name = "remarks")
    private String remarks;

    @Column(name = "status", nullable = false)
    private String status = "GIVEN"; // GIVEN, RETURNED, PARTIALLY_RETURNED, CANCELLED

    @Column(name = "returned_amount", precision = 19, scale = 4)
    private BigDecimal returnedAmount;

    @Column(name = "return_date")
    private LocalDateTime returnDate;

    @Column(name = "return_remarks")
    private String returnRemarks;

    @PrePersist
    @Override
    protected void onCreate() {
        super.onCreate();
        if (this.advanceDate == null) {
            this.advanceDate = LocalDateTime.now();
        }
        if (this.returnedAmount == null) {
            this.returnedAmount = BigDecimal.ZERO;
        }
    }
}
