package com.stopforfuel.backend.dto;

import com.stopforfuel.backend.entity.Product;
import com.stopforfuel.backend.entity.ProductInventory;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class ProductInventoryDTO {
    private Long id;
    private LocalDate date;
    private Double openStock;
    private Double incomeStock;
    private Double totalStock;
    private Double closeStock;
    private Double sales;
    private BigDecimal rate;
    private BigDecimal amount;
    private ProductSummary product;
    private Long shiftId;
    private Long scid;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ProductInventoryDTO from(ProductInventory pi) {
        return ProductInventoryDTO.builder()
                .id(pi.getId())
                .date(pi.getDate())
                .openStock(pi.getOpenStock())
                .incomeStock(pi.getIncomeStock())
                .totalStock(pi.getTotalStock())
                .closeStock(pi.getCloseStock())
                .sales(pi.getSales())
                .rate(pi.getRate())
                .amount(pi.getAmount())
                .product(ProductSummary.from(pi.getProduct()))
                .shiftId(pi.getShiftId())
                .scid(pi.getScid())
                .createdAt(pi.getCreatedAt())
                .updatedAt(pi.getUpdatedAt())
                .build();
    }

    @Getter
    @Builder
    public static class ProductSummary {
        private Long id;
        private String name;

        public static ProductSummary from(Product p) {
            if (p == null) return null;
            return ProductSummary.builder().id(p.getId()).name(p.getName()).build();
        }
    }
}
