package com.stopforfuel.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "purchase_invoice", indexes = {
    @Index(name = "idx_purch_inv_scid", columnList = "scid"),
    @Index(name = "idx_purch_inv_status", columnList = "status"),
    @Index(name = "idx_purch_inv_supplier_id", columnList = "supplier_id"),
    @Index(name = "idx_purch_inv_invoice_date", columnList = "invoice_date")
})
@Getter
@Setter
public class PurchaseInvoice extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id")
    @JsonIgnoreProperties({"items", "hibernateLazyInitializer", "handler"})
    private PurchaseOrder purchaseOrder;

    @Column(nullable = false)
    private String invoiceNumber;

    @Column(nullable = false)
    private LocalDate invoiceDate;

    private LocalDate deliveryDate;

    @Column(nullable = false)
    private String invoiceType; // FUEL, NON_FUEL

    @Column(nullable = false)
    private String status = "PENDING"; // PENDING, VERIFIED, PAID

    private BigDecimal totalAmount;

    private String remarks;

    private String pdfFilePath;

    @OneToMany(mappedBy = "purchaseInvoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties({"purchaseInvoice", "hibernateLazyInitializer", "handler"})
    private List<PurchaseInvoiceItem> items = new ArrayList<>();
}
