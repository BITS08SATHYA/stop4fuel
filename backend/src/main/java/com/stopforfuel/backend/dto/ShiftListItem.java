package com.stopforfuel.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShiftListItem {
    private Long id;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String attendantName;
    private String status;
    private BigDecimal totalSales;
    private BigDecimal totalExpenses;
    private BigDecimal netSales;
}
