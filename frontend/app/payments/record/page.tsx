"use client";

import { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import { GlassCard } from "@/components/ui/glass-card";
import {
    CreditCard, Receipt, FileText, Search, Check, IndianRupee, Clock,
    ImageIcon, Paperclip, ArrowLeft, ChevronRight
} from "lucide-react";
import {
    getPaymentModes, getOutstandingByCustomer,
    getCustomers, recordStatementPayment, recordBillPayment,
    getOutstandingBills, uploadPaymentProof,
    type Payment, type PaymentMode, type Statement, type InvoiceBill
} from "@/lib/api/station";
import { PermissionGate } from "@/components/permission-gate";

type PayTarget = "statement" | "bill";

const fmt = (n: number) =>
    Number(n).toLocaleString("en-IN", { minimumFractionDigits: 2 });

const fmtCurrency = (n: number) =>
    Number(n).toLocaleString("en-IN", { style: "currency", currency: "INR" });

export default function RecordPaymentPage() {
    const router = useRouter();
    const [paymentModes, setPaymentModes] = useState<PaymentMode[]>([]);
    const [payTarget, setPayTarget] = useState<PayTarget>("statement");

    // Customer search
    const [customerSearch, setCustomerSearch] = useState("");
    const [customerSuggestions, setCustomerSuggestions] = useState<any[]>([]);
    const [selectedCustomer, setSelectedCustomer] = useState<any>(null);

    // Outstanding items
    const [outstandingStatements, setOutstandingStatements] = useState<Statement[]>([]);
    const [outstandingBills, setOutstandingBills] = useState<InvoiceBill[]>([]);
    const [loadingOutstanding, setLoadingOutstanding] = useState(false);
    const [selectedTarget, setSelectedTarget] = useState<Statement | InvoiceBill | null>(null);
    const [billSearch, setBillSearch] = useState("");

    // Date filter (default: current month)
    const [outFromDate, setOutFromDate] = useState(() => {
        const d = new Date(); d.setDate(1);
        return d.toISOString().split("T")[0];
    });
    const [outToDate, setOutToDate] = useState(() => new Date().toISOString().split("T")[0]);

    // Payment details
    const [payAmount, setPayAmount] = useState("");
    const [payModeId, setPayModeId] = useState<number | "">("");
    const [payReference, setPayReference] = useState("");
    const [payRemarks, setPayRemarks] = useState("");
    // Mode-specific metadata
    const [cardLast4, setCardLast4] = useState("");
    const [cardType, setCardType] = useState("");
    const [authCode, setAuthCode] = useState("");
    const [chequeNo, setChequeNo] = useState("");
    const [chequeBank, setChequeBank] = useState("");
    const [chequeDate, setChequeDate] = useState("");
    const [utrNo, setUtrNo] = useState("");
    const [upiId, setUpiId] = useState("");
    const [bankName, setBankName] = useState("");
    const [depositSlipNo, setDepositSlipNo] = useState("");
    const [proofFile, setProofFile] = useState<File | null>(null);
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState("");
    const [success, setSuccess] = useState("");

    useEffect(() => {
        getPaymentModes().then(setPaymentModes).catch(console.error);
    }, []);

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
        setSuccess("");
        await loadOutstandingItems(c.id, payTarget);
    };

    const loadOutstandingItems = async (customerId: number, target: PayTarget, from?: string, to?: string) => {
        setLoadingOutstanding(true);
        const fd = (from ?? outFromDate) || undefined;
        const td = (to ?? outToDate) || undefined;
        try {
            if (target === "statement") {
                const stmts = await getOutstandingByCustomer(customerId, fd, td);
                setOutstandingStatements(stmts);
            } else {
                const bills = await getOutstandingBills(customerId, fd, td);
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

    const selectedModeName = paymentModes.find(m => m.id === payModeId)?.modeName || "";

    const buildReferenceNo = (): string => {
        const parts: string[] = [];
        switch (selectedModeName) {
            case "CARD":
                if (cardLast4) parts.push(`Card: ****${cardLast4}`);
                if (cardType) parts.push(`Type: ${cardType}`);
                if (authCode) parts.push(`Auth: ${authCode}`);
                break;
            case "CHEQUE":
                if (chequeNo) parts.push(`Chq: ${chequeNo}`);
                if (chequeBank) parts.push(`Bank: ${chequeBank}`);
                if (chequeDate) parts.push(`Date: ${chequeDate}`);
                break;
            case "UPI":
                if (utrNo) parts.push(`UTR: ${utrNo}`);
                if (upiId) parts.push(`UPI: ${upiId}`);
                break;
            case "NEFT":
                if (utrNo) parts.push(`UTR: ${utrNo}`);
                if (bankName) parts.push(`Bank: ${bankName}`);
                break;
            case "BANK":
                if (depositSlipNo) parts.push(`Slip: ${depositSlipNo}`);
                if (bankName) parts.push(`Bank: ${bankName}`);
                break;
            case "CCMS":
                if (payReference) parts.push(`Ref: ${payReference}`);
                break;
            default:
                if (payReference) parts.push(payReference);
        }
        return parts.join(" | ");
    };

    const resetModeFields = () => {
        setCardLast4(""); setCardType(""); setAuthCode("");
        setChequeNo(""); setChequeBank(""); setChequeDate("");
        setUtrNo(""); setUpiId(""); setBankName(""); setDepositSlipNo("");
        setPayReference("");
    };

    const getTargetBalance = (): number => {
        if (!selectedTarget) return 0;
        if (payTarget === "statement") {
            return (selectedTarget as Statement).balanceAmount || 0;
        }
        return (selectedTarget as InvoiceBill).netAmount || 0;
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
        setSuccess("");
        try {
            const paymentData: Partial<Payment> = {
                amount: Number(payAmount),
                paymentMode: { id: Number(payModeId) } as PaymentMode,
                referenceNo: buildReferenceNo() || undefined,
                remarks: payRemarks || undefined,
                paymentDate: new Date().toISOString(),
            };

            let saved: Payment;
            if (payTarget === "statement") {
                saved = await recordStatementPayment(Number((selectedTarget as Statement).id), paymentData);
            } else {
                saved = await recordBillPayment(Number((selectedTarget as InvoiceBill).id), paymentData);
            }

            if (proofFile && saved.id) {
                try { await uploadPaymentProof(saved.id, proofFile); } catch { console.error("Proof upload failed"); }
            }

            // Reset payment form but keep customer selected for next payment
            setSelectedTarget(null);
            setPayAmount("");
            setPayModeId("");
            setPayReference("");
            setPayRemarks("");
            setProofFile(null);
            resetModeFields();
            setSuccess(`Payment of ${fmtCurrency(Number(paymentData.amount))} recorded successfully!`);

            // Reload outstanding items to reflect the payment
            if (selectedCustomer) {
                await loadOutstandingItems(selectedCustomer.id, payTarget);
            }
        } catch (e: any) {
            setError(e.message || "Failed to record payment");
        } finally {
            setSubmitting(false);
        }
    };

    // Filtered items
    const filteredStatements = outstandingStatements.filter(s => {
        if (!billSearch) return true;
        const q = billSearch.toLowerCase();
        return String(s.statementNo).includes(q) || s.fromDate?.includes(q) || s.toDate?.includes(q);
    });

    const filteredBills = outstandingBills.filter(b => {
        if (!billSearch) return true;
        const q = billSearch.toLowerCase();
        return String(b.id).includes(q) ||
            (b.vehicle?.vehicleNumber || "").toLowerCase().includes(q) ||
            (b.indentNo || "").toLowerCase().includes(q) ||
            (b.driverName || "").toLowerCase().includes(q);
    });

    const items = payTarget === "statement" ? filteredStatements : filteredBills;
    const totalOutstanding = payTarget === "statement"
        ? outstandingStatements.reduce((s, st) => s + ((st.balanceAmount ?? st.netAmount) || 0), 0)
        : outstandingBills.reduce((s, b) => s + (b.netAmount || 0), 0);
    const itemCount = payTarget === "statement" ? outstandingStatements.length : outstandingBills.length;

    return (
        <div className="p-6 max-w-[1400px] mx-auto">
            {/* Header */}
            <div className="flex items-center gap-4 mb-6">
                <button
                    onClick={() => router.push("/payments")}
                    className="p-2 rounded-lg border border-border text-muted-foreground hover:bg-muted transition-colors"
                >
                    <ArrowLeft className="w-5 h-5" />
                </button>
                <div>
                    <h1 className="text-3xl font-bold text-foreground tracking-tight">
                        Record <span className="text-gradient">Payment</span>
                    </h1>
                    <p className="text-muted-foreground text-sm mt-0.5">
                        Record credit payments against statements or individual bills
                    </p>
                </div>
            </div>

            {/* Pay Against Toggle + Customer Search */}
            <div className="flex flex-wrap items-end gap-4 mb-6">
                <div>
                    <label className="block text-xs font-medium text-muted-foreground mb-1.5">Pay Against</label>
                    <div className="flex">
                        <button
                            onClick={() => handlePayTargetChange("statement")}
                            className={`px-4 py-2 rounded-l-lg border text-sm font-medium transition-colors flex items-center gap-2 ${
                                payTarget === "statement"
                                    ? "border-primary bg-primary/20 text-primary"
                                    : "border-border text-muted-foreground hover:bg-muted"
                            }`}
                        >
                            <Receipt className="w-4 h-4" />Statement
                        </button>
                        <button
                            onClick={() => handlePayTargetChange("bill")}
                            className={`px-4 py-2 rounded-r-lg border border-l-0 text-sm font-medium transition-colors flex items-center gap-2 ${
                                payTarget === "bill"
                                    ? "border-primary bg-primary/20 text-primary"
                                    : "border-border text-muted-foreground hover:bg-muted"
                            }`}
                        >
                            <FileText className="w-4 h-4" />Individual Bill
                        </button>
                    </div>
                </div>

                <div className="flex-1 min-w-[300px] max-w-lg">
                    <label className="block text-xs font-medium text-muted-foreground mb-1.5">Customer</label>
                    <div className="relative">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                        <input
                            type="text"
                            placeholder="Search customer name or phone..."
                            value={customerSearch}
                            onChange={(e) => searchCustomers(e.target.value)}
                            className="w-full pl-10 pr-4 py-2 bg-card border border-border rounded-lg text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                        />
                        {customerSuggestions.length > 0 && (
                            <div className="absolute top-full left-0 right-0 mt-1 bg-card border border-border rounded-lg shadow-xl z-50 max-h-64 overflow-y-auto">
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
                </div>

                {selectedCustomer && (
                    <div className="flex items-center gap-2 px-4 py-2 bg-emerald-500/10 border border-emerald-500/20 rounded-lg">
                        <Check className="w-4 h-4 text-emerald-400" />
                        <span className="text-sm font-medium text-foreground">{selectedCustomer.name}</span>
                        <button
                            onClick={() => {
                                setSelectedCustomer(null); setCustomerSearch(""); setSelectedTarget(null);
                                setOutstandingStatements([]); setOutstandingBills([]); setSuccess("");
                            }}
                            className="text-xs text-muted-foreground hover:text-foreground ml-2"
                        >
                            Change
                        </button>
                    </div>
                )}
            </div>

            {/* Alerts */}
            {error && (
                <div className="bg-rose-500/20 border border-rose-500/30 text-rose-400 px-4 py-2.5 rounded-lg text-sm mb-4">
                    {error}
                </div>
            )}
            {success && (
                <div className="bg-emerald-500/20 border border-emerald-500/30 text-emerald-400 px-4 py-2.5 rounded-lg text-sm mb-4 flex items-center justify-between">
                    <span>{success}</span>
                    <button onClick={() => router.push("/payments")} className="text-xs underline hover:text-emerald-300">
                        View Payments
                    </button>
                </div>
            )}

            {/* Two-column layout */}
            {selectedCustomer && (
                <div className="grid grid-cols-1 lg:grid-cols-5 gap-6">
                    {/* LEFT: Outstanding Items (3 cols) */}
                    <div className="lg:col-span-3">
                        <GlassCard>
                            <div className="flex items-center justify-between mb-3">
                                <h2 className="text-sm font-semibold text-foreground">
                                    Outstanding {payTarget === "statement" ? "Statements" : "Credit Bills"}
                                </h2>
                                <div className="text-xs text-muted-foreground">
                                    {itemCount} {payTarget === "statement" ? "stmt" : "bill"}{itemCount !== 1 ? "s" : ""}
                                    {" | Total: "}
                                    <span className="font-bold text-amber-400">{fmtCurrency(totalOutstanding)}</span>
                                </div>
                            </div>

                            {/* Date filter bar */}
                            <div className="flex flex-wrap gap-2 items-center mb-3 p-2.5 bg-muted/30 rounded-lg border border-border/50">
                                <span className="text-xs text-muted-foreground">From</span>
                                <input
                                    type="date"
                                    value={outFromDate}
                                    onChange={(e) => setOutFromDate(e.target.value)}
                                    className="px-2 py-1.5 bg-background border border-border rounded-lg text-foreground text-xs focus:outline-none focus:ring-1 focus:ring-primary/50"
                                />
                                <span className="text-xs text-muted-foreground">To</span>
                                <input
                                    type="date"
                                    value={outToDate}
                                    onChange={(e) => setOutToDate(e.target.value)}
                                    className="px-2 py-1.5 bg-background border border-border rounded-lg text-foreground text-xs focus:outline-none focus:ring-1 focus:ring-primary/50"
                                />
                                <button
                                    onClick={() => loadOutstandingItems(selectedCustomer.id, payTarget, outFromDate, outToDate)}
                                    disabled={loadingOutstanding}
                                    className="px-3 py-1.5 text-xs font-medium bg-primary text-primary-foreground rounded-lg hover:bg-primary/90 transition-colors disabled:opacity-50 flex items-center gap-1"
                                >
                                    <Search className="w-3 h-3" />
                                    Display
                                </button>
                                <button
                                    onClick={() => { setOutFromDate(""); setOutToDate(""); loadOutstandingItems(selectedCustomer.id, payTarget, "", ""); }}
                                    className="text-xs text-muted-foreground hover:text-foreground transition-colors px-2 py-1.5 border border-border rounded-lg"
                                >
                                    Show All
                                </button>
                            </div>

                            {/* Search */}
                            <div className="relative mb-3">
                                <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-muted-foreground" />
                                <input
                                    type="text"
                                    placeholder={payTarget === "statement" ? "Search by stmt #, date..." : "Search by bill #, vehicle, indent..."}
                                    value={billSearch}
                                    onChange={(e) => setBillSearch(e.target.value)}
                                    className="w-full pl-9 pr-4 py-2 bg-background border border-border rounded-lg text-foreground text-sm focus:outline-none focus:ring-1 focus:ring-primary/50"
                                />
                            </div>

                            {/* Items list */}
                            {loadingOutstanding ? (
                                <div className="flex items-center justify-center py-12">
                                    <div className="animate-spin rounded-full h-6 w-6 border-b-2 border-primary" />
                                </div>
                            ) : payTarget === "statement" ? (
                                <div className="max-h-[calc(100vh-380px)] overflow-y-auto border border-border rounded-lg divide-y divide-border/50">
                                    {filteredStatements.length === 0 ? (
                                        <div className="px-4 py-12 text-center text-muted-foreground text-sm">
                                            No outstanding statements found
                                        </div>
                                    ) : filteredStatements.map(stmt => {
                                        const isSelected = selectedTarget && (selectedTarget as Statement).id === stmt.id;
                                        return (
                                            <button
                                                key={stmt.id}
                                                onClick={() => { setSelectedTarget(stmt); setPayAmount(""); setSuccess(""); }}
                                                className={`w-full px-4 py-3 text-left transition-colors ${
                                                    isSelected ? "bg-primary/10 border-l-3 border-l-primary" : "hover:bg-muted/50 border-l-3 border-l-transparent"
                                                }`}
                                            >
                                                <div className="flex items-center justify-between">
                                                    <div className="flex items-center gap-3">
                                                        <Receipt className="w-4 h-4 text-muted-foreground" />
                                                        <div>
                                                            <span className="text-sm font-bold text-foreground">Stmt #{stmt.statementNo}</span>
                                                            <span className="text-xs text-muted-foreground ml-2">
                                                                {stmt.fromDate} — {stmt.toDate}
                                                            </span>
                                                        </div>
                                                    </div>
                                                    <div className="text-right">
                                                        <div className="text-sm font-bold text-amber-400">{fmtCurrency(stmt.balanceAmount)}</div>
                                                        <div className="text-[10px] text-muted-foreground">of {fmtCurrency(stmt.netAmount)}</div>
                                                    </div>
                                                </div>
                                                <div className="flex items-center gap-3 mt-1 ml-7">
                                                    <span className="text-xs text-muted-foreground">{stmt.numberOfBills} bills</span>
                                                    <span className="text-xs text-emerald-400">Received: {fmt(stmt.receivedAmount)}</span>
                                                    {isSelected && <Check className="w-3.5 h-3.5 text-primary ml-auto" />}
                                                </div>
                                            </button>
                                        );
                                    })}
                                </div>
                            ) : (
                                <div className="max-h-[calc(100vh-380px)] overflow-y-auto border border-border rounded-lg divide-y divide-border/50">
                                    {filteredBills.length === 0 ? (
                                        <div className="px-4 py-12 text-center text-muted-foreground text-sm">
                                            No unpaid credit bills found
                                        </div>
                                    ) : filteredBills.map(bill => {
                                        const isSelected = selectedTarget && (selectedTarget as InvoiceBill).id === bill.id;
                                        const billDate = bill.date ? new Date(bill.date) : null;
                                        const daysOld = billDate ? Math.floor((Date.now() - billDate.getTime()) / (1000 * 60 * 60 * 24)) : 0;
                                        return (
                                            <button
                                                key={bill.id}
                                                onClick={() => { setSelectedTarget(bill); setPayAmount(""); setSuccess(""); }}
                                                className={`w-full px-4 py-3 text-left transition-colors ${
                                                    isSelected ? "bg-primary/10 border-l-3 border-l-primary" : "hover:bg-muted/50 border-l-3 border-l-transparent"
                                                }`}
                                            >
                                                <div className="flex items-center justify-between">
                                                    <div className="flex items-center gap-3">
                                                        <FileText className="w-4 h-4 text-muted-foreground" />
                                                        <div className="flex items-center gap-2">
                                                            <span className="text-sm font-bold text-foreground">Bill #{bill.id}</span>
                                                            <span className="text-xs text-muted-foreground">
                                                                {billDate?.toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "2-digit" })}
                                                            </span>
                                                            {bill.vehicle?.vehicleNumber && (
                                                                <span className="text-xs text-muted-foreground font-medium bg-muted px-1.5 py-0.5 rounded">
                                                                    {bill.vehicle.vehicleNumber}
                                                                </span>
                                                            )}
                                                        </div>
                                                    </div>
                                                    <div className="flex items-center gap-3">
                                                        <span className={`text-xs flex items-center gap-0.5 ${
                                                            daysOld > 90 ? "text-rose-400" : daysOld > 60 ? "text-orange-400" : daysOld > 30 ? "text-amber-400" : "text-muted-foreground"
                                                        }`}>
                                                            <Clock className="w-3 h-3" />{daysOld}d
                                                        </span>
                                                        <span className="text-sm font-bold text-foreground">{fmtCurrency(bill.netAmount)}</span>
                                                    </div>
                                                </div>
                                                <div className="flex items-center gap-3 mt-1 ml-7">
                                                    {bill.indentNo && <span className="text-xs text-muted-foreground">Indent: {bill.indentNo}</span>}
                                                    {bill.driverName && <span className="text-xs text-muted-foreground">Driver: {bill.driverName}</span>}
                                                    {bill.products?.length > 0 && (
                                                        <span className="text-xs text-muted-foreground truncate max-w-[250px]">
                                                            {bill.products.map(p => p.product?.name).filter(Boolean).join(", ")}
                                                        </span>
                                                    )}
                                                    {isSelected && <Check className="w-3.5 h-3.5 text-primary ml-auto" />}
                                                </div>
                                            </button>
                                        );
                                    })}
                                </div>
                            )}
                        </GlassCard>
                    </div>

                    {/* RIGHT: Payment Form (2 cols) */}
                    <div className="lg:col-span-2">
                        <GlassCard>
                            <h2 className="text-sm font-semibold text-foreground mb-4">Payment Details</h2>

                            {!selectedTarget ? (
                                <div className="flex flex-col items-center justify-center py-16 text-center">
                                    <ChevronRight className="w-8 h-8 text-muted-foreground/30 mb-3" />
                                    <p className="text-muted-foreground text-sm">
                                        Select a {payTarget === "statement" ? "statement" : "bill"} from the left to record payment
                                    </p>
                                </div>
                            ) : (
                                <div className="space-y-4">
                                    {/* Selected target summary */}
                                    <div className="bg-primary/5 border border-primary/20 rounded-lg px-4 py-3">
                                        <div className="text-xs text-muted-foreground mb-1">Paying against</div>
                                        <div className="flex items-center justify-between">
                                            <span className="text-sm font-semibold text-foreground">
                                                {payTarget === "statement"
                                                    ? `Stmt #${(selectedTarget as Statement).statementNo}`
                                                    : `Bill #${(selectedTarget as InvoiceBill).id}`
                                                }
                                            </span>
                                            <div className="text-right">
                                                <div className="text-lg font-bold text-amber-400">
                                                    {fmtCurrency(getTargetBalance())}
                                                </div>
                                                <div className="text-[10px] text-muted-foreground">Outstanding</div>
                                            </div>
                                        </div>
                                    </div>

                                    {/* Amount */}
                                    <div>
                                        <label className="block text-xs font-medium text-muted-foreground mb-1.5">Amount</label>
                                        <div className="relative">
                                            <IndianRupee className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                                            <input
                                                type="number"
                                                value={payAmount}
                                                onChange={(e) => setPayAmount(e.target.value)}
                                                placeholder="0.00"
                                                max={getTargetBalance()}
                                                className="w-full pl-10 pr-4 py-2.5 bg-card border border-border rounded-lg text-foreground text-lg font-bold focus:outline-none focus:ring-2 focus:ring-primary/50"
                                            />
                                        </div>
                                        <button
                                            onClick={() => setPayAmount(String(getTargetBalance()))}
                                            className="text-xs text-primary hover:underline mt-1"
                                        >
                                            Pay full balance ({fmt(getTargetBalance())})
                                        </button>
                                    </div>

                                    {/* Payment Mode */}
                                    <div>
                                        <label className="block text-xs font-medium text-muted-foreground mb-1.5">Payment Mode</label>
                                        <select
                                            value={payModeId}
                                            onChange={(e) => { setPayModeId(Number(e.target.value)); resetModeFields(); }}
                                            className="w-full px-4 py-2.5 bg-card border border-border rounded-lg text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                                        >
                                            <option value="">Select mode...</option>
                                            {paymentModes.map((m) => (
                                                <option key={m.id} value={m.id}>{m.modeName}</option>
                                            ))}
                                        </select>
                                    </div>

                                    {/* Mode-specific reference fields */}
                                    {selectedModeName === "CARD" && (
                                        <div className="space-y-3 p-3 bg-muted/20 rounded-lg border border-border/50">
                                            <div className="text-xs font-medium text-muted-foreground">Card Details</div>
                                            <div className="grid grid-cols-2 gap-3">
                                                <div>
                                                    <label className="block text-[10px] text-muted-foreground mb-1">Last 4 Digits</label>
                                                    <input type="text" value={cardLast4} onChange={(e) => setCardLast4(e.target.value.replace(/\D/g, "").slice(0, 4))}
                                                        placeholder="1234" maxLength={4}
                                                        className="w-full px-3 py-2 bg-card border border-border rounded-lg text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-1 focus:ring-primary/50 text-sm" />
                                                </div>
                                                <div>
                                                    <label className="block text-[10px] text-muted-foreground mb-1">Card Type</label>
                                                    <select value={cardType} onChange={(e) => setCardType(e.target.value)}
                                                        className="w-full px-3 py-2 bg-card border border-border rounded-lg text-foreground focus:outline-none focus:ring-1 focus:ring-primary/50 text-sm">
                                                        <option value="">Select...</option>
                                                        <option value="Debit">Debit</option>
                                                        <option value="Credit">Credit</option>
                                                    </select>
                                                </div>
                                            </div>
                                            <div>
                                                <label className="block text-[10px] text-muted-foreground mb-1">Authorization Code (optional)</label>
                                                <input type="text" value={authCode} onChange={(e) => setAuthCode(e.target.value)}
                                                    placeholder="Auth code from POS machine"
                                                    className="w-full px-3 py-2 bg-card border border-border rounded-lg text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-1 focus:ring-primary/50 text-sm" />
                                            </div>
                                        </div>
                                    )}

                                    {selectedModeName === "CHEQUE" && (
                                        <div className="space-y-3 p-3 bg-muted/20 rounded-lg border border-border/50">
                                            <div className="text-xs font-medium text-muted-foreground">Cheque Details</div>
                                            <div className="grid grid-cols-2 gap-3">
                                                <div>
                                                    <label className="block text-[10px] text-muted-foreground mb-1">Cheque Number</label>
                                                    <input type="text" value={chequeNo} onChange={(e) => setChequeNo(e.target.value)}
                                                        placeholder="e.g. 445566"
                                                        className="w-full px-3 py-2 bg-card border border-border rounded-lg text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-1 focus:ring-primary/50 text-sm" />
                                                </div>
                                                <div>
                                                    <label className="block text-[10px] text-muted-foreground mb-1">Bank Name</label>
                                                    <input type="text" value={chequeBank} onChange={(e) => setChequeBank(e.target.value)}
                                                        placeholder="e.g. SBI, HDFC"
                                                        className="w-full px-3 py-2 bg-card border border-border rounded-lg text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-1 focus:ring-primary/50 text-sm" />
                                                </div>
                                            </div>
                                            <div>
                                                <label className="block text-[10px] text-muted-foreground mb-1">Cheque Date</label>
                                                <input type="date" value={chequeDate} onChange={(e) => setChequeDate(e.target.value)}
                                                    className="w-full px-3 py-2 bg-card border border-border rounded-lg text-foreground focus:outline-none focus:ring-1 focus:ring-primary/50 text-sm" />
                                            </div>
                                        </div>
                                    )}

                                    {selectedModeName === "UPI" && (
                                        <div className="space-y-3 p-3 bg-muted/20 rounded-lg border border-border/50">
                                            <div className="text-xs font-medium text-muted-foreground">UPI Details</div>
                                            <div>
                                                <label className="block text-[10px] text-muted-foreground mb-1">UTR / Transaction ID</label>
                                                <input type="text" value={utrNo} onChange={(e) => setUtrNo(e.target.value)}
                                                    placeholder="e.g. 420263012345678901"
                                                    className="w-full px-3 py-2 bg-card border border-border rounded-lg text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-1 focus:ring-primary/50 text-sm" />
                                            </div>
                                            <div>
                                                <label className="block text-[10px] text-muted-foreground mb-1">UPI ID (optional)</label>
                                                <input type="text" value={upiId} onChange={(e) => setUpiId(e.target.value)}
                                                    placeholder="e.g. customer@upi"
                                                    className="w-full px-3 py-2 bg-card border border-border rounded-lg text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-1 focus:ring-primary/50 text-sm" />
                                            </div>
                                        </div>
                                    )}

                                    {selectedModeName === "NEFT" && (
                                        <div className="space-y-3 p-3 bg-muted/20 rounded-lg border border-border/50">
                                            <div className="text-xs font-medium text-muted-foreground">NEFT Details</div>
                                            <div>
                                                <label className="block text-[10px] text-muted-foreground mb-1">UTR Number</label>
                                                <input type="text" value={utrNo} onChange={(e) => setUtrNo(e.target.value)}
                                                    placeholder="e.g. UTIB20260328001234"
                                                    className="w-full px-3 py-2 bg-card border border-border rounded-lg text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-1 focus:ring-primary/50 text-sm" />
                                            </div>
                                            <div>
                                                <label className="block text-[10px] text-muted-foreground mb-1">Bank Name (optional)</label>
                                                <input type="text" value={bankName} onChange={(e) => setBankName(e.target.value)}
                                                    placeholder="e.g. Axis Bank"
                                                    className="w-full px-3 py-2 bg-card border border-border rounded-lg text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-1 focus:ring-primary/50 text-sm" />
                                            </div>
                                        </div>
                                    )}

                                    {selectedModeName === "BANK" && (
                                        <div className="space-y-3 p-3 bg-muted/20 rounded-lg border border-border/50">
                                            <div className="text-xs font-medium text-muted-foreground">Bank Deposit Details</div>
                                            <div className="grid grid-cols-2 gap-3">
                                                <div>
                                                    <label className="block text-[10px] text-muted-foreground mb-1">Deposit Slip No</label>
                                                    <input type="text" value={depositSlipNo} onChange={(e) => setDepositSlipNo(e.target.value)}
                                                        placeholder="Slip number"
                                                        className="w-full px-3 py-2 bg-card border border-border rounded-lg text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-1 focus:ring-primary/50 text-sm" />
                                                </div>
                                                <div>
                                                    <label className="block text-[10px] text-muted-foreground mb-1">Bank Name</label>
                                                    <input type="text" value={bankName} onChange={(e) => setBankName(e.target.value)}
                                                        placeholder="e.g. Indian Bank"
                                                        className="w-full px-3 py-2 bg-card border border-border rounded-lg text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-1 focus:ring-primary/50 text-sm" />
                                                </div>
                                            </div>
                                        </div>
                                    )}

                                    {selectedModeName === "CCMS" && (
                                        <div className="space-y-3 p-3 bg-muted/20 rounded-lg border border-border/50">
                                            <div className="text-xs font-medium text-muted-foreground">CCMS Details</div>
                                            <div>
                                                <label className="block text-[10px] text-muted-foreground mb-1">CCMS Reference ID</label>
                                                <input type="text" value={payReference} onChange={(e) => setPayReference(e.target.value)}
                                                    placeholder="CCMS transaction reference"
                                                    className="w-full px-3 py-2 bg-card border border-border rounded-lg text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-1 focus:ring-primary/50 text-sm" />
                                            </div>
                                        </div>
                                    )}

                                    {/* Remarks */}
                                    <div>
                                        <label className="block text-xs font-medium text-muted-foreground mb-1.5">Remarks (optional)</label>
                                        <input
                                            type="text"
                                            value={payRemarks}
                                            onChange={(e) => setPayRemarks(e.target.value)}
                                            placeholder="Any notes..."
                                            className="w-full px-4 py-2.5 bg-card border border-border rounded-lg text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                                        />
                                    </div>

                                    {/* Proof Upload */}
                                    <div>
                                        <label className="block text-xs font-medium text-muted-foreground mb-1.5">
                                            <Paperclip className="w-3.5 h-3.5 inline mr-1" />
                                            Attach Proof (optional)
                                        </label>
                                        <label className="flex items-center gap-2 px-4 py-2.5 bg-card border border-border border-dashed rounded-lg text-muted-foreground text-sm cursor-pointer hover:border-primary/50 transition-colors">
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
                                            <button onClick={() => setProofFile(null)} className="text-xs text-rose-400 hover:underline mt-1">
                                                Remove
                                            </button>
                                        )}
                                    </div>

                                    {/* Submit */}
                                    <button
                                        onClick={handleRecordPayment}
                                        disabled={submitting || !payAmount || !payModeId}
                                        className="w-full btn-gradient px-6 py-3 rounded-lg font-semibold disabled:opacity-50 flex items-center justify-center gap-2 mt-2"
                                    >
                                        <CreditCard className="w-5 h-5" />
                                        {submitting ? "Recording..." : "Record Payment"}
                                    </button>
                                </div>
                            )}
                        </GlassCard>
                    </div>
                </div>
            )}

            {/* Empty state when no customer selected */}
            {!selectedCustomer && (
                <GlassCard>
                    <div className="flex flex-col items-center justify-center py-20 text-center">
                        <CreditCard className="w-12 h-12 text-muted-foreground/20 mb-4" />
                        <p className="text-muted-foreground">Search and select a customer above to begin recording payment</p>
                    </div>
                </GlassCard>
            )}
        </div>
    );
}
