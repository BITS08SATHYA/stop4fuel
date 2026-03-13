package com.stopforfuel.employee;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;

@Data
@Entity
@Table(name = "employee_advances")
public class EmployeeAdvance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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
