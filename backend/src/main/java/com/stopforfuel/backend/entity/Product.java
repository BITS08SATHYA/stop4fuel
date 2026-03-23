package com.stopforfuel.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "product")
@Getter
@Setter
public class Product extends BaseEntity {

    @NotBlank(message = "Product name is required")
    @Size(max = 255, message = "Product name must not exceed 255 characters")
    @Column(nullable = false)
    private String name;

    @Column(name = "hsn_code")
    private String hsnCode;

    @PositiveOrZero(message = "Price must be zero or positive")
    private BigDecimal price;

    @NotBlank(message = "Category is required")
    @Column(nullable = false, columnDefinition = "varchar(255) default 'FUEL'")
    private String category = "FUEL";

    @NotBlank(message = "Unit is required")
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
    @JoinColumn(name = "oil_type_id")
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private OilType oilType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grade_id")
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @com.fasterxml.jackson.annotation.JsonProperty("gradeType")
    private GradeType grade;

    /** GST rate percentage (e.g., 18.0 for 18%) */
    @Column(name = "gst_rate", precision = 5, scale = 2)
    private BigDecimal gstRate;

    /**
     * Fuel family grouping: PETROL (includes Xtra Premium), DIESEL (includes Xtra Mile).
     * Null for non-fuel products (oils, coolants, etc.).
     */
    @Column(name = "fuel_family")
    private String fuelFamily;

    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean active = true;
}
