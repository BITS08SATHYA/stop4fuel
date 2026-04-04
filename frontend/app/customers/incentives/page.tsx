"use client";

import { useEffect, useState, useCallback, useMemo, useRef } from "react";
import { GlassCard } from "@/components/ui/glass-card";
import { Modal } from "@/components/ui/modal";
import { StyledSelect } from "@/components/ui/styled-select";
import {
    Tag,
    Search,
    Plus,
    Edit3,
    Trash2,
    ToggleLeft,
    ToggleRight,
    Hash,
    Users,
    CheckCircle,
    XCircle,
} from "lucide-react";
import {
    Incentive,
    Customer,
    Product,
    getAllIncentives,
    getCustomers,
    getActiveProducts,
    createIncentive,
    updateIncentive,
    deleteIncentive,
} from "@/lib/api/station";
import { TablePagination, useClientPagination } from "@/components/ui/table-pagination";
import { PermissionGate } from "@/components/permission-gate";

function getCustomerName(inc: Incentive): string {
    if ("name" in inc.customer) return inc.customer.name;
    return `Customer #${inc.customer.id}`;
}

function getProductName(inc: Incentive): string {
    if ("name" in inc.product) return inc.product.name;
    return `Product #${inc.product.id}`;
}

export default function IncentivesPage() {
    const [incentives, setIncentives] = useState<Incentive[]>([]);
    const [products, setProducts] = useState<Product[]>([]);
    const [isLoading, setIsLoading] = useState(true);

    // Filters
    const [searchQuery, setSearchQuery] = useState("");
    const [statusFilter, setStatusFilter] = useState("ALL");
    const [productFilter, setProductFilter] = useState("ALL");

    // Modal
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [editingIncentive, setEditingIncentive] = useState<Incentive | null>(null);
    const [formCustomerId, setFormCustomerId] = useState("");
    const [formProductId, setFormProductId] = useState("");
    const [formDiscountRate, setFormDiscountRate] = useState("");
    const [formMinQuantity, setFormMinQuantity] = useState("");
    const [isSubmitting, setIsSubmitting] = useState(false);

    // Customer autocomplete
    const [customerSearch, setCustomerSearch] = useState("");
    const [customerResults, setCustomerResults] = useState<Customer[]>([]);
    const [showCustomerDropdown, setShowCustomerDropdown] = useState(false);
    const [isSearchingCustomers, setIsSearchingCustomers] = useState(false);
    const customerDropdownRef = useRef<HTMLDivElement>(null);
    const customerSearchTimerRef = useRef<NodeJS.Timeout | null>(null);

    const loadData = useCallback(async () => {
        setIsLoading(true);
        try {
            const [incs, prods] = await Promise.all([
                getAllIncentives(),
                getActiveProducts(),
            ]);
            setIncentives(incs);
            setProducts(prods);
        } catch (err) {
            console.error("Failed to load data", err);
        } finally {
            setIsLoading(false);
        }
    }, []);

    useEffect(() => {
        loadData();
    }, [loadData]);

    // Customer autocomplete search with debounce
    const handleCustomerSearch = useCallback((query: string) => {
        setCustomerSearch(query);
        setFormCustomerId("");
        if (customerSearchTimerRef.current) clearTimeout(customerSearchTimerRef.current);
        if (query.trim().length < 1) {
            setCustomerResults([]);
            setShowCustomerDropdown(false);
            return;
        }
        setIsSearchingCustomers(true);
        customerSearchTimerRef.current = setTimeout(async () => {
            try {
                const res = await getCustomers(query.trim());
                const list = Array.isArray(res) ? res : res.content || [];
                setCustomerResults(list);
                setShowCustomerDropdown(list.length > 0);
            } catch {
                setCustomerResults([]);
            } finally {
                setIsSearchingCustomers(false);
            }
        }, 300);
    }, []);

    const selectCustomer = (c: Customer) => {
        setFormCustomerId(String(c.id));
        setCustomerSearch(c.name);
        setShowCustomerDropdown(false);
    };

    // Close dropdown on outside click
    useEffect(() => {
        const handleClickOutside = (e: MouseEvent) => {
            if (customerDropdownRef.current && !customerDropdownRef.current.contains(e.target as Node)) {
                setShowCustomerDropdown(false);
            }
        };
        document.addEventListener("mousedown", handleClickOutside);
        return () => document.removeEventListener("mousedown", handleClickOutside);
    }, []);

    // Summary
    const summary = useMemo(() => {
        const total = incentives.length;
        const active = incentives.filter((i) => i.active).length;
        const inactive = total - active;
        const uniqueCustomers = new Set(
            incentives.map((i) => ("id" in i.customer ? i.customer.id : i.customer))
        ).size;
        return { total, active, inactive, uniqueCustomers };
    }, [incentives]);

    // Filtering
    const filtered = useMemo(() => {
        return incentives.filter((inc) => {
            const matchStatus =
                statusFilter === "ALL" ||
                (statusFilter === "ACTIVE" && inc.active) ||
                (statusFilter === "INACTIVE" && !inc.active);
            const matchProduct =
                productFilter === "ALL" ||
                ("id" in inc.product && String(inc.product.id) === productFilter);
            const q = searchQuery.toLowerCase();
            const matchSearch =
                !searchQuery ||
                getCustomerName(inc).toLowerCase().includes(q) ||
                getProductName(inc).toLowerCase().includes(q);
            return matchStatus && matchProduct && matchSearch;
        });
    }, [incentives, statusFilter, productFilter, searchQuery]);

    const { page, setPage, totalPages, totalElements, pageSize, paginatedData: pagedIncentives } =
        useClientPagination(filtered);

    // Unique products for filter dropdown
    const productOptions = useMemo(() => {
        const map = new Map<number, string>();
        incentives.forEach((inc) => {
            if ("name" in inc.product) {
                map.set(inc.product.id, inc.product.name);
            }
        });
        return Array.from(map.entries()).sort((a, b) => a[1].localeCompare(b[1]));
    }, [incentives]);

    // Handlers
    const resetForm = () => {
        setFormCustomerId("");
        setFormProductId("");
        setFormDiscountRate("");
        setFormMinQuantity("");
        setEditingIncentive(null);
        setCustomerSearch("");
        setCustomerResults([]);
        setShowCustomerDropdown(false);
    };

    const handleOpenCreate = () => {
        resetForm();
        setIsModalOpen(true);
    };

    const handleOpenEdit = (inc: Incentive) => {
        setEditingIncentive(inc);
        setFormCustomerId(String("id" in inc.customer ? inc.customer.id : ""));
        setCustomerSearch(getCustomerName(inc));
        setFormProductId(String("id" in inc.product ? inc.product.id : ""));
        setFormDiscountRate(String(inc.discountRate));
        setFormMinQuantity(inc.minQuantity != null ? String(inc.minQuantity) : "");
        setIsModalOpen(true);
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setIsSubmitting(true);
        try {
            const payload: Partial<Incentive> = {
                customer: { id: Number(formCustomerId) },
                product: { id: Number(formProductId) },
                discountRate: Number(formDiscountRate),
                minQuantity: formMinQuantity ? Number(formMinQuantity) : undefined,
                active: editingIncentive ? editingIncentive.active : true,
            };
            if (editingIncentive?.id) {
                await updateIncentive(editingIncentive.id, payload);
            } else {
                await createIncentive(payload);
            }
            setIsModalOpen(false);
            resetForm();
            await loadData();
        } catch (err: any) {
            alert(err.message || "Failed to save incentive");
        } finally {
            setIsSubmitting(false);
        }
    };

    const handleToggleActive = async (inc: Incentive) => {
        if (!inc.id) return;
        try {
            await updateIncentive(inc.id, { ...inc, active: !inc.active });
            await loadData();
        } catch (err: any) {
            alert(err.message || "Failed to toggle status");
        }
    };

    const handleDelete = async (inc: Incentive) => {
        if (!inc.id) return;
        if (!confirm(`Delete incentive for ${getCustomerName(inc)} — ${getProductName(inc)}?`)) return;
        try {
            await deleteIncentive(inc.id);
            await loadData();
        } catch (err: any) {
            alert(err.message || "Failed to delete incentive");
        }
    };

    return (
        <div className="p-6 h-screen overflow-hidden bg-background transition-colors duration-300">
            <div className="max-w-7xl mx-auto">
                {/* Header */}
                <div className="flex justify-between items-center mb-8">
                    <div>
                        <h1 className="text-4xl font-bold text-foreground tracking-tight">
                            Incentive <span className="text-gradient">Management</span>
                        </h1>
                        <p className="text-muted-foreground mt-2">
                            Manage discount rules across all customers and products.
                        </p>
                    </div>
                    <PermissionGate permission="CUSTOMER_MANAGE">
                        <button
                            onClick={handleOpenCreate}
                            className="btn-gradient px-5 py-2.5 rounded-xl font-medium flex items-center gap-2 shadow-lg hover:shadow-xl transition-all"
                        >
                            <Plus className="w-4 h-4" />
                            Add Incentive
                        </button>
                    </PermissionGate>
                </div>

                {isLoading ? (
                    <div className="flex flex-col items-center justify-center py-20 text-muted-foreground">
                        <div className="w-12 h-12 border-4 border-primary/20 border-t-primary rounded-full animate-spin mb-4" />
                        <p className="animate-pulse">Loading incentives...</p>
                    </div>
                ) : (
                    <>
                        {/* Summary Cards */}
                        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mb-6">
                            <div className="flex items-center gap-3 p-3 rounded-xl border border-border bg-card">
                                <div className="p-2 rounded-lg text-blue-500 bg-blue-500/10">
                                    <Tag className="w-4 h-4" />
                                </div>
                                <div>
                                    <p className="text-[10px] uppercase tracking-wider text-muted-foreground">Total Incentives</p>
                                    <p className="text-sm font-bold text-foreground">{summary.total}</p>
                                </div>
                            </div>
                            <div className="flex items-center gap-3 p-3 rounded-xl border border-border bg-card">
                                <div className="p-2 rounded-lg text-green-500 bg-green-500/10">
                                    <CheckCircle className="w-4 h-4" />
                                </div>
                                <div>
                                    <p className="text-[10px] uppercase tracking-wider text-muted-foreground">Active</p>
                                    <p className="text-sm font-bold text-green-500">{summary.active}</p>
                                </div>
                            </div>
                            <div className="flex items-center gap-3 p-3 rounded-xl border border-border bg-card">
                                <div className="p-2 rounded-lg text-red-500 bg-red-500/10">
                                    <XCircle className="w-4 h-4" />
                                </div>
                                <div>
                                    <p className="text-[10px] uppercase tracking-wider text-muted-foreground">Inactive</p>
                                    <p className="text-sm font-bold text-red-500">{summary.inactive}</p>
                                </div>
                            </div>
                            <div className="flex items-center gap-3 p-3 rounded-xl border border-border bg-card">
                                <div className="p-2 rounded-lg text-purple-500 bg-purple-500/10">
                                    <Users className="w-4 h-4" />
                                </div>
                                <div>
                                    <p className="text-[10px] uppercase tracking-wider text-muted-foreground">Unique Customers</p>
                                    <p className="text-sm font-bold text-foreground">{summary.uniqueCustomers}</p>
                                </div>
                            </div>
                        </div>

                        {/* Filter Bar */}
                        <div className="mb-4 flex flex-wrap gap-3 items-center">
                            <div className="relative flex-1 min-w-[200px] max-w-md">
                                <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                                <input
                                    type="text"
                                    placeholder="Search customer or product..."
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
                            <StyledSelect
                                value={productFilter}
                                onChange={(val) => setProductFilter(val)}
                                options={[
                                    { value: "ALL", label: "All Products" },
                                    ...productOptions.map(([id, name]) => ({ value: String(id), label: String(name) })),
                                ]}
                                className="min-w-[160px]"
                            />
                        </div>

                        {/* Table */}
                        <GlassCard className="overflow-hidden border-none p-0 mb-6">
                            <div className="overflow-x-auto">
                                <table className="w-full text-left border-collapse">
                                    <thead>
                                        <tr className="bg-white/5 border-b border-border/50">
                                            <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Customer</th>
                                            <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Product</th>
                                            <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right">Min Qty</th>
                                            <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right">Discount Rate</th>
                                            <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center">Status</th>
                                            <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center">Actions</th>
                                        </tr>
                                    </thead>
                                    <tbody className="divide-y divide-border/30">
                                        {filtered.length === 0 ? (
                                            <tr>
                                                <td colSpan={6} className="px-6 py-12 text-center text-muted-foreground">
                                                    No incentives found
                                                </td>
                                            </tr>
                                        ) : (
                                            pagedIncentives.map((inc) => (
                                                <tr key={inc.id} className="hover:bg-white/5 transition-colors group">
                                                    <td className="px-6 py-3">
                                                        <span className="text-sm font-medium text-foreground">
                                                            {getCustomerName(inc)}
                                                        </span>
                                                    </td>
                                                    <td className="px-6 py-3">
                                                        <span className="text-sm text-foreground">
                                                            {getProductName(inc)}
                                                        </span>
                                                    </td>
                                                    <td className="px-6 py-3 text-right">
                                                        <span className="text-sm text-foreground font-mono">
                                                            {inc.minQuantity != null ? inc.minQuantity : "-"}
                                                        </span>
                                                    </td>
                                                    <td className="px-6 py-3 text-right">
                                                        <span className="text-sm font-bold text-foreground font-mono">
                                                            ₹{Number(inc.discountRate).toFixed(2)}/unit
                                                        </span>
                                                    </td>
                                                    <td className="px-6 py-3 text-center">
                                                        <span
                                                            className={`px-2.5 py-1 rounded-full text-[10px] font-bold uppercase ${
                                                                inc.active
                                                                    ? "bg-green-500/10 text-green-500"
                                                                    : "bg-gray-500/10 text-gray-500"
                                                            }`}
                                                        >
                                                            {inc.active ? "Active" : "Inactive"}
                                                        </span>
                                                    </td>
                                                    <td className="px-6 py-3 text-center">
                                                        <PermissionGate permission="CUSTOMER_MANAGE">
                                                            <div className="flex items-center justify-center gap-1">
                                                                <button
                                                                    onClick={() => handleOpenEdit(inc)}
                                                                    title="Edit"
                                                                    className="p-1.5 rounded-lg hover:bg-blue-500/10 text-muted-foreground hover:text-blue-500 opacity-100 md:opacity-0 group-hover:opacity-100 transition-all"
                                                                >
                                                                    <Edit3 className="w-3.5 h-3.5" />
                                                                </button>
                                                                <button
                                                                    onClick={() => handleToggleActive(inc)}
                                                                    title={inc.active ? "Deactivate" : "Activate"}
                                                                    className={`p-1.5 rounded-lg opacity-100 md:opacity-0 group-hover:opacity-100 transition-all ${
                                                                        inc.active
                                                                            ? "hover:bg-amber-500/10 text-muted-foreground hover:text-amber-500"
                                                                            : "hover:bg-green-500/10 text-muted-foreground hover:text-green-500"
                                                                    }`}
                                                                >
                                                                    {inc.active ? (
                                                                        <ToggleRight className="w-3.5 h-3.5" />
                                                                    ) : (
                                                                        <ToggleLeft className="w-3.5 h-3.5" />
                                                                    )}
                                                                </button>
                                                                <button
                                                                    onClick={() => handleDelete(inc)}
                                                                    title="Delete"
                                                                    className="p-1.5 rounded-lg hover:bg-red-500/10 text-muted-foreground hover:text-red-500 opacity-100 md:opacity-0 group-hover:opacity-100 transition-all"
                                                                >
                                                                    <Trash2 className="w-3.5 h-3.5" />
                                                                </button>
                                                            </div>
                                                        </PermissionGate>
                                                    </td>
                                                </tr>
                                            ))
                                        )}
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

            {/* Create / Edit Modal */}
            <Modal
                isOpen={isModalOpen}
                onClose={() => {
                    setIsModalOpen(false);
                    resetForm();
                }}
                title={editingIncentive ? "Edit Incentive" : "Add Incentive"}
            >
                <form onSubmit={handleSubmit} className="space-y-4">
                    {/* Customer */}
                    <div ref={customerDropdownRef} className="relative">
                        <label className="block text-sm font-medium text-foreground mb-1.5">
                            Customer <span className="text-red-500">*</span>
                        </label>
                        <input type="hidden" required value={formCustomerId} />
                        <input
                            type="text"
                            required={!formCustomerId}
                            disabled={!!editingIncentive}
                            value={customerSearch}
                            onChange={(e) => handleCustomerSearch(e.target.value)}
                            onFocus={() => { if (customerResults.length > 0) setShowCustomerDropdown(true); }}
                            placeholder="Type to search customer..."
                            className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50 disabled:opacity-50"
                            autoComplete="off"
                        />
                        {isSearchingCustomers && (
                            <div className="absolute right-3 top-[42px]">
                                <div className="w-4 h-4 border-2 border-primary/20 border-t-primary rounded-full animate-spin" />
                            </div>
                        )}
                        {showCustomerDropdown && customerResults.length > 0 && (
                            <div className="absolute z-50 w-full mt-1 bg-card border border-border rounded-xl shadow-lg max-h-48 overflow-y-auto">
                                {customerResults.map((c) => (
                                    <button
                                        key={c.id}
                                        type="button"
                                        onClick={() => selectCustomer(c)}
                                        className="w-full text-left px-4 py-2.5 text-sm text-foreground hover:bg-white/10 transition-colors first:rounded-t-xl last:rounded-b-xl"
                                    >
                                        {c.name}
                                    </button>
                                ))}
                            </div>
                        )}
                    </div>

                    {/* Product */}
                    <div>
                        <label className="block text-sm font-medium text-foreground mb-1.5">
                            Product <span className="text-red-500">*</span>
                        </label>
                        <StyledSelect
                            value={formProductId}
                            onChange={(val) => setFormProductId(val)}
                            options={[
                                { value: "", label: "Select product..." },
                                ...products.map((p) => ({ value: String(p.id), label: p.name })),
                            ]}
                            placeholder="Select product..."
                            className={`w-full ${!!editingIncentive ? "opacity-50 pointer-events-none" : ""}`}
                        />
                    </div>

                    {/* Discount Rate */}
                    <div>
                        <label className="block text-sm font-medium text-foreground mb-1.5">
                            Discount Rate (₹/unit) <span className="text-red-500">*</span>
                        </label>
                        <input
                            type="number"
                            step="0.01"
                            min="0.01"
                            required
                            value={formDiscountRate}
                            onChange={(e) => setFormDiscountRate(e.target.value)}
                            className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground font-mono focus:outline-none focus:ring-2 focus:ring-primary/50"
                            placeholder="0.00"
                        />
                    </div>

                    {/* Min Quantity */}
                    <div>
                        <label className="block text-sm font-medium text-foreground mb-1.5">
                            Min Quantity
                        </label>
                        <input
                            type="number"
                            step="0.01"
                            min="0"
                            value={formMinQuantity}
                            onChange={(e) => setFormMinQuantity(e.target.value)}
                            className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground font-mono focus:outline-none focus:ring-2 focus:ring-primary/50"
                            placeholder="Optional"
                        />
                    </div>

                    <div className="flex justify-end gap-3 pt-6 border-t border-border mt-6">
                        <button
                            type="button"
                            onClick={() => {
                                setIsModalOpen(false);
                                resetForm();
                            }}
                            className="px-6 py-2.5 rounded-xl font-medium text-foreground bg-black/5 dark:bg-white/5 hover:bg-black/10 dark:hover:bg-white/10 transition-colors"
                        >
                            Cancel
                        </button>
                        <button
                            type="submit"
                            disabled={isSubmitting}
                            className="btn-gradient px-8 py-2.5 rounded-xl font-medium shadow-lg hover:shadow-xl transition-all disabled:opacity-50"
                        >
                            {isSubmitting ? "Saving..." : editingIncentive ? "Update" : "Create"}
                        </button>
                    </div>
                </form>
            </Modal>
        </div>
    );
}
