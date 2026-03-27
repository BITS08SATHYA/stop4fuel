package com.stopforfuel.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "stock_alert", indexes = {
    @Index(name = "idx_stock_alert_scid", columnList = "scid"),
    @Index(name = "idx_stock_alert_active", columnList = "scid, active"),
    @Index(name = "idx_stock_alert_tank", columnList = "tank_id")
})
@Getter
@Setter
public class StockAlert extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tank_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Tank tank;

    @Column(nullable = false)
    private Double availableStock;

    @Column(nullable = false)
    private Double thresholdStock;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @Column(name = "acknowledged_by")
    private String acknowledgedBy;

    @Column(name = "notified_via")
    private String notifiedVia;
}
