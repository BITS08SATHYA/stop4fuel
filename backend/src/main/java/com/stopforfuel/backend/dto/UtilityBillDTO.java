package com.stopforfuel.backend.dto;

import com.stopforfuel.backend.entity.UtilityBill;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class UtilityBillDTO {
    private Long id;
    private String billType;
    private String provider;
    private String consumerNumber;
    private LocalDate billDate;
    private LocalDate dueDate;
    private Double billAmount;
    private Double paidAmount;
    private String status;
    private Double unitsConsumed;
    private String billPeriod;
    private String remarks;
    private Long scid;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static UtilityBillDTO from(UtilityBill ub) {
        return UtilityBillDTO.builder()
                .id(ub.getId())
                .billType(ub.getBillType())
                .provider(ub.getProvider())
                .consumerNumber(ub.getConsumerNumber())
                .billDate(ub.getBillDate())
                .dueDate(ub.getDueDate())
                .billAmount(ub.getBillAmount())
                .paidAmount(ub.getPaidAmount())
                .status(ub.getStatus())
                .unitsConsumed(ub.getUnitsConsumed())
                .billPeriod(ub.getBillPeriod())
                .remarks(ub.getRemarks())
                .scid(ub.getScid())
                .createdAt(ub.getCreatedAt())
                .updatedAt(ub.getUpdatedAt())
                .build();
    }
}
