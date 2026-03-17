package com.stopforfuel.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "salary_payments")
@Getter
@Setter
public class SalaryPayment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "salaryHistories", "advances"})
    private Employee employee;

    private Integer month;
    private Integer year;

    private Double baseSalary;
    private Double advanceDeduction = 0.0;
    private Double incentiveAmount = 0.0;
    private Double otherDeductions = 0.0;
    private Double netPayable;

    private LocalDate paymentDate;
    private String paymentMode;

    private String status = "DRAFT"; // DRAFT, PAID

    @Column(columnDefinition = "TEXT")
    private String remarks;
}
