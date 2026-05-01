package com.stopforfuel.backend.dto;

import com.stopforfuel.backend.entity.Customer;
import lombok.Builder;
import lombok.Getter;

/**
 * Lightweight DTO for the bulk statement-order admin page.
 * Returned by GET /api/customers/statement-order and PATCH /api/customers/bulk/statement-order.
 */
@Getter
@Builder
public class StatementOrderEntry {
    private Long id;
    private String name;
    private String groupName;
    private String categoryName;
    private String statementFrequency;
    private String statementGrouping;
    private Integer statementOrder;
    private String status; // ACTIVE / BLOCKED — INACTIVE customers are filtered out upstream

    public static StatementOrderEntry from(Customer c) {
        return StatementOrderEntry.builder()
                .id(c.getId())
                .name(c.getName())
                .groupName(c.getGroup() != null ? c.getGroup().getGroupName() : null)
                .categoryName(c.getCustomerCategory() != null ? c.getCustomerCategory().getCategoryName() : null)
                .statementFrequency(c.getStatementFrequency())
                .statementGrouping(c.getStatementGrouping())
                .statementOrder(c.getStatementOrder())
                .status(c.getStatus() != null ? c.getStatus().name() : null)
                .build();
    }
}
