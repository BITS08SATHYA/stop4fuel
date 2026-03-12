"use client";

import React, { useEffect, useState } from "react";

interface VehicleStepProps {
    data: any;
    updateData: (data: any) => void;
}

export function VehicleStep({ data, updateData }: VehicleStepProps) {
    const [vehicleTypes, setVehicleTypes] = useState<any[]>([]);
    const [products, setProducts] = useState<any[]>([]);

    useEffect(() => {
        fetchVehicleTypes();
        fetchProducts();
    }, []);

    const fetchVehicleTypes = async () => {
        try {
            const res = await fetch("http://localhost:8080/api/vehicle-types");
            if (res.ok) {
                const data = await res.json();
                setVehicleTypes(Array.isArray(data) ? data : data.content || []);
            }
        } catch (error) {
            console.error("Failed to fetch vehicle types", error);
        }
    };

    const fetchProducts = async () => {
        try {
            const res = await fetch("http://localhost:8080/api/products");
            if (res.ok) {
                const data = await res.json();
                setProducts(Array.isArray(data) ? data : data.content || []);
            }
        } catch (error) {
            console.error("Failed to fetch products", error);
        }
    };

    return (
        <div className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
                <div>
                    <label className="block text-sm font-medium text-muted-foreground mb-1">
                        Vehicle Number
                    </label>
                    <input
                        type="text"
                        value={data.vehicleNumber || ""}
                        onChange={(e) => updateData({ ...data, vehicleNumber: e.target.value.toUpperCase() })}
                        className="w-full bg-white/5 border border-white/10 rounded-lg px-4 py-2 text-white focus:outline-none focus:ring-2 focus:ring-cyan-500/50 uppercase"
                        placeholder="MH 12 AB 1234"
                    />
                    <p className="text-[10px] text-muted-foreground mt-1">Format: MH 12 AB 1234</p>
                </div>
                <div>
                    <label className="block text-sm font-medium text-muted-foreground mb-1">
                        Vehicle Type
                    </label>
                    <select
                        value={data.vehicleType || ""}
                        onChange={(e) => updateData({ ...data, vehicleType: e.target.value })}
                        className="w-full bg-white/5 border border-white/10 rounded-lg px-4 py-2 text-white focus:outline-none focus:ring-2 focus:ring-cyan-500/50"
                    >
                        <option value="" className="bg-slate-900">Select Type</option>
                        {vehicleTypes.map(vt => (
                            <option key={vt.id} value={vt.id} className="bg-slate-900">{vt.typeName}</option>
                        ))}
                    </select>
                </div>
            </div>

            <div className="grid grid-cols-2 gap-4">
                <div>
                    <label className="block text-sm font-medium text-muted-foreground mb-1">
                        Fuel Type
                    </label>
                    <select
                        value={data.fuelType || ""}
                        onChange={(e) => updateData({ ...data, fuelType: e.target.value })}
                        className="w-full bg-white/5 border border-white/10 rounded-lg px-4 py-2 text-white focus:outline-none focus:ring-2 focus:ring-cyan-500/50"
                    >
                        <option value="" className="bg-slate-900">Select Fuel</option>
                        {products.map(p => (
                            <option key={p.id} value={p.id} className="bg-slate-900">{p.name}</option>
                        ))}
                    </select>
                </div>
                <div>
                    <label className="block text-sm font-medium text-muted-foreground mb-1">
                        Max Capacity (Liters)
                    </label>
                    <input
                        type="number"
                        value={data.maxCapacity || ""}
                        onChange={(e) => updateData({ ...data, maxCapacity: e.target.value })}
                        className="w-full bg-white/5 border border-white/10 rounded-lg px-4 py-2 text-white focus:outline-none focus:ring-2 focus:ring-cyan-500/50"
                        placeholder="50"
                    />
                </div>
            </div>
        </div>
    );
}
