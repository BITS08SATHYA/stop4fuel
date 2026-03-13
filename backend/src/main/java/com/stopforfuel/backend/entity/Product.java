package com.stopforfuel.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "product")
@Getter
@Setter
public class Product extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(name = "hsn_code")
    private String hsnCode;

    /** Selling price: per liter for fuel, MRP for non-fuel */
    private BigDecimal price;

    /** FUEL, LUBRICANT, ACCESSORY */
    @Column(nullable = false, columnDefinition = "varchar(255) default 'FUEL'")
    private String category = "FUEL";

    /** LITERS (fuel), PIECES (non-fuel) */
    @Column(nullable = false, columnDefinition = "varchar(255) default 'LITERS'")
    private String unit = "LITERS";

    /** Can/bottle capacity in liters (e.g., 0.5, 1.0). Null for fuel products. */
    private Double volume;

    private String brand;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grade_id")
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @com.fasterxml.jackson.annotation.JsonProperty("gradeType")
    private GradeType grade;

    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean active = true;
}
