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
    private String phone;
    private String email;
    private String logoUrl;
    private Long ownerId;
    private String ownerName;
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
                .phone(c.getPhone())
                .email(c.getEmail())
                .logoUrl(c.getLogoUrl())
                .ownerId(c.getOwner() != null ? c.getOwner().getId() : null)
                .ownerName(c.getOwner() != null ? c.getOwner().getName() : null)
                .scid(c.getScid())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
