package com.stopforfuel.backend.dto;

import com.stopforfuel.backend.entity.InvoiceBill;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Row in the admin Orphan Bills list. One bill = one row.
 *
 * failureType:
 *   NULL_SHIFT       — bill has shift_id IS NULL (legacy import or shift-gap creation)
 *   NO_STATEMENT     — Statement-party customer's bill not yet linked to a statement
 *   BOTH             — both of the above on the same bill
 *
 * Suggested-fix fields (suggestedShiftId, suggestedStatementId/No) are pre-computed by the
 * service so the UI can show a one-line "→ Shift 2614, attach to S-12246" hint per row and
 * the Auto-fix button can fire without a follow-up call.
 */
@Getter
@Builder
public class OrphanBillDTO {
    private Long id;
    private String billNo;
    private LocalDateTime billDate;
    private LocalDateTime createdAt;
    private BigDecimal netAmount;

    private Long customerId;
    private String customerName;
    private String partyType; // "Statement" / "Local" / null

    private Long shiftId;          // current value (null when NULL_SHIFT)
    private Long statementId;       // current value (null when NO_STATEMENT)
    private String statementNo;

    private String failureType;     // NULL_SHIFT / NO_STATEMENT / BOTH
    private Long suggestedShiftId;  // shift whose [start, end] covers bill_date; null if none
    private Long suggestedStatementId; // covering DRAFT/NOT_PAID statement; null if none / Local
    private String suggestedStatementNo;

    public static OrphanBillDTO from(InvoiceBill bill,
                                     Long suggestedShiftId,
                                     Long suggestedStatementId,
                                     String suggestedStatementNo) {
        boolean nullShift = bill.getShiftId() == null;
        boolean noStatement = bill.getStatement() == null
                && bill.getCustomer() != null
                && bill.getCustomer().getParty() != null
                && "Statement".equalsIgnoreCase(bill.getCustomer().getParty().getPartyType());
        String failure = nullShift && noStatement ? "BOTH"
                : nullShift ? "NULL_SHIFT"
                : "NO_STATEMENT";

        String partyType = null;
        try {
            if (bill.getCustomer() != null && bill.getCustomer().getParty() != null) {
                partyType = bill.getCustomer().getParty().getPartyType();
            }
        } catch (org.hibernate.LazyInitializationException ignored) {}

        return OrphanBillDTO.builder()
                .id(bill.getId())
                .billNo(bill.getBillNo())
                .billDate(bill.getDate())
                .createdAt(bill.getCreatedAt())
                .netAmount(bill.getNetAmount())
                .customerId(bill.getCustomer() != null ? bill.getCustomer().getId() : null)
                .customerName(bill.getCustomer() != null ? bill.getCustomer().getName() : null)
                .partyType(partyType)
                .shiftId(bill.getShiftId())
                .statementId(bill.getStatement() != null ? bill.getStatement().getId() : null)
                .statementNo(bill.getStatement() != null ? bill.getStatement().getStatementNo() : null)
                .failureType(failure)
                .suggestedShiftId(suggestedShiftId)
                .suggestedStatementId(suggestedStatementId)
                .suggestedStatementNo(suggestedStatementNo)
                .build();
    }
}
