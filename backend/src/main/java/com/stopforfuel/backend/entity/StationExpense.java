package com.stopforfuel.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "station_expenses")
@Getter
@Setter
public class StationExpense extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expense_type_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private com.stopforfuel.backend.entity.ExpenseType expenseType;

    private Double amount;
    private LocalDate expenseDate;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String paidTo;
    @Enumerated(EnumType.STRING)
    private com.stopforfuel.backend.enums.PaymentMode paymentMode;

    private String recurringType = "ONE_TIME"; // ONE_TIME, MONTHLY, QUARTERLY, ANNUAL
}
