package com.stopforfuel.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "pump_session_readings", indexes = {
    @Index(name = "idx_psr_session", columnList = "pump_session_id")
})
@Getter
@Setter
public class PumpSessionReading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pump_session_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private PumpSession pumpSession;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "nozzle_id", nullable = false)
    private Nozzle nozzle;

    @Column(precision = 12, scale = 2)
    private BigDecimal openReading;

    @Column(precision = 12, scale = 2)
    private BigDecimal closeReading;

    @Column(precision = 12, scale = 2)
    private BigDecimal litersSold;

    @Column(precision = 12, scale = 2)
    private BigDecimal salesAmount;
}
