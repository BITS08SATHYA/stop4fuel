package com.stopforfuel.backend.dto;

import com.stopforfuel.backend.entity.ExpenseType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ExpenseTypeDTO {
    private Long id;
    private String typeName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ExpenseTypeDTO from(ExpenseType et) {
        return ExpenseTypeDTO.builder()
                .id(et.getId())
                .typeName(et.getTypeName())
                .createdAt(et.getCreatedAt())
                .updatedAt(et.getUpdatedAt())
                .build();
    }
}
