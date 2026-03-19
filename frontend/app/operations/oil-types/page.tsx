"use client";

import { useEffect, useState } from "react";
import { GlassCard } from "@/components/ui/glass-card";
import { Modal } from "@/components/ui/modal";
import {
    getOilTypes,
    createOilType,
    updateOilType,
    deleteOilType,
    toggleOilTypeStatus,
    OilType
} from "@/lib/api/station";
import { Droplets, Plus, Edit2, Trash2, CheckCircle2, XCircle, Search, FileText } from "lucide-react";

export default function OilTypesPage() {
    const [oilTypes, setOilTypes] = useState<OilType[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [editingId, setEditingId] = useState<number | null>(null);
    const [searchQuery, setSearchQuery] = useState("");
    const [statusFilter, setStatusFilter] = useState<string>("ALL");

    // Form State
    const [name, setName] = useState("");
    const [description, setDescription] = useState("");
    const [active, setActive] = useState(true);

    const loadData = async () => {
        setIsLoading(true);
        try {
            const data = await getOilTypes();
            setOilTypes(data);
        } catch (err) {
            console.error("Failed to load oil types", err);
        } finally {
            setIsLoading(false);
        }
    };

    useEffect(() => {
        loadData();
    }, []);

    const handleEdit = (oilType: OilType) => {
        setEditingId(oilType.id);
        setName(oilType.name);
        setDescription(oilType.description || "");
        setActive(oilType.active);
        setIsModalOpen(true);
    };

    const handleDelete = async (id: number) => {
        if (!confirm("Are you sure you want to delete this oil type?")) return;
        try {
            await deleteOilType(id);
            loadData();
        } catch (err) {
            console.error("Failed to delete oil type", err);
            alert("Error deleting oil type. It may be in use by grades or products.");
        }
    };

    const handleToggleStatus = async (id: number) => {
        try {
            await toggleOilTypeStatus(id);
            loadData();
        } catch (err) {
            console.error("Failed to toggle status", err);
        }
    };

    const handleSave = async (e: React.FormEvent) => {
        e.preventDefault();
        try {
            const payload = { name, description, active };
            if (editingId) {
                await updateOilType(editingId, payload);
            } else {
                await createOilType(payload);
            }
            setIsModalOpen(false);
            resetForm();
            loadData();
        } catch (err) {
            console.error("Failed to save oil type", err);
            alert("Error saving oil type details");
        }
    };

    const resetForm = () => {
        setEditingId(null);
        setName("");
        setDescription("");
        setActive(true);
    };

    const filtered = oilTypes.filter((ot) => {
        const q = searchQuery.toLowerCase();
        const matchesSearch = !searchQuery || ot.name?.toLowerCase().includes(q) || ot.description?.toLowerCase().includes(q);
        const matchesStatus = statusFilter === "ALL" || (ot.active ? "ACTIVE" : "INACTIVE") === statusFilter;
        return matchesSearch && matchesStatus;
    });

    return (
        <div className="p-8 min-h-screen bg-background">
            <div className="max-w-7xl mx-auto">
                <div className="flex justify-between items-center mb-8">
                    <div>
                        <h1 className="text-4xl font-bold text-foreground tracking-tight">
                            Oil <span className="text-gradient">Types</span>
                        </h1>
                        <p className="text-muted-foreground mt-2">
                            Manage fluid/oil type categories (e.g. Engine Oil, Gear Oil, Diesel, Petrol).
                        </p>
                    </div>
                    <button
                        onClick={() => { resetForm(); setIsModalOpen(true); }}
                        className="btn-gradient px-6 py-3 rounded-xl font-medium flex items-center gap-2 shadow-lg hover:shadow-xl transition-all"
                    >
                        <Plus className="w-5 h-5" />
                        Add Oil Type
                    </button>
                </div>

                {/* Filter Bar */}
                <div className="mb-6 flex flex-wrap gap-3 items-center">
                    <div className="relative flex-1 min-w-[200px] max-w-md">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                        <input
                            type="text"
                            placeholder="Search by name or description..."
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            className="w-full pl-10 pr-4 py-2.5 bg-card border border-border rounded-xl text-foreground text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                        />
                    </div>
                    <select
                        value={statusFilter}
                        onChange={(e) => setStatusFilter(e.target.value)}
                        className="px-4 py-2.5 bg-card border border-border rounded-xl text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
                    >
                        <option value="ALL">All Status</option>
                        <option value="ACTIVE">Enabled</option>
                        <option value="INACTIVE">Disabled</option>
                    </select>
                </div>

                {isLoading ? (
                    <div className="flex flex-col items-center justify-center py-20 text-muted-foreground">
                        <div className="w-12 h-12 border-4 border-primary/20 border-t-primary rounded-full animate-spin mb-4"></div>
                        <p className="animate-pulse">Loading oil types...</p>
                    </div>
                ) : oilTypes.length === 0 ? (
                    <div className="text-center py-20 bg-black/5 dark:bg-white/5 rounded-2xl border border-dashed border-border">
                        <Droplets className="w-16 h-16 mx-auto text-muted-foreground mb-4 opacity-50" />
                        <h3 className="text-xl font-semibold text-foreground mb-2">No Oil Types Defined</h3>
                        <p className="text-muted-foreground mb-6">Create oil types to categorize your grades and products.</p>
                    </div>
                ) : (
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                        {filtered.map(oilType => (
                            <GlassCard key={oilType.id} className="relative group hover:shadow-xl transition-all border-none">
                                <div className="flex justify-between items-start mb-4">
                                    <div className="p-3 rounded-2xl bg-primary/10 text-primary shadow-inner">
                                        <Droplets className="w-8 h-8" />
                                    </div>
                                    <div className="flex gap-2 opacity-0 group-hover:opacity-100 transition-opacity">
                                        <button
                                            onClick={() => handleEdit(oilType)}
                                            className="p-2 rounded-lg bg-black/5 dark:bg-white/5 hover:bg-primary/10 text-muted-foreground hover:text-primary transition-colors"
                                        >
                                            <Edit2 className="w-4 h-4" />
                                        </button>
                                        <button
                                            onClick={() => handleDelete(oilType.id)}
                                            className="p-2 rounded-lg bg-black/5 dark:bg-white/5 hover:bg-red-500/10 text-muted-foreground hover:text-red-500 transition-colors"
                                        >
                                            <Trash2 className="w-4 h-4" />
                                        </button>
                                    </div>
                                </div>
                                <div className="flex items-center gap-2 mb-1">
                                    <h3 className="text-2xl font-black text-foreground">{oilType.name}</h3>
                                    <button onClick={() => handleToggleStatus(oilType.id)}>
                                        {oilType.active ? (
                                            <CheckCircle2 className="w-4 h-4 text-green-500 cursor-pointer hover:opacity-70" />
                                        ) : (
                                            <XCircle className="w-4 h-4 text-red-500 cursor-pointer hover:opacity-70" />
                                        )}
                                    </button>
                                </div>
                                <p className="text-muted-foreground text-sm line-clamp-2 min-h-[2.5rem] mb-4">
                                    {oilType.description || "No description provided."}
                                </p>
                                <div className="pt-4 border-t border-border/50 flex justify-between items-center">
                                    <span className="text-[10px] font-bold uppercase tracking-widest text-muted-foreground/60">Oil Type</span>
                                    <span className={`text-[10px] font-bold px-2 py-0.5 rounded-md ${
                                        oilType.active ? 'bg-green-500/10 text-green-500' : 'bg-red-500/10 text-red-500'
                                    }`}>
                                        {oilType.active ? 'ENABLED' : 'DISABLED'}
                                    </span>
                                </div>
                            </GlassCard>
                        ))}
                    </div>
                )}
            </div>

            <Modal
                isOpen={isModalOpen}
                onClose={() => { setIsModalOpen(false); resetForm(); }}
                title={editingId ? "Edit Oil Type" : "Add Oil Type"}
            >
                <form onSubmit={handleSave} className="space-y-6 pt-2">
                    <div className="space-y-4">
                        <div>
                            <label className="block text-sm font-bold text-foreground mb-1.5">
                                Oil Type Name <span className="text-red-500">*</span>
                            </label>
                            <input
                                type="text"
                                required
                                value={name}
                                onChange={(e) => setName(e.target.value)}
                                className="w-full bg-background border border-border rounded-2xl px-5 py-4 text-lg font-bold text-foreground focus:outline-none focus:ring-4 focus:ring-primary/20 transition-all placeholder:text-muted-foreground/30"
                                placeholder="e.g. Engine Oil, Gear Oil, Diesel, Petrol"
                            />
                        </div>

                        <div>
                            <label className="block text-sm font-bold text-foreground mb-1.5 flex items-center gap-2">
                                <FileText className="w-4 h-4 text-primary" /> Description
                            </label>
                            <textarea
                                value={description}
                                onChange={(e) => setDescription(e.target.value)}
                                className="w-full bg-background border border-border rounded-2xl px-5 py-4 text-foreground focus:outline-none focus:ring-4 focus:ring-primary/20 transition-all min-h-[100px] resize-none"
                                placeholder="Details about this oil type..."
                            />
                        </div>
                    </div>

                    <div className="flex justify-end gap-3 pt-6 border-t border-border">
                        <button
                            type="button"
                            onClick={() => { setIsModalOpen(false); resetForm(); }}
                            className="px-8 py-3 rounded-2xl font-bold text-muted-foreground hover:text-foreground hover:bg-black/5 dark:hover:bg-white/5 transition-all"
                        >
                            Cancel
                        </button>
                        <button
                            type="submit"
                            className="btn-gradient px-10 py-3 rounded-2xl font-bold shadow-xl hover:shadow-primary/20 transition-all"
                        >
                            {editingId ? "Update Oil Type" : "Save Oil Type"}
                        </button>
                    </div>
                </form>
            </Modal>
        </div>
    );
}
