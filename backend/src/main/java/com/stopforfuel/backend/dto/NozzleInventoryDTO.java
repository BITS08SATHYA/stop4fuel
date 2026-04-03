package com.stopforfuel.backend.dto;

import com.stopforfuel.backend.entity.Nozzle;
import com.stopforfuel.backend.entity.NozzleInventory;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class NozzleInventoryDTO {
    private Long id;
    private LocalDate date;
    private Double openMeterReading;
    private Double closeMeterReading;
    private Double testQuantity;
    private Double sales;
    private Double rate;
    private Double amount;
    private NozzleSummary nozzle;
    private Long shiftId;
    private Long scid;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static NozzleInventoryDTO from(NozzleInventory ni) {
        return NozzleInventoryDTO.builder()
                .id(ni.getId())
                .date(ni.getDate())
                .openMeterReading(ni.getOpenMeterReading())
                .closeMeterReading(ni.getCloseMeterReading())
                .testQuantity(ni.getTestQuantity())
                .sales(ni.getSales())
                .rate(ni.getRate())
                .amount(ni.getAmount())
                .nozzle(NozzleSummary.from(ni.getNozzle()))
                .shiftId(ni.getShiftId())
                .scid(ni.getScid())
                .createdAt(ni.getCreatedAt())
                .updatedAt(ni.getUpdatedAt())
                .build();
    }

    @Getter
    @Builder
    public static class NozzleSummary {
        private Long id;
        private String nozzleName;
        private String pumpName;
        private String productName;

        public static NozzleSummary from(Nozzle n) {
            if (n == null) return null;
            return NozzleSummary.builder()
                    .id(n.getId())
                    .nozzleName(n.getNozzleName())
                    .pumpName(n.getPump() != null ? n.getPump().getName() : null)
                    .productName(n.getTank() != null && n.getTank().getProduct() != null
                            ? n.getTank().getProduct().getName() : null)
                    .build();
        }
    }
}
