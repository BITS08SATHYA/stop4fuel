"use client";

import { useEffect, useState, useCallback } from "react";
import { useParams, useRouter } from "next/navigation";
import { GlassCard } from "@/components/ui/glass-card";
import {
    getShiftReport,
    getShiftReportPrintData,
    editReportLineItem,
    finalizeShiftReport,
    recomputeShiftReport,
    getReportAuditLog,
    transferReportEntry,
    getAllShiftReports,
    getShiftReportPdfUrl,
    ShiftClosingReport,
    ReportLineItem,
    ReportAuditLog,
    ShiftReportPrintData,
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
    ArrowLeft,
    Download,
} from "lucide-react";

function fmtDT(dt?: string) {
    if (!dt) return "-";
    return new Date(dt).toLocaleString("en-IN", { day: "2-digit", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" });
}
function fmtDate(dt?: string) {
    if (!dt) return "-";
    return new Date(dt).toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "numeric" });
}
function fmtCur(v?: number) {
    if (v == null) return "0.00";
    return v.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}
function fmtQty(v?: number) {
    if (v == null || v === 0) return "-";
    return v.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

const CATEGORY_LABELS: Record<string, string> = {
    FUEL_SALES: "Fuel Sales", OIL_SALES: "Oil / Lubricant", BILL_PAYMENT: "Bill Payments",
    STATEMENT_PAYMENT: "Statement Payments", EXTERNAL_INFLOW: "External Cash Inflow",
    CREDIT_BILLS: "Credit Bills", CARD: "Card", CCMS: "CCMS", UPI: "UPI",
    BANK: "Bank Transfer", CHEQUE: "Cheque", CASH_ADVANCE: "Cash Advance",
    HOME_ADVANCE: "Home Advance", EXPENSES: "Expenses", INCENTIVE: "Incentive / Discount",
    SALARY_ADVANCE: "Salary Advance", INFLOW_REPAYMENT: "Inflow Repayment",
};

export default function ShiftReportPage() {
    const params = useParams();
    const router = useRouter();
    const shiftId = Number(params.shiftId);

    const [report, setReport] = useState<ShiftClosingReport | null>(null);
    const [printData, setPrintData] = useState<ShiftReportPrintData | null>(null);
    const [auditLogs, setAuditLogs] = useState<ReportAuditLog[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [showAuditLog, setShowAuditLog] = useState(false);

    const [editingItemId, setEditingItemId] = useState<number | null>(null);
    const [editAmount, setEditAmount] = useState("");
    const [editReason, setEditReason] = useState("");
    const [transferItemId, setTransferItemId] = useState<number | null>(null);
    const [targetReportId, setTargetReportId] = useState("");
    const [transferReason, setTransferReason] = useState("");
    const [draftReports, setDraftReports] = useState<ShiftClosingReport[]>([]);
    const [showFinalizeConfirm, setShowFinalizeConfirm] = useState(false);

    const loadData = useCallback(async () => {
        try {
            setIsLoading(true);
            const [rpt, pd] = await Promise.all([getShiftReport(shiftId), getShiftReportPrintData(shiftId)]);
            setReport(rpt);
            setPrintData(pd);
            setError(null);
        } catch (err: unknown) {
            setError(err instanceof Error ? err.message : "Failed to load report");
        } finally {
            setIsLoading(false);
        }
    }, [shiftId]);

    useEffect(() => { loadData(); }, [loadData]);
    useEffect(() => {
        if (showAuditLog && report) {
            getReportAuditLog(report.id).then(setAuditLogs).catch(() => {});
        }
    }, [showAuditLog, report]);

    const handleRecompute = async () => {
        if (!report) return;
        try { const u = await recomputeShiftReport(report.id); setReport(u); loadData(); } catch (e: unknown) { alert(e instanceof Error ? e.message : "Failed"); }
    };
    const handleFinalize = async () => {
        if (!report) return;
        try { const u = await finalizeShiftReport(report.id, "manager"); setReport(u); setShowFinalizeConfirm(false); } catch (e: unknown) { alert(e instanceof Error ? e.message : "Failed"); }
    };
    const handleEditSave = async () => {
        if (!report || editingItemId == null) return;
        try { const u = await editReportLineItem(report.id, editingItemId, parseFloat(editAmount), editReason || undefined); setReport(u); setEditingItemId(null); setEditAmount(""); setEditReason(""); } catch (e: unknown) { alert(e instanceof Error ? e.message : "Failed"); }
    };
    const handleTransfer = async () => {
        if (!report || transferItemId == null || !targetReportId) return;
        try { const u = await transferReportEntry(report.id, transferItemId, parseInt(targetReportId), transferReason || undefined); setReport(u); setTransferItemId(null); } catch (e: unknown) { alert(e instanceof Error ? e.message : "Failed"); }
    };
    const openTransferModal = async (itemId: number) => {
        setTransferItemId(itemId);
        try { const r = await getAllShiftReports("DRAFT"); setDraftReports(r.filter(x => x.id !== report?.id)); } catch { /* */ }
    };

    if (isLoading) return <div className="p-6 flex items-center justify-center min-h-[60vh]"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary" /></div>;
    if (error || !report) return (
        <div className="p-6"><GlassCard><div className="flex items-center gap-3 text-red-500 p-6"><AlertCircle className="w-5 h-5" /><span>{error || "Report not found"}</span></div>
        <div className="px-6 pb-6"><button onClick={() => router.push("/operations/shifts")} className="text-sm text-primary hover:underline">Back to Shifts</button></div></GlassCard></div>
    );

    const isDraft = report.status === "DRAFT";
    const revenueItems = (report.lineItems || []).filter(i => i.section === "REVENUE" && !i.transferredToReportId).sort((a, b) => a.sortOrder - b.sortOrder);
    const advanceItems = (report.lineItems || []).filter(i => i.section === "ADVANCE" && !i.transferredToReportId).sort((a, b) => a.sortOrder - b.sortOrder);

    // Group credit bills by customer
    const creditBillsByCustomer: Record<string, ShiftReportPrintData["creditBillDetails"]> = {};
    if (printData) {
        for (const bill of printData.creditBillDetails) {
            const key = bill.customerName || "-";
            if (!creditBillsByCustomer[key]) creditBillsByCustomer[key] = [];
            creditBillsByCustomer[key].push(bill);
        }
    }

    return (
        <>
        {/* === SCREEN VIEW (hidden on print) === */}
        <div className="print:hidden p-6 space-y-6 max-w-7xl mx-auto">
            {/* Header */}
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                <div className="flex items-center gap-3">
                    <button onClick={() => router.push("/operations/shifts")} className="p-2 rounded-lg hover:bg-muted"><ArrowLeft className="w-5 h-5" /></button>
                    <div>
                        <h1 className="text-2xl font-bold flex items-center gap-2"><FileText className="w-6 h-6 text-primary" />Shift Closing Report</h1>
                        <p className="text-sm text-muted-foreground mt-1">
                            {printData?.companyName} &mdash; Shift #{report.shift?.id} &mdash; {fmtDT(report.shift?.startTime)} to {fmtDT(report.shift?.endTime)}
                        </p>
                    </div>
                </div>
                <div className="flex items-center gap-2 flex-wrap">
                    <span className={`px-3 py-1 rounded-full text-xs font-semibold ${isDraft ? "bg-amber-500/10 text-amber-500" : "bg-green-500/10 text-green-500"}`}>{report.status}</span>
                    {isDraft && (<>
                        <button onClick={handleRecompute} className="flex items-center gap-1.5 px-3 py-1.5 text-sm rounded-lg bg-blue-500/10 text-blue-500 hover:bg-blue-500/20"><RefreshCw className="w-4 h-4" />Recompute</button>
                        <button onClick={() => setShowFinalizeConfirm(true)} className="flex items-center gap-1.5 px-3 py-1.5 text-sm rounded-lg bg-green-500/10 text-green-600 hover:bg-green-500/20"><Lock className="w-4 h-4" />Finalize</button>
                    </>)}
                    <button onClick={() => window.print()} className="flex items-center gap-1.5 px-3 py-1.5 text-sm rounded-lg bg-muted text-muted-foreground hover:bg-muted/80"><Printer className="w-4 h-4" />Print</button>
                    {report.reportPdfUrl && (
                        <button onClick={async () => { const url = await getShiftReportPdfUrl(shiftId); window.open(url, "_blank"); }} className="flex items-center gap-1.5 px-3 py-1.5 text-sm rounded-lg bg-primary/10 text-primary hover:bg-primary/20"><Download className="w-4 h-4" />Download PDF</button>
                    )}
                </div>
            </div>

            {/* Summary Cards */}
            <div className="grid grid-cols-2 lg:grid-cols-5 gap-3">
                <SummaryCard label="Total Revenue" value={report.totalRevenue} color="text-green-500" />
                <SummaryCard label="Total Advances" value={report.totalAdvances} color="text-red-500" />
                <SummaryCard label="Balance" value={report.balance} color={report.balance >= 0 ? "text-blue-500" : "text-red-600"} />
                <SummaryCard label="Cash Bills" value={report.cashBillAmount} color="text-purple-500" />
                <SummaryCard label="Credit Bills" value={report.creditBillAmount} color="text-amber-500" />
            </div>

            {/* Revenue & Advances side by side */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                {/* Revenue */}
                <GlassCard><div className="p-4">
                    <h2 className="text-base font-semibold mb-3 text-green-500">Revenue (Money IN)</h2>
                    <table className="w-full text-sm"><thead><tr className="border-b border-border text-muted-foreground text-xs">
                        <th className="text-left py-1.5 px-2">Item</th><th className="text-right py-1.5 px-2">Litres</th><th className="text-right py-1.5 px-2">Rate</th><th className="text-right py-1.5 px-2">Amount</th>
                        {isDraft && <th className="w-16"/>}
                    </tr></thead><tbody>
                        {revenueItems.map(item => <EditableRow key={item.id} item={item} isDraft={isDraft} editingItemId={editingItemId} editAmount={editAmount} editReason={editReason} setEditingItemId={setEditingItemId} setEditAmount={setEditAmount} setEditReason={setEditReason} onSave={handleEditSave} onTransfer={openTransferModal} showQty />)}
                        <tr className="border-t-2 border-border font-bold"><td className="py-1.5 px-2" colSpan={3}>TOTAL</td><td className="py-1.5 px-2 text-right text-green-500">{fmtCur(report.totalRevenue)}</td>{isDraft && <td/>}</tr>
                    </tbody></table>
                </div></GlassCard>

                {/* Advances */}
                <GlassCard><div className="p-4">
                    <h2 className="text-base font-semibold mb-3 text-red-500">Advances (Money OUT)</h2>
                    <table className="w-full text-sm"><thead><tr className="border-b border-border text-muted-foreground text-xs">
                        <th className="text-left py-1.5 px-2">Item</th><th className="text-right py-1.5 px-2">Amount</th>
                        {isDraft && <th className="w-16"/>}
                    </tr></thead><tbody>
                        {advanceItems.map(item => <EditableRow key={item.id} item={item} isDraft={isDraft} editingItemId={editingItemId} editAmount={editAmount} editReason={editReason} setEditingItemId={setEditingItemId} setEditAmount={setEditAmount} setEditReason={setEditReason} onSave={handleEditSave} onTransfer={openTransferModal} />)}
                        <tr className="border-t-2 border-border font-bold"><td className="py-1.5 px-2">TOTAL</td><td className="py-1.5 px-2 text-right text-red-500">{fmtCur(report.totalAdvances)}</td>{isDraft && <td/>}</tr>
                    </tbody></table>
                </div></GlassCard>
            </div>

            {/* Meter & Tank Readings + Cash Bill Breakdown */}
            {printData && (
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                {/* Meter Readings */}
                <GlassCard><div className="p-4">
                    <h2 className="text-base font-semibold mb-3">Meter Readings</h2>
                    <table className="w-full text-xs"><thead><tr className="border-b border-border text-muted-foreground">
                        <th className="text-left py-1 px-1.5">Pump</th><th className="text-left py-1 px-1.5">Nozzle</th><th className="text-left py-1 px-1.5">Product</th>
                        <th className="text-right py-1 px-1.5">Open</th><th className="text-right py-1 px-1.5">Close</th><th className="text-right py-1 px-1.5">Sales</th>
                    </tr></thead><tbody>
                        {printData.meterReadings.map((m, i) => <tr key={i} className="border-b border-border/30"><td className="py-1 px-1.5">{m.pumpName}</td><td className="py-1 px-1.5">{m.nozzleName}</td><td className="py-1 px-1.5">{m.productName}</td>
                        <td className="py-1 px-1.5 text-right">{fmtQty(m.openReading)}</td><td className="py-1 px-1.5 text-right">{fmtQty(m.closeReading)}</td><td className="py-1 px-1.5 text-right font-semibold">{fmtQty(m.sales)}</td></tr>)}
                    </tbody></table>
                </div></GlassCard>

                {/* Tank Readings */}
                <GlassCard><div className="p-4">
                    <h2 className="text-base font-semibold mb-3">Tank Readings</h2>
                    <table className="w-full text-xs"><thead><tr className="border-b border-border text-muted-foreground">
                        <th className="text-left py-1 px-1.5">Tank</th><th className="text-left py-1 px-1.5">Product</th>
                        <th className="text-right py-1 px-1.5">Open</th><th className="text-right py-1 px-1.5">Income</th><th className="text-right py-1 px-1.5">Total</th><th className="text-right py-1 px-1.5">Close</th><th className="text-right py-1 px-1.5">Sales</th>
                    </tr></thead><tbody>
                        {printData.tankReadings.map((t, i) => <tr key={i} className="border-b border-border/30"><td className="py-1 px-1.5">{t.tankName}</td><td className="py-1 px-1.5">{t.productName}</td>
                        <td className="py-1 px-1.5 text-right">{fmtQty(t.openStock)}</td><td className="py-1 px-1.5 text-right">{fmtQty(t.incomeStock)}</td><td className="py-1 px-1.5 text-right">{fmtQty(t.totalStock)}</td><td className="py-1 px-1.5 text-right">{fmtQty(t.closeStock)}</td><td className="py-1 px-1.5 text-right font-semibold">{fmtQty(t.saleStock)}</td></tr>)}
                    </tbody></table>
                </div></GlassCard>
            </div>)}

            {/* Cash Bill Breakdown + Sales Difference */}
            {printData && (
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                <GlassCard><div className="p-4">
                    <h2 className="text-base font-semibold mb-3 text-purple-500">Cash Bill Breakdown (Litres)</h2>
                    <table className="w-full text-xs"><thead><tr className="border-b border-border text-muted-foreground">
                        <th className="text-left py-1 px-1.5">Product</th><th className="text-right py-1 px-1.5">Cash</th><th className="text-right py-1 px-1.5">Card</th><th className="text-right py-1 px-1.5">CCMS</th><th className="text-right py-1 px-1.5">UPI</th><th className="text-right py-1 px-1.5">Cheque</th><th className="text-right py-1 px-1.5">Total</th>
                    </tr></thead><tbody>
                        {(report.cashBillBreakdowns || []).map(bd => <tr key={bd.id} className="border-b border-border/30"><td className="py-1 px-1.5">{bd.productName}</td>
                        <td className="py-1 px-1.5 text-right">{fmtQty(bd.cashLitres)}</td><td className="py-1 px-1.5 text-right">{fmtQty(bd.cardLitres)}</td><td className="py-1 px-1.5 text-right">{fmtQty(bd.ccmsLitres)}</td><td className="py-1 px-1.5 text-right">{fmtQty(bd.upiLitres)}</td><td className="py-1 px-1.5 text-right">{fmtQty(bd.chequeLitres)}</td><td className="py-1 px-1.5 text-right font-semibold">{fmtQty(bd.totalLitres)}</td></tr>)}
                    </tbody></table>
                </div></GlassCard>

                <GlassCard><div className="p-4">
                    <h2 className="text-base font-semibold mb-3">Sales Difference (Tank vs Meter)</h2>
                    <table className="w-full text-xs"><thead><tr className="border-b border-border text-muted-foreground">
                        <th className="text-left py-1 px-1.5">Product</th><th className="text-right py-1 px-1.5">Tank Sale</th><th className="text-right py-1 px-1.5">Meter Sale</th><th className="text-right py-1 px-1.5">Diff</th>
                    </tr></thead><tbody>
                        {printData.salesDifferences.map((sd, i) => <tr key={i} className="border-b border-border/30"><td className="py-1 px-1.5">{sd.productName}</td>
                        <td className="py-1 px-1.5 text-right">{fmtQty(sd.tankSale)}</td><td className="py-1 px-1.5 text-right">{fmtQty(sd.meterSale)}</td>
                        <td className={`py-1 px-1.5 text-right font-semibold ${sd.difference !== 0 ? "text-red-500" : ""}`}>{fmtQty(sd.difference)}</td></tr>)}
                    </tbody></table>
                </div></GlassCard>
            </div>)}

            {/* Credit Bills + Stock + Advance Details + Payment Details */}
            {printData && (
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                {/* Credit Bills Detail */}
                <GlassCard><div className="p-4">
                    <h2 className="text-base font-semibold mb-3 text-amber-500">Credit Bills Detail</h2>
                    <table className="w-full text-xs"><thead><tr className="border-b border-border text-muted-foreground">
                        <th className="text-left py-1 px-1.5">Bill#</th><th className="text-left py-1 px-1.5">V.No</th><th className="text-left py-1 px-1.5">Products</th><th className="text-right py-1 px-1.5">Amount</th>
                    </tr></thead><tbody>
                        {Object.entries(creditBillsByCustomer).map(([customer, bills]) => (
                            <>{/* Customer header */}
                            <tr key={`h-${customer}`} className="bg-muted/40"><td colSpan={4} className="py-1 px-1.5 font-semibold text-xs">{customer} ({bills.length})</td></tr>
                            {bills.map((b, i) => <tr key={`${customer}-${i}`} className="border-b border-border/20"><td className="py-0.5 px-1.5 pl-3">{b.billNo}</td><td className="py-0.5 px-1.5">{b.vehicleNo}</td><td className="py-0.5 px-1.5">{b.products}</td><td className="py-0.5 px-1.5 text-right">{fmtCur(b.amount)}</td></tr>)}
                            </>
                        ))}
                        <tr className="border-t-2 border-border font-bold"><td colSpan={3} className="py-1 px-1.5">Total</td><td className="py-1 px-1.5 text-right">{fmtCur(report.creditBillAmount)}</td></tr>
                    </tbody></table>
                </div></GlassCard>

                {/* Advance Entries Detail */}
                <GlassCard><div className="p-4">
                    <h2 className="text-base font-semibold mb-3 text-red-400">Advance Entries Detail</h2>
                    <table className="w-full text-xs"><thead><tr className="border-b border-border text-muted-foreground">
                        <th className="text-left py-1 px-1.5">Type</th><th className="text-left py-1 px-1.5">Description</th><th className="text-right py-1 px-1.5">Amount</th>
                    </tr></thead><tbody>
                        {printData.advanceEntries.map((e, i) => <tr key={i} className="border-b border-border/30"><td className="py-0.5 px-1.5"><span className="px-1 py-0.5 rounded bg-muted text-[10px] font-semibold">{e.type}</span></td><td className="py-0.5 px-1.5">{e.description}</td><td className="py-0.5 px-1.5 text-right">{fmtCur(e.amount)}</td></tr>)}
                        <tr className="border-t-2 border-border font-bold"><td colSpan={2} className="py-1 px-1.5">Total</td><td className="py-1 px-1.5 text-right">{fmtCur(report.totalAdvances)}</td></tr>
                    </tbody></table>
                </div></GlassCard>
            </div>)}

            {printData && (
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                {/* Stock Summary */}
                <GlassCard><div className="p-4">
                    <h2 className="text-base font-semibold mb-3">Stock Summary</h2>
                    <table className="w-full text-xs"><thead><tr className="border-b border-border text-muted-foreground">
                        <th className="text-left py-1 px-1.5">Product</th><th className="text-right py-1 px-1.5">Open</th><th className="text-right py-1 px-1.5">Rcpt</th><th className="text-right py-1 px-1.5">Total</th><th className="text-right py-1 px-1.5">Sales</th><th className="text-right py-1 px-1.5">Rate</th><th className="text-right py-1 px-1.5">Amount</th>
                    </tr></thead><tbody>
                        {printData.stockSummary.map((s, i) => <tr key={i} className="border-b border-border/30"><td className="py-0.5 px-1.5">{s.productName}</td>
                        <td className="py-0.5 px-1.5 text-right">{fmtQty(s.openStock)}</td><td className="py-0.5 px-1.5 text-right">{fmtQty(s.receipt)}</td><td className="py-0.5 px-1.5 text-right">{fmtQty(s.totalStock)}</td><td className="py-0.5 px-1.5 text-right">{fmtQty(s.sales)}</td><td className="py-0.5 px-1.5 text-right">{fmtCur(s.rate)}</td><td className="py-0.5 px-1.5 text-right font-semibold">{fmtCur(s.amount)}</td></tr>)}
                    </tbody></table>
                </div></GlassCard>

                {/* Stock Position (Godown + Cashier) */}
                {printData.stockPosition && printData.stockPosition.length > 0 && (
                <GlassCard><div className="p-4">
                    <h2 className="text-base font-semibold mb-3 text-purple-500">Stock Position</h2>
                    <table className="w-full text-xs"><thead><tr className="border-b border-border text-muted-foreground">
                        <th className="text-left py-1 px-1.5">Product</th><th className="text-right py-1 px-1.5">Godown</th><th className="text-right py-1 px-1.5">Cashier</th><th className="text-right py-1 px-1.5">Total</th><th className="text-center py-1 px-1.5">Status</th>
                    </tr></thead><tbody>
                        {printData.stockPosition.map((sp, i) => <tr key={i} className="border-b border-border/30"><td className="py-0.5 px-1.5">{sp.productName}</td>
                        <td className="py-0.5 px-1.5 text-right">{fmtQty(sp.godownStock)}</td><td className="py-0.5 px-1.5 text-right">{fmtQty(sp.cashierStock)}</td><td className="py-0.5 px-1.5 text-right font-semibold">{fmtQty(sp.totalStock)}</td>
                        <td className="py-0.5 px-1.5 text-center">{sp.lowStock ? <span className="text-red-500 font-bold text-[10px]">LOW</span> : <span className="text-green-500 text-[10px]">OK</span>}</td></tr>)}
                    </tbody></table>
                </div></GlassCard>
                )}

                {/* Payment Entries */}
                <GlassCard><div className="p-4">
                    <h2 className="text-base font-semibold mb-3 text-blue-500">Bill / Statement Payments</h2>
                    <table className="w-full text-xs"><thead><tr className="border-b border-border text-muted-foreground">
                        <th className="text-left py-1 px-1.5">Type</th><th className="text-left py-1 px-1.5">Customer</th><th className="text-left py-1 px-1.5">Ref</th><th className="text-left py-1 px-1.5">Mode</th><th className="text-right py-1 px-1.5">Amount</th>
                    </tr></thead><tbody>
                        {printData.paymentEntries.length === 0 ? <tr><td colSpan={5} className="py-2 text-center text-muted-foreground">No payments recorded</td></tr> :
                        printData.paymentEntries.map((p, i) => <tr key={i} className="border-b border-border/30"><td className="py-0.5 px-1.5"><span className="px-1 py-0.5 rounded bg-muted text-[10px] font-semibold">{p.type}</span></td><td className="py-0.5 px-1.5">{p.customerName}</td><td className="py-0.5 px-1.5">{p.reference}</td><td className="py-0.5 px-1.5">{p.paymentMode}</td><td className="py-0.5 px-1.5 text-right">{fmtCur(p.amount)}</td></tr>)}
                        {printData.paymentEntries.length > 0 && <tr className="border-t-2 border-border font-bold"><td colSpan={4} className="py-1 px-1.5">Total</td><td className="py-1 px-1.5 text-right">{fmtCur(printData.paymentEntries.reduce((s, p) => s + (p.amount || 0), 0))}</td></tr>}
                    </tbody></table>
                </div></GlassCard>
            </div>)}

            {/* Audit Log */}
            <GlassCard><div className="p-4">
                <button onClick={() => setShowAuditLog(!showAuditLog)} className="flex items-center gap-2 text-base font-semibold w-full text-left">
                    <Clock className="w-4 h-4 text-muted-foreground" />Audit Log
                    {showAuditLog ? <ChevronUp className="w-4 h-4 ml-auto" /> : <ChevronDown className="w-4 h-4 ml-auto" />}
                </button>
                {showAuditLog && <div className="mt-3 space-y-1.5">
                    {auditLogs.length === 0 ? <p className="text-xs text-muted-foreground">No changes recorded.</p> :
                    auditLogs.map(log => <div key={log.id} className="flex items-start gap-2 text-xs p-1.5 rounded bg-muted/30">
                        <span className="text-muted-foreground whitespace-nowrap">{fmtDT(log.performedAt)}</span>
                        <span className={`px-1 py-0.5 rounded text-[10px] font-semibold ${log.action === "FINALIZED" ? "bg-green-500/10 text-green-500" : log.action === "LINE_ITEM_EDITED" ? "bg-amber-500/10 text-amber-500" : "bg-blue-500/10 text-blue-500"}`}>{log.action}</span>
                        <span>{log.description}</span>
                        {log.previousValue != null && <span className="text-muted-foreground">({fmtCur(log.previousValue)} → {fmtCur(log.newValue)})</span>}
                    </div>)}
                </div>}
            </div></GlassCard>
        </div>

        {/* === PRINT VIEW (hidden on screen, shown on print) === */}
        <div className="hidden print:block text-[8pt] leading-tight text-black bg-white">
            {/* FRONT PAGE */}
            <div className="print-page p-4">
                {/* Header */}
                <div className="text-center border-b-2 border-black pb-1 mb-2">
                    <div className="text-[12pt] font-bold">{printData?.companyName || "StopForFuel"}</div>
                    <div className="text-[9pt] font-semibold">SHIFT CLOSING REPORT</div>
                    <div className="text-[7pt]">Employee: {printData?.employeeName} | Shift #{printData?.shiftId} | {fmtDT(printData?.shiftStart)} to {fmtDT(printData?.shiftEnd)} | Status: {report.status}</div>
                </div>

                <div className="grid grid-cols-2 gap-3">
                    {/* Left: Meter + Tank Readings */}
                    <div className="space-y-2">
                        <div>
                            <div className="font-bold text-[8pt] border-b border-black mb-0.5">METER READINGS</div>
                            <table className="w-full border-collapse"><thead><tr className="bg-gray-100">
                                <th className="border border-gray-400 px-0.5 py-0 text-left text-[7pt]">Pump</th><th className="border border-gray-400 px-0.5 py-0 text-left text-[7pt]">Nozzle</th><th className="border border-gray-400 px-0.5 py-0 text-left text-[7pt]">Prod</th>
                                <th className="border border-gray-400 px-0.5 py-0 text-right text-[7pt]">Open</th><th className="border border-gray-400 px-0.5 py-0 text-right text-[7pt]">Close</th><th className="border border-gray-400 px-0.5 py-0 text-right text-[7pt]">Sales</th>
                            </tr></thead><tbody>
                                {(printData?.meterReadings || []).map((m, i) => <tr key={i}><td className="border border-gray-300 px-0.5 py-0 text-[7pt]">{m.pumpName}</td><td className="border border-gray-300 px-0.5 py-0 text-[7pt]">{m.nozzleName}</td><td className="border border-gray-300 px-0.5 py-0 text-[7pt]">{m.productName}</td>
                                <td className="border border-gray-300 px-0.5 py-0 text-right text-[7pt]">{fmtQty(m.openReading)}</td><td className="border border-gray-300 px-0.5 py-0 text-right text-[7pt]">{fmtQty(m.closeReading)}</td><td className="border border-gray-300 px-0.5 py-0 text-right text-[7pt] font-bold">{fmtQty(m.sales)}</td></tr>)}
                            </tbody></table>
                        </div>
                        <div>
                            <div className="font-bold text-[8pt] border-b border-black mb-0.5">TANK READINGS</div>
                            <table className="w-full border-collapse"><thead><tr className="bg-gray-100">
                                <th className="border border-gray-400 px-0.5 py-0 text-left text-[7pt]">Tank</th><th className="border border-gray-400 px-0.5 py-0 text-left text-[7pt]">Prod</th>
                                <th className="border border-gray-400 px-0.5 py-0 text-right text-[7pt]">Open</th><th className="border border-gray-400 px-0.5 py-0 text-right text-[7pt]">Inc</th><th className="border border-gray-400 px-0.5 py-0 text-right text-[7pt]">Tot</th><th className="border border-gray-400 px-0.5 py-0 text-right text-[7pt]">Close</th><th className="border border-gray-400 px-0.5 py-0 text-right text-[7pt]">Sales</th>
                            </tr></thead><tbody>
                                {(printData?.tankReadings || []).map((t, i) => <tr key={i}><td className="border border-gray-300 px-0.5 py-0 text-[7pt]">{t.tankName}</td><td className="border border-gray-300 px-0.5 py-0 text-[7pt]">{t.productName}</td>
                                <td className="border border-gray-300 px-0.5 py-0 text-right text-[7pt]">{fmtQty(t.openStock)}</td><td className="border border-gray-300 px-0.5 py-0 text-right text-[7pt]">{fmtQty(t.incomeStock)}</td><td className="border border-gray-300 px-0.5 py-0 text-right text-[7pt]">{fmtQty(t.totalStock)}</td><td className="border border-gray-300 px-0.5 py-0 text-right text-[7pt]">{fmtQty(t.closeStock)}</td><td className="border border-gray-300 px-0.5 py-0 text-right text-[7pt] font-bold">{fmtQty(t.saleStock)}</td></tr>)}
                            </tbody></table>
                        </div>
                        {/* Summary boxes */}
                        <div className="grid grid-cols-2 gap-2 mt-1">
                            <div className="border-2 border-black p-1 text-center"><div className="text-[7pt] font-bold">TOTAL REVENUE</div><div className="text-[10pt] font-bold">{fmtCur(report.totalRevenue)}</div></div>
                            <div className="border-2 border-black p-1 text-center"><div className="text-[7pt] font-bold">SHIFT BALANCE</div><div className="text-[10pt] font-bold">{fmtCur(report.balance)}</div></div>
                        </div>
                        {/* Cash Bill Breakdown */}
                        <div>
                            <div className="font-bold text-[8pt] border-b border-black mb-0.5">CASH BILL BREAKDOWN (Litres)</div>
                            <table className="w-full border-collapse"><thead><tr className="bg-gray-100">
                                <th className="border border-gray-400 px-0.5 py-0 text-left text-[7pt]">Prod</th><th className="border border-gray-400 px-0.5 py-0 text-right text-[7pt]">Cash</th><th className="border border-gray-400 px-0.5 py-0 text-right text-[7pt]">Card</th><th className="border border-gray-400 px-0.5 py-0 text-right text-[7pt]">CCMS</th><th className="border border-gray-400 px-0.5 py-0 text-right text-[7pt]">UPI</th><th className="border border-gray-400 px-0.5 py-0 text-right text-[7pt]">Chq</th><th className="border border-gray-400 px-0.5 py-0 text-right text-[7pt]">Total</th>
                            </tr></thead><tbody>
                                {(report.cashBillBreakdowns || []).map(b => <tr key={b.id}><td className="border border-gray-300 px-0.5 py-0 text-[7pt]">{b.productName}</td>
                                <td className="border border-gray-300 px-0.5 py-0 text-right text-[7pt]">{fmtQty(b.cashLitres)}</td><td className="border border-gray-300 px-0.5 py-0 text-right text-[7pt]">{fmtQty(b.cardLitres)}</td><td className="border border-gray-300 px-0.5 py-0 text-right text-[7pt]">{fmtQty(b.ccmsLitres)}</td><td className="border border-gray-300 px-0.5 py-0 text-right text-[7pt]">{fmtQty(b.upiLitres)}</td><td className="border border-gray-300 px-0.5 py-0 text-right text-[7pt]">{fmtQty(b.chequeLitres)}</td><td className="border border-gray-300 px-0.5 py-0 text-right text-[7pt] font-bold">{fmtQty(b.totalLitres)}</td></tr>)}
                            </tbody></table>
                        </div>
                        {/* Sales Difference */}
                        <div>
                            <div className="font-bold text-[8pt] border-b border-black mb-0.5">SALES DIFFERENCE</div>
                            <table className="w-full border-collapse"><thead><tr className="bg-gray-100">
                                <th className="border border-gray-400 px-0.5 py-0 text-left text-[7pt]">Product</th><th className="border border-gray-400 px-0.5 py-0 text-right text-[7pt]">Tank</th><th className="border border-gray-400 px-0.5 py-0 text-right text-[7pt]">Meter</th><th className="border border-gray-400 px-0.5 py-0 text-right text-[7pt]">Diff</th>
                            </tr></thead><tbody>
                                {(printData?.salesDifferences || []).map((s, i) => <tr key={i}><td className="border border-gray-300 px-0.5 py-0 text-[7pt]">{s.productName}</td>
                                <td className="border border-gray-300 px-0.5 py-0 text-right text-[7pt]">{fmtQty(s.tankSale)}</td><td className="border border-gray-300 px-0.5 py-0 text-right text-[7pt]">{fmtQty(s.meterSale)}</td><td className="border border-gray-300 px-0.5 py-0 text-right text-[7pt] font-bold">{fmtQty(s.difference)}</td></tr>)}
                            </tbody></table>
                        </div>
                    </div>

                    {/* Right: Revenue + Advances */}
                    <div className="space-y-2">
                        <div>
                            <div className="font-bold text-[8pt] border-b border-black mb-0.5">REVENUE (MONEY IN)</div>
                            <table className="w-full border-collapse"><thead><tr className="bg-gray-100">
                                <th className="border border-gray-400 px-0.5 py-0 text-left text-[7pt]">Item</th><th className="border border-gray-400 px-0.5 py-0 text-right text-[7pt]">Litres</th><th className="border border-gray-400 px-0.5 py-0 text-right text-[7pt]">Rate</th><th className="border border-gray-400 px-0.5 py-0 text-right text-[7pt]">Amount</th>
                            </tr></thead><tbody>
                                {revenueItems.map(item => <tr key={item.id}><td className="border border-gray-300 px-0.5 py-0 text-[7pt]">{item.label}</td><td className="border border-gray-300 px-0.5 py-0 text-right text-[7pt]">{fmtQty(item.quantity)}</td><td className="border border-gray-300 px-0.5 py-0 text-right text-[7pt]">{item.rate != null ? fmtCur(item.rate) : ""}</td><td className="border border-gray-300 px-0.5 py-0 text-right text-[7pt] font-bold">{fmtCur(item.amount)}</td></tr>)}
                                <tr className="bg-gray-200 font-bold"><td className="border border-gray-400 px-0.5 py-0 text-[7pt]" colSpan={3}>TOTAL REVENUE</td><td className="border border-gray-400 px-0.5 py-0 text-right text-[7pt]">{fmtCur(report.totalRevenue)}</td></tr>
                            </tbody></table>
                        </div>
                        <div>
                            <div className="font-bold text-[8pt] border-b border-black mb-0.5">ADVANCES (MONEY OUT)</div>
                            <table className="w-full border-collapse"><thead><tr className="bg-gray-100">
                                <th className="border border-gray-400 px-0.5 py-0 text-left text-[7pt]">Item</th><th className="border border-gray-400 px-0.5 py-0 text-right text-[7pt]">Amount</th>
                            </tr></thead><tbody>
                                {advanceItems.map(item => <tr key={item.id}><td className="border border-gray-300 px-0.5 py-0 text-[7pt]">{item.label}</td><td className="border border-gray-300 px-0.5 py-0 text-right text-[7pt]">{fmtCur(item.amount)}</td></tr>)}
                                <tr className="bg-gray-200 font-bold"><td className="border border-gray-400 px-0.5 py-0 text-[7pt]">TOTAL ADVANCES</td><td className="border border-gray-400 px-0.5 py-0 text-right text-[7pt]">{fmtCur(report.totalAdvances)}</td></tr>
                            </tbody></table>
                        </div>
                    </div>
                </div>
            </div>

            {/* BACK PAGE */}
            <div className="print-page p-4">
                <div className="text-center border-b border-black pb-0.5 mb-2 text-[8pt] font-bold">
                    {printData?.companyName} — Shift #{printData?.shiftId} — {fmtDate(printData?.shiftStart)} (continued)
                </div>
                <div className="grid grid-cols-2 gap-3">
                    {/* Left: Credit Bills + Stock */}
                    <div className="space-y-2">
                        <div>
                            <div className="font-bold text-[8pt] border-b border-black mb-0.5">CREDIT BILLS DETAIL</div>
                            <table className="w-full border-collapse"><thead><tr className="bg-gray-100">
                                <th className="border border-gray-400 px-0.5 py-0 text-left text-[7pt]">Bill#</th><th className="border border-gray-400 px-0.5 py-0 text-left text-[7pt]">V.No</th><th className="border border-gray-400 px-0.5 py-0 text-left text-[7pt]">Products</th><th className="border border-gray-400 px-0.5 py-0 text-right text-[7pt]">Amount</th>
                            </tr></thead><tbody>
                                {Object.entries(creditBillsByCustomer).map(([customer, bills]) => (<>
                                    <tr key={`ph-${customer}`} className="bg-gray-100"><td colSpan={4} className="border border-gray-400 px-0.5 py-0 text-[7pt] font-bold">{customer} ({bills.length})</td></tr>
                                    {bills.map((b, i) => <tr key={`pb-${customer}-${i}`}><td className="border border-gray-300 px-0.5 py-0 text-[7pt] pl-1">{b.billNo}</td><td className="border border-gray-300 px-0.5 py-0 text-[7pt]">{b.vehicleNo}</td><td className="border border-gray-300 px-0.5 py-0 text-[7pt]">{b.products}</td><td className="border border-gray-300 px-0.5 py-0 text-right text-[7pt]">{fmtCur(b.amount)}</td></tr>)}
                                </>))}
                                <tr className="bg-gray-200 font-bold"><td colSpan={3} className="border border-gray-400 px-0.5 py-0 text-[7pt]">TOTAL CREDIT</td><td className="border border-gray-400 px-0.5 py-0 text-right text-[7pt]">{fmtCur(report.creditBillAmount)}</td></tr>
                            </tbody></table>
                        </div>
                        <div>
                            <div className="font-bold text-[8pt] border-b border-black mb-0.5">STOCK SUMMARY</div>
                            <table className="w-full border-collapse"><thead><tr className="bg-gray-100">
                                <th className="border border-gray-400 px-0.5 py-0 text-left text-[7pt]">Product</th><th className="border border-gray-400 px-0.5 py-0 text-right text-[7pt]">Open</th><th className="border border-gray-400 px-0.5 py-0 text-right text-[7pt]">Rcpt</th><th className="border border-gray-400 px-0.5 py-0 text-right text-[7pt]">Total</th><th className="border border-gray-400 px-0.5 py-0 text-right text-[7pt]">Sales</th><th className="border border-gray-400 px-0.5 py-0 text-right text-[7pt]">Rate</th><th className="border border-gray-400 px-0.5 py-0 text-right text-[7pt]">Amount</th>
                            </tr></thead><tbody>
                                {(printData?.stockSummary || []).map((s, i) => <tr key={i}><td className="border border-gray-300 px-0.5 py-0 text-[7pt]">{s.productName}</td>
                                <td className="border border-gray-300 px-0.5 py-0 text-right text-[7pt]">{fmtQty(s.openStock)}</td><td className="border border-gray-300 px-0.5 py-0 text-right text-[7pt]">{fmtQty(s.receipt)}</td><td className="border border-gray-300 px-0.5 py-0 text-right text-[7pt]">{fmtQty(s.totalStock)}</td><td className="border border-gray-300 px-0.5 py-0 text-right text-[7pt]">{fmtQty(s.sales)}</td><td className="border border-gray-300 px-0.5 py-0 text-right text-[7pt]">{fmtCur(s.rate)}</td><td className="border border-gray-300 px-0.5 py-0 text-right text-[7pt] font-bold">{fmtCur(s.amount)}</td></tr>)}
                            </tbody></table>
                        </div>
                        {(printData?.stockPosition || []).length > 0 && (
                        <div>
                            <div className="font-bold text-[8pt] border-b border-black mb-0.5">STOCK POSITION</div>
                            <table className="w-full border-collapse"><thead><tr className="bg-gray-100">
                                <th className="border border-gray-400 px-0.5 py-0 text-left text-[7pt]">Product</th><th className="border border-gray-400 px-0.5 py-0 text-right text-[7pt]">Godown</th><th className="border border-gray-400 px-0.5 py-0 text-right text-[7pt]">Cashier</th><th className="border border-gray-400 px-0.5 py-0 text-right text-[7pt]">Total</th><th className="border border-gray-400 px-0.5 py-0 text-center text-[7pt]">Status</th>
                            </tr></thead><tbody>
                                {(printData?.stockPosition || []).map((sp, i) => <tr key={i}><td className="border border-gray-300 px-0.5 py-0 text-[7pt]">{sp.productName}</td>
                                <td className="border border-gray-300 px-0.5 py-0 text-right text-[7pt]">{fmtQty(sp.godownStock)}</td><td className="border border-gray-300 px-0.5 py-0 text-right text-[7pt]">{fmtQty(sp.cashierStock)}</td><td className="border border-gray-300 px-0.5 py-0 text-right text-[7pt] font-bold">{fmtQty(sp.totalStock)}</td><td className="border border-gray-300 px-0.5 py-0 text-center text-[7pt]">{sp.lowStock ? "LOW" : "OK"}</td></tr>)}
                            </tbody></table>
                        </div>
                        )}
                    </div>

                    {/* Right: Advance Entries + Payment Entries */}
                    <div className="space-y-2">
                        <div>
                            <div className="font-bold text-[8pt] border-b border-black mb-0.5">ADVANCE ENTRIES</div>
                            <table className="w-full border-collapse"><thead><tr className="bg-gray-100">
                                <th className="border border-gray-400 px-0.5 py-0 text-left text-[7pt]">Type</th><th className="border border-gray-400 px-0.5 py-0 text-left text-[7pt]">Description</th><th className="border border-gray-400 px-0.5 py-0 text-right text-[7pt]">Amount</th>
                            </tr></thead><tbody>
                                {(printData?.advanceEntries || []).map((e, i) => <tr key={i}><td className="border border-gray-300 px-0.5 py-0 text-[7pt]">{e.type}</td><td className="border border-gray-300 px-0.5 py-0 text-[7pt]">{e.description}</td><td className="border border-gray-300 px-0.5 py-0 text-right text-[7pt]">{fmtCur(e.amount)}</td></tr>)}
                                <tr className="bg-gray-200 font-bold"><td colSpan={2} className="border border-gray-400 px-0.5 py-0 text-[7pt]">TOTAL</td><td className="border border-gray-400 px-0.5 py-0 text-right text-[7pt]">{fmtCur(report.totalAdvances)}</td></tr>
                            </tbody></table>
                        </div>
                        <div>
                            <div className="font-bold text-[8pt] border-b border-black mb-0.5">BILL / STATEMENT PAYMENTS</div>
                            <table className="w-full border-collapse"><thead><tr className="bg-gray-100">
                                <th className="border border-gray-400 px-0.5 py-0 text-left text-[7pt]">Type</th><th className="border border-gray-400 px-0.5 py-0 text-left text-[7pt]">Customer</th><th className="border border-gray-400 px-0.5 py-0 text-left text-[7pt]">Ref</th><th className="border border-gray-400 px-0.5 py-0 text-left text-[7pt]">Mode</th><th className="border border-gray-400 px-0.5 py-0 text-right text-[7pt]">Amt</th>
                            </tr></thead><tbody>
                                {(printData?.paymentEntries || []).length === 0 ? <tr><td colSpan={5} className="border border-gray-300 px-0.5 py-0 text-[7pt] text-center">No payments</td></tr> :
                                (printData?.paymentEntries || []).map((p, i) => <tr key={i}><td className="border border-gray-300 px-0.5 py-0 text-[7pt]">{p.type}</td><td className="border border-gray-300 px-0.5 py-0 text-[7pt]">{p.customerName}</td><td className="border border-gray-300 px-0.5 py-0 text-[7pt]">{p.reference}</td><td className="border border-gray-300 px-0.5 py-0 text-[7pt]">{p.paymentMode}</td><td className="border border-gray-300 px-0.5 py-0 text-right text-[7pt]">{fmtCur(p.amount)}</td></tr>)}
                                {(printData?.paymentEntries || []).length > 0 && <tr className="bg-gray-200 font-bold"><td colSpan={4} className="border border-gray-400 px-0.5 py-0 text-[7pt]">TOTAL</td><td className="border border-gray-400 px-0.5 py-0 text-right text-[7pt]">{fmtCur((printData?.paymentEntries || []).reduce((s, p) => s + (p.amount || 0), 0))}</td></tr>}
                            </tbody></table>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        {/* Modals */}
        {transferItemId != null && (
            <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 print:hidden">
                <div className="bg-card rounded-xl p-6 w-full max-w-md shadow-2xl">
                    <h3 className="text-lg font-semibold mb-4">Transfer Entry</h3>
                    <div className="space-y-3">
                        <div><label className="text-sm font-medium text-muted-foreground">Target Report</label>
                        <select value={targetReportId} onChange={e => setTargetReportId(e.target.value)} className="w-full mt-1 px-3 py-2 border rounded-lg bg-background text-sm">
                            <option value="">Select a draft report...</option>
                            {draftReports.map(r => <option key={r.id} value={r.id}>Shift #{r.shift?.id} — {fmtDT(r.reportDate)}</option>)}
                        </select></div>
                        <div><label className="text-sm font-medium text-muted-foreground">Reason</label>
                        <input type="text" value={transferReason} onChange={e => setTransferReason(e.target.value)} placeholder="Why transfer?" className="w-full mt-1 px-3 py-2 border rounded-lg bg-background text-sm" /></div>
                    </div>
                    <div className="flex justify-end gap-2 mt-5">
                        <button onClick={() => setTransferItemId(null)} className="px-4 py-2 text-sm rounded-lg bg-muted text-muted-foreground">Cancel</button>
                        <button onClick={handleTransfer} disabled={!targetReportId} className="px-4 py-2 text-sm rounded-lg bg-primary text-primary-foreground disabled:opacity-50">Transfer</button>
                    </div>
                </div>
            </div>
        )}
        {showFinalizeConfirm && (
            <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 print:hidden">
                <div className="bg-card rounded-xl p-6 w-full max-w-sm shadow-2xl">
                    <h3 className="text-lg font-semibold mb-2">Finalize Report?</h3>
                    <p className="text-sm text-muted-foreground mb-5">This will lock the report permanently. The shift will be marked as RECONCILED.</p>
                    <div className="flex justify-end gap-2">
                        <button onClick={() => setShowFinalizeConfirm(false)} className="px-4 py-2 text-sm rounded-lg bg-muted text-muted-foreground">Cancel</button>
                        <button onClick={handleFinalize} className="px-4 py-2 text-sm rounded-lg bg-green-600 text-white"><CheckCircle2 className="w-4 h-4 inline mr-1" />Finalize</button>
                    </div>
                </div>
            </div>
        )}
        </>
    );
}

function SummaryCard({ label, value, color }: { label: string; value: number; color: string }) {
    return <GlassCard><div className="p-3"><p className="text-[10px] font-medium text-muted-foreground uppercase tracking-wide">{label}</p><p className={`text-xl font-bold mt-0.5 ${color}`}>{fmtCur(value)}</p></div></GlassCard>;
}

function EditableRow({ item, isDraft, editingItemId, editAmount, editReason, setEditingItemId, setEditAmount, setEditReason, onSave, onTransfer, showQty }: {
    item: ReportLineItem; isDraft: boolean; editingItemId: number | null; editAmount: string; editReason: string;
    setEditingItemId: (id: number | null) => void; setEditAmount: (v: string) => void; setEditReason: (v: string) => void;
    onSave: () => void; onTransfer: (id: number) => void; showQty?: boolean;
}) {
    const isEditing = editingItemId === item.id;
    return (
        <tr className="border-b border-border/50 hover:bg-muted/30">
            <td className="py-1.5 px-2 text-sm">
                {item.label || CATEGORY_LABELS[item.category] || item.category}
                {item.originalAmount != null && <span className="text-xs text-amber-500 ml-1">(was {fmtCur(item.originalAmount)})</span>}
            </td>
            {showQty && <td className="py-1.5 px-2 text-right text-sm">{fmtQty(item.quantity)}</td>}
            {showQty && <td className="py-1.5 px-2 text-right text-sm">{item.rate != null ? fmtCur(item.rate) : "-"}</td>}
            <td className="py-1.5 px-2 text-right text-sm">
                {isEditing ? (
                    <div className="flex flex-col items-end gap-1">
                        <input type="number" value={editAmount} onChange={e => setEditAmount(e.target.value)} className="w-28 px-2 py-1 border rounded text-right text-sm bg-background" />
                        <input type="text" value={editReason} placeholder="Reason" onChange={e => setEditReason(e.target.value)} className="w-40 px-2 py-1 border rounded text-sm bg-background" />
                        <div className="flex gap-1"><button onClick={onSave} className="text-xs px-2 py-0.5 rounded bg-green-500/10 text-green-500">Save</button><button onClick={() => setEditingItemId(null)} className="text-xs px-2 py-0.5 rounded bg-muted text-muted-foreground">Cancel</button></div>
                    </div>
                ) : fmtCur(item.amount)}
            </td>
            {isDraft && <td className="py-1.5 px-2 text-center"><div className="flex items-center justify-center gap-0.5">
                <button onClick={() => { setEditingItemId(item.id); setEditAmount(String(item.amount)); }} className="p-1 rounded hover:bg-muted" title="Edit"><Edit3 className="w-3.5 h-3.5 text-blue-500" /></button>
                <button onClick={() => onTransfer(item.id)} className="p-1 rounded hover:bg-muted" title="Transfer"><ArrowRightLeft className="w-3.5 h-3.5 text-amber-500" /></button>
            </div></td>}
        </tr>
    );
}
