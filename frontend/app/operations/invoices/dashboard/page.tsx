"use client";

import { useEffect, useState } from "react";
import { GlassCard } from "@/components/ui/glass-card";
import {
    getInvoiceAnalytics,
    InvoiceAnalytics,
} from "@/lib/api/station";
import {
    IndianRupee,
    FileText,
    Banknote,
    CreditCard,
    TrendingUp,
    BarChart3,
    Clock,
    Users,
    Package,
    CheckCircle2,
    XCircle,
    ArrowUpRight,
    ArrowDownRight,
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
    Legend,
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

function formatHour(hour: number) {
    if (hour === 0) return "12 AM";
    if (hour < 12) return `${hour} AM`;
    if (hour === 12) return "12 PM";
    return `${hour - 12} PM`;
}

const COLORS = ["#f97316", "#06b6d4", "#8b5cf6", "#10b981", "#ef4444", "#eab308", "#ec4899", "#3b82f6"];

const TOOLTIP_STYLE = {
    backgroundColor: "rgba(0,0,0,0.85)",
    border: "1px solid rgba(255,255,255,0.1)",
    borderRadius: "12px",
    fontSize: "12px",
};

export default function InvoiceDashboardPage() {
    const [data, setData] = useState<InvoiceAnalytics | null>(null);
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
        getInvoiceAnalytics(from, to)
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
                <p className="text-muted-foreground animate-pulse">Loading invoice analytics...</p>
            </div>
        );
    }

    if (error || !data) {
        return (
            <div className="min-h-screen bg-background p-8 flex flex-col items-center justify-center">
                <p className="text-red-500 mb-2">Failed to load invoice analytics</p>
                <p className="text-muted-foreground text-sm">{error}</p>
            </div>
        );
    }

    const chartData = data.dailyTrend.map((d) => ({
        date: formatShortDate(d.date),
        total: d.totalAmount,
        cash: d.cashAmount,
        credit: d.creditAmount,
        count: d.totalCount,
    }));

    const cashCreditPie = [
        { name: "Cash", value: data.cashAmount, color: "#10b981" },
        { name: "Credit", value: data.creditAmount, color: "#f97316" },
    ].filter((d) => d.value > 0);

    const paidUnpaidPie = [
        { name: "Paid", value: data.paidAmount, color: "#10b981" },
        { name: "Unpaid", value: data.unpaidAmount, color: "#ef4444" },
    ].filter((d) => d.value > 0);

    const hourlyData = data.hourlyDistribution.filter((h) => h.count > 0);
    const maxHourly = Math.max(...data.hourlyDistribution.map((h) => h.count), 1);

    return (
        <div className="min-h-screen bg-background p-6 md:p-8 transition-colors duration-300">
            <div className="max-w-7xl mx-auto space-y-6">
                {/* Header + Date Range */}
                <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
                    <div>
                        <h1 className="text-4xl font-bold text-foreground tracking-tight">
                            <span className="text-gradient">Invoice Analytics</span>
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
                <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
                    <GlassCard>
                        <div className="flex justify-between items-start">
                            <div>
                                <p className="text-[10px] uppercase tracking-wider text-muted-foreground font-bold">Total Revenue</p>
                                <h3 className="text-2xl font-bold text-green-500 mt-2">&#8377;{formatCompact(data.totalRevenue)}</h3>
                                <p className="text-xs text-muted-foreground mt-1">{data.totalInvoices} invoices</p>
                            </div>
                            <div className="p-2 bg-green-500/10 rounded-lg">
                                <IndianRupee className="w-5 h-5 text-green-500" />
                            </div>
                        </div>
                    </GlassCard>

                    <GlassCard>
                        <div className="flex justify-between items-start">
                            <div>
                                <p className="text-[10px] uppercase tracking-wider text-muted-foreground font-bold">Avg Invoice Value</p>
                                <h3 className="text-2xl font-bold text-cyan-500 mt-2">&#8377;{formatCurrency(data.avgInvoiceValue)}</h3>
                                <p className="text-xs text-muted-foreground mt-1">Per invoice</p>
                            </div>
                            <div className="p-2 bg-cyan-500/10 rounded-lg">
                                <TrendingUp className="w-5 h-5 text-cyan-500" />
                            </div>
                        </div>
                    </GlassCard>

                    <GlassCard>
                        <div className="flex justify-between items-start">
                            <div>
                                <p className="text-[10px] uppercase tracking-wider text-muted-foreground font-bold">Cash Sales</p>
                                <h3 className="text-2xl font-bold text-emerald-500 mt-2">&#8377;{formatCompact(data.cashAmount)}</h3>
                                <p className="text-xs text-muted-foreground mt-1">{data.cashCount} invoices</p>
                            </div>
                            <div className="p-2 bg-emerald-500/10 rounded-lg">
                                <Banknote className="w-5 h-5 text-emerald-500" />
                            </div>
                        </div>
                    </GlassCard>

                    <GlassCard>
                        <div className="flex justify-between items-start">
                            <div>
                                <p className="text-[10px] uppercase tracking-wider text-muted-foreground font-bold">Credit Sales</p>
                                <h3 className="text-2xl font-bold text-orange-500 mt-2">&#8377;{formatCompact(data.creditAmount)}</h3>
                                <p className="text-xs text-muted-foreground mt-1">{data.creditCount} invoices</p>
                            </div>
                            <div className="p-2 bg-orange-500/10 rounded-lg">
                                <CreditCard className="w-5 h-5 text-orange-500" />
                            </div>
                        </div>
                    </GlassCard>
                </div>

                {/* Revenue Trend (stacked: cash + credit) */}
                <GlassCard>
                    <div className="flex items-center justify-between mb-4">
                        <div>
                            <h3 className="text-lg font-semibold text-foreground flex items-center gap-2">
                                <TrendingUp className="w-5 h-5 text-muted-foreground" />
                                Revenue Trend
                            </h3>
                            <p className="text-xs text-muted-foreground">Cash vs Credit daily breakdown</p>
                        </div>
                        <div className="text-right">
                            <p className="text-sm font-medium text-foreground">&#8377;{formatCompact(data.totalRevenue)}</p>
                            <p className="text-[10px] text-muted-foreground">Total period</p>
                        </div>
                    </div>
                    <div className="h-72">
                        <ResponsiveContainer width="100%" height="100%">
                            <AreaChart data={chartData} margin={{ top: 5, right: 10, left: 0, bottom: 0 }}>
                                <defs>
                                    <linearGradient id="cashGrad" x1="0" y1="0" x2="0" y2="1">
                                        <stop offset="5%" stopColor="#10b981" stopOpacity={0.3} />
                                        <stop offset="95%" stopColor="#10b981" stopOpacity={0} />
                                    </linearGradient>
                                    <linearGradient id="creditGrad" x1="0" y1="0" x2="0" y2="1">
                                        <stop offset="5%" stopColor="#f97316" stopOpacity={0.3} />
                                        <stop offset="95%" stopColor="#f97316" stopOpacity={0} />
                                    </linearGradient>
                                </defs>
                                <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" />
                                <XAxis dataKey="date" tick={{ fontSize: 11 }} stroke="#888" />
                                <YAxis tick={{ fontSize: 11 }} stroke="#888" tickFormatter={(v) => formatCompact(v)} />
                                <Tooltip
                                    contentStyle={TOOLTIP_STYLE}
                                    formatter={(value, name) => [
                                        `₹${formatCurrency(value as number)}`,
                                        name === "cash" ? "Cash" : "Credit",
                                    ]}
                                />
                                <Area type="monotone" dataKey="cash" stackId="1" stroke="#10b981" strokeWidth={2} fill="url(#cashGrad)" />
                                <Area type="monotone" dataKey="credit" stackId="1" stroke="#f97316" strokeWidth={2} fill="url(#creditGrad)" />
                            </AreaChart>
                        </ResponsiveContainer>
                    </div>
                </GlassCard>

                {/* Row: Cash/Credit Pie + Paid/Unpaid Pie + Payment Mode Distribution */}
                <div className="grid gap-6 lg:grid-cols-3">
                    {/* Cash vs Credit */}
                    <GlassCard>
                        <h3 className="text-lg font-semibold text-foreground flex items-center gap-2 mb-4">
                            <BarChart3 className="w-5 h-5 text-muted-foreground" />
                            Cash vs Credit
                        </h3>
                        {cashCreditPie.length === 0 ? (
                            <div className="flex items-center justify-center h-48 text-muted-foreground text-sm">No data</div>
                        ) : (
                            <>
                                <div className="h-48">
                                    <ResponsiveContainer width="100%" height="100%">
                                        <PieChart>
                                            <Pie data={cashCreditPie} cx="50%" cy="50%" innerRadius={45} outerRadius={75} paddingAngle={3} dataKey="value">
                                                {cashCreditPie.map((d, i) => (
                                                    <Cell key={i} fill={d.color} />
                                                ))}
                                            </Pie>
                                            <Tooltip contentStyle={TOOLTIP_STYLE} formatter={(value) => [`₹${formatCurrency(value as number)}`, "Amount"]} />
                                        </PieChart>
                                    </ResponsiveContainer>
                                </div>
                                <div className="space-y-2 mt-2">
                                    <div className="flex items-center justify-between text-sm">
                                        <div className="flex items-center gap-2">
                                            <div className="w-3 h-3 rounded-full bg-emerald-500" />
                                            <span className="text-foreground">Cash ({data.cashCount})</span>
                                        </div>
                                        <span className="font-medium text-foreground">&#8377;{formatCurrency(data.cashAmount)}</span>
                                    </div>
                                    <div className="flex items-center justify-between text-sm">
                                        <div className="flex items-center gap-2">
                                            <div className="w-3 h-3 rounded-full bg-orange-500" />
                                            <span className="text-foreground">Credit ({data.creditCount})</span>
                                        </div>
                                        <span className="font-medium text-foreground">&#8377;{formatCurrency(data.creditAmount)}</span>
                                    </div>
                                </div>
                            </>
                        )}
                    </GlassCard>

                    {/* Paid vs Unpaid */}
                    <GlassCard>
                        <h3 className="text-lg font-semibold text-foreground flex items-center gap-2 mb-4">
                            <CheckCircle2 className="w-5 h-5 text-muted-foreground" />
                            Payment Status
                        </h3>
                        {paidUnpaidPie.length === 0 ? (
                            <div className="flex items-center justify-center h-48 text-muted-foreground text-sm">No data</div>
                        ) : (
                            <>
                                <div className="h-48">
                                    <ResponsiveContainer width="100%" height="100%">
                                        <PieChart>
                                            <Pie data={paidUnpaidPie} cx="50%" cy="50%" innerRadius={45} outerRadius={75} paddingAngle={3} dataKey="value">
                                                {paidUnpaidPie.map((d, i) => (
                                                    <Cell key={i} fill={d.color} />
                                                ))}
                                            </Pie>
                                            <Tooltip contentStyle={TOOLTIP_STYLE} formatter={(value) => [`₹${formatCurrency(value as number)}`, "Amount"]} />
                                        </PieChart>
                                    </ResponsiveContainer>
                                </div>
                                <div className="space-y-2 mt-2">
                                    <div className="flex items-center justify-between text-sm">
                                        <div className="flex items-center gap-2">
                                            <CheckCircle2 className="w-3 h-3 text-green-500" />
                                            <span className="text-foreground">Paid ({data.paidCount})</span>
                                        </div>
                                        <span className="font-medium text-foreground">&#8377;{formatCurrency(data.paidAmount)}</span>
                                    </div>
                                    <div className="flex items-center justify-between text-sm">
                                        <div className="flex items-center gap-2">
                                            <XCircle className="w-3 h-3 text-red-500" />
                                            <span className="text-foreground">Unpaid ({data.unpaidCount})</span>
                                        </div>
                                        <span className="font-medium text-foreground">&#8377;{formatCurrency(data.unpaidAmount)}</span>
                                    </div>
                                </div>
                            </>
                        )}
                    </GlassCard>

                    {/* Payment Mode Distribution */}
                    <GlassCard>
                        <h3 className="text-lg font-semibold text-foreground flex items-center gap-2 mb-4">
                            <Banknote className="w-5 h-5 text-muted-foreground" />
                            Payment Modes
                        </h3>
                        <p className="text-xs text-muted-foreground mb-4">Cash invoice payment methods</p>
                        {data.paymentModeDistribution.length === 0 ? (
                            <div className="flex items-center justify-center h-40 text-muted-foreground text-sm">No data</div>
                        ) : (
                            <div className="space-y-3">
                                {data.paymentModeDistribution.map((mode, i) => {
                                    const maxAmount = Math.max(...data.paymentModeDistribution.map((m) => m.amount), 1);
                                    return (
                                        <div key={mode.name}>
                                            <div className="flex items-center justify-between mb-1">
                                                <span className="text-sm text-foreground">{mode.name}</span>
                                                <span className="text-sm font-semibold text-foreground">&#8377;{formatCurrency(mode.amount)}</span>
                                            </div>
                                            <div className="w-full bg-muted/30 rounded-full h-2">
                                                <div
                                                    className="h-2 rounded-full transition-all duration-500"
                                                    style={{
                                                        width: `${(mode.amount / maxAmount) * 100}%`,
                                                        backgroundColor: COLORS[i % COLORS.length],
                                                    }}
                                                />
                                            </div>
                                            <p className="text-[10px] text-muted-foreground mt-0.5">{mode.count} transactions</p>
                                        </div>
                                    );
                                })}
                            </div>
                        )}
                    </GlassCard>
                </div>

                {/* Row: Top Customers + Product Breakdown */}
                <div className="grid gap-6 lg:grid-cols-2">
                    {/* Top Customers */}
                    <GlassCard>
                        <h3 className="text-lg font-semibold text-foreground flex items-center gap-2 mb-4">
                            <Users className="w-5 h-5 text-muted-foreground" />
                            Top Customers by Revenue
                        </h3>
                        {data.topCustomers.length === 0 ? (
                            <p className="text-muted-foreground text-sm py-4 text-center">No customer data</p>
                        ) : (
                            <div className="h-64">
                                <ResponsiveContainer width="100%" height="100%">
                                    <BarChart
                                        data={data.topCustomers.slice(0, 8).map((c) => ({
                                            name: c.name.length > 15 ? c.name.substring(0, 15) + "..." : c.name,
                                            amount: c.amount,
                                            count: c.count,
                                        }))}
                                        layout="vertical"
                                        margin={{ top: 5, right: 30, left: 80, bottom: 5 }}
                                    >
                                        <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" />
                                        <XAxis type="number" tick={{ fontSize: 11 }} stroke="#888" tickFormatter={(v) => formatCompact(v)} />
                                        <YAxis type="category" dataKey="name" tick={{ fontSize: 11 }} stroke="#888" width={75} />
                                        <Tooltip
                                            contentStyle={TOOLTIP_STYLE}
                                            formatter={(value) => [`₹${formatCurrency(value as number)}`, "Revenue"]}
                                        />
                                        <Bar dataKey="amount" radius={[0, 6, 6, 0]}>
                                            {data.topCustomers.slice(0, 8).map((_, i) => (
                                                <Cell key={i} fill={COLORS[i % COLORS.length]} />
                                            ))}
                                        </Bar>
                                    </BarChart>
                                </ResponsiveContainer>
                            </div>
                        )}
                    </GlassCard>

                    {/* Product Breakdown */}
                    <GlassCard>
                        <h3 className="text-lg font-semibold text-foreground flex items-center gap-2 mb-4">
                            <Package className="w-5 h-5 text-muted-foreground" />
                            Product-wise Sales
                        </h3>
                        {data.productBreakdown.length === 0 ? (
                            <p className="text-muted-foreground text-sm py-4 text-center">No product data</p>
                        ) : (
                            <>
                                <div className="h-48">
                                    <ResponsiveContainer width="100%" height="100%">
                                        <PieChart>
                                            <Pie
                                                data={data.productBreakdown.map((p) => ({
                                                    name: p.productName,
                                                    value: p.amount,
                                                }))}
                                                cx="50%"
                                                cy="50%"
                                                innerRadius={45}
                                                outerRadius={75}
                                                paddingAngle={3}
                                                dataKey="value"
                                            >
                                                {data.productBreakdown.map((_, i) => (
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
                                    {data.productBreakdown.map((p, i) => (
                                        <div key={p.productName} className="flex items-center justify-between text-sm">
                                            <div className="flex items-center gap-2">
                                                <div className="w-3 h-3 rounded-full" style={{ backgroundColor: COLORS[i % COLORS.length] }} />
                                                <span className="text-foreground">{p.productName}</span>
                                            </div>
                                            <div className="text-right">
                                                <span className="text-muted-foreground text-xs mr-2">{p.quantity.toFixed(2)} L</span>
                                                <span className="font-medium text-foreground">&#8377;{formatCurrency(p.amount)}</span>
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            </>
                        )}
                    </GlassCard>
                </div>

                {/* Hourly Distribution */}
                <GlassCard>
                    <div className="flex items-center justify-between mb-4">
                        <div>
                            <h3 className="text-lg font-semibold text-foreground flex items-center gap-2">
                                <Clock className="w-5 h-5 text-muted-foreground" />
                                Hourly Invoice Distribution
                            </h3>
                            <p className="text-xs text-muted-foreground">When are invoices created throughout the day</p>
                        </div>
                    </div>
                    <div className="h-52">
                        <ResponsiveContainer width="100%" height="100%">
                            <BarChart
                                data={data.hourlyDistribution.map((h) => ({
                                    hour: formatHour(h.hour),
                                    count: h.count,
                                }))}
                                margin={{ top: 5, right: 10, left: 0, bottom: 0 }}
                            >
                                <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" />
                                <XAxis dataKey="hour" tick={{ fontSize: 10 }} stroke="#888" interval={2} />
                                <YAxis tick={{ fontSize: 11 }} stroke="#888" />
                                <Tooltip
                                    contentStyle={TOOLTIP_STYLE}
                                    formatter={(value) => [value as number, "Invoices"]}
                                />
                                <Bar dataKey="count" radius={[4, 4, 0, 0]}>
                                    {data.hourlyDistribution.map((h, i) => (
                                        <Cell key={i} fill={h.count === Math.max(...data.hourlyDistribution.map((x) => x.count)) && h.count > 0 ? "#f97316" : "rgba(249, 115, 22, 0.4)"} />
                                    ))}
                                </Bar>
                            </BarChart>
                        </ResponsiveContainer>
                    </div>
                </GlassCard>
            </div>
        </div>
    );
}
