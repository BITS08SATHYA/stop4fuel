"use client";

import { useState, useEffect, useCallback, useRef } from "react";
import { useSearchParams } from "next/navigation";
import { GlassCard } from "@/components/ui/glass-card";
import { Badge } from "@/components/ui/badge";
import { TablePagination } from "@/components/ui/table-pagination";
import { CustomerAutocomplete } from "@/components/ui/customer-autocomplete";
import { InvoiceAutocomplete } from "@/components/ui/invoice-autocomplete";
import { PermissionGate } from "@/components/permission-gate";
import { StyledSelect } from "@/components/ui/styled-select";
import { showToast } from "@/components/ui/toast";
import {
    IndianRupee, Search, ChevronDown, ChevronRight, FileText,
    Loader2, ArrowLeft, CreditCard, Receipt, Banknote,
    ImageIcon, Truck, ShoppingCart, AlertTriangle, Link2, Link2Off
} from "lucide-react";
import {
    getInvoiceHistory, getCustomerInvoices,
    getPaymentsByBill, recordBillPayment, getBillPaymentSummary,
    getCustomerCreditInfo, PAYMENT_MODES, markInvoiceIndependent,
    submitApprovalRequest,
    type InvoiceBill, type Payment,
    type PageResponse, type BillPaymentSummary
} from "@/lib/api/station";
import { useAuth } from "@/lib/auth/auth-context";

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

