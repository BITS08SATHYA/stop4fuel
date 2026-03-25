"use client";

import { useEffect, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import { GlassCard } from "@/components/ui/glass-card";
import { Modal } from "@/components/ui/modal";
import {
    getActiveShift,
    getShifts,
    openShift,
    closeShift,
    getShiftTransactions,
    getShiftTransactionSummary,
    createShiftTransaction,
    deleteShiftTransaction,
    getUpiCompanies,
    createUpiCompany,
    getExpenseTypes,
    createExpenseType,
    Shift,
    ShiftTransaction,
    ShiftTransactionSummary,
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
    ChevronDown,
    ChevronUp,
    AlertCircle,
    Moon,
} from "lucide-react";
import { TablePagination, useClientPagination } from "@/components/ui/table-pagination";
import { PermissionGate } from "@/components/permission-gate";

const TXN_TYPES = [
    { value: "CASH", label: "Cash", icon: Banknote, color: "text-green-500 bg-green-500/10" },
    { value: "NIGHT_CASH", label: "Night Cash", icon: Moon, color: "text-indigo-500 bg-indigo-500/10" },
    { value: "UPI", label: "UPI", icon: Smartphone, color: "text-purple-500 bg-purple-500/10" },
    { value: "CARD", label: "Card", icon: CreditCard, color: "text-blue-500 bg-blue-500/10" },
    { value: "CHEQUE", label: "Cheque", icon: FileText, color: "text-amber-500 bg-amber-500/10" },
    { value: "BANK", label: "Bank Transfer", icon: Building2, color: "text-cyan-500 bg-cyan-500/10" },
    { value: "CCMS", label: "CCMS", icon: Receipt, color: "text-pink-500 bg-pink-500/10" },
    { value: "EXPENSE", label: "Expense", icon: Wallet, color: "text-red-500 bg-red-500/10" },
];

function getTxnMeta(type: string) {
    return TXN_TYPES.find((t) => t.value === type) || TXN_TYPES[0];
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

export default function ShiftsPage() {
    const router = useRouter();
    const [activeShift, setActiveShift] = useState<Shift | null>(null);
    const [pastShifts, setPastShifts] = useState<Shift[]>([]);
    const [transactions, setTransactions] = useState<ShiftTransaction[]>([]);
    const [summary, setSummary] = useState<ShiftTransactionSummary | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [showPastShifts, setShowPastShifts] = useState(false);
    const [selectedPastShift, setSelectedPastShift] = useState<Shift | null>(null);

    // Add transaction modal
    const [isAddModalOpen, setIsAddModalOpen] = useState(false);
    const [txnType, setTxnType] = useState("CASH");
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
    const [expenseAmount, setExpenseAmount] = useState("");
    const [expenseDescription, setExpenseDescription] = useState("");
    const [expenseTypes, setExpenseTypes] = useState<ExpenseType[]>([]);
    const [selectedExpenseTypeId, setSelectedExpenseTypeId] = useState("");
    const [newExpenseTypeName, setNewExpenseTypeName] = useState("");

    // Filter
    const [typeFilter, setTypeFilter] = useState("ALL");
    const [searchQuery, setSearchQuery] = useState("");

    const viewingShift = selectedPastShift || activeShift;

    const loadData = useCallback(async () => {
        setIsLoading(true);
        try {
            const [active, allShifts] = await Promise.all([getActiveShift(), getShifts()]);
            setActiveShift(active);
            setPastShifts(allShifts.filter((s) => s.status !== "OPEN").sort((a, b) => b.id - a.id));

            const shiftToView = selectedPastShift || active;
            if (shiftToView) {
                const [txns, sum] = await Promise.all([
                    getShiftTransactions(shiftToView.id),
                    getShiftTransactionSummary(shiftToView.id),
                ]);
                setTransactions(txns);
                setSummary(sum);
            } else {
                setTransactions([]);
                setSummary(null);
            }
        } catch (err) {
            console.error("Failed to load shift data", err);
        } finally {
            setIsLoading(false);
        }
    }, [selectedPastShift]);

    useEffect(() => {
        loadData();
    }, [loadData]);

    const loadTransactions = async (shiftId: number) => {
        const [txns, sum] = await Promise.all([
            getShiftTransactions(shiftId),
            getShiftTransactionSummary(shiftId),
        ]);
        setTransactions(txns);
        setSummary(sum);
    };

    const handleOpenShift = async () => {
        try {
            const shift = await openShift({});
            setActiveShift(shift);
            setSelectedPastShift(null);
            await loadTransactions(shift.id);
        } catch (err: any) {
            alert(err.message || "Failed to open shift");
        }
    };

    const handleCloseShift = async () => {
        if (!activeShift) return;
        if (!confirm("Are you sure you want to close this shift?")) return;
        try {
            const closedShiftId = activeShift.id;
            await closeShift(closedShiftId);
            router.push(`/operations/shifts/report/${closedShiftId}`);
        } catch (err: any) {
            alert(err.message || "Failed to close shift");
        }
    };

    const resetForm = () => {
        setTxnType("CASH");
        setTxnAmount("");
        setTxnRemarks("");
        setSelectedUpiCompanyId("");
        setNewUpiCompanyName("");
        setCardBatchId(""); setCardTid(""); setCardCustomerName(""); setCardCustomerPhone(""); setCardBankName(""); setCardLast4("");
        setChequeBankName(""); setChequeInFavorOf(""); setChequeNo(""); setChequeDate("");
        setBankName("");
        setCcmsNumber("");
        setExpenseAmount(""); setExpenseDescription(""); setSelectedExpenseTypeId(""); setNewExpenseTypeName("");
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
            const payload: Partial<ShiftTransaction> = {
                txnType,
                shiftId: viewingShift.id,
                receivedAmount: Number(txnAmount),
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
            } else if (txnType === "BANK") {
                payload.bankName = bankName || undefined;
            } else if (txnType === "CCMS") {
                payload.ccmsNumber = ccmsNumber || undefined;
            } else if (txnType === "EXPENSE") {
                payload.expenseAmount = expenseAmount ? Number(expenseAmount) : undefined;
                payload.expenseDescription = expenseDescription || undefined;
                if (newExpenseTypeName) {
                    const created = await createExpenseType({ typeName: newExpenseTypeName });
                    payload.expenseType = created;
                } else if (selectedExpenseTypeId) {
                    payload.expenseType = { id: Number(selectedExpenseTypeId), typeName: "" };
                }
            }

            await createShiftTransaction(payload);
            setIsAddModalOpen(false);
            await loadTransactions(viewingShift.id);
        } catch (err: any) {
            alert(err.message || "Failed to add transaction");
        }
    };

    const handleDeleteTransaction = async (id: number) => {
        if (!confirm("Delete this transaction?")) return;
        try {
            await deleteShiftTransaction(id);
            if (viewingShift) await loadTransactions(viewingShift.id);
        } catch (err) {
            alert("Failed to delete transaction");
        }
    };

    const filtered = transactions.filter((t) => {
        const matchType = typeFilter === "ALL" || t.txnType === typeFilter;
        const q = searchQuery.toLowerCase();
        const matchSearch = !searchQuery ||
            t.remarks?.toLowerCase().includes(q) ||
            t.customerName?.toLowerCase().includes(q) ||
            t.bankName?.toLowerCase().includes(q) ||
            t.upiCompany?.companyName?.toLowerCase().includes(q);
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
                            Manage shifts and record all transactions during a shift.
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
                                    <SummaryCard label="Cash + UPI + Card" value={summary.total} color="text-blue-500" />
                                    <SummaryCard label="Expenses" value={summary.expense} color="text-red-500" />
                                    <SummaryCard label="Net Revenue" value={summary.net} color="text-green-500" />
                                </>
                            )}
                        </div>

                        {/* Summary Breakdown */}
                        {summary && (
                            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mb-6">
                                <MiniStat label="Cash" value={summary.cash} icon={Banknote} color="text-green-500 bg-green-500/10" />
                                <MiniStat label="UPI" value={summary.upi} icon={Smartphone} color="text-purple-500 bg-purple-500/10" />
                                <MiniStat label="Card" value={summary.card} icon={CreditCard} color="text-blue-500 bg-blue-500/10" />
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
                                        Add Transaction
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
                                                    No transactions found
                                                </td>
                                            </tr>
                                        ) : (
                                            pagedTxns.map((txn, idx) => {
                                                const meta = getTxnMeta(txn.txnType);
                                                const Icon = meta.icon;
                                                return (
                                                    <tr key={txn.id} className="hover:bg-white/5 transition-colors group">
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
                                                            <span className={`text-sm font-bold ${txn.txnType === 'EXPENSE' ? 'text-red-500' : 'text-green-500'}`}>
                                                                {txn.txnType === 'EXPENSE' ? '-' : '+'}
                                                                {formatCurrency(txn.receivedAmount)}
                                                            </span>
                                                        </td>
                                                        <td className="px-6 py-3">
                                                            <TxnDetails txn={txn} />
                                                        </td>
                                                        <td className="px-6 py-3 text-xs text-muted-foreground max-w-[200px] truncate">
                                                            {txn.remarks || "-"}
                                                        </td>
                                                        <td className="px-6 py-3 text-xs text-muted-foreground">
                                                            {formatDateTime(txn.transactionDate)}
                                                        </td>
                                                        {isViewingActive && (
                                                            <td className="px-6 py-3 text-center">
                                                                <PermissionGate permission="SHIFT_MANAGE">
                                                                    <button
                                                                        onClick={() => txn.id && handleDeleteTransaction(txn.id)}
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

                {/* Past Shifts Accordion */}
                <div className="mt-6">
                    <button
                        onClick={() => setShowPastShifts(!showPastShifts)}
                        className="flex items-center gap-2 text-sm font-medium text-muted-foreground hover:text-foreground transition-colors mb-3"
                    >
                        {showPastShifts ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
                        Past Shifts ({pastShifts.length})
                    </button>
                    {showPastShifts && pastShifts.length > 0 && (
                        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
                            {pastShifts.slice(0, 12).map((shift) => (
                                <button
                                    key={shift.id}
                                    onClick={() => {
                                        setSelectedPastShift(shift.id === selectedPastShift?.id ? null : shift);
                                    }}
                                    className={`text-left p-4 rounded-xl border transition-all ${
                                        selectedPastShift?.id === shift.id
                                            ? 'border-primary bg-primary/5'
                                            : 'border-border bg-card hover:border-primary/30'
                                    }`}
                                >
                                    <div className="flex justify-between items-center mb-2">
                                        <span className="text-sm font-bold text-foreground">Shift #{shift.id}</span>
                                        <span className={`px-2 py-0.5 rounded-full text-[10px] font-bold uppercase ${
                                            shift.status === 'CLOSED' ? 'bg-gray-500/10 text-gray-500' : 'bg-yellow-500/10 text-yellow-500'
                                        }`}>
                                            {shift.status}
                                        </span>
                                    </div>
                                    <p className="text-xs text-muted-foreground">{formatDateTime(shift.startTime)}</p>
                                    {shift.endTime && (
                                        <p className="text-xs text-muted-foreground">to {formatDateTime(shift.endTime)}</p>
                                    )}
                                    {(shift.status === "CLOSED" || shift.status === "RECONCILED") && (
                                        <button
                                            onClick={(e) => { e.stopPropagation(); router.push(`/operations/shifts/report/${shift.id}`); }}
                                            className="mt-2 text-xs px-2.5 py-1 rounded-lg bg-primary/10 text-primary hover:bg-primary/20 transition-colors"
                                        >
                                            View Report
                                        </button>
                                    )}
                                </button>
                            ))}
                        </div>
                    )}
                </div>
            </div>

            {/* Add Transaction Modal */}
            <Modal
                isOpen={isAddModalOpen}
                onClose={() => setIsAddModalOpen(false)}
                title="Add Shift Transaction"
            >
                <form onSubmit={handleAddTransaction} className="space-y-4">
                    {/* Transaction Type Selector */}
                    <div>
                        <label className="block text-sm font-medium text-foreground mb-2">Transaction Type</label>
                        <div className="grid grid-cols-4 gap-2">
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

                    {txnType === "BANK" && (
                        <InputField label="Bank Name" value={bankName} onChange={setBankName} placeholder="e.g. ICICI" />
                    )}

                    {txnType === "CCMS" && (
                        <InputField label="CCMS Number" value={ccmsNumber} onChange={setCcmsNumber} placeholder="CCMS ref number" />
                    )}

                    {txnType === "EXPENSE" && (
                        <div className="space-y-3">
                            <div>
                                <label className="block text-xs font-medium text-foreground mb-1">Expense Amount</label>
                                <input
                                    type="number"
                                    step="0.01"
                                    min="0"
                                    value={expenseAmount}
                                    onChange={(e) => setExpenseAmount(e.target.value)}
                                    className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground font-mono focus:outline-none focus:ring-2 focus:ring-primary/50"
                                    placeholder="0.00"
                                />
                            </div>
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
                                        <option key={et.id} value={et.id}>{et.typeName}</option>
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
                            Add Transaction
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

function TxnDetails({ txn }: { txn: ShiftTransaction }) {
    const details: string[] = [];
    if (txn.upiCompany?.companyName) details.push(txn.upiCompany.companyName);
    if (txn.bankName) details.push(txn.bankName);
    if (txn.cardLast4Digit) details.push(`****${txn.cardLast4Digit}`);
    if (txn.customerName) details.push(txn.customerName);
    if (txn.chequeNo) details.push(`Chq: ${txn.chequeNo}`);
    if (txn.ccmsNumber) details.push(`CCMS: ${txn.ccmsNumber}`);
    if (txn.expenseDescription) details.push(txn.expenseDescription);
    if (txn.expenseType?.typeName) details.push(`[${txn.expenseType.typeName}]`);

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
