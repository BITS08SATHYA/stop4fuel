package com.stopforfuel.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@MappedSuperclass
@Getter
@Setter
public abstract class BaseEntity {

    @PrePersist
    protected void onCreate() {
        if (scid == null) {
            scid = 1L;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Site Company ID (Tenant ID).
     * All data is isolated by this ID.
     */
    @Column(name = "scid", nullable = false, columnDefinition = "bigint default 1")
    private Long scid;

    /**
     * Shift Closing ID.
     * Links this record to a specific Shift Cycle.
     * Nullable because some setup data (like Company info) might not belong to a shift.
     */
    @Column(name = "shift_id")
    private Long shiftId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
