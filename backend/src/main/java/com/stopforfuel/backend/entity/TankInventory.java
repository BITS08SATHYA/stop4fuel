package com.stopforfuel.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "tank_inventory")
@Getter
@Setter
public class TankInventory extends BaseEntity {

    @NotNull(message = "Date is required")
    @Column(nullable = false)
    private LocalDate date;

    @NotNull(message = "Tank is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tank_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Tank tank;

    private String openDip;
    
    private Double openStock;
    
    private Double incomeStock;
    
    private Double totalStock;
    
    private String closeDip;
    
    private Double closeStock;
    
    private Double saleStock;
}
