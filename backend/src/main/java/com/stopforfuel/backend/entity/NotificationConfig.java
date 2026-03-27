package com.stopforfuel.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "notification_config", indexes = {
    @Index(name = "idx_notif_config_scid", columnList = "scid"),
    @Index(name = "idx_notif_config_type", columnList = "scid, alert_type")
})
@Getter
@Setter
public class NotificationConfig extends BaseEntity {

    @NotBlank(message = "Alert type is required")
    @Column(name = "alert_type", nullable = false)
    private String alertType; // e.g., "LOW_STOCK", "SHIFT_CLOSE", etc.

    @Column(nullable = false)
    private boolean enabled = true;

    /** Roles that should receive this alert (e.g., OWNER, ADMIN, CASHIER) */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "notification_config_roles", joinColumns = @JoinColumn(name = "config_id"))
    @Column(name = "role_type")
    private Set<String> notifyRoles = new HashSet<>();

    /** Channels to send notifications via (DASHBOARD, EMAIL, SMS) */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "notification_config_channels", joinColumns = @JoinColumn(name = "config_id"))
    @Column(name = "channel")
    private Set<String> channels = new HashSet<>();
}
