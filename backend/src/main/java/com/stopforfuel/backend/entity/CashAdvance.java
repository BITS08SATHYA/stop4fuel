package com.stopforfuel.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "cash_advances", indexes = {
    @Index(name = "idx_cash_adv_shift_id", columnList = "shift_id"),
    @Index(name = "idx_cash_adv_status", columnList = "status"),
    @Index(name = "idx_cash_adv_advance_type", columnList = "advance_type"),
    @Index(name = "idx_cash_adv_employee_id", columnList = "employee_id")
})
@Getter
@Setter
public class CashAdvance extends BaseEntity {

    @Column(name = "advance_date")
    private LocalDateTime advanceDate;

    @Column(name = "amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;

    @Column(name = "advance_type", nullable = false)
    private String advanceType; // HOME_ADVANCE, NIGHT_ADVANCE, REGULAR_ADVANCE, SALARY_ADVANCE

    @Column(name = "recipient_name")
    private String recipientName;

    @Column(name = "recipient_phone")
    private String recipientPhone;

    @Column(name = "purpose")
    private String purpose;

    @Column(name = "remarks")
    private String remarks;

    @Column(name = "status", nullable = false)
    private String status = "GIVEN"; // GIVEN, RETURNED, PARTIALLY_RETURNED, SETTLED, CANCELLED

    @Column(name = "returned_amount", precision = 19, scale = 4)
    private BigDecimal returnedAmount;

    @Column(name = "return_date")
    private LocalDateTime returnDate;

    @Column(name = "return_remarks")
    private String returnRemarks;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "salaryHistories", "advances"})
    private Employee employee;

    @Column(name = "utilized_amount", precision = 19, scale = 4)
    private BigDecimal utilizedAmount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "statement_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Statement statement;

    @OneToMany(mappedBy = "cashAdvance")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "cashAdvance", "products", "statement", "raisedBy"})
    private List<InvoiceBill> invoiceBills = new ArrayList<>();

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
        if (this.utilizedAmount == null) {
            this.utilizedAmount = BigDecimal.ZERO;
        }
    }
}
