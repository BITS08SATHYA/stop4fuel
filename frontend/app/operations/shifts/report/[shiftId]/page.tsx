"use client";

import { useEffect, useState, useCallback } from "react";
import { useParams, useRouter } from "next/navigation";
import { GlassCard } from "@/components/ui/glass-card";
import {
    getShiftReport,
    editReportLineItem,
    finalizeShiftReport,
    recomputeShiftReport,
    getReportAuditLog,
    transferReportEntry,
    getAllShiftReports,
    ShiftClosingReport,
    ReportLineItem,
    ReportCashBillBreakdown,
    ReportAuditLog,
} from "@/lib/api/station";
import {
    FileText,
    RefreshCw,
    Lock,
    CheckCircle2,
    Edit3,
    ArrowRightLeft,
    Clock,
    ChevronDown,
    ChevronUp,
    AlertCircle,
    Printer,
} from "lucide-react";

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

function formatLitres(val?: number) {
    if (val == null) return "-";
    return val.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

const CATEGORY_LABELS: Record<string, string> = {
    FUEL_SALES: "Fuel Sales",
    OIL_SALES: "Oil / Lubricant Sales",
    BILL_PAYMENT: "Bill Payments",
    STATEMENT_PAYMENT: "Statement Payments",
    EXTERNAL_INFLOW: "External Cash Inflow",
    CREDIT_BILLS: "Credit Bills",
    CARD: "Card Advance",
    CCMS: "CCMS Advance",
    UPI: "UPI Advance",
    BANK: "Bank Transfer Advance",
    CHEQUE: "Cheque Advance",
    CASH_ADVANCE: "Cash Advance",
    HOME_ADVANCE: "Home Advance",
    EXPENSES: "Expenses",
    INCENTIVE: "Incentive / Discount",
    SALARY_ADVANCE: "Salary Advance",
    INFLOW_REPAYMENT: "Cash Inflow Repayment",
};

export default function ShiftReportPage() {
    const params = useParams();
    const router = useRouter();
    const shiftId = Number(params.shiftId);

    const [report, setReport] = useState<ShiftClosingReport | null>(null);
    const [auditLogs, setAuditLogs] = useState<ReportAuditLog[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [showAuditLog, setShowAuditLog] = useState(false);

    // Edit state
    const [editingItemId, setEditingItemId] = useState<number | null>(null);
    const [editAmount, setEditAmount] = useState("");
    const [editReason, setEditReason] = useState("");

    // Transfer state
    const [transferItemId, setTransferItemId] = useState<number | null>(null);
    const [targetReportId, setTargetReportId] = useState("");
    const [transferReason, setTransferReason] = useState("");
    const [draftReports, setDraftReports] = useState<ShiftClosingReport[]>([]);

    // Finalize confirmation
    const [showFinalizeConfirm, setShowFinalizeConfirm] = useState(false);

    const loadReport = useCallback(async () => {
        try {
            setIsLoading(true);
            const data = await getShiftReport(shiftId);
            setReport(data);
            setError(null);
        } catch (err: unknown) {
            const msg = err instanceof Error ? err.message : "Failed to load report";
            setError(msg);
        } finally {
            setIsLoading(false);
        }
    }, [shiftId]);

    const loadAuditLog = useCallback(async () => {
        if (!report) return;
        try {
            const logs = await getReportAuditLog(report.id);
            setAuditLogs(logs);
        } catch {
            // silent
        }
    }, [report]);

    useEffect(() => {
        loadReport();
    }, [loadReport]);

    useEffect(() => {
        if (showAuditLog) loadAuditLog();
    }, [showAuditLog, loadAuditLog]);

    const handleRecompute = async () => {
        if (!report) return;
        try {
            const updated = await recomputeShiftReport(report.id);
            setReport(updated);
        } catch (err: unknown) {
            alert(err instanceof Error ? err.message : "Recompute failed");
        }
    };

    const handleFinalize = async () => {
        if (!report) return;
        try {
            const updated = await finalizeShiftReport(report.id, "manager");
            setReport(updated);
            setShowFinalizeConfirm(false);
        } catch (err: unknown) {
            alert(err instanceof Error ? err.message : "Finalize failed");
        }
    };

    const handleEditSave = async () => {
        if (!report || editingItemId == null) return;
        try {
            const updated = await editReportLineItem(
                report.id, editingItemId, parseFloat(editAmount), editReason || undefined
            );
            setReport(updated);
            setEditingItemId(null);
            setEditAmount("");
            setEditReason("");
        } catch (err: unknown) {
            alert(err instanceof Error ? err.message : "Edit failed");
        }
    };

    const handleTransfer = async () => {
        if (!report || transferItemId == null || !targetReportId) return;
        try {
            const updated = await transferReportEntry(
                report.id, transferItemId, parseInt(targetReportId), transferReason || undefined
            );
            setReport(updated);
            setTransferItemId(null);
            setTargetReportId("");
            setTransferReason("");
        } catch (err: unknown) {
            alert(err instanceof Error ? err.message : "Transfer failed");
        }
    };

    const openTransferModal = async (itemId: number) => {
        setTransferItemId(itemId);
        try {
            const reports = await getAllShiftReports("DRAFT");
            setDraftReports(reports.filter(r => r.id !== report?.id));
        } catch {
            // silent
        }
    };

    if (isLoading) {
        return (
            <div className="p-6 flex items-center justify-center min-h-[60vh]">
                <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary" />
            </div>
        );
    }

    if (error || !report) {
        return (
            <div className="p-6">
                <GlassCard>
                    <div className="flex items-center gap-3 text-red-500 p-6">
                        <AlertCircle className="w-5 h-5" />
                        <span>{error || "Report not found"}</span>
                    </div>
                    <div className="px-6 pb-6">
                        <button onClick={() => router.push("/operations/shifts")}
                            className="text-sm text-primary hover:underline">
                            Back to Shifts
                        </button>
                    </div>
                </GlassCard>
            </div>
        );
    }

    const isDraft = report.status === "DRAFT";
    const revenueItems = (report.lineItems || [])
        .filter(i => i.section === "REVENUE" && !i.transferredToReportId)
        .sort((a, b) => a.sortOrder - b.sortOrder);
    const advanceItems = (report.lineItems || [])
        .filter(i => i.section === "ADVANCE" && !i.transferredToReportId)
        .sort((a, b) => a.sortOrder - b.sortOrder);

    return (
        <div className="p-6 space-y-6 max-w-7xl mx-auto">
            {/* Header */}
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                <div>
                    <h1 className="text-2xl font-bold flex items-center gap-2">
                        <FileText className="w-6 h-6 text-primary" />
                        Shift Closing Report
                    </h1>
                    <p className="text-sm text-muted-foreground mt-1">
                        Shift #{report.shift?.id} &mdash; {formatDateTime(report.shift?.startTime)} to {formatDateTime(report.shift?.endTime)}
                    </p>
                </div>
                <div className="flex items-center gap-3">
                    <span className={`px-3 py-1 rounded-full text-xs font-semibold ${
                        isDraft ? "bg-amber-500/10 text-amber-500" : "bg-green-500/10 text-green-500"
                    }`}>
                        {report.status}
                    </span>
                    {isDraft && (
                        <>
                            <button onClick={handleRecompute}
                                className="flex items-center gap-1.5 px-3 py-1.5 text-sm rounded-lg bg-blue-500/10 text-blue-500 hover:bg-blue-500/20 transition-colors">
                                <RefreshCw className="w-4 h-4" /> Recompute
                            </button>
                            <button onClick={() => setShowFinalizeConfirm(true)}
                                className="flex items-center gap-1.5 px-3 py-1.5 text-sm rounded-lg bg-green-500/10 text-green-600 hover:bg-green-500/20 transition-colors">
                                <Lock className="w-4 h-4" /> Finalize
                            </button>
                        </>
                    )}
                    <button onClick={() => window.print()}
                        className="flex items-center gap-1.5 px-3 py-1.5 text-sm rounded-lg bg-muted text-muted-foreground hover:bg-muted/80 transition-colors">
                        <Printer className="w-4 h-4" /> Print
                    </button>
                </div>
            </div>

            {/* Summary Cards */}
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                <SummaryCard label="Total Revenue" value={report.totalRevenue} color="text-green-500" />
                <SummaryCard label="Total Advances" value={report.totalAdvances} color="text-red-500" />
                <SummaryCard label="Balance (Cash in Hand)" value={report.balance}
                    color={report.balance >= 0 ? "text-blue-500" : "text-red-600"} />
                <SummaryCard label="Cash Bill Amount" value={report.cashBillAmount} color="text-purple-500" />
            </div>

            {/* Revenue Table */}
            <GlassCard>
                <div className="p-5">
                    <h2 className="text-lg font-semibold mb-4 text-green-500">Revenue (Money IN)</h2>
                    <div className="overflow-x-auto">
                        <table className="w-full text-sm">
                            <thead>
                                <tr className="border-b border-border text-muted-foreground">
                                    <th className="text-left py-2 px-3 font-medium">Item</th>
                                    <th className="text-right py-2 px-3 font-medium">Litres</th>
                                    <th className="text-right py-2 px-3 font-medium">Rate</th>
                                    <th className="text-right py-2 px-3 font-medium">Amount</th>
                                    {isDraft && <th className="text-center py-2 px-3 font-medium w-24">Actions</th>}
                                </tr>
                            </thead>
                            <tbody>
                                {revenueItems.map(item => (
                                    <LineItemRow key={item.id} item={item} isDraft={isDraft}
                                        editingItemId={editingItemId} editAmount={editAmount}
                                        editReason={editReason} setEditingItemId={setEditingItemId}
                                        setEditAmount={setEditAmount} setEditReason={setEditReason}
                                        onSave={handleEditSave} onTransfer={openTransferModal} />
                                ))}
                                <tr className="border-t-2 border-border font-bold">
                                    <td className="py-2 px-3" colSpan={3}>TOTAL REVENUE</td>
                                    <td className="py-2 px-3 text-right text-green-500">
                                        {formatCurrency(report.totalRevenue)}
                                    </td>
                                    {isDraft && <td />}
                                </tr>
                            </tbody>
                        </table>
                    </div>
                </div>
            </GlassCard>

            {/* Advances Table */}
            <GlassCard>
                <div className="p-5">
                    <h2 className="text-lg font-semibold mb-4 text-red-500">Advances / Costs (Money OUT)</h2>
                    <div className="overflow-x-auto">
                        <table className="w-full text-sm">
                            <thead>
                                <tr className="border-b border-border text-muted-foreground">
                                    <th className="text-left py-2 px-3 font-medium">Item</th>
                                    <th className="text-right py-2 px-3 font-medium">Amount</th>
                                    {isDraft && <th className="text-center py-2 px-3 font-medium w-24">Actions</th>}
                                </tr>
                            </thead>
                            <tbody>
                                {advanceItems.map(item => (
                                    <tr key={item.id} className="border-b border-border/50 hover:bg-muted/30">
                                        <td className="py-2 px-3">
                                            {item.label || CATEGORY_LABELS[item.category] || item.category}
                                            {item.originalAmount != null && (
                                                <span className="text-xs text-amber-500 ml-2">
                                                    (was {formatCurrency(item.originalAmount)})
                                                </span>
                                            )}
                                        </td>
                                        <td className="py-2 px-3 text-right">
                                            {editingItemId === item.id ? (
                                                <div className="flex flex-col items-end gap-1">
                                                    <input type="number" value={editAmount}
                                                        onChange={e => setEditAmount(e.target.value)}
                                                        className="w-32 px-2 py-1 border rounded text-right text-sm bg-background" />
                                                    <input type="text" value={editReason} placeholder="Reason"
                                                        onChange={e => setEditReason(e.target.value)}
                                                        className="w-48 px-2 py-1 border rounded text-sm bg-background" />
                                                    <div className="flex gap-1">
                                                        <button onClick={handleEditSave}
                                                            className="text-xs px-2 py-1 rounded bg-green-500/10 text-green-500">Save</button>
                                                        <button onClick={() => setEditingItemId(null)}
                                                            className="text-xs px-2 py-1 rounded bg-muted text-muted-foreground">Cancel</button>
                                                    </div>
                                                </div>
                                            ) : formatCurrency(item.amount)}
                                        </td>
                                        {isDraft && (
                                            <td className="py-2 px-3 text-center">
                                                <div className="flex items-center justify-center gap-1">
                                                    <button onClick={() => { setEditingItemId(item.id); setEditAmount(String(item.amount)); }}
                                                        className="p-1 rounded hover:bg-muted" title="Edit">
                                                        <Edit3 className="w-3.5 h-3.5 text-blue-500" />
                                                    </button>
                                                    <button onClick={() => openTransferModal(item.id)}
                                                        className="p-1 rounded hover:bg-muted" title="Transfer">
                                                        <ArrowRightLeft className="w-3.5 h-3.5 text-amber-500" />
                                                    </button>
                                                </div>
                                            </td>
                                        )}
                                    </tr>
                                ))}
                                <tr className="border-t-2 border-border font-bold">
                                    <td className="py-2 px-3">TOTAL ADVANCES</td>
                                    <td className="py-2 px-3 text-right text-red-500">
                                        {formatCurrency(report.totalAdvances)}
                                    </td>
                                    {isDraft && <td />}
                                </tr>
                            </tbody>
                        </table>
                    </div>
                </div>
            </GlassCard>

            {/* Cash Bill Sales Breakdown */}
            {report.cashBillBreakdowns && report.cashBillBreakdowns.length > 0 && (
                <GlassCard>
                    <div className="p-5">
                        <h2 className="text-lg font-semibold mb-4 text-purple-500">Cash Bill Sales Breakdown (Litres)</h2>
                        <div className="overflow-x-auto">
                            <table className="w-full text-sm">
                                <thead>
                                    <tr className="border-b border-border text-muted-foreground">
                                        <th className="text-left py-2 px-3 font-medium">Product</th>
                                        <th className="text-right py-2 px-3 font-medium">Cash</th>
                                        <th className="text-right py-2 px-3 font-medium">Card</th>
                                        <th className="text-right py-2 px-3 font-medium">CCMS</th>
                                        <th className="text-right py-2 px-3 font-medium">UPI</th>
                                        <th className="text-right py-2 px-3 font-medium">Cheque</th>
                                        <th className="text-right py-2 px-3 font-medium">Total</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {report.cashBillBreakdowns.map(bd => (
                                        <tr key={bd.id} className="border-b border-border/50 hover:bg-muted/30">
                                            <td className="py-2 px-3 font-medium">{bd.productName}</td>
                                            <td className="py-2 px-3 text-right">{formatLitres(bd.cashLitres)}</td>
                                            <td className="py-2 px-3 text-right">{formatLitres(bd.cardLitres)}</td>
                                            <td className="py-2 px-3 text-right">{formatLitres(bd.ccmsLitres)}</td>
                                            <td className="py-2 px-3 text-right">{formatLitres(bd.upiLitres)}</td>
                                            <td className="py-2 px-3 text-right">{formatLitres(bd.chequeLitres)}</td>
                                            <td className="py-2 px-3 text-right font-semibold">{formatLitres(bd.totalLitres)}</td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    </div>
                </GlassCard>
            )}

            {/* Audit Log */}
            <GlassCard>
                <div className="p-5">
                    <button onClick={() => setShowAuditLog(!showAuditLog)}
                        className="flex items-center gap-2 text-lg font-semibold w-full text-left">
                        <Clock className="w-5 h-5 text-muted-foreground" />
                        Audit Log
                        {showAuditLog ? <ChevronUp className="w-4 h-4 ml-auto" /> : <ChevronDown className="w-4 h-4 ml-auto" />}
                    </button>
                    {showAuditLog && (
                        <div className="mt-4 space-y-2">
                            {auditLogs.length === 0 ? (
                                <p className="text-sm text-muted-foreground">No changes recorded yet.</p>
                            ) : (
                                auditLogs.map(log => (
                                    <div key={log.id} className="flex items-start gap-3 text-sm p-2 rounded bg-muted/30">
                                        <span className="text-xs text-muted-foreground whitespace-nowrap mt-0.5">
                                            {formatDateTime(log.performedAt)}
                                        </span>
                                        <div>
                                            <span className={`text-xs font-semibold px-1.5 py-0.5 rounded ${
                                                log.action === "FINALIZED" ? "bg-green-500/10 text-green-500" :
                                                log.action === "LINE_ITEM_EDITED" ? "bg-amber-500/10 text-amber-500" :
                                                "bg-blue-500/10 text-blue-500"
                                            }`}>{log.action}</span>
                                            <span className="ml-2">{log.description}</span>
                                            {log.previousValue != null && (
                                                <span className="ml-2 text-muted-foreground">
                                                    ({formatCurrency(log.previousValue)} → {formatCurrency(log.newValue)})
                                                </span>
                                            )}
                                        </div>
                                    </div>
                                ))
                            )}
                        </div>
                    )}
                </div>
            </GlassCard>

            {/* Transfer Modal */}
            {transferItemId != null && (
                <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
                    <div className="bg-card rounded-xl p-6 w-full max-w-md shadow-2xl">
                        <h3 className="text-lg font-semibold mb-4">Transfer Entry</h3>
                        <div className="space-y-3">
                            <div>
                                <label className="text-sm font-medium text-muted-foreground">Target Report</label>
                                <select value={targetReportId} onChange={e => setTargetReportId(e.target.value)}
                                    className="w-full mt-1 px-3 py-2 border rounded-lg bg-background text-sm">
                                    <option value="">Select a draft report...</option>
                                    {draftReports.map(r => (
                                        <option key={r.id} value={r.id}>
                                            Shift #{r.shift?.id} — {formatDateTime(r.reportDate)}
                                        </option>
                                    ))}
                                </select>
                            </div>
                            <div>
                                <label className="text-sm font-medium text-muted-foreground">Reason</label>
                                <input type="text" value={transferReason}
                                    onChange={e => setTransferReason(e.target.value)}
                                    placeholder="Why transfer?"
                                    className="w-full mt-1 px-3 py-2 border rounded-lg bg-background text-sm" />
                            </div>
                        </div>
                        <div className="flex justify-end gap-2 mt-5">
                            <button onClick={() => setTransferItemId(null)}
                                className="px-4 py-2 text-sm rounded-lg bg-muted text-muted-foreground">Cancel</button>
                            <button onClick={handleTransfer} disabled={!targetReportId}
                                className="px-4 py-2 text-sm rounded-lg bg-primary text-primary-foreground disabled:opacity-50">
                                Transfer
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* Finalize Confirmation */}
            {showFinalizeConfirm && (
                <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
                    <div className="bg-card rounded-xl p-6 w-full max-w-sm shadow-2xl">
                        <h3 className="text-lg font-semibold mb-2">Finalize Report?</h3>
                        <p className="text-sm text-muted-foreground mb-5">
                            This will lock the report permanently. No further edits or transfers will be possible.
                            The shift will be marked as RECONCILED.
                        </p>
                        <div className="flex justify-end gap-2">
                            <button onClick={() => setShowFinalizeConfirm(false)}
                                className="px-4 py-2 text-sm rounded-lg bg-muted text-muted-foreground">Cancel</button>
                            <button onClick={handleFinalize}
                                className="px-4 py-2 text-sm rounded-lg bg-green-600 text-white">
                                <CheckCircle2 className="w-4 h-4 inline mr-1" /> Finalize
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}

function SummaryCard({ label, value, color }: { label: string; value: number; color: string }) {
    return (
        <GlassCard>
            <div className="p-4">
                <p className="text-xs font-medium text-muted-foreground uppercase tracking-wide">{label}</p>
                <p className={`text-2xl font-bold mt-1 ${color}`}>{formatCurrency(value)}</p>
            </div>
        </GlassCard>
    );
}

function LineItemRow({
    item, isDraft, editingItemId, editAmount, editReason,
    setEditingItemId, setEditAmount, setEditReason, onSave, onTransfer
}: {
    item: ReportLineItem;
    isDraft: boolean;
    editingItemId: number | null;
    editAmount: string;
    editReason: string;
    setEditingItemId: (id: number | null) => void;
    setEditAmount: (v: string) => void;
    setEditReason: (v: string) => void;
    onSave: () => void;
    onTransfer: (id: number) => void;
}) {
    const isEditing = editingItemId === item.id;

    return (
        <tr className="border-b border-border/50 hover:bg-muted/30">
            <td className="py-2 px-3">
                {item.label || CATEGORY_LABELS[item.category] || item.category}
                {item.originalAmount != null && (
                    <span className="text-xs text-amber-500 ml-2">
                        (was {formatCurrency(item.originalAmount)})
                    </span>
                )}
                {item.transferredFromReportId && (
                    <span className="text-xs text-blue-500 ml-2">(transferred in)</span>
                )}
            </td>
            <td className="py-2 px-3 text-right">{formatLitres(item.quantity)}</td>
            <td className="py-2 px-3 text-right">{item.rate != null ? formatCurrency(item.rate) : "-"}</td>
            <td className="py-2 px-3 text-right">
                {isEditing ? (
                    <div className="flex flex-col items-end gap-1">
                        <input type="number" value={editAmount}
                            onChange={e => setEditAmount(e.target.value)}
                            className="w-32 px-2 py-1 border rounded text-right text-sm bg-background" />
                        <input type="text" value={editReason} placeholder="Reason"
                            onChange={e => setEditReason(e.target.value)}
                            className="w-48 px-2 py-1 border rounded text-sm bg-background" />
                        <div className="flex gap-1">
                            <button onClick={onSave}
                                className="text-xs px-2 py-1 rounded bg-green-500/10 text-green-500">Save</button>
                            <button onClick={() => setEditingItemId(null)}
                                className="text-xs px-2 py-1 rounded bg-muted text-muted-foreground">Cancel</button>
                        </div>
                    </div>
                ) : formatCurrency(item.amount)}
            </td>
            {isDraft && (
                <td className="py-2 px-3 text-center">
                    <div className="flex items-center justify-center gap-1">
                        <button onClick={() => { setEditingItemId(item.id); setEditAmount(String(item.amount)); }}
                            className="p-1 rounded hover:bg-muted" title="Edit">
                            <Edit3 className="w-3.5 h-3.5 text-blue-500" />
                        </button>
                        <button onClick={() => onTransfer(item.id)}
                            className="p-1 rounded hover:bg-muted" title="Transfer">
                            <ArrowRightLeft className="w-3.5 h-3.5 text-amber-500" />
                        </button>
                    </div>
                </td>
            )}
        </tr>
    );
}
