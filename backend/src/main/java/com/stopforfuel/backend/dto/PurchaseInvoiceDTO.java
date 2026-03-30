package com.stopforfuel.backend.dto;

import com.stopforfuel.backend.entity.*;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
public class PurchaseInvoiceDTO {
    private Long id;
    private String invoiceNumber;
    private LocalDate invoiceDate;
    private LocalDate deliveryDate;
    private String invoiceType;
    private String status;
    private BigDecimal totalAmount;
    private String remarks;
    private String pdfFilePath;
    private SupplierSummary supplier;
    private PurchaseOrderSummary purchaseOrder;
    private List<ItemDTO> items;
    private Long scid;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static PurchaseInvoiceDTO from(PurchaseInvoice pi) {
        return PurchaseInvoiceDTO.builder()
                .id(pi.getId())
                .invoiceNumber(pi.getInvoiceNumber())
                .invoiceDate(pi.getInvoiceDate())
                .deliveryDate(pi.getDeliveryDate())
                .invoiceType(pi.getInvoiceType())
                .status(pi.getStatus())
                .totalAmount(pi.getTotalAmount())
                .remarks(pi.getRemarks())
                .pdfFilePath(pi.getPdfFilePath())
                .supplier(SupplierSummary.from(pi.getSupplier()))
                .purchaseOrder(PurchaseOrderSummary.from(pi.getPurchaseOrder()))
                .items(pi.getItems() != null
                        ? pi.getItems().stream().map(ItemDTO::from).collect(Collectors.toList())
                        : null)
                .scid(pi.getScid())
                .createdAt(pi.getCreatedAt())
                .updatedAt(pi.getUpdatedAt())
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
    public static class PurchaseOrderSummary {
        private Long id;
        private String status;

        public static PurchaseOrderSummary from(PurchaseOrder po) {
            if (po == null) return null;
            return PurchaseOrderSummary.builder().id(po.getId()).status(po.getStatus()).build();
        }
    }

    @Getter
    @Builder
    public static class ItemDTO {
        private Long id;
        private Long productId;
        private String productName;
        private Double quantity;
        private BigDecimal unitPrice;
        private BigDecimal totalPrice;

        public static ItemDTO from(PurchaseInvoiceItem item) {
            if (item == null) return null;
            return ItemDTO.builder()
                    .id(item.getId())
                    .productId(item.getProduct() != null ? item.getProduct().getId() : null)
                    .productName(item.getProduct() != null ? item.getProduct().getName() : null)
                    .quantity(item.getQuantity())
                    .unitPrice(item.getUnitPrice())
                    .totalPrice(item.getTotalPrice())
                    .build();
        }
    }
}
