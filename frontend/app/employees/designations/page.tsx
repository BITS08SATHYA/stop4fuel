"use client";

import { useEffect, useState } from "react";
import { GlassCard } from "@/components/ui/glass-card";
import { Modal } from "@/components/ui/modal";
import { StyledSelect } from "@/components/ui/styled-select";
import { fetchWithAuth } from "@/lib/api/fetch-with-auth";
import { BadgeCheck, ShieldCheck, Plus, Edit2, Trash2, Search, FileText, Lock, UserCog } from "lucide-react";
import { PermissionGate } from "@/components/permission-gate";
import { showToast } from "@/components/ui/toast";

const getApiBaseUrl = () => {
    if (typeof window !== "undefined") {
        return process.env.NEXT_PUBLIC_API_URL || `${window.location.protocol}//${window.location.hostname}:8080/api`;
    }
    return process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api";
};

interface Designation {
    id: number;
    name: string;
    defaultRole: string | null;
    description: string | null;
}

interface RoleItem {
    id: number;
    roleType: string;
}

// Seeded roles the backend refuses to rename/delete
const PROTECTED_ROLES = new Set(["CUSTOMER", "EMPLOYEE", "DEALER", "OWNER", "ADMIN", "CASHIER"]);

async function readErrorMessage(res: Response, fallback: string): Promise<string> {
    try {
        const body = await res.json();
        return body?.error || body?.message || fallback;
    } catch {
        return fallback;
    }
}

