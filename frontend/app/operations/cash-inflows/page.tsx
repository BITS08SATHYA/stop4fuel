"use client";

import { useEffect, useState, useCallback, useMemo } from "react";
import { GlassCard } from "@/components/ui/glass-card";
import { Modal } from "@/components/ui/modal";
import {
    Banknote,
    ArrowDownLeft,
    ArrowUpRight,
    Search,
    Plus,
    Undo2,
    Hash,
    AlertCircle,
    Eye,
    Clock,
} from "lucide-react";
import { API_BASE_URL } from "@/lib/api/station";
import { fetchWithAuth } from "@/lib/api/fetch-with-auth";
import { TablePagination, useClientPagination } from "@/components/ui/table-pagination";
import { PermissionGate } from "@/components/permission-gate";

// --- Types ---

interface Shift {
    id: number;
    startTime: string;
    status: string;
}

interface ExternalCashInflow {
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

interface CashInflowRepayment {
    id: number;
    amount: number;
    repaymentDate: string;
    remarks?: string;
}

// --- API helpers ---

async function fetchInflows(): Promise<ExternalCashInflow[]> {
    const res = await fetchWithAuth(`${API_BASE_URL}/cash-inflows`);
    if (!res.ok) throw new Error("Failed to fetch cash inflows");
    return res.json();
}

async function fetchActiveShift(): Promise<Shift | null> {
    try {
        const res = await fetchWithAuth(`${API_BASE_URL}/shifts/active`);
        if (!res.ok) return null;
        return res.json();
    } catch { return null; }
}

async function createInflow(data: Partial<ExternalCashInflow>): Promise<ExternalCashInflow> {
    const res = await fetchWithAuth(`${API_BASE_URL}/cash-inflows`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(data),
    });
    if (!res.ok) throw new Error("Failed to create inflow");
    return res.json();
}

async function recordRepayment(inflowId: number, data: { amount: number; remarks?: string }): Promise<CashInflowRepayment> {
    const res = await fetchWithAuth(`${API_BASE_URL}/cash-inflows/${inflowId}/repay`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(data),
    });
    if (!res.ok) throw new Error("Failed to record repayment");
    return res.json();
}

async function fetchRepayments(inflowId: number): Promise<CashInflowRepayment[]> {
    const res = await fetchWithAuth(`${API_BASE_URL}/cash-inflows/${inflowId}/repayments`);
    if (!res.ok) return [];
    return res.json();
}

// --- Constants ---

const STATUS_CONFIG: Record<string, { label: string; color: string }> = {
    ACTIVE: { label: "Active", color: "bg-amber-500/10 text-amber-500" },
    PARTIALLY_REPAID: { label: "Partial", color: "bg-orange-500/10 text-orange-500" },
    FULLY_REPAID: { label: "Repaid", color: "bg-green-500/10 text-green-500" },
};

function formatDateTime(dt?: string | null) {
    if (!dt) return "-";
    return new Date(dt).toLocaleString("en-IN", {
        day: "2-digit", month: "short", year: "numeric",
        hour: "2-digit", minute: "2-digit",
    });
}

