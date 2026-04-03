import { API_BASE_URL, handleResponse, PageResponse } from './common';
import { fetchWithAuth } from '../fetch-with-auth';

// --- Types ---

export interface CustomerDashboardData {
    creditLimitAmount: number;
    creditLimitLiters: number;
    consumedLiters: number;
    ledgerBalance: number;
    totalBilled: number;
    totalPaid: number;
    outstandingBalance: number;
    creditUtilization: number;
    unpaidStatements: number;
    unpaidStatementsAmount: number;
    vehicleCount: number;
    recentInvoices: CustomerRecentInvoice[];
    recentPayments: CustomerRecentPayment[];
}

export interface CustomerRecentInvoice {
    id: number;
    date: string | null;
    billNo: string;
    billType: string | null;
    netAmount: number;
    paymentStatus: string | null;
    vehicleNumber: string | null;
}

export interface CustomerRecentPayment {
    id: number;
    date: string | null;
    amount: number;
    paymentMode: string | null;
    referenceNo: string | null;
}

export interface CustomerStatement {
    id: number;
    statementNo: string;
    customerName: string | null;
    fromDate: string;
    toDate: string;
    statementDate: string;
    numberOfBills: number;
    totalAmount: number;
    netAmount: number;
    receivedAmount: number;
    balanceAmount: number;
    status: string;
}

export interface CustomerPayment {
    id: number;
    paymentDate: string;
    amount: number;
    paymentModeName: string | null;
    referenceNo: string | null;
    customerName: string | null;
    statementNo: string | null;
    billNo: string | null;
    remarks: string | null;
}

export interface CustomerInvoice {
    id: number;
    date: string;
    billNo: string;
    billType: string;
    customerName: string | null;
    vehicleNumber: string | null;
    grossAmount: number;
    totalDiscount: number;
    netAmount: number;
    paymentMode: string | null;
    paymentStatus: string;
    driverName: string | null;
}

export interface CustomerVehicle {
    id: number;
    vehicleNumber: string;
    vehicleTypeName: string | null;
    customerName: string | null;
    monthlyLimitLiters: number | null;
    consumedLiters: number | null;
}

// --- API Functions ---

export const getMyDashboard = (): Promise<CustomerDashboardData> =>
    fetchWithAuth(`${API_BASE_URL}/customer-portal/dashboard`).then(handleResponse);

export const getMyStatements = (
    page = 0, size = 10, status?: string, fromDate?: string, toDate?: string
): Promise<PageResponse<CustomerStatement>> => {
    const params = new URLSearchParams();
    params.append('page', page.toString());
    params.append('size', size.toString());
    if (status) params.append('status', status);
    if (fromDate) params.append('fromDate', fromDate);
    if (toDate) params.append('toDate', toDate);
    return fetchWithAuth(`${API_BASE_URL}/customer-portal/statements?${params}`).then(handleResponse);
};

export const getMyPayments = (page = 0, size = 10): Promise<PageResponse<CustomerPayment>> => {
    const params = new URLSearchParams();
    params.append('page', page.toString());
    params.append('size', size.toString());
    return fetchWithAuth(`${API_BASE_URL}/customer-portal/payments?${params}`).then(handleResponse);
};

export const getMyInvoices = (
    page = 0, size = 20, paymentStatus?: string, fromDate?: string, toDate?: string
): Promise<PageResponse<CustomerInvoice>> => {
    const params = new URLSearchParams();
    params.append('page', page.toString());
    params.append('size', size.toString());
    if (paymentStatus) params.append('paymentStatus', paymentStatus);
    if (fromDate) params.append('fromDate', fromDate);
    if (toDate) params.append('toDate', toDate);
    return fetchWithAuth(`${API_BASE_URL}/customer-portal/invoices?${params}`).then(handleResponse);
};

export const getMyVehicles = (): Promise<CustomerVehicle[]> =>
    fetchWithAuth(`${API_BASE_URL}/customer-portal/vehicles`).then(handleResponse);

export const getMyCreditInfo = (): Promise<Record<string, number>> =>
    fetchWithAuth(`${API_BASE_URL}/customer-portal/credit-info`).then(handleResponse);
