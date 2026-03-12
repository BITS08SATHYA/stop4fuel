package com.stopforfuel.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "nozzle")
@Getter
@Setter
public class Nozzle extends BaseEntity {

    @Column(name = "nozzle_name", nullable = false)
    private String nozzleName;

    @Column(name = "nozzle_number")
    private String nozzleNumber;

    @Column(name = "nozzle_company")
    private String nozzleCompany;

    @Column(name = "stamping_expiry_date")
    private LocalDate stampingExpiryDate;

    /** Which tank feeds this nozzle */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tank_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Tank tank;

    /** Which pump this nozzle sits on */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "pump_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Pump pump;

    @Column(nullable = false)
    private boolean active = true;
}
