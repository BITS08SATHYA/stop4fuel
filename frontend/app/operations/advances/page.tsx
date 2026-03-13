"use client";

import { useEffect, useState, useCallback, useMemo } from "react";
import { GlassCard } from "@/components/ui/glass-card";
import { Modal } from "@/components/ui/modal";
import {
    Banknote,
    Moon,
    Sun,
    ArrowDownLeft,
    ArrowUpRight,
    Search,
    Plus,
    Undo2,
    XCircle,
    Hash,
    AlertCircle,
} from "lucide-react";

const API_BASE = "http://localhost:8080/api";

// --- Types ---

interface CashAdvance {
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
}

// --- Constants ---

const ADVANCE_TYPES = [
    { value: "HOME_ADVANCE", label: "Home Advance", icon: Sun, color: "text-purple-500 bg-purple-500/10" },
    { value: "NIGHT_ADVANCE", label: "Night Advance", icon: Moon, color: "text-indigo-500 bg-indigo-500/10" },
    { value: "REGULAR_ADVANCE", label: "Regular Advance", icon: Banknote, color: "text-blue-500 bg-blue-500/10" },
];

const STATUS_CONFIG: Record<string, { label: string; color: string }> = {
    GIVEN: { label: "Given", color: "bg-amber-500/10 text-amber-500" },
    RETURNED: { label: "Returned", color: "bg-green-500/10 text-green-500" },
    PARTIALLY_RETURNED: { label: "Partial", color: "bg-orange-500/10 text-orange-500" },
    CANCELLED: { label: "Cancelled", color: "bg-gray-500/10 text-gray-500" },
};

