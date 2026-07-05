"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import {
    Bar,
    BarChart,
    CartesianGrid,
    ComposedChart,
    LabelList,
    Legend,
    Line,
    ResponsiveContainer,
    Tooltip,
    XAxis,
    YAxis,
} from "recharts";
import { GlassCard } from "@/components/ui/glass-card";
import { Badge } from "@/components/ui/badge";
import { parseLocalDate } from "@/lib/utils";
import {
    CustomerRepaymentAnalytics,
    CustomerRepaymentRow,
    getCustomerRepaymentAnalytics,
} from "@/lib/api/station";
import {
    AlertTriangle,
    ArrowDownRight,
    ArrowUpRight,
    Clock,
    IndianRupee,
    Moon,
    Timer,
    TrendingUp,
    Users,
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
    if (Math.abs(val) >= 10000000) return (val / 10000000).toFixed(1) + "Cr";
    if (Math.abs(val) >= 100000) return (val / 100000).toFixed(1) + "L";
    if (Math.abs(val) >= 1000) return (val / 1000).toFixed(1) + "K";
    return val.toLocaleString("en-IN", { maximumFractionDigits: 0 });
}

function formatMonth(ym: string) {
    return parseLocalDate(ym + "-01").toLocaleDateString("en-IN", { month: "short", year: "2-digit" });
}

const toIso = (d: Date) => d.toISOString().split("T")[0];

type Preset = "30d" | "quarter" | "year" | "custom";
type Filter = "all" | "statement" | "local" | "overdue" | "more" | "less" | "quiet";

/** 30-day activity strip: one cell per day, orange intensity = liters vs the customer's own best day */
function HeatStrip({ dates, liters }: { dates: string[]; liters: number[] }) {
    const max = Math.max(...liters, 0);
    return (
        <div className="flex items-center gap-[2px]">
            {liters.map((v, i) => {
                const ratio = max > 0 ? v / max : 0;
                const cls =
                    v <= 0
                        ? "bg-muted"
                        : ratio > 0.66
                          ? "bg-orange-500"
                          : ratio > 0.33
                            ? "bg-orange-500/60"
                            : "bg-orange-500/30";
                return (
                    <div
                        key={dates[i] ?? i}
                        title={`${dates[i] ?? ""}: ${v.toLocaleString("en-IN", { maximumFractionDigits: 1 })} L`}
                        className={`w-[5px] h-4 rounded-[2px] ${cls}`}
                    />
                );
            })}
        </div>
    );
}

