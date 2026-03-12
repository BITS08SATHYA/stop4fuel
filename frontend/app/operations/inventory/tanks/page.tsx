"use client";

import { useEffect, useState } from "react";
import { GlassCard } from "@/components/ui/glass-card";
import { Modal } from "@/components/ui/modal";
import {
    getTankInventories,
    getTanks,
    createTankInventory,
    updateTankInventory,
    TankInventory,
    Tank,
    deleteTankInventory
} from "@/lib/api/station";
import { Droplets, Plus, Calendar, Ruler, TrendingDown, Trash2, Edit2, Search } from "lucide-react";

export default function TankInventoryPage() {
    const [inventories, setInventories] = useState<TankInventory[]>([]);
    const [tanks, setTanks] = useState<Tank[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [editingId, setEditingId] = useState<number | null>(null);
    const [searchQuery, setSearchQuery] = useState("");
    const [dateFilter, setDateFilter] = useState("");
    const [tankFilter, setTankFilter] = useState<string>("");

    // Form State
    const [date, setDate] = useState(new Date().toISOString().split('T')[0]);
    const [tankId, setTankId] = useState("");
    const [openDip, setOpenDip] = useState("");
    const [openStock, setOpenStock] = useState("");
    const [incomeStock, setIncomeStock] = useState("");
    const [closeDip, setCloseDip] = useState("");
    const [closeStock, setCloseStock] = useState("");
    
    // Derived Calculations
    const [totalStock, setTotalStock] = useState(0);
    const [saleStock, setSaleStock] = useState(0);

    const loadData = async () => {
        setIsLoading(true);
        // Load Tanks (for the dropdown)
        try {
            console.log("Fetching tanks from:", getTanks);
            const tData = await getTanks();
            console.log("Loaded tanks:", tData);
            setTanks(tData.filter(t => t.active));
        } catch (err) {
            console.error("Failed to load tanks", err);
            // alert("Failed to fetch tanks master data. Check if backend is running at http://localhost:8080");
        }

        // Load Inventories (for the table)
        try {
            const iData = await getTankInventories();
            setInventories(iData.sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime()));
        } catch (err) {
            console.error("Failed to load tank inventory logs", err);
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
        setSaleStock(Math.max(0, total - c));
    }, [openStock, incomeStock, closeStock]);

    const handleEdit = (inv: TankInventory) => {
        setEditingId(inv.id);
        setDate(inv.date);
        setTankId(String(inv.tank.id));
        setOpenDip(inv.openDip || "");
        setOpenStock(String(inv.openStock || ""));
        setIncomeStock(String(inv.incomeStock || ""));
        setCloseDip(inv.closeDip || "");
        setCloseStock(String(inv.closeStock || ""));
        setIsModalOpen(true);
    };

    const handleSave = async (e: React.FormEvent) => {
        e.preventDefault();
        try {
            const payload = {
                date,
                tank: { id: Number(tankId) },
                openDip,
                openStock: Number(openStock),
                incomeStock: Number(incomeStock),
                closeDip,
                closeStock: Number(closeStock)
            };

            if (editingId) {
                await updateTankInventory(editingId, payload as any);
            } else {
                await createTankInventory(payload as any);
            }
            setIsModalOpen(false);
            resetForm();
            loadData();
        } catch (err) {
            console.error("Failed to save tank inventory", err);
            alert("Error saving tank dip details");
        }
    };

    const handleDelete = async (id: number) => {
        if (!confirm("Are you sure you want to delete this reading?")) return;
        try {
            await deleteTankInventory(id);
            loadData();
        } catch (err) {
            console.error("Failed to delete", err);
            alert("Error deleting record");
        }
    };

    const resetForm = () => {
        setEditingId(null);
        setTankId("");
        setOpenDip("");
        setOpenStock("");
        setIncomeStock("");
        setCloseDip("");
        setCloseStock("");
    };

    return (
        <div className="p-8 min-h-screen bg-background transition-colors duration-300">
            <div className="max-w-7xl mx-auto">
                <div className="flex justify-between items-center mb-8">
                    <div>
                        <h1 className="text-4xl font-bold text-foreground tracking-tight">
                            Tank <span className="text-gradient">Dip Readings</span>
                        </h1>
                        <p className="text-muted-foreground mt-2">
                            Manage underground fuel storage measurements and stock reconciliation.
                        </p>
                    </div>
                    <button
                        onClick={() => { resetForm(); setIsModalOpen(true); }}
                        className="btn-gradient px-6 py-3 rounded-xl font-medium flex items-center gap-2 shadow-lg hover:shadow-xl transition-all"
                    >
                        <Plus className="w-5 h-5" />
                        Record Tank Dip
                    </button>
                </div>

                {isLoading ? (
                    <div className="flex flex-col items-center justify-center py-20 text-muted-foreground">
                        <div className="w-12 h-12 border-4 border-primary/20 border-t-primary rounded-full animate-spin mb-4"></div>
                        <p className="animate-pulse">Loading dip logs...</p>
                    </div>
                ) : inventories.length === 0 ? (
                    <div className="text-center py-20 bg-black/5 dark:bg-white/5 rounded-2xl border border-dashed border-border">
                        <Ruler className="w-16 h-16 mx-auto text-muted-foreground mb-4 opacity-50" />
                        <h3 className="text-xl font-semibold text-foreground mb-2">No Records Found</h3>
                        <p className="text-muted-foreground mb-6">Start by recording your first physical tank dip measurement.</p>
                    </div>
                ) : (
                    <>
                    {/* Filter Bar */}
                    <div className="mb-6 flex flex-wrap gap-3 items-center">
                        <div className="relative flex-1 min-w-[200px] max-w-md">
                            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                            <input
                                type="text"
                                placeholder="Search by tank or product name..."
                                value={searchQuery}
                                onChange={(e) => setSearchQuery(e.target.value)}
                                className="w-full pl-10 pr-4 py-2.5 bg-card border border-border rounded-xl text-foreground text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                            />
                        </div>
                        <select
                            value={tankFilter}
                            onChange={(e) => setTankFilter(e.target.value)}
                            className="px-4 py-2.5 bg-card border border-border rounded-xl text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
                        >
                            <option value="">All Tanks</option>
                            {tanks.map((t) => (
                                <option key={t.id} value={t.id}>{t.name}</option>
                            ))}
                        </select>
                        <input
                            type="date"
                            value={dateFilter}
                            onChange={(e) => setDateFilter(e.target.value)}
                            className="px-4 py-2.5 bg-card border border-border rounded-xl text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
                        />
                        {(searchQuery || tankFilter || dateFilter) && (
                            <button
                                onClick={() => { setSearchQuery(""); setTankFilter(""); setDateFilter(""); }}
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
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Tank / Product</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right w-24">Open Stock</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right w-24">Income (+)</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right w-24">Close Stock</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right w-24 italic">Daily Sales</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center w-24">Actions</th>
                                    </tr>
                                </thead>
                                <tbody className="divide-y divide-border/30">
                                    {inventories.filter((inv) => {
                                        const q = searchQuery.toLowerCase();
                                        const matchesSearch = !searchQuery || inv.tank.name?.toLowerCase().includes(q) || inv.tank.product.name?.toLowerCase().includes(q);
                                        const matchesTank = !tankFilter || String(inv.tank.id) === tankFilter;
                                        const matchesDate = !dateFilter || inv.date === dateFilter;
                                        return matchesSearch && matchesTank && matchesDate;
                                    }).map((inv, idx) => (
                                        <tr key={inv.id} className="hover:bg-white/5 transition-colors group">
                                            <td className="px-6 py-4 text-xs font-mono text-muted-foreground text-center">{idx + 1}</td>
                                            <td className="px-6 py-4">
                                                <div className="text-sm font-medium text-foreground">{new Date(inv.date).toLocaleDateString()}</div>
                                            </td>
                                            <td className="px-6 py-4 font-bold">
                                                <div className="text-sm text-foreground">{inv.tank.name}</div>
                                                <div className="text-[10px] text-muted-foreground">{inv.tank.product.name}</div>
                                            </td>
                                            <td className="px-6 py-4 text-right">
                                                <div className="text-sm font-mono">{inv.openStock?.toLocaleString()}</div>
                                                <div className="text-[9px] text-muted-foreground font-mono">{inv.openDip}cm</div>
                                            </td>
                                            <td className="px-6 py-4 text-right text-blue-500 font-medium font-mono text-sm leading-none">
                                                {inv.incomeStock && inv.incomeStock > 0 ? `+${inv.incomeStock.toLocaleString()}` : "-"}
                                            </td>
                                            <td className="px-6 py-4 text-right">
                                                <div className="text-sm font-mono">{inv.closeStock?.toLocaleString()}</div>
                                                <div className="text-[9px] text-muted-foreground font-mono">{inv.closeDip}cm</div>
                                            </td>
                                            <td className="px-6 py-4 text-right font-black text-primary text-base font-mono bg-primary/5">
                                                {inv.saleStock?.toLocaleString()}
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
                title={editingId ? "Edit Tank Reading" : "Record Daily Tank Reading"}
            >
                <form onSubmit={handleSave} className="space-y-6">
                    <div className="grid grid-cols-2 gap-4">
                        <div className="col-span-1">
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
                        <div className="col-span-1">
                            <label className="block text-sm font-medium text-foreground mb-1.5">Selected Tank</label>
                            <select
                                required
                                value={tankId}
                                onChange={(e) => setTankId(e.target.value)}
                                className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground"
                            >
                                <option value="">Select Tank...</option>
                                {tanks.map(t => (
                                    <option key={t.id} value={t.id}>{t.name} ({t.product.name} - Cap: {t.capacity}L)</option>
                                ))}
                            </select>
                        </div>
                    </div>

                    <div className="space-y-4">
                        <div className="grid grid-cols-3 gap-4 items-end">
                            <div className="col-span-1">
                                <label className="block text-sm font-medium text-foreground mb-1.5">Open Dip (cm)</label>
                                <input
                                    type="text"
                                    required
                                    value={openDip}
                                    onChange={(e) => setOpenDip(e.target.value)}
                                    className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground"
                                    placeholder="e.g. 150.5"
                                />
                            </div>
                            <div className="col-span-1">
                                <label className="block text-sm font-medium text-foreground mb-1.5">Open Stock (L)</label>
                                <input
                                    type="number"
                                    required
                                    value={openStock}
                                    onChange={(e) => setOpenStock(e.target.value)}
                                    className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground"
                                    placeholder="0"
                                />
                            </div>
                            <div className="col-span-1">
                                <label className="block text-sm font-medium text-foreground mb-1.5 text-blue-500">Income (+ L)</label>
                                <input
                                    type="number"
                                    required
                                    value={incomeStock}
                                    onChange={(e) => setIncomeStock(e.target.value)}
                                    className="w-full bg-blue-50/50 dark:bg-blue-900/10 border-blue-200 dark:border-blue-900/50 border rounded-xl px-4 py-3 text-blue-700 dark:text-blue-400"
                                    placeholder="New Drop"
                                />
                            </div>
                        </div>

                        <div className="grid grid-cols-2 gap-4">
                            <div>
                                <label className="block text-sm font-medium text-foreground mb-1.5">Close Dip (cm)</label>
                                <input
                                    type="text"
                                    required
                                    value={closeDip}
                                    onChange={(e) => setCloseDip(e.target.value)}
                                    className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground"
                                    placeholder="e.g. 142.2"
                                />
                            </div>
                            <div>
                                <label className="block text-sm font-medium text-foreground mb-1.5">Close Stock (L)</label>
                                <input
                                    type="number"
                                    required
                                    value={closeStock}
                                    onChange={(e) => setCloseStock(e.target.value)}
                                    className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground"
                                    placeholder="Reading"
                                />
                            </div>
                        </div>
                    </div>

                    <div className="grid grid-cols-2 gap-3">
                        <div className="bg-muted rounded-2xl p-4">
                            <p className="text-xs text-muted-foreground uppercase font-bold mb-1">Total Available</p>
                            <p className="text-xl font-bold font-mono">{totalStock.toLocaleString()} L</p>
                        </div>
                        <div className="bg-primary/20 rounded-2xl p-4 border border-primary/30">
                            <p className="text-xs text-primary uppercase font-bold mb-1">Est. Daily Sales</p>
                            <p className="text-xl font-bold text-primary dark:text-primary font-mono">{saleStock.toLocaleString()} L</p>
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
                            {editingId ? "Update Reading" : "Confirm Dip Reading"}
                        </button>
                    </div>
                </form>
            </Modal>
        </div>
    );
}
