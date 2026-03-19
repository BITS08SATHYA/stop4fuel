package com.stopforfuel.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "leave_balances", indexes = {
    @Index(name = "idx_leave_bal_employee_id", columnList = "employee_id"),
    @Index(name = "idx_leave_bal_emp_year", columnList = "employee_id, year")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_leave_bal_emp_type_year", columnNames = {"employee_id", "leave_type_id", "year"})
})
@Getter
@Setter
public class LeaveBalance extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "salaryHistories", "advances"})
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leave_type_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private LeaveType leaveType;

    private Integer year;

    private Double totalAllotted = 0.0;

    private Double used = 0.0;

    private Double remaining = 0.0;
}
