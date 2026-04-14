"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { GlassCard } from "@/components/ui/glass-card";
import { API_BASE_URL } from "@/lib/api/station";
import { fetchWithAuth } from "@/lib/api/fetch-with-auth";
import {
    AlertTriangle, Users, IndianRupee, RefreshCw, Zap, Search, X, Receipt, Clock, CreditCard
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
    partyType: string | null;
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

interface UnpaidItem {
    id: number;
    reference: string;
    date: string | null;
    amount: number;
    vehicleNo: string | null;
    billCount: number | null;
    daysOld: number;
}

interface CustomerUnpaidDetail {
    customerId: number;
    customerName: string;
    groupName: string | null;
    status: string;
    partyType: string;
    repaymentDays: number | null;
    creditLimit: number | null;
    totalUnpaid: number;
    unpaidItems: UnpaidItem[];
}

const fmt = (n: number) =>
    n.toLocaleString("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 0 });

function dotSize(amount: number, maxAmount: number): number {
    if (maxAmount <= 0) return 14;
    const ratio = Math.sqrt(amount / maxAmount);
    return Math.max(10, Math.min(36, 10 + ratio * 26));
}

// Place dots in a grid-like pattern to avoid overlap
function layoutDots(customers: BubbleCustomer[], bandMin: number, bandMax: number, maxAmount: number): { c: BubbleCustomer; x: number; y: number; size: number }[] {
    if (customers.length === 0) return [];
    const effectiveMax = bandMax > 9000 ? Math.max(180, ...customers.map(c => c.daysOverdue)) : bandMax;
    const range = effectiveMax - bandMin || 1;

    // Sort by daysOverdue so dots flow left to right
    const sorted = [...customers].sort((a, b) => a.daysOverdue - b.daysOverdue);
    const placed: { c: BubbleCustomer; x: number; y: number; size: number }[] = [];

    for (const customer of sorted) {
        const size = dotSize(customer.unpaidAmount, maxAmount);
        const xBase = ((Math.min(customer.daysOverdue, effectiveMax) - bandMin) / range) * 85 + 5;

        // Try to find a Y position that doesn't overlap with existing dots
        let bestY = 50; // center
        let minOverlap = Infinity;

        for (let yCandidate = 20; yCandidate <= 80; yCandidate += 8) {
            let overlap = 0;
            for (const p of placed) {
                const dx = (xBase - p.x) * 10; // scale x distance (% to approx px ratio)
                const dy = (yCandidate - p.y) * 1.2;
                const dist = Math.sqrt(dx * dx + dy * dy);
                const minDist = (size + p.size) / 2 + 3; // 3px gap
                if (dist < minDist) overlap += minDist - dist;
            }
            if (overlap < minOverlap) {
                minOverlap = overlap;
                bestY = yCandidate;
                if (overlap === 0) break;
            }
        }

        placed.push({ c: customer, x: xBase, y: bestY, size });
    }

    return placed;
}

export default function CreditMonitoringPage() {
    const router = useRouter();
    const [data, setData] = useState<BubbleMapData | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [activeTab, setActiveTab] = useState<"local" | "statement">("local");
    const [hoveredCustomer, setHoveredCustomer] = useState<BubbleCustomer | null>(null);
    const [tooltipPos, setTooltipPos] = useState({ x: 0, y: 0 });

    // Search
    const [searchQuery, setSearchQuery] = useState("");
    const [searchResults, setSearchResults] = useState<BubbleCustomer[]>([]);
    const [highlightedId, setHighlightedId] = useState<number | null>(null);
    const [showSearchDropdown, setShowSearchDropdown] = useState(false);

    // Side panel
    const [selectedDetail, setSelectedDetail] = useState<CustomerUnpaidDetail | null>(null);
    const [detailLoading, setDetailLoading] = useState(false);

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

    // Search logic — filter across all bands
    useEffect(() => {
        if (!data || !searchQuery.trim()) {
            setSearchResults([]);
            setShowSearchDropdown(false);
            return;
        }
        const q = searchQuery.toLowerCase();
        const results = data.bands.flatMap(b => b.customers)
            .filter(c => c.customerName.toLowerCase().includes(q) || c.groupName?.toLowerCase().includes(q))
            .slice(0, 20);
        setSearchResults(results);
        setShowSearchDropdown(results.length > 0);
    }, [searchQuery, data]);

    const handleSearchSelect = (customer: BubbleCustomer) => {
        setHighlightedId(customer.customerId);
        setSearchQuery(customer.customerName);
        setShowSearchDropdown(false);
        loadCustomerDetail(customer.customerId);
    };

    const loadCustomerDetail = async (customerId: number) => {
        setDetailLoading(true);
        try {
            const res = await fetchWithAuth(`${API_BASE_URL}/credit/monitoring/customer/${customerId}/unpaid`);
            if (res.ok) setSelectedDetail(await res.json());
        } catch (e) {
            console.error("Failed to load customer detail", e);
        } finally {
            setDetailLoading(false);
        }
    };

    const handleDotClick = (customer: BubbleCustomer) => {
        setHighlightedId(customer.customerId);
        loadCustomerDetail(customer.customerId);
    };

    const closeSidePanel = () => {
        setSelectedDetail(null);
        setHighlightedId(null);
    };

    const switchTab = (tab: "local" | "statement") => {
        setActiveTab(tab);
        setHoveredCustomer(null);
        setHighlightedId(null);
        setSelectedDetail(null);
        setSearchQuery("");
    };

    const maxAmount = data
        ? Math.max(1, ...data.bands.flatMap(b => b.customers.map(c => c.unpaidAmount || 0)))
        : 1;

    const skippedCount = data
        ? data.bands.reduce((sum, b) => sum + b.customers.filter(c => c.hasSkippedBills).length, 0)
        : 0;

    return (
        <div className="p-6 min-h-screen bg-background text-foreground">
            <div className={`max-w-7xl mx-auto transition-all ${selectedDetail ? "mr-[380px]" : ""}`}>
                {/* Header */}
                <div className="flex justify-between items-center mb-6">
                    <div>
                        <h1 className="text-4xl font-bold tracking-tight">
                            Credit <span className="text-gradient">Monitoring</span>
                        </h1>
                        <p className="text-muted-foreground mt-1">Repayment aging map — each dot is a customer</p>
                    </div>
                    <button onClick={() => loadData(activeTab)} className="p-2 rounded-lg hover:bg-muted transition-colors">
                        <RefreshCw className="w-5 h-5" />
                    </button>
                </div>

                {/* Summary Cards */}
                {data && (
                    <div className="grid grid-cols-1 md:grid-cols-4 gap-4 mb-6">
                        <GlassCard className="p-4">
                            <div className="flex items-center gap-3">
                                <div className="p-2 rounded-lg bg-primary/10"><Users className="w-5 h-5 text-primary" /></div>
                                <div>
                                    <p className="text-2xl font-bold">{data.totalCustomers}</p>
                                    <p className="text-xs text-muted-foreground">{activeTab === "local" ? "Local" : "Statement"} Customers</p>
                                </div>
                            </div>
                        </GlassCard>
                        <GlassCard className="p-4">
                            <div className="flex items-center gap-3">
                                <div className="p-2 rounded-lg bg-orange-500/10"><IndianRupee className="w-5 h-5 text-orange-500" /></div>
                                <div>
                                    <p className="text-2xl font-bold">{fmt(data.totalOverdueAmount || 0)}</p>
                                    <p className="text-xs text-muted-foreground">Total Unpaid</p>
                                </div>
                            </div>
                        </GlassCard>
                        <GlassCard className="p-4">
                            <div className="flex items-center gap-3">
                                <div className="p-2 rounded-lg bg-red-500/10"><AlertTriangle className="w-5 h-5 text-red-500" /></div>
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
                                <div className="p-2 rounded-lg bg-yellow-500/10"><Zap className="w-5 h-5 text-yellow-500" /></div>
                                <div>
                                    <p className="text-2xl font-bold text-yellow-500">{skippedCount}</p>
                                    <p className="text-xs text-muted-foreground">Skipped Bills</p>
                                </div>
                            </div>
                        </GlassCard>
                    </div>
                )}

                {/* Tabs + Search */}
                <div className="flex flex-wrap items-center gap-3 mb-6">
                    <div className="flex bg-muted rounded-xl p-1">
                        <button onClick={() => switchTab("local")} className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${activeTab === "local" ? "bg-background text-foreground shadow-sm" : "text-muted-foreground"}`}>
                            Local Credit
                        </button>
                        <button onClick={() => switchTab("statement")} className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${activeTab === "statement" ? "bg-background text-foreground shadow-sm" : "text-muted-foreground"}`}>
                            Statement
                        </button>
                    </div>

                    {/* Search autocomplete */}
                    <div className="relative flex-1 max-w-md">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                        <input
                            type="text"
                            placeholder="Search customer..."
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            onFocus={() => searchResults.length > 0 && setShowSearchDropdown(true)}
                            className="w-full pl-10 pr-4 py-2.5 bg-card border border-border rounded-xl text-foreground text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                        />
                        {searchQuery && (
                            <button onClick={() => { setSearchQuery(""); setHighlightedId(null); }} className="absolute right-3 top-1/2 -translate-y-1/2">
                                <X className="w-4 h-4 text-muted-foreground" />
                            </button>
                        )}
                        {showSearchDropdown && (
                            <div className="absolute top-full left-0 right-0 mt-1 bg-popover border border-border rounded-xl shadow-2xl z-50 max-h-64 overflow-y-auto">
                                {searchResults.map(c => (
                                    <button
                                        key={c.customerId}
                                        onClick={() => handleSearchSelect(c)}
                                        className="w-full text-left px-4 py-2.5 hover:bg-muted/50 flex justify-between items-center text-sm border-b border-border/30 last:border-0"
                                    >
                                        <div>
                                            <span className="font-medium">{c.customerName}</span>
                                            {c.groupName && <span className="text-xs text-muted-foreground ml-2">{c.groupName}</span>}
                                        </div>
                                        <div className="text-xs text-muted-foreground">
                                            {c.daysOverdue}d · {fmt(c.unpaidAmount)}
                                        </div>
                                    </button>
                                ))}
                            </div>
                        )}
                    </div>

                    <div className="flex items-center gap-4 ml-auto text-xs text-muted-foreground">
                        <span className="flex items-center gap-1.5">
                            <span className="w-3 h-3 rounded-full bg-white border border-border inline-block" /> Customer
                        </span>
                        <span className="flex items-center gap-1.5">
                            <span className="w-3 h-3 rounded-full bg-yellow-400 border border-yellow-500 inline-block" /> Skipped
                        </span>
                    </div>
                </div>

                {/* Bubble Map */}
                {isLoading ? (
                    <div className="flex items-center justify-center py-20">
                        <div className="w-10 h-10 border-4 border-primary/20 border-t-primary rounded-full animate-spin" />
                    </div>
                ) : error ? (
                    <GlassCard className="p-4 sm:p-6 lg:p-8 text-center">
                        <p className="text-red-500 mb-4">{error}</p>
                        <button onClick={() => loadData(activeTab)} className="btn-gradient px-6 py-2 rounded-lg">Retry</button>
                    </GlassCard>
                ) : data ? (
                    <div className="space-y-3 relative">
                        {data.bands.map((band) => (
                            <BandRow
                                key={band.label}
                                band={band}
                                maxAmount={maxAmount}
                                highlightedId={highlightedId}
                                onHover={(c, x, y) => { setHoveredCustomer(c); setTooltipPos({ x, y }); }}
                                onLeave={() => setHoveredCustomer(null)}
                                onClick={handleDotClick}
                            />
                        ))}

                        {/* Floating Tooltip */}
                        {hoveredCustomer && (
                            <div className="fixed z-50 pointer-events-none" style={{ left: tooltipPos.x + 16, top: tooltipPos.y - 10 }}>
                                <div className="bg-popover border border-border rounded-xl shadow-2xl p-3 min-w-[220px]">
                                    <div className="font-bold text-foreground text-sm">{hoveredCustomer.customerName}</div>
                                    {hoveredCustomer.groupName && <div className="text-xs text-muted-foreground">{hoveredCustomer.groupName}</div>}
                                    <div className="mt-2 space-y-1 text-xs">
                                        <div className="flex justify-between"><span className="text-muted-foreground">Unpaid</span><span className="font-bold">{fmt(hoveredCustomer.unpaidAmount)}</span></div>
                                        <div className="flex justify-between"><span className="text-muted-foreground">Bills</span><span>{hoveredCustomer.unpaidCount}</span></div>
                                        <div className="flex justify-between"><span className="text-muted-foreground">Days</span><span className="font-bold">{hoveredCustomer.daysOverdue}d</span></div>
                                        {hoveredCustomer.repaymentDays != null && <div className="flex justify-between"><span className="text-muted-foreground">Window</span><span>{hoveredCustomer.repaymentDays}d</span></div>}
                                        {hoveredCustomer.hasSkippedBills && (
                                            <div className="flex items-center gap-1 text-yellow-500 font-semibold mt-1 pt-1 border-t border-border"><Zap className="w-3 h-3" />Skipped older bills</div>
                                        )}
                                    </div>
                                    <div className="mt-2 pt-2 border-t border-border text-[10px] text-muted-foreground">Click to view bills</div>
                                </div>
                            </div>
                        )}
                    </div>
                ) : null}
            </div>

            {/* Side Panel */}
            {(selectedDetail || detailLoading) && (
                <div className="fixed right-0 top-0 h-full w-[380px] bg-background border-l border-border shadow-2xl z-40 flex flex-col animate-in slide-in-from-right duration-200">
                    <div className="flex items-center justify-between p-4 border-b border-border">
                        <h3 className="font-bold text-lg">{selectedDetail?.customerName || "Loading..."}</h3>
                        <button onClick={closeSidePanel} className="p-1.5 rounded-lg hover:bg-muted"><X className="w-5 h-5" /></button>
                    </div>
                    {detailLoading ? (
                        <div className="flex-1 flex items-center justify-center"><div className="w-8 h-8 border-4 border-primary/20 border-t-primary rounded-full animate-spin" /></div>
                    ) : selectedDetail ? (
                        <div className="flex-1 overflow-y-auto p-4 space-y-4">
                            {/* Customer info */}
                            <div className="grid grid-cols-2 gap-2 text-sm">
                                <div><span className="text-muted-foreground text-xs">Group</span><p className="font-medium">{selectedDetail.groupName || "—"}</p></div>
                                <div><span className="text-muted-foreground text-xs">Type</span><p className="font-medium">{selectedDetail.partyType}</p></div>
                                <div><span className="text-muted-foreground text-xs">Status</span>
                                    <p className={`font-bold ${selectedDetail.status === "BLOCKED" ? "text-red-500" : "text-green-500"}`}>{selectedDetail.status}</p>
                                </div>
                                <div><span className="text-muted-foreground text-xs">Window</span><p className="font-medium">{selectedDetail.repaymentDays ? `${selectedDetail.repaymentDays} days` : "Not set"}</p></div>
                            </div>

                            {/* Total */}
                            <div className="bg-red-500/10 rounded-xl p-3 flex items-center justify-between">
                                <span className="text-sm text-muted-foreground">Total Unpaid</span>
                                <span className="text-xl font-bold text-red-500">{fmt(selectedDetail.totalUnpaid || 0)}</span>
                            </div>

                            {/* Unpaid items */}
                            <div>
                                <h4 className="text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-2">
                                    {selectedDetail.partyType === "Statement" ? "Unpaid Statements" : "Unpaid Bills"} ({selectedDetail.unpaidItems.length})
                                </h4>
                                <div className="space-y-2">
                                    {selectedDetail.unpaidItems.map((item) => (
                                        <div key={item.id} className={`p-3 rounded-lg border ${item.daysOld > (selectedDetail.repaymentDays || 90) ? "border-red-500/30 bg-red-500/5" : "border-border"}`}>
                                            <div className="flex justify-between items-start">
                                                <div>
                                                    <p className="font-semibold text-sm">{item.reference}</p>
                                                    {item.vehicleNo && <p className="text-xs text-muted-foreground">{item.vehicleNo}</p>}
                                                    {item.billCount != null && <p className="text-xs text-muted-foreground">{item.billCount} bills</p>}
                                                </div>
                                                <div className="text-right">
                                                    <p className="font-bold text-sm">{fmt(item.amount || 0)}</p>
                                                    <p className={`text-xs font-medium ${item.daysOld > (selectedDetail.repaymentDays || 90) ? "text-red-500" : "text-muted-foreground"}`}>
                                                        {item.daysOld}d ago
                                                    </p>
                                                </div>
                                            </div>
                                            {item.date && <p className="text-[10px] text-muted-foreground mt-1">{item.date}</p>}
                                        </div>
                                    ))}
                                </div>
                            </div>

                            {/* View full profile */}
                            <button
                                onClick={() => router.push(`/payments/credit/customer/${selectedDetail.customerId}`)}
                                className="w-full py-2.5 rounded-xl border border-border text-sm font-medium hover:bg-muted transition-colors flex items-center justify-center gap-2"
                            >
                                <CreditCard className="w-4 h-4" /> View Full Credit Profile
                            </button>
                        </div>
                    ) : null}
                </div>
            )}
        </div>
    );
}

function BandRow({
    band, maxAmount, highlightedId, onHover, onLeave, onClick,
}: {
    band: BubbleMapBand;
    maxAmount: number;
    highlightedId: number | null;
    onHover: (c: BubbleCustomer, x: number, y: number) => void;
    onLeave: () => void;
    onClick: (c: BubbleCustomer) => void;
}) {
    const isEmpty = band.customers.length === 0;
    const dots = layoutDots(band.customers, band.min, band.max, maxAmount);

    return (
        <div className="flex items-stretch gap-0">
            <div className="w-[120px] shrink-0 flex flex-col justify-center pr-3 text-right">
                <span className="text-sm font-semibold text-foreground">{band.label}</span>
                <span className="text-[10px] text-muted-foreground">{band.customers.length} customer{band.customers.length !== 1 ? "s" : ""}</span>
            </div>
            <div
                className="flex-1 relative rounded-2xl overflow-hidden transition-all"
                style={{
                    backgroundColor: band.color + "18",
                    borderLeft: `4px solid ${band.color}`,
                    minHeight: isEmpty ? 48 : Math.max(80, Math.min(140, band.customers.length * 14 + 60)),
                }}
            >
                {isEmpty && <div className="absolute inset-0 flex items-center justify-center text-xs text-muted-foreground/50">No customers</div>}
                {dots.map(({ c: customer, x: xPercent, y: yPercent, size }) => {
                    const isHighlighted = highlightedId === customer.customerId;

                    return (
                        <div
                            key={customer.customerId}
                            className={`absolute rounded-full cursor-pointer transition-all duration-300 ${
                                customer.hasSkippedBills && !isHighlighted ? "animate-pulse" : ""
                            }`}
                            style={{
                                left: `${xPercent}%`,
                                top: `${yPercent}%`,
                                width: isHighlighted ? size + 8 : size,
                                height: isHighlighted ? size + 8 : size,
                                transform: "translate(-50%, -50%)",
                                backgroundColor: customer.status === "BLOCKED" ? "#EF4444"
                                    : customer.hasSkippedBills ? "#FBBF24"
                                    : "rgba(255,255,255,0.9)",
                                boxShadow: isHighlighted
                                    ? `0 0 0 4px ${band.color}, 0 0 20px ${band.color}80`
                                    : `0 0 0 2px ${band.color}40, 0 2px 8px rgba(0,0,0,0.3)`,
                                border: isHighlighted ? `3px solid ${band.color}`
                                    : customer.hasSkippedBills ? "2px solid #F59E0B"
                                    : "1px solid rgba(255,255,255,0.3)",
                                zIndex: isHighlighted ? 20 : customer.hasSkippedBills ? 5 : 1,
                            }}
                            onMouseEnter={(e) => onHover(customer, e.clientX, e.clientY)}
                            onMouseMove={(e) => onHover(customer, e.clientX, e.clientY)}
                            onMouseLeave={onLeave}
                            onClick={() => onClick(customer)}
                        />
                    );
                })}
            </div>
        </div>
    );
}
