"use client";

import React, { useState, useEffect, useCallback, useMemo } from "react";
import { Users, Truck, ArrowRight, Check, X, AlertCircle, Link2, Unlink } from "lucide-react";
import { GlassCard } from "@/components/ui/glass-card";
import { PermissionGate } from "@/components/permission-gate";
import { Badge } from "@/components/ui/badge";
import { CustomerAutocomplete } from "@/components/ui/customer-autocomplete";
import { API_BASE_URL } from "@/lib/api/station";
import { fetchWithAuth } from "@/lib/api/fetch-with-auth";
import { TablePagination, useClientPagination } from "@/components/ui/table-pagination";

const API = API_BASE_URL;

type Group = { id: number; groupName: string; description?: string };
type Customer = { id: number; name: string; phoneNumbers?: string[]; emails?: string[]; group?: Group | null; isActive: boolean };
type Vehicle = { id: number; vehicleNumber: string; maxCapacity?: number; customer?: Customer | null; vehicleType?: { typeName: string } };

type Tab = "customer-group" | "vehicle-customer";

export default function MappingsPage() {
    const [activeTab, setActiveTab] = useState<Tab>("customer-group");

    return (
        <div className="p-6 h-screen overflow-hidden bg-background text-foreground">
            <div className="max-w-7xl mx-auto">
                <div className="mb-8">
                    <h1 className="text-4xl font-bold tracking-tight">
                        Customer <span className="text-gradient">Mappings</span>
                    </h1>
                    <p className="text-muted-foreground mt-2">
                        Assign and reassign relationships between groups, customers, and vehicles.
                    </p>
                </div>

                {/* Tabs */}
                <div className="flex gap-2 mb-6">
                    <button
                        onClick={() => setActiveTab("customer-group")}
                        className={`flex items-center gap-2 px-5 py-2.5 rounded-xl text-sm font-medium transition-colors ${
                            activeTab === "customer-group"
                                ? "bg-primary text-primary-foreground shadow-lg"
                                : "bg-secondary border border-border text-muted-foreground hover:text-foreground"
                        }`}
                    >
                        <Users className="w-4 h-4" />
                        Customer → Group
                    </button>
                    <button
                        onClick={() => setActiveTab("vehicle-customer")}
                        className={`flex items-center gap-2 px-5 py-2.5 rounded-xl text-sm font-medium transition-colors ${
                            activeTab === "vehicle-customer"
                                ? "bg-primary text-primary-foreground shadow-lg"
                                : "bg-secondary border border-border text-muted-foreground hover:text-foreground"
                        }`}
                    >
                        <Truck className="w-4 h-4" />
                        Vehicle → Customer
                    </button>
                </div>

                {activeTab === "customer-group" && <CustomerGroupMapping />}
                {activeTab === "vehicle-customer" && <VehicleCustomerMapping />}
            </div>
        </div>
    );
}

/* ─────────────────────────────────────────────
   Customer → Group Mapping
   ───────────────────────────────────────────── */
