package com.stopforfuel.backend.dto;

import com.stopforfuel.backend.entity.Customer;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Lightweight DTO for map visualization — only includes fields needed for markers.
 */
@Getter
@Builder
public class CustomerMapDTO {
    private Long id;
    private String name;
    private String status;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String groupName;
    private String address;

    public static CustomerMapDTO from(Customer c) {
        return CustomerMapDTO.builder()
                .id(c.getId())
                .name(c.getName())
                .status(c.getStatus())
                .latitude(c.getLatitude())
                .longitude(c.getLongitude())
                .groupName(c.getGroup() != null ? c.getGroup().getGroupName() : null)
                .address(c.getAddress())
                .build();
    }
}
