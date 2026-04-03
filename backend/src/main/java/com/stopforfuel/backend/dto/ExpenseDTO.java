package com.stopforfuel.backend.dto;

import com.stopforfuel.backend.entity.Expense;
import com.stopforfuel.backend.entity.ExpenseType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class ExpenseDTO {
    private Long id;
    private LocalDateTime expenseDate;
    private BigDecimal amount;
    private String description;
    private String remarks;
    private ExpenseTypeSummary expenseType;
    private Long shiftId;
    private Long scid;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ExpenseDTO from(Expense e) {
        return ExpenseDTO.builder()
                .id(e.getId())
                .expenseDate(e.getExpenseDate())
                .amount(e.getAmount())
                .description(e.getDescription())
                .remarks(e.getRemarks())
                .expenseType(ExpenseTypeSummary.from(e.getExpenseType()))
                .shiftId(e.getShiftId())
                .scid(e.getScid())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    @Getter
    @Builder
    public static class ExpenseTypeSummary {
        private Long id;
        private String name;

        public static ExpenseTypeSummary from(ExpenseType et) {
            if (et == null) return null;
            return ExpenseTypeSummary.builder().id(et.getId()).name(et.getTypeName()).build();
        }
    }
}