function CustomerGroupMapping() {
    const [customers, setCustomers] = useState<Customer[]>([]);
    const [groups, setGroups] = useState<Group[]>([]);
    const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
    const [targetGroupId, setTargetGroupId] = useState<string>("");
    const [filter, setFilter] = useState<"all" | "unassigned" | "assigned">("all");
    const [loading, setLoading] = useState(false);
    const [message, setMessage] = useState<{ type: "success" | "error"; text: string } | null>(null);

    const fetchData = useCallback(async () => {
        try {
            const [custRes, groupRes, unassignedRes] = await Promise.all([
                fetchWithAuth(`${API}/customers?page=0&size=1000`),
                fetchWithAuth(`${API}/groups`),
                fetchWithAuth(`${API}/mappings/unassigned-customers`),
            ]);
            const custData = await custRes.json();
            const groupData = await groupRes.json();
            const unassignedData = await unassignedRes.json();

            const allCustomers: Customer[] = custData.content || custData || [];
            const unassignedIds = new Set((unassignedData as Customer[]).map((c) => c.id));

            // Mark unassigned ones clearly
            const merged = allCustomers.map((c) => ({
                ...c,
                group: unassignedIds.has(c.id) ? null : c.group,
            }));

            setCustomers(merged);
            setGroups(Array.isArray(groupData) ? groupData : groupData.content || []);
        } catch (err) {
            console.error("Failed to fetch data", err);
        }
    }, []);

    useEffect(() => { fetchData(); }, [fetchData]);

    const filtered = customers.filter((c) => {
        if (filter === "unassigned") return !c.group;
        if (filter === "assigned") return !!c.group;
        return true;
    });

    const { page: cgPage, setPage: setCgPage, totalPages: cgTotalPages, totalElements: cgTotalElements, pageSize: cgPageSize, paginatedData: cgPaged } = useClientPagination(filtered);

    const toggleSelect = (id: number) => {
        setSelectedIds((prev) => {
            const next = new Set(prev);
            next.has(id) ? next.delete(id) : next.add(id);
            return next;
        });
    };

    const toggleSelectAll = () => {
        if (selectedIds.size === filtered.length) {
            setSelectedIds(new Set());
        } else {
            setSelectedIds(new Set(filtered.map((c) => c.id)));
        }
    };

    const handleAssign = async () => {
        if (selectedIds.size === 0 || !targetGroupId) return;
        setLoading(true);
        try {
            const res = await fetchWithAuth(`${API}/mappings/assign-customers-to-group`, {
                method: "PATCH",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ customerIds: [...selectedIds], groupId: Number(targetGroupId) }),
            });
            if (res.ok) {
                setMessage({ type: "success", text: `${selectedIds.size} customer(s) assigned to group.` });
                setSelectedIds(new Set());
                setTargetGroupId("");
                await fetchData();
            } else {
                setMessage({ type: "error", text: "Failed to assign customers." });
            }
        } catch { setMessage({ type: "error", text: "Network error." }); }
        finally { setLoading(false); setTimeout(() => setMessage(null), 3000); }
    };

    const handleUnassign = async () => {
        if (selectedIds.size === 0) return;
        setLoading(true);
        try {
            const res = await fetchWithAuth(`${API}/mappings/unassign-customers-from-group`, {
                method: "PATCH",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ customerIds: [...selectedIds] }),
            });
            if (res.ok) {
                setMessage({ type: "success", text: `${selectedIds.size} customer(s) unassigned from group.` });
                setSelectedIds(new Set());
                await fetchData();
            } else {
                setMessage({ type: "error", text: "Failed to unassign customers." });
            }
        } catch { setMessage({ type: "error", text: "Network error." }); }
        finally { setLoading(false); setTimeout(() => setMessage(null), 3000); }
    };

    return (
        <div className="space-y-4">
            {/* Action bar */}
            <GlassCard className="flex flex-wrap items-center gap-4">
                {/* Filter */}
                <div className="flex gap-1 bg-secondary rounded-lg p-1">
                    {(["all", "unassigned", "assigned"] as const).map((f) => (
                        <button
                            key={f}
                            onClick={() => { setFilter(f); setSelectedIds(new Set()); }}
                            className={`px-3 py-1.5 text-xs font-medium rounded-md transition-colors capitalize ${
                                filter === f ? "bg-card text-foreground shadow" : "text-muted-foreground hover:text-foreground"
                            }`}
                        >
                            {f}
                        </button>
                    ))}
                </div>

                <div className="h-8 w-px bg-border" />

                {/* Assign controls */}
                <select
                    value={targetGroupId}
                    onChange={(e) => setTargetGroupId(e.target.value)}
                    className="bg-secondary border border-border rounded-lg px-3 py-2 text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-accent/50"
                >
                    <option value="">Select group...</option>
                    {groups.map((g) => (
                        <option key={g.id} value={g.id}>{g.groupName}</option>
                    ))}
                </select>

                <PermissionGate permission="CUSTOMER_MANAGE">
                    <button
                        onClick={handleAssign}
                        disabled={loading || selectedIds.size === 0 || !targetGroupId}
                        className="btn-gradient px-4 py-2 rounded-lg text-sm font-medium flex items-center gap-2 disabled:opacity-50"
                    >
                        <Link2 className="w-4 h-4" />
                        Assign ({selectedIds.size})
                    </button>

                    <button
                        onClick={handleUnassign}
                        disabled={loading || selectedIds.size === 0}
                        className="px-4 py-2 rounded-lg text-sm font-medium border border-border text-muted-foreground hover:text-destructive hover:border-destructive transition-colors flex items-center gap-2 disabled:opacity-50"
                    >
                        <Unlink className="w-4 h-4" />
                        Unassign ({selectedIds.size})
                    </button>
                </PermissionGate>

                {message && (
                    <div className={`ml-auto flex items-center gap-2 text-sm ${message.type === "success" ? "text-green-500" : "text-destructive"}`}>
                        {message.type === "success" ? <Check className="w-4 h-4" /> : <AlertCircle className="w-4 h-4" />}
                        {message.text}
                    </div>
                )}
            </GlassCard>

            {/* Table */}
            <GlassCard className="overflow-hidden">
                <table className="w-full text-left">
                    <thead>
                        <tr className="border-b border-border bg-muted/50">
                            <th className="px-4 py-3 w-10">
                                <input
                                    type="checkbox"
                                    checked={filtered.length > 0 && selectedIds.size === filtered.length}
                                    onChange={toggleSelectAll}
                                    className="rounded border-border accent-primary"
                                />
                            </th>
                            <th className="px-4 py-3 text-sm font-semibold">Customer</th>
                            <th className="px-4 py-3 text-sm font-semibold">Phone</th>
                            <th className="px-4 py-3 text-sm font-semibold">Status</th>
                            <th className="px-4 py-3 text-sm font-semibold">Current Group</th>
                        </tr>
                    </thead>
                    <tbody className="divide-y divide-border">
                        {cgPaged.map((customer) => (
                            <tr
                                key={customer.id}
                                onClick={() => toggleSelect(customer.id)}
                                className={`hover:bg-muted/30 transition-colors cursor-pointer ${selectedIds.has(customer.id) ? "bg-primary/5" : ""}`}
                            >
                                <td className="px-4 py-3">
                                    <input
                                        type="checkbox"
                                        checked={selectedIds.has(customer.id)}
                                        onChange={() => toggleSelect(customer.id)}
                                        className="rounded border-border accent-primary"
                                    />
                                </td>
                                <td className="px-4 py-3">
                                    <div className="flex items-center gap-3">
                                        <div className="w-8 h-8 rounded-full bg-primary/10 flex items-center justify-center text-primary font-bold text-xs">
                                            {customer.name?.charAt(0) || "C"}
                                        </div>
                                        <span className="font-medium">{customer.name}</span>
                                    </div>
                                </td>
                                <td className="px-4 py-3 text-sm text-muted-foreground">{customer.phoneNumbers?.[0] || "-"}</td>
                                <td className="px-4 py-3">
                                    <Badge variant={customer.isActive ? "success" : "default"}>
                                        {customer.isActive ? "Active" : "Inactive"}
                                    </Badge>
                                </td>
                                <td className="px-4 py-3">
                                    {customer.group ? (
                                        <span className="text-sm font-medium px-2.5 py-1 rounded-full bg-accent/10 text-accent">
                                            {customer.group.groupName}
                                        </span>
                                    ) : (
                                        <span className="text-sm text-muted-foreground italic flex items-center gap-1">
                                            <AlertCircle className="w-3 h-3" /> Unassigned
                                        </span>
                                    )}
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
                {filtered.length === 0 && (
                    <div className="p-12 text-center text-muted-foreground">
                        No customers found for this filter.
                    </div>
                )}
                <TablePagination
                    page={cgPage}
                    totalPages={cgTotalPages}
                    totalElements={cgTotalElements}
                    pageSize={cgPageSize}
                    onPageChange={setCgPage}
                />
            </GlassCard>
        </div>
    );
}

/* ─────────────────────────────────────────────
   Vehicle → Customer Mapping
   ───────────────────────────────────────────── */
function VehicleCustomerMapping() {
    const [vehicles, setVehicles] = useState<Vehicle[]>([]);
    const [customers, setCustomers] = useState<Customer[]>([]);
    const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
    const [targetCustomerId, setTargetCustomerId] = useState<string>("");
    const [filter, setFilter] = useState<"all" | "unassigned" | "assigned">("all");
    const [loading, setLoading] = useState(false);
    const [message, setMessage] = useState<{ type: "success" | "error"; text: string } | null>(null);

    const fetchData = useCallback(async () => {
        try {
            const [vehRes, custRes, unassignedRes] = await Promise.all([
                fetchWithAuth(`${API}/vehicles`),
                fetchWithAuth(`${API}/customers?page=0&size=1000`),
                fetchWithAuth(`${API}/mappings/unassigned-vehicles`),
            ]);
            const vehData = await vehRes.json();
            const custData = await custRes.json();
            const unassignedData = await unassignedRes.json();

            const allVehicles: Vehicle[] = Array.isArray(vehData) ? vehData : vehData.content || [];
            const unassignedIds = new Set((unassignedData as Vehicle[]).map((v) => v.id));

            const merged = allVehicles.map((v) => ({
                ...v,
                customer: unassignedIds.has(v.id) ? null : v.customer,
            }));

            setVehicles(merged);
            const allCustomers = custData.content || custData || [];
            setCustomers(Array.isArray(allCustomers) ? allCustomers : []);
        } catch (err) {
            console.error("Failed to fetch data", err);
        }
    }, []);

    useEffect(() => { fetchData(); }, [fetchData]);

    const filtered = vehicles.filter((v) => {
        if (filter === "unassigned") return !v.customer;
        if (filter === "assigned") return !!v.customer;
        return true;
    });

    const { page: vcPage, setPage: setVcPage, totalPages: vcTotalPages, totalElements: vcTotalElements, pageSize: vcPageSize, paginatedData: vcPaged } = useClientPagination(filtered);

    const toggleSelect = (id: number) => {
        setSelectedIds((prev) => {
            const next = new Set(prev);
            next.has(id) ? next.delete(id) : next.add(id);
            return next;
        });
    };

    const toggleSelectAll = () => {
        if (selectedIds.size === filtered.length) {
            setSelectedIds(new Set());
        } else {
            setSelectedIds(new Set(filtered.map((v) => v.id)));
        }
    };

    const handleAssign = async () => {
        if (selectedIds.size === 0 || !targetCustomerId) return;
        setLoading(true);
        try {
            const res = await fetchWithAuth(`${API}/mappings/assign-vehicles-to-customer`, {
                method: "PATCH",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ vehicleIds: [...selectedIds], customerId: Number(targetCustomerId) }),
            });
            if (res.ok) {
                setMessage({ type: "success", text: `${selectedIds.size} vehicle(s) assigned to customer.` });
                setSelectedIds(new Set());
                setTargetCustomerId("");
                await fetchData();
            } else {
                const errText = await res.text();
                setMessage({ type: "error", text: errText || "Failed to assign vehicles." });
            }
        } catch { setMessage({ type: "error", text: "Network error." }); }
        finally { setLoading(false); setTimeout(() => setMessage(null), 5000); }
    };

    const handleUnassign = async () => {
        if (selectedIds.size === 0) return;
        setLoading(true);
        try {
            const res = await fetchWithAuth(`${API}/mappings/unassign-vehicles-from-customer`, {
                method: "PATCH",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ vehicleIds: [...selectedIds] }),
            });
            if (res.ok) {
                setMessage({ type: "success", text: `${selectedIds.size} vehicle(s) unassigned from customer.` });
                setSelectedIds(new Set());
                await fetchData();
            } else {
                const errText = await res.text();
                setMessage({ type: "error", text: errText || "Failed to unassign vehicles." });
            }
        } catch { setMessage({ type: "error", text: "Network error." }); }
        finally { setLoading(false); setTimeout(() => setMessage(null), 5000); }
    };

    return (
        <div className="space-y-4">
            {/* Action bar */}
            <GlassCard className="flex flex-wrap items-center gap-4">
                <div className="flex gap-1 bg-secondary rounded-lg p-1">
                    {(["all", "unassigned", "assigned"] as const).map((f) => (
                        <button
                            key={f}
                            onClick={() => { setFilter(f); setSelectedIds(new Set()); }}
                            className={`px-3 py-1.5 text-xs font-medium rounded-md transition-colors capitalize ${
                                filter === f ? "bg-card text-foreground shadow" : "text-muted-foreground hover:text-foreground"
                            }`}
                        >
                            {f}
                        </button>
                    ))}
                </div>

                <div className="h-8 w-px bg-border" />

                <CustomerAutocomplete
                    value={targetCustomerId}
                    onChange={(id) => setTargetCustomerId(String(id))}
                    customers={customers}
                    placeholder="Search customer..."
                />

                <PermissionGate permission="VEHICLE_MANAGE">
                    <button
                        onClick={handleAssign}
                        disabled={loading || selectedIds.size === 0 || !targetCustomerId}
                        className="btn-gradient px-4 py-2 rounded-lg text-sm font-medium flex items-center gap-2 disabled:opacity-50"
                    >
                        <Link2 className="w-4 h-4" />
                        Assign ({selectedIds.size})
                    </button>

                    <button
                        onClick={handleUnassign}
                        disabled={loading || selectedIds.size === 0}
                        className="px-4 py-2 rounded-lg text-sm font-medium border border-border text-muted-foreground hover:text-destructive hover:border-destructive transition-colors flex items-center gap-2 disabled:opacity-50"
                    >
                        <Unlink className="w-4 h-4" />
                        Unassign ({selectedIds.size})
                    </button>
                </PermissionGate>

                {message && (
                    <div className={`ml-auto flex items-center gap-2 text-sm ${message.type === "success" ? "text-green-500" : "text-destructive"}`}>
                        {message.type === "success" ? <Check className="w-4 h-4" /> : <AlertCircle className="w-4 h-4" />}
                        {message.text}
                    </div>
                )}
            </GlassCard>

            {/* Table */}
            <GlassCard className="overflow-hidden">
                <table className="w-full text-left">
                    <thead>
                        <tr className="border-b border-border bg-muted/50">
                            <th className="px-4 py-3 w-10">
                                <input
                                    type="checkbox"
                                    checked={filtered.length > 0 && selectedIds.size === filtered.length}
                                    onChange={toggleSelectAll}
                                    className="rounded border-border accent-primary"
                                />
                            </th>
                            <th className="px-4 py-3 text-sm font-semibold">Vehicle Number</th>
                            <th className="px-4 py-3 text-sm font-semibold">Type</th>
                            <th className="px-4 py-3 text-sm font-semibold">Capacity</th>
                            <th className="px-4 py-3 text-sm font-semibold">Current Customer</th>
                        </tr>
                    </thead>
                    <tbody className="divide-y divide-border">
                        {vcPaged.map((vehicle) => (
                            <tr
                                key={vehicle.id}
                                onClick={() => toggleSelect(vehicle.id)}
                                className={`hover:bg-muted/30 transition-colors cursor-pointer ${selectedIds.has(vehicle.id) ? "bg-primary/5" : ""}`}
                            >
                                <td className="px-4 py-3">
                                    <input
                                        type="checkbox"
                                        checked={selectedIds.has(vehicle.id)}
                                        onChange={() => toggleSelect(vehicle.id)}
                                        className="rounded border-border accent-primary"
                                    />
                                </td>
                                <td className="px-4 py-3">
                                    <div className="flex items-center gap-3">
                                        <div className="w-8 h-8 rounded-full bg-accent/10 flex items-center justify-center text-accent font-bold text-xs">
                                            <Truck className="w-4 h-4" />
                                        </div>
                                        <span className="font-medium font-mono">{vehicle.vehicleNumber}</span>
                                    </div>
                                </td>
                                <td className="px-4 py-3 text-sm text-muted-foreground">{vehicle.vehicleType?.typeName || "-"}</td>
                                <td className="px-4 py-3 text-sm text-muted-foreground">{vehicle.maxCapacity ? `${vehicle.maxCapacity} L` : "-"}</td>
                                <td className="px-4 py-3">
                                    {vehicle.customer ? (
                                        <span className="text-sm font-medium px-2.5 py-1 rounded-full bg-accent/10 text-accent">
                                            {vehicle.customer.name}
                                        </span>
                                    ) : (
                                        <span className="text-sm text-muted-foreground italic flex items-center gap-1">
                                            <AlertCircle className="w-3 h-3" /> Unassigned
                                        </span>
                                    )}
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
                {filtered.length === 0 && (
                    <div className="p-12 text-center text-muted-foreground">
                        No vehicles found for this filter.
                    </div>
                )}
                <TablePagination
                    page={vcPage}
                    totalPages={vcTotalPages}
                    totalElements={vcTotalElements}
                    pageSize={vcPageSize}
                    onPageChange={setVcPage}
                />
            </GlassCard>
        </div>
    );
}
