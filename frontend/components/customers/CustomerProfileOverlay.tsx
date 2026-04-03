"use client";

import { useState, useEffect } from "react";
import { Badge } from "@/components/ui/badge";
import { GlassCard } from "@/components/ui/glass-card";
import {
    Phone, FileText, Receipt, CreditCard, ExternalLink,
    ShieldAlert, ShieldCheck, ChevronLeft, ChevronRight, Filter
} from "lucide-react";
import {
    blockCustomer, unblockCustomer, getCustomerInvoices,
    type CreditCustomerSummary, type CreditCustomerDetail,
    type InvoiceBill, type PageResponse
} from "@/lib/api/station";
import Link from "next/link";

type OverlayTab = "invoices" | "statements" | "payments";

interface CustomerProfileOverlayProps {
    isOpen: boolean;
    onClose: () => void;
    customer: CreditCustomerSummary | null;
    detail: CreditCustomerDetail | null;
    onBlockStatusChange: () => void;
}

const fmt = (n: number) =>
    Number(n).toLocaleString("en-IN", { minimumFractionDigits: 2 });

export function CustomerProfileOverlay({
    isOpen,
    onClose,
    customer,
    detail,
    onBlockStatusChange,
}: CustomerProfileOverlayProps) {
    const [activeTab, setActiveTab] = useState<OverlayTab>("invoices");
    const [blockLoading, setBlockLoading] = useState(false);

    // Invoices tab state
    const [invoices, setInvoices] = useState<InvoiceBill[]>([]);
    const [invoicePage, setInvoicePage] = useState(0);
    const [invoiceTotalPages, setInvoiceTotalPages] = useState(0);
    const [invoiceTotalElements, setInvoiceTotalElements] = useState(0);
    const [invoiceLoading, setInvoiceLoading] = useState(false);
    const [billTypeFilter, setBillTypeFilter] = useState("");
    const [paymentStatusFilter, setPaymentStatusFilter] = useState("");
    const [fromDate, setFromDate] = useState("");
    const [toDate, setToDate] = useState("");

    // Reset state when customer changes or overlay opens
    useEffect(() => {
        if (isOpen && customer) {
            setActiveTab("invoices");
            setInvoicePage(0);
            setBillTypeFilter("");
            setPaymentStatusFilter("");
            setFromDate("");
            setToDate("");
        }
    }, [isOpen, customer?.customerId]);

    // Fetch invoices when tab/page/filters change
    useEffect(() => {
        if (isOpen && customer && activeTab === "invoices") {
            loadInvoices();
        }
    }, [isOpen, customer?.customerId, activeTab, invoicePage, billTypeFilter, paymentStatusFilter, fromDate, toDate]);

    const loadInvoices = async () => {
        if (!customer) return;
        setInvoiceLoading(true);
        try {
            const filters: { billType?: string; paymentStatus?: string; fromDate?: string; toDate?: string } = {};
            if (billTypeFilter) filters.billType = billTypeFilter;
            if (paymentStatusFilter) filters.paymentStatus = paymentStatusFilter;
            if (fromDate) filters.fromDate = fromDate;
            if (toDate) filters.toDate = toDate;
            const res: PageResponse<InvoiceBill> = await getCustomerInvoices(customer.customerId, invoicePage, 15, filters);
            setInvoices(res.content);
            setInvoiceTotalPages(res.totalPages);
            setInvoiceTotalElements(res.totalElements);
        } catch (e) {
            console.error("Failed to load invoices", e);
            setInvoices([]);
        } finally {
            setInvoiceLoading(false);
        }
    };

    const handleBlockToggle = async () => {
        if (!customer) return;
        setBlockLoading(true);
        try {
            if (customer.status === "BLOCKED") {
                await unblockCustomer(customer.customerId);
            } else {
                await blockCustomer(customer.customerId);
            }
            onBlockStatusChange();
        } catch (e) {
            console.error("Failed to toggle block status", e);
        } finally {
            setBlockLoading(false);
        }
    };

    const handleFilterApply = () => {
        setInvoicePage(0);
    };

    if (!isOpen || !customer) return null;

    const isBlocked = customer.status === "BLOCKED";
    const statusBadge = customer.status === "ACTIVE"
        ? <Badge variant="success" className="text-[10px] px-1.5 py-0">ACTIVE</Badge>
        : customer.status === "BLOCKED"
            ? <Badge variant="danger" className="text-[10px] px-1.5 py-0">BLOCKED</Badge>
            : <Badge variant="warning" className="text-[10px] px-1.5 py-0">{customer.status || "INACTIVE"}</Badge>;

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm" onClick={onClose}>
            <div
                className="relative w-full max-w-6xl max-h-[90vh] bg-card border border-border rounded-2xl shadow-2xl overflow-hidden flex flex-col"
                onClick={(e) => e.stopPropagation()}
            >
                {/* Header */}
                <div className="px-5 py-4 border-b border-border bg-muted/20">
                    <div className="flex items-start justify-between">
                        <div className="min-w-0 flex-1">
                            <div className="flex items-center gap-2">
                                <h2 className="text-base font-bold text-foreground truncate">
                                    {customer.customerName || "Unnamed Customer"}
                                </h2>
                                {statusBadge}
                            </div>
                            <div className="flex items-center gap-3 mt-1 text-[11px] text-muted-foreground">
                                {customer.phoneNumbers && customer.phoneNumbers.length > 0 && (
                                    <span className="flex items-center gap-1">
                                        <Phone className="w-3 h-3" />
                                        {customer.phoneNumbers.join(", ")}
                                    </span>
                                )}
                                {customer.groupName && (
                                    <span className="text-muted-foreground/70">{customer.groupName}</span>
                                )}
                                <Link
                                    href={`/customers/${customer.customerId}`}
                                    className="flex items-center gap-0.5 text-primary hover:underline"
                                >
                                    <ExternalLink className="w-3 h-3" />
                                    View Full Profile
                                </Link>
                            </div>
                        </div>
                        <div className="flex items-center gap-2 flex-shrink-0">
                            <button
                                onClick={handleBlockToggle}
                                disabled={blockLoading}
                                className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium transition-colors disabled:opacity-50 ${
                                    isBlocked
                                        ? "bg-emerald-500/20 text-emerald-400 border border-emerald-500/30 hover:bg-emerald-500/30"
                                        : "bg-rose-500/20 text-rose-400 border border-rose-500/30 hover:bg-rose-500/30"
                                }`}
                            >
                                {isBlocked ? (
                                    <><ShieldCheck className="w-3.5 h-3.5" />{blockLoading ? "Unblocking..." : "Unblock"}</>
                                ) : (
                                    <><ShieldAlert className="w-3.5 h-3.5" />{blockLoading ? "Blocking..." : "Block"}</>
                                )}
                            </button>
                            <button
                                onClick={onClose}
                                className="text-muted-foreground hover:text-foreground transition-colors text-lg px-2"
                            >
                                &times;
                            </button>
                        </div>
                    </div>
                </div>

                {/* Credit Summary Bar */}
                <div className="px-5 py-3 border-b border-border">
                    <div className="grid grid-cols-4 gap-3">
                        <div className="bg-muted/30 rounded-lg px-3 py-2">
                            <div className="text-[9px] uppercase tracking-wider text-muted-foreground">Total Billed</div>
                            <div className="text-sm font-bold text-foreground">{fmt(customer.totalBilled)}</div>
                        </div>
                        <div className="bg-muted/30 rounded-lg px-3 py-2">
                            <div className="text-[9px] uppercase tracking-wider text-emerald-400">Total Paid</div>
                            <div className="text-sm font-bold text-emerald-400">{fmt(customer.totalPaid)}</div>
                        </div>
                        <div className="bg-muted/30 rounded-lg px-3 py-2">
                            <div className="text-[9px] uppercase tracking-wider text-amber-400">Ledger Balance</div>
                            <div className="text-sm font-bold text-amber-400">{fmt(customer.ledgerBalance)}</div>
                        </div>
                        <div className="bg-muted/30 rounded-lg px-3 py-2">
                            <div className="text-[9px] uppercase tracking-wider text-muted-foreground">Credit Limit</div>
                            <div className="text-sm font-bold text-foreground">
                                {customer.creditLimitAmount != null && Number(customer.creditLimitAmount) > 0
                                    ? fmt(customer.creditLimitAmount)
                                    : "No limit"}
                            </div>
                        </div>
                    </div>
                </div>

                {/* Tabs */}
                <div className="px-5 pt-2 flex gap-1 border-b border-border">
                    {([
                        { key: "invoices" as OverlayTab, label: "Invoices", icon: FileText },
                        { key: "statements" as OverlayTab, label: "Statements", icon: Receipt },
                        { key: "payments" as OverlayTab, label: "Payments", icon: CreditCard },
                    ]).map(tab => (
                        <button
                            key={tab.key}
                            onClick={() => setActiveTab(tab.key)}
                            className={`flex items-center gap-1.5 px-3 py-2 text-xs font-medium transition-colors border-b-2 -mb-px ${
                                activeTab === tab.key
                                    ? "border-primary text-foreground"
                                    : "border-transparent text-muted-foreground hover:text-foreground"
                            }`}
                        >
                            <tab.icon className="w-3 h-3" />
                            {tab.label}
                        </button>
                    ))}
                </div>

                {/* Tab Content */}
                <div className="flex-1 overflow-y-auto">
                    {activeTab === "invoices" && (
                        <InvoicesTabContent
                            invoices={invoices}
                            loading={invoiceLoading}
                            page={invoicePage}
                            totalPages={invoiceTotalPages}
                            totalElements={invoiceTotalElements}
                            onPageChange={setInvoicePage}
                            billTypeFilter={billTypeFilter}
                            onBillTypeChange={(v) => { setBillTypeFilter(v); setInvoicePage(0); }}
                            paymentStatusFilter={paymentStatusFilter}
                            onPaymentStatusChange={(v) => { setPaymentStatusFilter(v); setInvoicePage(0); }}
                            fromDate={fromDate}
                            onFromDateChange={(v) => { setFromDate(v); setInvoicePage(0); }}
                            toDate={toDate}
                            onToDateChange={(v) => { setToDate(v); setInvoicePage(0); }}
                        />
                    )}
                    {activeTab === "statements" && detail && (
                        <StatementsTabContent statements={detail.statements} />
                    )}
                    {activeTab === "payments" && detail && (
                        <PaymentsTabContent payments={detail.payments} />
                    )}
                </div>
            </div>
        </div>
    );
}

// --- Invoices Tab ---

function InvoicesTabContent({
    invoices,
    loading,
    page,
    totalPages,
    totalElements,
    onPageChange,
    billTypeFilter,
    onBillTypeChange,
    paymentStatusFilter,
    onPaymentStatusChange,
    fromDate,
    onFromDateChange,
    toDate,
    onToDateChange,
}: {
    invoices: InvoiceBill[];
    loading: boolean;
    page: number;
    totalPages: number;
    totalElements: number;
    onPageChange: (p: number) => void;
    billTypeFilter: string;
    onBillTypeChange: (v: string) => void;
    paymentStatusFilter: string;
    onPaymentStatusChange: (v: string) => void;
    fromDate: string;
    onFromDateChange: (v: string) => void;
    toDate: string;
    onToDateChange: (v: string) => void;
}) {
    return (
        <div className="flex flex-col h-full">
            {/* Filter Bar */}
            <div className="px-4 py-2 border-b border-border bg-muted/10 flex items-center gap-3 flex-wrap">
                <Filter className="w-3 h-3 text-muted-foreground flex-shrink-0" />
                <select
                    value={billTypeFilter}
                    onChange={(e) => onBillTypeChange(e.target.value)}
                    className="text-[11px] bg-card border border-border rounded-md px-2 py-1 text-foreground focus:outline-none focus:ring-1 focus:ring-primary/50"
                >
                    <option value="">All Types</option>
                    <option value="CASH">CASH</option>
                    <option value="CREDIT">CREDIT</option>
                </select>
                <select
                    value={paymentStatusFilter}
                    onChange={(e) => onPaymentStatusChange(e.target.value)}
                    className="text-[11px] bg-card border border-border rounded-md px-2 py-1 text-foreground focus:outline-none focus:ring-1 focus:ring-primary/50"
                >
                    <option value="">All Status</option>
                    <option value="PAID">PAID</option>
                    <option value="NOT_PAID">NOT PAID</option>
                </select>
                <div className="flex items-center gap-1.5">
                    <span className="text-[10px] text-muted-foreground">From</span>
                    <input
                        type="date"
                        value={fromDate}
                        onChange={(e) => onFromDateChange(e.target.value)}
                        className="text-[11px] bg-card border border-border rounded-md px-2 py-1 text-foreground focus:outline-none focus:ring-1 focus:ring-primary/50"
                    />
                </div>
                <div className="flex items-center gap-1.5">
                    <span className="text-[10px] text-muted-foreground">To</span>
                    <input
                        type="date"
                        value={toDate}
                        onChange={(e) => onToDateChange(e.target.value)}
                        className="text-[11px] bg-card border border-border rounded-md px-2 py-1 text-foreground focus:outline-none focus:ring-1 focus:ring-primary/50"
                    />
                </div>
                {(billTypeFilter || paymentStatusFilter || fromDate || toDate) && (
                    <button
                        onClick={() => {
                            onBillTypeChange("");
                            onPaymentStatusChange("");
                            onFromDateChange("");
                            onToDateChange("");
                        }}
                        className="text-[10px] text-muted-foreground hover:text-foreground"
                    >
                        Clear
                    </button>
                )}
                <span className="text-[10px] text-muted-foreground ml-auto">{totalElements} invoices</span>
            </div>

            {/* Table */}
            {loading ? (
                <div className="flex-1 flex items-center justify-center py-12">
                    <div className="animate-spin rounded-full h-5 w-5 border-b-2 border-primary" />
                </div>
            ) : invoices.length === 0 ? (
                <div className="flex-1 flex items-center justify-center py-12 text-muted-foreground text-xs">
                    No invoices found
                </div>
            ) : (
                <div className="flex-1 overflow-y-auto">
                    <table className="w-full text-[11px]">
                        <thead>
                            <tr className="border-b border-border text-muted-foreground sticky top-0 bg-card">
                                <th className="text-left py-1.5 px-3 font-medium">Date</th>
                                <th className="text-left py-1.5 px-3 font-medium">Vehicle</th>
                                <th className="text-left py-1.5 px-3 font-medium">Products</th>
                                <th className="text-right py-1.5 px-3 font-medium">Amount</th>
                                <th className="text-right py-1.5 px-3 font-medium">Discount</th>
                                <th className="text-right py-1.5 px-3 font-medium">Net</th>
                                <th className="text-center py-1.5 px-3 font-medium">Status</th>
                            </tr>
                        </thead>
                        <tbody>
                            {invoices.map(bill => {
                                const billDate = bill.date ? new Date(bill.date) : null;
                                const isPaid = bill.paymentStatus === "PAID";
                                return (
                                    <tr
                                        key={bill.id}
                                        className={`border-b border-border/20 hover:bg-muted/30 transition-colors ${isPaid ? "opacity-50" : ""}`}
                                    >
                                        <td className="py-1.5 px-3">
                                            {billDate
                                                ? billDate.toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "2-digit" })
                                                : "-"}
                                        </td>
                                        <td className="py-1.5 px-3 font-medium">{bill.vehicle?.vehicleNumber || "-"}</td>
                                        <td className="py-1.5 px-3 text-muted-foreground max-w-[180px] truncate">
                                            {bill.products?.map(p => p.productName).filter(Boolean).join(", ") || "-"}
                                        </td>
                                        <td className="py-1.5 px-3 text-right">
                                            {bill.grossAmount != null ? fmt(bill.grossAmount) : fmt(bill.netAmount)}
                                        </td>
                                        <td className="py-1.5 px-3 text-right text-muted-foreground">
                                            {bill.totalDiscount != null && Number(bill.totalDiscount) > 0
                                                ? fmt(bill.totalDiscount)
                                                : "-"}
                                        </td>
                                        <td className="py-1.5 px-3 text-right font-semibold">
                                            {fmt(bill.netAmount)}
                                        </td>
                                        <td className="py-1.5 px-3 text-center">
                                            {isPaid ? (
                                                <Badge variant="success" className="text-[9px] px-1 py-0">PAID</Badge>
                                            ) : (
                                                <Badge variant="warning" className="text-[9px] px-1 py-0">NOT PAID</Badge>
                                            )}
                                        </td>
                                    </tr>
                                );
                            })}
                        </tbody>
                    </table>
                </div>
            )}

            {/* Pagination */}
            {totalPages > 1 && (
                <div className="px-4 py-2 border-t border-border bg-muted/10 flex items-center justify-between">
                    <button
                        onClick={() => onPageChange(page - 1)}
                        disabled={page === 0}
                        className="flex items-center gap-1 text-[11px] text-muted-foreground hover:text-foreground disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
                    >
                        <ChevronLeft className="w-3 h-3" />
                        Previous
                    </button>
                    <span className="text-[10px] text-muted-foreground">
                        Page {page + 1} of {totalPages}
                    </span>
                    <button
                        onClick={() => onPageChange(page + 1)}
                        disabled={page >= totalPages - 1}
                        className="flex items-center gap-1 text-[11px] text-muted-foreground hover:text-foreground disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
                    >
                        Next
                        <ChevronRight className="w-3 h-3" />
                    </button>
                </div>
            )}
        </div>
    );
}

// --- Statements Tab ---

function StatementsTabContent({ statements }: { statements: CreditCustomerDetail["statements"] }) {
    if (statements.length === 0) {
        return <div className="p-6 text-center text-muted-foreground text-xs">No statements</div>;
    }

    return (
        <table className="w-full text-[11px]">
            <thead>
                <tr className="border-b border-border text-muted-foreground sticky top-0 bg-card">
                    <th className="text-left py-1.5 px-3 font-medium">Stmt #</th>
                    <th className="text-left py-1.5 px-3 font-medium">Period</th>
                    <th className="text-left py-1.5 px-3 font-medium">Date</th>
                    <th className="text-right py-1.5 px-3 font-medium">Bills</th>
                    <th className="text-right py-1.5 px-3 font-medium">Net Amount</th>
                    <th className="text-right py-1.5 px-3 font-medium">Received</th>
                    <th className="text-right py-1.5 px-3 font-medium">Balance</th>
                    <th className="text-center py-1.5 px-3 font-medium">Status</th>
                </tr>
            </thead>
            <tbody>
                {statements.map(stmt => (
                    <tr
                        key={stmt.id}
                        className={`border-b border-border/20 hover:bg-muted/30 transition-colors ${stmt.status === "PAID" ? "opacity-50" : ""}`}
                    >
                        <td className="py-1.5 px-3 font-mono font-semibold">{stmt.statementNo}</td>
                        <td className="py-1.5 px-3 text-muted-foreground">{stmt.fromDate} — {stmt.toDate}</td>
                        <td className="py-1.5 px-3">{stmt.statementDate}</td>
                        <td className="py-1.5 px-3 text-right">{stmt.numberOfBills}</td>
                        <td className="py-1.5 px-3 text-right font-medium">
                            {fmt(stmt.netAmount)}
                        </td>
                        <td className="py-1.5 px-3 text-right text-emerald-400">
                            {fmt(stmt.receivedAmount)}
                        </td>
                        <td className="py-1.5 px-3 text-right font-semibold text-amber-400">
                            {fmt(stmt.balanceAmount)}
                        </td>
                        <td className="py-1.5 px-3 text-center">
                            <Badge variant={stmt.status === "PAID" ? "success" : "warning"} className="text-[9px] px-1 py-0">
                                {stmt.status === "PAID" ? "PAID" : "NOT PAID"}
                            </Badge>
                        </td>
                    </tr>
                ))}
            </tbody>
            <tfoot>
                <tr className="border-t border-border bg-muted/20">
                    <td colSpan={4} className="py-1.5 px-3 text-right font-semibold text-muted-foreground text-[10px]">Totals</td>
                    <td className="py-1.5 px-3 text-right font-bold text-foreground">
                        {fmt(statements.reduce((s, st) => s + Number(st.netAmount || 0), 0))}
                    </td>
                    <td className="py-1.5 px-3 text-right font-bold text-emerald-400">
                        {fmt(statements.reduce((s, st) => s + Number(st.receivedAmount || 0), 0))}
                    </td>
                    <td className="py-1.5 px-3 text-right font-bold text-amber-400">
                        {fmt(statements.reduce((s, st) => s + Number(st.balanceAmount || 0), 0))}
                    </td>
                    <td></td>
                </tr>
            </tfoot>
        </table>
    );
}

// --- Payments Tab ---

function PaymentsTabContent({ payments }: { payments: CreditCustomerDetail["payments"] }) {
    if (payments.length === 0) {
        return <div className="p-6 text-center text-muted-foreground text-xs">No payments recorded</div>;
    }

    return (
        <table className="w-full text-[11px]">
            <thead>
                <tr className="border-b border-border text-muted-foreground sticky top-0 bg-card">
                    <th className="text-left py-1.5 px-3 font-medium">Date</th>
                    <th className="text-left py-1.5 px-3 font-medium">Paid Against</th>
                    <th className="text-right py-1.5 px-3 font-medium">Amount</th>
                    <th className="text-left py-1.5 px-3 font-medium">Mode</th>
                    <th className="text-left py-1.5 px-3 font-medium">Reference</th>
                    <th className="text-left py-1.5 px-3 font-medium">Remarks</th>
                </tr>
            </thead>
            <tbody>
                {payments.map(pmt => (
                    <tr key={pmt.id} className="border-b border-border/20 hover:bg-muted/30 transition-colors">
                        <td className="py-1.5 px-3">
                            {pmt.paymentDate
                                ? new Date(pmt.paymentDate).toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "2-digit" })
                                : "-"}
                        </td>
                        <td className="py-1.5 px-3">
                            {pmt.statement ? (
                                <span className="flex items-center gap-1">
                                    <Receipt className="w-2.5 h-2.5 text-primary" />
                                    Stmt #{pmt.statement.statementNo}
                                </span>
                            ) : pmt.invoiceBill ? (
                                <span className="flex items-center gap-1">
                                    <FileText className="w-2.5 h-2.5 text-muted-foreground" />
                                    Bill #{pmt.invoiceBill.id}
                                </span>
                            ) : "-"}
                        </td>
                        <td className="py-1.5 px-3 text-right font-semibold text-emerald-400">
                            {fmt(pmt.amount)}
                        </td>
                        <td className="py-1.5 px-3">
                            <Badge variant="default" className="text-[9px] px-1 py-0">
                                {pmt.paymentMode?.name || "-"}
                            </Badge>
                        </td>
                        <td className="py-1.5 px-3 text-muted-foreground">{pmt.referenceNo || "-"}</td>
                        <td className="py-1.5 px-3 text-muted-foreground max-w-[120px] truncate">{pmt.remarks || "-"}</td>
                    </tr>
                ))}
            </tbody>
            <tfoot>
                <tr className="border-t border-border bg-muted/20">
                    <td colSpan={2} className="py-1.5 px-3 text-right font-semibold text-muted-foreground text-[10px]">Total</td>
                    <td className="py-1.5 px-3 text-right font-bold text-emerald-400">
                        {fmt(payments.reduce((s, p) => s + Number(p.amount || 0), 0))}
                    </td>
                    <td colSpan={3}></td>
                </tr>
            </tfoot>
        </table>
    );
}
