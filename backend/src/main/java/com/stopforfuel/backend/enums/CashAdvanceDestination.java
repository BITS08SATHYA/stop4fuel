package com.stopforfuel.backend.enums;

/**
 * For CASH-type operational advances: where the cash actually went.
 * Drives audit classification — BANK_DEPOSIT is an internal transfer
 * (money stays with the business), SPENT is real cash-out.
 *
 * Nullable on non-CASH advances; required on CASH advances going forward.
 * Historical rows stay null and are treated as SPENT by default in the audit.
 */
public enum CashAdvanceDestination {
    BANK_DEPOSIT,
    SPENT
}
