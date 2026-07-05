"use client";

import { useEffect, useState } from "react";
import {
    Bar,
    BarChart,
    CartesianGrid,
    ComposedChart,
    LabelList,
    Legend,
    Line,
    LineChart,
    ResponsiveContainer,
    Tooltip,
    XAxis,
    YAxis,
} from "recharts";
import { GlassCard } from "@/components/ui/glass-card";
import { Badge } from "@/components/ui/badge";
import { parseLocalDate } from "@/lib/utils";
import { getTankAnalytics, TankAnalytics, TankAnalyticsRow } from "@/lib/api/station";
import { CalendarClock, Droplets, Fuel, Gauge, ShoppingCart, TrendingUp, Truck } from "lucide-react";

const COLORS = ["#f97316", "#06b6d4", "#8b5cf6", "#10b981", "#ef4444", "#eab308", "#ec4899", "#3b82f6"];

const TOOLTIP_STYLE = {
    backgroundColor: "rgba(0,0,0,0.85)",
    border: "1px solid rgba(255,255,255,0.1)",
    borderRadius: "12px",
    fontSize: "12px",
};

function formatLiters(val?: number | null) {
    if (val == null) return "—";
    if (Math.abs(val) >= 1000) return (val / 1000).toLocaleString("en-IN", { maximumFractionDigits: 1 }) + " KL";
    return val.toLocaleString("en-IN", { maximumFractionDigits: 0 }) + " L";
}

function formatShortDate(dateStr?: string | null) {
    if (!dateStr) return "—";
    return parseLocalDate(dateStr).toLocaleDateString("en-IN", { day: "2-digit", month: "short" });
}

const STATUS_META: Record<TankAnalyticsRow["status"], { label: string; variant: "success" | "warning" | "danger" | "outline" }> = {
    OK: { label: "OK", variant: "success" },
    ORDER_SOON: { label: "Order Soon", variant: "warning" },
    ORDER_NOW: { label: "Order Now", variant: "danger" },
    STAGNANT: { label: "No Sales", variant: "outline" },
};

const toIso = (d: Date) => d.toISOString().split("T")[0];

