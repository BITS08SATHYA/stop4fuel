package com.stopforfuel.backend.dto;

import com.stopforfuel.backend.entity.Employee;
import com.stopforfuel.backend.entity.SalaryHistory;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class SalaryHistoryDTO {
    private Long id;
    private Double oldSalary;
    private Double newSalary;
    private LocalDate effectiveDate;
    private String reason;
    private EmployeeSummary employee;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static SalaryHistoryDTO from(SalaryHistory sh) {
        return SalaryHistoryDTO.builder()
                .id(sh.getId())
                .oldSalary(sh.getOldSalary())
                .newSalary(sh.getNewSalary())
                .effectiveDate(sh.getEffectiveDate())
                .reason(sh.getReason())
                .employee(EmployeeSummary.from(sh.getEmployee()))
                .createdAt(sh.getCreatedAt())
                .updatedAt(sh.getUpdatedAt())
                .build();
    }

    @Getter
    @Builder
    public static class EmployeeSummary {
        private Long id;
        private String name;
        private String employeeCode;

        public static EmployeeSummary from(Employee e) {
            if (e == null) return null;
            return EmployeeSummary.builder()
                    .id(e.getId())
                    .name(e.getName())
                    .employeeCode(e.getEmployeeCode())
                    .build();
        }
    }
}
