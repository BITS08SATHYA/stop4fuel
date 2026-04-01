import { API_BASE_URL, handleResponse } from './common';
import { fetchWithAuth } from '../fetch-with-auth';

// --- Types ---
export interface DashboardDailyRevenue {
    date: string;
    revenue: number;
    invoiceCount: number;
    fuelVolume: number;
}

export interface DashboardProductSales {
    productName: string;
    quantity: number;
    amount: number;
}

export interface DashboardTankStatus {
    tankId: number;
    tankName: string;
    productName: string | null;
    capacity: number;
    currentStock: number;
    thresholdStock: number | null;
    productPrice: number | null;
    active: boolean;
    lastReadingDate: string | null;
}

export interface RecentInvoiceItem {
    id: number;
    date: string;
    customerName: string | null;
    billType: string;
    amount: number;
    paymentStatus: string;
}

export interface DashboardStats {
    todayRevenue: number;
    todayFuelVolume: number;
    todayInvoiceCount: number;
    todayCashInvoices: number;
    todayCreditInvoices: number;
    activeShiftId: number | null;
    activeShiftStartTime: string | null;
    shiftCash: number | null;
    shiftUpi: number | null;
    shiftCard: number | null;
    shiftExpense: number | null;
    shiftTotal: number | null;
    shiftNet: number | null;
    totalTanks: number;
    activeTanks: number;
    totalPumps: number;
    activePumps: number;
    totalNozzles: number;
    activeNozzles: number;
    totalOutstanding: number;
    totalCreditCustomers: number;
    creditAging0to30: number;
    creditAging31to60: number;
    creditAging61to90: number;
    creditAging90Plus: number;
    dailyRevenue: DashboardDailyRevenue[];
    productSales: DashboardProductSales[];
    tankStatuses: DashboardTankStatus[];
    recentInvoices: RecentInvoiceItem[];
}

// --- Invoice Analytics Dashboard ---
export interface InvoiceDailyTrend {
    date: string;
    totalCount: number;
    totalAmount: number;
    cashCount: number;
    cashAmount: number;
    creditCount: number;
    creditAmount: number;
}

export interface NameCountAmount {
    name: string;
    count: number;
    amount: number;
}

export interface ProductBreakdown {
    productName: string;
    quantity: number;
    amount: number;
}

export interface HourlyData {
    hour: number;
    count: number;
}

export interface InvoiceAnalytics {
    fromDate: string;
    toDate: string;
    totalInvoices: number;
    totalRevenue: number;
    avgInvoiceValue: number;
    cashCount: number;
    cashAmount: number;
    creditCount: number;
    creditAmount: number;
    paidCount: number;
    paidAmount: number;
    unpaidCount: number;
    unpaidAmount: number;
    dailyTrend: InvoiceDailyTrend[];
    paymentModeDistribution: NameCountAmount[];
    topCustomers: NameCountAmount[];
    productBreakdown: ProductBreakdown[];
    hourlyDistribution: HourlyData[];
}

// --- Payment Analytics Dashboard ---
export interface PaymentDailyTrend {
    date: string;
    count: number;
    amount: number;
}

export interface PaymentAnalytics {
    fromDate: string;
    toDate: string;
    totalCollected: number;
    totalPayments: number;
    avgPaymentAmount: number;
    totalOutstanding: number;
    creditCustomers: number;
    collectionRate: number;
    aging0to30: number;
    aging31to60: number;
    aging61to90: number;
    aging90Plus: number;
    dailyTrend: PaymentDailyTrend[];
    paymentModeBreakdown: NameCountAmount[];
    topCustomers: NameCountAmount[];
}

// --- System Health ---
export interface SystemHealth {
    totalCustomers: number;
    totalEmployees: number;
    totalUsers: number;
    activeShiftCount: number;
    todayAttendanceCount: number;
    totalProducts: number;
}

// Dashboard
export const getDashboardStats = (): Promise<DashboardStats> =>
    fetchWithAuth(`${API_BASE_URL}/dashboard/stats`).then(handleResponse);

export const getSystemHealth = (): Promise<SystemHealth> =>
    fetchWithAuth(`${API_BASE_URL}/dashboard/system-health`).then(handleResponse);

export const getInvoiceAnalytics = (from?: string, to?: string): Promise<InvoiceAnalytics> => {
    const params = new URLSearchParams();
    if (from) params.append('from', from);
    if (to) params.append('to', to);
    const qs = params.toString();
    return fetchWithAuth(`${API_BASE_URL}/dashboard/invoice-analytics${qs ? `?${qs}` : ''}`).then(handleResponse);
};

export const getPaymentAnalytics = (from?: string, to?: string): Promise<PaymentAnalytics> => {
    const params = new URLSearchParams();
    if (from) params.append('from', from);
    if (to) params.append('to', to);
    const qs = params.toString();
    return fetchWithAuth(`${API_BASE_URL}/dashboard/payment-analytics${qs ? `?${qs}` : ''}`).then(handleResponse);
};
