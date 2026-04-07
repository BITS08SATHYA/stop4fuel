import { API_BASE_URL, handleResponse, PageResponse } from './common';
import { fetchWithAuth } from '../fetch-with-auth';
import type { InvoiceBill } from './invoices';

// --- Types ---
export const PAYMENT_MODES = ["CASH", "CARD", "UPI", "CHEQUE", "CCMS", "BANK_TRANSFER", "NEFT", "PAYTM"] as const;
export type PaymentModeType = typeof PAYMENT_MODES[number];

export interface Statement {
    id?: number;
    statementNo: string;
    customer: { id: number; name: string; username?: string };
    fromDate: string;
    toDate: string;
    statementDate: string;
    numberOfBills: number;
    totalAmount: number;
    roundingAmount: number;
    netAmount: number;
    receivedAmount: number;
    balanceAmount: number;
    status: 'PAID' | 'NOT_PAID' | 'DRAFT';
    statementPdfUrl?: string;
}

export interface Payment {
    id?: number;
    paymentDate: string;
    amount: number;
    paymentMode: string;
    referenceNo?: string;
    customer?: { id: number; name: string; username?: string };
    statement?: { id: number; statementNo: string; netAmount: number; receivedAmount: number; balanceAmount: number };
    invoiceBill?: { id: number; billNo: string; netAmount: number };
    shiftId?: number;
    remarks?: string;
    proofImageKey?: string;
    receivedBy?: { id: number; name: string; username?: string };
    targetPaymentStatus?: string;
    scid?: number;
}

export interface LedgerEntry {
    date: string;
    type: 'DEBIT' | 'CREDIT';
    description: string;
    referenceId: number;
    debitAmount: number;
    creditAmount: number;
    runningBalance: number;
}

export interface CustomerLedger {
    customerId: number;
    fromDate: string;
    toDate: string;
    openingBalance: number;
    closingBalance: number;
    totalDebits: number;
    totalCredits: number;
    entries: LedgerEntry[];
}

export interface StatementStats {
    statementsLastMonth: number;
    paidLastMonth: number;
    amountGeneratedLastMonth: number;
    amountCollectedLastMonth: number;
    totalStatements: number;
    totalPaid: number;
    paidPercentage: number;
    totalUnpaidAmount: number;
    totalNetAmount: number;
    totalReceivedAmount: number;
    collectionRate: number;
    avgStatementAmount: number;
}

// Incentive Payments
export interface IncentivePayment {
    id?: number;
    shiftId?: number;
    paymentDate?: string;
    amount: number;
    description?: string;
    customer?: { id: number; name?: string } | null;
    invoiceBill?: { id: number; billNo?: string; billType?: string; netAmount?: number } | null;
    statement?: { id: number; statementNo?: string } | null;
}

// Statements
export const getStatements = (
    page = 0, size = 10, customerId?: number, status?: string,
    fromDate?: string, toDate?: string, search?: string, categoryType?: string
): Promise<PageResponse<Statement>> => {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (customerId) params.append('customerId', String(customerId));
    if (status) params.append('status', status);
    if (fromDate) params.append('fromDate', fromDate);
    if (toDate) params.append('toDate', toDate);
    if (search) params.append('search', search);
    if (categoryType) params.append('categoryType', categoryType);
    return fetchWithAuth(`${API_BASE_URL}/statements?${params}`).then(handleResponse);
};

export const getStatementById = (id: number): Promise<Statement> =>
    fetchWithAuth(`${API_BASE_URL}/statements/${id}`).then(handleResponse);

export const getStatementsByCustomer = (customerId: number): Promise<Statement[]> =>
    fetchWithAuth(`${API_BASE_URL}/statements/customer/${customerId}`).then(handleResponse);

export const getOutstandingStatements = (): Promise<Statement[]> =>
    fetchWithAuth(`${API_BASE_URL}/statements/outstanding`).then(handleResponse);

export const getOutstandingByCustomer = (customerId: number): Promise<Statement[]> =>
    fetchWithAuth(`${API_BASE_URL}/statements/outstanding/customer/${customerId}`).then(handleResponse);

export const generateStatement = (
    customerId: number, fromDate: string, toDate: string,
    filters?: { vehicleId?: number; productId?: number; billIds?: number[] }
): Promise<Statement> => {
    const params = new URLSearchParams({
        customerId: String(customerId),
        fromDate,
        toDate,
    });
    if (filters?.vehicleId) params.append('vehicleId', String(filters.vehicleId));
    if (filters?.productId) params.append('productId', String(filters.productId));
    if (filters?.billIds?.length) {
        filters.billIds.forEach(id => params.append('billIds', String(id)));
    }
    return fetchWithAuth(`${API_BASE_URL}/statements/generate?${params}`, {
        method: 'POST',
    }).then(handleResponse);
};

