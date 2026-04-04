import { API_BASE_URL } from "@/lib/api/station";
import { fetchWithAuth } from "@/lib/api/fetch-with-auth";
import { Banknote, Wallet, Receipt } from "lucide-react";

// --- Types ---

export interface Employee {
    id: number;
    name: string;
    phone: string;
    designation: string;
}

export interface StatementRef {
    id: number;
    statementNo: string;
    customer: { id: number; name: string } | null;
    totalAmount: number;
    netAmount: number;
    balanceAmount: number;
    status: string;
}

export interface OperationalAdvance {
    id: number;
    advanceDate: string;
    amount: number;
    advanceType: string;
    recipientName: string;
    recipientPhone: string;
    purpose: string;
    remarks: string;
    status: string;
    returnedAmount: number;
    returnDate: string | null;
    returnRemarks: string | null;
    shiftId: number;
    utilizedAmount: number;
    employee: Employee | null;
    statement: StatementRef | null;
    invoiceBills: InvoiceBill[];
}

export interface InvoiceBill {
    id: number;
    billNo: string;
    billType: string;
    netAmount: number;
    date: string;
    customer: { id: number; name: string } | null;
    vehicle: { id: number; vehicleNumber: string } | null;
    paymentMode: string;
    billDesc: string;
    operationalAdvance: { id: number } | null;
}

// --- Constants ---

export const ADVANCE_TYPES = [
    { value: "CASH", label: "Cash Advance", icon: Banknote, color: "text-blue-500 bg-blue-500/10" },
    { value: "SALARY", label: "Salary Advance", icon: Wallet, color: "text-emerald-500 bg-emerald-500/10" },
    { value: "MANAGEMENT", label: "Management", icon: Receipt, color: "text-purple-500 bg-purple-500/10" },
];

export const STATUS_CONFIG: Record<string, { label: string; color: string }> = {
    GIVEN: { label: "Given", color: "bg-amber-500/10 text-amber-500" },
    RETURNED: { label: "Returned", color: "bg-green-500/10 text-green-500" },
    PARTIALLY_RETURNED: { label: "Partial", color: "bg-orange-500/10 text-orange-500" },
    SETTLED: { label: "Settled", color: "bg-teal-500/10 text-teal-500" },
    CANCELLED: { label: "Cancelled", color: "bg-gray-500/10 text-gray-500" },
};

// --- Utility functions ---

export function getAdvanceTypeMeta(type: string) {
    return ADVANCE_TYPES.find((t) => t.value === type) || ADVANCE_TYPES[0];
}

export function formatDateTime(dt?: string | null) {
    if (!dt) return "-";
    return new Date(dt).toLocaleString("en-IN", {
        day: "2-digit",
        month: "short",
        year: "numeric",
        hour: "2-digit",
        minute: "2-digit",
    });
}

export function formatCurrency(val?: number) {
    if (val == null) return "0.00";
    return val.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

// --- API helpers ---

export async function fetchAdvances(): Promise<OperationalAdvance[]> {
    const res = await fetchWithAuth(`${API_BASE_URL}/operational-advances`);
    if (!res.ok) throw new Error("Failed to fetch advances");
    return res.json();
}

export async function fetchAdvanceById(id: number): Promise<OperationalAdvance> {
    const res = await fetchWithAuth(`${API_BASE_URL}/operational-advances/${id}`);
    if (!res.ok) throw new Error("Failed to fetch advance");
    return res.json();
}

export async function fetchActiveShift(): Promise<{ id: number; startTime?: string } | null> {
    try {
        const res = await fetchWithAuth(`${API_BASE_URL}/shifts/active`);
        if (!res.ok) return null;
        const text = await res.text();
        if (!text) return null;
        return JSON.parse(text);
    } catch {
        return null;
    }
}

export async function fetchEmployees(): Promise<Employee[]> {
    try {
        const res = await fetchWithAuth(`${API_BASE_URL}/employees`);
        if (!res.ok) return [];
        return res.json();
    } catch {
        return [];
    }
}

export async function fetchShiftInvoices(shiftId: number): Promise<InvoiceBill[]> {
    try {
        const res = await fetchWithAuth(`${API_BASE_URL}/invoices/shift/${shiftId}`);
        if (!res.ok) return [];
        return res.json();
    } catch {
        return [];
    }
}

export async function createAdvance(data: {
    amount: number;
    advanceType: string;
    recipientName: string;
    recipientPhone: string;
    purpose: string;
    remarks: string;
    employee?: { id: number } | null;
}): Promise<OperationalAdvance> {
    const res = await fetchWithAuth(`${API_BASE_URL}/operational-advances`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(data),
    });
    if (!res.ok) {
        const err = await res.text();
        throw new Error(err || "Failed to create advance");
    }
    return res.json();
}

