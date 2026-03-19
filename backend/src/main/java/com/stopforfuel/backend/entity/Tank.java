package com.stopforfuel.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "tank")
@Getter
@Setter
public class Tank extends BaseEntity {

    @NotBlank(message = "Tank name is required")
    @Column(nullable = false)
    private String name;

    @NotNull(message = "Tank capacity is required")
    @Positive(message = "Tank capacity must be positive")
    @Column(nullable = false)
    private Double capacity;

    @PositiveOrZero(message = "Available stock must be zero or positive")
    @Column(nullable = false)
    private Double availableStock = 0.0;

    @NotNull(message = "Product is required for tank")
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Product product;

    @Column(nullable = false)
    private boolean active = true;
}
