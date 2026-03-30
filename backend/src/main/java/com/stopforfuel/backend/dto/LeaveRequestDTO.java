package com.stopforfuel.backend.dto;

import com.stopforfuel.backend.entity.Employee;
import com.stopforfuel.backend.entity.LeaveRequest;
import com.stopforfuel.backend.entity.LeaveType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class LeaveRequestDTO {
    private Long id;
    private LocalDate fromDate;
    private LocalDate toDate;
    private Double numberOfDays;
    private String reason;
    private String status;
    private String approvedBy;
    private String remarks;
    private EmployeeSummary employee;
    private LeaveTypeSummary leaveType;
    private Long scid;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static LeaveRequestDTO from(LeaveRequest lr) {
        return LeaveRequestDTO.builder()
                .id(lr.getId())
                .fromDate(lr.getFromDate())
                .toDate(lr.getToDate())
                .numberOfDays(lr.getNumberOfDays())
                .reason(lr.getReason())
                .status(lr.getStatus())
                .approvedBy(lr.getApprovedBy())
                .remarks(lr.getRemarks())
                .employee(EmployeeSummary.from(lr.getEmployee()))
                .leaveType(LeaveTypeSummary.from(lr.getLeaveType()))
                .scid(lr.getScid())
                .createdAt(lr.getCreatedAt())
                .updatedAt(lr.getUpdatedAt())
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

    @Getter
    @Builder
    public static class LeaveTypeSummary {
        private Long id;
        private String name;

        public static LeaveTypeSummary from(LeaveType lt) {
            if (lt == null) return null;
            return LeaveTypeSummary.builder().id(lt.getId()).name(lt.getTypeName()).build();
        }
    }
}
