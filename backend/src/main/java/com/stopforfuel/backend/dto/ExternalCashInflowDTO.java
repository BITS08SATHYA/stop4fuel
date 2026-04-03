package com.stopforfuel.backend.dto;

import com.stopforfuel.backend.entity.ExternalCashInflow;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class ExternalCashInflowDTO {
    private Long id;
    private LocalDateTime inflowDate;
    private BigDecimal amount;
    private String source;
    private String purpose;
    private String remarks;
    private String status;
    private BigDecimal repaidAmount;
    private Long shiftId;
    private Long scid;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ExternalCashInflowDTO from(ExternalCashInflow eci) {
        return ExternalCashInflowDTO.builder()
                .id(eci.getId())
                .inflowDate(eci.getInflowDate())
                .amount(eci.getAmount())
                .source(eci.getSource())
                .purpose(eci.getPurpose())
                .remarks(eci.getRemarks())
                .status(eci.getStatus())
                .repaidAmount(eci.getRepaidAmount())
                .shiftId(eci.getShiftId())
                .scid(eci.getScid())
                .createdAt(eci.getCreatedAt())
                .updatedAt(eci.getUpdatedAt())
                .build();
    }
}
