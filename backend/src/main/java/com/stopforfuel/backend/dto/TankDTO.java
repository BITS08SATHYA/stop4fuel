package com.stopforfuel.backend.dto;

import com.stopforfuel.backend.entity.Product;
import com.stopforfuel.backend.entity.Tank;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class TankDTO {
    private Long id;
    private String name;
    private Double capacity;
    private Double availableStock;
    private Double thresholdStock;
    private boolean active;
    private ProductSummary product;
    private Long scid;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static TankDTO from(Tank t) {
        return TankDTO.builder()
                .id(t.getId())
                .name(t.getName())
                .capacity(t.getCapacity())
                .availableStock(t.getAvailableStock())
                .thresholdStock(t.getThresholdStock())
                .active(t.isActive())
                .product(ProductSummary.from(t.getProduct()))
                .scid(t.getScid())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .build();
    }

    @Getter
    @Builder
    public static class ProductSummary {
        private Long id;
        private String name;
        private String category;
        private String fuelFamily;

        public static ProductSummary from(Product p) {
            if (p == null) return null;
            return ProductSummary.builder()
                    .id(p.getId())
                    .name(p.getName())
                    .category(p.getCategory())
                    .fuelFamily(p.getFuelFamily())
                    .build();
        }
    }
}
