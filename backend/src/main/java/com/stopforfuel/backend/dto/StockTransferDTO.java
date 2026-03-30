package com.stopforfuel.backend.dto;

import com.stopforfuel.backend.entity.Product;
import com.stopforfuel.backend.entity.StockTransfer;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class StockTransferDTO {
    private Long id;
    private Double quantity;
    private String fromLocation;
    private String toLocation;
    private LocalDateTime transferDate;
    private String remarks;
    private String transferredBy;
    private ProductSummary product;
    private Long shiftId;
    private Long scid;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static StockTransferDTO from(StockTransfer st) {
        return StockTransferDTO.builder()
                .id(st.getId())
                .quantity(st.getQuantity())
                .fromLocation(st.getFromLocation())
                .toLocation(st.getToLocation())
                .transferDate(st.getTransferDate())
                .remarks(st.getRemarks())
                .transferredBy(st.getTransferredBy())
                .product(ProductSummary.from(st.getProduct()))
                .shiftId(st.getShiftId())
                .scid(st.getScid())
                .createdAt(st.getCreatedAt())
                .updatedAt(st.getUpdatedAt())
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
