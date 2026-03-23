package com.stopforfuel.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "salary_history")
@Getter
@Setter
public class SalaryHistory extends SimpleBaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "salaryHistories", "advances"})
    private Employee employee;

    private Double oldSalary;

    @NotNull(message = "New salary is required")
    @PositiveOrZero(message = "New salary must be zero or positive")
    private Double newSalary;

    @NotNull(message = "Effective date is required")
    private LocalDate effectiveDate;

    @Size(max = 500, message = "Reason must not exceed 500 characters")
    private String reason;
}
