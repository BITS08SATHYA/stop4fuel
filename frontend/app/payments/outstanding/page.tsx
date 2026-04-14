"use client";

import { useState, useEffect, useCallback } from "react";
import Link from "next/link";
import { AlertTriangle, Calendar, Search, ExternalLink, Receipt, FileText } from "lucide-react";
import { GlassCard } from "@/components/ui/glass-card";
import { TablePagination } from "@/components/ui/table-pagination";
import {
    searchOutstandingBills,
    getOutstandingStatementsSearch,
    type OutstandingBill,
    type Statement,
    type PageResponse,
} from "@/lib/api/station";

type Tab = "bills" | "statements";

const PAGE_SIZE = 20;

const fmtInr = (n: number | undefined | null) =>
    Number(n || 0).toLocaleString("en-IN", { style: "currency", currency: "INR" });

const fmtDate = (d?: string | null) =>
    d ? new Date(d).toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "numeric" }) : "-";

export default function OutstandingExplorerPage() {
    const [tab, setTab] = useState<Tab>("bills");

    // Applied filters (what's actually sent to the server)
    const [applied, setApplied] = useState<{
        search: string; fromDate: string; toDate: string; maxBalance: string;
    }>({ search: "", fromDate: "", toDate: "", maxBalance: "" });

    // Draft filters (edited in the form until "Apply")
    const [draft, setDraft] = useState(applied);

    const [page, setPage] = useState(0);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState("");

    const [bills, setBills] = useState<PageResponse<OutstandingBill> | null>(null);
    const [statements, setStatements] = useState<PageResponse<Statement> | null>(null);

    const load = useCallback(async () => {
        setLoading(true);
        setError("");
        try {
            if (tab === "bills") {
                const res = await searchOutstandingBills(page, PAGE_SIZE, {
                    fromDate: applied.fromDate || undefined,
                    toDate: applied.toDate || undefined,
                    search: applied.search || undefined,
                    maxBalance: applied.maxBalance === "" ? undefined : applied.maxBalance,
                });
                setBills(res);
            } else {
                const res = await getOutstandingStatementsSearch(page, PAGE_SIZE, {
                    fromDate: applied.fromDate || undefined,
                    toDate: applied.toDate || undefined,
                    search: applied.search || undefined,
                    maxBalance: applied.maxBalance === "" ? undefined : applied.maxBalance,
                });
                setStatements(res);
            }
        } catch (e: any) {
            setError(e?.message || "Failed to load outstanding records");
        } finally {
            setLoading(false);
        }
    }, [tab, page, applied]);

    useEffect(() => { load(); }, [load]);

    const onApply = () => {
        setPage(0);
        setApplied(draft);
    };

    const onClear = () => {
        const empty = { search: "", fromDate: "", toDate: "", maxBalance: "" };
        setDraft(empty);
        setApplied(empty);
        setPage(0);
    };

    const switchTab = (t: Tab) => {
        if (t === tab) return;
        setTab(t);
        setPage(0);
    };

    return (
        <div className="p-4 sm:p-6 lg:p-8 min-h-screen bg-background">
            <div className="max-w-7xl mx-auto">
                <div className="mb-6">
                    <h1 className="text-3xl sm:text-4xl font-bold text-foreground tracking-tight">
                        Outstanding <span className="text-gradient">Explorer</span>
                    </h1>
                    <p className="text-muted-foreground mt-2 text-sm sm:text-base">
                        Find credit bills or statements with remaining balance below a threshold.
                        Smallest balances first — ideal for nudging nearly-settled accounts.
                    </p>
                </div>

                {/* Tabs */}
                <div className="flex items-center gap-1 mb-4 border-b border-border">
                    <button
                        onClick={() => switchTab("bills")}
                        className={`flex items-center gap-2 px-4 py-2.5 text-sm font-medium border-b-2 -mb-px transition-colors ${
                            tab === "bills"
                                ? "border-primary text-foreground"
                                : "border-transparent text-muted-foreground hover:text-foreground"
                        }`}
                    >
                        <Receipt className="w-4 h-4" />
                        Local Credit Bills
                    </button>
                    <button
                        onClick={() => switchTab("statements")}
                        className={`flex items-center gap-2 px-4 py-2.5 text-sm font-medium border-b-2 -mb-px transition-colors ${
                            tab === "statements"
                                ? "border-primary text-foreground"
                                : "border-transparent text-muted-foreground hover:text-foreground"
                        }`}
                    >
                        <FileText className="w-4 h-4" />
                        Statements
                    </button>
                </div>

                {/* Filter row */}
                <GlassCard className="mb-6">
                    <div className="flex flex-wrap gap-3 items-end">
                        <div className="flex-1 min-w-[200px]">
                            <label className="block text-xs font-medium text-muted-foreground mb-1">
                                Search {tab === "bills" ? "(bill no, customer, vehicle)" : "(statement no, customer)"}
                            </label>
                            <div className="relative">
                                <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                                <input
                                    type="text"
                                    value={draft.search}
                                    onChange={(e) => setDraft({ ...draft, search: e.target.value })}
                                    onKeyDown={(e) => { if (e.key === "Enter") onApply(); }}
                                    placeholder={tab === "bills" ? "C26/34 or TN..." : "S26/12..."}
                                    className="w-full pl-9 pr-3 py-2 bg-card border border-border rounded-lg text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
                                />
                            </div>
                        </div>

                        <div>
                            <label className="block text-xs font-medium text-muted-foreground mb-1">
                                <Calendar className="w-3 h-3 inline mr-1" />From
                            </label>
                            <input
                                type="date"
                                value={draft.fromDate}
                                onChange={(e) => setDraft({ ...draft, fromDate: e.target.value })}
                                className="px-3 py-2 bg-card border border-border rounded-lg text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
                            />
                        </div>

                        <div>
                            <label className="block text-xs font-medium text-muted-foreground mb-1">
                                <Calendar className="w-3 h-3 inline mr-1" />To
                            </label>
                            <input
                                type="date"
                                value={draft.toDate}
                                onChange={(e) => setDraft({ ...draft, toDate: e.target.value })}
                                className="px-3 py-2 bg-card border border-border rounded-lg text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
                            />
                        </div>

                        <div>
                            <label className="block text-xs font-medium text-muted-foreground mb-1">
                                Max Balance (&lt;)
                            </label>
                            <input
                                type="number"
                                value={draft.maxBalance}
                                onChange={(e) => setDraft({ ...draft, maxBalance: e.target.value })}
                                onKeyDown={(e) => { if (e.key === "Enter") onApply(); }}
                                placeholder="e.g. 500"
                                className="w-32 px-3 py-2 bg-card border border-border rounded-lg text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
                                min="0"
                                step="0.01"
                            />
                        </div>

                        <button
                            onClick={onApply}
                            disabled={loading}
                            className="btn-gradient px-5 py-2 rounded-lg font-medium text-sm flex items-center gap-2 disabled:opacity-50"
                        >
                            <Search className="w-4 h-4" />
                            {loading ? "Loading..." : "Apply"}
                        </button>
                        <button
                            onClick={onClear}
                            disabled={loading}
                            className="px-4 py-2 rounded-lg border border-border text-sm text-muted-foreground hover:bg-muted disabled:opacity-50"
                        >
                            Clear
                        </button>
                    </div>

                    {error && (
                        <div className="mt-3 bg-rose-500/20 border border-rose-500/30 text-rose-400 px-4 py-2 rounded-lg text-sm flex items-center gap-2">
                            <AlertTriangle className="w-4 h-4" />
                            {error}
                        </div>
                    )}
                </GlassCard>

                {/* Results */}
                <GlassCard className="!p-0 overflow-hidden">
                    <div className="overflow-x-auto">
                        {tab === "bills" ? (
                            <table className="w-full text-sm">
                                <thead>
                                    <tr className="border-b border-border text-muted-foreground text-xs uppercase tracking-wider">
                                        <th className="text-left py-3 px-4">Bill No</th>
                                        <th className="text-left py-3 px-4">Date</th>
                                        <th className="text-left py-3 px-4">Customer</th>
                                        <th className="text-left py-3 px-4">Vehicle</th>
                                        <th className="text-right py-3 px-4">Net</th>
                                        <th className="text-right py-3 px-4">Paid</th>
                                        <th className="text-right py-3 px-4">Balance</th>
                                        <th className="text-left py-3 px-4">Status</th>
                                        <th className="py-3 px-4"></th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {(bills?.content ?? []).length === 0 && !loading ? (
                                        <tr><td colSpan={9} className="text-center py-10 text-muted-foreground">No outstanding bills match these filters.</td></tr>
                                    ) : (
                                        (bills?.content ?? []).map((b) => (
                                            <tr key={b.id} className="border-b border-border/50 hover:bg-muted/30">
                                                <td className="py-2.5 px-4 font-medium text-foreground">{b.billNo || `#${b.id}`}</td>
                                                <td className="py-2.5 px-4 text-muted-foreground">{fmtDate(b.date)}</td>
                                                <td className="py-2.5 px-4">{b.customerName || "-"}</td>
                                                <td className="py-2.5 px-4 text-muted-foreground">{b.vehicleNumber || "-"}</td>
                                                <td className="py-2.5 px-4 text-right tabular-nums">{fmtInr(b.netAmount)}</td>
                                                <td className="py-2.5 px-4 text-right tabular-nums text-emerald-400">{fmtInr(b.paidAmount)}</td>
                                                <td className="py-2.5 px-4 text-right tabular-nums font-semibold text-amber-400">{fmtInr(b.balance)}</td>
                                                <td className="py-2.5 px-4 text-xs">
                                                    <span className={`px-2 py-0.5 rounded-full ${
                                                        b.paymentStatus === "PARTIAL" ? "bg-amber-500/10 text-amber-400" : "bg-rose-500/10 text-rose-400"
                                                    }`}>{b.paymentStatus || "-"}</span>
                                                </td>
                                                <td className="py-2.5 px-4 text-right">
                                                    <Link
                                                        href={`/operations/invoices/explorer?invoiceId=${b.id}`}
                                                        className="text-primary hover:underline inline-flex items-center gap-1 text-xs"
                                                    >
                                                        View <ExternalLink className="w-3 h-3" />
                                                    </Link>
                                                </td>
                                            </tr>
                                        ))
                                    )}
                                </tbody>
                            </table>
                        ) : (
                            <table className="w-full text-sm">
                                <thead>
                                    <tr className="border-b border-border text-muted-foreground text-xs uppercase tracking-wider">
                                        <th className="text-left py-3 px-4">Statement No</th>
                                        <th className="text-left py-3 px-4">Date</th>
                                        <th className="text-left py-3 px-4">Customer</th>
                                        <th className="text-right py-3 px-4">Net</th>
                                        <th className="text-right py-3 px-4">Received</th>
                                        <th className="text-right py-3 px-4">Balance</th>
                                        <th className="text-left py-3 px-4">Status</th>
                                        <th className="py-3 px-4"></th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {(statements?.content ?? []).length === 0 && !loading ? (
                                        <tr><td colSpan={8} className="text-center py-10 text-muted-foreground">No outstanding statements match these filters.</td></tr>
                                    ) : (
                                        (statements?.content ?? []).map((s) => (
                                            <tr key={s.id} className="border-b border-border/50 hover:bg-muted/30">
                                                <td className="py-2.5 px-4 font-medium text-foreground">{s.statementNo}</td>
                                                <td className="py-2.5 px-4 text-muted-foreground">{fmtDate(s.statementDate)}</td>
                                                <td className="py-2.5 px-4">{s.customer?.name || "-"}</td>
                                                <td className="py-2.5 px-4 text-right tabular-nums">{fmtInr(s.netAmount)}</td>
                                                <td className="py-2.5 px-4 text-right tabular-nums text-emerald-400">{fmtInr(s.receivedAmount)}</td>
                                                <td className="py-2.5 px-4 text-right tabular-nums font-semibold text-amber-400">{fmtInr(s.balanceAmount)}</td>
                                                <td className="py-2.5 px-4 text-xs">
                                                    <span className={`px-2 py-0.5 rounded-full ${
                                                        s.status === "PAID" ? "bg-emerald-500/10 text-emerald-400" :
                                                        s.status === "DRAFT" ? "bg-muted text-muted-foreground" :
                                                        "bg-rose-500/10 text-rose-400"
                                                    }`}>{s.status}</span>
                                                </td>
                                                <td className="py-2.5 px-4 text-right">
                                                    <Link
                                                        href={`/payments/explorer?statementId=${s.id}`}
                                                        className="text-primary hover:underline inline-flex items-center gap-1 text-xs"
                                                    >
                                                        View <ExternalLink className="w-3 h-3" />
                                                    </Link>
                                                </td>
                                            </tr>
                                        ))
                                    )}
                                </tbody>
                            </table>
                        )}
                    </div>

                    {tab === "bills" && bills && (
                        <TablePagination
                            page={page}
                            totalPages={bills.totalPages}
                            totalElements={bills.totalElements}
                            pageSize={PAGE_SIZE}
                            onPageChange={setPage}
                        />
                    )}
                    {tab === "statements" && statements && (
                        <TablePagination
                            page={page}
                            totalPages={statements.totalPages}
                            totalElements={statements.totalElements}
                            pageSize={PAGE_SIZE}
                            onPageChange={setPage}
                        />
                    )}
                </GlassCard>
            </div>
        </div>
    );
}
