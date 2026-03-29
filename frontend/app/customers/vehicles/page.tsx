"use client";

import React, { useState, useEffect, useMemo, useRef } from "react";
import { Plus, Search, Edit2, Trash2, Truck, X } from "lucide-react";
import { Modal } from "@/components/ui/modal";
import { PermissionGate } from "@/components/permission-gate";
import { VehicleStep } from "@/components/steps/vehicle-step";
import { Badge } from "@/components/ui/badge";
import { API_BASE_URL } from "@/lib/api/station";
import { fetchWithAuth } from "@/lib/api/fetch-with-auth";
import { TablePagination, useClientPagination } from "@/components/ui/table-pagination";

function CustomerAutocomplete({
    customers,
    value,
    onChange,
    placeholder = "Search customer...",
    className = "",
}: {
    customers: any[];
    value: string;
    onChange: (id: string, name?: string) => void;
    placeholder?: string;
    className?: string;
}) {
    const [query, setQuery] = useState("");
    const [isOpen, setIsOpen] = useState(false);
    const [highlightIndex, setHighlightIndex] = useState(-1);
    const wrapperRef = useRef<HTMLDivElement>(null);
    const listRef = useRef<HTMLUListElement>(null);

    const selectedCustomer = customers.find((c) => String(c.id) === String(value));

    const filtered = useMemo(() => {
        if (!query) return customers;
        const q = query.toLowerCase();
        return customers.filter((c) => c.name?.toLowerCase().includes(q));
    }, [customers, query]);

    useEffect(() => {
        const handleClickOutside = (e: MouseEvent) => {
            if (wrapperRef.current && !wrapperRef.current.contains(e.target as Node)) {
                setIsOpen(false);
            }
        };
        document.addEventListener("mousedown", handleClickOutside);
        return () => document.removeEventListener("mousedown", handleClickOutside);
    }, []);

    useEffect(() => {
        setHighlightIndex(-1);
    }, [query]);

    const handleSelect = (customer: any) => {
        onChange(String(customer.id), customer.name);
        setQuery("");
        setIsOpen(false);
    };

    const handleKeyDown = (e: React.KeyboardEvent) => {
        if (!isOpen) return;
        if (e.key === "ArrowDown") {
            e.preventDefault();
            setHighlightIndex((prev) => Math.min(prev + 1, filtered.length - 1));
        } else if (e.key === "ArrowUp") {
            e.preventDefault();
            setHighlightIndex((prev) => Math.max(prev - 1, 0));
        } else if (e.key === "Enter" && highlightIndex >= 0) {
            e.preventDefault();
            handleSelect(filtered[highlightIndex]);
        } else if (e.key === "Escape") {
            setIsOpen(false);
        }
    };

    useEffect(() => {
        if (highlightIndex >= 0 && listRef.current) {
            const item = listRef.current.children[highlightIndex] as HTMLElement;
            item?.scrollIntoView({ block: "nearest" });
        }
    }, [highlightIndex]);

    return (
        <div ref={wrapperRef} className={`relative ${className}`}>
            {selectedCustomer && !isOpen ? (
                <div className="flex items-center gap-2 w-full bg-white/5 border border-white/10 rounded-lg px-4 py-2 text-foreground">
                    <span className="flex-1 truncate">{selectedCustomer.name}</span>
                    <button
                        type="button"
                        onClick={() => {
                            onChange("", "");
                            setQuery("");
                            setIsOpen(true);
                        }}
                        className="text-muted-foreground hover:text-foreground"
                    >
                        <X className="w-4 h-4" />
                    </button>
                </div>
            ) : (
                <input
                    type="text"
                    value={query}
                    onChange={(e) => {
                        setQuery(e.target.value);
                        setIsOpen(true);
                    }}
                    onFocus={() => setIsOpen(true)}
                    onKeyDown={handleKeyDown}
                    placeholder={placeholder}
                    className="w-full bg-white/5 border border-white/10 rounded-lg px-4 py-2 text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-cyan-500/50"
                />
            )}
            {isOpen && (
                <ul
                    ref={listRef}
                    className="absolute z-50 mt-1 w-full max-h-60 overflow-auto bg-card border border-border rounded-lg shadow-xl"
                >
                    {filtered.length === 0 ? (
                        <li className="px-4 py-3 text-sm text-muted-foreground">No customers found</li>
                    ) : (
                        filtered.map((c, i) => (
                            <li
                                key={c.id}
                                onClick={() => handleSelect(c)}
                                className={`px-4 py-2.5 text-sm cursor-pointer transition-colors ${
                                    i === highlightIndex
                                        ? "bg-primary/20 text-foreground"
                                        : "text-muted-foreground hover:bg-muted/50 hover:text-foreground"
                                }`}
                            >
                                {c.name}
                            </li>
                        ))
                    )}
                </ul>
            )}
        </div>
    );
}

