"use client";

import { useEffect, useState } from "react";
import { GlassCard } from "@/components/ui/glass-card";
import {
    getMyDashboard,
    CustomerDashboardData,
} from "@/lib/api/station";
import {
    IndianRupee,
    Receipt,
    CreditCard,
    Truck,
    Loader2,
    AlertCircle,
    TrendingUp,
} from "lucide-react";

function formatCurrency(val?: number | null) {
    if (val == null) return "0.00";
    return val.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function formatDate(dt?: string | null) {
    if (!dt) return "-";
    return new Date(dt).toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "numeric" });
}

function formatDateTime(dt?: string | null) {
    if (!dt) return "-";
    return new Date(dt).toLocaleString("en-IN", { day: "2-digit", month: "short", hour: "2-digit", minute: "2-digit" });
}

export function CustomerDashboard() {
    const [data, setData] = useState<CustomerDashboardData | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");

    useEffect(() => {
        getMyDashboard()
            .then(setData)
            .catch((err) => setError(err.message || "Failed to load dashboard"))
            .finally(() => setLoading(false));
    }, []);

    if (loading) {
        return (
            <div className="flex items-center justify-center h-[60vh]">
                <Loader2 className="w-8 h-8 animate-spin text-primary" />
            </div>
        );
    }

    if (error || !data) {
        return (
            <div className="flex items-center justify-center h-[60vh]">
                <GlassCard className="p-8 text-center max-w-md">
                    <AlertCircle className="w-10 h-10 text-red-500 mx-auto mb-3" />
                    <p className="text-sm text-muted-foreground">{error || "No data available"}</p>
                </GlassCard>
            </div>
        );
    }

    return (
        <div className="space-y-6">
            {/* KPI Cards */}
            <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
                <GlassCard className="p-4">
                    <div className="flex items-center gap-3">
                        <div className="p-2 bg-red-500/10 rounded-lg">
                            <IndianRupee className="w-5 h-5 text-red-500" />
                        </div>
                        <div>
                            <p className="text-[10px] uppercase tracking-wider text-muted-foreground font-bold">Outstanding</p>
                            <p className="text-2xl font-bold text-red-500 mt-1">{formatCurrency(data.outstandingBalance)}</p>
                        </div>
                    </div>
                </GlassCard>

                <GlassCard className="p-4">
                    <div className="flex items-center gap-3">
                        <div className="p-2 bg-amber-500/10 rounded-lg">
                            <TrendingUp className="w-5 h-5 text-amber-500" />
                        </div>
                        <div>
                            <p className="text-[10px] uppercase tracking-wider text-muted-foreground font-bold">Credit Used</p>
                            <p className="text-2xl font-bold text-amber-500 mt-1">{data.creditUtilization?.toFixed(1) || 0}%</p>
                            <p className="text-xs text-muted-foreground">
                                of {formatCurrency(data.creditLimitAmount)}
                            </p>
                        </div>
                    </div>
                </GlassCard>

                <GlassCard className="p-4">
                    <div className="flex items-center gap-3">
                        <div className="p-2 bg-blue-500/10 rounded-lg">
                            <Receipt className="w-5 h-5 text-blue-500" />
                        </div>
                        <div>
                            <p className="text-[10px] uppercase tracking-wider text-muted-foreground font-bold">Unpaid Statements</p>
                            <p className="text-2xl font-bold text-blue-500 mt-1">{data.unpaidStatements}</p>
                            <p className="text-xs text-muted-foreground">{formatCurrency(data.unpaidStatementsAmount)}</p>
                        </div>
                    </div>
                </GlassCard>

                <GlassCard className="p-4">
                    <div className="flex items-center gap-3">
                        <div className="p-2 bg-green-500/10 rounded-lg">
                            <Truck className="w-5 h-5 text-green-500" />
                        </div>
                        <div>
                            <p className="text-[10px] uppercase tracking-wider text-muted-foreground font-bold">Vehicles</p>
                            <p className="text-2xl font-bold text-green-500 mt-1">{data.vehicleCount}</p>
                            <p className="text-xs text-muted-foreground">
                                {data.consumedLiters?.toFixed(0) || 0}L consumed
                            </p>
                        </div>
                    </div>
                </GlassCard>
            </div>

            {/* Credit Utilization Bar */}
            <GlassCard className="p-5">
                <h3 className="text-sm font-bold mb-3">Credit Utilization</h3>
                <div className="w-full bg-muted rounded-full h-3">
                    <div
                        className={`h-3 rounded-full transition-all ${
                            (data.creditUtilization || 0) > 80 ? "bg-red-500" :
                            (data.creditUtilization || 0) > 50 ? "bg-amber-500" : "bg-green-500"
                        }`}
                        style={{ width: `${Math.min(data.creditUtilization || 0, 100)}%` }}
                    />
                </div>
                <div className="flex justify-between text-xs text-muted-foreground mt-2">
                    <span>Used: {formatCurrency(data.outstandingBalance)}</span>
                    <span>Limit: {formatCurrency(data.creditLimitAmount)}</span>
                </div>
            </GlassCard>

            {/* Recent Invoices & Payments */}
            <div className="grid md:grid-cols-2 gap-6">
                <GlassCard className="p-5">
                    <h3 className="text-sm font-bold mb-3 flex items-center gap-2">
                        <Receipt className="w-4 h-4 text-muted-foreground" />
                        Recent Invoices
                    </h3>
                    {data.recentInvoices.length === 0 ? (
                        <p className="text-sm text-muted-foreground">No invoices</p>
                    ) : (
                        <div className="space-y-2">
                            {data.recentInvoices.map((inv) => (
                                <div key={inv.id} className="flex items-center justify-between border-b border-border/50 py-2">
                                    <div>
                                        <p className="text-sm font-medium">{inv.billNo}</p>
                                        <p className="text-xs text-muted-foreground">
                                            {formatDateTime(inv.date)}
                                            {inv.vehicleNumber && ` · ${inv.vehicleNumber}`}
                                        </p>
                                    </div>
                                    <div className="text-right">
                                        <p className="text-sm font-medium">{formatCurrency(inv.netAmount)}</p>
                                        <span className={`text-xs ${inv.paymentStatus === "PAID" ? "text-green-500" : "text-amber-500"}`}>
                                            {inv.paymentStatus}
                                        </span>
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}
                </GlassCard>

                <GlassCard className="p-5">
                    <h3 className="text-sm font-bold mb-3 flex items-center gap-2">
                        <CreditCard className="w-4 h-4 text-muted-foreground" />
                        Recent Payments
                    </h3>
                    {data.recentPayments.length === 0 ? (
                        <p className="text-sm text-muted-foreground">No payments</p>
                    ) : (
                        <div className="space-y-2">
                            {data.recentPayments.map((p) => (
                                <div key={p.id} className="flex items-center justify-between border-b border-border/50 py-2">
                                    <div>
                                        <p className="text-sm font-medium">{formatCurrency(p.amount)}</p>
                                        <p className="text-xs text-muted-foreground">
                                            {formatDateTime(p.date)}
                                        </p>
                                    </div>
                                    <div className="text-right">
                                        <span className="text-xs bg-primary/10 text-primary px-2 py-0.5 rounded">
                                            {p.paymentMode || "N/A"}
                                        </span>
                                        {p.referenceNo && (
                                            <p className="text-xs text-muted-foreground mt-0.5">{p.referenceNo}</p>
                                        )}
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}
                </GlassCard>
            </div>
        </div>
    );
}
