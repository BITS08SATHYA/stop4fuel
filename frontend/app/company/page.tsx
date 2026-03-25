"use client";

import { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import { Building2, Plus, Eye, Pencil, Trash2, Loader2, AlertTriangle } from "lucide-react";
import { API_BASE_URL } from "@/lib/api/station";
import { PermissionGate } from "@/components/permission-gate";

interface Company {
    id: number;
    name: string;
    openDate: string;
    sapCode: string;
    gstNo: string;
    site: string;
    type: string;
    address: string;
}

export default function CompanyListPage() {
    const router = useRouter();
    const [companies, setCompanies] = useState<Company[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [deleteId, setDeleteId] = useState<number | null>(null);
    const [deleting, setDeleting] = useState(false);

    useEffect(() => {
        fetchCompanies();
    }, []);

    const fetchCompanies = async () => {
        try {
            setLoading(true);
            const res = await fetch(`${API_BASE_URL}/companies`);
            if (!res.ok) throw new Error("Failed to fetch companies");
            const data = await res.json();
            setCompanies(data);
        } catch (err) {
            console.error(err);
            setError("Failed to load companies");
        } finally {
            setLoading(false);
        }
    };

    const handleDelete = async (id: number) => {
        setDeleting(true);
        try {
            const res = await fetch(`${API_BASE_URL}/companies/${id}`, { method: "DELETE" });
            if (!res.ok) throw new Error("Failed to delete");
            setCompanies((prev) => prev.filter((c) => c.id !== id));
            setDeleteId(null);
        } catch (err) {
            console.error(err);
            setError("Failed to delete company");
        } finally {
            setDeleting(false);
        }
    };

    const typeLabel: Record<string, string> = {
        COCO: "COCO",
        CODO: "CODO",
        DODO: "DODO",
    };

    return (
        <div className="p-6 h-screen flex flex-col bg-background transition-colors duration-300 overflow-hidden">
            <div className="max-w-7xl mx-auto w-full flex flex-col flex-1 min-h-0">
                {/* Header */}
                <div className="flex justify-between items-center mb-6 flex-shrink-0">
                    <div>
                        <h1 className="text-4xl font-bold text-foreground tracking-tight">
                            Company <span className="text-gradient">Management</span>
                        </h1>
                        <p className="text-muted-foreground mt-1">
                            Manage company profiles, documents, and certifications.
                        </p>
                    </div>
                    <PermissionGate permission="SETTINGS_MANAGE">
                        <button
                            onClick={() => router.push("/company/new")}
                            className="btn-gradient px-6 py-3 rounded-xl font-medium flex items-center gap-2"
                        >
                            <Plus className="w-5 h-5" />
                            Add Company
                        </button>
                    </PermissionGate>
                </div>

                {error && (
                    <div className="p-4 text-sm text-red-500 bg-red-50 dark:bg-red-950/20 rounded-md border border-red-200 dark:border-red-800 mb-4 flex-shrink-0">
                        {error}
                    </div>
                )}

                {/* Table */}
                <div className="flex-1 min-h-0 rounded-xl border bg-card text-card-foreground shadow-sm overflow-auto">
                    {loading ? (
                        <div className="flex items-center justify-center h-64">
                            <Loader2 className="w-8 h-8 animate-spin text-muted-foreground" />
                        </div>
                    ) : companies.length === 0 ? (
                        <div className="flex flex-col items-center justify-center h-64 text-muted-foreground">
                            <Building2 className="w-12 h-12 mb-3 opacity-30" />
                            <p className="text-lg font-medium">No companies found</p>
                            <p className="text-sm">Add your first company to get started.</p>
                        </div>
                    ) : (
                        <table className="w-full">
                            <thead>
                                <tr className="border-b bg-muted/30">
                                    <th className="text-left px-6 py-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">Name</th>
                                    <th className="text-left px-6 py-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">Type</th>
                                    <th className="text-left px-6 py-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">SAP Code</th>
                                    <th className="text-left px-6 py-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">GST No</th>
                                    <th className="text-left px-6 py-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">Site</th>
                                    <th className="text-left px-6 py-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">Open Date</th>
                                    <th className="text-right px-6 py-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">Actions</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-border">
                                {companies.map((company) => (
                                    <tr key={company.id} className="hover:bg-muted/20 transition-colors">
                                        <td className="px-6 py-4">
                                            <div className="flex items-center gap-3">
                                                <div className="w-8 h-8 rounded-lg bg-primary/10 flex items-center justify-center">
                                                    <Building2 className="w-4 h-4 text-primary" />
                                                </div>
                                                <span className="font-medium text-foreground">{company.name}</span>
                                            </div>
                                        </td>
                                        <td className="px-6 py-4">
                                            <span className="px-2.5 py-1 rounded-full text-xs font-medium bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400">
                                                {typeLabel[company.type] || company.type || "—"}
                                            </span>
                                        </td>
                                        <td className="px-6 py-4 text-sm text-muted-foreground">{company.sapCode || "—"}</td>
                                        <td className="px-6 py-4 text-sm text-muted-foreground">{company.gstNo || "—"}</td>
                                        <td className="px-6 py-4 text-sm text-muted-foreground">{company.site || "—"}</td>
                                        <td className="px-6 py-4 text-sm text-muted-foreground">
                                            {company.openDate ? new Date(company.openDate).toLocaleDateString("en-IN") : "—"}
                                        </td>
                                        <td className="px-6 py-4">
                                            <div className="flex items-center justify-end gap-1">
                                                <button
                                                    onClick={() => router.push(`/company/${company.id}`)}
                                                    className="p-2 rounded-lg hover:bg-muted transition-colors text-muted-foreground hover:text-foreground"
                                                    title="View"
                                                >
                                                    <Eye className="w-4 h-4" />
                                                </button>
                                                <PermissionGate permission="SETTINGS_MANAGE">
                                                    <button
                                                        onClick={() => router.push(`/company/${company.id}?edit=true`)}
                                                        className="p-2 rounded-lg hover:bg-muted transition-colors text-muted-foreground hover:text-foreground"
                                                        title="Edit"
                                                    >
                                                        <Pencil className="w-4 h-4" />
                                                    </button>
                                                    <button
                                                        onClick={() => setDeleteId(company.id)}
                                                        className="p-2 rounded-lg hover:bg-red-100 dark:hover:bg-red-950/30 transition-colors text-muted-foreground hover:text-red-600"
                                                        title="Delete"
                                                    >
                                                        <Trash2 className="w-4 h-4" />
                                                    </button>
                                                </PermissionGate>
                                            </div>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    )}
                </div>
            </div>

            {/* Delete confirmation modal */}
            {deleteId !== null && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm">
                    <div className="bg-card border border-border rounded-2xl shadow-2xl p-6 max-w-sm w-full mx-4">
                        <div className="flex items-center gap-3 mb-4">
                            <div className="p-2 rounded-full bg-red-100 dark:bg-red-950/30">
                                <AlertTriangle className="w-5 h-5 text-red-600" />
                            </div>
                            <h3 className="text-lg font-semibold">Delete Company</h3>
                        </div>
                        <p className="text-sm text-muted-foreground mb-6">
                            Are you sure you want to delete this company? This will also remove all associated documents. This action cannot be undone.
                        </p>
                        <div className="flex justify-end gap-3">
                            <button
                                onClick={() => setDeleteId(null)}
                                className="px-4 py-2 text-sm rounded-lg border border-border hover:bg-muted transition-colors"
                                disabled={deleting}
                            >
                                Cancel
                            </button>
                            <button
                                onClick={() => handleDelete(deleteId)}
                                disabled={deleting}
                                className="px-4 py-2 text-sm rounded-lg bg-red-600 text-white hover:bg-red-700 transition-colors disabled:opacity-50"
                            >
                                {deleting ? "Deleting..." : "Delete"}
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
