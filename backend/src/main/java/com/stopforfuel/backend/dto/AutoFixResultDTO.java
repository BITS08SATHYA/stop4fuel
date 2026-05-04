package com.stopforfuel.backend.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * Result of OrphanBillService.autoFix(billId).
 *
 * action values:
 *   FIXED                   — shift assigned (and statement attached if applicable); recompute done.
 *   SHIFT_ASSIGNED_LOCAL    — Local-customer bill: only shift_id was set (no statement linkage needed).
 *   NEEDS_MANUAL_SHIFT      — no shift's [start, end] window covers bill_date; admin must pick one.
 *   NEEDS_MANUAL_STATEMENT  — covering statement is FINALIZED/PAID; admin must un-finalize first.
 *   NO_STATEMENT_YET        — Statement customer, but no DRAFT/NOT_PAID statement exists for the
 *                             period containing bill_date; bill waits for next auto-gen.
 *                             This is the "system working as designed" case — not an error.
 *   NOOP                    — bill is already non-orphan (race / double-click).
 */
@Getter
@Builder
public class AutoFixResultDTO {
    private Long billId;
    private String billNo;
    private String action;
    private String reason;
    private Long oldShiftId;
    private Long newShiftId;
    private Long statementLinkedId;
    private String statementLinkedNo;
}
