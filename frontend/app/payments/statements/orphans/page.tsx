"use client";

import { useEffect, useMemo, useState } from "react";
import {
    AlertTriangle,
    Wand2,
    RefreshCw,
    CheckCircle2,
    XCircle,
    Hourglass,
    Filter,
    Search,
    Link2Off,
    History,
} from "lucide-react";
import { GlassCard } from "@/components/ui/glass-card";
import { useToast } from "@/components/ui/toast";
import { PermissionGate } from "@/components/permission-gate";
import { TablePagination, useClientPagination } from "@/components/ui/table-pagination";
import { ShiftPicker } from "@/components/ShiftPicker";
import {
    getOrphanBills,
    autoFixOrphan,
    autoFixAllOrphans,
    setBillShift,
    markInvoiceIndependent,
    OrphanBill,
    AutoFixResult,
    BulkAutoFixResult,
} from "@/lib/api/station";

type FailureFilter = "ALL" | "NULL_SHIFT" | "NO_STATEMENT" | "BOTH";

const FAILURE_LABELS: Record<OrphanBill["failureType"], { label: string; tone: string }> = {
    NULL_SHIFT: { label: "Missing shift", tone: "bg-amber-500/10 text-amber-500 border-amber-500/30" },
    NO_STATEMENT: { label: "Awaiting statement", tone: "bg-amber-500/10 text-amber-500 border-amber-500/30" },
    BOTH: { label: "Missing both", tone: "bg-rose-500/10 text-rose-500 border-rose-500/30" },
};

const ACTION_LABELS: Record<AutoFixResult["action"], { label: string; tone: string; icon: React.ReactNode }> = {
    FIXED: { label: "Fixed", tone: "text-green-500", icon: <CheckCircle2 className="w-3.5 h-3.5" /> },
    SHIFT_ASSIGNED_LOCAL: { label: "Shift assigned", tone: "text-green-500", icon: <CheckCircle2 className="w-3.5 h-3.5" /> },
    NEEDS_MANUAL_SHIFT: { label: "Needs manual shift", tone: "text-amber-500", icon: <AlertTriangle className="w-3.5 h-3.5" /> },
    NEEDS_MANUAL_STATEMENT: { label: "Needs manual statement", tone: "text-amber-500", icon: <AlertTriangle className="w-3.5 h-3.5" /> },
    NO_STATEMENT_YET: { label: "Waiting for statement", tone: "text-blue-500", icon: <Hourglass className="w-3.5 h-3.5" /> },
    NOOP: { label: "Already fixed", tone: "text-muted-foreground", icon: <CheckCircle2 className="w-3.5 h-3.5" /> },
    ERROR: { label: "Error", tone: "text-rose-500", icon: <XCircle className="w-3.5 h-3.5" /> },
};

