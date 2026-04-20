import { API_BASE_URL, handleResponse } from './common';
import { fetchWithAuth } from '../fetch-with-auth';

export type ApprovalRequestType =
    | 'ADD_VEHICLE'
    | 'UNBLOCK_CUSTOMER'
    | 'RAISE_CREDIT_LIMIT'
    | 'RECORD_STATEMENT_PAYMENT'
    | 'RECORD_INVOICE_PAYMENT';

export type ApprovalRequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface ApprovalRequest {
    id: number;
    requestType: ApprovalRequestType;
    status: ApprovalRequestStatus;
    customerId: number | null;
    customerName?: string | null;
    /** Server-parsed payload map. */
    payload: Record<string, unknown>;
    requestedBy: number | null;
    requestNote?: string | null;
    reviewedBy?: number | null;
    reviewNote?: string | null;
    reviewedAt?: string | null;
    createdAt: string;
    updatedAt: string;

    // Type-specific hydrated extras (nullable — only the relevant ones populated)
    billNo?: string | null;
    statementNo?: string | null;
    amount?: number | null;
    paymentMode?: string | null;
    vehicleNumber?: string | null;
    currentCreditLimitAmount?: number | null;
    requestedCreditLimitAmount?: number | null;
    currentCreditLimitLiters?: number | null;
    requestedCreditLimitLiters?: number | null;
}

export interface SubmitApprovalRequest {
    requestType: ApprovalRequestType;
    customerId?: number | null;
    payload: Record<string, unknown>;
    note?: string;
}

export const submitApprovalRequest = async (body: SubmitApprovalRequest): Promise<ApprovalRequest> => {
    const res = await fetchWithAuth(`${API_BASE_URL}/approval-requests`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
    });
    return handleResponse(res);
};

export const listPendingApprovals = async (): Promise<ApprovalRequest[]> => {
    const res = await fetchWithAuth(`${API_BASE_URL}/approval-requests/pending`);
    return handleResponse(res);
};

export const listMyApprovals = async (): Promise<ApprovalRequest[]> => {
    const res = await fetchWithAuth(`${API_BASE_URL}/approval-requests/mine`);
    return handleResponse(res);
};

export const getPendingApprovalCount = async (): Promise<number> => {
    const res = await fetchWithAuth(`${API_BASE_URL}/approval-requests/pending/count`);
    const data = await handleResponse(res);
    return data?.count ?? 0;
};

export const getPendingRequestsForInvoice = async (invoiceBillId: number): Promise<ApprovalRequest[]> => {
    const res = await fetchWithAuth(`${API_BASE_URL}/approval-requests/pending/invoice/${invoiceBillId}`);
    return handleResponse(res);
};

export const approveApprovalRequest = async (id: number, note?: string): Promise<ApprovalRequest> => {
    const res = await fetchWithAuth(`${API_BASE_URL}/approval-requests/${id}/approve`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ note: note ?? null }),
    });
    return handleResponse(res);
};

export const rejectApprovalRequest = async (id: number, note: string): Promise<ApprovalRequest> => {
    const res = await fetchWithAuth(`${API_BASE_URL}/approval-requests/${id}/reject`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ note }),
    });
    return handleResponse(res);
};

/**
 * Payload is already parsed by the backend DTO. Kept as a passthrough so callers
 * written against the older string-payload API don't need churn.
 */
export const parseApprovalPayload = (req: ApprovalRequest): Record<string, unknown> => {
    return req.payload ?? {};
};
