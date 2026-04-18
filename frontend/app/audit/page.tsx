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
    Activity,
    Calendar,
    Download,
    RefreshCw,
    Shuffle,
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
    if (preset === "today") return { from: to, to, granularity: "DAY" };
    if (preset === "7d") return { from: toDateInput(new Date(now.getTime() - 6 * 86400000)), to, granularity: "RANGE" };
    if (preset === "30d") return { from: toDateInput(new Date(now.getTime() - 29 * 86400000)), to, granularity: "RANGE" };
    if (preset === "mtd") return { from: toDateInput(new Date(now.getFullYear(), now.getMonth(), 1)), to, granularity: "MONTH" };
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
        if (!resolved) { setIsLoading(false); return; }
        getAudit(resolved.from, resolved.to, resolved.granularity)
            .then(setData)
            .catch((err) => setError(err?.message || "Failed to load audit"))
            .finally(() => setIsLoading(false));
    }, [preset, customFrom, customTo, year]);

    const cashFlow = data?.cashFlow;
    const prof = data?.profitability;
    const isCashPositive = (cashFlow?.netPosition ?? 0) >= 0;
    const isProfit = (prof?.netProfit ?? 0) >= 0;
    const productSales = data?.productSales ?? [];
    const variance = data?.variance ?? [];

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
                <button
                    onClick={() => setPreset((p) => p)}
                    className="mt-4 inline-flex items-center gap-2 px-3 py-1.5 text-sm rounded-md border border-border hover:bg-muted"
                >
                    <RefreshCw className="w-4 h-4" /> Retry
                </button>
            </div>
        );
    }

    return (
        <div className="min-h-screen bg-background p-6 md:p-8">
            <div className="max-w-7xl mx-auto space-y-6">
                {/* Header + period picker */}
                <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
                    <div>
                        <h1 className="text-4xl font-bold tracking-tight">
                            <span className="text-gradient">Bunk Audit</span>
                        </h1>
                        <p className="text-muted-foreground mt-1">
                            {preset === "yearly"
                                ? `Year ${year}`
                                : data
                                    ? `${data.fromDate} – ${data.toDate} · ${data.shiftCount} shift${data.shiftCount === 1 ? "" : "s"}`
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
                                className="flex items-center gap-2 px-4 py-2 text-sm rounded-lg border border-border bg-background hover:bg-muted/50 disabled:opacity-50"
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
                                <input type="date" value={customFrom} max={customTo}
                                    onChange={(e) => setCustomFrom(e.target.value)}
                                    className="mt-1 px-3 py-2 bg-background border border-border rounded-md text-sm" />
                            </div>
                            <div>
                                <label className="text-[10px] uppercase tracking-wider text-muted-foreground font-bold">To</label>
                                <input type="date" value={customTo} min={customFrom} max={today}
                                    onChange={(e) => setCustomTo(e.target.value)}
                                    className="mt-1 px-3 py-2 bg-background border border-border rounded-md text-sm" />
                            </div>
                        </div>
                    </GlassCard>
                )}

                {preset === "yearly" && (
                    <YearlyScorecard monthly={monthly} year={year} onYearChange={setYear} />
                )}

                {preset !== "yearly" && data && cashFlow && prof && (
                  <>
                    {/* Dual verdict: Net Cash Position | Net Profit */}
                    <div className="grid gap-4 md:grid-cols-2">
                        <GlassCard>
                            <p className="text-[10px] uppercase tracking-wider text-muted-foreground font-bold">Net Cash Position</p>
                            <div className="flex items-baseline gap-3 mt-2">
                                <h2 className={`text-4xl font-bold ${isCashPositive ? "text-green-500" : "text-red-500"}`}>
                                    &#8377;{formatCompact(cashFlow.netPosition)}
                                </h2>
                                {isCashPositive
                                    ? <TrendingUp className="w-8 h-8 text-green-500" />
                                    : <TrendingDown className="w-8 h-8 text-red-500" />}
                            </div>
                            <p className="text-sm text-muted-foreground mt-1">IN − OUT · till perspective</p>
                        </GlassCard>
                        <GlassCard>
                            <p className="text-[10px] uppercase tracking-wider text-muted-foreground font-bold">Net Profit <span className="normal-case text-muted-foreground">(accrual)</span></p>
                            <div className="flex items-baseline gap-3 mt-2">
                                <h2 className={`text-4xl font-bold ${isProfit ? "text-green-500" : "text-red-500"}`}>
                                    &#8377;{formatCompact(prof.netProfit)}
                                </h2>
                                <span className={`text-sm ${isProfit ? "text-green-500" : "text-red-500"}`}>
                                    {(prof.marginPct ?? 0).toFixed(2)}%
                                </span>
                            </div>
                            <p className="text-sm text-muted-foreground mt-1">
                                Rev &#8377;{formatCompact(prof.grossRevenue)} − COGS &#8377;{formatCompact(prof.totalCogs)} − OpEx &#8377;{formatCompact(prof.operatingExpenses)}
                            </p>
                        </GlassCard>
                    </div>

                    {/* Cash Flow panels */}
                    <div className="grid gap-4 lg:grid-cols-2">
                        <GlassCard>
                            <div className="flex items-center gap-2 mb-4">
                                <ArrowDownCircle className="w-5 h-5 text-green-500" />
                                <h3 className="text-lg font-semibold">Money IN</h3>
                            </div>
                            <div className="space-y-2 text-sm">
                                <Row label="Cash Invoices (explicit + synthetic)" value={cashFlow.in.cashInvoices} />
                                <Row label="Bill Payments" value={cashFlow.in.billPayments} />
                                <Row label="Statement Payments" value={cashFlow.in.statementPayments} />
                                <Row label="External Inflow" value={cashFlow.in.externalInflow} />
                            </div>
                        </GlassCard>
                        <GlassCard>
                            <div className="flex items-center gap-2 mb-4">
                                <ArrowUpCircle className="w-5 h-5 text-red-500" />
                                <h3 className="text-lg font-semibold">Money OUT</h3>
                            </div>
                            <div className="space-y-2 text-sm">
                                <Row label="Credit Invoices" value={cashFlow.out.creditInvoices} />
                                {(cashFlow.out.eAdvances ?? []).length > 0 && (
                                    <>
                                        <div className="text-[10px] uppercase tracking-wider text-muted-foreground pt-2">E-Advances</div>
                                        {(cashFlow.out.eAdvances ?? []).map((a) => (
                                            <Row key={a.mode} label={`  ${a.mode}`} value={a.amount} />
                                        ))}
                                    </>
                                )}
                                {(cashFlow.out.expenses ?? []).map((a) => (
                                    <Row key={a.type} label={a.type.replace("_", " ").toLowerCase()} value={a.amount} />
                                ))}
                                <Row label="Station Expenses" value={cashFlow.out.stationExpenses} />
                                <Row label="Incentives" value={cashFlow.out.incentives} />
                                <Row label="Salary Advance" value={cashFlow.out.salaryAdvance} />
                                <Row label="Cash Advance (spent)" value={cashFlow.out.cashAdvanceSpent} />
                                <Row label="Inflow Repayments" value={cashFlow.out.inflowRepayments} />
                                <Row
                                    label={`Test Quantity (${formatLitres(cashFlow.out.testQuantity?.litres)} L, info)`}
                                    value={cashFlow.out.testQuantity?.amount ?? 0}
                                    muted
                                />
                            </div>
                        </GlassCard>
                    </div>

                    {/* Internal Transfers — only if non-zero */}
                    {((cashFlow.internalTransfers.managementAdvance ?? 0) > 0
                      || (cashFlow.internalTransfers.cashAdvanceBankDeposit ?? 0) > 0) && (
                        <GlassCard>
                            <div className="flex items-center gap-2 mb-3">
                                <Shuffle className="w-5 h-5 text-indigo-400" />
                                <h3 className="text-lg font-semibold">Internal Transfers</h3>
                                <span className="text-xs text-muted-foreground">(stay with the business — excluded from IN/OUT)</span>
                            </div>
                            <div className="grid gap-2 md:grid-cols-2 text-sm">
                                <Row label="Management Advance" value={cashFlow.internalTransfers.managementAdvance} />
                                <Row label="Cash Advance → Bank Deposit" value={cashFlow.internalTransfers.cashAdvanceBankDeposit} />
                            </div>
                        </GlassCard>
                    )}

                    {/* Per-Product Margin */}
                    {productSales.length > 0 && (
                        <GlassCard>
                            <div className="flex items-center gap-2 mb-4">
                                <Activity className="w-5 h-5 text-cyan-500" />
                                <h3 className="text-lg font-semibold">Per-Product Margin (accrual)</h3>
                            </div>
                            <div className="overflow-x-auto">
                                <table className="w-full text-sm">
                                    <thead className="text-left text-muted-foreground text-xs uppercase tracking-wider border-b border-border">
                                        <tr>
                                            <th className="py-2 pr-4">Product</th>
                                            <th className="py-2 pr-4 text-right">Qty</th>
                                            <th className="py-2 pr-4 text-right">Revenue</th>
                                            <th className="py-2 pr-4 text-right">COGS</th>
                                            <th className="py-2 pr-4 text-right">Margin</th>
                                            <th className="py-2 pr-4 text-right">Margin %</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {productSales.map((p) => (
                                            <tr key={p.productName} className="border-b border-border/50">
                                                <td className="py-2 pr-4 font-medium">{p.productName}</td>
                                                <td className="py-2 pr-4 text-right">{formatLitres(p.quantity)}</td>
                                                <td className="py-2 pr-4 text-right">&#8377;{formatCurrency(p.revenue)}</td>
                                                <td className="py-2 pr-4 text-right text-amber-500">&#8377;{formatCurrency(p.cogs)}</td>
                                                <td className={`py-2 pr-4 text-right font-semibold ${p.margin >= 0 ? "text-green-500" : "text-red-500"}`}>
                                                    &#8377;{formatCurrency(p.margin)}
                                                </td>
                                                <td className={`py-2 pr-4 text-right ${p.margin >= 0 ? "text-green-500" : "text-red-500"}`}>
                                                    {(p.marginPct ?? 0).toFixed(2)}%
                                                </td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                        </GlassCard>
                    )}

                    {/* Variance */}
                    {variance.length > 0 && (
                        <GlassCard>
                            <div className="flex items-center gap-2 mb-4">
                                <AlertTriangle className="w-5 h-5 text-yellow-500" />
                                <h3 className="text-lg font-semibold">Tank vs Meter Variance</h3>
                                <span className="text-xs text-muted-foreground">(shrinkage &gt; 0.5% flagged)</span>
                            </div>
                            <div className="overflow-x-auto">
                                <table className="w-full text-sm">
                                    <thead className="text-left text-muted-foreground text-xs uppercase tracking-wider border-b border-border">
                                        <tr>
                                            <th className="py-2 pr-4">Product</th>
                                            <th className="py-2 pr-4 text-right">Tank (L)</th>
                                            <th className="py-2 pr-4 text-right">Meter (L)</th>
                                            <th className="py-2 pr-4 text-right">Shrink (L)</th>
                                            <th className="py-2 pr-4 text-right">Shrink %</th>
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
                                                    {((v.shrinkagePct ?? 0) * 100).toFixed(3)}%
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

function Row({ label, value, muted }: { label: string; value?: number | null; muted?: boolean }) {
    return (
        <div className={`flex items-center justify-between border-t border-border/30 pt-1.5 ${muted ? "text-muted-foreground" : ""}`}>
            <span className="text-foreground">{label}</span>
            <span className="font-semibold">&#8377;{formatCompact(value)}</span>
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
                                year === y ? "bg-primary text-primary-foreground" : "bg-muted text-muted-foreground hover:bg-muted/80"
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
                                <Line type="monotone" dataKey="y"
                                    stroke={totalNet >= 0 ? "#10b981" : "#ef4444"}
                                    strokeWidth={2} dot={{ r: 3 }} />
                            </LineChart>
                        </ResponsiveContainer>
                    </div>
                    {(bestMonth || worstMonth) && (
                        <div className="flex gap-6 text-xs text-muted-foreground mt-2">
                            {bestMonth && (
                                <span>Best: <span className="text-green-500 font-semibold">
                                    {MONTH_LABELS[bestMonth.month - 1]} (&#8377;{formatCompact(bestMonth.netProfit)})
                                </span></span>
                            )}
                            {worstMonth && (
                                <span>Worst: <span className="text-red-500 font-semibold">
                                    {MONTH_LABELS[worstMonth.month - 1]} (&#8377;{formatCompact(worstMonth.netProfit)})
                                </span></span>
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
                                {positive
                                    ? <TrendingUp className="w-4 h-4 text-green-500" />
                                    : <TrendingDown className="w-4 h-4 text-red-500" />}
                            </div>
                            <p className={`text-xs mt-2 ${positive ? "text-green-500" : "text-red-500"}`}>
                                {(m.marginPct ?? 0).toFixed(2)}% margin
                            </p>
                        </GlassCard>
                    );
                })}
            </div>
        </div>
    );
}
