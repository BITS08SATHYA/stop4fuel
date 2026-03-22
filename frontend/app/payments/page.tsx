"use client";

import { useState, useEffect } from "react";
import { TablePagination } from "@/components/ui/table-pagination";
import { GlassCard } from "@/components/ui/glass-card";
import { Badge } from "@/components/ui/badge";
import { Modal } from "@/components/ui/modal";
import {
    CreditCard, Receipt, FileText, Search, Check, IndianRupee, Clock, ImageIcon, Paperclip, ExternalLink
} from "lucide-react";
import {
    getPayments, getPaymentModes, getOutstandingStatements,
    getCustomers, recordStatementPayment, recordBillPayment,
    getOutstandingBills, uploadPaymentProof, getPaymentProofUrl,
    type Payment, type PaymentMode, type Statement, type InvoiceBill, type Customer, type PageResponse
} from "@/lib/api/station";

type PayTarget = "statement" | "bill";

const fmt = (n: number) =>
    Number(n).toLocaleString("en-IN", { minimumFractionDigits: 2 });

const fmtCurrency = (n: number) =>
    Number(n).toLocaleString("en-IN", { style: "currency", currency: "INR" });

export default function PaymentsPage() {
    const [payments, setPayments] = useState<Payment[]>([]);
    const [paymentModes, setPaymentModes] = useState<PaymentMode[]>([]);
    const [loading, setLoading] = useState(true);

    // Pagination
    const [page, setPage] = useState(0);
    const [pageSize] = useState(10);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);

    // Record payment modal
    const [showPayModal, setShowPayModal] = useState(false);
    const [payTarget, setPayTarget] = useState<PayTarget>("statement");

    // Step 1: Customer search
    const [customerSearch, setCustomerSearch] = useState("");
    const [customerSuggestions, setCustomerSuggestions] = useState<any[]>([]);
    const [selectedCustomer, setSelectedCustomer] = useState<any>(null);

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

    useEffect(() => {
        loadInitialData();
    }, []);

    useEffect(() => {
        loadPayments();
    }, [page, categoryFilter]);

    const loadInitialData = async () => {
        try {
            const modes = await getPaymentModes();
            setPaymentModes(modes);
        } catch (e) {
            console.error("Failed to load data", e);
        }
    };

    const loadPayments = async () => {
        setLoading(true);
        try {
            const result: PageResponse<Payment> = await getPayments(page, pageSize, categoryFilter || undefined);
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
                    // Payment saved but proof upload failed — non-blocking
                    console.error("Proof upload failed");
                }
            }

            setShowPayModal(false);
            resetPayForm();
            loadPayments();
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

    if (loading) {
        return (
            <div className="p-8 flex items-center justify-center h-screen">
                <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary" />
            </div>
        );
    }

    return (
        <div className="p-6 h-screen overflow-hidden bg-background transition-colors duration-300">
            <div className="max-w-7xl mx-auto">
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
                    <button
                        onClick={() => setShowPayModal(true)}
                        className="btn-gradient px-6 py-3 rounded-xl font-medium flex items-center gap-2"
                    >
                        <CreditCard className="w-5 h-5" />
                        Record Payment
                    </button>
                </div>

                {/* Stats */}
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-8">
                    <GlassCard>
                        <div className="text-muted-foreground text-sm">Total Payments</div>
                        <div className="text-2xl font-bold text-foreground mt-1">{totalElements}</div>
                    </GlassCard>
                    <GlassCard>
                        <div className="text-muted-foreground text-sm">Page</div>
                        <div className="text-2xl font-bold text-foreground mt-1">{page + 1} of {totalPages || 1}</div>
                    </GlassCard>
                </div>

                {/* Payment History Table */}
                <GlassCard>
                    {/* Filter Bar */}
                    <div className="flex flex-wrap gap-3 items-center mb-4">
                        <div className="relative flex-1 min-w-[200px] max-w-md">
                            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                            <input
                                type="text"
                                placeholder="Search by customer, reference..."
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
                            value={modeFilter}
                            onChange={(e) => setModeFilter(e.target.value)}
                            className="px-4 py-2 bg-background border border-border rounded-xl text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
                        >
                            <option value="ALL">All Modes</option>
                            {paymentModes.map((m) => (
                                <option key={m.id} value={m.modeName}>{m.modeName}</option>
                            ))}
                        </select>
                    </div>

                    <div className="overflow-x-auto">
                        <table className="w-full text-sm">
                            <thead>
                                <tr className="border-b border-border text-muted-foreground">
                                    <th className="text-left py-3 px-4">Date</th>
                                    <th className="text-left py-3 px-4">Customer</th>
                                    <th className="text-left py-3 px-4">Paid Against</th>
                                    <th className="text-right py-3 px-4">Amount</th>
                                    <th className="text-left py-3 px-4">Mode</th>
                                    <th className="text-left py-3 px-4">Reference</th>
                                    <th className="text-left py-3 px-4">Remarks</th>
                                    <th className="text-center py-3 px-4">Proof</th>
                                </tr>
                            </thead>
                            <tbody>
                                {payments.length === 0 ? (
                                    <tr>
                                        <td colSpan={8} className="text-center py-8 text-muted-foreground">
                                            No payments recorded yet
                                        </td>
                                    </tr>
                                ) : (
                                    payments.filter((p) => {
                                        const q = tableSearch.toLowerCase();
                                        const matchesSearch = !tableSearch || p.customer?.name?.toLowerCase().includes(q) || p.referenceNo?.toLowerCase().includes(q) || p.remarks?.toLowerCase().includes(q);
                                        const matchesMode = modeFilter === "ALL" || p.paymentMode?.modeName === modeFilter;
                                        return matchesSearch && matchesMode;
                                    }).map((p) => (
                                        <tr key={p.id} className="border-b border-border/50 hover:bg-muted/50 transition-colors">
                                            <td className="py-3 px-4 text-muted-foreground">
                                                {p.paymentDate ? new Date(p.paymentDate).toLocaleString("en-IN", {
                                                    day: "2-digit", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit"
                                                }) : "-"}
                                            </td>
                                            <td className="py-3 px-4 font-medium">{p.customer?.name || "-"}</td>
                                            <td className="py-3 px-4">
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
                                            <td className="py-3 px-4 text-right font-semibold text-emerald-400">
                                                {fmtCurrency(p.amount)}
                                            </td>
                                            <td className="py-3 px-4">{p.paymentMode?.modeName || "-"}</td>
                                            <td className="py-3 px-4 text-muted-foreground">{p.referenceNo || "-"}</td>
                                            <td className="py-3 px-4 text-muted-foreground">{p.remarks || "-"}</td>
                                            <td className="py-3 px-4 text-center">
                                                {p.proofImageKey ? (
                                                    <button
                                                        onClick={async () => {
                                                            try {
                                                                const data = await getPaymentProofUrl(p.id!);
                                                                window.open(data.url, '_blank');
                                                            } catch {
                                                                alert("Failed to load proof image");
                                                            }
                                                        }}
                                                        className="text-primary hover:text-primary/80 transition-colors"
                                                        title="View proof"
                                                    >
                                                        <ImageIcon className="w-4 h-4 inline" />
                                                    </button>
                                                ) : (
                                                    <span className="text-muted-foreground/30">-</span>
                                                )}
                                            </td>
                                        </tr>
                                    ))
                                )}
                            </tbody>
                        </table>
                    </div>

                    <TablePagination page={page} totalPages={totalPages} totalElements={totalElements} pageSize={pageSize} onPageChange={setPage} />
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
                                                            {bill.products.map(p => p.product?.name).filter(Boolean).join(", ")}
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
                                            <option key={m.id} value={m.id}>{m.modeName}</option>
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
        </div>
    );
}
