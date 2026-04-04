"use client";

import { useEffect, useState } from "react";
import { GlassCard } from "@/components/ui/glass-card";
import { Modal } from "@/components/ui/modal";
import { StyledSelect } from "@/components/ui/styled-select";
import {
    getGradeTypes,
    createGradeType,
    updateGradeType,
    deleteGradeType,
    toggleGradeStatus,
    getActiveOilTypes,
    GradeType,
    OilType
} from "@/lib/api/station";
import { Award, Plus, Edit2, Trash2, FileText, CheckCircle2, XCircle, Search } from "lucide-react";
import { PermissionGate } from "@/components/permission-gate";

export default function GradeTypesPage() {
    const [grades, setGrades] = useState<GradeType[]>([]);
    const [oilTypes, setOilTypes] = useState<OilType[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [editingId, setEditingId] = useState<number | null>(null);
    const [searchQuery, setSearchQuery] = useState("");
    const [oilTypeFilter, setOilTypeFilter] = useState<string>("ALL");
    const [statusFilter, setStatusFilter] = useState<string>("ALL");

    // Form State
    const [oilTypeId, setOilTypeId] = useState("");
    const [name, setName] = useState("");
    const [description, setDescription] = useState("");
    const [active, setActive] = useState(true);

    const loadData = async () => {
        setIsLoading(true);
        try {
            const [gradeData, oilTypeData] = await Promise.all([
                getGradeTypes(),
                getActiveOilTypes()
            ]);
            setGrades(gradeData);
            setOilTypes(oilTypeData);
        } catch (err) {
            console.error("Failed to load grades", err);
        } finally {
            setIsLoading(false);
        }
    };

    useEffect(() => {
        loadData();
    }, []);

    const handleEdit = (grade: GradeType) => {
        setEditingId(grade.id);
        setOilTypeId(grade.oilType?.id?.toString() || "");
        setName(grade.name);
        setDescription(grade.description || "");
        setActive(grade.active);
        setIsModalOpen(true);
    };

    const handleDelete = async (id: number) => {
        if (!confirm("Are you sure you want to delete this grade?")) return;
        try {
            await deleteGradeType(id);
            loadData();
        } catch (err) {
            console.error("Failed to delete grade", err);
            alert("Error deleting grade. It may be in use by products.");
        }
    };

    const handleToggleStatus = async (id: number) => {
        try {
            await toggleGradeStatus(id);
            loadData();
        } catch (err) {
            console.error("Failed to toggle status", err);
        }
    };

    const handleSave = async (e: React.FormEvent) => {
        e.preventDefault();
        try {
            const payload: any = {
                name,
                description,
                active,
                oilType: oilTypeId ? { id: Number(oilTypeId) } : null
            };
            if (editingId) {
                await updateGradeType(editingId, payload);
            } else {
                await createGradeType(payload);
            }
            setIsModalOpen(false);
            resetForm();
            loadData();
        } catch (err) {
            console.error("Failed to save grade", err);
            alert("Error saving grade details");
        }
    };

    const resetForm = () => {
        setEditingId(null);
        setOilTypeId("");
        setName("");
        setDescription("");
        setActive(true);
    };

    // Filter then group grades by oilType for display
    const filteredGrades = grades.filter((g) => {
        const q = searchQuery.toLowerCase();
        const oilTypeName = g.oilType?.name || "Uncategorized";
        const matchesSearch = !searchQuery || g.name?.toLowerCase().includes(q) || oilTypeName.toLowerCase().includes(q) || g.description?.toLowerCase().includes(q);
        const matchesOilType = oilTypeFilter === "ALL" || oilTypeName === oilTypeFilter;
        const matchesStatus = statusFilter === "ALL" || (g.active ? "ACTIVE" : "INACTIVE") === statusFilter;
        return matchesSearch && matchesOilType && matchesStatus;
    });

    const groupedGrades = filteredGrades.reduce<Record<string, GradeType[]>>((acc, g) => {
        const key = g.oilType?.name || "Uncategorized";
        if (!acc[key]) acc[key] = [];
        acc[key].push(g);
        return acc;
    }, {});

    // Get unique oil type names from existing grades for filter dropdown
    const uniqueOilTypeNames = [...new Set(grades.map(g => g.oilType?.name || "Uncategorized"))];

    return (
        <div className="p-8 min-h-screen bg-background">
            <div className="max-w-7xl mx-auto">
                <div className="flex justify-between items-center mb-8">
                    <div>
                        <h1 className="text-4xl font-bold text-foreground tracking-tight">
                            Product <span className="text-gradient">Grades</span>
                        </h1>
                        <p className="text-muted-foreground mt-2">
                            Define fluid types and their variant grades (e.g. Engine Oil: 20W-40, 20W-50).
                        </p>
                    </div>
                    <PermissionGate permission="PRODUCT_MANAGE">
                        <button
                            onClick={() => { resetForm(); setIsModalOpen(true); }}
                            className="btn-gradient px-6 py-3 rounded-xl font-medium flex items-center gap-2 shadow-lg hover:shadow-xl transition-all"
                        >
                            <Plus className="w-5 h-5" />
                            Define New Grade
                        </button>
                    </PermissionGate>
                </div>

                {/* Filter Bar */}
                <div className="mb-6 flex flex-wrap gap-3 items-center">
                    <div className="relative flex-1 min-w-[200px] max-w-md">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                        <input
                            type="text"
                            placeholder="Search by grade name, oil type..."
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            className="w-full pl-10 pr-4 py-2.5 bg-card border border-border rounded-xl text-foreground text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                        />
                    </div>
                    <StyledSelect
                        value={oilTypeFilter}
                        onChange={(val) => setOilTypeFilter(val)}
                        options={[
                            { value: "ALL", label: "All Oil Types" },
                            ...uniqueOilTypeNames.map((type) => ({ value: type, label: type })),
                        ]}
                        className="min-w-[160px]"
                    />
                    <StyledSelect
                        value={statusFilter}
                        onChange={(val) => setStatusFilter(val)}
                        options={[
                            { value: "ALL", label: "All Status" },
                            { value: "ACTIVE", label: "Enabled" },
                            { value: "INACTIVE", label: "Disabled" },
                        ]}
                        className="min-w-[140px]"
                    />
                </div>

                {isLoading ? (
                    <div className="flex flex-col items-center justify-center py-20 text-muted-foreground">
                        <div className="w-12 h-12 border-4 border-primary/20 border-t-primary rounded-full animate-spin mb-4"></div>
                        <p className="animate-pulse">Loading grade types...</p>
                    </div>
                ) : grades.length === 0 ? (
                    <div className="text-center py-20 bg-black/5 dark:bg-white/5 rounded-2xl border border-dashed border-border">
                        <Award className="w-16 h-16 mx-auto text-muted-foreground mb-4 opacity-50" />
                        <h3 className="text-xl font-semibold text-foreground mb-2">No Grades Defined</h3>
                        <p className="text-muted-foreground mb-6">Create grade types to categorize your product inventory better.</p>
                    </div>
                ) : (
                    <div className="space-y-8">
                        {Object.entries(groupedGrades).map(([type, typeGrades]) => (
                            <div key={type}>
                                <h2 className="text-lg font-bold text-foreground mb-4 flex items-center gap-2">
                                    <span className="w-2 h-2 rounded-full bg-primary"></span>
                                    {type}
                                    <span className="text-xs text-muted-foreground font-normal ml-1">({typeGrades.length} variants)</span>
                                </h2>
                                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                                    {typeGrades.map(grade => (
                                        <GlassCard key={grade.id} className="relative group hover:shadow-xl transition-all border-none">
                                            <div className="flex justify-between items-start mb-4">
                                                <div className="p-3 rounded-2xl bg-primary/10 text-primary shadow-inner">
                                                    <Award className="w-8 h-8" />
                                                </div>
                                                <PermissionGate permission="PRODUCT_MANAGE">
                                                    <div className="flex gap-2 opacity-0 group-hover:opacity-100 transition-opacity">
                                                        <button
                                                            onClick={() => handleEdit(grade)}
                                                            className="p-2 rounded-lg bg-black/5 dark:bg-white/5 hover:bg-primary/10 text-muted-foreground hover:text-primary transition-colors"
                                                        >
                                                            <Edit2 className="w-4 h-4" />
                                                        </button>
                                                        <button
                                                            onClick={() => handleDelete(grade.id)}
                                                            className="p-2 rounded-lg bg-black/5 dark:bg-white/5 hover:bg-red-500/10 text-muted-foreground hover:text-red-500 transition-colors"
                                                        >
                                                            <Trash2 className="w-4 h-4" />
                                                        </button>
                                                    </div>
                                                </PermissionGate>
                                            </div>
                                            <div className="flex items-center gap-2 mb-1">
                                                <h3 className="text-2xl font-black text-foreground">{grade.name}</h3>
                                                <PermissionGate permission="PRODUCT_MANAGE">
                                                    <button onClick={() => handleToggleStatus(grade.id)}>
                                                        {grade.active ? (
                                                            <CheckCircle2 className="w-4 h-4 text-green-500 cursor-pointer hover:opacity-70" />
                                                        ) : (
                                                            <XCircle className="w-4 h-4 text-red-500 cursor-pointer hover:opacity-70" />
                                                        )}
                                                    </button>
                                                </PermissionGate>
                                            </div>
                                            {grade.oilType && (
                                                <p className="text-xs text-primary/70 font-medium mb-2">{grade.oilType.name}</p>
                                            )}
                                            <p className="text-muted-foreground text-sm line-clamp-2 min-h-[2.5rem] mb-4">
                                                {grade.description || "No description provided for this grade type."}
                                            </p>
                                            <div className="pt-4 border-t border-border/50 flex justify-between items-center">
                                                <span className="text-[10px] font-bold uppercase tracking-widest text-muted-foreground/60">Grade Variant</span>
                                                <span className={`text-[10px] font-bold px-2 py-0.5 rounded-md ${
                                                    grade.active ? 'bg-green-500/10 text-green-500' : 'bg-red-500/10 text-red-500'
                                                }`}>
                                                    {grade.active ? 'ENABLED' : 'DISABLED'}
                                                </span>
                                            </div>
                                        </GlassCard>
                                    ))}
                                </div>
                            </div>
                        ))}
                    </div>
                )}
            </div>

            <Modal
                isOpen={isModalOpen}
                onClose={() => { setIsModalOpen(false); resetForm(); }}
                title={editingId ? "Edit Grade" : "Define Grade Type"}
            >
                <form onSubmit={handleSave} className="space-y-6 pt-2">
                    <div className="space-y-4">
                        <div>
                            <label className="block text-sm font-bold text-foreground mb-1.5">
                                Fluid / Oil Type <span className="text-red-500">*</span>
                            </label>
                            <StyledSelect
                                value={oilTypeId}
                                onChange={(val) => setOilTypeId(val)}
                                options={[
                                    { value: "", label: "Select Oil Type..." },
                                    ...oilTypes.map((ot) => ({ value: String(ot.id), label: ot.name })),
                                ]}
                                placeholder="Select Oil Type..."
                                className="w-full"
                            />
                            <p className="text-xs text-muted-foreground mt-1">Select an oil type (manage oil types from the Oil Types page)</p>
                        </div>

                        <div>
                            <label className="block text-sm font-bold text-foreground mb-1.5">
                                Grade Specification <span className="text-red-500">*</span>
                            </label>
                            <input
                                type="text"
                                required
                                value={name}
                                onChange={(e) => setName(e.target.value)}
                                className="w-full bg-background border border-border rounded-2xl px-5 py-4 text-lg font-bold text-foreground focus:outline-none focus:ring-4 focus:ring-primary/20 transition-all placeholder:text-muted-foreground/30"
                                placeholder="e.g. 20W-40, 20W-50, Premium 95"
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
                                placeholder="Details about this grade's usage or performance standards..."
                            />
                        </div>

                        <div className="flex items-center justify-between p-4 bg-background border border-border rounded-2xl">
                            <div>
                                <label className="block text-sm font-bold text-foreground">Status</label>
                                <p className="text-xs text-muted-foreground mt-0.5">
                                    {active ? "This grade is available for use" : "This grade is hidden from selections"}
                                </p>
                            </div>
                            <button
                                type="button"
                                onClick={() => setActive(!active)}
                                className={`relative inline-flex h-7 w-12 items-center rounded-full transition-colors duration-200 focus:outline-none focus:ring-2 focus:ring-primary/50 ${
                                    active ? 'bg-green-500' : 'bg-muted-foreground/30'
                                }`}
                            >
                                <span
                                    className={`inline-block h-5 w-5 rounded-full bg-white shadow-md transform transition-transform duration-200 ${
                                        active ? 'translate-x-6' : 'translate-x-1'
                                    }`}
                                />
                            </button>
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
                            {editingId ? "Update Grade" : "Save Grade"}
                        </button>
                    </div>
                </form>
            </Modal>
        </div>
    );
}
