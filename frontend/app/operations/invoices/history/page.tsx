"use client";

import { useState, useEffect, useCallback } from "react";
import { GlassCard } from "@/components/ui/glass-card";
import {
    Search, Filter, Calendar, ChevronDown, ChevronRight,
    Package, FileText, RotateCcw, Loader2
} from "lucide-react";
import {
    getInvoiceHistory, getProductSalesSummary,
    type InvoiceBill, type PageResponse, type ProductSalesSummary
} from "@/lib/api/station";

export default function InvoiceHistoryPage() {
    // Invoices data
    const [invoices, setInvoices] = useState<InvoiceBill[]>([]);
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);
    const [loading, setLoading] = useState(true);

    // Product summary
    const [productSummary, setProductSummary] = useState<ProductSalesSummary[]>([]);
    const [summaryLoading, setSummaryLoading] = useState(true);

    // Expanded row
    const [expandedRowId, setExpandedRowId] = useState<number | null>(null);

    // Filters
    const now = new Date();
    const firstDayOfMonth = new Date(now.getFullYear(), now.getMonth(), 1);
    const [filters, setFilters] = useState({
        billType: "",
        paymentStatus: "",
        fromDate: firstDayOfMonth.toISOString().slice(0, 16),
        toDate: now.toISOString().slice(0, 16),
        search: "",
    });
    const [appliedFilters, setAppliedFilters] = useState({ ...filters });

    const pageSize = 20;

    const buildFilterParams = useCallback((f: typeof filters) => {
        const params: any = {};
        if (f.billType) params.billType = f.billType;
        if (f.paymentStatus) params.paymentStatus = f.paymentStatus;
        if (f.fromDate) params.fromDate = f.fromDate + ":00";
        if (f.toDate) params.toDate = f.toDate + ":00";
        if (f.search) params.search = f.search;
        return params;
    }, []);

    const fetchInvoices = useCallback(async (p: number, f: typeof filters) => {
        setLoading(true);
        try {
            const data = await getInvoiceHistory(p, pageSize, buildFilterParams(f));
            setInvoices(data.content);
            setTotalPages(data.totalPages);
            setTotalElements(data.totalElements);
        } catch (e) {
            console.error("Failed to load invoices:", e);
        } finally {
            setLoading(false);
        }
    }, [buildFilterParams]);

    const fetchSummary = useCallback(async (f: typeof filters) => {
        setSummaryLoading(true);
        try {
            const data = await getProductSalesSummary(buildFilterParams(f));
            setProductSummary(data);
        } catch (e) {
            console.error("Failed to load summary:", e);
        } finally {
            setSummaryLoading(false);
        }
    }, [buildFilterParams]);

    // Initial load and on filter apply
    useEffect(() => {
        fetchInvoices(page, appliedFilters);
    }, [page, appliedFilters, fetchInvoices]);

    useEffect(() => {
        fetchSummary(appliedFilters);
    }, [appliedFilters, fetchSummary]);

    const handleApply = () => {
        setPage(0);
        setExpandedRowId(null);
        setAppliedFilters({ ...filters });
    };

    const handleReset = () => {
        const now2 = new Date();
        const firstDay2 = new Date(now2.getFullYear(), now2.getMonth(), 1);
        const defaultFilters = {
            billType: "",
            paymentStatus: "",
            fromDate: firstDay2.toISOString().slice(0, 16),
            toDate: now2.toISOString().slice(0, 16),
            search: "",
        };
        setFilters(defaultFilters);
        setPage(0);
        setExpandedRowId(null);
        setAppliedFilters(defaultFilters);
    };

    const fmt = (n: number) => n.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });

    return (
        <div className="p-6 space-y-6 max-w-[1400px] mx-auto">
            {/* Header */}
            <div>
                <h1 className="text-2xl font-bold text-foreground">Invoice History</h1>
                <p className="text-sm text-muted-foreground mt-1">Browse, filter, and analyze all invoices</p>
            </div>

            {/* Filter Bar */}
            <GlassCard className="p-4">
                <div className="flex flex-wrap items-end gap-3">
                    <div className="flex-1 min-w-[180px]">
                        <label className="block text-[10px] font-bold uppercase tracking-widest text-muted-foreground mb-1">From Date</label>
                        <input
                            type="datetime-local"
                            value={filters.fromDate}
                            onChange={e => setFilters(f => ({ ...f, fromDate: e.target.value }))}
                            className="w-full px-3 py-2 text-sm rounded-lg border border-border bg-background text-foreground"
                        />
                    </div>
                    <div className="flex-1 min-w-[180px]">
                        <label className="block text-[10px] font-bold uppercase tracking-widest text-muted-foreground mb-1">To Date</label>
                        <input
                            type="datetime-local"
                            value={filters.toDate}
                            onChange={e => setFilters(f => ({ ...f, toDate: e.target.value }))}
                            className="w-full px-3 py-2 text-sm rounded-lg border border-border bg-background text-foreground"
                        />
                    </div>
                    <div className="min-w-[130px]">
                        <label className="block text-[10px] font-bold uppercase tracking-widest text-muted-foreground mb-1">Bill Type</label>
                        <select
                            value={filters.billType}
                            onChange={e => setFilters(f => ({ ...f, billType: e.target.value }))}
                            className="w-full px-3 py-2 text-sm rounded-lg border border-border bg-background text-foreground"
                        >
                            <option value="">All</option>
                            <option value="CASH">Cash</option>
                            <option value="CREDIT">Credit</option>
                        </select>
                    </div>
                    <div className="min-w-[140px]">
                        <label className="block text-[10px] font-bold uppercase tracking-widest text-muted-foreground mb-1">Payment Status</label>
                        <select
                            value={filters.paymentStatus}
                            onChange={e => setFilters(f => ({ ...f, paymentStatus: e.target.value }))}
                            className="w-full px-3 py-2 text-sm rounded-lg border border-border bg-background text-foreground"
                        >
                            <option value="">All</option>
                            <option value="PAID">Paid</option>
                            <option value="NOT_PAID">Not Paid</option>
                        </select>
                    </div>
                    <div className="flex-1 min-w-[200px]">
                        <label className="block text-[10px] font-bold uppercase tracking-widest text-muted-foreground mb-1">Search</label>
                        <div className="relative">
                            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                            <input
                                type="text"
                                placeholder="Bill no, customer, vehicle..."
                                value={filters.search}
                                onChange={e => setFilters(f => ({ ...f, search: e.target.value }))}
                                onKeyDown={e => e.key === 'Enter' && handleApply()}
                                className="w-full pl-9 pr-3 py-2 text-sm rounded-lg border border-border bg-background text-foreground"
                            />
                        </div>
                    </div>
                    <button onClick={handleApply} className="px-4 py-2 text-sm font-medium rounded-lg bg-primary text-primary-foreground hover:bg-primary/90 transition-colors">
                        <Filter className="w-4 h-4 inline mr-1" />Apply
                    </button>
                    <button onClick={handleReset} className="px-4 py-2 text-sm font-medium rounded-lg border border-border text-muted-foreground hover:bg-muted transition-colors">
                        <RotateCcw className="w-4 h-4 inline mr-1" />Reset
                    </button>
                </div>
            </GlassCard>

            {/* Product Sales Summary */}
            <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3">
                {summaryLoading ? (
                    Array.from({ length: 3 }).map((_, i) => (
                        <GlassCard key={i} className="p-4 animate-pulse">
                            <div className="h-4 bg-muted rounded w-2/3 mb-3" />
                            <div className="h-6 bg-muted rounded w-1/2 mb-2" />
                            <div className="h-4 bg-muted rounded w-3/4" />
                        </GlassCard>
                    ))
                ) : productSummary.length > 0 ? (
                    productSummary.map(ps => (
                        <GlassCard key={ps.productId} className="p-4">
                            <div className="flex items-center gap-2 mb-2">
                                <Package className="w-4 h-4 text-primary" />
                                <span className="text-xs font-bold text-muted-foreground uppercase tracking-wider">{ps.productName}</span>
                            </div>
                            <div className="text-lg font-bold text-foreground">{fmt(ps.totalQuantity)} L</div>
                            <div className="text-sm text-muted-foreground mt-1">₹{fmt(ps.totalAmount)}</div>
                            {ps.totalDiscount > 0 && (
                                <div className="text-[10px] text-orange-500 mt-1">Disc: ₹{fmt(ps.totalDiscount)}</div>
                            )}
                        </GlassCard>
                    ))
                ) : (
                    <div className="col-span-full text-sm text-muted-foreground text-center py-4">No product sales in this range</div>
                )}
            </div>

            {/* Invoice Table */}
            <GlassCard className="overflow-hidden border-none p-0">
                <div className="overflow-x-auto">
                    <table className="w-full text-left border-collapse">
                        <thead>
                            <tr className="bg-white/5 border-b border-border/50">
                                <th className="px-4 py-3 w-8" />
                                <th className="px-4 py-3 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Bill No</th>
                                <th className="px-4 py-3 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Date / Time</th>
                                <th className="px-4 py-3 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Customer</th>
                                <th className="px-4 py-3 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Vehicle</th>
                                <th className="px-4 py-3 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Type</th>
                                <th className="px-4 py-3 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Payment</th>
                                <th className="px-4 py-3 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center">Items</th>
                                <th className="px-4 py-3 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right">Net Amount</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-border/30">
                            {loading ? (
                                Array.from({ length: 5 }).map((_, i) => (
                                    <tr key={i} className="animate-pulse">
                                        <td className="px-4 py-4" colSpan={9}>
                                            <div className="h-4 bg-muted rounded w-full" />
                                        </td>
                                    </tr>
                                ))
                            ) : invoices.length === 0 ? (
                                <tr>
                                    <td colSpan={9} className="px-4 py-12 text-center text-muted-foreground">
                                        No invoices found for the selected filters.
                                    </td>
                                </tr>
                            ) : (
                                invoices.map(inv => {
                                    const isExpanded = expandedRowId === inv.id;
                                    return (
                                        <>
                                            <tr
                                                key={inv.id}
                                                onClick={() => setExpandedRowId(isExpanded ? null : (inv.id ?? null))}
                                                className="hover:bg-white/5 transition-colors cursor-pointer"
                                            >
                                                <td className="px-4 py-3 text-muted-foreground">
                                                    {isExpanded ? <ChevronDown className="w-4 h-4" /> : <ChevronRight className="w-4 h-4" />}
                                                </td>
                                                <td className="px-4 py-3 font-mono font-bold text-foreground text-sm">{inv.billNo || "—"}</td>
                                                <td className="px-4 py-3">
                                                    <div className="text-sm font-medium text-foreground">{new Date(inv.date).toLocaleDateString()}</div>
                                                    <div className="text-[10px] text-muted-foreground font-mono">{new Date(inv.date).toLocaleTimeString()}</div>
                                                </td>
                                                <td className="px-4 py-3 text-sm text-foreground">{inv.customer?.name || <span className="text-muted-foreground italic">Walk-in</span>}</td>
                                                <td className="px-4 py-3 text-sm font-mono text-foreground">{inv.vehicle?.vehicleNumber || "—"}</td>
                                                <td className="px-4 py-3">
                                                    <span className={`px-2 py-0.5 rounded-full text-[10px] font-bold uppercase ${
                                                        inv.billType === 'CASH'
                                                            ? 'bg-green-500/10 text-green-500 border border-green-500/20'
                                                            : 'bg-blue-500/10 text-blue-500 border border-blue-500/20'
                                                    }`}>{inv.billType}</span>
                                                </td>
                                                <td className="px-4 py-3">
                                                    <span className={`px-2 py-0.5 rounded-full text-[10px] font-bold uppercase ${
                                                        inv.paymentStatus === 'PAID'
                                                            ? 'bg-emerald-500/10 text-emerald-500 border border-emerald-500/20'
                                                            : 'bg-amber-500/10 text-amber-500 border border-amber-500/20'
                                                    }`}>{inv.paymentStatus === 'NOT_PAID' ? 'Unpaid' : 'Paid'}</span>
                                                </td>
                                                <td className="px-4 py-3 text-center">
                                                    <span className="text-sm font-mono bg-black/5 dark:bg-white/5 px-2 py-1 rounded-lg">
                                                        {inv.products?.length || 0}
                                                    </span>
                                                </td>
                                                <td className="px-4 py-3 text-right font-bold text-foreground">
                                                    ₹{fmt(inv.netAmount || 0)}
                                                </td>
                                            </tr>
                                            {isExpanded && (
                                                <tr key={`${inv.id}-detail`} className="bg-muted/30">
                                                    <td colSpan={9} className="px-6 py-4">
                                                        {/* Product line items */}
                                                        {inv.products && inv.products.length > 0 && (
                                                            <div className="mb-3">
                                                                <div className="text-[10px] font-bold uppercase tracking-widest text-muted-foreground mb-2">Product Details</div>
                                                                <table className="w-full text-sm">
                                                                    <thead>
                                                                        <tr className="text-[10px] font-bold uppercase tracking-widest text-muted-foreground">
                                                                            <th className="text-left py-1 pr-4">Product</th>
                                                                            <th className="text-left py-1 pr-4">Nozzle</th>
                                                                            <th className="text-right py-1 pr-4">Qty (L)</th>
                                                                            <th className="text-right py-1 pr-4">Rate</th>
                                                                            <th className="text-right py-1 pr-4">Discount</th>
                                                                            <th className="text-right py-1">Amount</th>
                                                                        </tr>
                                                                    </thead>
                                                                    <tbody>
                                                                        {inv.products.map((ip, idx) => (
                                                                            <tr key={idx} className="border-t border-border/20">
                                                                                <td className="py-1.5 pr-4 text-foreground">{ip.product?.name || "—"}</td>
                                                                                <td className="py-1.5 pr-4 font-mono text-muted-foreground">{ip.nozzle?.nozzleName || "—"}</td>
                                                                                <td className="py-1.5 pr-4 text-right font-mono">{ip.quantity?.toFixed(2)}</td>
                                                                                <td className="py-1.5 pr-4 text-right font-mono">₹{ip.unitPrice?.toFixed(2)}</td>
                                                                                <td className="py-1.5 pr-4 text-right font-mono text-orange-500">
                                                                                    {ip.discountAmount ? `₹${ip.discountAmount.toFixed(2)}` : "—"}
                                                                                </td>
                                                                                <td className="py-1.5 text-right font-mono font-bold">₹{ip.amount?.toFixed(2)}</td>
                                                                            </tr>
                                                                        ))}
                                                                    </tbody>
                                                                </table>
                                                            </div>
                                                        )}
                                                        {/* Extra info */}
                                                        <div className="flex flex-wrap gap-4 text-xs text-muted-foreground">
                                                            {inv.driverName && <span><strong>Driver:</strong> {inv.driverName} {inv.driverPhone && `(${inv.driverPhone})`}</span>}
                                                            {inv.indentNo && <span><strong>Indent No:</strong> {inv.indentNo}</span>}
                                                            {inv.paymentMode && <span><strong>Payment Mode:</strong> {inv.paymentMode}</span>}
                                                            {inv.vehicleKM && <span><strong>KM:</strong> {inv.vehicleKM}</span>}
                                                        </div>
                                                    </td>
                                                </tr>
                                            )}
                                        </>
                                    );
                                })
                            )}
                        </tbody>
                    </table>
                </div>

                {/* Pagination */}
                {totalPages > 1 && (
                    <div className="flex items-center justify-between p-4 border-t border-border">
                        <p className="text-sm text-muted-foreground">
                            Showing {page * pageSize + 1}-{Math.min((page + 1) * pageSize, totalElements)} of {totalElements}
                        </p>
                        <div className="flex items-center gap-2">
                            <button
                                onClick={() => { setPage(p => Math.max(0, p - 1)); setExpandedRowId(null); }}
                                disabled={page === 0}
                                className="px-3 py-1.5 text-sm rounded-md border border-border text-muted-foreground hover:bg-muted disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
                            >
                                Previous
                            </button>
                            <span className="text-sm text-foreground font-medium px-2">
                                {page + 1} / {totalPages}
                            </span>
                            <button
                                onClick={() => { setPage(p => Math.min(totalPages - 1, p + 1)); setExpandedRowId(null); }}
                                disabled={page >= totalPages - 1}
                                className="px-3 py-1.5 text-sm rounded-md border border-border text-muted-foreground hover:bg-muted disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
                            >
                                Next
                            </button>
                        </div>
                    </div>
                )}
            </GlassCard>
        </div>
    );
}
