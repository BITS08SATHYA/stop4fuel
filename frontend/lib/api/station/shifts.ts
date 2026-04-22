import { API_BASE_URL, handleResponse } from './common';
import { fetchWithAuth } from '../fetch-with-auth';

// --- Types ---
export interface Shift {
    id: number;
    startTime: string;
    endTime?: string;
    status: string; // OPEN, REVIEW, CLOSED
    attendant?: { id: number; name: string; username?: string };
    scid?: number;
}

// --- Shift Closing Report Types ---
export interface ReportLineItem {
    id: number;
    section: string; // REVENUE, ADVANCE
    category: string;
    label: string;
    quantity?: number;
    rate?: number;
    amount: number;
    sourceEntityType?: string;
    sourceEntityId?: number;
    sortOrder: number;
    originalAmount?: number;
    transferredFromReportId?: number;
    transferredToReportId?: number;
}

export interface ReportCashBillBreakdown {
    id: number;
    productName: string;
    cashLitres: number;
    cardLitres: number;
    ccmsLitres: number;
    upiLitres: number;
    chequeLitres: number;
    totalLitres: number;
}

export interface ReportAuditLog {
    id: number;
    action: string;
    description: string;
    lineItemId?: number;
    previousValue?: number;
    newValue?: number;
    performedBy: string;
    performedAt: string;
}

export interface ShiftClosingReport {
    id: number;
    shift: Shift;
    reportDate: string;
    status: string; // DRAFT, FINALIZED
    finalizedBy?: string;
    finalizedAt?: string;
    reportPdfUrl?: string;
    totalRevenue: number;
    totalAdvances: number;
    balance: number;
    cashBillAmount: number;
    creditBillAmount: number;
    lineItems: ReportLineItem[];
    cashBillBreakdowns: ReportCashBillBreakdown[];
    auditLogs: ReportAuditLog[];
}

export interface ExternalCashInflow {
    id: number;
    amount: number;
    inflowDate: string;
    source: string;
    purpose?: string;
    remarks?: string;
    status: string;
    repaidAmount: number;
    shiftId?: number;
}

export interface CashInflowRepayment {
    id: number;
    cashInflow?: ExternalCashInflow;
    amount: number;
    repaymentDate: string;
    remarks?: string;
}

// --- Print Data Types ---
export interface ShiftReportPrintData {
    companyName: string;
    employeeName: string;
    shiftId: number;
    shiftStart: string;
    shiftEnd: string;
    reportStatus: string;
    meterReadings: { pumpName: string; nozzleName: string; productName: string; openReading: number; closeReading: number; sales: number }[];
    tankReadings: { tankName: string; productName: string; openDip: string; openStock: number; incomeStock: number; totalStock: number; closeDip: string; closeStock: number; saleStock: number }[];
    salesDifferences: { productName: string; tankSale: number; meterSale: number; difference: number }[];
    salesReconciliation: { productName: string; meterLitres: number; creditLitres: number; testLitres: number; expectedCashLitres: number; actualCashLitres: number; variance: number }[];
    cashBillDetails: { billNo: string; vehicleNo: string; driverName: string; products: string; paymentMode: string; amount: number }[];
    creditBillDetails: { customerName: string; billNo: string; vehicleNo: string; products: string; amount: number }[];
    stockSummary: { productName: string; openStock: number; receipt: number; totalStock: number; sales: number; rate: number; amount: number }[];
    stockPosition: { productName: string; godownStock: number; cashierStock: number; totalStock: number; lowStock: boolean }[];
    advanceEntries: { type: string; description: string; amount: number; reference?: string }[];
    paymentEntries: { type: string; customerName: string; reference: string; paymentMode: string; amount: number }[];
    paymentModeBreakdown: { mode: string; amount: number; billCount: number }[];
}

// --- Shift Closing Workspace ---
export interface NozzleReadingRow {
    nozzleId: number;
    nozzleName: string;
    pumpName?: string;
    productName?: string;
    productPrice?: number;
    openMeterReading: number;
    closeMeterReading?: number;
    testQuantity?: number;
}

