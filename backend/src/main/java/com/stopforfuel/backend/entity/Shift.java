package com.stopforfuel.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "shifts")
@Getter
@Setter
public class Shift extends BaseEntity {

    @Column(nullable = false)
    private LocalDateTime startTime;

    private LocalDateTime endTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attendant_id")
    private User attendant;

    @Column(nullable = false)
    private String status; // OPEN, CLOSED, RECONCILED
}
