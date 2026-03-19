package com.stopforfuel.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "vehicle")
@Getter
@Setter
public class Vehicle extends SimpleBaseEntity {

    @NotBlank(message = "Vehicle number is required")
    @Size(max = 20, message = "Vehicle number must not exceed 20 characters")
    @Column(name = "vehicle_number", nullable = false, unique = true)
    private String vehicleNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_type_id")
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private VehicleType vehicleType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Product preferredProduct;

    @PositiveOrZero(message = "Max capacity must be zero or positive")
    @Column(name = "max_capacity")
    private BigDecimal maxCapacity;

    @PositiveOrZero(message = "Max liters per month must be zero or positive")
    @Column(name = "max_liters_per_month")
    private BigDecimal maxLitersPerMonth;

    @Column(name = "consumed_liters")
    private BigDecimal consumedLiters = BigDecimal.ZERO;

    /**
     * ACTIVE  — normal operating state
     * INACTIVE — manually disabled by admin
     * BLOCKED — automatically blocked (consumed >= limit)
     */
    @Column(name = "status")
    private String status = "ACTIVE";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Customer customer;

    // Convenience methods
    @com.fasterxml.jackson.annotation.JsonProperty("isActive")
    public boolean isActive() {
        return status == null || "ACTIVE".equals(status);
    }

    public boolean isBlocked() {
        return "BLOCKED".equals(status);
    }

    public boolean canRaiseInvoice() {
        return status == null || "ACTIVE".equals(status);
    }
}
