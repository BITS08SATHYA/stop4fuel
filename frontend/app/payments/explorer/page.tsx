"use client";

import { useState, useEffect, useCallback } from "react";
import { GlassCard } from "@/components/ui/glass-card";
import { Badge } from "@/components/ui/badge";
import { TablePagination } from "@/components/ui/table-pagination";
import { CustomerAutocomplete } from "@/components/ui/customer-autocomplete";
import { StatementAutocomplete } from "@/components/ui/statement-autocomplete";
import { PermissionGate } from "@/components/permission-gate";
import {
    IndianRupee, Percent, FileClock, FileCheck2, TrendingUp,
    Search, Calendar, ChevronDown, ChevronRight, FileText,
    Download, Loader2, ArrowLeft, Receipt, CreditCard, Clock,
    Banknote, ImageIcon, ExternalLink
} from "lucide-react";
import {
    getStatements, getStatementBills, getPaymentsByStatement,
    getStatementStats, getStatementPdfUrl, getBillPaymentSummary,
    getCustomerCreditInfo, getPaymentModes, recordStatementPayment,
    type Statement, type Payment, type InvoiceBill, type StatementStats,
    type PageResponse, type BillPaymentSummary, type PaymentMode
} from "@/lib/api/station";

function formatCurrency(val?: number | null) {
    if (val == null) return "0.00";
    return val.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function formatCompact(val?: number | null) {
    if (val == null) return "0";
    if (val >= 100000) return (val / 100000).toFixed(1) + "L";
    if (val >= 1000) return (val / 1000).toFixed(1) + "K";
    return val.toLocaleString("en-IN", { maximumFractionDigits: 0 });
}

function formatDate(dateStr?: string) {
    if (!dateStr) return "-";
    const d = new Date(dateStr);
    return d.toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "numeric" });
}

function formatDateTime(dateStr?: string) {
    if (!dateStr) return "-";
    const d = new Date(dateStr);
    return d.toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "numeric" }) +
        " " + d.toLocaleTimeString("en-IN", { hour: "2-digit", minute: "2-digit" });
}

const PAYMENT_MODE_COLORS: Record<string, string> = {
    CASH: "bg-green-500/20 text-green-400",
    CARD: "bg-blue-500/20 text-blue-400",
    UPI: "bg-purple-500/20 text-purple-400",
    CHEQUE: "bg-amber-500/20 text-amber-400",
    BANK_TRANSFER: "bg-cyan-500/20 text-cyan-400",
    NEFT: "bg-cyan-500/20 text-cyan-400",
    RTGS: "bg-cyan-500/20 text-cyan-400",
};

