"use client";

import { useEffect, useState, useRef } from "react";
import { useRouter } from "next/navigation";
import { GlassCard } from "@/components/ui/glass-card";
import { API_BASE_URL } from "@/lib/api/station";
import { fetchWithAuth } from "@/lib/api/fetch-with-auth";
import {
    AlertTriangle, Users, IndianRupee, RefreshCw, Zap
} from "lucide-react";

interface BubbleCustomer {
    customerId: number;
    customerName: string;
    daysOverdue: number;
    unpaidAmount: number;
    unpaidCount: number;
    repaymentDays: number | null;
    status: string;
    hasSkippedBills: boolean;
    groupName: string | null;
}

interface BubbleMapBand {
    label: string;
    min: number;
    max: number;
    color: string;
    customers: BubbleCustomer[];
}

interface BubbleMapData {
    bands: BubbleMapBand[];
    totalCustomers: number;
    totalOverdueAmount: number;
}

const fmt = (n: number) =>
    n.toLocaleString("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 0 });

// Deterministic scatter for y position based on customer ID
function scatterY(customerId: number, index: number): number {
    const hash = ((customerId * 2654435761) >>> 0) % 100;
    return 15 + (hash / 100) * 70; // 15% to 85% of band height
}

// Dot size based on amount (min 10px, max 36px)
function dotSize(amount: number, maxAmount: number): number {
    if (maxAmount <= 0) return 14;
    const ratio = Math.sqrt(amount / maxAmount); // sqrt for better visual scaling
    return Math.max(10, Math.min(36, 10 + ratio * 26));
}

export default function CreditMonitoringPage() {
    const router = useRouter();
    const [data, setData] = useState<BubbleMapData | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [activeTab, setActiveTab] = useState<"local" | "statement">("local");
    const [hoveredCustomer, setHoveredCustomer] = useState<BubbleCustomer | null>(null);
    const [tooltipPos, setTooltipPos] = useState<{ x: number; y: number }>({ x: 0, y: 0 });

    const loadData = async (type: string) => {
        setIsLoading(true);
        setError(null);
        try {
            const res = await fetchWithAuth(`${API_BASE_URL}/credit/monitoring/bubble-map?type=${type}`);
            if (!res.ok) throw new Error("Failed to load data");
            setData(await res.json());
        } catch (e: any) {
            setError(e.message);
        } finally {
            setIsLoading(false);
        }
    };

    useEffect(() => { loadData(activeTab); }, [activeTab]);

    const switchTab = (tab: "local" | "statement") => {
        setActiveTab(tab);
        setHoveredCustomer(null);
    };

    // Find max amount across all bands for consistent dot sizing
    const maxAmount = data
        ? Math.max(1, ...data.bands.flatMap(b => b.customers.map(c => c.unpaidAmount || 0)))
        : 1;

    const skippedCount = data
        ? data.bands.reduce((sum, b) => sum + b.customers.filter(c => c.hasSkippedBills).length, 0)
        : 0;

    return (
        <div className="p-6 min-h-screen bg-background text-foreground">
            <div className="max-w-7xl mx-auto">
                {/* Header */}
                <div className="flex justify-between items-center mb-6">
                    <div>
                        <h1 className="text-4xl font-bold tracking-tight">
                            Credit <span className="text-gradient">Monitoring</span>
                        </h1>
                        <p className="text-muted-foreground mt-1">
                            Repayment aging map — each dot is a customer
                        </p>
                    </div>
                    <button
                        onClick={() => loadData(activeTab)}
                        className="p-2 rounded-lg hover:bg-muted transition-colors"
                        title="Refresh"
                    >
                        <RefreshCw className="w-5 h-5" />
                    </button>
                </div>

                {/* Summary Cards */}
                {data && (
                    <div className="grid grid-cols-1 md:grid-cols-4 gap-4 mb-6">
                        <GlassCard className="p-4">
                            <div className="flex items-center gap-3">
                                <div className="p-2 rounded-lg bg-primary/10">
                                    <Users className="w-5 h-5 text-primary" />
                                </div>
                                <div>
                                    <p className="text-2xl font-bold">{data.totalCustomers}</p>
                                    <p className="text-xs text-muted-foreground">
                                        {activeTab === "local" ? "Local" : "Statement"} Customers
                                    </p>
                                </div>
                            </div>
                        </GlassCard>
                        <GlassCard className="p-4">
                            <div className="flex items-center gap-3">
                                <div className="p-2 rounded-lg bg-orange-500/10">
                                    <IndianRupee className="w-5 h-5 text-orange-500" />
                                </div>
                                <div>
                                    <p className="text-2xl font-bold">{fmt(data.totalOverdueAmount || 0)}</p>
                                    <p className="text-xs text-muted-foreground">Total Unpaid</p>
                                </div>
                            </div>
                        </GlassCard>
                        <GlassCard className="p-4">
                            <div className="flex items-center gap-3">
                                <div className="p-2 rounded-lg bg-red-500/10">
                                    <AlertTriangle className="w-5 h-5 text-red-500" />
                                </div>
                                <div>
                                    <p className="text-2xl font-bold text-red-500">
                                        {data.bands.filter(b => b.min >= 31).reduce((s, b) => s + b.customers.length, 0)}
                                    </p>
                                    <p className="text-xs text-muted-foreground">30+ Days Overdue</p>
                                </div>
                            </div>
                        </GlassCard>
                        <GlassCard className="p-4">
                            <div className="flex items-center gap-3">
                                <div className="p-2 rounded-lg bg-yellow-500/10">
                                    <Zap className="w-5 h-5 text-yellow-500" />
                                </div>
                                <div>
                                    <p className="text-2xl font-bold text-yellow-500">{skippedCount}</p>
                                    <p className="text-xs text-muted-foreground">Skipped Bills</p>
                                </div>
                            </div>
                        </GlassCard>
                    </div>
                )}

                {/* Tabs */}
                <div className="flex items-center gap-3 mb-6">
                    <div className="flex bg-muted rounded-xl p-1">
                        <button
                            onClick={() => switchTab("local")}
                            className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
                                activeTab === "local"
                                    ? "bg-background text-foreground shadow-sm"
                                    : "text-muted-foreground"
                            }`}
                        >
                            Local Credit
                        </button>
                        <button
                            onClick={() => switchTab("statement")}
                            className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
                                activeTab === "statement"
                                    ? "bg-background text-foreground shadow-sm"
                                    : "text-muted-foreground"
                            }`}
                        >
                            Statement
                        </button>
                    </div>
                    <div className="flex items-center gap-4 ml-auto text-xs text-muted-foreground">
                        <span className="flex items-center gap-1.5">
                            <span className="w-3 h-3 rounded-full bg-white border border-border inline-block" />
                            Customer
                        </span>
                        <span className="flex items-center gap-1.5">
                            <span className="w-3 h-3 rounded-full bg-white border border-border inline-block animate-pulse" />
                            Skipped bills
                        </span>
                        <span className="flex items-center gap-1.5">
                            <span className="w-2 h-2 rounded-full bg-white/50 inline-block" /> Small = low amount
                        </span>
                        <span className="flex items-center gap-1.5">
                            <span className="w-4 h-4 rounded-full bg-white/80 inline-block" /> Large = high amount
                        </span>
                    </div>
                </div>

                {/* Bubble Map */}
                {isLoading ? (
                    <div className="flex items-center justify-center py-20">
                        <div className="w-10 h-10 border-4 border-primary/20 border-t-primary rounded-full animate-spin" />
                    </div>
                ) : error ? (
                    <GlassCard className="p-8 text-center">
                        <p className="text-red-500 mb-4">{error}</p>
                        <button onClick={() => loadData(activeTab)} className="btn-gradient px-6 py-2 rounded-lg">
                            Retry
                        </button>
                    </GlassCard>
                ) : data ? (
                    <div className="space-y-3 relative">
                        {data.bands.map((band) => (
                            <BandRow
                                key={band.label}
                                band={band}
                                maxAmount={maxAmount}
                                onHover={(c, x, y) => {
                                    setHoveredCustomer(c);
                                    setTooltipPos({ x, y });
                                }}
                                onLeave={() => setHoveredCustomer(null)}
                                onClick={(c) => router.push(`/payments/credit/customer/${c.customerId}`)}
                            />
                        ))}

                        {/* Floating Tooltip */}
                        {hoveredCustomer && (
                            <div
                                className="fixed z-50 pointer-events-none"
                                style={{ left: tooltipPos.x + 16, top: tooltipPos.y - 10 }}
                            >
                                <div className="bg-popover border border-border rounded-xl shadow-2xl p-3 min-w-[220px]">
                                    <div className="font-bold text-foreground text-sm">
                                        {hoveredCustomer.customerName}
                                    </div>
                                    {hoveredCustomer.groupName && (
                                        <div className="text-xs text-muted-foreground">{hoveredCustomer.groupName}</div>
                                    )}
                                    <div className="mt-2 space-y-1 text-xs">
                                        <div className="flex justify-between">
                                            <span className="text-muted-foreground">Unpaid</span>
                                            <span className="font-bold">{fmt(hoveredCustomer.unpaidAmount)}</span>
                                        </div>
                                        <div className="flex justify-between">
                                            <span className="text-muted-foreground">Bills</span>
                                            <span>{hoveredCustomer.unpaidCount}</span>
                                        </div>
                                        <div className="flex justify-between">
                                            <span className="text-muted-foreground">Days overdue</span>
                                            <span className="font-bold">{hoveredCustomer.daysOverdue}d</span>
                                        </div>
                                        {hoveredCustomer.repaymentDays != null && (
                                            <div className="flex justify-between">
                                                <span className="text-muted-foreground">Window</span>
                                                <span>{hoveredCustomer.repaymentDays}d</span>
                                            </div>
                                        )}
                                        <div className="flex justify-between">
                                            <span className="text-muted-foreground">Status</span>
                                            <span className={hoveredCustomer.status === "BLOCKED" ? "text-red-500 font-bold" : ""}>
                                                {hoveredCustomer.status}
                                            </span>
                                        </div>
                                        {hoveredCustomer.hasSkippedBills && (
                                            <div className="flex items-center gap-1 text-yellow-500 font-semibold mt-1 pt-1 border-t border-border">
                                                <Zap className="w-3 h-3" />
                                                Skipped older bills
                                            </div>
                                        )}
                                    </div>
                                </div>
                            </div>
                        )}
                    </div>
                ) : null}
            </div>
        </div>
    );
}

