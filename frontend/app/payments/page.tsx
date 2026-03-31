"use client";

import { useState, useEffect } from "react";
import { TablePagination } from "@/components/ui/table-pagination";
import { GlassCard } from "@/components/ui/glass-card";
import { Badge } from "@/components/ui/badge";
import { Modal } from "@/components/ui/modal";
import {
    CreditCard, Receipt, FileText, Search, Check, IndianRupee, Clock, ImageIcon, Paperclip,
    Download, FileSpreadsheet, Calendar, Eye, Pencil
} from "lucide-react";
import {
    getPayments, getPaymentsByShift, getPaymentModes, getOutstandingStatements,
    getCustomers, recordStatementPayment, recordBillPayment,
    getOutstandingBills, uploadPaymentProof, getPaymentProofUrl,
    exportPaymentsPdf, exportPaymentsExcel, downloadPaymentReceipt,
    API_BASE_URL,
    type Payment, type PaymentMode, type Statement, type InvoiceBill, type Customer, type PageResponse
} from "@/lib/api/station";
import { fetchWithAuth } from "@/lib/api/fetch-with-auth";
import { PermissionGate } from "@/components/permission-gate";

type PayTarget = "statement" | "bill";

const fmt = (n: number) =>
    Number(n).toLocaleString("en-IN", { minimumFractionDigits: 2 });

const fmtCurrency = (n: number) =>
    Number(n).toLocaleString("en-IN", { style: "currency", currency: "INR" });

const statusBadge = (status?: string) => {
    if (!status) return <span className="text-muted-foreground/50">—</span>;
    switch (status) {
        case "PAID":
            return <Badge className="bg-emerald-500/20 text-emerald-400 border-emerald-500/30 text-[10px]">Paid</Badge>;
        case "PARTIAL":
            return <Badge className="bg-amber-500/20 text-amber-400 border-amber-500/30 text-[10px]">Partial</Badge>;
        case "NOT_PAID":
            return <Badge className="bg-rose-500/20 text-rose-400 border-rose-500/30 text-[10px]">Unpaid</Badge>;
        default:
            return <span className="text-muted-foreground text-xs">{status}</span>;
    }
};

