"use client";

import React, { useEffect, useMemo, useState } from "react";
import { GlassCard } from "@/components/ui/glass-card";
import { PermissionGate } from "@/components/permission-gate";
import { showToast } from "@/components/ui/toast";
import {
    getStatementOrderList,
    bulkUpdateStatementOrder,
    StatementOrderEntry,
    getVehicleStatementOrderList,
    bulkUpdateVehicleStatementOrder,
    VehicleStatementOrderEntry,
} from "@/lib/api/station/customers";
import { ListOrdered, Save, RotateCcw, AlertTriangle, Search, ChevronLeft, ChevronRight, ChevronDown, ChevronRight as ChevronRightIcon, Truck } from "lucide-react";

type Row = StatementOrderEntry & { _draft: number | null };
type VehicleRow = VehicleStatementOrderEntry & { _draft: number | null };

const FREQS = ["MONTHLY", "BIWEEKLY", "WEEKLY", "CUSTOM"] as const;
type Freq = typeof FREQS[number];

const PAGE_SIZE = 16;

function compareRows(a: Row, b: Row): number {
    const ao = a._draft;
    const bo = b._draft;
    if (ao == null && bo == null) return a.name.localeCompare(b.name);
    if (ao == null) return 1;
    if (bo == null) return -1;
    if (ao !== bo) return ao - bo;
    return a.id - b.id;
}

