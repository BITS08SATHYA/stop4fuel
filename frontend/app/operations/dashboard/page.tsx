"use client";

import { useEffect, useState } from "react";
import { GlassCard } from "@/components/ui/glass-card";
import {
    getTanks,
    getPumps,
    getNozzles,
    getTankInventories,
    getNozzleInventories,
    getProductInventories,
    getActiveShift,
    checkStockAlerts,
    acknowledgeStockAlert,
    acknowledgeAllStockAlerts,
    Tank,
    Pump,
    Nozzle,
    TankInventory,
    NozzleInventory,
    ProductInventory,
    Shift,
    StockAlert,
} from "@/lib/api/station";
import { Droplets, Activity, Fuel, Clock, AlertTriangle, X, CheckCheck, ChevronLeft, ChevronRight } from "lucide-react";

function formatNumber(val?: number | null) {
    if (val == null) return "0";
    return val.toLocaleString("en-IN", { maximumFractionDigits: 2 });
}

function todayISO() {
    return new Date().toISOString().slice(0, 10);
}

export default function OperationalDashboardPage() {
    const [tanks, setTanks] = useState<Tank[]>([]);
    const [pumps, setPumps] = useState<Pump[]>([]);
    const [nozzles, setNozzles] = useState<Nozzle[]>([]);
    const [tankInventories, setTankInventories] = useState<TankInventory[]>([]);
    const [nozzleInventories, setNozzleInventories] = useState<NozzleInventory[]>([]);
    const [productInventories, setProductInventories] = useState<ProductInventory[]>([]);
    const [activeShift, setActiveShift] = useState<Shift | null>(null);
    const [stockAlerts, setStockAlerts] = useState<StockAlert[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [productPage, setProductPage] = useState(0);
    const productPageSize = 5;

    const loadAlerts = () => {
        checkStockAlerts().then(setStockAlerts).catch(() => {});
    };

    useEffect(() => {
        Promise.all([
            getTanks(),
            getPumps(),
            getNozzles(),
            getTankInventories(),
            getNozzleInventories(),
            getProductInventories(),
            getActiveShift(),
            checkStockAlerts().catch(() => []),
        ])
            .then(([t, p, n, ti, ni, pi, shift, alerts]) => {
                setTanks(t);
                setPumps(p);
                setNozzles(n);
                setTankInventories(ti);
                setNozzleInventories(ni);
                setProductInventories(pi);
                setActiveShift(shift);
                setStockAlerts(alerts as StockAlert[]);
            })
            .catch((err) => setError(err.message || "Failed to load data"))
            .finally(() => setIsLoading(false));
    }, []);

    const handleAcknowledge = async (alertId: number) => {
        await acknowledgeStockAlert(alertId);
        loadAlerts();
    };

    const handleAcknowledgeAll = async () => {
        await acknowledgeAllStockAlerts();
        setStockAlerts([]);
    };

    if (isLoading) {
        return (
            <div className="min-h-screen bg-background p-8 flex flex-col items-center justify-center">
                <div className="w-12 h-12 border-4 border-primary/20 border-t-primary rounded-full animate-spin mb-4" />
                <p className="text-muted-foreground animate-pulse">Loading operational dashboard...</p>
            </div>
        );
    }

    if (error) {
        return (
            <div className="min-h-screen bg-background p-8 flex flex-col items-center justify-center">
                <p className="text-red-500 mb-2">Failed to load dashboard</p>
                <p className="text-muted-foreground text-sm">{error}</p>
            </div>
        );
    }

    // Computed values
    const activeTanks = tanks.filter((t) => t.active).length;
    const activePumps = pumps.filter((p) => p.active).length;
    const activeNozzles = nozzles.filter((n) => n.active).length;
    const totalCapacity = tanks.reduce((sum, t) => sum + t.capacity, 0);

    // Latest tank inventory per tank (most recent by date)
    const latestTankInventory = new Map<number, TankInventory>();
    tankInventories.forEach((ti) => {
        const existing = latestTankInventory.get(ti.tank.id);
        if (!existing || ti.date > existing.date) {
            latestTankInventory.set(ti.tank.id, ti);
        }
    });

    // Today's nozzle readings
    const today = todayISO();
    const todayNozzleReadings = nozzleInventories.filter((ni) => ni.date === today);

    // Latest product inventory per product (most recent by date)
    const latestProductInventory = new Map<number, ProductInventory>();
    productInventories.forEach((pi) => {
        const existing = latestProductInventory.get(pi.product.id);
        if (!existing || pi.date > existing.date) {
            latestProductInventory.set(pi.product.id, pi);
        }
    });
    const productInvEntries = Array.from(latestProductInventory.values());
    const productTotalPages = Math.ceil(productInvEntries.length / productPageSize);
    const paginatedProducts = productInvEntries.slice(
        productPage * productPageSize,
        (productPage + 1) * productPageSize
    );

    return (
        <div className="min-h-screen bg-background p-8 transition-colors duration-300">
            <div className="max-w-7xl mx-auto">
                {/* Header */}
                <div className="mb-8">
                    <h1 className="text-4xl font-bold text-foreground tracking-tight">
                        <span className="text-gradient">Operational Dashboard</span>
                    </h1>
                    <p className="text-muted-foreground mt-2">
                        Real-time view of station equipment, inventory levels, and meter readings.
                    </p>
                </div>

                {/* Low Stock Alert Banner */}
                {stockAlerts.length > 0 && (
                    <div className="mb-6 bg-red-500/10 border border-red-500/30 rounded-xl p-4">
                        <div className="flex items-center justify-between mb-3">
                            <div className="flex items-center gap-2">
                                <AlertTriangle className="w-5 h-5 text-red-500" />
                                <h3 className="font-semibold text-red-500">
                                    Low Stock Alert — {stockAlerts.length} tank{stockAlerts.length > 1 ? 's' : ''} below threshold
                                </h3>
                            </div>
                            <button
                                onClick={handleAcknowledgeAll}
                                className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium bg-red-500/20 text-red-500 rounded-lg hover:bg-red-500/30 transition-colors"
                            >
                                <CheckCheck className="w-3.5 h-3.5" />
                                Acknowledge All
                            </button>
                        </div>
                        <div className="space-y-2">
                            {stockAlerts.map((alert) => (
                                <div key={alert.id} className="flex items-center justify-between bg-background/50 rounded-lg px-3 py-2">
                                    <div className="flex items-center gap-3">
                                        <Droplets className="w-4 h-4 text-red-500" />
                                        <div>
                                            <span className="text-sm font-medium text-foreground">{alert.tank.name}</span>
                                            <span className="text-xs text-muted-foreground ml-2">
                                                {alert.tank.product?.name}
                                            </span>
                                        </div>
                                        <span className="text-xs text-red-500 font-semibold">
                                            {alert.availableStock.toLocaleString()} L / {alert.thresholdStock.toLocaleString()} L threshold
                                        </span>
                                    </div>
                                    <button
                                        onClick={() => handleAcknowledge(alert.id)}
                                        className="p-1 text-muted-foreground hover:text-foreground transition-colors"
                                        title="Acknowledge alert"
                                    >
                                        <X className="w-4 h-4" />
                                    </button>
                                </div>
                            ))}
                        </div>
                    </div>
                )}

                {/* Station Overview Cards */}
                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
                    <GlassCard>
                        <div className="flex items-center gap-3 mb-2">
                            <div className="p-2 rounded-lg bg-blue-500/10">
                                <Droplets className="w-5 h-5 text-blue-500" />
                            </div>
                            <span className="text-[10px] uppercase tracking-wider text-muted-foreground font-medium">
                                Tanks
                            </span>
                        </div>
                        <p className="text-2xl font-bold text-foreground">
                            {activeTanks} <span className="text-sm text-muted-foreground font-normal">/ {tanks.length}</span>
                        </p>
                        <p className="text-xs text-muted-foreground mt-1">
                            Total Capacity: {formatNumber(totalCapacity)} L
                        </p>
                    </GlassCard>

                    <GlassCard>
                        <div className="flex items-center gap-3 mb-2">
                            <div className="p-2 rounded-lg bg-green-500/10">
                                <Activity className="w-5 h-5 text-green-500" />
                            </div>
                            <span className="text-[10px] uppercase tracking-wider text-muted-foreground font-medium">
                                Pumps
                            </span>
                        </div>
                        <p className="text-2xl font-bold text-foreground">
                            {activePumps} <span className="text-sm text-muted-foreground font-normal">/ {pumps.length}</span>
                        </p>
                        <p className="text-xs text-muted-foreground mt-1">Active pumps</p>
                    </GlassCard>

                    <GlassCard>
                        <div className="flex items-center gap-3 mb-2">
                            <div className="p-2 rounded-lg bg-orange-500/10">
                                <Fuel className="w-5 h-5 text-orange-500" />
                            </div>
                            <span className="text-[10px] uppercase tracking-wider text-muted-foreground font-medium">
                                Nozzles
                            </span>
                        </div>
                        <p className="text-2xl font-bold text-foreground">
                            {activeNozzles} <span className="text-sm text-muted-foreground font-normal">/ {nozzles.length}</span>
                        </p>
                        <p className="text-xs text-muted-foreground mt-1">Active nozzles</p>
                    </GlassCard>

                    <GlassCard>
                        <div className="flex items-center gap-3 mb-2">
                            <div className="p-2 rounded-lg bg-purple-500/10">
                                <Clock className="w-5 h-5 text-purple-500" />
                            </div>
                            <span className="text-[10px] uppercase tracking-wider text-muted-foreground font-medium">
                                Current Shift
                            </span>
                        </div>
                        <p className="text-2xl font-bold text-foreground">
                            {activeShift ? `#${activeShift.id}` : "—"}
                        </p>
                        <p className="text-xs text-muted-foreground mt-1">
                            {activeShift ? `Started: ${new Date(activeShift.startTime).toLocaleString("en-IN", { day: "2-digit", month: "short", hour: "2-digit", minute: "2-digit" })}` : "No active shift"}
                        </p>
                    </GlassCard>
                </div>

                {/* Tank Status Section */}
                <div className="mb-8">
                    <h2 className="text-xl font-semibold text-foreground mb-4">Tank Status</h2>
                    {tanks.length === 0 ? (
                        <GlassCard>
                            <p className="text-muted-foreground text-center py-4">No tanks configured.</p>
                        </GlassCard>
                    ) : (
                        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                            {tanks.map((tank) => {
                                const inv = latestTankInventory.get(tank.id);
                                const currentStock = tank.availableStock ?? 0;
                                const pct = tank.capacity > 0 ? (currentStock / tank.capacity) * 100 : 0;
                                const clampedPct = Math.min(100, Math.max(0, pct));

                                const isBelowThreshold = tank.thresholdStock != null && tank.thresholdStock > 0 && (tank.availableStock ?? 0) <= tank.thresholdStock;
                                let barColor = "bg-green-500";
                                let statusColor = "text-green-500";
                                let statusLabel = "Good";
                                if (isBelowThreshold) {
                                    barColor = "bg-red-500";
                                    statusColor = "text-red-500";
                                    statusLabel = "Below Threshold";
                                } else if (pct < 20) {
                                    barColor = "bg-red-500";
                                    statusColor = "text-red-500";
                                    statusLabel = "Critical";
                                } else if (pct < 50) {
                                    barColor = "bg-amber-500";
                                    statusColor = "text-amber-500";
                                    statusLabel = "Low";
                                }

                                return (
                                    <GlassCard key={tank.id}>
                                        <div className="flex items-center justify-between mb-3">
                                            <div>
                                                <p className="font-semibold text-foreground">{tank.name}</p>
                                                <p className="text-[10px] uppercase tracking-wider text-muted-foreground">
                                                    {tank.product?.name || "—"}
                                                </p>
                                            </div>
                                            <div className="text-right">
                                                <span className={`text-xs font-medium ${statusColor}`}>{statusLabel}</span>
                                                {!tank.active && (
                                                    <span className="ml-2 text-[10px] bg-red-500/10 text-red-500 px-1.5 py-0.5 rounded">
                                                        Inactive
                                                    </span>
                                                )}
                                            </div>
                                        </div>
                                        <div className="mb-2">
                                            <div className="flex justify-between text-xs text-muted-foreground mb-1">
                                                <span>{formatNumber(currentStock)} L</span>
                                                <span>{formatNumber(tank.capacity)} L</span>
                                            </div>
                                            <div className="w-full h-3 bg-muted rounded-full overflow-hidden">
                                                <div
                                                    className={`h-full rounded-full transition-all duration-500 ${barColor}`}
                                                    style={{ width: `${clampedPct}%` }}
                                                />
                                            </div>
                                            <p className="text-xs text-muted-foreground mt-1 text-right">
                                                {clampedPct.toFixed(1)}%
                                            </p>
                                        </div>
                                        {inv && (
                                            <p className="text-[10px] text-muted-foreground">
                                                Last reading: {inv.date}
                                            </p>
                                        )}
                                    </GlassCard>
                                );
                            })}
                        </div>
                    )}
                </div>

                {/* Today's Meter Readings */}
                <div className="mb-8">
                    <h2 className="text-xl font-semibold text-foreground mb-4">
                        Today&apos;s Meter Readings
                        <span className="text-sm text-muted-foreground font-normal ml-2">({today})</span>
                    </h2>
                    <GlassCard className="overflow-x-auto">
                        {todayNozzleReadings.length === 0 ? (
                            <p className="text-muted-foreground text-center py-4">
                                No meter readings recorded for today.
                            </p>
                        ) : (
                            <table className="w-full text-sm">
                                <thead>
                                    <tr className="border-b border-white/10">
                                        <th className="text-left py-3 px-3 text-[10px] uppercase tracking-wider text-muted-foreground font-medium">
                                            Nozzle
                                        </th>
                                        <th className="text-left py-3 px-3 text-[10px] uppercase tracking-wider text-muted-foreground font-medium">
                                            Pump
                                        </th>
                                        <th className="text-left py-3 px-3 text-[10px] uppercase tracking-wider text-muted-foreground font-medium">
                                            Product
                                        </th>
                                        <th className="text-right py-3 px-3 text-[10px] uppercase tracking-wider text-muted-foreground font-medium">
                                            Open Reading
                                        </th>
                                        <th className="text-right py-3 px-3 text-[10px] uppercase tracking-wider text-muted-foreground font-medium">
                                            Close Reading
                                        </th>
                                        <th className="text-right py-3 px-3 text-[10px] uppercase tracking-wider text-muted-foreground font-medium">
                                            Total Sales
                                        </th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {todayNozzleReadings.map((ni) => (
                                        <tr key={ni.id} className="border-b border-white/5 hover:bg-white/5 transition-colors">
                                            <td className="py-3 px-3 text-foreground font-medium">
                                                {ni.nozzle?.nozzleName || `Nozzle #${ni.nozzle?.id}`}
                                            </td>
                                            <td className="py-3 px-3 text-muted-foreground">
                                                {ni.nozzle?.pump?.name || "—"}
                                            </td>
                                            <td className="py-3 px-3 text-muted-foreground">
                                                {ni.nozzle?.tank?.productName || "—"}
                                            </td>
                                            <td className="py-3 px-3 text-right text-foreground">
                                                {formatNumber(ni.openMeterReading)}
                                            </td>
                                            <td className="py-3 px-3 text-right text-foreground">
                                                {formatNumber(ni.closeMeterReading)}
                                            </td>
                                            <td className="py-3 px-3 text-right font-semibold text-green-500">
                                                {formatNumber(ni.sales)}
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        )}
                    </GlassCard>
                </div>

                {/* Product Stock Summary */}
                <div className="mb-8">
                    <h2 className="text-xl font-semibold text-foreground mb-4">Product Stock Summary</h2>
                    <GlassCard className="overflow-x-auto">
                        {productInvEntries.length === 0 ? (
                            <p className="text-muted-foreground text-center py-4">
                                No product inventory data available.
                            </p>
                        ) : (
                            <>
                            <table className="w-full text-sm">
                                <thead>
                                    <tr className="border-b border-white/10">
                                        <th className="text-left py-3 px-3 text-[10px] uppercase tracking-wider text-muted-foreground font-medium">
                                            Product
                                        </th>
                                        <th className="text-right py-3 px-3 text-[10px] uppercase tracking-wider text-muted-foreground font-medium">
                                            Opening Stock
                                        </th>
                                        <th className="text-right py-3 px-3 text-[10px] uppercase tracking-wider text-muted-foreground font-medium">
                                            Income
                                        </th>
                                        <th className="text-right py-3 px-3 text-[10px] uppercase tracking-wider text-muted-foreground font-medium">
                                            Closing Stock
                                        </th>
                                        <th className="text-right py-3 px-3 text-[10px] uppercase tracking-wider text-muted-foreground font-medium">
                                            Units Sold
                                        </th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {paginatedProducts.map((pi) => (
                                        <tr key={pi.id} className="border-b border-white/5 hover:bg-white/5 transition-colors">
                                            <td className="py-3 px-3 text-foreground font-medium">
                                                {pi.product?.name || "—"}
                                            </td>
                                            <td className="py-3 px-3 text-right text-foreground">
                                                {formatNumber(pi.openStock)}
                                            </td>
                                            <td className="py-3 px-3 text-right text-foreground">
                                                {formatNumber(pi.incomeStock)}
                                            </td>
                                            <td className="py-3 px-3 text-right text-foreground">
                                                {formatNumber(pi.closeStock)}
                                            </td>
                                            <td className="py-3 px-3 text-right font-semibold text-green-500">
                                                {formatNumber(pi.sales)}
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                            {productTotalPages > 1 && (
                                <div className="flex items-center justify-between pt-3 px-3 border-t border-white/10">
                                    <p className="text-xs text-muted-foreground">
                                        Showing {productPage * productPageSize + 1}–{Math.min((productPage + 1) * productPageSize, productInvEntries.length)} of {productInvEntries.length}
                                    </p>
                                    <div className="flex items-center gap-2">
                                        <button
                                            onClick={() => setProductPage((p) => Math.max(0, p - 1))}
                                            disabled={productPage === 0}
                                            className="p-1.5 rounded-lg hover:bg-white/10 disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
                                        >
                                            <ChevronLeft className="w-4 h-4" />
                                        </button>
                                        <span className="text-xs text-muted-foreground">
                                            {productPage + 1} / {productTotalPages}
                                        </span>
                                        <button
                                            onClick={() => setProductPage((p) => Math.min(productTotalPages - 1, p + 1))}
                                            disabled={productPage >= productTotalPages - 1}
                                            className="p-1.5 rounded-lg hover:bg-white/10 disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
                                        >
                                            <ChevronRight className="w-4 h-4" />
                                        </button>
                                    </div>
                                </div>
                            )}
                            </>
                        )}
                    </GlassCard>
                </div>
            </div>
        </div>
    );
}
