package com.stopforfuel.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "product_price_history", indexes = {
    @Index(name = "idx_pph_product_id", columnList = "product_id"),
    @Index(name = "idx_pph_effective_date", columnList = "effective_date")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_pph_product_date", columnNames = {"product_id", "effective_date"})
})
@Getter
@Setter
public class ProductPriceHistory extends SimpleBaseEntity {

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Product product;

    @Column(name = "price", precision = 19, scale = 4, nullable = false)
    private BigDecimal price;
}
