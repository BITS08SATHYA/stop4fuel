import { API_BASE_URL, handleResponse } from './common';
import { fetchWithAuth } from '../fetch-with-auth';

export type AuditGranularity = 'SHIFT' | 'DAY' | 'RANGE' | 'MONTH';

export interface AmountByMode {
    mode: string;
    amount: number;
    count?: number;
}

export interface AmountByType {
    type: string;
    amount: number;
}

export interface FuelReceived {
    productName: string;
    litres: number;
    purchaseAmount: number;
}

export interface ProductSale {
    productName: string;
    quantity: number;
    revenue: number;
    cogs: number;
    margin: number;
    marginPct: number;
}

export interface TestQuantity {
    litres: number;
    amount: number;
}

export interface ProductVariance {
    productName: string;
    expectedLitres: number;
    actualLitres: number;
    shrinkageLitres: number;
    shrinkagePct: number;
    flagged: boolean;
}

export interface CashIn {
    cashInvoices: number;
    billPayments: number;
    statementPayments: number;
    externalInflow: number;
}

export interface CashOut {
    creditInvoices: number;
    eAdvances: AmountByMode[];
    expenses: AmountByType[];
    stationExpenses: number;
    incentives: number;
    salaryAdvance: number;
    cashAdvanceSpent: number;
    inflowRepayments: number;
    testQuantity: TestQuantity;
}

export interface InternalTransfers {
    managementAdvance: number;
    cashAdvanceBankDeposit: number;
}

export interface CashFlow {
    in: CashIn;
    out: CashOut;
    internalTransfers: InternalTransfers;
    netPosition: number;
}

export interface Profitability {
    grossRevenue: number;
    totalCogs: number;
    grossProfit: number;
    operatingExpenses: number;
    netProfit: number;
    marginPct: number;
}

export interface BunkAuditReport {
    fromDate: string;
    toDate: string;
    granularity: AuditGranularity;
    shiftCount: number;
    cashFlow: CashFlow;
    profitability: Profitability;
    productSales: ProductSale[];
    variance: ProductVariance[];
    fuelReceived: FuelReceived[];
}

export const getAudit = (
    from: string,
    to: string,
    granularity: AuditGranularity = 'RANGE'
): Promise<BunkAuditReport> => {
    const params = new URLSearchParams({ from, to, granularity });
    return fetchWithAuth(`${API_BASE_URL}/audit?${params}`).then(handleResponse);
};

export const getAuditForShift = (shiftId: number): Promise<BunkAuditReport> =>
    fetchWithAuth(`${API_BASE_URL}/audit/shift/${shiftId}`).then(handleResponse);

export interface MonthlyAuditSummary {
    year: number;
    month: number;
    shiftCount: number;
    grossRevenue: number;
    totalCogs: number;
    grossProfit: number;
    operatingExpenses: number;
    netProfit: number;
    marginPct: number;
}

export const getAuditMonthly = (year: number): Promise<MonthlyAuditSummary[]> =>
    fetchWithAuth(`${API_BASE_URL}/audit/monthly?year=${year}`).then(handleResponse);

export const downloadAuditPdf = async (
    from: string,
    to: string,
    granularity: AuditGranularity = 'RANGE'
): Promise<void> => {
    const params = new URLSearchParams({ from, to, granularity });
    const res = await fetchWithAuth(`${API_BASE_URL}/audit/pdf?${params}`);
    if (!res.ok) throw new Error('Failed to download PDF');
    const blob = await res.blob();
    const url = URL.createObjectURL(blob);
    try {
        window.open(url, '_blank', 'noopener,noreferrer');
    } finally {
        setTimeout(() => URL.revokeObjectURL(url), 10_000);
    }
};
