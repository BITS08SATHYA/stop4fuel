"use client";

import { useState, useEffect } from "react";
import { GlassCard } from "@/components/ui/glass-card";
import { Badge } from "@/components/ui/badge";
import {
    BookOpen, Search, Calendar, ArrowUpRight, ArrowDownLeft, FileText
} from "lucide-react";
import { CustomerAutocomplete } from "@/components/ui/customer-autocomplete";
import {
    getCustomers, getCustomerLedger, getOpeningBalance, downloadLedgerPdf,
    type CustomerLedger, type Customer
} from "@/lib/api/station";

export default function LedgerPage() {
    const [customers, setCustomers] = useState<Customer[]>([]);
    const [selectedCustomerId, setSelectedCustomerId] = useState<number | "">("");
    const [fromDate, setFromDate] = useState("");
    const [toDate, setToDate] = useState("");
    const [ledger, setLedger] = useState<CustomerLedger | null>(null);
    const [loading, setLoading] = useState(false);
    const [initialLoading, setInitialLoading] = useState(true);
    const [isDownloading, setIsDownloading] = useState(false);
    const [error, setError] = useState("");

    useEffect(() => {
        getCustomers(undefined, 10000).then((data) => {
            setCustomers(Array.isArray(data) ? data : data.content || []);
        }).finally(() => setInitialLoading(false));
    }, []);

    const handleSearch = async () => {
        if (!selectedCustomerId || !fromDate || !toDate) {
            setError("Please select customer and date range");
            return;
        }
        setLoading(true);
        setError("");
        try {
            const result = await getCustomerLedger(Number(selectedCustomerId), fromDate, toDate);
            setLedger(result);
        } catch (e: any) {
            setError(e.message || "Failed to load ledger");
            setLedger(null);
        } finally {
            setLoading(false);
        }
    };

    const selectedCustomer = customers.find((c: any) => c.id === Number(selectedCustomerId));

    const handleDownloadPdf = async () => {
        if (!selectedCustomerId || !fromDate || !toDate) return;
        setIsDownloading(true);
        try {
            const blob = await downloadLedgerPdf(Number(selectedCustomerId), fromDate, toDate);
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement("a");
            a.href = url;
            const name = selectedCustomer?.name?.replace(/[^a-zA-Z0-9]/g, "_") || "Customer";
            a.download = `Ledger_${name}_${fromDate}_to_${toDate}.pdf`;
            document.body.appendChild(a);
            a.click();
            a.remove();
            window.URL.revokeObjectURL(url);
        } catch (err) {
            console.error("Failed to download ledger PDF", err);
            setError("Failed to generate PDF");
        }
        setIsDownloading(false);
    };

    if (initialLoading) {
        return (
            <div className="p-8 flex items-center justify-center min-h-screen">
                <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary" />
            </div>
        );
    }

    return (
        <div className="p-8 min-h-screen bg-background transition-colors duration-300">
            <div className="max-w-7xl mx-auto">
                {/* Header */}
                <div className="mb-8">
                    <h1 className="text-4xl font-bold text-foreground tracking-tight">
                        Customer <span className="text-gradient">Ledger</span>
                    </h1>
                    <p className="text-muted-foreground mt-2">
                        View credit transaction history and balance for any customer.
                    </p>
                </div>

                {/* Search Filters */}
                <GlassCard className="mb-8 relative z-10">
                    <div className="flex flex-wrap gap-4 items-end">
                        <div className="flex-1 min-w-[200px]">
                            <label className="block text-sm font-medium text-muted-foreground mb-1">Customer</label>
                            <CustomerAutocomplete
                                value={selectedCustomerId}
                                onChange={(id) => setSelectedCustomerId(id ? Number(id) : "")}
                                customers={customers}
                                placeholder="Search customer..."
                            />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-muted-foreground mb-1">
                                <Calendar className="w-3 h-3 inline mr-1" />From
                            </label>
                            <input
                                type="date"
                                value={fromDate}
                                onChange={(e) => setFromDate(e.target.value)}
                                className="px-4 py-2 bg-card border border-border rounded-lg text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                            />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-muted-foreground mb-1">
                                <Calendar className="w-3 h-3 inline mr-1" />To
                            </label>
                            <input
                                type="date"
                                value={toDate}
                                onChange={(e) => setToDate(e.target.value)}
                                className="px-4 py-2 bg-card border border-border rounded-lg text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                            />
                        </div>
                        <button
                            onClick={handleSearch}
                            disabled={loading}
                            className="btn-gradient px-6 py-2 rounded-lg font-medium flex items-center gap-2 disabled:opacity-50"
                        >
                            <Search className="w-4 h-4" />
                            {loading ? "Loading..." : "View Ledger"}
                        </button>
                        {ledger && (
                            <button
                                onClick={handleDownloadPdf}
                                disabled={isDownloading}
                                className="flex items-center gap-1.5 px-4 py-2 bg-red-500/10 hover:bg-red-500/20 text-red-500 rounded-lg text-sm font-medium transition-colors disabled:opacity-50"
                                title="Download Ledger PDF"
                            >
                                <FileText className="w-4 h-4" />
                                {isDownloading ? "Generating..." : "PDF"}
                            </button>
                        )}
                    </div>
                    {error && (
                        <div className="mt-3 bg-rose-500/20 border border-rose-500/30 text-rose-400 px-4 py-2 rounded-lg text-sm">
                            {error}
                        </div>
                    )}
                </GlassCard>

                {/* Loading spinner */}
                {loading && (
                    <div className="flex flex-col items-center justify-center py-20 text-muted-foreground">
                        <div className="w-12 h-12 border-4 border-primary/20 border-t-primary rounded-full animate-spin mb-4" />
                        <p className="animate-pulse">Loading ledger...</p>
                    </div>
                )}

                {/* Ledger Results */}
                {!loading && ledger && (
                    <>
                        {/* Balance Summary */}
                        <div className="grid grid-cols-1 md:grid-cols-4 gap-4 mb-8">
                            <GlassCard>
                                <div className="text-muted-foreground text-sm">Opening Balance</div>
                                <div className={`text-2xl font-bold mt-1 ${ledger.openingBalance > 0 ? "text-amber-400" : "text-emerald-400"}`}>
                                    {Number(ledger.openingBalance).toLocaleString("en-IN", { style: "currency", currency: "INR" })}
                                </div>
                            </GlassCard>
                            <GlassCard>
                                <div className="text-muted-foreground text-sm">Total Debits (Bills)</div>
                                <div className="text-2xl font-bold text-rose-400 mt-1">
                                    {Number(ledger.totalDebits).toLocaleString("en-IN", { style: "currency", currency: "INR" })}
                                </div>
                            </GlassCard>
                            <GlassCard>
                                <div className="text-muted-foreground text-sm">Total Credits (Payments)</div>
                                <div className="text-2xl font-bold text-emerald-400 mt-1">
                                    {Number(ledger.totalCredits).toLocaleString("en-IN", { style: "currency", currency: "INR" })}
                                </div>
                            </GlassCard>
                            <GlassCard>
                                <div className="text-muted-foreground text-sm">Closing Balance</div>
                                <div className={`text-2xl font-bold mt-1 ${ledger.closingBalance > 0 ? "text-amber-400" : "text-emerald-400"}`}>
                                    {Number(ledger.closingBalance).toLocaleString("en-IN", { style: "currency", currency: "INR" })}
                                </div>
                            </GlassCard>
                        </div>

                        {/* Ledger Table */}
                        <GlassCard>
                            <div className="flex items-center justify-between mb-4">
                                <h3 className="text-lg font-semibold text-foreground">
                                    <BookOpen className="w-5 h-5 inline mr-2" />
                                    Ledger for {selectedCustomer?.name || "Customer"}
                                </h3>
                                <span className="text-sm text-muted-foreground">
                                    {fromDate} to {toDate}
                                </span>
                            </div>
                            <div className="overflow-x-auto">
                                <table className="w-full text-sm">
                                    <thead>
                                        <tr className="border-b border-border text-muted-foreground">
                                            <th className="text-left py-3 px-4">Date</th>
                                            <th className="text-left py-3 px-4">Description</th>
                                            <th className="text-right py-3 px-4">Debit</th>
                                            <th className="text-right py-3 px-4">Credit</th>
                                            <th className="text-right py-3 px-4">Balance</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {/* Opening balance row */}
                                        <tr className="border-b border-border/50 bg-muted/30">
                                            <td className="py-3 px-4 text-muted-foreground">{fromDate}</td>
                                            <td className="py-3 px-4 font-medium text-foreground">Opening Balance</td>
                                            <td className="py-3 px-4 text-right">-</td>
                                            <td className="py-3 px-4 text-right">-</td>
                                            <td className="py-3 px-4 text-right font-bold text-foreground">
                                                {Number(ledger.openingBalance).toLocaleString("en-IN", { minimumFractionDigits: 2 })}
                                            </td>
                                        </tr>

                                        {ledger.entries.length === 0 ? (
                                            <tr>
                                                <td colSpan={5} className="text-center py-8 text-muted-foreground">
                                                    No transactions in this period
                                                </td>
                                            </tr>
                                        ) : (
                                            ledger.entries.map((entry, idx) => (
                                                <tr key={idx} className="border-b border-border/50 hover:bg-muted/50 transition-colors">
                                                    <td className="py-3 px-4 text-muted-foreground">
                                                        {entry.date ? new Date(entry.date).toLocaleDateString("en-IN", {
                                                            day: "2-digit", month: "short", year: "numeric"
                                                        }) : "-"}
                                                    </td>
                                                    <td className="py-3 px-4">
                                                        <div className="flex items-center gap-2">
                                                            {entry.type === "DEBIT" ? (
                                                                <ArrowUpRight className="w-4 h-4 text-rose-400" />
                                                            ) : (
                                                                <ArrowDownLeft className="w-4 h-4 text-emerald-400" />
                                                            )}
                                                            <span className="text-foreground">{entry.description}</span>
                                                        </div>
                                                    </td>
                                                    <td className="py-3 px-4 text-right text-rose-400">
                                                        {entry.debitAmount > 0
                                                            ? Number(entry.debitAmount).toLocaleString("en-IN", { minimumFractionDigits: 2 })
                                                            : "-"}
                                                    </td>
                                                    <td className="py-3 px-4 text-right text-emerald-400">
                                                        {entry.creditAmount > 0
                                                            ? Number(entry.creditAmount).toLocaleString("en-IN", { minimumFractionDigits: 2 })
                                                            : "-"}
                                                    </td>
                                                    <td className="py-3 px-4 text-right font-medium text-foreground">
                                                        {Number(entry.runningBalance).toLocaleString("en-IN", { minimumFractionDigits: 2 })}
                                                    </td>
                                                </tr>
                                            ))
                                        )}

                                        {/* Closing balance row */}
                                        <tr className="border-t-2 border-border bg-muted/30">
                                            <td className="py-3 px-4 text-muted-foreground">{toDate}</td>
                                            <td className="py-3 px-4 font-bold text-foreground">Closing Balance</td>
                                            <td className="py-3 px-4 text-right font-semibold text-rose-400">
                                                {Number(ledger.totalDebits).toLocaleString("en-IN", { minimumFractionDigits: 2 })}
                                            </td>
                                            <td className="py-3 px-4 text-right font-semibold text-emerald-400">
                                                {Number(ledger.totalCredits).toLocaleString("en-IN", { minimumFractionDigits: 2 })}
                                            </td>
                                            <td className={`py-3 px-4 text-right font-bold text-lg ${ledger.closingBalance > 0 ? "text-amber-400" : "text-emerald-400"}`}>
                                                {Number(ledger.closingBalance).toLocaleString("en-IN", { minimumFractionDigits: 2 })}
                                            </td>
                                        </tr>
                                    </tbody>
                                </table>
                            </div>
                        </GlassCard>
                    </>
                )}

                {/* Empty state */}
                {!ledger && !loading && (
                    <GlassCard className="text-center py-16">
                        <BookOpen className="w-12 h-12 text-muted-foreground mx-auto mb-4" />
                        <h3 className="text-lg font-medium text-foreground mb-2">Select a Customer</h3>
                        <p className="text-muted-foreground">
                            Choose a customer and date range to view their credit ledger.
                        </p>
                    </GlassCard>
                )}
            </div>
        </div>
    );
}
