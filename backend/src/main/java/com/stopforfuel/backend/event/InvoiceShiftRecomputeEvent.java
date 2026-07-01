package com.stopforfuel.backend.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Set;

/**
 * Published inside an invoice edit/move transaction to request an AFTER_COMMIT recompute of the
 * affected shifts' closing reports. Doing it after commit (rather than inline) means a recompute
 * failure can never roll back the invoice write itself — the report is just left stale and logged.
 * See {@link InvoiceShiftRecomputeListener}.
 */
@Getter
@RequiredArgsConstructor
public class InvoiceShiftRecomputeEvent {
    private final Long scid;
    private final String reason;
    private final Set<Long> shiftIds;
}
