package com.stopforfuel.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "customer_vehicle_mapper", indexes = {
    @Index(name = "idx_cvm_customer_id", columnList = "customer_id"),
    @Index(name = "idx_cvm_vehicle_id", columnList = "vehicle_id")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_cvm_customer_vehicle", columnNames = {"customer_id", "vehicle_id"})
})
@Getter
@Setter
public class CustomerVehicleMapper extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Column(name = "is_active")
    private boolean isActive = true;
}
