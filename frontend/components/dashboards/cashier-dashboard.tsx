"use client";

import { useEffect, useState } from "react";
import { GlassCard } from "@/components/ui/glass-card";
import { fetchWithAuth } from "@/lib/api/fetch-with-auth";
import {
    IndianRupee,
    Banknote,
    CreditCard,
    Wallet,
    Clock,
    Receipt,
    ArrowDownRight,
    ArrowUpRight,
    Loader2,
    AlertCircle,
} from "lucide-react";

const getApiBaseUrl = () => {
    if (typeof window !== "undefined") {
        return process.env.NEXT_PUBLIC_API_URL || `${window.location.protocol}//${window.location.hostname}:8080/api`;
    }
    return process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api";
};

interface CashierDashboardData {
    hasActiveShift: boolean;
    shiftId: number | null;
    shiftStatus: string | null;
    startTime: string | null;
    endTime: string | null;
    attendantName: string | null;
    cashBillTotal: number;
    creditBillTotal: number;
    totalInvoiceCount: number;
    cashInvoiceCount: number;
    creditInvoiceCount: number;
    eAdvanceTotals: Record<string, number>;
    billPaymentTotal: number;
    statementPaymentTotal: number;
    expenseTotal: number;
    operationalAdvanceTotal: number;
    operationalAdvanceCount: number;
    incentiveTotal: number;
    incentiveCount: number;
    cashInHand: number;
    recentInvoices: {
        id: number;
        billNo: string;
        billType: string;
        paymentMode: string;
        netAmount: number;
        date: string;
        customerName: string | null;
    }[];
}

