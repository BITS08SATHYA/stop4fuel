package com.stopforfuel.backend.entity;

import com.stopforfuel.backend.enums.PumpSessionStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pump_sessions", indexes = {
    @Index(name = "idx_pump_sessions_scid", columnList = "scid"),
    @Index(name = "idx_pump_sessions_status", columnList = "status"),
    @Index(name = "idx_pump_sessions_attendant", columnList = "attendant_id"),
    @Index(name = "idx_pump_sessions_shift", columnList = "shift_id")
})
@Getter
@Setter
public class PumpSession extends BaseEntity {

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "pump_id", nullable = false)
    private Pump pump;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attendant_id", nullable = false)
    private User attendant;

    @Column(nullable = false)
    private LocalDateTime startTime;

    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PumpSessionStatus status;

    @OneToMany(mappedBy = "pumpSession", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PumpSessionReading> readings = new ArrayList<>();
}
