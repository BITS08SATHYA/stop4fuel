package com.stopforfuel.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "messages", indexes = {
        @Index(name = "idx_msg_conv_id", columnList = "conversation_id, id DESC")
})
@Getter
@Setter
public class Message extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @Column(name = "sender_user_id", nullable = false)
    private Long senderUserId;

    @Column(name = "text", nullable = false, columnDefinition = "TEXT")
    private String text;

    /** Reserved for future "Cashier joined" / "Shift closed" system lines. */
    @Column(name = "system_generated", nullable = false)
    private boolean systemGenerated = false;
}
