"use client";

import { useEffect, useState, useMemo } from "react";
import { TablePagination, useClientPagination } from "@/components/ui/table-pagination";
import { GlassCard } from "@/components/ui/glass-card";
import { Modal } from "@/components/ui/modal";
import {
    getCashierStocks,
    getActiveProducts,
    createCashierStock,
    updateCashierStock,
    deleteCashierStock,
    CashierStock,
    Product,
} from "@/lib/api/station";
import { ShoppingBag, Plus, Search, Edit2, Trash2 } from "lucide-react";
import { useFormValidation, required, min } from "@/lib/validation";
import { FieldError, inputErrorClass, FormErrorBanner } from "@/components/ui/field-error";
import { PermissionGate } from "@/components/permission-gate";

export default function CashierStockPage() {
    const [stocks, setStocks] = useState<CashierStock[]>([]);
    const [products, setProducts] = useState<Product[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [editingId, setEditingId] = useState<number | null>(null);
    const [searchQuery, setSearchQuery] = useState("");

    // Form state
    const [productId, setProductId] = useState("");
    const [currentStock, setCurrentStock] = useState("");
    const [maxCapacity, setMaxCapacity] = useState("");
    const [apiError, setApiError] = useState("");
    const validationRules = useMemo(() => ({
        productId: [required("Product is required")],
        currentStock: [required("Current stock is required"), min(0)],
    }), []);
    const { errors, validate, clearError, clearAllErrors } = useFormValidation(validationRules);

    const loadData = async () => {
        setIsLoading(true);
        try {
            const [stockData, productData] = await Promise.all([
                getCashierStocks(),
                getActiveProducts(),
            ]);
            setStocks(stockData);
            setProducts(productData.filter((p) => p.category !== "FUEL"));
        } catch (err) {
            console.error("Failed to load cashier stock data", err);
        }
        setIsLoading(false);
    };

    useEffect(() => {
        loadData();
    }, []);

    const handleEdit = (stock: CashierStock) => {
        clearAllErrors();
        setApiError("");
        setEditingId(stock.id);
        setProductId(String(stock.product.id));
        setCurrentStock(String(stock.currentStock));
        setMaxCapacity(String(stock.maxCapacity));
        setIsModalOpen(true);
    };

    const handleSave = async (e: React.FormEvent) => {
        e.preventDefault();
        setApiError("");
        if (!validate({ productId, currentStock })) return;
        try {
            const payload = {
                product: { id: Number(productId) },
                currentStock: Number(currentStock),
                maxCapacity: Number(maxCapacity),
            };
            if (editingId) {
                await updateCashierStock(editingId, payload as any);
            } else {
                await createCashierStock(payload as any);
            }
            setIsModalOpen(false);
            resetForm();
            loadData();
        } catch (err) {
            console.error("Failed to save cashier stock", err);
            setApiError("Error saving cashier stock");
        }
    };

    const handleDelete = async (id: number) => {
        if (!confirm("Are you sure you want to delete this cashier stock record?")) return;
        try {
            await deleteCashierStock(id);
            loadData();
        } catch (err) {
            console.error("Failed to delete", err);
            setApiError("Error deleting record");
        }
    };

    const resetForm = () => {
        setEditingId(null);
        setProductId("");
        setCurrentStock("");
        setMaxCapacity("");
    };

    const getFillPercent = (stock: CashierStock) => {
        if (!stock.maxCapacity || stock.maxCapacity === 0) return 0;
        return Math.round((stock.currentStock / stock.maxCapacity) * 100);
    };

    const getFillColor = (percent: number) => {
        if (percent > 50) return "bg-green-500";
        if (percent > 25) return "bg-yellow-500";
        return "bg-red-500";
    };

    const filtered = stocks.filter((s) => {
        const q = searchQuery.toLowerCase();
        return (
            !searchQuery ||
            s.product.name?.toLowerCase().includes(q) ||
            s.product.brand?.toLowerCase().includes(q) ||
            s.product.category?.toLowerCase().includes(q)
        );
    });

    const { page, setPage, totalPages, totalElements, pageSize, paginatedData } = useClientPagination(filtered);

    return (
        <div className="p-6 h-screen overflow-hidden bg-background transition-colors duration-300">
            <div className="max-w-7xl mx-auto">
                <div className="flex justify-between items-center mb-8">
                    <div>
                        <h1 className="text-4xl font-bold text-foreground tracking-tight">
                            Cashier <span className="text-gradient">Stock</span>
                        </h1>
                        <p className="text-muted-foreground mt-2">
                            Showcase/display inventory available for direct sale.
                        </p>
                    </div>
                    <PermissionGate permission="INVENTORY_MANAGE">
                        <button
                            onClick={() => { resetForm(); clearAllErrors(); setApiError(""); setIsModalOpen(true); }}
                            className="btn-gradient px-6 py-3 rounded-xl font-medium flex items-center gap-2 shadow-lg hover:shadow-xl transition-all"
                        >
                            <Plus className="w-5 h-5" />
                            Add Stock
                        </button>
                    </PermissionGate>
                </div>

                {isLoading ? (
                    <div className="flex flex-col items-center justify-center py-20 text-muted-foreground">
                        <div className="w-12 h-12 border-4 border-primary/20 border-t-primary rounded-full animate-spin mb-4"></div>
                        <p className="animate-pulse">Loading cashier stock...</p>
                    </div>
                ) : stocks.length === 0 ? (
                    <div className="text-center py-20 bg-black/5 dark:bg-white/5 rounded-2xl border border-dashed border-border">
                        <ShoppingBag className="w-16 h-16 mx-auto text-muted-foreground mb-4 opacity-50" />
                        <h3 className="text-xl font-semibold text-foreground mb-2">No Cashier Stock</h3>
                        <p className="text-muted-foreground mb-6">Add products to your cashier display inventory to get started.</p>
                    </div>
                ) : (
                    <>
                        <div className="mb-6 flex flex-wrap gap-3 items-center">
                            <div className="relative flex-1 min-w-[200px] max-w-md">
                                <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                                <input
                                    type="text"
                                    placeholder="Search by product name, brand..."
                                    value={searchQuery}
                                    onChange={(e) => setSearchQuery(e.target.value)}
                                    className="w-full pl-10 pr-4 py-2.5 bg-card border border-border rounded-xl text-foreground text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                                />
                            </div>
                            {searchQuery && (
                                <button onClick={() => setSearchQuery("")} className="px-3 py-2.5 text-xs text-muted-foreground hover:text-foreground">
                                    Clear
                                </button>
                            )}
                        </div>

                        <GlassCard className="overflow-hidden border-none p-0">
                            <div className="overflow-x-auto">
                                <table className="w-full text-left border-collapse">
                                    <thead>
                                        <tr className="bg-white/5 border-b border-border/50">
                                            <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center w-16">#</th>
                                            <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Product</th>
                                            <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right">Current Stock</th>
                                            <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right">Max Capacity</th>
                                            <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground w-48">Fill Level</th>
                                            <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center w-24">Actions</th>
                                        </tr>
                                    </thead>
                                    <tbody className="divide-y divide-border/30">
                                        {paginatedData.map((stock, idx) => {
                                            const fillPct = getFillPercent(stock);
                                            return (
                                                <tr key={stock.id} className="hover:bg-white/5 transition-colors">
                                                    <td className="px-6 py-4 text-xs font-mono text-muted-foreground text-center">{page * pageSize + idx + 1}</td>
                                                    <td className="px-6 py-4">
                                                        <div className="flex items-center gap-3">
                                                            <div className="p-2 rounded-lg bg-primary/10 text-primary">
                                                                <ShoppingBag className="w-4 h-4" />
                                                            </div>
                                                            <div>
                                                                <div className="text-sm font-bold text-foreground">{stock.product.name}</div>
                                                                <div className="text-[10px] text-muted-foreground uppercase flex items-center gap-1">
                                                                    <span>{stock.product.category}</span>
                                                                    {stock.product.brand && (
                                                                        <>
                                                                            <span>•</span>
                                                                            <span>{stock.product.brand}</span>
                                                                        </>
                                                                    )}
                                                                </div>
                                                            </div>
                                                        </div>
                                                    </td>
                                                    <td className="px-6 py-4 text-right font-bold font-mono text-base text-foreground">
                                                        {stock.currentStock}
                                                        <div className="text-[9px] text-muted-foreground uppercase font-normal">{stock.product.unit}</div>
                                                    </td>
                                                    <td className="px-6 py-4 text-right text-sm font-mono text-muted-foreground">{stock.maxCapacity}</td>
                                                    <td className="px-6 py-4">
                                                        <div className="flex items-center gap-3">
                                                            <div className="flex-1 h-2.5 bg-black/10 dark:bg-white/10 rounded-full overflow-hidden">
                                                                <div
                                                                    className={`h-full rounded-full transition-all ${getFillColor(fillPct)}`}
                                                                    style={{ width: `${Math.min(fillPct, 100)}%` }}
                                                                />
                                                            </div>
                                                            <span className="text-xs font-mono font-bold text-muted-foreground w-10 text-right">{fillPct}%</span>
                                                        </div>
                                                    </td>
                                                    <td className="px-6 py-4">
                                                        <div className="flex justify-center gap-2">
                                                            <PermissionGate permission="INVENTORY_MANAGE">
                                                                <button onClick={() => handleEdit(stock)} className="p-1.5 rounded-lg hover:bg-white/10 text-muted-foreground hover:text-foreground transition-colors" title="Edit">
                                                                    <Edit2 className="w-4 h-4" />
                                                                </button>
                                                                <button onClick={() => handleDelete(stock.id)} className="p-1.5 rounded-lg hover:bg-red-500/10 text-muted-foreground hover:text-red-500 transition-colors" title="Delete">
                                                                    <Trash2 className="w-4 h-4" />
                                                                </button>
                                                            </PermissionGate>
                                                        </div>
                                                    </td>
                                                </tr>
                                            );
                                        })}
                                    </tbody>
                                </table>
                            </div>
                            <TablePagination page={page} totalPages={totalPages} totalElements={totalElements} pageSize={pageSize} onPageChange={setPage} />
                        </GlassCard>
                    </>
                )}
            </div>

            <Modal isOpen={isModalOpen} onClose={() => { setIsModalOpen(false); resetForm(); }} title={editingId ? "Edit Cashier Stock" : "Add Cashier Stock"}>
                <form onSubmit={handleSave} className="space-y-4">
                    <FormErrorBanner message={apiError} />
                    <div>
                        <label className="block text-sm font-medium text-foreground mb-1.5">Product</label>
                        <select
                            value={productId}
                            onChange={(e) => { setProductId(e.target.value); clearError("productId"); }}
                            disabled={!!editingId}
                            className={`w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground disabled:opacity-50 ${inputErrorClass(errors.productId)}`}
                        >
                            <option value="">Select a product...</option>
                            {products.map((p) => (
                                <option key={p.id} value={p.id}>{p.name} ({p.unit})</option>
                            ))}
                        </select>
                        <FieldError error={errors.productId} />
                    </div>
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5">Current Stock</label>
                            <input type="number" value={currentStock} onChange={(e) => { setCurrentStock(e.target.value); clearError("currentStock"); }} className={`w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground ${inputErrorClass(errors.currentStock)}`} placeholder="0" min="0" step="any" />
                            <FieldError error={errors.currentStock} />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5">Max Capacity</label>
                            <input type="number" value={maxCapacity} onChange={(e) => setMaxCapacity(e.target.value)} className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground" placeholder="0" min="0" step="any" />
                        </div>
                    </div>
                    <div className="flex justify-end gap-3 pt-6 border-t border-border mt-6">
                        <button type="button" onClick={() => setIsModalOpen(false)} className="px-6 py-2.5 rounded-xl font-medium text-foreground bg-black/5 dark:bg-white/5 hover:bg-black/10 transition-colors">
                            Cancel
                        </button>
                        <button type="submit" className="btn-gradient px-8 py-2.5 rounded-xl font-medium shadow-lg hover:shadow-xl transition-all">
                            {editingId ? "Update" : "Add Stock"}
                        </button>
                    </div>
                </form>
            </Modal>
        </div>
    );
}
