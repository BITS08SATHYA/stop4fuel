package com.stopforfuel.backend.entity;

import com.stopforfuel.backend.enums.ShiftStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "shifts", indexes = {
    @Index(name = "idx_shifts_status", columnList = "status"),
    @Index(name = "idx_shifts_scid", columnList = "scid")
})
@Getter
@Setter
public class Shift extends BaseEntity {

    @Column(nullable = false)
    private LocalDateTime startTime;

    private LocalDateTime endTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attendant_id")
    private User attendant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShiftStatus status;
}
