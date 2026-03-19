package com.stopforfuel.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Entity
@Table(name = "incentive", indexes = {
    @Index(name = "idx_incentive_customer_id", columnList = "customer_id"),
    @Index(name = "idx_incentive_cust_prod_active", columnList = "customer_id, product_id, active")
}, uniqueConstraints = {
    @UniqueConstraint(columnNames = {"customer_id", "product_id"})
})
@Getter
@Setter
public class Incentive extends BaseEntity {

    @NotNull(message = "Customer is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Customer customer;

    @NotNull(message = "Product is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Product product;

    @PositiveOrZero(message = "Minimum quantity must be zero or positive")
    @Column(name = "min_quantity", precision = 19, scale = 4)
    private BigDecimal minQuantity;

    @NotNull(message = "Discount rate is required")
    @PositiveOrZero(message = "Discount rate must be zero or positive")
    @Column(name = "discount_rate", nullable = false, precision = 19, scale = 4)
    private BigDecimal discountRate;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
