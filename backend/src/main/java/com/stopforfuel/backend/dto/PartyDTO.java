package com.stopforfuel.backend.dto;

import com.stopforfuel.backend.entity.Party;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class PartyDTO {
    private Long id;
    private String partyType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static PartyDTO from(Party p) {
        return PartyDTO.builder()
                .id(p.getId())
                .partyType(p.getPartyType())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
