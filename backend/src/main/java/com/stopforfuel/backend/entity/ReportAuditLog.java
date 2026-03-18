package com.stopforfuel.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "report_audit_logs")
@Getter
@Setter
public class ReportAuditLog extends SimpleBaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private ShiftClosingReport report;

    @Column(name = "action", nullable = false)
    private String action; // LINE_ITEM_EDITED, ENTRY_TRANSFERRED_IN, ENTRY_TRANSFERRED_OUT, FINALIZED, RECOMPUTED

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "line_item_id")
    private Long lineItemId;

    @Column(name = "previous_value", precision = 19, scale = 4)
    private BigDecimal previousValue;

    @Column(name = "new_value", precision = 19, scale = 4)
    private BigDecimal newValue;

    @Column(name = "performed_by")
    private String performedBy;

    @Column(name = "performed_at")
    private LocalDateTime performedAt;

    @PrePersist
    protected void onCreateAudit() {
        if (this.performedAt == null) {
            this.performedAt = LocalDateTime.now();
        }
    }
}
