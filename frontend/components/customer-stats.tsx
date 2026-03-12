"use client";

import { GlassCard } from "@/components/ui/glass-card";
import { Users, Truck, Fuel, ShieldAlert } from "lucide-react";
import { useEffect, useState } from "react";

interface Stats {
    totalCustomers: number;
    activeFleets: number;
    blockedCustomers: number;
    totalCreditGiven: number;
    totalConsumed: number;
    utilization: number;
}

export function CustomerStats() {
    const [stats, setStats] = useState<Stats>({
        totalCustomers: 0,
        activeFleets: 0,
        blockedCustomers: 0,
        totalCreditGiven: 0,
        totalConsumed: 0,
        utilization: 0
    });

    useEffect(() => {
        fetchStats();
    }, []);

    const fetchStats = async () => {
        try {
            const res = await fetch("http://localhost:8080/api/customers/stats");
            if (res.ok) {
                setStats(await res.json());
            }
        } catch (error) {
            console.error("Failed to fetch stats", error);
        }
    };

    return (
        <div className="grid grid-cols-1 md:grid-cols-4 gap-6 mb-8">
            <GlassCard className="relative overflow-hidden">
                <div className="flex justify-between items-start">
                    <div>
                        <p className="text-muted-foreground text-sm font-medium">Total Customers</p>
                        <h3 className="text-3xl font-bold mt-2 text-foreground">{stats.totalCustomers}</h3>
                        <div className="flex items-center mt-2 text-emerald-400 text-sm">
                            <span>{stats.activeFleets} active</span>
                        </div>
                    </div>
                    <div className="p-3 bg-cyan-500/10 rounded-lg">
                        <Users className="w-6 h-6 text-cyan-400" />
                    </div>
                </div>
                <div className="absolute -right-6 -bottom-6 w-24 h-24 bg-cyan-500/20 rounded-full blur-2xl" />
            </GlassCard>

            <GlassCard className="relative overflow-hidden">
                <div className="flex justify-between items-start">
                    <div>
                        <p className="text-muted-foreground text-sm font-medium">Active Fleets</p>
                        <h3 className="text-3xl font-bold mt-2 text-foreground">{stats.activeFleets}</h3>
                        <div className="flex items-center mt-2 text-muted-foreground text-sm">
                            <span>of {stats.totalCustomers} total</span>
                        </div>
                    </div>
                    <div className="p-3 bg-blue-500/10 rounded-lg">
                        <Truck className="w-6 h-6 text-blue-400" />
                    </div>
                </div>
                <div className="absolute -right-6 -bottom-6 w-24 h-24 bg-blue-500/20 rounded-full blur-2xl" />
            </GlassCard>

            <GlassCard className="relative overflow-hidden">
                <div className="flex justify-between items-start">
                    <div>
                        <p className="text-muted-foreground text-sm font-medium">Blocked</p>
                        <h3 className={`text-3xl font-bold mt-2 ${stats.blockedCustomers > 0 ? 'text-destructive' : 'text-foreground'}`}>
                            {stats.blockedCustomers}
                        </h3>
                        <div className="flex items-center mt-2 text-muted-foreground text-sm">
                            <span>limit exceeded</span>
                        </div>
                    </div>
                    <div className="p-3 bg-red-500/10 rounded-lg">
                        <ShieldAlert className="w-6 h-6 text-red-400" />
                    </div>
                </div>
                <div className="absolute -right-6 -bottom-6 w-24 h-24 bg-red-500/20 rounded-full blur-2xl" />
            </GlassCard>

            <GlassCard className="relative overflow-hidden">
                <div className="flex justify-between items-start">
                    <div>
                        <p className="text-muted-foreground text-sm font-medium">Credit Utilization</p>
                        <h3 className="text-3xl font-bold mt-2 text-foreground">{stats.utilization}%</h3>
                        <div className="flex items-center mt-2 text-amber-400 text-sm">
                            <span>{stats.totalConsumed} / {stats.totalCreditGiven} L</span>
                        </div>
                    </div>
                    <div className="p-3 bg-amber-500/10 rounded-lg">
                        <Fuel className="w-6 h-6 text-amber-400" />
                    </div>
                </div>
                <div className="absolute -right-6 -bottom-6 w-24 h-24 bg-amber-500/20 rounded-full blur-2xl" />
            </GlassCard>
        </div>
    );
}
