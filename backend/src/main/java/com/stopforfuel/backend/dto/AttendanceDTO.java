package com.stopforfuel.backend.dto;

import com.stopforfuel.backend.entity.Attendance;
import com.stopforfuel.backend.entity.Employee;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
@Builder
public class AttendanceDTO {
    private Long id;
    private LocalDate date;
    private LocalTime checkInTime;
    private LocalTime checkOutTime;
    private Double totalHoursWorked;
    private Long shiftRefId;
    private String status;
    private String source;
    private String remarks;
    private EmployeeSummary employee;
    private Long scid;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AttendanceDTO from(Attendance a) {
        return AttendanceDTO.builder()
                .id(a.getId())
                .date(a.getDate())
                .checkInTime(a.getCheckInTime())
                .checkOutTime(a.getCheckOutTime())
                .totalHoursWorked(a.getTotalHoursWorked())
                .shiftRefId(a.getShiftRefId())
                .status(a.getStatus())
                .source(a.getSource())
                .remarks(a.getRemarks())
                .employee(EmployeeSummary.from(a.getEmployee()))
                .scid(a.getScid())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .build();
    }

    @Getter
    @Builder
    public static class EmployeeSummary {
        private Long id;
        private String name;
        private String employeeCode;
        private String designation;

        public static EmployeeSummary from(Employee e) {
            if (e == null) return null;
            return EmployeeSummary.builder()
                    .id(e.getId())
                    .name(e.getName())
                    .employeeCode(e.getEmployeeCode())
                    .designation(e.getDesignation())
                    .build();
        }
    }
}
