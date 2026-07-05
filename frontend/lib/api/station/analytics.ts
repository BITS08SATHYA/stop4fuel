import { API_BASE_URL, handleResponse } from './common';
import { fetchWithAuth } from '../fetch-with-auth';

// --- Product Analytics ---
export interface ProductAnalyticsRow {
    productId: number;
    name: string;
    brand?: string;
    category?: string;
    unit?: string;
    price?: number;
    godownStock: number;
    cashierStock: number;
    totalStock: number;
    reorderLevel?: number;
    maxStock?: number;
    stockValue: number;
    soldQuantity: number;
    soldAmount: number;
    avgDailyQuantity: number;
    daysOfStock?: number;
    suggestedOrderQty?: number;
    lastSaleDate?: string;
    stockStatus: 'OK' | 'LOW' | 'OUT' | 'STALE';
}

export interface ProductAnalytics {
    fromDate: string;
    toDate: string;
    rangeDays: number;
    totalProducts: number;
    belowReorderCount: number;
    outOfStockCount: number;
    totalStockValue: number;
    totalSoldQuantity: number;
    totalSoldAmount: number;
    products: ProductAnalyticsRow[];
}

export const getProductAnalytics = (params?: { fromDate?: string; toDate?: string }): Promise<ProductAnalytics> => {
    const q = new URLSearchParams();
    if (params?.fromDate) q.set('fromDate', params.fromDate);
    if (params?.toDate) q.set('toDate', params.toDate);
    const qs = q.toString();
    return fetchWithAuth(`${API_BASE_URL}/analytics/products${qs ? '?' + qs : ''}`).then(handleResponse);
};

// --- Tank Analytics ---
export interface TankAnalyticsRow {
    tankId: number;
    name: string;
    productName?: string;
    capacity: number;
    currentStock: number;
    thresholdStock: number;
    fillPercent?: number;
    avgDailySales: number;
    daysToEmpty?: number;
    daysToThreshold?: number;
    projectedEmptyDate?: string;
    thresholdHitDate?: string;
    recommendedOrderDate?: string;
    suggestedOrderLiters?: number;
    lastReadingDate?: string;
    status: 'OK' | 'ORDER_SOON' | 'ORDER_NOW' | 'STAGNANT';
}

export interface TankAnalytics {
    fromDate: string;
    toDate: string;
    rangeDays: number;
    leadTimeDays: number;
    tankerLoadLiters: number;
    totalStock: number;
    totalCapacity: number;
    totalAvgDailySales: number;
    totalDeliveredInRange: number;
    nextEmptyDate?: string;
    tanks: TankAnalyticsRow[];
    dailyProductSales: { date: string; product: string; liters: number }[];
    dailyTankStock: { date: string; tank: string; openStock?: number; delivered: number }[];
    monthlyPurchases: { month: string; liters: number }[];
}

export const getTankAnalytics = (params?: {
    fromDate?: string;
    toDate?: string;
    leadTimeDays?: number;
    tankerLoadLiters?: number;
}): Promise<TankAnalytics> => {
    const q = new URLSearchParams();
    if (params?.fromDate) q.set('fromDate', params.fromDate);
    if (params?.toDate) q.set('toDate', params.toDate);
    if (params?.leadTimeDays != null) q.set('leadTimeDays', String(params.leadTimeDays));
    if (params?.tankerLoadLiters != null) q.set('tankerLoadLiters', String(params.tankerLoadLiters));
    const qs = q.toString();
    return fetchWithAuth(`${API_BASE_URL}/analytics/tanks${qs ? '?' + qs : ''}`).then(handleResponse);
};

// --- Customer Repayment Analytics ---
export interface CustomerRepaymentRow {
    customerId: number;
    name: string;
    partyType: 'STATEMENT' | 'LOCAL';
    repaymentDaysAllowed?: number;
    billedInRange: number;
    litersInRange: number;
    billCount: number;
    outstanding: number;
    oldestUnpaidDays?: number;
    avgRepaymentLagDays?: number;
    onTimePercent?: number;
    prevAvgBilled?: number;
    changePercent?: number;
    consumptionTrend: 'MORE' | 'LESS' | 'NORMAL' | 'NEW';
    overdue: boolean;
}

export interface CustomerRepaymentAnalytics {
    fromDate: string;
    toDate: string;
    rangeDays: number;
    totalBilled: number;
    totalCollected: number;
    totalOutstanding: number;
    avgRepaymentLagDays?: number;
    overdueCustomers: number;
    activeCreditCustomers: number;
    monthlyTurnover: { month: string; billed: number; collected: number }[];
    lagHistogram: { bucket: string; count: number }[];
    customers: CustomerRepaymentRow[];
}

export interface CustomerConsumption {
    customerId: number;
    name: string;
    fromDate: string;
    toDate: string;
    monthly: { month: string; quantity: number; amount: number }[];
    productMix: { product: string; quantity: number; amount: number }[];
}

export const getCustomerRepaymentAnalytics = (params?: {
    fromDate?: string;
    toDate?: string;
}): Promise<CustomerRepaymentAnalytics> => {
    const q = new URLSearchParams();
    if (params?.fromDate) q.set('fromDate', params.fromDate);
    if (params?.toDate) q.set('toDate', params.toDate);
    const qs = q.toString();
    return fetchWithAuth(`${API_BASE_URL}/analytics/customers${qs ? '?' + qs : ''}`).then(handleResponse);
};

export const getCustomerConsumption = (customerId: number, months = 12): Promise<CustomerConsumption> =>
    fetchWithAuth(`${API_BASE_URL}/analytics/customers/${customerId}?months=${months}`).then(handleResponse);
