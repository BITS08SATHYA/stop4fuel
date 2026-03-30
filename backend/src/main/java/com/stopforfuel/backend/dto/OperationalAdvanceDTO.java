package com.stopforfuel.backend.dto;

import com.stopforfuel.backend.entity.Employee;
import com.stopforfuel.backend.entity.OperationalAdvance;
import com.stopforfuel.backend.entity.Statement;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class OperationalAdvanceDTO {
    private Long id;
    private LocalDateTime advanceDate;
    private BigDecimal amount;
    private String advanceType;
    private String recipientName;
    private String recipientPhone;
    private String purpose;
    private String remarks;
    private String status;
    private BigDecimal returnedAmount;
    private LocalDateTime returnDate;
    private String returnRemarks;
    private BigDecimal utilizedAmount;
    private Long shiftId;
    private EmployeeSummary employee;
    private StatementSummary statement;
    private Long scid;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static OperationalAdvanceDTO from(OperationalAdvance a) {
        return OperationalAdvanceDTO.builder()
                .id(a.getId())
                .advanceDate(a.getAdvanceDate())
                .amount(a.getAmount())
                .advanceType(a.getAdvanceType())
                .recipientName(a.getRecipientName())
                .recipientPhone(a.getRecipientPhone())
                .purpose(a.getPurpose())
                .remarks(a.getRemarks())
                .status(a.getStatus())
                .returnedAmount(a.getReturnedAmount())
                .returnDate(a.getReturnDate())
                .returnRemarks(a.getReturnRemarks())
                .utilizedAmount(a.getUtilizedAmount())
                .shiftId(a.getShiftId())
                .employee(EmployeeSummary.from(a.getEmployee()))
                .statement(StatementSummary.from(a.getStatement()))
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

    @Getter
    @Builder
    public static class StatementSummary {
        private Long id;
        private String statementNo;

        public static StatementSummary from(Statement s) {
            if (s == null) return null;
            return StatementSummary.builder().id(s.getId()).statementNo(s.getStatementNo()).build();
        }
    }
}
