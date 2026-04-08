"use client";

import { useEffect, useState, useMemo } from "react";
import { GlassCard } from "@/components/ui/glass-card";
import { TablePagination, useClientPagination } from "@/components/ui/table-pagination";
import { Modal } from "@/components/ui/modal";
import { StyledSelect } from "@/components/ui/styled-select";
import {
    getProductInventories,
    getActiveProducts,
    createProductInventory,
    updateProductInventory,
    downloadProductInventoryReport,
    ProductInventory,
    Product,
    deleteProductInventory
} from "@/lib/api/station";
import { Box, Plus, Calendar, Archive, TrendingUp, Trash2, Edit2, Search, FileText, FileSpreadsheet } from "lucide-react";
import { useFormValidation, required } from "@/lib/validation";
import { FieldError, inputErrorClass, FormErrorBanner } from "@/components/ui/field-error";
import { PermissionGate } from "@/components/permission-gate";

function getCurrentMonthRange() {
    const now = new Date();
    const y = now.getFullYear();
    const m = String(now.getMonth() + 1).padStart(2, "0");
    return {
        fromDate: `${y}-${m}-01`,
        toDate: `${y}-${m}-${String(now.getDate()).padStart(2, "0")}`
    };
}

export default function ProductInventoryPage() {
    const [inventories, setInventories] = useState<ProductInventory[]>([]);
    const [products, setProducts] = useState<Product[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [editingId, setEditingId] = useState<number | null>(null);
    const [searchQuery, setSearchQuery] = useState("");
    const [productFilter, setProductFilter] = useState<string>("");
    const [isDownloading, setIsDownloading] = useState(false);

    // Date range filter — defaults to current month
    const { fromDate: defaultFrom, toDate: defaultTo } = getCurrentMonthRange();
    const [fromDate, setFromDate] = useState(defaultFrom);
    const [toDate, setToDate] = useState(defaultTo);

    const filteredInv = useMemo(() => inventories.filter((inv) => {
        const q = searchQuery.toLowerCase();
        const matchesSearch = !searchQuery || inv.product.name?.toLowerCase().includes(q) || inv.product.category?.toLowerCase().includes(q) || inv.product.brand?.toLowerCase().includes(q);
        const matchesProduct = !productFilter || String(inv.product.id) === productFilter;
        return matchesSearch && matchesProduct;
    }), [inventories, searchQuery, productFilter]);

    const { page, setPage, totalPages, totalElements, pageSize, paginatedData: pagedInv } = useClientPagination(filteredInv);

    // Form State
    const [date, setDate] = useState(new Date().toISOString().split('T')[0]);
    const [productId, setProductId] = useState("");
    const [productSearch, setProductSearch] = useState("");
    const [openStock, setOpenStock] = useState("");
    const [incomeStock, setIncomeStock] = useState("");
    const [salesInput, setSalesInput] = useState("");
    const [rateInput, setRateInput] = useState("");
    const [apiError, setApiError] = useState("");
    const validationRules = useMemo(() => ({
        productId: [required("Product is required")],
        openStock: [required("Opening stock is required")],
    }), []);
    const { errors, validate, clearError, clearAllErrors } = useFormValidation(validationRules);

    // Derived Calculations
    const totalStock = (parseFloat(openStock) || 0) + (parseFloat(incomeStock) || 0);
    const sales = parseFloat(salesInput) || 0;
    const closeStock = Math.max(0, totalStock - sales);
    const amount = sales * (parseFloat(rateInput) || 0);

    const loadData = async () => {
        setIsLoading(true);
        try {
            const pData = await getActiveProducts();
            setProducts(pData);
        } catch (err) {
            console.error("Failed to load products", err);
        }

        try {
            const params: { fromDate?: string; toDate?: string } = {};
            if (fromDate) params.fromDate = fromDate;
            if (toDate) params.toDate = toDate;
            const iData = await getProductInventories(params);
            setInventories(iData.sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime()));
        } catch (err) {
            console.error("Failed to load product inventory logs", err);
        }
        setIsLoading(false);
    };

    useEffect(() => {
        loadData();
    }, [fromDate, toDate]);


    const handleEdit = (inv: ProductInventory) => {
        clearAllErrors();
        setApiError("");
        setEditingId(inv.id);
        setDate(inv.date);
        setProductId(String(inv.product.id));
        setProductSearch(inv.product.name || "");
        setOpenStock(String(inv.openStock || ""));
        setIncomeStock(String(inv.incomeStock || ""));
        setSalesInput(String(inv.sales || ""));
        setRateInput(String(inv.rate || ""));
        setIsModalOpen(true);
    };

    const handleSave = async (e: React.FormEvent) => {
        e.preventDefault();
        setApiError("");
        if (!validate({ productId, openStock })) return;
        try {
            const payload = {
                date,
                product: { id: Number(productId) },
                openStock: Number(openStock),
                incomeStock: Number(incomeStock),
                closeStock,
                rate: rateInput ? Number(rateInput) : undefined,
            };

            if (editingId) {
                await updateProductInventory(editingId, payload as any);
            } else {
                await createProductInventory(payload as any);
            }
            setIsModalOpen(false);
            resetForm();
            loadData();
        } catch (err) {
            console.error("Failed to save product inventory", err);
            setApiError("Error saving inventory details");
        }
    };

    const handleDelete = async (id: number) => {
        if (!confirm("Are you sure you want to delete this check?")) return;
        try {
            await deleteProductInventory(id);
            loadData();
        } catch (err) {
            console.error("Failed to delete", err);
            setApiError("Error deleting record");
        }
    };

    const handleDownload = async (format: 'pdf' | 'excel') => {
        if (!fromDate || !toDate) return;
        setIsDownloading(true);
        try {
            const pid = productFilter ? Number(productFilter) : undefined;
            const blob = await downloadProductInventoryReport(fromDate, toDate, format, pid);
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            const ext = format === 'pdf' ? 'pdf' : 'xlsx';
            const productLabel = productFilter ? products.find(p => String(p.id) === productFilter)?.name || 'Product' : 'All_Products';
            a.download = `ProductInventory_${productLabel}_${fromDate}_${toDate}.${ext}`;
            document.body.appendChild(a);
            a.click();
            a.remove();
            window.URL.revokeObjectURL(url);
        } catch (err) {
            console.error("Failed to download report", err);
            setApiError("Error downloading report");
        }
        setIsDownloading(false);
    };

    const resetForm = () => {
        setEditingId(null);
        setProductId("");
        setProductSearch("");
        setOpenStock("");
        setIncomeStock("");
        setSalesInput("");
        setRateInput("");
    };

    const getUnit = () => {
        const p = products.find(p => p.id === Number(productId));
        return p?.unit || "Units";
    };

    return (
        <div className="p-6 h-screen overflow-hidden bg-background transition-colors duration-300">
            <div className="max-w-7xl mx-auto">
                <div className="flex justify-between items-center mb-8">
                    <div>
                        <h1 className="text-4xl font-bold text-foreground tracking-tight">
                            Product <span className="text-gradient">Stock</span>
                        </h1>
                        <p className="text-muted-foreground mt-2">
                            Monitor fuel, lubricants, accessories, and all product inventory levels.
                        </p>
                    </div>
                    <PermissionGate permission="INVENTORY_CREATE">
                        <button
                            onClick={() => { resetForm(); clearAllErrors(); setApiError(""); setIsModalOpen(true); }}
                            className="btn-gradient px-6 py-3 rounded-xl font-medium flex items-center gap-2 shadow-lg hover:shadow-xl transition-all"
                        >
                            <Plus className="w-5 h-5" />
                            Record Inventory
                        </button>
                    </PermissionGate>
                </div>

                {/* Filter Bar */}
                <div className="mb-6 flex flex-wrap gap-3 items-center">
                    <div className="relative flex-1 min-w-[200px] max-w-md">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                        <input
                            type="text"
                            placeholder="Search by product name, category, brand..."
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            className="w-full pl-10 pr-4 py-2.5 bg-card border border-border rounded-xl text-foreground text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                        />
                    </div>
                    <StyledSelect
                        value={productFilter}
                        onChange={(val) => setProductFilter(val)}
                        options={[
                            { value: "", label: "All Products" },
                            ...products.map((p) => ({ value: String(p.id), label: p.name })),
                        ]}
                        placeholder="All Products"
                        className="min-w-[160px]"
                    />
                    <div className="flex items-center gap-2">
                        <label className="text-xs text-muted-foreground font-medium">From</label>
                        <input
                            type="date"
                            value={fromDate}
                            onChange={(e) => setFromDate(e.target.value)}
                            className="px-3 py-2.5 bg-card border border-border rounded-xl text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
                        />
                        <label className="text-xs text-muted-foreground font-medium">To</label>
                        <input
                            type="date"
                            value={toDate}
                            onChange={(e) => setToDate(e.target.value)}
                            className="px-3 py-2.5 bg-card border border-border rounded-xl text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
                        />
                    </div>
                    {(searchQuery || productFilter || fromDate !== defaultFrom || toDate !== defaultTo) && (
                        <button
                            onClick={() => { setSearchQuery(""); setProductFilter(""); setFromDate(defaultFrom); setToDate(defaultTo); }}
                            className="px-3 py-2.5 text-xs text-muted-foreground hover:text-foreground"
                        >
                            Reset
                        </button>
                    )}

                    {/* Download Buttons */}
                    <div className="flex items-center gap-1 ml-auto">
                        <button
                            onClick={() => handleDownload('pdf')}
                            disabled={isDownloading || !fromDate || !toDate}
                            className="flex items-center gap-1.5 px-3 py-2.5 bg-red-500/10 hover:bg-red-500/20 text-red-500 rounded-xl text-xs font-medium transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                            title="Download PDF Report"
                        >
                            <FileText className="w-3.5 h-3.5" />
                            PDF
                        </button>
                        <button
                            onClick={() => handleDownload('excel')}
                            disabled={isDownloading || !fromDate || !toDate}
                            className="flex items-center gap-1.5 px-3 py-2.5 bg-green-500/10 hover:bg-green-500/20 text-green-500 rounded-xl text-xs font-medium transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                            title="Download Excel Report"
                        >
                            <FileSpreadsheet className="w-3.5 h-3.5" />
                            Excel
                        </button>
                    </div>
                </div>

                {isLoading ? (
                    <div className="flex flex-col items-center justify-center py-20 text-muted-foreground">
                        <div className="w-12 h-12 border-4 border-primary/20 border-t-primary rounded-full animate-spin mb-4"></div>
                        <p className="animate-pulse">Loading product checks...</p>
                    </div>
                ) : filteredInv.length === 0 ? (
                    <div className="text-center py-20 bg-black/5 dark:bg-white/5 rounded-2xl border border-dashed border-border">
                        <Archive className="w-16 h-16 mx-auto text-muted-foreground mb-4 opacity-50" />
                        <h3 className="text-xl font-semibold text-foreground mb-2">No Records Found</h3>
                        <p className="text-muted-foreground mb-6">
                            {inventories.length === 0
                                ? "Track your daily shop sales and stock levels here."
                                : "No records match the selected filters."}
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
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Product Details</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right w-24">Opening</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right w-24">Arrivals (+)</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right w-24">Closing</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right w-24 italic">Units Sold</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right w-20">Rate</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right w-24">Amount</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center w-24">Actions</th>
                                    </tr>
                                </thead>
                                <tbody className="divide-y divide-border/30">
                                    {pagedInv.map((inv, idx) => (
                                        <tr key={inv.id} className="hover:bg-white/5 transition-colors group">
                                            <td className="px-6 py-4 text-xs font-mono text-muted-foreground text-center">{page * pageSize + idx + 1}</td>
                                            <td className="px-6 py-4">
                                                <div className="text-sm font-medium text-foreground">{new Date(inv.date).toLocaleDateString('en-GB')}</div>
                                            </td>
                                            <td className="px-6 py-4">
                                                <div className="flex items-center gap-3">
                                                    <div className="p-2 rounded-lg bg-primary/10 text-primary">
                                                        <Box className="w-4 h-4" />
                                                    </div>
                                                    <div>
                                                        <div className="text-sm font-bold text-foreground">{inv.product?.name}</div>
                                                        <div className="text-[10px] text-muted-foreground uppercase flex items-center gap-1">
                                                            <span>{inv.product?.category}</span>
                                                            {inv.product?.brand && (
                                                                <>
                                                                    <span>•</span>
                                                                    <span>{inv.product.brand}</span>
                                                                </>
                                                            )}
                                                        </div>
                                                    </div>
                                                </div>
                                            </td>
                                            <td className="px-6 py-4 text-right">
                                                <div className="text-sm font-mono">{inv.openStock?.toLocaleString()}</div>
                                                <div className="text-[9px] text-muted-foreground uppercase">{inv.product?.unit}</div>
                                            </td>
                                            <td className="px-6 py-4 text-right text-blue-500 font-medium font-mono text-sm leading-none">
                                                {inv.incomeStock && inv.incomeStock > 0 ? `+${inv.incomeStock.toLocaleString()}` : "-"}
                                            </td>
                                            <td className="px-6 py-4 text-right">
                                                <div className="text-sm font-mono">{inv.closeStock?.toLocaleString()}</div>
                                                <div className="text-[9px] text-muted-foreground uppercase">{inv.product?.unit}</div>
                                            </td>
                                            <td className="px-6 py-4 text-right font-black text-primary text-base font-mono bg-primary/5">
                                                {inv.sales?.toLocaleString()}
                                            </td>
                                            <td className="px-6 py-4 text-right">
                                                <div className="text-sm font-mono text-muted-foreground">{inv.rate != null ? `₹${Number(inv.rate).toLocaleString('en-IN', { minimumFractionDigits: 2 })}` : "-"}</div>
                                            </td>
                                            <td className="px-6 py-4 text-right">
                                                <div className="text-sm font-mono font-semibold">{inv.amount != null ? `₹${Number(inv.amount).toLocaleString('en-IN', { minimumFractionDigits: 2 })}` : "-"}</div>
                                            </td>
                                            <td className="px-6 py-4">
                                                <div className="flex justify-center gap-2">
                                                    <PermissionGate permission="INVENTORY_UPDATE">
                                                        <button
                                                            onClick={() => handleEdit(inv)}
                                                            className="p-1.5 rounded-lg hover:bg-white/10 text-muted-foreground hover:text-foreground transition-colors"
                                                            title="Edit"
                                                        >
                                                            <Edit2 className="w-4 h-4" />
                                                        </button>
                                                        <button
                                                            onClick={() => handleDelete(inv.id)}
                                                            className="p-1.5 rounded-lg hover:bg-red-500/10 text-muted-foreground hover:text-red-500 transition-colors"
                                                            title="Delete"
                                                        >
                                                            <Trash2 className="w-4 h-4" />
                                                        </button>
                                                    </PermissionGate>
                                                </div>
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                        <TablePagination page={page} totalPages={totalPages} totalElements={totalElements} pageSize={pageSize} onPageChange={setPage} />
                    </GlassCard>
                )}
            </div>

            <Modal
                isOpen={isModalOpen}
                onClose={() => { setIsModalOpen(false); resetForm(); }}
                title={editingId ? "Edit Inventory Check" : "Daily Inventory Check"}
            >
                <form onSubmit={handleSave} className="space-y-4">
                    <FormErrorBanner message={apiError} onDismiss={() => setApiError("")} />
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <div className="md:col-span-2">
                            <label className="block text-sm font-medium text-foreground mb-1.5 flex items-center gap-2">
                                <Calendar className="w-4 h-4 text-primary" /> Date
                            </label>
                            <input
                                type="date"
                                required
                                value={date}
                                onChange={(e) => setDate(e.target.value)}
                                className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground"
                            />
                        </div>

                        <div className="md:col-span-2 relative z-10">
                            <label className="block text-sm font-medium text-foreground mb-1.5">Product</label>
                            <div className="relative">
                                <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground z-10" />
                                <input
                                    type="text"
                                    placeholder="Search product..."
                                    value={productSearch}
                                    onChange={(e) => {
                                        setProductSearch(e.target.value);
                                        if (!e.target.value) { setProductId(""); setRateInput(""); }
                                    }}
                                    className={`w-full pl-10 pr-4 py-3 bg-background border border-border rounded-xl text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/50 ${inputErrorClass(errors.productId)}`}
                                />
                            </div>
                            {productSearch && !productId && (
                                <div className="absolute left-0 right-0 mt-1 bg-card border border-border rounded-lg shadow-xl z-50 max-h-60 overflow-y-auto">
                                    {products
                                        .filter(p => p.name.toLowerCase().includes(productSearch.toLowerCase()))
                                        .map(p => (
                                            <button
                                                key={p.id}
                                                type="button"
                                                className="w-full px-4 py-2.5 text-left hover:bg-primary/10 text-foreground transition-colors flex items-center justify-between text-sm"
                                                onClick={() => {
                                                    setProductId(String(p.id));
                                                    setProductSearch(p.name);
                                                    setRateInput(String(p.price || ""));
                                                    clearError("productId");
                                                }}
                                            >
                                                <span className="font-medium">{p.name}</span>
                                                <span className="text-xs text-muted-foreground">{p.unit} {p.price ? `| ₹${p.price}` : ""}</span>
                                            </button>
                                        ))}
                                </div>
                            )}
                            <FieldError error={errors.productId} />
                        </div>

                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5">Opening Stock</label>
                            <input
                                type="number"
                                value={openStock}
                                onChange={(e) => { setOpenStock(e.target.value); clearError("openStock"); }}
                                className={`w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground ${inputErrorClass(errors.openStock)}`}
                                placeholder="0"
                            />
                            <FieldError error={errors.openStock} />
                        </div>

                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5">New Arrivals (+)</label>
                            <input
                                type="number"
                                value={incomeStock}
                                onChange={(e) => setIncomeStock(e.target.value)}
                                className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground border-blue-200"
                                placeholder="0"
                            />
                        </div>

                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5 font-bold">Units Sold</label>
                            <input
                                type="number"
                                value={salesInput}
                                onChange={(e) => setSalesInput(e.target.value)}
                                className="w-full bg-primary/5 border-primary/30 border rounded-xl px-4 py-3 text-foreground text-center text-xl font-bold"
                                placeholder="Enter sales"
                            />
                        </div>

                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5">Rate (₹)</label>
                            <input
                                type="number"
                                step="0.01"
                                value={rateInput}
                                onChange={(e) => setRateInput(e.target.value)}
                                className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground"
                                placeholder="0.00"
                            />
                        </div>
                    </div>

                    <div className="bg-primary/10 border border-primary/20 rounded-2xl p-6 mt-4">
                        <div className="flex justify-between items-center text-center">
                            <div className="flex-1">
                                <p className="text-[10px] text-muted-foreground uppercase font-bold">Book Stock</p>
                                <p className="text-xl font-bold">{totalStock}</p>
                            </div>
                            <div className="w-px h-8 bg-border"></div>
                            <div className="flex-1">
                                <p className="text-[10px] text-primary uppercase font-bold tracking-widest">Closing Stock</p>
                                <p className="text-2xl font-black text-primary">{closeStock} <span className="text-sm">{getUnit()}</span></p>
                            </div>
                            <div className="w-px h-8 bg-border"></div>
                            <div className="flex-1">
                                <p className="text-[10px] text-amber-500 uppercase font-bold tracking-widest">Amount</p>
                                <p className="text-2xl font-black text-amber-500">₹{amount.toLocaleString("en-IN", { minimumFractionDigits: 2 })}</p>
                            </div>
                        </div>
                    </div>

                    <div className="flex justify-end gap-3 pt-6 border-t border-border mt-6">
                        <button
                            type="button"
                            onClick={() => setIsModalOpen(false)}
                            className="px-6 py-2.5 rounded-xl font-medium text-foreground bg-black/5 dark:bg-white/5 hover:bg-black/10 transition-colors"
                        >
                            Cancel
                        </button>
                        <button
                            type="submit"
                            className="btn-gradient px-8 py-2.5 rounded-xl font-medium shadow-lg hover:shadow-xl transition-all"
                        >
                            {editingId ? "Update Check" : "Log Inventory Check"}
                        </button>
                    </div>
                </form>
            </Modal>
        </div>
    );
}