function formatCurrency(val?: number | null) {
    if (val == null) return "0.00";
    return val.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

export default function CashInflowsPage() {
    const [inflows, setInflows] = useState<ExternalCashInflow[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [activeShift, setActiveShift] = useState<Shift | null>(null);

    // Filters
    const [searchQuery, setSearchQuery] = useState("");
    const [statusFilter, setStatusFilter] = useState("ALL");

    // Record Inflow modal
    const [showAddModal, setShowAddModal] = useState(false);
    const [addAmount, setAddAmount] = useState("");
    const [addSource, setAddSource] = useState("");
    const [addPurpose, setAddPurpose] = useState("");
    const [addRemarks, setAddRemarks] = useState("");
    const [addSaving, setAddSaving] = useState(false);

    // Repayment modal
    const [showRepayModal, setShowRepayModal] = useState(false);
    const [repayTarget, setRepayTarget] = useState<ExternalCashInflow | null>(null);
    const [repayAmount, setRepayAmount] = useState("");
    const [repayRemarks, setRepayRemarks] = useState("");
    const [repaySaving, setRepaySaving] = useState(false);

    // Detail modal
    const [showDetailModal, setShowDetailModal] = useState(false);
    const [detailInflow, setDetailInflow] = useState<ExternalCashInflow | null>(null);
    const [repayments, setRepayments] = useState<CashInflowRepayment[]>([]);
    const [loadingRepayments, setLoadingRepayments] = useState(false);

    const loadData = useCallback(async () => {
        setIsLoading(true);
        try {
            const [inflowData, shift] = await Promise.all([
                fetchInflows(),
                fetchActiveShift(),
            ]);
            setInflows(inflowData);
            setActiveShift(shift);
        } catch (err) {
            console.error("Failed to load cash inflows", err);
        } finally {
            setIsLoading(false);
        }
    }, []);

    useEffect(() => {
        loadData();
    }, [loadData]);

    // Summary
    const summary = useMemo(() => {
        const totalInflow = inflows
            .reduce((sum, i) => sum + (i.amount || 0), 0);
        const totalRepaid = inflows.reduce((sum, i) => sum + (i.repaidAmount || 0), 0);
        const outstanding = totalInflow - totalRepaid;
        const activeCount = inflows.filter((i) => i.status !== "FULLY_REPAID").length;
        return { totalInflow, totalRepaid, outstanding, count: inflows.length, activeCount };
    }, [inflows]);

    // Filtered list
    const filtered = useMemo(() => {
        return inflows.filter((i) => {
            if (statusFilter !== "ALL" && i.status !== statusFilter) return false;
            if (searchQuery) {
                const q = searchQuery.toLowerCase();
                return (
                    i.source?.toLowerCase().includes(q) ||
                    i.purpose?.toLowerCase().includes(q) ||
                    i.remarks?.toLowerCase().includes(q)
                );
            }
            return true;
        });
    }, [inflows, statusFilter, searchQuery]);

    const { page, setPage, totalPages, totalElements, pageSize, paginatedData: pagedInflows } = useClientPagination(filtered);

    // --- Handlers ---

    const handleOpenAddModal = () => {
        setAddAmount("");
        setAddSource("");
        setAddPurpose("");
        setAddRemarks("");
        setShowAddModal(true);
    };

    const handleCreateInflow = async () => {
        if (!addAmount || !addSource) return;
        setAddSaving(true);
        try {
            await createInflow({
                amount: parseFloat(addAmount),
                source: addSource,
                purpose: addPurpose || undefined,
                remarks: addRemarks || undefined,
            });
            setShowAddModal(false);
            loadData();
        } catch (err) {
            console.error("Failed to create inflow", err);
        } finally {
            setAddSaving(false);
        }
    };

    const handleOpenRepayModal = (inflow: ExternalCashInflow) => {
        setRepayTarget(inflow);
        setRepayAmount("");
        setRepayRemarks("");
        setShowRepayModal(true);
    };

    const handleRecordRepayment = async () => {
        if (!repayTarget || !repayAmount) return;
        setRepaySaving(true);
        try {
            await recordRepayment(repayTarget.id, {
                amount: parseFloat(repayAmount),
                remarks: repayRemarks || undefined,
            });
            setShowRepayModal(false);
            loadData();
        } catch (err) {
            console.error("Failed to record repayment", err);
        } finally {
            setRepaySaving(false);
        }
    };

    const handleOpenDetail = async (inflow: ExternalCashInflow) => {
        setDetailInflow(inflow);
        setRepayments([]);
        setShowDetailModal(true);
        setLoadingRepayments(true);
        try {
            const reps = await fetchRepayments(inflow.id);
            setRepayments(reps);
        } catch (err) {
            console.error("Failed to load repayments", err);
        } finally {
            setLoadingRepayments(false);
        }
    };

    const repayOutstanding = repayTarget
        ? repayTarget.amount - (repayTarget.repaidAmount || 0)
        : 0;

    return (
        <div className="p-6 h-screen overflow-hidden bg-background transition-colors duration-300">
            <div className="max-w-7xl mx-auto">
                {/* Header */}
                <div className="flex justify-between items-center mb-8">
                    <div>
                        <h1 className="text-4xl font-bold text-foreground tracking-tight">
                            Cash <span className="text-gradient">Inflows</span>
                        </h1>
                        <p className="text-muted-foreground mt-2">
                            Track external cash brought into the station and repayments.
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
                                disabled={!activeShift}
                                title={!activeShift ? "Open a shift first to record inflows" : "Record a new cash inflow"}
                                className={`px-5 py-2.5 rounded-xl font-medium flex items-center gap-2 shadow-lg transition-all ${
                                    activeShift
                                        ? "btn-gradient hover:shadow-xl"
                                        : "bg-gray-500/10 text-gray-500 border border-gray-500/20 cursor-not-allowed shadow-none"
                                }`}
                            >
                                <Plus className="w-4 h-4" />
                                Record Inflow
                            </button>
                        </PermissionGate>
                    </div>
                </div>

                {isLoading ? (
                    <div className="flex flex-col items-center justify-center py-20 text-muted-foreground">
                        <div className="w-12 h-12 border-4 border-primary/20 border-t-primary rounded-full animate-spin mb-4" />
                        <p className="animate-pulse">Loading cash inflows...</p>
                    </div>
                ) : (
                    <>
                        {/* Summary Cards */}
                        <div className="grid grid-cols-2 sm:grid-cols-5 gap-3 mb-6">
                            <div className="flex items-center gap-3 p-3 rounded-xl border border-border bg-card">
                                <div className="p-2 rounded-lg text-blue-500 bg-blue-500/10">
                                    <ArrowDownLeft className="w-4 h-4" />
                                </div>
                                <div>
                                    <p className="text-[10px] uppercase tracking-wider text-muted-foreground">Total Inflow</p>
                                    <p className="text-sm font-bold text-foreground">{formatCurrency(summary.totalInflow)}</p>
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
                                <div className="p-2 rounded-lg text-green-500 bg-green-500/10">
                                    <ArrowUpRight className="w-4 h-4" />
                                </div>
                                <div>
                                    <p className="text-[10px] uppercase tracking-wider text-muted-foreground">Total Repaid</p>
                                    <p className="text-sm font-bold text-green-500">{formatCurrency(summary.totalRepaid)}</p>
                                </div>
                            </div>
                            <div className="flex items-center gap-3 p-3 rounded-xl border border-border bg-card">
                                <div className="p-2 rounded-lg text-amber-500 bg-amber-500/10">
                                    <Clock className="w-4 h-4" />
                                </div>
                                <div>
                                    <p className="text-[10px] uppercase tracking-wider text-muted-foreground">Active</p>
                                    <p className="text-sm font-bold text-amber-500">{summary.activeCount}</p>
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
                                    placeholder="Search source, purpose, remarks..."
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
                                <option value="ACTIVE">Active</option>
                                <option value="PARTIALLY_REPAID">Partially Repaid</option>
                                <option value="FULLY_REPAID">Fully Repaid</option>
                            </select>
                        </div>

                        {/* Table */}
                        <GlassCard className="overflow-hidden border-none p-0 mb-6">
                            <div className="overflow-x-auto">
                                <table className="w-full text-left border-collapse">
                                    <thead>
                                        <tr className="bg-white/5 border-b border-border/50">
                                            <th className="px-4 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Date</th>
                                            <th className="px-4 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Source</th>
                                            <th className="px-4 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Purpose</th>
                                            <th className="px-4 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right">Amount</th>
                                            <th className="px-4 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right">Repaid</th>
                                            <th className="px-4 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right">Outstanding</th>
                                            <th className="px-4 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center">Status</th>
                                            <th className="px-4 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center">Actions</th>
                                        </tr>
                                    </thead>
                                    <tbody className="divide-y divide-border/30">
                                        {filtered.length === 0 ? (
                                            <tr>
                                                <td colSpan={8} className="px-6 py-12 text-center text-muted-foreground">
                                                    No cash inflows found
                                                </td>
                                            </tr>
                                        ) : (
                                            pagedInflows.map((inf) => {
                                                const outstanding = inf.amount - (inf.repaidAmount || 0);
                                                const statusCfg = STATUS_CONFIG[inf.status] || STATUS_CONFIG.ACTIVE;
                                                const canRepay = inf.status !== "FULLY_REPAID";

                                                return (
                                                    <tr key={inf.id} className="hover:bg-white/5 transition-colors group">
                                                        <td className="px-4 py-3 text-xs text-muted-foreground whitespace-nowrap">
                                                            {formatDateTime(inf.inflowDate)}
                                                        </td>
                                                        <td className="px-4 py-3">
                                                            <div className="flex items-center gap-2">
                                                                <div className="p-1.5 rounded-lg text-blue-500 bg-blue-500/10">
                                                                    <Banknote className="w-3.5 h-3.5" />
                                                                </div>
                                                                <span className="text-sm font-medium text-foreground">{inf.source}</span>
                                                            </div>
                                                        </td>
                                                        <td className="px-4 py-3 text-sm text-muted-foreground max-w-[200px] truncate">
                                                            {inf.purpose || "-"}
                                                        </td>
                                                        <td className="px-4 py-3 text-sm font-semibold text-foreground text-right">
                                                            {formatCurrency(inf.amount)}
                                                        </td>
                                                        <td className="px-4 py-3 text-sm text-green-500 text-right">
                                                            {formatCurrency(inf.repaidAmount)}
                                                        </td>
                                                        <td className="px-4 py-3 text-sm font-semibold text-right">
                                                            <span className={outstanding > 0 ? "text-red-500" : "text-green-500"}>
                                                                {formatCurrency(outstanding)}
                                                            </span>
                                                        </td>
                                                        <td className="px-4 py-3 text-center">
                                                            <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-[10px] font-bold uppercase tracking-wider ${statusCfg.color}`}>
                                                                {statusCfg.label}
                                                            </span>
                                                        </td>
                                                        <td className="px-4 py-3 text-center">
                                                            <div className="flex items-center justify-center gap-1">
                                                                <button
                                                                    onClick={() => handleOpenDetail(inf)}
                                                                    className="p-1.5 rounded-lg text-muted-foreground hover:text-primary hover:bg-primary/10 transition-colors"
                                                                    title="View details"
                                                                >
                                                                    <Eye className="w-4 h-4" />
                                                                </button>
                                                                {canRepay && (
                                                                    <PermissionGate permission="FINANCE_MANAGE">
                                                                        <button
                                                                            onClick={() => handleOpenRepayModal(inf)}
                                                                            className="p-1.5 rounded-lg text-muted-foreground hover:text-green-500 hover:bg-green-500/10 transition-colors"
                                                                            title="Record repayment"
                                                                        >
                                                                            <Undo2 className="w-4 h-4" />
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
                        </GlassCard>

                        {totalPages > 1 && (
                            <TablePagination
                                page={page}
                                totalPages={totalPages}
                                totalElements={totalElements}
                                pageSize={pageSize}
                                onPageChange={setPage}
                            />
                        )}
                    </>
                )}
            </div>

            {/* Record Inflow Modal */}
            <Modal isOpen={showAddModal} onClose={() => setShowAddModal(false)} title="Record Cash Inflow">
                <div className="space-y-4">
                    <div>
                        <label className="block text-sm font-medium text-foreground mb-1">Amount <span className="text-red-500">*</span></label>
                        <input
                            type="number"
                            min="0"
                            step="0.01"
                            value={addAmount}
                            onChange={(e) => setAddAmount(e.target.value)}
                            placeholder="Enter amount"
                            className="w-full px-4 py-2.5 bg-card border border-border rounded-xl text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
                        />
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-foreground mb-1">Source <span className="text-red-500">*</span></label>
                        <input
                            type="text"
                            value={addSource}
                            onChange={(e) => setAddSource(e.target.value)}
                            placeholder="e.g. Owner, Partner, Bank Withdrawal"
                            className="w-full px-4 py-2.5 bg-card border border-border rounded-xl text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
                        />
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-foreground mb-1">Purpose</label>
                        <input
                            type="text"
                            value={addPurpose}
                            onChange={(e) => setAddPurpose(e.target.value)}
                            placeholder="e.g. Working capital, Emergency fund"
                            className="w-full px-4 py-2.5 bg-card border border-border rounded-xl text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
                        />
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-foreground mb-1">Remarks</label>
                        <textarea
                            value={addRemarks}
                            onChange={(e) => setAddRemarks(e.target.value)}
                            placeholder="Additional notes..."
                            rows={2}
                            className="w-full px-4 py-2.5 bg-card border border-border rounded-xl text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50 resize-none"
                        />
                    </div>
                    <div className="flex justify-end gap-3 pt-2">
                        <button
                            onClick={() => setShowAddModal(false)}
                            className="px-4 py-2.5 rounded-xl text-sm font-medium text-muted-foreground hover:bg-muted transition-colors"
                        >
                            Cancel
                        </button>
                        <button
                            onClick={handleCreateInflow}
                            disabled={!addAmount || !addSource || addSaving}
                            className="px-5 py-2.5 rounded-xl text-sm font-medium btn-gradient disabled:opacity-50 disabled:cursor-not-allowed"
                        >
                            {addSaving ? "Saving..." : "Save"}
                        </button>
                    </div>
                </div>
            </Modal>

            {/* Record Repayment Modal */}
            <Modal isOpen={showRepayModal} onClose={() => setShowRepayModal(false)} title="Record Repayment">
                {repayTarget && (
                    <div className="space-y-4">
                        <div className="p-4 rounded-xl bg-muted/30 border border-border/50 space-y-2">
                            <div className="flex justify-between text-sm">
                                <span className="text-muted-foreground">Source</span>
                                <span className="font-medium text-foreground">{repayTarget.source}</span>
                            </div>
                            <div className="flex justify-between text-sm">
                                <span className="text-muted-foreground">Total Amount</span>
                                <span className="font-medium text-foreground">{formatCurrency(repayTarget.amount)}</span>
                            </div>
                            <div className="flex justify-between text-sm">
                                <span className="text-muted-foreground">Already Repaid</span>
                                <span className="font-medium text-green-500">{formatCurrency(repayTarget.repaidAmount)}</span>
                            </div>
                            <div className="flex justify-between text-sm border-t border-border/50 pt-2">
                                <span className="text-muted-foreground font-medium">Outstanding</span>
                                <span className="font-bold text-red-500">{formatCurrency(repayOutstanding)}</span>
                            </div>
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1">Repayment Amount <span className="text-red-500">*</span></label>
                            <input
                                type="number"
                                min="0"
                                max={repayOutstanding}
                                step="0.01"
                                value={repayAmount}
                                onChange={(e) => setRepayAmount(e.target.value)}
                                placeholder={`Max ${formatCurrency(repayOutstanding)}`}
                                className="w-full px-4 py-2.5 bg-card border border-border rounded-xl text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
                            />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1">Remarks</label>
                            <textarea
                                value={repayRemarks}
                                onChange={(e) => setRepayRemarks(e.target.value)}
                                placeholder="Repayment notes..."
                                rows={2}
                                className="w-full px-4 py-2.5 bg-card border border-border rounded-xl text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50 resize-none"
                            />
                        </div>
                        <div className="flex justify-end gap-3 pt-2">
                            <button
                                onClick={() => setShowRepayModal(false)}
                                className="px-4 py-2.5 rounded-xl text-sm font-medium text-muted-foreground hover:bg-muted transition-colors"
                            >
                                Cancel
                            </button>
                            <button
                                onClick={handleRecordRepayment}
                                disabled={!repayAmount || parseFloat(repayAmount) <= 0 || repaySaving}
                                className="px-5 py-2.5 rounded-xl text-sm font-medium btn-gradient disabled:opacity-50 disabled:cursor-not-allowed"
                            >
                                {repaySaving ? "Saving..." : "Record Repayment"}
                            </button>
                        </div>
                    </div>
                )}
            </Modal>

            {/* Detail Modal */}
            <Modal isOpen={showDetailModal} onClose={() => setShowDetailModal(false)} title="Cash Inflow Details">
                {detailInflow && (
                    <div className="space-y-6">
                        {/* Summary */}
                        <div className="grid grid-cols-2 gap-4">
                            <div className="space-y-3">
                                <div>
                                    <p className="text-[10px] uppercase tracking-wider text-muted-foreground">Source</p>
                                    <p className="text-sm font-medium text-foreground">{detailInflow.source}</p>
                                </div>
                                <div>
                                    <p className="text-[10px] uppercase tracking-wider text-muted-foreground">Date</p>
                                    <p className="text-sm text-foreground">{formatDateTime(detailInflow.inflowDate)}</p>
                                </div>
                                {detailInflow.purpose && (
                                    <div>
                                        <p className="text-[10px] uppercase tracking-wider text-muted-foreground">Purpose</p>
                                        <p className="text-sm text-foreground">{detailInflow.purpose}</p>
                                    </div>
                                )}
                                {detailInflow.remarks && (
                                    <div>
                                        <p className="text-[10px] uppercase tracking-wider text-muted-foreground">Remarks</p>
                                        <p className="text-sm text-muted-foreground">{detailInflow.remarks}</p>
                                    </div>
                                )}
                            </div>
                            <div className="space-y-3">
                                <div>
                                    <p className="text-[10px] uppercase tracking-wider text-muted-foreground">Status</p>
                                    <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-[10px] font-bold uppercase tracking-wider ${(STATUS_CONFIG[detailInflow.status] || STATUS_CONFIG.ACTIVE).color}`}>
                                        {(STATUS_CONFIG[detailInflow.status] || STATUS_CONFIG.ACTIVE).label}
                                    </span>
                                </div>
                                <div className="p-3 rounded-xl bg-muted/30 border border-border/50 space-y-2">
                                    <div className="flex justify-between text-sm">
                                        <span className="text-muted-foreground">Amount</span>
                                        <span className="font-semibold text-foreground">{formatCurrency(detailInflow.amount)}</span>
                                    </div>
                                    <div className="flex justify-between text-sm">
                                        <span className="text-muted-foreground">Repaid</span>
                                        <span className="font-semibold text-green-500">{formatCurrency(detailInflow.repaidAmount)}</span>
                                    </div>
                                    <div className="flex justify-between text-sm border-t border-border/50 pt-2">
                                        <span className="text-muted-foreground">Outstanding</span>
                                        <span className="font-bold text-red-500">
                                            {formatCurrency(detailInflow.amount - (detailInflow.repaidAmount || 0))}
                                        </span>
                                    </div>
                                </div>
                            </div>
                        </div>

                        {/* Repayment History */}
                        <div>
                            <h3 className="text-sm font-semibold text-foreground mb-3 flex items-center gap-2">
                                <Undo2 className="w-4 h-4 text-muted-foreground" />
                                Repayment History
                            </h3>
                            {loadingRepayments ? (
                                <p className="text-sm text-muted-foreground animate-pulse py-4 text-center">Loading repayments...</p>
                            ) : repayments.length === 0 ? (
                                <p className="text-sm text-muted-foreground py-4 text-center border border-border/30 rounded-xl">
                                    No repayments recorded yet.
                                </p>
                            ) : (
                                <div className="border border-border/30 rounded-xl overflow-hidden">
                                    <table className="w-full text-left">
                                        <thead>
                                            <tr className="bg-white/5 border-b border-border/50">
                                                <th className="px-4 py-2.5 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Date</th>
                                                <th className="px-4 py-2.5 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right">Amount</th>
                                                <th className="px-4 py-2.5 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Remarks</th>
                                            </tr>
                                        </thead>
                                        <tbody className="divide-y divide-border/30">
                                            {repayments.map((rep) => (
                                                <tr key={rep.id} className="hover:bg-white/5">
                                                    <td className="px-4 py-2.5 text-xs text-muted-foreground">{formatDateTime(rep.repaymentDate)}</td>
                                                    <td className="px-4 py-2.5 text-sm font-semibold text-green-500 text-right">{formatCurrency(rep.amount)}</td>
                                                    <td className="px-4 py-2.5 text-xs text-muted-foreground">{rep.remarks || "-"}</td>
                                                </tr>
                                            ))}
                                        </tbody>
                                    </table>
                                </div>
                            )}
                        </div>
                    </div>
                )}
            </Modal>
        </div>
    );
}
