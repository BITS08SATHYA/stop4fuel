package com.stopforfuel.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Stock a cashier added by hand at the counter (usually to clear an "insufficient stock" block
 * on an invoice). ProductInventory.incomeStock only keeps the running sum, so this row is the
 * record of who added how much and why — a cashier who keeps "finding" the same product shows
 * up here.
 */
@Entity
@Table(name = "manual_stock_receipt", indexes = {
    @Index(name = "idx_manual_stock_receipt_shift", columnList = "shift_id"),
    @Index(name = "idx_manual_stock_receipt_product", columnList = "product_id")
})
@Getter
@Setter
public class ManualStockReceipt extends BaseEntity {

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Product product;

    @Column(nullable = false)
    private Double quantity;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "added_by_id")
    private Long addedById;

    @Column(name = "added_by_name")
    private String addedByName;
}