export const previewStatementBills = (
    customerId: number, fromDate: string, toDate: string,
    filters?: { vehicleId?: number; productId?: number }
): Promise<InvoiceBill[]> => {
    const params = new URLSearchParams({
        customerId: String(customerId),
        fromDate,
        toDate,
    });
    if (filters?.vehicleId) params.append('vehicleId', String(filters.vehicleId));
    if (filters?.productId) params.append('productId', String(filters.productId));
    return fetchWithAuth(`${API_BASE_URL}/statements/preview?${params}`).then(handleResponse);
};

export const getStatementBills = (statementId: number): Promise<InvoiceBill[]> =>
    fetchWithAuth(`${API_BASE_URL}/statements/${statementId}/bills`).then(handleResponse);

export const removeBillFromStatement = (statementId: number, billId: number): Promise<Statement> =>
    fetchWithAuth(`${API_BASE_URL}/statements/${statementId}/bills/${billId}`, {
        method: 'DELETE',
    }).then(handleResponse);

export const deleteStatement = (id: number): Promise<void> =>
    fetchWithAuth(`${API_BASE_URL}/statements/${id}`, { method: 'DELETE' }).then(handleResponse);

export const generateStatementPdf = (id: number): Promise<Statement> =>
    fetchWithAuth(`${API_BASE_URL}/statements/${id}/generate-pdf`, { method: 'POST' }).then(handleResponse);

export const getStatementPdfUrl = (id: number): Promise<string> =>
    fetchWithAuth(`${API_BASE_URL}/statements/${id}/pdf-url`).then(handleResponse).then((data: { url: string }) => data.url);

export const approveStatement = (id: number): Promise<Statement> =>
    fetchWithAuth(`${API_BASE_URL}/statements/${id}/approve`, { method: 'POST' }).then(handleResponse);

export const autoGenerateStatementDrafts = (): Promise<{ count: number }> =>
    fetchWithAuth(`${API_BASE_URL}/statements/auto-generate`, { method: 'POST' }).then(handleResponse);

export const getStatementStats = (): Promise<StatementStats> =>
    fetchWithAuth(`${API_BASE_URL}/statements/stats`).then(handleResponse);

// Payments
export const getPayments = (
    page = 0, size = 10, categoryType?: string,
    paidAgainst?: string, fromDate?: string, toDate?: string
): Promise<PageResponse<Payment>> => {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (categoryType) params.append('categoryType', categoryType);
    if (paidAgainst) params.append('paidAgainst', paidAgainst);
    if (fromDate) params.append('fromDate', fromDate);
    if (toDate) params.append('toDate', toDate);
    return fetchWithAuth(`${API_BASE_URL}/payments?${params}`).then(handleResponse);
};

export const exportPaymentsPdf = (
    categoryType?: string, paidAgainst?: string, fromDate?: string, toDate?: string
): Promise<Blob> => {
    const params = new URLSearchParams();
    if (categoryType) params.append('categoryType', categoryType);
    if (paidAgainst) params.append('paidAgainst', paidAgainst);
    if (fromDate) params.append('fromDate', fromDate);
    if (toDate) params.append('toDate', toDate);
    return fetchWithAuth(`${API_BASE_URL}/payments/export/pdf?${params}`).then(r => r.blob());
};

export const exportPaymentsExcel = (
    categoryType?: string, paidAgainst?: string, fromDate?: string, toDate?: string
): Promise<Blob> => {
    const params = new URLSearchParams();
    if (categoryType) params.append('categoryType', categoryType);
    if (paidAgainst) params.append('paidAgainst', paidAgainst);
    if (fromDate) params.append('fromDate', fromDate);
    if (toDate) params.append('toDate', toDate);
    return fetchWithAuth(`${API_BASE_URL}/payments/export/excel?${params}`).then(r => r.blob());
};

export const downloadPaymentReceipt = (paymentId: number): Promise<Blob> =>
    fetchWithAuth(`${API_BASE_URL}/payments/${paymentId}/receipt/pdf`).then(r => r.blob());

export const getPaymentsByCustomer = (customerId: number, page = 0, size = 10): Promise<PageResponse<Payment>> =>
    fetchWithAuth(`${API_BASE_URL}/payments/customer/${customerId}?page=${page}&size=${size}`).then(handleResponse);

export const getPaymentsByStatement = (statementId: number): Promise<Payment[]> =>
    fetchWithAuth(`${API_BASE_URL}/payments/statement/${statementId}`).then(handleResponse);

export const getPaymentsByBill = (invoiceBillId: number): Promise<Payment[]> =>
    fetchWithAuth(`${API_BASE_URL}/payments/bill/${invoiceBillId}`).then(handleResponse);

export const getPaymentsByShift = (shiftId: number): Promise<Payment[]> =>
    fetchWithAuth(`${API_BASE_URL}/payments/shift/${shiftId}`).then(handleResponse);

