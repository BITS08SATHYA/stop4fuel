package com.stopforfuel.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "customer_block_event", indexes = {
    @Index(name = "idx_block_event_customer", columnList = "customer_id"),
    @Index(name = "idx_block_event_scid", columnList = "scid")
})
@Getter
@Setter
public class CustomerBlockEvent extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Customer customer;

    /** BLOCKED or UNBLOCKED */
    @NotBlank
    @Column(name = "event_type", nullable = false, length = 20)
    private String eventType;

    /** AUTO_SCHEDULED, AUTO_INVOICE, or MANUAL */
    @NotBlank
    @Column(name = "trigger_type", nullable = false, length = 20)
    private String triggerType;

    /** Human-readable reason, e.g. "Aging 95 days exceeds 90-day threshold" */
    @Column(name = "reason", length = 500)
    private String reason;

    /** Optional notes (for manual blocks) */
    @Column(name = "notes", length = 1000)
    private String notes;

    /** Who performed the action (null for system/scheduled jobs) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performed_by_id")
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private User performedBy;

    /** Status before the event */
    @Column(name = "previous_status", length = 20)
    private String previousStatus;
}
