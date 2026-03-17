package com.stopforfuel.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "leave_requests")
@Getter
@Setter
public class LeaveRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "salaryHistories", "advances"})
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leave_type_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private LeaveType leaveType;

    private LocalDate fromDate;
    private LocalDate toDate;
    private Double numberOfDays;

    @Column(columnDefinition = "TEXT")
    private String reason;

    private String status = "PENDING"; // PENDING, APPROVED, REJECTED

    private String approvedBy;

    @Column(columnDefinition = "TEXT")
    private String remarks;
}
