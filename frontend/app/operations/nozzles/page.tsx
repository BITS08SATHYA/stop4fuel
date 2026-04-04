"use client";

import { useEffect, useState, useMemo } from "react";
import { GlassCard } from "@/components/ui/glass-card";
import { Modal } from "@/components/ui/modal";
import { StyledSelect } from "@/components/ui/styled-select";
import { getNozzles, getTanks, getPumps, createNozzle, updateNozzle, deleteNozzle, Nozzle, Tank, Pump } from "@/lib/api/station";
import { Fuel, Plus, Edit2, Trash2, Search, Activity, Droplets } from "lucide-react";
import { ToggleSwitch } from "@/components/ui/toggle-switch";
import { useFormValidation, required } from "@/lib/validation";
import { FieldError, inputErrorClass, FormErrorBanner } from "@/components/ui/field-error";
import { TablePagination, useClientPagination } from "@/components/ui/table-pagination";
import { PermissionGate } from "@/components/permission-gate";

export default function NozzlesPage() {
    const [nozzles, setNozzles] = useState<Nozzle[]>([]);
    const [tanks, setTanks] = useState<Tank[]>([]);
    const [pumps, setPumps] = useState<Pump[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [searchQuery, setSearchQuery] = useState("");
    const [statusFilter, setStatusFilter] = useState<string>("ALL");
    const [pumpFilter, setPumpFilter] = useState<string>("");

    // Modal state
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [editingNozzle, setEditingNozzle] = useState<Nozzle | null>(null);

    // Form state
    const [nozzleName, setNozzleName] = useState("");
    const [tankId, setTankId] = useState("");
    const [pumpId, setPumpId] = useState("");
    const [active, setActive] = useState(true);
    const [apiError, setApiError] = useState("");

    const validationRules = useMemo(() => ({
        nozzleName: [required("Nozzle name is required")],
        pumpId: [required("Pump is required")],
        tankId: [required("Tank is required")],
    }), []);
    const { errors, validate, clearError, clearAllErrors } = useFormValidation(validationRules);

    const loadData = async () => {
        setIsLoading(true);
        try {
            const [nData, tData, pData] = await Promise.all([getNozzles(), getTanks(), getPumps()]);
            setNozzles(nData);
            setTanks(tData);
            setPumps(pData);
        } catch (err) {
            console.error("Failed to load data", err);
        } finally {
            setIsLoading(false);
        }
    };

    useEffect(() => {
        loadData();
    }, []);

    const openModal = (nozzle?: Nozzle) => {
        if (nozzle) {
            setEditingNozzle(nozzle);
            setNozzleName(nozzle.nozzleName);
            setTankId(nozzle.tank.id.toString());
            setPumpId(nozzle.pump.id.toString());
            setActive(nozzle.active);
        } else {
            setEditingNozzle(null);
            setNozzleName("");
            setTankId("");
            setPumpId("");
            setActive(true);
        }
        clearAllErrors();
        setApiError("");
        setIsModalOpen(true);
    };

    const handleSave = async (e: React.FormEvent) => {
        e.preventDefault();
        setApiError("");
        if (!validate({ nozzleName, pumpId, tankId })) return;
        try {
            const payload = {
                nozzleName,
                tank: { id: Number(tankId) },
                pump: { id: Number(pumpId) },
                active
            };

            if (editingNozzle) {
                await updateNozzle(editingNozzle.id, payload as any);
            } else {
                await createNozzle(payload as any);
            }
            setIsModalOpen(false);
            loadData();
        } catch (err) {
            console.error("Failed to save nozzle", err);
            setApiError("Error saving nozzle details");
        }
    };

    const handleDelete = async (id: number) => {
        if (confirm("Are you sure you want to deactivate this nozzle?")) {
            try {
                await deleteNozzle(id);
                loadData();
            } catch (err) {
                console.error("Failed to deactivate nozzle", err);
                setApiError("Failed to deactivate nozzle.");
            }
        }
    };

    const filtered = nozzles.filter((n) => {
        const q = searchQuery.toLowerCase();
        const matchesSearch = !searchQuery || n.nozzleName?.toLowerCase().includes(q) || n.pump.name?.toLowerCase().includes(q) || n.tank.name?.toLowerCase().includes(q) || n.tank.productName?.toLowerCase().includes(q);
        const matchesStatus = statusFilter === "ALL" || (n.active ? "ACTIVE" : "INACTIVE") === statusFilter;
        const matchesPump = !pumpFilter || String(n.pump.id) === pumpFilter;
        return matchesSearch && matchesStatus && matchesPump;
    });

    const { page, setPage, totalPages, totalElements, pageSize, paginatedData: pagedNozzles } = useClientPagination(filtered);

    return (
        <div className="p-6 h-screen overflow-hidden bg-background transition-colors duration-300">
            <div className="max-w-7xl mx-auto">
                <div className="flex justify-between items-center mb-8">
                    <div>
                        <h1 className="text-4xl font-bold text-foreground tracking-tight">
                            Nozzle <span className="text-gradient">Management</span>
                        </h1>
                        <p className="text-muted-foreground mt-2">
                            Manage fuel nozzles and their connections to tanks and pumps.
                        </p>
                    </div>
                    <PermissionGate permission="STATION_MANAGE">
                        <button
                            onClick={() => openModal()}
                            className="btn-gradient px-6 py-3 rounded-xl font-medium flex items-center gap-2 shadow-lg hover:shadow-xl transition-all"
                        >
                            <Plus className="w-5 h-5" />
                            Add New Nozzle
                        </button>
                    </PermissionGate>
                </div>

                {/* Filter Bar */}
                <div className="mb-6 flex flex-wrap gap-3 items-center">
                    <div className="relative flex-1 min-w-[200px] max-w-md">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                        <input
                            type="text"
                            placeholder="Search by nozzle, pump, tank, or product..."
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            className="w-full pl-10 pr-4 py-2.5 bg-card border border-border rounded-xl text-foreground text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                        />
                    </div>
                    <StyledSelect
                        value={pumpFilter}
                        onChange={(val) => setPumpFilter(val)}
                        options={[
                            { value: "", label: "All Pumps" },
                            ...pumps.map((p) => ({ value: String(p.id), label: p.name })),
                        ]}
                        placeholder="All Pumps"
                        className="min-w-[140px]"
                    />
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
                    <div className="flex flex-col items-center justify-center py-20 text-muted-foreground">
                        <div className="w-12 h-12 border-4 border-primary/20 border-t-primary rounded-full animate-spin mb-4"></div>
                        <p className="animate-pulse">Loading nozzles...</p>
                    </div>
                ) : nozzles.length === 0 ? (
                    <div className="text-center py-20 bg-black/5 dark:bg-white/5 rounded-2xl border border-dashed border-border">
                        <Fuel className="w-16 h-16 mx-auto text-muted-foreground mb-4 opacity-50" />
                        <h3 className="text-xl font-semibold text-foreground mb-2">No Nozzles Found</h3>
                        <p className="text-muted-foreground mb-6">Start by adding your first nozzle.</p>
                    </div>
                ) : (
                    <GlassCard className="overflow-hidden border-none p-0">
                        <div className="overflow-x-auto">
                            <table className="w-full text-left border-collapse">
                                <thead>
                                    <tr className="bg-white/5 border-b border-border/50">
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center w-16">#</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Nozzle</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Assigned Pump</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Source Tank</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Product</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center w-24">Status</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center w-32">Actions</th>
                                    </tr>
                                </thead>
                                <tbody className="divide-y divide-border/30">
                                    {pagedNozzles.map((nozzle, idx) => (
                                        <tr key={nozzle.id} className="hover:bg-white/5 transition-colors group">
                                            <td className="px-6 py-4 text-xs font-mono text-muted-foreground text-center">{page * pageSize + idx + 1}</td>
                                            <td className="px-6 py-4">
                                                <div className="flex items-center gap-3">
                                                    <div className="p-2.5 rounded-xl bg-green-500/10 text-green-500">
                                                        <Fuel className="w-5 h-5" />
                                                    </div>
                                                    <span className="text-base font-bold text-foreground">{nozzle.nozzleName}</span>
                                                </div>
                                            </td>
                                            <td className="px-6 py-4">
                                                <div className="flex items-center gap-2">
                                                    <Activity className="w-4 h-4 text-orange-500" />
                                                    <span className="text-sm text-foreground font-medium">{nozzle.pump.name}</span>
                                                </div>
                                            </td>
                                            <td className="px-6 py-4">
                                                <div className="flex items-center gap-2">
                                                    <Droplets className="w-4 h-4 text-blue-500" />
                                                    <span className="text-sm text-foreground font-medium">{nozzle.tank.name}</span>
                                                </div>
                                            </td>
                                            <td className="px-6 py-4">
                                                <span className="text-sm text-primary font-medium">{nozzle.tank.productName}</span>
                                            </td>
                                            <td className="px-6 py-4 text-center">
                                                <span className={`px-2 py-0.5 rounded-full text-[10px] font-bold uppercase ${
                                                    nozzle.active
                                                        ? 'bg-green-500/10 text-green-500 border border-green-500/20'
                                                        : 'bg-red-500/10 text-red-500 border border-red-500/20'
                                                }`}>
                                                    {nozzle.active ? 'Active' : 'Inactive'}
                                                </span>
                                            </td>
                                            <td className="px-6 py-4">
                                                <PermissionGate permission="STATION_MANAGE">
                                                    <div className="flex justify-center gap-2 opacity-100 md:opacity-0 group-hover:opacity-100 transition-opacity">
                                                        <button
                                                            onClick={() => openModal(nozzle)}
                                                            className="p-2 rounded-lg hover:bg-white/10 text-muted-foreground hover:text-foreground"
                                                        >
                                                            <Edit2 className="w-4 h-4" />
                                                        </button>
                                                        <button
                                                            onClick={() => handleDelete(nozzle.id)}
                                                            className="p-2 rounded-lg hover:bg-red-500/10 text-muted-foreground hover:text-red-500"
                                                        >
                                                            <Trash2 className="w-4 h-4" />
                                                        </button>
                                                    </div>
                                                </PermissionGate>
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                        <TablePagination
                            page={page}
                            totalPages={totalPages}
                            totalElements={totalElements}
                            pageSize={pageSize}
                            onPageChange={setPage}
                        />
                    </GlassCard>
                )}
            </div>

            <Modal
                isOpen={isModalOpen}
                onClose={() => setIsModalOpen(false)}
                title={editingNozzle ? "Edit Nozzle" : "Add New Nozzle"}
            >
                <form onSubmit={handleSave} className="space-y-4">
                    <FormErrorBanner message={apiError} onDismiss={() => setApiError("")} />
                    <div>
                        <label className="block text-sm font-medium text-foreground mb-1.5">
                            Nozzle Name <span className="text-red-500">*</span>
                        </label>
                        <input
                            type="text"
                            value={nozzleName}
                            onChange={(e) => { setNozzleName(e.target.value); clearError("nozzleName"); }}
                            className={`w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50 ${inputErrorClass(errors.nozzleName)}`}
                            placeholder="e.g. N-1"
                        />
                        <FieldError error={errors.nozzleName} />
                    </div>

                    <div className="grid grid-cols-2 gap-4">
                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5">
                                Assigned Pump <span className="text-red-500">*</span>
                            </label>
                            <StyledSelect
                                value={pumpId}
                                onChange={(val) => { setPumpId(val); clearError("pumpId"); }}
                                options={[
                                    { value: "", label: "Select a pump..." },
                                    ...pumps.map(p => ({ value: String(p.id), label: p.name })),
                                ]}
                                placeholder="Select a pump..."
                                className={`w-full ${inputErrorClass(errors.pumpId)}`}
                            />
                            <FieldError error={errors.pumpId} />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5">
                                Source Tank <span className="text-red-500">*</span>
                            </label>
                            <StyledSelect
                                value={tankId}
                                onChange={(val) => { setTankId(val); clearError("tankId"); }}
                                options={[
                                    { value: "", label: "Select a fuel tank..." },
                                    ...tanks.map(t => ({ value: String(t.id), label: `${t.name} (${t.product.name})` })),
                                ]}
                                placeholder="Select a fuel tank..."
                                className={`w-full ${inputErrorClass(errors.tankId)}`}
                            />
                            <FieldError error={errors.tankId} />
                        </div>
                    </div>

                    <div className="pt-2">
                        <ToggleSwitch
                            checked={active}
                            onChange={setActive}
                            label={active ? "Active" : "Inactive"}
                        />
                    </div>

                    <div className="flex justify-end gap-3 pt-4 border-t border-border mt-6">
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
                            {editingNozzle ? "Update Nozzle" : "Save Nozzle"}
                        </button>
                    </div>
                </form>
            </Modal>
        </div>
    );
}
