"use client";

import { useEffect, useState, useCallback } from "react";
import { RouteGuard } from "@/components/route-guard";
import { fetchWithAuth } from "@/lib/api/fetch-with-auth";
import { Save, Loader2 } from "lucide-react";

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
}

const EDITABLE_ROLES = ["ADMIN", "CASHIER", "EMPLOYEE", "CUSTOMER"];

export default function PermissionsPage() {
    const [permissionsByModule, setPermissionsByModule] = useState<Record<string, Permission[]>>({});
    const [rolePermissions, setRolePermissions] = useState<Record<string, string[]>>({});
    const [saving, setSaving] = useState<string | null>(null);
    const [loading, setLoading] = useState(true);

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

    const saveRole = async (role: string) => {
        setSaving(role);
        try {
            const API = getApiBaseUrl();
            await fetchWithAuth(`${API}/permissions/role/${role}`, {
                method: "PUT",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ permissionCodes: rolePermissions[role] || [] }),
            });
        } catch (err) {
            console.error("Failed to save:", err);
        } finally {
            setSaving(null);
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
        <RouteGuard permission="SETTINGS_MANAGE">
            <div className="p-6 space-y-6">
                <div className="flex items-center justify-between">
                    <div>
                        <h1 className="text-2xl font-bold">Role Permissions</h1>
                        <p className="text-muted-foreground text-sm mt-1">
                            Configure which permissions each role has. Owner always has all permissions.
                        </p>
                    </div>
                </div>

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
                                {modules.map(module => (
                                    <>
                                        <tr key={`mod-${module}`} className="bg-muted/30">
                                            <td colSpan={2 + EDITABLE_ROLES.length} className="p-2 px-3 font-semibold text-xs uppercase tracking-wider text-muted-foreground">
                                                {module}
                                            </td>
                                        </tr>
                                        {permissionsByModule[module].map(perm => (
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
                                    </>
                                ))}
                            </tbody>
                        </table>
                    </div>
                </div>

                <div className="flex gap-3">
                    {EDITABLE_ROLES.map(role => (
                        <button
                            key={role}
                            onClick={() => saveRole(role)}
                            disabled={saving !== null}
                            className="flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-lg text-sm font-medium hover:bg-primary/90 disabled:opacity-50 transition-colors"
                        >
                            {saving === role ? (
                                <Loader2 className="w-4 h-4 animate-spin" />
                            ) : (
                                <Save className="w-4 h-4" />
                            )}
                            Save {role}
                        </button>
                    ))}
                </div>
            </div>
        </RouteGuard>
    );
}