export default function ExplorerPage() {
    // KPI
    const [stats, setStats] = useState<StatementStats | null>(null);
    const [statsLoading, setStatsLoading] = useState(true);

    // Filters
    const [customerId, setCustomerId] = useState<number | "">("");
    const [statusFilter, setStatusFilter] = useState<string>("");
    const [fromDate, setFromDate] = useState("");
    const [toDate, setToDate] = useState("");

    // Statement list (left panel)
    const [statements, setStatements] = useState<Statement[]>([]);
    const [listLoading, setListLoading] = useState(true);
    const [page, setPage] = useState(0);
    const [pageSize] = useState(5);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);

    // Selected statement (right panel)
    const [selectedStatement, setSelectedStatement] = useState<Statement | null>(null);
    const [activeTab, setActiveTab] = useState<"summary" | "bills" | "payments">("summary");
    const [bills, setBills] = useState<InvoiceBill[]>([]);
    const [payments, setPayments] = useState<Payment[]>([]);
    const [detailLoading, setDetailLoading] = useState(false);

    // Bill expansion
    const [expandedBillId, setExpandedBillId] = useState<number | null>(null);
    const [billPaymentSummary, setBillPaymentSummary] = useState<BillPaymentSummary | null>(null);
    const [billPaymentLoading, setBillPaymentLoading] = useState(false);

    // Customer KPI
    const [customerInfo, setCustomerInfo] = useState<{
        creditLimitAmount: number | null;
        creditLimitLiters: number | null;
        consumedLiters: number | null;
        ledgerBalance: number;
        totalBilled: number;
        totalPaid: number;
    } | null>(null);
    const [customerInfoLoading, setCustomerInfoLoading] = useState(false);

    // PDF
    const [pdfLoading, setPdfLoading] = useState(false);

    // Mobile detail view
    const [showMobileDetail, setShowMobileDetail] = useState(false);

    // Payment form (for statement payments)
    const [paymentModes, setPaymentModes] = useState<PaymentMode[]>([]);
    const [showPaymentForm, setShowPaymentForm] = useState(false);
    const [paymentAmount, setPaymentAmount] = useState("");
    const [paymentModeId, setPaymentModeId] = useState<number | "">("");
    const [paymentRef, setPaymentRef] = useState("");
    const [paymentRemarks, setPaymentRemarks] = useState("");
    const [paymentSubmitting, setPaymentSubmitting] = useState(false);
    const [paymentError, setPaymentError] = useState("");

    // Load payment modes
    useEffect(() => {
        getPaymentModes().then(setPaymentModes).catch(console.error);
    }, []);

    // Load KPI stats
    useEffect(() => {
        getStatementStats()
            .then(setStats)
            .catch(console.error)
            .finally(() => setStatsLoading(false));
    }, []);

    // Load statements
    const fetchStatements = useCallback(async () => {
        setListLoading(true);
        try {
            const res: PageResponse<Statement> = await getStatements(
                page, pageSize,
                customerId || undefined,
                statusFilter || undefined,
                fromDate || undefined,
                toDate || undefined
            );
            setStatements(res.content);
            setTotalPages(res.totalPages);
            setTotalElements(res.totalElements);
        } catch (err) {
            console.error(err);
        } finally {
            setListLoading(false);
        }
    }, [page, pageSize, customerId, statusFilter, fromDate, toDate]);

    useEffect(() => { fetchStatements(); }, [fetchStatements]);

    // Load statement details
    const selectStatement = async (stmt: Statement) => {
        setSelectedStatement(stmt);
        setActiveTab("summary");
        setExpandedBillId(null);
        setBillPaymentSummary(null);
        setShowMobileDetail(true);
        setDetailLoading(true);
        try {
            const [billsRes, paymentsRes] = await Promise.all([
                getStatementBills(stmt.id!),
                getPaymentsByStatement(stmt.id!),
            ]);
            setBills(billsRes);
            setPayments(paymentsRes);
        } catch (err) {
            console.error(err);
        } finally {
            setDetailLoading(false);
        }
    };

    // Expand bill to see per-bill payments
    const toggleBillExpand = async (billId: number) => {
        if (expandedBillId === billId) {
            setExpandedBillId(null);
            setBillPaymentSummary(null);
            return;
        }
        setExpandedBillId(billId);
        setBillPaymentLoading(true);
        try {
            const summary = await getBillPaymentSummary(billId);
            setBillPaymentSummary(summary);
        } catch (err) {
            console.error(err);
        } finally {
            setBillPaymentLoading(false);
        }
    };

    // Download PDF
    const handleDownloadPdf = async (statementId: number) => {
        setPdfLoading(true);
        try {
            const url = await getStatementPdfUrl(statementId);
            window.open(url, "_blank");
        } catch {
            alert("PDF not available. Generate it first from the Statements page.");
        } finally {
            setPdfLoading(false);
        }
    };

    // Record statement payment
    const handleRecordStatementPayment = async () => {
        if (!selectedStatement || !paymentAmount || !paymentModeId) return;
        setPaymentSubmitting(true);
        setPaymentError("");
        try {
            await recordStatementPayment(selectedStatement.id!, {
                amount: Number(paymentAmount),
                paymentMode: { id: paymentModeId as number, name: "" },
                referenceNo: paymentRef || undefined,
                remarks: paymentRemarks || undefined,
            });
            // Refresh payments and statement
            const [paymentsRes, statementsRes] = await Promise.all([
                getPaymentsByStatement(selectedStatement.id!),
                getStatements(page, pageSize, customerId || undefined, statusFilter || undefined, fromDate || undefined, toDate || undefined),
            ]);
            setPayments(paymentsRes);
            setStatements(statementsRes.content);
            // Update selected statement from refreshed list
            const updated = statementsRes.content.find(s => s.id === selectedStatement.id);
            if (updated) setSelectedStatement(updated);
            setShowPaymentForm(false);
            setPaymentAmount("");
            setPaymentModeId("");
            setPaymentRef("");
            setPaymentRemarks("");
            getStatementStats().then(setStats).catch(console.error);
        } catch (err: any) {
            setPaymentError(err?.message || "Payment failed");
        } finally {
            setPaymentSubmitting(false);
        }
    };

    // Reset filters
    const handleCustomerChange = (id: string | number) => {
        const numId = id ? Number(id) : "";
        setCustomerId(numId);
        setPage(0);
        setSelectedStatement(null);
        if (numId) {
            setCustomerInfoLoading(true);
            getCustomerCreditInfo(numId as number)
                .then(setCustomerInfo)
                .catch(console.error)
                .finally(() => setCustomerInfoLoading(false));
        } else {
            setCustomerInfo(null);
        }
    };

    const handleFilterChange = () => {
        setPage(0);
        setSelectedStatement(null);
    };

    // Computed
    const progressPercent = selectedStatement
        ? Math.min(100, ((selectedStatement.receivedAmount || 0) / (selectedStatement.netAmount || 1)) * 100)
        : 0;

    return (
        <PermissionGate permission="PAYMENT_VIEW">
            <div className="flex flex-col h-[calc(100vh-4rem)] overflow-hidden gap-3">
                {/* Page Header */}
                <div className="flex items-center justify-between shrink-0">
                    <div>
                        <h1 className="text-2xl font-bold text-foreground">Statement Explorer</h1>
                        <p className="text-sm text-muted-foreground mt-1">Search and track statements, bills, and payments</p>
                    </div>
                </div>

                {/* KPI Bar */}
                <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-3 shrink-0">
                    {statsLoading ? (
                        Array.from({ length: 5 }).map((_, i) => (
                            <GlassCard key={i} className="p-4 animate-pulse">
                                <div className="h-4 bg-muted rounded w-2/3 mb-2" />
                                <div className="h-6 bg-muted rounded w-1/2" />
                            </GlassCard>
                        ))
                    ) : stats ? (
                        <>
                            <GlassCard className="p-4">
                                <div className="flex items-center gap-2 text-xs text-muted-foreground mb-1">
                                    <IndianRupee className="w-3.5 h-3.5" />
                                    Total Outstanding
                                </div>
                                <div className="text-xl font-bold text-red-400">{formatCompact(stats.totalUnpaidAmount)}</div>
                            </GlassCard>
                            <GlassCard className="p-4">
                                <div className="flex items-center gap-2 text-xs text-muted-foreground mb-1">
                                    <Percent className="w-3.5 h-3.5" />
                                    Collection Rate
                                </div>
                                <div className={`text-xl font-bold ${
                                    (stats.collectionRate || 0) >= 80 ? "text-green-400" :
                                    (stats.collectionRate || 0) >= 50 ? "text-amber-400" : "text-red-400"
                                }`}>
                                    {(stats.collectionRate || 0).toFixed(1)}%
                                </div>
                            </GlassCard>
                            <GlassCard className="p-4">
                                <div className="flex items-center gap-2 text-xs text-muted-foreground mb-1">
                                    <FileClock className="w-3.5 h-3.5" />
                                    This Month
                                </div>
                                <div className="text-xl font-bold text-foreground">{stats.statementsLastMonth}</div>
                            </GlassCard>
                            <GlassCard className="p-4">
                                <div className="flex items-center gap-2 text-xs text-muted-foreground mb-1">
                                    <FileCheck2 className="w-3.5 h-3.5" />
                                    Paid This Month
                                </div>
                                <div className="text-xl font-bold text-green-400">
                                    {stats.paidLastMonth} <span className="text-sm text-muted-foreground font-normal">/ {stats.statementsLastMonth}</span>
                                </div>
                                {stats.statementsLastMonth > 0 && (
                                    <div className="mt-1.5 h-1.5 bg-muted rounded-full overflow-hidden">
                                        <div
                                            className="h-full bg-green-500 rounded-full transition-all"
                                            style={{ width: `${(stats.paidLastMonth / stats.statementsLastMonth) * 100}%` }}
                                        />
                                    </div>
                                )}
                            </GlassCard>
                            <GlassCard className="p-4">
                                <div className="flex items-center gap-2 text-xs text-muted-foreground mb-1">
                                    <TrendingUp className="w-3.5 h-3.5" />
                                    Avg Statement
                                </div>
                                <div className="text-xl font-bold text-foreground">{formatCompact(stats.avgStatementAmount)}</div>
                            </GlassCard>
                        </>
                    ) : null}
                </div>

                {/* Controls Bar */}
                <GlassCard className="p-4 relative z-20 shrink-0">
                    <div className="flex flex-wrap items-end gap-3">
                        <div className="flex-1 min-w-[180px]">
                            <label className="text-xs text-muted-foreground mb-1 block">Customer</label>
                            <CustomerAutocomplete
                                value={customerId}
                                onChange={handleCustomerChange}
                                placeholder="Search by name or phone..."
                            />
                        </div>
                        <div className="flex-1 min-w-[180px]">
                            <label className="text-xs text-muted-foreground mb-1 block">Search Statement</label>
                            <StatementAutocomplete
                                value={null}
                                onChange={(stmt) => {
                                    if (stmt) selectStatement(stmt);
                                }}
                                placeholder="Search by statement #..."
                            />
                        </div>
                        <div className="w-36">
                            <label className="text-xs text-muted-foreground mb-1 block">Status</label>
                            <select
                                value={statusFilter}
                                onChange={(e) => { setStatusFilter(e.target.value); handleFilterChange(); }}
                                className="w-full px-3 py-2 bg-card border border-border rounded-lg text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
                            >
                                <option value="">All</option>
                                <option value="NOT_PAID">Not Paid</option>
                                <option value="PAID">Paid</option>
                            </select>
                        </div>
                        <div className="w-40">
                            <label className="text-xs text-muted-foreground mb-1 block">From Date</label>
                            <input
                                type="date"
                                value={fromDate}
                                onChange={(e) => { setFromDate(e.target.value); handleFilterChange(); }}
                                className="w-full px-3 py-2 bg-card border border-border rounded-lg text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
                            />
                        </div>
                        <div className="w-40">
                            <label className="text-xs text-muted-foreground mb-1 block">To Date</label>
                            <input
                                type="date"
                                value={toDate}
                                onChange={(e) => { setToDate(e.target.value); handleFilterChange(); }}
                                className="w-full px-3 py-2 bg-card border border-border rounded-lg text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
                            />
                        </div>
                    </div>
                </GlassCard>

                {/* Customer KPI Strip */}
                {customerId && (
                    <div className="shrink-0">
                        {customerInfoLoading ? (
                            <GlassCard className="p-3 animate-pulse">
                                <div className="h-4 bg-muted rounded w-1/3" />
                            </GlassCard>
                        ) : customerInfo ? (
                            <GlassCard className="px-4 py-2.5">
                                <div className="flex items-center gap-4 overflow-x-auto">
                                    {(() => {
                                        const paymentPercent = customerInfo.totalBilled > 0
                                            ? (customerInfo.totalPaid / customerInfo.totalBilled) * 100 : 0;
                                        const creditUsage = customerInfo.creditLimitAmount && customerInfo.creditLimitAmount > 0
                                            ? (customerInfo.ledgerBalance / customerInfo.creditLimitAmount) * 100 : null;
                                        return (
                                            <>
                                                <div className="flex items-center gap-2 whitespace-nowrap">
                                                    <span className="text-[10px] uppercase text-muted-foreground tracking-wider">Statements</span>
                                                    <span className="text-sm font-bold text-foreground">{totalElements}</span>
                                                </div>
                                                <div className="w-px h-5 bg-border shrink-0" />
                                                <div className="flex items-center gap-2 whitespace-nowrap">
                                                    <span className="text-[10px] uppercase text-muted-foreground tracking-wider">Total Billed</span>
                                                    <span className="text-sm font-bold text-foreground">{formatCompact(customerInfo.totalBilled)}</span>
                                                </div>
                                                <div className="w-px h-5 bg-border shrink-0" />
                                                <div className="flex items-center gap-2 whitespace-nowrap">
                                                    <span className="text-[10px] uppercase text-muted-foreground tracking-wider">Paid</span>
                                                    <span className="text-sm font-bold text-green-400">{formatCompact(customerInfo.totalPaid)}</span>
                                                </div>
                                                <div className="w-px h-5 bg-border shrink-0" />
                                                <div className="flex items-center gap-2 whitespace-nowrap">
                                                    <span className="text-[10px] uppercase text-muted-foreground tracking-wider">Outstanding</span>
                                                    <span className={`text-sm font-bold ${customerInfo.ledgerBalance > 0 ? "text-red-400" : "text-green-400"}`}>
                                                        {formatCompact(customerInfo.ledgerBalance)}
                                                    </span>
                                                </div>
                                                <div className="w-px h-5 bg-border shrink-0" />
                                                <div className="flex items-center gap-2 whitespace-nowrap">
                                                    <span className="text-[10px] uppercase text-muted-foreground tracking-wider">Collection %</span>
                                                    <span className={`text-sm font-bold ${
                                                        paymentPercent >= 80 ? "text-green-400" :
                                                        paymentPercent >= 50 ? "text-amber-400" : "text-red-400"
                                                    }`}>
                                                        {paymentPercent.toFixed(1)}%
                                                    </span>
                                                </div>
                                                {creditUsage !== null && (
                                                    <>
                                                        <div className="w-px h-5 bg-border shrink-0" />
                                                        <div className="flex items-center gap-2 whitespace-nowrap">
                                                            <span className="text-[10px] uppercase text-muted-foreground tracking-wider">Credit Used</span>
                                                            <span className={`text-sm font-bold ${
                                                                creditUsage >= 90 ? "text-red-400" :
                                                                creditUsage >= 70 ? "text-amber-400" : "text-green-400"
                                                            }`}>
                                                                {creditUsage.toFixed(0)}%
                                                            </span>
                                                            <span className="text-[10px] text-muted-foreground">
                                                                of {formatCompact(customerInfo.creditLimitAmount)}
                                                            </span>
                                                        </div>
                                                    </>
                                                )}
                                                {customerInfo.creditLimitLiters && customerInfo.creditLimitLiters > 0 && (
                                                    <>
                                                        <div className="w-px h-5 bg-border shrink-0" />
                                                        <div className="flex items-center gap-2 whitespace-nowrap">
                                                            <span className="text-[10px] uppercase text-muted-foreground tracking-wider">Liters</span>
                                                            <span className="text-sm font-bold text-foreground">
                                                                {formatCompact(customerInfo.consumedLiters)} / {formatCompact(customerInfo.creditLimitLiters)}
                                                            </span>
                                                        </div>
                                                    </>
                                                )}
                                            </>
                                        );
                                    })()}
                                </div>
                            </GlassCard>
                        ) : null}
                    </div>
                )}

                {/* Two-Column Layout */}
                <div className="flex gap-4 flex-1 min-h-0">
                    {/* Left Panel: Statement List */}
                    <div className={`w-full lg:w-[40%] flex flex-col ${showMobileDetail ? "hidden lg:flex" : "flex"}`}>
                        <GlassCard className="flex-1 p-0 flex flex-col overflow-hidden">
                            <div className="px-4 py-3 border-b border-border flex items-center justify-between">
                                <h2 className="text-sm font-semibold text-foreground flex items-center gap-2">
                                    <Receipt className="w-4 h-4" />
                                    Statements
                                    <span className="text-xs text-muted-foreground font-normal">({totalElements})</span>
                                </h2>
                            </div>

                            <div className="flex-1 overflow-y-auto">
                                {listLoading ? (
                                    <div className="flex items-center justify-center py-20">
                                        <Loader2 className="w-6 h-6 animate-spin text-primary" />
                                    </div>
                                ) : statements.length === 0 ? (
                                    <div className="flex flex-col items-center justify-center py-20 text-muted-foreground">
                                        <Search className="w-10 h-10 mb-3 opacity-30" />
                                        <p className="text-sm">No statements found</p>
                                        <p className="text-xs mt-1">Try adjusting your filters</p>
                                    </div>
                                ) : (
                                    <div className="divide-y divide-border">
                                        {statements.map((stmt) => (
                                            <button
                                                key={stmt.id}
                                                onClick={() => selectStatement(stmt)}
                                                className={`w-full px-4 py-3 text-left transition-all hover:bg-primary/5 ${
                                                    selectedStatement?.id === stmt.id
                                                        ? "bg-primary/10 ring-2 ring-primary/30 ring-inset"
                                                        : ""
                                                }`}
                                            >
                                                <div className="flex items-center justify-between mb-1">
                                                    <span className="text-sm font-semibold text-foreground">{stmt.statementNo}</span>
                                                    <Badge variant={stmt.status === "PAID" ? "success" : "danger"} className="text-[10px] px-1.5 py-0">
                                                        {stmt.status === "PAID" ? "PAID" : "UNPAID"}
                                                    </Badge>
                                                </div>
                                                <div className="text-xs text-muted-foreground mb-1">{stmt.customer?.name}</div>
                                                <div className="flex items-center justify-between text-xs">
                                                    <span className="text-muted-foreground">
                                                        {formatDate(stmt.fromDate)} - {formatDate(stmt.toDate)}
                                                    </span>
                                                    <span className="font-medium text-foreground">{formatCurrency(stmt.netAmount)}</span>
                                                </div>
                                                {stmt.status === "NOT_PAID" && stmt.netAmount > 0 && (
                                                    <div className="mt-1.5 h-1 bg-muted rounded-full overflow-hidden">
                                                        <div
                                                            className="h-full bg-primary rounded-full transition-all"
                                                            style={{ width: `${Math.min(100, ((stmt.receivedAmount || 0) / stmt.netAmount) * 100)}%` }}
                                                        />
                                                    </div>
                                                )}
                                            </button>
                                        ))}
                                    </div>
                                )}
                            </div>

                            {totalPages > 1 && (
                                <div className="border-t border-border px-2 py-2">
                                    <TablePagination
                                        page={page}
                                        totalPages={totalPages}
                                        totalElements={totalElements}
                                        pageSize={pageSize}
                                        onPageChange={setPage}
                                    />
                                </div>
                            )}
                        </GlassCard>
                    </div>

                    {/* Right Panel: Detail */}
                    <div className={`w-full lg:w-[60%] flex flex-col ${!showMobileDetail ? "hidden lg:flex" : "flex"}`}>
                        <GlassCard className="flex-1 p-0 flex flex-col overflow-hidden">
                            {!selectedStatement ? (
                                <div className="flex-1 flex flex-col items-center justify-center text-muted-foreground">
                                    <FileText className="w-12 h-12 mb-3 opacity-20" />
                                    <p className="text-sm">Select a statement to view details</p>
                                </div>
                            ) : (
                                <>
                                    {/* Mobile back button */}
                                    <button
                                        onClick={() => setShowMobileDetail(false)}
                                        className="lg:hidden flex items-center gap-2 px-4 py-2 text-sm text-primary border-b border-border"
                                    >
                                        <ArrowLeft className="w-4 h-4" /> Back to list
                                    </button>

                                    {/* Tabs */}
                                    <div className="flex border-b border-border">
                                        {(["summary", "bills", "payments"] as const).map((tab) => (
                                            <button
                                                key={tab}
                                                onClick={() => setActiveTab(tab)}
                                                className={`flex-1 px-4 py-3 text-sm font-medium capitalize transition-colors ${
                                                    activeTab === tab
                                                        ? "text-primary border-b-2 border-primary"
                                                        : "text-muted-foreground hover:text-foreground"
                                                }`}
                                            >
                                                {tab} {tab === "bills" ? `(${bills.length})` : tab === "payments" ? `(${payments.length})` : ""}
                                            </button>
                                        ))}
                                    </div>

                                    {/* Tab Content */}
                                    <div className="flex-1 overflow-y-auto p-4">
                                        {detailLoading ? (
                                            <div className="flex items-center justify-center py-20">
                                                <Loader2 className="w-6 h-6 animate-spin text-primary" />
                                            </div>
                                        ) : activeTab === "summary" ? (
                                            <SummaryTab
                                                statement={selectedStatement}
                                                billCount={bills.length}
                                                paymentCount={payments.length}
                                                progressPercent={progressPercent}
                                                onDownloadPdf={() => handleDownloadPdf(selectedStatement.id!)}
                                                pdfLoading={pdfLoading}
                                                onRecordPayment={() => { setActiveTab("payments"); setShowPaymentForm(true); }}
                                            />
                                        ) : activeTab === "bills" ? (
                                            <BillsTab
                                                bills={bills}
                                                expandedBillId={expandedBillId}
                                                billPaymentSummary={billPaymentSummary}
                                                billPaymentLoading={billPaymentLoading}
                                                onToggleBill={toggleBillExpand}
                                            />
                                        ) : (
                                            <PaymentsTab
                                                payments={payments}
                                                statement={selectedStatement}
                                                showForm={showPaymentForm}
                                                onShowForm={setShowPaymentForm}
                                                paymentModes={paymentModes}
                                                paymentAmount={paymentAmount}
                                                onAmountChange={setPaymentAmount}
                                                paymentModeId={paymentModeId}
                                                onModeChange={setPaymentModeId}
                                                paymentRef={paymentRef}
                                                onRefChange={setPaymentRef}
                                                paymentRemarks={paymentRemarks}
                                                onRemarksChange={setPaymentRemarks}
                                                submitting={paymentSubmitting}
                                                error={paymentError}
                                                onSubmit={handleRecordStatementPayment}
                                                onCancel={() => { setShowPaymentForm(false); setPaymentError(""); }}
                                            />
                                        )}
                                    </div>
                                </>
                            )}
                        </GlassCard>
                    </div>
                </div>
            </div>
        </PermissionGate>
    );
}