export interface TankDipRow {
    tankId: number;
    tankName: string;
    productName?: string;
    capacity?: number;
    openDip?: string;
    openStock: number;
    incomeStock?: number;
    closeDip?: string;
    closeStock?: number;
}

export interface ShiftClosingData {
    shiftId: number;
    shiftStatus: string;
    startTime: string;
    attendantName?: string;
    nozzleReadings: NozzleReadingRow[];
    tankDips: TankDipRow[];
    billPaymentTotal?: number;
    statementPaymentTotal?: number;
    externalInflowTotal?: number;
    creditBillTotal?: number;
    eAdvanceTotals?: Record<string, number>;
    opAdvanceTotals?: Record<string, number>;
    expenseTotal?: number;
    incentiveTotal?: number;
    inflowRepaymentTotal?: number;
}

export interface NozzleReadingInput {
    nozzleId: number;
    openMeterReading?: number;
    closeMeterReading: number;
    testQuantity?: number;
}

export interface TankDipInput {
    tankId: number;
    openDip?: string;
    openStock?: number;
    incomeStock?: number;
    closeDip: string;
    closeStock: number;
}

export interface ShiftClosingSubmit {
    nozzleReadings: NozzleReadingInput[];
    tankDips: TankDipInput[];
}

// Shifts
export const getShifts = (): Promise<Shift[]> =>
    fetchWithAuth(`${API_BASE_URL}/shifts`).then(handleResponse);

export const getActiveShift = (): Promise<Shift | null> =>
    fetchWithAuth(`${API_BASE_URL}/shifts/active`).then(handleResponse);

export interface CashierUser {
    id: number;
    name: string;
    role?: string;
    phone?: string;
}

export const getShiftCashiers = (): Promise<CashierUser[]> =>
    fetchWithAuth(`${API_BASE_URL}/shifts/cashiers`).then(handleResponse);

export const openShift = (shift: Partial<Shift>): Promise<Shift> =>
    fetchWithAuth(`${API_BASE_URL}/shifts/open`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(shift),
    }).then(handleResponse);

export const changeShiftAttendant = (shiftId: number, attendantId: number): Promise<Shift> =>
    fetchWithAuth(`${API_BASE_URL}/shifts/${shiftId}/attendant`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ attendantId }),
    }).then(handleResponse);

export const closeShift = (id: number): Promise<Shift> =>
    fetchWithAuth(`${API_BASE_URL}/shifts/${id}/close`, {
        method: 'POST',
    }).then(handleResponse);

// --- Shift Closing Report API ---
export const generateShiftReport = (shiftId: number): Promise<ShiftClosingReport> =>
    fetchWithAuth(`${API_BASE_URL}/shift-reports/${shiftId}/generate`, { method: 'POST' }).then(handleResponse);

export const getShiftReport = (shiftId: number): Promise<ShiftClosingReport> =>
    fetchWithAuth(`${API_BASE_URL}/shift-reports/${shiftId}`).then(handleResponse);

export const getAllShiftReports = (status?: string): Promise<ShiftClosingReport[]> => {
    const qs = status ? `?status=${status}` : '';
    return fetchWithAuth(`${API_BASE_URL}/shift-reports${qs}`).then(handleResponse);
};

