"use client";

import { useEffect, useState, useMemo } from "react";
import { TablePagination, useClientPagination } from "@/components/ui/table-pagination";
import { GlassCard } from "@/components/ui/glass-card";
import { Modal } from "@/components/ui/modal";
import {
    getStockTransfers,
    getActiveProducts,
    getGodownStocks,
    getCashierStocks,
    createStockTransfer,
    updateStockTransfer,
    deleteStockTransfer,
    StockTransfer,
    Product,
    GodownStock,
    CashierStock,
} from "@/lib/api/station";
import { ArrowLeftRight, Plus, Search, Warehouse, ShoppingBag, ArrowRight, Trash2, Eye, Edit3 } from "lucide-react";
import { useFormValidation, required, min } from "@/lib/validation";
import { FieldError, inputErrorClass, FormErrorBanner } from "@/components/ui/field-error";
import { PermissionGate } from "@/components/permission-gate";

export default function StockTransferPage() {
    const [transfers, setTransfers] = useState<StockTransfer[]>([]);
    const [products, setProducts] = useState<Product[]>([]);
    const [godownStocks, setGodownStocks] = useState<GodownStock[]>([]);
    const [cashierStocks, setCashierStocks] = useState<CashierStock[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [searchQuery, setSearchQuery] = useState("");
    const [editingId, setEditingId] = useState<number | null>(null);
    const [isViewModalOpen, setIsViewModalOpen] = useState(false);
    const [viewTransfer, setViewTransfer] = useState<StockTransfer | null>(null);

    // Form state
    const [productId, setProductId] = useState("");
    const [quantity, setQuantity] = useState("");
    const [direction, setDirection] = useState<"GODOWN_TO_CASHIER" | "CASHIER_TO_GODOWN">("GODOWN_TO_CASHIER");
    const [remarks, setRemarks] = useState("");
    const [transferredBy, setTransferredBy] = useState("");
    const [apiError, setApiError] = useState("");
    const validationRules = useMemo(() => ({
        productId: [required("Product is required")],
        quantity: [required("Quantity is required"), min(0.01, "Quantity must be greater than 0")],
    }), []);
    const { errors, validate, clearError, clearAllErrors } = useFormValidation(validationRules);

    const loadData = async () => {
        setIsLoading(true);
        try {
            const [transferData, productData, godownData, cashierData] = await Promise.all([
                getStockTransfers(),
                getActiveProducts(),
                getGodownStocks(),
                getCashierStocks(),
            ]);
            setTransfers(transferData);
            setProducts(productData.filter((p) => p.category !== "FUEL"));
            setGodownStocks(godownData);
            setCashierStocks(cashierData);
        } catch (err) {
            console.error("Failed to load transfer data", err);
        }
        setIsLoading(false);
    };

    useEffect(() => {
        loadData();
    }, []);

    const getAvailableStock = () => {
        if (!productId) return 0;
        const pid = Number(productId);
        if (direction === "GODOWN_TO_CASHIER") {
            const gs = godownStocks.find((s) => s.product.id === pid);
            return gs?.currentStock || 0;
        } else {
            const cs = cashierStocks.find((s) => s.product.id === pid);
            return cs?.currentStock || 0;
        }
    };

    const handleSave = async (e: React.FormEvent) => {
        e.preventDefault();
        setApiError("");
        if (!validate({ productId, quantity })) return;
        const available = getAvailableStock();
        if (Number(quantity) > available) {
            setApiError(`Insufficient stock. Available: ${available}`);
            return;
        }
        try {
            const payload = {
                product: { id: Number(productId) },
                quantity: Number(quantity),
                fromLocation: direction === "GODOWN_TO_CASHIER" ? "GODOWN" : "CASHIER",
                toLocation: direction === "GODOWN_TO_CASHIER" ? "CASHIER" : "GODOWN",
                transferDate: new Date().toISOString(),
                remarks: remarks || null,
                transferredBy: transferredBy || null,
            };
            if (editingId) {
                await updateStockTransfer(editingId, payload as any);
            } else {
                await createStockTransfer(payload as any);
            }
            setIsModalOpen(false);
            resetForm();
            loadData();
        } catch (err: any) {
            console.error("Failed to create transfer", err);
            setApiError(err.message || "Error creating transfer");
        }
    };

    const handleEdit = (t: StockTransfer) => {
        clearAllErrors();
        setApiError("");
        setEditingId(t.id);
        setProductId(String(t.product.id));
        setQuantity(String(t.quantity));
        setDirection(t.fromLocation === "GODOWN" ? "GODOWN_TO_CASHIER" : "CASHIER_TO_GODOWN");
        setRemarks(t.remarks || "");
        setTransferredBy(t.transferredBy || "");
        setIsModalOpen(true);
    };

    const handleDelete = async (id: number) => {
        if (!confirm("Are you sure? This will reverse the stock changes.")) return;
        try {
            await deleteStockTransfer(id);
            loadData();
        } catch (err: any) {
            console.error("Failed to delete", err);
            setApiError(err.message || "Error deleting transfer");
        }
    };

    const handleView = (t: StockTransfer) => {
        setViewTransfer(t);
        setIsViewModalOpen(true);
    };

    const resetForm = () => {
        setEditingId(null);
        setProductId("");
        setQuantity("");
        setDirection("GODOWN_TO_CASHIER");
        setRemarks("");
        setTransferredBy("");
    };

    const filtered = transfers.filter((t) => {
        const q = searchQuery.toLowerCase();
        return (
            !searchQuery ||
            t.product.name?.toLowerCase().includes(q) ||
            t.fromLocation?.toLowerCase().includes(q) ||
            t.toLocation?.toLowerCase().includes(q) ||
            t.transferredBy?.toLowerCase().includes(q)
        );
    });

    const { page, setPage, totalPages, totalElements, pageSize, paginatedData } = useClientPagination(filtered);

    return (
        <div className="p-6 h-screen overflow-hidden bg-background transition-colors duration-300">
            <div className="max-w-7xl mx-auto">
                <div className="flex justify-between items-center mb-8">
                    <div>
                        <h1 className="text-4xl font-bold text-foreground tracking-tight">
                            Stock <span className="text-gradient">Transfers</span>
                        </h1>
                        <p className="text-muted-foreground mt-2">
                            Transfer non-fuel products between Godown and Cashier.
                        </p>
                    </div>
                    <PermissionGate permission="INVENTORY_MANAGE">
                        <button
                            onClick={() => { resetForm(); clearAllErrors(); setApiError(""); setIsModalOpen(true); }}
                            className="btn-gradient px-6 py-3 rounded-xl font-medium flex items-center gap-2 shadow-lg hover:shadow-xl transition-all"
                        >
                            <Plus className="w-5 h-5" />
                            New Transfer
                        </button>
                    </PermissionGate>
                </div>

                {isLoading ? (
                    <div className="flex flex-col items-center justify-center py-20 text-muted-foreground">
                        <div className="w-12 h-12 border-4 border-primary/20 border-t-primary rounded-full animate-spin mb-4"></div>
                        <p className="animate-pulse">Loading transfers...</p>
                    </div>
                ) : transfers.length === 0 ? (
                    <div className="text-center py-20 bg-black/5 dark:bg-white/5 rounded-2xl border border-dashed border-border">
                        <ArrowLeftRight className="w-16 h-16 mx-auto text-muted-foreground mb-4 opacity-50" />
                        <h3 className="text-xl font-semibold text-foreground mb-2">No Transfers Yet</h3>
                        <p className="text-muted-foreground mb-6">Transfer stock between your godown and cashier display.</p>
                    </div>
                ) : (
                    <>
                        <div className="mb-6 flex flex-wrap gap-3 items-center">
                            <div className="relative flex-1 min-w-[200px] max-w-md">
                                <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                                <input
                                    type="text"
                                    placeholder="Search by product, location, transferred by..."
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
                                            <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Date & Time</th>
                                            <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Product</th>
                                            <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center">Direction</th>
                                            <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right">Quantity</th>
                                            <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">By</th>
                                            <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Remarks</th>
                                            <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center w-32">Actions</th>
                                        </tr>
                                    </thead>
                                    <tbody className="divide-y divide-border/30">
                                        {paginatedData.map((t, idx) => (
                                            <tr key={t.id} className="hover:bg-white/5 transition-colors">
                                                <td className="px-6 py-4 text-xs font-mono text-muted-foreground text-center">{page * pageSize + idx + 1}</td>
                                                <td className="px-6 py-4">
                                                    <div className="text-sm font-medium text-foreground">{new Date(t.transferDate).toLocaleDateString()}</div>
                                                    <div className="text-[10px] text-muted-foreground">{new Date(t.transferDate).toLocaleTimeString()}</div>
                                                </td>
                                                <td className="px-6 py-4">
                                                    <div className="text-sm font-bold text-foreground">{t.product.name}</div>
                                                    <div className="text-[10px] text-muted-foreground uppercase">{t.product.category}{t.product.brand ? ` • ${t.product.brand}` : ''}</div>
                                                </td>
                                                <td className="px-6 py-4">
                                                    <div className="flex items-center justify-center gap-2 text-xs font-medium">
                                                        <span className={`flex items-center gap-1 px-2 py-1 rounded-lg ${t.fromLocation === 'GODOWN' ? 'bg-blue-500/10 text-blue-500' : 'bg-purple-500/10 text-purple-500'}`}>
                                                            {t.fromLocation === 'GODOWN' ? <Warehouse className="w-3 h-3" /> : <ShoppingBag className="w-3 h-3" />}
                                                            {t.fromLocation}
                                                        </span>
                                                        <ArrowRight className="w-4 h-4 text-muted-foreground" />
                                                        <span className={`flex items-center gap-1 px-2 py-1 rounded-lg ${t.toLocation === 'CASHIER' ? 'bg-purple-500/10 text-purple-500' : 'bg-blue-500/10 text-blue-500'}`}>
                                                            {t.toLocation === 'CASHIER' ? <ShoppingBag className="w-3 h-3" /> : <Warehouse className="w-3 h-3" />}
                                                            {t.toLocation}
                                                        </span>
                                                    </div>
                                                </td>
                                                <td className="px-6 py-4 text-right font-bold font-mono text-base text-primary">{t.quantity}</td>
                                                <td className="px-6 py-4 text-sm text-muted-foreground">{t.transferredBy || '-'}</td>
                                                <td className="px-6 py-4 text-sm text-muted-foreground max-w-[200px] truncate">{t.remarks || '-'}</td>
                                                <td className="px-6 py-4">
                                                    <div className="flex justify-center gap-1">
                                                        <button onClick={() => handleView(t)} className="p-1.5 rounded-lg hover:bg-white/10 text-muted-foreground hover:text-foreground transition-colors" title="View">
                                                            <Eye className="w-4 h-4" />
                                                        </button>
                                                        <PermissionGate permission="INVENTORY_MANAGE">
                                                            <button onClick={() => handleEdit(t)} className="p-1.5 rounded-lg hover:bg-white/10 text-muted-foreground hover:text-blue-500 transition-colors" title="Edit">
                                                                <Edit3 className="w-4 h-4" />
                                                            </button>
                                                            <button onClick={() => handleDelete(t.id)} className="p-1.5 rounded-lg hover:bg-red-500/10 text-muted-foreground hover:text-red-500 transition-colors" title="Delete">
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

            <Modal isOpen={isModalOpen} onClose={() => { setIsModalOpen(false); resetForm(); }} title={editingId ? "Edit Stock Transfer" : "New Stock Transfer"}>
                <form onSubmit={handleSave} className="space-y-4">
                    <FormErrorBanner message={apiError} />
                    {/* Direction Toggle */}
                    <div>
                        <label className="block text-sm font-medium text-foreground mb-2">Transfer Direction</label>
                        <div className="grid grid-cols-2 gap-2">
                            <button
                                type="button"
                                onClick={() => setDirection("GODOWN_TO_CASHIER")}
                                className={`flex items-center justify-center gap-2 px-4 py-3 rounded-xl border text-sm font-medium transition-all ${
                                    direction === "GODOWN_TO_CASHIER"
                                        ? "border-primary bg-primary/10 text-primary"
                                        : "border-border text-muted-foreground hover:border-primary/50"
                                }`}
                            >
                                <Warehouse className="w-4 h-4" />
                                <ArrowRight className="w-3 h-3" />
                                <ShoppingBag className="w-4 h-4" />
                                <span className="ml-1">Godown → Cashier</span>
                            </button>
                            <button
                                type="button"
                                onClick={() => setDirection("CASHIER_TO_GODOWN")}
                                className={`flex items-center justify-center gap-2 px-4 py-3 rounded-xl border text-sm font-medium transition-all ${
                                    direction === "CASHIER_TO_GODOWN"
                                        ? "border-primary bg-primary/10 text-primary"
                                        : "border-border text-muted-foreground hover:border-primary/50"
                                }`}
                            >
                                <ShoppingBag className="w-4 h-4" />
                                <ArrowRight className="w-3 h-3" />
                                <Warehouse className="w-4 h-4" />
                                <span className="ml-1">Cashier → Godown</span>
                            </button>
                        </div>
                    </div>

                    <div>
                        <label className="block text-sm font-medium text-foreground mb-1.5">Product</label>
                        <select
                            value={productId}
                            onChange={(e) => { setProductId(e.target.value); clearError("productId"); }}
                            className={`w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground ${inputErrorClass(errors.productId)}`}
                        >
                            <option value="">Select a product...</option>
                            {products.map((p) => (
                                <option key={p.id} value={p.id}>{p.name} ({p.unit})</option>
                            ))}
                        </select>
                        <FieldError error={errors.productId} />
                    </div>

                    {productId && (
                        <div className="bg-primary/5 border border-primary/10 rounded-xl px-4 py-3 text-sm">
                            <span className="text-muted-foreground">Available in {direction === "GODOWN_TO_CASHIER" ? "Godown" : "Cashier"}: </span>
                            <span className="font-bold text-foreground">{getAvailableStock()}</span>
                        </div>
                    )}

                    <div>
                        <label className="block text-sm font-medium text-foreground mb-1.5">Quantity</label>
                        <input
                            type="number"
                            value={quantity}
                            onChange={(e) => { setQuantity(e.target.value); clearError("quantity"); }}
                            className={`w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground ${inputErrorClass(errors.quantity)}`}
                            placeholder="0"
                            min="0.01"
                            step="any"
                        />
                        <FieldError error={errors.quantity} />
                    </div>

                    <div>
                        <label className="block text-sm font-medium text-foreground mb-1.5">Transferred By</label>
                        <input
                            type="text"
                            value={transferredBy}
                            onChange={(e) => setTransferredBy(e.target.value)}
                            className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground"
                            placeholder="Name of person"
                        />
                    </div>

                    <div>
                        <label className="block text-sm font-medium text-foreground mb-1.5">Remarks</label>
                        <textarea
                            value={remarks}
                            onChange={(e) => setRemarks(e.target.value)}
                            className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground"
                            placeholder="Optional notes..."
                            rows={2}
                        />
                    </div>

                    <div className="flex justify-end gap-3 pt-6 border-t border-border mt-6">
                        <button type="button" onClick={() => setIsModalOpen(false)} className="px-6 py-2.5 rounded-xl font-medium text-foreground bg-black/5 dark:bg-white/5 hover:bg-black/10 transition-colors">
                            Cancel
                        </button>
                        <button type="submit" className="btn-gradient px-8 py-2.5 rounded-xl font-medium shadow-lg hover:shadow-xl transition-all">
                            {editingId ? "Update Transfer" : "Transfer Stock"}
                        </button>
                    </div>
                </form>
            </Modal>

            <Modal isOpen={isViewModalOpen} onClose={() => { setIsViewModalOpen(false); setViewTransfer(null); }} title="Transfer Details">
                {viewTransfer && (
                    <div className="space-y-4">
                        <div className="grid grid-cols-2 gap-4 text-sm">
                            <div>
                                <span className="text-muted-foreground">Date & Time:</span>
                                <div className="font-medium text-foreground">{new Date(viewTransfer.transferDate).toLocaleString()}</div>
                            </div>
                            <div>
                                <span className="text-muted-foreground">Product:</span>
                                <div className="font-medium text-foreground">{viewTransfer.product.name}</div>
                                <div className="text-[10px] text-muted-foreground uppercase">{viewTransfer.product.category}{viewTransfer.product.brand ? ` • ${viewTransfer.product.brand}` : ''}</div>
                            </div>
                            <div>
                                <span className="text-muted-foreground">Direction:</span>
                                <div className="font-medium text-foreground">{viewTransfer.fromLocation} → {viewTransfer.toLocation}</div>
                            </div>
                            <div>
                                <span className="text-muted-foreground">Quantity:</span>
                                <div className="font-bold text-primary text-lg">{viewTransfer.quantity}</div>
                            </div>
                            <div>
                                <span className="text-muted-foreground">Transferred By:</span>
                                <div className="font-medium text-foreground">{viewTransfer.transferredBy || '-'}</div>
                            </div>
                            <div>
                                <span className="text-muted-foreground">Remarks:</span>
                                <div className="font-medium text-foreground">{viewTransfer.remarks || '-'}</div>
                            </div>
                        </div>
                    </div>
                )}
            </Modal>
        </div>
    );
}