// --- Summary Tab ---
function SummaryTab({
    statement, billCount, paymentCount, progressPercent, onDownloadPdf, pdfLoading, onRecordPayment
}: {
    statement: Statement;
    billCount: number;
    paymentCount: number;
    progressPercent: number;
    onDownloadPdf: () => void;
    pdfLoading: boolean;
    onRecordPayment: () => void;
}) {
    return (
        <div className="space-y-4">
            {/* Header */}
            <div className="flex items-start justify-between">
                <div>
                    <h3 className="text-lg font-bold text-foreground">{statement.statementNo}</h3>
                    <p className="text-sm text-muted-foreground">{statement.customer?.name}</p>
                    <p className="text-xs text-muted-foreground mt-1">
                        Period: {formatDate(statement.fromDate)} - {formatDate(statement.toDate)}
                    </p>
                    <p className="text-xs text-muted-foreground">
                        Generated: {formatDate(statement.statementDate)}
                    </p>
                </div>
                <div className="flex items-center gap-2">
                    <Badge variant={statement.status === "PAID" ? "success" : "danger"}>
                        {statement.status === "PAID" ? "PAID" : "UNPAID"}
                    </Badge>
                    <button
                        onClick={onDownloadPdf}
                        disabled={pdfLoading}
                        className="p-2 rounded-lg hover:bg-primary/10 text-primary transition-colors"
                        title="Download PDF"
                    >
                        {pdfLoading ? <Loader2 className="w-4 h-4 animate-spin" /> : <Download className="w-4 h-4" />}
                    </button>
                </div>
            </div>

            {/* Financial Cards */}
            <div className="grid grid-cols-2 gap-3">
                <div className="bg-card/50 border border-border rounded-xl p-3">
                    <p className="text-xs text-muted-foreground">Net Amount</p>
                    <p className="text-lg font-bold text-foreground">{formatCurrency(statement.netAmount)}</p>
                </div>
                <div className="bg-card/50 border border-border rounded-xl p-3">
                    <p className="text-xs text-muted-foreground">Received</p>
                    <p className="text-lg font-bold text-green-400">{formatCurrency(statement.receivedAmount)}</p>
                </div>
                <div className="bg-card/50 border border-border rounded-xl p-3">
                    <p className="text-xs text-muted-foreground">Balance</p>
                    <p className={`text-lg font-bold ${(statement.balanceAmount || 0) > 0 ? "text-red-400" : "text-green-400"}`}>
                        {formatCurrency(statement.balanceAmount)}
                    </p>
                </div>
                <div className="bg-card/50 border border-border rounded-xl p-3">
                    <p className="text-xs text-muted-foreground">Bills / Payments</p>
                    <p className="text-lg font-bold text-foreground">{billCount} / {paymentCount}</p>
                </div>
            </div>

            {/* Progress */}
            {statement.status === "NOT_PAID" && (
                <div>
                    <div className="flex items-center justify-between text-xs text-muted-foreground mb-1">
                        <span>Payment Progress</span>
                        <span>{progressPercent.toFixed(1)}%</span>
                    </div>
                    <div className="h-2.5 bg-muted rounded-full overflow-hidden">
                        <div
                            className={`h-full rounded-full transition-all ${
                                progressPercent >= 80 ? "bg-green-500" :
                                progressPercent >= 50 ? "bg-amber-500" : "bg-red-500"
                            }`}
                            style={{ width: `${progressPercent}%` }}
                        />
                    </div>
                </div>
            )}

            {/* Rounding */}
            {statement.roundingAmount !== 0 && (
                <p className="text-xs text-muted-foreground">
                    Rounding adjustment: {formatCurrency(statement.roundingAmount)}
                </p>
            )}

            {/* Record Payment */}
            {statement.status === "NOT_PAID" && (statement.balanceAmount || 0) > 0 && (
                <PermissionGate permission="PAYMENT_MANAGE">
                    <button
                        onClick={onRecordPayment}
                        className="w-full px-4 py-2.5 bg-primary text-primary-foreground rounded-lg font-medium text-sm hover:bg-primary/90 transition-colors flex items-center justify-center gap-2"
                    >
                        <CreditCard className="w-4 h-4" /> Record Payment
                    </button>
                </PermissionGate>
            )}
        </div>
    );
}

