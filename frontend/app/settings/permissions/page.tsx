"use client";

import React, { useEffect, useState, useCallback } from "react";
import { RouteGuard } from "@/components/route-guard";
import { PermissionGate } from "@/components/permission-gate";
import { fetchWithAuth } from "@/lib/api/fetch-with-auth";
import { Save, Loader2, Plus, Trash2 } from "lucide-react";
import { showToast } from "@/components/ui/toast";

const getApiBaseUrl = () => {
    if (typeof window !== "undefined") {
        return process.env.NEXT_PUBLIC_API_URL || `${window.location.protocol}//${window.location.hostname}:8080/api`;
    }
    return process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api";
};

interface Permission {
    id: number;
    code: string;
    description: string;
    module: string;
    systemDefault: boolean;
}

const EDITABLE_ROLES = ["ADMIN", "CASHIER", "EMPLOYEE", "CUSTOMER"];

export default function PermissionsPage() {
    const [permissionsByModule, setPermissionsByModule] = useState<Record<string, Permission[]>>({});
    const [rolePermissions, setRolePermissions] = useState<Record<string, string[]>>({});
    const [saving, setSaving] = useState<string | null>(null);
    const [loading, setLoading] = useState(true);
    const [newModule, setNewModule] = useState("");
    const [newModuleDesc, setNewModuleDesc] = useState("");
    const [addingModule, setAddingModule] = useState(false);
    const [deletingModule, setDeletingModule] = useState<string | null>(null);

    const loadData = useCallback(async () => {
        try {
            const API = getApiBaseUrl();
            const [permsRes, ...roleRes] = await Promise.all([
                fetchWithAuth(`${API}/permissions`),
                ...EDITABLE_ROLES.map(role => fetchWithAuth(`${API}/permissions/role/${role}`)),
            ]);

            const permsData = await permsRes.json();
            setPermissionsByModule(permsData);

            const rolePermsMap: Record<string, string[]> = {};
            for (let i = 0; i < EDITABLE_ROLES.length; i++) {
                rolePermsMap[EDITABLE_ROLES[i]] = await roleRes[i].json();
            }
            setRolePermissions(rolePermsMap);
        } catch (err) {
            console.error("Failed to load permissions:", err);
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => { loadData(); }, [loadData]);

    const togglePermission = (role: string, permCode: string) => {
        setRolePermissions(prev => {
            const current = prev[role] || [];
            const updated = current.includes(permCode)
                ? current.filter(c => c !== permCode)
                : [...current, permCode];
            return { ...prev, [role]: updated };
        });
    };

    const [saveSuccess, setSaveSuccess] = useState(false);
    const [saveError, setSaveError] = useState("");

    const saveAll = async () => {
        setSaving("ALL");
        setSaveError("");
        setSaveSuccess(false);
        try {
            const API = getApiBaseUrl();
            for (const role of EDITABLE_ROLES) {
                const res = await fetchWithAuth(`${API}/permissions/role/${role}`, {
                    method: "PUT",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ permissionCodes: rolePermissions[role] || [] }),
                });
                if (!res.ok) {
                    throw new Error(`Failed to save ${role}: ${res.status}`);
                }
            }
            // Clear backend caches
            await fetchWithAuth(`${API}/permissions/clear-cache`, { method: "POST" });
            // Reload to confirm
            await loadData();
            setSaveSuccess(true);
            setTimeout(() => setSaveSuccess(false), 3000);
        } catch (err: any) {
            console.error("Failed to save:", err);
            setSaveError(err?.message || "Failed to save permissions");
        } finally {
            setSaving(null);
        }
    };

    const addModule = async () => {
        if (!newModule.trim()) return;
        setAddingModule(true);
        try {
            const API = getApiBaseUrl();
            const res = await fetchWithAuth(`${API}/permissions`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    module: newModule.toUpperCase().trim(),
                    description: newModuleDesc.trim() || undefined,
                }),
            });
            if (!res.ok) {
                const err = await res.text();
                showToast.error(err || "Failed to add module");
                return;
            }
            setNewModule("");
            setNewModuleDesc("");
            await loadData();
        } catch (err) {
            console.error("Failed to add module:", err);
        } finally {
            setAddingModule(false);
        }
    };

    const deleteModule = async (module: string) => {
        if (!confirm(`Delete all permissions for module "${module}"? This cannot be undone.`)) return;
        setDeletingModule(module);
        try {
            const API = getApiBaseUrl();
            const res = await fetchWithAuth(`${API}/permissions/module/${module}`, { method: "DELETE" });
            if (!res.ok) {
                const err = await res.text();
                showToast.error(err || "Failed to delete module");
                return;
            }
            await loadData();
        } catch (err) {
            console.error("Failed to delete module:", err);
        } finally {
            setDeletingModule(null);
        }
    };

    if (loading) {
        return (
            <div className="flex items-center justify-center min-h-[50vh]">
                <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary" />
            </div>
        );
    }

    const modules = Object.keys(permissionsByModule);

    return (
        <RouteGuard permission="SETTINGS_VIEW">
            <div className="p-6 space-y-6">
                <div className="flex items-center justify-between">
                    <div>
                        <h1 className="text-2xl font-bold">Role Permissions</h1>
                        <p className="text-muted-foreground text-sm mt-1">
                            Configure which permissions each role has. Owner always has all permissions.
                        </p>
                    </div>
                </div>

                <PermissionGate permission="SETTINGS_CREATE">
                    <div className="border border-border rounded-lg p-4 bg-muted/20">
                        <h3 className="text-sm font-semibold mb-3">Add New Module</h3>
                        <div className="flex items-end gap-3">
                            <div className="flex-1 max-w-xs">
                                <label className="text-xs text-muted-foreground mb-1 block">Module Name</label>
                                <input
                                    type="text"
                                    value={newModule}
                                    onChange={e => setNewModule(e.target.value.toUpperCase().replace(/[^A-Z_]/g, ""))}
                                    placeholder="e.g. ANALYTICS"
                                    className="w-full px-3 py-2 rounded-md border border-border bg-background text-sm"
                                />
                            </div>
                            <div className="flex-1 max-w-xs">
                                <label className="text-xs text-muted-foreground mb-1 block">Description (optional)</label>
                                <input
                                    type="text"
                                    value={newModuleDesc}
                                    onChange={e => setNewModuleDesc(e.target.value)}
                                    placeholder="e.g. analytics data"
                                    className="w-full px-3 py-2 rounded-md border border-border bg-background text-sm"
                                />
                            </div>
                            <button
                                onClick={addModule}
                                disabled={addingModule || !newModule.trim()}
                                className="flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-lg text-sm font-medium hover:bg-primary/90 disabled:opacity-50 transition-colors"
                            >
                                {addingModule ? <Loader2 className="w-4 h-4 animate-spin" /> : <Plus className="w-4 h-4" />}
                                Add Module
                            </button>
                        </div>
                        <p className="text-xs text-muted-foreground mt-2">
                            Creates VIEW, CREATE, UPDATE, DELETE permissions for the new module.
                        </p>
                    </div>
                </PermissionGate>

                <div className="border border-border rounded-lg overflow-hidden">
                    <div className="overflow-x-auto">
                        <table className="w-full text-sm">
                            <thead>
                                <tr className="bg-muted/50">
                                    <th className="text-left p-3 font-medium text-muted-foreground w-48">Permission</th>
                                    <th className="text-center p-3 font-medium text-muted-foreground w-20">OWNER</th>
                                    {EDITABLE_ROLES.map(role => (
                                        <th key={role} className="text-center p-3 font-medium text-muted-foreground w-20">
                                            {role}
                                        </th>
                                    ))}
                                </tr>
                            </thead>
                            <tbody>
                                {modules.map(module => {
                                    const perms = permissionsByModule[module];
                                    const isCustom = perms.length > 0 && !perms[0].systemDefault;
                                    return (
                                        <React.Fragment key={`mod-${module}`}>
                                            <tr className="bg-muted/30">
                                                <td colSpan={2 + EDITABLE_ROLES.length} className="p-2 px-3">
                                                    <div className="flex items-center justify-between">
                                                        <span className="font-semibold text-xs uppercase tracking-wider text-muted-foreground">
                                                            {module}
                                                        </span>
                                                        {isCustom && (
                                                            <PermissionGate permission="SETTINGS_DELETE">
                                                                <button
                                                                    onClick={() => deleteModule(module)}
                                                                    disabled={deletingModule === module}
                                                                    className="text-destructive hover:text-destructive/80 transition-colors p-1"
                                                                    title={`Delete ${module} module`}
                                                                >
                                                                    {deletingModule === module ? (
                                                                        <Loader2 className="w-3.5 h-3.5 animate-spin" />
                                                                    ) : (
                                                                        <Trash2 className="w-3.5 h-3.5" />
                                                                    )}
                                                                </button>
                                                            </PermissionGate>
                                                        )}
                                                    </div>
                                                </td>
                                            </tr>
                                            {perms.map(perm => (
                                                <tr key={perm.code} className="border-t border-border hover:bg-muted/20">
                                                    <td className="p-3">
                                                        <div className="font-medium">{perm.description}</div>
                                                        <div className="text-xs text-muted-foreground">{perm.code}</div>
                                                    </td>
                                                    <td className="text-center p-3">
                                                        <input type="checkbox" checked disabled className="opacity-50 cursor-not-allowed" />
                                                    </td>
                                                    {EDITABLE_ROLES.map(role => (
                                                        <td key={role} className="text-center p-3">
                                                            <input
                                                                type="checkbox"
                                                                checked={(rolePermissions[role] || []).includes(perm.code)}
                                                                onChange={() => togglePermission(role, perm.code)}
                                                                className="cursor-pointer"
                                                            />
                                                        </td>
                                                    ))}
                                                </tr>
                                            ))}
                                        </React.Fragment>
                                    );
                                })}
                            </tbody>
                        </table>
                    </div>
                </div>

                <div className="flex items-center gap-3">
                    <button
                        onClick={saveAll}
                        disabled={saving !== null}
                        className="flex items-center gap-2 px-6 py-2.5 bg-primary text-primary-foreground rounded-lg text-sm font-medium hover:bg-primary/90 disabled:opacity-50 transition-colors"
                    >
                        {saving ? (
                            <Loader2 className="w-4 h-4 animate-spin" />
                        ) : (
                            <Save className="w-4 h-4" />
                        )}
                        {saving ? "Saving..." : "Save All Permissions"}
                    </button>
                    {saveSuccess && (
                        <span className="text-sm text-green-400 font-medium">Saved successfully</span>
                    )}
                    {saveError && (
                        <span className="text-sm text-red-400 font-medium">{saveError}</span>
                    )}
                </div>
            </div>
        </RouteGuard>
    );
}
