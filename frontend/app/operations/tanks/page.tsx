"use client";

import { useEffect, useState, useMemo } from "react";
import { GlassCard } from "@/components/ui/glass-card";
import { Modal } from "@/components/ui/modal";
import { getTanks, getFuelProducts, createTank, updateTank, deleteTank, Tank, Product } from "@/lib/api/station";
import { Droplets, Plus, Edit2, Trash2, Search, Gauge, AlertTriangle } from "lucide-react";
import { ToggleSwitch } from "@/components/ui/toggle-switch";
import { useFormValidation, required, min } from "@/lib/validation";
import { FieldError, inputErrorClass, FormErrorBanner } from "@/components/ui/field-error";
import { PermissionGate } from "@/components/permission-gate";

export default function TanksPage() {
    const [tanks, setTanks] = useState<Tank[]>([]);
    const [products, setProducts] = useState<Product[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [editingTank, setEditingTank] = useState<Tank | null>(null);
    const [searchQuery, setSearchQuery] = useState("");
    const [statusFilter, setStatusFilter] = useState<string>("ALL");

    // Form State
    const [name, setName] = useState("");
    const [capacity, setCapacity] = useState("");
    const [availableStock, setAvailableStock] = useState("");
    const [productId, setProductId] = useState("");
    const [active, setActive] = useState(true);
    const [apiError, setApiError] = useState("");

    const validationRules = useMemo(() => ({
        name: [required("Tank name is required")],
        capacity: [required("Capacity is required"), min(1, "Capacity must be at least 1")],
        productId: [required("Fuel product is required")],
    }), []);
    const { errors, validate, clearError, clearAllErrors } = useFormValidation(validationRules);

    const loadData = async () => {
        setIsLoading(true);
        try {
            const [tData, pData] = await Promise.all([getTanks(), getFuelProducts()]);
            setTanks(tData);
            setProducts(pData);
        } catch (err) {
            console.error("Failed to load tanks", err);
        } finally {
            setIsLoading(false);
        }
    };

    useEffect(() => {
        loadData();
    }, []);

    const openModal = (tank?: Tank) => {
        if (tank) {
            setEditingTank(tank);
            setName(tank.name);
            setCapacity(tank.capacity.toString());
            setAvailableStock((tank.availableStock ?? 0).toString());
            setProductId(tank.product.id.toString());
            setActive(tank.active);
        } else {
            setEditingTank(null);
            setName("");
            setCapacity("");
            setAvailableStock("");
            setProductId("");
            setActive(true);
        }
        clearAllErrors();
        setApiError("");
        setIsModalOpen(true);
    };

    const handleSave = async (e: React.FormEvent) => {
        e.preventDefault();
        setApiError("");
        if (!validate({ name, capacity, productId })) return;
        try {
            const payload = {
                name,
                capacity: Number(capacity),
                availableStock: availableStock ? Number(availableStock) : 0,
                product: { id: Number(productId) },
                active
            };

            if (editingTank) {
                await updateTank(editingTank.id, payload as any);
            } else {
                await createTank(payload as any);
            }
            setIsModalOpen(false);
            loadData();
        } catch (err) {
            console.error("Failed to save tank", err);
            setApiError("Error saving tank details");
        }
    };

    const handleDelete = async (id: number) => {
        if (confirm("Are you sure you want to delete this tank? This may affect connected nozzles.")) {
            try {
                await deleteTank(id);
                loadData();
            } catch (err) {
                console.error("Failed to delete tank", err);
                alert("Cannot delete tank. It might be in use by nozzles.");
            }
        }
    };

    return (
        <div className="p-8 min-h-screen bg-background transition-colors duration-300">
            <div className="max-w-7xl mx-auto">
                <div className="flex justify-between items-center mb-8">
                    <div>
                        <h1 className="text-4xl font-bold text-foreground tracking-tight">
                            Tank <span className="text-gradient">Management</span>
                        </h1>
                        <p className="text-muted-foreground mt-2">
                            Manage underground fuel storage tanks and capacities.
                        </p>
                    </div>
                    <PermissionGate permission="STATION_MANAGE">
                        <button
                            onClick={() => openModal()}
                            className="btn-gradient px-6 py-3 rounded-xl font-medium flex items-center gap-2"
                        >
                            <Plus className="w-5 h-5" />
                            Add New Tank
                        </button>
                    </PermissionGate>
                </div>

                {/* Filter Bar */}
                <div className="mb-6 flex flex-wrap gap-3 items-center">
                    <div className="relative flex-1 min-w-[200px] max-w-md">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                        <input
                            type="text"
                            placeholder="Search by tank name or product..."
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
                        <option value="ACTIVE">Active</option>
                        <option value="INACTIVE">Inactive</option>
                    </select>
                </div>

                {isLoading ? (
                    <div className="text-center py-12 text-muted-foreground animate-pulse">Loading tanks...</div>
                ) : (
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                        {tanks.filter((t) => {
                            const q = searchQuery.toLowerCase();
                            const matchesSearch = !searchQuery || t.name?.toLowerCase().includes(q) || t.product.name?.toLowerCase().includes(q);
                            const matchesStatus = statusFilter === "ALL" || (t.active ? "ACTIVE" : "INACTIVE") === statusFilter;
                            return matchesSearch && matchesStatus;
                        }).map(tank => (
                            <GlassCard key={tank.id} className="relative group">
                                <div className="flex justify-between items-start mb-4">
                                    <div className="w-12 h-12 rounded-full bg-blue-500/20 flex items-center justify-center text-blue-500">
                                        <Droplets className="w-6 h-6" />
                                    </div>
                                    <span className={`px-2 py-1 rounded text-xs font-medium ${tank.active ? 'bg-green-500/10 text-green-500' : 'bg-red-500/10 text-red-500'}`}>
                                        {tank.active ? 'Active' : 'Inactive'}
                                    </span>
                                </div>
                                <h3 className="text-xl font-bold text-foreground mb-1">{tank.name}</h3>
                                <p className="text-muted-foreground mb-3">Capacity: {tank.capacity.toLocaleString()} L</p>

                                {/* Available Stock Widget */}
                                {(() => {
                                    const stock = tank.availableStock ?? 0;
                                    const pct = tank.capacity > 0 ? Math.min((stock / tank.capacity) * 100, 100) : 0;
                                    const barColor = pct <= 20 ? 'bg-red-500' : pct <= 50 ? 'bg-amber-500' : 'bg-green-500';
                                    const badgeColor = pct <= 20 ? 'text-red-600 bg-red-500/10' : pct <= 50 ? 'text-amber-600 bg-amber-500/10' : 'text-green-600 bg-green-500/10';
                                    return (
                                        <div className="mb-4">
                                            <div className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-xs font-semibold ${badgeColor} mb-2`}>
                                                <Gauge className="w-3.5 h-3.5" />
                                                Available: {stock.toLocaleString()} L ({pct.toFixed(0)}%)
                                            </div>
                                            <div className="w-full h-2 bg-black/10 dark:bg-white/10 rounded-full overflow-hidden">
                                                <div className={`h-full rounded-full transition-all ${barColor}`} style={{ width: `${pct}%` }} />
                                            </div>
                                        </div>
                                    );
                                })()}

                                <div className="bg-black/5 dark:bg-white/5 rounded-lg p-3 mb-4">
                                    <span className="text-xs text-muted-foreground uppercase tracking-wider block mb-1">Attached Product</span>
                                    <span className="font-medium text-foreground">{tank.product.name}</span>
                                </div>

                                <PermissionGate permission="STATION_MANAGE">
                                    <div className="flex justify-end gap-2 opacity-0 group-hover:opacity-100 transition-opacity">
                                        <button onClick={() => openModal(tank)} className="p-2 bg-blue-500/10 text-blue-500 rounded-lg hover:bg-blue-500/20 transition-colors">
                                            <Edit2 className="w-4 h-4" />
                                        </button>
                                        <button onClick={() => handleDelete(tank.id)} className="p-2 bg-red-500/10 text-red-500 rounded-lg hover:bg-red-500/20 transition-colors">
                                            <Trash2 className="w-4 h-4" />
                                        </button>
                                    </div>
                                </PermissionGate>
                            </GlassCard>
                        ))}
                    </div>
                )}
            </div>

            <Modal
                isOpen={isModalOpen}
                onClose={() => setIsModalOpen(false)}
                title={editingTank ? "Edit Tank" : "Add New Tank"}
            >
                <form onSubmit={handleSave} className="space-y-4">
                    <FormErrorBanner message={apiError} onDismiss={() => setApiError("")} />
                    <div>
                        <label className="block text-sm font-medium text-muted-foreground mb-1">Tank Name</label>
                        <input
                            type="text"
                            value={name}
                            onChange={(e) => { setName(e.target.value); clearError("name"); }}
                            className={`w-full bg-black/5 dark:bg-white/5 border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50 ${inputErrorClass(errors.name)}`}
                            placeholder="e.g. Tank 1"
                        />
                        <FieldError error={errors.name} />
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-muted-foreground mb-1">Capacity (Liters)</label>
                        <input
                            type="number"
                            value={capacity}
                            onChange={(e) => { setCapacity(e.target.value); clearError("capacity"); }}
                            className={`w-full bg-black/5 dark:bg-white/5 border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50 ${inputErrorClass(errors.capacity)}`}
                            placeholder="e.g. 10000"
                        />
                        <FieldError error={errors.capacity} />
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-muted-foreground mb-1">Available Stock (Liters)</label>
                        <input
                            type="number"
                            value={availableStock}
                            onChange={(e) => setAvailableStock(e.target.value)}
                            className="w-full bg-black/5 dark:bg-white/5 border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                            placeholder="e.g. 5000"
                        />
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-muted-foreground mb-1">Fuel Product</label>
                        <select
                            value={productId}
                            onChange={(e) => { setProductId(e.target.value); clearError("productId"); }}
                            className={`w-full bg-black/5 dark:bg-white/5 border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50 ${inputErrorClass(errors.productId)}`}
                        >
                            <option value="">Select a product...</option>
                            {products.map(p => (
                                <option key={p.id} value={p.id}>{p.name}</option>
                            ))}
                        </select>
                        <FieldError error={errors.productId} />
                    </div>
                    <div className="pt-2">
                        <ToggleSwitch
                            checked={active}
                            onChange={setActive}
                            label={active ? "Active" : "Inactive"}
                        />
                        {!active && (
                            <div className="mt-2 flex items-start gap-2 p-3 bg-amber-500/10 border border-amber-500/20 rounded-lg">
                                <AlertTriangle className="w-4 h-4 text-amber-500 mt-0.5 shrink-0" />
                                <p className="text-xs text-amber-500">
                                    Setting this tank as inactive will also deactivate all nozzles connected to it.
                                </p>
                            </div>
                        )}
                    </div>
                    <div className="flex justify-end gap-3 pt-4 border-t border-border mt-6">
                        <button
                            type="button"
                            onClick={() => setIsModalOpen(false)}
                            className="px-6 py-2 rounded-xl font-medium text-muted-foreground hover:bg-muted transition-colors"
                        >
                            Cancel
                        </button>
                        <button
                            type="submit"
                            className="btn-gradient px-8 py-2 rounded-xl font-medium"
                        >
                            {editingTank ? "Update Tank" : "Save Tank"}
                        </button>
                    </div>
                </form>
            </Modal>
        </div>
    );
}
