"use client";

import { useEffect, useState, useCallback, useMemo } from "react";
import { GlassCard } from "@/components/ui/glass-card";
import { Modal } from "@/components/ui/modal";
import {
    Banknote,
    Wallet,
    ArrowDownLeft,
    ArrowUpRight,
    Search,
    Plus,
    Undo2,
    XCircle,
    Hash,
    AlertCircle,
    Eye,
    FileText,
    Link2,
    Unlink,
    Receipt,
    ChevronRight,
    Calendar,
} from "lucide-react";
import { API_BASE_URL } from "@/lib/api/station";
import { TablePagination, useClientPagination } from "@/components/ui/table-pagination";
import { useFormValidation, required, min, indianMobile } from "@/lib/validation";
import { FieldError, inputErrorClass, FormErrorBanner } from "@/components/ui/field-error";
import { PermissionGate } from "@/components/permission-gate";

// --- Types ---

interface Employee {
    id: number;
    name: string;
    phone: string;
    designation: string;
}

interface StatementRef {
    id: number;
    statementNo: string;
    customer: { id: number; name: string } | null;
    totalAmount: number;
    netAmount: number;
    balanceAmount: number;
    status: string;
}

interface OperationalAdvance {
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

interface InvoiceBill {
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

const ADVANCE_TYPES = [
    { value: "CASH_ADVANCE", label: "Cash Advance", icon: Banknote, color: "text-blue-500 bg-blue-500/10" },
    { value: "SALARY_ADVANCE", label: "Salary Advance", icon: Wallet, color: "text-emerald-500 bg-emerald-500/10" },
    { value: "MANAGEMENT", label: "Management", icon: Receipt, color: "text-purple-500 bg-purple-500/10" },
];

const STATUS_CONFIG: Record<string, { label: string; color: string }> = {
    GIVEN: { label: "Given", color: "bg-amber-500/10 text-amber-500" },
    RETURNED: { label: "Returned", color: "bg-green-500/10 text-green-500" },
    PARTIALLY_RETURNED: { label: "Partial", color: "bg-orange-500/10 text-orange-500" },
    SETTLED: { label: "Settled", color: "bg-teal-500/10 text-teal-500" },
    CANCELLED: { label: "Cancelled", color: "bg-gray-500/10 text-gray-500" },
};

function getAdvanceTypeMeta(type: string) {
    return ADVANCE_TYPES.find((t) => t.value === type) || ADVANCE_TYPES[0];
}

function formatDateTime(dt?: string | null) {
    if (!dt) return "-";
    return new Date(dt).toLocaleString("en-IN", {
        day: "2-digit",
        month: "short",
        year: "numeric",
        hour: "2-digit",
        minute: "2-digit",
    });
}

function formatCurrency(val?: number) {
    if (val == null) return "0.00";
    return val.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

// --- API helpers ---

async function fetchAdvances(): Promise<OperationalAdvance[]> {
    const res = await fetch(`${API_BASE_URL}/operational-advances`);
    if (!res.ok) throw new Error("Failed to fetch advances");
    return res.json();
}

async function fetchAdvanceById(id: number): Promise<OperationalAdvance> {
    const res = await fetch(`${API_BASE_URL}/operational-advances/${id}`);
    if (!res.ok) throw new Error("Failed to fetch advance");
    return res.json();
}

async function fetchActiveShift(): Promise<{ id: number } | null> {
    try {
        const res = await fetch(`${API_BASE_URL}/shifts/active`);
        if (!res.ok) return null;
        const text = await res.text();
        if (!text) return null;
        return JSON.parse(text);
    } catch {
        return null;
    }
}

async function fetchEmployees(): Promise<Employee[]> {
    try {
        const res = await fetch(`${API_BASE_URL}/employees`);
        if (!res.ok) return [];
        return res.json();
    } catch {
        return [];
    }
}

async function fetchShiftInvoices(shiftId: number): Promise<InvoiceBill[]> {
    try {
        const res = await fetch(`${API_BASE_URL}/invoices/shift/${shiftId}`);
        if (!res.ok) return [];
        return res.json();
    } catch {
        return [];
    }
}

async function createAdvance(data: {
    amount: number;
    advanceType: string;
    recipientName: string;
    recipientPhone: string;
    purpose: string;
    remarks: string;
    employee?: { id: number } | null;
}): Promise<OperationalAdvance> {
    const res = await fetch(`${API_BASE_URL}/operational-advances`, {
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

async function returnAdvance(id: number, data: { returnedAmount: number; returnRemarks: string }): Promise<OperationalAdvance> {
    const res = await fetch(`${API_BASE_URL}/operational-advances/${id}/return`, {
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

async function cancelAdvance(id: number): Promise<void> {
    const res = await fetch(`${API_BASE_URL}/operational-advances/${id}/cancel`, { method: "PATCH" });
    if (!res.ok) {
        const err = await res.text();
        throw new Error(err || "Failed to cancel advance");
    }
}

async function deleteAdvance(id: number): Promise<void> {
    const res = await fetch(`${API_BASE_URL}/operational-advances/${id}`, { method: "DELETE" });
    if (!res.ok) {
        const err = await res.text();
        throw new Error(err || "Failed to delete advance");
    }
}

async function assignInvoice(advanceId: number, invoiceId: number): Promise<OperationalAdvance> {
    const res = await fetch(`${API_BASE_URL}/operational-advances/${advanceId}/invoices/${invoiceId}`, { method: "POST" });
    if (!res.ok) {
        const err = await res.text();
        throw new Error(err || "Failed to assign invoice");
    }
    return res.json();
}

async function unassignInvoice(advanceId: number, invoiceId: number): Promise<OperationalAdvance> {
    const res = await fetch(`${API_BASE_URL}/operational-advances/${advanceId}/invoices/${invoiceId}`, { method: "DELETE" });
    if (!res.ok) {
        const err = await res.text();
        throw new Error(err || "Failed to unassign invoice");
    }
    return res.json();
}

async function fetchAssignedInvoices(advanceId: number): Promise<InvoiceBill[]> {
    const res = await fetch(`${API_BASE_URL}/operational-advances/${advanceId}/invoices`);
    if (!res.ok) return [];
    return res.json();
}

async function assignStatement(advanceId: number, statementId: number): Promise<OperationalAdvance> {
    const res = await fetch(`${API_BASE_URL}/operational-advances/${advanceId}/statement/${statementId}`, { method: "POST" });
    if (!res.ok) {
        const err = await res.text();
        throw new Error(err || "Failed to assign statement");
    }
    return res.json();
}

async function unassignStatement(advanceId: number): Promise<OperationalAdvance> {
    const res = await fetch(`${API_BASE_URL}/operational-advances/${advanceId}/statement`, { method: "DELETE" });
    if (!res.ok) {
        const err = await res.text();
        throw new Error(err || "Failed to unassign statement");
    }
    return res.json();
}

async function fetchOutstandingStatements(): Promise<StatementRef[]> {
    const res = await fetch(`${API_BASE_URL}/statements/outstanding`);
    if (!res.ok) return [];
    return res.json();
}

async function fetchAdvancesByShift(shiftId: number): Promise<OperationalAdvance[]> {
    const res = await fetch(`${API_BASE_URL}/operational-advances/shift/${shiftId}`);
    if (!res.ok) throw new Error("Failed to fetch advances");
    return res.json();
}

async function fetchAdvancesByDateRange(fromDate: string, toDate: string): Promise<OperationalAdvance[]> {
    const res = await fetch(`${API_BASE_URL}/operational-advances/search?fromDate=${fromDate}&toDate=${toDate}`);
    if (!res.ok) throw new Error("Failed to fetch advances");
    return res.json();
}

// --- Page Component ---

export default function OperationalAdvancesPage() {
    const [advances, setAdvances] = useState<OperationalAdvance[]>([]);
    const [activeShift, setActiveShift] = useState<{ id: number } | null>(null);
    const [employees, setEmployees] = useState<Employee[]>([]);
    const [isLoading, setIsLoading] = useState(true);

    // Filters
    const [searchQuery, setSearchQuery] = useState("");
    const [statusFilter, setStatusFilter] = useState("ALL");
    const [typeFilter, setTypeFilter] = useState("ALL");
    const [fromDate, setFromDate] = useState("");
    const [toDate, setToDate] = useState("");
    const [viewMode, setViewMode] = useState<"shift" | "dates">("shift");

    // Record Advance Modal
    const [isAddModalOpen, setIsAddModalOpen] = useState(false);
    const [addType, setAddType] = useState("CASH_ADVANCE");
    const [addAmount, setAddAmount] = useState("");
    const [addRecipientName, setAddRecipientName] = useState("");
    const [addRecipientPhone, setAddRecipientPhone] = useState("");
    const [addPurpose, setAddPurpose] = useState("");
    const [addRemarks, setAddRemarks] = useState("");
    const [addEmployeeId, setAddEmployeeId] = useState<number | null>(null);
    const [isSubmitting, setIsSubmitting] = useState(false);

    // Return Modal
    const [isReturnModalOpen, setIsReturnModalOpen] = useState(false);
    const [returnTarget, setReturnTarget] = useState<OperationalAdvance | null>(null);
    const [returnAmount, setReturnAmount] = useState("");
    const [returnRemarks, setReturnRemarks] = useState("");

    // Validation
    const { errors: advErrors, validate: validateAdv, clearError: clearAdvError, clearAllErrors: clearAllAdvErrors } = useFormValidation({
        addAmount: [required("Amount is required"), min(0.01, "Amount must be greater than 0")],
        addRecipientName: [required("Recipient name is required")],
        addRecipientPhone: [indianMobile()],
    });
    const { errors: retErrors, validate: validateRet, clearError: clearRetError, clearAllErrors: clearAllRetErrors } = useFormValidation({
        returnAmount: [required("Return amount is required"), min(0.01, "Amount must be greater than 0")],
    });
    const [advApiError, setAdvApiError] = useState("");

    // Detail / Invoice & Statement Assignment Modal
    const [isDetailModalOpen, setIsDetailModalOpen] = useState(false);
    const [detailAdvance, setDetailAdvance] = useState<OperationalAdvance | null>(null);
    const [assignedInvoices, setAssignedInvoices] = useState<InvoiceBill[]>([]);
    const [availableInvoices, setAvailableInvoices] = useState<InvoiceBill[]>([]);
    const [showAssignPanel, setShowAssignPanel] = useState(false);
    const [invoiceSearch, setInvoiceSearch] = useState("");
    const [availableStatements, setAvailableStatements] = useState<StatementRef[]>([]);
    const [showStatementPanel, setShowStatementPanel] = useState(false);

    const loadAdvances = useCallback(async (mode: "shift" | "dates", shiftId?: number | null, from?: string, to?: string) => {
        setIsLoading(true);
        try {
            let data: OperationalAdvance[];
            if (mode === "dates" && from && to) {
                data = await fetchAdvancesByDateRange(from, to);
            } else if (mode === "shift" && shiftId) {
                data = await fetchAdvancesByShift(shiftId);
            } else {
                data = [];
            }
            setAdvances(data);
        } catch (err) {
            console.error("Failed to load advances", err);
        } finally {
            setIsLoading(false);
        }
    }, []);

    const loadData = useCallback(async () => {
        setIsLoading(true);
        try {
            const [shift, emps] = await Promise.all([
                fetchActiveShift(),
                fetchEmployees(),
            ]);
            setActiveShift(shift);
            setEmployees(emps);
            if (shift?.id) {
                const advs = await fetchAdvancesByShift(shift.id);
                setAdvances(advs);
            }
        } catch (err) {
            console.error("Failed to load data", err);
        } finally {
            setIsLoading(false);
        }
    }, []);

    useEffect(() => {
        loadData();
    }, [loadData]);

    const handleDateSearch = () => {
        if (fromDate && toDate) {
            setViewMode("dates");
            loadAdvances("dates", null, fromDate, toDate);
        }
    };

    const handleShowCurrentShift = () => {
        setViewMode("shift");
        setFromDate("");
        setToDate("");
        if (activeShift?.id) {
            loadAdvances("shift", activeShift.id);
        } else {
            setAdvances([]);
        }
    };

    const reloadCurrentView = async () => {
        if (viewMode === "dates" && fromDate && toDate) {
            loadAdvances("dates", null, fromDate, toDate);
        } else if (activeShift?.id) {
            loadAdvances("shift", activeShift.id);
        }
    };

    // --- Summary calculations ---
    const summary = useMemo(() => {
        const totalGiven = advances
            .filter((a) => a.status !== "CANCELLED")
            .reduce((sum, a) => sum + a.amount, 0);

        const outstanding = advances
            .filter((a) => a.status === "GIVEN" || a.status === "PARTIALLY_RETURNED")
            .reduce((sum, a) => sum + (a.amount - (a.returnedAmount || 0) - (a.utilizedAmount || 0)), 0);

        const returned = advances.reduce((sum, a) => sum + (a.returnedAmount || 0), 0);
        const utilized = advances.reduce((sum, a) => sum + (a.utilizedAmount || 0), 0);

        return { totalGiven, outstanding, returned, utilized, count: advances.length };
    }, [advances]);

    // --- Filtering ---
    const filtered = useMemo(() => {
        return advances.filter((a) => {
            const matchStatus = statusFilter === "ALL" || a.status === statusFilter;
            const matchType = typeFilter === "ALL" || a.advanceType === typeFilter;
            const q = searchQuery.toLowerCase();
            const matchSearch =
                !searchQuery ||
                a.recipientName?.toLowerCase().includes(q) ||
                a.purpose?.toLowerCase().includes(q) ||
                a.remarks?.toLowerCase().includes(q) ||
                a.employee?.name?.toLowerCase().includes(q);
            return matchStatus && matchType && matchSearch;
        });
    }, [advances, statusFilter, typeFilter, searchQuery]);

    const { page, setPage, totalPages, totalElements, pageSize, paginatedData: pagedAdvances } = useClientPagination(filtered);

    // --- Handlers ---
    const resetAddForm = () => {
        setAddType("CASH_ADVANCE");
        setAddAmount("");
        setAddRecipientName("");
        setAddRecipientPhone("");
        setAddPurpose("");
        setAddRemarks("");
        setAddEmployeeId(null);
    };

    const handleOpenAddModal = () => {
        if (!activeShift) return;
        resetAddForm();
        clearAllAdvErrors();
        setAdvApiError("");
        setIsAddModalOpen(true);
    };

    const handleEmployeeSelect = (empId: string) => {
        if (!empId) {
            setAddEmployeeId(null);
            return;
        }
        const emp = employees.find((e) => e.id === Number(empId));
        if (emp) {
            setAddEmployeeId(emp.id);
            setAddRecipientName(emp.name);
            setAddRecipientPhone(emp.phone || "");
        }
    };

    const handleCreateAdvance = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!validateAdv({ addAmount, addRecipientName, addRecipientPhone })) return;
        setIsSubmitting(true);
        setAdvApiError("");
        try {
            await createAdvance({
                amount: Number(addAmount),
                advanceType: addType,
                recipientName: addRecipientName,
                recipientPhone: addRecipientPhone,
                purpose: addPurpose,
                remarks: addRemarks,
                employee: addEmployeeId ? { id: addEmployeeId } : null,
            });
            setIsAddModalOpen(false);
            await reloadCurrentView();
        } catch (err: any) {
            setAdvApiError(err.message || "Failed to create advance");
        } finally {
            setIsSubmitting(false);
        }
    };

    const handleOpenReturnModal = (adv: OperationalAdvance) => {
        setReturnTarget(adv);
        setReturnAmount("");
        setReturnRemarks("");
        clearAllRetErrors();
        setAdvApiError("");
        setIsReturnModalOpen(true);
    };

    const handleReturn = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!returnTarget) return;
        if (!validateRet({ returnAmount })) return;
        setIsSubmitting(true);
        setAdvApiError("");
        try {
            await returnAdvance(returnTarget.id, {
                returnedAmount: Number(returnAmount),
                returnRemarks: returnRemarks,
            });
            setIsReturnModalOpen(false);
            setReturnTarget(null);
            await reloadCurrentView();
        } catch (err: any) {
            setAdvApiError(err.message || "Failed to record return");
        } finally {
            setIsSubmitting(false);
        }
    };

    const handleCancel = async (adv: OperationalAdvance) => {
        if (!confirm(`Cancel advance of Rs.${formatCurrency(adv.amount)} to ${adv.recipientName}?`)) return;
        try {
            await cancelAdvance(adv.id);
            await reloadCurrentView();
        } catch (err: any) {
            alert(err.message || "Failed to cancel advance");
        }
    };

    const handleDelete = async (adv: OperationalAdvance) => {
        if (!confirm(`Delete this advance record permanently?`)) return;
        try {
            await deleteAdvance(adv.id);
            await reloadCurrentView();
        } catch (err: any) {
            alert(err.message || "Failed to delete advance");
        }
    };

    // --- Detail / Invoice Assignment ---
    const handleOpenDetail = async (adv: OperationalAdvance) => {
        setDetailAdvance(adv);
        setShowAssignPanel(false);
        setInvoiceSearch("");
        setIsDetailModalOpen(true);
        try {
            const invoices = await fetchAssignedInvoices(adv.id);
            setAssignedInvoices(invoices);
        } catch {
            setAssignedInvoices([]);
        }
    };

    const handleOpenAssignPanel = async () => {
        setShowAssignPanel(true);
        if (detailAdvance?.shiftId) {
            try {
                const all = await fetchShiftInvoices(detailAdvance.shiftId);
                // Filter out already assigned invoices
                const assignedIds = new Set(assignedInvoices.map((inv) => inv.id));
                setAvailableInvoices(all.filter((inv) => !assignedIds.has(inv.id) && !inv.operationalAdvance));
            } catch {
                setAvailableInvoices([]);
            }
        }
    };

    const handleAssignInvoice = async (invoiceId: number) => {
        if (!detailAdvance) return;
        try {
            const updated = await assignInvoice(detailAdvance.id, invoiceId);
            setDetailAdvance(updated);
            const invoices = await fetchAssignedInvoices(detailAdvance.id);
            setAssignedInvoices(invoices);
            setAvailableInvoices((prev) => prev.filter((inv) => inv.id !== invoiceId));
            await reloadCurrentView();
        } catch (err: any) {
            alert(err.message || "Failed to assign invoice");
        }
    };

    const handleUnassignInvoice = async (invoiceId: number) => {
        if (!detailAdvance) return;
        try {
            const updated = await unassignInvoice(detailAdvance.id, invoiceId);
            setDetailAdvance(updated);
            const invoices = await fetchAssignedInvoices(detailAdvance.id);
            setAssignedInvoices(invoices);
            await reloadCurrentView();
        } catch (err: any) {
            alert(err.message || "Failed to unassign invoice");
        }
    };

    const handleOpenStatementPanel = async () => {
        setShowStatementPanel(true);
        try {
            const stmts = await fetchOutstandingStatements();
            setAvailableStatements(stmts);
        } catch {
            setAvailableStatements([]);
        }
    };

    const handleAssignStatement = async (statementId: number) => {
        if (!detailAdvance) return;
        try {
            const updated = await assignStatement(detailAdvance.id, statementId);
            setDetailAdvance(updated);
            setShowStatementPanel(false);
            await reloadCurrentView();
        } catch (err: any) {
            alert(err.message || "Failed to assign statement");
        }
    };

    const handleUnassignStatement = async () => {
        if (!detailAdvance) return;
        try {
            const updated = await unassignStatement(detailAdvance.id);
            setDetailAdvance(updated);
            await reloadCurrentView();
        } catch (err: any) {
            alert(err.message || "Failed to unassign statement");
        }
    };

    const filteredAvailableInvoices = useMemo(() => {
        if (!invoiceSearch) return availableInvoices;
        const q = invoiceSearch.toLowerCase();
        return availableInvoices.filter(
            (inv) =>
                inv.billNo?.toLowerCase().includes(q) ||
                inv.customer?.name?.toLowerCase().includes(q) ||
                inv.vehicle?.vehicleNumber?.toLowerCase().includes(q)
        );
    }, [availableInvoices, invoiceSearch]);

    const returnOutstanding = returnTarget
        ? returnTarget.amount - (returnTarget.returnedAmount || 0) - (returnTarget.utilizedAmount || 0)
        : 0;

    return (
        <div className="p-6 h-screen overflow-hidden bg-background transition-colors duration-300">
            <div className="max-w-7xl mx-auto">
                {/* Header */}
                <div className="flex justify-between items-center mb-8">
                    <div>
                        <h1 className="text-4xl font-bold text-foreground tracking-tight">
                            Operational <span className="text-gradient">Advances</span>
                        </h1>
                        <p className="text-muted-foreground mt-2">
                            Track operational advances (cash, salary, management) and their settlements.
                        </p>
                    </div>
                    <div className="flex items-center gap-3">
                        {!activeShift && (
                            <div className="flex items-center gap-2 px-4 py-2 bg-amber-500/10 border border-amber-500/20 rounded-xl">
                                <AlertCircle className="w-4 h-4 text-amber-500" />
                                <span className="text-sm font-medium text-amber-500">No active shift</span>
                            </div>
                        )}
                        <PermissionGate permission="FINANCE_MANAGE">
                            <button
                                onClick={handleOpenAddModal}
                                title="Record a new advance"
                                className="btn-gradient px-5 py-2.5 rounded-xl font-medium flex items-center gap-2 shadow-lg hover:shadow-xl transition-all"
                            >
                                <Plus className="w-4 h-4" />
                                Record Advance
                            </button>
                        </PermissionGate>
                    </div>
                </div>

                {isLoading ? (
                    <div className="flex flex-col items-center justify-center py-20 text-muted-foreground">
                        <div className="w-12 h-12 border-4 border-primary/20 border-t-primary rounded-full animate-spin mb-4" />
                        <p className="animate-pulse">Loading advances...</p>
                    </div>
                ) : (
                    <>
                        {/* Summary Cards */}
                        <div className="grid grid-cols-2 sm:grid-cols-5 gap-3 mb-6">
                            <div className="flex items-center gap-3 p-3 rounded-xl border border-border bg-card">
                                <div className="p-2 rounded-lg text-blue-500 bg-blue-500/10">
                                    <ArrowUpRight className="w-4 h-4" />
                                </div>
                                <div>
                                    <p className="text-[10px] uppercase tracking-wider text-muted-foreground">Total Given</p>
                                    <p className="text-sm font-bold text-foreground">{formatCurrency(summary.totalGiven)}</p>
                                </div>
                            </div>
                            <div className="flex items-center gap-3 p-3 rounded-xl border border-border bg-card">
                                <div className="p-2 rounded-lg text-red-500 bg-red-500/10">
                                    <AlertCircle className="w-4 h-4" />
                                </div>
                                <div>
                                    <p className="text-[10px] uppercase tracking-wider text-muted-foreground">Outstanding</p>
                                    <p className="text-sm font-bold text-red-500">{formatCurrency(summary.outstanding)}</p>
                                </div>
                            </div>
                            <div className="flex items-center gap-3 p-3 rounded-xl border border-border bg-card">
                                <div className="p-2 rounded-lg text-teal-500 bg-teal-500/10">
                                    <Receipt className="w-4 h-4" />
                                </div>
                                <div>
                                    <p className="text-[10px] uppercase tracking-wider text-muted-foreground">Bill Settled</p>
                                    <p className="text-sm font-bold text-teal-500">{formatCurrency(summary.utilized)}</p>
                                </div>
                            </div>
                            <div className="flex items-center gap-3 p-3 rounded-xl border border-border bg-card">
                                <div className="p-2 rounded-lg text-green-500 bg-green-500/10">
                                    <ArrowDownLeft className="w-4 h-4" />
                                </div>
                                <div>
                                    <p className="text-[10px] uppercase tracking-wider text-muted-foreground">Cash Returned</p>
                                    <p className="text-sm font-bold text-green-500">{formatCurrency(summary.returned)}</p>
                                </div>
                            </div>
                            <div className="flex items-center gap-3 p-3 rounded-xl border border-border bg-card">
                                <div className="p-2 rounded-lg text-purple-500 bg-purple-500/10">
                                    <Hash className="w-4 h-4" />
                                </div>
                                <div>
                                    <p className="text-[10px] uppercase tracking-wider text-muted-foreground">Count</p>
                                    <p className="text-sm font-bold text-foreground">{summary.count}</p>
                                </div>
                            </div>
                        </div>

                        {/* Filter Bar */}
                        <div className="mb-4 flex flex-wrap gap-3 items-center">
                            <div className="relative flex-1 min-w-[200px] max-w-md">
                                <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                                <input
                                    type="text"
                                    placeholder="Search recipient, purpose, employee..."
                                    value={searchQuery}
                                    onChange={(e) => setSearchQuery(e.target.value)}
                                    className="w-full pl-10 pr-4 py-2.5 bg-card border border-border rounded-xl text-foreground text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                                />
                            </div>
                            <select
                                value={statusFilter}
                                onChange={(e) => setStatusFilter(e.target.value)}
                                className="px-4 py-2.5 bg-card border border-border rounded-xl text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
                            >
                                <option value="ALL">All Status</option>
                                <option value="GIVEN">Given</option>
                                <option value="RETURNED">Returned</option>
                                <option value="PARTIALLY_RETURNED">Partially Returned</option>
                                <option value="SETTLED">Settled</option>
                                <option value="CANCELLED">Cancelled</option>
                            </select>
                            <select
                                value={typeFilter}
                                onChange={(e) => setTypeFilter(e.target.value)}
                                className="px-4 py-2.5 bg-card border border-border rounded-xl text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
                            >
                                <option value="ALL">All Types</option>
                                <option value="CASH_ADVANCE">Cash Advance</option>
                                <option value="SALARY_ADVANCE">Salary Advance</option>
                            </select>
                            <div className="flex items-center gap-2">
                                <div className="flex items-center gap-1">
                                    <Calendar className="w-4 h-4 text-muted-foreground" />
                                    <input
                                        type="date"
                                        value={fromDate}
                                        onChange={(e) => setFromDate(e.target.value)}
                                        className="px-3 py-2.5 bg-card border border-border rounded-xl text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
                                    />
                                </div>
                                <span className="text-muted-foreground text-sm">to</span>
                                <input
                                    type="date"
                                    value={toDate}
                                    onChange={(e) => setToDate(e.target.value)}
                                    className="px-3 py-2.5 bg-card border border-border rounded-xl text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
                                />
                                <button
                                    onClick={handleDateSearch}
                                    disabled={!fromDate || !toDate}
                                    className="px-4 py-2.5 bg-primary text-primary-foreground rounded-xl text-sm font-medium hover:bg-primary/90 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                                >
                                    Search
                                </button>
                            </div>
                            {viewMode === "dates" && (
                                <button
                                    onClick={handleShowCurrentShift}
                                    className="px-4 py-2.5 bg-card border border-border rounded-xl text-sm font-medium text-foreground hover:bg-primary/10 transition-colors"
                                >
                                    Current Shift
                                </button>
                            )}
                        </div>

                        {/* View indicator */}
                        <div className="mb-3">
                            <span className="text-xs text-muted-foreground">
                                {viewMode === "shift"
                                    ? activeShift
                                        ? `Showing current shift #${activeShift.id} entries`
                                        : "No active shift"
                                    : `Showing entries from ${fromDate} to ${toDate}`
                                }
                            </span>
                        </div>

                        {/* Advances Table */}
                        <GlassCard className="overflow-hidden border-none p-0 mb-6">
                            <div className="overflow-x-auto">
                                <table className="w-full text-left border-collapse">
                                    <thead>
                                        <tr className="bg-white/5 border-b border-border/50">
                                            <th className="px-4 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Date</th>
                                            <th className="px-4 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Type</th>
                                            <th className="px-4 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Recipient</th>
                                            <th className="px-4 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right">Amount</th>
                                            <th className="px-4 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right">Bills</th>
                                            <th className="px-4 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right">Returned</th>
                                            <th className="px-4 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right">Outstanding</th>
                                            <th className="px-4 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center">Status</th>
                                            <th className="px-4 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center">Actions</th>
                                        </tr>
                                    </thead>
                                    <tbody className="divide-y divide-border/30">
                                        {filtered.length === 0 ? (
                                            <tr>
                                                <td colSpan={9} className="px-6 py-12 text-center text-muted-foreground">
                                                    No advances found
                                                </td>
                                            </tr>
                                        ) : (
                                            pagedAdvances.map((adv) => {
                                                const meta = getAdvanceTypeMeta(adv.advanceType);
                                                const Icon = meta.icon;
                                                const utilized = adv.utilizedAmount || 0;
                                                const returned = adv.returnedAmount || 0;
                                                const outstanding = adv.amount - returned - utilized;
                                                const statusCfg = STATUS_CONFIG[adv.status] || STATUS_CONFIG.GIVEN;
                                                const canReturn = adv.status === "GIVEN" || adv.status === "PARTIALLY_RETURNED";
                                                const canCancel = adv.status === "GIVEN";

                                                return (
                                                    <tr key={adv.id} className="hover:bg-white/5 transition-colors group">
                                                        <td className="px-4 py-3 text-xs text-muted-foreground whitespace-nowrap">
                                                            {formatDateTime(adv.advanceDate)}
                                                        </td>
                                                        <td className="px-4 py-3">
                                                            <div className="flex items-center gap-2">
                                                                <div className={`p-1.5 rounded-lg ${meta.color}`}>
                                                                    <Icon className="w-3.5 h-3.5" />
                                                                </div>
                                                                <span className="text-sm font-medium text-foreground">
                                                                    {meta.label}
                                                                </span>
                                                            </div>
                                                        </td>
                                                        <td className="px-4 py-3">
                                                            <div>
                                                                <p className="text-sm font-medium text-foreground">{adv.recipientName}</p>
                                                                {adv.employee && (
                                                                    <p className="text-[10px] text-primary">{adv.employee.designation || "Employee"}</p>
                                                                )}
                                                                {adv.recipientPhone && !adv.employee && (
                                                                    <p className="text-[10px] text-muted-foreground">{adv.recipientPhone}</p>
                                                                )}
                                                            </div>
                                                        </td>
                                                        <td className="px-4 py-3 text-right">
                                                            <span className="text-sm font-bold text-foreground">
                                                                {formatCurrency(adv.amount)}
                                                            </span>
                                                        </td>
                                                        <td className="px-4 py-3 text-right">
                                                            <span className={`text-sm ${utilized > 0 ? "text-teal-500 font-medium" : "text-muted-foreground"}`}>
                                                                {utilized > 0 ? formatCurrency(utilized) : "-"}
                                                            </span>
                                                        </td>
                                                        <td className="px-4 py-3 text-right">
                                                            <span className="text-sm text-foreground">
                                                                {returned > 0 ? formatCurrency(returned) : "-"}
                                                            </span>
                                                        </td>
                                                        <td className="px-4 py-3 text-right">
                                                            <span className={`text-sm font-bold ${outstanding > 0 ? "text-red-500" : "text-foreground"}`}>
                                                                {adv.status === "CANCELLED" ? "-" : formatCurrency(Math.max(0, outstanding))}
                                                            </span>
                                                        </td>
                                                        <td className="px-4 py-3 text-center">
                                                            <span className={`px-2.5 py-1 rounded-full text-[10px] font-bold uppercase ${statusCfg.color}`}>
                                                                {statusCfg.label}
                                                            </span>
                                                        </td>
                                                        <td className="px-4 py-3 text-center">
                                                            <div className="flex items-center justify-center gap-1">
                                                                <button
                                                                    onClick={() => handleOpenDetail(adv)}
                                                                    title="View details & assign invoices"
                                                                    className="p-1.5 rounded-lg hover:bg-primary/10 text-muted-foreground hover:text-primary transition-all"
                                                                >
                                                                    <Eye className="w-3.5 h-3.5" />
                                                                </button>
                                                                {canReturn && (
                                                                    <PermissionGate permission="FINANCE_MANAGE">
                                                                        <button
                                                                            onClick={() => handleOpenReturnModal(adv)}
                                                                            title="Record return"
                                                                            className="p-1.5 rounded-lg hover:bg-green-500/10 text-muted-foreground hover:text-green-500 opacity-100 md:opacity-0 group-hover:opacity-100 transition-all"
                                                                        >
                                                                            <Undo2 className="w-3.5 h-3.5" />
                                                                        </button>
                                                                    </PermissionGate>
                                                                )}
                                                                {canCancel && (
                                                                    <PermissionGate permission="FINANCE_MANAGE">
                                                                        <button
                                                                            onClick={() => handleCancel(adv)}
                                                                            title="Cancel advance"
                                                                            className="p-1.5 rounded-lg hover:bg-red-500/10 text-muted-foreground hover:text-red-500 opacity-100 md:opacity-0 group-hover:opacity-100 transition-all"
                                                                        >
                                                                            <XCircle className="w-3.5 h-3.5" />
                                                                        </button>
                                                                    </PermissionGate>
                                                                )}
                                                            </div>
                                                        </td>
                                                    </tr>
                                                );
                                            })
                                        )}
                                    </tbody>
                                </table>
                            </div>
                            <TablePagination
                                page={page}
                                totalPages={totalPages}
                                totalElements={totalElements}
                                pageSize={pageSize}
                                onPageChange={setPage}
                            />
                        </GlassCard>
                    </>
                )}
            </div>

            {/* Record Advance Modal */}
            <Modal
                isOpen={isAddModalOpen}
                onClose={() => setIsAddModalOpen(false)}
                title="Record Advance"
            >
                <form onSubmit={handleCreateAdvance} className="space-y-4">
                    <FormErrorBanner message={advApiError} onDismiss={() => setAdvApiError("")} />
                    {/* Advance Type Selector */}
                    <div>
                        <label className="block text-sm font-medium text-foreground mb-2">Advance Type</label>
                        <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
                            {ADVANCE_TYPES.map((t) => {
                                const Icon = t.icon;
                                return (
                                    <button
                                        key={t.value}
                                        type="button"
                                        onClick={() => setAddType(t.value)}
                                        className={`flex flex-col items-center gap-1.5 p-3 rounded-xl border transition-all text-xs font-medium ${
                                            addType === t.value
                                                ? "border-primary bg-primary/10 text-primary"
                                                : "border-border bg-card text-muted-foreground hover:border-primary/30"
                                        }`}
                                    >
                                        <Icon className="w-4 h-4" />
                                        {t.label}
                                    </button>
                                );
                            })}
                        </div>
                    </div>

                    {/* Employee Selector (for salary advance) */}
                    {addType === "SALARY_ADVANCE" && employees.length > 0 && (
                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5">Select Employee</label>
                            <select
                                value={addEmployeeId || ""}
                                onChange={(e) => handleEmployeeSelect(e.target.value)}
                                className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
                            >
                                <option value="">-- Select employee (optional) --</option>
                                {employees.map((emp) => (
                                    <option key={emp.id} value={emp.id}>
                                        {emp.name} {emp.designation ? `(${emp.designation})` : ""}
                                    </option>
                                ))}
                            </select>
                        </div>
                    )}

                    {/* Amount */}
                    <div>
                        <label className="block text-sm font-medium text-foreground mb-1.5">
                            Amount <span className="text-red-500">*</span>
                        </label>
                        <input
                            type="number"
                            step="0.01"
                            min="0.01"
                            value={addAmount}
                            onChange={(e) => { setAddAmount(e.target.value); clearAdvError("addAmount"); }}
                            className={`w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground font-mono focus:outline-none focus:ring-2 focus:ring-primary/50 ${inputErrorClass(advErrors.addAmount)}`}
                            placeholder="0.00"
                        />
                        <FieldError error={advErrors.addAmount} />
                    </div>

                    {/* Recipient Name */}
                    <div>
                        <label className="block text-sm font-medium text-foreground mb-1.5">
                            Recipient Name <span className="text-red-500">*</span>
                        </label>
                        <input
                            type="text"
                            value={addRecipientName}
                            onChange={(e) => { setAddRecipientName(e.target.value); clearAdvError("addRecipientName"); }}
                            className={`w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50 ${inputErrorClass(advErrors.addRecipientName)}`}
                            placeholder="Who is taking the advance?"
                        />
                        <FieldError error={advErrors.addRecipientName} />
                    </div>

                    {/* Recipient Phone */}
                    <div>
                        <label className="block text-sm font-medium text-foreground mb-1.5">Recipient Phone</label>
                        <input
                            type="text"
                            value={addRecipientPhone}
                            onChange={(e) => { setAddRecipientPhone(e.target.value); clearAdvError("addRecipientPhone"); }}
                            className={`w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50 ${inputErrorClass(advErrors.addRecipientPhone)}`}
                            placeholder="Phone number (optional)"
                        />
                        <FieldError error={advErrors.addRecipientPhone} />
                    </div>

                    {/* Purpose */}
                    <div>
                        <label className="block text-sm font-medium text-foreground mb-1.5">Purpose</label>
                        <input
                            type="text"
                            value={addPurpose}
                            onChange={(e) => setAddPurpose(e.target.value)}
                            className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                            placeholder="Purpose of the advance"
                        />
                    </div>

                    {/* Remarks */}
                    <div>
                        <label className="block text-sm font-medium text-foreground mb-1.5">Remarks</label>
                        <input
                            type="text"
                            value={addRemarks}
                            onChange={(e) => setAddRemarks(e.target.value)}
                            className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                            placeholder="Optional notes..."
                        />
                    </div>

                    <div className="flex justify-end gap-3 pt-6 border-t border-border mt-6">
                        <button
                            type="button"
                            onClick={() => setIsAddModalOpen(false)}
                            className="px-6 py-2.5 rounded-xl font-medium text-foreground bg-black/5 dark:bg-white/5 hover:bg-black/10 dark:hover:bg-white/10 transition-colors"
                        >
                            Cancel
                        </button>
                        <button
                            type="submit"
                            disabled={isSubmitting}
                            className="btn-gradient px-8 py-2.5 rounded-xl font-medium shadow-lg hover:shadow-xl transition-all disabled:opacity-50"
                        >
                            {isSubmitting ? "Saving..." : "Record Advance"}
                        </button>
                    </div>
                </form>
            </Modal>

            {/* Record Return Modal */}
            <Modal
                isOpen={isReturnModalOpen}
                onClose={() => setIsReturnModalOpen(false)}
                title="Record Return"
            >
                {returnTarget && (
                    <form onSubmit={handleReturn} className="space-y-4">
                        {/* Advance Details */}
                        <div className="p-4 bg-white/5 rounded-xl border border-border space-y-2">
                            <div className="flex items-center gap-2 mb-3">
                                {(() => {
                                    const meta = getAdvanceTypeMeta(returnTarget.advanceType);
                                    const Icon = meta.icon;
                                    return (
                                        <div className={`p-1.5 rounded-lg ${meta.color}`}>
                                            <Icon className="w-3.5 h-3.5" />
                                        </div>
                                    );
                                })()}
                                <span className="text-sm font-medium text-foreground">
                                    {getAdvanceTypeMeta(returnTarget.advanceType).label}
                                </span>
                            </div>
                            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-sm">
                                <div>
                                    <p className="text-[10px] uppercase tracking-wider text-muted-foreground">Amount</p>
                                    <p className="font-bold text-foreground">{formatCurrency(returnTarget.amount)}</p>
                                </div>
                                <div>
                                    <p className="text-[10px] uppercase tracking-wider text-muted-foreground">Bills</p>
                                    <p className="font-bold text-teal-500">{formatCurrency(returnTarget.utilizedAmount || 0)}</p>
                                </div>
                                <div>
                                    <p className="text-[10px] uppercase tracking-wider text-muted-foreground">Returned</p>
                                    <p className="font-bold text-green-500">{formatCurrency(returnTarget.returnedAmount)}</p>
                                </div>
                                <div>
                                    <p className="text-[10px] uppercase tracking-wider text-muted-foreground">Outstanding</p>
                                    <p className="font-bold text-red-500">{formatCurrency(Math.max(0, returnOutstanding))}</p>
                                </div>
                            </div>
                            <p className="text-xs text-muted-foreground mt-1">
                                Recipient: <span className="text-foreground">{returnTarget.recipientName}</span>
                            </p>
                        </div>

                        {/* Return Amount */}
                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5">
                                Return Amount <span className="text-red-500">*</span>
                            </label>
                            <input
                                type="number"
                                step="0.01"
                                min="0.01"
                                max={Math.max(0, returnOutstanding)}
                                value={returnAmount}
                                onChange={(e) => { setReturnAmount(e.target.value); clearRetError("returnAmount"); }}
                                className={`w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground font-mono focus:outline-none focus:ring-2 focus:ring-primary/50 ${inputErrorClass(retErrors.returnAmount)}`}
                                placeholder={`Max: ${formatCurrency(Math.max(0, returnOutstanding))}`}
                            />
                            <FieldError error={retErrors.returnAmount} />
                        </div>

                        {/* Return Remarks */}
                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5">Return Remarks</label>
                            <input
                                type="text"
                                value={returnRemarks}
                                onChange={(e) => setReturnRemarks(e.target.value)}
                                className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                                placeholder="Optional notes about the return..."
                            />
                        </div>

                        <div className="flex justify-end gap-3 pt-6 border-t border-border mt-6">
                            <button
                                type="button"
                                onClick={() => setIsReturnModalOpen(false)}
                                className="px-6 py-2.5 rounded-xl font-medium text-foreground bg-black/5 dark:bg-white/5 hover:bg-black/10 dark:hover:bg-white/10 transition-colors"
                            >
                                Cancel
                            </button>
                            <button
                                type="submit"
                                disabled={isSubmitting}
                                className="btn-gradient px-8 py-2.5 rounded-xl font-medium shadow-lg hover:shadow-xl transition-all disabled:opacity-50"
                            >
                                {isSubmitting ? "Saving..." : "Record Return"}
                            </button>
                        </div>
                    </form>
                )}
            </Modal>

            {/* Detail & Invoice Assignment Modal */}
            <Modal
                isOpen={isDetailModalOpen}
                onClose={() => { setIsDetailModalOpen(false); setDetailAdvance(null); }}
                title="Advance Details"
            >
                {detailAdvance && (
                    <div className="space-y-5">
                        {/* Advance Summary */}
                        <div className="p-4 bg-white/5 rounded-xl border border-border">
                            <div className="flex items-center gap-2 mb-4">
                                {(() => {
                                    const meta = getAdvanceTypeMeta(detailAdvance.advanceType);
                                    const Icon = meta.icon;
                                    return (
                                        <>
                                            <div className={`p-1.5 rounded-lg ${meta.color}`}>
                                                <Icon className="w-4 h-4" />
                                            </div>
                                            <span className="text-sm font-bold text-foreground">{meta.label}</span>
                                        </>
                                    );
                                })()}
                                <span className={`ml-auto px-2.5 py-1 rounded-full text-[10px] font-bold uppercase ${(STATUS_CONFIG[detailAdvance.status] || STATUS_CONFIG.GIVEN).color}`}>
                                    {(STATUS_CONFIG[detailAdvance.status] || STATUS_CONFIG.GIVEN).label}
                                </span>
                            </div>

                            <div className="grid grid-cols-2 gap-x-6 gap-y-3 text-sm">
                                <div>
                                    <p className="text-[10px] uppercase tracking-wider text-muted-foreground">Recipient</p>
                                    <p className="font-medium text-foreground">{detailAdvance.recipientName}</p>
                                    {detailAdvance.employee && (
                                        <p className="text-[10px] text-primary mt-0.5">{detailAdvance.employee.designation || "Employee"}</p>
                                    )}
                                </div>
                                <div>
                                    <p className="text-[10px] uppercase tracking-wider text-muted-foreground">Date</p>
                                    <p className="font-medium text-foreground">{formatDateTime(detailAdvance.advanceDate)}</p>
                                </div>
                                {detailAdvance.purpose && (
                                    <div className="col-span-2">
                                        <p className="text-[10px] uppercase tracking-wider text-muted-foreground">Purpose</p>
                                        <p className="font-medium text-foreground">{detailAdvance.purpose}</p>
                                    </div>
                                )}
                            </div>

                            {/* Financial Breakdown */}
                            <div className="mt-4 pt-4 border-t border-border">
                                <div className="grid grid-cols-4 gap-3 text-sm">
                                    <div>
                                        <p className="text-[10px] uppercase tracking-wider text-muted-foreground">Amount</p>
                                        <p className="font-bold text-foreground text-base">{formatCurrency(detailAdvance.amount)}</p>
                                    </div>
                                    <div>
                                        <p className="text-[10px] uppercase tracking-wider text-muted-foreground">Bills</p>
                                        <p className="font-bold text-teal-500">{formatCurrency(detailAdvance.utilizedAmount || 0)}</p>
                                    </div>
                                    <div>
                                        <p className="text-[10px] uppercase tracking-wider text-muted-foreground">Returned</p>
                                        <p className="font-bold text-green-500">{formatCurrency(detailAdvance.returnedAmount || 0)}</p>
                                    </div>
                                    <div>
                                        <p className="text-[10px] uppercase tracking-wider text-muted-foreground">Outstanding</p>
                                        <p className="font-bold text-red-500">
                                            {formatCurrency(Math.max(0, detailAdvance.amount - (detailAdvance.returnedAmount || 0) - (detailAdvance.utilizedAmount || 0)))}
                                        </p>
                                    </div>
                                </div>
                            </div>
                        </div>

                        {/* Assigned Invoices */}
                        <div>
                            <div className="flex items-center justify-between mb-3">
                                <h3 className="text-sm font-bold text-foreground flex items-center gap-2">
                                    <FileText className="w-4 h-4 text-primary" />
                                    Assigned Invoices ({assignedInvoices.length})
                                </h3>
                                {detailAdvance.status !== "CANCELLED" && detailAdvance.status !== "RETURNED" && detailAdvance.status !== "SETTLED" && (
                                    <button
                                        onClick={handleOpenAssignPanel}
                                        className="text-xs font-medium text-primary hover:text-primary/80 flex items-center gap-1 transition-colors"
                                    >
                                        <Link2 className="w-3.5 h-3.5" />
                                        Assign Invoice
                                    </button>
                                )}
                            </div>

                            {assignedInvoices.length === 0 ? (
                                <div className="text-center py-6 text-muted-foreground text-sm border border-dashed border-border rounded-xl">
                                    No invoices assigned to this advance yet.
                                </div>
                            ) : (
                                <div className="space-y-2">
                                    {assignedInvoices.map((inv) => (
                                        <div
                                            key={inv.id}
                                            className="flex items-center justify-between p-3 bg-card border border-border rounded-xl hover:bg-white/5 transition-colors"
                                        >
                                            <div className="flex items-center gap-3">
                                                <div className="p-1.5 rounded-lg bg-teal-500/10 text-teal-500">
                                                    <Receipt className="w-3.5 h-3.5" />
                                                </div>
                                                <div>
                                                    <p className="text-sm font-medium text-foreground">
                                                        {inv.billNo || `INV-${inv.id}`}
                                                        <span className={`ml-2 px-1.5 py-0.5 rounded text-[9px] font-bold ${inv.billType === "CASH" ? "bg-green-500/10 text-green-500" : "bg-amber-500/10 text-amber-500"}`}>
                                                            {inv.billType}
                                                        </span>
                                                    </p>
                                                    <p className="text-[10px] text-muted-foreground">
                                                        {inv.customer?.name || "Walk-in"} {inv.vehicle ? `| ${inv.vehicle.vehicleNumber}` : ""} | {formatDateTime(inv.date)}
                                                    </p>
                                                </div>
                                            </div>
                                            <div className="flex items-center gap-3">
                                                <span className="text-sm font-bold text-foreground">{formatCurrency(inv.netAmount)}</span>
                                                {detailAdvance.status !== "CANCELLED" && detailAdvance.status !== "RETURNED" && detailAdvance.status !== "SETTLED" && (
                                                    <button
                                                        onClick={() => handleUnassignInvoice(inv.id)}
                                                        title="Unassign invoice"
                                                        className="p-1 rounded-lg hover:bg-red-500/10 text-muted-foreground hover:text-red-500 transition-all"
                                                    >
                                                        <Unlink className="w-3.5 h-3.5" />
                                                    </button>
                                                )}
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            )}
                        </div>

                        {/* Assign Invoice Panel */}
                        {showAssignPanel && (
                            <div className="border border-primary/30 rounded-xl p-4 bg-primary/5">
                                <div className="flex items-center justify-between mb-3">
                                    <h4 className="text-sm font-bold text-foreground">
                                        Available Invoices (Shift #{detailAdvance.shiftId})
                                    </h4>
                                    <button
                                        onClick={() => setShowAssignPanel(false)}
                                        className="text-xs text-muted-foreground hover:text-foreground"
                                    >
                                        Close
                                    </button>
                                </div>

                                <div className="relative mb-3">
                                    <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-muted-foreground" />
                                    <input
                                        type="text"
                                        placeholder="Search by bill no, customer, vehicle..."
                                        value={invoiceSearch}
                                        onChange={(e) => setInvoiceSearch(e.target.value)}
                                        className="w-full pl-9 pr-4 py-2 bg-background border border-border rounded-lg text-foreground text-xs placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                                    />
                                </div>

                                <div className="max-h-48 overflow-y-auto space-y-1.5">
                                    {filteredAvailableInvoices.length === 0 ? (
                                        <p className="text-center py-4 text-muted-foreground text-xs">
                                            No unassigned invoices available in this shift.
                                        </p>
                                    ) : (
                                        filteredAvailableInvoices.map((inv) => (
                                            <div
                                                key={inv.id}
                                                className="flex items-center justify-between p-2.5 bg-card border border-border rounded-lg hover:border-primary/30 transition-colors cursor-pointer"
                                                onClick={() => handleAssignInvoice(inv.id)}
                                            >
                                                <div>
                                                    <p className="text-xs font-medium text-foreground">
                                                        {inv.billNo || `INV-${inv.id}`}
                                                        <span className={`ml-1.5 px-1.5 py-0.5 rounded text-[9px] font-bold ${inv.billType === "CASH" ? "bg-green-500/10 text-green-500" : "bg-amber-500/10 text-amber-500"}`}>
                                                            {inv.billType}
                                                        </span>
                                                    </p>
                                                    <p className="text-[10px] text-muted-foreground">
                                                        {inv.customer?.name || "Walk-in"} {inv.vehicle ? `| ${inv.vehicle.vehicleNumber}` : ""}
                                                    </p>
                                                </div>
                                                <div className="flex items-center gap-2">
                                                    <span className="text-xs font-bold text-foreground">{formatCurrency(inv.netAmount)}</span>
                                                    <ChevronRight className="w-3.5 h-3.5 text-primary" />
                                                </div>
                                            </div>
                                        ))
                                    )}
                                </div>
                            </div>
                        )}

                        {/* Linked Statement */}
                        <div>
                            <div className="flex items-center justify-between mb-3">
                                <h3 className="text-sm font-bold text-foreground flex items-center gap-2">
                                    <FileText className="w-4 h-4 text-purple-500" />
                                    Linked Statement
                                </h3>
                                {!detailAdvance.statement && detailAdvance.status !== "CANCELLED" && detailAdvance.status !== "RETURNED" && detailAdvance.status !== "SETTLED" && (
                                    <button
                                        onClick={handleOpenStatementPanel}
                                        className="text-xs font-medium text-purple-500 hover:text-purple-400 flex items-center gap-1 transition-colors"
                                    >
                                        <Link2 className="w-3.5 h-3.5" />
                                        Assign Statement
                                    </button>
                                )}
                            </div>

                            {detailAdvance.statement ? (
                                <div className="flex items-center justify-between p-3 bg-card border border-border rounded-xl">
                                    <div className="flex items-center gap-3">
                                        <div className="p-1.5 rounded-lg bg-purple-500/10 text-purple-500">
                                            <FileText className="w-3.5 h-3.5" />
                                        </div>
                                        <div>
                                            <p className="text-sm font-medium text-foreground">
                                                Stmt #{detailAdvance.statement.statementNo}
                                                <span className={`ml-2 px-1.5 py-0.5 rounded text-[9px] font-bold ${detailAdvance.statement.status === "PAID" ? "bg-green-500/10 text-green-500" : "bg-red-500/10 text-red-500"}`}>
                                                    {detailAdvance.statement.status}
                                                </span>
                                            </p>
                                            <p className="text-[10px] text-muted-foreground">
                                                {detailAdvance.statement.customer?.name || "-"} | Net: {formatCurrency(detailAdvance.statement.netAmount)}
                                            </p>
                                        </div>
                                    </div>
                                    <div className="flex items-center gap-3">
                                        <span className="text-sm font-bold text-foreground">{formatCurrency(detailAdvance.statement.totalAmount)}</span>
                                        {detailAdvance.status !== "CANCELLED" && detailAdvance.status !== "RETURNED" && detailAdvance.status !== "SETTLED" && (
                                            <button
                                                onClick={handleUnassignStatement}
                                                title="Unassign statement"
                                                className="p-1 rounded-lg hover:bg-red-500/10 text-muted-foreground hover:text-red-500 transition-all"
                                            >
                                                <Unlink className="w-3.5 h-3.5" />
                                            </button>
                                        )}
                                    </div>
                                </div>
                            ) : (
                                <div className="text-center py-4 text-muted-foreground text-sm border border-dashed border-border rounded-xl">
                                    No statement linked.
                                </div>
                            )}
                        </div>

                        {/* Statement Assignment Panel */}
                        {showStatementPanel && (
                            <div className="border border-purple-500/30 rounded-xl p-4 bg-purple-500/5">
                                <div className="flex items-center justify-between mb-3">
                                    <h4 className="text-sm font-bold text-foreground">Outstanding Statements</h4>
                                    <button onClick={() => setShowStatementPanel(false)} className="text-xs text-muted-foreground hover:text-foreground">Close</button>
                                </div>
                                <div className="max-h-48 overflow-y-auto space-y-1.5">
                                    {availableStatements.length === 0 ? (
                                        <p className="text-center py-4 text-muted-foreground text-xs">No outstanding statements available.</p>
                                    ) : (
                                        availableStatements.map((stmt) => (
                                            <div
                                                key={stmt.id}
                                                className="flex items-center justify-between p-2.5 bg-card border border-border rounded-lg hover:border-purple-500/30 transition-colors cursor-pointer"
                                                onClick={() => handleAssignStatement(stmt.id)}
                                            >
                                                <div>
                                                    <p className="text-xs font-medium text-foreground">
                                                        Stmt #{stmt.statementNo}
                                                    </p>
                                                    <p className="text-[10px] text-muted-foreground">{stmt.customer?.name || "-"}</p>
                                                </div>
                                                <div className="flex items-center gap-2">
                                                    <span className="text-xs font-bold text-foreground">{formatCurrency(stmt.balanceAmount)}</span>
                                                    <ChevronRight className="w-3.5 h-3.5 text-purple-500" />
                                                </div>
                                            </div>
                                        ))
                                    )}
                                </div>
                            </div>
                        )}

                        {/* Return Info */}
                        {detailAdvance.returnDate && (
                            <div className="p-3 bg-green-500/5 border border-green-500/20 rounded-xl">
                                <p className="text-xs text-green-500 font-medium">
                                    Returned on {formatDateTime(detailAdvance.returnDate)}
                                </p>
                                {detailAdvance.returnRemarks && (
                                    <p className="text-xs text-muted-foreground mt-1">{detailAdvance.returnRemarks}</p>
                                )}
                            </div>
                        )}
                    </div>
                )}
            </Modal>
        </div>
    );
}
