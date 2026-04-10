"use client";

import { useEffect, useState, useMemo } from "react";
import { GlassCard } from "@/components/ui/glass-card";
import { TablePagination, useClientPagination } from "@/components/ui/table-pagination";
import { Modal } from "@/components/ui/modal";
import { StyledSelect } from "@/components/ui/styled-select";
import {
    getSuppliers,
    createSupplier,
    updateSupplier,
    deleteSupplier,
    toggleSupplierStatus,
    Supplier
} from "@/lib/api/station";
import { Truck, Plus, Edit2, Trash2, Phone, Mail, User, Search } from "lucide-react";
import { useFormValidation, required, email, phone } from "@/lib/validation";
import { FieldError, inputErrorClass, FormErrorBanner } from "@/components/ui/field-error";
import { PermissionGate } from "@/components/permission-gate";
import { showToast } from "@/components/ui/toast";

export default function SuppliersPage() {
    const [suppliers, setSuppliers] = useState<Supplier[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [editingId, setEditingId] = useState<number | null>(null);
    const [searchQuery, setSearchQuery] = useState("");
    const [statusFilter, setStatusFilter] = useState<string>("ALL");

    const filteredSuppliers = useMemo(() => suppliers.filter((s) => {
        const q = searchQuery.toLowerCase();
        const matchesSearch = !searchQuery || s.name?.toLowerCase().includes(q) || s.contactPerson?.toLowerCase().includes(q) || s.phone?.includes(q);
        const matchesStatus = statusFilter === "ALL" || (s.active ? "ACTIVE" : "INACTIVE") === statusFilter;
        return matchesSearch && matchesStatus;
    }), [suppliers, searchQuery, statusFilter]);

    const { page, setPage, totalPages, totalElements, pageSize, paginatedData: pagedSuppliers } = useClientPagination(filteredSuppliers);

    // Form State
    const [name, setName] = useState("");
    const [contactPerson, setContactPerson] = useState("");
    const [phoneNum, setPhoneNum] = useState("");
    const [emailAddr, setEmailAddr] = useState("");
    const [active, setActive] = useState(true);
    const [apiError, setApiError] = useState("");

    const validationRules = useMemo(() => ({
        name: [required("Supplier name is required")],
        phoneNum: [phone("Invalid phone number")],
        emailAddr: [email("Invalid email address")],
    }), []);
    const { errors, validate, clearError, clearAllErrors } = useFormValidation(validationRules);

    const loadData = async () => {
        setIsLoading(true);
        try {
            const data = await getSuppliers();
            setSuppliers(data);
        } catch (err) {
            console.error("Failed to load suppliers", err);
        } finally {
            setIsLoading(false);
        }
    };

    useEffect(() => {
        loadData();
    }, []);

    const handleEdit = (s: Supplier) => {
        setEditingId(s.id);
        setName(s.name);
        setContactPerson(s.contactPerson || "");
        setPhoneNum(s.phone || "");
        setEmailAddr(s.email || "");
        setActive(s.active);
        clearAllErrors();
        setApiError("");
        setIsModalOpen(true);
    };

    const handleDelete = async (id: number) => {
        if (!confirm("Are you sure you want to delete this supplier?")) return;
        try {
            await deleteSupplier(id);
            loadData();
        } catch (err) {
            console.error("Failed to delete supplier", err);
            showToast.error("Error deleting supplier");
        }
    };

    const handleToggleStatus = async (id: number) => {
        try {
            await toggleSupplierStatus(id);
            loadData();
        } catch (err) {
            console.error("Failed to toggle status", err);
        }
    };

    const handleSave = async (e: React.FormEvent) => {
        e.preventDefault();
        setApiError("");
        if (!validate({ name, phoneNum, emailAddr })) return;
        try {
            const payload = { name, contactPerson, phone: phoneNum, email: emailAddr, active };
            if (editingId) {
                await updateSupplier(editingId, payload as any);
            } else {
                await createSupplier(payload as any);
            }
            setIsModalOpen(false);
            resetForm();
            loadData();
        } catch (err) {
            console.error("Failed to save supplier", err);
            setApiError("Error saving supplier details");
        }
    };

    const resetForm = () => {
        setEditingId(null);
        setName("");
        setContactPerson("");
        setPhoneNum("");
        setEmailAddr("");
        setActive(true);
        clearAllErrors();
        setApiError("");
    };

    return (
        <div className="p-6 h-screen overflow-hidden bg-background">
            <div className="max-w-7xl mx-auto">
                <div className="flex justify-between items-center mb-8">
                    <div>
                        <h1 className="text-4xl font-bold text-foreground tracking-tight">
                            Lubricant <span className="text-gradient">Suppliers</span>
                        </h1>
                        <p className="text-muted-foreground mt-2">
                            Manage entities that supply oil, lubricants, and other retail products.
                        </p>
                    </div>
                    <PermissionGate permission="STATION_CREATE">
                        <button
                            onClick={() => { resetForm(); setIsModalOpen(true); }}
                            className="btn-gradient px-6 py-3 rounded-xl font-medium flex items-center gap-2 shadow-lg hover:shadow-xl transition-all"
                        >
                            <Plus className="w-5 h-5" />
                            Add New Supplier
                        </button>
                    </PermissionGate>
                </div>

                {isLoading ? (
                    <div className="flex flex-col items-center justify-center py-20 text-muted-foreground">
                        <div className="w-12 h-12 border-4 border-primary/20 border-t-primary rounded-full animate-spin mb-4"></div>
                        <p className="animate-pulse">Loading suppliers...</p>
                    </div>
                ) : suppliers.length === 0 ? (
                    <div className="text-center py-20 bg-black/5 dark:bg-white/5 rounded-2xl border border-dashed border-border">
                        <Truck className="w-16 h-16 mx-auto text-muted-foreground mb-4 opacity-50" />
                        <h3 className="text-xl font-semibold text-foreground mb-2">No Suppliers Found</h3>
                        <p className="text-muted-foreground mb-6">Start by adding your first product supplier.</p>
                    </div>
                ) : (
                    <>
                    {/* Filter Bar */}
                    <div className="mb-6 flex flex-wrap gap-3 items-center">
                        <div className="relative flex-1 min-w-[200px] max-w-md">
                            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                            <input
                                type="text"
                                placeholder="Search by name, contact person, phone..."
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

                    <GlassCard className="overflow-hidden border-none p-0">
                        <div className="overflow-x-auto">
                            <table className="w-full text-left border-collapse">
                                <thead>
                                    <tr className="bg-white/5 border-b border-border/50">
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center w-16">#</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Supplier Information</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Contact Details</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center w-24">Status</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center w-32">Actions</th>
                                    </tr>
                                </thead>
                                <tbody className="divide-y divide-border/30">
                                    {pagedSuppliers.map((s, idx) => (
                                        <tr key={s.id} className="hover:bg-white/5 transition-colors group">
                                            <td className="px-6 py-4 text-xs font-mono text-muted-foreground text-center">{page * pageSize + idx + 1}</td>
                                            <td className="px-6 py-4">
                                                <div className="flex items-center gap-3">
                                                    <div className="p-2.5 rounded-xl bg-primary/10 text-primary">
                                                        <Truck className="w-5 h-5" />
                                                    </div>
                                                    <div>
                                                        <div className="text-base font-bold text-foreground leading-tight">{s.name}</div>
                                                        <div className="text-xs text-muted-foreground flex items-center gap-1 mt-1">
                                                            <User className="w-3 h-3" />
                                                            {s.contactPerson || 'No contact person'}
                                                        </div>
                                                    </div>
                                                </div>
                                            </td>
                                            <td className="px-6 py-4">
                                                <div className="space-y-1">
                                                    {s.phone && (
                                                        <div className="text-xs text-foreground flex items-center gap-2">
                                                            <Phone className="w-3 h-3 text-muted-foreground" />
                                                            {s.phone}
                                                        </div>
                                                    )}
                                                    {s.email && (
                                                        <div className="text-xs text-foreground flex items-center gap-2">
                                                            <Mail className="w-3 h-3 text-muted-foreground" />
                                                            {s.email}
                                                        </div>
                                                    )}
                                                </div>
                                            </td>
                                            <td className="px-6 py-4 text-center">
                                                <PermissionGate permission="STATION_UPDATE">
                                                    <button onClick={() => handleToggleStatus(s.id)}>
                                                        <span className={`px-2 py-0.5 rounded-full text-[10px] font-bold uppercase cursor-pointer hover:opacity-80 transition-opacity ${
                                                            s.active ? 'bg-green-500/10 text-green-500 border border-green-500/20' : 'bg-red-500/10 text-red-500 border border-red-500/20'
                                                        }`}>
                                                            {s.active ? 'Active' : 'Inactive'}
                                                        </span>
                                                    </button>
                                                </PermissionGate>
                                            </td>
                                            <td className="px-6 py-4">
                                                <PermissionGate permission="STATION_UPDATE">
                                                    <div className="flex justify-center gap-2 opacity-100 md:opacity-0 group-hover:opacity-100 transition-opacity">
                                                        <button
                                                            onClick={() => handleEdit(s)}
                                                            className="p-2 rounded-lg hover:bg-white/10 text-muted-foreground hover:text-foreground"
                                                        >
                                                            <Edit2 className="w-4 h-4" />
                                                        </button>
                                                        <button
                                                            onClick={() => handleDelete(s.id)}
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
                    </>
                )}
            </div>

            <Modal
                isOpen={isModalOpen}
                onClose={() => { setIsModalOpen(false); resetForm(); }}
                title={editingId ? "Edit Supplier" : "Add New Supplier"}
            >
                <form onSubmit={handleSave} className="space-y-4">
                    <FormErrorBanner message={apiError} onDismiss={() => setApiError("")} />
                    <div className="space-y-4">
                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5 flex items-center gap-2 font-bold tracking-tight">
                                Supplier Name <span className="text-red-500">*</span>
                            </label>
                            <input
                                type="text"
                                value={name}
                                onChange={(e) => { setName(e.target.value); clearError("name"); }}
                                className={`w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50 transition-all shadow-sm ${inputErrorClass(errors.name)}`}
                                placeholder="e.g. Acme Petroleum Ltd."
                            />
                            <FieldError error={errors.name} />
                        </div>

                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                            <div>
                                <label className="block text-sm font-medium text-foreground mb-1.5">Contact Person</label>
                                <input
                                    type="text"
                                    value={contactPerson}
                                    onChange={(e) => setContactPerson(e.target.value)}
                                    className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground"
                                    placeholder="Enter name"
                                />
                            </div>
                            <div>
                                <label className="block text-sm font-medium text-foreground mb-1.5">Phone Number</label>
                                <input
                                    type="tel"
                                    value={phoneNum}
                                    onChange={(e) => { setPhoneNum(e.target.value); clearError("phoneNum"); }}
                                    className={`w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground ${inputErrorClass(errors.phoneNum)}`}
                                    placeholder="+91 00000 00000"
                                />
                                <FieldError error={errors.phoneNum} />
                            </div>
                        </div>

                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5 font-bold tracking-tight">Email Address</label>
                            <input
                                type="text"
                                value={emailAddr}
                                onChange={(e) => { setEmailAddr(e.target.value); clearError("emailAddr"); }}
                                className={`w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground ${inputErrorClass(errors.emailAddr)}`}
                                placeholder="order@supplier.com"
                            />
                            <FieldError error={errors.emailAddr} />
                        </div>
                    </div>

                    <div className="flex justify-end gap-3 pt-6 border-t border-border mt-6">
                        <button
                            type="button"
                            onClick={() => { setIsModalOpen(false); resetForm(); }}
                            className="px-6 py-2.5 rounded-xl font-medium text-foreground bg-black/5 dark:bg-white/5 hover:bg-black/10 transition-colors"
                        >
                            Cancel
                        </button>
                        <button
                            type="submit"
                            className="btn-gradient px-8 py-2.5 rounded-xl font-medium shadow-lg hover:shadow-xl transition-all"
                        >
                            {editingId ? "Update Supplier" : "Save Supplier"}
                        </button>
                    </div>
                </form>
            </Modal>
        </div>
    );
}
