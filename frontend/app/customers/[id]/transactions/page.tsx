"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import {
    Bar, CartesianGrid, ComposedChart, Legend, Line, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from "recharts";
import { GlassCard } from "@/components/ui/glass-card";
import { Badge } from "@/components/ui/badge";
import { Modal } from "@/components/ui/modal";
import { API_BASE_URL } from "@/lib/api/station";
import { fetchWithAuth } from "@/lib/api/fetch-with-auth";
import {
    getCustomerLedger, getLedgerYears, getStatementById, getStatementBills, getPaymentsByStatement,
    getInvoiceById,
    type CustomerLedger, type LedgerEntry, type Statement, type Payment,
} from "@/lib/api/station";
import type { InvoiceBill } from "@/lib/api/station";
import {
    ArrowLeft, BookOpen, FileText, IndianRupee, Landmark, ReceiptText, Scale, TrendingUp,
} from "lucide-react";

const TOOLTIP_STYLE = {
    backgroundColor: "rgba(0,0,0,0.85)",
    border: "1px solid rgba(255,255,255,0.1)",
    borderRadius: "12px",
    fontSize: "12px",
};

const MONTH_LABELS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

function fmt(v?: number | null) {
    if (v == null) return "0.00";
    return v.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function fmtCompact(v?: number | null) {
    if (v == null) return "0";
    if (Math.abs(v) >= 10000000) return (v / 10000000).toFixed(1) + "Cr";
    if (Math.abs(v) >= 100000) return (v / 100000).toFixed(1) + "L";
    if (Math.abs(v) >= 1000) return (v / 1000).toFixed(1) + "K";
    return v.toLocaleString("en-IN", { maximumFractionDigits: 0 });
}

function fmtDate(d?: string) {
    if (!d) return "—";
    return new Date(d).toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "numeric" });
}

function entryBadge(e: LedgerEntry) {
    if (e.referenceType === "STATEMENT") return <Badge variant="default" className="text-[9px]">Statement</Badge>;
    if (e.referenceType === "BILL") return <Badge variant="outline" className="text-[9px]">Bill</Badge>;
    return <Badge variant="success" className="text-[9px]">Payment</Badge>;
}

type Detail =
    | { kind: "STATEMENT"; id: number }
    | { kind: "BILL"; id: number };

