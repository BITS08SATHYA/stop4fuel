"use client";

import { useEffect, useState, useMemo } from "react";
import { GlassCard } from "@/components/ui/glass-card";
import { TablePagination, useClientPagination } from "@/components/ui/table-pagination";
import { Modal } from "@/components/ui/modal";
import {
    getNozzleInventories,
    getNozzles,
    createNozzleInventory,
    updateNozzleInventory,
    downloadNozzleInventoryReport,
    NozzleInventory,
    Nozzle,
    deleteNozzleInventory
} from "@/lib/api/station";
import { Fuel, Plus, Calendar, Trash2, Edit2, Search, FileText, FileSpreadsheet, BarChart3 } from "lucide-react";
import { useFormValidation, required } from "@/lib/validation";
import { FieldError, inputErrorClass, FormErrorBanner } from "@/components/ui/field-error";

function getCurrentMonthRange() {
    const now = new Date();
    const y = now.getFullYear();
    const m = String(now.getMonth() + 1).padStart(2, "0");
    return {
        fromDate: `${y}-${m}-01`,
        toDate: `${y}-${m}-${String(now.getDate()).padStart(2, "0")}`
    };
}

export default function NozzleInventoryPage() {
    const [inventories, setInventories] = useState<NozzleInventory[]>([]);
    const [nozzles, setNozzles] = useState<Nozzle[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [editingId, setEditingId] = useState<number | null>(null);
    const [searchQuery, setSearchQuery] = useState("");
    const [nozzleFilter, setNozzleFilter] = useState<string>("");
    const [productFilter, setProductFilter] = useState<string>("");
    const [isDownloading, setIsDownloading] = useState(false);

    const { fromDate: defaultFrom, toDate: defaultTo } = getCurrentMonthRange();
    const [fromDate, setFromDate] = useState(defaultFrom);
    const [toDate, setToDate] = useState(defaultTo);

    const filteredInv = useMemo(() => inventories.filter((inv) => {
        const q = searchQuery.toLowerCase();
        const matchesSearch = !searchQuery || inv.nozzle?.nozzleName?.toLowerCase().includes(q) || inv.nozzle?.pump?.name?.toLowerCase().includes(q) || inv.nozzle?.tank?.product?.name?.toLowerCase().includes(q);
        const matchesNozzle = !nozzleFilter || String(inv.nozzle?.id) === nozzleFilter;
        const matchesProduct = !productFilter || String(inv.nozzle?.tank?.product?.id) === productFilter;
        return matchesSearch && matchesNozzle && matchesProduct;
    }), [inventories, searchQuery, nozzleFilter, productFilter]);

    // When product filter is active, aggregate by date
    type DailySummary = { date: string; product: string; nozzles: string; totalSales: number; rate: number; amount: number };
    const isAggregated = !!productFilter;
    const aggregatedData = useMemo<DailySummary[]>(() => {
        if (!isAggregated) return [];
        const grouped = new Map<string, NozzleInventory[]>();
        filteredInv.forEach(inv => {
            const key = inv.date;
            if (!grouped.has(key)) grouped.set(key, []);
            grouped.get(key)!.push(inv);
        });
        return Array.from(grouped.entries())
            .map(([date, items]) => {
                const totalSales = items.reduce((sum, i) => sum + (i.sales || 0), 0);
                const rate = items[0]?.rate || 0;
                return {
                    date,
                    product: items[0]?.nozzle?.tank?.product?.name || '-',
                    nozzles: [...new Set(items.map(i => i.nozzle?.nozzleName))].sort().join(', '),
                    totalSales,
                    rate,
                    amount: totalSales * rate,
                };
            })
            .sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime());
    }, [filteredInv, isAggregated]);

    const displayData = isAggregated ? aggregatedData : filteredInv;
    const { page, setPage, totalPages, totalElements, pageSize, paginatedData: pagedData } = useClientPagination(displayData as any[]);

    // Form State
    const [date, setDate] = useState(new Date().toISOString().split('T')[0]);
    const [nozzleId, setNozzleId] = useState("");
    const [openReading, setOpenReading] = useState("");
    const [closeReading, setCloseReading] = useState("");
    const [calculatedSales, setCalculatedSales] = useState(0);
    const [apiError, setApiError] = useState("");
    const validationRules = useMemo(() => ({
        nozzleId: [required("Nozzle is required")],
        openReading: [required("Open reading is required")],
        closeReading: [required("Close reading is required")],
    }), []);
    const { errors, validate, clearError, clearAllErrors } = useFormValidation(validationRules);

    const uniqueProducts = useMemo(() => {
        const map = new Map<number, { id: number; name: string }>();
        nozzles.forEach(n => {
            if (n.tank?.product) {
                map.set(n.tank.product.id, { id: n.tank.product.id, name: n.tank.product.name });
            }
        });
        return Array.from(map.values());
    }, [nozzles]);

    const loadData = async () => {
        setIsLoading(true);
        try {
            const nData = await getNozzles();
            setNozzles(nData.filter(n => n.active));
        } catch (err) {
            console.error("Failed to load nozzles", err);
        }
        try {
            const params: { fromDate?: string; toDate?: string } = {};
            if (fromDate) params.fromDate = fromDate;
            if (toDate) params.toDate = toDate;
            const iData = await getNozzleInventories(params);
            setInventories(iData.sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime()));
        } catch (err) {
            console.error("Failed to load nozzle inventory logs", err);
        }
        setIsLoading(false);
    };

    useEffect(() => { loadData(); }, [fromDate, toDate]);

    useEffect(() => {
        const open = parseFloat(openReading);
        const close = parseFloat(closeReading);
        if (!isNaN(open) && !isNaN(close)) setCalculatedSales(Math.max(0, close - open));
        else setCalculatedSales(0);
    }, [openReading, closeReading]);

    const handleEdit = (inv: NozzleInventory) => {
        clearAllErrors(); setApiError(""); setEditingId(inv.id);
        setDate(inv.date); setNozzleId(String(inv.nozzle.id));
        setOpenReading(String(inv.openMeterReading || "")); setCloseReading(String(inv.closeMeterReading || ""));
        setIsModalOpen(true);
    };

    const handleSave = async (e: React.FormEvent) => {
        e.preventDefault(); setApiError("");
        if (!validate({ nozzleId, openReading, closeReading })) return;
        try {
            const payload = { date, nozzle: { id: Number(nozzleId) }, openMeterReading: Number(openReading), closeMeterReading: Number(closeReading) };
            if (editingId) await updateNozzleInventory(editingId, payload as any);
            else await createNozzleInventory(payload as any);
            setIsModalOpen(false); resetForm(); loadData();
        } catch (err) {
            console.error("Failed to save nozzle inventory", err); setApiError("Error saving inventory details");
        }
    };

    const handleDelete = async (id: number) => {
        if (!confirm("Are you sure you want to delete this reading?")) return;
        try { await deleteNozzleInventory(id); loadData(); } catch (err) { console.error("Failed to delete", err); setApiError("Error deleting record"); }
    };

    const handleDownload = async (format: 'pdf' | 'excel', reportType?: string) => {
        if (!fromDate || !toDate) return;
        setIsDownloading(true);
        try {
            const nid = nozzleFilter ? Number(nozzleFilter) : undefined;
            const pid = productFilter ? Number(productFilter) : undefined;
            const blob = await downloadNozzleInventoryReport(fromDate, toDate, format, nid, pid, reportType);
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            const ext = format === 'pdf' ? 'pdf' : 'xlsx';
            let label = 'All_Nozzles';
            if (nozzleFilter) label = nozzles.find(n => String(n.id) === nozzleFilter)?.nozzleName || 'Nozzle';
            else if (productFilter) label = uniqueProducts.find(p => String(p.id) === productFilter)?.name || 'Product';
            const prefix = reportType === 'meter_tracker' ? 'MeterTracker' : productFilter ? 'ProductSales' : 'NozzleInventory';
            a.download = `${prefix}_${label}_${fromDate}_${toDate}.${ext}`;
            document.body.appendChild(a); a.click(); a.remove();
            window.URL.revokeObjectURL(url);
        } catch (err) { console.error("Failed to download report", err); setApiError("Error downloading report"); }
        setIsDownloading(false);
    };

    const resetForm = () => { setEditingId(null); setNozzleId(""); setOpenReading(""); setCloseReading(""); setCalculatedSales(0); };

    return (
        <div className="p-6 h-screen overflow-hidden bg-background transition-colors duration-300">
            <div className="max-w-7xl mx-auto">
                <div className="flex justify-between items-center mb-8">
                    <div>
                        <h1 className="text-4xl font-bold text-foreground tracking-tight">
                            Nozzle <span className="text-gradient">Daily Inventory</span>
                        </h1>
                        <p className="text-muted-foreground mt-2">Record daily meter readings and track sales per nozzle.</p>
                    </div>
                    <button onClick={() => { resetForm(); clearAllErrors(); setApiError(""); setIsModalOpen(true); }}
                        className="btn-gradient px-6 py-3 rounded-xl font-medium flex items-center gap-2 shadow-lg hover:shadow-xl transition-all">
                        <Plus className="w-5 h-5" /> Add Daily Reading
                    </button>
                </div>

                {/* Filter Bar */}
                <div className="mb-6 flex flex-wrap gap-3 items-center">
                    <div className="relative flex-1 min-w-[200px] max-w-md">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                        <input type="text" placeholder="Search by nozzle, pump, or product..." value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            className="w-full pl-10 pr-4 py-2.5 bg-card border border-border rounded-xl text-foreground text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/50" />
                    </div>
                    <select value={nozzleFilter} onChange={(e) => { setNozzleFilter(e.target.value); if (e.target.value) setProductFilter(""); }}
                        className="px-4 py-2.5 bg-card border border-border rounded-xl text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50">
                        <option value="">All Nozzles</option>
                        {nozzles.map((n) => <option key={n.id} value={n.id}>{n.nozzleName} ({n.pump.name})</option>)}
                    </select>
                    <select value={productFilter} onChange={(e) => { setProductFilter(e.target.value); if (e.target.value) setNozzleFilter(""); }}
                        className="px-4 py-2.5 bg-card border border-border rounded-xl text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50">
                        <option value="">All Products</option>
                        {uniqueProducts.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
                    </select>
                    <div className="flex items-center gap-2">
                        <label className="text-xs text-muted-foreground font-medium">From</label>
                        <input type="date" value={fromDate} onChange={(e) => setFromDate(e.target.value)}
                            className="px-3 py-2.5 bg-card border border-border rounded-xl text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50" />
                        <label className="text-xs text-muted-foreground font-medium">To</label>
                        <input type="date" value={toDate} onChange={(e) => setToDate(e.target.value)}
                            className="px-3 py-2.5 bg-card border border-border rounded-xl text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50" />
                    </div>
                    {(searchQuery || nozzleFilter || productFilter || fromDate !== defaultFrom || toDate !== defaultTo) && (
                        <button onClick={() => { setSearchQuery(""); setNozzleFilter(""); setProductFilter(""); setFromDate(defaultFrom); setToDate(defaultTo); }}
                            className="px-3 py-2.5 text-xs text-muted-foreground hover:text-foreground">Reset</button>
                    )}

                    {/* Download Buttons */}
                    <div className="flex items-center gap-1 ml-auto">
                        <button onClick={() => handleDownload('pdf')} disabled={isDownloading || !fromDate || !toDate}
                            className="flex items-center gap-1.5 px-3 py-2.5 bg-red-500/10 hover:bg-red-500/20 text-red-500 rounded-xl text-xs font-medium transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                            title={isAggregated ? "Download Product Daily Sales PDF" : "Download PDF Report"}>
                            <FileText className="w-3.5 h-3.5" /> PDF
                        </button>
                        <button onClick={() => handleDownload('excel')} disabled={isDownloading || !fromDate || !toDate}
                            className="flex items-center gap-1.5 px-3 py-2.5 bg-green-500/10 hover:bg-green-500/20 text-green-500 rounded-xl text-xs font-medium transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                            title={isAggregated ? "Download Product Daily Sales Excel" : "Download Excel Report"}>
                            <FileSpreadsheet className="w-3.5 h-3.5" /> Excel
                        </button>
                        {isAggregated && (
                            <>
                                <div className="w-px h-6 bg-border mx-1" />
                                <button onClick={() => handleDownload('pdf', 'meter_tracker')} disabled={isDownloading || !fromDate || !toDate}
                                    className="flex items-center gap-1.5 px-3 py-2.5 bg-purple-500/10 hover:bg-purple-500/20 text-purple-500 rounded-xl text-xs font-medium transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                                    title="Download Meter Reading Tracker PDF">
                                    <BarChart3 className="w-3.5 h-3.5" /> Tracker PDF
                                </button>
                                <button onClick={() => handleDownload('excel', 'meter_tracker')} disabled={isDownloading || !fromDate || !toDate}
                                    className="flex items-center gap-1.5 px-3 py-2.5 bg-purple-500/10 hover:bg-purple-500/20 text-purple-500 rounded-xl text-xs font-medium transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                                    title="Download Meter Reading Tracker Excel">
                                    <BarChart3 className="w-3.5 h-3.5" /> Tracker Excel
                                </button>
                            </>
                        )}
                    </div>
                </div>

                {isLoading ? (
                    <div className="flex flex-col items-center justify-center py-20 text-muted-foreground">
                        <div className="w-12 h-12 border-4 border-primary/20 border-t-primary rounded-full animate-spin mb-4"></div>
                        <p className="animate-pulse">Loading reading logs...</p>
                    </div>
                ) : displayData.length === 0 ? (
                    <div className="text-center py-20 bg-black/5 dark:bg-white/5 rounded-2xl border border-dashed border-border">
                        <Calendar className="w-16 h-16 mx-auto text-muted-foreground mb-4 opacity-50" />
                        <h3 className="text-xl font-semibold text-foreground mb-2">No Records Found</h3>
                        <p className="text-muted-foreground mb-6">
                            {inventories.length === 0 ? "Start by recording your first daily nozzle meter reading." : "No readings match the selected filters."}
                        </p>
                    </div>
                ) : (
                    <GlassCard className="overflow-hidden border-none p-0">
                        <div className="overflow-x-auto">
                            <table className="w-full text-left border-collapse">
                                <thead>
                                    <tr className="bg-white/5 border-b border-border/50">
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center w-16">#</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground w-32">Date</th>
                                        {isAggregated ? (
                                            <>
                                                <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Product</th>
                                                <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Nozzles</th>
                                            </>
                                        ) : (
                                            <>
                                                <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Nozzle/Pump Details</th>
                                                <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right w-28">Open Reading</th>
                                                <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right w-28">Close Reading</th>
                                            </>
                                        )}
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right w-24 italic">Sales (L)</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right w-20">Rate</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right w-28">Amount</th>
                                        {!isAggregated && (
                                            <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center w-24">Actions</th>
                                        )}
                                    </tr>
                                </thead>
                                <tbody className="divide-y divide-border/30">
                                    {isAggregated ? (
                                        (pagedData as DailySummary[]).map((row, idx) => (
                                            <tr key={row.date} className="hover:bg-white/5 transition-colors">
                                                <td className="px-6 py-4 text-xs font-mono text-muted-foreground text-center">{page * pageSize + idx + 1}</td>
                                                <td className="px-6 py-4"><div className="text-sm font-medium text-foreground">{new Date(row.date).toLocaleDateString('en-GB')}</div></td>
                                                <td className="px-6 py-4"><div className="text-sm font-bold text-foreground">{row.product}</div></td>
                                                <td className="px-6 py-4"><div className="text-xs text-muted-foreground">{row.nozzles}</div></td>
                                                <td className="px-6 py-4 text-right font-black text-primary text-base font-mono bg-primary/5">
                                                    {row.totalSales?.toLocaleString()} <span className="text-[10px] font-bold opacity-70 ml-1">L</span>
                                                </td>
                                                <td className="px-6 py-4 text-right font-mono text-sm text-muted-foreground">{row.rate?.toLocaleString(undefined, {minimumFractionDigits: 2, maximumFractionDigits: 2})}</td>
                                                <td className="px-6 py-4 text-right font-bold text-foreground font-mono text-sm bg-primary/5">
                                                    {row.amount?.toLocaleString(undefined, {minimumFractionDigits: 2, maximumFractionDigits: 2})}
                                                </td>
                                            </tr>
                                        ))
                                    ) : (
                                        (pagedData as NozzleInventory[]).map((inv, idx) => (
                                            <tr key={inv.id} className="hover:bg-white/5 transition-colors group">
                                                <td className="px-6 py-4 text-xs font-mono text-muted-foreground text-center">{page * pageSize + idx + 1}</td>
                                                <td className="px-6 py-4"><div className="text-sm font-medium text-foreground">{new Date(inv.date).toLocaleDateString('en-GB')}</div></td>
                                                <td className="px-6 py-4">
                                                    <div className="flex items-center gap-3">
                                                        <div className="p-2 rounded-lg bg-green-500/10 text-green-500"><Fuel className="w-4 h-4" /></div>
                                                        <div>
                                                            <div className="text-sm font-bold text-foreground">{inv.nozzle?.nozzleName || '-'}</div>
                                                            <div className="text-[10px] text-muted-foreground flex items-center gap-1">
                                                                <span>{inv.nozzle?.pump?.name || '-'}</span><span>•</span><span>{inv.nozzle?.tank?.product?.name || '-'}</span>
                                                            </div>
                                                        </div>
                                                    </div>
                                                </td>
                                                <td className="px-6 py-4 text-right font-mono text-sm">{inv.openMeterReading?.toLocaleString()}</td>
                                                <td className="px-6 py-4 text-right font-mono text-sm">{inv.closeMeterReading?.toLocaleString()}</td>
                                                <td className="px-6 py-4 text-right font-black text-primary text-base font-mono bg-primary/5">
                                                    {inv.sales?.toLocaleString()} <span className="text-[10px] font-bold opacity-70 ml-1">L</span>
                                                </td>
                                                <td className="px-6 py-4 text-right font-mono text-sm text-muted-foreground">{inv.rate?.toLocaleString(undefined, {minimumFractionDigits: 2, maximumFractionDigits: 2}) || '-'}</td>
                                                <td className="px-6 py-4 text-right font-bold text-foreground font-mono text-sm bg-primary/5">
                                                    {inv.amount?.toLocaleString(undefined, {minimumFractionDigits: 2, maximumFractionDigits: 2}) || '-'}
                                                </td>
                                                <td className="px-6 py-4">
                                                    <div className="flex justify-center gap-2">
                                                        <button onClick={() => handleEdit(inv)} className="p-1.5 rounded-lg hover:bg-white/10 text-muted-foreground hover:text-foreground transition-colors" title="Edit">
                                                            <Edit2 className="w-4 h-4" />
                                                        </button>
                                                        <button onClick={() => handleDelete(inv.id)} className="p-1.5 rounded-lg hover:bg-red-500/10 text-muted-foreground hover:text-red-500 transition-colors" title="Delete">
                                                            <Trash2 className="w-4 h-4" />
                                                        </button>
                                                    </div>
                                                </td>
                                            </tr>
                                        ))
                                    )}
                                </tbody>
                            </table>
                        </div>
                        <TablePagination page={page} totalPages={totalPages} totalElements={totalElements} pageSize={pageSize} onPageChange={setPage} />
                    </GlassCard>
                )}
            </div>

            <Modal isOpen={isModalOpen} onClose={() => { setIsModalOpen(false); resetForm(); }}
                title={editingId ? "Edit Nozzle Reading" : "Record Daily Nozzle Reading"}>
                <form onSubmit={handleSave} className="space-y-4">
                    <FormErrorBanner message={apiError} onDismiss={() => setApiError("")} />
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5 flex items-center gap-2">
                                <Calendar className="w-4 h-4 text-primary" /> Date
                            </label>
                            <input type="date" required value={date} onChange={(e) => setDate(e.target.value)}
                                className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50" />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5">Selected Nozzle</label>
                            <select value={nozzleId} onChange={(e) => { setNozzleId(e.target.value); clearError("nozzleId"); }}
                                className={`w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50 ${inputErrorClass(errors.nozzleId)}`}>
                                <option value="">Select Nozzle...</option>
                                {nozzles.map(n => <option key={n.id} value={n.id}>{n.nozzleName} ({n.pump.name} - {n.tank.product.name})</option>)}
                            </select>
                            <FieldError error={errors.nozzleId} />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5">Open Reading</label>
                            <input type="number" step="0.01" value={openReading} onChange={(e) => { setOpenReading(e.target.value); clearError("openReading"); }}
                                className={`w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50 font-mono ${inputErrorClass(errors.openReading)}`} placeholder="0.00" />
                            <FieldError error={errors.openReading} />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5">Close Reading</label>
                            <input type="number" step="0.01" value={closeReading} onChange={(e) => { setCloseReading(e.target.value); clearError("closeReading"); }}
                                className={`w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50 font-mono ${inputErrorClass(errors.closeReading)}`} placeholder="0.00" />
                            <FieldError error={errors.closeReading} />
                        </div>
                    </div>
                    <div className="bg-primary/5 border border-primary/20 rounded-2xl p-6 mt-4">
                        <div className="flex justify-between items-center">
                            <div>
                                <p className="text-sm font-medium text-primary uppercase tracking-wider">Calculated Sales</p>
                                <p className="text-xs text-muted-foreground mt-1">Difference between readings</p>
                            </div>
                            <div className="text-right">
                                <span className="text-4xl font-bold text-primary font-mono">{calculatedSales.toFixed(2)}</span>
                                <span className="text-lg font-medium text-primary ml-2 uppercase">L</span>
                            </div>
                        </div>
                    </div>
                    <div className="flex justify-end gap-3 pt-6 border-t border-border mt-6">
                        <button type="button" onClick={() => setIsModalOpen(false)}
                            className="px-6 py-2.5 rounded-xl font-medium text-foreground bg-black/5 dark:bg-white/5 hover:bg-black/10 transition-colors">Cancel</button>
                        <button type="submit" className="btn-gradient px-8 py-2.5 rounded-xl font-medium shadow-lg hover:shadow-xl transition-all">
                            {editingId ? "Update Reading" : "Save Reading"}
                        </button>
                    </div>
                </form>
            </Modal>
        </div>
    );
}
