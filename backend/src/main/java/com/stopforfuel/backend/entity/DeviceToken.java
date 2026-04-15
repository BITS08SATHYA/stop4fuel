package com.stopforfuel.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "device_tokens", indexes = {
        @Index(name = "idx_device_tokens_user_id", columnList = "user_id"),
        @Index(name = "idx_device_tokens_fcm_token", columnList = "fcm_token", unique = true)
})
@Getter
@Setter
public class DeviceToken extends SimpleBaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "fcm_token", nullable = false, length = 4096, unique = true)
    private String fcmToken;

    @Column(name = "sns_endpoint_arn", length = 512)
    private String snsEndpointArn;

    @Column(name = "platform", nullable = false, length = 16)
    private String platform = "ANDROID";

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;
}
