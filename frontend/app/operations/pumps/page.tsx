"use client";

import { useEffect, useState, useMemo } from "react";
import { GlassCard } from "@/components/ui/glass-card";
import { Modal } from "@/components/ui/modal";
import { StyledSelect } from "@/components/ui/styled-select";
import { getPumps, createPump, updatePump, deletePump, Pump } from "@/lib/api/station";
import { Activity, Plus, Edit2, Trash2, Search, AlertTriangle } from "lucide-react";
import { ToggleSwitch } from "@/components/ui/toggle-switch";
import { useFormValidation, required } from "@/lib/validation";
import { FieldError, inputErrorClass, FormErrorBanner } from "@/components/ui/field-error";
import { PermissionGate } from "@/components/permission-gate";

export default function PumpsPage() {
    const [pumps, setPumps] = useState<Pump[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [editingPump, setEditingPump] = useState<Pump | null>(null);
    const [searchQuery, setSearchQuery] = useState("");
    const [statusFilter, setStatusFilter] = useState<string>("ALL");

    // Form State
    const [name, setName] = useState("");
    const [active, setActive] = useState(true);
    const [apiError, setApiError] = useState("");

    const validationRules = useMemo(() => ({
        name: [required("Pump name is required")],
    }), []);
    const { errors, validate, clearError, clearAllErrors } = useFormValidation(validationRules);

    const loadData = async () => {
        setIsLoading(true);
        try {
            const data = await getPumps();
            setPumps(data);
        } catch (err) {
            console.error("Failed to load pumps", err);
        } finally {
            setIsLoading(false);
        }
    };

    useEffect(() => {
        loadData();
    }, []);

    const openModal = (pump?: Pump) => {
        if (pump) {
            setEditingPump(pump);
            setName(pump.name);
            setActive(pump.active);
        } else {
            setEditingPump(null);
            setName("");
            setActive(true);
        }
        clearAllErrors();
        setApiError("");
        setIsModalOpen(true);
    };

    const handleSave = async (e: React.FormEvent) => {
        e.preventDefault();
        setApiError("");
        if (!validate({ name })) return;
        try {
            const payload = { name, active };

            if (editingPump) {
                await updatePump(editingPump.id, payload);
            } else {
                await createPump(payload);
            }
            setIsModalOpen(false);
            loadData();
        } catch (err) {
            console.error("Failed to save pump", err);
            setApiError("Error saving pump details");
        }
    };

    const handleDelete = async (id: number) => {
        if (confirm("Are you sure you want to deactivate this pump? Connected nozzles will also be deactivated.")) {
            try {
                await deletePump(id);
                loadData();
            } catch (err) {
                console.error("Failed to deactivate pump", err);
                alert("Failed to deactivate pump.");
            }
        }
    };

    return (
        <div className="p-8 min-h-screen bg-background transition-colors duration-300">
            <div className="max-w-7xl mx-auto">
                <div className="flex justify-between items-center mb-8">
                    <div>
                        <h1 className="text-4xl font-bold text-foreground tracking-tight">
                            Pump <span className="text-gradient">Management</span>
                        </h1>
                        <p className="text-muted-foreground mt-2">
                            Manage physical fuel dispenser pumps.
                        </p>
                    </div>
                    <PermissionGate permission="STATION_MANAGE">
                        <button
                            onClick={() => openModal()}
                            className="btn-gradient px-6 py-3 rounded-xl font-medium flex items-center gap-2"
                        >
                            <Plus className="w-5 h-5" />
                            Add New Pump
                        </button>
                    </PermissionGate>
                </div>

                {/* Filter Bar */}
                <div className="mb-6 flex flex-wrap gap-3 items-center">
                    <div className="relative flex-1 min-w-[200px] max-w-md">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                        <input
                            type="text"
                            placeholder="Search by pump name..."
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
                    <div className="text-center py-12 text-muted-foreground animate-pulse">Loading pumps...</div>
                ) : (
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
                        {pumps.filter((p) => {
                            const matchesSearch = !searchQuery || p.name?.toLowerCase().includes(searchQuery.toLowerCase());
                            const matchesStatus = statusFilter === "ALL" || (p.active ? "ACTIVE" : "INACTIVE") === statusFilter;
                            return matchesSearch && matchesStatus;
                        }).map(pump => (
                            <GlassCard key={pump.id} className="relative group hover:-translate-y-1 transition-transform">
                                <div className="flex justify-between items-start mb-6">
                                    <div className="w-12 h-12 rounded-full bg-orange-500/20 flex items-center justify-center text-orange-500">
                                        <Activity className="w-6 h-6" />
                                    </div>
                                    <span className={`px-2 py-1 rounded text-xs font-medium ${pump.active ? 'bg-green-500/10 text-green-500' : 'bg-red-500/10 text-red-500'}`}>
                                        {pump.active ? 'Active' : 'Inactive'}
                                    </span>
                                </div>
                                <h3 className="text-2xl font-bold text-foreground mb-4">{pump.name}</h3>
                                
                                <PermissionGate permission="STATION_MANAGE">
                                    <div className="flex justify-end gap-2 opacity-0 group-hover:opacity-100 transition-opacity">
                                        <button onClick={() => openModal(pump)} className="p-2 bg-orange-500/10 text-orange-500 rounded-lg hover:bg-orange-500/20 transition-colors">
                                            <Edit2 className="w-4 h-4" />
                                        </button>
                                        <button onClick={() => handleDelete(pump.id)} className="p-2 bg-red-500/10 text-red-500 rounded-lg hover:bg-red-500/20 transition-colors">
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
                title={editingPump ? "Edit Pump" : "Add New Pump"}
            >
                <form onSubmit={handleSave} className="space-y-4">
                    <FormErrorBanner message={apiError} onDismiss={() => setApiError("")} />
                    <div>
                        <label className="block text-sm font-medium text-muted-foreground mb-1">Pump Name / Identifier</label>
                        <input
                            type="text"
                            value={name}
                            onChange={(e) => { setName(e.target.value); clearError("name"); }}
                            className={`w-full bg-black/5 dark:bg-white/5 border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50 ${inputErrorClass(errors.name)}`}
                            placeholder="e.g. Pump 1"
                        />
                        <FieldError error={errors.name} />
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
                                    Setting this pump as inactive will also deactivate all nozzles connected to it.
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
                            {editingPump ? "Update Pump" : "Save Pump"}
                        </button>
                    </div>
                </form>
            </Modal>
        </div>
    );
}
