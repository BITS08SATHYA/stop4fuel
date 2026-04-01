package com.stopforfuel.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "credit_policy", indexes = {
    @Index(name = "idx_credit_policy_scid", columnList = "scid"),
    @Index(name = "idx_credit_policy_category", columnList = "customer_category_id")
})
@Getter
@Setter
public class CreditPolicy extends BaseEntity {

    @NotBlank(message = "Policy name is required")
    @Size(max = 100)
    @Column(name = "policy_name", nullable = false)
    private String policyName;

    /** Null means this is the default/fallback policy for all categories */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_category_id")
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "customers"})
    private CustomerCategory customerCategory;

    /** Auto-block if any unpaid bill is older than this many days */
    @PositiveOrZero
    @Column(name = "aging_block_days", nullable = false)
    private Integer agingBlockDays = 90;

    /** Show on watchlist if any unpaid bill is older than this many days */
    @PositiveOrZero
    @Column(name = "aging_watch_days", nullable = false)
    private Integer agingWatchDays = 60;

    /** Show on watchlist if credit utilization >= this % */
    @PositiveOrZero
    @Column(name = "utilization_warn_percent", nullable = false)
    private Integer utilizationWarnPercent = 80;

    /** Auto-block if credit utilization >= this % */
    @PositiveOrZero
    @Column(name = "utilization_block_percent", nullable = false)
    private Integer utilizationBlockPercent = 100;

    /** Master switch: if false, scheduled scan skips auto-blocking for this category */
    @Column(name = "auto_block_enabled", nullable = false)
    private Boolean autoBlockEnabled = true;
}
