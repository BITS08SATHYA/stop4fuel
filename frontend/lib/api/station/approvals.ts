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
    payload: string; // JSON string
    requestedBy: number | null;
    requestNote?: string | null;
    reviewedBy?: number | null;
    reviewNote?: string | null;
    reviewedAt?: string | null;
    createdAt: string;
    updatedAt: string;
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

export const parseApprovalPayload = (req: ApprovalRequest): Record<string, unknown> => {
    if (!req.payload) return {};
    try {
        return JSON.parse(req.payload);
    } catch {
        return {};
    }
};
