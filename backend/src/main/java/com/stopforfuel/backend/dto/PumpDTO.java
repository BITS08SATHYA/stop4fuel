package com.stopforfuel.backend.dto;

import com.stopforfuel.backend.entity.Pump;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class PumpDTO {
    private Long id;
    private String name;
    private boolean active;
    private Long scid;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static PumpDTO from(Pump p) {
        return PumpDTO.builder()
                .id(p.getId())
                .name(p.getName())
                .active(p.isActive())
                .scid(p.getScid())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
