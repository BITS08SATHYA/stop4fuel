package com.stopforfuel.backend.dto;

import com.stopforfuel.backend.entity.Designation;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DesignationDTO {
    private Long id;
    private String name;
    private String defaultRole;
    private String description;

    public static DesignationDTO from(Designation d) {
        return DesignationDTO.builder()
                .id(d.getId())
                .name(d.getName())
                .defaultRole(d.getDefaultRole())
                .description(d.getDescription())
                .build();
    }
}