export default function RepaymentAnalyticsPage() {
    const router = useRouter();
    const [data, setData] = useState<CustomerRepaymentAnalytics | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [preset, setPreset] = useState<Preset>("quarter");
    const [{ fromDate, toDate }, setRange] = useState(() => presetRange("quarter"));
    const [filter, setFilter] = useState<Filter>("all");

    function presetRange(p: Exclude<Preset, "custom">) {
        const now = new Date();
        const days = p === "30d" ? 29 : p === "quarter" ? 89 : 364;
        return { fromDate: toIso(new Date(now.getTime() - days * 86400000)), toDate: toIso(now) };
    }

    useEffect(() => {
        if (!fromDate || !toDate || fromDate > toDate) return;
        setIsLoading(true);
        getCustomerRepaymentAnalytics({ fromDate, toDate })
            .then(setData)
            .catch((err) => setError(err.message || "Failed to load"))
            .finally(() => setIsLoading(false));
    }, [fromDate, toDate]);

    const openCustomer = (row: CustomerRepaymentRow) => {
        router.push(`/customers/${row.customerId}/transactions`);
    };

    if (isLoading && !data) {
        return (
            <div className="min-h-screen bg-background p-8 flex flex-col items-center justify-center">
                <div className="w-12 h-12 border-4 border-primary/20 border-t-primary rounded-full animate-spin mb-4" />
                <p className="text-muted-foreground animate-pulse">Loading repayment analytics...</p>
            </div>
        );
    }

    if (error || !data) {
        return (
            <div className="min-h-screen bg-background p-8 flex flex-col items-center justify-center">
                <p className="text-red-500 mb-2">Failed to load repayment analytics</p>
                <p className="text-muted-foreground text-sm">{error}</p>
            </div>
        );
    }

    const monthlyData = data.monthlyTurnover.map((m) => ({
        month: formatMonth(m.month),
        billed: m.billed,
        collected: m.collected,
    }));

    const filtered = data.customers.filter((c) => {
        if (filter === "statement") return c.partyType === "STATEMENT";
        if (filter === "local") return c.partyType === "LOCAL";
        if (filter === "overdue") return c.overdue;
        if (filter === "more") return c.consumptionTrend === "MORE";
        if (filter === "less") return c.consumptionTrend === "LESS";
        if (filter === "quiet") return c.quiet;
        return true;
    });

    const consumingMore = data.customers.filter((c) => c.consumptionTrend === "MORE").length;
    const quietList = data.customers
        .filter((c) => c.quiet)
        .sort((a, b) => (b.daysSinceLastBill ?? 0) - (a.daysSinceLastBill ?? 0));
    const kpis = [
        { label: "Turnover (billed)", value: `₹${formatCompact(data.totalBilled)}`, icon: IndianRupee, color: "text-primary", bg: "bg-primary/10" },
        { label: "Collected", value: `₹${formatCompact(data.totalCollected)}`, icon: TrendingUp, color: "text-green-500", bg: "bg-green-500/10" },
        { label: "Outstanding", value: `₹${formatCompact(data.totalOutstanding)}`, icon: Clock, color: "text-amber-500", bg: "bg-amber-500/10" },
        { label: "Avg Repayment", value: data.avgRepaymentLagDays != null ? `${data.avgRepaymentLagDays}d` : "—", icon: Timer, color: "text-cyan-500", bg: "bg-cyan-500/10" },
        { label: "Overdue Customers", value: String(data.overdueCustomers), icon: AlertTriangle, color: "text-red-500", bg: "bg-red-500/10" },
        { label: "Consuming More", value: String(consumingMore), icon: ArrowUpRight, color: "text-purple-500", bg: "bg-purple-500/10" },
        { label: "Gone Quiet", value: String(data.quietCustomers ?? quietList.length), icon: Moon, color: "text-rose-500", bg: "bg-rose-500/10" },
    ];

    const trendChip = (c: CustomerRepaymentRow) => {
        if (c.consumptionTrend === "MORE")
            return (
                <span className="inline-flex items-center gap-0.5 text-xs font-bold text-red-400">
                    <ArrowUpRight className="w-3.5 h-3.5" />+{c.changePercent}%
                </span>
            );
        if (c.consumptionTrend === "LESS")
            return (
                <span className="inline-flex items-center gap-0.5 text-xs font-bold text-cyan-400">
                    <ArrowDownRight className="w-3.5 h-3.5" />{c.changePercent}%
                </span>
            );
        if (c.consumptionTrend === "NEW") return <span className="text-xs text-muted-foreground">new</span>;
        return <span className="text-xs text-muted-foreground">{c.changePercent != null ? `${c.changePercent > 0 ? "+" : ""}${c.changePercent}%` : "~"}</span>;
    };

    return (
        <div className="min-h-screen bg-background p-6 md:p-8 transition-colors duration-300">
            <div className="max-w-7xl mx-auto space-y-6">
                {/* Header */}
                <div className="flex flex-col lg:flex-row lg:items-center lg:justify-between gap-4">
                    <div>
                        <h1 className="text-4xl font-bold text-foreground tracking-tight">
                            <span className="text-gradient">Repayment Analytics</span>
                        </h1>
                        <p className="text-muted-foreground mt-1">
                            Customer turnover, repayment behaviour and utilization &middot; {data.fromDate} to {data.toDate}
                        </p>
                    </div>
                    <div className="flex flex-wrap items-center gap-2">
                        {(["30d", "quarter", "year"] as const).map((p) => (
                            <button
                                key={p}
                                onClick={() => {
                                    setPreset(p);
                                    setRange(presetRange(p));
                                }}
                                className={`px-4 py-2 text-sm font-medium rounded-lg transition-colors ${
                                    preset === p
                                        ? "bg-primary text-primary-foreground"
                                        : "bg-muted text-muted-foreground hover:bg-muted/80"
                                }`}
                            >
                                {p === "30d" ? "30 Days" : p === "quarter" ? "Quarter" : "Year"}
                            </button>
                        ))}
                        <input
                            type="date"
                            value={fromDate}
                            max={toDate}
                            onChange={(e) => {
                                setPreset("custom");
                                setRange((r) => ({ ...r, fromDate: e.target.value }));
                            }}
                            className="px-3 py-2 bg-card border border-border rounded-xl text-sm"
                        />
                        <span className="text-muted-foreground text-sm">to</span>
                        <input
                            type="date"
                            value={toDate}
                            min={fromDate}
                            onChange={(e) => {
                                setPreset("custom");
                                setRange((r) => ({ ...r, toDate: e.target.value }));
                            }}
                            className="px-3 py-2 bg-card border border-border rounded-xl text-sm"
                        />
                    </div>
                </div>

                {/* KPI cards */}
                <div className="grid gap-4 grid-cols-2 md:grid-cols-4 lg:grid-cols-7">
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

                {/* Turnover trend + repayment histogram */}
                <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 items-start">
                    <GlassCard className="p-6 lg:col-span-2">
                        <div className="flex items-center gap-2 mb-4">
                            <div className="p-2 bg-primary/10 rounded-lg">
                                <TrendingUp className="w-5 h-5 text-primary" />
                            </div>
                            <div>
                                <h2 className="text-lg font-semibold text-foreground">Turnover vs Collection</h2>
                                <p className="text-xs text-muted-foreground">Monthly billed amount and payments received</p>
                            </div>
                        </div>
                        <div className="h-64">
                            <ResponsiveContainer width="100%" height="100%">
                                <ComposedChart data={monthlyData} margin={{ top: 8, right: 16, left: 0, bottom: 0 }}>
                                    <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" vertical={false} />
                                    <XAxis dataKey="month" tick={{ fontSize: 11 }} stroke="#888" />
                                    <YAxis tick={{ fontSize: 11 }} stroke="#888" tickFormatter={(v) => "₹" + formatCompact(v)} width={70} />
                                    <Tooltip contentStyle={TOOLTIP_STYLE} formatter={(v, name) => [`₹${formatCurrency(Number(v))}`, name === "billed" ? "Billed" : "Collected"]} />
                                    <Legend wrapperStyle={{ fontSize: 12 }} formatter={(v) => (v === "billed" ? "Billed" : "Collected")} />
                                    <Bar dataKey="billed" fill="#f97316" radius={[6, 6, 0, 0]} maxBarSize={40} />
                                    <Line type="monotone" dataKey="collected" stroke="#10b981" strokeWidth={2} dot={{ r: 3 }} />
                                </ComposedChart>
                            </ResponsiveContainer>
                        </div>
                    </GlassCard>

                    <GlassCard className="p-6">
                        <div className="flex items-center gap-2 mb-4">
                            <div className="p-2 bg-cyan-500/10 rounded-lg">
                                <Timer className="w-5 h-5 text-cyan-500" />
                            </div>
                            <div>
                                <h2 className="text-lg font-semibold text-foreground">Repayment Window</h2>
                                <p className="text-xs text-muted-foreground">Days from statement (or credit bill) to payment</p>
                            </div>
                        </div>
                        <div className="h-64">
                            <ResponsiveContainer width="100%" height="100%">
                                <BarChart data={data.lagHistogram} margin={{ top: 24, right: 8, left: 0, bottom: 0 }}>
                                    <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" vertical={false} />
                                    <XAxis dataKey="bucket" tick={{ fontSize: 11 }} stroke="#888" />
                                    <YAxis tick={{ fontSize: 11 }} stroke="#888" allowDecimals={false} />
                                    <Tooltip contentStyle={TOOLTIP_STYLE} formatter={(v) => [String(v), "Payments"]} />
                                    <Bar dataKey="count" fill="#06b6d4" radius={[6, 6, 0, 0]} maxBarSize={36}>
                                        <LabelList dataKey="count" position="top" fontSize={10} fill="#a1a1aa" formatter={(v) => (Number(v) > 0 ? String(v) : "")} />
                                    </Bar>
                                </BarChart>
                            </ResponsiveContainer>
                        </div>
                    </GlassCard>
                </div>

                {/* Gone-quiet alerts: silent vs their own fill cadence */}
                {quietList.length > 0 && (
                    <GlassCard className="p-6 border border-rose-500/30">
                        <div className="flex items-center gap-2 mb-4">
                            <div className="p-2 bg-rose-500/10 rounded-lg">
                                <Moon className="w-5 h-5 text-rose-500" />
                            </div>
                            <div>
                                <h2 className="text-lg font-semibold text-foreground">Gone Quiet — Needs Follow-up</h2>
                                <p className="text-xs text-muted-foreground">
                                    Silent for over 2&times; their usual fill interval (learned from the last 90 days)
                                </p>
                            </div>
                        </div>
                        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-3">
                            {quietList.map((c) => (
                                <button
                                    key={c.customerId}
                                    onClick={() => openCustomer(c)}
                                    className="text-left p-3 rounded-xl bg-rose-500/5 hover:bg-rose-500/10 border border-rose-500/20 transition-colors"
                                >
                                    <div className="flex items-center gap-2">
                                        <span className="text-sm font-bold text-foreground">{c.name}</span>
                                        <Badge variant={c.partyType === "STATEMENT" ? "default" : "outline"} className="text-[9px]">
                                            {c.partyType === "STATEMENT" ? "Statement" : "Local"}
                                        </Badge>
                                    </div>
                                    <p className="text-xs text-muted-foreground mt-1">
                                        Usually fuels every ~{c.typicalIntervalDays}d &middot;{" "}
                                        <span className="font-semibold text-rose-400">silent {c.daysSinceLastBill}d</span>
                                        {c.lastBillDate ? ` · last fill ${c.lastBillDate}` : ""}
                                    </p>
                                    {c.outstanding > 0 && (
                                        <p className="text-xs font-semibold text-amber-500 mt-0.5">
                                            &#8377;{formatCompact(c.outstanding)} outstanding
                                        </p>
                                    )}
                                </button>
                            ))}
                        </div>
                    </GlassCard>
                )}

                {/* Customer table */}
                <GlassCard className="overflow-hidden border-none p-0">
                    <div className="px-6 py-4 border-b border-border/50 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
                        <div className="flex items-center gap-2">
                            <div className="p-2 bg-primary/10 rounded-lg">
                                <Users className="w-5 h-5 text-primary" />
                            </div>
                            <div>
                                <h2 className="text-lg font-semibold text-foreground">Customer Insights</h2>
                                <p className="text-xs text-muted-foreground">Click a customer to open their full transactions by year</p>
                            </div>
                        </div>
                        <div className="flex flex-wrap gap-2">
                            {(
                                [
                                    ["all", `All (${data.customers.length})`],
                                    ["statement", `Statement (${data.customers.filter((c) => c.partyType === "STATEMENT").length})`],
                                    ["local", `Local (${data.customers.filter((c) => c.partyType === "LOCAL").length})`],
                                    ["overdue", `Overdue (${data.overdueCustomers})`],
                                    ["more", `Consuming More (${consumingMore})`],
                                    ["less", "Dropping"],
                                    ["quiet", `Quiet (${quietList.length})`],
                                ] as [Filter, string][]
                            ).map(([f, label]) => (
                                <button
                                    key={f}
                                    onClick={() => setFilter(f)}
                                    className={`px-3 py-1.5 text-xs font-medium rounded-lg transition-colors ${
                                        filter === f ? "bg-primary text-primary-foreground" : "bg-muted text-muted-foreground hover:bg-muted/80"
                                    }`}
                                >
                                    {label}
                                </button>
                            ))}
                        </div>
                    </div>
                    <div className="overflow-x-auto">
                        <table className="w-full text-left border-collapse">
                            <thead>
                                <tr className="bg-white/5 border-b border-border/50">
                                    {["Customer", "Daily (30d)", "Billed", "Liters", "Outstanding", "Allowed", "Avg Repay", "On-Time %", "Oldest Unpaid", "Usage Trend"].map((h) => (
                                        <th key={h} className="px-4 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">
                                            {h}
                                        </th>
                                    ))}
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-border/30">
                                {filtered.length === 0 ? (
                                    <tr>
                                        <td colSpan={10} className="px-4 py-10 text-center text-sm text-muted-foreground">
                                            No customers match this filter
                                        </td>
                                    </tr>
                                ) : (
                                    filtered.map((c) => (
                                        <tr
                                            key={c.customerId}
                                            onClick={() => openCustomer(c)}
                                            className="hover:bg-white/5 transition-colors cursor-pointer"
                                        >
                                            <td className="px-4 py-4">
                                                <div className="flex items-center gap-2">
                                                    <span className="text-sm font-bold text-foreground hover:text-primary">{c.name}</span>
                                                    <Badge
                                                        variant={c.partyType === "STATEMENT" ? "default" : "outline"}
                                                        className="text-[9px]"
                                                    >
                                                        {c.partyType === "STATEMENT" ? "Statement" : "Local"}
                                                    </Badge>
                                                </div>
                                                <div className="text-[10px] text-muted-foreground">{c.billCount} bills</div>
                                            </td>
                                            <td className="px-4 py-4">
                                                {c.dailyLiters?.length ? (
                                                    <HeatStrip dates={data.dailyDates ?? []} liters={c.dailyLiters} />
                                                ) : (
                                                    <span className="text-xs text-muted-foreground">—</span>
                                                )}
                                                {c.quiet && (
                                                    <div className="inline-flex items-center gap-1 text-[10px] font-semibold text-rose-400 mt-1">
                                                        <Moon className="w-3 h-3" /> silent {c.daysSinceLastBill}d
                                                    </div>
                                                )}
                                            </td>
                                            <td className="px-4 py-4 text-sm font-semibold">&#8377;{formatCompact(c.billedInRange)}</td>
                                            <td className="px-4 py-4 text-sm">{formatCompact(c.litersInRange)}</td>
                                            <td className="px-4 py-4 text-sm font-semibold text-amber-500">&#8377;{formatCompact(c.outstanding)}</td>
                                            <td className="px-4 py-4 text-sm text-muted-foreground">
                                                {c.repaymentDaysAllowed != null ? `${c.repaymentDaysAllowed}d` : "—"}
                                            </td>
                                            <td className="px-4 py-4 text-sm">{c.avgRepaymentLagDays != null ? `${c.avgRepaymentLagDays}d` : "—"}</td>
                                            <td className="px-4 py-4">
                                                {c.onTimePercent != null ? (
                                                    <span className={`text-sm font-bold ${c.onTimePercent >= 80 ? "text-green-500" : c.onTimePercent >= 50 ? "text-amber-500" : "text-red-500"}`}>
                                                        {c.onTimePercent}%
                                                    </span>
                                                ) : (
                                                    <span className="text-sm text-muted-foreground">—</span>
                                                )}
                                            </td>
                                            <td className="px-4 py-4">
                                                {c.oldestUnpaidDays != null ? (
                                                    <span className={`inline-flex items-center gap-1 text-sm font-semibold ${c.overdue ? "text-red-500" : "text-foreground"}`}>
                                                        {c.overdue && <AlertTriangle className="w-3.5 h-3.5" />}
                                                        {c.oldestUnpaidDays}d
                                                    </span>
                                                ) : (
                                                    <Badge variant="success" className="text-[10px]">Clear</Badge>
                                                )}
                                            </td>
                                            <td className="px-4 py-4">{trendChip(c)}</td>
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