export default function InvoiceExplorerPage() {
    const { user } = useAuth();
    const requestMode = user?.designation === "Cashier" && user?.role !== "OWNER" && user?.role !== "ADMIN";

    // Filters
    const [customerId, setCustomerId] = useState<number | "">("");
    const [billTypeFilter, setBillTypeFilter] = useState<string>("");
    const [paymentStatusFilter, setPaymentStatusFilter] = useState<string>("");
    const [fromDate, setFromDate] = useState("");
    const [toDate, setToDate] = useState("");

    // Invoice list (left panel)
    const [invoices, setInvoices] = useState<InvoiceBill[]>([]);
    const [listLoading, setListLoading] = useState(true);
    const [page, setPage] = useState(0);
    const [pageSize] = useState(10);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);

    // Selected invoice (right panel)
    const [selectedInvoice, setSelectedInvoice] = useState<InvoiceBill | null>(null);
    const [activeTab, setActiveTab] = useState<"details" | "payments">("details");
    const [invoicePayments, setInvoicePayments] = useState<Payment[]>([]);
    const [detailLoading, setDetailLoading] = useState(false);

    // Payment form
    const [showPaymentForm, setShowPaymentForm] = useState(false);
    // paymentModes are now a static constant (PAYMENT_MODES)
    const [paymentAmount, setPaymentAmount] = useState("");
    const [paymentModeId, setPaymentModeId] = useState("");
    const [paymentRef, setPaymentRef] = useState("");
    const [paymentRemarks, setPaymentRemarks] = useState("");
    const [paymentSubmitting, setPaymentSubmitting] = useState(false);
    const [paymentError, setPaymentError] = useState("");
    const [markingIndependent, setMarkingIndependent] = useState(false);

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

    // Mobile detail view
    const [showMobileDetail, setShowMobileDetail] = useState(false);

    // KPI aggregates from current page data
    const [kpiCash, setKpiCash] = useState(0);
    const [kpiCredit, setKpiCredit] = useState(0);
    const [kpiPaid, setKpiPaid] = useState(0);
    const [kpiUnpaid, setKpiUnpaid] = useState(0);

    // Deep-link: auto-select invoice from query param ?invoiceId=123
    const searchParams = useSearchParams();
    const autoSelectedRef = useRef(false);
    useEffect(() => {
        const invId = searchParams.get("invoiceId");
        if (invId && !autoSelectedRef.current) {
            autoSelectedRef.current = true;
            const found = invoices.find(i => i.id === Number(invId));
            if (found) {
                selectInvoice(found);
            } else {
                // Fetch directly by ID
                import("@/lib/api/station/invoices").then(({ getInvoiceById }) => {
                    getInvoiceById(Number(invId)).then(inv => {
                        if (inv) selectInvoice(inv);
                    }).catch(console.error);
                });
            }
        }
    }, [invoices, searchParams]);

    // Payment modes are static constants (PAYMENT_MODES)

    // Load invoices
    const fetchInvoices = useCallback(async () => {
        setListLoading(true);
        try {
            let res: PageResponse<InvoiceBill>;
            if (customerId) {
                res = await getCustomerInvoices(customerId as number, page, pageSize, {
                    billType: billTypeFilter || undefined,
                    paymentStatus: paymentStatusFilter || undefined,
                    fromDate: fromDate || undefined,
                    toDate: toDate || undefined,
                });
            } else {
                res = await getInvoiceHistory(page, pageSize, {
                    billType: billTypeFilter || undefined,
                    paymentStatus: paymentStatusFilter || undefined,
                    fromDate: fromDate || undefined,
                    toDate: toDate || undefined,
                });
            }
            setInvoices(res.content);
            setTotalPages(res.totalPages);
            setTotalElements(res.totalElements);

            // Compute KPIs from fetched page
            let cash = 0, credit = 0, paid = 0, unpaid = 0;
            res.content.forEach(inv => {
                if (inv.billType === "CASH") cash += inv.netAmount || 0;
                else credit += inv.netAmount || 0;
                if (inv.paymentStatus === "PAID") paid++;
                else unpaid++;
            });
            setKpiCash(cash);
            setKpiCredit(credit);
            setKpiPaid(paid);
            setKpiUnpaid(unpaid);
        } catch (err) {
            console.error(err);
        } finally {
            setListLoading(false);
        }
    }, [page, pageSize, customerId, billTypeFilter, paymentStatusFilter, fromDate, toDate]);

    useEffect(() => { fetchInvoices(); }, [fetchInvoices]);

    // Select invoice
    const selectInvoice = async (inv: InvoiceBill) => {
        setSelectedInvoice(inv);
        setActiveTab("details");
        setShowPaymentForm(false);
        setPaymentError("");
        setShowMobileDetail(true);
        setDetailLoading(true);
        try {
            const payments = await getPaymentsByBill(inv.id!);
            setInvoicePayments(payments);
        } catch (err) {
            console.error(err);
        } finally {
            setDetailLoading(false);
        }
    };

    // Record payment (or submit approval request when cashier)
    const handleRecordPayment = async () => {
        if (!selectedInvoice || !paymentAmount || !paymentModeId) return;
        setPaymentSubmitting(true);
        setPaymentError("");
        try {
            if (requestMode) {
                await submitApprovalRequest({
                    requestType: "RECORD_INVOICE_PAYMENT",
                    customerId: selectedInvoice.customer?.id ?? null,
                    payload: {
                        invoiceBillId: selectedInvoice.id,
                        amount: Number(paymentAmount),
                        paymentMode: paymentModeId,
                        referenceNo: paymentRef || undefined,
                        remarks: paymentRemarks || undefined,
                    },
                });
                showToast.success("Request submitted — admin will review it shortly");
                setShowPaymentForm(false);
                setPaymentAmount("");
                setPaymentModeId("");
                setPaymentRef("");
                setPaymentRemarks("");
                return;
            }

            await recordBillPayment(selectedInvoice.id!, {
                amount: Number(paymentAmount),
                paymentMode: paymentModeId,
                referenceNo: paymentRef || undefined,
                remarks: paymentRemarks || undefined,
            });
            // Refresh
            const payments = await getPaymentsByBill(selectedInvoice.id!);
            setInvoicePayments(payments);
            setShowPaymentForm(false);
            setPaymentAmount("");
            setPaymentModeId("");
            setPaymentRef("");
            setPaymentRemarks("");
            fetchInvoices(); // refresh list to update statuses
        } catch (err: any) {
            setPaymentError(err?.message || (requestMode ? "Submit failed" : "Payment failed"));
        } finally {
            setPaymentSubmitting(false);
        }
    };

    // Customer change
    const handleCustomerChange = (id: string | number) => {
        const numId = id ? Number(id) : "";
        setCustomerId(numId);
        setPage(0);
        setSelectedInvoice(null);
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
        setSelectedInvoice(null);
    };

    // Can this invoice accept direct payment?
    const canPayDirectly = (inv: InvoiceBill): boolean => {
        const isStatementCustomer = inv.customer?.partyType === "Statement";
        return inv.billType === "CREDIT"
            && inv.paymentStatus !== "PAID"
            && !inv.statement
            && (!isStatementCustomer || !!inv.independent);
    };

    // Mark invoice as independent
    const handleMarkIndependent = async (invoiceId: number) => {
        setMarkingIndependent(true);
        try {
            const updated = await markInvoiceIndependent(invoiceId);
            setSelectedInvoice(updated);
            fetchInvoices();
        } catch (err: any) {
            setPaymentError(err?.message || "Failed to mark independent");
        } finally {
            setMarkingIndependent(false);
        }
    };

    // Compute balance for selected invoice
    const selectedBalance = selectedInvoice
        ? (selectedInvoice.netAmount || 0) - invoicePayments.reduce((s, p) => s + (p.amount || 0), 0)
        : 0;

    return (
        <PermissionGate permission="INVOICE_VIEW">
            <div className="flex flex-col h-[calc(100vh-4rem)] overflow-hidden gap-3">
                {/* Page Header */}
                <div className="flex items-center justify-between shrink-0">
                    <div>
                        <h1 className="text-2xl font-bold text-foreground">Invoice Explorer</h1>
                        <p className="text-sm text-muted-foreground mt-1">Search invoices, view details, and record payments</p>
                    </div>
                </div>

                {/* KPI Bar */}
                <div className="grid grid-cols-2 md:grid-cols-4 gap-3 shrink-0">
                    <GlassCard className="p-4">
                        <div className="flex items-center gap-2 text-xs text-muted-foreground mb-1">
                            <FileText className="w-3.5 h-3.5" />
                            Total Invoices
                        </div>
                        <div className="text-xl font-bold text-foreground">{totalElements.toLocaleString()}</div>
                    </GlassCard>
                    <GlassCard className="p-4">
                        <div className="flex items-center gap-2 text-xs text-muted-foreground mb-1">
                            <Banknote className="w-3.5 h-3.5" />
                            Cash (page)
                        </div>
                        <div className="text-xl font-bold text-green-400">{formatCompact(kpiCash)}</div>
                    </GlassCard>
                    <GlassCard className="p-4">
                        <div className="flex items-center gap-2 text-xs text-muted-foreground mb-1">
                            <Receipt className="w-3.5 h-3.5" />
                            Credit (page)
                        </div>
                        <div className="text-xl font-bold text-amber-400">{formatCompact(kpiCredit)}</div>
                    </GlassCard>
                    <GlassCard className="p-4">
                        <div className="flex items-center gap-2 text-xs text-muted-foreground mb-1">
                            <CreditCard className="w-3.5 h-3.5" />
                            Paid / Unpaid (page)
                        </div>
                        <div className="text-xl font-bold text-foreground">
                            <span className="text-green-400">{kpiPaid}</span>
                            {" / "}
                            <span className="text-red-400">{kpiUnpaid}</span>
                        </div>
                    </GlassCard>
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
                            <label className="text-xs text-muted-foreground mb-1 block">Search Invoice</label>
                            <InvoiceAutocomplete
                                value={null}
                                onChange={(inv) => {
                                    if (inv) selectInvoice(inv);
                                }}
                                placeholder="Search by bill #..."
                            />
                        </div>
                        <div className="w-32">
                            <label className="text-xs text-muted-foreground mb-1 block">Bill Type</label>
                            <StyledSelect
                                value={billTypeFilter}
                                onChange={(val) => { setBillTypeFilter(val); handleFilterChange(); }}
                                options={[
                                    { value: "", label: "All" },
                                    { value: "CASH", label: "Cash" },
                                    { value: "CREDIT", label: "Credit" },
                                ]}
                            />
                        </div>
                        <div className="w-32">
                            <label className="text-xs text-muted-foreground mb-1 block">Status</label>
                            <StyledSelect
                                value={paymentStatusFilter}
                                onChange={(val) => { setPaymentStatusFilter(val); handleFilterChange(); }}
                                options={[
                                    { value: "", label: "All" },
                                    { value: "PAID", label: "Paid" },
                                    { value: "NOT_PAID", label: "Unpaid" },
                                ]}
                            />
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
                                    <div className="flex items-center gap-2 whitespace-nowrap">
                                        <span className="text-[10px] uppercase text-muted-foreground tracking-wider">Invoices</span>
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
                                            customerInfo.totalBilled > 0 && (customerInfo.totalPaid / customerInfo.totalBilled) * 100 >= 80
                                                ? "text-green-400"
                                                : customerInfo.totalBilled > 0 && (customerInfo.totalPaid / customerInfo.totalBilled) * 100 >= 50
                                                    ? "text-amber-400" : "text-red-400"
                                        }`}>
                                            {customerInfo.totalBilled > 0
                                                ? ((customerInfo.totalPaid / customerInfo.totalBilled) * 100).toFixed(1) + "%"
                                                : "N/A"
                                            }
                                        </span>
                                    </div>
                                </div>
                            </GlassCard>
                        ) : null}
                    </div>
                )}

                {/* Two-Column Layout */}
                <div className="flex gap-4 flex-1 min-h-0">
                    {/* Left Panel: Invoice List */}
                    <div className={`w-full lg:w-[40%] flex flex-col ${showMobileDetail ? "hidden lg:flex" : "flex"}`}>
                        <GlassCard className="flex-1 p-0 flex flex-col overflow-hidden">
                            <div className="px-4 py-3 border-b border-border">
                                <h2 className="text-sm font-semibold text-foreground flex items-center gap-2">
                                    <FileText className="w-4 h-4" />
                                    Invoices
                                    <span className="text-xs text-muted-foreground font-normal">({totalElements})</span>
                                </h2>
                            </div>

                            <div className="flex-1 overflow-y-auto">
                                {listLoading ? (
                                    <div className="flex items-center justify-center py-20">
                                        <Loader2 className="w-6 h-6 animate-spin text-primary" />
                                    </div>
                                ) : invoices.length === 0 ? (
                                    <div className="flex flex-col items-center justify-center py-20 text-muted-foreground">
                                        <Search className="w-10 h-10 mb-3 opacity-30" />
                                        <p className="text-sm">No invoices found</p>
                                        <p className="text-xs mt-1">Try adjusting your filters</p>
                                    </div>
                                ) : (
                                    <div className="divide-y divide-border">
                                        {invoices.map((inv) => (
                                            <button
                                                key={inv.id}
                                                onClick={() => selectInvoice(inv)}
                                                className={`w-full px-4 py-3 text-left transition-all hover:bg-primary/5 ${
                                                    selectedInvoice?.id === inv.id
                                                        ? "bg-primary/10 ring-2 ring-primary/30 ring-inset"
                                                        : ""
                                                }`}
                                            >
                                                <div className="flex items-center justify-between mb-1">
                                                    <div className="flex items-center gap-2">
                                                        <span className="text-sm font-semibold text-foreground">{inv.billNo || "-"}</span>
                                                        <Badge
                                                            variant={inv.billType === "CASH" ? "default" : "warning"}
                                                            className="text-[10px] px-1.5 py-0"
                                                        >
                                                            {inv.billType}
                                                        </Badge>
                                                    </div>
                                                    <div className="flex items-center gap-1.5">
                                                        {inv.statement && (
                                                            <span title={`Linked to ${inv.statement.statementNo}`}><Link2 className="w-3 h-3 text-muted-foreground" /></span>
                                                        )}
                                                        <Badge
                                                            variant={inv.paymentStatus === "PAID" ? "success" : "danger"}
                                                            className="text-[10px] px-1.5 py-0"
                                                        >
                                                            {inv.paymentStatus === "PAID" ? "PAID" : "UNPAID"}
                                                        </Badge>
                                                    </div>
                                                </div>
                                                <div className="text-xs text-muted-foreground mb-1">
                                                    {inv.customer?.name || "-"}
                                                    {inv.vehicle?.vehicleNumber && ` - ${inv.vehicle.vehicleNumber}`}
                                                </div>
                                                <div className="flex items-center justify-between text-xs">
                                                    <span className="text-muted-foreground">{formatDate(inv.date)}</span>
                                                    <span className="font-medium text-foreground">{formatCurrency(inv.netAmount)}</span>
                                                </div>
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
                            {!selectedInvoice ? (
                                <div className="flex-1 flex flex-col items-center justify-center text-muted-foreground">
                                    <FileText className="w-12 h-12 mb-3 opacity-20" />
                                    <p className="text-sm">Select an invoice to view details</p>
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
                                        {(["details", "payments"] as const).map((tab) => (
                                            <button
                                                key={tab}
                                                onClick={() => setActiveTab(tab)}
                                                className={`flex-1 px-4 py-3 text-sm font-medium capitalize transition-colors ${
                                                    activeTab === tab
                                                        ? "text-primary border-b-2 border-primary"
                                                        : "text-muted-foreground hover:text-foreground"
                                                }`}
                                            >
                                                {tab === "details" ? "Details" : `Payments (${invoicePayments.length})`}
                                            </button>
                                        ))}
                                    </div>

                                    {/* Tab Content */}
                                    <div className="flex-1 overflow-y-auto p-4">
                                        {detailLoading ? (
                                            <div className="flex items-center justify-center py-20">
                                                <Loader2 className="w-6 h-6 animate-spin text-primary" />
                                            </div>
                                        ) : activeTab === "details" ? (
                                            <InvoiceDetailsTab
                                                invoice={selectedInvoice}
                                                payments={invoicePayments}
                                                balance={selectedBalance}
                                                canPay={canPayDirectly(selectedInvoice)}
                                                onRecordPayment={() => { setActiveTab("payments"); setShowPaymentForm(true); }}
                                                onMarkIndependent={() => handleMarkIndependent(selectedInvoice.id!)}
                                                markingIndependent={markingIndependent}
                                                requestMode={requestMode}
                                            />
                                        ) : (
                                            <InvoicePaymentsTab
                                                invoice={selectedInvoice}
                                                payments={invoicePayments}
                                                balance={selectedBalance}
                                                canPay={canPayDirectly(selectedInvoice)}
                                                showForm={showPaymentForm}
                                                onShowForm={setShowPaymentForm}
                                                paymentModes={PAYMENT_MODES}
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
                                                onSubmit={handleRecordPayment}
                                                onCancel={() => { setShowPaymentForm(false); setPaymentError(""); }}
                                                requestMode={requestMode}
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

// --- Details Tab ---
function InvoiceDetailsTab({
    invoice, payments, balance, canPay, onRecordPayment, onMarkIndependent, markingIndependent, requestMode
}: {
    invoice: InvoiceBill;
    payments: Payment[];
    balance: number;
    canPay: boolean;
    onRecordPayment: () => void;
    onMarkIndependent: () => void;
    markingIndependent: boolean;
    requestMode: boolean;
}) {
    const totalPaid = payments.reduce((s, p) => s + (p.amount || 0), 0);

    return (
        <div className="space-y-4">
            {/* Header */}
            <div className="flex items-start justify-between">
                <div>
                    <h3 className="text-lg font-bold text-foreground">{invoice.billNo || "No Bill #"}</h3>
                    <p className="text-sm text-muted-foreground">{invoice.customer?.name || "-"}</p>
                    <p className="text-xs text-muted-foreground mt-1">{formatDateTime(invoice.date)}</p>
                </div>
                <div className="flex items-center gap-2">
                    <Badge variant={invoice.billType === "CASH" ? "default" : "warning"}>
                        {invoice.billType}
                    </Badge>
                    <Badge variant={invoice.paymentStatus === "PAID" ? "success" : "danger"}>
                        {invoice.paymentStatus === "PAID" ? "PAID" : "UNPAID"}
                    </Badge>
                </div>
            </div>

            {/* Statement link status */}
            {invoice.billType === "CREDIT" && (() => {
                const isStatementCustomer = invoice.customer?.partyType === "Statement";
                if (invoice.statement) {
                    // Linked to a statement
                    return (
                        <div className="flex items-center justify-between gap-2 px-3 py-2 rounded-lg text-xs bg-blue-500/10 text-blue-400 border border-blue-500/20">
                            <div className="flex items-center gap-2">
                                <Link2 className="w-3.5 h-3.5 flex-shrink-0" />
                                <span>Linked to statement <span className="font-bold">{invoice.statement.statementNo}</span> — pay via statement</span>
                            </div>
                            <button
                                onClick={onMarkIndependent}
                                disabled={markingIndependent}
                                className="px-2 py-1 bg-amber-500/20 text-amber-400 border border-amber-500/30 rounded text-xs font-medium hover:bg-amber-500/30 transition-colors disabled:opacity-50 flex-shrink-0"
                            >
                                {markingIndependent ? "..." : "Unlink"}
                            </button>
                        </div>
                    );
                } else if (isStatementCustomer && !invoice.independent) {
                    // Statement customer, not linked yet, not independent
                    return (
                        <div className="flex items-center justify-between gap-2 px-3 py-2 rounded-lg text-xs bg-amber-500/10 text-amber-400 border border-amber-500/20">
                            <div className="flex items-center gap-2">
                                <AlertTriangle className="w-3.5 h-3.5 flex-shrink-0" />
                                <span>Statement customer — awaiting statement</span>
                            </div>
                            <button
                                onClick={onMarkIndependent}
                                disabled={markingIndependent}
                                className="px-2 py-1 bg-primary/20 text-primary border border-primary/30 rounded text-xs font-medium hover:bg-primary/30 transition-colors disabled:opacity-50 flex-shrink-0"
                            >
                                {markingIndependent ? "..." : "Mark Independent"}
                            </button>
                        </div>
                    );
                } else {
                    // Local customer or independent invoice
                    return (
                        <div className="flex items-center gap-2 px-3 py-2 rounded-lg text-xs bg-green-500/10 text-green-400 border border-green-500/20">
                            <Link2Off className="w-3.5 h-3.5" />
                            {invoice.independent ? "Independent invoice — direct payment allowed" : "Not linked to any statement — direct payment allowed"}
                        </div>
                    );
                }
            })()}

            {/* Financial Cards */}
            <div className="grid grid-cols-2 gap-3">
                <div className="bg-card/50 border border-border rounded-xl p-3">
                    <p className="text-xs text-muted-foreground">Gross Amount</p>
                    <p className="text-lg font-bold text-foreground">{formatCurrency(invoice.grossAmount)}</p>
                </div>
                <div className="bg-card/50 border border-border rounded-xl p-3">
                    <p className="text-xs text-muted-foreground">Net Amount</p>
                    <p className="text-lg font-bold text-foreground">{formatCurrency(invoice.netAmount)}</p>
                </div>
                {invoice.billType === "CREDIT" && (
                    <>
                        <div className="bg-card/50 border border-border rounded-xl p-3">
                            <p className="text-xs text-muted-foreground">Received</p>
                            <p className="text-lg font-bold text-green-400">{formatCurrency(totalPaid)}</p>
                        </div>
                        <div className="bg-card/50 border border-border rounded-xl p-3">
                            <p className="text-xs text-muted-foreground">Balance</p>
                            <p className={`text-lg font-bold ${balance > 0 ? "text-red-400" : "text-green-400"}`}>
                                {formatCurrency(balance)}
                            </p>
                        </div>
                    </>
                )}
            </div>

            {/* Discount */}
            {invoice.totalDiscount && Number(invoice.totalDiscount) > 0 && (
                <p className="text-xs text-muted-foreground">
                    Discount applied: {formatCurrency(invoice.totalDiscount)}
                </p>
            )}

            {/* Products */}
            {invoice.products?.length > 0 && (
                <div>
                    <h4 className="text-xs font-medium text-muted-foreground uppercase tracking-wider mb-2">Products</h4>
                    <div className="space-y-1.5">
                        {invoice.products.map((p, idx) => (
                            <div key={idx} className="flex items-center justify-between text-sm bg-card/50 border border-border rounded-lg px-3 py-2">
                                <div>
                                    <span className="font-medium text-foreground">{p.productName}</span>
                                    {p.nozzleName && <span className="text-xs text-muted-foreground ml-2">({p.nozzleName})</span>}
                                </div>
                                <div className="text-right text-xs text-muted-foreground">
                                    {p.quantity}L x {formatCurrency(p.unitPrice)} = <span className="text-foreground font-medium">{formatCurrency(p.amount)}</span>
                                </div>
                            </div>
                        ))}
                    </div>
                </div>
            )}

            {/* Info Grid */}
            <div className="grid grid-cols-2 gap-2 text-xs">
                {invoice.vehicle?.vehicleNumber && (
                    <div className="flex items-center gap-1.5">
                        <Truck className="w-3 h-3 text-muted-foreground" />
                        <span className="text-muted-foreground">Vehicle:</span>
                        <span className="text-foreground">{invoice.vehicle.vehicleNumber}</span>
                    </div>
                )}
                {invoice.driverName && (
                    <div><span className="text-muted-foreground">Driver:</span> <span className="text-foreground">{invoice.driverName}</span></div>
                )}
                {invoice.signatoryName && (
                    <div><span className="text-muted-foreground">Signatory:</span> <span className="text-foreground">{invoice.signatoryName}</span></div>
                )}
                {invoice.indentNo && (
                    <div><span className="text-muted-foreground">Indent:</span> <span className="text-foreground">{invoice.indentNo}</span></div>
                )}
                {invoice.vehicleKM && (
                    <div><span className="text-muted-foreground">KM:</span> <span className="text-foreground">{invoice.vehicleKM.toLocaleString()}</span></div>
                )}
                {invoice.paymentMode && (
                    <div><span className="text-muted-foreground">Mode:</span> <span className="text-foreground">{invoice.paymentMode}</span></div>
                )}
            </div>

            {/* Record / Request Payment button */}
            {canPay && balance > 0 && (
                <button
                    onClick={onRecordPayment}
                    className="w-full mt-2 px-4 py-2.5 bg-primary text-primary-foreground rounded-lg font-medium text-sm hover:bg-primary/90 transition-colors flex items-center justify-center gap-2"
                >
                    <CreditCard className="w-4 h-4" /> {requestMode ? "Request Payment" : "Record Payment"}
                </button>
            )}
        </div>
    );
}

// --- Payments Tab ---
function InvoicePaymentsTab({
    invoice, payments, balance, canPay, showForm, onShowForm,
    paymentModes, paymentAmount, onAmountChange, paymentModeId, onModeChange,
    paymentRef, onRefChange, paymentRemarks, onRemarksChange,
    submitting, error, onSubmit, onCancel, requestMode
}: {
    invoice: InvoiceBill;
    payments: Payment[];
    balance: number;
    canPay: boolean;
    showForm: boolean;
    onShowForm: (v: boolean) => void;
    paymentModes: readonly string[];
    paymentAmount: string;
    onAmountChange: (v: string) => void;
    paymentModeId: string;
    onModeChange: (v: string) => void;
    paymentRef: string;
    onRefChange: (v: string) => void;
    paymentRemarks: string;
    onRemarksChange: (v: string) => void;
    submitting: boolean;
    error: string;
    onSubmit: () => void;
    onCancel: () => void;
    requestMode: boolean;
}) {
    const totalReceived = payments.reduce((s, p) => s + (p.amount || 0), 0);

    return (
        <div className="space-y-4">
            {/* Summary */}
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
                <div className="bg-card/50 border border-border rounded-xl p-3 text-center">
                    <p className="text-xs text-muted-foreground">Received</p>
                    <p className="text-base font-bold text-green-400">{formatCurrency(totalReceived)}</p>
                </div>
                <div className="bg-card/50 border border-border rounded-xl p-3 text-center">
                    <p className="text-xs text-muted-foreground">Balance</p>
                    <p className={`text-base font-bold ${balance > 0 ? "text-red-400" : "text-green-400"}`}>{formatCurrency(balance)}</p>
                </div>
                <div className="bg-card/50 border border-border rounded-xl p-3 text-center">
                    <p className="text-xs text-muted-foreground">Payments</p>
                    <p className="text-base font-bold text-foreground">{payments.length}</p>
                </div>
            </div>

            {/* Statement linked warning */}
            {invoice.billType === "CREDIT" && invoice.statement && (
                <div className="flex items-start gap-2 px-3 py-2.5 rounded-lg text-xs bg-blue-500/10 text-blue-400 border border-blue-500/20">
                    <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5" />
                    <div>
                        This invoice is linked to statement <span className="font-bold">{invoice.statement.statementNo}</span>.
                        Payments should be recorded against the statement, not this individual bill.
                        Use the <span className="font-bold">Statement Explorer</span> to record payments.
                    </div>
                </div>
            )}

            {/* Record / Request payment button */}
            {canPay && balance > 0 && !showForm && (
                <PermissionGate permission={requestMode ? "APPROVAL_REQUEST_CREATE" : "PAYMENT_CREATE"}>
                    <button
                        onClick={() => onShowForm(true)}
                        className="w-full px-4 py-2.5 bg-primary text-primary-foreground rounded-lg font-medium text-sm hover:bg-primary/90 transition-colors flex items-center justify-center gap-2"
                    >
                        <CreditCard className="w-4 h-4" /> {requestMode ? "Request Payment" : "Record Payment"}
                    </button>
                </PermissionGate>
            )}

            {/* Payment form */}
            {showForm && canPay && (
                <div className="border border-primary/30 rounded-lg p-4 space-y-3 bg-card/50">
                    <h4 className="text-sm font-semibold text-foreground">{requestMode ? "Request Payment (admin approval required)" : "Record Payment"}</h4>

                    <div className="grid grid-cols-2 gap-3">
                        <div>
                            <div className="flex items-center justify-between mb-1">
                                <label className="text-xs text-muted-foreground">Amount *</label>
                                <label className="flex items-center gap-1 cursor-pointer">
                                    <input
                                        type="checkbox"
                                        checked={paymentAmount === String(balance)}
                                        onChange={(e) => onAmountChange(e.target.checked ? String(balance) : "")}
                                        className="w-3.5 h-3.5 rounded accent-primary"
                                    />
                                    <span className="text-xs text-muted-foreground">Full Bal</span>
                                </label>
                            </div>
                            <input
                                type="number"
                                value={paymentAmount}
                                onChange={(e) => onAmountChange(e.target.value)}
                                max={balance}
                                step="0.01"
                                placeholder={`Max: ${formatCurrency(balance)}`}
                                className="w-full px-3 py-2 bg-card border border-border rounded-lg text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
                            />
                        </div>
                        <div>
                            <label className="text-xs text-muted-foreground mb-1 block">Payment Mode *</label>
                            <StyledSelect
                                value={String(paymentModeId)}
                                onChange={(val) => onModeChange(val)}
                                placeholder="Select mode"
                                options={[{ value: "", label: "Select mode" }, ...paymentModes.map(m => ({ value: m, label: m }))]}
                            />
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
                            {submitting ? (requestMode ? "Submitting…" : "Processing...") : (requestMode ? "Submit Request" : "Submit Payment")}
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

            {/* Payment timeline */}
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
                                            PAYMENT_MODE_COLORS[pmt.paymentMode] || "bg-muted text-muted-foreground"
                                        }`}>
                                            {pmt.paymentMode}
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
                                            <ImageIcon className="w-3 h-3" /> Proof
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
