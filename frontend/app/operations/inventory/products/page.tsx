"use client";

import { useEffect, useState } from "react";
import { GlassCard } from "@/components/ui/glass-card";
import { Modal } from "@/components/ui/modal";
import {
    getProductInventories,
    getActiveProducts,
    createProductInventory,
    updateProductInventory,
    ProductInventory,
    Product,
    deleteProductInventory
} from "@/lib/api/station";
import { Box, Plus, Calendar, Archive, TrendingUp, Trash2, Edit2, Search } from "lucide-react";

export default function ProductInventoryPage() {
    const [inventories, setInventories] = useState<ProductInventory[]>([]);
    const [products, setProducts] = useState<Product[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [editingId, setEditingId] = useState<number | null>(null);
    const [searchQuery, setSearchQuery] = useState("");
    const [dateFilter, setDateFilter] = useState("");
    const [productFilter, setProductFilter] = useState<string>("");

    // Form State
    const [date, setDate] = useState(new Date().toISOString().split('T')[0]);
    const [productId, setProductId] = useState("");
    const [openStock, setOpenStock] = useState("");
    const [incomeStock, setIncomeStock] = useState("");
    const [closeStock, setCloseStock] = useState("");
    
    // Derived Calculations
    const [totalStock, setTotalStock] = useState(0);
    const [sales, setSales] = useState(0);

    const loadData = async () => {
        setIsLoading(true);
        // Load Products (for the dropdown)
        try {
            const pData = await getActiveProducts();
            setProducts(pData);
        } catch (err) {
            console.error("Failed to load products", err);
        }

        // Load Inventories (for the list)
        try {
            const iData = await getProductInventories();
            setInventories(iData.sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime()));
        } catch (err) {
            console.error("Failed to load product inventory logs", err);
        }
        setIsLoading(false);
    };

    useEffect(() => {
        loadData();
    }, []);

    // Derived auto-calculations
    useEffect(() => {
        const o = parseFloat(openStock) || 0;
        const i = parseFloat(incomeStock) || 0;
        const c = parseFloat(closeStock) || 0;
        
        const total = o + i;
        setTotalStock(total);
        setSales(Math.max(0, total - c));
    }, [openStock, incomeStock, closeStock]);

    const handleEdit = (inv: ProductInventory) => {
        setEditingId(inv.id);
        setDate(inv.date);
        setProductId(String(inv.product.id));
        setOpenStock(String(inv.openStock || ""));
        setIncomeStock(String(inv.incomeStock || ""));
        setCloseStock(String(inv.closeStock || ""));
        setIsModalOpen(true);
    };

    const handleSave = async (e: React.FormEvent) => {
        e.preventDefault();
        try {
            const payload = {
                date,
                product: { id: Number(productId) },
                openStock: Number(openStock),
                incomeStock: Number(incomeStock),
                closeStock: Number(closeStock)
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
            alert("Error saving non-fuel inventory details");
        }
    };

    const handleDelete = async (id: number) => {
        if (!confirm("Are you sure you want to delete this check?")) return;
        try {
            await deleteProductInventory(id);
            loadData();
        } catch (err) {
            console.error("Failed to delete", err);
            alert("Error deleting record");
        }
    };

    const resetForm = () => {
        setEditingId(null);
        setProductId("");
        setOpenStock("");
        setIncomeStock("");
        setCloseStock("");
    };

    const getUnit = () => {
        const p = products.find(p => p.id === Number(productId));
        return p?.unit || "Units";
    };

    return (
        <div className="p-8 min-h-screen bg-background transition-colors duration-300">
            <div className="max-w-7xl mx-auto">
                <div className="flex justify-between items-center mb-8">
                    <div>
                        <h1 className="text-4xl font-bold text-foreground tracking-tight">
                            Non-Fuel <span className="text-gradient">Daily Check</span>
                        </h1>
                        <p className="text-muted-foreground mt-2">
                            Monitor lubricants, accessories, and other retail items inventory levels.
                        </p>
                    </div>
                    <button
                        onClick={() => { resetForm(); setIsModalOpen(true); }}
                        className="btn-gradient px-6 py-3 rounded-xl font-medium flex items-center gap-2 shadow-lg hover:shadow-xl transition-all"
                    >
                        <Plus className="w-5 h-5" />
                        Record Inventory
                    </button>
                </div>

                {isLoading ? (
                    <div className="flex flex-col items-center justify-center py-20 text-muted-foreground">
                        <div className="w-12 h-12 border-4 border-primary/20 border-t-primary rounded-full animate-spin mb-4"></div>
                        <p className="animate-pulse">Loading product checks...</p>
                    </div>
                ) : inventories.length === 0 ? (
                    <div className="text-center py-20 bg-black/5 dark:bg-white/5 rounded-2xl border border-dashed border-border">
                        <Archive className="w-16 h-16 mx-auto text-muted-foreground mb-4 opacity-50" />
                        <h3 className="text-xl font-semibold text-foreground mb-2">No Records Found</h3>
                        <p className="text-muted-foreground mb-6">Track your daily shop sales and stock levels here.</p>
                    </div>
                ) : (
                    <>
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
                        <select
                            value={productFilter}
                            onChange={(e) => setProductFilter(e.target.value)}
                            className="px-4 py-2.5 bg-card border border-border rounded-xl text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
                        >
                            <option value="">All Products</option>
                            {products.map((p) => (
                                <option key={p.id} value={p.id}>{p.name}</option>
                            ))}
                        </select>
                        <input
                            type="date"
                            value={dateFilter}
                            onChange={(e) => setDateFilter(e.target.value)}
                            className="px-4 py-2.5 bg-card border border-border rounded-xl text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
                        />
                        {(searchQuery || productFilter || dateFilter) && (
                            <button
                                onClick={() => { setSearchQuery(""); setProductFilter(""); setDateFilter(""); }}
                                className="px-3 py-2.5 text-xs text-muted-foreground hover:text-foreground"
                            >
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
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground w-32">Date</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Product Details</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right w-24">Opening</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right w-24">Arrivals (+)</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right w-24">Closing</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right w-24 italic">Units Sold</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center w-24">Actions</th>
                                    </tr>
                                </thead>
                                <tbody className="divide-y divide-border/30">
                                    {inventories.filter((inv) => {
                                        const q = searchQuery.toLowerCase();
                                        const matchesSearch = !searchQuery || inv.product.name?.toLowerCase().includes(q) || inv.product.category?.toLowerCase().includes(q) || inv.product.brand?.toLowerCase().includes(q);
                                        const matchesProduct = !productFilter || String(inv.product.id) === productFilter;
                                        const matchesDate = !dateFilter || inv.date === dateFilter;
                                        return matchesSearch && matchesProduct && matchesDate;
                                    }).map((inv, idx) => (
                                        <tr key={inv.id} className="hover:bg-white/5 transition-colors group">
                                            <td className="px-6 py-4 text-xs font-mono text-muted-foreground text-center">{idx + 1}</td>
                                            <td className="px-6 py-4">
                                                <div className="text-sm font-medium text-foreground">{new Date(inv.date).toLocaleDateString()}</div>
                                            </td>
                                            <td className="px-6 py-4">
                                                <div className="flex items-center gap-3">
                                                    <div className="p-2 rounded-lg bg-primary/10 text-primary">
                                                        <Box className="w-4 h-4" />
                                                    </div>
                                                    <div>
                                                        <div className="text-sm font-bold text-foreground">{inv.product.name}</div>
                                                        <div className="text-[10px] text-muted-foreground uppercase flex items-center gap-1">
                                                            <span>{inv.product.category}</span>
                                                            {inv.product.brand && (
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
                                                <div className="text-[9px] text-muted-foreground uppercase">{inv.product.unit}</div>
                                            </td>
                                            <td className="px-6 py-4 text-right text-blue-500 font-medium font-mono text-sm leading-none">
                                                {inv.incomeStock && inv.incomeStock > 0 ? `+${inv.incomeStock.toLocaleString()}` : "-"}
                                            </td>
                                            <td className="px-6 py-4 text-right">
                                                <div className="text-sm font-mono">{inv.closeStock?.toLocaleString()}</div>
                                                <div className="text-[9px] text-muted-foreground uppercase">{inv.product.unit}</div>
                                            </td>
                                            <td className="px-6 py-4 text-right font-black text-primary text-base font-mono bg-primary/5">
                                                {inv.sales?.toLocaleString()}
                                            </td>
                                            <td className="px-6 py-4">
                                                <div className="flex justify-center gap-2">
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
                                                </div>
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    </GlassCard>
                    </>
                )}
            </div>

            <Modal
                isOpen={isModalOpen}
                onClose={() => { setIsModalOpen(false); resetForm(); }}
                title={editingId ? "Edit Inventory Check" : "Daily Non-Fuel Inventory Check"}
            >
                <form onSubmit={handleSave} className="space-y-4">
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
                        
                        <div className="md:col-span-2">
                            <label className="block text-sm font-medium text-foreground mb-1.5">Select Product</label>
                            <select
                                required
                                value={productId}
                                onChange={(e) => setProductId(e.target.value)}
                                className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground"
                            >
                                <option value="">Select a Product...</option>
                                {products.map(p => (
                                    <option key={p.id} value={p.id}>{p.name} ({p.unit})</option>
                                ))}
                            </select>
                        </div>

                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5">Opening Stock</label>
                            <input
                                type="number"
                                required
                                value={openStock}
                                onChange={(e) => setOpenStock(e.target.value)}
                                className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground"
                                placeholder="0"
                            />
                        </div>

                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5">New Arrivals (+)</label>
                            <input
                                type="number"
                                required
                                value={incomeStock}
                                onChange={(e) => setIncomeStock(e.target.value)}
                                className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground border-blue-200"
                                placeholder="0"
                            />
                        </div>

                        <div className="md:col-span-2">
                            <label className="block text-sm font-medium text-foreground mb-1.5 font-bold">Actual Closing Physical Stock</label>
                            <input
                                type="number"
                                required
                                value={closeStock}
                                onChange={(e) => setCloseStock(e.target.value)}
                                className="w-full bg-primary/5 border-primary/30 border rounded-xl px-4 py-3 text-foreground text-center text-xl font-bold"
                                placeholder="Count them now"
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
                                <p className="text-[10px] text-primary uppercase font-bold tracking-widest">Units Sold</p>
                                <p className="text-3xl font-black text-primary">{sales} <span className="text-sm">{getUnit()}</span></p>
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