function formatCurrency(val?: number | null) {
    if (val == null) return "0.00";
    return val.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function formatTime(dt?: string | null) {
    if (!dt) return "-";
    return new Date(dt).toLocaleString("en-IN", {
        hour: "2-digit", minute: "2-digit", hour12: true,
    });
}

function formatDateTime(dt?: string | null) {
    if (!dt) return "-";
    return new Date(dt).toLocaleString("en-IN", {
        day: "2-digit", month: "short", hour: "2-digit", minute: "2-digit",
    });
}

export function CashierDashboard() {
    const [data, setData] = useState<CashierDashboardData | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");

    useEffect(() => {
        const loadData = async () => {
            try {
                const res = await fetchWithAuth(`${getApiBaseUrl()}/dashboard/cashier`);
                if (res.ok) {
                    setData(await res.json());
                } else {
                    setError("Failed to load dashboard data");
                }
            } catch {
                setError("Failed to load dashboard data");
            } finally {
                setLoading(false);
            }
        };

        loadData();
        const interval = setInterval(loadData, 30000); // Refresh every 30s
        return () => clearInterval(interval);
    }, []);

    if (loading) {
        return (
            <div className="flex items-center justify-center min-h-[60vh]">
                <Loader2 className="w-8 h-8 animate-spin text-muted-foreground" />
            </div>
        );
    }

    if (error) {
        return (
            <div className="flex items-center justify-center min-h-[60vh]">
                <div className="text-center space-y-2">
                    <AlertCircle className="w-12 h-12 text-red-400 mx-auto" />
                    <p className="text-muted-foreground">{error}</p>
                </div>
            </div>
        );
    }

    if (!data?.hasActiveShift) {
        return (
            <div className="p-6">
                <div className="flex items-center justify-center min-h-[60vh]">
                    <div className="text-center space-y-4">
                        <Clock className="w-16 h-16 text-muted-foreground mx-auto" />
                        <h2 className="text-xl font-semibold text-foreground">No Active Shift</h2>
                        <p className="text-muted-foreground">
                            There is no active shift currently. Ask your manager to open a shift.
                        </p>
                    </div>
                </div>
            </div>
        );
    }

    const eAdv = data.eAdvanceTotals || {};
    const totalInflow = data.cashBillTotal + data.billPaymentTotal + data.statementPaymentTotal;
    const totalOutflow = data.expenseTotal + data.operationalAdvanceTotal + data.incentiveTotal;

    return (
        <div className="p-6 space-y-6">
            {/* Shift Info Header */}
            <div className="flex items-center justify-between">
                <div>
                    <h1 className="text-2xl font-bold text-foreground">Cashier Dashboard</h1>
                    <p className="text-sm text-muted-foreground">
                        Shift #{data.shiftId} &middot; Started {formatDateTime(data.startTime)}
                        {data.attendantName && ` \u00B7 ${data.attendantName}`}
                    </p>
                </div>
                <div className="flex items-center gap-2">
                    <span className="inline-flex items-center px-3 py-1 rounded-full text-sm font-medium bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400">
                        <Clock className="w-4 h-4 mr-1" />
                        {data.shiftStatus}
                    </span>
                </div>
            </div>

            {/* Key Metrics Row */}
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                <GlassCard className="p-4">
                    <div className="flex items-center gap-2 text-muted-foreground text-sm mb-1">
                        <Wallet className="w-4 h-4" />
                        Cash in Hand
                    </div>
                    <div className="text-2xl font-bold text-foreground">
                        <span className="text-sm text-muted-foreground mr-0.5">&#8377;</span>
                        {formatCurrency(data.cashInHand)}
                    </div>
                </GlassCard>

                <GlassCard className="p-4">
                    <div className="flex items-center gap-2 text-muted-foreground text-sm mb-1">
                        <Banknote className="w-4 h-4" />
                        Cash Bills
                    </div>
                    <div className="text-2xl font-bold text-foreground">
                        <span className="text-sm text-muted-foreground mr-0.5">&#8377;</span>
                        {formatCurrency(data.cashBillTotal)}
                    </div>
                    <div className="text-xs text-muted-foreground">{data.cashInvoiceCount} invoices</div>
                </GlassCard>

                <GlassCard className="p-4">
                    <div className="flex items-center gap-2 text-muted-foreground text-sm mb-1">
                        <CreditCard className="w-4 h-4" />
                        Credit Bills
                    </div>
                    <div className="text-2xl font-bold text-foreground">
                        <span className="text-sm text-muted-foreground mr-0.5">&#8377;</span>
                        {formatCurrency(data.creditBillTotal)}
                    </div>
                    <div className="text-xs text-muted-foreground">{data.creditInvoiceCount} invoices</div>
                </GlassCard>

                <GlassCard className="p-4">
                    <div className="flex items-center gap-2 text-muted-foreground text-sm mb-1">
                        <Receipt className="w-4 h-4" />
                        Total Invoices
                    </div>
                    <div className="text-2xl font-bold text-foreground">{data.totalInvoiceCount}</div>
                </GlassCard>
            </div>

            {/* Inflows & Outflows */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <GlassCard className="p-4">
                    <h3 className="font-semibold text-foreground mb-3 flex items-center gap-2">
                        <ArrowDownRight className="w-4 h-4 text-green-500" />
                        Inflows
                    </h3>
                    <div className="space-y-2">
                        <div className="flex justify-between text-sm">
                            <span className="text-muted-foreground">Cash Bills</span>
                            <span className="text-foreground font-medium">&#8377;{formatCurrency(data.cashBillTotal)}</span>
                        </div>
                        <div className="flex justify-between text-sm">
                            <span className="text-muted-foreground">Bill Payments</span>
                            <span className="text-foreground font-medium">&#8377;{formatCurrency(data.billPaymentTotal)}</span>
                        </div>
                        <div className="flex justify-between text-sm">
                            <span className="text-muted-foreground">Statement Payments</span>
                            <span className="text-foreground font-medium">&#8377;{formatCurrency(data.statementPaymentTotal)}</span>
                        </div>
                        <div className="border-t border-border pt-2 flex justify-between text-sm font-semibold">
                            <span className="text-foreground">Total Inflow</span>
                            <span className="text-green-600 dark:text-green-400">&#8377;{formatCurrency(totalInflow)}</span>
                        </div>
                    </div>
                </GlassCard>

                <GlassCard className="p-4">
                    <h3 className="font-semibold text-foreground mb-3 flex items-center gap-2">
                        <ArrowUpRight className="w-4 h-4 text-red-500" />
                        Outflows
                    </h3>
                    <div className="space-y-2">
                        <div className="flex justify-between text-sm">
                            <span className="text-muted-foreground">Expenses</span>
                            <span className="text-foreground font-medium">&#8377;{formatCurrency(data.expenseTotal)}</span>
                        </div>
                        <div className="flex justify-between text-sm">
                            <span className="text-muted-foreground">Op. Advances ({data.operationalAdvanceCount})</span>
                            <span className="text-foreground font-medium">&#8377;{formatCurrency(data.operationalAdvanceTotal)}</span>
                        </div>
                        <div className="flex justify-between text-sm">
                            <span className="text-muted-foreground">Incentives ({data.incentiveCount})</span>
                            <span className="text-foreground font-medium">&#8377;{formatCurrency(data.incentiveTotal)}</span>
                        </div>
                        <div className="border-t border-border pt-2 flex justify-between text-sm font-semibold">
                            <span className="text-foreground">Total Outflow</span>
                            <span className="text-red-600 dark:text-red-400">&#8377;{formatCurrency(totalOutflow)}</span>
                        </div>
                    </div>
                </GlassCard>
            </div>

            {/* E-Advance Breakdown */}
            {Object.keys(eAdv).length > 0 && (
                <GlassCard className="p-4">
                    <h3 className="font-semibold text-foreground mb-3 flex items-center gap-2">
                        <IndianRupee className="w-4 h-4" />
                        E-Advance Breakdown
                    </h3>
                    <div className="grid grid-cols-2 md:grid-cols-5 gap-3">
                        {Object.entries(eAdv)
                            .filter(([key]) => key !== "total")
                            .map(([key, val]) => (
                                <div key={key} className="text-center p-2 rounded-lg bg-muted/50">
                                    <div className="text-xs text-muted-foreground uppercase">{key.replace("_", " ")}</div>
                                    <div className="text-sm font-semibold text-foreground">&#8377;{formatCurrency(val)}</div>
                                </div>
                            ))}
                    </div>
                </GlassCard>
            )}

            {/* Recent Invoices */}
            <GlassCard className="p-4">
                <h3 className="font-semibold text-foreground mb-3">Recent Invoices</h3>
                {data.recentInvoices.length === 0 ? (
                    <p className="text-sm text-muted-foreground text-center py-4">No invoices in this shift yet</p>
                ) : (
                    <div className="overflow-x-auto">
                        <table className="w-full text-sm">
                            <thead>
                                <tr className="border-b border-border">
                                    <th className="text-left px-3 py-2 text-muted-foreground font-medium">Bill No</th>
                                    <th className="text-left px-3 py-2 text-muted-foreground font-medium">Type</th>
                                    <th className="text-left px-3 py-2 text-muted-foreground font-medium">Customer</th>
                                    <th className="text-right px-3 py-2 text-muted-foreground font-medium">Amount</th>
                                    <th className="text-right px-3 py-2 text-muted-foreground font-medium">Time</th>
                                </tr>
                            </thead>
                            <tbody>
                                {data.recentInvoices.map((inv) => (
                                    <tr key={inv.id} className="border-b border-border/50 hover:bg-muted/30">
                                        <td className="px-3 py-2 font-medium text-foreground">{inv.billNo}</td>
                                        <td className="px-3 py-2">
                                            <span className={`inline-flex px-1.5 py-0.5 rounded text-xs font-medium ${
                                                inv.billType === "CASH"
                                                    ? "bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400"
                                                    : "bg-orange-100 text-orange-800 dark:bg-orange-900/30 dark:text-orange-400"
                                            }`}>
                                                {inv.billType}
                                            </span>
                                        </td>
                                        <td className="px-3 py-2 text-foreground">{inv.customerName || "-"}</td>
                                        <td className="px-3 py-2 text-right text-foreground font-medium">
                                            &#8377;{formatCurrency(inv.netAmount)}
                                        </td>
                                        <td className="px-3 py-2 text-right text-muted-foreground">
                                            {formatTime(inv.date)}
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}
            </GlassCard>
        </div>
    );
}
