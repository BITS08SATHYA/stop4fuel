import { API_BASE_URL, handleResponse } from './common';
import { fetchWithAuth } from '../fetch-with-auth';

export interface BankStatementRow {
    rowIndex: number;
    txnDate: string | null;
    narration: string;
    debit: number | null;
    credit: number | null;
    balance: number | null;
    rawLine: string;
}

export interface BankMatchCandidate {
    type: 'INVOICE' | 'STATEMENT';
    id: number;
    displayNo: string | null;
    customerId: number | null;
    customerName: string | null;
    amount: number;
    docDate: string | null;
    matchReason: string;
    score: number;
}

export interface BankStatementParseResponse {
    rows: BankStatementRow[];
    matchesByRow: Record<number, BankMatchCandidate[]>;
    unparsedLineCount: number;
    warnings: string[];
}

export interface BankStatementParseFilters {
    minAmount?: number;
    maxAmount?: number;
    customerNameContains?: string;
}

export const parseBankStatement = (
    file: File,
    filters: BankStatementParseFilters = {},
): Promise<BankStatementParseResponse> => {
    const params = new URLSearchParams();
    if (filters.minAmount != null && !Number.isNaN(filters.minAmount)) {
        params.set('minAmount', String(filters.minAmount));
    }
    if (filters.maxAmount != null && !Number.isNaN(filters.maxAmount)) {
        params.set('maxAmount', String(filters.maxAmount));
    }
    if (filters.customerNameContains && filters.customerNameContains.trim()) {
        params.set('customerNameContains', filters.customerNameContains.trim());
    }
    const qs = params.toString();
    const url = `${API_BASE_URL}/bank-statements/parse${qs ? `?${qs}` : ''}`;
    const formData = new FormData();
    formData.append('file', file);
    return fetchWithAuth(url, {
        method: 'POST',
        body: formData,
    }).then(handleResponse);
};