export default function CustomerTransactionsPage() {
    const params = useParams();
    const customerId = Number(params.id);

    const [customerName, setCustomerName] = useState<string>("");
    const [years, setYears] = useState<number[]>([]);
    const [year, setYear] = useState<number | null>(null);
    const [ledger, setLedger] = useState<CustomerLedger | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const [detail, setDetail] = useState<Detail | null>(null);
    const [detailLoading, setDetailLoading] = useState(false);
    const [stmtDetail, setStmtDetail] = useState<{ statement: Statement; bills: InvoiceBill[]; payments: Payment[] } | null>(null);
    const [billDetail, setBillDetail] = useState<InvoiceBill | null>(null);

    useEffect(() => {
        if (!customerId) return;
        fetchWithAuth(`${API_BASE_URL}/customers/${customerId}`)
            .then((r) => (r.ok ? r.json() : null))
            .then((c) => setCustomerName(c?.name ?? `Customer #${customerId}`))
            .catch(() => setCustomerName(`Customer #${customerId}`));
        getLedgerYears(customerId)
            .then((ys) => {
                setYears(ys);
                setYear(ys.length > 0 ? ys[0] : new Date().getFullYear());
            })
            .catch(() => {
                setYear(new Date().getFullYear());
            });
    }, [customerId]);

    useEffect(() => {
        if (!customerId || year == null) return;
        setLoading(true);
        setError(null);
        getCustomerLedger(customerId, `${year}-01-01`, `${year}-12-31`)
            .then(setLedger)
            .catch((e) => setError(e instanceof Error ? e.message : "Failed to load transactions"))
            .finally(() => setLoading(false));
    }, [customerId, year]);

    const monthly = useMemo(() => {
        const buckets = MONTH_LABELS.map((m) => ({ month: m, billed: 0, collected: 0 }));
        for (const e of ledger?.entries ?? []) {
            const idx = new Date(e.date).getMonth();
            if (idx >= 0 && idx < 12) {
                buckets[idx].billed += e.debitAmount || 0;
                buckets[idx].collected += e.creditAmount || 0;
            }
        }
        return buckets;
    }, [ledger]);

    const openDetail = useCallback(async (e: LedgerEntry) => {
        // Payments open the statement/bill they settle; on-account payments have no detail
        const kind = e.referenceType === "PAYMENT" ? e.relatedType : e.referenceType;
        const id = e.referenceType === "PAYMENT" ? e.relatedId : e.referenceId;
        if (!kind || !id || (kind !== "STATEMENT" && kind !== "BILL")) return;
        setDetail({ kind, id });
        setDetailLoading(true);
        setStmtDetail(null);
        setBillDetail(null);
        try {
            if (kind === "STATEMENT") {
                const [statement, bills, payments] = await Promise.all([
                    getStatementById(id),
                    getStatementBills(id).catch(() => [] as InvoiceBill[]),
                    getPaymentsByStatement(id).catch(() => [] as Payment[]),
                ]);
                setStmtDetail({ statement, bills, payments });
            } else {
                setBillDetail(await getInvoiceById(id));
            }
        } catch {
            setDetail(null);
        } finally {
            setDetailLoading(false);
        }
    }, []);

    const clickable = (e: LedgerEntry) =>
        e.referenceType === "STATEMENT" || e.referenceType === "BILL" ||
        (e.referenceType === "PAYMENT" && e.relatedType != null && e.relatedId != null);

    const kpis = ledger
        ? [
              { label: "Opening Balance", value: `₹${fmtCompact(ledger.openingBalance)}`, icon: Scale, color: "text-muted-foreground", bg: "bg-muted" },
              { label: `Billed in ${year}`, value: `₹${fmtCompact(ledger.totalDebits)}`, icon: IndianRupee, color: "text-primary", bg: "bg-primary/10" },
              { label: `Collected in ${year}`, value: `₹${fmtCompact(ledger.totalCredits)}`, icon: TrendingUp, color: "text-green-500", bg: "bg-green-500/10" },
              {
                  label: "Closing Balance",
                  value: `₹${fmtCompact(ledger.closingBalance)}`,
                  icon: Landmark,
                  color: ledger.closingBalance > 0 ? "text-amber-500" : "text-green-500",
                  bg: ledger.closingBalance > 0 ? "bg-amber-500/10" : "bg-green-500/10",
              },
          ]
        : [];

    return (
        <div className="min-h-screen bg-background p-6 md:p-8 transition-colors duration-300">
            <div className="max-w-7xl mx-auto space-y-6">
                {/* Header */}
                <div className="flex flex-col lg:flex-row lg:items-center lg:justify-between gap-4">
                    <div className="flex items-center gap-3">
                        <Link
                            href={`/customers/${customerId}`}
                            className="p-2 rounded-lg bg-muted hover:bg-muted/80 transition-colors"
                            aria-label="Back to customer profile"
                        >
                            <ArrowLeft className="w-4 h-4" />
                        </Link>
                        <div>
                            <h1 className="text-3xl md:text-4xl font-bold text-foreground tracking-tight">
                                <span className="text-gradient">{customerName || "Transactions"}</span>
                            </h1>
                            <p className="text-muted-foreground mt-1">
                                Every statement, bill and payment{year != null ? ` in ${year}` : ""} — click a row for details
                            </p>
                        </div>
                    </div>
                    <div className="flex flex-wrap items-center gap-2">
                        {(years.length > 0 ? years : year != null ? [year] : []).map((y) => (
                            <button
                                key={y}
                                onClick={() => setYear(y)}
                                className={`px-4 py-2 text-sm font-medium rounded-lg transition-colors ${
                                    year === y ? "bg-primary text-primary-foreground" : "bg-muted text-muted-foreground hover:bg-muted/80"
                                }`}
                            >
                                {y}
                            </button>
                        ))}
                        <Link
                            href={`/payments/ledger?customerId=${customerId}`}
                            className="flex items-center gap-1.5 px-4 py-2 text-sm font-medium rounded-lg bg-muted text-muted-foreground hover:bg-muted/80 transition-colors"
                        >
                            <BookOpen className="w-4 h-4" /> Ledger PDF
                        </Link>
                    </div>
                </div>

                {loading && !ledger ? (
                    <div className="flex flex-col items-center justify-center py-24">
                        <div className="w-12 h-12 border-4 border-primary/20 border-t-primary rounded-full animate-spin mb-4" />
                        <p className="text-muted-foreground animate-pulse">Loading transactions...</p>
                    </div>
                ) : error ? (
                    <GlassCard className="p-8 text-center">
                        <p className="text-red-500">{error}</p>
                    </GlassCard>
                ) : ledger ? (
                    <>
                        {/* KPI tiles */}
                        <div className="grid gap-4 grid-cols-2 lg:grid-cols-4">
                            {kpis.map((kpi) => (
                                <GlassCard key={kpi.label} className="p-4">
                                    <div className={`p-2 ${kpi.bg} rounded-lg w-fit`}>
                                        <kpi.icon className={`w-4 h-4 ${kpi.color}`} />
                                    </div>
                                    <h3 className={`text-xl font-bold mt-2 ${kpi.color}`}>{kpi.value}</h3>
                                    <p className="text-[10px] uppercase tracking-wider text-muted-foreground font-bold mt-1">{kpi.label}</p>
                                </GlassCard>
                            ))}
                        </div>

                        {/* Monthly billed vs collected */}
                        <GlassCard className="p-6">
                            <div className="flex items-center gap-2 mb-4">
                                <div className="p-2 bg-primary/10 rounded-lg">
                                    <TrendingUp className="w-5 h-5 text-primary" />
                                </div>
                                <div>
                                    <h2 className="text-lg font-semibold text-foreground">Billed vs Collected — {year}</h2>
                                    <p className="text-xs text-muted-foreground">Monthly totals from the transactions below</p>
                                </div>
                            </div>
                            <div className="h-64">
                                <ResponsiveContainer width="100%" height="100%">
                                    <ComposedChart data={monthly} margin={{ top: 8, right: 16, left: 0, bottom: 0 }}>
                                        <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" vertical={false} />
                                        <XAxis dataKey="month" tick={{ fontSize: 11 }} stroke="#888" />
                                        <YAxis tick={{ fontSize: 11 }} stroke="#888" tickFormatter={(v) => "₹" + fmtCompact(Number(v))} width={70} />
                                        <Tooltip contentStyle={TOOLTIP_STYLE} formatter={(v, name) => [`₹${fmt(Number(v))}`, name === "billed" ? "Billed" : "Collected"]} />
                                        <Legend wrapperStyle={{ fontSize: 12 }} formatter={(v) => (v === "billed" ? "Billed" : "Collected")} />
                                        <Bar dataKey="billed" fill="#f97316" radius={[6, 6, 0, 0]} maxBarSize={36} />
                                        <Line type="monotone" dataKey="collected" stroke="#10b981" strokeWidth={2} dot={{ r: 3 }} />
                                    </ComposedChart>
                                </ResponsiveContainer>
                            </div>
                        </GlassCard>

                        {/* Transactions ledger */}
                        <GlassCard className="overflow-hidden border-none p-0">
                            <div className="px-6 py-4 border-b border-border/50 flex items-center gap-2">
                                <div className="p-2 bg-primary/10 rounded-lg">
                                    <ReceiptText className="w-5 h-5 text-primary" />
                                </div>
                                <div>
                                    <h2 className="text-lg font-semibold text-foreground">Transactions — {year}</h2>
                                    <p className="text-xs text-muted-foreground">
                                        {ledger.entries.length} entries · running balance carried through the year
                                    </p>
                                </div>
                            </div>
                            <div className="overflow-x-auto">
                                <table className="w-full border-collapse">
                                    <thead>
                                        <tr className="bg-white/5 border-b border-border/50">
                                            {["Date", "Type", "Description", "Billed (Dr)", "Paid (Cr)", "Balance"].map((h) => (
                                                <th key={h} className={`px-4 py-3 text-[10px] font-bold uppercase tracking-widest text-muted-foreground ${
                                                    h === "Billed (Dr)" || h === "Paid (Cr)" || h === "Balance" ? "text-right" : "text-left"
                                                }`}>
                                                    {h}
                                                </th>
                                            ))}
                                        </tr>
                                    </thead>
                                    <tbody className="divide-y divide-border/30">
                                        <tr className="bg-white/5">
                                            <td className="px-4 py-3 text-sm text-muted-foreground">{fmtDate(ledger.fromDate)}</td>
                                            <td className="px-4 py-3" />
                                            <td className="px-4 py-3 text-sm font-semibold text-foreground">Opening Balance</td>
                                            <td className="px-4 py-3" />
                                            <td className="px-4 py-3" />
                                            <td className="px-4 py-3 text-sm font-bold text-right">₹{fmt(ledger.openingBalance)}</td>
                                        </tr>
                                        {ledger.entries.length === 0 ? (
                                            <tr>
                                                <td colSpan={6} className="px-4 py-10 text-center text-sm text-muted-foreground">
                                                    No transactions in {year}
                                                </td>
                                            </tr>
                                        ) : (
                                            ledger.entries.map((e, i) => (
                                                <tr
                                                    key={`${e.referenceType}-${e.referenceId}-${i}`}
                                                    onClick={() => openDetail(e)}
                                                    className={`transition-colors ${clickable(e) ? "hover:bg-white/5 cursor-pointer" : ""}`}
                                                >
                                                    <td className="px-4 py-3 text-sm whitespace-nowrap">{fmtDate(e.date)}</td>
                                                    <td className="px-4 py-3">{entryBadge(e)}</td>
                                                    <td className="px-4 py-3 text-sm text-foreground">{e.description}</td>
                                                    <td className="px-4 py-3 text-sm text-right">
                                                        {e.debitAmount > 0 ? <span className="font-semibold text-primary">₹{fmt(e.debitAmount)}</span> : "—"}
                                                    </td>
                                                    <td className="px-4 py-3 text-sm text-right">
                                                        {e.creditAmount > 0 ? <span className="font-semibold text-green-500">₹{fmt(e.creditAmount)}</span> : "—"}
                                                    </td>
                                                    <td className={`px-4 py-3 text-sm font-bold text-right ${e.runningBalance > 0 ? "text-amber-500" : "text-green-500"}`}>
                                                        ₹{fmt(e.runningBalance)}
                                                    </td>
                                                </tr>
                                            ))
                                        )}
                                        <tr className="bg-white/5">
                                            <td className="px-4 py-3 text-sm text-muted-foreground">{fmtDate(ledger.toDate)}</td>
                                            <td className="px-4 py-3" />
                                            <td className="px-4 py-3 text-sm font-semibold text-foreground">Closing Balance</td>
                                            <td className="px-4 py-3 text-sm font-semibold text-right">₹{fmt(ledger.totalDebits)}</td>
                                            <td className="px-4 py-3 text-sm font-semibold text-right">₹{fmt(ledger.totalCredits)}</td>
                                            <td className={`px-4 py-3 text-sm font-bold text-right ${ledger.closingBalance > 0 ? "text-amber-500" : "text-green-500"}`}>
                                                ₹{fmt(ledger.closingBalance)}
                                            </td>
                                        </tr>
                                    </tbody>
                                </table>
                            </div>
                        </GlassCard>
                    </>
                ) : null}

                {/* Detail modal */}
                <Modal
                    isOpen={detail != null}
                    onClose={() => setDetail(null)}
                    title={detail?.kind === "STATEMENT" ? "Statement Details" : "Invoice Bill Details"}
                    size="lg"
                >
                    {detailLoading ? (
                        <div className="flex items-center justify-center py-12">
                            <div className="w-8 h-8 border-4 border-primary/20 border-t-primary rounded-full animate-spin" />
                        </div>
                    ) : stmtDetail ? (
                        <div className="space-y-5">
                            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                                <div>
                                    <p className="text-xs text-muted-foreground">Statement No</p>
                                    <p className="font-bold text-foreground">{stmtDetail.statement.statementNo}</p>
                                </div>
                                <div>
                                    <p className="text-xs text-muted-foreground">Period</p>
                                    <p className="text-sm font-medium text-foreground">
                                        {fmtDate(stmtDetail.statement.fromDate)} – {fmtDate(stmtDetail.statement.toDate)}
                                    </p>
                                </div>
                                <div>
                                    <p className="text-xs text-muted-foreground">Net Amount</p>
                                    <p className="font-bold text-foreground">₹{fmt(stmtDetail.statement.netAmount)}</p>
                                </div>
                                <div>
                                    <p className="text-xs text-muted-foreground">Status</p>
                                    <Badge variant={stmtDetail.statement.status === "PAID" ? "success" : "danger"} className="text-[10px]">
                                        {stmtDetail.statement.status.replace("_", " ")}
                                    </Badge>
                                </div>
                            </div>

                            <div>
                                <h4 className="text-sm font-semibold text-foreground mb-2 flex items-center gap-1.5">
                                    <FileText className="w-4 h-4 text-primary" /> Bills in this statement ({stmtDetail.bills.length})
                                </h4>
                                <div className="overflow-x-auto rounded-xl border border-border/50">
                                    <table className="w-full border-collapse text-sm">
                                        <thead>
                                            <tr className="bg-white/5">
                                                {["Date", "Bill No", "Vehicle", "Amount"].map((h) => (
                                                    <th key={h} className={`px-3 py-2 text-[10px] font-bold uppercase tracking-widest text-muted-foreground ${h === "Amount" ? "text-right" : "text-left"}`}>{h}</th>
                                                ))}
                                            </tr>
                                        </thead>
                                        <tbody className="divide-y divide-border/30">
                                            {stmtDetail.bills.map((b) => (
                                                <tr key={b.id}>
                                                    <td className="px-3 py-2 whitespace-nowrap">{fmtDate(b.date)}</td>
                                                    <td className="px-3 py-2 font-medium">{b.billNo || "—"}</td>
                                                    <td className="px-3 py-2">{b.vehicle?.vehicleNumber || "—"}</td>
                                                    <td className="px-3 py-2 text-right font-semibold">₹{fmt(b.netAmount)}</td>
                                                </tr>
                                            ))}
                                        </tbody>
                                    </table>
                                </div>
                            </div>

                            <div>
                                <h4 className="text-sm font-semibold text-foreground mb-2">Payments ({stmtDetail.payments.length})</h4>
                                {stmtDetail.payments.length === 0 ? (
                                    <p className="text-sm text-muted-foreground">No payments recorded against this statement.</p>
                                ) : (
                                    <ul className="space-y-1.5">
                                        {stmtDetail.payments.map((p) => (
                                            <li key={p.id} className="flex items-center justify-between text-sm bg-white/5 rounded-lg px-3 py-2">
                                                <span className="text-muted-foreground">
                                                    {fmtDate(p.paymentDate)} · {p.paymentMode}
                                                    {p.referenceNo ? ` · Ref ${p.referenceNo}` : ""}
                                                </span>
                                                <span className="font-semibold text-green-500">₹{fmt(p.amount)}</span>
                                            </li>
                                        ))}
                                    </ul>
                                )}
                            </div>
                        </div>
                    ) : billDetail ? (
                        <div className="space-y-5">
                            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                                <div>
                                    <p className="text-xs text-muted-foreground">Bill No</p>
                                    <p className="font-bold text-foreground">{billDetail.billNo || `#${billDetail.id}`}</p>
                                </div>
                                <div>
                                    <p className="text-xs text-muted-foreground">Date</p>
                                    <p className="text-sm font-medium text-foreground">{fmtDate(billDetail.date)}</p>
                                </div>
                                <div>
                                    <p className="text-xs text-muted-foreground">Vehicle / Driver</p>
                                    <p className="text-sm font-medium text-foreground">
                                        {billDetail.vehicle?.vehicleNumber || "—"}
                                        {billDetail.driverName ? ` · ${billDetail.driverName}` : ""}
                                    </p>
                                </div>
                                <div>
                                    <p className="text-xs text-muted-foreground">Status</p>
                                    <div className="flex items-center gap-1.5">
                                        <Badge variant={billDetail.billType === "CREDIT" ? "default" : "outline"} className="text-[10px]">{billDetail.billType}</Badge>
                                        {billDetail.billType === "CREDIT" && (
                                            <Badge variant={billDetail.paymentStatus === "PAID" ? "success" : "danger"} className="text-[10px]">
                                                {billDetail.paymentStatus === "PAID" ? "Paid" : "Unpaid"}
                                            </Badge>
                                        )}
                                    </div>
                                </div>
                            </div>

                            <div className="overflow-x-auto rounded-xl border border-border/50">
                                <table className="w-full border-collapse text-sm">
                                    <thead>
                                        <tr className="bg-white/5">
                                            {["Product", "Qty", "Rate", "Amount"].map((h) => (
                                                <th key={h} className={`px-3 py-2 text-[10px] font-bold uppercase tracking-widest text-muted-foreground ${h === "Product" ? "text-left" : "text-right"}`}>{h}</th>
                                            ))}
                                        </tr>
                                    </thead>
                                    <tbody className="divide-y divide-border/30">
                                        {(billDetail.products ?? []).map((p, i) => (
                                            <tr key={p.id ?? i}>
                                                <td className="px-3 py-2 font-medium">{p.productName}</td>
                                                <td className="px-3 py-2 text-right">{p.quantity}</td>
                                                <td className="px-3 py-2 text-right">₹{fmt(p.unitPrice)}</td>
                                                <td className="px-3 py-2 text-right font-semibold">₹{fmt(p.amount)}</td>
                                            </tr>
                                        ))}
                                        <tr className="bg-white/5">
                                            <td colSpan={3} className="px-3 py-2 text-right font-semibold">Net Amount</td>
                                            <td className="px-3 py-2 text-right font-bold">₹{fmt(billDetail.netAmount)}</td>
                                        </tr>
                                    </tbody>
                                </table>
                            </div>

                            {billDetail.statement && (
                                <p className="text-xs text-muted-foreground">
                                    Filed under statement <span className="font-semibold text-foreground">{billDetail.statement.statementNo}</span>
                                </p>
                            )}
                        </div>
                    ) : (
                        <p className="text-sm text-muted-foreground">Could not load details.</p>
                    )}
                </Modal>
            </div>
        </div>
    );
}
