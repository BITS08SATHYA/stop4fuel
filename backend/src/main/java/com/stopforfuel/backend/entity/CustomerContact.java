package com.stopforfuel.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * A named contact person under a customer (owner, manager, driver, accountant …)
 * so the station has every reachable phone number in one place.
 */
@Entity
@Table(name = "customer_contact", indexes = {
    @Index(name = "idx_customer_contact_customer_id", columnList = "customer_id")
})
@Getter
@Setter
public class CustomerContact extends BaseEntity {

    // Set by the service from the path variable, so no bean validation here —
    // @Valid on the request body runs before the service can attach the customer
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Customer customer;

    @NotBlank(message = "Contact name is required")
    @Column(nullable = false)
    private String name;

    /** Free-text role label: Owner, Manager, Driver, Accountant … */
    @Column(name = "contact_role")
    private String contactRole;

    @NotBlank(message = "Phone number is required")
    @Column(name = "phone_number", nullable = false)
    private String phoneNumber;

    @Column
    private String notes;
}
