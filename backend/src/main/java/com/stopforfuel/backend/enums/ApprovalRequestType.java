package com.stopforfuel.backend.enums;

public enum ApprovalRequestType {
    ADD_VEHICLE,
    UNBLOCK_CUSTOMER,
    RAISE_CREDIT_LIMIT,
    RAISE_VEHICLE_LIMIT,
    RECORD_STATEMENT_PAYMENT,
    RECORD_INVOICE_PAYMENT,
    /**
     * A privileged action an OWNER attempted past their limit (e.g. the sixth delete of the
     * day). Approving one does not replay the action — it issues the requester a short,
     * single-use grant to perform it themselves, so the audit trail records the real actor.
     */
    PRIVILEGED_ACTION
}
