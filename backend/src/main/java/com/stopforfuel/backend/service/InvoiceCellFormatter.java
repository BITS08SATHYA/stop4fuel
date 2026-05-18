package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.PurchaseInvoice;

/**
 * Purchase-invoice "Invoice No" cell formatting, shared by the auditor
 * registers: "{sapEntryNumber}/({invoiceNumber})" when a SAP entry number is
 * present, otherwise just the invoice number.
 */
public final class InvoiceCellFormatter {

    private InvoiceCellFormatter() {}

    public static String formatInvoiceCell(PurchaseInvoice pi) {
        String inv = pi.getInvoiceNumber();
        String sap = pi.getSapEntryNumber();
        if (sap != null && !sap.isBlank()) {
            return sap + "/(" + (inv != null ? inv : "-") + ")";
        }
        return inv != null ? inv : "";
    }
}