export default function DesignationsPage() {
    const [activeTab, setActiveTab] = useState<"designations" | "roles">("designations");
    const [designations, setDesignations] = useState<Designation[]>([]);
    const [roles, setRoles] = useState<RoleItem[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [searchQuery, setSearchQuery] = useState("");

    // Designation modal state
    const [isDesignationModalOpen, setIsDesignationModalOpen] = useState(false);
    const [editingDesignationId, setEditingDesignationId] = useState<number | null>(null);
    const [designationName, setDesignationName] = useState("");
    const [defaultRole, setDefaultRole] = useState("");
    const [description, setDescription] = useState("");

    // Role modal state
    const [isRoleModalOpen, setIsRoleModalOpen] = useState(false);
    const [editingRoleId, setEditingRoleId] = useState<number | null>(null);
    const [roleName, setRoleName] = useState("");

    const [isSaving, setIsSaving] = useState(false);

    const loadData = async () => {
        setIsLoading(true);
        try {
            const [desRes, rolesRes] = await Promise.all([
                fetchWithAuth(`${getApiBaseUrl()}/designations`),
                fetchWithAuth(`${getApiBaseUrl()}/roles`),
            ]);
            if (desRes.ok) setDesignations(await desRes.json());
            if (rolesRes.ok) setRoles(await rolesRes.json());
        } catch (err) {
            console.error("Failed to load designations/roles", err);
            showToast.error("Failed to load designations and roles");
        } finally {
            setIsLoading(false);
        }
    };

    useEffect(() => {
        loadData();
    }, []);

    // ---------- Designations ----------

    const resetDesignationForm = () => {
        setEditingDesignationId(null);
        setDesignationName("");
        setDefaultRole("");
        setDescription("");
    };

    const handleEditDesignation = (d: Designation) => {
        setEditingDesignationId(d.id);
        setDesignationName(d.name);
        setDefaultRole(d.defaultRole || "");
        setDescription(d.description || "");
        setIsDesignationModalOpen(true);
    };

    const handleSaveDesignation = async (e: React.FormEvent) => {
        e.preventDefault();
        setIsSaving(true);
        try {
            const payload = {
                name: designationName.trim(),
                defaultRole: defaultRole || null,
                description: description.trim() || null,
            };
            const url = editingDesignationId
                ? `${getApiBaseUrl()}/designations/${editingDesignationId}`
                : `${getApiBaseUrl()}/designations`;
            const res = await fetchWithAuth(url, {
                method: editingDesignationId ? "PUT" : "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(payload),
            });
            if (!res.ok) {
                showToast.error(await readErrorMessage(res, "Failed to save designation"));
                return;
            }
            showToast.success(editingDesignationId ? "Designation updated" : "Designation created");
            setIsDesignationModalOpen(false);
            resetDesignationForm();
            loadData();
        } catch (err) {
            console.error("Failed to save designation", err);
            showToast.error("Failed to save designation");
        } finally {
            setIsSaving(false);
        }
    };

    const handleDeleteDesignation = async (d: Designation) => {
        if (!confirm(`Delete designation "${d.name}"?`)) return;
        try {
            const res = await fetchWithAuth(`${getApiBaseUrl()}/designations/${d.id}`, { method: "DELETE" });
            if (!res.ok) {
                showToast.error(await readErrorMessage(res, "Cannot delete — designation may be assigned to employees"));
                return;
            }
            showToast.success("Designation deleted");
            loadData();
        } catch (err) {
            console.error("Failed to delete designation", err);
            showToast.error("Cannot delete — designation may be assigned to employees");
        }
    };

    // ---------- Roles ----------

    const resetRoleForm = () => {
        setEditingRoleId(null);
        setRoleName("");
    };

    const handleEditRole = (r: RoleItem) => {
        setEditingRoleId(r.id);
        setRoleName(r.roleType);
        setIsRoleModalOpen(true);
    };

    const handleSaveRole = async (e: React.FormEvent) => {
        e.preventDefault();
        setIsSaving(true);
        try {
            const url = editingRoleId
                ? `${getApiBaseUrl()}/roles/${editingRoleId}`
                : `${getApiBaseUrl()}/roles`;
            const res = await fetchWithAuth(url, {
                method: editingRoleId ? "PUT" : "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ roleType: roleName.trim() }),
            });
            if (!res.ok) {
                showToast.error(await readErrorMessage(res, "Failed to save role"));
                return;
            }
            showToast.success(editingRoleId ? "Role updated" : "Role created");
            setIsRoleModalOpen(false);
            resetRoleForm();
            loadData();
        } catch (err) {
            console.error("Failed to save role", err);
            showToast.error("Failed to save role");
        } finally {
            setIsSaving(false);
        }
    };

    const handleDeleteRole = async (r: RoleItem) => {
        if (!confirm(`Delete role "${r.roleType}"?`)) return;
        try {
            const res = await fetchWithAuth(`${getApiBaseUrl()}/roles/${r.id}`, { method: "DELETE" });
            if (!res.ok) {
                showToast.error(await readErrorMessage(res, "Cannot delete — role may be in use"));
                return;
            }
            showToast.success("Role deleted");
            loadData();
        } catch (err) {
            console.error("Failed to delete role", err);
            showToast.error("Cannot delete — role may be in use");
        }
    };

    // ---------- Render ----------

    const q = searchQuery.toLowerCase();
    const filteredDesignations = designations.filter(
        (d) => !q || d.name?.toLowerCase().includes(q) || d.defaultRole?.toLowerCase().includes(q) || d.description?.toLowerCase().includes(q)
    );
    const filteredRoles = roles.filter((r) => !q || r.roleType?.toLowerCase().includes(q));

    const roleOptions = [
        { value: "", label: "None — defaults to EMPLOYEE" },
        ...roles.map((r) => ({ value: r.roleType, label: r.roleType })),
    ];

    return (
        <div className="p-4 sm:p-6 lg:p-8 min-h-screen bg-background">
            <div className="max-w-7xl mx-auto">
                <div className="flex flex-wrap justify-between items-center gap-4 mb-8">
                    <div>
                        <h1 className="text-4xl font-bold text-foreground tracking-tight">
                            Designations <span className="text-gradient">&amp; Roles</span>
                        </h1>
                        <p className="text-muted-foreground mt-2">
                            Manage employee designations and user roles. A designation&apos;s default role decides the app access an employee gets.
                        </p>
                    </div>
                    <PermissionGate permission="EMPLOYEE_CREATE">
                        {activeTab === "designations" ? (
                            <button
                                onClick={() => { resetDesignationForm(); setIsDesignationModalOpen(true); }}
                                className="btn-gradient px-6 py-3 rounded-xl font-medium flex items-center gap-2 shadow-lg hover:shadow-xl transition-all"
                            >
                                <Plus className="w-5 h-5" />
                                Add Designation
                            </button>
                        ) : (
                            <button
                                onClick={() => { resetRoleForm(); setIsRoleModalOpen(true); }}
                                className="btn-gradient px-6 py-3 rounded-xl font-medium flex items-center gap-2 shadow-lg hover:shadow-xl transition-all"
                            >
                                <Plus className="w-5 h-5" />
                                Add Role
                            </button>
                        )}
                    </PermissionGate>
                </div>

                {/* Tabs + Search */}
                <div className="mb-6 flex flex-wrap gap-3 items-center">
                    <div className="flex bg-card border border-border rounded-xl p-1">
                        <button
                            onClick={() => setActiveTab("designations")}
                            className={`px-5 py-2 rounded-lg text-sm font-semibold transition-all flex items-center gap-2 ${
                                activeTab === "designations"
                                    ? "bg-primary text-primary-foreground shadow"
                                    : "text-muted-foreground hover:text-foreground"
                            }`}
                        >
                            <BadgeCheck className="w-4 h-4" />
                            Designations ({designations.length})
                        </button>
                        <button
                            onClick={() => setActiveTab("roles")}
                            className={`px-5 py-2 rounded-lg text-sm font-semibold transition-all flex items-center gap-2 ${
                                activeTab === "roles"
                                    ? "bg-primary text-primary-foreground shadow"
                                    : "text-muted-foreground hover:text-foreground"
                            }`}
                        >
                            <ShieldCheck className="w-4 h-4" />
                            Roles ({roles.length})
                        </button>
                    </div>
                    <div className="relative flex-1 min-w-[200px] max-w-md">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                        <input
                            type="text"
                            placeholder={activeTab === "designations" ? "Search designations..." : "Search roles..."}
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            className="w-full pl-10 pr-4 py-2.5 bg-card border border-border rounded-xl text-foreground text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                        />
                    </div>
                </div>

                {isLoading ? (
                    <div className="flex flex-col items-center justify-center py-20 text-muted-foreground">
                        <div className="w-12 h-12 border-4 border-primary/20 border-t-primary rounded-full animate-spin mb-4"></div>
                        <p className="animate-pulse">Loading...</p>
                    </div>
                ) : activeTab === "designations" ? (
                    filteredDesignations.length === 0 ? (
                        <div className="text-center py-20 bg-black/5 dark:bg-white/5 rounded-2xl border border-dashed border-border">
                            <BadgeCheck className="w-16 h-16 mx-auto text-muted-foreground mb-4 opacity-50" />
                            <h3 className="text-xl font-semibold text-foreground mb-2">No Designations Found</h3>
                            <p className="text-muted-foreground mb-6">Create designations like Manager, Cashier, or Attendant to assign to employees.</p>
                        </div>
                    ) : (
                        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                            {filteredDesignations.map((d) => (
                                <GlassCard key={d.id} className="relative group hover:shadow-xl transition-all border-none">
                                    <div className="flex justify-between items-start mb-4">
                                        <div className="p-3 rounded-2xl bg-primary/10 text-primary shadow-inner">
                                            <UserCog className="w-8 h-8" />
                                        </div>
                                        <div className="flex gap-2 opacity-0 group-hover:opacity-100 transition-opacity">
                                            <PermissionGate permission="EMPLOYEE_UPDATE">
                                                <button
                                                    onClick={() => handleEditDesignation(d)}
                                                    className="p-2 rounded-lg bg-black/5 dark:bg-white/5 hover:bg-primary/10 text-muted-foreground hover:text-primary transition-colors"
                                                >
                                                    <Edit2 className="w-4 h-4" />
                                                </button>
                                            </PermissionGate>
                                            <PermissionGate permission="EMPLOYEE_DELETE">
                                                <button
                                                    onClick={() => handleDeleteDesignation(d)}
                                                    className="p-2 rounded-lg bg-black/5 dark:bg-white/5 hover:bg-red-500/10 text-muted-foreground hover:text-red-500 transition-colors"
                                                >
                                                    <Trash2 className="w-4 h-4" />
                                                </button>
                                            </PermissionGate>
                                        </div>
                                    </div>
                                    <h3 className="text-2xl font-black text-foreground mb-1">{d.name}</h3>
                                    <p className="text-muted-foreground text-sm line-clamp-2 min-h-[2.5rem] mb-4">
                                        {d.description || "No description provided."}
                                    </p>
                                    <div className="pt-4 border-t border-border/50 flex justify-between items-center">
                                        <span className="text-[10px] font-bold uppercase tracking-widest text-muted-foreground/60">Default Role</span>
                                        <span className="text-[10px] font-bold px-2 py-0.5 rounded-md bg-primary/10 text-primary">
                                            {d.defaultRole || "EMPLOYEE"}
                                        </span>
                                    </div>
                                </GlassCard>
                            ))}
                        </div>
                    )
                ) : filteredRoles.length === 0 ? (
                    <div className="text-center py-20 bg-black/5 dark:bg-white/5 rounded-2xl border border-dashed border-border">
                        <ShieldCheck className="w-16 h-16 mx-auto text-muted-foreground mb-4 opacity-50" />
                        <h3 className="text-xl font-semibold text-foreground mb-2">No Roles Found</h3>
                        <p className="text-muted-foreground mb-6">Create roles to classify user accounts.</p>
                    </div>
                ) : (
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
                        {filteredRoles.map((r) => {
                            const isProtected = PROTECTED_ROLES.has(r.roleType);
                            return (
                                <GlassCard key={r.id} className="relative group hover:shadow-xl transition-all border-none">
                                    <div className="flex justify-between items-start mb-4">
                                        <div className="p-3 rounded-2xl bg-primary/10 text-primary shadow-inner">
                                            <ShieldCheck className="w-7 h-7" />
                                        </div>
                                        {!isProtected && (
                                            <div className="flex gap-2 opacity-0 group-hover:opacity-100 transition-opacity">
                                                <PermissionGate permission="EMPLOYEE_UPDATE">
                                                    <button
                                                        onClick={() => handleEditRole(r)}
                                                        className="p-2 rounded-lg bg-black/5 dark:bg-white/5 hover:bg-primary/10 text-muted-foreground hover:text-primary transition-colors"
                                                    >
                                                        <Edit2 className="w-4 h-4" />
                                                    </button>
                                                </PermissionGate>
                                                <PermissionGate permission="EMPLOYEE_DELETE">
                                                    <button
                                                        onClick={() => handleDeleteRole(r)}
                                                        className="p-2 rounded-lg bg-black/5 dark:bg-white/5 hover:bg-red-500/10 text-muted-foreground hover:text-red-500 transition-colors"
                                                    >
                                                        <Trash2 className="w-4 h-4" />
                                                    </button>
                                                </PermissionGate>
                                            </div>
                                        )}
                                    </div>
                                    <h3 className="text-xl font-black text-foreground mb-4">{r.roleType}</h3>
                                    <div className="pt-4 border-t border-border/50 flex justify-between items-center">
                                        <span className="text-[10px] font-bold uppercase tracking-widest text-muted-foreground/60">Role</span>
                                        {isProtected ? (
                                            <span className="text-[10px] font-bold px-2 py-0.5 rounded-md bg-amber-500/10 text-amber-500 flex items-center gap-1">
                                                <Lock className="w-3 h-3" /> SYSTEM
                                            </span>
                                        ) : (
                                            <span className="text-[10px] font-bold px-2 py-0.5 rounded-md bg-green-500/10 text-green-500">CUSTOM</span>
                                        )}
                                    </div>
                                </GlassCard>
                            );
                        })}
                    </div>
                )}
            </div>

            {/* Designation Modal */}
            <Modal
                isOpen={isDesignationModalOpen}
                onClose={() => { setIsDesignationModalOpen(false); resetDesignationForm(); }}
                title={editingDesignationId ? "Edit Designation" : "Add Designation"}
            >
                <form onSubmit={handleSaveDesignation} className="space-y-6 pt-2">
                    <div className="space-y-4">
                        <div>
                            <label className="block text-sm font-bold text-foreground mb-1.5">
                                Designation Name <span className="text-red-500">*</span>
                            </label>
                            <input
                                type="text"
                                required
                                value={designationName}
                                onChange={(e) => setDesignationName(e.target.value)}
                                className="w-full bg-background border border-border rounded-2xl px-5 py-4 text-lg font-bold text-foreground focus:outline-none focus:ring-4 focus:ring-primary/20 transition-all placeholder:text-muted-foreground/30"
                                placeholder="e.g. Manager, Cashier, Attendant"
                            />
                        </div>

                        <div>
                            <label className="block text-sm font-bold text-foreground mb-1.5 flex items-center gap-2">
                                <ShieldCheck className="w-4 h-4 text-primary" /> Default Role
                            </label>
                            <StyledSelect
                                value={defaultRole}
                                onChange={setDefaultRole}
                                options={roleOptions}
                                placeholder="Select a default role..."
                            />
                            <p className="text-xs text-muted-foreground mt-1.5">
                                Employees with this designation get this role when their user account is created.
                            </p>
                        </div>

                        <div>
                            <label className="block text-sm font-bold text-foreground mb-1.5 flex items-center gap-2">
                                <FileText className="w-4 h-4 text-primary" /> Description
                            </label>
                            <textarea
                                value={description}
                                onChange={(e) => setDescription(e.target.value)}
                                className="w-full bg-background border border-border rounded-2xl px-5 py-4 text-foreground focus:outline-none focus:ring-4 focus:ring-primary/20 transition-all min-h-[100px] resize-none"
                                placeholder="Responsibilities of this designation..."
                            />
                        </div>
                    </div>

                    <div className="flex justify-end gap-3 pt-6 border-t border-border">
                        <button
                            type="button"
                            onClick={() => { setIsDesignationModalOpen(false); resetDesignationForm(); }}
                            className="px-8 py-3 rounded-2xl font-bold text-muted-foreground hover:text-foreground hover:bg-black/5 dark:hover:bg-white/5 transition-all"
                        >
                            Cancel
                        </button>
                        <button
                            type="submit"
                            disabled={isSaving}
                            className="btn-gradient px-10 py-3 rounded-2xl font-bold shadow-xl hover:shadow-primary/20 transition-all disabled:opacity-60"
                        >
                            {isSaving ? "Saving..." : editingDesignationId ? "Update Designation" : "Save Designation"}
                        </button>
                    </div>
                </form>
            </Modal>

            {/* Role Modal */}
            <Modal
                isOpen={isRoleModalOpen}
                onClose={() => { setIsRoleModalOpen(false); resetRoleForm(); }}
                title={editingRoleId ? "Edit Role" : "Add Role"}
            >
                <form onSubmit={handleSaveRole} className="space-y-6 pt-2">
                    <div>
                        <label className="block text-sm font-bold text-foreground mb-1.5">
                            Role Name <span className="text-red-500">*</span>
                        </label>
                        <input
                            type="text"
                            required
                            value={roleName}
                            onChange={(e) => setRoleName(e.target.value)}
                            className="w-full bg-background border border-border rounded-2xl px-5 py-4 text-lg font-bold text-foreground uppercase focus:outline-none focus:ring-4 focus:ring-primary/20 transition-all placeholder:text-muted-foreground/30 placeholder:normal-case"
                            placeholder="e.g. SUPERVISOR, ACCOUNTANT"
                        />
                        <p className="text-xs text-muted-foreground mt-1.5">
                            Saved in UPPERCASE; spaces become underscores (e.g. &quot;Shift Lead&quot; → SHIFT_LEAD).
                        </p>
                    </div>

                    <div className="flex justify-end gap-3 pt-6 border-t border-border">
                        <button
                            type="button"
                            onClick={() => { setIsRoleModalOpen(false); resetRoleForm(); }}
                            className="px-8 py-3 rounded-2xl font-bold text-muted-foreground hover:text-foreground hover:bg-black/5 dark:hover:bg-white/5 transition-all"
                        >
                            Cancel
                        </button>
                        <button
                            type="submit"
                            disabled={isSaving}
                            className="btn-gradient px-10 py-3 rounded-2xl font-bold shadow-xl hover:shadow-primary/20 transition-all disabled:opacity-60"
                        >
                            {isSaving ? "Saving..." : editingRoleId ? "Update Role" : "Save Role"}
                        </button>
                    </div>
                </form>
            </Modal>
        </div>
    );
}
