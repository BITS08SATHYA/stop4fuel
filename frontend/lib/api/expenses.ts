import { fetchWithAuth } from './fetch-with-auth';
import { API_BASE_URL, handleResponse } from './common';
import type { ExpenseType } from './shifts';

// --- Types ---
export interface UtilityBill {
    id: number;
    billType: string; // ELECTRICITY, WATER
    provider: string;
    consumerNumber: string;
    billDate: string;
    dueDate: string;
    billAmount: number;
    paidAmount: number;
    status: string; // PENDING, PAID, OVERDUE
    unitsConsumed?: number;
    billPeriod?: string;
    remarks?: string;
}

export interface StationExpense {
    id: number;
    expenseType?: ExpenseType;
    amount: number;
    expenseDate: string;
    description?: string;
    paidTo?: string;
    paymentMode?: string;
    recurringType: string; // ONE_TIME, MONTHLY, QUARTERLY, ANNUAL
}

export interface ExpenseSummary {
    totalAmount: number;
    count: number;
    byCategory: Record<string, number>;
    from: string;
    to: string;
}

// --- Utility Bills ---
export const getUtilityBills = (type?: string): Promise<UtilityBill[]> => {
    const params = type ? `?type=${type}` : '';
    return fetchWithAuth(`${API_BASE_URL}/utility-bills${params}`).then(handleResponse);
};

export const getPendingUtilityBills = (): Promise<UtilityBill[]> =>
    fetchWithAuth(`${API_BASE_URL}/utility-bills/pending`).then(handleResponse);

export const createUtilityBill = (bill: Partial<UtilityBill>): Promise<UtilityBill> =>
    fetchWithAuth(`${API_BASE_URL}/utility-bills`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(bill),
    }).then(handleResponse);

export const updateUtilityBill = (id: number, bill: Partial<UtilityBill>): Promise<UtilityBill> =>
    fetchWithAuth(`${API_BASE_URL}/utility-bills/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(bill),
    }).then(handleResponse);

export const deleteUtilityBill = (id: number): Promise<void> =>
    fetchWithAuth(`${API_BASE_URL}/utility-bills/${id}`, { method: 'DELETE' }).then(handleResponse);

export const uploadUtilityBillPdf = (file: File): Promise<UtilityBill> => {
    const formData = new FormData();
    formData.append('file', file);
    return fetchWithAuth(`${API_BASE_URL}/utility-bills/upload-pdf`, {
        method: 'POST',
        body: formData,
    }).then(handleResponse);
};

export const uploadBulkUtilityBillPdfs = (files: File[]): Promise<UtilityBill[]> => {
    const formData = new FormData();
    files.forEach(f => formData.append('files', f));
    return fetchWithAuth(`${API_BASE_URL}/utility-bills/upload-bulk`, {
        method: 'POST',
        body: formData,
    }).then(handleResponse);
};

// --- Station Expenses ---
export const getStationExpenses = (from?: string, to?: string): Promise<StationExpense[]> => {
    const params = new URLSearchParams();
    if (from) params.append('from', from);
    if (to) params.append('to', to);
    const qs = params.toString();
    return fetchWithAuth(`${API_BASE_URL}/station-expenses${qs ? `?${qs}` : ''}`).then(handleResponse);
};

export const createStationExpense = (expense: Partial<StationExpense>): Promise<StationExpense> =>
    fetchWithAuth(`${API_BASE_URL}/station-expenses`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(expense),
    }).then(handleResponse);

export const updateStationExpense = (id: number, expense: Partial<StationExpense>): Promise<StationExpense> =>
    fetchWithAuth(`${API_BASE_URL}/station-expenses/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(expense),
    }).then(handleResponse);

export const deleteStationExpense = (id: number): Promise<void> =>
    fetchWithAuth(`${API_BASE_URL}/station-expenses/${id}`, { method: 'DELETE' }).then(handleResponse);

export const getExpenseSummary = (from: string, to: string): Promise<ExpenseSummary> =>
    fetchWithAuth(`${API_BASE_URL}/station-expenses/summary?from=${from}&to=${to}`).then(handleResponse);
