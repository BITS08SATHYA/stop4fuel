package com.stopforfuel.backend.dto;

import com.stopforfuel.backend.entity.Tank;
import com.stopforfuel.backend.entity.TankInventory;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class TankInventoryDTO {
    private Long id;
    private LocalDate date;
    private String openDip;
    private Double openStock;
    private Double incomeStock;
    private Double totalStock;
    private String closeDip;
    private Double closeStock;
    private Double saleStock;
    private TankSummary tank;
    private Long shiftId;
    private Long scid;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static TankInventoryDTO from(TankInventory ti) {
        return TankInventoryDTO.builder()
                .id(ti.getId())
                .date(ti.getDate())
                .openDip(ti.getOpenDip())
                .openStock(ti.getOpenStock())
                .incomeStock(ti.getIncomeStock())
                .totalStock(ti.getTotalStock())
                .closeDip(ti.getCloseDip())
                .closeStock(ti.getCloseStock())
                .saleStock(ti.getSaleStock())
                .tank(TankSummary.from(ti.getTank()))
                .shiftId(ti.getShiftId())
                .scid(ti.getScid())
                .createdAt(ti.getCreatedAt())
                .updatedAt(ti.getUpdatedAt())
                .build();
    }

    @Getter
    @Builder
    public static class TankSummary {
        private Long id;
        private String name;
        private String productName;

        public static TankSummary from(Tank t) {
            if (t == null) return null;
            return TankSummary.builder()
                    .id(t.getId())
                    .name(t.getName())
                    .productName(t.getProduct() != null ? t.getProduct().getName() : null)
                    .build();
        }
    }
}
