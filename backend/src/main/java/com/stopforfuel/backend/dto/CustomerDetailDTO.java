package com.stopforfuel.backend.dto;

import com.stopforfuel.backend.entity.Customer;
import com.stopforfuel.backend.entity.Roles;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Detail DTO for Customer — includes all fields except cognitoId.
 */
@Getter
@Builder
public class CustomerDetailDTO {
    private Long id;
    private String name;
    private String username;
    private Set<String> emails;
    private Set<String> phoneNumbers;
    private String address;
    private String personType;
    private String status;
    private boolean isActive;
    private LocalDate joinDate;
    private Roles role;
    private Long scid;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Relationships
    private CustomerListDTO.GroupSummary group;
    private CustomerListDTO.PartySummary party;
    private CustomerListDTO.CategorySummary customerCategory;

    // Credit
    private BigDecimal creditLimitAmount;
    private BigDecimal creditLimitLiters;
    private BigDecimal consumedLiters;

    // Compliance & location
    private String gstNumber;
    private BigDecimal latitude;
    private BigDecimal longitude;

    // Statement preferences
    private String statementFrequency;
    private String statementGrouping;
    public static CustomerDetailDTO from(Customer c) {
        return CustomerDetailDTO.builder()
                .id(c.getId())
                .name(c.getName())
                .username(c.getUsername())
                .emails(c.getEmails())
                .phoneNumbers(c.getPhoneNumbers())
                .address(c.getAddress())
                .personType(c.getPersonType())
                .status(c.getStatus() != null ? c.getStatus().name() : null)
                .isActive(c.isActive())
                .joinDate(c.getJoinDate())
                .role(c.getRole())
                .scid(c.getScid())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .group(CustomerListDTO.GroupSummary.from(c.getGroup()))
                .party(CustomerListDTO.PartySummary.from(c.getParty()))
                .customerCategory(CustomerListDTO.CategorySummary.from(c.getCustomerCategory()))
                .creditLimitAmount(c.getCreditLimitAmount())
                .creditLimitLiters(c.getCreditLimitLiters())
                .consumedLiters(c.getConsumedLiters())
                .gstNumber(c.getGstNumber())
                .latitude(c.getLatitude())
                .longitude(c.getLongitude())
                .statementFrequency(c.getStatementFrequency())
                .statementGrouping(c.getStatementGrouping())
                .build();
    }
}
