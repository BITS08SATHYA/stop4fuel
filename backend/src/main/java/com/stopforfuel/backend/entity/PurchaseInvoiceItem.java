package com.stopforfuel.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "purchase_invoice_item")
@Getter
@Setter
public class PurchaseInvoiceItem extends SimpleBaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_invoice_id", nullable = false)
    @JsonIgnoreProperties({"items", "hibernateLazyInitializer", "handler"})
    private PurchaseInvoice purchaseInvoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Product product;

    @Column(nullable = false)
    private Double quantity;

    private BigDecimal unitPrice;

    private BigDecimal totalPrice;

    @Column(name = "basic_price", precision = 19, scale = 4)
    private BigDecimal basicPrice;

    @Column(name = "basic_amount", precision = 19, scale = 2)
    private BigDecimal basicAmount;

    @Column(name = "tax_percent", precision = 6, scale = 3)
    private BigDecimal taxPercent;

    @Column(name = "tax_amount", precision = 19, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "additional_tax_amount", precision = 19, scale = 2)
    private BigDecimal additionalTaxAmount;
}
