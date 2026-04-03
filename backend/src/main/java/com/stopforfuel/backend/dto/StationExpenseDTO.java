package com.stopforfuel.backend.dto;

import com.stopforfuel.backend.entity.ExpenseType;
import com.stopforfuel.backend.entity.StationExpense;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class StationExpenseDTO {
    private Long id;
    private Double amount;
    private LocalDate expenseDate;
    private String description;
    private String paidTo;
    private String paymentMode;
    private String recurringType;
    private ExpenseTypeSummary expenseType;
    private Long scid;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static StationExpenseDTO from(StationExpense se) {
        return StationExpenseDTO.builder()
                .id(se.getId())
                .amount(se.getAmount())
                .expenseDate(se.getExpenseDate())
                .description(se.getDescription())
                .paidTo(se.getPaidTo())
                .paymentMode(se.getPaymentMode())
                .recurringType(se.getRecurringType())
                .expenseType(ExpenseTypeSummary.from(se.getExpenseType()))
                .scid(se.getScid())
                .createdAt(se.getCreatedAt())
                .updatedAt(se.getUpdatedAt())
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
