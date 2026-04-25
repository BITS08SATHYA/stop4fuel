package com.stopforfuel.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "conversation_participants",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_conv_participant", columnNames = {"conversation_id", "user_id"})
        },
        indexes = {
                @Index(name = "idx_convpart_user", columnList = "user_id"),
                @Index(name = "idx_convpart_conv", columnList = "conversation_id")
        })
@Getter
@Setter
public class ConversationParticipant extends SimpleBaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** null = participant has never opened the thread. */
    @Column(name = "last_read_at")
    private LocalDateTime lastReadAt;

    @Column(name = "last_read_message_id")
    private Long lastReadMessageId;

    /** Reserved for future mute support — not exposed in MVP. */
    @Column(name = "muted_until")
    private LocalDateTime mutedUntil;
}