function getAdvanceTypeMeta(type: string) {
    return ADVANCE_TYPES.find((t) => t.value === type) || ADVANCE_TYPES[2];
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

async function fetchAdvances(): Promise<CashAdvance[]> {
    const res = await fetch(`${API_BASE}/advances`);
    if (!res.ok) throw new Error("Failed to fetch advances");
    return res.json();
}

async function fetchActiveShift(): Promise<{ id: number } | null> {
    try {
        const res = await fetch(`${API_BASE}/shifts/active`);
        if (!res.ok) return null;
        return res.json();
    } catch {
        return null;
    }
}

async function createAdvance(data: {
    amount: number;
    advanceType: string;
    recipientName: string;
    recipientPhone: string;
    purpose: string;
    remarks: string;
}): Promise<CashAdvance> {
    const res = await fetch(`${API_BASE}/advances`, {
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

async function returnAdvance(id: number, data: { returnedAmount: number; returnRemarks: string }): Promise<CashAdvance> {
    const res = await fetch(`${API_BASE}/advances/${id}/return`, {
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
    const res = await fetch(`${API_BASE}/advances/${id}/cancel`, { method: "PATCH" });
    if (!res.ok) {
        const err = await res.text();
        throw new Error(err || "Failed to cancel advance");
    }
}

async function deleteAdvance(id: number): Promise<void> {
    const res = await fetch(`${API_BASE}/advances/${id}`, { method: "DELETE" });
    if (!res.ok) {
        const err = await res.text();
        throw new Error(err || "Failed to delete advance");
    }
}

// --- Page Component ---

export default function CashAdvancesPage() {
    const [advances, setAdvances] = useState<CashAdvance[]>([]);
    const [activeShift, setActiveShift] = useState<{ id: number } | null>(null);
    const [isLoading, setIsLoading] = useState(true);

    // Filters
    const [searchQuery, setSearchQuery] = useState("");
    const [statusFilter, setStatusFilter] = useState("ALL");
    const [typeFilter, setTypeFilter] = useState("ALL");

    // Record Advance Modal
    const [isAddModalOpen, setIsAddModalOpen] = useState(false);
    const [addType, setAddType] = useState("HOME_ADVANCE");
    const [addAmount, setAddAmount] = useState("");
    const [addRecipientName, setAddRecipientName] = useState("");
    const [addRecipientPhone, setAddRecipientPhone] = useState("");
    const [addPurpose, setAddPurpose] = useState("");
    const [addRemarks, setAddRemarks] = useState("");
    const [isSubmitting, setIsSubmitting] = useState(false);

    // Return Modal
    const [isReturnModalOpen, setIsReturnModalOpen] = useState(false);
    const [returnTarget, setReturnTarget] = useState<CashAdvance | null>(null);
    const [returnAmount, setReturnAmount] = useState("");
    const [returnRemarks, setReturnRemarks] = useState("");

    const loadData = useCallback(async () => {
        setIsLoading(true);
        try {
            const [advs, shift] = await Promise.all([fetchAdvances(), fetchActiveShift()]);
            setAdvances(advs);
            setActiveShift(shift);
        } catch (err) {
            console.error("Failed to load data", err);
        } finally {
            setIsLoading(false);
        }
    }, []);

    useEffect(() => {
        loadData();
    }, [loadData]);

    // --- Summary calculations ---
    const summary = useMemo(() => {
        const totalGiven = advances
            .filter((a) => a.status !== "CANCELLED")
            .reduce((sum, a) => sum + a.amount, 0);

        const outstanding = advances
            .filter((a) => a.status === "GIVEN" || a.status === "PARTIALLY_RETURNED")
            .reduce((sum, a) => sum + (a.amount - a.returnedAmount), 0);

        const returned = advances.reduce((sum, a) => sum + a.returnedAmount, 0);

        return { totalGiven, outstanding, returned, count: advances.length };
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
                a.remarks?.toLowerCase().includes(q);
            return matchStatus && matchType && matchSearch;
        });
    }, [advances, statusFilter, typeFilter, searchQuery]);

    // --- Handlers ---
    const resetAddForm = () => {
        setAddType("HOME_ADVANCE");
        setAddAmount("");
        setAddRecipientName("");
        setAddRecipientPhone("");
        setAddPurpose("");
        setAddRemarks("");
    };

    const handleOpenAddModal = () => {
        if (!activeShift) return;
        resetAddForm();
        setIsAddModalOpen(true);
    };

    const handleCreateAdvance = async (e: React.FormEvent) => {
        e.preventDefault();
        setIsSubmitting(true);
        try {
            await createAdvance({
                amount: Number(addAmount),
                advanceType: addType,
                recipientName: addRecipientName,
                recipientPhone: addRecipientPhone,
                purpose: addPurpose,
                remarks: addRemarks,
            });
            setIsAddModalOpen(false);
            await loadData();
        } catch (err: any) {
            alert(err.message || "Failed to create advance");
        } finally {
            setIsSubmitting(false);
        }
    };

    const handleOpenReturnModal = (adv: CashAdvance) => {
        setReturnTarget(adv);
        setReturnAmount("");
        setReturnRemarks("");
        setIsReturnModalOpen(true);
    };

    const handleReturn = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!returnTarget) return;
        setIsSubmitting(true);
        try {
            await returnAdvance(returnTarget.id, {
                returnedAmount: Number(returnAmount),
                returnRemarks: returnRemarks,
            });
            setIsReturnModalOpen(false);
            setReturnTarget(null);
            await loadData();
        } catch (err: any) {
            alert(err.message || "Failed to record return");
        } finally {
            setIsSubmitting(false);
        }
    };

    const handleCancel = async (adv: CashAdvance) => {
        if (!confirm(`Cancel advance of Rs.${formatCurrency(adv.amount)} to ${adv.recipientName}?`)) return;
        try {
            await cancelAdvance(adv.id);
            await loadData();
        } catch (err: any) {
            alert(err.message || "Failed to cancel advance");
        }
    };

    const handleDelete = async (adv: CashAdvance) => {
        if (!confirm(`Delete this advance record permanently?`)) return;
        try {
            await deleteAdvance(adv.id);
            await loadData();
        } catch (err: any) {
            alert(err.message || "Failed to delete advance");
        }
    };

    const returnOutstanding = returnTarget ? returnTarget.amount - returnTarget.returnedAmount : 0;

    return (
        <div className="p-8 min-h-screen bg-background transition-colors duration-300">
            <div className="max-w-7xl mx-auto">
                {/* Header */}
                <div className="flex justify-between items-center mb-8">
                    <div>
                        <h1 className="text-4xl font-bold text-foreground tracking-tight">
                            Cash <span className="text-gradient">Advances</span>
                        </h1>
                        <p className="text-muted-foreground mt-2">
                            Track cash advances taken from the register and their returns.
                        </p>
                    </div>
                    <div className="flex items-center gap-3">
                        {!activeShift && (
                            <div className="flex items-center gap-2 px-4 py-2 bg-amber-500/10 border border-amber-500/20 rounded-xl">
                                <AlertCircle className="w-4 h-4 text-amber-500" />
                                <span className="text-sm font-medium text-amber-500">No active shift</span>
                            </div>
                        )}
                        <button
                            onClick={handleOpenAddModal}
                            disabled={!activeShift}
                            title={!activeShift ? "Open a shift first to record advances" : "Record a new cash advance"}
                            className={`px-5 py-2.5 rounded-xl font-medium flex items-center gap-2 shadow-lg transition-all ${
                                activeShift
                                    ? "btn-gradient hover:shadow-xl"
                                    : "bg-gray-500/10 text-gray-500 border border-gray-500/20 cursor-not-allowed shadow-none"
                            }`}
                        >
                            <Plus className="w-4 h-4" />
                            Record Advance
                        </button>
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
                        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mb-6">
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
                                <div className="p-2 rounded-lg text-green-500 bg-green-500/10">
                                    <ArrowDownLeft className="w-4 h-4" />
                                </div>
                                <div>
                                    <p className="text-[10px] uppercase tracking-wider text-muted-foreground">Returned</p>
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
                                    placeholder="Search recipient, purpose, remarks..."
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
                                <option value="CANCELLED">Cancelled</option>
                            </select>
                            <select
                                value={typeFilter}
                                onChange={(e) => setTypeFilter(e.target.value)}
                                className="px-4 py-2.5 bg-card border border-border rounded-xl text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
                            >
                                <option value="ALL">All Types</option>
                                <option value="HOME_ADVANCE">Home Advance</option>
                                <option value="NIGHT_ADVANCE">Night Advance</option>
                                <option value="REGULAR_ADVANCE">Regular Advance</option>
                            </select>
                        </div>

                        {/* Advances Table */}
                        <GlassCard className="overflow-hidden border-none p-0 mb-6">
                            <div className="overflow-x-auto">
                                <table className="w-full text-left border-collapse">
                                    <thead>
                                        <tr className="bg-white/5 border-b border-border/50">
                                            <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Date</th>
                                            <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Type</th>
                                            <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Recipient</th>
                                            <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right">Amount</th>
                                            <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right">Returned</th>
                                            <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right">Outstanding</th>
                                            <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center">Status</th>
                                            <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center">Actions</th>
                                        </tr>
                                    </thead>
                                    <tbody className="divide-y divide-border/30">
                                        {filtered.length === 0 ? (
                                            <tr>
                                                <td colSpan={8} className="px-6 py-12 text-center text-muted-foreground">
                                                    No advances found
                                                </td>
                                            </tr>
                                        ) : (
                                            filtered.map((adv) => {
                                                const meta = getAdvanceTypeMeta(adv.advanceType);
                                                const Icon = meta.icon;
                                                const outstanding = adv.amount - adv.returnedAmount;
                                                const statusCfg = STATUS_CONFIG[adv.status] || STATUS_CONFIG.GIVEN;
                                                const canReturn = adv.status === "GIVEN" || adv.status === "PARTIALLY_RETURNED";
                                                const canCancel = adv.status === "GIVEN";

                                                return (
                                                    <tr key={adv.id} className="hover:bg-white/5 transition-colors group">
                                                        <td className="px-6 py-3 text-xs text-muted-foreground whitespace-nowrap">
                                                            {formatDateTime(adv.advanceDate)}
                                                        </td>
                                                        <td className="px-6 py-3">
                                                            <div className="flex items-center gap-2">
                                                                <div className={`p-1.5 rounded-lg ${meta.color}`}>
                                                                    <Icon className="w-3.5 h-3.5" />
                                                                </div>
                                                                <span className="text-sm font-medium text-foreground">
                                                                    {meta.label}
                                                                </span>
                                                            </div>
                                                        </td>
                                                        <td className="px-6 py-3">
                                                            <div>
                                                                <p className="text-sm font-medium text-foreground">{adv.recipientName}</p>
                                                                {adv.recipientPhone && (
                                                                    <p className="text-[10px] text-muted-foreground">{adv.recipientPhone}</p>
                                                                )}
                                                            </div>
                                                        </td>
                                                        <td className="px-6 py-3 text-right">
                                                            <span className="text-sm font-bold text-foreground">
                                                                {formatCurrency(adv.amount)}
                                                            </span>
                                                        </td>
                                                        <td className="px-6 py-3 text-right">
                                                            <span className="text-sm text-foreground">
                                                                {adv.returnedAmount > 0 ? formatCurrency(adv.returnedAmount) : "-"}
                                                            </span>
                                                        </td>
                                                        <td className="px-6 py-3 text-right">
                                                            <span className={`text-sm font-bold ${outstanding > 0 ? "text-red-500" : "text-foreground"}`}>
                                                                {adv.status === "CANCELLED" ? "-" : formatCurrency(outstanding)}
                                                            </span>
                                                        </td>
                                                        <td className="px-6 py-3 text-center">
                                                            <span className={`px-2.5 py-1 rounded-full text-[10px] font-bold uppercase ${statusCfg.color}`}>
                                                                {statusCfg.label}
                                                            </span>
                                                        </td>
                                                        <td className="px-6 py-3 text-center">
                                                            <div className="flex items-center justify-center gap-1">
                                                                {canReturn && (
                                                                    <button
                                                                        onClick={() => handleOpenReturnModal(adv)}
                                                                        title="Record return"
                                                                        className="p-1.5 rounded-lg hover:bg-green-500/10 text-muted-foreground hover:text-green-500 opacity-100 md:opacity-0 group-hover:opacity-100 transition-all"
                                                                    >
                                                                        <Undo2 className="w-3.5 h-3.5" />
                                                                    </button>
                                                                )}
                                                                {canCancel && (
                                                                    <button
                                                                        onClick={() => handleCancel(adv)}
                                                                        title="Cancel advance"
                                                                        className="p-1.5 rounded-lg hover:bg-red-500/10 text-muted-foreground hover:text-red-500 opacity-100 md:opacity-0 group-hover:opacity-100 transition-all"
                                                                    >
                                                                        <XCircle className="w-3.5 h-3.5" />
                                                                    </button>
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
                    </>
                )}
            </div>

            {/* Record Advance Modal */}
            <Modal
                isOpen={isAddModalOpen}
                onClose={() => setIsAddModalOpen(false)}
                title="Record Cash Advance"
            >
                <form onSubmit={handleCreateAdvance} className="space-y-4">
                    {/* Advance Type Selector */}
                    <div>
                        <label className="block text-sm font-medium text-foreground mb-2">Advance Type</label>
                        <div className="grid grid-cols-3 gap-2">
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

                    {/* Amount */}
                    <div>
                        <label className="block text-sm font-medium text-foreground mb-1.5">
                            Amount <span className="text-red-500">*</span>
                        </label>
                        <input
                            type="number"
                            step="0.01"
                            min="0.01"
                            required
                            value={addAmount}
                            onChange={(e) => setAddAmount(e.target.value)}
                            className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground font-mono focus:outline-none focus:ring-2 focus:ring-primary/50"
                            placeholder="0.00"
                        />
                    </div>

                    {/* Recipient Name */}
                    <div>
                        <label className="block text-sm font-medium text-foreground mb-1.5">
                            Recipient Name <span className="text-red-500">*</span>
                        </label>
                        <input
                            type="text"
                            required
                            value={addRecipientName}
                            onChange={(e) => setAddRecipientName(e.target.value)}
                            className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                            placeholder="Who is taking the advance?"
                        />
                    </div>

                    {/* Recipient Phone */}
                    <div>
                        <label className="block text-sm font-medium text-foreground mb-1.5">Recipient Phone</label>
                        <input
                            type="text"
                            value={addRecipientPhone}
                            onChange={(e) => setAddRecipientPhone(e.target.value)}
                            className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                            placeholder="Phone number (optional)"
                        />
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
                            <div className="grid grid-cols-3 gap-3 text-sm">
                                <div>
                                    <p className="text-[10px] uppercase tracking-wider text-muted-foreground">Amount</p>
                                    <p className="font-bold text-foreground">{formatCurrency(returnTarget.amount)}</p>
                                </div>
                                <div>
                                    <p className="text-[10px] uppercase tracking-wider text-muted-foreground">Already Returned</p>
                                    <p className="font-bold text-green-500">{formatCurrency(returnTarget.returnedAmount)}</p>
                                </div>
                                <div>
                                    <p className="text-[10px] uppercase tracking-wider text-muted-foreground">Outstanding</p>
                                    <p className="font-bold text-red-500">{formatCurrency(returnOutstanding)}</p>
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
                                max={returnOutstanding}
                                required
                                value={returnAmount}
                                onChange={(e) => setReturnAmount(e.target.value)}
                                className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground font-mono focus:outline-none focus:ring-2 focus:ring-primary/50"
                                placeholder={`Max: ${formatCurrency(returnOutstanding)}`}
                            />
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
        </div>
    );
}
