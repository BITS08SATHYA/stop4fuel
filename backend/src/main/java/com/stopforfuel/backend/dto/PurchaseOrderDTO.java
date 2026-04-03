package com.stopforfuel.backend.dto;

import com.stopforfuel.backend.entity.Product;
import com.stopforfuel.backend.entity.PurchaseOrder;
import com.stopforfuel.backend.entity.PurchaseOrderItem;
import com.stopforfuel.backend.entity.Supplier;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
public class PurchaseOrderDTO {
    private Long id;
    private LocalDate orderDate;
    private LocalDate expectedDeliveryDate;
    private String status;
    private BigDecimal totalAmount;
    private String remarks;
    private SupplierSummary supplier;
    private List<ItemDTO> items;
    private Long scid;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static PurchaseOrderDTO from(PurchaseOrder po) {
        return PurchaseOrderDTO.builder()
                .id(po.getId())
                .orderDate(po.getOrderDate())
                .expectedDeliveryDate(po.getExpectedDeliveryDate())
                .status(po.getStatus() != null ? po.getStatus().name() : null)
                .totalAmount(po.getTotalAmount())
                .remarks(po.getRemarks())
                .supplier(SupplierSummary.from(po.getSupplier()))
                .items(po.getItems() != null
                        ? po.getItems().stream().map(ItemDTO::from).collect(Collectors.toList())
                        : null)
                .scid(po.getScid())
                .createdAt(po.getCreatedAt())
                .updatedAt(po.getUpdatedAt())
                .build();
    }

    @Getter
    @Builder
    public static class SupplierSummary {
        private Long id;
        private String name;

        public static SupplierSummary from(Supplier s) {
            if (s == null) return null;
            return SupplierSummary.builder().id(s.getId()).name(s.getName()).build();
        }
    }

    @Getter
    @Builder
    public static class ItemDTO {
        private Long id;
        private Long productId;
        private String productName;
        private Double orderedQty;
        private Double receivedQty;
        private BigDecimal unitPrice;
        private BigDecimal totalPrice;

        public static ItemDTO from(PurchaseOrderItem item) {
            if (item == null) return null;
            return ItemDTO.builder()
                    .id(item.getId())
                    .productId(item.getProduct() != null ? item.getProduct().getId() : null)
                    .productName(item.getProduct() != null ? item.getProduct().getName() : null)
                    .orderedQty(item.getOrderedQty())
                    .receivedQty(item.getReceivedQty())
                    .unitPrice(item.getUnitPrice())
                    .totalPrice(item.getTotalPrice())
                    .build();
        }
    }
}
