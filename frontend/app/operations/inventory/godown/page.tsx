"use client";

import { useEffect, useState, useMemo } from "react";
import { TablePagination, useClientPagination } from "@/components/ui/table-pagination";
import { GlassCard } from "@/components/ui/glass-card";
import { Modal } from "@/components/ui/modal";
import { ProductAutocomplete } from "@/components/ui/product-autocomplete";
import {
    getGodownStocks,
    getActiveProducts,
    createGodownStock,
    updateGodownStock,
    deleteGodownStock,
    GodownStock,
    Product,
} from "@/lib/api/station";
import { Warehouse, Plus, Search, Edit2, Trash2, AlertTriangle } from "lucide-react";
import { useFormValidation, required, min } from "@/lib/validation";
import { FieldError, inputErrorClass, FormErrorBanner } from "@/components/ui/field-error";
import { PermissionGate } from "@/components/permission-gate";

export default function GodownStockPage() {
    const [stocks, setStocks] = useState<GodownStock[]>([]);
    const [products, setProducts] = useState<Product[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [editingId, setEditingId] = useState<number | null>(null);
    const [searchQuery, setSearchQuery] = useState("");

    // Form state
    const [productId, setProductId] = useState("");
    const [currentStock, setCurrentStock] = useState("");
    const [reorderLevel, setReorderLevel] = useState("");
    const [maxStock, setMaxStock] = useState("");
    const [location, setLocation] = useState("");
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
                getGodownStocks(),
                getActiveProducts(),
            ]);
            setStocks(stockData);
            setProducts(productData.filter((p) => p.category !== "FUEL"));
        } catch (err) {
            console.error("Failed to load godown data", err);
        }
        setIsLoading(false);
    };

    useEffect(() => {
        loadData();
    }, []);

    const handleEdit = (stock: GodownStock) => {
        clearAllErrors();
        setApiError("");
        setEditingId(stock.id);
        setProductId(String(stock.product.id));
        setCurrentStock(String(stock.currentStock));
        setReorderLevel(String(stock.reorderLevel));
        setMaxStock(String(stock.maxStock));
        setLocation(stock.location || "");
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
                reorderLevel: Number(reorderLevel),
                maxStock: Number(maxStock),
                location: location || null,
            };
            if (editingId) {
                await updateGodownStock(editingId, payload as any);
            } else {
                await createGodownStock(payload as any);
            }
            setIsModalOpen(false);
            resetForm();
            loadData();
        } catch (err) {
            console.error("Failed to save godown stock", err);
            setApiError("Error saving godown stock");
        }
    };

    const handleDelete = async (id: number) => {
        if (!confirm("Are you sure you want to delete this godown stock record?")) return;
        try {
            await deleteGodownStock(id);
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
        setReorderLevel("");
        setMaxStock("");
        setLocation("");
    };

    const isLowStock = (stock: GodownStock) =>
        stock.reorderLevel > 0 && stock.currentStock <= stock.reorderLevel;

    const filtered = stocks.filter((s) => {
        const q = searchQuery.toLowerCase();
        return (
            !searchQuery ||
            s.product.name?.toLowerCase().includes(q) ||
            s.product.brand?.toLowerCase().includes(q) ||
            s.product.category?.toLowerCase().includes(q) ||
            s.location?.toLowerCase().includes(q)
        );
    });

    const { page, setPage, totalPages, totalElements, pageSize, paginatedData } = useClientPagination(filtered);

    const lowStockCount = stocks.filter(isLowStock).length;

    return (
        <div className="p-6 h-screen overflow-hidden bg-background transition-colors duration-300">
            <div className="max-w-7xl mx-auto">
                <div className="flex justify-between items-center mb-8">
                    <div>
                        <h1 className="text-4xl font-bold text-foreground tracking-tight">
                            Godown <span className="text-gradient">Stock</span>
                        </h1>
                        <p className="text-muted-foreground mt-2">
                            Warehouse inventory for non-fuel products (lubricants, accessories).
                        </p>
                    </div>
                    <div className="flex items-center gap-3">
                        {lowStockCount > 0 && (
                            <div className="flex items-center gap-2 px-4 py-2 bg-red-500/10 border border-red-500/20 rounded-xl text-red-500 text-sm font-medium">
                                <AlertTriangle className="w-4 h-4" />
                                {lowStockCount} Low Stock
                            </div>
                        )}
                        <PermissionGate permission="INVENTORY_CREATE">
                            <button
                                onClick={() => { resetForm(); clearAllErrors(); setApiError(""); setIsModalOpen(true); }}
                                className="btn-gradient px-6 py-3 rounded-xl font-medium flex items-center gap-2 shadow-lg hover:shadow-xl transition-all"
                            >
                                <Plus className="w-5 h-5" />
                                Add Stock
                            </button>
                        </PermissionGate>
                    </div>
                </div>

                {isLoading ? (
                    <div className="flex flex-col items-center justify-center py-20 text-muted-foreground">
                        <div className="w-12 h-12 border-4 border-primary/20 border-t-primary rounded-full animate-spin mb-4"></div>
                        <p className="animate-pulse">Loading godown stock...</p>
                    </div>
                ) : stocks.length === 0 ? (
                    <div className="text-center py-20 bg-black/5 dark:bg-white/5 rounded-2xl border border-dashed border-border">
                        <Warehouse className="w-16 h-16 mx-auto text-muted-foreground mb-4 opacity-50" />
                        <h3 className="text-xl font-semibold text-foreground mb-2">No Godown Stock</h3>
                        <p className="text-muted-foreground mb-6">Add products to your warehouse inventory to get started.</p>
                    </div>
                ) : (
                    <>
                        <div className="mb-6 flex flex-wrap gap-3 items-center">
                            <div className="relative flex-1 min-w-[200px] max-w-md">
                                <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                                <input
                                    type="text"
                                    placeholder="Search by product name, brand, location..."
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
                                            <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right">Reorder Level</th>
                                            <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right">Max Stock</th>
                                            <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Location</th>
                                            <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Last Restock</th>
                                            <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center w-24">Actions</th>
                                        </tr>
                                    </thead>
                                    <tbody className="divide-y divide-border/30">
                                        {paginatedData.map((stock, idx) => (
                                            <tr key={stock.id} className={`hover:bg-white/5 transition-colors ${isLowStock(stock) ? 'bg-red-500/5' : ''}`}>
                                                <td className="px-6 py-4 text-xs font-mono text-muted-foreground text-center">{page * pageSize + idx + 1}</td>
                                                <td className="px-6 py-4">
                                                    <div className="flex items-center gap-3">
                                                        <div className="p-2 rounded-lg bg-primary/10 text-primary">
                                                            <Warehouse className="w-4 h-4" />
                                                        </div>
                                                        <div>
                                                            <div className="text-sm font-bold text-foreground flex items-center gap-2">
                                                                {stock.product.name}
                                                                {isLowStock(stock) && (
                                                                    <span className="px-1.5 py-0.5 text-[9px] font-bold bg-red-500 text-white rounded-full uppercase">Low</span>
                                                                )}
                                                            </div>
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
                                                <td className={`px-6 py-4 text-right font-bold font-mono text-base ${isLowStock(stock) ? 'text-red-500' : 'text-foreground'}`}>
                                                    {stock.currentStock}
                                                    <div className="text-[9px] text-muted-foreground uppercase font-normal">{stock.product.unit}</div>
                                                </td>
                                                <td className="px-6 py-4 text-right text-sm font-mono text-muted-foreground">{stock.reorderLevel}</td>
                                                <td className="px-6 py-4 text-right text-sm font-mono text-muted-foreground">{stock.maxStock}</td>
                                                <td className="px-6 py-4 text-sm text-muted-foreground">{stock.location || '-'}</td>
                                                <td className="px-6 py-4 text-sm text-muted-foreground">
                                                    {stock.lastRestockDate ? new Date(stock.lastRestockDate).toLocaleDateString() : '-'}
                                                </td>
                                                <td className="px-6 py-4">
                                                    <div className="flex justify-center gap-2">
                                                        <PermissionGate permission="INVENTORY_UPDATE">
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
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                            <TablePagination page={page} totalPages={totalPages} totalElements={totalElements} pageSize={pageSize} onPageChange={setPage} />
                        </GlassCard>
                    </>
                )}
            </div>

            <Modal isOpen={isModalOpen} onClose={() => { setIsModalOpen(false); resetForm(); }} title={editingId ? "Edit Godown Stock" : "Add Godown Stock"}>
                <form onSubmit={handleSave} className="space-y-4">
                    <FormErrorBanner message={apiError} />
                    <div>
                        <label className="block text-sm font-medium text-foreground mb-1.5">Product</label>
                        <ProductAutocomplete
                            value={productId}
                            onChange={(val) => { setProductId(String(val)); clearError("productId"); }}
                            products={products}
                            placeholder="Search product..."
                            disabled={!!editingId}
                            className={`w-full ${!!editingId ? "opacity-50 pointer-events-none" : ""} ${inputErrorClass(errors.productId)}`}
                        />
                        <FieldError error={errors.productId} />
                    </div>
                    <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5">Current Stock</label>
                            <input type="number" value={currentStock} onChange={(e) => { setCurrentStock(e.target.value); clearError("currentStock"); }} className={`w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground ${inputErrorClass(errors.currentStock)}`} placeholder="0" min="0" step="any" />
                            <FieldError error={errors.currentStock} />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5">Reorder Level</label>
                            <input type="number" value={reorderLevel} onChange={(e) => setReorderLevel(e.target.value)} className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground" placeholder="0" min="0" step="any" />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5">Max Stock</label>
                            <input type="number" value={maxStock} onChange={(e) => setMaxStock(e.target.value)} className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground" placeholder="0" min="0" step="any" />
                        </div>
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-foreground mb-1.5">Location</label>
                        <input type="text" value={location} onChange={(e) => setLocation(e.target.value)} className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground" placeholder="e.g., Shelf A, Rack 2" />
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
