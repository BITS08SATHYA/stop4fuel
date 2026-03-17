package com.stopforfuel.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "stock_transfer")
@Getter
@Setter
public class StockTransfer extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Product product;

    @Column(nullable = false)
    private Double quantity;

    @Column(nullable = false)
    private String fromLocation; // GODOWN or CASHIER

    @Column(nullable = false)
    private String toLocation; // GODOWN or CASHIER

    @Column(nullable = false)
    private LocalDateTime transferDate;

    private String remarks;

    private String transferredBy;
}
