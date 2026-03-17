package com.stopforfuel.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "utility_bills")
@Getter
@Setter
public class UtilityBill extends BaseEntity {

    private String billType = "ELECTRICITY"; // ELECTRICITY, WATER

    private String provider;
    private String consumerNumber;

    private LocalDate billDate;
    private LocalDate dueDate;

    private Double billAmount;
    private Double paidAmount = 0.0;

    private String status = "PENDING"; // PENDING, PAID, OVERDUE

    private Double unitsConsumed;

    private String billPeriod;

    @Column(columnDefinition = "TEXT")
    private String remarks;
}
