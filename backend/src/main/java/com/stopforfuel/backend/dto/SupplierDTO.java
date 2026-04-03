package com.stopforfuel.backend.dto;

import com.stopforfuel.backend.entity.Supplier;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class SupplierDTO {
    private Long id;
    private String name;
    private String contactPerson;
    private String phone;
    private String email;
    private String gstNumber;
    private String address;
    private boolean active;
    private Long scid;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static SupplierDTO from(Supplier s) {
        return SupplierDTO.builder()
                .id(s.getId())
                .name(s.getName())
                .contactPerson(s.getContactPerson())
                .phone(s.getPhone())
                .email(s.getEmail())
                .gstNumber(s.getGstNumber())
                .address(s.getAddress())
                .active(s.isActive())
                .scid(s.getScid())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }
}
