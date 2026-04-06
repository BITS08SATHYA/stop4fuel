"use client";

import { useEffect, useState, useMemo } from "react";
import { GlassCard } from "@/components/ui/glass-card";
import { Modal } from "@/components/ui/modal";
import { StyledSelect } from "@/components/ui/styled-select";
import { getTanks, getFuelProducts, createTank, updateTank, deleteTank, Tank, Product, checkStockAlerts } from "@/lib/api/station";
import { Droplets, Plus, Edit2, Trash2, Search, AlertTriangle } from "lucide-react";
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
    const [thresholdStock, setThresholdStock] = useState("");
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
            setThresholdStock(tank.thresholdStock ? tank.thresholdStock.toString() : "");
            setProductId(tank.product.id.toString());
            setActive(tank.active);
        } else {
            setEditingTank(null);
            setName("");
            setCapacity("");
            setAvailableStock("");
            setThresholdStock("");
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
        const cap = Number(capacity);
        const stock = availableStock ? Number(availableStock) : 0;
        const threshold = thresholdStock ? Number(thresholdStock) : null;
        if (stock < 0) { setApiError("Available stock cannot be negative"); return; }
        if (stock > cap) { setApiError("Available stock cannot exceed capacity"); return; }
        if (threshold != null && threshold < 0) { setApiError("Threshold cannot be negative"); return; }
        if (threshold != null && threshold > cap) { setApiError("Threshold cannot exceed capacity"); return; }
        try {
            const payload = {
                name,
                capacity: cap,
                availableStock: stock,
                thresholdStock: threshold,
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
            // Trigger stock alert check after tank update
            checkStockAlerts().catch(() => {});
        } catch (err) {
            console.error("Failed to save tank", err);
            setApiError("Error saving tank details");
        }
    };

    const handleDelete = async (id: number) => {
        if (confirm("Are you sure you want to deactivate this tank? Connected nozzles will also be deactivated.")) {
            try {
                await deleteTank(id);
                loadData();
            } catch (err) {
                console.error("Failed to deactivate tank", err);
                alert("Failed to deactivate tank.");
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
                    <PermissionGate permission="STATION_CREATE">
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
                    <StyledSelect
                        value={statusFilter}
                        onChange={(val) => setStatusFilter(val)}
                        options={[
                            { value: "ALL", label: "All Status" },
                            { value: "ACTIVE", label: "Active" },
                            { value: "INACTIVE", label: "Inactive" },
                        ]}
                        className="min-w-[140px]"
                    />
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
                        }).map(tank => {
                            const stock = tank.availableStock ?? 0;
                            const pct = tank.capacity > 0 ? Math.min((stock / tank.capacity) * 100, 100) : 0;
                            const thresholdPct = (tank.thresholdStock != null && tank.thresholdStock > 0 && tank.capacity > 0)
                                ? Math.min((tank.thresholdStock / tank.capacity) * 100, 100) : 0;
                            const isBelowThreshold = tank.thresholdStock != null && tank.thresholdStock > 0 && stock <= tank.thresholdStock;
                            const fillColor = isBelowThreshold ? '#ef4444' : pct <= 20 ? '#ef4444' : pct <= 50 ? '#f59e0b' : '#22c55e';
                            const fillColorDim = isBelowThreshold ? '#ef444440' : pct <= 20 ? '#ef444440' : pct <= 50 ? '#f59e0b40' : '#22c55e40';
                            const statusColor = isBelowThreshold ? 'text-red-500' : pct <= 20 ? 'text-red-500' : pct <= 50 ? 'text-amber-500' : 'text-green-500';

                            return (
                            <GlassCard key={tank.id} className="relative group">
                                {/* Header */}
                                <div className="flex justify-between items-start mb-3">
                                    <div>
                                        <h3 className="text-xl font-bold text-foreground">{tank.name}</h3>
                                        <p className="text-sm text-muted-foreground">{tank.product.name}</p>
                                    </div>
                                    <span className={`px-2 py-1 rounded text-xs font-medium ${tank.active ? 'bg-green-500/10 text-green-500' : 'bg-red-500/10 text-red-500'}`}>
                                        {tank.active ? 'Active' : 'Inactive'}
                                    </span>
                                </div>

                                {/* Tank Visualization */}
                                <div className="flex items-center gap-5 my-4">
                                    {/* SVG Tank */}
                                    <div className="relative flex-shrink-0" style={{ width: 80, height: 140 }}>
                                        <svg viewBox="0 0 80 140" width="80" height="140" className="drop-shadow-lg">
                                            <defs>
                                                <clipPath id={`tank-clip-${tank.id}`}>
                                                    {/* Tank body shape: rounded rect with elliptical top/bottom */}
                                                    <rect x="8" y="16" width="64" height="108" rx="8" />
                                                    <ellipse cx="40" cy="16" rx="32" ry="10" />
                                                    <ellipse cx="40" cy="124" rx="32" ry="10" />
                                                </clipPath>
                                                <linearGradient id={`liquid-grad-${tank.id}`} x1="0" y1="0" x2="0" y2="1">
                                                    <stop offset="0%" stopColor={fillColor} stopOpacity="0.9" />
                                                    <stop offset="100%" stopColor={fillColor} stopOpacity="0.6" />
                                                </linearGradient>
                                                <linearGradient id={`tank-body-${tank.id}`} x1="0" y1="0" x2="1" y2="0">
                                                    <stop offset="0%" stopColor="currentColor" stopOpacity="0.08" />
                                                    <stop offset="50%" stopColor="currentColor" stopOpacity="0.04" />
                                                    <stop offset="100%" stopColor="currentColor" stopOpacity="0.08" />
                                                </linearGradient>
                                            </defs>

                                            {/* Tank outline */}
                                            <rect x="8" y="16" width="64" height="108" rx="8"
                                                fill={`url(#tank-body-${tank.id})`}
                                                stroke="currentColor" strokeOpacity="0.15" strokeWidth="1.5" className="text-foreground" />
                                            <ellipse cx="40" cy="16" rx="32" ry="10"
                                                fill={`url(#tank-body-${tank.id})`}
                                                stroke="currentColor" strokeOpacity="0.15" strokeWidth="1.5" className="text-foreground" />
                                            <ellipse cx="40" cy="124" rx="32" ry="10"
                                                fill={`url(#tank-body-${tank.id})`}
                                                stroke="currentColor" strokeOpacity="0.15" strokeWidth="1.5" className="text-foreground" />

                                            {/* Liquid fill */}
                                            <g clipPath={`url(#tank-clip-${tank.id})`}>
                                                {/* Fill rect from bottom, animated */}
                                                <rect
                                                    x="8" width="64"
                                                    y={134 - (pct / 100) * 118}
                                                    height={(pct / 100) * 118}
                                                    fill={`url(#liquid-grad-${tank.id})`}
                                                >
                                                    <animate attributeName="y"
                                                        from="134" to={134 - (pct / 100) * 118}
                                                        dur="1.2s" fill="freeze" calcMode="spline"
                                                        keySplines="0.25 0.1 0.25 1" />
                                                    <animate attributeName="height"
                                                        from="0" to={(pct / 100) * 118}
                                                        dur="1.2s" fill="freeze" calcMode="spline"
                                                        keySplines="0.25 0.1 0.25 1" />
                                                </rect>

                                                {/* Wave effect on liquid surface */}
                                                {pct > 0 && (
                                                    <ellipse
                                                        cx="40"
                                                        cy={134 - (pct / 100) * 118}
                                                        rx="32" ry="4"
                                                        fill={fillColor} fillOpacity="0.3"
                                                    >
                                                        <animate attributeName="cy"
                                                            from="134" to={134 - (pct / 100) * 118}
                                                            dur="1.2s" fill="freeze" calcMode="spline"
                                                            keySplines="0.25 0.1 0.25 1" />
                                                        <animate attributeName="ry"
                                                            values="4;6;4" dur="3s" repeatCount="indefinite" />
                                                    </ellipse>
                                                )}
                                            </g>

                                            {/* Threshold marker */}
                                            {thresholdPct > 0 && (
                                                <g>
                                                    <line
                                                        x1="6" y1={134 - (thresholdPct / 100) * 118}
                                                        x2="74" y2={134 - (thresholdPct / 100) * 118}
                                                        stroke="#f59e0b" strokeWidth="1.5"
                                                        strokeDasharray="4 3" strokeOpacity="0.8"
                                                    />
                                                    {/* Small triangle markers on edges */}
                                                    <polygon
                                                        points={`2,${134 - (thresholdPct / 100) * 118 - 3} 8,${134 - (thresholdPct / 100) * 118} 2,${134 - (thresholdPct / 100) * 118 + 3}`}
                                                        fill="#f59e0b" fillOpacity="0.8"
                                                    />
                                                    <polygon
                                                        points={`78,${134 - (thresholdPct / 100) * 118 - 3} 72,${134 - (thresholdPct / 100) * 118} 78,${134 - (thresholdPct / 100) * 118 + 3}`}
                                                        fill="#f59e0b" fillOpacity="0.8"
                                                    />
                                                </g>
                                            )}

                                            {/* Percentage text in center */}
                                            <text x="40" y="72" textAnchor="middle" dominantBaseline="middle"
                                                className="fill-foreground" fontSize="16" fontWeight="bold" opacity="0.8">
                                                {pct.toFixed(0)}%
                                            </text>
                                        </svg>
                                    </div>

                                    {/* Stats beside tank */}
                                    <div className="flex-1 space-y-2.5">
                                        <div>
                                            <div className="text-[10px] uppercase tracking-wider text-muted-foreground">Capacity</div>
                                            <div className="text-sm font-semibold text-foreground">{tank.capacity.toLocaleString()} L</div>
                                        </div>
                                        <div>
                                            <div className="text-[10px] uppercase tracking-wider text-muted-foreground">Available</div>
                                            <div className={`text-sm font-bold ${statusColor}`}>{stock.toLocaleString()} L</div>
                                        </div>
                                        {thresholdPct > 0 && (
                                            <div>
                                                <div className="text-[10px] uppercase tracking-wider text-muted-foreground flex items-center gap-1">
                                                    <span className="inline-block w-2.5 h-0.5 bg-amber-500 rounded" /> Threshold
                                                </div>
                                                <div className="text-sm font-semibold text-amber-500">{tank.thresholdStock?.toLocaleString()} L</div>
                                            </div>
                                        )}
                                        {isBelowThreshold && (
                                            <div className="flex items-center gap-1.5 px-2 py-1.5 bg-red-500/10 border border-red-500/20 rounded-lg">
                                                <AlertTriangle className="w-3 h-3 text-red-500 shrink-0" />
                                                <span className="text-[10px] font-semibold text-red-500">LOW STOCK</span>
                                            </div>
                                        )}
                                    </div>
                                </div>

                                <PermissionGate permission="STATION_UPDATE">
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
                            );
                        })}
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
                        <label className="block text-sm font-medium text-muted-foreground mb-1">Threshold Stock (Liters)</label>
                        <input
                            type="number"
                            value={thresholdStock}
                            onChange={(e) => setThresholdStock(e.target.value)}
                            className="w-full bg-black/5 dark:bg-white/5 border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                            placeholder="e.g. 2000 — alerts when stock falls below this"
                        />
                        <p className="text-xs text-muted-foreground mt-1">
                            You will be notified when available stock drops to or below this level.
                        </p>
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-muted-foreground mb-1">Fuel Product</label>
                        <StyledSelect
                            value={productId}
                            onChange={(val) => { setProductId(val); clearError("productId"); }}
                            options={[
                                { value: "", label: "Select a product..." },
                                ...products.map(p => ({ value: String(p.id), label: p.name })),
                            ]}
                            placeholder="Select a product..."
                            className={`w-full ${inputErrorClass(errors.productId)}`}
                        />
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
