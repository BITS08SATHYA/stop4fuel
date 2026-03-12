"use client";

import { useEffect, useState } from "react";
import { GlassCard } from "@/components/ui/glass-card";
import { Modal } from "@/components/ui/modal";
import {
    getNozzleInventories,
    getNozzles,
    createNozzleInventory,
    updateNozzleInventory,
    NozzleInventory,
    Nozzle,
    deleteNozzleInventory
} from "@/lib/api/station";
import { Fuel, Plus, Calendar, Hash, ArrowUpRight, Trash2, Edit2, Search } from "lucide-react";

export default function NozzleInventoryPage() {
    const [inventories, setInventories] = useState<NozzleInventory[]>([]);
    const [nozzles, setNozzles] = useState<Nozzle[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [editingId, setEditingId] = useState<number | null>(null);
    const [searchQuery, setSearchQuery] = useState("");
    const [dateFilter, setDateFilter] = useState("");
    const [nozzleFilter, setNozzleFilter] = useState<string>("");

    // Form State
    const [date, setDate] = useState(new Date().toISOString().split('T')[0]);
    const [nozzleId, setNozzleId] = useState("");
    const [openReading, setOpenReading] = useState("");
    const [closeReading, setCloseReading] = useState("");
    const [calculatedSales, setCalculatedSales] = useState(0);

    const loadData = async () => {
        setIsLoading(true);
        // Load Nozzles (for the dropdown)
        try {
            const nData = await getNozzles();
            setNozzles(nData.filter(n => n.active));
        } catch (err) {
            console.error("Failed to load nozzles", err);
        }

        // Load Inventories (for the table)
        try {
            const iData = await getNozzleInventories();
            setInventories(iData.sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime()));
        } catch (err) {
            console.error("Failed to load nozzle inventory logs", err);
            // Don't alert here to avoid annoying the user if logs are just empty/erroring
        }
        setIsLoading(false);
    };

    useEffect(() => {
        loadData();
    }, []);

    // Auto-calculate sales when readings change
    useEffect(() => {
        const open = parseFloat(openReading);
        const close = parseFloat(closeReading);
        if (!isNaN(open) && !isNaN(close)) {
            setCalculatedSales(Math.max(0, close - open));
        } else {
            setCalculatedSales(0);
        }
    }, [openReading, closeReading]);

    const handleEdit = (inv: NozzleInventory) => {
        setEditingId(inv.id);
        setDate(inv.date);
        setNozzleId(String(inv.nozzle.id));
        setOpenReading(String(inv.openMeterReading || ""));
        setCloseReading(String(inv.closeMeterReading || ""));
        setIsModalOpen(true);
    };

    const handleSave = async (e: React.FormEvent) => {
        e.preventDefault();
        try {
            const payload = {
                date,
                nozzle: { id: Number(nozzleId) },
                openMeterReading: Number(openReading),
                closeMeterReading: Number(closeReading)
            };

            if (editingId) {
                await updateNozzleInventory(editingId, payload as any);
            } else {
                await createNozzleInventory(payload as any);
            }
            setIsModalOpen(false);
            resetForm();
            loadData();
        } catch (err) {
            console.error("Failed to save nozzle inventory", err);
            alert("Error saving inventory details");
        }
    };

    const handleDelete = async (id: number) => {
        if (!confirm("Are you sure you want to delete this reading?")) return;
        try {
            await deleteNozzleInventory(id);
            loadData();
        } catch (err) {
            console.error("Failed to delete", err);
            alert("Error deleting record");
        }
    };

    const resetForm = () => {
        setEditingId(null);
        setNozzleId("");
        setOpenReading("");
        setCloseReading("");
        setCalculatedSales(0);
    };

    return (
        <div className="p-8 min-h-screen bg-background transition-colors duration-300">
            <div className="max-w-7xl mx-auto">
                <div className="flex justify-between items-center mb-8">
                    <div>
                        <h1 className="text-4xl font-bold text-foreground tracking-tight">
                            Nozzle <span className="text-gradient">Daily Inventory</span>
                        </h1>
                        <p className="text-muted-foreground mt-2">
                            Record daily meter readings and track sales per nozzle.
                        </p>
                    </div>
                    <button
                        onClick={() => { resetForm(); setIsModalOpen(true); }}
                        className="btn-gradient px-6 py-3 rounded-xl font-medium flex items-center gap-2 shadow-lg hover:shadow-xl transition-all"
                    >
                        <Plus className="w-5 h-5" />
                        Add Daily Reading
                    </button>
                </div>

                {isLoading ? (
                    <div className="flex flex-col items-center justify-center py-20 text-muted-foreground">
                        <div className="w-12 h-12 border-4 border-primary/20 border-t-primary rounded-full animate-spin mb-4"></div>
                        <p className="animate-pulse">Loading reading logs...</p>
                    </div>
                ) : inventories.length === 0 ? (
                    <div className="text-center py-20 bg-black/5 dark:bg-white/5 rounded-2xl border border-dashed border-border">
                        <Calendar className="w-16 h-16 mx-auto text-muted-foreground mb-4 opacity-50" />
                        <h3 className="text-xl font-semibold text-foreground mb-2">No Records Found</h3>
                        <p className="text-muted-foreground mb-6">Start by recording your first daily nozzle meter reading.</p>
                    </div>
                ) : (
                    <>
                    {/* Filter Bar */}
                    <div className="mb-6 flex flex-wrap gap-3 items-center">
                        <div className="relative flex-1 min-w-[200px] max-w-md">
                            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                            <input
                                type="text"
                                placeholder="Search by nozzle, pump, or product..."
                                value={searchQuery}
                                onChange={(e) => setSearchQuery(e.target.value)}
                                className="w-full pl-10 pr-4 py-2.5 bg-card border border-border rounded-xl text-foreground text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                            />
                        </div>
                        <select
                            value={nozzleFilter}
                            onChange={(e) => setNozzleFilter(e.target.value)}
                            className="px-4 py-2.5 bg-card border border-border rounded-xl text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
                        >
                            <option value="">All Nozzles</option>
                            {nozzles.map((n) => (
                                <option key={n.id} value={n.id}>{n.nozzleName} ({n.pump.name})</option>
                            ))}
                        </select>
                        <input
                            type="date"
                            value={dateFilter}
                            onChange={(e) => setDateFilter(e.target.value)}
                            className="px-4 py-2.5 bg-card border border-border rounded-xl text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
                        />
                        {(searchQuery || nozzleFilter || dateFilter) && (
                            <button
                                onClick={() => { setSearchQuery(""); setNozzleFilter(""); setDateFilter(""); }}
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
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Nozzle/Pump Details</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right w-32">Open Reading</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right w-32">Close Reading</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right w-32 italic">Total Sales</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center w-24">Actions</th>
                                    </tr>
                                </thead>
                                <tbody className="divide-y divide-border/30">
                                    {inventories.filter((inv) => {
                                        const q = searchQuery.toLowerCase();
                                        const matchesSearch = !searchQuery || inv.nozzle.nozzleName?.toLowerCase().includes(q) || inv.nozzle.pump.name?.toLowerCase().includes(q) || inv.nozzle.tank.product.name?.toLowerCase().includes(q);
                                        const matchesNozzle = !nozzleFilter || String(inv.nozzle.id) === nozzleFilter;
                                        const matchesDate = !dateFilter || inv.date === dateFilter;
                                        return matchesSearch && matchesNozzle && matchesDate;
                                    }).map((inv, idx) => (
                                        <tr key={inv.id} className="hover:bg-white/5 transition-colors group">
                                            <td className="px-6 py-4 text-xs font-mono text-muted-foreground text-center">{idx + 1}</td>
                                            <td className="px-6 py-4">
                                                <div className="text-sm font-medium text-foreground">{new Date(inv.date).toLocaleDateString()}</div>
                                            </td>
                                            <td className="px-6 py-4">
                                                <div className="flex items-center gap-3">
                                                    <div className="p-2 rounded-lg bg-green-500/10 text-green-500">
                                                        <Fuel className="w-4 h-4" />
                                                    </div>
                                                    <div>
                                                        <div className="text-sm font-bold text-foreground">{inv.nozzle.nozzleName}</div>
                                                        <div className="text-[10px] text-muted-foreground flex items-center gap-1">
                                                            <span>{inv.nozzle.pump.name}</span>
                                                            <span>•</span>
                                                            <span>{inv.nozzle.tank.product.name}</span>
                                                        </div>
                                                    </div>
                                                </div>
                                            </td>
                                            <td className="px-6 py-4 text-right font-mono text-sm">{inv.openMeterReading?.toLocaleString()}</td>
                                            <td className="px-6 py-4 text-right font-mono text-sm">{inv.closeMeterReading?.toLocaleString()}</td>
                                            <td className="px-6 py-4 text-right font-black text-primary text-base font-mono bg-primary/5">
                                                {inv.sales?.toLocaleString()} <span className="text-[10px] font-bold opacity-70 ml-1">L</span>
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
                title={editingId ? "Edit Nozzle Reading" : "Record Daily Nozzle Reading"}
            >
                <form onSubmit={handleSave} className="space-y-4">
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5 flex items-center gap-2">
                                <Calendar className="w-4 h-4 text-primary" /> Date
                            </label>
                            <input
                                type="date"
                                required
                                value={date}
                                onChange={(e) => setDate(e.target.value)}
                                className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                            />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5">Selected Nozzle</label>
                            <select
                                required
                                value={nozzleId}
                                onChange={(e) => setNozzleId(e.target.value)}
                                className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                            >
                                <option value="">Select Nozzle...</option>
                                {nozzles.map(n => (
                                    <option key={n.id} value={n.id}>{n.nozzleName} ({n.pump.name} - {n.tank.product.name})</option>
                                ))}
                            </select>
                        </div>

                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5">Open Reading</label>
                            <input
                                type="number"
                                step="0.01"
                                required
                                value={openReading}
                                onChange={(e) => setOpenReading(e.target.value)}
                                className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50 font-mono"
                                placeholder="0.00"
                            />
                        </div>

                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5">Close Reading</label>
                            <input
                                type="number"
                                step="0.01"
                                required
                                value={closeReading}
                                onChange={(e) => setCloseReading(e.target.value)}
                                className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50 font-mono"
                                placeholder="0.00"
                            />
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
                            {editingId ? "Update Reading" : "Save Reading"}
                        </button>
                    </div>
                </form>
            </Modal>
        </div>
    );
}
