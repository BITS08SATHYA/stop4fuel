"use client";

import { useEffect, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import { GlassCard } from "@/components/ui/glass-card";
import { Modal } from "@/components/ui/modal";
import { InvoiceAutocomplete } from "@/components/ui/invoice-autocomplete";
import {
    getActiveShift,
    openShift,

    getEAdvancesByShift,
    getEAdvanceSummary,
    createEAdvance,
    deleteEAdvance,
    getExpensesByShift,
    getExpenseShiftTotal,
    createExpense,
    deleteExpense,
    getUpiCompanies,
    createUpiCompany,
    getExpenseTypes,
    createExpenseType,
    Shift,
    EAdvance,
    ShiftExpense,
    EAdvanceSummary,
    UpiCompany,
    ExpenseType,
} from "@/lib/api/station";
import {
    Clock,
    Play,
    Square,
    Plus,
    Trash2,
    Banknote,
    CreditCard,
    Smartphone,
    Building2,
    FileText,
    Receipt,
    Wallet,
    Search,
} from "lucide-react";
import { TablePagination, useClientPagination } from "@/components/ui/table-pagination";
import { PermissionGate } from "@/components/permission-gate";

// Unified row for display — merges EAdvance and Expense
interface ShiftTxnRow {
    id: number;
    source: "EADVANCE" | "EXPENSE";
    type: string;
    amount: number;
    date?: string;
    remarks?: string;
    // EAdvance detail fields
    upiCompany?: UpiCompany;
    bankName?: string;
    cardLast4Digit?: string;
    customerName?: string;
    chequeNo?: string;
    ccmsNumber?: string;
    // Expense detail fields
    description?: string;
    expenseType?: ExpenseType;
}

const TXN_TYPES = [
    { value: "UPI", label: "UPI", icon: Smartphone, color: "text-purple-500 bg-purple-500/10" },
    { value: "CARD", label: "Card", icon: CreditCard, color: "text-blue-500 bg-blue-500/10" },
    { value: "CHEQUE", label: "Cheque", icon: FileText, color: "text-amber-500 bg-amber-500/10" },
    { value: "BANK_TRANSFER", label: "Bank Transfer", icon: Building2, color: "text-cyan-500 bg-cyan-500/10" },
    { value: "CCMS", label: "CCMS", icon: Receipt, color: "text-pink-500 bg-pink-500/10" },
    { value: "EXPENSE", label: "Expense", icon: Wallet, color: "text-red-500 bg-red-500/10" },
];

function getTxnMeta(type: string) {
    return TXN_TYPES.find((t) => t.value === type) || { value: type, label: type, icon: Banknote, color: "text-gray-500 bg-gray-500/10" };
}

function formatDateTime(dt?: string) {
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

interface ShiftSummary {
    upi: number;
    card: number;
    cheque: number;
    ccms: number;
    bankTransfer: number;
    eAdvanceTotal: number;
    expense: number;
}

export default function ShiftsPage() {
    const router = useRouter();
    const [activeShift, setActiveShift] = useState<Shift | null>(null);
    const [transactions, setTransactions] = useState<ShiftTxnRow[]>([]);
    const [summary, setSummary] = useState<ShiftSummary | null>(null);
    const [isLoading, setIsLoading] = useState(true);

    // Add transaction modal
    const [isAddModalOpen, setIsAddModalOpen] = useState(false);
    const [txnType, setTxnType] = useState("UPI");
    const [txnAmount, setTxnAmount] = useState("");
    const [txnRemarks, setTxnRemarks] = useState("");
    // UPI fields
    const [upiCompanies, setUpiCompanies] = useState<UpiCompany[]>([]);
    const [selectedUpiCompanyId, setSelectedUpiCompanyId] = useState("");
    const [newUpiCompanyName, setNewUpiCompanyName] = useState("");
    // Card fields
    const [cardBatchId, setCardBatchId] = useState("");
    const [cardTid, setCardTid] = useState("");
    const [cardCustomerName, setCardCustomerName] = useState("");
    const [cardCustomerPhone, setCardCustomerPhone] = useState("");
    const [cardBankName, setCardBankName] = useState("");
    const [cardLast4, setCardLast4] = useState("");
    // Cheque fields
    const [chequeBankName, setChequeBankName] = useState("");
    const [chequeInFavorOf, setChequeInFavorOf] = useState("");
    const [chequeNo, setChequeNo] = useState("");
    const [chequeDate, setChequeDate] = useState("");
    // Bank fields
    const [bankName, setBankName] = useState("");
    // CCMS fields
    const [ccmsNumber, setCcmsNumber] = useState("");
    // Expense fields
    const [expenseDescription, setExpenseDescription] = useState("");
    const [expenseTypes, setExpenseTypes] = useState<ExpenseType[]>([]);
    const [selectedExpenseTypeId, setSelectedExpenseTypeId] = useState("");
    const [newExpenseTypeName, setNewExpenseTypeName] = useState("");
    // Invoice linking
    const [linkedInvoice, setLinkedInvoice] = useState<any>(null);

    // Filter
    const [typeFilter, setTypeFilter] = useState("ALL");
    const [searchQuery, setSearchQuery] = useState("");

    const viewingShift = activeShift;

    const loadTransactions = async (shiftId: number) => {
        const [eAdvances, expenses, eAdvSummary, expenseTotal] = await Promise.all([
            getEAdvancesByShift(shiftId),
            getExpensesByShift(shiftId),
            getEAdvanceSummary(shiftId),
            getExpenseShiftTotal(shiftId),
        ]);

        // Merge into unified rows
        const rows: ShiftTxnRow[] = [
            ...eAdvances.map((ea): ShiftTxnRow => ({
                id: ea.id!,
                source: "EADVANCE",
                type: ea.advanceType,
                amount: ea.amount,
                date: ea.transactionDate,
                remarks: ea.remarks,
                upiCompany: ea.upiCompany,
                bankName: ea.bankName,
                cardLast4Digit: ea.cardLast4Digit,
                customerName: ea.customerName,
                chequeNo: ea.chequeNo,
                ccmsNumber: ea.ccmsNumber,
            })),
            ...expenses.map((exp): ShiftTxnRow => ({
                id: exp.id!,
                source: "EXPENSE",
                type: "EXPENSE",
                amount: exp.amount,
                date: exp.expenseDate,
                remarks: exp.remarks,
                description: exp.description,
                expenseType: exp.expenseType,
            })),
        ].sort((a, b) => new Date(b.date || 0).getTime() - new Date(a.date || 0).getTime());

        setTransactions(rows);
        setSummary({
            upi: eAdvSummary.upi || 0,
            card: eAdvSummary.card || 0,
            cheque: eAdvSummary.cheque || 0,
            ccms: eAdvSummary.ccms || 0,
            bankTransfer: eAdvSummary.bank_transfer || 0,
            eAdvanceTotal: eAdvSummary.total || 0,
            expense: expenseTotal || 0,
        });
    };

    const loadData = useCallback(async () => {
        setIsLoading(true);
        try {
            const active = await getActiveShift();
            setActiveShift(active);

            const shiftToView = active;
            if (shiftToView) {
                await loadTransactions(shiftToView.id);
            } else {
                setTransactions([]);
                setSummary(null);
            }
        } catch (err) {
            console.error("Failed to load shift data", err);
        } finally {
            setIsLoading(false);
        }
    }, []);

    useEffect(() => {
        loadData();
    }, [loadData]);

    const handleOpenShift = async () => {
        try {
            const shift = await openShift({});
            setActiveShift(shift);

            await loadTransactions(shift.id);
        } catch (err: any) {
            alert(err.message || "Failed to open shift");
        }
    };

    const handleCloseShift = () => {
        if (!activeShift) return;
        router.push(`/operations/shifts/close/${activeShift.id}`);
    };

    const resetForm = () => {
        setTxnType("UPI");
        setTxnAmount("");
        setTxnRemarks("");
        setSelectedUpiCompanyId("");
        setNewUpiCompanyName("");
        setCardBatchId(""); setCardTid(""); setCardCustomerName(""); setCardCustomerPhone(""); setCardBankName(""); setCardLast4("");
        setChequeBankName(""); setChequeInFavorOf(""); setChequeNo(""); setChequeDate("");
        setBankName("");
        setCcmsNumber("");
        setExpenseDescription(""); setSelectedExpenseTypeId(""); setNewExpenseTypeName("");
        setLinkedInvoice(null);
    };

    const handleOpenAddModal = async () => {
        resetForm();
        const [upi, exp] = await Promise.all([getUpiCompanies(), getExpenseTypes()]);
        setUpiCompanies(upi);
        setExpenseTypes(exp);
        setIsAddModalOpen(true);
    };

    const handleAddTransaction = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!viewingShift) return;

        try {
            if (txnType === "EXPENSE") {
                // Create Expense
                const payload: Partial<ShiftExpense> = {
                    shiftId: viewingShift.id,
                    amount: Number(txnAmount),
                    description: expenseDescription || undefined,
                    remarks: txnRemarks || undefined,
                };
                if (newExpenseTypeName) {
                    const created = await createExpenseType({ name: newExpenseTypeName });
                    payload.expenseType = created;
                } else if (selectedExpenseTypeId) {
                    payload.expenseType = { id: Number(selectedExpenseTypeId), name: "" };
                }
                await createExpense(payload);
            } else {
                // Create EAdvance
                const payload: Partial<EAdvance> = {
                    advanceType: txnType,
                    shiftId: viewingShift.id,
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
                    payload.batchId = cardBatchId || undefined;
                    payload.tid = cardTid || undefined;
                    payload.customerName = cardCustomerName || undefined;
                    payload.customerPhone = cardCustomerPhone || undefined;
                    payload.bankName = cardBankName || undefined;
                    payload.cardLast4Digit = cardLast4 || undefined;
                } else if (txnType === "CHEQUE") {
                    payload.bankName = chequeBankName || undefined;
                    payload.inFavorOf = chequeInFavorOf || undefined;
                    payload.chequeNo = chequeNo || undefined;
                    payload.chequeDate = chequeDate || undefined;
                } else if (txnType === "BANK_TRANSFER") {
                    payload.bankName = bankName || undefined;
                } else if (txnType === "CCMS") {
                    payload.ccmsNumber = ccmsNumber || undefined;
                }

                if (linkedInvoice) {
                    payload.invoiceBill = { id: linkedInvoice.id };
                }

                await createEAdvance(payload);
            }

            setIsAddModalOpen(false);
            await loadTransactions(viewingShift.id);
        } catch (err: any) {
            alert(err.message || "Failed to add transaction");
        }
    };

    const handleDeleteTransaction = async (row: ShiftTxnRow) => {
        if (!confirm("Delete this transaction?")) return;
        try {
            if (row.source === "EADVANCE") {
                await deleteEAdvance(row.id);
            } else {
                await deleteExpense(row.id);
            }
            if (viewingShift) await loadTransactions(viewingShift.id);
        } catch (err) {
            alert("Failed to delete transaction");
        }
    };

    const filtered = transactions.filter((t) => {
        const matchType = typeFilter === "ALL" || t.type === typeFilter;
        const q = searchQuery.toLowerCase();
        const matchSearch = !searchQuery ||
            t.remarks?.toLowerCase().includes(q) ||
            t.customerName?.toLowerCase().includes(q) ||
            t.bankName?.toLowerCase().includes(q) ||
            t.upiCompany?.companyName?.toLowerCase().includes(q) ||
            t.description?.toLowerCase().includes(q);
        return matchType && matchSearch;
    });

    const { page: txnPage, setPage: setTxnPage, totalPages: txnTotalPages, totalElements: txnTotalElements, pageSize: txnPageSize, paginatedData: pagedTxns } = useClientPagination(filtered);

    const isViewingActive = viewingShift?.status === "OPEN";

    return (
        <div className="p-6 h-screen overflow-hidden bg-background transition-colors duration-300">
            <div className="max-w-7xl mx-auto">
                {/* Header */}
                <div className="flex justify-between items-center mb-8">
                    <div>
                        <h1 className="text-4xl font-bold text-foreground tracking-tight">
                            Shift <span className="text-gradient">Register</span>
                        </h1>
                        <p className="text-muted-foreground mt-2">
                            Manage shifts and record advance entries during a shift.
                        </p>
                    </div>
                    <div className="flex items-center gap-3">
                        {activeShift ? (
                            <>
                                <div className="flex items-center gap-2 px-4 py-2 bg-green-500/10 border border-green-500/20 rounded-xl">
                                    <div className="w-2 h-2 rounded-full bg-green-500 animate-pulse" />
                                    <span className="text-sm font-medium text-green-500">
                                        Shift #{activeShift.id} - Active
                                    </span>
                                </div>
                                <PermissionGate permission="SHIFT_MANAGE">
                                    <button
                                        onClick={handleCloseShift}
                                        className="px-5 py-2.5 rounded-xl font-medium flex items-center gap-2 bg-red-500/10 text-red-500 border border-red-500/20 hover:bg-red-500/20 transition-colors"
                                    >
                                        <Square className="w-4 h-4" />
                                        Close Shift
                                    </button>
                                </PermissionGate>
                            </>
                        ) : (
                            <PermissionGate permission="SHIFT_MANAGE">
                                <button
                                    onClick={handleOpenShift}
                                    className="btn-gradient px-6 py-3 rounded-xl font-medium flex items-center gap-2 shadow-lg hover:shadow-xl transition-all"
                                >
                                    <Play className="w-5 h-5" />
                                    Open New Shift
                                </button>
                            </PermissionGate>
                        )}
                    </div>
                </div>

                {isLoading ? (
                    <div className="flex flex-col items-center justify-center py-20 text-muted-foreground">
                        <div className="w-12 h-12 border-4 border-primary/20 border-t-primary rounded-full animate-spin mb-4" />
                        <p className="animate-pulse">Loading shift data...</p>
                    </div>
                ) : !viewingShift ? (
                    <div className="text-center py-20 bg-black/5 dark:bg-white/5 rounded-2xl border border-dashed border-border">
                        <Clock className="w-16 h-16 mx-auto text-muted-foreground mb-4 opacity-50" />
                        <h3 className="text-xl font-semibold text-foreground mb-2">No Active Shift</h3>
                        <p className="text-muted-foreground mb-6 max-w-md mx-auto">
                            Open a new shift to start recording transactions.
                        </p>
                        <PermissionGate permission="SHIFT_MANAGE">
                            <button
                                onClick={handleOpenShift}
                                className="bg-primary/10 text-primary hover:bg-primary/20 px-6 py-2 rounded-xl font-medium transition-colors"
                            >
                                Open Shift
                            </button>
                        </PermissionGate>
                    </div>
                ) : (
                    <>
                        {/* Shift Info & Summary */}
                        <div className="grid grid-cols-1 lg:grid-cols-4 gap-4 mb-6">
                            <GlassCard className="lg:col-span-1">
                                <div className="flex items-center gap-3 mb-3">
                                    <div className={`p-2.5 rounded-xl ${isViewingActive ? 'bg-green-500/10 text-green-500' : 'bg-gray-500/10 text-gray-500'}`}>
                                        <Clock className="w-5 h-5" />
                                    </div>
                                    <div>
                                        <p className="text-xs text-muted-foreground">Shift #{viewingShift.id}</p>
                                        <p className={`text-sm font-bold ${isViewingActive ? 'text-green-500' : 'text-muted-foreground'}`}>
                                            {viewingShift.status}
                                        </p>
                                    </div>
                                </div>
                                <div className="space-y-1.5 text-xs text-muted-foreground">
                                    <p>Start: <span className="text-foreground font-medium">{formatDateTime(viewingShift.startTime)}</span></p>
                                    {viewingShift.endTime && (
                                        <p>End: <span className="text-foreground font-medium">{formatDateTime(viewingShift.endTime)}</span></p>
                                    )}
                                </div>
                            </GlassCard>

                            {summary && (
                                <>
                                    <SummaryCard label="E-Advance Total" value={summary.eAdvanceTotal} color="text-blue-500" />
                                    <SummaryCard label="Expenses" value={summary.expense} color="text-red-500" />
                                    <SummaryCard label="Net Advance" value={summary.eAdvanceTotal - summary.expense} color="text-green-500" />
                                </>
                            )}
                        </div>

                        {/* Summary Breakdown */}
                        {summary && (
                            <div className="grid grid-cols-2 sm:grid-cols-5 gap-3 mb-6">
                                <MiniStat label="UPI" value={summary.upi} icon={Smartphone} color="text-purple-500 bg-purple-500/10" />
                                <MiniStat label="Card" value={summary.card} icon={CreditCard} color="text-blue-500 bg-blue-500/10" />
                                <MiniStat label="Cheque" value={summary.cheque} icon={FileText} color="text-amber-500 bg-amber-500/10" />
                                <MiniStat label="CCMS" value={summary.ccms} icon={Receipt} color="text-pink-500 bg-pink-500/10" />
                                <MiniStat label="Expense" value={summary.expense} icon={Wallet} color="text-red-500 bg-red-500/10" />
                            </div>
                        )}

                        {/* Filter Bar + Add Button */}
                        <div className="mb-4 flex flex-wrap gap-3 items-center">
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
                            <select
                                value={typeFilter}
                                onChange={(e) => setTypeFilter(e.target.value)}
                                className="px-4 py-2.5 bg-card border border-border rounded-xl text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
                            >
                                <option value="ALL">All Types</option>
                                {TXN_TYPES.map((t) => (
                                    <option key={t.value} value={t.value}>{t.label}</option>
                                ))}
                            </select>
                            {isViewingActive && (
                                <PermissionGate permission="SHIFT_MANAGE">
                                    <button
                                        onClick={handleOpenAddModal}
                                        className="btn-gradient px-5 py-2.5 rounded-xl font-medium flex items-center gap-2 shadow-lg hover:shadow-xl transition-all"
                                    >
                                        <Plus className="w-4 h-4" />
                                        Add Entry
                                    </button>
                                </PermissionGate>
                            )}
                        </div>

                        {/* Transactions Table */}
                        <GlassCard className="overflow-hidden border-none p-0 mb-6">
                            <div className="overflow-x-auto">
                                <table className="w-full text-left border-collapse">
                                    <thead>
                                        <tr className="bg-white/5 border-b border-border/50">
                                            <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center w-12">#</th>
                                            <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Type</th>
                                            <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right">Amount</th>
                                            <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Details</th>
                                            <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Remarks</th>
                                            <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Date</th>
                                            {isViewingActive && (
                                                <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center w-20">Actions</th>
                                            )}
                                        </tr>
                                    </thead>
                                    <tbody className="divide-y divide-border/30">
                                        {filtered.length === 0 ? (
                                            <tr>
                                                <td colSpan={isViewingActive ? 7 : 6} className="px-6 py-12 text-center text-muted-foreground">
                                                    No entries found
                                                </td>
                                            </tr>
                                        ) : (
                                            pagedTxns.map((txn, idx) => {
                                                const meta = getTxnMeta(txn.type);
                                                const Icon = meta.icon;
                                                return (
                                                    <tr key={`${txn.source}-${txn.id}`} className="hover:bg-white/5 transition-colors group">
                                                        <td className="px-6 py-3 text-xs font-mono text-muted-foreground text-center">{txnPage * txnPageSize + idx + 1}</td>
                                                        <td className="px-6 py-3">
                                                            <div className="flex items-center gap-2">
                                                                <div className={`p-1.5 rounded-lg ${meta.color}`}>
                                                                    <Icon className="w-3.5 h-3.5" />
                                                                </div>
                                                                <span className="text-sm font-medium text-foreground">{meta.label}</span>
                                                            </div>
                                                        </td>
                                                        <td className="px-6 py-3 text-right">
                                                            <span className={`text-sm font-bold ${txn.source === 'EXPENSE' ? 'text-red-500' : 'text-green-500'}`}>
                                                                {txn.source === 'EXPENSE' ? '-' : '+'}
                                                                {formatCurrency(txn.amount)}
                                                            </span>
                                                        </td>
                                                        <td className="px-6 py-3">
                                                            <TxnDetails txn={txn} />
                                                        </td>
                                                        <td className="px-6 py-3 text-xs text-muted-foreground max-w-[200px] truncate">
                                                            {txn.remarks || "-"}
                                                        </td>
                                                        <td className="px-6 py-3 text-xs text-muted-foreground">
                                                            {formatDateTime(txn.date)}
                                                        </td>
                                                        {isViewingActive && (
                                                            <td className="px-6 py-3 text-center">
                                                                <PermissionGate permission="SHIFT_MANAGE">
                                                                    <button
                                                                        onClick={() => handleDeleteTransaction(txn)}
                                                                        className="p-1.5 rounded-lg hover:bg-red-500/10 text-muted-foreground hover:text-red-500 opacity-100 md:opacity-0 group-hover:opacity-100 transition-all"
                                                                    >
                                                                        <Trash2 className="w-3.5 h-3.5" />
                                                                    </button>
                                                                </PermissionGate>
                                                            </td>
                                                        )}
                                                    </tr>
                                                );
                                            })
                                        )}
                                    </tbody>
                                </table>
                            </div>
                            <TablePagination
                                page={txnPage}
                                totalPages={txnTotalPages}
                                totalElements={txnTotalElements}
                                pageSize={txnPageSize}
                                onPageChange={setTxnPage}
                            />
                        </GlassCard>
                    </>
                )}

                {/* Link to Shift History */}
                <div className="mt-6 text-center">
                    <button
                        onClick={() => router.push("/operations/shifts/history")}
                        className="text-sm text-primary hover:text-primary/80 transition-colors font-medium"
                    >
                        View All Past Shifts →
                    </button>
                </div>
            </div>

            {/* Add Transaction Modal */}
            <Modal
                isOpen={isAddModalOpen}
                onClose={() => setIsAddModalOpen(false)}
                title="Add Shift Entry"
            >
                <form onSubmit={handleAddTransaction} className="space-y-4">
                    {/* Transaction Type Selector */}
                    <div>
                        <label className="block text-sm font-medium text-foreground mb-2">Entry Type</label>
                        <div className="grid grid-cols-3 gap-2">
                            {TXN_TYPES.map((t) => {
                                const Icon = t.icon;
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
                            <select
                                value={selectedUpiCompanyId}
                                onChange={(e) => { setSelectedUpiCompanyId(e.target.value); setNewUpiCompanyName(""); }}
                                className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50 mb-2"
                            >
                                <option value="">Select or add new...</option>
                                {upiCompanies.map((u) => (
                                    <option key={u.id} value={u.id}>{u.companyName}</option>
                                ))}
                            </select>
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

                    {txnType === "EXPENSE" && (
                        <div className="space-y-3">
                            <InputField label="Description" value={expenseDescription} onChange={setExpenseDescription} placeholder="What was the expense for?" />
                            <div>
                                <label className="block text-xs font-medium text-foreground mb-1">Expense Type</label>
                                <select
                                    value={selectedExpenseTypeId}
                                    onChange={(e) => { setSelectedExpenseTypeId(e.target.value); setNewExpenseTypeName(""); }}
                                    className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50 mb-2"
                                >
                                    <option value="">Select or add new...</option>
                                    {expenseTypes.map((et) => (
                                        <option key={et.id} value={et.id}>{et.name}</option>
                                    ))}
                                </select>
                                {!selectedExpenseTypeId && (
                                    <input
                                        type="text"
                                        value={newExpenseTypeName}
                                        onChange={(e) => setNewExpenseTypeName(e.target.value)}
                                        className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                                        placeholder="New expense type..."
                                    />
                                )}
                            </div>
                        </div>
                    )}

                    {/* Link to Invoice (only for E-Advances, not Expenses) */}
                    {txnType !== "EXPENSE" && (
                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5">Link to Invoice (Optional)</label>
                            <InvoiceAutocomplete
                                value={linkedInvoice}
                                onChange={setLinkedInvoice}
                                placeholder="Search by bill #..."
                            />
                        </div>
                    )}

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
                            onClick={() => setIsAddModalOpen(false)}
                            className="px-6 py-2.5 rounded-xl font-medium text-foreground bg-black/5 dark:bg-white/5 hover:bg-black/10 dark:hover:bg-white/10 transition-colors"
                        >
                            Cancel
                        </button>
                        <button
                            type="submit"
                            className="btn-gradient px-8 py-2.5 rounded-xl font-medium shadow-lg hover:shadow-xl transition-all"
                        >
                            Add Entry
                        </button>
                    </div>
                </form>
            </Modal>
        </div>
    );
}

// --- Helper Components ---

function SummaryCard({ label, value, color }: { label: string; value: number; color: string }) {
    return (
        <GlassCard>
            <p className="text-xs text-muted-foreground mb-1">{label}</p>
            <p className={`text-2xl font-bold ${color}`}>{formatCurrency(value)}</p>
        </GlassCard>
    );
}

function MiniStat({ label, value, icon: Icon, color }: { label: string; value: number; icon: any; color: string }) {
    return (
        <div className={`flex items-center gap-3 p-3 rounded-xl border border-border bg-card`}>
            <div className={`p-2 rounded-lg ${color}`}>
                <Icon className="w-4 h-4" />
            </div>
            <div>
                <p className="text-[10px] uppercase tracking-wider text-muted-foreground">{label}</p>
                <p className="text-sm font-bold text-foreground">{formatCurrency(value)}</p>
            </div>
        </div>
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

function TxnDetails({ txn }: { txn: ShiftTxnRow }) {
    const details: string[] = [];
    if (txn.upiCompany?.companyName) details.push(txn.upiCompany.companyName);
    if (txn.bankName) details.push(txn.bankName);
    if (txn.cardLast4Digit) details.push(`****${txn.cardLast4Digit}`);
    if (txn.customerName) details.push(txn.customerName);
    if (txn.chequeNo) details.push(`Chq: ${txn.chequeNo}`);
    if (txn.ccmsNumber) details.push(`CCMS: ${txn.ccmsNumber}`);
    if (txn.description) details.push(txn.description);
    if (txn.expenseType?.name) details.push(`[${txn.expenseType.name}]`);

    if (details.length === 0) return <span className="text-xs text-muted-foreground">-</span>;
    return (
        <div className="flex flex-wrap gap-1.5">
            {details.map((d, i) => (
                <span key={i} className="text-[10px] px-2 py-0.5 rounded-full bg-white/5 border border-border text-muted-foreground">
                    {d}
                </span>
            ))}
        </div>
    );
}
