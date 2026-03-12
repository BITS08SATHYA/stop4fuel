package com.stopforfuel.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Entity
@Table(name = "invoice_product")
@Getter
@Setter
public class InvoiceProduct extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_bill_id")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private InvoiceBill invoiceBill;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nozzle_id")
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Nozzle nozzle;

    @Column(name = "quantity", precision = 19, scale = 4)
    private BigDecimal quantity;

    @Column(name = "unit_price", precision = 19, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "amount", precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "gross_amount", precision = 19, scale = 4)
    private BigDecimal grossAmount;

    @Column(name = "discount_rate", precision = 19, scale = 4)
    private BigDecimal discountRate;

    @Column(name = "discount_amount", precision = 19, scale = 4)
    private BigDecimal discountAmount;
}
