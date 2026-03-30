package com.stopforfuel.backend.dto;

import com.stopforfuel.backend.entity.StockAlert;
import com.stopforfuel.backend.entity.Tank;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class StockAlertDTO {
    private Long id;
    private Double availableStock;
    private Double thresholdStock;
    private String message;
    private boolean active;
    private LocalDateTime acknowledgedAt;
    private String acknowledgedBy;
    private String notifiedVia;
    private TankSummary tank;
    private Long scid;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static StockAlertDTO from(StockAlert sa) {
        return StockAlertDTO.builder()
                .id(sa.getId())
                .availableStock(sa.getAvailableStock())
                .thresholdStock(sa.getThresholdStock())
                .message(sa.getMessage())
                .active(sa.isActive())
                .acknowledgedAt(sa.getAcknowledgedAt())
                .acknowledgedBy(sa.getAcknowledgedBy())
                .notifiedVia(sa.getNotifiedVia())
                .tank(TankSummary.from(sa.getTank()))
                .scid(sa.getScid())
                .createdAt(sa.getCreatedAt())
                .updatedAt(sa.getUpdatedAt())
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
