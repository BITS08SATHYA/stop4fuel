package com.stopforfuel.backend.dto;

import com.stopforfuel.backend.entity.Company;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class CompanyDTO {
    private Long id;
    private String name;
    private LocalDate openDate;
    private String sapCode;
    private String gstNo;
    private String site;
    private String type;
    private String address;
    private Long scid;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CompanyDTO from(Company c) {
        return CompanyDTO.builder()
                .id(c.getId())
                .name(c.getName())
                .openDate(c.getOpenDate())
                .sapCode(c.getSapCode())
                .gstNo(c.getGstNo())
                .site(c.getSite())
                .type(c.getType())
                .address(c.getAddress())
                .scid(c.getScid())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
