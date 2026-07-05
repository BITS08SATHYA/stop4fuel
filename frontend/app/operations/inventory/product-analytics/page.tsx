"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import {
    Bar,
    BarChart,
    CartesianGrid,
    LabelList,
    ResponsiveContainer,
    Tooltip,
    XAxis,
    YAxis,
} from "recharts";
import { GlassCard } from "@/components/ui/glass-card";
import { Badge } from "@/components/ui/badge";
import { fmtProductQty } from "@/lib/utils";
import { getProductAnalytics, ProductAnalytics, ProductAnalyticsRow } from "@/lib/api/station";
import {
    AlertTriangle,
    Archive,
    IndianRupee,
    PackageX,
    ShoppingCart,
    Timer,
    TrendingUp,
} from "lucide-react";

const TOOLTIP_STYLE = {
    backgroundColor: "rgba(0,0,0,0.85)",
    border: "1px solid rgba(255,255,255,0.1)",
    borderRadius: "12px",
    fontSize: "12px",
};

function formatCurrency(val?: number | null) {
    if (val == null) return "0.00";
    return val.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function formatCompact(val?: number | null) {
    if (val == null) return "0";
    if (val >= 10000000) return (val / 10000000).toFixed(1) + "Cr";
    if (val >= 100000) return (val / 100000).toFixed(1) + "L";
    if (val >= 1000) return (val / 1000).toFixed(1) + "K";
    return val.toLocaleString("en-IN", { maximumFractionDigits: 0 });
}

const STATUS_BADGE: Record<ProductAnalyticsRow["stockStatus"], { label: string; variant: "success" | "warning" | "danger" | "outline" }> = {
    OK: { label: "OK", variant: "success" },
    LOW: { label: "Low", variant: "warning" },
    OUT: { label: "Out", variant: "danger" },
    STALE: { label: "Slow", variant: "outline" },
};

const toIso = (d: Date) => d.toISOString().split("T")[0];

export default function ProductAnalyticsPage() {
    const [data, setData] = useState<ProductAnalytics | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [range, setRange] = useState<"7d" | "30d" | "90d">("30d");

    useEffect(() => {
        setIsLoading(true);
        const now = new Date();
        const days = range === "7d" ? 6 : range === "30d" ? 29 : 89;
        getProductAnalytics({
            fromDate: toIso(new Date(now.getTime() - days * 86400000)),
            toDate: toIso(now),
        })
            .then(setData)
            .catch((err) => setError(err.message || "Failed to load"))
            .finally(() => setIsLoading(false));
    }, [range]);

    if (isLoading && !data) {
        return (
            <div className="min-h-screen bg-background p-8 flex flex-col items-center justify-center">
                <div className="w-12 h-12 border-4 border-primary/20 border-t-primary rounded-full animate-spin mb-4" />
                <p className="text-muted-foreground animate-pulse">Loading product analytics...</p>
            </div>
        );
    }

    if (error || !data) {
        return (
            <div className="min-h-screen bg-background p-8 flex flex-col items-center justify-center">
                <p className="text-red-500 mb-2">Failed to load product analytics</p>
                <p className="text-muted-foreground text-sm">{error}</p>
            </div>
        );
    }

    const toBuy = data.products.filter((p) => p.stockStatus === "OUT" || p.suggestedOrderQty != null);
    const topMovers = [...data.products]
        .filter((p) => p.soldQuantity > 0)
        .sort((a, b) => b.soldQuantity - a.soldQuantity)
        .slice(0, 10)
        .map((p) => ({ name: p.name.length > 22 ? p.name.slice(0, 21) + "…" : p.name, qty: p.soldQuantity, amount: p.soldAmount }));
    const slowMovers = data.products.filter((p) => p.stockStatus === "STALE");

    const kpis = [
        { label: "Active Products", value: String(data.totalProducts), icon: Archive, color: "text-primary", bg: "bg-primary/10" },
        { label: "Stock Value", value: `₹${formatCompact(data.totalStockValue)}`, icon: IndianRupee, color: "text-green-500", bg: "bg-green-500/10" },
        { label: "Need Purchase", value: String(toBuy.length), icon: ShoppingCart, color: "text-amber-500", bg: "bg-amber-500/10" },
        { label: "Out of Stock", value: String(data.outOfStockCount), icon: PackageX, color: "text-red-500", bg: "bg-red-500/10" },
        { label: `Sold (${data.rangeDays}d)`, value: formatCompact(data.totalSoldQuantity), icon: TrendingUp, color: "text-cyan-500", bg: "bg-cyan-500/10" },
        { label: `Revenue (${data.rangeDays}d)`, value: `₹${formatCompact(data.totalSoldAmount)}`, icon: IndianRupee, color: "text-purple-500", bg: "bg-purple-500/10" },
    ];

    return (
        <div className="min-h-screen bg-background p-6 md:p-8 transition-colors duration-300">
            <div className="max-w-7xl mx-auto space-y-6">
                {/* Header */}
                <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
                    <div>
                        <h1 className="text-4xl font-bold text-foreground tracking-tight">
                            <span className="text-gradient">Product Analytics</span>
                        </h1>
                        <p className="text-muted-foreground mt-1">
                            Stock availability, selling rate and purchase planning &middot; {data.fromDate} to {data.toDate}
                        </p>
                    </div>
                    <div className="flex gap-2">
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
                    </div>
                </div>

                {/* KPI cards */}
                <div className="grid gap-4 grid-cols-2 md:grid-cols-3 lg:grid-cols-6">
                    {kpis.map((kpi) => (
                        <GlassCard key={kpi.label} className="p-4">
                            <div className="flex items-center justify-between">
                                <div className={`p-2 ${kpi.bg} rounded-lg`}>
                                    <kpi.icon className={`w-4 h-4 ${kpi.color}`} />
                                </div>
                            </div>
                            <h3 className={`text-xl font-bold mt-2 ${kpi.color}`}>{kpi.value}</h3>
                            <p className="text-[10px] uppercase tracking-wider text-muted-foreground font-bold mt-1">{kpi.label}</p>
                        </GlassCard>
                    ))}
                </div>

                {/* Purchase recommendations */}
                <GlassCard className="overflow-hidden border-none p-0">
                    <div className="px-6 py-4 border-b border-border/50 flex items-center gap-2">
                        <div className="p-2 bg-amber-500/10 rounded-lg">
                            <ShoppingCart className="w-5 h-5 text-amber-500" />
                        </div>
                        <div>
                            <h2 className="text-lg font-semibold text-foreground">Purchase Recommendations</h2>
                            <p className="text-xs text-muted-foreground">
                                Products below {14} days of stock &mdash; suggested quantity restores ~30 days of sales
                            </p>
                        </div>
                    </div>
                    <div className="overflow-x-auto">
                        <table className="w-full text-left border-collapse">
                            <thead>
                                <tr className="bg-white/5 border-b border-border/50">
                                    {["Product", "Status", "Stock", "Avg / Day", "Days Left", "Suggested Order", "Last Sale"].map((h) => (
                                        <th key={h} className="px-4 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">
                                            {h}
                                        </th>
                                    ))}
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-border/30">
                                {toBuy.length === 0 ? (
                                    <tr>
                                        <td colSpan={7} className="px-4 py-10 text-center text-sm text-muted-foreground">
                                            All products have healthy stock — nothing to purchase right now
                                        </td>
                                    </tr>
                                ) : (
                                    toBuy.map((p) => (
                                        <tr key={p.productId} className="hover:bg-white/5 transition-colors">
                                            <td className="px-4 py-4">
                                                <Link
                                                    href={`/operations/inventory/product-profiles/${p.productId}`}
                                                    className="text-sm font-bold text-foreground hover:text-primary hover:underline underline-offset-2"
                                                >
                                                    {p.name}
                                                </Link>
                                                <div className="text-[10px] text-muted-foreground uppercase">{p.brand || p.category}</div>
                                            </td>
                                            <td className="px-4 py-4">
                                                <Badge variant={STATUS_BADGE[p.stockStatus].variant} className="text-[10px]">
                                                    {STATUS_BADGE[p.stockStatus].label}
                                                </Badge>
                                            </td>
                                            <td className="px-4 py-4 text-sm">{fmtProductQty(p.totalStock, p.unit)}</td>
                                            <td className="px-4 py-4 text-sm">{fmtProductQty(p.avgDailyQuantity, null)}</td>
                                            <td className="px-4 py-4">
                                                <span className={`text-sm font-bold ${p.daysOfStock != null && p.daysOfStock < 7 ? "text-red-500" : "text-amber-500"}`}>
                                                    {p.daysOfStock != null ? `${p.daysOfStock}d` : "—"}
                                                </span>
                                            </td>
                                            <td className="px-4 py-4 text-sm font-bold text-primary">
                                                {p.suggestedOrderQty != null ? `${fmtProductQty(p.suggestedOrderQty, p.unit)} ${p.unit?.toLowerCase() || ""}` : "—"}
                                            </td>
                                            <td className="px-4 py-4 text-sm text-muted-foreground">{p.lastSaleDate || "Never"}</td>
                                        </tr>
                                    ))
                                )}
                            </tbody>
                        </table>
                    </div>
                </GlassCard>

                {/* Top movers + slow movers */}
                <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 items-start">
                    <GlassCard className="p-6 lg:col-span-2">
                        <div className="flex items-center gap-2 mb-4">
                            <div className="p-2 bg-primary/10 rounded-lg">
                                <TrendingUp className="w-5 h-5 text-primary" />
                            </div>
                            <div>
                                <h2 className="text-lg font-semibold text-foreground">Top Sellers</h2>
                                <p className="text-xs text-muted-foreground">Units sold in the last {data.rangeDays} days</p>
                            </div>
                        </div>
                        <div style={{ height: Math.max(topMovers.length * 42, 120) }}>
                            <ResponsiveContainer width="100%" height="100%">
                                <BarChart data={topMovers} layout="vertical" margin={{ top: 0, right: 48, left: 8, bottom: 0 }}>
                                    <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" horizontal={false} />
                                    <XAxis type="number" tick={{ fontSize: 11 }} stroke="#888" />
                                    <YAxis type="category" dataKey="name" tick={{ fontSize: 11 }} stroke="#888" width={150} />
                                    <Tooltip
                                        contentStyle={TOOLTIP_STYLE}
                                        formatter={(value, _name, item) => {
                                            const amount = (item?.payload as { amount?: number } | undefined)?.amount ?? 0;
                                            return [`${fmtProductQty(Number(value), null)} units · ₹${formatCurrency(amount)}`, "Sold"];
                                        }}
                                    />
                                    <Bar dataKey="qty" fill="#f97316" radius={[0, 6, 6, 0]} maxBarSize={22}>
                                        <LabelList
                                            dataKey="qty"
                                            position="right"
                                            fontSize={10}
                                            fill="#a1a1aa"
                                            formatter={(value) => fmtProductQty(Number(value), null)}
                                        />
                                    </Bar>
                                </BarChart>
                            </ResponsiveContainer>
                        </div>
                        {topMovers.length === 0 && (
                            <p className="text-sm text-muted-foreground text-center py-8">No sales in this range</p>
                        )}
                    </GlassCard>

                    <GlassCard className="p-6">
                        <div className="flex items-center gap-2 mb-4">
                            <div className="p-2 bg-white/5 rounded-lg">
                                <Timer className="w-5 h-5 text-muted-foreground" />
                            </div>
                            <div>
                                <h2 className="text-lg font-semibold text-foreground">Slow Movers</h2>
                                <p className="text-xs text-muted-foreground">No sales in 30+ days</p>
                            </div>
                        </div>
                        {slowMovers.length === 0 ? (
                            <p className="text-sm text-muted-foreground py-4">Every product sold recently.</p>
                        ) : (
                            <ul className="space-y-3">
                                {slowMovers.map((p) => (
                                    <li key={p.productId} className="flex items-center justify-between gap-3">
                                        <Link
                                            href={`/operations/inventory/product-profiles/${p.productId}`}
                                            className="text-sm font-medium text-foreground hover:text-primary hover:underline underline-offset-2 truncate"
                                        >
                                            {p.name}
                                        </Link>
                                        <span className="text-xs text-muted-foreground whitespace-nowrap">
                                            {p.lastSaleDate ? `last: ${p.lastSaleDate}` : "never sold"}
                                        </span>
                                    </li>
                                ))}
                            </ul>
                        )}
                    </GlassCard>
                </div>

                {/* Stock availability */}
                <GlassCard className="overflow-hidden border-none p-0">
                    <div className="px-6 py-4 border-b border-border/50 flex items-center gap-2">
                        <div className="p-2 bg-cyan-500/10 rounded-lg">
                            <Archive className="w-5 h-5 text-cyan-500" />
                        </div>
                        <h2 className="text-lg font-semibold text-foreground">Stock Availability</h2>
                    </div>
                    <div className="overflow-x-auto">
                        <table className="w-full text-left border-collapse">
                            <thead>
                                <tr className="bg-white/5 border-b border-border/50">
                                    {["Product", "Godown", "Cashier", "Total", "Reorder Lvl", "Fill", "Stock Value", "Status"].map((h) => (
                                        <th key={h} className="px-4 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">
                                            {h}
                                        </th>
                                    ))}
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-border/30">
                                {data.products.map((p) => {
                                    const fillPct = p.maxStock && p.maxStock > 0 ? Math.min((p.totalStock / p.maxStock) * 100, 100) : null;
                                    return (
                                        <tr key={p.productId} className="hover:bg-white/5 transition-colors">
                                            <td className="px-4 py-4">
                                                <Link
                                                    href={`/operations/inventory/product-profiles/${p.productId}`}
                                                    className="text-sm font-bold text-foreground hover:text-primary hover:underline underline-offset-2"
                                                >
                                                    {p.name}
                                                </Link>
                                                <div className="text-[10px] text-muted-foreground uppercase">{p.brand || p.category}</div>
                                            </td>
                                            <td className="px-4 py-4 text-sm">{fmtProductQty(p.godownStock, p.unit)}</td>
                                            <td className="px-4 py-4 text-sm">{fmtProductQty(p.cashierStock, p.unit)}</td>
                                            <td className="px-4 py-4 text-sm font-semibold">{fmtProductQty(p.totalStock, p.unit)}</td>
                                            <td className="px-4 py-4 text-sm text-muted-foreground">
                                                {p.reorderLevel != null ? fmtProductQty(p.reorderLevel, p.unit) : "—"}
                                            </td>
                                            <td className="px-4 py-4 w-36">
                                                {fillPct != null ? (
                                                    <div className="flex items-center gap-2">
                                                        <div className="flex-1 h-2 rounded-full bg-white/10 overflow-hidden">
                                                            <div
                                                                className={`h-full rounded-full ${
                                                                    p.stockStatus === "OUT" || p.stockStatus === "LOW" ? "bg-amber-500" : "bg-emerald-500"
                                                                }`}
                                                                style={{ width: `${fillPct}%` }}
                                                            />
                                                        </div>
                                                        <span className="text-[10px] text-muted-foreground w-8">{Math.round(fillPct)}%</span>
                                                    </div>
                                                ) : (
                                                    <span className="text-xs text-muted-foreground">—</span>
                                                )}
                                            </td>
                                            <td className="px-4 py-4 text-sm">&#8377;{formatCurrency(p.stockValue)}</td>
                                            <td className="px-4 py-4">
                                                <div className="flex items-center gap-1.5">
                                                    <Badge variant={STATUS_BADGE[p.stockStatus].variant} className="text-[10px]">
                                                        {STATUS_BADGE[p.stockStatus].label}
                                                    </Badge>
                                                    {p.stockStatus === "LOW" && <AlertTriangle className="w-3.5 h-3.5 text-amber-500" />}
                                                </div>
                                            </td>
                                        </tr>
                                    );
                                })}
                            </tbody>
                        </table>
                    </div>
                </GlassCard>
            </div>
        </div>
    );
}
