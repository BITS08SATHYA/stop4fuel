"use client";

import { useState, useEffect, useCallback, Fragment } from "react";
import { GlassCard } from "@/components/ui/glass-card";
import { TablePagination } from "@/components/ui/table-pagination";
import { Modal } from "@/components/ui/modal";
import {
    Search, Filter, ChevronDown, ChevronRight,
    Package, RotateCcw, Pencil, Trash2, Plus, X, Save
} from "lucide-react";
import { FormErrorBanner } from "@/components/ui/field-error";
import { PermissionGate } from "@/components/permission-gate";
import {
    getInvoiceHistory, getProductSalesSummary, updateInvoice, deleteInvoice,
    getActiveProducts, getNozzles, getCustomers, getVehiclesByCustomer, searchVehicles,
    type InvoiceBill, type InvoiceProduct, type PageResponse, type ProductSalesSummary,
    type Product, type Nozzle, type Vehicle
} from "@/lib/api/station";

interface EditLine {
    id?: number;
    product: Product | null;
    nozzle: Nozzle | null;
    quantity: string;
    unitPrice: string;
    discountRate: string;
    grossAmount: number;
    discountAmount: number;
    amount: number;
}

export default function InvoiceHistoryPage() {
    const [invoices, setInvoices] = useState<InvoiceBill[]>([]);
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);
    const [loading, setLoading] = useState(true);

    const [productSummary, setProductSummary] = useState<ProductSalesSummary[]>([]);
    const [summaryLoading, setSummaryLoading] = useState(true);

    const [expandedRowId, setExpandedRowId] = useState<number | null>(null);

    // Filters — toDate uses end of day so newly created invoices are always visible
    const now = new Date();
    const firstDayOfMonth = new Date(now.getFullYear(), now.getMonth(), 1);
    const endOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 23, 59);
    const [filters, setFilters] = useState({
        billType: "",
        paymentStatus: "",
        categoryType: "",
        fromDate: firstDayOfMonth.toISOString().slice(0, 16),
        toDate: endOfToday.toISOString().slice(0, 16),
        search: "",
    });
    const [appliedFilters, setAppliedFilters] = useState({ ...filters });
    const pageSize = 10;

    // Edit modal state
    const [editModal, setEditModal] = useState(false);
    const [editInvoice, setEditInvoice] = useState<InvoiceBill | null>(null);
    const [editLines, setEditLines] = useState<EditLine[]>([]);
    const [editDriverName, setEditDriverName] = useState("");
    const [editDriverPhone, setEditDriverPhone] = useState("");
    const [editIndentNo, setEditIndentNo] = useState("");
    const [editPaymentMode, setEditPaymentMode] = useState("");
    const [editVehicleKM, setEditVehicleKM] = useState("");
    const [editBillType, setEditBillType] = useState<"CASH" | "CREDIT">("CASH");
    const [editCustomer, setEditCustomer] = useState<any>(null);
    const [editVehicle, setEditVehicle] = useState<any>(null);
    const [editCustomerSearch, setEditCustomerSearch] = useState("");
    const [editCustomerSuggestions, setEditCustomerSuggestions] = useState<any[]>([]);
    const [editCustomerVehicles, setEditCustomerVehicles] = useState<any[]>([]);
    const [editVehicleSearch, setEditVehicleSearch] = useState("");
    const [editVehicleResults, setEditVehicleResults] = useState<Vehicle[]>([]);
    const [saving, setSaving] = useState(false);
    const [editError, setEditError] = useState("");

    // Delete confirm
    const [deleteConfirm, setDeleteConfirm] = useState<InvoiceBill | null>(null);
    const [deleting, setDeleting] = useState(false);

    // Products & Nozzles for edit form
    const [allProducts, setAllProducts] = useState<Product[]>([]);
    const [allNozzles, setAllNozzles] = useState<Nozzle[]>([]);

    useEffect(() => {
        Promise.all([getActiveProducts(), getNozzles()]).then(([p, n]) => {
            setAllProducts(p);
            setAllNozzles(n.filter((nz: Nozzle) => nz.active));
        }).catch(() => {});
    }, []);

    const buildFilterParams = useCallback((f: typeof filters) => {
        const params: any = {};
        if (f.billType) params.billType = f.billType;
        if (f.paymentStatus) params.paymentStatus = f.paymentStatus;
        if (f.categoryType) params.categoryType = f.categoryType;
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
        const endOfDay2 = new Date(now2.getFullYear(), now2.getMonth(), now2.getDate(), 23, 59);
        const defaultFilters = {
            billType: "",
            paymentStatus: "",
            categoryType: "",
            fromDate: firstDay2.toISOString().slice(0, 16),
            toDate: endOfDay2.toISOString().slice(0, 16),
            search: "",
        };
        setFilters(defaultFilters);
        setPage(0);
        setExpandedRowId(null);
        setAppliedFilters(defaultFilters);
    };

    const refresh = () => {
        fetchInvoices(page, appliedFilters);
        fetchSummary(appliedFilters);
    };

    const fmt = (n: number) => n.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });

    // --- Edit ---
    const openEdit = async (inv: InvoiceBill) => {
        setEditInvoice(inv);
        setEditDriverName(inv.driverName || "");
        setEditDriverPhone(inv.driverPhone || "");
        setEditIndentNo(inv.indentNo || "");
        setEditPaymentMode(inv.paymentMode || "");
        setEditVehicleKM(inv.vehicleKM ? String(inv.vehicleKM) : "");
        setEditBillType((inv.billType as "CASH" | "CREDIT") || "CASH");
        setEditCustomer(inv.customer || null);
        setEditVehicle(inv.vehicle || null);
        setEditCustomerSearch(inv.customer?.name || "");
        setEditCustomerSuggestions([]);
        setEditVehicleSearch("");
        setEditVehicleResults([]);
        setEditError("");
        // Load customer vehicles if customer exists
        if (inv.customer?.id) {
            try {
                const vehicles = await getVehiclesByCustomer(inv.customer.id);
                setEditCustomerVehicles(vehicles);
            } catch { setEditCustomerVehicles([]); }
        } else {
            setEditCustomerVehicles([]);
        }
        setEditLines(
            (inv.products || []).map(ip => ({
                id: ip.id,
                product: ip.productId ? (allProducts.find(p => p.id === ip.productId) || { id: ip.productId, name: ip.productName } as any) : null,
                nozzle: ip.nozzleId ? (allNozzles.find(n => n.id === ip.nozzleId) || { id: ip.nozzleId, nozzleName: ip.nozzleName } as any) : null,
                quantity: ip.quantity != null ? String(ip.quantity) : "",
                unitPrice: ip.unitPrice != null ? String(ip.unitPrice) : "",
                discountRate: ip.discountRate != null ? String(ip.discountRate) : "",
                grossAmount: ip.grossAmount || 0,
                discountAmount: ip.discountAmount || 0,
                amount: ip.amount || 0,
            }))
        );
        setEditModal(true);
    };

    const updateEditLine = (index: number, updates: Partial<EditLine>) => {
        setEditLines(prev => {
            const lines = [...prev];
            const line = { ...lines[index], ...updates };

            const qty = parseFloat(line.quantity) || 0;
            const price = parseFloat(line.unitPrice) || 0;
            const gross = qty * price;
            line.grossAmount = gross;

            const discRate = parseFloat(line.discountRate) || 0;
            if (discRate > 0) {
                const discAmt = discRate * qty;
                line.discountAmount = discAmt;
                line.amount = gross - discAmt;
            } else {
                line.discountAmount = 0;
                line.amount = gross;
            }

            lines[index] = line;
            return lines;
        });
    };

    const addEditLine = () => {
        setEditLines(prev => [...prev, {
            product: null, nozzle: null, quantity: "", unitPrice: "",
            discountRate: "", grossAmount: 0, discountAmount: 0, amount: 0,
        }]);
    };

    const removeEditLine = (index: number) => {
        setEditLines(prev => prev.filter((_, i) => i !== index));
    };

    const editTotalGross = editLines.reduce((s, l) => s + l.grossAmount, 0);
    const editTotalDiscount = editLines.reduce((s, l) => s + l.discountAmount, 0);
    const editNetAmount = editTotalGross - editTotalDiscount;

    const handleSaveEdit = async () => {
        if (!editInvoice?.id) return;
        if (editLines.length === 0) {
            setEditError("At least one product line is required.");
            return;
        }
        setSaving(true);
        setEditError("");
        try {
            await updateInvoice(editInvoice.id, {
                billType: editBillType,
                driverName: editDriverName || undefined,
                driverPhone: editDriverPhone || undefined,
                indentNo: editIndentNo || undefined,
                paymentMode: editPaymentMode || undefined,
                vehicleKM: editVehicleKM ? Number(editVehicleKM) : undefined,
                customer: editCustomer ? { id: editCustomer.id } as any : undefined,
                vehicle: editVehicle ? { id: editVehicle.id } as any : undefined,
                products: editLines.map(l => ({
                    product: l.product ? { id: l.product.id } as any : undefined,
                    nozzle: l.nozzle ? { id: l.nozzle.id } as any : undefined,
                    quantity: parseFloat(l.quantity) || 0,
                    unitPrice: parseFloat(l.unitPrice) || 0,
                    amount: l.amount,
                    discountRate: parseFloat(l.discountRate) || undefined,
                })) as any[],
            });
            setEditModal(false);
            refresh();
        } catch (e: any) {
            setEditError(e.message || "Failed to update invoice");
        } finally {
            setSaving(false);
        }
    };

    // --- Delete ---
    const handleDelete = async () => {
        if (!deleteConfirm?.id) return;
        setDeleting(true);
        try {
            await deleteInvoice(deleteConfirm.id);
            setDeleteConfirm(null);
            setExpandedRowId(null);
            refresh();
        } catch (e: any) {
            setEditError(e.message || "Failed to delete invoice");
        } finally {
            setDeleting(false);
        }
    };

    return (
        <div className="p-6 space-y-6 max-w-[1400px] mx-auto">
            {/* Header */}
            <div>
                <h1 className="text-2xl font-bold text-foreground">Invoice History</h1>
                <p className="text-sm text-muted-foreground mt-1">Browse, filter, edit, and analyze all invoices</p>
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
                    <div className="min-w-[150px]">
                        <label className="block text-[10px] font-bold uppercase tracking-widest text-muted-foreground mb-1">Category</label>
                        <select
                            value={filters.categoryType}
                            onChange={e => setFilters(f => ({ ...f, categoryType: e.target.value }))}
                            className="w-full px-3 py-2 text-sm rounded-lg border border-border bg-background text-foreground"
                        >
                            <option value="">All</option>
                            <option value="GOVERNMENT">Government</option>
                            <option value="NON_GOVERNMENT">Non-Government</option>
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
                                <th className="px-4 py-3 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center">Actions</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-border/30">
                            {loading ? (
                                Array.from({ length: 5 }).map((_, i) => (
                                    <tr key={i} className="animate-pulse">
                                        <td className="px-4 py-4" colSpan={10}>
                                            <div className="h-4 bg-muted rounded w-full" />
                                        </td>
                                    </tr>
                                ))
                            ) : invoices.length === 0 ? (
                                <tr>
                                    <td colSpan={10} className="px-4 py-12 text-center text-muted-foreground">
                                        No invoices found for the selected filters.
                                    </td>
                                </tr>
                            ) : (
                                invoices.map(inv => {
                                    const isExpanded = expandedRowId === inv.id;
                                    return (
                                        <Fragment key={inv.id}>
                                            <tr
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
                                                <td className="px-4 py-3 text-center" onClick={e => e.stopPropagation()}>
                                                    <div className="flex items-center justify-center gap-1">
                                                        <PermissionGate permission="INVOICE_MODIFY">
                                                            <button
                                                                onClick={() => openEdit(inv)}
                                                                className="p-1.5 rounded-md text-muted-foreground hover:text-primary hover:bg-primary/10 transition-colors"
                                                                title="Edit"
                                                            >
                                                                <Pencil className="w-3.5 h-3.5" />
                                                            </button>
                                                        </PermissionGate>
                                                        <PermissionGate permission="INVOICE_DELETE">
                                                            <button
                                                                onClick={() => setDeleteConfirm(inv)}
                                                                className="p-1.5 rounded-md text-muted-foreground hover:text-red-500 hover:bg-red-500/10 transition-colors"
                                                                title="Delete"
                                                            >
                                                                <Trash2 className="w-3.5 h-3.5" />
                                                            </button>
                                                        </PermissionGate>
                                                    </div>
                                                </td>
                                            </tr>
                                            {isExpanded && (
                                                <tr key={`${inv.id}-detail`} className="bg-muted/30">
                                                    <td colSpan={10} className="px-6 py-4">
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
                                                                            <th className="text-right py-1 pr-4">Gross</th>
                                                                            <th className="text-right py-1 pr-4">Discount</th>
                                                                            <th className="text-right py-1">Amount</th>
                                                                        </tr>
                                                                    </thead>
                                                                    <tbody>
                                                                        {inv.products.map((ip, idx) => (
                                                                            <tr key={idx} className="border-t border-border/20">
                                                                                <td className="py-1.5 pr-4 text-foreground">{ip.productName || "—"}</td>
                                                                                <td className="py-1.5 pr-4 font-mono text-muted-foreground">{ip.nozzleName || "—"}</td>
                                                                                <td className="py-1.5 pr-4 text-right font-mono">{ip.quantity?.toFixed(2)}</td>
                                                                                <td className="py-1.5 pr-4 text-right font-mono">₹{ip.unitPrice?.toFixed(2)}</td>
                                                                                <td className="py-1.5 pr-4 text-right font-mono">₹{(ip.grossAmount || 0).toFixed(2)}</td>
                                                                                <td className="py-1.5 pr-4 text-right font-mono text-orange-500">
                                                                                    {ip.discountAmount ? `₹${ip.discountAmount.toFixed(2)}` : "—"}
                                                                                </td>
                                                                                <td className="py-1.5 text-right font-mono font-bold">₹{ip.amount?.toFixed(2)}</td>
                                                                            </tr>
                                                                        ))}
                                                                    </tbody>
                                                                </table>
                                                                {/* Totals row */}
                                                                <div className="flex justify-end gap-6 mt-2 pt-2 border-t border-border/30 text-sm font-mono">
                                                                    <span className="text-muted-foreground">Gross: <strong className="text-foreground">₹{fmt(inv.grossAmount || 0)}</strong></span>
                                                                    {(inv.totalDiscount || 0) > 0 && (
                                                                        <span className="text-orange-500">Discount: <strong>₹{fmt(inv.totalDiscount || 0)}</strong></span>
                                                                    )}
                                                                    <span className="text-foreground">Net: <strong className="text-primary">₹{fmt(inv.netAmount || 0)}</strong></span>
                                                                </div>
                                                            </div>
                                                        )}
                                                        <div className="flex flex-wrap gap-4 text-xs text-muted-foreground">
                                                            {inv.driverName && <span><strong>Driver:</strong> {inv.driverName} {inv.driverPhone && `(${inv.driverPhone})`}</span>}
                                                            {inv.indentNo && <span><strong>Indent No:</strong> {inv.indentNo}</span>}
                                                            {inv.paymentMode && <span><strong>Payment Mode:</strong> {inv.paymentMode}</span>}
                                                            {inv.vehicleKM && <span><strong>KM:</strong> {inv.vehicleKM}</span>}
                                                        </div>
                                                    </td>
                                                </tr>
                                            )}
                                        </Fragment>
                                    );
                                })
                            )}
                        </tbody>
                    </table>
                </div>

                {/* Pagination */}
                <TablePagination
                    page={page}
                    totalPages={totalPages}
                    totalElements={totalElements}
                    pageSize={pageSize}
                    onPageChange={(p) => { setPage(p); setExpandedRowId(null); }}
                />
            </GlassCard>

            {/* Edit Invoice Modal */}
            <Modal isOpen={editModal} onClose={() => setEditModal(false)} title={`Edit Invoice — ${editInvoice?.billNo || ''}`}>
                <div className="space-y-4 max-h-[70vh] overflow-y-auto">
                    {editError && (
                        <div className="p-3 text-sm text-red-500 bg-red-500/10 border border-red-500/20 rounded-lg">{editError}</div>
                    )}

                    {/* Bill Type Toggle */}
                    <div>
                        <label className="block text-[9px] font-bold uppercase text-muted-foreground mb-1">Bill Type</label>
                        <div className="flex gap-2">
                            <button type="button" onClick={() => setEditBillType("CASH")}
                                className={`px-4 py-1.5 text-sm font-medium rounded-lg border transition-colors ${editBillType === "CASH" ? "bg-primary text-primary-foreground border-primary" : "bg-muted border-border text-muted-foreground"}`}>
                                Cash
                            </button>
                            <button type="button" onClick={() => setEditBillType("CREDIT")}
                                className={`px-4 py-1.5 text-sm font-medium rounded-lg border transition-colors ${editBillType === "CREDIT" ? "bg-primary text-primary-foreground border-primary" : "bg-muted border-border text-muted-foreground"}`}>
                                Credit
                            </button>
                        </div>
                    </div>

                    {/* Customer & Vehicle */}
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                        <div className="relative">
                            <label className="block text-[9px] font-bold uppercase text-muted-foreground mb-0.5">Customer</label>
                            <input
                                value={editCustomerSearch}
                                onChange={async (e) => {
                                    const q = e.target.value;
                                    setEditCustomerSearch(q);
                                    if (q.length >= 2) {
                                        try {
                                            const res = await getCustomers(q);
                                            setEditCustomerSuggestions(Array.isArray(res) ? res : res.content || []);
                                        } catch { setEditCustomerSuggestions([]); }
                                    } else {
                                        setEditCustomerSuggestions([]);
                                    }
                                }}
                                placeholder={editCustomer ? editCustomer.name : "Search customer..."}
                                className="w-full px-2 py-1.5 text-sm rounded border border-border bg-background text-foreground"
                            />
                            {editCustomerSuggestions.length > 0 && (
                                <div className="absolute z-50 w-full mt-1 bg-background border border-border rounded-lg shadow-lg max-h-40 overflow-y-auto">
                                    {editCustomerSuggestions.map((c: any) => (
                                        <button key={c.id} type="button"
                                            onClick={async () => {
                                                setEditCustomer(c);
                                                setEditCustomerSearch(c.name);
                                                setEditCustomerSuggestions([]);
                                                setEditVehicle(null);
                                                try {
                                                    const vehicles = await getVehiclesByCustomer(c.id);
                                                    setEditCustomerVehicles(vehicles);
                                                } catch { setEditCustomerVehicles([]); }
                                            }}
                                            className="w-full px-3 py-2 text-left text-sm hover:bg-muted transition-colors">
                                            {c.name} {c.phone && <span className="text-muted-foreground ml-1">({c.phone})</span>}
                                        </button>
                                    ))}
                                </div>
                            )}
                            {editCustomer && (
                                <button type="button" onClick={() => { setEditCustomer(null); setEditCustomerSearch(""); setEditCustomerVehicles([]); setEditVehicle(null); }}
                                    className="absolute right-2 top-6 p-0.5 text-muted-foreground hover:text-red-500">
                                    <X className="w-3 h-3" />
                                </button>
                            )}
                        </div>
                        <div>
                            <label className="block text-[9px] font-bold uppercase text-muted-foreground mb-0.5">Vehicle</label>
                            <select
                                value={editVehicle?.id || ""}
                                onChange={(e) => {
                                    const vid = Number(e.target.value);
                                    const v = editCustomerVehicles.find((v: any) => v.id === vid) || editVehicleResults.find(v => v.id === vid) || null;
                                    setEditVehicle(v);
                                }}
                                className="w-full px-2 py-1.5 text-sm rounded border border-border bg-background text-foreground"
                            >
                                <option value="">— No vehicle —</option>
                                {editCustomerVehicles.map((v: any) => (
                                    <option key={v.id} value={v.id}>{v.vehicleNumber}</option>
                                ))}
                            </select>
                            {/* Vehicle search for non-customer vehicles */}
                            <input
                                value={editVehicleSearch}
                                onChange={async (e) => {
                                    const q = e.target.value;
                                    setEditVehicleSearch(q);
                                    if (q.length >= 3) {
                                        try {
                                            const results = await searchVehicles(q);
                                            const custIds = new Set(editCustomerVehicles.map((v: any) => v.id));
                                            setEditVehicleResults(results.filter(v => !custIds.has(v.id)));
                                        } catch { setEditVehicleResults([]); }
                                    } else { setEditVehicleResults([]); }
                                }}
                                placeholder="Search other vehicle..."
                                className="w-full px-2 py-1.5 text-sm rounded border border-border bg-background text-foreground mt-1"
                            />
                            {editVehicleResults.length > 0 && (
                                <div className="mt-1 bg-background border border-border rounded-lg shadow-lg max-h-32 overflow-y-auto">
                                    {editVehicleResults.map(v => (
                                        <button key={v.id} type="button"
                                            onClick={() => { setEditVehicle(v); setEditVehicleSearch(""); setEditVehicleResults([]); }}
                                            className="w-full px-3 py-1.5 text-left text-sm hover:bg-muted transition-colors">
                                            {v.vehicleNumber} {(v as any).customer && <span className="text-muted-foreground ml-1">({(v as any).customer.name})</span>}
                                        </button>
                                    ))}
                                </div>
                            )}
                        </div>
                    </div>

                    {/* Product Lines */}
                    <div>
                        <div className="flex items-center justify-between mb-2">
                            <span className="text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Product Line Items</span>
                            <button onClick={addEditLine} className="flex items-center gap-1 px-2 py-1 text-xs font-medium rounded-md bg-primary/10 text-primary hover:bg-primary/20 transition-colors">
                                <Plus className="w-3 h-3" /> Add Line
                            </button>
                        </div>

                        <div className="space-y-2">
                            {editLines.map((line, idx) => (
                                <div key={idx} className="grid grid-cols-12 gap-2 items-end p-3 rounded-lg bg-muted/30 border border-border/30">
                                    <div className="col-span-3">
                                        <label className="block text-[9px] font-bold uppercase text-muted-foreground mb-0.5">Product</label>
                                        <select
                                            value={line.product?.id || ""}
                                            onChange={e => {
                                                const p = allProducts.find(pr => pr.id === Number(e.target.value)) || null;
                                                updateEditLine(idx, {
                                                    product: p,
                                                    unitPrice: p ? String(p.price) : line.unitPrice,
                                                });
                                            }}
                                            className="w-full px-2 py-1.5 text-sm rounded border border-border bg-background text-foreground"
                                        >
                                            <option value="">Select...</option>
                                            {allProducts.map(p => (
                                                <option key={p.id} value={p.id}>{p.name}</option>
                                            ))}
                                        </select>
                                    </div>
                                    <div className="col-span-2">
                                        <label className="block text-[9px] font-bold uppercase text-muted-foreground mb-0.5">Nozzle</label>
                                        <select
                                            value={line.nozzle?.id || ""}
                                            onChange={e => {
                                                const n = allNozzles.find(nz => nz.id === Number(e.target.value)) || null;
                                                updateEditLine(idx, { nozzle: n });
                                            }}
                                            className="w-full px-2 py-1.5 text-sm rounded border border-border bg-background text-foreground"
                                        >
                                            <option value="">—</option>
                                            {allNozzles.map(n => (
                                                <option key={n.id} value={n.id}>{n.nozzleName}</option>
                                            ))}
                                        </select>
                                    </div>
                                    <div className="col-span-1">
                                        <label className="block text-[9px] font-bold uppercase text-muted-foreground mb-0.5">Qty</label>
                                        <input
                                            type="number" step="0.01" value={line.quantity}
                                            onChange={e => updateEditLine(idx, { quantity: e.target.value })}
                                            className="w-full px-2 py-1.5 text-sm rounded border border-border bg-background text-foreground text-right"
                                        />
                                    </div>
                                    <div className="col-span-1">
                                        <label className="block text-[9px] font-bold uppercase text-muted-foreground mb-0.5">Rate</label>
                                        <input
                                            type="number" step="0.01" value={line.unitPrice}
                                            onChange={e => updateEditLine(idx, { unitPrice: e.target.value })}
                                            className="w-full px-2 py-1.5 text-sm rounded border border-border bg-background text-foreground text-right"
                                        />
                                    </div>
                                    <div className="col-span-1">
                                        <label className="block text-[9px] font-bold uppercase text-muted-foreground mb-0.5">Disc/L</label>
                                        <input
                                            type="number" step="0.01" value={line.discountRate}
                                            onChange={e => updateEditLine(idx, { discountRate: e.target.value })}
                                            className="w-full px-2 py-1.5 text-sm rounded border border-border bg-background text-foreground text-right"
                                            placeholder="0"
                                        />
                                    </div>
                                    <div className="col-span-1">
                                        <label className="block text-[9px] font-bold uppercase text-muted-foreground mb-0.5">Gross</label>
                                        <div className="px-2 py-1.5 text-sm font-mono text-muted-foreground text-right">₹{line.grossAmount.toFixed(2)}</div>
                                    </div>
                                    <div className="col-span-1">
                                        <label className="block text-[9px] font-bold uppercase text-muted-foreground mb-0.5">Disc</label>
                                        <div className="px-2 py-1.5 text-sm font-mono text-orange-500 text-right">{line.discountAmount > 0 ? `₹${line.discountAmount.toFixed(2)}` : "—"}</div>
                                    </div>
                                    <div className="col-span-1">
                                        <label className="block text-[9px] font-bold uppercase text-muted-foreground mb-0.5">Amount</label>
                                        <div className="px-2 py-1.5 text-sm font-mono font-bold text-foreground text-right">₹{line.amount.toFixed(2)}</div>
                                    </div>
                                    <div className="col-span-1 flex items-end justify-center">
                                        <button onClick={() => removeEditLine(idx)} className="p-1.5 text-muted-foreground hover:text-red-500 transition-colors">
                                            <X className="w-4 h-4" />
                                        </button>
                                    </div>
                                </div>
                            ))}
                        </div>

                        {/* Totals */}
                        <div className="flex justify-end gap-6 mt-3 pt-3 border-t border-border/50 text-sm font-mono">
                            <span className="text-muted-foreground">Gross: <strong className="text-foreground">₹{fmt(editTotalGross)}</strong></span>
                            {editTotalDiscount > 0 && (
                                <span className="text-orange-500">Discount: <strong>₹{fmt(editTotalDiscount)}</strong></span>
                            )}
                            <span className="text-foreground text-base">Net Amount: <strong className="text-primary">₹{fmt(editNetAmount)}</strong></span>
                        </div>
                    </div>

                    {/* Other fields */}
                    <div className="grid grid-cols-2 md:grid-cols-4 gap-3 pt-2 border-t border-border/30">
                        <div>
                            <label className="block text-[9px] font-bold uppercase text-muted-foreground mb-0.5">Driver Name</label>
                            <input value={editDriverName} onChange={e => setEditDriverName(e.target.value)}
                                className="w-full px-2 py-1.5 text-sm rounded border border-border bg-background text-foreground" />
                        </div>
                        <div>
                            <label className="block text-[9px] font-bold uppercase text-muted-foreground mb-0.5">Driver Phone</label>
                            <input value={editDriverPhone} onChange={e => setEditDriverPhone(e.target.value)}
                                className="w-full px-2 py-1.5 text-sm rounded border border-border bg-background text-foreground" />
                        </div>
                        <div>
                            <label className="block text-[9px] font-bold uppercase text-muted-foreground mb-0.5">Indent No</label>
                            <input value={editIndentNo} onChange={e => setEditIndentNo(e.target.value)}
                                className="w-full px-2 py-1.5 text-sm rounded border border-border bg-background text-foreground" />
                        </div>
                        <div>
                            <label className="block text-[9px] font-bold uppercase text-muted-foreground mb-0.5">Vehicle KM</label>
                            <input type="number" value={editVehicleKM} onChange={e => setEditVehicleKM(e.target.value)}
                                className="w-full px-2 py-1.5 text-sm rounded border border-border bg-background text-foreground" />
                        </div>
                    </div>

                    {/* Save button */}
                    <div className="flex justify-end gap-2 pt-3">
                        <button onClick={() => setEditModal(false)}
                            className="px-4 py-2 text-sm rounded-lg border border-border text-muted-foreground hover:bg-muted transition-colors">
                            Cancel
                        </button>
                        <button onClick={handleSaveEdit} disabled={saving}
                            className="flex items-center gap-2 px-4 py-2 text-sm font-medium rounded-lg bg-primary text-primary-foreground hover:bg-primary/90 disabled:opacity-50 transition-colors">
                            <Save className="w-4 h-4" />
                            {saving ? "Saving..." : "Save Changes"}
                        </button>
                    </div>
                </div>
            </Modal>

            {/* Delete Confirmation Modal */}
            <Modal isOpen={!!deleteConfirm} onClose={() => setDeleteConfirm(null)} title="Delete Invoice">
                <div className="space-y-4">
                    <FormErrorBanner message={editError} onDismiss={() => setEditError("")} />
                    <p className="text-sm text-foreground">
                        Are you sure you want to delete invoice <strong className="font-mono">{deleteConfirm?.billNo}</strong>?
                        This action cannot be undone.
                    </p>
                    {deleteConfirm && (
                        <div className="p-3 rounded-lg bg-muted/50 text-sm space-y-1">
                            <div>Customer: <strong>{deleteConfirm.customer?.name || "Walk-in"}</strong></div>
                            <div>Amount: <strong>₹{fmt(deleteConfirm.netAmount || 0)}</strong></div>
                            <div>Date: <strong>{new Date(deleteConfirm.date).toLocaleString()}</strong></div>
                        </div>
                    )}
                    <div className="flex justify-end gap-2">
                        <button onClick={() => setDeleteConfirm(null)}
                            className="px-4 py-2 text-sm rounded-lg border border-border text-muted-foreground hover:bg-muted transition-colors">
                            Cancel
                        </button>
                        <button onClick={handleDelete} disabled={deleting}
                            className="flex items-center gap-2 px-4 py-2 text-sm font-medium rounded-lg bg-red-500 text-white hover:bg-red-600 disabled:opacity-50 transition-colors">
                            <Trash2 className="w-4 h-4" />
                            {deleting ? "Deleting..." : "Delete Invoice"}
                        </button>
                    </div>
                </div>
            </Modal>
        </div>
    );
}
