package com.stopforfuel.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "employee_advances")
@Getter
@Setter
public class EmployeeAdvance extends SimpleBaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "salaryHistories", "advances"})
    private Employee employee;

    private Double amount;
    private LocalDate advanceDate;
    private String advanceType; // SALARY_ADVANCE, HOME_ADVANCE, NIGHT_ADVANCE
    private String remarks;
    private String status = "PENDING"; // PENDING, DEDUCTED, WAIVED
}
