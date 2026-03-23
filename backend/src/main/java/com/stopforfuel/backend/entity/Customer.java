package com.stopforfuel.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "customer")
@PrimaryKeyJoinColumn(name = "id") // Joins with PersonEntity.id
@Getter
@Setter
public class Customer extends User {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "party_id")
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Party party;

    @PositiveOrZero(message = "Credit limit amount must be zero or positive")
    @Column(name = "credit_limit_amount")
    private BigDecimal creditLimitAmount;

    @PositiveOrZero(message = "Credit limit liters must be zero or positive")
    @Column(name = "credit_limit_liters")
    private BigDecimal creditLimitLiters;

    @Column(name = "consumed_liters")
    private BigDecimal consumedLiters = BigDecimal.ZERO;

    /** Customer GSTIN for credit invoicing */
    @Column(name = "gst_number", length = 15)
    private String gstNumber;

    /** Customer category (dynamic master data with GOVERNMENT/NON_GOVERNMENT type) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_category_id")
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "customers"})
    private CustomerCategory customerCategory;

    /** GPS coordinates for customer location */
    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    /** Statement preferences */
    @Column(name = "statement_frequency", length = 20)
    private String statementFrequency; // MONTHLY, BIWEEKLY, WEEKLY, CUSTOM

    @Column(name = "statement_grouping", length = 20)
    private String statementGrouping; // CUSTOMER_WISE, VEHICLE_WISE, BILL_WISE

    @OneToMany(mappedBy = "customer")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private java.util.List<Vehicle> vehicles = new java.util.ArrayList<>();

    /**
     * Uses the inherited `status` field from User entity.
     * Values: ACTIVE, INACTIVE, BLOCKED
     */
    @com.fasterxml.jackson.annotation.JsonProperty("isActive")
    public boolean isActive() {
        String s = getStatus();
        return s == null || "ACTIVE".equals(s);
    }

    public boolean isBlocked() {
        return "BLOCKED".equals(getStatus());
    }

    public boolean canRaiseInvoice() {
        String s = getStatus();
        return s == null || "ACTIVE".equals(s);
    }
}
