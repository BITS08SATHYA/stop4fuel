"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import {
    Bar, BarChart, CartesianGrid, LabelList, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from "recharts";
import { GlassCard } from "@/components/ui/glass-card";
import { Badge } from "@/components/ui/badge";
import { StatementVehicleTag } from "@/components/payments/StatementVehicleTag";
import { parseLocalDate } from "@/lib/utils";
import {
    getCustomerInvoices, getPaymentsByCustomer, getStatementsByCustomer, getCustomerConsumption,
    type InvoiceBill, type Payment, type Statement, type CustomerConsumption, type PageResponse,
} from "@/lib/api/station";
import { ChevronLeft, ChevronRight, History, TrendingUp } from "lucide-react";

const TOOLTIP_STYLE = {
    backgroundColor: "rgba(0,0,0,0.85)",
    border: "1px solid rgba(255,255,255,0.1)",
    borderRadius: "12px",
    fontSize: "12px",
};

type Tab = "invoices" | "payments" | "statements" | "consumption";

function formatAmount(v?: number | null) {
    if (v == null) return "0.00";
    return v.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function formatDate(d?: string) {
    if (!d) return "—";
    return new Date(d).toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "numeric" });
}

function formatMonth(ym: string) {
    return parseLocalDate(ym + "-01").toLocaleDateString("en-IN", { month: "short", year: "2-digit" });
}

function Pager({ page, totalPages, onPage }: { page: number; totalPages: number; onPage: (p: number) => void }) {
    if (totalPages <= 1) return null;
    return (
        <div className="flex items-center justify-end gap-2 px-4 py-3 border-t border-border/50">
            <button
                onClick={() => onPage(page - 1)}
                disabled={page <= 0}
                className="p-1.5 rounded-lg bg-muted hover:bg-muted/80 disabled:opacity-40 transition-colors"
                aria-label="Previous page"
            >
                <ChevronLeft className="w-4 h-4" />
            </button>
            <span className="text-xs text-muted-foreground">Page {page + 1} of {totalPages}</span>
            <button
                onClick={() => onPage(page + 1)}
                disabled={page >= totalPages - 1}
                className="p-1.5 rounded-lg bg-muted hover:bg-muted/80 disabled:opacity-40 transition-colors"
                aria-label="Next page"
            >
                <ChevronRight className="w-4 h-4" />
            </button>
        </div>
    );
}

