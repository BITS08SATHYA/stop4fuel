import { API_BASE_URL, handleResponse } from './common';
import { fetchWithAuth } from '../fetch-with-auth';

/** Mirrors backend OrphanBillDTO. failureType: NULL_SHIFT | NO_STATEMENT | BOTH. */
export interface OrphanBill {
    id: number;
    billNo?: string;
    billDate?: string;
    createdAt?: string;
    netAmount: number;
    customerId?: number;
    customerName?: string;
    partyType?: string;
    shiftId?: number | null;
    statementId?: number | null;
    statementNo?: string | null;
    failureType: 'NULL_SHIFT' | 'NO_STATEMENT' | 'BOTH';
    suggestedShiftId?: number | null;
    suggestedStatementId?: number | null;
    suggestedStatementNo?: string | null;
}

/** Mirrors backend AutoFixResultDTO. action enum lives in OrphanBillService Javadoc. */
export interface AutoFixResult {
    billId: number;
    billNo?: string;
    action: 'FIXED' | 'SHIFT_ASSIGNED_LOCAL' | 'NEEDS_MANUAL_SHIFT' | 'NEEDS_MANUAL_STATEMENT' | 'NO_STATEMENT_YET' | 'NOOP' | 'ERROR';
    reason?: string;
    oldShiftId?: number | null;
    newShiftId?: number | null;
    statementLinkedId?: number | null;
    statementLinkedNo?: string | null;
}

export interface BulkAutoFixResult {
    total: number;
    fixed: number;
    needsManual: number;
    waitingForStatement: number;
    results: AutoFixResult[];
}

export const getOrphanBills = (includeHistorical = false): Promise<OrphanBill[]> =>
    fetchWithAuth(`${API_BASE_URL}/admin/orphan-bills?includeHistorical=${includeHistorical}`).then(handleResponse);

export const getOrphanBillCount = (includeHistorical = false): Promise<{ count: number }> =>
    fetchWithAuth(`${API_BASE_URL}/admin/orphan-bills/count?includeHistorical=${includeHistorical}`).then(handleResponse);

export const autoFixOrphan = (billId: number): Promise<AutoFixResult> =>
    fetchWithAuth(`${API_BASE_URL}/admin/orphan-bills/${billId}/auto-fix`, { method: 'POST' }).then(handleResponse);

export const autoFixAllOrphans = (includeHistorical = false): Promise<BulkAutoFixResult> =>
    fetchWithAuth(`${API_BASE_URL}/admin/orphan-bills/auto-fix-all?includeHistorical=${includeHistorical}`, {
        method: 'POST',
    }).then(handleResponse);

/** Set an invoice's shift_id without changing bill_date. Admin-only. Used by the manual drawer. */
export const setBillShift = (billId: number, shiftId: number) =>
    fetchWithAuth(`${API_BASE_URL}/invoices/${billId}/shift`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ shiftId }),
    }).then(handleResponse);
