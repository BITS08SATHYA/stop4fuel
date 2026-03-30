"use client";

import { useEffect, useState } from "react";
import { GlassCard } from "@/components/ui/glass-card";
import { getTanks, getPumps, getNozzles, Tank, Pump, Nozzle } from "@/lib/api/station";
import { Droplets, Fuel, Activity, ArrowRight, CircleDot, Database } from "lucide-react";

export default function StationLayoutPage() {
    const [tanks, setTanks] = useState<Tank[]>([]);
    const [pumps, setPumps] = useState<Pump[]>([]);
    const [nozzles, setNozzles] = useState<Nozzle[]>([]);
    const [isLoading, setIsLoading] = useState(true);

    useEffect(() => {
        Promise.all([getTanks(), getPumps(), getNozzles()])
            .then(([tanksData, pumpsData, nozzlesData]) => {
                setTanks(tanksData);
                setPumps(pumpsData);
                setNozzles(nozzlesData);
            })
            .catch(err => console.error("Failed to load station layout:", err))
            .finally(() => setIsLoading(false));
    }, []);

    if (isLoading) {
        return (
            <div className="p-8 min-h-screen bg-background flex items-center justify-center">
                <div className="text-center">
                    <div className="w-12 h-12 border-4 border-primary/20 border-t-primary rounded-full animate-spin mx-auto mb-4"></div>
                    <p className="text-muted-foreground animate-pulse">Loading station layout...</p>
                </div>
            </div>
        );
    }

    const activeTanks = tanks.filter(t => t.active).length;
    const activePumps = pumps.filter(p => p.active).length;
    const activeNozzles = nozzles.filter(n => n.active).length;

    return (
        <div className="p-8 min-h-screen bg-background transition-colors duration-300">
            <div className="max-w-7xl mx-auto">
                {/* Header */}
                <div className="mb-8">
                    <h1 className="text-4xl font-bold text-foreground tracking-tight">
                        Station <span className="text-gradient">Layout</span>
                    </h1>
                    <p className="text-muted-foreground mt-2">
                        Visual overview of your fuel station infrastructure — Tanks, Pumps, and Nozzles.
                    </p>
                </div>

                {/* Summary Stats */}
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-10">
                    <div className="flex items-center gap-4 p-5 rounded-2xl bg-card border border-border">
                        <div className="w-12 h-12 rounded-xl bg-blue-500/10 flex items-center justify-center">
                            <Database className="w-6 h-6 text-blue-500" />
                        </div>
                        <div>
                            <p className="text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Underground Tanks</p>
                            <div className="flex items-baseline gap-2">
                                <span className="text-3xl font-black text-foreground">{activeTanks}</span>
                                <span className="text-xs text-muted-foreground">/ {tanks.length} total</span>
                            </div>
                        </div>
                    </div>
                    <div className="flex items-center gap-4 p-5 rounded-2xl bg-card border border-border">
                        <div className="w-12 h-12 rounded-xl bg-orange-500/10 flex items-center justify-center">
                            <Activity className="w-6 h-6 text-orange-500" />
                        </div>
                        <div>
                            <p className="text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Fuel Pumps</p>
                            <div className="flex items-baseline gap-2">
                                <span className="text-3xl font-black text-foreground">{activePumps}</span>
                                <span className="text-xs text-muted-foreground">/ {pumps.length} total</span>
                            </div>
                        </div>
                    </div>
                    <div className="flex items-center gap-4 p-5 rounded-2xl bg-card border border-border">
                        <div className="w-12 h-12 rounded-xl bg-green-500/10 flex items-center justify-center">
                            <Fuel className="w-6 h-6 text-green-500" />
                        </div>
                        <div>
                            <p className="text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Nozzle Points</p>
                            <div className="flex items-baseline gap-2">
                                <span className="text-3xl font-black text-foreground">{activeNozzles}</span>
                                <span className="text-xs text-muted-foreground">/ {nozzles.length} total</span>
                            </div>
                        </div>
                    </div>
                </div>

                {/* Pump-Centric Layout */}
                <div className="space-y-6">
                    {pumps.map(pump => {
                        const pumpNozzles = nozzles.filter(n => n.pump.id === pump.id);
                        // Get unique tanks connected through nozzles
                        const connectedTankIds = [...new Set(pumpNozzles.map(n => n.tank.id))];
                        const connectedTanks = tanks.filter(t => connectedTankIds.includes(t.id));

                        return (
                            <GlassCard key={pump.id} className="border-none p-0 overflow-hidden">
                                {/* Pump Header */}
                                <div className="px-6 py-4 bg-white/5 border-b border-border/50 flex items-center justify-between">
                                    <div className="flex items-center gap-3">
                                        <div className="w-10 h-10 rounded-xl bg-orange-500/10 flex items-center justify-center">
                                            <Activity className="w-5 h-5 text-orange-500" />
                                        </div>
                                        <div>
                                            <h3 className="text-lg font-bold text-foreground">{pump.name}</h3>
                                            <p className="text-xs text-muted-foreground">{pumpNozzles.length} nozzle{pumpNozzles.length !== 1 ? 's' : ''} attached</p>
                                        </div>
                                    </div>
                                    <span className={`px-3 py-1 rounded-full text-[10px] font-bold uppercase tracking-wider ${
                                        pump.active
                                            ? 'bg-green-500/10 text-green-500 border border-green-500/20'
                                            : 'bg-red-500/10 text-red-500 border border-red-500/20'
                                    }`}>
                                        {pump.active ? 'Active' : 'Inactive'}
                                    </span>
                                </div>

                                {/* Flow Diagram: Tank → Nozzle */}
                                <div className="p-6">
                                    {pumpNozzles.length === 0 ? (
                                        <p className="text-sm text-muted-foreground text-center py-6">No nozzles attached to this pump</p>
                                    ) : (
                                        <div className="grid grid-cols-1 md:grid-cols-[1fr_auto_1fr_auto_1fr] gap-4 items-center">
                                            {/* Tanks Column */}
                                            <div className="space-y-3">
                                                <p className="text-[10px] font-bold uppercase tracking-widest text-muted-foreground mb-2">Source Tanks</p>
                                                {connectedTanks.map(tank => (
                                                    <div key={tank.id} className="flex items-center gap-3 p-3 rounded-xl bg-blue-500/5 border border-blue-500/10">
                                                        <Droplets className="w-5 h-5 text-blue-500 shrink-0" />
                                                        <div className="min-w-0">
                                                            <p className="text-sm font-bold text-foreground truncate">{tank.name}</p>
                                                            <p className="text-[10px] text-muted-foreground">{tank.product.name} &middot; {tank.capacity?.toLocaleString()}L capacity</p>
                                                        </div>
                                                    </div>
                                                ))}
                                            </div>

                                            {/* Arrow */}
                                            <div className="hidden md:flex items-center justify-center">
                                                <ArrowRight className="w-5 h-5 text-muted-foreground/40" />
                                            </div>

                                            {/* Pump Center */}
                                            <div className="flex items-center justify-center">
                                                <div className="p-6 rounded-2xl bg-orange-500/5 border-2 border-dashed border-orange-500/20 text-center">
                                                    <Activity className="w-8 h-8 text-orange-500 mx-auto mb-2" />
                                                    <p className="text-sm font-bold text-foreground">{pump.name}</p>
                                                    <p className="text-[10px] text-muted-foreground">Dispenser Unit</p>
                                                </div>
                                            </div>

                                            {/* Arrow */}
                                            <div className="hidden md:flex items-center justify-center">
                                                <ArrowRight className="w-5 h-5 text-muted-foreground/40" />
                                            </div>

                                            {/* Nozzles Column */}
                                            <div className="space-y-3">
                                                <p className="text-[10px] font-bold uppercase tracking-widest text-muted-foreground mb-2">Nozzle Points</p>
                                                {pumpNozzles.map(nozzle => (
                                                    <div key={nozzle.id} className="flex items-center gap-3 p-3 rounded-xl bg-green-500/5 border border-green-500/10">
                                                        <CircleDot className="w-5 h-5 text-green-500 shrink-0" />
                                                        <div className="min-w-0">
                                                            <p className="text-sm font-bold text-foreground truncate">{nozzle.nozzleName}</p>
                                                            <p className="text-[10px] text-muted-foreground">
                                                                {nozzle.tank.productName} &middot; from {nozzle.tank.name}
                                                            </p>
                                                        </div>
                                                        <span className={`ml-auto shrink-0 w-2 h-2 rounded-full ${nozzle.active ? 'bg-green-500' : 'bg-red-500'}`}></span>
                                                    </div>
                                                ))}
                                            </div>
                                        </div>
                                    )}
                                </div>
                            </GlassCard>
                        );
                    })}
                </div>

                {/* Unconnected Tanks */}
                {(() => {
                    const connectedTankIds = new Set(nozzles.map(n => n.tank.id));
                    const unconnectedTanks = tanks.filter(t => !connectedTankIds.has(t.id));
                    if (unconnectedTanks.length === 0) return null;
                    return (
                        <div className="mt-10">
                            <h2 className="text-lg font-bold text-foreground mb-4 flex items-center gap-2">
                                <Database className="w-5 h-5 text-muted-foreground" />
                                Unconnected Tanks
                                <span className="text-xs text-muted-foreground font-normal">({unconnectedTanks.length})</span>
                            </h2>
                            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                                {unconnectedTanks.map(tank => (
                                    <div key={tank.id} className="flex items-center gap-3 p-4 rounded-xl bg-card border border-dashed border-border">
                                        <Droplets className="w-5 h-5 text-blue-500/50" />
                                        <div>
                                            <p className="text-sm font-bold text-foreground">{tank.name}</p>
                                            <p className="text-xs text-muted-foreground">{tank.product.name} &middot; {tank.capacity?.toLocaleString()}L</p>
                                        </div>
                                        <span className={`ml-auto px-2 py-0.5 rounded-full text-[10px] font-bold ${tank.active ? 'bg-green-500/10 text-green-500' : 'bg-red-500/10 text-red-500'}`}>
                                            {tank.active ? 'Active' : 'Inactive'}
                                        </span>
                                    </div>
                                ))}
                            </div>
                        </div>
                    );
                })()}
            </div>
        </div>
    );
}