export function CustomerTransactionHistory({ customerId }: { customerId: number }) {
    const [tab, setTab] = useState<Tab>("invoices");
    const [loading, setLoading] = useState(false);

    const [invoices, setInvoices] = useState<PageResponse<InvoiceBill> | null>(null);
    const [invoicePage, setInvoicePage] = useState(0);
    const [payments, setPayments] = useState<PageResponse<Payment> | null>(null);
    const [paymentPage, setPaymentPage] = useState(0);
    const [statements, setStatements] = useState<Statement[] | null>(null);
    const [consumption, setConsumption] = useState<CustomerConsumption | null>(null);

    useEffect(() => {
        setLoading(true);
        const done = () => setLoading(false);
        if (tab === "invoices") {
            getCustomerInvoices(customerId, invoicePage, 10).then(setInvoices).catch(() => setInvoices(null)).finally(done);
        } else if (tab === "payments") {
            getPaymentsByCustomer(customerId, paymentPage, 10).then(setPayments).catch(() => setPayments(null)).finally(done);
        } else if (tab === "statements") {
            getStatementsByCustomer(customerId).then(setStatements).catch(() => setStatements(null)).finally(done);
        } else {
            getCustomerConsumption(customerId, 12).then(setConsumption).catch(() => setConsumption(null)).finally(done);
        }
    }, [tab, customerId, invoicePage, paymentPage]);

    const th = "px-4 py-3 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-left";
    const td = "px-4 py-3 text-sm";

    const monthly = consumption?.monthly.map((m) => ({ month: formatMonth(m.month), quantity: m.quantity, amount: m.amount })) ?? [];
    const avgQty = monthly.length > 0 ? monthly.reduce((s, m) => s + m.quantity, 0) / monthly.length : 0;

    return (
        <GlassCard className="overflow-hidden border-none p-0">
            <div className="px-6 py-4 border-b border-border/50 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
                <div className="flex items-center gap-2">
                    <div className="p-2 bg-primary/10 rounded-lg">
                        <History className="w-5 h-5 text-primary" />
                    </div>
                    <div>
                        <h2 className="text-lg font-semibold text-foreground">Transaction History</h2>
                        <p className="text-xs text-muted-foreground">Invoices, payments, statements and 12-month consumption</p>
                    </div>
                </div>
                <div className="flex flex-wrap gap-2">
                    {(
                        [
                            ["invoices", "Invoices"],
                            ["payments", "Payments"],
                            ["statements", "Statements"],
                            ["consumption", "Consumption"],
                        ] as [Tab, string][]
                    ).map(([t, label]) => (
                        <button
                            key={t}
                            onClick={() => setTab(t)}
                            className={`px-3 py-1.5 text-xs font-medium rounded-lg transition-colors ${
                                tab === t ? "bg-primary text-primary-foreground" : "bg-muted text-muted-foreground hover:bg-muted/80"
                            }`}
                        >
                            {label}
                        </button>
                    ))}
                </div>
            </div>

            {loading ? (
                <div className="flex items-center justify-center py-12">
                    <div className="w-8 h-8 border-4 border-primary/20 border-t-primary rounded-full animate-spin" />
                </div>
            ) : tab === "invoices" ? (
                <div className="overflow-x-auto">
                    <table className="w-full border-collapse">
                        <thead>
                            <tr className="bg-white/5 border-b border-border/50">
                                {["Date", "Bill No", "Type", "Vehicle", "Driver", "Amount", "Status"].map((h) => <th key={h} className={th}>{h}</th>)}
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-border/30">
                            {!invoices || invoices.content.length === 0 ? (
                                <tr><td colSpan={7} className="px-4 py-10 text-center text-sm text-muted-foreground">No invoices found</td></tr>
                            ) : (
                                invoices.content.map((b) => (
                                    <tr key={b.id} className="hover:bg-white/5 transition-colors">
                                        <td className={td}>{formatDate(b.date)}</td>
                                        <td className={`${td} font-medium`}>{b.billNo || "—"}</td>
                                        <td className={td}>
                                            <Badge variant={b.billType === "CREDIT" ? "default" : "outline"} className="text-[9px]">{b.billType}</Badge>
                                        </td>
                                        <td className={td}>{b.vehicle?.vehicleNumber || "—"}</td>
                                        <td className={td}>
                                            {b.driverName || "—"}
                                            {b.driverPhone && <span className="block text-xs text-muted-foreground">{b.driverPhone}</span>}
                                        </td>
                                        <td className={`${td} font-semibold`}>&#8377;{formatAmount(b.netAmount)}</td>
                                        <td className={td}>
                                            {b.billType === "CREDIT" ? (
                                                <Badge variant={b.paymentStatus === "PAID" ? "success" : "danger"} className="text-[9px]">
                                                    {b.paymentStatus === "PAID" ? "Paid" : "Unpaid"}
                                                </Badge>
                                            ) : (
                                                <span className="text-xs text-muted-foreground">—</span>
                                            )}
                                        </td>
                                    </tr>
                                ))
                            )}
                        </tbody>
                    </table>
                    {invoices && <Pager page={invoices.number} totalPages={invoices.totalPages} onPage={setInvoicePage} />}
                </div>
            ) : tab === "payments" ? (
                <div className="overflow-x-auto">
                    <table className="w-full border-collapse">
                        <thead>
                            <tr className="bg-white/5 border-b border-border/50">
                                {["Date", "Amount", "Mode", "Against", "Reference", "Received By"].map((h) => <th key={h} className={th}>{h}</th>)}
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-border/30">
                            {!payments || payments.content.length === 0 ? (
                                <tr><td colSpan={6} className="px-4 py-10 text-center text-sm text-muted-foreground">No payments found</td></tr>
                            ) : (
                                payments.content.map((p) => (
                                    <tr key={p.id} className="hover:bg-white/5 transition-colors">
                                        <td className={td}>{formatDate(p.paymentDate)}</td>
                                        <td className={`${td} font-semibold text-green-500`}>&#8377;{formatAmount(p.amount)}</td>
                                        <td className={td}><Badge variant="outline" className="text-[9px]">{p.paymentMode}</Badge></td>
                                        <td className={td}>{p.statement ? `Statement ${p.statement.statementNo}` : p.invoiceBill ? `Bill ${p.invoiceBill.billNo}` : "On account"}</td>
                                        <td className={td}>{p.referenceNo || "—"}</td>
                                        <td className={td}>{p.receivedBy?.name || "—"}</td>
                                    </tr>
                                ))
                            )}
                        </tbody>
                    </table>
                    {payments && <Pager page={payments.number} totalPages={payments.totalPages} onPage={setPaymentPage} />}
                </div>
            ) : tab === "statements" ? (
                <div className="overflow-x-auto">
                    <table className="w-full border-collapse">
                        <thead>
                            <tr className="bg-white/5 border-b border-border/50">
                                {["Statement", "Period", "Bills", "Net Amount", "Received", "Balance", "Status"].map((h) => <th key={h} className={th}>{h}</th>)}
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-border/30">
                            {!statements || statements.length === 0 ? (
                                <tr><td colSpan={7} className="px-4 py-10 text-center text-sm text-muted-foreground">No statements found</td></tr>
                            ) : (
                                statements.map((s) => (
                                    <tr key={s.id} className="hover:bg-white/5 transition-colors">
                                        <td className={`${td} font-medium`}>
                                            <div className="flex items-center gap-2 flex-wrap">
                                                <span>{s.statementNo}</span>
                                                <StatementVehicleTag vehicleNumbers={s.vehicleNumbers} />
                                            </div>
                                        </td>
                                        <td className={td}>{formatDate(s.fromDate)} – {formatDate(s.toDate)}</td>
                                        <td className={td}>{s.numberOfBills}</td>
                                        <td className={`${td} font-semibold`}>&#8377;{formatAmount(s.netAmount)}</td>
                                        <td className={td}>&#8377;{formatAmount(s.receivedAmount)}</td>
                                        <td className={`${td} ${s.balanceAmount > 0 ? "text-amber-500 font-semibold" : ""}`}>&#8377;{formatAmount(s.balanceAmount)}</td>
                                        <td className={td}>
                                            <Badge variant={s.status === "PAID" ? "success" : s.status === "DRAFT" ? "outline" : "danger"} className="text-[9px]">
                                                {s.status.replace("_", " ")}
                                            </Badge>
                                        </td>
                                    </tr>
                                ))
                            )}
                        </tbody>
                    </table>
                </div>
            ) : (
                <div className="p-6">
                    {monthly.length === 0 ? (
                        <p className="text-sm text-muted-foreground">No consumption recorded in the last 12 months.</p>
                    ) : (
                        <>
                            <div className="flex items-center gap-2 mb-3">
                                <TrendingUp className="w-4 h-4 text-primary" />
                                <p className="text-xs text-muted-foreground">Monthly quantity; dashed line is the 12-month average</p>
                            </div>
                            <div className="h-64">
                                <ResponsiveContainer width="100%" height="100%">
                                    <BarChart data={monthly} margin={{ top: 24, right: 16, left: 0, bottom: 0 }}>
                                        <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" vertical={false} />
                                        <XAxis dataKey="month" tick={{ fontSize: 11 }} stroke="#888" />
                                        <YAxis tick={{ fontSize: 11 }} stroke="#888" />
                                        <Tooltip
                                            contentStyle={TOOLTIP_STYLE}
                                            formatter={(v, _name, item) => {
                                                const amount = (item?.payload as { amount?: number } | undefined)?.amount ?? 0;
                                                return [`${Number(v).toLocaleString("en-IN")} qty · ₹${formatAmount(amount)}`, "Consumed"];
                                            }}
                                        />
                                        <ReferenceLine y={avgQty} stroke="#a1a1aa" strokeDasharray="6 4" />
                                        <Bar dataKey="quantity" fill="#f97316" radius={[6, 6, 0, 0]} maxBarSize={36}>
                                            <LabelList
                                                dataKey="quantity"
                                                position="top"
                                                fontSize={10}
                                                fill="#a1a1aa"
                                                formatter={(v) => (Number(v) > 0 ? Number(v).toLocaleString("en-IN", { maximumFractionDigits: 0 }) : "")}
                                            />
                                        </Bar>
                                    </BarChart>
                                </ResponsiveContainer>
                            </div>
                        </>
                    )}
                </div>
            )}

            <div className="px-6 py-3 border-t border-border/50 flex flex-wrap gap-x-6 gap-y-1">
                <Link href={`/customers/${customerId}/transactions`} className="text-xs font-medium text-primary hover:underline">
                    All transactions by year →
                </Link>
                <Link href={`/payments/ledger?customerId=${customerId}`} className="text-xs font-medium text-primary hover:underline">
                    View full ledger with running balance →
                </Link>
            </div>
        </GlassCard>
    );
}
