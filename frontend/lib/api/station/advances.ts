import { API_BASE_URL, handleResponse } from './common';
import { fetchWithAuth } from '../fetch-with-auth';

// --- Types ---
export interface UpiCompany {
    id: number;
    companyName: string;
}

export interface ExpenseType {
    id: number;
    name: string;
}

// EAdvance (electronic advance entries: Card, UPI, Cheque, CCMS, Bank Transfer)
export interface EAdvance {
    id?: number;
    shiftId?: number;
    transactionDate?: string;
    amount: number;
    advanceType: string; // CARD, UPI, CHEQUE, CCMS, BANK_TRANSFER
    remarks?: string;
    // UPI-specific
    upiCompany?: UpiCompany;
    // Card-specific
    batchId?: string;
    tid?: string;
    customerName?: string;
    customerPhone?: string;
    cardLast4Digit?: string;
    // Shared: Card, Cheque, Bank
    bankName?: string;
    // Cheque-specific
    chequeNo?: string;
    chequeDate?: string;
    inFavorOf?: string;
    // CCMS-specific
    ccmsNumber?: string;
    // Source references
    invoiceBill?: { id: number; billNo?: string; billType?: string; netAmount?: number; customer?: { id: number; name: string } | null } | null;
    payment?: { id: number; amount?: number; customer?: { id: number; name: string } | null } | null;
}

export interface EAdvanceSummary {
    card: number;
    upi: number;
    cheque: number;
    ccms: number;
    bank_transfer: number;
    total: number;
}

// Expense (shift-level expenses)
export interface ShiftExpense {
    id?: number;
    shiftId?: number;
    expenseDate?: string;
    amount: number;
    description?: string;
    remarks?: string;
    expenseType?: ExpenseType;
}

// Station Expenses
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

// EAdvances
export const getEAdvancesByShift = (shiftId: number): Promise<EAdvance[]> =>
    fetchWithAuth(`${API_BASE_URL}/e-advances/shift/${shiftId}`).then(handleResponse);

export const getEAdvanceSummary = (shiftId: number): Promise<EAdvanceSummary> =>
    fetchWithAuth(`${API_BASE_URL}/e-advances/shift/${shiftId}/summary`).then(handleResponse);

export const createEAdvance = (eAdvance: Partial<EAdvance>): Promise<EAdvance> =>
    fetchWithAuth(`${API_BASE_URL}/e-advances`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(eAdvance),
    }).then(handleResponse);

export const deleteEAdvance = (id: number): Promise<void> =>
    fetchWithAuth(`${API_BASE_URL}/e-advances/${id}`, { method: 'DELETE' }).then(handleResponse);

// Shift Expenses
export const getExpensesByShift = (shiftId: number): Promise<ShiftExpense[]> =>
    fetchWithAuth(`${API_BASE_URL}/expenses/shift/${shiftId}`).then(handleResponse);

export const getExpenseShiftTotal = (shiftId: number): Promise<number> =>
    fetchWithAuth(`${API_BASE_URL}/expenses/shift/${shiftId}/total`).then(handleResponse);

export const createExpense = (expense: Partial<ShiftExpense>): Promise<ShiftExpense> =>
    fetchWithAuth(`${API_BASE_URL}/expenses`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(expense),
    }).then(handleResponse);

export const deleteExpense = (id: number): Promise<void> =>
    fetchWithAuth(`${API_BASE_URL}/expenses/${id}`, { method: 'DELETE' }).then(handleResponse);

// UPI Companies
export const getUpiCompanies = (): Promise<UpiCompany[]> =>
    fetchWithAuth(`${API_BASE_URL}/upi-companies`).then(handleResponse);

export const createUpiCompany = (company: Partial<UpiCompany>): Promise<UpiCompany> =>
    fetchWithAuth(`${API_BASE_URL}/upi-companies`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(company),
    }).then(handleResponse);

// Expense Types
export const getExpenseTypes = (): Promise<ExpenseType[]> =>
    fetchWithAuth(`${API_BASE_URL}/expense-types`).then(handleResponse);

export const createExpenseType = (type: Partial<ExpenseType>): Promise<ExpenseType> =>
    fetchWithAuth(`${API_BASE_URL}/expense-types`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(type),
    }).then(handleResponse);

// Station Expenses
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