export default function VehiclesPage() {
    const [vehicles, setVehicles] = useState<any[]>([]);
    const [customers, setCustomers] = useState<any[]>([]);
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [formData, setFormData] = useState<any>({});
    const [loading, setLoading] = useState(false);
    const [formErrors, setFormErrors] = useState<Record<string, string>>({});
    const [searchQuery, setSearchQuery] = useState("");
    const [statusFilter, setStatusFilter] = useState<string>("ALL");
    const [customerFilter, setCustomerFilter] = useState<string>("");

    const filteredVehicles = useMemo(() => vehicles.filter((v) => {
        const q = searchQuery.toLowerCase();
        const matchesSearch = !searchQuery || v.vehicleNumber?.toLowerCase().includes(q) || v.customer?.name?.toLowerCase().includes(q);
        const matchesStatus = statusFilter === "ALL" || (v.status || "ACTIVE") === statusFilter;
        const matchesCustomer = !customerFilter || String(v.customer?.id) === customerFilter;
        return matchesSearch && matchesStatus && matchesCustomer;
    }), [vehicles, searchQuery, statusFilter, customerFilter]);

    const { page, setPage, totalPages, totalElements, pageSize, paginatedData: pagedVehicles } = useClientPagination(filteredVehicles);

    const handleToggleVehicleStatus = async (id: number) => {
        try {
            const res = await fetchWithAuth(`${API_BASE_URL}/vehicles/${id}/toggle-status`, { method: "PATCH" });
            if (res.ok) fetchVehicles();
        } catch (error) {
            console.error("Failed to toggle vehicle status", error);
        }
    };

    useEffect(() => {
        fetchVehicles();
        fetchCustomers();
    }, []);

    const fetchVehicles = async () => {
        try {
            const res = await fetchWithAuth(`${API_BASE_URL}/vehicles`);
            if (res.ok) {
                const data = await res.json();
                setVehicles(Array.isArray(data) ? data : data.content || []);
            }
        } catch (error) {
            console.error("Failed to fetch vehicles", error);
        }
    };

    const fetchCustomers = async () => {
        try {
            const res = await fetchWithAuth(`${API_BASE_URL}/customers?size=1000`);
            if (res.ok) {
                const data = await res.json();
                setCustomers(Array.isArray(data) ? data : data.content || []);
            }
        } catch (error) {
            console.error("Failed to fetch customers", error);
        }
    };

    const handleEdit = (vehicle: any) => {
        setFormData({
            id: vehicle.id,
            vehicleNumber: vehicle.vehicleNumber,
            maxCapacity: vehicle.maxCapacity,
            maxLitersPerMonth: vehicle.maxLitersPerMonth,
            customerId: vehicle.customer?.id,
            vehicleType: vehicle.vehicleType?.id,
            fuelType: vehicle.preferredProduct?.id,
        });
        setIsModalOpen(true);
    };

    const handleDelete = async (id: number) => {
        if (!confirm("Are you sure you want to delete this vehicle?")) return;
        try {
            const res = await fetchWithAuth(`${API_BASE_URL}/vehicles/${id}`, {
                method: "DELETE",
            });
            if (res.ok) {
                fetchVehicles();
            }
        } catch (error) {
            console.error("Failed to delete vehicle", error);
        }
    };

    const validateForm = (): Record<string, string> => {
        const errors: Record<string, string> = {};

        if (!formData.customerId) {
            errors.customer = "Please select a customer";
        }

        const vehicleNumber = (formData.vehicleNumber || "").trim();
        if (!vehicleNumber) {
            errors.vehicleNumber = "Vehicle number is required";
        } else if (vehicleNumber.length > 20) {
            errors.vehicleNumber = "Vehicle number must not exceed 20 characters";
        }

        if (!formData.vehicleType) {
            errors.vehicleType = "Please select a vehicle type";
        }

        if (!formData.fuelType) {
            errors.fuelType = "Please select a fuel type";
        }

        if (formData.maxCapacity !== undefined && formData.maxCapacity !== "" && Number(formData.maxCapacity) < 0) {
            errors.maxCapacity = "Max capacity must be zero or positive";
        }

        if (formData.maxLitersPerMonth !== undefined && formData.maxLitersPerMonth !== "" && Number(formData.maxLitersPerMonth) < 0) {
            errors.maxLitersPerMonth = "Monthly liter limit must be zero or positive";
        }

        return errors;
    };

    const handleSave = async () => {
        const errors = validateForm();
        setFormErrors(errors);
        if (Object.keys(errors).length > 0) return;

        setLoading(true);
        try {
            const url = formData.id
                ? `${API_BASE_URL}/vehicles/${formData.id}`
                : `${API_BASE_URL}/vehicles`;
            const method = formData.id ? "PUT" : "POST";

            const payload: any = {
                vehicleNumber: formData.vehicleNumber.trim(),
                maxCapacity: formData.maxCapacity,
                maxLitersPerMonth: formData.maxLitersPerMonth || null,
                customer: { id: formData.customerId },
            };

            if (formData.vehicleType) {
                payload.vehicleType = { id: formData.vehicleType };
            }

            if (formData.fuelType) {
                payload.preferredProduct = { id: formData.fuelType };
            }

            const res = await fetchWithAuth(url, {
                method: method,
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(payload),
            });
            if (res.ok) {
                setIsModalOpen(false);
                setFormData({});
                setFormErrors({});
                fetchVehicles();
            } else {
                const errorData = await res.json().catch(() => null);
                if (errorData?.error) {
                    if (errorData.error.toLowerCase().includes("already exists")) {
                        setFormErrors({ vehicleNumber: "This vehicle number already exists" });
                    } else {
                        setFormErrors({ _general: errorData.error });
                    }
                }
            }
        } catch (error) {
            console.error("Failed to save vehicle", error);
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="p-6 h-screen overflow-hidden bg-background text-foreground">
            <div className="max-w-7xl mx-auto">
                <div className="flex justify-between items-center mb-8">
                    <div>
                        <h1 className="text-4xl font-bold tracking-tight">
                            Vehicle <span className="text-gradient">Management</span>
                        </h1>
                        <p className="text-muted-foreground mt-2">
                            Manage fleet vehicles and their customer associations.
                        </p>
                    </div>
                    <PermissionGate permission="VEHICLE_MANAGE">
                        <button
                            onClick={() => {
                                setFormData({});
                                setFormErrors({});
                                setIsModalOpen(true);
                            }}
                            className="btn-gradient px-6 py-3 rounded-xl font-medium flex items-center gap-2"
                        >
                            <Plus className="w-5 h-5" />
                            Add New Vehicle
                        </button>
                    </PermissionGate>
                </div>

                {/* Filter Bar */}
                <div className="mb-6 flex flex-wrap gap-3 items-center">
                    <div className="relative flex-1 min-w-[200px] max-w-md">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                        <input
                            type="text"
                            placeholder="Search by vehicle number or customer..."
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            className="w-full pl-10 pr-4 py-2.5 bg-card border border-border rounded-xl text-foreground text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                        />
                    </div>
                    <select
                        value={statusFilter}
                        onChange={(e) => setStatusFilter(e.target.value)}
                        className="px-4 py-2.5 bg-card border border-border rounded-xl text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
                    >
                        <option value="ALL">All Status</option>
                        <option value="ACTIVE">Active</option>
                        <option value="BLOCKED">Blocked</option>
                        <option value="INACTIVE">Inactive</option>
                    </select>
                    <CustomerAutocomplete
                        customers={customers}
                        value={customerFilter}
                        onChange={(id) => setCustomerFilter(id)}
                        placeholder="Filter by customer..."
                        className="w-[220px]"
                    />
                </div>

                <div className="bg-card border border-border rounded-2xl overflow-hidden shadow-xl">
                    <table className="w-full text-left">
                        <thead>
                            <tr className="border-b border-border bg-muted/50">
                                <th className="px-6 py-4 text-sm font-semibold">Vehicle Number</th>
                                <th className="px-6 py-4 text-sm font-semibold">Customer</th>
                                <th className="px-6 py-4 text-sm font-semibold">Monthly Limit</th>
                                <th className="px-6 py-4 text-sm font-semibold">Consumed</th>
                                <th className="px-6 py-4 text-sm font-semibold">Status</th>
                                <th className="px-6 py-4 text-sm font-semibold text-right">Actions</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-border">
                            {pagedVehicles.map((vehicle) => {
                                const vStatus = vehicle.status || "ACTIVE";
                                return (
                                    <tr key={vehicle.id} className="hover:bg-muted/30 transition-colors">
                                        <td className="px-6 py-4 font-medium flex items-center gap-2">
                                            <Truck className="w-4 h-4 text-primary" />
                                            {vehicle.vehicleNumber}
                                        </td>
                                        <td className="px-6 py-4 text-muted-foreground">{vehicle.customer?.name || "Unassigned"}</td>
                                        <td className="px-6 py-4 text-muted-foreground">
                                            {vehicle.maxLitersPerMonth ? `${vehicle.maxLitersPerMonth} L` : "-"}
                                        </td>
                                        <td className="px-6 py-4 text-muted-foreground">
                                            {vehicle.consumedLiters ? `${vehicle.consumedLiters} L` : "0 L"}
                                        </td>
                                        <td className="px-6 py-4">
                                            <button onClick={() => handleToggleVehicleStatus(vehicle.id)}>
                                                <Badge
                                                    variant={vStatus === "ACTIVE" ? "success" : vStatus === "BLOCKED" ? "danger" : "warning"}
                                                    className="cursor-pointer hover:opacity-80 transition-opacity"
                                                >
                                                    {vStatus === "ACTIVE" ? "Active" : vStatus === "BLOCKED" ? "Blocked" : "Inactive"}
                                                </Badge>
                                            </button>
                                        </td>
                                        <td className="px-6 py-4 text-right">
                                            <PermissionGate permission="VEHICLE_MANAGE">
                                                <div className="flex justify-end gap-2">
                                                    <button
                                                        onClick={() => handleEdit(vehicle)}
                                                        className="p-2 hover:bg-primary/10 rounded-lg text-primary transition-colors"
                                                    >
                                                        <Edit2 className="w-4 h-4" />
                                                    </button>
                                                    <button
                                                        onClick={() => handleDelete(vehicle.id)}
                                                        className="p-2 hover:bg-destructive/10 rounded-lg text-destructive transition-colors"
                                                    >
                                                        <Trash2 className="w-4 h-4" />
                                                    </button>
                                                </div>
                                            </PermissionGate>
                                        </td>
                                    </tr>
                                );
                            })}
                        </tbody>
                    </table>
                    {vehicles.length === 0 && (
                        <div className="p-12 text-center text-muted-foreground">
                            No vehicles found. Click "Add New Vehicle" to get started.
                        </div>
                    )}
                    <TablePagination
                        page={page}
                        totalPages={totalPages}
                        totalElements={totalElements}
                        pageSize={pageSize}
                        onPageChange={setPage}
                    />
                </div>
            </div>

            <Modal
                isOpen={isModalOpen}
                onClose={() => {
                    setIsModalOpen(false);
                    setFormData({});
                    setFormErrors({});
                }}
                title={formData.id ? "Edit Vehicle" : "Add New Vehicle"}
            >
                <div className="p-6">
                    {formErrors._general && (
                        <div className="mb-4 p-3 bg-red-500/10 border border-red-500/30 rounded-lg text-red-400 text-sm">
                            {formErrors._general}
                        </div>
                    )}
                    <div className="mb-6">
                        <label className="block text-sm font-medium text-muted-foreground mb-2">
                            Select Customer <span className="text-red-400">*</span>
                        </label>
                        <CustomerAutocomplete
                            customers={customers}
                            value={formData.customerId || ""}
                            onChange={(id) => {
                                setFormData({ ...formData, customerId: id });
                                if (id) setFormErrors((prev) => { const { customer, ...rest } = prev; return rest; });
                            }}
                            placeholder="Type to search customer..."
                        />
                        {formErrors.customer && (
                            <p className="text-[11px] text-red-400 mt-1">{formErrors.customer}</p>
                        )}
                    </div>

                    <VehicleStep data={formData} updateData={(d) => { setFormData(d); setFormErrors({}); }} errors={formErrors} />

                    <div className="flex justify-end gap-3 mt-8">
                        <button
                            onClick={() => { setIsModalOpen(false); setFormErrors({}); }}
                            className="px-6 py-2.5 rounded-xl text-sm font-medium text-muted-foreground hover:text-white hover:bg-white/5 transition-colors"
                        >
                            Cancel
                        </button>
                        <button
                            onClick={handleSave}
                            disabled={loading}
                            className="btn-gradient px-8 py-2.5 rounded-xl text-sm font-bold text-white shadow-lg disabled:opacity-50"
                        >
                            {loading ? "Saving..." : "Save Vehicle"}
                        </button>
                    </div>
                </div>
            </Modal>
        </div>
    );
}
