package com.stopforfuel.backend.entity;

import com.stopforfuel.backend.enums.ConversationType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "conversations", indexes = {
        @Index(name = "idx_conv_scid_last_message", columnList = "scid, last_message_at DESC")
})
@Getter
@Setter
public class Conversation extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    private ConversationType type = ConversationType.DIRECT;

    /** Denormalized for list-view sorting without a sub-join. */
    @Column(name = "last_message_id")
    private Long lastMessageId;

    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    /** Truncated to 140 chars; never rendered as the authoritative message body. */
    @Column(name = "last_message_preview", length = 160)
    private String lastMessagePreview;

    @Column(name = "created_by")
    private Long createdBy;
}
