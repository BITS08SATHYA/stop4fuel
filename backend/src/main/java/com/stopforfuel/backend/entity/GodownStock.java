package com.stopforfuel.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "godown_stock", indexes = {
    @Index(name = "idx_godown_stock_scid", columnList = "scid")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_godown_stock_product_scid", columnNames = {"product_id", "scid"})
})
@Getter
@Setter
public class GodownStock extends BaseEntity {

    @NotNull(message = "Product is required")
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Product product;

    @PositiveOrZero(message = "Current stock must be zero or positive")
    @Column(nullable = false)
    private Double currentStock = 0.0;

    @PositiveOrZero(message = "Reorder level must be zero or positive")
    private Double reorderLevel = 0.0;

    @PositiveOrZero(message = "Max stock must be zero or positive")
    private Double maxStock = 0.0;

    private String location;

    private LocalDate lastRestockDate;
}
