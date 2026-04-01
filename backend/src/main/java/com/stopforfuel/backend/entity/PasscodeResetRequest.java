package com.stopforfuel.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "passcode_reset_requests", indexes = {
    @Index(name = "idx_reset_scid_status", columnList = "scid, status")
})
@Getter
@Setter
public class PasscodeResetRequest extends SimpleBaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "user_name")
    private String userName;

    @Column(name = "phone", length = 15)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "processed_by")
    private String processedBy;

    @Column(name = "scid", nullable = false)
    private Long scid;

    public enum Status {
        PENDING, APPROVED, REJECTED
    }
}
