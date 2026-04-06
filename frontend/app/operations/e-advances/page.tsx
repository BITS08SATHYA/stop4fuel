"use client";

import { useEffect, useState, useCallback, useMemo } from "react";
import { GlassCard } from "@/components/ui/glass-card";
import { StyledSelect } from "@/components/ui/styled-select";
import { Modal } from "@/components/ui/modal";
import {
    CreditCard,
    Smartphone,
    Building2,
    FileText,
    Receipt,
    Search,
    Plus,
    Pencil,
    Trash2,
    Calendar,
} from "lucide-react";
import {
    API_BASE_URL,
    EAdvance,
    EAdvanceSummary,
    UpiCompany,
    getEAdvancesByShift,
    getEAdvanceSummary,
    createEAdvance,
    deleteEAdvance,
    getUpiCompanies,
    createUpiCompany,
} from "@/lib/api/station";
import { fetchWithAuth } from "@/lib/api/fetch-with-auth";
import { TablePagination, useClientPagination } from "@/components/ui/table-pagination";
import { InvoiceAutocomplete } from "@/components/ui/invoice-autocomplete";
import { StatementAutocomplete } from "@/components/ui/statement-autocomplete";
import { PermissionGate } from "@/components/permission-gate";

// --- Constants ---

const ADVANCE_TYPES = [
    { value: "UPI", label: "UPI", icon: Smartphone, color: "text-purple-500 bg-purple-500/10" },
    { value: "CARD", label: "Card", icon: CreditCard, color: "text-blue-500 bg-blue-500/10" },
    { value: "CHEQUE", label: "Cheque", icon: FileText, color: "text-amber-500 bg-amber-500/10" },
    { value: "BANK_TRANSFER", label: "Bank Transfer", icon: Building2, color: "text-cyan-500 bg-cyan-500/10" },
    { value: "CCMS", label: "CCMS", icon: Receipt, color: "text-pink-500 bg-pink-500/10" },
];

function getTypeMeta(type: string) {
    const upper = type?.toUpperCase();
    return ADVANCE_TYPES.find((t) => t.value === upper) || ADVANCE_TYPES[0];
}

function formatDateTime(dt?: string | null) {
    if (!dt) return "-";
    return new Date(dt).toLocaleString("en-IN", {
        day: "2-digit", month: "short", year: "numeric",
        hour: "2-digit", minute: "2-digit",
    });
}