function BandRow({
    band,
    maxAmount,
    onHover,
    onLeave,
    onClick,
}: {
    band: BubbleMapBand;
    maxAmount: number;
    onHover: (c: BubbleCustomer, x: number, y: number) => void;
    onLeave: () => void;
    onClick: (c: BubbleCustomer) => void;
}) {
    const bandWidth = band.max - band.min || 1;
    const isEmpty = band.customers.length === 0;

    return (
        <div className="flex items-stretch gap-0">
            {/* Label */}
            <div className="w-[120px] shrink-0 flex flex-col justify-center pr-3 text-right">
                <span className="text-sm font-semibold text-foreground">{band.label}</span>
                <span className="text-[10px] text-muted-foreground">
                    {band.customers.length} customer{band.customers.length !== 1 ? "s" : ""}
                </span>
            </div>

            {/* Band */}
            <div
                className="flex-1 relative rounded-2xl overflow-hidden transition-all"
                style={{
                    backgroundColor: band.color + "18",
                    borderLeft: `4px solid ${band.color}`,
                    minHeight: isEmpty ? 48 : Math.max(80, Math.min(120, band.customers.length * 12 + 60)),
                }}
            >
                {isEmpty && (
                    <div className="absolute inset-0 flex items-center justify-center text-xs text-muted-foreground/50">
                        No customers
                    </div>
                )}
                {band.customers.map((customer, i) => {
                    const effectiveMax = band.max > 9000 ? Math.max(180, ...band.customers.map(c => c.daysOverdue)) : band.max;
                    const xPercent = bandWidth > 0
                        ? ((Math.min(customer.daysOverdue, effectiveMax) - band.min) / (effectiveMax - band.min)) * 85 + 5
                        : 50;
                    const yPercent = scatterY(customer.customerId, i);
                    const size = dotSize(customer.unpaidAmount, maxAmount);

                    return (
                        <div
                            key={customer.customerId}
                            className={`absolute rounded-full cursor-pointer transition-transform hover:scale-125 hover:z-10 ${
                                customer.hasSkippedBills ? "animate-pulse" : ""
                            }`}
                            style={{
                                left: `${xPercent}%`,
                                top: `${yPercent}%`,
                                width: size,
                                height: size,
                                transform: "translate(-50%, -50%)",
                                backgroundColor: customer.status === "BLOCKED"
                                    ? "#EF4444"
                                    : customer.hasSkippedBills
                                        ? "#FBBF24"
                                        : "rgba(255,255,255,0.9)",
                                boxShadow: `0 0 0 2px ${band.color}40, 0 2px 8px rgba(0,0,0,0.3)`,
                                border: customer.hasSkippedBills ? "2px solid #F59E0B" : "1px solid rgba(255,255,255,0.3)",
                            }}
                            onMouseEnter={(e) => onHover(customer, e.clientX, e.clientY)}
                            onMouseMove={(e) => onHover(customer, e.clientX, e.clientY)}
                            onMouseLeave={onLeave}
                            onClick={() => onClick(customer)}
                            title={customer.customerName}
                        />
                    );
                })}
            </div>
        </div>
    );
}
