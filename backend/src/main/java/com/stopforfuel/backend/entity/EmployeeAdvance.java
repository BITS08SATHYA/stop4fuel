package com.stopforfuel.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "employee_advances", indexes = {
    @Index(name = "idx_emp_adv_employee_id", columnList = "employee_id"),
    @Index(name = "idx_emp_adv_advance_date", columnList = "advance_date")
})
@Getter
@Setter
public class EmployeeAdvance extends SimpleBaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "salaryHistories", "advances"})
    private Employee employee;

    @NotNull(message = "Amount is required")
    @Positive(message = "Advance amount must be positive")
    private Double amount;

    @NotNull(message = "Advance date is required")
    private LocalDate advanceDate;

    @NotBlank(message = "Advance type is required")
    private String advanceType; // SALARY_ADVANCE, HOME_ADVANCE, NIGHT_ADVANCE

    @Size(max = 500, message = "Remarks must not exceed 500 characters")
    private String remarks;

    @Pattern(regexp = "^(PENDING|DEDUCTED|WAIVED)$", message = "Status must be PENDING, DEDUCTED, or WAIVED")
    private String status = "PENDING"; // PENDING, DEDUCTED, WAIVED
}