export const recordStatementPayment = (statementId: number, payment: Partial<Payment>): Promise<Payment> =>
    fetchWithAuth(`${API_BASE_URL}/payments/statement/${statementId}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payment),
    }).then(handleResponse);

export const recordBillPayment = (invoiceBillId: number, payment: Partial<Payment>): Promise<Payment> =>
    fetchWithAuth(`${API_BASE_URL}/payments/bill/${invoiceBillId}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payment),
    }).then(handleResponse);

export const uploadPaymentProof = (paymentId: number, file: File): Promise<Payment> => {
    const formData = new FormData();
    formData.append('file', file);
    return fetchWithAuth(`${API_BASE_URL}/payments/${paymentId}/upload-proof`, {
        method: 'POST',
        body: formData,
    }).then(handleResponse);
};

export const getPaymentProofUrl = (paymentId: number): Promise<{ url: string }> =>
    fetchWithAuth(`${API_BASE_URL}/payments/${paymentId}/proof-url`).then(handleResponse);

// Payment Summaries
export interface StatementPaymentSummary {
    statementId: number;
    statementNo: string;
    netAmount: number;
    totalReceived: number;
    balance: number;
    payments: Payment[];
}

export interface BillPaymentSummary {
    invoiceBillId: number;
    billNo: string;
    netAmount: number;
    totalReceived: number;
    balance: number;
    payments: Payment[];
}

export const getStatementPaymentSummary = (statementId: number): Promise<StatementPaymentSummary> =>
    fetchWithAuth(`${API_BASE_URL}/payments/summary/statement/${statementId}`).then(handleResponse);

export const getBillPaymentSummary = (invoiceBillId: number): Promise<BillPaymentSummary> =>
    fetchWithAuth(`${API_BASE_URL}/payments/summary/bill/${invoiceBillId}`).then(handleResponse);

// Ledger
export const getOpeningBalance = (customerId: number, asOfDate: string): Promise<number> =>
    fetchWithAuth(`${API_BASE_URL}/ledger/opening-balance?customerId=${customerId}&asOfDate=${asOfDate}`).then(handleResponse);

export const getCustomerLedger = (customerId: number, fromDate: string, toDate: string): Promise<CustomerLedger> =>
    fetchWithAuth(`${API_BASE_URL}/ledger/customer/${customerId}?fromDate=${fromDate}&toDate=${toDate}`).then(handleResponse);

export const getOutstandingBills = (customerId: number): Promise<InvoiceBill[]> =>
    fetchWithAuth(`${API_BASE_URL}/ledger/outstanding/${customerId}`).then(handleResponse);

// Incentive Payments
export const getIncentivePayments = (): Promise<IncentivePayment[]> =>
    fetchWithAuth(`${API_BASE_URL}/incentive-payments`).then(handleResponse);

export const getIncentivePaymentsByShift = (shiftId: number): Promise<IncentivePayment[]> =>
    fetchWithAuth(`${API_BASE_URL}/incentive-payments/shift/${shiftId}`).then(handleResponse);

export const getIncentivePaymentsByCustomer = (customerId: number): Promise<IncentivePayment[]> =>
    fetchWithAuth(`${API_BASE_URL}/incentive-payments/customer/${customerId}`).then(handleResponse);

export const createIncentivePayment = (payment: Partial<IncentivePayment>): Promise<IncentivePayment> =>
    fetchWithAuth(`${API_BASE_URL}/incentive-payments`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payment),
    }).then(handleResponse);

export const deletePayment = (id: number): Promise<void> =>
    fetchWithAuth(`${API_BASE_URL}/payments/${id}`, { method: 'DELETE' }).then(handleResponse);

export const deleteIncentivePayment = (id: number): Promise<void> =>
    fetchWithAuth(`${API_BASE_URL}/incentive-payments/${id}`, { method: 'DELETE' }).then(handleResponse);

// --- Paytm POS Integration ---

export interface PaytmInitiateRequest {
    amount: number;
    invoiceBillId?: number;
    statementId?: number;
    txnType: 'CASH_INVOICE' | 'CREDIT_PAYMENT';
}

export interface PaytmInitiateResponse {
    merchantTxnId: string;
    cpayId: string;
    status: 'INITIATED';
}

export interface PaytmStatusResponse {
    merchantTxnId: string;
    status: 'INITIATED' | 'SUCCESS' | 'FAILED' | 'TIMEOUT';
    amount?: number;
    paytmTxnId?: string;
    bankTxnId?: string;
    paymentMode?: string;
    respMsg?: string;
}

export const initiatePaytmPayment = (req: PaytmInitiateRequest): Promise<PaytmInitiateResponse> =>
    fetchWithAuth(`${API_BASE_URL}/paytm/initiate`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(req),
    }).then(handleResponse);

export const getPaytmStatus = (merchantTxnId: string): Promise<PaytmStatusResponse> =>
    fetchWithAuth(`${API_BASE_URL}/paytm/status/${merchantTxnId}`).then(handleResponse);
