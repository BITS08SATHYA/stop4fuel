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
    riskLevel: 'HIGH' | 'MEDIUM' | 'LOW' | null;
    utilizationPercent: number | null;
    oldestUnpaidDays: number;
    blockCount: number;
    lastBlockedAt: string | null;
}

// Credit Monitoring Types
export interface CreditHealth {
    customerId: number;
    customerName: string;
    status: string;
    riskLevel: 'HIGH' | 'MEDIUM' | 'LOW';
    creditLimit: number;
    ledgerBalance: number;
    totalBilled: number;
    totalPaid: number;
    utilizationPercent: number;
    oldestUnpaidDays: number;
    suggestedAction: string;
    blockCount: number;
    lastBlockedAt: string | null;
    policyName: string;
    categoryType: string | null;     // GOVERNMENT, NON_GOVERNMENT
    categoryName: string | null;
    groupName: string | null;
    statementFrequency: string | null; // null = Local/credit customer
}

export interface BlockEvent {
    id: number;
    eventType: 'BLOCKED' | 'UNBLOCKED';
    triggerType: 'AUTO_SCHEDULED' | 'AUTO_INVOICE' | 'MANUAL';
    reason: string | null;
    notes: string | null;
    performedByName: string;
    previousStatus: string;
    createdAt: string;
}

export interface ReconciliationSummary {
    health: CreditHealth;
    creditLimitLiters: number | null;
    consumedLiters: number | null;
    statementFrequency: string | null;
    statementGrouping: string | null;
    categoryType: string | null;
    categoryName: string | null;
    groupName: string | null;
    blockHistory: BlockEvent[];
}

export interface CreditPolicy {
    id?: number;
    policyName: string;
    customerCategory?: { id: number; categoryName: string; categoryType: string } | null;
    agingBlockDays: number;
    agingWatchDays: number;
    utilizationWarnPercent: number;
    utilizationBlockPercent: number;
    autoBlockEnabled: boolean;
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

/** Lightweight autocomplete endpoint — returns ALL customers (id, name, phone). No pagination cap. */
export const getCustomersForAutocomplete = (): Promise<any[]> =>
    fetchWithAuth(`${API_BASE_URL}/customers/autocomplete`).then(handleResponse);

// Customers & Vehicles Search
export const getCustomers = (search?: string, size?: number): Promise<any> => {
    const params = new URLSearchParams();
    if (search) params.set("search", search);
    if (size) params.set("size", String(size));
    const query = params.toString();
    const url = query ? `${API_BASE_URL}/customers?${query}` : `${API_BASE_URL}/customers`;
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

// Blocking Status — aggregated "why is this customer blocked?" gates
export type GateState = 'PASS' | 'WARN' | 'FAIL' | 'SKIPPED';
export type GateKey =
    | 'CUSTOMER_STATUS'
    | 'CREDIT_AMOUNT'
    | 'CREDIT_LITERS'
    | 'AGING'
    | 'VEHICLE_STATUS'
    | 'VEHICLE_MONTHLY_LITERS';

export interface BlockingGate {
    key: GateKey;
    label: string;
    state: GateState;
    value: number | string | null;
    limit: number | string | null;
    detail: string;
    progressPercent: number | null;
}

export interface BlockingStatus {
    customerId: number;
    customerName: string;
    overall: 'PASS' | 'WARN' | 'BLOCKED' | 'OVERRIDE';
    forceUnblocked: boolean;
    primaryReason: string;
    suggestedAction: string;
    gates: BlockingGate[];
}

export const getBlockingStatus = (
    id: number,
    opts?: { vehicleId?: number; invoiceAmount?: number; invoiceLiters?: number }
): Promise<BlockingStatus> => {
    const params = new URLSearchParams();
    if (opts?.vehicleId != null) params.set('vehicleId', String(opts.vehicleId));
    if (opts?.invoiceAmount != null) params.set('invoiceAmount', String(opts.invoiceAmount));
    if (opts?.invoiceLiters != null) params.set('invoiceLiters', String(opts.invoiceLiters));
    const qs = params.toString();
    const url = `${API_BASE_URL}/customers/${id}/blocking-status${qs ? `?${qs}` : ''}`;
    return fetchWithAuth(url).then(handleResponse);
};

// Block / Unblock Customer (with optional notes for audit trail)
export const blockCustomer = (id: number, notes?: string): Promise<any> =>
    fetchWithAuth(`${API_BASE_URL}/customers/${id}/block`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ notes: notes || null }),
    }).then(handleResponse);

export const unblockCustomer = (id: number, notes?: string): Promise<any> =>
    fetchWithAuth(`${API_BASE_URL}/customers/${id}/unblock`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ notes: notes || null }),
    }).then(handleResponse);

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

// --- Credit Monitoring ---
export const getWatchlist = (): Promise<CreditHealth[]> =>
    fetchWithAuth(`${API_BASE_URL}/credit/monitoring/watchlist`).then(handleResponse);

export const getCreditHealth = (customerId: number): Promise<CreditHealth> =>
    fetchWithAuth(`${API_BASE_URL}/credit/monitoring/health/${customerId}`).then(handleResponse);

export const getReconciliation = (customerId: number): Promise<ReconciliationSummary> =>
    fetchWithAuth(`${API_BASE_URL}/credit/monitoring/reconciliation/${customerId}`).then(handleResponse);

export const triggerAutoBlockScan = (): Promise<{ blockedCount: number }> =>
    fetchWithAuth(`${API_BASE_URL}/credit/monitoring/scan`, { method: 'POST' }).then(handleResponse);

export const getBlockHistory = (customerId: number): Promise<BlockEvent[]> =>
    fetchWithAuth(`${API_BASE_URL}/credit/monitoring/block-history/${customerId}`).then(handleResponse);

// --- Credit Policies ---
export const getCreditPolicies = (): Promise<CreditPolicy[]> =>
    fetchWithAuth(`${API_BASE_URL}/credit/policies`).then(handleResponse);

export const createCreditPolicy = (policy: Partial<CreditPolicy>): Promise<CreditPolicy> =>
    fetchWithAuth(`${API_BASE_URL}/credit/policies`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(policy),
    }).then(handleResponse);

export const updateCreditPolicy = (id: number, policy: Partial<CreditPolicy>): Promise<CreditPolicy> =>
    fetchWithAuth(`${API_BASE_URL}/credit/policies/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(policy),
    }).then(handleResponse);

export const deleteCreditPolicy = (id: number): Promise<void> =>
    fetchWithAuth(`${API_BASE_URL}/credit/policies/${id}`, { method: 'DELETE' }).then(handleResponse);
