package com.stopforfuel.backend.dto;

import com.stopforfuel.backend.entity.OilType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class OilTypeDTO {
    private Long id;
    private String name;
    private String description;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static OilTypeDTO from(OilType o) {
        return OilTypeDTO.builder()
                .id(o.getId())
                .name(o.getName())
                .description(o.getDescription())
                .active(o.isActive())
                .createdAt(o.getCreatedAt())
                .updatedAt(o.getUpdatedAt())
                .build();
    }
}