export default function TankAnalyticsPage() {
    const [data, setData] = useState<TankAnalytics | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [range, setRange] = useState<"7d" | "30d" | "90d">("30d");
    const [leadTimeDays, setLeadTimeDays] = useState(2);
    const [tankerLoadKl, setTankerLoadKl] = useState(12);

    useEffect(() => {
        setIsLoading(true);
        const now = new Date();
        const days = range === "7d" ? 6 : range === "30d" ? 29 : 89;
        getTankAnalytics({
            fromDate: toIso(new Date(now.getTime() - days * 86400000)),
            toDate: toIso(now),
            leadTimeDays,
            tankerLoadLiters: tankerLoadKl * 1000,
        })
            .then(setData)
            .catch((err) => setError(err.message || "Failed to load"))
            .finally(() => setIsLoading(false));
    }, [range, leadTimeDays, tankerLoadKl]);

    if (isLoading && !data) {
        return (
            <div className="min-h-screen bg-background p-8 flex flex-col items-center justify-center">
                <div className="w-12 h-12 border-4 border-primary/20 border-t-primary rounded-full animate-spin mb-4" />
                <p className="text-muted-foreground animate-pulse">Loading tank analytics...</p>
            </div>
        );
    }

    if (error || !data) {
        return (
            <div className="min-h-screen bg-background p-8 flex flex-col items-center justify-center">
                <p className="text-red-500 mb-2">Failed to load tank analytics</p>
                <p className="text-muted-foreground text-sm">{error}</p>
            </div>
        );
    }

    // Pivot daily product sales rows into one object per date
    const productNames = [...new Set(data.dailyProductSales.map((p) => p.product))];
    const salesByDate = new Map<string, Record<string, number | string>>();
    for (const p of data.dailyProductSales) {
        const entry = salesByDate.get(p.date) ?? { date: formatShortDate(p.date) };
        entry[p.product] = p.liters;
        salesByDate.set(p.date, entry);
    }
    const dailySalesData = [...salesByDate.values()];

    // Pivot daily tank stock rows; sum deliveries per day
    const tankNames = [...new Set(data.dailyTankStock.map((p) => p.tank))];
    const stockByDate = new Map<string, Record<string, number | string>>();
    for (const p of [...data.dailyTankStock].sort((a, b) => (a.date < b.date ? -1 : 1))) {
        const entry = stockByDate.get(p.date) ?? { date: formatShortDate(p.date), delivered: 0 };
        if (p.openStock != null) entry[p.tank] = p.openStock;
        entry.delivered = (Number(entry.delivered) || 0) + p.delivered;
        stockByDate.set(p.date, entry);
    }
    const stockTrendData = [...stockByDate.values()];

    const monthlyData = data.monthlyPurchases.map((m) => ({
        month: parseLocalDate(m.month + "-01").toLocaleDateString("en-IN", { month: "short", year: "2-digit" }),
        liters: m.liters,
    }));

    const utilization = data.totalCapacity > 0 ? Math.round((data.totalStock / data.totalCapacity) * 100) : 0;
    const toOrder = data.tanks.filter((t) => t.status === "ORDER_NOW" || t.status === "ORDER_SOON");

    const kpis = [
        { label: "Total Fuel Stock", value: formatLiters(data.totalStock), icon: Fuel, color: "text-primary", bg: "bg-primary/10" },
        { label: "Capacity Used", value: `${utilization}%`, icon: Gauge, color: "text-cyan-500", bg: "bg-cyan-500/10" },
        { label: "Avg Daily Sales", value: formatLiters(data.totalAvgDailySales), icon: TrendingUp, color: "text-green-500", bg: "bg-green-500/10" },
        { label: `Received (${data.rangeDays}d)`, value: formatLiters(data.totalDeliveredInRange), icon: Truck, color: "text-purple-500", bg: "bg-purple-500/10" },
        { label: "Next Tank Empty", value: formatShortDate(data.nextEmptyDate), icon: CalendarClock, color: "text-amber-500", bg: "bg-amber-500/10" },
        { label: "Tanks To Order", value: String(toOrder.length), icon: ShoppingCart, color: "text-red-500", bg: "bg-red-500/10" },
    ];

    return (
        <div className="min-h-screen bg-background p-6 md:p-8 transition-colors duration-300">
            <div className="max-w-7xl mx-auto space-y-6">
                {/* Header */}
                <div className="flex flex-col lg:flex-row lg:items-center lg:justify-between gap-4">
                    <div>
                        <h1 className="text-4xl font-bold text-foreground tracking-tight">
                            <span className="text-gradient">Tank Analytics</span>
                        </h1>
                        <p className="text-muted-foreground mt-1">
                            Selling rate, depletion projections and IOCL purchase planning &middot; {data.fromDate} to {data.toDate}
                        </p>
                    </div>
                    <div className="flex flex-wrap items-center gap-2">
                        {(["7d", "30d", "90d"] as const).map((r) => (
                            <button
                                key={r}
                                onClick={() => setRange(r)}
                                className={`px-4 py-2 text-sm font-medium rounded-lg transition-colors ${
                                    range === r
                                        ? "bg-primary text-primary-foreground"
                                        : "bg-muted text-muted-foreground hover:bg-muted/80"
                                }`}
                            >
                                {r === "7d" ? "7 Days" : r === "30d" ? "30 Days" : "90 Days"}
                            </button>
                        ))}
                        <div className="flex items-center gap-1.5 ml-2">
                            <label className="text-xs text-muted-foreground">Lead time</label>
                            <input
                                type="number"
                                min={0}
                                max={30}
                                value={leadTimeDays}
                                onChange={(e) => setLeadTimeDays(Math.max(0, Math.min(30, Number(e.target.value) || 0)))}
                                className="w-14 px-2 py-2 bg-card border border-border rounded-xl text-sm text-center"
                            />
                            <span className="text-xs text-muted-foreground">d</span>
                        </div>
                        <div className="flex items-center gap-1.5">
                            <label className="text-xs text-muted-foreground">Tanker</label>
                            <input
                                type="number"
                                min={1}
                                value={tankerLoadKl}
                                onChange={(e) => setTankerLoadKl(Math.max(1, Number(e.target.value) || 1))}
                                className="w-14 px-2 py-2 bg-card border border-border rounded-xl text-sm text-center"
                            />
                            <span className="text-xs text-muted-foreground">KL</span>
                        </div>
                    </div>
                </div>

                {/* KPI cards */}
                <div className="grid gap-4 grid-cols-2 md:grid-cols-3 lg:grid-cols-6">
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

                {/* Tank projection cards */}
                <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
                    {data.tanks.map((t) => (
                        <GlassCard key={t.tankId} className="p-5">
                            <div className="flex items-start justify-between mb-3">
                                <div>
                                    <h3 className="text-base font-bold text-foreground">{t.name}</h3>
                                    <p className="text-xs text-muted-foreground">{t.productName || "—"}</p>
                                </div>
                                <Badge variant={STATUS_META[t.status].variant} className="text-[10px]">
                                    {STATUS_META[t.status].label}
                                </Badge>
                            </div>
                            <div className="flex items-center gap-2 mb-3">
                                <div className="flex-1 h-2.5 rounded-full bg-white/10 overflow-hidden">
                                    <div
                                        className={`h-full rounded-full ${
                                            t.status === "ORDER_NOW" ? "bg-red-500" : t.status === "ORDER_SOON" ? "bg-amber-500" : "bg-emerald-500"
                                        }`}
                                        style={{ width: `${Math.min(t.fillPercent ?? 0, 100)}%` }}
                                    />
                                </div>
                                <span className="text-xs font-bold text-foreground w-10 text-right">{t.fillPercent != null ? `${Math.round(t.fillPercent)}%` : "—"}</span>
                            </div>
                            <div className="grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
                                <div>
                                    <p className="text-[10px] uppercase tracking-wider text-muted-foreground font-bold">Stock</p>
                                    <p className="font-semibold">{formatLiters(t.currentStock)} <span className="text-xs text-muted-foreground">/ {formatLiters(t.capacity)}</span></p>
                                </div>
                                <div>
                                    <p className="text-[10px] uppercase tracking-wider text-muted-foreground font-bold">Avg / Day</p>
                                    <p className="font-semibold">{formatLiters(t.avgDailySales)}</p>
                                </div>
                                <div>
                                    <p className="text-[10px] uppercase tracking-wider text-muted-foreground font-bold">Days Left</p>
                                    <p className={`font-bold ${t.daysToThreshold != null && t.daysToThreshold <= data.leadTimeDays ? "text-red-500" : "text-foreground"}`}>
                                        {t.daysToEmpty != null ? `${t.daysToEmpty}d` : "—"}
                                        <span className="text-xs text-muted-foreground font-normal"> (empty {formatShortDate(t.projectedEmptyDate)})</span>
                                    </p>
                                </div>
                                <div>
                                    <p className="text-[10px] uppercase tracking-wider text-muted-foreground font-bold">Order By</p>
                                    <p className={`font-bold ${t.status === "ORDER_NOW" ? "text-red-500" : t.status === "ORDER_SOON" ? "text-amber-500" : "text-foreground"}`}>
                                        {formatShortDate(t.recommendedOrderDate)}
                                    </p>
                                </div>
                            </div>
                            {t.suggestedOrderLiters != null && (
                                <div className="mt-3 pt-3 border-t border-border/50 flex items-center justify-between">
                                    <span className="text-xs text-muted-foreground">Suggested IOCL order</span>
                                    <span className="text-sm font-bold text-primary">{formatLiters(t.suggestedOrderLiters)}</span>
                                </div>
                            )}
                        </GlassCard>
                    ))}
                    {data.tanks.length === 0 && (
                        <GlassCard className="p-8 md:col-span-2 xl:col-span-3 text-center text-muted-foreground">
                            No active tanks configured
                        </GlassCard>
                    )}
                </div>

                {/* Daily sales per product */}
                <GlassCard className="p-6">
                    <div className="flex items-center gap-2 mb-4">
                        <div className="p-2 bg-primary/10 rounded-lg">
                            <TrendingUp className="w-5 h-5 text-primary" />
                        </div>
                        <div>
                            <h2 className="text-lg font-semibold text-foreground">Fuel Selling Rate</h2>
                            <p className="text-xs text-muted-foreground">Liters sold per day by product (nozzle meter readings)</p>
                        </div>
                    </div>
                    <div className="h-72">
                        <ResponsiveContainer width="100%" height="100%">
                            <LineChart data={dailySalesData} margin={{ top: 8, right: 16, left: 0, bottom: 0 }}>
                                <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" />
                                <XAxis dataKey="date" tick={{ fontSize: 11 }} stroke="#888" interval="preserveStartEnd" />
                                <YAxis tick={{ fontSize: 11 }} stroke="#888" tickFormatter={(v) => formatLiters(v)} width={70} />
                                <Tooltip contentStyle={TOOLTIP_STYLE} formatter={(v, name) => [formatLiters(Number(v)), name]} />
                                <Legend wrapperStyle={{ fontSize: 12 }} />
                                {productNames.map((name, i) => (
                                    <Line
                                        key={name}
                                        type="monotone"
                                        dataKey={name}
                                        stroke={COLORS[i % COLORS.length]}
                                        strokeWidth={2}
                                        dot={false}
                                        connectNulls
                                    />
                                ))}
                            </LineChart>
                        </ResponsiveContainer>
                    </div>
                    {dailySalesData.length === 0 && (
                        <p className="text-sm text-muted-foreground text-center py-4">No meter readings in this range</p>
                    )}
                </GlassCard>

                {/* Stock trend + deliveries */}
                <GlassCard className="p-6">
                    <div className="flex items-center gap-2 mb-4">
                        <div className="p-2 bg-cyan-500/10 rounded-lg">
                            <Droplets className="w-5 h-5 text-cyan-500" />
                        </div>
                        <div>
                            <h2 className="text-lg font-semibold text-foreground">Tank Stock Trend</h2>
                            <p className="text-xs text-muted-foreground">Daily opening stock per tank; bars mark fuel received (IOCL deliveries)</p>
                        </div>
                    </div>
                    <div className="h-72">
                        <ResponsiveContainer width="100%" height="100%">
                            <ComposedChart data={stockTrendData} margin={{ top: 8, right: 16, left: 0, bottom: 0 }}>
                                <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" />
                                <XAxis dataKey="date" tick={{ fontSize: 11 }} stroke="#888" interval="preserveStartEnd" />
                                <YAxis tick={{ fontSize: 11 }} stroke="#888" tickFormatter={(v) => formatLiters(v)} width={70} />
                                <Tooltip contentStyle={TOOLTIP_STYLE} formatter={(v, name) => [formatLiters(Number(v)), name === "delivered" ? "Received" : name]} />
                                <Legend wrapperStyle={{ fontSize: 12 }} formatter={(v) => (v === "delivered" ? "Received" : v)} />
                                <Bar dataKey="delivered" fill="#8b5cf6" opacity={0.5} maxBarSize={18} radius={[4, 4, 0, 0]} />
                                {tankNames.map((name, i) => (
                                    <Line
                                        key={name}
                                        type="monotone"
                                        dataKey={name}
                                        stroke={COLORS[i % COLORS.length]}
                                        strokeWidth={2}
                                        dot={false}
                                        connectNulls
                                    />
                                ))}
                            </ComposedChart>
                        </ResponsiveContainer>
                    </div>
                    {stockTrendData.length === 0 && (
                        <p className="text-sm text-muted-foreground text-center py-4">No tank readings in this range</p>
                    )}
                </GlassCard>

                {/* Monthly purchases */}
                <GlassCard className="p-6">
                    <div className="flex items-center gap-2 mb-4">
                        <div className="p-2 bg-purple-500/10 rounded-lg">
                            <Truck className="w-5 h-5 text-purple-500" />
                        </div>
                        <div>
                            <h2 className="text-lg font-semibold text-foreground">Fuel Purchases</h2>
                            <p className="text-xs text-muted-foreground">Liters received into tanks per month (within selected range)</p>
                        </div>
                    </div>
                    <div className="h-60">
                        <ResponsiveContainer width="100%" height="100%">
                            <BarChart data={monthlyData} margin={{ top: 24, right: 16, left: 0, bottom: 0 }}>
                                <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" vertical={false} />
                                <XAxis dataKey="month" tick={{ fontSize: 11 }} stroke="#888" />
                                <YAxis tick={{ fontSize: 11 }} stroke="#888" tickFormatter={(v) => formatLiters(v)} width={70} />
                                <Tooltip contentStyle={TOOLTIP_STYLE} formatter={(v) => [formatLiters(Number(v)), "Received"]} />
                                <Bar dataKey="liters" fill="#f97316" radius={[6, 6, 0, 0]} maxBarSize={48}>
                                    <LabelList
                                        dataKey="liters"
                                        position="top"
                                        fontSize={10}
                                        fill="#a1a1aa"
                                        formatter={(value) => (Number(value) > 0 ? formatLiters(Number(value)) : "")}
                                    />
                                </Bar>
                            </BarChart>
                        </ResponsiveContainer>
                    </div>
                    {monthlyData.length === 0 && (
                        <p className="text-sm text-muted-foreground text-center py-4">No fuel received in this range</p>
                    )}
                </GlassCard>
            </div>
        </div>
    );
}
