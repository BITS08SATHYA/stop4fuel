"use client";

import { useEffect, useState } from "react";
import { GlassCard } from "@/components/ui/glass-card";
import { getMyVehicles, CustomerVehicle } from "@/lib/api/station";
import { Loader2, Truck } from "lucide-react";

export default function CustomerVehiclesPage() {
    const [vehicles, setVehicles] = useState<CustomerVehicle[]>([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        getMyVehicles()
            .then(setVehicles)
            .catch(() => setVehicles([]))
            .finally(() => setLoading(false));
    }, []);

    return (
        <div className="space-y-4">
            <h1 className="text-xl font-bold flex items-center gap-2">
                <Truck className="w-5 h-5" /> My Vehicles
            </h1>

            {loading ? (
                <div className="flex justify-center py-12"><Loader2 className="w-6 h-6 animate-spin text-primary" /></div>
            ) : vehicles.length === 0 ? (
                <GlassCard className="p-8 text-center">
                    <p className="text-sm text-muted-foreground">No vehicles registered</p>
                </GlassCard>
            ) : (
                <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-4">
                    {vehicles.map((v) => {
                        const utilization = v.monthlyLimitLiters && v.monthlyLimitLiters > 0
                            ? Math.min(((v.consumedLiters || 0) / v.monthlyLimitLiters) * 100, 100)
                            : null;
                        return (
                            <GlassCard key={v.id} className="p-4">
                                <div className="flex items-center gap-3 mb-3">
                                    <div className="p-2 bg-primary/10 rounded-lg">
                                        <Truck className="w-5 h-5 text-primary" />
                                    </div>
                                    <div>
                                        <p className="font-bold">{v.vehicleNumber}</p>
                                        <p className="text-xs text-muted-foreground">{v.vehicleTypeName || "Vehicle"}</p>
                                    </div>
                                </div>
                                {v.monthlyLimitLiters != null && v.monthlyLimitLiters > 0 && (
                                    <div className="mt-2">
                                        <div className="flex justify-between text-xs text-muted-foreground mb-1">
                                            <span>{(v.consumedLiters || 0).toFixed(1)}L used</span>
                                            <span>{v.monthlyLimitLiters}L limit</span>
                                        </div>
                                        <div className="w-full bg-muted rounded-full h-2">
                                            <div
                                                className={`h-2 rounded-full ${
                                                    (utilization || 0) > 80 ? "bg-red-500" :
                                                    (utilization || 0) > 50 ? "bg-amber-500" : "bg-green-500"
                                                }`}
                                                style={{ width: `${utilization || 0}%` }}
                                            />
                                        </div>
                                    </div>
                                )}
                            </GlassCard>
                        );
                    })}
                </div>
            )}
        </div>
    );
}
