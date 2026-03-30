package com.stopforfuel.backend.dto;

import com.stopforfuel.backend.entity.CompanyDocument;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class CompanyDocumentDTO {
    private Long id;
    private String documentType;
    private String documentName;
    private String description;
    private String fileUrl;
    private String fileName;
    private LocalDate expiryDate;
    private Long scid;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CompanyDocumentDTO from(CompanyDocument cd) {
        return CompanyDocumentDTO.builder()
                .id(cd.getId())
                .documentType(cd.getDocumentType())
                .documentName(cd.getDocumentName())
                .description(cd.getDescription())
                .fileUrl(cd.getFileUrl())
                .fileName(cd.getFileName())
                .expiryDate(cd.getExpiryDate())
                .scid(cd.getScid())
                .createdAt(cd.getCreatedAt())
                .updatedAt(cd.getUpdatedAt())
                .build();
    }
}
