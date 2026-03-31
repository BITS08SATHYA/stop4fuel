import { API_BASE_URL, handleResponse } from './common';
import { fetchWithAuth } from '../fetch-with-auth';
import type { Product } from './products';
import type { InvoiceBill } from './invoices';
import type { Statement, Payment } from './payments';
import type { PageResponse } from './common';

// --- Types ---
export interface Vehicle {
    id: number;
    vehicleNumber: string;
    vehicleType?: { id: number; name: string };
    preferredProduct?: { id: number; name: string; fuelFamily?: string };
    maxCapacity?: number;
    maxLitersPerMonth?: number;
    consumedLiters?: number;
    status?: string;
    isActive: boolean;
    customer?: { id: number; name: string };
}

export interface Customer {
    id: number;
    name: string;
    username?: string;
    address?: string;
    phoneNumbers?: string;
    active: boolean;
    statementGrouping?: string;
}

export interface User {
    id: number;
    name: string;
    username: string;
    role: {
        id: number;
        roleType: string;
    };
}

// Customer Categories
export interface CustomerCategoryType {
    id: number;
    categoryName: string;
    categoryType: string;
    description?: string;
}

// Credit Management
export interface CreditCustomerSummary {
    customerId: number;
    customerName: string;
    phoneNumbers: string[] | null;
    groupName: string | null;
    categoryType: string | null;
    categoryName: string | null;
    status: string | null;
    creditLimitAmount: number | null;
    ledgerBalance: number;
    totalBilled: number;
    totalPaid: number;
    totalOutstanding: number;
    aging0to30: number;
    aging31to60: number;
    aging61to90: number;
    aging90Plus: number;
    pendingBillCount: number;
    totalBillCount: number;
    totalPaymentCount: number;
    pendingStatementCount: number;
}

export interface CreditOverview {
    totalOutstanding: number;
    totalAging0to30: number;
    totalAging31to60: number;
    totalAging61to90: number;
    totalAging90Plus: number;
    totalCreditCustomers: number;
    totalCustomers: number;
    govtOutstanding: number;
    nonGovtOutstanding: number;
    customers: CreditCustomerSummary[];
}

export interface CreditCustomerDetail {
    unpaidBills: InvoiceBill[];
    paidBills: InvoiceBill[];
    statements: Statement[];
    payments: Payment[];
}

// Incentives
export interface Incentive {
    id?: number;
    customer: { id: number } | Customer;
    product: { id: number } | Product;
    minQuantity?: number;
    discountRate: number;
    active: boolean;
}

// Customers & Vehicles Search
export const getCustomers = (search?: string): Promise<any> => {
    const url = search ? `${API_BASE_URL}/customers?search=${encodeURIComponent(search)}` : `${API_BASE_URL}/customers`;
    return fetchWithAuth(url).then(handleResponse);
};

export const getVehicles = (search?: string): Promise<Vehicle[]> => {
    const url = search ? `${API_BASE_URL}/vehicles?search=${encodeURIComponent(search)}` : `${API_BASE_URL}/vehicles`;
    return fetchWithAuth(url).then(handleResponse);
};

export const getVehiclesByCustomer = (customerId: number): Promise<Vehicle[]> =>
    fetchWithAuth(`${API_BASE_URL}/vehicles/customer/${customerId}`).then(handleResponse);

export const searchVehicles = (query: string): Promise<Vehicle[]> =>
    fetchWithAuth(`${API_BASE_URL}/vehicles/search?q=${encodeURIComponent(query)}`).then(handleResponse);

// Customer Categories
export const getCustomerCategories = (): Promise<CustomerCategoryType[]> =>
    fetchWithAuth(`${API_BASE_URL}/customer-categories`).then(handleResponse);

export const createCustomerCategory = (category: Partial<CustomerCategoryType>): Promise<CustomerCategoryType> =>
    fetchWithAuth(`${API_BASE_URL}/customer-categories`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(category),
    }).then(handleResponse);

export const updateCustomerCategory = (id: number, category: Partial<CustomerCategoryType>): Promise<CustomerCategoryType> =>
    fetchWithAuth(`${API_BASE_URL}/customer-categories/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(category),
    }).then(handleResponse);

export const deleteCustomerCategory = (id: number): Promise<void> =>
    fetchWithAuth(`${API_BASE_URL}/customer-categories/${id}`, { method: 'DELETE' }).then(handleResponse);

// Credit Management
export const getCreditOverview = (categoryType?: string): Promise<CreditOverview> => {
    const params = new URLSearchParams();
    if (categoryType) params.append('categoryType', categoryType);
    const qs = params.toString();
    return fetchWithAuth(`${API_BASE_URL}/credit/overview${qs ? `?${qs}` : ''}`).then(handleResponse);
};

export const getCreditCustomerDetail = (customerId: number): Promise<CreditCustomerDetail> =>
    fetchWithAuth(`${API_BASE_URL}/credit/customer/${customerId}`).then(handleResponse);

// Update Customer Credit Limits (for "Set as Limit" workflow)
export const updateCustomerCreditLimits = (id: number, limits: { creditLimitAmount?: number | null; creditLimitLiters?: number | null }): Promise<any> =>
    fetchWithAuth(`${API_BASE_URL}/customers/${id}/credit-limits`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(limits)
    }).then(handleResponse);

// Update Vehicle Liter Limit (for "Set as Limit" workflow)
export const updateVehicleLiterLimit = (id: number, maxLitersPerMonth: number | null): Promise<Vehicle> =>
    fetchWithAuth(`${API_BASE_URL}/vehicles/${id}/liter-limit`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ maxLitersPerMonth })
    }).then(handleResponse);

// Customer Credit Info (for invoice-time validation)
export const getCustomerCreditInfo = (id: number): Promise<{
    creditLimitAmount: number | null;
    creditLimitLiters: number | null;
    consumedLiters: number | null;
    ledgerBalance: number;
    totalBilled: number;
    totalPaid: number;
}> => fetchWithAuth(`${API_BASE_URL}/customers/${id}/credit-info`).then(handleResponse);

// Block / Unblock Customer
export const blockCustomer = (id: number): Promise<any> =>
    fetchWithAuth(`${API_BASE_URL}/customers/${id}/block`, { method: 'PATCH' }).then(handleResponse);

export const unblockCustomer = (id: number): Promise<any> =>
    fetchWithAuth(`${API_BASE_URL}/customers/${id}/unblock`, { method: 'PATCH' }).then(handleResponse);

// Incentives
export const getAllIncentives = (): Promise<Incentive[]> =>
    fetchWithAuth(`${API_BASE_URL}/incentives`).then(handleResponse);

export const getIncentivesByCustomer = (customerId: number): Promise<Incentive[]> =>
    fetchWithAuth(`${API_BASE_URL}/incentives/customer/${customerId}`).then(handleResponse);

export const createIncentive = (incentive: Partial<Incentive>): Promise<Incentive> =>
    fetchWithAuth(`${API_BASE_URL}/incentives`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(incentive),
    }).then(handleResponse);

export const updateIncentive = (id: number, incentive: Partial<Incentive>): Promise<Incentive> =>
    fetchWithAuth(`${API_BASE_URL}/incentives/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(incentive),
    }).then(handleResponse);

export const deleteIncentive = (id: number): Promise<void> =>
    fetchWithAuth(`${API_BASE_URL}/incentives/${id}`, { method: 'DELETE' }).then(handleResponse);
