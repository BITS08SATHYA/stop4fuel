package com.stopforfuel.backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class MessageDTO {

    private Long id;
    private Long conversationId;
    private Long senderUserId;
    private String senderName;
    private String text;
    private LocalDateTime createdAt;
}
