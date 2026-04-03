"use client";

import { useState, useEffect } from "react";
import {
    Plus,
    Search,
    Pencil,
    Trash2,
    Receipt,
    TrendingUp,
} from "lucide-react";
import { Modal } from "@/components/ui/modal";
import { GlassCard } from "@/components/ui/glass-card";
import { Badge } from "@/components/ui/badge";
import { TablePagination, useClientPagination } from "@/components/ui/table-pagination";
import {
    getStationExpenses,
    createStationExpense,
    updateStationExpense,
    deleteStationExpense,
    getExpenseSummary,
    getExpenseTypes,
    StationExpense,
    ExpenseType,
    ExpenseSummary,
    API_BASE_URL,
} from "@/lib/api/station";
import { fetchWithAuth } from "@/lib/api/fetch-with-auth";
import { PermissionGate } from "@/components/permission-gate";

const formatRupees = (val: number) => `₹${val.toLocaleString("en-IN")}`;

const recurringOptions = [
    { value: "ONE_TIME", label: "One Time" },
    { value: "MONTHLY", label: "Monthly" },
    { value: "QUARTERLY", label: "Quarterly" },
    { value: "ANNUAL", label: "Annual" },
];

export default function ExpensesPage() {
    const [expenses, setExpenses] = useState<StationExpense[]>([]);
    const [expenseTypes, setExpenseTypes] = useState<ExpenseType[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [editingExpense, setEditingExpense] = useState<StationExpense | null>(null);
    const [searchQuery, setSearchQuery] = useState("");
    const [summary, setSummary] = useState<ExpenseSummary | null>(null);

    // Date filters — default to shift start time (fallback: first of month)
    const now = new Date();
    const firstOfMonth = new Date(now.getFullYear(), now.getMonth(), 1).toISOString().split("T")[0];
    const today = now.toISOString().split("T")[0];
    const [fromDate, setFromDate] = useState(firstOfMonth);
    const [toDate, setToDate] = useState(today);
    const [shiftLoaded, setShiftLoaded] = useState(false);

    // Form state
    const [expenseTypeId, setExpenseTypeId] = useState("");
    const [amount, setAmount] = useState("");
    const [expenseDate, setExpenseDate] = useState(today);
    const [description, setDescription] = useState("");
    const [paidTo, setPaidTo] = useState("");
    const [paymentMode, setPaymentMode] = useState("CASH");
    const [recurringType, setRecurringType] = useState("ONE_TIME");

    // Fetch active shift on mount and set fromDate to shift start time
    useEffect(() => {
        (async () => {
            try {
                const res = await fetchWithAuth(`${API_BASE_URL}/shifts/active`);
                if (res.ok) {
                    const text = await res.text();
                    if (text) {
                        const shift = JSON.parse(text);
                        if (shift.startTime) {
                            setFromDate(shift.startTime.split("T")[0]);
                        }
                    }
                }
            } catch { /* ignore */ }
            setShiftLoaded(true);
        })();
    }, []);

    useEffect(() => { if (shiftLoaded) loadData(); }, [fromDate, toDate, shiftLoaded]);

    const loadData = async () => {
        setIsLoading(true);
        try {
            const [exps, types, sum] = await Promise.all([
                getStationExpenses(fromDate, toDate),
                getExpenseTypes(),
                getExpenseSummary(fromDate, toDate),
            ]);
            setExpenses(exps);
            setExpenseTypes(types);
            setSummary(sum);
        } catch (e) { console.error(e); }
        setIsLoading(false);
    };

    const resetForm = () => {
        setExpenseTypeId("");
        setAmount("");
        setExpenseDate(today);
        setDescription("");
        setPaidTo("");
        setPaymentMode("CASH");
        setRecurringType("ONE_TIME");
    };

    const handleEdit = (exp: StationExpense) => {
        setEditingExpense(exp);
        setExpenseTypeId(exp.expenseType?.id?.toString() || "");
        setAmount(exp.amount?.toString() || "");
        setExpenseDate(exp.expenseDate);
        setDescription(exp.description || "");
        setPaidTo(exp.paidTo || "");
        setPaymentMode(exp.paymentMode || "CASH");
        setRecurringType(exp.recurringType || "ONE_TIME");
        setIsModalOpen(true);
    };

    const handleSave = async (e: React.FormEvent) => {
        e.preventDefault();
        try {
            const data: any = {
                amount: parseFloat(amount),
                expenseDate,
                description: description || undefined,
                paidTo: paidTo || undefined,
                paymentMode,
                recurringType,
            };
            if (expenseTypeId) {
                data.expenseType = { id: Number(expenseTypeId) };
            }
            if (editingExpense) {
                await updateStationExpense(editingExpense.id, data);
            } else {
                await createStationExpense(data);
            }
            setIsModalOpen(false);
            setEditingExpense(null);
            resetForm();
            loadData();
        } catch (e) {
            alert("Failed to save expense");
        }
    };

    const handleDelete = async (id: number) => {
        if (!confirm("Delete this expense?")) return;
        await deleteStationExpense(id);
        loadData();
    };

    const filtered = expenses.filter((exp) =>
        !searchQuery ||
        exp.description?.toLowerCase().includes(searchQuery.toLowerCase()) ||
        exp.paidTo?.toLowerCase().includes(searchQuery.toLowerCase()) ||
        exp.expenseType?.name?.toLowerCase().includes(searchQuery.toLowerCase())
    );

    const { page, setPage, totalPages, totalElements, pageSize, paginatedData } =
        useClientPagination(filtered, 8);

    if (isLoading) {
        return (
            <div className="p-6 h-screen overflow-hidden bg-background flex items-center justify-center">
                <div className="flex flex-col items-center">
                    <div className="w-12 h-12 border-4 border-primary/20 border-t-primary rounded-full animate-spin mb-4" />
                    <p className="animate-pulse text-muted-foreground">Loading expenses...</p>
                </div>
            </div>
        );
    }

    return (
        <div className="p-6 h-screen overflow-hidden bg-background transition-colors duration-300">
            <div className="max-w-7xl mx-auto h-full flex flex-col">
                {/* Header */}
                <div className="flex justify-between items-center mb-6">
                    <div>
                        <h1 className="text-4xl font-bold text-foreground tracking-tight">
                            Station <span className="text-gradient">Expenses</span>
                        </h1>
                        <p className="text-muted-foreground mt-2">Track and manage station operating expenses</p>
                    </div>
                    <PermissionGate permission="FINANCE_MANAGE">
                        <button
                            onClick={() => { resetForm(); setEditingExpense(null); setIsModalOpen(true); }}
                            className="btn-gradient px-6 py-3 rounded-xl font-medium flex items-center gap-2"
                        >
                            <Plus className="w-5 h-5" />
                            Add Expense
                        </button>
                    </PermissionGate>
                </div>

                {/* Summary Cards */}
                {summary && (
                    <div className="grid grid-cols-2 md:grid-cols-4 gap-3 mb-6">
                        <GlassCard className="p-4 text-center">
                            <Receipt className="w-5 h-5 mx-auto text-primary mb-1" />
                            <p className="text-xl font-bold text-primary">{formatRupees(summary.totalAmount || 0)}</p>
                            <p className="text-xs text-muted-foreground">Total Expenses</p>
                        </GlassCard>
                        <GlassCard className="p-4 text-center">
                            <TrendingUp className="w-5 h-5 mx-auto text-muted-foreground mb-1" />
                            <p className="text-2xl font-bold text-foreground">{summary.count}</p>
                            <p className="text-xs text-muted-foreground">Transactions</p>
                        </GlassCard>
                        {Object.entries(summary.byCategory || {}).slice(0, 2).map(([cat, amt]) => (
                            <GlassCard key={cat} className="p-4 text-center">
                                <p className="text-xs text-muted-foreground mb-1 uppercase">{cat}</p>
                                <p className="text-xl font-bold text-foreground">{formatRupees(amt)}</p>
                            </GlassCard>
                        ))}
                    </div>
                )}

                {/* Filters */}
                <div className="mb-4 flex gap-3 items-center flex-wrap">
                    <div className="relative flex-1 max-w-md">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                        <input
                            type="text"
                            placeholder="Search expenses..."
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            className="w-full bg-background border border-border rounded-xl pl-10 pr-4 py-2.5 text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                        />
                    </div>
                    <input type="date" value={fromDate} onChange={(e) => setFromDate(e.target.value)} className="bg-background border border-border rounded-xl px-4 py-2.5 text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50" />
                    <span className="text-muted-foreground">to</span>
                    <input type="date" value={toDate} onChange={(e) => setToDate(e.target.value)} className="bg-background border border-border rounded-xl px-4 py-2.5 text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50" />
                </div>

                {/* Table */}
                {filtered.length === 0 ? (
                    <div className="text-center py-20 bg-black/5 dark:bg-white/5 rounded-2xl border border-dashed border-border">
                        <Receipt className="w-16 h-16 mx-auto text-muted-foreground mb-4 opacity-50" />
                        <h3 className="text-xl font-semibold text-foreground mb-2">No Expenses Found</h3>
                        <p className="text-muted-foreground mb-6">Record your first station expense.</p>
                    </div>
                ) : (
                    <GlassCard className="overflow-hidden border-none p-0 flex-1">
                        <div className="overflow-x-auto">
                            <table className="w-full text-left border-collapse">
                                <thead>
                                    <tr className="bg-white/5 border-b border-border/50">
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center w-12">#</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Date</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Category</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Description</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Paid To</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center">Mode</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center">Recurring</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right">Amount</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center w-28">Actions</th>
                                    </tr>
                                </thead>
                                <tbody className="divide-y divide-border/30">
                                    {paginatedData.map((exp, idx) => (
                                        <tr key={exp.id} className="hover:bg-white/5 transition-colors group">
                                            <td className="px-6 py-4 text-xs font-mono text-muted-foreground text-center">{page * pageSize + idx + 1}</td>
                                            <td className="px-6 py-4 text-sm">{exp.expenseDate}</td>
                                            <td className="px-6 py-4">
                                                <Badge variant="default">{exp.expenseType?.name || "Uncategorized"}</Badge>
                                            </td>
                                            <td className="px-6 py-4 text-sm text-foreground">{exp.description || "-"}</td>
                                            <td className="px-6 py-4 text-sm text-muted-foreground">{exp.paidTo || "-"}</td>
                                            <td className="px-6 py-4 text-center text-xs">{exp.paymentMode}</td>
                                            <td className="px-6 py-4 text-center">
                                                <Badge variant={exp.recurringType === "ONE_TIME" ? "outline" : "warning"}>
                                                    {exp.recurringType?.replace("_", " ")}
                                                </Badge>
                                            </td>
                                            <td className="px-6 py-4 text-right font-bold text-primary">{formatRupees(exp.amount || 0)}</td>
                                            <td className="px-6 py-4">
                                                <div className="flex justify-center gap-2 opacity-100 md:opacity-0 group-hover:opacity-100 transition-opacity">
                                                    <PermissionGate permission="FINANCE_MANAGE">
                                                        <button onClick={() => handleEdit(exp)} className="p-2 rounded-lg hover:bg-white/10"><Pencil className="w-4 h-4" /></button>
                                                    </PermissionGate>
                                                    <PermissionGate permission="FINANCE_MANAGE">
                                                        <button onClick={() => handleDelete(exp.id)} className="p-2 rounded-lg hover:bg-red-500/10 text-red-500"><Trash2 className="w-4 h-4" /></button>
                                                    </PermissionGate>
                                                </div>
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                        <TablePagination page={page} totalPages={totalPages} totalElements={totalElements} pageSize={pageSize} onPageChange={setPage} />
                    </GlassCard>
                )}
            </div>

            {/* Modal */}
            <Modal isOpen={isModalOpen} onClose={() => { setIsModalOpen(false); setEditingExpense(null); resetForm(); }} title={editingExpense ? "Edit Expense" : "Add Expense"}>
                <form onSubmit={handleSave} className="space-y-4">
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5">Category</label>
                            <select value={expenseTypeId} onChange={(e) => setExpenseTypeId(e.target.value)} className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50 [&>option]:bg-background [&>option]:text-foreground">
                                <option value="">Select Category</option>
                                {expenseTypes.map((et) => (
                                    <option key={et.id} value={et.id}>{et.name}</option>
                                ))}
                            </select>
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5">Amount <span className="text-red-500">*</span></label>
                            <input type="number" step="0.01" required value={amount} onChange={(e) => setAmount(e.target.value)} className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50" />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5">Date <span className="text-red-500">*</span></label>
                            <input type="date" required value={expenseDate} onChange={(e) => setExpenseDate(e.target.value)} className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50" />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5">Payment Mode</label>
                            <select value={paymentMode} onChange={(e) => setPaymentMode(e.target.value)} className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50 [&>option]:bg-background [&>option]:text-foreground">
                                <option value="CASH">Cash</option>
                                <option value="UPI">UPI</option>
                                <option value="NEFT">NEFT</option>
                                <option value="CHEQUE">Cheque</option>
                                <option value="CARD">Card</option>
                            </select>
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5">Paid To</label>
                            <input type="text" value={paidTo} onChange={(e) => setPaidTo(e.target.value)} className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50" placeholder="Vendor / person name" />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5">Recurring Type</label>
                            <select value={recurringType} onChange={(e) => setRecurringType(e.target.value)} className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50 [&>option]:bg-background [&>option]:text-foreground">
                                {recurringOptions.map((opt) => (
                                    <option key={opt.value} value={opt.value}>{opt.label}</option>
                                ))}
                            </select>
                        </div>
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-foreground mb-1.5">Description</label>
                        <textarea value={description} onChange={(e) => setDescription(e.target.value)} rows={2} className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50" placeholder="Expense details..." />
                    </div>
                    <div className="flex justify-end gap-3 pt-4 border-t border-border">
                        <button type="button" onClick={() => { setIsModalOpen(false); setEditingExpense(null); resetForm(); }} className="px-6 py-2.5 rounded-xl font-medium text-foreground bg-black/5 dark:bg-white/5 hover:bg-black/10 dark:hover:bg-white/10 transition-colors">Cancel</button>
                        <button type="submit" className="btn-gradient px-8 py-2.5 rounded-xl font-medium">Save</button>
                    </div>
                </form>
            </Modal>
        </div>
    );
}
