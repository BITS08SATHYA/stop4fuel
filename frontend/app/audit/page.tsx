"use client";

import { useEffect, useMemo, useState } from "react";
import { GlassCard } from "@/components/ui/glass-card";
import {
    getAudit,
    getAuditMonthly,
    downloadAuditPdf,
    BunkAuditReport,
    AuditGranularity,
    MonthlyAuditSummary,
} from "@/lib/api/station";
import {
    IndianRupee,
    TrendingUp,
    TrendingDown,
    ArrowDownCircle,
    ArrowUpCircle,
    AlertTriangle,
    Fuel,
    Droplet,
    Receipt,
    Wallet,
    Activity,
    Calendar,
    Download,
} from "lucide-react";
import { LineChart, Line, ResponsiveContainer } from "recharts";

type PresetRange = "today" | "7d" | "30d" | "mtd" | "custom" | "yearly";

function formatCurrency(val?: number | null) {
    if (val == null) return "0.00";
    return val.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function formatCompact(val?: number | null) {
    if (val == null) return "0";
    const abs = Math.abs(val);
    const sign = val < 0 ? "-" : "";
    if (abs >= 10000000) return `${sign}${(abs / 10000000).toFixed(2)}Cr`;
    if (abs >= 100000) return `${sign}${(abs / 100000).toFixed(2)}L`;
    if (abs >= 1000) return `${sign}${(abs / 1000).toFixed(1)}K`;
    return val.toLocaleString("en-IN", { maximumFractionDigits: 0 });
}

function formatLitres(val?: number | null) {
    if (val == null) return "0";
    return val.toLocaleString("en-IN", { maximumFractionDigits: 2 });
}

function toDateInput(d: Date) {
    return d.toISOString().split("T")[0];
}

function resolvePreset(preset: PresetRange): { from: string; to: string; granularity: AuditGranularity } | null {
    const now = new Date();
    const to = toDateInput(now);
    if (preset === "today") {
        return { from: to, to, granularity: "DAY" };
    }
    if (preset === "7d") {
        const from = toDateInput(new Date(now.getTime() - 6 * 86400000));
        return { from, to, granularity: "RANGE" };
    }
    if (preset === "30d") {
        const from = toDateInput(new Date(now.getTime() - 29 * 86400000));
        return { from, to, granularity: "RANGE" };
    }
    if (preset === "mtd") {
        const mtdStart = new Date(now.getFullYear(), now.getMonth(), 1);
        return { from: toDateInput(mtdStart), to, granularity: "MONTH" };
    }
    // custom/yearly resolved by page state, not preset
    return null;
}

const MONTH_LABELS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

export default function AuditPage() {
    const today = useMemo(() => toDateInput(new Date()), []);
    const [preset, setPreset] = useState<PresetRange>("today");
    const [customFrom, setCustomFrom] = useState<string>(today);
    const [customTo, setCustomTo] = useState<string>(today);
    const [year, setYear] = useState<number>(new Date().getFullYear());
    const [data, setData] = useState<BunkAuditReport | null>(null);
    const [monthly, setMonthly] = useState<MonthlyAuditSummary[] | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [downloading, setDownloading] = useState(false);

    useEffect(() => {
        setIsLoading(true);
        setError(null);
        if (preset === "yearly") {
            getAuditMonthly(year)
                .then(setMonthly)
                .catch((err) => setError(err?.message || "Failed to load monthly"))
                .finally(() => setIsLoading(false));
            return;
        }
        const resolved = preset === "custom"
            ? { from: customFrom, to: customTo, granularity: "RANGE" as AuditGranularity }
            : resolvePreset(preset);
        if (!resolved) {
            setIsLoading(false);
            return;
        }
        getAudit(resolved.from, resolved.to, resolved.granularity)
            .then(setData)
            .catch((err) => setError(err?.message || "Failed to load audit"))
            .finally(() => setIsLoading(false));
    }, [preset, customFrom, customTo, year]);

    const isProfit = useMemo(() => (data?.profitability?.netProfit ?? 0) >= 0, [data]);

    const handleDownloadPdf = async () => {
        const resolved = preset === "custom"
            ? { from: customFrom, to: customTo, granularity: "RANGE" as AuditGranularity }
            : resolvePreset(preset);
        if (!resolved) return;
        setDownloading(true);
        try {
            await downloadAuditPdf(resolved.from, resolved.to, resolved.granularity);
        } catch (err) {
            setError(err instanceof Error ? err.message : "Failed to download PDF");
        } finally {
            setDownloading(false);
        }
    };

    if (isLoading) {
        return (
            <div className="min-h-screen bg-background p-8 flex flex-col items-center justify-center">
                <div className="w-12 h-12 border-4 border-primary/20 border-t-primary rounded-full animate-spin mb-4" />
                <p className="text-muted-foreground animate-pulse">Auditing the bunk...</p>
            </div>
        );
    }

    if (error) {
        return (
            <div className="min-h-screen bg-background p-8 flex flex-col items-center justify-center">
                <p className="text-red-500 mb-2">Failed to load bunk audit</p>
                <p className="text-muted-foreground text-sm">{error}</p>
            </div>
        );
    }

    const allProductSales = data ? [...data.outputs.fuelSold, ...data.outputs.oilSold] : [];
    const inputs = data?.inputs;
    const outputs = data?.outputs;
    const variance = data?.variance ?? [];
    const profitability = data?.profitability;

    return (
        <div className="min-h-screen bg-background p-6 md:p-8 transition-colors duration-300">
            <div className="max-w-7xl mx-auto space-y-6">
                {/* Header + Period Picker */}
                <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
                    <div>
                        <h1 className="text-4xl font-bold text-foreground tracking-tight">
                            <span className="text-gradient">Bunk Audit</span>
                        </h1>
                        <p className="text-muted-foreground mt-1">
                            {preset === "yearly"
                                ? `Year ${year}`
                                : data
                                    ? `${data.fromDate} – ${data.toDate} • ${data.shiftCount} shift${data.shiftCount === 1 ? "" : "s"}`
                                    : "Pick a period"}
                        </p>
                    </div>
                    <div className="flex gap-2 flex-wrap items-center">
                        {(["today", "7d", "30d", "mtd", "custom", "yearly"] as PresetRange[]).map((p) => (
                            <button
                                key={p}
                                onClick={() => setPreset(p)}
                                className={`px-4 py-2 text-sm font-medium rounded-lg transition-colors ${
                                    preset === p
                                        ? "bg-primary text-primary-foreground"
                                        : "bg-muted text-muted-foreground hover:bg-muted/80"
                                }`}
                            >
                                {p === "today" ? "Today"
                                    : p === "7d" ? "7 Days"
                                    : p === "30d" ? "30 Days"
                                    : p === "mtd" ? "This Month"
                                    : p === "custom" ? "Custom"
                                    : "Yearly"}
                            </button>
                        ))}
                        {preset !== "yearly" && (
                            <button
                                onClick={handleDownloadPdf}
                                disabled={downloading || !data}
                                className="flex items-center gap-2 px-4 py-2 text-sm font-medium rounded-lg border border-border bg-background hover:bg-muted/50 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                                title="Download PDF"
                            >
                                <Download className="w-4 h-4" />
                                {downloading ? "..." : "PDF"}
                            </button>
                        )}
                    </div>
                </div>

                {preset === "custom" && (
                    <GlassCard>
                        <div className="flex flex-wrap items-end gap-4">
                            <div>
                                <label className="text-[10px] uppercase tracking-wider text-muted-foreground font-bold">From</label>
                                <input
                                    type="date"
                                    value={customFrom}
                                    max={customTo}
                                    onChange={(e) => setCustomFrom(e.target.value)}
                                    className="mt-1 px-3 py-2 bg-background border border-border rounded-md text-sm"
                                />
                            </div>
                            <div>
                                <label className="text-[10px] uppercase tracking-wider text-muted-foreground font-bold">To</label>
                                <input
                                    type="date"
                                    value={customTo}
                                    min={customFrom}
                                    max={today}
                                    onChange={(e) => setCustomTo(e.target.value)}
                                    className="mt-1 px-3 py-2 bg-background border border-border rounded-md text-sm"
                                />
                            </div>
                        </div>
                    </GlassCard>
                )}

                {preset === "yearly" && (
                    <YearlyScorecard
                        monthly={monthly}
                        year={year}
                        onYearChange={setYear}
                    />
                )}

                {preset !== "yearly" && data && profitability && inputs && outputs && (
                  <>
                {/* Profit Verdict Strip */}
                <GlassCard>
                    <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
                        <div>
                            <p className="text-[10px] uppercase tracking-wider text-muted-foreground font-bold">
                                Net Profit
                            </p>
                            <h2 className={`text-5xl font-bold mt-2 ${isProfit ? "text-green-500" : "text-red-500"}`}>
                                &#8377;{formatCompact(profitability.netProfit)}
                            </h2>
                            <p className="text-sm text-muted-foreground mt-1">
                                Margin: <span className={isProfit ? "text-green-500" : "text-red-500"}>
                                    {profitability.marginPct.toFixed(2)}%
                                </span>
                            </p>
                        </div>
                        <div className="flex items-center gap-3">
                            {isProfit ? (
                                <TrendingUp className="w-14 h-14 text-green-500" />
                            ) : (
                                <TrendingDown className="w-14 h-14 text-red-500" />
                            )}
                        </div>
                        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
                            <div>
                                <p className="text-[10px] uppercase tracking-wider text-muted-foreground">Gross Revenue</p>
                                <p className="text-lg font-semibold">&#8377;{formatCompact(profitability.grossRevenue)}</p>
                            </div>
                            <div>
                                <p className="text-[10px] uppercase tracking-wider text-muted-foreground">COGS</p>
                                <p className="text-lg font-semibold text-amber-500">&#8377;{formatCompact(profitability.totalCogs)}</p>
                            </div>
                            <div>
                                <p className="text-[10px] uppercase tracking-wider text-muted-foreground">Gross Profit</p>
                                <p className="text-lg font-semibold">&#8377;{formatCompact(profitability.grossProfit)}</p>
                            </div>
                            <div>
                                <p className="text-[10px] uppercase tracking-wider text-muted-foreground">Op Ex</p>
                                <p className="text-lg font-semibold text-red-500">&#8377;{formatCompact(profitability.operatingExpenses)}</p>
                            </div>
                        </div>
                    </div>
                </GlassCard>

                {/* Inputs & Outputs */}
                <div className="grid gap-4 lg:grid-cols-2">
                    <GlassCard>
                        <div className="flex items-center gap-2 mb-4">
                            <ArrowDownCircle className="w-5 h-5 text-green-500" />
                            <h3 className="text-lg font-semibold">Inputs</h3>
                            <span className="text-xs text-muted-foreground">(money & fuel in)</span>
                        </div>
                        <div className="space-y-4 text-sm">
                            <InputRow
                                icon={<Fuel className="w-4 h-4 text-blue-500" />}
                                label="Fuel Received"
                                items={inputs.fuelReceived.map((f) => ({
                                    key: f.productName,
                                    label: `${f.productName} — ${formatLitres(f.litres)} L`,
                                    value: `₹${formatCompact(f.purchaseAmount)}`,
                                }))}
                            />
                            <InputRow
                                icon={<Wallet className="w-4 h-4 text-indigo-500" />}
                                label="E-Advances"
                                items={inputs.eAdvances.map((a) => ({
                                    key: a.mode,
                                    label: a.mode,
                                    value: `₹${formatCompact(a.amount)}`,
                                }))}
                            />
                            <SimpleRow label="Credit Billed" value={`₹${formatCompact(inputs.creditBilled)}`} />
                            <SimpleRow label="Credit Collected" value={`₹${formatCompact(inputs.creditCollected)}`} />
                            <SimpleRow label="External Inflow" value={`₹${formatCompact(inputs.externalInflow)}`} />
                        </div>
                    </GlassCard>

                    <GlassCard>
                        <div className="flex items-center gap-2 mb-4">
                            <ArrowUpCircle className="w-5 h-5 text-red-500" />
                            <h3 className="text-lg font-semibold">Outputs</h3>
                            <span className="text-xs text-muted-foreground">(money & fuel out)</span>
                        </div>
                        <div className="space-y-4 text-sm">
                            <InputRow
                                icon={<Droplet className="w-4 h-4 text-amber-500" />}
                                label="Operational Advances"
                                items={outputs.opAdvances.map((a) => ({
                                    key: a.type,
                                    label: a.type,
                                    value: `₹${formatCompact(a.amount)}`,
                                }))}
                            />
                            <InputRow
                                icon={<Receipt className="w-4 h-4 text-orange-500" />}
                                label="Expenses"
                                items={outputs.expenses.map((a) => ({
                                    key: a.type,
                                    label: a.type,
                                    value: `₹${formatCompact(a.amount)}`,
                                }))}
                            />
                            <SimpleRow label="Station Expenses" value={`₹${formatCompact(outputs.stationExpenses)}`} />
                            <SimpleRow label="Incentives" value={`₹${formatCompact(outputs.incentives)}`} />
                            <SimpleRow
                                label="Test Quantity"
                                value={`${formatLitres(outputs.testQuantity.litres)} L / ₹${formatCompact(outputs.testQuantity.amount)}`}
                            />
                        </div>
                    </GlassCard>
                </div>

                {/* Per-Product Margin */}
                {allProductSales.length > 0 && (
                    <GlassCard>
                        <div className="flex items-center gap-2 mb-4">
                            <Activity className="w-5 h-5 text-cyan-500" />
                            <h3 className="text-lg font-semibold">Per-Product Margin</h3>
                        </div>
                        <div className="overflow-x-auto">
                            <table className="w-full text-sm">
                                <thead className="text-left text-muted-foreground text-xs uppercase tracking-wider border-b border-border">
                                    <tr>
                                        <th className="py-2 pr-4">Product</th>
                                        <th className="py-2 pr-4 text-right">Quantity</th>
                                        <th className="py-2 pr-4 text-right">Revenue</th>
                                        <th className="py-2 pr-4 text-right">COGS</th>
                                        <th className="py-2 pr-4 text-right">Margin</th>
                                        <th className="py-2 pr-4 text-right">Margin %</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {allProductSales.map((p) => (
                                        <tr key={p.productName} className="border-b border-border/50">
                                            <td className="py-2 pr-4 font-medium">{p.productName}</td>
                                            <td className="py-2 pr-4 text-right">{formatLitres(p.quantity)}</td>
                                            <td className="py-2 pr-4 text-right">&#8377;{formatCurrency(p.revenue)}</td>
                                            <td className="py-2 pr-4 text-right text-amber-500">&#8377;{formatCurrency(p.cogs)}</td>
                                            <td className={`py-2 pr-4 text-right font-semibold ${p.margin >= 0 ? "text-green-500" : "text-red-500"}`}>
                                                &#8377;{formatCurrency(p.margin)}
                                            </td>
                                            <td className={`py-2 pr-4 text-right ${p.margin >= 0 ? "text-green-500" : "text-red-500"}`}>
                                                {p.marginPct.toFixed(2)}%
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    </GlassCard>
                )}

                {/* Variance / Shrinkage */}
                {variance.length > 0 && (
                    <GlassCard>
                        <div className="flex items-center gap-2 mb-4">
                            <AlertTriangle className="w-5 h-5 text-yellow-500" />
                            <h3 className="text-lg font-semibold">Tank vs Meter Variance</h3>
                            <span className="text-xs text-muted-foreground">(shrinkage &gt; 0.5% flagged red)</span>
                        </div>
                        <div className="overflow-x-auto">
                            <table className="w-full text-sm">
                                <thead className="text-left text-muted-foreground text-xs uppercase tracking-wider border-b border-border">
                                    <tr>
                                        <th className="py-2 pr-4">Product</th>
                                        <th className="py-2 pr-4 text-right">Tank Sale (L)</th>
                                        <th className="py-2 pr-4 text-right">Meter Sale (L)</th>
                                        <th className="py-2 pr-4 text-right">Shrinkage (L)</th>
                                        <th className="py-2 pr-4 text-right">Shrinkage %</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {variance.map((v) => (
                                        <tr key={v.productName} className={`border-b border-border/50 ${v.flagged ? "bg-red-500/5" : ""}`}>
                                            <td className="py-2 pr-4 font-medium">{v.productName}</td>
                                            <td className="py-2 pr-4 text-right">{formatLitres(v.expectedLitres)}</td>
                                            <td className="py-2 pr-4 text-right">{formatLitres(v.actualLitres)}</td>
                                            <td className={`py-2 pr-4 text-right font-semibold ${v.flagged ? "text-red-500" : ""}`}>
                                                {formatLitres(v.shrinkageLitres)}
                                            </td>
                                            <td className={`py-2 pr-4 text-right ${v.flagged ? "text-red-500 font-semibold" : ""}`}>
                                                {(v.shrinkagePct * 100).toFixed(3)}%
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    </GlassCard>
                )}

                {data.shiftCount === 0 && (
                    <GlassCard>
                        <div className="flex items-center gap-3 text-muted-foreground">
                            <IndianRupee className="w-5 h-5" />
                            <p>No shifts in this period. Try a wider range.</p>
                        </div>
                    </GlassCard>
                )}
                  </>
                )}
            </div>
        </div>
    );
}

function YearlyScorecard({
    monthly,
    year,
    onYearChange,
}: {
    monthly: MonthlyAuditSummary[] | null;
    year: number;
    onYearChange: (y: number) => void;
}) {
    const thisYear = new Date().getFullYear();
    const years = Array.from({ length: 5 }, (_, i) => thisYear - i);
    const profitSeries = (monthly ?? []).map((m) => ({ x: m.month, y: m.netProfit }));
    const bestMonth = (monthly ?? []).reduce<MonthlyAuditSummary | null>(
        (best, m) => (best === null || m.netProfit > best.netProfit ? m : best), null);
    const worstMonth = (monthly ?? []).reduce<MonthlyAuditSummary | null>(
        (worst, m) => (worst === null || m.netProfit < worst.netProfit ? m : worst), null);
    const totalNet = (monthly ?? []).reduce((s, m) => s + m.netProfit, 0);
    const totalRev = (monthly ?? []).reduce((s, m) => s + m.grossRevenue, 0);

    return (
        <div className="space-y-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="flex items-center gap-2 flex-wrap">
                    <Calendar className="w-4 h-4 text-muted-foreground" />
                    {years.map((y) => (
                        <button
                            key={y}
                            onClick={() => onYearChange(y)}
                            className={`px-3 py-1.5 text-sm rounded-md ${
                                year === y
                                    ? "bg-primary text-primary-foreground"
                                    : "bg-muted text-muted-foreground hover:bg-muted/80"
                            }`}
                        >
                            {y}
                        </button>
                    ))}
                </div>
                <div className="flex gap-4 text-sm">
                    <div>
                        <span className="text-muted-foreground">Year Revenue:</span>{" "}
                        <span className="font-semibold">&#8377;{formatCompact(totalRev)}</span>
                    </div>
                    <div>
                        <span className="text-muted-foreground">Year Net:</span>{" "}
                        <span className={`font-semibold ${totalNet >= 0 ? "text-green-500" : "text-red-500"}`}>
                            &#8377;{formatCompact(totalNet)}
                        </span>
                    </div>
                </div>
            </div>

            {profitSeries.length > 0 && (
                <GlassCard>
                    <p className="text-[10px] uppercase tracking-wider text-muted-foreground font-bold mb-2">
                        Net Profit Trend · {year}
                    </p>
                    <div className="h-24">
                        <ResponsiveContainer width="100%" height="100%">
                            <LineChart data={profitSeries}>
                                <Line
                                    type="monotone"
                                    dataKey="y"
                                    stroke={totalNet >= 0 ? "#10b981" : "#ef4444"}
                                    strokeWidth={2}
                                    dot={{ r: 3 }}
                                />
                            </LineChart>
                        </ResponsiveContainer>
                    </div>
                    {(bestMonth || worstMonth) && (
                        <div className="flex gap-6 text-xs text-muted-foreground mt-2">
                            {bestMonth && (
                                <span>
                                    Best: <span className="text-green-500 font-semibold">
                                        {MONTH_LABELS[bestMonth.month - 1]} (&#8377;{formatCompact(bestMonth.netProfit)})
                                    </span>
                                </span>
                            )}
                            {worstMonth && (
                                <span>
                                    Worst: <span className="text-red-500 font-semibold">
                                        {MONTH_LABELS[worstMonth.month - 1]} (&#8377;{formatCompact(worstMonth.netProfit)})
                                    </span>
                                </span>
                            )}
                        </div>
                    )}
                </GlassCard>
            )}

            <div className="grid gap-3 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4">
                {(monthly ?? []).map((m) => {
                    const positive = m.netProfit >= 0;
                    return (
                        <GlassCard key={m.month}>
                            <div className="flex items-start justify-between">
                                <div>
                                    <p className="text-[10px] uppercase tracking-wider text-muted-foreground font-bold">
                                        {MONTH_LABELS[m.month - 1]}
                                    </p>
                                    <h4 className={`text-2xl font-bold mt-1 ${positive ? "text-green-500" : "text-red-500"}`}>
                                        &#8377;{formatCompact(m.netProfit)}
                                    </h4>
                                    <p className="text-xs text-muted-foreground mt-1">
                                        Rev &#8377;{formatCompact(m.grossRevenue)} · {m.shiftCount} shifts
                                    </p>
                                </div>
                                {positive ? (
                                    <TrendingUp className="w-4 h-4 text-green-500" />
                                ) : (
                                    <TrendingDown className="w-4 h-4 text-red-500" />
                                )}
                            </div>
                            <p className={`text-xs mt-2 ${positive ? "text-green-500" : "text-red-500"}`}>
                                {m.marginPct.toFixed(2)}% margin
                            </p>
                        </GlassCard>
                    );
                })}
            </div>
        </div>
    );
}

function InputRow({
    icon,
    label,
    items,
}: {
    icon: React.ReactNode;
    label: string;
    items: { key: string; label: string; value: string }[];
}) {
    if (items.length === 0) return null;
    return (
        <div>
            <div className="flex items-center gap-2 text-muted-foreground text-xs uppercase tracking-wider mb-2">
                {icon}
                {label}
            </div>
            <div className="space-y-1 pl-6">
                {items.map((i) => (
                    <div key={i.key} className="flex items-center justify-between">
                        <span className="text-foreground">{i.label}</span>
                        <span className="font-semibold">{i.value}</span>
                    </div>
                ))}
            </div>
        </div>
    );
}

function SimpleRow({ label, value }: { label: string; value: string }) {
    return (
        <div className="flex items-center justify-between border-t border-border/30 pt-2">
            <span className="text-muted-foreground">{label}</span>
            <span className="font-semibold">{value}</span>
        </div>
    );
}
