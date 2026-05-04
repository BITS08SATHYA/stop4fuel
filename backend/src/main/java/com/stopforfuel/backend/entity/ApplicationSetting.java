package com.stopforfuel.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Generic key/value config table used for low-volume admin-tunable settings that don't
 * deserve their own table. First use case: the next-run override for the scheduled
 * statement auto-gen job (`auto_gen.next_run_override_date`). Schema is intentionally
 * dumb — type coercion lives in the service layer.
 */
@Entity
@Table(name = "application_setting")
@Getter
@Setter
@NoArgsConstructor
public class ApplicationSetting {

    @Id
    @Column(name = "setting_key", length = 100)
    private String key;

    @Column(name = "setting_value", length = 500)
    private String value;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}
