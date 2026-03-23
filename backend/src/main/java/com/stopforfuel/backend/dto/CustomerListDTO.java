package com.stopforfuel.backend.dto;

import com.stopforfuel.backend.entity.Customer;
import com.stopforfuel.backend.entity.CustomerCategory;
import com.stopforfuel.backend.entity.Group;
import com.stopforfuel.backend.entity.Party;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * List DTO for Customer — excludes cognitoId, GPS coordinates, and statement preferences.
 */
@Getter
@Builder
public class CustomerListDTO {
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
    private Long scid;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Relationships
    private GroupSummary group;
    private PartySummary party;
    private CategorySummary customerCategory;

    // Credit info (operational)
    private BigDecimal creditLimitAmount;
    private BigDecimal creditLimitLiters;
    private BigDecimal consumedLiters;

    // Statement preferences
    private String statementGrouping;

    public static CustomerListDTO from(Customer c) {
        return CustomerListDTO.builder()
                .id(c.getId())
                .name(c.getName())
                .username(c.getUsername())
                .emails(c.getEmails())
                .phoneNumbers(c.getPhoneNumbers())
                .address(c.getAddress())
                .personType(c.getPersonType())
                .status(c.getStatus())
                .isActive(c.isActive())
                .joinDate(c.getJoinDate())
                .scid(c.getScid())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .group(GroupSummary.from(c.getGroup()))
                .party(PartySummary.from(c.getParty()))
                .customerCategory(CategorySummary.from(c.getCustomerCategory()))
                .creditLimitAmount(c.getCreditLimitAmount())
                .creditLimitLiters(c.getCreditLimitLiters())
                .consumedLiters(c.getConsumedLiters())
                .statementGrouping(c.getStatementGrouping())
                .build();
    }

    @Getter
    @Builder
    public static class GroupSummary {
        private Long id;
        private String groupName;

        public static GroupSummary from(Group g) {
            if (g == null) return null;
            return GroupSummary.builder().id(g.getId()).groupName(g.getGroupName()).build();
        }
    }

    @Getter
    @Builder
    public static class PartySummary {
        private Long id;
        private String partyType;

        public static PartySummary from(Party p) {
            if (p == null) return null;
            return PartySummary.builder().id(p.getId()).partyType(p.getPartyType()).build();
        }
    }

    @Getter
    @Builder
    public static class CategorySummary {
        private Long id;
        private String categoryName;
        private String categoryType;

        public static CategorySummary from(CustomerCategory cc) {
            if (cc == null) return null;
            return CategorySummary.builder().id(cc.getId()).categoryName(cc.getCategoryName()).categoryType(cc.getCategoryType()).build();
        }
    }
}
