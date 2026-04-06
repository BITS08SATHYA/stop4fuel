"use client";

import { useEffect, useState, useCallback, useMemo } from "react";
import { GlassCard } from "@/components/ui/glass-card";
import { StyledSelect } from "@/components/ui/styled-select";
import { Modal } from "@/components/ui/modal";
import { CustomerAutocomplete } from "@/components/ui/customer-autocomplete";
import { InvoiceAutocomplete } from "@/components/ui/invoice-autocomplete";
import { StatementAutocomplete } from "@/components/ui/statement-autocomplete";
import {
    Search,
    Plus,
    Trash2,
    Users,
    TrendingDown,
    Hash,
    Calendar,
} from "lucide-react";
import {
    API_BASE_URL,
    IncentivePayment,
    createIncentivePayment,
    deleteIncentivePayment,
} from "@/lib/api/station";
import { fetchWithAuth } from "@/lib/api/fetch-with-auth";
import { TablePagination, useClientPagination } from "@/components/ui/table-pagination";
import { PermissionGate } from "@/components/permission-gate";

// --- Types ---

interface Customer {
    id: number;
    name: string;
}

// --- Helpers ---

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

async function fetchCustomers(): Promise<Customer[]> {
    try {
        const res = await fetchWithAuth(`${API_BASE_URL}/customers`);
        if (!res.ok) return [];
        return res.json();
    } catch {
        return [];
    }
}

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

async function fetchIncentivesByShift(shiftId: number): Promise<IncentivePayment[]> {
    const res = await fetchWithAuth(`${API_BASE_URL}/incentive-payments/shift/${shiftId}`);
    if (!res.ok) throw new Error("Failed to fetch incentive payments");
    return res.json();
}

async function fetchIncentivesByDateRange(fromDate: string, toDate: string): Promise<IncentivePayment[]> {
    const res = await fetchWithAuth(`${API_BASE_URL}/incentive-payments/search?fromDate=${fromDate}&toDate=${toDate}`);
    if (!res.ok) throw new Error("Failed to fetch incentive payments");
    return res.json();
}

// --- Page ---

