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
    private String sapEntryNumber;
    private LocalDate invoiceDate;
    private LocalDate deliveryDate;
    private String invoiceType;
    private String status;
    private BigDecimal totalAmount;
    private BigDecimal roundingAdjustment;
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
                .sapEntryNumber(pi.getSapEntryNumber())
                .invoiceDate(pi.getInvoiceDate())
                .deliveryDate(pi.getDeliveryDate())
                .invoiceType(pi.getInvoiceType())
                .status(pi.getStatus())
                .totalAmount(pi.getTotalAmount())
                .roundingAdjustment(pi.getRoundingAdjustment())
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
            return PurchaseOrderSummary.builder().id(po.getId()).status(po.getStatus() != null ? po.getStatus().name() : null).build();
        }
    }

    @Getter
    @Builder
    public static class ProductSummary {
        private Long id;
        private String name;
        private GradeSummary gradeType;

        public static ProductSummary from(Product p) {
            if (p == null) return null;
            return ProductSummary.builder()
                    .id(p.getId())
                    .name(p.getName())
                    .gradeType(p.getGrade() != null
                            ? GradeSummary.builder().name(p.getGrade().getName()).build()
                            : null)
                    .build();
        }
    }

    @Getter
    @Builder
    public static class GradeSummary {
        private String name;
    }

    @Getter
    @Builder
    public static class ItemDTO {
        private Long id;
        private Long productId;
        private String productName;
        // Nested product summary — the frontend reads item.product.{id,name,gradeType.name}
        // for the details modal and the Edit form. Without it the modal shows "-" and
        // the Edit button crashes on item.product.id.
        private ProductSummary product;
        private Double quantity;
        private BigDecimal unitPrice;
        private BigDecimal totalPrice;
        private BigDecimal basicPrice;
        private BigDecimal basicAmount;
        private BigDecimal taxPercent;
        private BigDecimal taxAmount;
        private BigDecimal additionalTaxAmount;

        public static ItemDTO from(PurchaseInvoiceItem item) {
            if (item == null) return null;
            return ItemDTO.builder()
                    .id(item.getId())
                    .productId(item.getProduct() != null ? item.getProduct().getId() : null)
                    .productName(item.getProduct() != null ? item.getProduct().getName() : null)
                    .product(ProductSummary.from(item.getProduct()))
                    .quantity(item.getQuantity())
                    .unitPrice(item.getUnitPrice())
                    .totalPrice(item.getTotalPrice())
                    .basicPrice(item.getBasicPrice())
                    .basicAmount(item.getBasicAmount())
                    .taxPercent(item.getTaxPercent())
                    .taxAmount(item.getTaxAmount())
                    .additionalTaxAmount(item.getAdditionalTaxAmount())
                    .build();
        }
    }
}
