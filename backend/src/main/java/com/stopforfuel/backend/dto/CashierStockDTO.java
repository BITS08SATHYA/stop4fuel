package com.stopforfuel.backend.dto;

import com.stopforfuel.backend.entity.CashierStock;
import com.stopforfuel.backend.entity.Product;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class CashierStockDTO {
    private Long id;
    private Double currentStock;
    private Double maxCapacity;
    private ProductSummary product;
    private Long scid;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CashierStockDTO from(CashierStock cs) {
        return CashierStockDTO.builder()
                .id(cs.getId())
                .currentStock(cs.getCurrentStock())
                .maxCapacity(cs.getMaxCapacity())
                .product(ProductSummary.from(cs.getProduct()))
                .scid(cs.getScid())
                .createdAt(cs.getCreatedAt())
                .updatedAt(cs.getUpdatedAt())
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