export default function IncentivePaymentsPage() {
    const [payments, setPayments] = useState<IncentivePayment[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [activeShiftId, setActiveShiftId] = useState<number | null>(null);

    // Filters
    const [searchQuery, setSearchQuery] = useState("");
    const [customerFilter, setCustomerFilter] = useState("ALL");
    const [fromDate, setFromDate] = useState("");
    const [toDate, setToDate] = useState("");
    const [viewMode, setViewMode] = useState<"shift" | "dates">("shift");

    // Add modal
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [customers, setCustomers] = useState<Customer[]>([]);
    const [selectedCustomerId, setSelectedCustomerId] = useState("");
    const [amount, setAmount] = useState("");
    const [description, setDescription] = useState("");
    const [linkedInvoice, setLinkedInvoice] = useState<{ id: number; billNo?: string; billType?: string; netAmount?: number } | null>(null);
    const [linkedStatement, setLinkedStatement] = useState<{ id: number; statementNo?: string } | null>(null);

    const loadData = useCallback(async (mode: "shift" | "dates", shiftId?: number | null, from?: string, to?: string) => {
        setIsLoading(true);
        try {
            let data: IncentivePayment[];
            if (mode === "dates" && from && to) {
                data = await fetchIncentivesByDateRange(from, to);
            } else if (mode === "shift" && shiftId) {
                data = await fetchIncentivesByShift(shiftId);
            } else {
                data = [];
            }
            setPayments(data);
        } catch (err) {
            console.error("Failed to load incentive payments", err);
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
        setFromDate("");
        setToDate("");
        if (activeShiftId) {
            loadData("shift", activeShiftId);
        } else {
            setPayments([]);
        }
    };

    // Summary
    const summary = useMemo(() => {
        const customerSet = new Set<number>();
        let total = 0;
        for (const p of payments) {
            total += p.amount || 0;
            if (p.customer?.id) customerSet.add(p.customer.id);
        }
        return { total, count: payments.length, customerCount: customerSet.size };
    }, [payments]);

    // Unique customers for filter dropdown
    const uniqueCustomers = useMemo(() => {
        const map = new Map<number, string>();
        for (const p of payments) {
            if (p.customer?.id && p.customer?.name) {
                map.set(p.customer.id, p.customer.name);
            }
        }
        return Array.from(map.entries()).sort((a, b) => a[1].localeCompare(b[1]));
    }, [payments]);

    // Filter
    const filtered = useMemo(() => {
        return payments.filter((p) => {
            const matchCustomer = customerFilter === "ALL" || String(p.customer?.id) === customerFilter;
            const q = searchQuery.toLowerCase();
            const matchSearch = !searchQuery ||
                p.description?.toLowerCase().includes(q) ||
                p.customer?.name?.toLowerCase().includes(q) ||
                p.invoiceBill?.billNo?.toLowerCase().includes(q) ||
                p.statement?.statementNo?.toLowerCase().includes(q);
            return matchCustomer && matchSearch;
        });
    }, [payments, customerFilter, searchQuery]);

    const { page, setPage, totalPages, totalElements, pageSize, paginatedData } = useClientPagination(filtered);

    const handleOpenAdd = async () => {
        setSelectedCustomerId("");
        setAmount("");
        setDescription("");
        setLinkedInvoice(null);
        setLinkedStatement(null);
        const custs = await fetchCustomers();
        setCustomers(custs);
        setIsModalOpen(true);
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        try {
            const payload: Partial<IncentivePayment> = {
                amount: Number(amount),
                description: description || undefined,
            };
            if (selectedCustomerId) {
                payload.customer = { id: Number(selectedCustomerId) };
            }
            if (linkedInvoice) {
                payload.invoiceBill = { id: linkedInvoice.id };
            }
            if (linkedStatement) {
                payload.statement = { id: linkedStatement.id };
            }
            await createIncentivePayment(payload);
            setIsModalOpen(false);
            if (viewMode === "dates" && fromDate && toDate) {
                loadData("dates", null, fromDate, toDate);
            } else if (activeShiftId) {
                loadData("shift", activeShiftId);
            }
        } catch (err: any) {
            alert(err.message || "Failed to create incentive payment");
        }
    };

    const handleDelete = async (id: number) => {
        if (!confirm("Delete this incentive payment?")) return;
        try {
            await deleteIncentivePayment(id);
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
                            Incentive <span className="text-gradient">Payments</span>
                        </h1>
                        <p className="text-muted-foreground mt-2">
                            Track discount and incentive payments given to customers.
                        </p>
                    </div>
                    <PermissionGate permission="SHIFT_CREATE">
                        <button
                            onClick={handleOpenAdd}
                            className="btn-gradient px-6 py-3 rounded-xl font-medium flex items-center gap-2 shadow-lg hover:shadow-xl transition-all"
                        >
                            <Plus className="w-5 h-5" />
                            Add Payment
                        </button>
                    </PermissionGate>
                </div>

                {/* Summary Cards */}
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-6">
                    <GlassCard className="flex items-center gap-4">
                        <div className="p-3 rounded-xl bg-orange-500/10 text-orange-500">
                            <TrendingDown className="w-5 h-5" />
                        </div>
                        <div>
                            <p className="text-xs uppercase tracking-wider text-muted-foreground">Total Incentives</p>
                            <p className="text-2xl font-bold text-orange-500">{formatCurrency(summary.total)}</p>
                        </div>
                    </GlassCard>
                    <GlassCard className="flex items-center gap-4">
                        <div className="p-3 rounded-xl bg-blue-500/10 text-blue-500">
                            <Hash className="w-5 h-5" />
                        </div>
                        <div>
                            <p className="text-xs uppercase tracking-wider text-muted-foreground">Transactions</p>
                            <p className="text-2xl font-bold text-foreground">{summary.count}</p>
                        </div>
                    </GlassCard>
                    <GlassCard className="flex items-center gap-4">
                        <div className="p-3 rounded-xl bg-purple-500/10 text-purple-500">
                            <Users className="w-5 h-5" />
                        </div>
                        <div>
                            <p className="text-xs uppercase tracking-wider text-muted-foreground">Customers</p>
                            <p className="text-2xl font-bold text-foreground">{summary.customerCount}</p>
                        </div>
                    </GlassCard>
                </div>

                {/* Filter Bar */}
                <div className="mb-4 flex flex-wrap gap-3 items-end">
                    <div className="relative flex-1 min-w-[200px] max-w-md">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                        <input
                            type="text"
                            placeholder="Search description, customer, bill..."
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            className="w-full pl-10 pr-4 py-2.5 bg-card border border-border rounded-xl text-foreground text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                        />
                    </div>
                    <StyledSelect
                        value={customerFilter}
                        onChange={(val) => setCustomerFilter(val)}
                        options={[
                            { value: "ALL", label: "All Customers" },
                            ...uniqueCustomers.map(([id, name]) => ({ value: String(id), label: String(name) })),
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
                                        <th className="px-5 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Customer</th>
                                        <th className="px-5 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right">Amount</th>
                                        <th className="px-5 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Description</th>
                                        <th className="px-5 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Invoice / Statement</th>
                                        <th className="px-5 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Shift</th>
                                        <th className="px-5 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center">Actions</th>
                                    </tr>
                                </thead>
                                <tbody className="divide-y divide-border/30">
                                    {filtered.length === 0 ? (
                                        <tr>
                                            <td colSpan={8} className="px-5 py-12 text-center text-muted-foreground">
                                                No incentive payments found
                                            </td>
                                        </tr>
                                    ) : (
                                        paginatedData.map((p, idx) => (
                                            <tr key={p.id} className="hover:bg-white/5 transition-colors group">
                                                <td className="px-5 py-3 text-xs font-mono text-muted-foreground text-center">{page * pageSize + idx + 1}</td>
                                                <td className="px-5 py-3 text-xs text-muted-foreground">{formatDateTime(p.paymentDate)}</td>
                                                <td className="px-5 py-3">
                                                    <span className="text-sm font-medium text-foreground">
                                                        {p.customer?.name || "-"}
                                                    </span>
                                                </td>
                                                <td className="px-5 py-3 text-right">
                                                    <span className="text-sm font-bold text-orange-500">{formatCurrency(p.amount)}</span>
                                                </td>
                                                <td className="px-5 py-3 text-xs text-muted-foreground max-w-[250px] truncate">
                                                    {p.description || "-"}
                                                </td>
                                                <td className="px-5 py-3">
                                                    <div className="flex flex-wrap gap-1.5">
                                                        {p.invoiceBill && (
                                                            <span className="text-[10px] px-2 py-0.5 rounded-full bg-primary/5 border border-primary/20 text-primary">
                                                                Bill: {p.invoiceBill.billNo || `#${p.invoiceBill.id}`}
                                                            </span>
                                                        )}
                                                        {p.statement && (
                                                            <span className="text-[10px] px-2 py-0.5 rounded-full bg-teal-500/10 border border-teal-500/20 text-teal-500">
                                                                Stmt: {p.statement.statementNo || `#${p.statement.id}`}
                                                            </span>
                                                        )}
                                                        {!p.invoiceBill && !p.statement && (
                                                            <span className="text-xs text-muted-foreground">-</span>
                                                        )}
                                                    </div>
                                                </td>
                                                <td className="px-5 py-3 text-xs text-muted-foreground">
                                                    {p.shiftId ? `#${p.shiftId}` : "-"}
                                                </td>
                                                <td className="px-5 py-3 text-center">
                                                    <PermissionGate permission="SHIFT_DELETE">
                                                        <button
                                                            onClick={() => p.id && handleDelete(p.id)}
                                                            className="p-1.5 rounded-lg hover:bg-red-500/10 text-muted-foreground hover:text-red-500 opacity-100 md:opacity-0 group-hover:opacity-100 transition-all"
                                                        >
                                                            <Trash2 className="w-3.5 h-3.5" />
                                                        </button>
                                                    </PermissionGate>
                                                </td>
                                            </tr>
                                        ))
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

            {/* Add Modal */}
            <Modal
                isOpen={isModalOpen}
                onClose={() => setIsModalOpen(false)}
                title="Add Incentive Payment"
            >
                <form onSubmit={handleSubmit} className="space-y-4">
                    {/* Customer */}
                    <div>
                        <label className="block text-sm font-medium text-foreground mb-1.5">Customer</label>
                        <CustomerAutocomplete
                            value={selectedCustomerId}
                            onChange={(id) => setSelectedCustomerId(String(id))}
                            customers={customers}
                            placeholder="Search customer..."
                        />
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
                            value={amount}
                            onChange={(e) => setAmount(e.target.value)}
                            className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground font-mono focus:outline-none focus:ring-2 focus:ring-primary/50"
                            placeholder="0.00"
                        />
                    </div>

                    {/* Description */}
                    <div>
                        <label className="block text-sm font-medium text-foreground mb-1.5">Description</label>
                        <input
                            type="text"
                            value={description}
                            onChange={(e) => setDescription(e.target.value)}
                            className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                            placeholder="e.g. Monthly volume discount"
                        />
                    </div>

                    {/* Link to Invoice or Statement (mutually exclusive) */}
                    <div>
                        <label className="block text-sm font-medium text-foreground mb-1.5">Link to Invoice Bill (Optional)</label>
                        <InvoiceAutocomplete
                            value={linkedInvoice}
                            onChange={(inv) => { setLinkedInvoice(inv); if (inv) setLinkedStatement(null); }}
                            placeholder="Search by bill #..."
                        />
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-foreground mb-1.5">Link to Statement (Optional)</label>
                        <StatementAutocomplete
                            value={linkedStatement}
                            onChange={(stmt) => { setLinkedStatement(stmt); if (stmt) setLinkedInvoice(null); }}
                            placeholder="Search by statement # or customer..."
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
                            Add Payment
                        </button>
                    </div>
                </form>
            </Modal>
        </div>
    );
}
