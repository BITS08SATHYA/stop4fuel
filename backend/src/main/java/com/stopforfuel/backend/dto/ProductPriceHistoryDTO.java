package com.stopforfuel.backend.dto;

import com.stopforfuel.backend.entity.ProductPriceHistory;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class ProductPriceHistoryDTO {
    private Long id;
    private LocalDate effectiveDate;
    private Long productId;
    private String productName;
    private BigDecimal price;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ProductPriceHistoryDTO from(ProductPriceHistory h) {
        return ProductPriceHistoryDTO.builder()
                .id(h.getId())
                .effectiveDate(h.getEffectiveDate())
                .productId(h.getProduct() != null ? h.getProduct().getId() : null)
                .productName(h.getProduct() != null ? h.getProduct().getName() : null)
                .price(h.getPrice())
                .createdAt(h.getCreatedAt())
                .updatedAt(h.getUpdatedAt())
                .build();
    }
}
