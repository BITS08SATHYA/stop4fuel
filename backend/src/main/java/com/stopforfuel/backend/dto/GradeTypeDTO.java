package com.stopforfuel.backend.dto;

import com.stopforfuel.backend.entity.GradeType;
import com.stopforfuel.backend.entity.OilType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class GradeTypeDTO {
    private Long id;
    private String name;
    private String description;
    private boolean active;
    private OilTypeSummary oilType;
    private Long scid;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static GradeTypeDTO from(GradeType g) {
        return GradeTypeDTO.builder()
                .id(g.getId())
                .name(g.getName())
                .description(g.getDescription())
                .active(g.isActive())
                .oilType(OilTypeSummary.from(g.getOilType()))
                .scid(g.getScid())
                .createdAt(g.getCreatedAt())
                .updatedAt(g.getUpdatedAt())
                .build();
    }

    @Getter
    @Builder
    public static class OilTypeSummary {
        private Long id;
        private String name;

        public static OilTypeSummary from(OilType o) {
            if (o == null) return null;
            return OilTypeSummary.builder().id(o.getId()).name(o.getName()).build();
        }
    }
}
