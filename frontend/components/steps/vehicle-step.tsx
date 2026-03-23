"use client";

import React, { useEffect, useState } from "react";
import { API_BASE_URL } from "@/lib/api/station";

interface VehicleStepProps {
    data: any;
    updateData: (data: any) => void;
    errors?: Record<string, string>;
}

export function VehicleStep({ data, updateData, errors = {} }: VehicleStepProps) {
    const [vehicleTypes, setVehicleTypes] = useState<any[]>([]);
    const [products, setProducts] = useState<any[]>([]);

    useEffect(() => {
        fetchVehicleTypes();
        fetchProducts();
    }, []);

    const fetchVehicleTypes = async () => {
        try {
            const res = await fetch(`${API_BASE_URL}/vehicle-types`);
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
            const res = await fetch(`${API_BASE_URL}/products`);
            if (res.ok) {
                const data = await res.json();
                setProducts(Array.isArray(data) ? data : data.content || []);
            }
        } catch (error) {
            console.error("Failed to fetch products", error);
        }
    };

    const inputClass = (field: string) =>
        `w-full bg-white/5 border rounded-lg px-4 py-2 text-white focus:outline-none focus:ring-2 focus:ring-cyan-500/50 ${
            errors[field] ? "border-red-500" : "border-white/10"
        }`;

    return (
        <div className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
                <div>
                    <label className="block text-sm font-medium text-muted-foreground mb-1">
                        Vehicle Number <span className="text-red-400">*</span>
                    </label>
                    <input
                        type="text"
                        value={data.vehicleNumber || ""}
                        onChange={(e) => updateData({ ...data, vehicleNumber: e.target.value.toUpperCase() })}
                        className={`${inputClass("vehicleNumber")} uppercase`}
                        placeholder="MH 12 AB 1234"
                    />
                    {errors.vehicleNumber ? (
                        <p className="text-[11px] text-red-400 mt-1">{errors.vehicleNumber}</p>
                    ) : (
                        <p className="text-[10px] text-muted-foreground mt-1">Format: MH 12 AB 1234</p>
                    )}
                </div>
                <div>
                    <label className="block text-sm font-medium text-muted-foreground mb-1">
                        Vehicle Type <span className="text-red-400">*</span>
                    </label>
                    <select
                        value={data.vehicleType || ""}
                        onChange={(e) => updateData({ ...data, vehicleType: e.target.value })}
                        className={inputClass("vehicleType")}
                    >
                        <option value="" className="bg-slate-900">Select Type</option>
                        {vehicleTypes.map(vt => (
                            <option key={vt.id} value={vt.id} className="bg-slate-900">{vt.typeName}</option>
                        ))}
                    </select>
                    {errors.vehicleType && (
                        <p className="text-[11px] text-red-400 mt-1">{errors.vehicleType}</p>
                    )}
                </div>
            </div>

            <div className="grid grid-cols-2 gap-4">
                <div>
                    <label className="block text-sm font-medium text-muted-foreground mb-1">
                        Fuel Type <span className="text-red-400">*</span>
                    </label>
                    <select
                        value={data.fuelType || ""}
                        onChange={(e) => updateData({ ...data, fuelType: e.target.value })}
                        className={inputClass("fuelType")}
                    >
                        <option value="" className="bg-slate-900">Select Fuel</option>
                        {products.map(p => (
                            <option key={p.id} value={p.id} className="bg-slate-900">{p.name}</option>
                        ))}
                    </select>
                    {errors.fuelType && (
                        <p className="text-[11px] text-red-400 mt-1">{errors.fuelType}</p>
                    )}
                </div>
                <div>
                    <label className="block text-sm font-medium text-muted-foreground mb-1">
                        Max Capacity (Liters)
                    </label>
                    <input
                        type="number"
                        value={data.maxCapacity || ""}
                        onChange={(e) => updateData({ ...data, maxCapacity: e.target.value })}
                        className={inputClass("maxCapacity")}
                        placeholder="50"
                    />
                    {errors.maxCapacity && (
                        <p className="text-[11px] text-red-400 mt-1">{errors.maxCapacity}</p>
                    )}
                </div>
            </div>

            <div className="grid grid-cols-2 gap-4">
                <div>
                    <label className="block text-sm font-medium text-muted-foreground mb-1">
                        Monthly Liter Limit
                    </label>
                    <input
                        type="number"
                        value={data.maxLitersPerMonth || ""}
                        onChange={(e) => updateData({ ...data, maxLitersPerMonth: e.target.value })}
                        className={inputClass("maxLitersPerMonth")}
                        placeholder="e.g. 2000"
                    />
                    <p className="text-[10px] text-muted-foreground mt-1">Max liters this vehicle can consume per month</p>
                    {errors.maxLitersPerMonth && (
                        <p className="text-[11px] text-red-400 mt-1">{errors.maxLitersPerMonth}</p>
                    )}
                </div>
            </div>
        </div>
    );
}