function formatINR(n: number) {
    return n.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function formatDate(iso?: string) {
    if (!iso) return "—";
    try {
        return new Date(iso).toLocaleString("en-IN", {
            day: "2-digit", month: "short", year: "2-digit", hour: "2-digit", minute: "2-digit",
        });
    } catch {
        return iso;
    }
}

export default function OrphanBillsPage() {
    const toast = useToast();

    const [bills, setBills] = useState<OrphanBill[]>([]);
    const [loading, setLoading] = useState(true);
    const [includeHistorical, setIncludeHistorical] = useState(false);
    const [failureFilter, setFailureFilter] = useState<FailureFilter>("ALL");
    const [search, setSearch] = useState("");
    const [busyBillId, setBusyBillId] = useState<number | null>(null);
    const [bulkRunning, setBulkRunning] = useState(false);
    const [bulkResult, setBulkResult] = useState<BulkAutoFixResult | null>(null);
    const [manualBill, setManualBill] = useState<OrphanBill | null>(null);
    const [manualShiftId, setManualShiftId] = useState<number | null>(null);
    const [manualSubmitting, setManualSubmitting] = useState(false);

    const reload = async () => {
        setLoading(true);
        try {
            const data = await getOrphanBills(includeHistorical);
            setBills(data);
        } catch (err) {
            console.error(err);
            toast.error("Failed to load orphan bills");
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        reload();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [includeHistorical]);

    const filtered = useMemo(() => {
        const q = search.trim().toLowerCase();
        return bills.filter(b => {
            if (failureFilter !== "ALL" && b.failureType !== failureFilter) return false;
            if (q) {
                const hay = `${b.billNo ?? ""} ${b.customerName ?? ""}`.toLowerCase();
                if (!hay.includes(q)) return false;
            }
            return true;
        });
    }, [bills, failureFilter, search]);

    const { page, setPage, totalPages, totalElements, pageSize, paginatedData } = useClientPagination(filtered, 15);

    const handleAutoFix = async (bill: OrphanBill) => {
        setBusyBillId(bill.id);
        try {
            const result = await autoFixOrphan(bill.id);
            const meta = ACTION_LABELS[result.action];
            const message = result.action === "FIXED"
                ? `${bill.billNo}: linked to ${result.statementLinkedNo ?? "statement"}`
                : result.action === "SHIFT_ASSIGNED_LOCAL"
                    ? `${bill.billNo}: shift assigned`
                    : `${bill.billNo}: ${meta.label}${result.reason ? ` — ${result.reason}` : ""}`;
            if (result.action === "FIXED" || result.action === "SHIFT_ASSIGNED_LOCAL") {
                toast.success(message);
            } else if (result.action === "ERROR") {
                toast.error(message);
            } else {
                toast.info(message);
            }
            await reload();
        } catch (err) {
            console.error(err);
            toast.error("Auto-fix failed");
        } finally {
            setBusyBillId(null);
        }
    };

    const handleAutoFixAll = async () => {
        const fixableCount = filtered.filter(b => b.suggestedShiftId != null || b.suggestedStatementId != null).length;
        if (fixableCount === 0) {
            toast.info("No bills with auto-fixable suggestions in current filter");
            return;
        }
        if (!confirm(
            `Auto-fix ${filtered.length} orphan bills?\n\n` +
            `This will assign shifts and attach to draft statements where matches are found.\n` +
            `Bills needing manual intervention will be flagged in the result.`
        )) return;
        setBulkRunning(true);
        try {
            const result = await autoFixAllOrphans(includeHistorical);
            setBulkResult(result);
            toast.success(
                `Bulk fix done: ${result.fixed} fixed, ${result.needsManual} need manual, ${result.waitingForStatement} waiting`
            );
            await reload();
        } catch (err) {
            console.error(err);
            toast.error("Bulk fix failed");
        } finally {
            setBulkRunning(false);
        }
    };

    const openManual = (bill: OrphanBill) => {
        setManualBill(bill);
        setManualShiftId(bill.suggestedShiftId ?? bill.shiftId ?? null);
    };

    const handleManualShift = async () => {
        if (!manualBill || manualShiftId == null) return;
        setManualSubmitting(true);
        try {
            await setBillShift(manualBill.id, manualShiftId);
            // Re-run autoFix to attempt statement linkage now that shift is set.
            const result = await autoFixOrphan(manualBill.id);
            toast.success(`${manualBill.billNo}: shift set to #${manualShiftId}${
                result.action === "FIXED" && result.statementLinkedNo
                    ? `, linked to ${result.statementLinkedNo}` : ""
            }`);
            setManualBill(null);
            await reload();
        } catch (err) {
            console.error(err);
            toast.error("Failed to set shift");
        } finally {
            setManualSubmitting(false);
        }
    };

    const handleMarkIndependent = async () => {
        if (!manualBill) return;
        if (!confirm(`Mark ${manualBill.billNo} as independent? It will be removable from the orphan list and payable directly.`)) return;
        setManualSubmitting(true);
        try {
            await markInvoiceIndependent(manualBill.id);
            toast.success(`${manualBill.billNo}: marked independent`);
            setManualBill(null);
            await reload();
        } catch (err) {
            console.error(err);
            toast.error("Failed to mark independent");
        } finally {
            setManualSubmitting(false);
        }
    };

    // Group filtered bills by customer for visual separators in the table
    const groupedRows = useMemo(() => {
        const result: Array<{ kind: "header" | "row"; row?: OrphanBill; customerId?: number; customerName?: string }> = [];
        let lastCustomer: number | undefined = undefined;
        for (const row of paginatedData) {
            if (row.customerId !== lastCustomer) {
                result.push({ kind: "header", customerId: row.customerId, customerName: row.customerName });
                lastCustomer = row.customerId;
            }
            result.push({ kind: "row", row });
        }
        return result;
    }, [paginatedData]);

    const totalAmount = filtered.reduce((s, b) => s + (b.netAmount || 0), 0);

    return (
        <PermissionGate permission="PAYMENT_UPDATE">
            <div className="p-4 md:p-6 space-y-4">
                {/* Header */}
                <div className="flex flex-col md:flex-row md:items-end md:justify-between gap-3">
                    <div>
                        <h1 className="text-2xl font-bold text-foreground flex items-center gap-2">
                            <AlertTriangle className="w-6 h-6 text-amber-500" />
                            Orphan Bills
                        </h1>
                        <p className="text-sm text-muted-foreground mt-1">
                            Credit bills with missing shift assignment or statement linkage. Auto-fix mirrors the
                            recovery transaction we ran 14× on 2026-05-04.
                        </p>
                    </div>
                    <div className="flex items-center gap-3">
                        <button
                            onClick={() => setIncludeHistorical(v => !v)}
                            className={`px-3 py-1.5 rounded-lg text-xs font-medium border transition-colors flex items-center gap-1.5 ${
                                includeHistorical
                                    ? "bg-rose-500/10 text-rose-500 border-rose-500/30"
                                    : "bg-muted text-muted-foreground border-border hover:bg-muted/70"
                            }`}
                            title="Toggle pre-2026 legacy MySQL-import bills (501 bills, ₹14.66 lakh)"
                        >
                            <History className="w-3.5 h-3.5" />
                            {includeHistorical ? "Including historical" : "Hide historical"}
                        </button>
                        <button
                            onClick={reload}
                            className="px-3 py-1.5 rounded-lg text-xs font-medium border border-border text-muted-foreground hover:bg-muted transition-colors flex items-center gap-1.5"
                        >
                            <RefreshCw className="w-3.5 h-3.5" />
                            Reload
                        </button>
                    </div>
                </div>

                {/* KPIs */}
                <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
                    <GlassCard className="p-4">
                        <div className="text-xs text-muted-foreground">Total orphans</div>
                        <div className="text-2xl font-bold text-foreground">{bills.length}</div>
                    </GlassCard>
                    <GlassCard className="p-4">
                        <div className="text-xs text-muted-foreground">In current filter</div>
                        <div className="text-2xl font-bold text-foreground">{filtered.length}</div>
                    </GlassCard>
                    <GlassCard className="p-4">
                        <div className="text-xs text-muted-foreground">Total amount (filter)</div>
                        <div className="text-2xl font-bold text-amber-500">₹{formatINR(totalAmount)}</div>
                    </GlassCard>
                    <GlassCard className="p-4">
                        <div className="text-xs text-muted-foreground">Customers affected</div>
                        <div className="text-2xl font-bold text-foreground">
                            {new Set(filtered.map(b => b.customerId).filter(Boolean)).size}
                        </div>
                    </GlassCard>
                </div>

                {/* Toolbar */}
                <GlassCard className="p-3">
                    <div className="flex flex-col md:flex-row md:items-center gap-3">
                        <div className="flex-1 flex items-center gap-2 px-3 py-1.5 rounded-lg bg-background border border-border">
                            <Search className="w-4 h-4 text-muted-foreground" />
                            <input
                                value={search}
                                onChange={e => setSearch(e.target.value)}
                                placeholder="Search bill no or customer name…"
                                className="flex-1 bg-transparent text-sm outline-none"
                            />
                        </div>
                        <div className="flex items-center gap-2">
                            <Filter className="w-4 h-4 text-muted-foreground" />
                            <select
                                value={failureFilter}
                                onChange={e => setFailureFilter(e.target.value as FailureFilter)}
                                className="px-3 py-1.5 rounded-lg bg-background border border-border text-sm"
                            >
                                <option value="ALL">All failures</option>
                                <option value="NULL_SHIFT">Missing shift only</option>
                                <option value="NO_STATEMENT">Awaiting statement only</option>
                                <option value="BOTH">Missing both</option>
                            </select>
                        </div>
                        <button
                            onClick={handleAutoFixAll}
                            disabled={bulkRunning || filtered.length === 0}
                            className="px-4 py-1.5 rounded-lg text-sm font-medium bg-primary text-primary-foreground hover:bg-primary/90 transition-colors disabled:opacity-50 flex items-center gap-1.5"
                        >
                            <Wand2 className="w-4 h-4" />
                            {bulkRunning ? "Running…" : `Auto-fix ${filtered.length || ""}`}
                        </button>
                    </div>
                </GlassCard>

                {/* Bulk-run summary */}
                {bulkResult && (
                    <GlassCard className="p-3 border-l-4 border-l-primary">
                        <div className="flex items-center justify-between">
                            <div className="text-sm text-foreground">
                                <span className="font-semibold">Last bulk run:</span>{" "}
                                {bulkResult.fixed} fixed, {bulkResult.needsManual} need manual,{" "}
                                {bulkResult.waitingForStatement} waiting for statement
                                {" / "}{bulkResult.total} total
                            </div>
                            <button
                                onClick={() => setBulkResult(null)}
                                className="text-xs text-muted-foreground hover:text-foreground"
                            >
                                Dismiss
                            </button>
                        </div>
                    </GlassCard>
                )}

                {/* Table */}
                <GlassCard>
                    {loading ? (
                        <div className="p-12 text-center text-muted-foreground">Loading orphan bills…</div>
                    ) : filtered.length === 0 ? (
                        <div className="p-12 text-center">
                            <CheckCircle2 className="w-10 h-10 text-green-500 mx-auto mb-3" />
                            <p className="text-sm text-muted-foreground">No orphan bills in current filter. The system is clean.</p>
                        </div>
                    ) : (
                        <>
                            <div className="overflow-x-auto">
                                <table className="w-full text-sm">
                                    <thead>
                                        <tr className="text-xs uppercase tracking-widest text-muted-foreground border-b border-border">
                                            <th className="text-left px-4 py-3">Bill</th>
                                            <th className="text-left px-4 py-3">Date</th>
                                            <th className="text-right px-4 py-3">Amount</th>
                                            <th className="text-left px-4 py-3">Failure</th>
                                            <th className="text-left px-4 py-3">Suggested fix</th>
                                            <th className="text-right px-4 py-3">Actions</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {groupedRows.map((entry, i) => {
                                            if (entry.kind === "header") {
                                                return (
                                                    <tr key={`hdr-${entry.customerId}-${i}`} className="bg-muted/30">
                                                        <td colSpan={6} className="px-4 py-2 text-xs font-bold uppercase tracking-widest text-muted-foreground">
                                                            {entry.customerName ?? `Customer #${entry.customerId}`}
                                                            <span className="ml-2 text-foreground/70 normal-case">
                                                                ({entry.customerId})
                                                            </span>
                                                        </td>
                                                    </tr>
                                                );
                                            }
                                            const b = entry.row!;
                                            const failureMeta = FAILURE_LABELS[b.failureType];
                                            const canAutoFix = b.suggestedShiftId != null || b.suggestedStatementId != null
                                                || (b.shiftId != null && b.suggestedStatementId != null);
                                            const suggestion = [
                                                b.suggestedShiftId ? `Shift ${b.suggestedShiftId}` : null,
                                                b.suggestedStatementNo ? `→ ${b.suggestedStatementNo}` : null,
                                            ].filter(Boolean).join(" ");
                                            return (
                                                <tr key={b.id} className="border-b border-border/50 hover:bg-muted/30 transition-colors">
                                                    <td className="px-4 py-2 font-mono text-xs">{b.billNo ?? `#${b.id}`}</td>
                                                    <td className="px-4 py-2 text-xs text-muted-foreground">{formatDate(b.billDate)}</td>
                                                    <td className="px-4 py-2 text-right font-semibold">₹{formatINR(b.netAmount)}</td>
                                                    <td className="px-4 py-2">
                                                        <span className={`px-2 py-0.5 rounded text-[10px] font-bold uppercase tracking-widest border ${failureMeta.tone}`}>
                                                            {failureMeta.label}
                                                        </span>
                                                    </td>
                                                    <td className="px-4 py-2 text-xs text-muted-foreground">
                                                        {suggestion || <span className="italic">no auto-suggestion</span>}
                                                    </td>
                                                    <td className="px-4 py-2 text-right">
                                                        <div className="inline-flex items-center gap-1.5">
                                                            <button
                                                                onClick={() => handleAutoFix(b)}
                                                                disabled={busyBillId === b.id || !canAutoFix}
                                                                className="px-2.5 py-1 rounded text-xs font-medium bg-green-500/10 text-green-500 border border-green-500/30 hover:bg-green-500/20 transition-colors disabled:opacity-40 disabled:cursor-not-allowed inline-flex items-center gap-1"
                                                                title={canAutoFix ? "Auto-fix" : "No auto-suggestion — use Manual"}
                                                            >
                                                                <Wand2 className="w-3 h-3" />
                                                                {busyBillId === b.id ? "…" : "Auto-fix"}
                                                            </button>
                                                            <button
                                                                onClick={() => openManual(b)}
                                                                className="px-2.5 py-1 rounded text-xs font-medium bg-muted text-foreground border border-border hover:bg-muted/70 transition-colors inline-flex items-center gap-1"
                                                            >
                                                                <Link2Off className="w-3 h-3" />
                                                                Manual
                                                            </button>
                                                        </div>
                                                    </td>
                                                </tr>
                                            );
                                        })}
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
                        </>
                    )}
                </GlassCard>

                {/* Manual drawer */}
                {manualBill && (
                    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4" onClick={() => !manualSubmitting && setManualBill(null)}>
                        <div className="bg-card border border-border rounded-2xl shadow-2xl max-w-md w-full p-5 space-y-4" onClick={e => e.stopPropagation()}>
                            <div>
                                <h3 className="text-lg font-bold text-foreground">Manual fix — {manualBill.billNo}</h3>
                                <p className="text-xs text-muted-foreground mt-1">
                                    {manualBill.customerName} ({manualBill.partyType ?? "Local"}) · ₹{formatINR(manualBill.netAmount)} · bill date {formatDate(manualBill.billDate)}
                                </p>
                            </div>

                            <div className="space-y-2">
                                <div className="text-xs font-medium text-foreground">Assign shift</div>
                                <ShiftPicker
                                    value={manualShiftId}
                                    onChange={setManualShiftId}
                                    backDatedAmber={false}
                                />
                                <p className="text-[11px] text-muted-foreground">
                                    Pick the shift this bill belongs to. After saving, the system will retry attaching to a covering DRAFT statement.
                                </p>
                            </div>

                            <div className="flex flex-col gap-2 pt-2 border-t border-border">
                                <button
                                    onClick={handleManualShift}
                                    disabled={manualSubmitting || manualShiftId == null}
                                    className="w-full px-4 py-2 rounded-lg text-sm font-medium bg-primary text-primary-foreground hover:bg-primary/90 transition-colors disabled:opacity-50"
                                >
                                    {manualSubmitting ? "Saving…" : "Save shift + retry attach"}
                                </button>
                                <button
                                    onClick={handleMarkIndependent}
                                    disabled={manualSubmitting}
                                    className="w-full px-4 py-2 rounded-lg text-sm font-medium bg-amber-500/10 text-amber-500 border border-amber-500/30 hover:bg-amber-500/20 transition-colors disabled:opacity-50"
                                >
                                    Mark Independent (skip statement)
                                </button>
                                <button
                                    onClick={() => setManualBill(null)}
                                    disabled={manualSubmitting}
                                    className="w-full px-4 py-2 rounded-lg text-sm font-medium border border-border text-muted-foreground hover:bg-muted transition-colors"
                                >
                                    Cancel
                                </button>
                            </div>
                        </div>
                    </div>
                )}
            </div>
        </PermissionGate>
    );
}
