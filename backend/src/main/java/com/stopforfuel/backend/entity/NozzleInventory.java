package com.stopforfuel.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "nozzle_inventory", indexes = {
    @Index(name = "idx_nozzle_inv_shift_id", columnList = "shift_id"),
    @Index(name = "idx_nozzle_inv_nozzle_id", columnList = "nozzle_id"),
    @Index(name = "idx_nozzle_inv_date", columnList = "date"),
    @Index(name = "idx_nozzle_inv_nozzle_date", columnList = "nozzle_id, date DESC")
})
@Getter
@Setter
public class NozzleInventory extends BaseEntity {

    @Column(nullable = false)
    private LocalDate date;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "nozzle_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Nozzle nozzle;

    private Double openMeterReading;
    
    private Double closeMeterReading;
    
    private Double sales;
}
