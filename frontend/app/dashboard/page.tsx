"use client";

import { useEffect, useState } from "react";
import { useAuth, getDashboardType } from "@/lib/auth/auth-context";
import { CashierDashboard } from "@/components/dashboards/cashier-dashboard";
import { CustomerDashboard } from "@/components/dashboards/customer-dashboard";
import { EmployeeDashboard } from "@/components/dashboards/employee-dashboard";
import { GlassCard } from "@/components/ui/glass-card";
import { getDashboardStats, DashboardStats, checkStockAlerts, StockAlert } from "@/lib/api/station";
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
    Droplets,
    TrendingUp,
    BarChart3,
    ArrowUpRight,
    ArrowDownRight,
    AlertTriangle,
} from "lucide-react";
import {
    AreaChart,
    Area,
    XAxis,
    YAxis,
    CartesianGrid,
    Tooltip,
    ResponsiveContainer,
    BarChart,
    Bar,
    Cell,
    PieChart,
    Pie,
} from "recharts";

function formatCurrency(val?: number | null) {
    if (val == null) return "0.00";
    return val.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function formatCompact(val?: number | null) {
    if (val == null) return "0";
    if (val >= 100000) return (val / 100000).toFixed(1) + "L";
    if (val >= 1000) return (val / 1000).toFixed(1) + "K";
    return val.toLocaleString("en-IN", { maximumFractionDigits: 0 });
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

function formatShortDate(dateStr: string) {
    const d = new Date(dateStr);
    return d.toLocaleDateString("en-IN", { day: "2-digit", month: "short" });
}

const PRODUCT_COLORS = ["#f97316", "#06b6d4", "#8b5cf6", "#10b981", "#ef4444", "#eab308", "#ec4899"];

export default function DashboardPage() {
    const { user } = useAuth();
    const dashboardType = getDashboardType(user?.designation, user?.role);

    if (dashboardType === "customer") {
        return <CustomerDashboard />;
    }

    if (dashboardType === "cashier") {
        return <CashierDashboard />;
    }

    if (dashboardType === "employee") {
        return <EmployeeDashboard />;
    }

    return <OwnerDashboard />;
}

function OwnerDashboard() {
    const [stats, setStats] = useState<DashboardStats | null>(null);
    const [stockAlerts, setStockAlerts] = useState<StockAlert[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        getDashboardStats()
            .then(setStats)
            .catch((err) => setError(err.message || "Failed to load dashboard"))
            .finally(() => setIsLoading(false));
        checkStockAlerts().then(setStockAlerts).catch(() => {});
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

    // Compute yesterday comparison
    const yesterdayData = stats.dailyRevenue?.length >= 2 ? stats.dailyRevenue[stats.dailyRevenue.length - 2] : null;
    const revenueChange = yesterdayData && yesterdayData.revenue > 0
        ? ((stats.todayRevenue - yesterdayData.revenue) / yesterdayData.revenue) * 100
        : null;
    const volumeChange = yesterdayData && yesterdayData.fuelVolume > 0
        ? ((stats.todayFuelVolume - yesterdayData.fuelVolume) / yesterdayData.fuelVolume) * 100
        : null;

    // Chart data
    const chartData = (stats.dailyRevenue || []).map(d => ({
        date: formatShortDate(d.date),
        revenue: d.revenue,
        volume: d.fuelVolume,
        invoices: d.invoiceCount,
    }));

    // Credit aging data
    const agingData = [
        { label: "0-30d", value: stats.creditAging0to30 || 0, color: "#10b981" },
        { label: "31-60d", value: stats.creditAging31to60 || 0, color: "#eab308" },
        { label: "61-90d", value: stats.creditAging61to90 || 0, color: "#f97316" },
        { label: "90+d", value: stats.creditAging90Plus || 0, color: "#ef4444" },
    ];
    const maxAging = Math.max(...agingData.map(a => a.value), 1);

    return (
        <div className="min-h-screen bg-background p-6 md:p-8 transition-colors duration-300">
            <div className="max-w-7xl mx-auto space-y-6">
                {/* Header + Fuel Prices */}
                <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
                    <div>
                        <h1 className="text-4xl font-bold text-foreground tracking-tight">
                            <span className="text-gradient">Dashboard</span>
                        </h1>
                        <p className="text-muted-foreground mt-1">
                            Real-time overview of your fuel station operations.
                        </p>
                    </div>
                    {/* Fuel Prices */}
                    {stats.tankStatuses.length > 0 && (() => {
                        const seen = new Set<string>();
                        const fuelPrices = stats.tankStatuses.filter(t => {
                            if (!t.productName || !t.productPrice || seen.has(t.productName)) return false;
                            seen.add(t.productName);
                            return true;
                        });
                        if (fuelPrices.length === 0) return null;
                        return (
                            <div className="flex items-center gap-3">
                                <Fuel className="w-4 h-4 text-muted-foreground hidden sm:block" />
                                {fuelPrices.map((f, i) => (
                                    <div key={f.productName} className="flex items-center gap-2">
                                        {i > 0 && <div className="w-px h-8 bg-border hidden sm:block" />}
                                        <div className="text-center px-3 py-1.5 rounded-xl bg-card border border-border">
                                            <p className="text-[10px] uppercase tracking-wider text-muted-foreground font-medium">{f.productName}</p>
                                            <p className="text-sm font-bold text-foreground">&#8377;{f.productPrice?.toFixed(2)}</p>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        );
                    })()}
                </div>

                {/* Low Stock Alert Banner */}
                {stockAlerts.length > 0 && (
                    <div className="bg-red-500/10 border border-red-500/30 rounded-xl p-4 flex items-center gap-3">
                        <AlertTriangle className="w-5 h-5 text-red-500 shrink-0" />
                        <div className="flex-1">
                            <span className="font-semibold text-red-500">Low Stock Alert:</span>
                            <span className="text-sm text-foreground ml-2">
                                {stockAlerts.map(a => `${a.tank.name} (${a.availableStock.toLocaleString()} L)`).join(", ")}
                                {" "}&mdash; below threshold level
                            </span>
                        </div>
                        <a
                            href="/operations/dashboard"
                            className="text-xs font-medium text-red-500 hover:text-red-400 underline whitespace-nowrap"
                        >
                            View Details
                        </a>
                    </div>
                )}

                {/* Active Shift Banner */}
                {stats.activeShiftId && (
                    <div className="rounded-2xl border border-green-500/20 bg-green-500/5 p-4">
                        <div className="flex items-center gap-3 mb-3">
                            <div className="w-2.5 h-2.5 rounded-full bg-green-500 animate-pulse" />
                            <span className="text-sm font-bold text-green-500">
                                Shift #{stats.activeShiftId}
                            </span>
                            <span className="text-xs text-muted-foreground">
                                Active since {formatShiftTime(stats.activeShiftStartTime)}
                            </span>
                        </div>
                        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
                            <ShiftMiniStat label="Cash" value={stats.shiftCash} icon={Banknote} color="text-green-500 bg-green-500/10" />
                            <ShiftMiniStat label="UPI" value={stats.shiftUpi} icon={Smartphone} color="text-purple-500 bg-purple-500/10" />
                            <ShiftMiniStat label="Card" value={stats.shiftCard} icon={CreditCard} color="text-blue-500 bg-blue-500/10" />
                            <ShiftMiniStat label="Expense" value={stats.shiftExpense} icon={Wallet} color="text-red-500 bg-red-500/10" />
                            <ShiftMiniStat label="Total" value={stats.shiftTotal} icon={IndianRupee} color="text-cyan-500 bg-cyan-500/10" />
                            <ShiftMiniStat label="Net" value={stats.shiftNet} icon={IndianRupee} color="text-green-600 bg-green-600/10" />
                        </div>
                    </div>
                )}

                {/* KPI Stats Grid */}
                <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
                    {/* Today's Revenue */}
                    <GlassCard className="relative overflow-hidden">
                        <div className="flex justify-between items-start">
                            <div>
                                <p className="text-[10px] uppercase tracking-wider text-muted-foreground font-bold">Today&apos;s Revenue</p>
                                <h3 className="text-2xl font-bold text-green-500 mt-2">
                                    &#8377;{formatCurrency(stats.todayRevenue)}
                                </h3>
                                <div className="flex items-center gap-2 mt-1">
                                    <p className="text-xs text-muted-foreground">
                                        {stats.todayInvoiceCount} invoices
                                    </p>
                                    {revenueChange !== null && (
                                        <span className={`text-[10px] font-medium flex items-center gap-0.5 ${revenueChange >= 0 ? "text-green-500" : "text-red-500"}`}>
                                            {revenueChange >= 0 ? <ArrowUpRight className="w-3 h-3" /> : <ArrowDownRight className="w-3 h-3" />}
                                            {Math.abs(revenueChange).toFixed(1)}%
                                        </span>
                                    )}
                                </div>
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
                                <div className="flex items-center gap-2 mt-1">
                                    <p className="text-xs text-muted-foreground">Today&apos;s total</p>
                                    {volumeChange !== null && (
                                        <span className={`text-[10px] font-medium flex items-center gap-0.5 ${volumeChange >= 0 ? "text-green-500" : "text-red-500"}`}>
                                            {volumeChange >= 0 ? <ArrowUpRight className="w-3 h-3" /> : <ArrowDownRight className="w-3 h-3" />}
                                            {Math.abs(volumeChange).toFixed(1)}%
                                        </span>
                                    )}
                                </div>
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

                {/* Charts Row: Revenue Trend + Product Sales */}
                <div className="grid gap-6 lg:grid-cols-3">
                    {/* Revenue Trend - 7 Day */}
                    <GlassCard className="lg:col-span-2">
                        <div className="flex items-center justify-between mb-4">
                            <div>
                                <h3 className="text-lg font-semibold text-foreground flex items-center gap-2">
                                    <TrendingUp className="w-5 h-5 text-muted-foreground" />
                                    Revenue Trend
                                </h3>
                                <p className="text-xs text-muted-foreground">Last 7 days</p>
                            </div>
                            <div className="text-right">
                                <p className="text-sm font-medium text-foreground">
                                    &#8377;{formatCompact(chartData.reduce((s, d) => s + d.revenue, 0))}
                                </p>
                                <p className="text-[10px] text-muted-foreground">Total 7-day</p>
                            </div>
                        </div>
                        <div className="h-64">
                            <ResponsiveContainer width="100%" height="100%">
                                <AreaChart data={chartData} margin={{ top: 5, right: 10, left: 0, bottom: 0 }}>
                                    <defs>
                                        <linearGradient id="revenueGrad" x1="0" y1="0" x2="0" y2="1">
                                            <stop offset="5%" stopColor="#f97316" stopOpacity={0.3} />
                                            <stop offset="95%" stopColor="#f97316" stopOpacity={0} />
                                        </linearGradient>
                                    </defs>
                                    <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" />
                                    <XAxis dataKey="date" tick={{ fontSize: 11 }} stroke="#888" />
                                    <YAxis tick={{ fontSize: 11 }} stroke="#888" tickFormatter={(v) => formatCompact(v)} />
                                    <Tooltip
                                        contentStyle={{
                                            backgroundColor: "rgba(0,0,0,0.85)",
                                            border: "1px solid rgba(255,255,255,0.1)",
                                            borderRadius: "12px",
                                            fontSize: "12px",
                                        }}
                                        formatter={(value) => [`₹${formatCurrency(value as number)}`, "Revenue"]}
                                    />
                                    <Area
                                        type="monotone"
                                        dataKey="revenue"
                                        stroke="#f97316"
                                        strokeWidth={2.5}
                                        fill="url(#revenueGrad)"
                                    />
                                </AreaChart>
                            </ResponsiveContainer>
                        </div>
                    </GlassCard>

                    {/* Tank Status (compact, stacked vertically) */}
                    <GlassCard>
                        <h3 className="text-lg font-semibold text-foreground flex items-center gap-2 mb-4">
                            <Droplets className="w-5 h-5 text-muted-foreground" />
                            Tank Status
                        </h3>
                        {stats.tankStatuses.length === 0 ? (
                            <p className="text-muted-foreground text-sm py-4 text-center">No tanks configured.</p>
                        ) : (
                            <div className="space-y-3">
                                {stats.tankStatuses.map((tank) => {
                                    const pct = tank.capacity > 0 ? Math.min((tank.currentStock / tank.capacity) * 100, 100) : 0;
                                    const threshPct = (tank.thresholdStock != null && tank.thresholdStock > 0 && tank.capacity > 0)
                                        ? Math.min((tank.thresholdStock / tank.capacity) * 100, 100) : 0;
                                    const isBelowThreshold = tank.thresholdStock != null && tank.thresholdStock > 0 && tank.currentStock <= tank.thresholdStock;
                                    const fillColor = isBelowThreshold ? '#ef4444' : pct <= 20 ? '#ef4444' : pct <= 50 ? '#f59e0b' : '#22c55e';
                                    const statusColor = isBelowThreshold ? 'text-red-500' : pct <= 20 ? 'text-red-500' : pct <= 50 ? 'text-amber-500' : 'text-green-500';

                                    return (
                                        <div key={tank.tankId} className="p-2.5 rounded-xl border border-border/50 bg-card/50 flex items-center gap-3">
                                            {/* Mini Tank SVG */}
                                            <div className="flex-shrink-0" style={{ width: 40, height: 66 }}>
                                                <svg viewBox="0 0 48 80" width="40" height="66">
                                                    <defs>
                                                        <clipPath id={`dtank-${tank.tankId}`}>
                                                            <rect x="6" y="10" width="36" height="60" rx="5" />
                                                            <ellipse cx="24" cy="10" rx="18" ry="6" />
                                                            <ellipse cx="24" cy="70" rx="18" ry="6" />
                                                        </clipPath>
                                                        <linearGradient id={`dliq-${tank.tankId}`} x1="0" y1="0" x2="0" y2="1">
                                                            <stop offset="0%" stopColor={fillColor} stopOpacity="0.9" />
                                                            <stop offset="100%" stopColor={fillColor} stopOpacity="0.5" />
                                                        </linearGradient>
                                                    </defs>
                                                    <rect x="6" y="10" width="36" height="60" rx="5"
                                                        fill="none" stroke="currentColor" strokeOpacity="0.15" strokeWidth="1" className="text-foreground" />
                                                    <ellipse cx="24" cy="10" rx="18" ry="6"
                                                        fill="none" stroke="currentColor" strokeOpacity="0.15" strokeWidth="1" className="text-foreground" />
                                                    <ellipse cx="24" cy="70" rx="18" ry="6"
                                                        fill="none" stroke="currentColor" strokeOpacity="0.15" strokeWidth="1" className="text-foreground" />
                                                    <g clipPath={`url(#dtank-${tank.tankId})`}>
                                                        <rect x="6" width="36" y={76 - (pct / 100) * 66} height={(pct / 100) * 66} fill={`url(#dliq-${tank.tankId})`}>
                                                            <animate attributeName="y" from="76" to={76 - (pct / 100) * 66} dur="1s" fill="freeze" calcMode="spline" keySplines="0.25 0.1 0.25 1" />
                                                            <animate attributeName="height" from="0" to={(pct / 100) * 66} dur="1s" fill="freeze" calcMode="spline" keySplines="0.25 0.1 0.25 1" />
                                                        </rect>
                                                        {pct > 0 && (
                                                            <ellipse cx="24" cy={76 - (pct / 100) * 66} rx="18" ry="3" fill={fillColor} fillOpacity="0.3">
                                                                <animate attributeName="cy" from="76" to={76 - (pct / 100) * 66} dur="1s" fill="freeze" calcMode="spline" keySplines="0.25 0.1 0.25 1" />
                                                                <animate attributeName="ry" values="3;4;3" dur="3s" repeatCount="indefinite" />
                                                            </ellipse>
                                                        )}
                                                    </g>
                                                    {threshPct > 0 && (
                                                        <line x1="4" y1={76 - (threshPct / 100) * 66} x2="44" y2={76 - (threshPct / 100) * 66}
                                                            stroke="#f59e0b" strokeWidth="1" strokeDasharray="3 2" strokeOpacity="0.7" />
                                                    )}
                                                    <text x="24" y="42" textAnchor="middle" dominantBaseline="middle"
                                                        className="fill-foreground" fontSize="11" fontWeight="bold" opacity="0.7">
                                                        {pct.toFixed(0)}%
                                                    </text>
                                                </svg>
                                            </div>
                                            <div className="flex-1 min-w-0">
                                                <p className="text-sm font-semibold text-foreground truncate">{tank.tankName}</p>
                                                <p className="text-[10px] text-muted-foreground">{tank.productName || "—"}</p>
                                                <div className="flex items-center gap-2 mt-1">
                                                    <span className={`text-xs font-bold ${statusColor}`}>
                                                        {tank.currentStock.toLocaleString("en-IN", { maximumFractionDigits: 0 })}
                                                    </span>
                                                    <span className="text-[10px] text-muted-foreground">/ {tank.capacity.toLocaleString("en-IN", { maximumFractionDigits: 0 })} L</span>
                                                </div>
                                                {threshPct > 0 && (
                                                    <div className="flex items-center gap-1.5 mt-0.5">
                                                        <span className="inline-block w-2 h-px bg-amber-500" />
                                                        <span className="text-[10px] text-amber-500">Threshold: {tank.thresholdStock?.toLocaleString("en-IN", { maximumFractionDigits: 0 })} L</span>
                                                    </div>
                                                )}
                                                {isBelowThreshold && (
                                                    <div className="flex items-center gap-1 mt-0.5">
                                                        <AlertTriangle className="w-3 h-3 text-red-500" />
                                                        <span className="text-[10px] font-semibold text-red-500">LOW STOCK</span>
                                                    </div>
                                                )}
                                            </div>
                                        </div>
                                    );
                                })}
                            </div>
                        )}
                    </GlassCard>
                </div>

                {/* Product Sales + Credit Aging Row */}
                <div className="grid gap-6 lg:grid-cols-3">
                    {/* Product-wise Sales */}
                    <GlassCard className="lg:col-span-2">
                        <h3 className="text-lg font-semibold text-foreground flex items-center gap-2 mb-4">
                            <BarChart3 className="w-5 h-5 text-muted-foreground" />
                            Product Sales
                        </h3>
                        <p className="text-xs text-muted-foreground mb-4">Today&apos;s breakdown</p>
                        {stats.productSales.length === 0 ? (
                            <div className="flex items-center justify-center h-48 text-muted-foreground text-sm">
                                No sales today
                            </div>
                        ) : (
                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-6 items-center">
                                <div className="h-56">
                                    <ResponsiveContainer width="100%" height="100%">
                                        <PieChart>
                                            <Pie
                                                data={stats.productSales.map(p => ({
                                                    name: p.productName,
                                                    value: p.amount,
                                                }))}
                                                cx="50%"
                                                cy="50%"
                                                innerRadius={50}
                                                outerRadius={85}
                                                paddingAngle={3}
                                                dataKey="value"
                                            >
                                                {stats.productSales.map((_, i) => (
                                                    <Cell key={i} fill={PRODUCT_COLORS[i % PRODUCT_COLORS.length]} />
                                                ))}
                                            </Pie>
                                            <Tooltip
                                                contentStyle={{
                                                    backgroundColor: "rgba(0,0,0,0.85)",
                                                    border: "1px solid rgba(255,255,255,0.1)",
                                                    borderRadius: "12px",
                                                    fontSize: "12px",
                                                }}
                                                formatter={(value) => [`₹${formatCurrency(value as number)}`, "Amount"]}
                                            />
                                        </PieChart>
                                    </ResponsiveContainer>
                                </div>
                                <div className="space-y-3">
                                    {stats.productSales.map((p, i) => (
                                        <div key={p.productName} className="flex items-center justify-between text-sm">
                                            <div className="flex items-center gap-2">
                                                <div
                                                    className="w-3 h-3 rounded-full"
                                                    style={{ backgroundColor: PRODUCT_COLORS[i % PRODUCT_COLORS.length] }}
                                                />
                                                <span className="text-foreground">{p.productName}</span>
                                            </div>
                                            <div className="text-right">
                                                <span className="text-muted-foreground text-xs mr-2">{p.quantity.toFixed(2)} L</span>
                                                <span className="font-medium text-foreground">&#8377;{formatCurrency(p.amount)}</span>
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            </div>
                        )}
                    </GlassCard>

                    {/* Credit Aging */}
                    <GlassCard>
                        <h3 className="text-lg font-semibold text-foreground flex items-center gap-2 mb-4">
                            <Clock className="w-5 h-5 text-muted-foreground" />
                            Credit Aging
                        </h3>
                        <div className="space-y-4">
                            {agingData.map((item) => (
                                <div key={item.label}>
                                    <div className="flex items-center justify-between mb-1">
                                        <span className="text-sm text-muted-foreground">{item.label}</span>
                                        <span className="text-sm font-semibold text-foreground">
                                            &#8377;{formatCurrency(item.value)}
                                        </span>
                                    </div>
                                    <div className="w-full bg-muted/30 rounded-full h-2.5">
                                        <div
                                            className="h-2.5 rounded-full transition-all duration-500"
                                            style={{
                                                width: `${(item.value / maxAging) * 100}%`,
                                                backgroundColor: item.color,
                                            }}
                                        />
                                    </div>
                                </div>
                            ))}
                        </div>
                        <div className="mt-4 pt-4 border-t border-border/50">
                            <div className="flex justify-between items-center">
                                <span className="text-sm font-medium text-muted-foreground">Total Outstanding</span>
                                <span className="text-lg font-bold text-amber-500">
                                    &#8377;{formatCurrency(stats.totalOutstanding)}
                                </span>
                            </div>
                        </div>
                    </GlassCard>
                </div>

                {/* Fuel Volume Trend */}
                <GlassCard>
                    <div className="flex items-center justify-between mb-4">
                        <div>
                            <h3 className="text-lg font-semibold text-foreground flex items-center gap-2">
                                <Fuel className="w-5 h-5 text-muted-foreground" />
                                Fuel Volume Trend
                            </h3>
                            <p className="text-xs text-muted-foreground">Liters dispensed per day (last 7 days)</p>
                        </div>
                    </div>
                    <div className="h-52">
                        <ResponsiveContainer width="100%" height="100%">
                            <BarChart data={chartData} margin={{ top: 5, right: 10, left: 0, bottom: 0 }}>
                                <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" />
                                <XAxis dataKey="date" tick={{ fontSize: 11 }} stroke="#888" />
                                <YAxis tick={{ fontSize: 11 }} stroke="#888" tickFormatter={(v) => `${v}L`} />
                                <Tooltip
                                    contentStyle={{
                                        backgroundColor: "rgba(0,0,0,0.85)",
                                        border: "1px solid rgba(255,255,255,0.1)",
                                        borderRadius: "12px",
                                        fontSize: "12px",
                                    }}
                                    formatter={(value) => [`${(value as number).toFixed(2)} L`, "Volume"]}
                                />
                                <Bar dataKey="volume" radius={[6, 6, 0, 0]}>
                                    {chartData.map((_, i) => (
                                        <Cell
                                            key={i}
                                            fill={i === chartData.length - 1 ? "#06b6d4" : "rgba(6, 182, 212, 0.4)"}
                                        />
                                    ))}
                                </Bar>
                            </BarChart>
                        </ResponsiveContainer>
                    </div>
                </GlassCard>

                {/* Recent Invoices */}
                <GlassCard className="overflow-hidden border-none p-0">
                    <div className="px-6 pt-6 pb-4">
                        <h3 className="text-lg font-semibold text-foreground">Recent Invoices</h3>
                        <p className="text-sm text-muted-foreground">Last 10 invoices generated.</p>
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
                                            No invoices yet
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
