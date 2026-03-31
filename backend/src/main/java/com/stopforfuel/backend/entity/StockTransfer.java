package com.stopforfuel.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.stopforfuel.backend.enums.StockLocation;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "stock_transfer")
@Getter
@Setter
public class StockTransfer extends BaseEntity {

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Product product;

    @Column(nullable = false)
    private Double quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StockLocation fromLocation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StockLocation toLocation;

    @Column(nullable = false)
    private LocalDateTime transferDate;

    private String remarks;

    private String transferredBy;
}
