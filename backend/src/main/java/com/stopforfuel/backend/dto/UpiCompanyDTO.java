package com.stopforfuel.backend.dto;

import com.stopforfuel.backend.entity.UpiCompany;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class UpiCompanyDTO {
    private Long id;
    private String companyName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static UpiCompanyDTO from(UpiCompany uc) {
        return UpiCompanyDTO.builder()
                .id(uc.getId())
                .companyName(uc.getCompanyName())
                .createdAt(uc.getCreatedAt())
                .updatedAt(uc.getUpdatedAt())
                .build();
    }
}
