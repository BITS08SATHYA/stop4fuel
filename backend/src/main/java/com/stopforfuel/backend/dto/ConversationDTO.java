package com.stopforfuel.backend.dto;

import com.stopforfuel.backend.enums.ConversationType;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * List-view projection for a conversation. "Other participant" is pre-resolved
 * server-side so the dock can render without a second fetch.
 */
@Getter
@Setter
public class ConversationDTO {

    private Long id;
    private ConversationType type;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private LocalDateTime lastMessageAt;
    private String lastMessagePreview;

    /** Count of messages in this conversation the caller has not yet read. */
    private long unreadCount;

    private ContactDTO otherParticipant;
}
