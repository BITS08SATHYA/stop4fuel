package com.stopforfuel.backend.dto;

import com.stopforfuel.backend.entity.Employee;
import com.stopforfuel.backend.entity.SalaryPayment;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class SalaryPaymentDTO {
    private Long id;
    private Integer month;
    private Integer year;
    private Double baseSalary;
    private Double advanceDeduction;
    private Integer lopDays;
    private Double lopDeduction;
    private Double incentiveAmount;
    private Double otherDeductions;
    private Double netPayable;
    private LocalDate paymentDate;
    private String paymentMode;
    private String status;
    private String remarks;
    private EmployeeSummary employee;
    private Long scid;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static SalaryPaymentDTO from(SalaryPayment sp) {
        return SalaryPaymentDTO.builder()
                .id(sp.getId())
                .month(sp.getMonth())
                .year(sp.getYear())
                .baseSalary(sp.getBaseSalary())
                .advanceDeduction(sp.getAdvanceDeduction())
                .lopDays(sp.getLopDays())
                .lopDeduction(sp.getLopDeduction())
                .incentiveAmount(sp.getIncentiveAmount())
                .otherDeductions(sp.getOtherDeductions())
                .netPayable(sp.getNetPayable())
                .paymentDate(sp.getPaymentDate())
                .paymentMode(sp.getPaymentMode())
                .status(sp.getStatus())
                .remarks(sp.getRemarks())
                .employee(EmployeeSummary.from(sp.getEmployee()))
                .scid(sp.getScid())
                .createdAt(sp.getCreatedAt())
                .updatedAt(sp.getUpdatedAt())
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
