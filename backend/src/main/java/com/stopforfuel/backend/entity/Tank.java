package com.stopforfuel.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "tank")
@Getter
@Setter
public class Tank extends BaseEntity {

    @Column(nullable = false)
    private String name;

    /** Tank capacity in liters */
    @Column(nullable = false)
    private Double capacity;

    /** Current available stock in liters */
    @Column(nullable = false)
    private Double availableStock = 0.0;

    /** Which product (fuel type) this tank holds */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Product product;

    @Column(nullable = false)
    private boolean active = true;
}