export const editReportLineItem = (reportId: number, lineItemId: number, amount: number, reason?: string): Promise<ShiftClosingReport> =>
    fetchWithAuth(`${API_BASE_URL}/shift-reports/${reportId}/line-items/${lineItemId}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ amount, reason }),
    }).then(handleResponse);

export const transferReportEntry = (reportId: number, lineItemId: number, targetReportId: number, reason?: string): Promise<ShiftClosingReport> =>
    fetchWithAuth(`${API_BASE_URL}/shift-reports/${reportId}/transfer`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ lineItemId, targetReportId, reason }),
    }).then(handleResponse);

export const finalizeShiftReport = (reportId: number, finalizedBy?: string): Promise<ShiftClosingReport> =>
    fetchWithAuth(`${API_BASE_URL}/shift-reports/${reportId}/finalize`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ finalizedBy }),
    }).then(handleResponse);

export const recomputeShiftReport = (reportId: number): Promise<ShiftClosingReport> =>
    fetchWithAuth(`${API_BASE_URL}/shift-reports/${reportId}/recompute`, { method: 'POST' }).then(handleResponse);

export const getReportAuditLog = (reportId: number): Promise<ReportAuditLog[]> =>
    fetchWithAuth(`${API_BASE_URL}/shift-reports/${reportId}/audit-log`).then(handleResponse);

// --- External Cash Inflow API ---
export const getExternalCashInflows = (): Promise<ExternalCashInflow[]> =>
    fetchWithAuth(`${API_BASE_URL}/cash-inflows`).then(handleResponse);

export const getExternalCashInflowsByShift = (shiftId: number): Promise<ExternalCashInflow[]> =>
    fetchWithAuth(`${API_BASE_URL}/cash-inflows/shift/${shiftId}`).then(handleResponse);

export const createExternalCashInflow = (inflow: Partial<ExternalCashInflow>): Promise<ExternalCashInflow> =>
    fetchWithAuth(`${API_BASE_URL}/cash-inflows`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(inflow),
    }).then(handleResponse);

export const getShiftReportPrintData = (shiftId: number): Promise<ShiftReportPrintData> =>
    fetchWithAuth(`${API_BASE_URL}/shift-reports/${shiftId}/print-data`).then(handleResponse);

export const getShiftReportPdfUrl = (shiftId: number): Promise<string> =>
    fetchWithAuth(`${API_BASE_URL}/shift-reports/${shiftId}/pdf-url`).then(handleResponse).then((data: { url: string }) => data.url);

export const regenerateShiftReportPdf = (shiftId: number): Promise<string> =>
    fetchWithAuth(`${API_BASE_URL}/shift-reports/${shiftId}/regenerate-pdf`, { method: 'POST' }).then(handleResponse).then((data: { url: string }) => data.url);

export const recordCashInflowRepayment = (inflowId: number, repayment: Partial<CashInflowRepayment>): Promise<CashInflowRepayment> =>
    fetchWithAuth(`${API_BASE_URL}/cash-inflows/${inflowId}/repay`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(repayment),
    }).then(handleResponse);

export const getCashInflowRepayments = (inflowId: number): Promise<CashInflowRepayment[]> =>
    fetchWithAuth(`${API_BASE_URL}/cash-inflows/${inflowId}/repayments`).then(handleResponse);

// --- Shift Closing Workspace ---
export const getShiftClosingData = (shiftId: number): Promise<ShiftClosingData> =>
    fetchWithAuth(`${API_BASE_URL}/shifts/${shiftId}/closing-data`).then(handleResponse);

export const reopenShiftToEdit = (shiftId: number): Promise<Shift> =>
    fetchWithAuth(`${API_BASE_URL}/shifts/${shiftId}/reopen-to-edit`, { method: 'POST' }).then(handleResponse);

export const submitShiftForReview = (shiftId: number, data: ShiftClosingSubmit): Promise<Shift> =>
    fetchWithAuth(`${API_BASE_URL}/shifts/${shiftId}/submit-for-review`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data),
    }).then(handleResponse);

export const approveShift = (shiftId: number): Promise<Shift> =>
    fetchWithAuth(`${API_BASE_URL}/shifts/${shiftId}/approve`, {
        method: 'POST',
    }).then(handleResponse);

export const reopenShift = (shiftId: number): Promise<Shift> =>
    fetchWithAuth(`${API_BASE_URL}/shifts/${shiftId}/reopen`, {
        method: 'POST',
    }).then(handleResponse);

export const downloadShiftReportPdf = async (shiftId: number): Promise<void> => {
    const res = await fetchWithAuth(`${API_BASE_URL}/shift-reports/${shiftId}/download-pdf`);
    if (!res.ok) throw new Error('Failed to download PDF');
    const blob = await res.blob();
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `shift-report-${shiftId}.pdf`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    window.URL.revokeObjectURL(url);
};
