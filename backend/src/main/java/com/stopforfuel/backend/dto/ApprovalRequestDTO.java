package com.stopforfuel.backend.dto;

import com.stopforfuel.backend.enums.ApprovalRequestStatus;
import com.stopforfuel.backend.enums.ApprovalRequestType;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Approval request projection served to the /approvals UIs.
 * Payload IDs are hydrated into friendly identifiers (billNo, statementNo,
 * customerName) so reviewers don't have to open a second tab to understand
 * what they're approving.
 */
@Getter
@Setter
public class ApprovalRequestDTO {

    private Long id;
    private ApprovalRequestType requestType;
    private ApprovalRequestStatus status;

    private Long customerId;
    private String customerName;

    private Long requestedBy;
    private String requestNote;

    private Long reviewedBy;
    private String reviewNote;
    private LocalDateTime reviewedAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** Already-parsed payload (server-side) so clients don't re-parse JSON. */
    private Map<String, Object> payload;

    // -------- type-specific hydrated fields (nullable; only what's relevant) --------

    /** RECORD_INVOICE_PAYMENT → InvoiceBill.billNo (e.g. A26/51) */
    private String billNo;

    /** RECORD_STATEMENT_PAYMENT → Statement.statementNo (e.g. S26/12) */
    private String statementNo;

    /** Payment types: amount + mode promoted from payload for easy display. */
    private BigDecimal amount;
    private String paymentMode;

    /** ADD_VEHICLE: promoted from payload. */
    private String vehicleNumber;

    /** RAISE_CREDIT_LIMIT: delta context — current vs requested. */
    private BigDecimal currentCreditLimitAmount;
    private BigDecimal requestedCreditLimitAmount;
    private BigDecimal currentCreditLimitLiters;
    private BigDecimal requestedCreditLimitLiters;
}
