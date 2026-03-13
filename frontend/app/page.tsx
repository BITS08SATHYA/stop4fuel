"use client";

import { useEffect, useState } from "react";
import { GlassCard } from "@/components/ui/glass-card";
import { getDashboardStats, DashboardStats } from "@/lib/api/station";
import {
    IndianRupee,
    Fuel,
    Activity,
    Landmark,
    Banknote,
    Smartphone,
    CreditCard,
    Wallet,
    Clock,
} from "lucide-react";

function formatCurrency(val?: number | null) {
    if (val == null) return "0.00";
    return val.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function formatDate(dt?: string | null) {
    if (!dt) return "-";
    return new Date(dt).toLocaleString("en-IN", {
        day: "2-digit", month: "short", hour: "2-digit", minute: "2-digit",
    });
}

function formatShiftTime(dt?: string | null) {
    if (!dt) return "-";
    return new Date(dt).toLocaleString("en-IN", {
        day: "2-digit", month: "short", year: "numeric",
        hour: "2-digit", minute: "2-digit",
    });
}

export default function DashboardPage() {
    const [stats, setStats] = useState<DashboardStats | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        getDashboardStats()
            .then(setStats)
            .catch((err) => setError(err.message || "Failed to load dashboard"))
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

    if (error || !stats) {
        return (
            <div className="min-h-screen bg-background p-8 flex flex-col items-center justify-center">
                <p className="text-red-500 mb-2">Failed to load dashboard</p>
                <p className="text-muted-foreground text-sm">{error}</p>
            </div>
        );
    }

    return (
        <div className="min-h-screen bg-background p-8 transition-colors duration-300">
            <div className="max-w-7xl mx-auto">
                {/* Header */}
                <div className="mb-8">
                    <h1 className="text-4xl font-bold text-foreground tracking-tight">
                        <span className="text-gradient">Dashboard</span>
                    </h1>
                    <p className="text-muted-foreground mt-2">
                        Real-time overview of your fuel station operations.
                    </p>
                </div>

                {/* Active Shift Banner */}
                {stats.activeShiftId && (
                    <div className="mb-6 rounded-2xl border border-green-500/20 bg-green-500/5 p-4">
                        <div className="flex items-center gap-3 mb-3">
                            <div className="w-2.5 h-2.5 rounded-full bg-green-500 animate-pulse" />
                            <span className="text-sm font-bold text-green-500">
                                Shift #{stats.activeShiftId}
                            </span>
                            <span className="text-xs text-muted-foreground">
                                Active since {formatShiftTime(stats.activeShiftStartTime)}
                            </span>
                        </div>
                        <div className="grid grid-cols-2 sm:grid-cols-5 gap-3">
                            <ShiftMiniStat label="Cash" value={stats.shiftCash} icon={Banknote} color="text-green-500 bg-green-500/10" />
                            <ShiftMiniStat label="UPI" value={stats.shiftUpi} icon={Smartphone} color="text-purple-500 bg-purple-500/10" />
                            <ShiftMiniStat label="Card" value={stats.shiftCard} icon={CreditCard} color="text-blue-500 bg-blue-500/10" />
                            <ShiftMiniStat label="Expense" value={stats.shiftExpense} icon={Wallet} color="text-red-500 bg-red-500/10" />
                            <ShiftMiniStat label="Net" value={stats.shiftNet} icon={IndianRupee} color="text-green-500 bg-green-500/10" />
                        </div>
                    </div>
                )}

                {/* KPI Stats Grid */}
                <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-4 mb-8">
                    {/* Today's Revenue */}
                    <GlassCard className="relative overflow-hidden">
                        <div className="flex justify-between items-start">
                            <div>
                                <p className="text-[10px] uppercase tracking-wider text-muted-foreground font-bold">Today&apos;s Revenue</p>
                                <h3 className="text-2xl font-bold text-green-500 mt-2">
                                    &#8377;{formatCurrency(stats.todayRevenue)}
                                </h3>
                                <p className="text-xs text-muted-foreground mt-1">
                                    {stats.todayInvoiceCount} invoices (Cash: {stats.todayCashInvoices}, Credit: {stats.todayCreditInvoices})
                                </p>
                            </div>
                            <div className="p-2 bg-green-500/10 rounded-lg">
                                <IndianRupee className="w-5 h-5 text-green-500" />
                            </div>
                        </div>
                    </GlassCard>

                    {/* Fuel Volume Sold */}
                    <GlassCard className="relative overflow-hidden">
                        <div className="flex justify-between items-start">
                            <div>
                                <p className="text-[10px] uppercase tracking-wider text-muted-foreground font-bold">Fuel Volume Sold</p>
                                <h3 className="text-2xl font-bold text-cyan-500 mt-2">
                                    {stats.todayFuelVolume.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })} L
                                </h3>
                                <p className="text-xs text-muted-foreground mt-1">
                                    Today&apos;s total fuel dispensed
                                </p>
                            </div>
                            <div className="p-2 bg-cyan-500/10 rounded-lg">
                                <Fuel className="w-5 h-5 text-cyan-500" />
                            </div>
                        </div>
                    </GlassCard>

                    {/* Active Station */}
                    <GlassCard className="relative overflow-hidden">
                        <div className="flex justify-between items-start">
                            <div>
                                <p className="text-[10px] uppercase tracking-wider text-muted-foreground font-bold">Active Station</p>
                                <h3 className="text-2xl font-bold text-blue-500 mt-2">
                                    {stats.activeNozzles}/{stats.totalNozzles} <span className="text-base font-medium text-muted-foreground">nozzles</span>
                                </h3>
                                <p className="text-xs text-muted-foreground mt-1">
                                    {stats.activePumps} pumps, {stats.activeTanks} tanks
                                </p>
                            </div>
                            <div className="p-2 bg-blue-500/10 rounded-lg">
                                <Activity className="w-5 h-5 text-blue-500" />
                            </div>
                        </div>
                    </GlassCard>

                    {/* Credit Outstanding */}
                    <GlassCard className="relative overflow-hidden">
                        <div className="flex justify-between items-start">
                            <div>
                                <p className="text-[10px] uppercase tracking-wider text-muted-foreground font-bold">Credit Outstanding</p>
                                <h3 className="text-2xl font-bold text-amber-500 mt-2">
                                    &#8377;{formatCurrency(stats.totalOutstanding)}
                                </h3>
                                <p className="text-xs text-muted-foreground mt-1">
                                    {stats.totalCreditCustomers} credit customers
                                </p>
                            </div>
                            <div className="p-2 bg-amber-500/10 rounded-lg">
                                <Landmark className="w-5 h-5 text-amber-500" />
                            </div>
                        </div>
                    </GlassCard>
                </div>

                {/* Recent Invoices */}
                <GlassCard className="overflow-hidden border-none p-0">
                    <div className="px-6 pt-6 pb-4">
                        <h3 className="text-lg font-semibold text-foreground">Recent Invoices</h3>
                        <p className="text-sm text-muted-foreground">Last 10 invoices generated today.</p>
                    </div>
                    <div className="overflow-x-auto">
                        <table className="w-full text-left border-collapse">
                            <thead>
                                <tr className="bg-white/5 border-b border-border/50">
                                    <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center w-12">#</th>
                                    <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Date</th>
                                    <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Customer</th>
                                    <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Type</th>
                                    <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right">Amount</th>
                                    <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Status</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-border/30">
                                {stats.recentInvoices.length === 0 ? (
                                    <tr>
                                        <td colSpan={6} className="px-6 py-12 text-center text-muted-foreground">
                                            No invoices today
                                        </td>
                                    </tr>
                                ) : (
                                    stats.recentInvoices.map((inv, idx) => (
                                        <tr key={inv.id} className="hover:bg-white/5 transition-colors">
                                            <td className="px-6 py-3 text-xs font-mono text-muted-foreground text-center">{idx + 1}</td>
                                            <td className="px-6 py-3 text-sm text-foreground">{formatDate(inv.date)}</td>
                                            <td className="px-6 py-3 text-sm text-foreground">{inv.customerName || "Walk-in"}</td>
                                            <td className="px-6 py-3">
                                                <span className={`px-2.5 py-1 rounded-full text-[10px] font-bold uppercase ${
                                                    inv.billType === "CASH"
                                                        ? "bg-green-500/10 text-green-500"
                                                        : "bg-amber-500/10 text-amber-500"
                                                }`}>
                                                    {inv.billType}
                                                </span>
                                            </td>
                                            <td className="px-6 py-3 text-sm font-bold text-foreground text-right">
                                                &#8377;{formatCurrency(inv.amount)}
                                            </td>
                                            <td className="px-6 py-3">
                                                <span className={`px-2.5 py-1 rounded-full text-[10px] font-bold uppercase ${
                                                    inv.paymentStatus === "PAID"
                                                        ? "bg-green-500/10 text-green-500"
                                                        : "bg-red-500/10 text-red-500"
                                                }`}>
                                                    {inv.paymentStatus === "PAID" ? "PAID" : "NOT PAID"}
                                                </span>
                                            </td>
                                        </tr>
                                    ))
                                )}
                            </tbody>
                        </table>
                    </div>
                </GlassCard>
            </div>
        </div>
    );
}

// --- Helper Component ---

function ShiftMiniStat({ label, value, icon: Icon, color }: { label: string; value: number | null; icon: any; color: string }) {
    return (
        <div className="flex items-center gap-3 p-3 rounded-xl border border-border bg-card">
            <div className={`p-2 rounded-lg ${color}`}>
                <Icon className="w-4 h-4" />
            </div>
            <div>
                <p className="text-[10px] uppercase tracking-wider text-muted-foreground">{label}</p>
                <p className="text-sm font-bold text-foreground">{formatCurrency(value)}</p>
            </div>
        </div>
    );
}
