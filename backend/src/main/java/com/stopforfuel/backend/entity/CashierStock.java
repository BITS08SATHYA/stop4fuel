package com.stopforfuel.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "cashier_stock", uniqueConstraints = {
    @UniqueConstraint(name = "uk_cashier_stock_product_scid", columnNames = {"product_id", "scid"})
})
@Getter
@Setter
public class CashierStock extends BaseEntity {

    @NotNull(message = "Product is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Product product;

    @PositiveOrZero(message = "Current stock must be zero or positive")
    @Column(nullable = false)
    private Double currentStock = 0.0;

    @PositiveOrZero(message = "Max capacity must be zero or positive")
    private Double maxCapacity = 0.0;
}
