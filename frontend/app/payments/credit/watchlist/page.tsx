"use client";

import { useState, useEffect } from "react";
import { GlassCard } from "@/components/ui/glass-card";
import {
    ShieldAlert, AlertTriangle, ShieldCheck, Eye, Search,
    Zap, RefreshCw, ChevronRight, IndianRupee, Clock,
    ExternalLink, Users, Building2
} from "lucide-react";
import { CreditHealthBadge } from "@/components/customers/CreditHealthBadge";
import { BlockHistory } from "@/components/customers/BlockHistory";
import {
    getWatchlist, triggerAutoBlockScan, getReconciliation,
    type CreditHealth, type ReconciliationSummary
} from "@/lib/api/station/customers";
import Link from "next/link";
import { showToast } from "@/components/ui/toast";

type RiskFilter = "all" | "HIGH" | "MEDIUM";

/** URGENT: Local/credit + Non-Govt. MONITOR: Statement + Govt */
function isUrgent(c: CreditHealth): boolean {
    const isLocal = !c.statementFrequency; // no statement frequency = local/credit customer
    const isNonGovt = c.categoryType !== "GOVERNMENT";
    return isLocal || isNonGovt;
}

export default function WatchlistPage() {
    const [watchlist, setWatchlist] = useState<CreditHealth[]>([]);
    const [loading, setLoading] = useState(true);
    const [scanning, setScanning] = useState(false);
    const [search, setSearch] = useState("");

    // Reconciliation panel
    const [selectedId, setSelectedId] = useState<number | null>(null);
    const [reconciliation, setReconciliation] = useState<ReconciliationSummary | null>(null);
    const [reconciliationLoading, setReconciliationLoading] = useState(false);

    useEffect(() => { loadWatchlist(); }, []);

    const loadWatchlist = async () => {
        setLoading(true);
        try { setWatchlist(await getWatchlist()); }
        catch (e) { console.error("Failed to load watchlist", e); }
        finally { setLoading(false); }
    };

    const handleScan = async () => {
        setScanning(true);
        try {
            const result = await triggerAutoBlockScan();
            showToast.error(`Scan complete. ${result.blockedCount} customer(s) blocked.`);
            loadWatchlist();
        } catch (e: any) { showToast.error(e.message || "Scan failed"); }
        finally { setScanning(false); }
    };

    const selectCustomer = async (id: number) => {
        setSelectedId(id);
        setReconciliationLoading(true);
        try { setReconciliation(await getReconciliation(id)); }
        catch (e) { console.error("Failed to load reconciliation", e); }
        finally { setReconciliationLoading(false); }
    };

    const fmt = (n: number) => Number(n).toLocaleString("en-IN", { minimumFractionDigits: 2 });
    const fmtCurrency = (n: number) => Number(n).toLocaleString("en-IN", { style: "currency", currency: "INR" });

    // Split into urgent vs monitor
    const allFiltered = watchlist.filter(c =>
        !search || c.customerName.toLowerCase().includes(search.toLowerCase())
    );
    const urgentList = allFiltered.filter(isUrgent);
    const monitorList = allFiltered.filter(c => !isUrgent(c));

    const highCount = watchlist.filter(c => c.riskLevel === "HIGH").length;
    const mediumCount = watchlist.filter(c => c.riskLevel === "MEDIUM").length;
    const totalAtRiskAmount = watchlist.reduce((sum, c) => sum + c.ledgerBalance, 0);
    const urgentAmount = urgentList.reduce((sum, c) => sum + c.ledgerBalance, 0);
    const monitorAmount = monitorList.reduce((sum, c) => sum + c.ledgerBalance, 0);

    if (loading) {
        return (
            <div className="p-6 flex items-center justify-center min-h-screen">
                <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary" />
            </div>
        );
    }

    return (
        <div className="p-4 min-h-screen bg-background overflow-hidden">
            <div className="w-full">
                {/* Header */}
                <div className="flex items-center justify-between mb-3">
                    <div>
                        <h1 className="text-2xl font-bold text-foreground tracking-tight">
                            Credit <span className="text-gradient">Watchlist</span>
                        </h1>
                        <p className="text-muted-foreground text-xs mt-0.5">
                            {watchlist.length} at-risk customers &middot; {urgentList.length} urgent &middot; {monitorList.length} monitoring
                        </p>
                    </div>
                    <div className="flex items-center gap-2">
                        <Link
                            href="/payments/credit"
                            className="text-xs px-3 py-1.5 rounded-lg border border-border text-muted-foreground hover:bg-muted transition-colors"
                        >
                            Credit Overview
                        </Link>
                        <button
                            onClick={handleScan}
                            disabled={scanning}
                            className="text-xs px-3 py-1.5 rounded-lg bg-rose-500/10 text-rose-400 border border-rose-500/30 hover:bg-rose-500/20 transition-colors disabled:opacity-50 flex items-center gap-1.5"
                        >
                            {scanning ? <RefreshCw className="w-3 h-3 animate-spin" /> : <Zap className="w-3 h-3" />}
                            {scanning ? "Scanning..." : "Run Block Scan"}
                        </button>
                    </div>
                </div>

                {/* KPI Cards */}
                <div className="grid grid-cols-5 gap-2 mb-3">
                    <GlassCard className="!p-3">
                        <div className="text-[10px] uppercase tracking-wider text-rose-400 flex items-center gap-1">
                            <ShieldAlert className="w-3 h-3" /> High Risk
                        </div>
                        <div className="text-xl font-bold text-rose-400 mt-0.5">{highCount}</div>
                    </GlassCard>
                    <GlassCard className="!p-3">
                        <div className="text-[10px] uppercase tracking-wider text-amber-400 flex items-center gap-1">
                            <AlertTriangle className="w-3 h-3" /> Medium Risk
                        </div>
                        <div className="text-xl font-bold text-amber-400 mt-0.5">{mediumCount}</div>
                    </GlassCard>
                    <GlassCard className="!p-3">
                        <div className="text-[10px] uppercase tracking-wider text-muted-foreground flex items-center gap-1">
                            <IndianRupee className="w-2.5 h-2.5" /> Total At-Risk
                        </div>
                        <div className="text-xl font-bold text-foreground mt-0.5">{fmtCurrency(totalAtRiskAmount)}</div>
                    </GlassCard>
                    <GlassCard className="!p-3 border-l-2 border-l-rose-500/50">
                        <div className="text-[10px] uppercase tracking-wider text-rose-300 flex items-center gap-1">
                            <Users className="w-2.5 h-2.5" /> Urgent
                        </div>
                        <div className="text-sm font-bold text-foreground mt-0.5">{fmtCurrency(urgentAmount)}</div>
                        <div className="text-[9px] text-muted-foreground">{urgentList.length} local / non-govt</div>
                    </GlassCard>
                    <GlassCard className="!p-3 border-l-2 border-l-blue-500/50">
                        <div className="text-[10px] uppercase tracking-wider text-blue-300 flex items-center gap-1">
                            <Building2 className="w-2.5 h-2.5" /> Monitor
                        </div>
                        <div className="text-sm font-bold text-foreground mt-0.5">{fmtCurrency(monitorAmount)}</div>
                        <div className="text-[9px] text-muted-foreground">{monitorList.length} stmt / govt</div>
                    </GlassCard>
                </div>

                {/* Search */}
                <div className="mb-2">
                    <div className="relative w-80">
                        <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-muted-foreground" />
                        <input
                            value={search}
                            onChange={(e) => setSearch(e.target.value)}
                            placeholder="Search customers..."
                            className="w-full pl-8 pr-3 py-1.5 text-xs rounded-lg border border-border bg-muted/30 text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-1 focus:ring-primary"
                        />
                    </div>
                </div>

                {/* Main Split: Left list + Right detail */}
                <div className="flex gap-3 overflow-hidden" style={{ height: "calc(100vh - 270px)" }}>
                    {/* Left Panel: Two-section customer list */}
                    <div className="w-[340px] min-w-[340px] max-w-[340px] flex flex-col overflow-y-auto space-y-3">
                        {/* URGENT Section */}
                        {urgentList.length > 0 && (
                            <div>
                                <div className="text-[10px] uppercase tracking-wider text-rose-400 font-medium px-1 mb-1 flex items-center gap-1">
                                    <ShieldAlert className="w-3 h-3" />
                                    Urgent — Local / Non-Government ({urgentList.length})
                                </div>
                                <div className="space-y-1">
                                    {urgentList.map(c => (
                                        <CustomerCard key={c.customerId} c={c} selected={selectedId === c.customerId} onSelect={selectCustomer} fmt={fmt} />
                                    ))}
                                </div>
                            </div>
                        )}

                        {/* MONITOR Section */}
                        {monitorList.length > 0 && (
                            <div>
                                <div className="text-[10px] uppercase tracking-wider text-blue-400 font-medium px-1 mb-1 flex items-center gap-1">
                                    <Building2 className="w-3 h-3" />
                                    Monitor — Statement / Government ({monitorList.length})
                                </div>
                                <div className="space-y-1">
                                    {monitorList.map(c => (
                                        <CustomerCard key={c.customerId} c={c} selected={selectedId === c.customerId} onSelect={selectCustomer} fmt={fmt} />
                                    ))}
                                </div>
                            </div>
                        )}

                        {urgentList.length === 0 && monitorList.length === 0 && (
                            <div className="flex flex-col items-center justify-center h-40 text-muted-foreground">
                                <ShieldCheck className="w-8 h-8 mb-2 opacity-30" />
                                <span className="text-xs">No at-risk customers</span>
                            </div>
                        )}
                    </div>

                    {/* Right Panel: Reconciliation */}
                    <div className="flex-1 min-w-0 overflow-y-auto overflow-x-hidden">
                        {selectedId === null ? (
                            <div className="flex flex-col items-center justify-center h-full text-muted-foreground">
                                <Eye className="w-12 h-12 mb-3 opacity-20" />
                                <span className="text-sm">Select a customer to view details</span>
                            </div>
                        ) : reconciliationLoading ? (
                            <div className="flex items-center justify-center h-full">
                                <div className="animate-spin rounded-full h-6 w-6 border-b-2 border-primary" />
                            </div>
                        ) : reconciliation ? (
                            <div className="space-y-3">
                                {/* Customer Header */}
                                <GlassCard className="!p-4">
                                    <div className="flex items-center justify-between">
                                        <div>
                                            <div className="flex items-center gap-2">
                                                <h2 className="text-lg font-bold text-foreground">
                                                    {reconciliation.health.customerName}
                                                </h2>
                                                <Link
                                                    href={`/payments/credit/customer/${selectedId}`}
                                                    className="text-xs px-2 py-0.5 rounded border border-primary/30 text-primary hover:bg-primary/10 transition-colors flex items-center gap-1"
                                                >
                                                    <ExternalLink className="w-3 h-3" /> Full Profile
                                                </Link>
                                            </div>
                                            <div className="flex items-center gap-2 text-xs text-muted-foreground mt-0.5">
                                                {reconciliation.categoryName && <span>{reconciliation.categoryName}</span>}
                                                {reconciliation.groupName && <span>&middot; {reconciliation.groupName}</span>}
                                                {reconciliation.statementFrequency && (
                                                    <span>&middot; Statement: {reconciliation.statementFrequency}</span>
                                                )}
                                                {!reconciliation.statementFrequency && (
                                                    <span>&middot; Local/Credit</span>
                                                )}
                                            </div>
                                        </div>
                                        <CreditHealthBadge
                                            riskLevel={reconciliation.health.riskLevel}
                                            utilizationPercent={reconciliation.health.utilizationPercent}
                                            oldestUnpaidDays={reconciliation.health.oldestUnpaidDays}
                                            size="md"
                                        />
                                    </div>
                                </GlassCard>

                                {/* Reconciliation Loop */}
                                <GlassCard className="!p-4">
                                    <h3 className="text-xs font-medium text-muted-foreground uppercase tracking-wider mb-3">
                                        Reconciliation Loop
                                    </h3>
                                    <div className="grid grid-cols-2 lg:grid-cols-4 gap-2">
                                        <div className="text-center p-2.5 rounded-lg bg-blue-500/5 border border-blue-500/20">
                                            <div className="text-[9px] text-blue-400 uppercase">Credit Limit</div>
                                            <div className="text-sm font-bold text-foreground mt-0.5">
                                                {fmtCurrency(reconciliation.health.creditLimit)}
                                            </div>
                                            {reconciliation.creditLimitLiters != null && reconciliation.creditLimitLiters > 0 && (
                                                <div className="text-[9px] text-muted-foreground">{reconciliation.creditLimitLiters.toFixed(0)} L</div>
                                            )}
                                        </div>
                                        <div className="text-center p-2.5 rounded-lg bg-violet-500/5 border border-violet-500/20">
                                            <div className="text-[9px] text-violet-400 uppercase">Total Billed</div>
                                            <div className="text-sm font-bold text-foreground mt-0.5">
                                                {fmtCurrency(reconciliation.health.totalBilled)}
                                            </div>
                                        </div>
                                        <div className="text-center p-2.5 rounded-lg bg-emerald-500/5 border border-emerald-500/20">
                                            <div className="text-[9px] text-emerald-400 uppercase">Total Paid</div>
                                            <div className="text-sm font-bold text-emerald-400 mt-0.5">
                                                {fmtCurrency(reconciliation.health.totalPaid)}
                                            </div>
                                        </div>
                                        <div className={`text-center p-2.5 rounded-lg border ${
                                            reconciliation.health.utilizationPercent >= 100 ? "bg-rose-500/5 border-rose-500/20"
                                                : reconciliation.health.utilizationPercent >= 80 ? "bg-amber-500/5 border-amber-500/20"
                                                : "bg-muted/30 border-border"
                                        }`}>
                                            <div className={`text-[9px] uppercase ${
                                                reconciliation.health.utilizationPercent >= 100 ? "text-rose-400"
                                                    : reconciliation.health.utilizationPercent >= 80 ? "text-amber-400"
                                                    : "text-muted-foreground"
                                            }`}>Balance</div>
                                            <div className={`text-sm font-bold mt-0.5 ${
                                                reconciliation.health.utilizationPercent >= 100 ? "text-rose-400"
                                                    : reconciliation.health.utilizationPercent >= 80 ? "text-amber-400"
                                                    : "text-foreground"
                                            }`}>
                                                {fmtCurrency(reconciliation.health.ledgerBalance)}
                                            </div>
                                            <div className="text-[9px] text-muted-foreground">
                                                {reconciliation.health.utilizationPercent.toFixed(1)}% utilized
                                            </div>
                                        </div>
                                    </div>

                                    {/* Aging & Action row */}
                                    <div className="flex items-center justify-between mt-2 p-2 rounded-lg bg-muted/20 border border-border/50 text-xs">
                                        <div className="flex items-center gap-1.5">
                                            <Clock className="w-3 h-3 text-muted-foreground" />
                                            <span className="text-muted-foreground">Oldest unpaid:</span>
                                            <span className={`font-semibold ${
                                                reconciliation.health.oldestUnpaidDays >= 90 ? "text-rose-400"
                                                    : reconciliation.health.oldestUnpaidDays >= 60 ? "text-amber-400"
                                                    : "text-foreground"
                                            }`}>{reconciliation.health.oldestUnpaidDays} days</span>
                                        </div>
                                        <span className="text-muted-foreground">
                                            Blocked {reconciliation.health.blockCount}x
                                        </span>
                                    </div>

                                    {/* Suggested action */}
                                    <div className={`mt-2 p-2 rounded-lg text-xs ${
                                        reconciliation.health.riskLevel === "HIGH"
                                            ? "bg-rose-500/10 text-rose-400 border border-rose-500/20"
                                            : "bg-amber-500/10 text-amber-400 border border-amber-500/20"
                                    }`}>
                                        {reconciliation.health.suggestedAction}
                                    </div>
                                </GlassCard>

                                {/* Block History */}
                                <GlassCard className="!p-4">
                                    <BlockHistory customerId={selectedId} />
                                </GlassCard>
                            </div>
                        ) : null}
                    </div>
                </div>
            </div>
        </div>
    );
}

