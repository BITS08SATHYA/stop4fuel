"use client";

import { useEffect, useState } from "react";
import { GlassCard } from "@/components/ui/glass-card";
import {
    getPaymentAnalytics,
    PaymentAnalytics,
} from "@/lib/api/station";
import {
    IndianRupee,
    TrendingUp,
    Clock,
    Users,
    AlertTriangle,
    CheckCircle2,
    Banknote,
    PieChart as PieChartIcon,
    BarChart3,
    ArrowUpRight,
    Percent,
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

function formatShortDate(dateStr: string) {
    const d = new Date(dateStr);
    return d.toLocaleDateString("en-IN", { day: "2-digit", month: "short" });
}

const COLORS = ["#10b981", "#3b82f6", "#8b5cf6", "#f97316", "#ef4444", "#eab308", "#ec4899", "#06b6d4"];

const TOOLTIP_STYLE = {
    backgroundColor: "rgba(0,0,0,0.85)",
    border: "1px solid rgba(255,255,255,0.1)",
    borderRadius: "12px",
    fontSize: "12px",
};

export default function PaymentDashboardPage() {
    const [data, setData] = useState<PaymentAnalytics | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [dateRange, setDateRange] = useState<"7d" | "30d" | "90d">("30d");

    const fetchData = (range: "7d" | "30d" | "90d") => {
        setIsLoading(true);
        const now = new Date();
        const to = now.toISOString().split("T")[0];
        const days = range === "7d" ? 6 : range === "30d" ? 29 : 89;
        const fromDate = new Date(now.getTime() - days * 24 * 60 * 60 * 1000);
        const from = fromDate.toISOString().split("T")[0];
        getPaymentAnalytics(from, to)
            .then(setData)
            .catch((err) => setError(err.message || "Failed to load"))
            .finally(() => setIsLoading(false));
    };

    useEffect(() => {
        fetchData(dateRange);
    }, [dateRange]);

    if (isLoading) {
        return (
            <div className="min-h-screen bg-background p-8 flex flex-col items-center justify-center">
                <div className="w-12 h-12 border-4 border-primary/20 border-t-primary rounded-full animate-spin mb-4" />
                <p className="text-muted-foreground animate-pulse">Loading payment analytics...</p>
            </div>
        );
    }

    if (error || !data) {
        return (
            <div className="min-h-screen bg-background p-8 flex flex-col items-center justify-center">
                <p className="text-red-500 mb-2">Failed to load payment analytics</p>
                <p className="text-muted-foreground text-sm">{error}</p>
            </div>
        );
    }

    const chartData = data.dailyTrend.map((d) => ({
        date: formatShortDate(d.date),
        amount: d.amount,
        count: d.count,
    }));

    const agingData = [
        { label: "0-30 days", value: data.aging0to30, color: "#10b981" },
        { label: "31-60 days", value: data.aging31to60, color: "#eab308" },
        { label: "61-90 days", value: data.aging61to90, color: "#f97316" },
        { label: "90+ days", value: data.aging90Plus, color: "#ef4444" },
    ];
    const maxAging = Math.max(...agingData.map((a) => a.value), 1);

    const collectedVsOutstanding = [
        { name: "Collected", value: data.totalCollected, color: "#10b981" },
        { name: "Outstanding", value: data.totalOutstanding, color: "#ef4444" },
    ].filter((d) => d.value > 0);

    return (
        <div className="min-h-screen bg-background p-6 md:p-8 transition-colors duration-300">
            <div className="max-w-7xl mx-auto space-y-6">
                {/* Header + Date Range */}
                <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
                    <div>
                        <h1 className="text-4xl font-bold text-foreground tracking-tight">
                            <span className="text-gradient">Payment Analytics</span>
                        </h1>
                        <p className="text-muted-foreground mt-1">
                            {formatShortDate(data.fromDate)} - {formatShortDate(data.toDate)}
                        </p>
                    </div>
                    <div className="flex gap-2">
                        {(["7d", "30d", "90d"] as const).map((range) => (
                            <button
                                key={range}
                                onClick={() => setDateRange(range)}
                                className={`px-4 py-2 text-sm font-medium rounded-lg transition-colors ${
                                    dateRange === range
                                        ? "bg-primary text-primary-foreground"
                                        : "bg-muted text-muted-foreground hover:bg-muted/80"
                                }`}
                            >
                                {range === "7d" ? "7 Days" : range === "30d" ? "30 Days" : "90 Days"}
                            </button>
                        ))}
                    </div>
                </div>

                {/* KPI Cards */}
                <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-5">
                    <GlassCard>
                        <div className="flex justify-between items-start">
                            <div>
                                <p className="text-[10px] uppercase tracking-wider text-muted-foreground font-bold">Total Collected</p>
                                <h3 className="text-2xl font-bold text-green-500 mt-2">&#8377;{formatCompact(data.totalCollected)}</h3>
                                <p className="text-xs text-muted-foreground mt-1">{data.totalPayments} payments</p>
                            </div>
                            <div className="p-2 bg-green-500/10 rounded-lg">
                                <IndianRupee className="w-5 h-5 text-green-500" />
                            </div>
                        </div>
                    </GlassCard>

                    <GlassCard>
                        <div className="flex justify-between items-start">
                            <div>
                                <p className="text-[10px] uppercase tracking-wider text-muted-foreground font-bold">Outstanding</p>
                                <h3 className="text-2xl font-bold text-red-500 mt-2">&#8377;{formatCompact(data.totalOutstanding)}</h3>
                                <p className="text-xs text-muted-foreground mt-1">{data.creditCustomers} customers</p>
                            </div>
                            <div className="p-2 bg-red-500/10 rounded-lg">
                                <AlertTriangle className="w-5 h-5 text-red-500" />
                            </div>
                        </div>
                    </GlassCard>

                    <GlassCard>
                        <div className="flex justify-between items-start">
                            <div>
                                <p className="text-[10px] uppercase tracking-wider text-muted-foreground font-bold">Collection Rate</p>
                                <h3 className="text-2xl font-bold text-cyan-500 mt-2">{data.collectionRate.toFixed(1)}%</h3>
                                <p className="text-xs text-muted-foreground mt-1">Collected / total</p>
                            </div>
                            <div className="p-2 bg-cyan-500/10 rounded-lg">
                                <Percent className="w-5 h-5 text-cyan-500" />
                            </div>
                        </div>
                    </GlassCard>

                    <GlassCard>
                        <div className="flex justify-between items-start">
                            <div>
                                <p className="text-[10px] uppercase tracking-wider text-muted-foreground font-bold">Avg Payment</p>
                                <h3 className="text-2xl font-bold text-purple-500 mt-2">&#8377;{formatCurrency(data.avgPaymentAmount)}</h3>
                                <p className="text-xs text-muted-foreground mt-1">Per transaction</p>
                            </div>
                            <div className="p-2 bg-purple-500/10 rounded-lg">
                                <TrendingUp className="w-5 h-5 text-purple-500" />
                            </div>
                        </div>
                    </GlassCard>

                    <GlassCard>
                        <div className="flex justify-between items-start">
                            <div>
                                <p className="text-[10px] uppercase tracking-wider text-muted-foreground font-bold">Total Payments</p>
                                <h3 className="text-2xl font-bold text-blue-500 mt-2">{data.totalPayments}</h3>
                                <p className="text-xs text-muted-foreground mt-1">Transactions</p>
                            </div>
                            <div className="p-2 bg-blue-500/10 rounded-lg">
                                <CheckCircle2 className="w-5 h-5 text-blue-500" />
                            </div>
                        </div>
                    </GlassCard>
                </div>

                {/* Collection Trend */}
                <GlassCard>
                    <div className="flex items-center justify-between mb-4">
                        <div>
                            <h3 className="text-lg font-semibold text-foreground flex items-center gap-2">
                                <TrendingUp className="w-5 h-5 text-muted-foreground" />
                                Collection Trend
                            </h3>
                            <p className="text-xs text-muted-foreground">Daily payment collections</p>
                        </div>
                        <div className="text-right">
                            <p className="text-sm font-medium text-foreground">&#8377;{formatCompact(data.totalCollected)}</p>
                            <p className="text-[10px] text-muted-foreground">Total collected</p>
                        </div>
                    </div>
                    <div className="h-72">
                        <ResponsiveContainer width="100%" height="100%">
                            <AreaChart data={chartData} margin={{ top: 5, right: 10, left: 0, bottom: 0 }}>
                                <defs>
                                    <linearGradient id="collectionGrad" x1="0" y1="0" x2="0" y2="1">
                                        <stop offset="5%" stopColor="#10b981" stopOpacity={0.3} />
                                        <stop offset="95%" stopColor="#10b981" stopOpacity={0} />
                                    </linearGradient>
                                </defs>
                                <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" />
                                <XAxis dataKey="date" tick={{ fontSize: 11 }} stroke="#888" />
                                <YAxis tick={{ fontSize: 11 }} stroke="#888" tickFormatter={(v) => formatCompact(v)} />
                                <Tooltip
                                    contentStyle={TOOLTIP_STYLE}
                                    formatter={(value, name) => [
                                        name === "amount" ? `₹${formatCurrency(value as number)}` : (value as number),
                                        name === "amount" ? "Collected" : "Payments",
                                    ]}
                                />
                                <Area type="monotone" dataKey="amount" stroke="#10b981" strokeWidth={2.5} fill="url(#collectionGrad)" />
                            </AreaChart>
                        </ResponsiveContainer>
                    </div>
                </GlassCard>

                {/* Row: Payment Mode + Collected vs Outstanding + Credit Aging */}
                <div className="grid gap-6 lg:grid-cols-3">
                    {/* Payment Mode Breakdown */}
                    <GlassCard>
                        <h3 className="text-lg font-semibold text-foreground flex items-center gap-2 mb-4">
                            <PieChartIcon className="w-5 h-5 text-muted-foreground" />
                            Payment Modes
                        </h3>
                        {data.paymentModeBreakdown.length === 0 ? (
                            <div className="flex items-center justify-center h-48 text-muted-foreground text-sm">No payments</div>
                        ) : (
                            <>
                                <div className="h-48">
                                    <ResponsiveContainer width="100%" height="100%">
                                        <PieChart>
                                            <Pie
                                                data={data.paymentModeBreakdown.map((m) => ({
                                                    name: m.name,
                                                    value: m.amount,
                                                }))}
                                                cx="50%"
                                                cy="50%"
                                                innerRadius={45}
                                                outerRadius={75}
                                                paddingAngle={3}
                                                dataKey="value"
                                            >
                                                {data.paymentModeBreakdown.map((_, i) => (
                                                    <Cell key={i} fill={COLORS[i % COLORS.length]} />
                                                ))}
                                            </Pie>
                                            <Tooltip
                                                contentStyle={TOOLTIP_STYLE}
                                                formatter={(value) => [`₹${formatCurrency(value as number)}`, "Amount"]}
                                            />
                                        </PieChart>
                                    </ResponsiveContainer>
                                </div>
                                <div className="space-y-2 mt-2">
                                    {data.paymentModeBreakdown.map((mode, i) => (
                                        <div key={mode.name} className="flex items-center justify-between text-sm">
                                            <div className="flex items-center gap-2">
                                                <div className="w-3 h-3 rounded-full" style={{ backgroundColor: COLORS[i % COLORS.length] }} />
                                                <span className="text-foreground">{mode.name}</span>
                                                <span className="text-[10px] text-muted-foreground">({mode.count})</span>
                                            </div>
                                            <span className="font-medium text-foreground">&#8377;{formatCurrency(mode.amount)}</span>
                                        </div>
                                    ))}
                                </div>
                            </>
                        )}
                    </GlassCard>

                    {/* Collected vs Outstanding */}
                    <GlassCard>
                        <h3 className="text-lg font-semibold text-foreground flex items-center gap-2 mb-4">
                            <BarChart3 className="w-5 h-5 text-muted-foreground" />
                            Collected vs Outstanding
                        </h3>
                        {collectedVsOutstanding.length === 0 ? (
                            <div className="flex items-center justify-center h-48 text-muted-foreground text-sm">No data</div>
                        ) : (
                            <>
                                <div className="h-48">
                                    <ResponsiveContainer width="100%" height="100%">
                                        <PieChart>
                                            <Pie
                                                data={collectedVsOutstanding}
                                                cx="50%"
                                                cy="50%"
                                                innerRadius={45}
                                                outerRadius={75}
                                                paddingAngle={3}
                                                dataKey="value"
                                            >
                                                {collectedVsOutstanding.map((d, i) => (
                                                    <Cell key={i} fill={d.color} />
                                                ))}
                                            </Pie>
                                            <Tooltip
                                                contentStyle={TOOLTIP_STYLE}
                                                formatter={(value) => [`₹${formatCurrency(value as number)}`, "Amount"]}
                                            />
                                        </PieChart>
                                    </ResponsiveContainer>
                                </div>
                                <div className="space-y-2 mt-2">
                                    <div className="flex items-center justify-between text-sm">
                                        <div className="flex items-center gap-2">
                                            <div className="w-3 h-3 rounded-full bg-green-500" />
                                            <span className="text-foreground">Collected</span>
                                        </div>
                                        <span className="font-medium text-green-500">&#8377;{formatCurrency(data.totalCollected)}</span>
                                    </div>
                                    <div className="flex items-center justify-between text-sm">
                                        <div className="flex items-center gap-2">
                                            <div className="w-3 h-3 rounded-full bg-red-500" />
                                            <span className="text-foreground">Outstanding</span>
                                        </div>
                                        <span className="font-medium text-red-500">&#8377;{formatCurrency(data.totalOutstanding)}</span>
                                    </div>
                                </div>
                                {/* Collection rate bar */}
                                <div className="mt-4 pt-3 border-t border-border/50">
                                    <div className="flex items-center justify-between mb-1">
                                        <span className="text-xs text-muted-foreground">Collection Rate</span>
                                        <span className="text-sm font-bold text-cyan-500">{data.collectionRate.toFixed(1)}%</span>
                                    </div>
                                    <div className="w-full bg-muted/30 rounded-full h-2.5 flex overflow-hidden">
                                        <div
                                            className="bg-green-500 h-2.5 transition-all duration-500"
                                            style={{ width: `${Math.min(data.collectionRate, 100)}%` }}
                                        />
                                        <div
                                            className="bg-red-500 h-2.5 transition-all duration-500"
                                            style={{ width: `${Math.min(100 - data.collectionRate, 100)}%` }}
                                        />
                                    </div>
                                </div>
                            </>
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
                                    &#8377;{formatCurrency(data.totalOutstanding)}
                                </span>
                            </div>
                        </div>
                    </GlassCard>
                </div>

                {/* Top Paying Customers */}
                <GlassCard>
                    <h3 className="text-lg font-semibold text-foreground flex items-center gap-2 mb-4">
                        <Users className="w-5 h-5 text-muted-foreground" />
                        Top Paying Customers
                    </h3>
                    {data.topCustomers.length === 0 ? (
                        <p className="text-muted-foreground text-sm py-4 text-center">No payment data</p>
                    ) : (
                        <div className="h-64">
                            <ResponsiveContainer width="100%" height="100%">
                                <BarChart
                                    data={data.topCustomers.slice(0, 10).map((c) => ({
                                        name: c.name.length > 15 ? c.name.substring(0, 15) + "..." : c.name,
                                        amount: c.amount,
                                        count: c.count,
                                    }))}
                                    margin={{ top: 5, right: 30, left: 0, bottom: 5 }}
                                >
                                    <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" />
                                    <XAxis dataKey="name" tick={{ fontSize: 10 }} stroke="#888" angle={-20} textAnchor="end" height={50} />
                                    <YAxis tick={{ fontSize: 11 }} stroke="#888" tickFormatter={(v) => formatCompact(v)} />
                                    <Tooltip
                                        contentStyle={TOOLTIP_STYLE}
                                        formatter={(value, name) => [
                                            name === "amount" ? `₹${formatCurrency(value as number)}` : (value as number),
                                            name === "amount" ? "Total Paid" : "Payments",
                                        ]}
                                    />
                                    <Bar dataKey="amount" radius={[6, 6, 0, 0]}>
                                        {data.topCustomers.slice(0, 10).map((_, i) => (
                                            <Cell key={i} fill={COLORS[i % COLORS.length]} />
                                        ))}
                                    </Bar>
                                </BarChart>
                            </ResponsiveContainer>
                        </div>
                    )}
                </GlassCard>
            </div>
        </div>
    );
}
