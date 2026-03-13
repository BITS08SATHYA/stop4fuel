"use client";

import React, { useState, useEffect } from "react";
import { Plus, Search, Edit2, Trash2, Truck } from "lucide-react";
import { Modal } from "@/components/ui/modal";
import { VehicleStep } from "@/components/steps/vehicle-step";
import { Badge } from "@/components/ui/badge";
import { API_BASE_URL } from "@/lib/api/station";

export default function VehiclesPage() {
    const [vehicles, setVehicles] = useState<any[]>([]);
    const [customers, setCustomers] = useState<any[]>([]);
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [formData, setFormData] = useState<any>({});
    const [loading, setLoading] = useState(false);
    const [searchQuery, setSearchQuery] = useState("");
    const [statusFilter, setStatusFilter] = useState<string>("ALL");
    const [customerFilter, setCustomerFilter] = useState<string>("");

    const handleToggleVehicleStatus = async (id: number) => {
        try {
            const res = await fetch(`${API_BASE_URL}/vehicles/${id}/toggle-status`, { method: "PATCH" });
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
            const res = await fetch(`${API_BASE_URL}/vehicles`);
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
            const res = await fetch(`${API_BASE_URL}/customers`);
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
            customerId: vehicle.customer?.id,
            vehicleType: vehicle.vehicleType?.id,
            fuelType: vehicle.preferredProduct?.id,
        });
        setIsModalOpen(true);
    };

    const handleDelete = async (id: number) => {
        if (!confirm("Are you sure you want to delete this vehicle?")) return;
        try {
            const res = await fetch(`${API_BASE_URL}/vehicles/${id}`, {
                method: "DELETE",
            });
            if (res.ok) {
                fetchVehicles();
            }
        } catch (error) {
            console.error("Failed to delete vehicle", error);
        }
    };

    const handleSave = async () => {
        setLoading(true);
        try {
            const url = formData.id 
                ? `${API_BASE_URL}/vehicles/${formData.id}`
                : `${API_BASE_URL}/vehicles`;
            const method = formData.id ? "PUT" : "POST";

            const payload: any = {
                vehicleNumber: formData.vehicleNumber,
                maxCapacity: formData.maxCapacity,
                customer: { id: formData.customerId },
            };

            if (formData.vehicleType) {
                payload.vehicleType = { id: formData.vehicleType };
            }

            if (formData.fuelType) {
                payload.preferredProduct = { id: formData.fuelType };
            }

            const res = await fetch(url, {
                method: method,
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(payload),
            });
            if (res.ok) {
                setIsModalOpen(false);
                setFormData({});
                fetchVehicles();
            }
        } catch (error) {
            console.error("Failed to save vehicle", error);
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="p-8 min-h-screen bg-background text-foreground">
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
                    <button
                        onClick={() => {
                            setFormData({});
                            setIsModalOpen(true);
                        }}
                        className="btn-gradient px-6 py-3 rounded-xl font-medium flex items-center gap-2"
                    >
                        <Plus className="w-5 h-5" />
                        Add New Vehicle
                    </button>
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
                    <select
                        value={customerFilter}
                        onChange={(e) => setCustomerFilter(e.target.value)}
                        className="px-4 py-2.5 bg-card border border-border rounded-xl text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50 max-w-[200px]"
                    >
                        <option value="">All Customers</option>
                        {customers.map((c) => (
                            <option key={c.id} value={c.id}>{c.name}</option>
                        ))}
                    </select>
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
                            {vehicles.filter((v) => {
                                const q = searchQuery.toLowerCase();
                                const matchesSearch = !searchQuery || v.vehicleNumber?.toLowerCase().includes(q) || v.customer?.name?.toLowerCase().includes(q);
                                const matchesStatus = statusFilter === "ALL" || (v.status || "ACTIVE") === statusFilter;
                                const matchesCustomer = !customerFilter || String(v.customer?.id) === customerFilter;
                                return matchesSearch && matchesStatus && matchesCustomer;
                            }).map((vehicle) => {
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
                </div>
            </div>

            <Modal
                isOpen={isModalOpen}
                onClose={() => {
                    setIsModalOpen(false);
                    setFormData({});
                }}
                title={formData.id ? "Edit Vehicle" : "Add New Vehicle"}
            >
                <div className="p-6">
                    <div className="mb-6">
                        <label className="block text-sm font-medium text-muted-foreground mb-2">
                            Select Customer
                        </label>
                        <select
                            value={formData.customerId || ""}
                            onChange={(e) => setFormData({ ...formData, customerId: e.target.value })}
                            className="w-full bg-white/5 border border-white/10 rounded-lg px-4 py-2 text-white focus:outline-none focus:ring-2 focus:ring-cyan-500/50"
                        >
                            <option value="" className="bg-slate-900">Select Customer</option>
                            {customers.map(customer => (
                                <option key={customer.id} value={customer.id} className="bg-slate-900">{customer.name}</option>
                            ))}
                        </select>
                    </div>
                    
                    <VehicleStep data={formData} updateData={setFormData} />
                    
                    <div className="flex justify-end gap-3 mt-8">
                        <button
                            onClick={() => setIsModalOpen(false)}
                            className="px-6 py-2.5 rounded-xl text-sm font-medium text-muted-foreground hover:text-white hover:bg-white/5 transition-colors"
                        >
                            Cancel
                        </button>
                        <button
                            onClick={handleSave}
                            disabled={loading || !formData.vehicleNumber || !formData.customerId}
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