export default function StatementOrderPage() {
    const [rows, setRows] = useState<Row[]>([]);
    const [original, setOriginal] = useState<Map<number, number | null>>(new Map());
    const [isLoading, setIsLoading] = useState(true);
    const [isSaving, setIsSaving] = useState(false);
    const [search, setSearch] = useState("");
    const [previewFreq, setPreviewFreq] = useState<Freq>("MONTHLY");
    const [page, setPage] = useState(0);
    /** Inline VEHICLE_WISE expansion state. Keyed by customerId. */
    const [expandedCustomers, setExpandedCustomers] = useState<Set<number>>(new Set());
    const [vehicleRowsByCustomer, setVehicleRowsByCustomer] = useState<Map<number, VehicleRow[]>>(new Map());
    const [vehicleOriginalByCustomer, setVehicleOriginalByCustomer] = useState<Map<number, Map<number, number | null>>>(new Map());
    const [loadingVehiclesFor, setLoadingVehiclesFor] = useState<Set<number>>(new Set());

    useEffect(() => {
        setPage(0);
    }, [search]);

    const load = async () => {
        setIsLoading(true);
        try {
            const data = await getStatementOrderList();
            const orig = new Map<number, number | null>();
            data.forEach((d) => orig.set(d.id, d.statementOrder ?? null));
            setOriginal(orig);
            setRows(data.map((d) => ({ ...d, _draft: d.statementOrder ?? null })));
        } catch (err) {
            console.error("Failed to load statement order list", err);
            showToast.error("Failed to load customers");
        } finally {
            setIsLoading(false);
        }
    };

    useEffect(() => {
        load();
    }, []);

    // Duplicate detection: only non-negative orders count.
    const dupOrders = useMemo(() => {
        const counts = new Map<number, number>();
        for (const r of rows) {
            if (r._draft == null || r._draft < 0) continue;
            counts.set(r._draft, (counts.get(r._draft) || 0) + 1);
        }
        const dupes = new Set<number>();
        counts.forEach((n, ord) => {
            if (n > 1) dupes.add(ord);
        });
        return dupes;
    }, [rows]);

    const dirtyIds = useMemo(() => {
        const s = new Set<number>();
        for (const r of rows) {
            if ((original.get(r.id) ?? null) !== (r._draft ?? null)) s.add(r.id);
        }
        return s;
    }, [rows, original]);

    /** Vehicles that have been edited but not yet saved. */
    const dirtyVehicleIds = useMemo(() => {
        const s = new Set<number>();
        for (const [custId, vehicles] of vehicleRowsByCustomer.entries()) {
            const orig = vehicleOriginalByCustomer.get(custId);
            if (!orig) continue;
            for (const v of vehicles) {
                if ((orig.get(v.id) ?? null) !== (v._draft ?? null)) s.add(v.id);
            }
        }
        return s;
    }, [vehicleRowsByCustomer, vehicleOriginalByCustomer]);

    const totalDirty = dirtyIds.size + dirtyVehicleIds.size;

    const stats = useMemo(() => {
        let ranked = 0;
        let unranked = 0;
        let skipped = 0;
        for (const r of rows) {
            if (r._draft == null) unranked++;
            else if (r._draft < 0) skipped++;
            else ranked++;
        }
        return { total: rows.length, ranked, unranked, skipped };
    }, [rows]);

    const filteredRows = useMemo(() => {
        const sorted = [...rows].sort(compareRows);
        const q = search.trim().toLowerCase();
        if (!q) return sorted;
        return sorted.filter((r) =>
            r.name.toLowerCase().includes(q) ||
            (r.groupName || "").toLowerCase().includes(q) ||
            (r.categoryName || "").toLowerCase().includes(q)
        );
    }, [rows, search]);

    const totalPages = Math.max(1, Math.ceil(filteredRows.length / PAGE_SIZE));
    const safePage = Math.min(page, totalPages - 1);
    const pagedRows = filteredRows.slice(safePage * PAGE_SIZE, (safePage + 1) * PAGE_SIZE);

    const previewRows = useMemo(() => {
        const eligible = rows.filter((r) => r.statementFrequency === previewFreq);
        const active: Row[] = [];
        const skipped: Row[] = [];
        for (const r of eligible) {
            if (r._draft != null && r._draft < 0) skipped.push(r);
            else active.push(r);
        }
        active.sort(compareRows);
        skipped.sort((a, b) => a.name.localeCompare(b.name));
        return { active, skipped };
    }, [rows, previewFreq]);

    const updateOrder = (id: number, value: string) => {
        const v = value.trim();
        const next = v === "" ? null : Number.parseInt(v, 10);
        if (v !== "" && Number.isNaN(next as number)) return;
        setRows((prev) => prev.map((r) => (r.id === id ? { ...r, _draft: next } : r)));
    };

    const handleReset = () => {
        if (totalDirty === 0) return;
        if (!confirm(`Discard ${totalDirty} unsaved change(s)?`)) return;
        setRows((prev) => prev.map((r) => ({ ...r, _draft: original.get(r.id) ?? null })));
        setVehicleRowsByCustomer((prev) => {
            const next = new Map(prev);
            for (const [custId, vehicles] of prev.entries()) {
                const orig = vehicleOriginalByCustomer.get(custId);
                if (!orig) continue;
                next.set(custId, vehicles.map((v) => ({ ...v, _draft: orig.get(v.id) ?? null })));
            }
            return next;
        });
    };

    const toggleExpand = async (customerId: number) => {
        if (expandedCustomers.has(customerId)) {
            setExpandedCustomers((prev) => {
                const next = new Set(prev);
                next.delete(customerId);
                return next;
            });
            return;
        }
        // Fetch on first expand (or refresh) — keep cached on subsequent expands of same row.
        if (!vehicleRowsByCustomer.has(customerId)) {
            setLoadingVehiclesFor((prev) => new Set(prev).add(customerId));
            try {
                const data = await getVehicleStatementOrderList(customerId);
                const orig = new Map<number, number | null>();
                data.forEach((d) => orig.set(d.id, d.statementOrder ?? null));
                setVehicleOriginalByCustomer((prev) => {
                    const next = new Map(prev);
                    next.set(customerId, orig);
                    return next;
                });
                setVehicleRowsByCustomer((prev) => {
                    const next = new Map(prev);
                    next.set(customerId, data.map((d) => ({ ...d, _draft: d.statementOrder ?? null })));
                    return next;
                });
            } catch (err) {
                console.error("Failed to load vehicles", err);
                showToast.error("Failed to load vehicles");
                return;
            } finally {
                setLoadingVehiclesFor((prev) => {
                    const next = new Set(prev);
                    next.delete(customerId);
                    return next;
                });
            }
        }
        setExpandedCustomers((prev) => new Set(prev).add(customerId));
    };

    const updateVehicleOrder = (customerId: number, vehicleId: number, value: string) => {
        const v = value.trim();
        const next = v === "" ? null : Number.parseInt(v, 10);
        if (v !== "" && Number.isNaN(next as number)) return;
        setVehicleRowsByCustomer((prev) => {
            const map = new Map(prev);
            const list = map.get(customerId) || [];
            map.set(customerId, list.map((r) => (r.id === vehicleId ? { ...r, _draft: next } : r)));
            return map;
        });
    };

    const handleSave = async () => {
        if (dupOrders.size > 0) {
            showToast.error("Resolve duplicate orders before saving");
            return;
        }
        if (totalDirty === 0) {
            showToast.info("No changes to save");
            return;
        }
        const customerUpdates: { customerId: number; statementOrder: number | null }[] = [];
        for (const r of rows) {
            if (dirtyIds.has(r.id)) {
                customerUpdates.push({ customerId: r.id, statementOrder: r._draft ?? null });
            }
        }
        const vehicleUpdates: { vehicleId: number; statementOrder: number | null }[] = [];
        for (const [, vehicles] of vehicleRowsByCustomer.entries()) {
            for (const v of vehicles) {
                if (dirtyVehicleIds.has(v.id)) {
                    vehicleUpdates.push({ vehicleId: v.id, statementOrder: v._draft ?? null });
                }
            }
        }
        setIsSaving(true);
        try {
            if (customerUpdates.length > 0) {
                const fresh = await bulkUpdateStatementOrder(customerUpdates);
                const orig = new Map<number, number | null>();
                fresh.forEach((d) => orig.set(d.id, d.statementOrder ?? null));
                setOriginal(orig);
                setRows(fresh.map((d) => ({ ...d, _draft: d.statementOrder ?? null })));
            }
            if (vehicleUpdates.length > 0) {
                await bulkUpdateVehicleStatementOrder(vehicleUpdates);
                // Refetch each affected customer's vehicles to get the canonical post-state.
                const affectedCustomerIds = new Set<number>();
                for (const [custId, vehicles] of vehicleRowsByCustomer.entries()) {
                    if (vehicles.some((v) => dirtyVehicleIds.has(v.id))) affectedCustomerIds.add(custId);
                }
                for (const custId of affectedCustomerIds) {
                    const data = await getVehicleStatementOrderList(custId);
                    const orig = new Map<number, number | null>();
                    data.forEach((d) => orig.set(d.id, d.statementOrder ?? null));
                    setVehicleOriginalByCustomer((prev) => {
                        const next = new Map(prev);
                        next.set(custId, orig);
                        return next;
                    });
                    setVehicleRowsByCustomer((prev) => {
                        const next = new Map(prev);
                        next.set(custId, data.map((d) => ({ ...d, _draft: d.statementOrder ?? null })));
                        return next;
                    });
                }
            }
            showToast.success(`Saved ${customerUpdates.length + vehicleUpdates.length} change(s)`);
        } catch (err: any) {
            console.error("Bulk save failed", err);
            showToast.error(err?.message || "Failed to save");
        } finally {
            setIsSaving(false);
        }
    };

    const saveDisabled = isSaving || dupOrders.size > 0 || totalDirty === 0;

    return (
        <div className="p-6 h-screen flex flex-col bg-background overflow-hidden">
            <div className="max-w-[1600px] mx-auto w-full flex flex-col flex-1 min-h-0">
                {/* Header */}
                <div className="flex justify-between items-start mb-4 flex-shrink-0 gap-4">
                    <div>
                        <h1 className="text-3xl font-bold text-foreground tracking-tight flex items-center gap-3">
                            <ListOrdered className="w-8 h-8 text-orange-500" />
                            Statement <span className="text-gradient">Order</span>
                        </h1>
                        <p className="text-muted-foreground mt-1 text-sm">
                            Set the sequence in which Auto-Generate Drafts and bulk PDF process customers.
                            Lower numbers go first. Use <span className="font-mono text-orange-400">-1</span> to skip a customer.
                            Blocked customers are shown so you can pre-configure their order — auto-gen still skips them at runtime until they're unblocked.
                        </p>
                    </div>
                    <div className="flex gap-2">
                        <button
                            onClick={handleReset}
                            disabled={totalDirty === 0 || isSaving}
                            className="px-4 py-2 rounded-lg border border-white/10 text-sm font-medium text-foreground hover:bg-white/5 disabled:opacity-40 disabled:cursor-not-allowed flex items-center gap-2"
                        >
                            <RotateCcw className="w-4 h-4" />
                            Reset
                        </button>
                        <PermissionGate permission="CUSTOMER_UPDATE">
                            <button
                                onClick={handleSave}
                                disabled={saveDisabled}
                                title={
                                    dupOrders.size > 0
                                        ? `Resolve ${dupOrders.size} duplicate order(s) before saving`
                                        : totalDirty === 0
                                            ? "No changes to save"
                                            : `Save ${totalDirty} change(s)`
                                }
                                className="btn-gradient px-5 py-2 rounded-lg font-medium flex items-center gap-2 disabled:opacity-40 disabled:cursor-not-allowed"
                            >
                                <Save className="w-4 h-4" />
                                {isSaving ? "Saving…" : `Save ${totalDirty > 0 ? `(${totalDirty})` : ""}`}
                            </button>
                        </PermissionGate>
                    </div>
                </div>

                {/* Stats strip */}
                <div className="flex flex-wrap gap-2 mb-4 flex-shrink-0">
                    <Stat label="Total" value={stats.total} />
                    <Stat label="Ranked" value={stats.ranked} tone="success" />
                    <Stat label="Unranked" value={stats.unranked} tone="muted" />
                    <Stat label="Skipped (-1)" value={stats.skipped} tone="danger" />
                    {dupOrders.size > 0 && (
                        <div className="px-3 py-1.5 rounded-lg bg-yellow-500/10 border border-yellow-500/30 text-yellow-300 text-sm flex items-center gap-2">
                            <AlertTriangle className="w-4 h-4" />
                            <span className="font-medium">
                                {dupOrders.size} duplicate group{dupOrders.size === 1 ? "" : "s"}:{" "}
                                {[...dupOrders].sort((a, b) => a - b).join(", ")}
                            </span>
                        </div>
                    )}
                </div>

                {/* Body */}
                <div className="grid grid-cols-1 lg:grid-cols-[1fr_360px] gap-4 flex-1 min-h-0">
                    {/* Editable table */}
                    <GlassCard className="p-4 flex flex-col min-h-0">
                        <div className="flex items-center gap-2 mb-2 flex-shrink-0">
                            <Search className="w-4 h-4 text-muted-foreground" />
                            <input
                                type="text"
                                placeholder="Search customer / group / category…"
                                value={search}
                                onChange={(e) => setSearch(e.target.value)}
                                className="flex-1 bg-white/5 border border-white/10 rounded-lg px-3 py-1 text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-cyan-500/40"
                            />
                        </div>
                        <div className="flex-1 min-h-0 overflow-auto">
                            {isLoading ? (
                                <div className="text-center py-12 text-muted-foreground">Loading…</div>
                            ) : filteredRows.length === 0 ? (
                                <div className="text-center py-12 text-muted-foreground">
                                    {rows.length === 0
                                        ? "No statement-eligible customers. Set a Statement Frequency on a customer first."
                                        : "No customers match the search."}
                                </div>
                            ) : (
                                <table className="w-full text-sm">
                                    <thead className="sticky top-0 bg-background/95 backdrop-blur z-10">
                                        <tr className="text-left text-xs uppercase tracking-wider text-muted-foreground border-b border-white/10">
                                            <th className="px-2 py-1.5 w-8"></th>
                                            <th className="px-2 py-1.5 w-12">#</th>
                                            <th className="px-2 py-1.5">Customer</th>
                                            <th className="px-2 py-1.5">Group</th>
                                            <th className="px-2 py-1.5">Category</th>
                                            <th className="px-2 py-1.5">Freq</th>
                                            <th className="px-2 py-1.5">Grouping</th>
                                            <th className="px-2 py-1.5">Status</th>
                                            <th className="px-2 py-1.5 w-32 text-right">Order</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {pagedRows.map((r, idx) => {
                                            const isDup = r._draft != null && r._draft >= 0 && dupOrders.has(r._draft);
                                            const isSkip = r._draft != null && r._draft < 0;
                                            const isDirty = dirtyIds.has(r.id);
                                            const isVehicleWise = r.statementGrouping === "VEHICLE_WISE";
                                            const isExpanded = expandedCustomers.has(r.id);
                                            const isLoadingVehicles = loadingVehiclesFor.has(r.id);
                                            const vehicleRows = vehicleRowsByCustomer.get(r.id) || [];
                                            const vehicleDupOrders = (() => {
                                                const counts = new Map<number, number>();
                                                for (const v of vehicleRows) {
                                                    if (v._draft == null || v._draft < 0) continue;
                                                    counts.set(v._draft, (counts.get(v._draft) || 0) + 1);
                                                }
                                                const dupes = new Set<number>();
                                                counts.forEach((n, ord) => { if (n > 1) dupes.add(ord); });
                                                return dupes;
                                            })();
                                            return (
                                                <React.Fragment key={r.id}>
                                                    <tr
                                                        className={[
                                                            "border-b border-white/5 hover:bg-white/[0.03]",
                                                            isDup ? "bg-yellow-500/10" : "",
                                                            isSkip ? "bg-red-500/5 opacity-70" : "",
                                                        ].join(" ")}
                                                    >
                                                        <td className="px-2 py-1">
                                                            {isVehicleWise && (
                                                                <button
                                                                    onClick={() => toggleExpand(r.id)}
                                                                    className="text-muted-foreground hover:text-foreground"
                                                                    title={isExpanded ? "Collapse vehicles" : "Expand to set per-vehicle order"}
                                                                >
                                                                    {isExpanded ? <ChevronDown className="w-4 h-4" /> : <ChevronRightIcon className="w-4 h-4" />}
                                                                </button>
                                                            )}
                                                        </td>
                                                        <td className="px-2 py-1 text-muted-foreground">{safePage * PAGE_SIZE + idx + 1}</td>
                                                        <td className="px-2 py-1 text-foreground font-medium">
                                                            <span className="flex items-center gap-2">
                                                                {r.name}
                                                                {isDirty && (
                                                                    <span className="inline-block w-1.5 h-1.5 rounded-full bg-cyan-400" title="Unsaved" />
                                                                )}
                                                            </span>
                                                        </td>
                                                        <td className="px-2 py-1 text-muted-foreground">{r.groupName || "—"}</td>
                                                        <td className="px-2 py-1 text-muted-foreground">{r.categoryName || "—"}</td>
                                                        <td className="px-2 py-1 text-muted-foreground">{r.statementFrequency || "—"}</td>
                                                        <td className="px-2 py-1 text-muted-foreground">{r.statementGrouping?.replace(/_/g, " ") || "—"}</td>
                                                        <td className="px-2 py-1">
                                                            {r.status === "BLOCKED" ? (
                                                                <span className="text-[10px] uppercase tracking-wide font-bold px-1.5 py-0.5 rounded bg-red-500/20 text-red-300 border border-red-500/30">
                                                                    Blocked
                                                                </span>
                                                            ) : (
                                                                <span className="text-xs text-emerald-300">Active</span>
                                                            )}
                                                        </td>
                                                        <td className="px-2 py-1 text-right">
                                                            <div className="flex items-center justify-end gap-2">
                                                                {isSkip && (
                                                                    <span className="text-[10px] uppercase tracking-wide font-bold px-1.5 py-0.5 rounded bg-red-500/20 text-red-300 border border-red-500/30">
                                                                        Skip
                                                                    </span>
                                                                )}
                                                                <input
                                                                    type="number"
                                                                    step="1"
                                                                    value={r._draft ?? ""}
                                                                    onChange={(e) => updateOrder(r.id, e.target.value)}
                                                                    placeholder="—"
                                                                    className={[
                                                                        "w-20 bg-white/5 border rounded px-2 py-1 text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-cyan-500/40 text-right",
                                                                        isDup ? "border-yellow-500/60" : "border-white/10",
                                                                    ].join(" ")}
                                                                />
                                                            </div>
                                                        </td>
                                                    </tr>
                                                    {isExpanded && (
                                                        <tr className="bg-white/[0.02]">
                                                            <td colSpan={9} className="px-2 py-2">
                                                                {isLoadingVehicles ? (
                                                                    <div className="text-xs text-muted-foreground py-2 pl-8">Loading vehicles…</div>
                                                                ) : vehicleRows.length === 0 ? (
                                                                    <div className="text-xs text-muted-foreground py-2 pl-8">No vehicles for this customer.</div>
                                                                ) : (
                                                                    <div className="pl-8">
                                                                        <div className="text-[10px] uppercase tracking-wider text-muted-foreground mb-1 flex items-center gap-1.5">
                                                                            <Truck className="w-3 h-3" />
                                                                            Vehicle order ({vehicleRows.length} vehicles)
                                                                        </div>
                                                                        <div className="grid grid-cols-1 md:grid-cols-2 gap-x-6 gap-y-1">
                                                                            {vehicleRows.map((v) => {
                                                                                const vIsDup = v._draft != null && v._draft >= 0 && vehicleDupOrders.has(v._draft);
                                                                                const vIsSkip = v._draft != null && v._draft < 0;
                                                                                const vIsDirty = dirtyVehicleIds.has(v.id);
                                                                                return (
                                                                                    <div key={v.id} className="flex items-center gap-2 text-xs">
                                                                                        <span className="text-muted-foreground">↳</span>
                                                                                        <span className="font-mono text-foreground flex-1">
                                                                                            {v.vehicleNumber}
                                                                                            {vIsDirty && <span className="ml-1 inline-block w-1.5 h-1.5 rounded-full bg-cyan-400" title="Unsaved" />}
                                                                                        </span>
                                                                                        {vIsSkip && (
                                                                                            <span className="text-[9px] uppercase tracking-wide font-bold px-1 py-0.5 rounded bg-red-500/20 text-red-300 border border-red-500/30">
                                                                                                Skip
                                                                                            </span>
                                                                                        )}
                                                                                        <input
                                                                                            type="number"
                                                                                            step="1"
                                                                                            value={v._draft ?? ""}
                                                                                            onChange={(e) => updateVehicleOrder(r.id, v.id, e.target.value)}
                                                                                            placeholder="—"
                                                                                            className={[
                                                                                                "w-16 bg-white/5 border rounded px-2 py-0.5 text-xs text-foreground focus:outline-none focus:ring-2 focus:ring-cyan-500/40 text-right",
                                                                                                vIsDup ? "border-yellow-500/60" : "border-white/10",
                                                                                            ].join(" ")}
                                                                                        />
                                                                                    </div>
                                                                                );
                                                                            })}
                                                                        </div>
                                                                    </div>
                                                                )}
                                                            </td>
                                                        </tr>
                                                    )}
                                                </React.Fragment>
                                            );
                                        })}
                                    </tbody>
                                </table>
                            )}
                        </div>
                        {filteredRows.length > PAGE_SIZE && (
                            <div className="flex items-center justify-between mt-3 flex-shrink-0 text-xs text-muted-foreground">
                                <span>
                                    Showing {safePage * PAGE_SIZE + 1}–{Math.min((safePage + 1) * PAGE_SIZE, filteredRows.length)} of {filteredRows.length}
                                </span>
                                <div className="flex items-center gap-2">
                                    <button
                                        onClick={() => setPage((p) => Math.max(0, p - 1))}
                                        disabled={safePage === 0}
                                        className="px-2 py-1 rounded border border-white/10 hover:bg-white/5 disabled:opacity-40 disabled:cursor-not-allowed flex items-center gap-1"
                                    >
                                        <ChevronLeft className="w-3.5 h-3.5" />
                                        Prev
                                    </button>
                                    <span className="text-foreground">Page {safePage + 1} of {totalPages}</span>
                                    <button
                                        onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                                        disabled={safePage >= totalPages - 1}
                                        className="px-2 py-1 rounded border border-white/10 hover:bg-white/5 disabled:opacity-40 disabled:cursor-not-allowed flex items-center gap-1"
                                    >
                                        Next
                                        <ChevronRight className="w-3.5 h-3.5" />
                                    </button>
                                </div>
                            </div>
                        )}
                    </GlassCard>

                    {/* Preview */}
                    <GlassCard className="p-4 flex flex-col min-h-0">
                        <div className="flex items-center justify-between mb-3 flex-shrink-0">
                            <h3 className="text-sm font-semibold text-foreground">Auto-gen preview</h3>
                            <select
                                value={previewFreq}
                                onChange={(e) => setPreviewFreq(e.target.value as Freq)}
                                className="bg-white/5 border border-white/10 rounded-lg px-2 py-1 text-xs text-foreground focus:outline-none focus:ring-2 focus:ring-cyan-500/40"
                            >
                                {FREQS.map((f) => (
                                    <option key={f} value={f} className="bg-background text-foreground">{f}</option>
                                ))}
                            </select>
                        </div>
                        <p className="text-xs text-muted-foreground mb-3 flex-shrink-0">
                            Order in which Auto-Generate Drafts will process <span className="text-foreground">{previewFreq}</span> customers.
                        </p>
                        <div className="flex-1 min-h-0 overflow-auto">
                            {previewRows.active.length === 0 && previewRows.skipped.length === 0 ? (
                                <div className="text-center py-8 text-muted-foreground text-sm">
                                    No {previewFreq.toLowerCase()} customers configured.
                                </div>
                            ) : (
                                <ol className="space-y-1">
                                    {previewRows.active.map((r, idx) => {
                                        const isBlocked = r.status === "BLOCKED";
                                        return (
                                            <li
                                                key={r.id}
                                                className={`flex items-baseline gap-2 text-sm py-0.5 ${isBlocked ? "opacity-60" : ""}`}
                                            >
                                                <span className="text-muted-foreground font-mono text-xs w-6 text-right shrink-0">
                                                    {idx + 1}.
                                                </span>
                                                <span className="text-foreground truncate flex-1">{r.name}</span>
                                                {isBlocked && (
                                                    <span className="text-[9px] uppercase tracking-wide font-bold px-1.5 py-0.5 rounded bg-red-500/20 text-red-300 border border-red-500/30 shrink-0">
                                                        Blocked
                                                    </span>
                                                )}
                                                <span className="text-muted-foreground text-xs font-mono shrink-0">
                                                    {r._draft ?? "—"}
                                                </span>
                                            </li>
                                        );
                                    })}
                                    {previewRows.skipped.length > 0 && (
                                        <>
                                            <li className="border-t border-white/10 my-2 pt-2 text-[10px] uppercase tracking-wider text-muted-foreground">
                                                Skipped
                                            </li>
                                            {previewRows.skipped.map((r) => (
                                                <li
                                                    key={r.id}
                                                    className="flex items-baseline gap-2 text-sm py-0.5 line-through opacity-60"
                                                >
                                                    <span className="w-6 shrink-0" />
                                                    <span className="text-muted-foreground truncate flex-1">{r.name}</span>
                                                    <span className="text-red-300 text-xs font-mono shrink-0">
                                                        {r._draft}
                                                    </span>
                                                </li>
                                            ))}
                                        </>
                                    )}
                                </ol>
                            )}
                        </div>
                    </GlassCard>
                </div>
            </div>
        </div>
    );
}

function Stat({ label, value, tone }: { label: string; value: number; tone?: "success" | "muted" | "danger" }) {
    const toneClass =
        tone === "success" ? "text-emerald-300" :
        tone === "danger" ? "text-red-300" :
        tone === "muted" ? "text-muted-foreground" :
        "text-foreground";
    return (
        <div className="px-3 py-1.5 rounded-lg bg-white/5 border border-white/10 text-sm flex items-center gap-2">
            <span className="text-muted-foreground">{label}:</span>
            <span className={`font-semibold ${toneClass}`}>{value}</span>
        </div>
    );
}
