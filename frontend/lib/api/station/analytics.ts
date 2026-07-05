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
