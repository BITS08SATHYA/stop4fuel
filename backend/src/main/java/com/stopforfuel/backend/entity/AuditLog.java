package com.stopforfuel.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_audit_scid_performed", columnList = "scid, performed_at"),
    @Index(name = "idx_audit_entity", columnList = "entity_type, entity_id"),
    @Index(name = "idx_audit_action", columnList = "action, scid"),
    @Index(name = "idx_audit_actor_action", columnList = "performed_by_id, action, outcome, performed_at")
})
@Getter
@Setter
public class AuditLog extends SimpleBaseEntity {

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "entity_type", length = 50)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "performed_by_id")
    private Long performedById;

    @Column(name = "performed_by_name")
    private String performedByName;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * OK, FAILED or BLOCKED. Kept as its own column rather than parsed out of the description
     * because the delete throttle counts on it — a delete that a foreign key or a permission
     * check refused destroyed nothing, and must not spend the actor's daily budget.
     */
    @Column(name = "outcome", length = 16)
    private String outcome;

    @Column(name = "scid", nullable = false)
    private Long scid;

    @Column(name = "performed_at", nullable = false)
    private LocalDateTime performedAt;
}