/** Compact customer card for the list */
function CustomerCard({ c, selected, onSelect, fmt }: {
    c: CreditHealth; selected: boolean; onSelect: (id: number) => void; fmt: (n: number) => string;
}) {
    return (
        <GlassCard
            className={`!p-2.5 cursor-pointer transition-all ${
                selected ? "ring-1 ring-primary" : "hover:ring-1 hover:ring-border"
            } ${c.riskLevel === "HIGH" ? "border-l-2 border-l-rose-500" : "border-l-2 border-l-amber-500"}`}
            onClick={() => onSelect(c.customerId)}
        >
            <div className="flex items-center justify-between gap-1">
                <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-1.5">
                        <span className="font-medium text-xs text-foreground truncate">{c.customerName}</span>
                        <CreditHealthBadge riskLevel={c.riskLevel} />
                    </div>
                    <div className="text-[9px] text-muted-foreground mt-0.5">
                        {c.status === "BLOCKED" && <span className="text-rose-400 font-medium mr-1">BLOCKED</span>}
                        {c.categoryName || c.categoryType || "Uncategorized"}
                        {c.groupName && <span> &middot; {c.groupName}</span>}
                    </div>
                </div>
                <ChevronRight className="w-3.5 h-3.5 text-muted-foreground/40 flex-shrink-0" />
            </div>
            <div className="grid grid-cols-3 gap-1.5 mt-1.5 text-[9px]">
                <div>
                    <div className="text-muted-foreground">Outstanding</div>
                    <div className="font-semibold text-foreground">{fmt(c.ledgerBalance)}</div>
                </div>
                <div>
                    <div className="text-muted-foreground">Utilization</div>
                    <div className={`font-semibold ${c.utilizationPercent >= 100 ? "text-rose-400" : c.utilizationPercent >= 80 ? "text-amber-400" : "text-foreground"}`}>
                        {c.utilizationPercent.toFixed(0)}%
                    </div>
                </div>
                <div>
                    <div className="text-muted-foreground">Aging</div>
                    <div className={`font-semibold ${c.oldestUnpaidDays >= 90 ? "text-rose-400" : c.oldestUnpaidDays >= 60 ? "text-amber-400" : "text-foreground"}`}>
                        {c.oldestUnpaidDays}d
                    </div>
                </div>
            </div>
        </GlassCard>
    );
}
