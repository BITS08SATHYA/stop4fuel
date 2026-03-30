package com.stopforfuel.backend.dto;

import com.stopforfuel.backend.entity.GodownStock;
import com.stopforfuel.backend.entity.Product;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class GodownStockDTO {
    private Long id;
    private Double currentStock;
    private Double reorderLevel;
    private Double maxStock;
    private String location;
    private LocalDate lastRestockDate;
    private ProductSummary product;
    private Long scid;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static GodownStockDTO from(GodownStock gs) {
        return GodownStockDTO.builder()
                .id(gs.getId())
                .currentStock(gs.getCurrentStock())
                .reorderLevel(gs.getReorderLevel())
                .maxStock(gs.getMaxStock())
                .location(gs.getLocation())
                .lastRestockDate(gs.getLastRestockDate())
                .product(ProductSummary.from(gs.getProduct()))
                .scid(gs.getScid())
                .createdAt(gs.getCreatedAt())
                .updatedAt(gs.getUpdatedAt())
                .build();
    }

    @Getter
    @Builder
    public static class ProductSummary {
        private Long id;
        private String name;
        private String category;

        public static ProductSummary from(Product p) {
            if (p == null) return null;
            return ProductSummary.builder().id(p.getId()).name(p.getName()).category(p.getCategory()).build();
        }
    }
}
