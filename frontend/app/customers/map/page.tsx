"use client";

import { useEffect, useState, useMemo } from "react";
import dynamic from "next/dynamic";
import { fetchWithAuth } from "@/lib/api/fetch-with-auth";
import { API_BASE_URL } from "@/lib/api/station";
import { MapPin, Navigation, Filter, X, ChevronDown, Settings2 } from "lucide-react";

const CustomerMap = dynamic(() => import("@/components/customers/customer-map"), { ssr: false });

interface CustomerLocation {
    id: number;
    name: string;
    status: string;
    latitude: number;
    longitude: number;
    groupName: string | null;
    address: string | null;
}

// Default station location (Chennai)
const DEFAULT_STATION = { lat: 13.0827, lng: 80.2707 };

function getStationLocation() {
    if (typeof window === "undefined") return DEFAULT_STATION;
    try {
        const saved = localStorage.getItem("station-location");
        if (saved) return JSON.parse(saved);
    } catch {}
    return DEFAULT_STATION;
}

export default function CustomerMapPage() {
    const [customers, setCustomers] = useState<CustomerLocation[]>([]);
    const [loading, setLoading] = useState(true);
    const [statusFilter, setStatusFilter] = useState("ALL");
    const [routeMode, setRouteMode] = useState(false);
    const [selectedIds, setSelectedIds] = useState<number[]>([]);
    const [showRoute, setShowRoute] = useState(false);
    const [stationConfig, setStationConfig] = useState(false);
    const [station, setStation] = useState(DEFAULT_STATION);
    const [tempStation, setTempStation] = useState(DEFAULT_STATION);

    useEffect(() => {
        setStation(getStationLocation());
        setTempStation(getStationLocation());
    }, []);

    useEffect(() => {
        fetchWithAuth(`${API_BASE_URL}/customers/map-locations`)
            .then(res => res.json())
            .then(data => setCustomers(data))
            .catch(err => console.error("Failed to load map locations:", err))
            .finally(() => setLoading(false));
    }, []);

    const filteredCustomers = useMemo(() => {
        if (statusFilter === "ALL") return customers;
        return customers.filter(c => c.status === statusFilter);
    }, [customers, statusFilter]);

    const groups = useMemo(() => {
        const set = new Set<string>();
        customers.forEach(c => { if (c.groupName) set.add(c.groupName); });
        return Array.from(set).sort();
    }, [customers]);

    const toggleCustomer = (id: number) => {
        setShowRoute(false);
        setSelectedIds(prev =>
            prev.includes(id) ? prev.filter(x => x !== id) : [...prev, id]
        );
    };

    const selectAll = () => {
        setShowRoute(false);
        setSelectedIds(filteredCustomers.map(c => c.id));
    };

    const clearSelection = () => {
        setShowRoute(false);
        setSelectedIds([]);
    };

    const saveStation = () => {
        setStation(tempStation);
        localStorage.setItem("station-location", JSON.stringify(tempStation));
        setStationConfig(false);
    };

    const stats = useMemo(() => ({
        total: customers.length,
        active: customers.filter(c => c.status === "ACTIVE").length,
        blocked: customers.filter(c => c.status === "BLOCKED").length,
        inactive: customers.filter(c => c.status === "INACTIVE").length,
    }), [customers]);

    return (
        <div className="p-6 h-screen flex flex-col bg-background transition-colors duration-300 overflow-hidden">
            <div className="w-full flex flex-col flex-1 min-h-0">
                {/* Header */}
                <div className="flex justify-between items-center mb-4 flex-shrink-0">
                    <div>
                        <h1 className="text-4xl font-bold text-foreground tracking-tight">
                            Customer <span className="text-gradient">Map</span>
                        </h1>
                        <p className="text-muted-foreground mt-1">
                            {stats.total} customers with GPS coordinates
                            {stats.total > 0 && (
                                <span className="ml-2 text-xs">
                                    ({stats.active} active, {stats.blocked} blocked, {stats.inactive} inactive)
                                </span>
                            )}
                        </p>
                    </div>
                    <div className="flex items-center gap-2">
                        {/* Station Config */}
                        <button
                            onClick={() => setStationConfig(!stationConfig)}
                            className={`px-3 py-2 rounded-lg text-sm font-medium flex items-center gap-2 transition-colors ${
                                stationConfig
                                    ? "bg-primary text-primary-foreground"
                                    : "bg-card border border-border text-foreground hover:bg-muted"
                            }`}
                        >
                            <Settings2 className="w-4 h-4" />
                            Station
                        </button>

                        {/* Status Filter */}
                        <div className="relative">
                            <select
                                value={statusFilter}
                                onChange={e => setStatusFilter(e.target.value)}
                                className="appearance-none bg-card border border-border rounded-lg px-4 py-2 pr-8 text-sm font-medium text-foreground cursor-pointer hover:bg-muted transition-colors"
                            >
                                <option value="ALL">All Status</option>
                                <option value="ACTIVE">Active</option>
                                <option value="BLOCKED">Blocked</option>
                                <option value="INACTIVE">Inactive</option>
                            </select>
                            <ChevronDown className="w-4 h-4 absolute right-2 top-1/2 -translate-y-1/2 text-muted-foreground pointer-events-none" />
                        </div>

                        {/* Route Toggle */}
                        <button
                            onClick={() => {
                                setRouteMode(!routeMode);
                                if (routeMode) {
                                    setSelectedIds([]);
                                    setShowRoute(false);
                                }
                            }}
                            className={`px-4 py-2 rounded-lg text-sm font-medium flex items-center gap-2 transition-colors ${
                                routeMode
                                    ? "bg-primary text-primary-foreground"
                                    : "bg-card border border-border text-foreground hover:bg-muted"
                            }`}
                        >
                            <Navigation className="w-4 h-4" />
                            Route Mode
                        </button>
                    </div>
                </div>

                {/* Station Config Panel */}
                {stationConfig && (
                    <div className="mb-4 flex-shrink-0 glass-card p-4 rounded-xl flex items-end gap-4">
                        <div>
                            <label className="block text-xs font-medium text-muted-foreground mb-1">Station Latitude</label>
                            <input
                                type="number"
                                step="0.0000001"
                                value={tempStation.lat}
                                onChange={e => setTempStation(prev => ({ ...prev, lat: parseFloat(e.target.value) || 0 }))}
                                className="w-40 px-3 py-2 bg-background border border-border rounded-lg text-sm"
                                placeholder="e.g. 13.0827"
                            />
                        </div>
                        <div>
                            <label className="block text-xs font-medium text-muted-foreground mb-1">Station Longitude</label>
                            <input
                                type="number"
                                step="0.0000001"
                                value={tempStation.lng}
                                onChange={e => setTempStation(prev => ({ ...prev, lng: parseFloat(e.target.value) || 0 }))}
                                className="w-40 px-3 py-2 bg-background border border-border rounded-lg text-sm"
                                placeholder="e.g. 80.2707"
                            />
                        </div>
                        <button onClick={saveStation} className="btn-gradient px-4 py-2 rounded-lg text-sm font-medium">
                            Save Location
                        </button>
                        <button onClick={() => setStationConfig(false)} className="px-3 py-2 text-muted-foreground hover:text-foreground">
                            <X className="w-4 h-4" />
                        </button>
                    </div>
                )}

                {/* Main Content */}
                <div className="flex-1 min-h-0 flex gap-4">
                    {/* Map */}
                    <div className={`flex-1 min-h-0 glass-card rounded-xl overflow-hidden ${routeMode ? "" : "w-full"}`}>
                        {loading ? (
                            <div className="w-full h-full flex items-center justify-center">
                                <div className="flex flex-col items-center gap-3">
                                    <div className="w-8 h-8 border-2 border-primary border-t-transparent rounded-full animate-spin" />
                                    <span className="text-sm text-muted-foreground">Loading map...</span>
                                </div>
                            </div>
                        ) : customers.length === 0 ? (
                            <div className="w-full h-full flex items-center justify-center">
                                <div className="text-center">
                                    <MapPin className="w-12 h-12 text-muted-foreground mx-auto mb-3" />
                                    <p className="text-lg font-medium text-foreground">No customer locations found</p>
                                    <p className="text-sm text-muted-foreground mt-1">
                                        Add GPS coordinates to your customers to see them on the map.
                                    </p>
                                </div>
                            </div>
                        ) : (
                            <CustomerMap
                                customers={customers}
                                stationLat={station.lat}
                                stationLng={station.lng}
                                statusFilter={statusFilter}
                                routeCustomerIds={selectedIds}
                                showRoute={showRoute}
                            />
                        )}
                    </div>

                    {/* Route Panel */}
                    {routeMode && (
                        <div className="w-72 flex-shrink-0 glass-card rounded-xl p-4 flex flex-col min-h-0">
                            <div className="flex items-center justify-between mb-3">
                                <h3 className="font-semibold text-foreground text-sm">Route Planner</h3>
                                <span className="text-xs text-muted-foreground">
                                    {selectedIds.length} selected
                                </span>
                            </div>

                            <div className="flex gap-2 mb-3">
                                <button
                                    onClick={selectAll}
                                    className="flex-1 text-xs px-2 py-1.5 rounded-md bg-muted text-foreground hover:bg-muted/80 transition-colors"
                                >
                                    Select All
                                </button>
                                <button
                                    onClick={clearSelection}
                                    className="flex-1 text-xs px-2 py-1.5 rounded-md bg-muted text-foreground hover:bg-muted/80 transition-colors"
                                >
                                    Clear
                                </button>
                            </div>

                            <div className="flex-1 overflow-y-auto space-y-1 min-h-0">
                                {filteredCustomers.map(c => (
                                    <label
                                        key={c.id}
                                        className={`flex items-center gap-2 px-2 py-1.5 rounded-md cursor-pointer transition-colors text-sm ${
                                            selectedIds.includes(c.id)
                                                ? "bg-primary/10 text-primary"
                                                : "hover:bg-muted text-foreground"
                                        }`}
                                    >
                                        <input
                                            type="checkbox"
                                            checked={selectedIds.includes(c.id)}
                                            onChange={() => toggleCustomer(c.id)}
                                            className="rounded border-border"
                                        />
                                        <div className="flex-1 min-w-0">
                                            <div className="truncate font-medium text-xs">{c.name}</div>
                                            {c.groupName && (
                                                <div className="truncate text-[10px] text-muted-foreground">{c.groupName}</div>
                                            )}
                                        </div>
                                        <span
                                            className="w-2 h-2 rounded-full flex-shrink-0"
                                            style={{
                                                background:
                                                    c.status === "ACTIVE" ? "#22c55e" :
                                                    c.status === "BLOCKED" ? "#ef4444" : "#eab308",
                                            }}
                                        />
                                    </label>
                                ))}
                            </div>

                            <button
                                onClick={() => setShowRoute(true)}
                                disabled={selectedIds.length === 0}
                                className="mt-3 w-full btn-gradient px-4 py-2.5 rounded-lg text-sm font-medium disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2"
                            >
                                <Navigation className="w-4 h-4" />
                                {selectedIds.length === 0
                                    ? "Select customers"
                                    : `Calculate Route (${selectedIds.length})`}
                            </button>
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
}