export default function PaymentsPage() {
    const [payments, setPayments] = useState<Payment[]>([]);
    const [paymentModes, setPaymentModes] = useState<PaymentMode[]>([]);
    const [loading, setLoading] = useState(true);

    // Shift-scoping
    const [activeShiftId, setActiveShiftId] = useState<number | null>(null);
    const [viewMode, setViewMode] = useState<"shift" | "dates">("shift");

    // Pagination (used in dates mode)
    const [page, setPage] = useState(0);
    const [pageSize] = useState(5);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);

    // Record payment modal
    const [showPayModal, setShowPayModal] = useState(false);
    const [payTarget, setPayTarget] = useState<PayTarget>("statement");

    // Step 1: Customer search
    const [customerSearch, setCustomerSearch] = useState("");
    const [customerSuggestions, setCustomerSuggestions] = useState<Customer[]>([]);
    const [selectedCustomer, setSelectedCustomer] = useState<Customer | null>(null);

    // Step 2: Outstanding items
    const [outstandingStatements, setOutstandingStatements] = useState<Statement[]>([]);
    const [outstandingBills, setOutstandingBills] = useState<InvoiceBill[]>([]);
    const [loadingOutstanding, setLoadingOutstanding] = useState(false);
    const [selectedTarget, setSelectedTarget] = useState<Statement | InvoiceBill | null>(null);
    const [billSearch, setBillSearch] = useState("");

    // Step 3: Payment details
    const [payAmount, setPayAmount] = useState("");
    const [payModeId, setPayModeId] = useState<number | "">("");
    const [payReference, setPayReference] = useState("");
    const [payRemarks, setPayRemarks] = useState("");
    const [proofFile, setProofFile] = useState<File | null>(null);
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState("");

    // Table filters
    const [tableSearch, setTableSearch] = useState("");
    const [modeFilter, setModeFilter] = useState<string>("ALL");
    const [categoryFilter, setCategoryFilter] = useState<string>("");
    const [paidAgainstFilter, setPaidAgainstFilter] = useState<string>("");
    const [fromDate, setFromDate] = useState<string>("");
    const [toDate, setToDate] = useState<string>("");

    // Detail view modal
    const [viewPayment, setViewPayment] = useState<Payment | null>(null);

    // Export loading
    const [exportingPdf, setExportingPdf] = useState(false);
    const [exportingExcel, setExportingExcel] = useState(false);

    useEffect(() => {
        loadInitialData();
    }, []);

    useEffect(() => {
        if (viewMode === "dates") {
            loadPaymentsByDate();
        }
    }, [page, categoryFilter, paidAgainstFilter, fromDate, toDate, viewMode]);

    const loadInitialData = async () => {
        try {
            const modes = await getPaymentModes();
            setPaymentModes(modes);
            // Fetch active shift
            const shiftRes = await fetchWithAuth(`${API_BASE_URL}/shifts/active`);
            if (shiftRes.ok) {
                const text = await shiftRes.text();
                if (text) {
                    const shift = JSON.parse(text);
                    setActiveShiftId(shift.id);
                    await loadPaymentsByShift(shift.id);
                    return;
                }
            }
            // No active shift — fall back to dates mode
            setViewMode("dates");
            await loadPaymentsByDate();
        } catch (e) {
            console.error("Failed to load data", e);
            setLoading(false);
        }
    };

    const loadPaymentsByShift = async (shiftId: number) => {
        setLoading(true);
        try {
            const data = await getPaymentsByShift(shiftId);
            setPayments(data);
            setTotalElements(data.length);
            setTotalPages(1);
        } catch (e) {
            console.error("Failed to load shift payments", e);
        } finally {
            setLoading(false);
        }
    };

    const loadPaymentsByDate = async () => {
        setLoading(true);
        try {
            const result: PageResponse<Payment> = await getPayments(
                page, pageSize,
                categoryFilter || undefined,
                paidAgainstFilter || undefined,
                fromDate || undefined,
                toDate || undefined
            );
            setPayments(result.content);
            setTotalPages(result.totalPages);
            setTotalElements(result.totalElements);
        } catch (e) {
            console.error("Failed to load payments", e);
        } finally {
            setLoading(false);
        }
    };

    // Customer search
    const searchCustomers = async (val: string) => {
        setCustomerSearch(val);
        if (val.length < 2) { setCustomerSuggestions([]); return; }
        try {
            const data = await getCustomers(val);
            setCustomerSuggestions(Array.isArray(data) ? data : data.content || []);
        } catch (err) { console.error(err); }
    };

    const handleSelectCustomer = async (c: any) => {
        setSelectedCustomer(c);
        setCustomerSearch(c.name);
        setCustomerSuggestions([]);
        setSelectedTarget(null);
        setBillSearch("");
        await loadOutstandingItems(c.id, payTarget);
    };

    const loadOutstandingItems = async (customerId: number, target: PayTarget) => {
        setLoadingOutstanding(true);
        try {
            if (target === "statement") {
                const stmts = await getOutstandingStatements();
                setOutstandingStatements(stmts.filter(s => s.customer?.id === customerId));
            } else {
                const bills = await getOutstandingBills(customerId);
                setOutstandingBills(bills);
            }
        } catch (e) {
            console.error("Failed to load outstanding items", e);
        } finally {
            setLoadingOutstanding(false);
        }
    };

    const handlePayTargetChange = async (target: PayTarget) => {
        setPayTarget(target);
        setSelectedTarget(null);
        setBillSearch("");
        if (selectedCustomer) {
            await loadOutstandingItems(selectedCustomer.id, target);
        }
    };

    const getTargetBalance = (): number => {
        if (!selectedTarget) return 0;
        if (payTarget === "statement") {
            return (selectedTarget as Statement).balanceAmount || 0;
        } else {
            return (selectedTarget as InvoiceBill).netAmount || 0;
        }
    };

    const handleRecordPayment = async () => {
        if (!selectedTarget || !payAmount || !payModeId) {
            setError("Please select a bill/statement, enter amount, and choose payment mode");
            return;
        }
        const balance = getTargetBalance();
        if (Number(payAmount) > balance) {
            setError(`Payment amount exceeds balance of ${fmtCurrency(balance)}`);
            return;
        }
        setSubmitting(true);
        setError("");
        try {
            const paymentData: Partial<Payment> = {
                amount: Number(payAmount),
                paymentMode: { id: Number(payModeId) } as PaymentMode,
                referenceNo: payReference || undefined,
                remarks: payRemarks || undefined,
                paymentDate: new Date().toISOString(),
            };

            let saved: Payment;
            if (payTarget === "statement") {
                saved = await recordStatementPayment(Number((selectedTarget as Statement).id), paymentData);
            } else {
                saved = await recordBillPayment(Number((selectedTarget as InvoiceBill).id), paymentData);
            }

            // Upload proof image if selected
            if (proofFile && saved.id) {
                try {
                    await uploadPaymentProof(saved.id, proofFile);
                } catch {
                    console.error("Proof upload failed");
                }
            }

            setShowPayModal(false);
            resetPayForm();
            if (viewMode === "shift" && activeShiftId) {
                loadPaymentsByShift(activeShiftId);
            } else {
                loadPaymentsByDate();
            }
        } catch (e: any) {
            setError(e.message || "Failed to record payment");
        } finally {
            setSubmitting(false);
        }
    };

    const resetPayForm = () => {
        setSelectedCustomer(null);
        setCustomerSearch("");
        setCustomerSuggestions([]);
        setSelectedTarget(null);
        setBillSearch("");
        setPayAmount("");
        setPayModeId("");
        setPayReference("");
        setPayRemarks("");
        setProofFile(null);
        setError("");
        setOutstandingStatements([]);
        setOutstandingBills([]);
        setPayTarget("statement");
    };

    const handleExportPdf = async () => {
        setExportingPdf(true);
        try {
            const blob = await exportPaymentsPdf(
                categoryFilter || undefined, paidAgainstFilter || undefined,
                fromDate || undefined, toDate || undefined
            );
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement("a");
            a.href = url;
            a.download = "payment_report.pdf";
            a.click();
            window.URL.revokeObjectURL(url);
        } catch (e) {
            console.error("PDF export failed", e);
        } finally {
            setExportingPdf(false);
        }
    };

    const handleExportExcel = async () => {
        setExportingExcel(true);
        try {
            const blob = await exportPaymentsExcel(
                categoryFilter || undefined, paidAgainstFilter || undefined,
                fromDate || undefined, toDate || undefined
            );
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement("a");
            a.href = url;
            a.download = "payment_report.xlsx";
            a.click();
            window.URL.revokeObjectURL(url);
        } catch (e) {
            console.error("Excel export failed", e);
        } finally {
            setExportingExcel(false);
        }
    };

    const handleDownloadReceipt = async (paymentId: number) => {
        try {
            const blob = await downloadPaymentReceipt(paymentId);
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement("a");
            a.href = url;
            a.download = `payment_receipt_${paymentId}.pdf`;
            a.click();
            window.URL.revokeObjectURL(url);
        } catch (e) {
            console.error("Receipt download failed", e);
        }
    };

    // Filter outstanding items by search
    const filteredStatements = outstandingStatements.filter(s => {
        if (!billSearch) return true;
        const q = billSearch.toLowerCase();
        return String(s.statementNo).includes(q) ||
            s.fromDate?.includes(q) || s.toDate?.includes(q);
    });

    const filteredBills = outstandingBills.filter(b => {
        if (!billSearch) return true;
        const q = billSearch.toLowerCase();
        return String(b.id).includes(q) ||
            (b.vehicle?.vehicleNumber || "").toLowerCase().includes(q) ||
            (b.indentNo || "").toLowerCase().includes(q) ||
            (b.driverName || "").toLowerCase().includes(q);
    });

    // Client-side filters (search + mode)
    const filteredPayments = payments.filter((p) => {
        const q = tableSearch.toLowerCase();
        const matchesSearch = !tableSearch ||
            p.customer?.name?.toLowerCase().includes(q) ||
            p.referenceNo?.toLowerCase().includes(q) ||
            p.remarks?.toLowerCase().includes(q) ||
            p.receivedBy?.name?.toLowerCase().includes(q);
        const matchesMode = modeFilter === "ALL" || p.paymentMode?.name === modeFilter;
        return matchesSearch && matchesMode;
    });

    if (loading && payments.length === 0) {
        return (
            <div className="p-8 flex items-center justify-center h-screen">
                <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary" />
            </div>
        );
    }

    return (
        <div className="p-6 h-screen overflow-hidden bg-background transition-colors duration-300">
            <div className="max-w-[1400px] mx-auto">
                {/* Header */}
                <div className="flex justify-between items-center mb-8">
                    <div>
                        <h1 className="text-4xl font-bold text-foreground tracking-tight">
                            Payment <span className="text-gradient">Tracking</span>
                        </h1>
                        <p className="text-muted-foreground mt-2">
                            Record and track credit payments from customers.
                        </p>
                    </div>
                    <div className="flex items-center gap-3">
                        {/* Export buttons */}
                        <button
                            onClick={handleExportPdf}
                            disabled={exportingPdf}
                            className="px-4 py-2.5 rounded-xl border border-border text-foreground hover:bg-muted transition-colors flex items-center gap-2 text-sm font-medium disabled:opacity-50"
                            title="Export as PDF"
                        >
                            <FileText className="w-4 h-4 text-rose-400" />
                            {exportingPdf ? "Exporting..." : "PDF"}
                        </button>
                        <button
                            onClick={handleExportExcel}
                            disabled={exportingExcel}
                            className="px-4 py-2.5 rounded-xl border border-border text-foreground hover:bg-muted transition-colors flex items-center gap-2 text-sm font-medium disabled:opacity-50"
                            title="Export as Excel"
                        >
                            <FileSpreadsheet className="w-4 h-4 text-emerald-400" />
                            {exportingExcel ? "Exporting..." : "Excel"}
                        </button>
                        <PermissionGate permission="PAYMENT_MANAGE">
                            <button
                                onClick={() => setShowPayModal(true)}
                                className="btn-gradient px-6 py-3 rounded-xl font-medium flex items-center gap-2"
                            >
                                <CreditCard className="w-5 h-5" />
                                Record Payment
                            </button>
                        </PermissionGate>
                    </div>
                </div>

                {/* View Toggle + Stats */}
                <div className="flex flex-wrap items-center gap-3 mb-6">
                    {/* View indicator */}
                    {viewMode === "shift" ? (
                        <span className="px-3 py-1.5 bg-primary/10 text-primary rounded-lg text-xs font-bold">
                            Shift #{activeShiftId || "—"}
                        </span>
                    ) : (
                        <span className="px-3 py-1.5 bg-amber-500/10 text-amber-500 rounded-lg text-xs font-bold">
                            {fromDate && toDate ? `${fromDate} → ${toDate}` : "All Payments"}
                        </span>
                    )}
                    {viewMode === "dates" && activeShiftId && (
                        <button
                            onClick={() => {
                                setViewMode("shift");
                                setFromDate("");
                                setToDate("");
                                setPage(0);
                                loadPaymentsByShift(activeShiftId);
                            }}
                            className="px-3 py-1.5 text-xs font-bold bg-primary/10 text-primary border border-primary/20 rounded-lg hover:bg-primary/20 transition-colors"
                        >
                            Current Shift
                        </button>
                    )}
                    {viewMode === "shift" && (
                        <button
                            onClick={() => {
                                setViewMode("dates");
                                setPage(0);
                            }}
                            className="px-3 py-1.5 text-xs font-bold bg-muted text-muted-foreground border border-border rounded-lg hover:bg-muted/80 transition-colors flex items-center gap-1.5"
                        >
                            <Calendar className="w-3.5 h-3.5" />
                            Search by Date
                        </button>
                    )}
                    <span className="text-sm text-muted-foreground ml-auto">{totalElements} payment{totalElements !== 1 ? "s" : ""}</span>
                    {payments.length > 0 && (
                        <span className="text-sm font-bold text-emerald-400">
                            Total: {fmtCurrency(payments.reduce((s, p) => s + (p.amount || 0), 0))}
                        </span>
                    )}
                </div>

                {/* Payment History Table */}
                <GlassCard>
                    {/* Filter Bar */}
                    <div className="flex flex-wrap gap-3 items-center mb-4">
                        <div className="relative flex-1 min-w-[200px] max-w-md">
                            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                            <input
                                type="text"
                                placeholder="Search by customer, reference, employee..."
                                value={tableSearch}
                                onChange={(e) => setTableSearch(e.target.value)}
                                className="w-full pl-10 pr-4 py-2 bg-background border border-border rounded-xl text-foreground text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                            />
                        </div>
                        <select
                            value={categoryFilter}
                            onChange={(e) => { setCategoryFilter(e.target.value); setPage(0); }}
                            className="px-4 py-2 bg-background border border-border rounded-xl text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
                        >
                            <option value="">All Categories</option>
                            <option value="GOVERNMENT">Government</option>
                            <option value="NON_GOVERNMENT">Non-Government</option>
                        </select>
                        <select
                            value={paidAgainstFilter}
                            onChange={(e) => { setPaidAgainstFilter(e.target.value); setPage(0); }}
                            className="px-4 py-2 bg-background border border-border rounded-xl text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
                        >
                            <option value="">Bills & Statements</option>
                            <option value="BILL">Bills Only</option>
                            <option value="STATEMENT">Statements Only</option>
                        </select>
                        <select
                            value={modeFilter}
                            onChange={(e) => setModeFilter(e.target.value)}
                            className="px-4 py-2 bg-background border border-border rounded-xl text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
                        >
                            <option value="ALL">All Modes</option>
                            {paymentModes.map((m) => (
                                <option key={m.id} value={m.name}>{m.name}</option>
                            ))}
                        </select>
                    </div>

                    {/* Date Range Filter — only in dates mode */}
                    {viewMode === "dates" && (
                        <div className="flex flex-wrap gap-3 items-center mb-4">
                            <div className="flex items-center gap-2">
                                <Calendar className="w-4 h-4 text-muted-foreground" />
                                <span className="text-sm text-muted-foreground">From</span>
                                <input
                                    type="date"
                                    value={fromDate}
                                    onChange={(e) => { setFromDate(e.target.value); setPage(0); }}
                                    className="px-3 py-1.5 bg-background border border-border rounded-lg text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
                                />
                            </div>
                            <div className="flex items-center gap-2">
                                <span className="text-sm text-muted-foreground">To</span>
                                <input
                                    type="date"
                                    value={toDate}
                                    onChange={(e) => { setToDate(e.target.value); setPage(0); }}
                                    className="px-3 py-1.5 bg-background border border-border rounded-lg text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
                                />
                            </div>
                            {(fromDate || toDate) && (
                                <button
                                    onClick={() => { setFromDate(""); setToDate(""); setPage(0); }}
                                    className="text-xs text-muted-foreground hover:text-foreground transition-colors px-2 py-1 border border-border rounded-lg"
                                >
                                    Clear dates
                                </button>
                            )}
                        </div>
                    )}

                    <div className="overflow-x-auto">
                        <table className="w-full text-sm">
                            <thead>
                                <tr className="border-b border-border text-muted-foreground">
                                    <th className="text-left py-3 px-3">Date</th>
                                    <th className="text-left py-3 px-3">Customer</th>
                                    <th className="text-left py-3 px-3">Paid Against</th>
                                    <th className="text-right py-3 px-3">Net Amount</th>
                                    <th className="text-right py-3 px-3">Received</th>
                                    <th className="text-right py-3 px-3">Balance</th>
                                    <th className="text-left py-3 px-3">Mode</th>
                                    <th className="text-center py-3 px-3">Actions</th>
                                </tr>
                            </thead>
                            <tbody>
                                {filteredPayments.length === 0 ? (
                                    <tr>
                                        <td colSpan={8} className="text-center py-8 text-muted-foreground">
                                            {viewMode === "shift"
                                                ? (activeShiftId ? "No payments in the current shift." : "No active shift found.")
                                                : "No payments found for the selected filters."}
                                        </td>
                                    </tr>
                                ) : (
                                    filteredPayments.map((p) => (
                                        <tr key={p.id} className="border-b border-border/50 hover:bg-muted/50 transition-colors">
                                            <td className="py-3 px-3 text-muted-foreground whitespace-nowrap">
                                                {p.paymentDate ? new Date(p.paymentDate).toLocaleString("en-IN", {
                                                    day: "2-digit", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit"
                                                }) : "-"}
                                            </td>
                                            <td className="py-3 px-3 font-medium">{p.customer?.name || "-"}</td>
                                            <td className="py-3 px-3">
                                                {p.statement ? (
                                                    <Badge variant="default">
                                                        <Receipt className="w-3 h-3 inline mr-1" />
                                                        Stmt #{p.statement.statementNo}
                                                    </Badge>
                                                ) : p.invoiceBill ? (
                                                    <Badge variant="outline">
                                                        <FileText className="w-3 h-3 inline mr-1" />
                                                        Bill #{p.invoiceBill.id}
                                                    </Badge>
                                                ) : "-"}
                                            </td>
                                            <td className="py-3 px-3 text-right whitespace-nowrap">
                                                {(p.statement?.netAmount || p.invoiceBill?.netAmount)
                                                    ? fmtCurrency(p.statement?.netAmount ?? p.invoiceBill?.netAmount ?? 0)
                                                    : "-"}
                                            </td>
                                            <td className="py-3 px-3 text-right font-semibold text-emerald-400 whitespace-nowrap">
                                                {fmtCurrency(p.amount)}
                                            </td>
                                            <td className="py-3 px-3 text-right font-semibold whitespace-nowrap">
                                                {p.statement
                                                    ? <span className={p.statement.balanceAmount > 0 ? "text-amber-400" : "text-emerald-400"}>
                                                        {fmtCurrency(p.statement.balanceAmount)}
                                                      </span>
                                                    : "-"}
                                            </td>
                                            <td className="py-3 px-3">{p.paymentMode?.name || "-"}</td>
                                            <td className="py-3 px-3 text-center">
                                                <div className="flex items-center justify-center gap-2">
                                                    <button
                                                        onClick={() => setViewPayment(p)}
                                                        className="text-primary hover:text-primary/80 transition-colors"
                                                        title="View details"
                                                    >
                                                        <Eye className="w-4 h-4" />
                                                    </button>
                                                </div>
                                            </td>
                                        </tr>
                                    ))
                                )}
                            </tbody>
                        </table>
                    </div>

                    {viewMode === "dates" && (
                        <TablePagination page={page} totalPages={totalPages} totalElements={totalElements} pageSize={pageSize} onPageChange={setPage} />
                    )}
                </GlassCard>
            </div>

            {/* Record Payment Modal */}
            <Modal
                isOpen={showPayModal}
                onClose={() => { setShowPayModal(false); resetPayForm(); }}
                title="Record Payment"
            >
                <div className="space-y-5">
                    {error && (
                        <div className="bg-rose-500/20 border border-rose-500/30 text-rose-400 px-4 py-2 rounded-lg text-sm">
                            {error}
                        </div>
                    )}

                    {/* Pay against type */}
                    <div>
                        <label className="block text-sm font-medium text-muted-foreground mb-2">Pay Against</label>
                        <div className="flex gap-3">
                            <button
                                onClick={() => handlePayTargetChange("statement")}
                                className={`flex-1 py-2.5 px-4 rounded-lg border text-sm font-medium transition-colors flex items-center justify-center gap-2 ${
                                    payTarget === "statement"
                                        ? "border-primary bg-primary/20 text-primary"
                                        : "border-border text-muted-foreground hover:bg-muted"
                                }`}
                            >
                                <Receipt className="w-4 h-4" />Statement
                            </button>
                            <button
                                onClick={() => handlePayTargetChange("bill")}
                                className={`flex-1 py-2.5 px-4 rounded-lg border text-sm font-medium transition-colors flex items-center justify-center gap-2 ${
                                    payTarget === "bill"
                                        ? "border-primary bg-primary/20 text-primary"
                                        : "border-border text-muted-foreground hover:bg-muted"
                                }`}
                            >
                                <FileText className="w-4 h-4" />Individual Bill
                            </button>
                        </div>
                    </div>

                    {/* Customer Search */}
                    <div>
                        <label className="block text-sm font-medium text-muted-foreground mb-1">Customer</label>
                        <div className="relative">
                            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                            <input
                                type="text"
                                placeholder="Search customer name or phone..."
                                value={customerSearch}
                                onChange={(e) => searchCustomers(e.target.value)}
                                className="w-full pl-10 pr-4 py-2.5 bg-card border border-border rounded-lg text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                            />
                            {customerSuggestions.length > 0 && (
                                <div className="absolute top-full left-0 right-0 mt-1 bg-card border border-border rounded-lg shadow-xl z-50 max-h-48 overflow-y-auto">
                                    {customerSuggestions.map((c: any) => (
                                        <button
                                            key={c.id}
                                            onClick={() => handleSelectCustomer(c)}
                                            className="w-full px-4 py-2.5 text-left hover:bg-muted transition-colors flex items-center justify-between"
                                        >
                                            <div>
                                                <span className="font-medium text-foreground text-sm">{c.name}</span>
                                                <span className="text-xs text-muted-foreground ml-2">{c.phoneNumbers || ""}</span>
                                            </div>
                                            {c.status && c.status !== "ACTIVE" && (
                                                <span className={`text-[9px] font-bold px-1.5 py-0.5 rounded ${
                                                    c.status === "BLOCKED" ? "bg-rose-500/10 text-rose-500" : "bg-amber-500/10 text-amber-500"
                                                }`}>{c.status}</span>
                                            )}
                                        </button>
                                    ))}
                                </div>
                            )}
                        </div>
                        {selectedCustomer && (
                            <div className="mt-2 flex items-center gap-2 text-xs">
                                <Check className="w-3 h-3 text-emerald-400" />
                                <span className="text-foreground font-medium">{selectedCustomer.name}</span>
                                <button
                                    onClick={() => { setSelectedCustomer(null); setCustomerSearch(""); setSelectedTarget(null); setOutstandingStatements([]); setOutstandingBills([]); }}
                                    className="text-muted-foreground hover:text-foreground ml-auto text-xs"
                                >
                                    Change
                                </button>
                            </div>
                        )}
                    </div>

                    {/* Outstanding items selection */}
                    {selectedCustomer && (
                        <div>
                            <label className="block text-sm font-medium text-muted-foreground mb-1">
                                Select {payTarget === "statement" ? "Statement" : "Credit Bill"} to pay against
                            </label>

                            {/* Search within items */}
                            <div className="relative mb-2">
                                <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-muted-foreground" />
                                <input
                                    type="text"
                                    placeholder={payTarget === "statement" ? "Search by stmt #, date..." : "Search by bill #, vehicle, indent..."}
                                    value={billSearch}
                                    onChange={(e) => setBillSearch(e.target.value)}
                                    className="w-full pl-9 pr-4 py-2 bg-card border border-border rounded-lg text-foreground text-xs focus:outline-none focus:ring-1 focus:ring-primary/50"
                                />
                            </div>

                            {loadingOutstanding ? (
                                <div className="flex items-center justify-center py-6">
                                    <div className="animate-spin rounded-full h-5 w-5 border-b-2 border-primary" />
                                </div>
                            ) : payTarget === "statement" ? (
                                <div className="max-h-48 overflow-y-auto border border-border rounded-lg divide-y divide-border/50">
                                    {filteredStatements.length === 0 ? (
                                        <div className="px-4 py-6 text-center text-muted-foreground text-xs">
                                            No outstanding statements for this customer
                                        </div>
                                    ) : filteredStatements.map(stmt => {
                                        const isSelected = selectedTarget && (selectedTarget as Statement).id === stmt.id;
                                        return (
                                            <button
                                                key={stmt.id}
                                                onClick={() => setSelectedTarget(stmt)}
                                                className={`w-full px-3 py-2.5 text-left transition-colors ${
                                                    isSelected ? "bg-primary/10 border-l-2 border-l-primary" : "hover:bg-muted/50 border-l-2 border-l-transparent"
                                                }`}
                                            >
                                                <div className="flex items-center justify-between">
                                                    <div>
                                                        <span className="text-xs font-bold text-foreground">Stmt #{stmt.statementNo}</span>
                                                        <span className="text-[10px] text-muted-foreground ml-2">
                                                            {stmt.fromDate} — {stmt.toDate}
                                                        </span>
                                                    </div>
                                                    <div className="text-right">
                                                        <div className="text-xs font-bold text-amber-400">{fmtCurrency(stmt.balanceAmount)}</div>
                                                        <div className="text-[10px] text-muted-foreground">
                                                            of {fmtCurrency(stmt.netAmount)}
                                                        </div>
                                                    </div>
                                                </div>
                                                <div className="flex items-center gap-2 mt-1">
                                                    <span className="text-[10px] text-muted-foreground">{stmt.numberOfBills} bills</span>
                                                    <span className="text-[10px] text-emerald-400">Received: {fmt(stmt.receivedAmount)}</span>
                                                    {isSelected && <Check className="w-3 h-3 text-primary ml-auto" />}
                                                </div>
                                            </button>
                                        );
                                    })}
                                </div>
                            ) : (
                                <div className="max-h-48 overflow-y-auto border border-border rounded-lg divide-y divide-border/50">
                                    {filteredBills.length === 0 ? (
                                        <div className="px-4 py-6 text-center text-muted-foreground text-xs">
                                            No unpaid credit bills for this customer
                                        </div>
                                    ) : filteredBills.map(bill => {
                                        const isSelected = selectedTarget && (selectedTarget as InvoiceBill).id === bill.id;
                                        const billDate = bill.date ? new Date(bill.date) : null;
                                        const daysOld = billDate ? Math.floor((Date.now() - billDate.getTime()) / (1000 * 60 * 60 * 24)) : 0;
                                        return (
                                            <button
                                                key={bill.id}
                                                onClick={() => setSelectedTarget(bill)}
                                                className={`w-full px-3 py-2.5 text-left transition-colors ${
                                                    isSelected ? "bg-primary/10 border-l-2 border-l-primary" : "hover:bg-muted/50 border-l-2 border-l-transparent"
                                                }`}
                                            >
                                                <div className="flex items-center justify-between">
                                                    <div className="flex items-center gap-2">
                                                        <span className="text-xs font-bold text-foreground">Bill #{bill.id}</span>
                                                        <span className="text-[10px] text-muted-foreground">
                                                            {billDate?.toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "2-digit" })}
                                                        </span>
                                                        {bill.vehicle?.vehicleNumber && (
                                                            <span className="text-[10px] text-muted-foreground font-medium">{bill.vehicle.vehicleNumber}</span>
                                                        )}
                                                    </div>
                                                    <div className="text-right flex items-center gap-2">
                                                        <span className={`text-[10px] flex items-center gap-0.5 ${
                                                            daysOld > 90 ? "text-rose-400" : daysOld > 60 ? "text-orange-400" : daysOld > 30 ? "text-amber-400" : "text-muted-foreground"
                                                        }`}>
                                                            <Clock className="w-2.5 h-2.5" />{daysOld}d
                                                        </span>
                                                        <span className="text-xs font-bold text-foreground">{fmtCurrency(bill.netAmount)}</span>
                                                    </div>
                                                </div>
                                                <div className="flex items-center gap-2 mt-1">
                                                    {bill.indentNo && <span className="text-[10px] text-muted-foreground">Indent: {bill.indentNo}</span>}
                                                    {bill.driverName && <span className="text-[10px] text-muted-foreground">Driver: {bill.driverName}</span>}
                                                    {bill.products?.length > 0 && (
                                                        <span className="text-[10px] text-muted-foreground truncate max-w-[200px]">
                                                            {bill.products.map(p => p.productName).filter(Boolean).join(", ")}
                                                        </span>
                                                    )}
                                                    {isSelected && <Check className="w-3 h-3 text-primary ml-auto" />}
                                                </div>
                                            </button>
                                        );
                                    })}
                                </div>
                            )}
                        </div>
                    )}

                    {/* Selected target summary + payment fields */}
                    {selectedTarget && (
                        <>
                            <div className="bg-primary/5 border border-primary/20 rounded-lg px-4 py-3">
                                <div className="flex items-center justify-between">
                                    <div className="text-xs text-muted-foreground">
                                        Paying against: <span className="text-foreground font-medium">
                                            {payTarget === "statement"
                                                ? `Stmt #${(selectedTarget as Statement).statementNo}`
                                                : `Bill #${(selectedTarget as InvoiceBill).id}`
                                            }
                                        </span>
                                    </div>
                                    <div className="text-sm font-bold text-amber-400">
                                        Balance: {fmtCurrency(getTargetBalance())}
                                    </div>
                                </div>
                            </div>

                            <div className="grid grid-cols-2 gap-4">
                                {/* Amount */}
                                <div>
                                    <label className="block text-sm font-medium text-muted-foreground mb-1">Amount</label>
                                    <div className="relative">
                                        <IndianRupee className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                                        <input
                                            type="number"
                                            value={payAmount}
                                            onChange={(e) => setPayAmount(e.target.value)}
                                            placeholder="0.00"
                                            max={getTargetBalance()}
                                            className="w-full pl-10 pr-4 py-2.5 bg-card border border-border rounded-lg text-foreground font-bold focus:outline-none focus:ring-2 focus:ring-primary/50"
                                        />
                                    </div>
                                    <button
                                        onClick={() => setPayAmount(String(getTargetBalance()))}
                                        className="text-[10px] text-primary hover:underline mt-1"
                                    >
                                        Pay full balance ({fmt(getTargetBalance())})
                                    </button>
                                </div>

                                {/* Payment Mode */}
                                <div>
                                    <label className="block text-sm font-medium text-muted-foreground mb-1">Payment Mode</label>
                                    <select
                                        value={payModeId}
                                        onChange={(e) => setPayModeId(Number(e.target.value))}
                                        className="w-full px-4 py-2.5 bg-card border border-border rounded-lg text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                                    >
                                        <option value="">Select mode...</option>
                                        {paymentModes.map((m) => (
                                            <option key={m.id} value={m.id}>{m.name}</option>
                                        ))}
                                    </select>
                                </div>
                            </div>

                            <div className="grid grid-cols-2 gap-4">
                                {/* Reference */}
                                <div>
                                    <label className="block text-sm font-medium text-muted-foreground mb-1">Reference No (optional)</label>
                                    <input
                                        type="text"
                                        value={payReference}
                                        onChange={(e) => setPayReference(e.target.value)}
                                        placeholder="Cheque no, UTR, UPI ref..."
                                        className="w-full px-4 py-2.5 bg-card border border-border rounded-lg text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                                    />
                                </div>

                                {/* Remarks */}
                                <div>
                                    <label className="block text-sm font-medium text-muted-foreground mb-1">Remarks (optional)</label>
                                    <input
                                        type="text"
                                        value={payRemarks}
                                        onChange={(e) => setPayRemarks(e.target.value)}
                                        placeholder="Any notes..."
                                        className="w-full px-4 py-2.5 bg-card border border-border rounded-lg text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                                    />
                                </div>
                            </div>

                            {/* Proof Image Upload */}
                            <div>
                                <label className="block text-sm font-medium text-muted-foreground mb-1">
                                    <Paperclip className="w-3.5 h-3.5 inline mr-1" />
                                    Attach Proof (optional)
                                </label>
                                <div className="flex items-center gap-3">
                                    <label className="flex-1 flex items-center gap-2 px-4 py-2.5 bg-card border border-border border-dashed rounded-lg text-muted-foreground text-sm cursor-pointer hover:border-primary/50 transition-colors">
                                        <ImageIcon className="w-4 h-4" />
                                        {proofFile ? proofFile.name : "Cheque scan, UPI screenshot..."}
                                        <input
                                            type="file"
                                            accept="image/*"
                                            className="hidden"
                                            onChange={(e) => setProofFile(e.target.files?.[0] || null)}
                                        />
                                    </label>
                                    {proofFile && (
                                        <button
                                            onClick={() => setProofFile(null)}
                                            className="text-xs text-rose-400 hover:underline"
                                        >
                                            Remove
                                        </button>
                                    )}
                                </div>
                            </div>
                        </>
                    )}

                    <div className="flex justify-end gap-3 pt-2">
                        <button
                            onClick={() => { setShowPayModal(false); resetPayForm(); }}
                            className="px-4 py-2 rounded-lg border border-border text-muted-foreground hover:bg-muted transition-colors"
                        >
                            Cancel
                        </button>
                        <button
                            onClick={handleRecordPayment}
                            disabled={submitting || !selectedTarget || !payAmount || !payModeId}
                            className="btn-gradient px-6 py-2 rounded-lg font-medium disabled:opacity-50"
                        >
                            {submitting ? "Recording..." : "Record Payment"}
                        </button>
                    </div>
                </div>
            </Modal>

            {/* Payment Detail View Modal */}
            <Modal
                isOpen={!!viewPayment}
                onClose={() => setViewPayment(null)}
                title="Payment Details"
            >
                {viewPayment && (
                    <div className="space-y-4">
                        {/* Amount & Status */}
                        <div className="flex items-center justify-between bg-primary/5 border border-primary/20 rounded-lg px-4 py-3">
                            <div>
                                <div className="text-2xl font-bold text-emerald-400">{fmtCurrency(viewPayment.amount)}</div>
                                <div className="text-xs text-muted-foreground mt-1">
                                    {viewPayment.paymentDate ? new Date(viewPayment.paymentDate).toLocaleString("en-IN", {
                                        day: "2-digit", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit"
                                    }) : "-"}
                                </div>
                            </div>
                            <div className="text-right">
                                {statusBadge(viewPayment.targetPaymentStatus)}
                            </div>
                        </div>

                        {/* Details Grid */}
                        <div className="grid grid-cols-2 gap-3 text-sm">
                            <div>
                                <span className="text-muted-foreground text-xs">Customer</span>
                                <div className="font-medium">{viewPayment.customer?.name || "-"}</div>
                            </div>
                            <div>
                                <span className="text-muted-foreground text-xs">Payment Mode</span>
                                <div className="font-medium">{viewPayment.paymentMode?.name || "-"}</div>
                            </div>
                            <div>
                                <span className="text-muted-foreground text-xs">Paid Against</span>
                                <div className="font-medium">
                                    {viewPayment.statement
                                        ? `Statement #${viewPayment.statement.statementNo}`
                                        : viewPayment.invoiceBill
                                            ? `Bill #${viewPayment.invoiceBill.id}`
                                            : "-"}
                                </div>
                            </div>
                            <div>
                                <span className="text-muted-foreground text-xs">Reference No</span>
                                <div className="font-medium">{viewPayment.referenceNo || "-"}</div>
                            </div>
                            <div>
                                <span className="text-muted-foreground text-xs">Received By</span>
                                <div className="font-medium">{viewPayment.receivedBy?.name || "-"}</div>
                            </div>
                            <div>
                                <span className="text-muted-foreground text-xs">Remarks</span>
                                <div className="font-medium">{viewPayment.remarks || "-"}</div>
                            </div>
                        </div>

                        {/* Invoice/Statement Amounts */}
                        {(viewPayment.statement || viewPayment.invoiceBill) && (
                            <div className="border border-border rounded-lg p-3">
                                <div className="text-xs text-muted-foreground mb-2 font-medium">
                                    {viewPayment.statement ? "Statement" : "Bill"} Summary
                                </div>
                                <div className="grid grid-cols-3 gap-3 text-sm">
                                    <div>
                                        <span className="text-muted-foreground text-xs">Net Amount</span>
                                        <div className="font-bold">
                                            {fmtCurrency(viewPayment.statement?.netAmount ?? viewPayment.invoiceBill?.netAmount ?? 0)}
                                        </div>
                                    </div>
                                    {viewPayment.statement && (
                                        <>
                                            <div>
                                                <span className="text-muted-foreground text-xs">Total Received</span>
                                                <div className="font-bold text-emerald-400">
                                                    {fmtCurrency(viewPayment.statement.receivedAmount)}
                                                </div>
                                            </div>
                                            <div>
                                                <span className="text-muted-foreground text-xs">Balance</span>
                                                <div className={`font-bold ${viewPayment.statement.balanceAmount > 0 ? "text-amber-400" : "text-emerald-400"}`}>
                                                    {fmtCurrency(viewPayment.statement.balanceAmount)}
                                                </div>
                                            </div>
                                        </>
                                    )}
                                </div>
                            </div>
                        )}

                        {/* Action buttons */}
                        <div className="flex items-center gap-3 pt-2 border-t border-border">
                            {viewPayment.proofImageKey && (
                                <button
                                    onClick={async () => {
                                        try {
                                            const data = await getPaymentProofUrl(viewPayment.id!);
                                            window.open(data.url, '_blank');
                                        } catch {
                                            alert("Failed to load proof image");
                                        }
                                    }}
                                    className="flex items-center gap-2 px-3 py-2 rounded-lg border border-border text-sm text-foreground hover:bg-muted transition-colors"
                                >
                                    <ImageIcon className="w-4 h-4 text-primary" />
                                    View Proof
                                </button>
                            )}
                            <button
                                onClick={() => handleDownloadReceipt(viewPayment.id!)}
                                className="flex items-center gap-2 px-3 py-2 rounded-lg border border-border text-sm text-foreground hover:bg-muted transition-colors"
                            >
                                <Download className="w-4 h-4 text-primary" />
                                Download Receipt
                            </button>
                            <button
                                onClick={() => setViewPayment(null)}
                                className="ml-auto px-4 py-2 rounded-lg border border-border text-muted-foreground hover:bg-muted transition-colors text-sm"
                            >
                                Close
                            </button>
                        </div>
                    </div>
                )}
            </Modal>
        </div>
    );
}
