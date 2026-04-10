"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { GlassCard } from "@/components/ui/glass-card";
import { API_BASE_URL } from "@/lib/api/station";
import { fetchWithAuth } from "@/lib/api/fetch-with-auth";
import {
    AlertTriangle, Users, IndianRupee, Clock, Shield, Search, RefreshCw
} from "lucide-react";

interface CreditCustomerRow {
    customerId: number;
    customerName: string;
    status: string;
    repaymentDays: number | null;
    creditLimit: number;
    groupName: string | null;
    forceUnblocked: boolean;
    unpaidCount: number;
    unpaidAmount: number;
    oldestUnpaidDate: string | null;
    daysOverdue: number;
    overdue: boolean;
}

interface DashboardData {
    localCustomers: CreditCustomerRow[];
    statementCustomers: CreditCustomerRow[];
    totalLocalOverdue: number;
    totalStatementOverdue: number;
    totalLocalAmount: number;
    totalStatementAmount: number;
}

const fmt = (n: number) => n.toLocaleString("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 0 });

function getRowColor(row: CreditCustomerRow): string {
    if (row.status === "BLOCKED") return "bg-red-500/5 border-l-4 border-l-red-500";
    if (row.overdue) return "bg-red-500/5 border-l-4 border-l-red-400";
    if (row.repaymentDays && row.daysOverdue > row.repaymentDays * 0.8) return "bg-yellow-500/5 border-l-4 border-l-yellow-500";
    return "border-l-4 border-l-transparent";
}

function getDaysLabel(row: CreditCustomerRow): { text: string; color: string } {
    if (row.repaymentDays == null) return { text: `${row.daysOverdue}d`, color: "text-muted-foreground" };
    if (row.overdue) return { text: `${row.daysOverdue}d / ${row.repaymentDays}d`, color: "text-red-500 font-bold" };
    if (row.daysOverdue > row.repaymentDays * 0.8) return { text: `${row.daysOverdue}d / ${row.repaymentDays}d`, color: "text-yellow-500 font-semibold" };
    return { text: `${row.daysOverdue}d / ${row.repaymentDays}d`, color: "text-green-500" };
}

export default function CreditMonitoringPage() {
    const router = useRouter();
    const [data, setData] = useState<DashboardData | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [activeTab, setActiveTab] = useState<"local" | "statement">("local");
    const [searchQuery, setSearchQuery] = useState("");
    const [overdueOnly, setOverdueOnly] = useState(false);

    const loadData = async () => {
        setIsLoading(true);
        setError(null);
        try {
            const res = await fetchWithAuth(`${API_BASE_URL}/credit/monitoring/dashboard`);
            if (!res.ok) throw new Error("Failed to load dashboard");
            setData(await res.json());
        } catch (e: any) {
            setError(e.message);
        } finally {
            setIsLoading(false);
        }
    };

    useEffect(() => { loadData(); }, []);

    const currentList = data ? (activeTab === "local" ? data.localCustomers : data.statementCustomers) : [];
    const filtered = currentList.filter(c => {
        if (overdueOnly && !c.overdue) return false;
        if (searchQuery) {
            const q = searchQuery.toLowerCase();
            return c.customerName?.toLowerCase().includes(q) || c.groupName?.toLowerCase().includes(q);
        }
        return true;
    });

    const totalOverdue = data ? data.totalLocalOverdue + data.totalStatementOverdue : 0;
    const totalAmount = data ? (data.totalLocalAmount || 0) + (data.totalStatementAmount || 0) : 0;

    return (
        <div className="p-6 min-h-screen bg-background text-foreground">
            <div className="max-w-7xl mx-auto">
                {/* Header */}
                <div className="flex justify-between items-center mb-6">
                    <div>
                        <h1 className="text-4xl font-bold tracking-tight">
                            Credit <span className="text-gradient">Monitoring</span>
                        </h1>
                        <p className="text-muted-foreground mt-1">Track repayment windows and overdue customers</p>
                    </div>
                    <button onClick={loadData} className="p-2 rounded-lg hover:bg-muted transition-colors" title="Refresh">
                        <RefreshCw className="w-5 h-5" />
                    </button>
                </div>

                {/* Summary Cards */}
                {data && (
                    <div className="grid grid-cols-1 md:grid-cols-4 gap-4 mb-6">
                        <GlassCard className="p-4">
                            <div className="flex items-center gap-3">
                                <div className="p-2 rounded-lg bg-red-500/10"><AlertTriangle className="w-5 h-5 text-red-500" /></div>
                                <div>
                                    <p className="text-2xl font-bold text-red-500">{totalOverdue}</p>
                                    <p className="text-xs text-muted-foreground">Overdue Customers</p>
                                </div>
                            </div>
                        </GlassCard>
                        <GlassCard className="p-4">
                            <div className="flex items-center gap-3">
                                <div className="p-2 rounded-lg bg-orange-500/10"><IndianRupee className="w-5 h-5 text-orange-500" /></div>
                                <div>
                                    <p className="text-2xl font-bold">{fmt(totalAmount)}</p>
                                    <p className="text-xs text-muted-foreground">Total Unpaid</p>
                                </div>
                            </div>
                        </GlassCard>
                        <GlassCard className="p-4">
                            <div className="flex items-center gap-3">
                                <div className="p-2 rounded-lg bg-blue-500/10"><Users className="w-5 h-5 text-blue-500" /></div>
                                <div>
                                    <p className="text-2xl font-bold">{data.localCustomers.length}</p>
                                    <p className="text-xs text-muted-foreground">Local Credit</p>
                                </div>
                            </div>
                        </GlassCard>
                        <GlassCard className="p-4">
                            <div className="flex items-center gap-3">
                                <div className="p-2 rounded-lg bg-purple-500/10"><Shield className="w-5 h-5 text-purple-500" /></div>
                                <div>
                                    <p className="text-2xl font-bold">{data.statementCustomers.length}</p>
                                    <p className="text-xs text-muted-foreground">Statement Customers</p>
                                </div>
                            </div>
                        </GlassCard>
                    </div>
                )}

                {/* Tabs + Filters */}
                <div className="flex flex-wrap items-center gap-3 mb-4">
                    <div className="flex bg-muted rounded-xl p-1">
                        <button
                            onClick={() => setActiveTab("local")}
                            className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${activeTab === "local" ? "bg-background text-foreground shadow-sm" : "text-muted-foreground"}`}
                        >
                            Local ({data?.localCustomers.length || 0})
                        </button>
                        <button
                            onClick={() => setActiveTab("statement")}
                            className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${activeTab === "statement" ? "bg-background text-foreground shadow-sm" : "text-muted-foreground"}`}
                        >
                            Statement ({data?.statementCustomers.length || 0})
                        </button>
                    </div>
                    <div className="relative flex-1 min-w-[200px] max-w-md">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                        <input
                            type="text"
                            placeholder="Search customer or group..."
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            className="w-full pl-10 pr-4 py-2.5 bg-card border border-border rounded-xl text-foreground text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                        />
                    </div>
                    <label className="flex items-center gap-2 text-sm text-muted-foreground cursor-pointer">
                        <input
                            type="checkbox"
                            checked={overdueOnly}
                            onChange={(e) => setOverdueOnly(e.target.checked)}
                            className="rounded"
                        />
                        Overdue only
                    </label>
                </div>

                {/* Table */}
                {isLoading ? (
                    <div className="flex items-center justify-center py-20">
                        <div className="w-10 h-10 border-4 border-primary/20 border-t-primary rounded-full animate-spin" />
                    </div>
                ) : error ? (
                    <GlassCard className="p-8 text-center">
                        <p className="text-red-500 mb-4">{error}</p>
                        <button onClick={loadData} className="btn-gradient px-6 py-2 rounded-lg">Retry</button>
                    </GlassCard>
                ) : filtered.length === 0 ? (
                    <GlassCard className="p-8 text-center text-muted-foreground">
                        No {overdueOnly ? "overdue " : ""}{activeTab === "local" ? "local credit" : "statement"} customers found.
                    </GlassCard>
                ) : (
                    <GlassCard>
                        <div className="overflow-x-auto">
                            <table className="w-full text-sm">
                                <thead>
                                    <tr className="border-b border-border text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                                        <th className="px-4 py-3">Customer</th>
                                        <th className="px-4 py-3">Group</th>
                                        <th className="px-4 py-3 text-right">Unpaid {activeTab === "statement" ? "Stmts" : "Bills"}</th>
                                        <th className="px-4 py-3 text-right">Amount</th>
                                        <th className="px-4 py-3">Oldest</th>
                                        <th className="px-4 py-3">Days / Window</th>
                                        <th className="px-4 py-3">Status</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {filtered.map((row) => {
                                        const days = getDaysLabel(row);
                                        return (
                                            <tr
                                                key={row.customerId}
                                                className={`border-b border-border/50 hover:bg-muted/30 transition-colors cursor-pointer ${getRowColor(row)}`}
                                                onClick={() => router.push(`/payments/credit/customer/${row.customerId}`)}
                                            >
                                                <td className="px-4 py-3">
                                                    <div className="font-semibold text-foreground">{row.customerName}</div>
                                                </td>
                                                <td className="px-4 py-3 text-muted-foreground">{row.groupName || "—"}</td>
                                                <td className="px-4 py-3 text-right font-medium">{row.unpaidCount}</td>
                                                <td className="px-4 py-3 text-right font-bold">{fmt(row.unpaidAmount || 0)}</td>
                                                <td className="px-4 py-3 text-muted-foreground text-xs">{row.oldestUnpaidDate || "—"}</td>
                                                <td className="px-4 py-3">
                                                    <span className={days.color}>{days.text}</span>
                                                    {row.repaymentDays == null && (
                                                        <span className="text-xs text-muted-foreground ml-1">(no window)</span>
                                                    )}
                                                </td>
                                                <td className="px-4 py-3">
                                                    <span className={`px-2 py-0.5 rounded-full text-[10px] font-bold uppercase ${
                                                        row.status === "BLOCKED" ? "bg-red-500/10 text-red-500 border border-red-500/20"
                                                        : row.forceUnblocked ? "bg-yellow-500/10 text-yellow-600 border border-yellow-500/20"
                                                        : "bg-green-500/10 text-green-500 border border-green-500/20"
                                                    }`}>
                                                        {row.forceUnblocked ? "FORCED" : row.status}
                                                    </span>
                                                </td>
                                            </tr>
                                        );
                                    })}
                                </tbody>
                            </table>
                        </div>
                    </GlassCard>
                )}
            </div>
        </div>
    );
}