export async function returnAdvance(id: number, data: { returnedAmount: number; returnRemarks: string }): Promise<OperationalAdvance> {
    const res = await fetchWithAuth(`${API_BASE_URL}/operational-advances/${id}/return`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(data),
    });
    if (!res.ok) {
        const err = await res.text();
        throw new Error(err || "Failed to record return");
    }
    return res.json();
}

export async function cancelAdvance(id: number): Promise<void> {
    const res = await fetchWithAuth(`${API_BASE_URL}/operational-advances/${id}/cancel`, { method: "PATCH" });
    if (!res.ok) {
        const err = await res.text();
        throw new Error(err || "Failed to cancel advance");
    }
}

export async function deleteAdvance(id: number): Promise<void> {
    const res = await fetchWithAuth(`${API_BASE_URL}/operational-advances/${id}`, { method: "DELETE" });
    if (!res.ok) {
        const err = await res.text();
        throw new Error(err || "Failed to delete advance");
    }
}

export async function assignInvoice(advanceId: number, invoiceId: number): Promise<OperationalAdvance> {
    const res = await fetchWithAuth(`${API_BASE_URL}/operational-advances/${advanceId}/invoices/${invoiceId}`, { method: "POST" });
    if (!res.ok) {
        const err = await res.text();
        throw new Error(err || "Failed to assign invoice");
    }
    return res.json();
}

export async function unassignInvoice(advanceId: number, invoiceId: number): Promise<OperationalAdvance> {
    const res = await fetchWithAuth(`${API_BASE_URL}/operational-advances/${advanceId}/invoices/${invoiceId}`, { method: "DELETE" });
    if (!res.ok) {
        const err = await res.text();
        throw new Error(err || "Failed to unassign invoice");
    }
    return res.json();
}

export async function fetchAssignedInvoices(advanceId: number): Promise<InvoiceBill[]> {
    const res = await fetchWithAuth(`${API_BASE_URL}/operational-advances/${advanceId}/invoices`);
    if (!res.ok) return [];
    return res.json();
}

export async function assignStatement(advanceId: number, statementId: number): Promise<OperationalAdvance> {
    const res = await fetchWithAuth(`${API_BASE_URL}/operational-advances/${advanceId}/statement/${statementId}`, { method: "POST" });
    if (!res.ok) {
        const err = await res.text();
        throw new Error(err || "Failed to assign statement");
    }
    return res.json();
}

export async function unassignStatement(advanceId: number): Promise<OperationalAdvance> {
    const res = await fetchWithAuth(`${API_BASE_URL}/operational-advances/${advanceId}/statement`, { method: "DELETE" });
    if (!res.ok) {
        const err = await res.text();
        throw new Error(err || "Failed to unassign statement");
    }
    return res.json();
}

export async function fetchOutstandingStatements(): Promise<StatementRef[]> {
    const res = await fetchWithAuth(`${API_BASE_URL}/statements/outstanding`);
    if (!res.ok) return [];
    return res.json();
}

export async function fetchAdvancesByShift(shiftId: number): Promise<OperationalAdvance[]> {
    const res = await fetchWithAuth(`${API_BASE_URL}/operational-advances/shift/${shiftId}`);
    if (!res.ok) throw new Error("Failed to fetch advances");
    return res.json();
}

export async function fetchAdvancesByDateRange(fromDate: string, toDate: string): Promise<OperationalAdvance[]> {
    const res = await fetchWithAuth(`${API_BASE_URL}/operational-advances/search?fromDate=${fromDate}&toDate=${toDate}`);
    if (!res.ok) throw new Error("Failed to fetch advances");
    return res.json();
}