// --- Bills Tab ---
function BillsTab({
    bills, expandedBillId, billPaymentSummary, billPaymentLoading, onToggleBill
}: {
    bills: InvoiceBill[];
    expandedBillId: number | null;
    billPaymentSummary: BillPaymentSummary | null;
    billPaymentLoading: boolean;
    onToggleBill: (id: number) => void;
}) {
    if (bills.length === 0) {
        return (
            <div className="flex flex-col items-center justify-center py-12 text-muted-foreground">
                <FileText className="w-8 h-8 mb-2 opacity-30" />
                <p className="text-sm">No bills linked</p>
            </div>
        );
    }

    return (
        <div className="space-y-2">
            {/* Header */}
            <div className="hidden sm:grid grid-cols-12 gap-2 px-3 py-2 text-xs font-medium text-muted-foreground uppercase">
                <div className="col-span-1" />
                <div className="col-span-2">Bill #</div>
                <div className="col-span-2">Date</div>
                <div className="col-span-3">Vehicle</div>
                <div className="col-span-2 text-right">Amount</div>
                <div className="col-span-2 text-right">Status</div>
            </div>

            {bills.map((bill) => (
                <div key={bill.id} className="border border-border rounded-lg overflow-hidden">
                    <button
                        onClick={() => onToggleBill(bill.id!)}
                        className="w-full grid grid-cols-12 gap-2 px-3 py-2.5 text-sm hover:bg-primary/5 transition-colors items-center"
                    >
                        <div className="col-span-1">
                            {expandedBillId === bill.id
                                ? <ChevronDown className="w-4 h-4 text-muted-foreground" />
                                : <ChevronRight className="w-4 h-4 text-muted-foreground" />
                            }
                        </div>
                        <div className="col-span-2 font-medium text-foreground">{bill.billNo || "-"}</div>
                        <div className="col-span-2 text-muted-foreground">{formatDate(bill.date)}</div>
                        <div className="col-span-3 text-muted-foreground truncate">{bill.vehicle?.vehicleNumber || "-"}</div>
                        <div className="col-span-2 text-right font-medium text-foreground">{formatCurrency(bill.netAmount)}</div>
                        <div className="col-span-2 text-right">
                            <Badge
                                variant={bill.paymentStatus === "PAID" ? "success" : "danger"}
                                className="text-[10px] px-1.5 py-0"
                            >
                                {bill.paymentStatus || "N/A"}
                            </Badge>
                        </div>
                    </button>

                    {/* Expanded bill details */}
                    {expandedBillId === bill.id && (
                        <div className="border-t border-border bg-card/30 px-4 py-3 space-y-3">
                            {/* Products */}
                            {bill.products?.length > 0 && (
                                <div>
                                    <p className="text-xs font-medium text-muted-foreground mb-1">Products</p>
                                    <div className="space-y-1">
                                        {bill.products.map((p, idx) => (
                                            <div key={idx} className="flex items-center justify-between text-xs">
                                                <span className="text-foreground">{p.productName}</span>
                                                <span className="text-muted-foreground">
                                                    {p.quantity}L x {formatCurrency(p.unitPrice)} = {formatCurrency(p.amount)}
                                                </span>
                                            </div>
                                        ))}
                                    </div>
                                </div>
                            )}

                            {/* Bill info */}
                            <div className="grid grid-cols-2 gap-2 text-xs">
                                {bill.driverName && (
                                    <div><span className="text-muted-foreground">Driver:</span> <span className="text-foreground">{bill.driverName}</span></div>
                                )}
                                {bill.signatoryName && (
                                    <div><span className="text-muted-foreground">Signatory:</span> <span className="text-foreground">{bill.signatoryName}</span></div>
                                )}
                                {bill.indentNo && (
                                    <div><span className="text-muted-foreground">Indent:</span> <span className="text-foreground">{bill.indentNo}</span></div>
                                )}
                                {bill.grossAmount && bill.totalDiscount && Number(bill.totalDiscount) > 0 && (
                                    <div><span className="text-muted-foreground">Discount:</span> <span className="text-foreground">{formatCurrency(bill.totalDiscount)}</span></div>
                                )}
                            </div>

                            {/* Per-bill payments */}
                            {billPaymentLoading ? (
                                <div className="flex items-center gap-2 text-xs text-muted-foreground py-2">
                                    <Loader2 className="w-3 h-3 animate-spin" /> Loading payments...
                                </div>
                            ) : billPaymentSummary && billPaymentSummary.payments?.length > 0 ? (
                                <div>
                                    <p className="text-xs font-medium text-muted-foreground mb-1">Payments against this bill</p>
                                    <div className="space-y-1">
                                        {billPaymentSummary.payments.map((pmt, idx) => (
                                            <div key={idx} className="flex items-center justify-between text-xs bg-card/50 rounded px-2 py-1.5">
                                                <div className="flex items-center gap-2">
                                                    <span className={`px-1.5 py-0.5 rounded text-[10px] font-medium ${
                                                        PAYMENT_MODE_COLORS[pmt.paymentMode?.name] || "bg-muted text-muted-foreground"
                                                    }`}>
                                                        {pmt.paymentMode?.name}
                                                    </span>
                                                    <span className="text-muted-foreground">{formatDate(pmt.paymentDate)}</span>
                                                </div>
                                                <span className="font-medium text-green-400">{formatCurrency(pmt.amount)}</span>
                                            </div>
                                        ))}
                                    </div>
                                </div>
                            ) : (
                                <p className="text-xs text-muted-foreground">No payments recorded against this bill</p>
                            )}
                        </div>
                    )}
                </div>
            ))}
        </div>
    );
}

