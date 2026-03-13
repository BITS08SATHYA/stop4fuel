"use client";

import { useEffect, useState } from "react";
import { GlassCard } from "@/components/ui/glass-card";
import {
    getInvoices,
    getPayments,
    getCreditOverview,
    getOutstandingStatements,
    InvoiceBill,
    Payment,
    CreditOverview,
    Statement,
    PageResponse,
} from "@/lib/api/station";
import {
    IndianRupee,
    FileText,
    CreditCard,
    Receipt,
    AlertTriangle,
    Banknote,
    Clock,
    CheckCircle2,
    XCircle,
    CalendarDays,
    TrendingUp,
} from "lucide-react";

function formatCurrency(val?: number | null) {
    if (val == null) return "0.00";
    return val.toLocaleString("en-IN", {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
    });
}

function formatDate(dt?: string | null) {
    if (!dt) return "-";
    return new Date(dt).toLocaleString("en-IN", {
        day: "2-digit",
        month: "short",
        year: "numeric",
    });
}

function formatDateShort(dt?: string | null) {
    if (!dt) return "-";
    return new Date(dt).toLocaleString("en-IN", {
        day: "2-digit",
        month: "short",
    });
}

export default function InvoicePaymentDashboard() {
    const [invoices, setInvoices] = useState<InvoiceBill[]>([]);
    const [paymentsPage, setPaymentsPage] = useState<PageResponse<Payment> | null>(null);
    const [creditOverview, setCreditOverview] = useState<CreditOverview | null>(null);
    const [outstandingStatements, setOutstandingStatements] = useState<Statement[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        Promise.all([
            getInvoices(),
            getPayments(0, 15),
            getCreditOverview(),
            getOutstandingStatements(),
        ])
            .then(([inv, pay, credit, statements]) => {
                setInvoices(inv || []);
                setPaymentsPage(pay);
                setCreditOverview(credit);
                setOutstandingStatements(statements || []);
            })
            .catch((err) => setError(err.message || "Failed to load data"))
            .finally(() => setIsLoading(false));
    }, []);

    if (isLoading) {
        return (
            <div className="min-h-screen bg-background p-8 flex flex-col items-center justify-center">
                <div className="w-12 h-12 border-4 border-primary/20 border-t-primary rounded-full animate-spin mb-4" />
                <p className="text-muted-foreground animate-pulse">Loading dashboard...</p>
            </div>
        );
    }

    if (error) {
        return (
            <div className="min-h-screen bg-background p-8 flex flex-col items-center justify-center">
                <p className="text-red-500 mb-2">Failed to load dashboard</p>
                <p className="text-muted-foreground text-sm">{error}</p>
            </div>
        );
    }

    // --- KPI calculations ---
    const totalInvoices = invoices.length;
    const cashSales = invoices
        .filter((inv) => inv.billType === "CASH")
        .reduce((sum, inv) => sum + (inv.netAmount || 0), 0);
    const creditSales = invoices
        .filter((inv) => inv.billType === "CREDIT")
        .reduce((sum, inv) => sum + (inv.netAmount || 0), 0);
    const totalCollected = paymentsPage?.content?.reduce((sum, p) => sum + (p.amount || 0), 0) ?? 0;
    const totalOutstanding = creditOverview?.totalOutstanding ?? 0;

    // --- Invoice breakdown ---
    const now = new Date();
    const todayStr = now.toISOString().split("T")[0];
    const sevenDaysAgo = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);

    const todayInvoices = invoices.filter((inv) => inv.date?.startsWith(todayStr));
    const todayCount = todayInvoices.length;
    const todayTotal = todayInvoices.reduce((sum, inv) => sum + (inv.netAmount || 0), 0);

    const weekInvoices = invoices.filter((inv) => {
        if (!inv.date) return false;
        const d = new Date(inv.date);
        return d >= sevenDaysAgo && d <= now;
    });
    const weekCount = weekInvoices.length;
    const weekTotal = weekInvoices.reduce((sum, inv) => sum + (inv.netAmount || 0), 0);

    const paidCount = invoices.filter((inv) => inv.paymentStatus === "PAID").length;
    const unpaidCount = invoices.filter((inv) => inv.paymentStatus === "NOT_PAID").length;

    // --- Outstanding statements sorted by balance desc, top 10 ---
    const sortedStatements = [...outstandingStatements]
        .sort((a, b) => (b.balanceAmount || 0) - (a.balanceAmount || 0))
        .slice(0, 10);

    // --- Recent payments ---
    const recentPayments = paymentsPage?.content ?? [];

    // --- Aging data ---
    const agingData = [
        {
            label: "0-30 days",
            value: creditOverview?.totalAging0to30 ?? 0,
            color: "bg-green-500",
            textColor: "text-green-400",
        },
        {
            label: "31-60 days",
            value: creditOverview?.totalAging31to60 ?? 0,
            color: "bg-amber-500",
            textColor: "text-amber-400",
        },
        {
            label: "61-90 days",
            value: creditOverview?.totalAging61to90 ?? 0,
            color: "bg-orange-500",
            textColor: "text-orange-400",
        },
        {
            label: "90+ days",
            value: creditOverview?.totalAging90Plus ?? 0,
            color: "bg-red-500",
            textColor: "text-red-400",
        },
    ];
    const maxAging = Math.max(...agingData.map((a) => a.value), 1);

    return (
        <div className="min-h-screen bg-background p-8 transition-colors duration-300">
            <div className="max-w-7xl mx-auto">
                {/* Header */}
                <div className="mb-8">
                    <h1 className="text-4xl font-bold text-foreground tracking-tight">
                        <span className="text-gradient">Invoice & Payment Dashboard</span>
                    </h1>
                    <p className="text-muted-foreground mt-2">
                        Analytics overview of invoices, payments, and credit.
                    </p>
                </div>

                {/* Top KPI Row */}
                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-4 mb-8">
                    <KPICard
                        icon={<FileText className="w-5 h-5" />}
                        label="Total Invoices"
                        value={totalInvoices.toString()}
                        iconBg="bg-blue-500/10"
                        iconColor="text-blue-400"
                    />
                    <KPICard
                        icon={<Banknote className="w-5 h-5" />}
                        label="Cash Sales"
                        value={`₹${formatCurrency(cashSales)}`}
                        iconBg="bg-green-500/10"
                        iconColor="text-green-400"
                    />
                    <KPICard
                        icon={<CreditCard className="w-5 h-5" />}
                        label="Credit Sales"
                        value={`₹${formatCurrency(creditSales)}`}
                        iconBg="bg-purple-500/10"
                        iconColor="text-purple-400"
                    />
                    <KPICard
                        icon={<IndianRupee className="w-5 h-5" />}
                        label="Total Collected"
                        value={`₹${formatCurrency(totalCollected)}`}
                        iconBg="bg-emerald-500/10"
                        iconColor="text-emerald-400"
                    />
                    <KPICard
                        icon={<AlertTriangle className="w-5 h-5" />}
                        label="Outstanding"
                        value={`₹${formatCurrency(totalOutstanding)}`}
                        iconBg="bg-red-500/10"
                        iconColor="text-red-400"
                    />
                </div>

                {/* Credit Aging Breakdown */}
                <GlassCard className="mb-8">
                    <h2 className="text-lg font-semibold text-foreground mb-4 flex items-center gap-2">
                        <Clock className="w-5 h-5 text-muted-foreground" />
                        Credit Aging Breakdown
                    </h2>
                    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                        {agingData.map((item) => (
                            <div key={item.label} className="space-y-2">
                                <div className="flex items-center justify-between">
                                    <span className="text-sm text-muted-foreground">{item.label}</span>
                                    <span className={`text-sm font-semibold ${item.textColor}`}>
                                        ₹{formatCurrency(item.value)}
                                    </span>
                                </div>
                                <div className="w-full bg-muted/30 rounded-full h-3">
                                    <div
                                        className={`${item.color} h-3 rounded-full transition-all duration-500`}
                                        style={{ width: `${(item.value / maxAging) * 100}%` }}
                                    />
                                </div>
                            </div>
                        ))}
                    </div>
                </GlassCard>

                {/* Two-column layout: Outstanding Statements + Invoice Breakdown */}
                <div className="grid grid-cols-1 lg:grid-cols-3 gap-8 mb-8">
                    {/* Outstanding Statements Table */}
                    <GlassCard className="lg:col-span-2">
                        <h2 className="text-lg font-semibold text-foreground mb-4 flex items-center gap-2">
                            <Receipt className="w-5 h-5 text-muted-foreground" />
                            Outstanding Statements
                            <span className="text-xs text-muted-foreground font-normal ml-1">(Top 10)</span>
                        </h2>
                        {sortedStatements.length === 0 ? (
                            <p className="text-muted-foreground text-sm py-4 text-center">
                                No outstanding statements found.
                            </p>
                        ) : (
                            <div className="overflow-x-auto">
                                <table className="w-full text-sm">
                                    <thead>
                                        <tr className="border-b border-white/10 text-muted-foreground">
                                            <th className="text-left py-2 px-2 font-medium">Stmt #</th>
                                            <th className="text-left py-2 px-2 font-medium">Customer</th>
                                            <th className="text-left py-2 px-2 font-medium">Date Range</th>
                                            <th className="text-right py-2 px-2 font-medium">Total</th>
                                            <th className="text-right py-2 px-2 font-medium">Received</th>
                                            <th className="text-right py-2 px-2 font-medium">Balance</th>
                                            <th className="text-center py-2 px-2 font-medium">Status</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {sortedStatements.map((stmt) => (
                                            <tr
                                                key={stmt.id}
                                                className="border-b border-white/5 hover:bg-white/5 transition-colors"
                                            >
                                                <td className="py-2 px-2 font-mono text-foreground">
                                                    {stmt.statementNo}
                                                </td>
                                                <td className="py-2 px-2 text-foreground">
                                                    {stmt.customer?.name || "-"}
                                                </td>
                                                <td className="py-2 px-2 text-muted-foreground">
                                                    {formatDateShort(stmt.fromDate)} - {formatDateShort(stmt.toDate)}
                                                </td>
                                                <td className="py-2 px-2 text-right text-foreground">
                                                    ₹{formatCurrency(stmt.totalAmount)}
                                                </td>
                                                <td className="py-2 px-2 text-right text-foreground">
                                                    ₹{formatCurrency(stmt.receivedAmount)}
                                                </td>
                                                <td className="py-2 px-2 text-right font-semibold text-foreground">
                                                    ₹{formatCurrency(stmt.balanceAmount)}
                                                </td>
                                                <td className="py-2 px-2 text-center">
                                                    <StatusBadge status={stmt.status} />
                                                </td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                        )}
                    </GlassCard>

                    {/* Invoice Breakdown Summary */}
                    <GlassCard>
                        <h2 className="text-lg font-semibold text-foreground mb-4 flex items-center gap-2">
                            <TrendingUp className="w-5 h-5 text-muted-foreground" />
                            Invoice Breakdown
                        </h2>
                        <div className="space-y-5">
                            <div className="space-y-1">
                                <div className="flex items-center gap-2 text-sm text-muted-foreground">
                                    <CalendarDays className="w-4 h-4" />
                                    Today
                                </div>
                                <div className="flex items-center justify-between">
                                    <span className="text-foreground font-medium">
                                        {todayCount} invoice{todayCount !== 1 ? "s" : ""}
                                    </span>
                                    <span className="text-foreground font-semibold">
                                        ₹{formatCurrency(todayTotal)}
                                    </span>
                                </div>
                            </div>

                            <div className="border-t border-white/10" />

                            <div className="space-y-1">
                                <div className="flex items-center gap-2 text-sm text-muted-foreground">
                                    <CalendarDays className="w-4 h-4" />
                                    This Week (Last 7 Days)
                                </div>
                                <div className="flex items-center justify-between">
                                    <span className="text-foreground font-medium">
                                        {weekCount} invoice{weekCount !== 1 ? "s" : ""}
                                    </span>
                                    <span className="text-foreground font-semibold">
                                        ₹{formatCurrency(weekTotal)}
                                    </span>
                                </div>
                            </div>

                            <div className="border-t border-white/10" />

                            <div className="space-y-2">
                                <div className="text-sm text-muted-foreground">Paid vs Unpaid</div>
                                <div className="flex items-center gap-3">
                                    <div className="flex items-center gap-1.5">
                                        <CheckCircle2 className="w-4 h-4 text-green-400" />
                                        <span className="text-green-400 font-semibold">{paidCount}</span>
                                        <span className="text-muted-foreground text-xs">Paid</span>
                                    </div>
                                    <div className="flex items-center gap-1.5">
                                        <XCircle className="w-4 h-4 text-red-400" />
                                        <span className="text-red-400 font-semibold">{unpaidCount}</span>
                                        <span className="text-muted-foreground text-xs">Unpaid</span>
                                    </div>
                                </div>
                                {totalInvoices > 0 && (
                                    <div className="w-full bg-muted/30 rounded-full h-2.5 flex overflow-hidden">
                                        <div
                                            className="bg-green-500 h-2.5 transition-all duration-500"
                                            style={{
                                                width: `${(paidCount / totalInvoices) * 100}%`,
                                            }}
                                        />
                                        <div
                                            className="bg-red-500 h-2.5 transition-all duration-500"
                                            style={{
                                                width: `${(unpaidCount / totalInvoices) * 100}%`,
                                            }}
                                        />
                                    </div>
                                )}
                            </div>
                        </div>
                    </GlassCard>
                </div>

                {/* Recent Payments Table */}
                <GlassCard>
                    <h2 className="text-lg font-semibold text-foreground mb-4 flex items-center gap-2">
                        <IndianRupee className="w-5 h-5 text-muted-foreground" />
                        Recent Payments
                        <span className="text-xs text-muted-foreground font-normal ml-1">(Last 15)</span>
                    </h2>
                    {recentPayments.length === 0 ? (
                        <p className="text-muted-foreground text-sm py-4 text-center">
                            No payments recorded yet.
                        </p>
                    ) : (
                        <div className="overflow-x-auto">
                            <table className="w-full text-sm">
                                <thead>
                                    <tr className="border-b border-white/10 text-muted-foreground">
                                        <th className="text-left py-2 px-2 font-medium">Date</th>
                                        <th className="text-left py-2 px-2 font-medium">Customer</th>
                                        <th className="text-right py-2 px-2 font-medium">Amount</th>
                                        <th className="text-left py-2 px-2 font-medium">Payment Mode</th>
                                        <th className="text-left py-2 px-2 font-medium">Reference</th>
                                        <th className="text-left py-2 px-2 font-medium">Target</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {recentPayments.map((payment) => (
                                        <tr
                                            key={payment.id}
                                            className="border-b border-white/5 hover:bg-white/5 transition-colors"
                                        >
                                            <td className="py-2 px-2 text-foreground">
                                                {formatDate(payment.paymentDate)}
                                            </td>
                                            <td className="py-2 px-2 text-foreground">
                                                {payment.customer?.name || "-"}
                                            </td>
                                            <td className="py-2 px-2 text-right font-semibold text-foreground">
                                                ₹{formatCurrency(payment.amount)}
                                            </td>
                                            <td className="py-2 px-2 text-muted-foreground">
                                                {payment.paymentMode?.modeName || "-"}
                                            </td>
                                            <td className="py-2 px-2 text-muted-foreground font-mono">
                                                {payment.referenceNo || "-"}
                                            </td>
                                            <td className="py-2 px-2 text-muted-foreground">
                                                {payment.statement
                                                    ? `Stmt #${payment.statement.statementNo}`
                                                    : payment.invoiceBill
                                                    ? `Bill #${payment.invoiceBill.id}`
                                                    : "-"}
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    )}
                </GlassCard>
            </div>
        </div>
    );
}

// --- Sub-components ---

function KPICard({
    icon,
    label,
    value,
    iconBg,
    iconColor,
}: {
    icon: React.ReactNode;
    label: string;
    value: string;
    iconBg: string;
    iconColor: string;
}) {
    return (
        <GlassCard className="flex items-center gap-4">
            <div className={`p-3 rounded-xl ${iconBg} ${iconColor}`}>{icon}</div>
            <div>
                <p className="text-sm text-muted-foreground">{label}</p>
                <p className="text-xl font-bold text-foreground">{value}</p>
            </div>
        </GlassCard>
    );
}

function StatusBadge({ status }: { status: string }) {
    const isPaid = status === "PAID";
    return (
        <span
            className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium ${
                isPaid
                    ? "bg-green-500/10 text-green-400"
                    : "bg-red-500/10 text-red-400"
            }`}
        >
            {isPaid ? (
                <CheckCircle2 className="w-3 h-3" />
            ) : (
                <XCircle className="w-3 h-3" />
            )}
            {isPaid ? "Paid" : "Unpaid"}
        </span>
    );
}
