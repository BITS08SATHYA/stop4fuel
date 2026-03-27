package com.stopforfuel.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "expense", indexes = {
    @Index(name = "idx_expense_shift_id", columnList = "shift_id"),
    @Index(name = "idx_expense_type_id", columnList = "expense_type_id"),
    @Index(name = "idx_expense_date", columnList = "expense_date")
})
@Getter
@Setter
public class Expense extends BaseEntity {

    @Column(name = "expense_date")
    private LocalDateTime expenseDate;

    @Column(name = "amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;

    @Column(name = "description")
    private String description;

    @Column(name = "remarks")
    private String remarks;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expense_type_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private ExpenseType expenseType;

    @PrePersist
    @Override
    protected void onCreate() {
        super.onCreate();
        if (this.expenseDate == null) {
            this.expenseDate = LocalDateTime.now();
        }
    }
}