// --- Payments Tab ---
function PaymentsTab({
    payments, statement, showForm, onShowForm,
    paymentModes, paymentAmount, onAmountChange, paymentModeId, onModeChange,
    paymentRef, onRefChange, paymentRemarks, onRemarksChange,
    submitting, error, onSubmit, onCancel
}: {
    payments: Payment[];
    statement: Statement;
    showForm: boolean;
    onShowForm: (v: boolean) => void;
    paymentModes: PaymentMode[];
    paymentAmount: string;
    onAmountChange: (v: string) => void;
    paymentModeId: number | "";
    onModeChange: (v: number | "") => void;
    paymentRef: string;
    onRefChange: (v: string) => void;
    paymentRemarks: string;
    onRemarksChange: (v: string) => void;
    submitting: boolean;
    error: string;
    onSubmit: () => void;
    onCancel: () => void;
}) {
    const totalReceived = payments.reduce((sum, p) => sum + (p.amount || 0), 0);
    const canPay = statement.status === "NOT_PAID" && (statement.balanceAmount || 0) > 0;

    return (
        <div className="space-y-4">
            {/* Summary cards */}
            <div className="grid grid-cols-3 gap-3">
                <div className="bg-card/50 border border-border rounded-xl p-3 text-center">
                    <p className="text-xs text-muted-foreground">Total Received</p>
                    <p className="text-base font-bold text-green-400">{formatCurrency(totalReceived)}</p>
                </div>
                <div className="bg-card/50 border border-border rounded-xl p-3 text-center">
                    <p className="text-xs text-muted-foreground">Balance</p>
                    <p className={`text-base font-bold ${(statement.balanceAmount || 0) > 0 ? "text-red-400" : "text-green-400"}`}>
                        {formatCurrency(statement.balanceAmount)}
                    </p>
                </div>
                <div className="bg-card/50 border border-border rounded-xl p-3 text-center">
                    <p className="text-xs text-muted-foreground">Payments</p>
                    <p className="text-base font-bold text-foreground">{payments.length}</p>
                </div>
            </div>

            {/* Record payment button */}
            {canPay && !showForm && (
                <PermissionGate permission="PAYMENT_MANAGE">
                    <button
                        onClick={() => onShowForm(true)}
                        className="w-full px-4 py-2.5 bg-primary text-primary-foreground rounded-lg font-medium text-sm hover:bg-primary/90 transition-colors flex items-center justify-center gap-2"
                    >
                        <CreditCard className="w-4 h-4" /> Record Payment
                    </button>
                </PermissionGate>
            )}

            {/* Payment form */}
            {showForm && canPay && (
                <div className="border border-primary/30 rounded-lg p-4 space-y-3 bg-card/50">
                    <h4 className="text-sm font-semibold text-foreground">Record Payment</h4>
                    <div className="grid grid-cols-2 gap-3">
                        <div>
                            <label className="text-xs text-muted-foreground mb-1 block">Amount *</label>
                            <input
                                type="number"
                                value={paymentAmount}
                                onChange={(e) => onAmountChange(e.target.value)}
                                max={statement.balanceAmount || 0}
                                step="0.01"
                                placeholder={`Max: ${formatCurrency(statement.balanceAmount)}`}
                                className="w-full px-3 py-2 bg-card border border-border rounded-lg text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
                            />
                        </div>
                        <div>
                            <label className="text-xs text-muted-foreground mb-1 block">Payment Mode *</label>
                            <select
                                value={paymentModeId}
                                onChange={(e) => onModeChange(e.target.value ? Number(e.target.value) : "")}
                                className="w-full px-3 py-2 bg-card border border-border rounded-lg text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
                                style={{ colorScheme: "dark" }}
                            >
                                <option value="">Select mode</option>
                                {paymentModes.map(m => (
                                    <option key={m.id} value={m.id}>{m.name}</option>
                                ))}
                            </select>
                        </div>
                    </div>
                    <div>
                        <label className="text-xs text-muted-foreground mb-1 block">Reference No</label>
                        <input
                            type="text"
                            value={paymentRef}
                            onChange={(e) => onRefChange(e.target.value)}
                            placeholder="Cheque no, UTR, UPI ref..."
                            className="w-full px-3 py-2 bg-card border border-border rounded-lg text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
                        />
                    </div>
                    <div>
                        <label className="text-xs text-muted-foreground mb-1 block">Remarks</label>
                        <input
                            type="text"
                            value={paymentRemarks}
                            onChange={(e) => onRemarksChange(e.target.value)}
                            placeholder="Optional notes"
                            className="w-full px-3 py-2 bg-card border border-border rounded-lg text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
                        />
                    </div>

                    {error && <p className="text-xs text-red-400">{error}</p>}

                    <div className="flex gap-2">
                        <button
                            onClick={onSubmit}
                            disabled={submitting || !paymentAmount || !paymentModeId}
                            className="flex-1 px-4 py-2 bg-primary text-primary-foreground rounded-lg font-medium text-sm hover:bg-primary/90 transition-colors disabled:opacity-50 flex items-center justify-center gap-2"
                        >
                            {submitting ? <Loader2 className="w-4 h-4 animate-spin" /> : <CreditCard className="w-4 h-4" />}
                            {submitting ? "Processing..." : "Submit Payment"}
                        </button>
                        <button
                            onClick={onCancel}
                            className="px-4 py-2 bg-muted text-muted-foreground rounded-lg text-sm hover:bg-muted/80 transition-colors"
                        >
                            Cancel
                        </button>
                    </div>
                </div>
            )}

            {/* Timeline */}
            {payments.length === 0 ? (
                <div className="flex flex-col items-center justify-center py-8 text-muted-foreground">
                    <CreditCard className="w-8 h-8 mb-2 opacity-30" />
                    <p className="text-sm">No payments recorded</p>
                </div>
            ) : (
                <div className="relative pl-6">
                    <div className="absolute left-2.5 top-0 bottom-0 w-px bg-border" />
                    {payments.map((pmt, idx) => (
                        <div key={pmt.id || idx} className="relative pb-4 last:pb-0">
                            <div className={`absolute left-[-16px] top-1 w-3 h-3 rounded-full border-2 ${
                                idx === 0 ? "bg-primary border-primary" : "bg-card border-border"
                            }`} />
                            <div className="bg-card/50 border border-border rounded-lg p-3">
                                <div className="flex items-center justify-between mb-1">
                                    <div className="flex items-center gap-2">
                                        <span className={`px-2 py-0.5 rounded text-xs font-medium ${
                                            PAYMENT_MODE_COLORS[pmt.paymentMode?.name] || "bg-muted text-muted-foreground"
                                        }`}>
                                            {pmt.paymentMode?.name}
                                        </span>
                                        <span className="text-xs text-muted-foreground">{formatDateTime(pmt.paymentDate)}</span>
                                    </div>
                                    <span className="text-sm font-bold text-green-400">{formatCurrency(pmt.amount)}</span>
                                </div>
                                <div className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted-foreground mt-1">
                                    {pmt.referenceNo && <span>Ref: {pmt.referenceNo}</span>}
                                    {pmt.receivedBy?.name && <span>By: {pmt.receivedBy.name}</span>}
                                    {pmt.remarks && <span className="italic">{pmt.remarks}</span>}
                                    {pmt.proofImageKey && (
                                        <span className="text-primary flex items-center gap-1">
                                            <ImageIcon className="w-3 h-3" /> Proof attached
                                        </span>
                                    )}
                                </div>
                            </div>
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
}
