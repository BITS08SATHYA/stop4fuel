package com.stopforfuel.backend.dto;

import com.stopforfuel.backend.entity.VehicleType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class VehicleTypeDTO {
    private Long id;
    private String typeName;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static VehicleTypeDTO from(VehicleType vt) {
        return VehicleTypeDTO.builder()
                .id(vt.getId())
                .typeName(vt.getTypeName())
                .description(vt.getDescription())
                .createdAt(vt.getCreatedAt())
                .updatedAt(vt.getUpdatedAt())
                .build();
    }
}