function formatCurrency(val?: number) {
    if (val == null) return "0.00";
    return val.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

// --- API helpers ---

async function fetchActiveShift(): Promise<{ id: number; startTime?: string } | null> {
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

async function fetchEAdvancesByShift(shiftId: number): Promise<EAdvance[]> {
    const res = await fetchWithAuth(`${API_BASE_URL}/e-advances/shift/${shiftId}`);
    if (!res.ok) throw new Error("Failed to fetch e-advances");
    return res.json();
}

async function fetchEAdvancesByDateRange(fromDate: string, toDate: string): Promise<EAdvance[]> {
    const res = await fetchWithAuth(`${API_BASE_URL}/e-advances/search?fromDate=${fromDate}&toDate=${toDate}`);
    if (!res.ok) throw new Error("Failed to fetch e-advances");
    return res.json();
}

async function updateEAdvance(id: number, data: Partial<EAdvance>): Promise<EAdvance> {
    const res = await fetchWithAuth(`${API_BASE_URL}/e-advances/${id}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(data),
    });
    if (!res.ok) {
        const err = await res.text();
        throw new Error(err || "Failed to update e-advance");
    }
    return res.json();
}

async function deleteEAdvanceById(id: number): Promise<void> {
    const res = await fetchWithAuth(`${API_BASE_URL}/e-advances/${id}`, { method: "DELETE" });
    if (!res.ok) throw new Error("Failed to delete e-advance");
}

// --- Page Component ---

export default function EAdvancesPage() {
    const [entries, setEntries] = useState<EAdvance[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [activeShiftId, setActiveShiftId] = useState<number | null>(null);

    // Filters
    const [searchQuery, setSearchQuery] = useState("");
    const [typeFilter, setTypeFilter] = useState("ALL");
    const [fromDate, setFromDate] = useState("");
    const [toDate, setToDate] = useState("");
    const [viewMode, setViewMode] = useState<"shift" | "dates">("shift");

    // Add/Edit modal
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [editingId, setEditingId] = useState<number | null>(null);
    const [txnType, setTxnType] = useState("UPI");
    const [txnAmount, setTxnAmount] = useState("");
    const [txnRemarks, setTxnRemarks] = useState("");
    // UPI
    const [upiCompanies, setUpiCompanies] = useState<UpiCompany[]>([]);
    const [selectedUpiCompanyId, setSelectedUpiCompanyId] = useState("");
    const [newUpiCompanyName, setNewUpiCompanyName] = useState("");
    // Card
    const [cardBankName, setCardBankName] = useState("");
    const [cardLast4, setCardLast4] = useState("");
    const [cardBatchId, setCardBatchId] = useState("");
    const [cardTid, setCardTid] = useState("");
    const [cardCustomerName, setCardCustomerName] = useState("");
    const [cardCustomerPhone, setCardCustomerPhone] = useState("");
    // Cheque
    const [chequeBankName, setChequeBankName] = useState("");
    const [chequeNo, setChequeNo] = useState("");
    const [chequeInFavorOf, setChequeInFavorOf] = useState("");
    const [chequeDate, setChequeDate] = useState("");
    // Bank
    const [bankName, setBankName] = useState("");
    // CCMS
    const [ccmsNumber, setCcmsNumber] = useState("");
    // Invoice / Statement linking
    const [linkedInvoice, setLinkedInvoice] = useState<{ id: number; billNo?: string; billType?: string; netAmount?: number; customer?: { id: number; name: string } | null } | null>(null);
    const [linkedStatement, setLinkedStatement] = useState<any>(null);

    const loadData = useCallback(async (mode: "shift" | "dates", shiftId?: number | null, from?: string, to?: string) => {
        setIsLoading(true);
        try {
            let data: EAdvance[];
            if (mode === "dates" && from && to) {
                data = await fetchEAdvancesByDateRange(from, to);
            } else if (mode === "shift" && shiftId) {
                data = await fetchEAdvancesByShift(shiftId);
            } else {
                data = [];
            }
            setEntries(data);
        } catch (err) {
            console.error("Failed to load e-advances", err);
        } finally {
            setIsLoading(false);
        }
    }, []);

    useEffect(() => {
        (async () => {
            const shift = await fetchActiveShift();
            setActiveShiftId(shift?.id ?? null);
            if (shift?.id) {
                loadData("shift", shift.id);
                // Pre-fill date filters with shift start time
                if (shift.startTime) {
                    setFromDate(shift.startTime.split("T")[0]);
                    setToDate(new Date().toISOString().split("T")[0]);
                }
            } else {
                setIsLoading(false);
            }
        })();
    }, [loadData]);

    const handleDateSearch = () => {
        if (fromDate && toDate) {
            setViewMode("dates");
            loadData("dates", null, fromDate, toDate);
        }
    };

    const handleShowCurrentShift = () => {
        setViewMode("shift");
        if (activeShiftId) {
            loadData("shift", activeShiftId);
        } else {
            setEntries([]);
        }
    };

    // Summary - computed from loaded entries
    const summary = useMemo(() => {
        const s = { upi: 0, card: 0, cheque: 0, ccms: 0, bankTransfer: 0, total: 0 };
        for (const e of entries) {
            const amt = e.amount || 0;
            s.total += amt;
            const upper = e.advanceType?.toUpperCase();
            if (upper === "UPI") s.upi += amt;
            else if (upper === "CARD") s.card += amt;
            else if (upper === "CHEQUE") s.cheque += amt;
            else if (upper === "CCMS") s.ccms += amt;
            else if (upper === "BANK_TRANSFER") s.bankTransfer += amt;
        }
        return s;
    }, [entries]);

    // Filter
    const filtered = useMemo(() => {
        return entries.filter((e) => {
            const matchType = typeFilter === "ALL" || e.advanceType?.toUpperCase() === typeFilter;
            const q = searchQuery.toLowerCase();
            const matchSearch = !searchQuery ||
                e.remarks?.toLowerCase().includes(q) ||
                e.customerName?.toLowerCase().includes(q) ||
                e.bankName?.toLowerCase().includes(q) ||
                e.upiCompany?.companyName?.toLowerCase().includes(q) ||
                e.chequeNo?.toLowerCase().includes(q) ||
                e.ccmsNumber?.toLowerCase().includes(q);
            return matchType && matchSearch;
        });
    }, [entries, typeFilter, searchQuery]);

    const { page, setPage, totalPages, totalElements, pageSize, paginatedData } = useClientPagination(filtered);

    const resetForm = () => {
        setEditingId(null);
        setTxnType("UPI");
        setTxnAmount("");
        setTxnRemarks("");
        setSelectedUpiCompanyId(""); setNewUpiCompanyName("");
        setCardBankName(""); setCardLast4(""); setCardBatchId(""); setCardTid(""); setCardCustomerName(""); setCardCustomerPhone("");
        setChequeBankName(""); setChequeNo(""); setChequeInFavorOf(""); setChequeDate("");
        setBankName("");
        setCcmsNumber("");
        setLinkedInvoice(null);
        setLinkedStatement(null);
    };

    const handleOpenAdd = async () => {
        resetForm();
        const upi = await getUpiCompanies();
        setUpiCompanies(upi);
        setIsModalOpen(true);
    };

    const handleOpenEdit = async (entry: EAdvance) => {
        resetForm();
        const upi = await getUpiCompanies();
        setUpiCompanies(upi);

        setEditingId(entry.id!);
        setTxnType(entry.advanceType);
        setTxnAmount(String(entry.amount));
        setTxnRemarks(entry.remarks || "");
        // Populate type-specific fields
        if (entry.advanceType?.toUpperCase() === "UPI" && entry.upiCompany) {
            setSelectedUpiCompanyId(String(entry.upiCompany.id));
        }
        if (entry.advanceType?.toUpperCase() === "CARD") {
            setCardBankName(entry.bankName || "");
            setCardLast4(entry.cardLast4Digit || "");
            setCardBatchId(entry.batchId || "");
            setCardTid(entry.tid || "");
            setCardCustomerName(entry.customerName || "");
            setCardCustomerPhone(entry.customerPhone || "");
        }
        if (entry.advanceType?.toUpperCase() === "CHEQUE") {
            setChequeBankName(entry.bankName || "");
            setChequeNo(entry.chequeNo || "");
            setChequeInFavorOf(entry.inFavorOf || "");
            setChequeDate(entry.chequeDate || "");
        }
        if (entry.advanceType?.toUpperCase() === "BANK_TRANSFER") {
            setBankName(entry.bankName || "");
        }
        if (entry.advanceType?.toUpperCase() === "CCMS") {
            setCcmsNumber(entry.ccmsNumber || "");
        }
        setLinkedInvoice(entry.invoiceBill || null);
        setLinkedStatement(entry.statement || null);
        setIsModalOpen(true);
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        try {
            const payload: Partial<EAdvance> = {
                advanceType: txnType,
                amount: Number(txnAmount),
                remarks: txnRemarks || undefined,
            };

            if (txnType === "UPI") {
                if (newUpiCompanyName) {
                    const created = await createUpiCompany({ companyName: newUpiCompanyName });
                    payload.upiCompany = created;
                } else if (selectedUpiCompanyId) {
                    payload.upiCompany = { id: Number(selectedUpiCompanyId), companyName: "" };
                }
            } else if (txnType === "CARD") {
                payload.bankName = cardBankName || undefined;
                payload.cardLast4Digit = cardLast4 || undefined;
                payload.batchId = cardBatchId || undefined;
                payload.tid = cardTid || undefined;
                payload.customerName = cardCustomerName || undefined;
                payload.customerPhone = cardCustomerPhone || undefined;
            } else if (txnType === "CHEQUE") {
                payload.bankName = chequeBankName || undefined;
                payload.chequeNo = chequeNo || undefined;
                payload.inFavorOf = chequeInFavorOf || undefined;
                payload.chequeDate = chequeDate || undefined;
            } else if (txnType === "BANK_TRANSFER") {
                payload.bankName = bankName || undefined;
            } else if (txnType === "CCMS") {
                payload.ccmsNumber = ccmsNumber || undefined;
            }

            if (linkedInvoice) {
                payload.invoiceBill = { id: linkedInvoice.id };
            }
            if (linkedStatement) {
                (payload as any).statement = { id: linkedStatement.id };
            }

            if (editingId) {
                await updateEAdvance(editingId, payload);
            } else {
                await createEAdvance(payload);
            }

            setIsModalOpen(false);
            // Reload with current view mode
            if (viewMode === "dates" && fromDate && toDate) {
                loadData("dates", null, fromDate, toDate);
            } else if (activeShiftId) {
                loadData("shift", activeShiftId);
            }
        } catch (err: any) {
            alert(err.message || "Failed to save e-advance");
        }
    };

    const handleDelete = async (id: number) => {
        if (!confirm("Delete this entry?")) return;
        try {
            await deleteEAdvanceById(id);
            if (viewMode === "dates" && fromDate && toDate) {
                loadData("dates", null, fromDate, toDate);
            } else if (activeShiftId) {
                loadData("shift", activeShiftId);
            }
        } catch (err) {
            alert("Failed to delete");
        }
    };

    return (
        <div className="p-6 min-h-screen bg-background transition-colors duration-300">
            <div className="max-w-7xl mx-auto">
                {/* Header */}
                <div className="flex justify-between items-center mb-8">
                    <div>
                        <h1 className="text-4xl font-bold text-foreground tracking-tight">
                            E-<span className="text-gradient">Advances</span>
                        </h1>
                        <p className="text-muted-foreground mt-2">
                            Track electronic advance entries — Card, UPI, Cheque, CCMS, Bank Transfer.
                        </p>
                    </div>
                    <PermissionGate permission="SHIFT_CREATE">
                        <button
                            onClick={handleOpenAdd}
                            className="btn-gradient px-6 py-3 rounded-xl font-medium flex items-center gap-2 shadow-lg hover:shadow-xl transition-all"
                        >
                            <Plus className="w-5 h-5" />
                            Add Entry
                        </button>
                    </PermissionGate>
                </div>

                {/* Summary Cards */}
                <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3 mb-6">
                    <MiniStat label="UPI" value={summary.upi} icon={Smartphone} color="text-purple-500 bg-purple-500/10" />
                    <MiniStat label="Card" value={summary.card} icon={CreditCard} color="text-blue-500 bg-blue-500/10" />
                    <MiniStat label="Cheque" value={summary.cheque} icon={FileText} color="text-amber-500 bg-amber-500/10" />
                    <MiniStat label="CCMS" value={summary.ccms} icon={Receipt} color="text-pink-500 bg-pink-500/10" />
                    <MiniStat label="Bank Transfer" value={summary.bankTransfer} icon={Building2} color="text-cyan-500 bg-cyan-500/10" />
                    <GlassCard className="flex items-center gap-3 !p-3">
                        <div className="p-2 rounded-lg text-green-500 bg-green-500/10">
                            <Receipt className="w-4 h-4" />
                        </div>
                        <div>
                            <p className="text-[10px] uppercase tracking-wider text-muted-foreground">Total</p>
                            <p className="text-sm font-bold text-green-500">{formatCurrency(summary.total)}</p>
                        </div>
                    </GlassCard>
                </div>

                {/* Filter Bar */}
                <div className="mb-4 flex flex-wrap gap-3 items-end">
                    <div className="relative flex-1 min-w-[200px] max-w-md">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                        <input
                            type="text"
                            placeholder="Search remarks, customer, bank..."
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            className="w-full pl-10 pr-4 py-2.5 bg-card border border-border rounded-xl text-foreground text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                        />
                    </div>
                    <StyledSelect
                        value={typeFilter}
                        onChange={(val) => setTypeFilter(val)}
                        options={[
                            { value: "ALL", label: "All Types" },
                            ...ADVANCE_TYPES.map((t) => ({ value: t.value, label: t.label })),
                        ]}
                    />
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
                            ? activeShiftId
                                ? `Showing current shift #${activeShiftId} entries`
                                : "No active shift"
                            : `Showing entries from ${fromDate} to ${toDate}`
                        }
                    </span>
                </div>

                {/* Table */}
                {isLoading ? (
                    <div className="flex flex-col items-center justify-center py-20 text-muted-foreground">
                        <div className="w-12 h-12 border-4 border-primary/20 border-t-primary rounded-full animate-spin mb-4" />
                        <p className="animate-pulse">Loading...</p>
                    </div>
                ) : (
                    <GlassCard className="overflow-hidden border-none p-0">
                        <div className="overflow-x-auto">
                            <table className="w-full text-left border-collapse">
                                <thead>
                                    <tr className="bg-white/5 border-b border-border/50">
                                        <th className="px-5 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center w-12">#</th>
                                        <th className="px-5 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Date</th>
                                        <th className="px-5 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Type</th>
                                        <th className="px-5 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right">Amount</th>
                                        <th className="px-5 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Details</th>
                                        <th className="px-5 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Remarks</th>
                                        <th className="px-5 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Shift</th>
                                        <th className="px-5 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center">Actions</th>
                                    </tr>
                                </thead>
                                <tbody className="divide-y divide-border/30">
                                    {filtered.length === 0 ? (
                                        <tr>
                                            <td colSpan={8} className="px-5 py-12 text-center text-muted-foreground">
                                                No e-advance entries found
                                            </td>
                                        </tr>
                                    ) : (
                                        paginatedData.map((entry, idx) => {
                                            const meta = getTypeMeta(entry.advanceType);
                                            const Icon = meta.icon;
                                            return (
                                                <tr key={entry.id} className="hover:bg-white/5 transition-colors group">
                                                    <td className="px-5 py-3 text-xs font-mono text-muted-foreground text-center">{page * pageSize + idx + 1}</td>
                                                    <td className="px-5 py-3 text-xs text-muted-foreground">{formatDateTime(entry.transactionDate)}</td>
                                                    <td className="px-5 py-3">
                                                        <div className="flex items-center gap-2">
                                                            <div className={`p-1.5 rounded-lg ${meta.color}`}>
                                                                <Icon className="w-3.5 h-3.5" />
                                                            </div>
                                                            <span className="text-sm font-medium text-foreground">{meta.label}</span>
                                                        </div>
                                                    </td>
                                                    <td className="px-5 py-3 text-right">
                                                        <span className="text-sm font-bold text-green-500">{formatCurrency(entry.amount)}</span>
                                                    </td>
                                                    <td className="px-5 py-3">
                                                        <EntryDetails entry={entry} />
                                                    </td>
                                                    <td className="px-5 py-3 text-xs text-muted-foreground max-w-[200px] truncate">
                                                        {entry.remarks || "-"}
                                                    </td>
                                                    <td className="px-5 py-3 text-xs text-muted-foreground">
                                                        {entry.shiftId ? `#${entry.shiftId}` : "-"}
                                                    </td>
                                                    <td className="px-5 py-3 text-center">
                                                        <div className="flex items-center justify-center gap-1 opacity-100 md:opacity-0 group-hover:opacity-100 transition-all">
                                                            <PermissionGate permission="SHIFT_UPDATE">
                                                                <button
                                                                    onClick={() => handleOpenEdit(entry)}
                                                                    className="p-1.5 rounded-lg hover:bg-blue-500/10 text-muted-foreground hover:text-blue-500 transition-all"
                                                                >
                                                                    <Pencil className="w-3.5 h-3.5" />
                                                                </button>
                                                            </PermissionGate>
                                                            <PermissionGate permission="SHIFT_DELETE">
                                                                <button
                                                                    onClick={() => entry.id && handleDelete(entry.id)}
                                                                    className="p-1.5 rounded-lg hover:bg-red-500/10 text-muted-foreground hover:text-red-500 transition-all"
                                                                >
                                                                    <Trash2 className="w-3.5 h-3.5" />
                                                                </button>
                                                            </PermissionGate>
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
                )}
            </div>

            {/* Add/Edit Modal */}
            <Modal
                isOpen={isModalOpen}
                onClose={() => setIsModalOpen(false)}
                title={editingId ? "Edit E-Advance" : "Add E-Advance"}
            >
                <form onSubmit={handleSubmit} className="space-y-4">
                    {/* Type Selector */}
                    <div>
                        <label className="block text-sm font-medium text-foreground mb-2">Type</label>
                        <div className="grid grid-cols-5 gap-2">
                            {ADVANCE_TYPES.map((t) => {
                                const TIcon = t.icon;
                                return (
                                    <button
                                        key={t.value}
                                        type="button"
                                        onClick={() => setTxnType(t.value)}
                                        className={`flex flex-col items-center gap-1.5 p-3 rounded-xl border transition-all text-xs font-medium ${
                                            txnType === t.value
                                                ? 'border-primary bg-primary/10 text-primary'
                                                : 'border-border bg-card text-muted-foreground hover:border-primary/30'
                                        }`}
                                    >
                                        <TIcon className="w-4 h-4" />
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
                            min="0"
                            required
                            value={txnAmount}
                            onChange={(e) => setTxnAmount(e.target.value)}
                            className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground font-mono focus:outline-none focus:ring-2 focus:ring-primary/50"
                            placeholder="0.00"
                        />
                    </div>

                    {/* Type-specific fields */}
                    {txnType === "UPI" && (
                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5">UPI Company</label>
                            <StyledSelect
                                value={selectedUpiCompanyId}
                                onChange={(val) => { setSelectedUpiCompanyId(val); setNewUpiCompanyName(""); }}
                                options={[
                                    { value: "", label: "Select or add new..." },
                                    ...upiCompanies.map((u) => ({ value: String(u.id), label: u.companyName })),
                                ]}
                                className="mb-2"
                            />
                            {!selectedUpiCompanyId && (
                                <input
                                    type="text"
                                    value={newUpiCompanyName}
                                    onChange={(e) => setNewUpiCompanyName(e.target.value)}
                                    className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                                    placeholder="New UPI company name..."
                                />
                            )}
                        </div>
                    )}

                    {txnType === "CARD" && (
                        <div className="grid grid-cols-2 gap-3">
                            <InputField label="Bank Name" value={cardBankName} onChange={setCardBankName} placeholder="e.g. HDFC" />
                            <InputField label="Card Last 4 Digits" value={cardLast4} onChange={setCardLast4} placeholder="1234" />
                            <InputField label="Batch ID" value={cardBatchId} onChange={setCardBatchId} placeholder="Batch ID" />
                            <InputField label="Terminal ID (TID)" value={cardTid} onChange={setCardTid} placeholder="TID" />
                            <InputField label="Customer Name" value={cardCustomerName} onChange={setCardCustomerName} placeholder="Name" />
                            <InputField label="Customer Phone" value={cardCustomerPhone} onChange={setCardCustomerPhone} placeholder="Phone" />
                        </div>
                    )}

                    {txnType === "CHEQUE" && (
                        <div className="grid grid-cols-2 gap-3">
                            <InputField label="Bank Name" value={chequeBankName} onChange={setChequeBankName} placeholder="e.g. SBI" />
                            <InputField label="Cheque No" value={chequeNo} onChange={setChequeNo} placeholder="Cheque number" />
                            <InputField label="In Favor Of" value={chequeInFavorOf} onChange={setChequeInFavorOf} placeholder="Payee name" />
                            <div>
                                <label className="block text-xs font-medium text-foreground mb-1">Cheque Date</label>
                                <input
                                    type="date"
                                    value={chequeDate}
                                    onChange={(e) => setChequeDate(e.target.value)}
                                    className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                                />
                            </div>
                        </div>
                    )}

                    {txnType === "BANK_TRANSFER" && (
                        <InputField label="Bank Name" value={bankName} onChange={setBankName} placeholder="e.g. ICICI" />
                    )}

                    {txnType === "CCMS" && (
                        <InputField label="CCMS Number" value={ccmsNumber} onChange={setCcmsNumber} placeholder="CCMS ref number" />
                    )}

                    {/* Link to Invoice / Statement */}
                    <div>
                        <label className="block text-sm font-medium text-foreground mb-1.5">Link to Invoice (Optional)</label>
                        <InvoiceAutocomplete
                            value={linkedInvoice}
                            onChange={setLinkedInvoice}
                            placeholder="Search by bill #..."
                        />
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-foreground mb-1.5">Link to Statement (Optional)</label>
                        <StatementAutocomplete
                            value={linkedStatement}
                            onChange={setLinkedStatement}
                            placeholder="Search by statement #..."
                        />
                    </div>

                    {/* Remarks */}
                    <div>
                        <label className="block text-sm font-medium text-foreground mb-1.5">Remarks</label>
                        <input
                            type="text"
                            value={txnRemarks}
                            onChange={(e) => setTxnRemarks(e.target.value)}
                            className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                            placeholder="Optional notes..."
                        />
                    </div>

                    <div className="flex justify-end gap-3 pt-6 border-t border-border mt-6">
                        <button
                            type="button"
                            onClick={() => setIsModalOpen(false)}
                            className="px-6 py-2.5 rounded-xl font-medium text-foreground bg-black/5 dark:bg-white/5 hover:bg-black/10 dark:hover:bg-white/10 transition-colors"
                        >
                            Cancel
                        </button>
                        <button
                            type="submit"
                            className="btn-gradient px-8 py-2.5 rounded-xl font-medium shadow-lg hover:shadow-xl transition-all"
                        >
                            {editingId ? "Save Changes" : "Add Entry"}
                        </button>
                    </div>
                </form>
            </Modal>
        </div>
    );
}

// --- Helper Components ---

function MiniStat({ label, value, icon: Icon, color }: { label: string; value: number; icon: any; color: string }) {
    return (
        <GlassCard className="flex items-center gap-3 !p-3">
            <div className={`p-2 rounded-lg ${color}`}>
                <Icon className="w-4 h-4" />
            </div>
            <div>
                <p className="text-[10px] uppercase tracking-wider text-muted-foreground">{label}</p>
                <p className="text-sm font-bold text-foreground">{formatCurrency(value)}</p>
            </div>
        </GlassCard>
    );
}

function InputField({ label, value, onChange, placeholder }: { label: string; value: string; onChange: (v: string) => void; placeholder?: string }) {
    return (
        <div>
            <label className="block text-xs font-medium text-foreground mb-1">{label}</label>
            <input
                type="text"
                value={value}
                onChange={(e) => onChange(e.target.value)}
                className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                placeholder={placeholder}
            />
        </div>
    );
}

function EntryDetails({ entry }: { entry: EAdvance }) {
    const details: string[] = [];
    if (entry.upiCompany?.companyName) details.push(entry.upiCompany.companyName);
    if (entry.bankName) details.push(entry.bankName);
    if (entry.cardLast4Digit) details.push(`****${entry.cardLast4Digit}`);
    if (entry.customerName) details.push(entry.customerName);
    if (entry.chequeNo) details.push(`Chq: ${entry.chequeNo}`);
    if (entry.ccmsNumber) details.push(`CCMS: ${entry.ccmsNumber}`);

    // Source reference
    if (entry.invoiceBill) {
        const inv = entry.invoiceBill;
        details.push(`Bill: ${inv.billNo || '#' + inv.id}${inv.customer?.name ? ' - ' + inv.customer.name : ''}`);
    }
    if (entry.statement) {
        const stmt = entry.statement;
        details.push(`Stmt: ${stmt.statementNo || '#' + stmt.id}`);
    }
    if (entry.payment) {
        const pay = entry.payment;
        details.push(`Payment: #${pay.id}${pay.customer?.name ? ' - ' + pay.customer.name : ''}`);
    }

    if (details.length === 0) return <span className="text-xs text-muted-foreground">-</span>;
    return (
        <div className="flex flex-wrap gap-1.5">
            {details.map((d, i) => (
                <span key={i} className={`text-[10px] px-2 py-0.5 rounded-full border border-border text-muted-foreground ${
                    d.startsWith('Bill:') || d.startsWith('Payment:') || d.startsWith('Stmt:') ? 'bg-primary/5 border-primary/20 text-primary' : 'bg-white/5'
                }`}>
                    {d}
                </span>
            ))}
        </div>
    );
}
