"use client";

import React, { useState, useEffect, useMemo } from "react";
import { Plus, Search, Edit2, Trash2, Shield, Building2 } from "lucide-react";
import { Modal } from "@/components/ui/modal";
import { PermissionGate } from "@/components/permission-gate";
import { Badge } from "@/components/ui/badge";
import { API_BASE_URL } from "@/lib/api/station";
import { TablePagination, useClientPagination } from "@/components/ui/table-pagination";

export default function CategoriesPage() {
    const [categories, setCategories] = useState<any[]>([]);
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [formData, setFormData] = useState<any>({});
    const [loading, setLoading] = useState(false);
    const [searchQuery, setSearchQuery] = useState("");
    const [error, setError] = useState("");

    const filteredCategories = useMemo(() => categories.filter((c) => {
        if (!searchQuery) return true;
        const q = searchQuery.toLowerCase();
        return c.categoryName?.toLowerCase().includes(q) || c.categoryType?.toLowerCase().includes(q) || c.description?.toLowerCase().includes(q);
    }), [categories, searchQuery]);

    const { page, setPage, totalPages, totalElements, pageSize, paginatedData: pagedCategories } = useClientPagination(filteredCategories);

    useEffect(() => {
        fetchCategories();
    }, []);

    const fetchCategories = async () => {
        try {
            const res = await fetch(`${API_BASE_URL}/customer-categories`);
            if (res.ok) {
                const data = await res.json();
                setCategories(Array.isArray(data) ? data : []);
            }
        } catch (error) {
            console.error("Failed to fetch categories", error);
        }
    };

    const handleEdit = (category: any) => {
        setFormData({
            id: category.id,
            categoryName: category.categoryName,
            categoryType: category.categoryType,
            description: category.description,
        });
        setError("");
        setIsModalOpen(true);
    };

    const handleDelete = async (id: number) => {
        if (!confirm("Are you sure you want to delete this category? Customers assigned to it will be unlinked.")) return;
        try {
            const res = await fetch(`${API_BASE_URL}/customer-categories/${id}`, { method: "DELETE" });
            if (res.ok) {
                fetchCategories();
            }
        } catch (error) {
            console.error("Failed to delete category", error);
        }
    };

    const handleSave = async () => {
        if (!formData.categoryName?.trim()) { setError("Category name is required"); return; }
        if (!formData.categoryType) { setError("Category type is required"); return; }
        setLoading(true);
        setError("");
        try {
            const url = formData.id
                ? `${API_BASE_URL}/customer-categories/${formData.id}`
                : `${API_BASE_URL}/customer-categories`;
            const method = formData.id ? "PUT" : "POST";

            const res = await fetch(url, {
                method,
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    categoryName: formData.categoryName,
                    categoryType: formData.categoryType,
                    description: formData.description,
                }),
            });
            if (res.ok) {
                setIsModalOpen(false);
                setFormData({});
                fetchCategories();
            } else {
                const text = await res.text();
                setError(text || "Failed to save");
            }
        } catch (error) {
            console.error("Failed to save category", error);
            setError("Failed to save category");
        } finally {
            setLoading(false);
        }
    };

    const govtCount = categories.filter(c => c.categoryType === "GOVERNMENT").length;
    const nonGovtCount = categories.filter(c => c.categoryType === "NON_GOVERNMENT").length;

    return (
        <div className="p-6 h-screen overflow-hidden bg-background text-foreground">
            <div className="max-w-7xl mx-auto">
                <div className="flex justify-between items-center mb-8">
                    <div>
                        <h1 className="text-4xl font-bold tracking-tight">
                            Customer <span className="text-gradient">Categories</span>
                        </h1>
                        <p className="text-muted-foreground mt-2">
                            Manage customer categories for tracking invoices and payments by type.
                        </p>
                        <div className="flex gap-3 mt-3">
                            <Badge variant="default" className="text-xs">
                                <Shield className="w-3 h-3 mr-1" /> {govtCount} Government
                            </Badge>
                            <Badge variant="outline" className="text-xs">
                                <Building2 className="w-3 h-3 mr-1" /> {nonGovtCount} Non-Government
                            </Badge>
                        </div>
                    </div>
                    <PermissionGate permission="CUSTOMER_MANAGE">
                        <button
                            onClick={() => { setFormData({}); setError(""); setIsModalOpen(true); }}
                            className="btn-gradient px-6 py-3 rounded-xl font-medium flex items-center gap-2"
                        >
                            <Plus className="w-5 h-5" />
                            Add Category
                        </button>
                    </PermissionGate>
                </div>

                <div className="mb-6">
                    <div className="relative max-w-md">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                        <input
                            type="text"
                            placeholder="Search categories..."
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            className="w-full pl-10 pr-4 py-2.5 bg-card border border-border rounded-xl text-foreground text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                        />
                    </div>
                </div>

                <div className="bg-card border border-border rounded-2xl overflow-hidden shadow-xl">
                    <table className="w-full text-left">
                        <thead>
                            <tr className="border-b border-border bg-muted/50">
                                <th className="px-6 py-4 text-sm font-semibold">Category Name</th>
                                <th className="px-6 py-4 text-sm font-semibold">Type</th>
                                <th className="px-6 py-4 text-sm font-semibold">Description</th>
                                <th className="px-6 py-4 text-sm font-semibold text-right">Actions</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-border">
                            {pagedCategories.length === 0 ? (
                                <tr>
                                    <td colSpan={4} className="text-center py-12 text-muted-foreground">
                                        No categories found. Create one to get started.
                                    </td>
                                </tr>
                            ) : pagedCategories.map((cat) => (
                                <tr key={cat.id} className="hover:bg-muted/30 transition-colors">
                                    <td className="px-6 py-4 font-medium">{cat.categoryName}</td>
                                    <td className="px-6 py-4">
                                        <Badge variant={cat.categoryType === "GOVERNMENT" ? "success" : "default"}>
                                            {cat.categoryType === "GOVERNMENT" ? "Government" : "Non-Government"}
                                        </Badge>
                                    </td>
                                    <td className="px-6 py-4 text-muted-foreground">{cat.description || "-"}</td>
                                    <td className="px-6 py-4 text-right">
                                        <PermissionGate permission="CUSTOMER_MANAGE">
                                            <div className="flex justify-end gap-2">
                                                <button
                                                    onClick={() => handleEdit(cat)}
                                                    className="p-2 hover:bg-primary/10 rounded-lg text-primary transition-colors"
                                                >
                                                    <Edit2 className="w-4 h-4" />
                                                </button>
                                                <button
                                                    onClick={() => handleDelete(cat.id)}
                                                    className="p-2 hover:bg-destructive/10 rounded-lg text-destructive transition-colors"
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

                    {totalPages > 1 && (
                        <TablePagination page={page} totalPages={totalPages} totalElements={totalElements} pageSize={pageSize} onPageChange={setPage} />
                    )}
                </div>
            </div>

            <Modal isOpen={isModalOpen} onClose={() => setIsModalOpen(false)} title={formData.id ? "Edit Category" : "New Category"}>
                <div className="space-y-4">
                    <div>
                        <label className="block text-sm font-medium text-muted-foreground mb-1">
                            Category Name <span className="text-red-500">*</span>
                        </label>
                        <input
                            type="text"
                            value={formData.categoryName || ""}
                            onChange={(e) => setFormData({ ...formData, categoryName: e.target.value })}
                            className="w-full bg-white/5 border border-white/10 rounded-lg px-4 py-2 text-foreground focus:outline-none focus:ring-2 focus:ring-cyan-500/50"
                            placeholder="e.g. Bus Operator, Transport"
                        />
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-muted-foreground mb-1">
                            Type <span className="text-red-500">*</span>
                        </label>
                        <select
                            value={formData.categoryType || ""}
                            onChange={(e) => setFormData({ ...formData, categoryType: e.target.value })}
                            className="w-full bg-white/5 border border-white/10 rounded-lg px-4 py-2 text-foreground focus:outline-none focus:ring-2 focus:ring-cyan-500/50"
                        >
                            <option value="" className="bg-slate-900">Select Type</option>
                            <option value="GOVERNMENT" className="bg-slate-900">Government</option>
                            <option value="NON_GOVERNMENT" className="bg-slate-900">Non-Government</option>
                        </select>
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-muted-foreground mb-1">Description</label>
                        <textarea
                            value={formData.description || ""}
                            onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                            className="w-full bg-white/5 border border-white/10 rounded-lg px-4 py-2 text-foreground focus:outline-none focus:ring-2 focus:ring-cyan-500/50"
                            placeholder="Optional description"
                            rows={2}
                        />
                    </div>
                    {error && <p className="text-red-500 text-sm">{error}</p>}
                    <div className="flex justify-end gap-3 pt-2">
                        <button onClick={() => setIsModalOpen(false)} className="px-4 py-2 text-sm border border-border rounded-lg hover:bg-muted">Cancel</button>
                        <button onClick={handleSave} disabled={loading} className="btn-gradient px-6 py-2 rounded-lg text-sm font-medium disabled:opacity-50">
                            {loading ? "Saving..." : formData.id ? "Update" : "Create"}
                        </button>
                    </div>
                </div>
            </Modal>
        </div>
    );
}
