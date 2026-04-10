"use client";

import { useState, useEffect } from "react";
import { GlassCard } from "@/components/ui/glass-card";
import { StyledSelect } from "@/components/ui/styled-select";
import { Badge } from "@/components/ui/badge";
import {
    Phone, Search, AlertTriangle, Clock, ChevronRight,
    Receipt, CreditCard, FileText, X, Users, IndianRupee,
    ShieldAlert, ShieldCheck, Eye
} from "lucide-react";
import {
    getCreditOverview, getCreditCustomerDetail,
    blockCustomer, unblockCustomer,
    type CreditOverview, type CreditCustomerSummary, type CreditCustomerDetail,
    type InvoiceBill, type Statement, type Payment
} from "@/lib/api/station";
import { CustomerProfileOverlay } from "@/components/customers/CustomerProfileOverlay";
import { CreditHealthBadge } from "@/components/customers/CreditHealthBadge";
import Link from "next/link";
import { showToast } from "@/components/ui/toast";

type DetailTab = "bills" | "statements" | "payments";
type ListFilter = "all" | "outstanding" | "current" | "30+" | "60+" | "90+" | "zero";

export default function CreditOverviewPage() {
    const [overview, setOverview] = useState<CreditOverview | null>(null);
    const [loading, setLoading] = useState(true);
    const [search, setSearch] = useState("");

    // Selected customer detail
    const [selectedId, setSelectedId] = useState<number | null>(null);
    const [selectedCustomer, setSelectedCustomer] = useState<CreditCustomerSummary | null>(null);
    const [detail, setDetail] = useState<CreditCustomerDetail | null>(null);
    const [detailLoading, setDetailLoading] = useState(false);
    const [activeTab, setActiveTab] = useState<DetailTab>("bills");

    // List filter
    const [listFilter, setListFilter] = useState<ListFilter>("all");
    const [categoryFilter, setCategoryFilter] = useState<string>("");

    // Profile overlay
    const [profileOpen, setProfileOpen] = useState(false);

    // Block/unblock handler
    const handleBlockToggle = async (customerId: number, currentStatus: string) => {
        try {
            if (currentStatus === "ACTIVE") {
                await blockCustomer(customerId);
            } else if (currentStatus === "BLOCKED") {
                await unblockCustomer(customerId);
            }
            // Refresh both overview and detail
            loadOverview();
            if (selectedId === customerId) {
                setDetail(await getCreditCustomerDetail(customerId));
            }
        } catch (e: any) {
            showToast.error(e.message || "Failed to update status");
        }
    };

    useEffect(() => {
        loadOverview();
    }, [categoryFilter]);

    const loadOverview = async () => {
        setLoading(true);
        try {
            setOverview(await getCreditOverview(categoryFilter || undefined));
        } catch (e) {
            console.error("Failed to load credit overview", e);
        } finally {
            setLoading(false);
        }
    };

    const selectCustomer = async (cust: CreditCustomerSummary) => {
        setSelectedId(cust.customerId);
        setSelectedCustomer(cust);
        setActiveTab("bills");
        setDetailLoading(true);
        try {
            setDetail(await getCreditCustomerDetail(cust.customerId));
        } catch (e) {
            console.error("Failed to load customer detail", e);
        } finally {
            setDetailLoading(false);
        }
    };

    const fmt = (n: number) =>
        Number(n).toLocaleString("en-IN", { minimumFractionDigits: 2 });

    const fmtCurrency = (n: number) =>
        Number(n).toLocaleString("en-IN", { style: "currency", currency: "INR" });

    const getAgingFlag = (cust: CreditCustomerSummary) => {
        if (cust.totalOutstanding === 0) return null;
        if (cust.aging90Plus > 0) return { label: "90+", color: "bg-rose-500/20 text-rose-400 border-rose-500/30" };
        if (cust.aging61to90 > 0) return { label: "60+", color: "bg-orange-500/20 text-orange-400 border-orange-500/30" };
        if (cust.aging31to60 > 0) return { label: "30+", color: "bg-amber-500/20 text-amber-400 border-amber-500/30" };
        return { label: "Current", color: "bg-emerald-500/20 text-emerald-400 border-emerald-500/30" };
    };

    const filteredCustomers = overview?.customers.filter(c => {
        const matchesSearch = !search ||
            (c.customerName && c.customerName.toLowerCase().includes(search.toLowerCase())) ||
            (c.phoneNumbers && c.phoneNumbers.some(p => p.includes(search))) ||
            (c.groupName && c.groupName.toLowerCase().includes(search.toLowerCase()));

        let matchesFilter = true;
        if (listFilter === "outstanding") matchesFilter = c.totalOutstanding > 0;
        else if (listFilter === "zero") matchesFilter = c.totalOutstanding === 0 && c.totalBillCount === 0;
        else if (listFilter === "current") matchesFilter = c.totalOutstanding > 0 && c.aging31to60 === 0 && c.aging61to90 === 0 && c.aging90Plus === 0;
        else if (listFilter === "30+") matchesFilter = c.aging31to60 > 0 || c.aging61to90 > 0 || c.aging90Plus > 0;
        else if (listFilter === "60+") matchesFilter = c.aging61to90 > 0 || c.aging90Plus > 0;
        else if (listFilter === "90+") matchesFilter = c.aging90Plus > 0;

        return matchesSearch && matchesFilter;
    }) || [];

    if (loading) {
        return (
            <div className="p-6 flex items-center justify-center min-h-screen">
                <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary" />
            </div>
        );
    }

    if (!overview) return <div className="p-6 text-muted-foreground">Failed to load data.</div>;

    return (
        <div className="p-4 min-h-screen bg-background transition-colors duration-300">
            <div className="max-w-[1600px] mx-auto">
                {/* Header */}
                <div className="flex items-center justify-between mb-3">
                    <div>
                        <h1 className="text-2xl font-bold text-foreground tracking-tight">
                            Credit <span className="text-gradient">Overview</span>
                        </h1>
                        <p className="text-muted-foreground text-xs mt-0.5">
                            {overview.totalCustomers} total customers &middot; {overview.totalCreditCustomers} with outstanding
                        </p>
                    </div>
                    <div className="flex items-center gap-2">
                        <Link
                            href="/payments/credit/watchlist"
                            className="text-xs px-3 py-1.5 rounded-lg bg-rose-500/10 text-rose-400 border border-rose-500/30 hover:bg-rose-500/20 transition-colors flex items-center gap-1.5"
                        >
                            <ShieldAlert className="w-3 h-3" />
                            Watchlist
                        </Link>
                        <button
                            onClick={loadOverview}
                            className="text-xs px-3 py-1.5 rounded-lg border border-border text-muted-foreground hover:bg-muted transition-colors"
                        >
                            Refresh
                        </button>
                    </div>
                </div>

                {/* Summary Cards */}
                <div className="grid grid-cols-2 md:grid-cols-5 gap-2 mb-3">
                    <GlassCard
                        className={`!p-3 cursor-pointer transition-all ${listFilter === "outstanding" ? "ring-1 ring-primary" : "hover:ring-1 hover:ring-border"}`}
                        onClick={() => setListFilter(listFilter === "outstanding" ? "all" : "outstanding")}
                    >
                        <div className="text-[10px] uppercase tracking-wider text-muted-foreground flex items-center gap-1">
                            <IndianRupee className="w-2.5 h-2.5" />Total Outstanding
                        </div>
                        <div className="text-lg font-bold text-foreground mt-0.5">{fmtCurrency(overview.totalOutstanding)}</div>
                        <div className="text-[10px] text-muted-foreground">{overview.totalCreditCustomers} customers</div>
                    </GlassCard>
                    <GlassCard
                        className={`!p-3 cursor-pointer transition-all ${listFilter === "current" ? "ring-1 ring-emerald-500" : "hover:ring-1 hover:ring-emerald-500/30"}`}
                        onClick={() => setListFilter(listFilter === "current" ? "all" : "current")}
                    >
                        <div className="text-[10px] uppercase tracking-wider text-emerald-400">0-30 Days</div>
                        <div className="text-lg font-bold text-emerald-400 mt-0.5">{fmt(overview.totalAging0to30)}</div>
                    </GlassCard>
                    <GlassCard
                        className={`!p-3 cursor-pointer transition-all ${listFilter === "30+" ? "ring-1 ring-amber-500" : "hover:ring-1 hover:ring-amber-500/30"}`}
                        onClick={() => setListFilter(listFilter === "30+" ? "all" : "30+")}
                    >
                        <div className="text-[10px] uppercase tracking-wider text-amber-400">31-60 Days</div>
                        <div className="text-lg font-bold text-amber-400 mt-0.5">{fmt(overview.totalAging31to60)}</div>
                    </GlassCard>
                    <GlassCard
                        className={`!p-3 cursor-pointer transition-all ${listFilter === "60+" ? "ring-1 ring-orange-500" : "hover:ring-1 hover:ring-orange-500/30"}`}
                        onClick={() => setListFilter(listFilter === "60+" ? "all" : "60+")}
                    >
                        <div className="text-[10px] uppercase tracking-wider text-orange-400">61-90 Days</div>
                        <div className="text-lg font-bold text-orange-400 mt-0.5">{fmt(overview.totalAging61to90)}</div>
                    </GlassCard>
                    <GlassCard
                        className={`!p-3 cursor-pointer transition-all ${listFilter === "90+" ? "ring-1 ring-rose-500" : "hover:ring-1 hover:ring-rose-500/30"}`}
                        onClick={() => setListFilter(listFilter === "90+" ? "all" : "90+")}
                    >
                        <div className="text-[10px] uppercase tracking-wider text-rose-400 flex items-center gap-1">
                            <AlertTriangle className="w-3 h-3" />90+ Days
                        </div>
                        <div className="text-lg font-bold text-rose-400 mt-0.5">{fmt(overview.totalAging90Plus)}</div>
                    </GlassCard>
                </div>

                {/* Main Split Layout */}
                <div className="flex gap-3 h-[calc(100vh-230px)]">
                    {/* Left Panel: Customer List */}
                    <div className="w-[380px] flex-shrink-0 flex flex-col">
                        {/* Search + filter */}
                        <div className="flex gap-1.5 mb-2">
                            <div className="relative flex-1">
                                <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-muted-foreground" />
                                <input
                                    type="text"
                                    placeholder="Search name, phone, group..."
                                    value={search}
                                    onChange={(e) => setSearch(e.target.value)}
                                    className="w-full pl-8 pr-3 py-1.5 bg-card border border-border rounded-lg text-foreground text-xs focus:outline-none focus:ring-1 focus:ring-primary/50"
                                />
                            </div>
                            <StyledSelect
                                value={categoryFilter}
                                onChange={(val) => setCategoryFilter(val)}
                                options={[
                                    { value: "", label: "All" },
                                    { value: "GOVERNMENT", label: "Govt" },
                                    { value: "NON_GOVERNMENT", label: "Non-Govt" },
                                ]}
                            />
                            {(listFilter !== "all" || search) && (
                                <button
                                    onClick={() => { setListFilter("all"); setSearch(""); }}
                                    className="px-2 py-1.5 text-[10px] bg-muted rounded-lg text-muted-foreground hover:text-foreground flex items-center gap-0.5"
                                >
                                    Clear <X className="w-2.5 h-2.5" />
                                </button>
                            )}
                        </div>

                        {/* Customer List */}
                        <GlassCard className="!p-0 flex-1 overflow-y-auto">
                            <div className="text-[10px] uppercase tracking-wider text-muted-foreground px-3 py-2 border-b border-border bg-muted/30 sticky top-0 flex items-center gap-1">
                                <Users className="w-3 h-3" />
                                {filteredCustomers.length} customers
                                {listFilter !== "all" && (
                                    <Badge variant="default" className="text-[8px] px-1 py-0 ml-1">{listFilter}</Badge>
                                )}
                            </div>
                            {filteredCustomers.map(cust => {
                                const flag = getAgingFlag(cust);
                                const isSelected = selectedId === cust.customerId;
                                const hasCredit = cust.totalBillCount > 0;
                                return (
                                    <div
                                        key={cust.customerId}
                                        onClick={() => selectCustomer(cust)}
                                        className={`px-3 py-2 border-b border-border/30 cursor-pointer transition-all ${
                                            isSelected
                                                ? "bg-primary/10 border-l-2 border-l-primary"
                                                : "hover:bg-muted/40 border-l-2 border-l-transparent"
                                        }`}
                                    >
                                        <div className="flex items-start justify-between gap-2">
                                            <div className="min-w-0 flex-1">
                                                <div className="flex items-center gap-1.5">
                                                    <span className={`text-xs font-semibold truncate ${
                                                        cust.status === "BLOCKED" ? "text-rose-400" :
                                                        cust.status === "INACTIVE" ? "text-muted-foreground" : "text-foreground"
                                                    }`}>
                                                        {cust.customerName || "Unnamed"}
                                                    </span>
                                                    {cust.riskLevel && cust.riskLevel !== "LOW" && (
                                                        <CreditHealthBadge
                                                            riskLevel={cust.riskLevel}
                                                            utilizationPercent={cust.utilizationPercent}
                                                            oldestUnpaidDays={cust.oldestUnpaidDays}
                                                        />
                                                    )}
                                                    {(!cust.riskLevel || cust.riskLevel === "LOW") && flag && (
                                                        <span className={`text-[9px] px-1 py-0 rounded border ${flag.color}`}>
                                                            {flag.label}
                                                        </span>
                                                    )}
                                                    {cust.status === "BLOCKED" && (
                                                        <span className="text-[9px] px-1 py-0 rounded border bg-rose-500/20 text-rose-400 border-rose-500/30">
                                                            Blocked
                                                        </span>
                                                    )}
                                                    {(cust.status === "ACTIVE" || cust.status === "BLOCKED") && (
                                                        <button
                                                            onClick={(e) => { e.stopPropagation(); handleBlockToggle(cust.customerId, cust.status || "ACTIVE"); }}
                                                            title={cust.status === "BLOCKED" ? "Unblock" : "Block"}
                                                            className={`ml-auto p-0.5 rounded transition-colors ${
                                                                cust.status === "BLOCKED"
                                                                    ? "text-emerald-400 hover:bg-emerald-500/10"
                                                                    : "text-rose-400 hover:bg-rose-500/10"
                                                            }`}
                                                        >
                                                            {cust.status === "BLOCKED" ? <ShieldCheck className="w-3 h-3" /> : <ShieldAlert className="w-3 h-3" />}
                                                        </button>
                                                    )}
                                                </div>
                                                <div className="flex items-center gap-2 mt-0.5">
                                                    {cust.phoneNumbers && cust.phoneNumbers.length > 0 && (
                                                        <span className="text-[10px] text-muted-foreground flex items-center gap-0.5">
                                                            <Phone className="w-2.5 h-2.5" />
                                                            {cust.phoneNumbers[0]}
                                                        </span>
                                                    )}
                                                    {cust.groupName && (
                                                        <span className="text-[10px] text-muted-foreground/70">
                                                            {cust.groupName}
                                                        </span>
                                                    )}
                                                </div>
                                                {/* Aging bar - only if outstanding */}
                                                {cust.totalOutstanding > 0 && (
                                                    <div className="flex gap-px mt-1.5 h-1 rounded overflow-hidden bg-muted/30">
                                                        {cust.aging0to30 > 0 && (
                                                            <div className="bg-emerald-500 rounded-sm"
                                                                style={{ width: `${(cust.aging0to30 / cust.totalOutstanding) * 100}%` }} />
                                                        )}
                                                        {cust.aging31to60 > 0 && (
                                                            <div className="bg-amber-500 rounded-sm"
                                                                style={{ width: `${(cust.aging31to60 / cust.totalOutstanding) * 100}%` }} />
                                                        )}
                                                        {cust.aging61to90 > 0 && (
                                                            <div className="bg-orange-500 rounded-sm"
                                                                style={{ width: `${(cust.aging61to90 / cust.totalOutstanding) * 100}%` }} />
                                                        )}
                                                        {cust.aging90Plus > 0 && (
                                                            <div className="bg-rose-500 rounded-sm"
                                                                style={{ width: `${(cust.aging90Plus / cust.totalOutstanding) * 100}%` }} />
                                                        )}
                                                    </div>
                                                )}
                                            </div>
                                            <div className="text-right flex-shrink-0">
                                                {hasCredit ? (
                                                    <>
                                                        <div className={`text-xs font-bold ${cust.totalOutstanding > 0 ? "text-foreground" : "text-emerald-400"}`}>
                                                            {fmt(cust.totalOutstanding)}
                                                        </div>
                                                        <div className="text-[10px] text-muted-foreground">
                                                            {cust.pendingBillCount > 0
                                                                ? `${cust.pendingBillCount} pending`
                                                                : `${cust.totalBillCount} bills`}
                                                        </div>
                                                    </>
                                                ) : (
                                                    <div className="text-[10px] text-muted-foreground/50">No credit</div>
                                                )}
                                            </div>
                                        </div>
                                    </div>
                                );
                            })}
                            {filteredCustomers.length === 0 && (
                                <div className="px-3 py-8 text-center text-muted-foreground text-xs">
                                    No customers match filters
                                </div>
                            )}
                        </GlassCard>
                    </div>

                    {/* Right Panel: Customer Detail */}
                    <div className="flex-1 min-w-0 flex flex-col">
                        {!selectedId ? (
                            <GlassCard className="flex-1 flex items-center justify-center">
                                <div className="text-center text-muted-foreground">
                                    <ChevronRight className="w-8 h-8 mx-auto mb-2 opacity-30" />
                                    <p className="text-sm">Select a customer to view details</p>
                                </div>
                            </GlassCard>
                        ) : detailLoading ? (
                            <GlassCard className="flex-1 flex items-center justify-center">
                                <div className="animate-spin rounded-full h-6 w-6 border-b-2 border-primary" />
                            </GlassCard>
                        ) : detail && selectedCustomer ? (
                            <div className="flex flex-col flex-1 gap-2 overflow-hidden">
                                {/* Customer Header with summary stats */}
                                <GlassCard className="!p-3">
                                    <div className="flex items-start justify-between mb-2">
                                        <div>
                                            <div className="flex items-center gap-2">
                                                <h2 className="text-sm font-bold text-foreground">{selectedCustomer.customerName}</h2>
                                                {selectedCustomer.status === "BLOCKED" && (
                                                    <Badge variant="danger" className="text-[9px] px-1 py-0">Blocked</Badge>
                                                )}
                                                {selectedCustomer.status === "INACTIVE" && (
                                                    <Badge variant="warning" className="text-[9px] px-1 py-0">Inactive</Badge>
                                                )}
                                            </div>
                                            <div className="flex items-center gap-3 mt-0.5 text-[10px] text-muted-foreground">
                                                {selectedCustomer.phoneNumbers && selectedCustomer.phoneNumbers.length > 0 && (
                                                    <span className="flex items-center gap-0.5">
                                                        <Phone className="w-2.5 h-2.5" />
                                                        {selectedCustomer.phoneNumbers.join(", ")}
                                                    </span>
                                                )}
                                                {selectedCustomer.groupName && <span>{selectedCustomer.groupName}</span>}
                                                {selectedCustomer.creditLimitAmount != null && Number(selectedCustomer.creditLimitAmount) > 0 && (
                                                    <span>Limit: {fmtCurrency(selectedCustomer.creditLimitAmount)}</span>
                                                )}
                                            </div>
                                        </div>
                                        <div className="flex items-center gap-1.5">
                                            <button
                                                onClick={() => setProfileOpen(true)}
                                                className="flex items-center gap-1 px-2 py-1 text-[10px] font-medium rounded-md border border-border text-muted-foreground hover:text-foreground hover:bg-muted transition-colors"
                                            >
                                                <Eye className="w-3 h-3" /> Profile
                                            </button>
                                            {(selectedCustomer.status === "ACTIVE" || selectedCustomer.status === "BLOCKED") && (
                                                <button
                                                    onClick={() => handleBlockToggle(selectedCustomer.customerId, selectedCustomer.status || "ACTIVE")}
                                                    className={`flex items-center gap-1 px-2 py-1 text-[10px] font-medium rounded-md transition-colors ${
                                                        selectedCustomer.status === "BLOCKED"
                                                            ? "bg-emerald-500/10 text-emerald-400 hover:bg-emerald-500/20 border border-emerald-500/30"
                                                            : "bg-rose-500/10 text-rose-400 hover:bg-rose-500/20 border border-rose-500/30"
                                                    }`}
                                                >
                                                    {selectedCustomer.status === "BLOCKED"
                                                        ? <><ShieldCheck className="w-3 h-3" /> Unblock</>
                                                        : <><ShieldAlert className="w-3 h-3" /> Block</>
                                                    }
                                                </button>
                                            )}
                                        </div>
                                    </div>
                                    <div className="grid grid-cols-4 gap-3">
                                        <div className="bg-muted/30 rounded-md px-2.5 py-1.5">
                                            <div className="text-[9px] uppercase text-muted-foreground">Total Billed</div>
                                            <div className="text-xs font-bold text-foreground">{fmt(selectedCustomer.totalBilled)}</div>
                                        </div>
                                        <div className="bg-muted/30 rounded-md px-2.5 py-1.5">
                                            <div className="text-[9px] uppercase text-emerald-400">Total Paid</div>
                                            <div className="text-xs font-bold text-emerald-400">{fmt(selectedCustomer.totalPaid)}</div>
                                        </div>
                                        <div className="bg-muted/30 rounded-md px-2.5 py-1.5">
                                            <div className="text-[9px] uppercase text-amber-400">Ledger Balance</div>
                                            <div className="text-xs font-bold text-amber-400">{fmt(selectedCustomer.ledgerBalance)}</div>
                                        </div>
                                        <div className="bg-muted/30 rounded-md px-2.5 py-1.5">
                                            <div className="text-[9px] uppercase text-muted-foreground">Unpaid Bills</div>
                                            <div className="text-xs font-bold text-foreground">
                                                {selectedCustomer.pendingBillCount} / {selectedCustomer.totalBillCount}
                                            </div>
                                        </div>
                                    </div>
                                </GlassCard>

                                {/* Tabs */}
                                <div className="flex gap-1">
                                    {([
                                        { key: "bills" as DetailTab, label: "Credit Bills", icon: FileText, count: (detail.unpaidBills.length + detail.paidBills.length) },
                                        { key: "statements" as DetailTab, label: "Statements", icon: Receipt, count: detail.statements.length },
                                        { key: "payments" as DetailTab, label: "Payments", icon: CreditCard, count: detail.payments.length },
                                    ]).map(tab => (
                                        <button
                                            key={tab.key}
                                            onClick={() => setActiveTab(tab.key)}
                                            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-t-lg text-xs font-medium transition-colors ${
                                                activeTab === tab.key
                                                    ? "bg-card text-foreground border border-border border-b-transparent"
                                                    : "text-muted-foreground hover:text-foreground"
                                            }`}
                                        >
                                            <tab.icon className="w-3 h-3" />
                                            {tab.label}
                                            <span className="text-[10px] bg-muted px-1 rounded">{tab.count}</span>
                                        </button>
                                    ))}
                                </div>

                                {/* Tab Content */}
                                <GlassCard className="!p-0 flex-1 overflow-y-auto !rounded-tl-none">
                                    {activeTab === "bills" && (
                                        <BillsTable unpaidBills={detail.unpaidBills} paidBills={detail.paidBills} />
                                    )}
                                    {activeTab === "statements" && (
                                        <StatementsTable statements={detail.statements} />
                                    )}
                                    {activeTab === "payments" && (
                                        <PaymentsTable payments={detail.payments} />
                                    )}
                                </GlassCard>
                            </div>
                        ) : null}
                    </div>
                </div>
            </div>

            {/* Customer Profile Overlay */}
            <CustomerProfileOverlay
                isOpen={profileOpen}
                onClose={() => setProfileOpen(false)}
                customer={selectedCustomer}
                detail={detail}
                onBlockStatusChange={() => {
                    loadOverview();
                    if (selectedId && selectedCustomer) {
                        getCreditCustomerDetail(selectedCustomer.customerId).then(setDetail);
                    }
                }}
            />
        </div>
    );
}

// --- Sub-components ---

function BillsTable({ unpaidBills, paidBills }: { unpaidBills: InvoiceBill[]; paidBills: InvoiceBill[] }) {
    const [showPaid, setShowPaid] = useState(false);
    const bills = showPaid ? [...unpaidBills, ...paidBills] : unpaidBills;
    const now = new Date();

    if (unpaidBills.length === 0 && paidBills.length === 0) {
        return <div className="p-6 text-center text-muted-foreground text-xs">No credit bills</div>;
    }

    return (
        <div>
            {/* Toggle */}
            <div className="px-3 py-1.5 border-b border-border bg-muted/20 flex items-center justify-between">
                <span className="text-[10px] text-muted-foreground">
                    {unpaidBills.length} unpaid{paidBills.length > 0 && ` · ${paidBills.length} paid`}
                </span>
                {paidBills.length > 0 && (
                    <button
                        onClick={() => setShowPaid(!showPaid)}
                        className="text-[10px] text-primary hover:underline"
                    >
                        {showPaid ? "Hide paid" : "Show paid"}
                    </button>
                )}
            </div>
            <table className="w-full text-[11px]">
                <thead>
                    <tr className="border-b border-border text-muted-foreground sticky top-0 bg-card">
                        <th className="text-left py-1.5 px-3 font-medium">Date</th>
                        <th className="text-left py-1.5 px-3 font-medium">Vehicle</th>
                        <th className="text-left py-1.5 px-3 font-medium">Products</th>
                        <th className="text-left py-1.5 px-3 font-medium">Indent</th>
                        <th className="text-left py-1.5 px-3 font-medium">Driver</th>
                        <th className="text-right py-1.5 px-3 font-medium">Amount</th>
                        <th className="text-center py-1.5 px-3 font-medium">Age</th>
                        <th className="text-center py-1.5 px-3 font-medium">Status</th>
                    </tr>
                </thead>
                <tbody>
                    {bills.map(bill => {
                        const billDate = bill.date ? new Date(bill.date) : null;
                        const daysOld = billDate ? Math.floor((now.getTime() - billDate.getTime()) / (1000 * 60 * 60 * 24)) : 0;
                        const isPaid = bill.paymentStatus === "PAID";
                        const ageColor = isPaid ? "text-muted-foreground/50" :
                            daysOld > 90 ? "text-rose-400" : daysOld > 60 ? "text-orange-400" : daysOld > 30 ? "text-amber-400" : "text-muted-foreground";

                        return (
                            <tr key={bill.id} className={`border-b border-border/20 hover:bg-muted/30 transition-colors ${isPaid ? "opacity-50" : ""}`}>
                                <td className="py-1.5 px-3">
                                    {billDate ? billDate.toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "2-digit" }) : "-"}
                                </td>
                                <td className="py-1.5 px-3 font-medium">{bill.vehicle?.vehicleNumber || "-"}</td>
                                <td className="py-1.5 px-3 text-muted-foreground max-w-[140px] truncate">
                                    {bill.products?.map(p => p.productName).filter(Boolean).join(", ") || "-"}
                                </td>
                                <td className="py-1.5 px-3">{bill.indentNo || "-"}</td>
                                <td className="py-1.5 px-3 text-muted-foreground">{bill.driverName || "-"}</td>
                                <td className="py-1.5 px-3 text-right font-semibold">
                                    {Number(bill.netAmount).toLocaleString("en-IN", { minimumFractionDigits: 2 })}
                                </td>
                                <td className={`py-1.5 px-3 text-center font-medium ${ageColor}`}>
                                    <span className="flex items-center justify-center gap-0.5">
                                        <Clock className="w-2.5 h-2.5" />{daysOld}d
                                    </span>
                                </td>
                                <td className="py-1.5 px-3 text-center">
                                    {isPaid ? (
                                        <Badge variant="success" className="text-[9px] px-1 py-0">Paid</Badge>
                                    ) : bill.statement ? (
                                        <Badge variant="default" className="text-[9px] px-1 py-0">In Stmt</Badge>
                                    ) : (
                                        <Badge variant="warning" className="text-[9px] px-1 py-0">Unpaid</Badge>
                                    )}
                                </td>
                            </tr>
                        );
                    })}
                </tbody>
                {unpaidBills.length > 0 && (
                    <tfoot>
                        <tr className="border-t border-border bg-muted/20">
                            <td colSpan={5} className="py-1.5 px-3 text-right font-semibold text-muted-foreground text-[10px]">
                                Unpaid Total
                            </td>
                            <td className="py-1.5 px-3 text-right font-bold text-foreground">
                                {unpaidBills.reduce((s, b) => s + Number(b.netAmount || 0), 0).toLocaleString("en-IN", { minimumFractionDigits: 2 })}
                            </td>
                            <td colSpan={2}></td>
                        </tr>
                    </tfoot>
                )}
            </table>
        </div>
    );
}

function StatementsTable({ statements }: { statements: Statement[] }) {
    if (statements.length === 0) {
        return <div className="p-6 text-center text-muted-foreground text-xs">No statements</div>;
    }

    return (
        <table className="w-full text-[11px]">
            <thead>
                <tr className="border-b border-border text-muted-foreground sticky top-0 bg-card">
                    <th className="text-left py-1.5 px-3 font-medium">Stmt #</th>
                    <th className="text-left py-1.5 px-3 font-medium">Period</th>
                    <th className="text-left py-1.5 px-3 font-medium">Date</th>
                    <th className="text-right py-1.5 px-3 font-medium">Bills</th>
                    <th className="text-right py-1.5 px-3 font-medium">Net Amount</th>
                    <th className="text-right py-1.5 px-3 font-medium">Received</th>
                    <th className="text-right py-1.5 px-3 font-medium">Balance</th>
                    <th className="text-center py-1.5 px-3 font-medium">Status</th>
                </tr>
            </thead>
            <tbody>
                {statements.map(stmt => (
                    <tr key={stmt.id} className={`border-b border-border/20 hover:bg-muted/30 transition-colors ${stmt.status === "PAID" ? "opacity-50" : ""}`}>
                        <td className="py-1.5 px-3 font-mono font-semibold">{stmt.statementNo}</td>
                        <td className="py-1.5 px-3 text-muted-foreground">{stmt.fromDate} — {stmt.toDate}</td>
                        <td className="py-1.5 px-3">{stmt.statementDate}</td>
                        <td className="py-1.5 px-3 text-right">{stmt.numberOfBills}</td>
                        <td className="py-1.5 px-3 text-right font-medium">
                            {Number(stmt.netAmount).toLocaleString("en-IN", { minimumFractionDigits: 2 })}
                        </td>
                        <td className="py-1.5 px-3 text-right text-emerald-400">
                            {Number(stmt.receivedAmount).toLocaleString("en-IN", { minimumFractionDigits: 2 })}
                        </td>
                        <td className="py-1.5 px-3 text-right font-semibold text-amber-400">
                            {Number(stmt.balanceAmount).toLocaleString("en-IN", { minimumFractionDigits: 2 })}
                        </td>
                        <td className="py-1.5 px-3 text-center">
                            <Badge variant={stmt.status === "PAID" ? "success" : "warning"} className="text-[9px] px-1 py-0">
                                {stmt.status === "PAID" ? "PAID" : "NOT PAID"}
                            </Badge>
                        </td>
                    </tr>
                ))}
            </tbody>
            <tfoot>
                <tr className="border-t border-border bg-muted/20">
                    <td colSpan={4} className="py-1.5 px-3 text-right font-semibold text-muted-foreground text-[10px]">Totals</td>
                    <td className="py-1.5 px-3 text-right font-bold text-foreground">
                        {statements.reduce((s, st) => s + Number(st.netAmount || 0), 0).toLocaleString("en-IN", { minimumFractionDigits: 2 })}
                    </td>
                    <td className="py-1.5 px-3 text-right font-bold text-emerald-400">
                        {statements.reduce((s, st) => s + Number(st.receivedAmount || 0), 0).toLocaleString("en-IN", { minimumFractionDigits: 2 })}
                    </td>
                    <td className="py-1.5 px-3 text-right font-bold text-amber-400">
                        {statements.reduce((s, st) => s + Number(st.balanceAmount || 0), 0).toLocaleString("en-IN", { minimumFractionDigits: 2 })}
                    </td>
                    <td></td>
                </tr>
            </tfoot>
        </table>
    );
}

function PaymentsTable({ payments }: { payments: Payment[] }) {
    if (payments.length === 0) {
        return <div className="p-6 text-center text-muted-foreground text-xs">No payments recorded</div>;
    }

    return (
        <table className="w-full text-[11px]">
            <thead>
                <tr className="border-b border-border text-muted-foreground sticky top-0 bg-card">
                    <th className="text-left py-1.5 px-3 font-medium">Date</th>
                    <th className="text-left py-1.5 px-3 font-medium">Paid Against</th>
                    <th className="text-right py-1.5 px-3 font-medium">Amount</th>
                    <th className="text-left py-1.5 px-3 font-medium">Mode</th>
                    <th className="text-left py-1.5 px-3 font-medium">Reference</th>
                    <th className="text-left py-1.5 px-3 font-medium">Remarks</th>
                </tr>
            </thead>
            <tbody>
                {payments.map(pmt => (
                    <tr key={pmt.id} className="border-b border-border/20 hover:bg-muted/30 transition-colors">
                        <td className="py-1.5 px-3">
                            {pmt.paymentDate
                                ? new Date(pmt.paymentDate).toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "2-digit" })
                                : "-"}
                        </td>
                        <td className="py-1.5 px-3">
                            {pmt.statement ? (
                                <span className="flex items-center gap-1">
                                    <Receipt className="w-2.5 h-2.5 text-primary" />
                                    Stmt #{pmt.statement.statementNo}
                                </span>
                            ) : pmt.invoiceBill ? (
                                <span className="flex items-center gap-1">
                                    <FileText className="w-2.5 h-2.5 text-muted-foreground" />
                                    Bill #{pmt.invoiceBill.id}
                                </span>
                            ) : "-"}
                        </td>
                        <td className="py-1.5 px-3 text-right font-semibold text-emerald-400">
                            {Number(pmt.amount).toLocaleString("en-IN", { minimumFractionDigits: 2 })}
                        </td>
                        <td className="py-1.5 px-3">
                            <Badge variant="default" className="text-[9px] px-1 py-0">
                                {pmt.paymentMode || "-"}
                            </Badge>
                        </td>
                        <td className="py-1.5 px-3 text-muted-foreground">{pmt.referenceNo || "-"}</td>
                        <td className="py-1.5 px-3 text-muted-foreground max-w-[120px] truncate">{pmt.remarks || "-"}</td>
                    </tr>
                ))}
            </tbody>
            <tfoot>
                <tr className="border-t border-border bg-muted/20">
                    <td colSpan={2} className="py-1.5 px-3 text-right font-semibold text-muted-foreground text-[10px]">Total</td>
                    <td className="py-1.5 px-3 text-right font-bold text-emerald-400">
                        {payments.reduce((s, p) => s + Number(p.amount || 0), 0).toLocaleString("en-IN", { minimumFractionDigits: 2 })}
                    </td>
                    <td colSpan={3}></td>
                </tr>
            </tfoot>
        </table>
    );
}
