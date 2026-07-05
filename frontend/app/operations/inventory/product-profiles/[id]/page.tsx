"use client";

import { useCallback, useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import {
    Bar,
    BarChart,
    CartesianGrid,
    LabelList,
    ResponsiveContainer,
    Tooltip,
    XAxis,
    YAxis,
} from "recharts";
import { GlassCard } from "@/components/ui/glass-card";
import { Badge } from "@/components/ui/badge";
import { ProductImage } from "@/components/ui/product-image";
import { TablePagination, useClientPagination } from "@/components/ui/table-pagination";
import { fmtProductQty } from "@/lib/utils";
import {
    CashierStock,
    getCashierStockByProduct,
    getGodownStockByProduct,
    getProduct,
    getProductInventories,
    getProductSalesHistory,
    GodownStock,
    Product,
    ProductInventory,
    ProductSalesHistory,
} from "@/lib/api/station";
import { AlertTriangle, ArrowLeft, IndianRupee, ShoppingBag, TrendingUp, Warehouse } from "lucide-react";

const TOOLTIP_STYLE = {
    backgroundColor: "rgba(0,0,0,0.85)",
    border: "1px solid rgba(255,255,255,0.1)",
    borderRadius: "12px",
    fontSize: "12px",
};

type Preset = "weekly" | "monthly" | "yearly" | "custom";
type Granularity = "DAY" | "WEEK" | "MONTH";

const toIso = (d: Date) => d.toISOString().split("T")[0];

function presetRange(preset: Exclude<Preset, "custom">): { fromDate: string; toDate: string; granularity: Granularity } {
    const now = new Date();
    if (preset === "weekly") {
        return { fromDate: toIso(new Date(now.getTime() - 6 * 86400000)), toDate: toIso(now), granularity: "DAY" };
    }
    if (preset === "monthly") {
        return { fromDate: toIso(new Date(now.getTime() - 29 * 86400000)), toDate: toIso(now), granularity: "DAY" };
    }
    const from = new Date(now.getFullYear(), now.getMonth() - 11, 1);
    return { fromDate: toIso(from), toDate: toIso(now), granularity: "MONTH" };
}

function autoGranularity(fromDate: string, toDate: string): Granularity {
    const span = (new Date(toDate).getTime() - new Date(fromDate).getTime()) / 86400000;
    if (span <= 92) return "DAY";
    if (span <= 365) return "WEEK";
    return "MONTH";
}

function formatBucketLabel(dateStr: string, granularity: Granularity) {
    const d = new Date(dateStr);
    if (granularity === "MONTH") {
        return d.toLocaleDateString("en-IN", { month: "short", year: "2-digit" });
    }
    return d.toLocaleDateString("en-IN", { day: "2-digit", month: "short" });
}

function formatCurrency(val?: number | null) {
    if (val == null) return "0.00";
    return val.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

export default function ProductProfilePage() {
    const params = useParams();
    const router = useRouter();
    const productId = Number(params.id);

    const [product, setProduct] = useState<Product | null>(null);
    const [godown, setGodown] = useState<GodownStock | null>(null);
    const [cashier, setCashier] = useState<CashierStock | null>(null);
    const [sales, setSales] = useState<ProductSalesHistory | null>(null);
    const [inventories, setInventories] = useState<ProductInventory[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [rangeLoading, setRangeLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [rangeError, setRangeError] = useState<string | null>(null);

    const [preset, setPreset] = useState<Preset>("monthly");
    const [{ fromDate, toDate }, setRange] = useState(() => presetRange("monthly"));

    useEffect(() => {
        if (!productId) return;
        getProduct(productId)
            .then(setProduct)
            .catch((err) => setError(err.message || "Product not found"))
            .finally(() => setIsLoading(false));
        // Stock snapshots are independent of the profile fetch — a 403 here
        // should not blank the whole page, only its section.
        getGodownStockByProduct(productId).then((rows) => setGodown(rows[0] ?? null)).catch(() => {});
        getCashierStockByProduct(productId).then((rows) => setCashier(rows[0] ?? null)).catch(() => {});
    }, [productId]);

    const loadRangeData = useCallback(() => {
        if (!productId || !fromDate || !toDate || fromDate > toDate) return;
        const granularity = preset === "custom" ? autoGranularity(fromDate, toDate) : presetRange(preset).granularity;
        setRangeLoading(true);
        setRangeError(null);
        Promise.all([
            getProductSalesHistory(productId, { fromDate, toDate, granularity }),
            getProductInventories({ productId, fromDate, toDate }),
        ])
            .then(([history, invRows]) => {
                setSales(history);
                setInventories(invRows);
            })
            .catch((err) => setRangeError(err.message || "Failed to load sales data"))
            .finally(() => setRangeLoading(false));
    }, [productId, fromDate, toDate, preset]);

    useEffect(() => {
        loadRangeData();
    }, [loadRangeData]);

    const sortedInv = [...inventories].sort((a, b) => (a.date < b.date ? 1 : -1));
    const { page, setPage, totalPages, totalElements, pageSize, paginatedData: pagedInv } = useClientPagination(sortedInv, 10);

    if (isLoading) {
        return (
            <div className="min-h-screen bg-background p-8 flex flex-col items-center justify-center">
                <div className="w-12 h-12 border-4 border-primary/20 border-t-primary rounded-full animate-spin mb-4" />
                <p className="text-muted-foreground animate-pulse">Loading product profile...</p>
            </div>
        );
    }

    if (error || !product) {
        return (
            <div className="min-h-screen bg-background p-8 flex flex-col items-center justify-center">
                <p className="text-red-500 mb-2">Failed to load product</p>
                <p className="text-muted-foreground text-sm mb-4">{error}</p>
                <button
                    onClick={() => router.push("/operations/inventory/product-profiles")}
                    className="px-4 py-2 text-sm font-medium rounded-lg bg-primary text-primary-foreground"
                >
                    Back to profiles
                </button>
            </div>
        );
    }

    const chartData = (sales?.points ?? []).map((pt) => ({
        label: formatBucketLabel(pt.date, sales?.granularity ?? "DAY"),
        quantity: pt.quantity,
        amount: pt.amount,
    }));

    const applyPreset = (p: Exclude<Preset, "custom">) => {
        setPreset(p);
        setRange(presetRange(p));
    };

    const infoRows: { label: string; value: string }[] = [
        { label: "Grade", value: product.gradeType?.name || "—" },
        { label: "Variant", value: product.oilType?.name || "—" },
        { label: "Supplier", value: product.supplier?.name || "—" },
        {
            label: "Price",
            value: `₹${formatCurrency(product.price)} / ${product.unit?.toLowerCase() || "unit"}`,
        },
        { label: "Brand", value: product.brand || "—" },
        { label: "HSN Code", value: product.hsnCode || "—" },
    ];

    const lowGodown = godown != null && godown.reorderLevel != null && godown.currentStock <= godown.reorderLevel;

    return (
        <div className="min-h-screen bg-background p-6 md:p-8 transition-colors duration-300">
            <div className="max-w-7xl mx-auto space-y-6">
                {/* Header */}
                <div className="flex items-center gap-4">
                    <button
                        onClick={() => router.push("/operations/inventory/product-profiles")}
                        className="p-2 rounded-xl bg-card border border-border hover:bg-white/5 transition-colors"
                        aria-label="Back to product profiles"
                    >
                        <ArrowLeft className="w-5 h-5" />
                    </button>
                    <div className="flex-1 min-w-0">
                        <h1 className="text-3xl font-bold text-foreground tracking-tight truncate">
                            <span className="text-gradient">{product.name}</span>
                        </h1>
                        <div className="flex items-center gap-2 mt-1 flex-wrap">
                            {product.gradeType?.name && <Badge className="text-[10px]">{product.gradeType.name}</Badge>}
                            {product.oilType?.name && (
                                <span className="text-xs text-muted-foreground">{product.oilType.name}</span>
                            )}
                            <Badge variant={product.active ? "success" : "danger"} className="text-[10px]">
                                {product.active ? "Active" : "Inactive"}
                            </Badge>
                        </div>
                    </div>
                </div>

                {/* Sales history chart */}
                <GlassCard className="p-6">
                    <div className="flex flex-col lg:flex-row lg:items-center lg:justify-between gap-4 mb-4">
                        <div className="flex items-center gap-2">
                            <div className="p-2 bg-primary/10 rounded-lg">
                                <TrendingUp className="w-5 h-5 text-primary" />
                            </div>
                            <div>
                                <h2 className="text-lg font-semibold text-foreground">Sales History</h2>
                                <p className="text-xs text-muted-foreground">
                                    Invoiced sales &middot; {fromDate} to {toDate}
                                </p>
                            </div>
                        </div>
                        <div className="flex flex-wrap items-center gap-2">
                            {(["weekly", "monthly", "yearly"] as const).map((p) => (
                                <button
                                    key={p}
                                    onClick={() => applyPreset(p)}
                                    className={`px-4 py-2 text-sm font-medium rounded-lg transition-colors capitalize ${
                                        preset === p
                                            ? "bg-primary text-primary-foreground"
                                            : "bg-muted text-muted-foreground hover:bg-muted/80"
                                    }`}
                                >
                                    {p}
                                </button>
                            ))}
                            <input
                                type="date"
                                value={fromDate}
                                max={toDate}
                                onChange={(e) => {
                                    setPreset("custom");
                                    setRange((r) => ({ ...r, fromDate: e.target.value }));
                                }}
                                className="px-3 py-2 bg-card border border-border rounded-xl text-sm"
                            />
                            <span className="text-muted-foreground text-sm">to</span>
                            <input
                                type="date"
                                value={toDate}
                                min={fromDate}
                                onChange={(e) => {
                                    setPreset("custom");
                                    setRange((r) => ({ ...r, toDate: e.target.value }));
                                }}
                                className="px-3 py-2 bg-card border border-border rounded-xl text-sm"
                            />
                        </div>
                    </div>

                    {rangeError ? (
                        <div className="h-72 flex flex-col items-center justify-center text-sm">
                            <p className="text-red-500 mb-1">Failed to load sales history</p>
                            <p className="text-muted-foreground">{rangeError}</p>
                        </div>
                    ) : (
                        <div className={`h-72 ${rangeLoading ? "opacity-50" : ""} transition-opacity`}>
                            <ResponsiveContainer width="100%" height="100%">
                                <BarChart data={chartData} margin={{ top: 24, right: 8, left: 0, bottom: 0 }}>
                                    <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" vertical={false} />
                                    <XAxis
                                        dataKey="label"
                                        tick={{ fontSize: 11 }}
                                        stroke="#888"
                                        interval="preserveStartEnd"
                                    />
                                    <YAxis tick={{ fontSize: 11 }} stroke="#888" allowDecimals={false} />
                                    <Tooltip
                                        contentStyle={TOOLTIP_STYLE}
                                        formatter={(value, _name, item) => {
                                            const amount = (item?.payload as { amount?: number } | undefined)?.amount ?? 0;
                                            const qty = fmtProductQty(Number(value), product.unit);
                                            const unit = product.unit?.toLowerCase() || "";
                                            return [`${qty} ${unit} · ₹${formatCurrency(amount)}`, "Sold"];
                                        }}
                                    />
                                    <Bar dataKey="quantity" fill="#f97316" radius={[6, 6, 0, 0]} maxBarSize={48}>
                                        <LabelList
                                            dataKey="quantity"
                                            position="top"
                                            fontSize={10}
                                            fill="#a1a1aa"
                                            formatter={(value) => {
                                                const n = Number(value);
                                                return n > 0 ? fmtProductQty(n, product.unit) : "";
                                            }}
                                        />
                                    </Bar>
                                </BarChart>
                            </ResponsiveContainer>
                        </div>
                    )}

                    <div className="flex flex-wrap gap-6 mt-4 pt-4 border-t border-border/50 text-sm">
                        <div>
                            <span className="text-muted-foreground">Total sold: </span>
                            <span className="font-bold text-foreground">
                                {fmtProductQty(sales?.totalQuantity ?? 0, product.unit)} {product.unit?.toLowerCase()}
                            </span>
                        </div>
                        <div>
                            <span className="text-muted-foreground">Total revenue: </span>
                            <span className="font-bold text-green-500">&#8377;{formatCurrency(sales?.totalAmount ?? 0)}</span>
                        </div>
                    </div>
                </GlassCard>

                {/* Two-column layout: inventory log (left) + metadata (right) */}
                <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 items-start">
                    <GlassCard className="overflow-hidden border-none p-0 lg:col-span-2">
                        <div className="px-6 py-4 border-b border-border/50 flex items-center justify-between">
                            <h2 className="text-lg font-semibold text-foreground">Inventory Log</h2>
                            <span className="text-xs text-muted-foreground">
                                {fromDate} to {toDate}
                            </span>
                        </div>
                        <div className="overflow-x-auto">
                            <table className="w-full text-left border-collapse">
                                <thead>
                                    <tr className="bg-white/5 border-b border-border/50">
                                        {["Date", "Open", "Income", "Total", "Close", "Sales", "Rate", "Amount"].map((h) => (
                                            <th
                                                key={h}
                                                className="px-4 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground"
                                            >
                                                {h}
                                            </th>
                                        ))}
                                    </tr>
                                </thead>
                                <tbody className="divide-y divide-border/30">
                                    {pagedInv.length === 0 ? (
                                        <tr>
                                            <td colSpan={8} className="px-4 py-10 text-center text-sm text-muted-foreground">
                                                No inventory records in this range
                                            </td>
                                        </tr>
                                    ) : (
                                        pagedInv.map((inv) => (
                                            <tr key={inv.id} className="hover:bg-white/5 transition-colors">
                                                <td className="px-4 py-4 text-sm font-medium text-foreground whitespace-nowrap">
                                                    {new Date(inv.date).toLocaleDateString("en-IN", {
                                                        day: "2-digit",
                                                        month: "short",
                                                        year: "numeric",
                                                    })}
                                                </td>
                                                <td className="px-4 py-4 text-sm">{fmtProductQty(inv.openStock, product.unit)}</td>
                                                <td className="px-4 py-4 text-sm">{fmtProductQty(inv.incomeStock, product.unit)}</td>
                                                <td className="px-4 py-4 text-sm">{fmtProductQty(inv.totalStock, product.unit)}</td>
                                                <td className="px-4 py-4 text-sm">{fmtProductQty(inv.closeStock, product.unit)}</td>
                                                <td className="px-4 py-4 text-sm font-semibold text-primary">
                                                    {fmtProductQty(inv.sales, product.unit)}
                                                </td>
                                                <td className="px-4 py-4 text-sm whitespace-nowrap">
                                                    {inv.rate != null ? (
                                                        <span className="inline-flex items-center">
                                                            <IndianRupee className="w-3 h-3" />
                                                            {formatCurrency(inv.rate)}
                                                        </span>
                                                    ) : (
                                                        "—"
                                                    )}
                                                </td>
                                                <td className="px-4 py-4 text-sm font-semibold whitespace-nowrap">
                                                    {inv.amount != null ? (
                                                        <span className="inline-flex items-center text-green-500">
                                                            <IndianRupee className="w-3 h-3" />
                                                            {formatCurrency(inv.amount)}
                                                        </span>
                                                    ) : (
                                                        "—"
                                                    )}
                                                </td>
                                            </tr>
                                        ))
                                    )}
                                </tbody>
                            </table>
                        </div>
                        <TablePagination
                            page={page}
                            totalPages={totalPages}
                            totalElements={totalElements}
                            pageSize={pageSize}
                            onPageChange={setPage}
                        />
                    </GlassCard>

                    <div className="space-y-6">
                        <GlassCard className="p-6">
                            <ProductImage name={product.name} size="lg" />
                        </GlassCard>

                        <GlassCard className="p-6">
                            <h2 className="text-lg font-semibold text-foreground mb-4">Profile</h2>
                            <dl className="space-y-3">
                                {infoRows.map((row) => (
                                    <div key={row.label} className="flex items-center justify-between gap-4">
                                        <dt className="text-[10px] uppercase tracking-widest text-muted-foreground font-bold">
                                            {row.label}
                                        </dt>
                                        <dd className="text-sm font-semibold text-foreground text-right truncate">{row.value}</dd>
                                    </div>
                                ))}
                            </dl>
                        </GlassCard>

                        <GlassCard className="p-6">
                            <h2 className="text-lg font-semibold text-foreground mb-4">Current Stock</h2>
                            <div className="space-y-4">
                                <div className="flex items-center justify-between p-4 rounded-xl bg-white/5 border border-border/50">
                                    <div className="flex items-center gap-3">
                                        <div className="p-2 bg-cyan-500/10 rounded-lg">
                                            <Warehouse className="w-5 h-5 text-cyan-500" />
                                        </div>
                                        <div>
                                            <p className="text-[10px] uppercase tracking-widest text-muted-foreground font-bold">Godown</p>
                                            <p className="text-lg font-bold text-foreground">
                                                {godown ? fmtProductQty(godown.currentStock, product.unit) : "—"}
                                            </p>
                                        </div>
                                    </div>
                                    {lowGodown && (
                                        <span className="flex items-center gap-1 text-xs text-amber-500 font-semibold">
                                            <AlertTriangle className="w-4 h-4" /> Reorder
                                        </span>
                                    )}
                                </div>
                                <div className="flex items-center justify-between p-4 rounded-xl bg-white/5 border border-border/50">
                                    <div className="flex items-center gap-3">
                                        <div className="p-2 bg-purple-500/10 rounded-lg">
                                            <ShoppingBag className="w-5 h-5 text-purple-500" />
                                        </div>
                                        <div>
                                            <p className="text-[10px] uppercase tracking-widest text-muted-foreground font-bold">Cashier</p>
                                            <p className="text-lg font-bold text-foreground">
                                                {cashier
                                                    ? `${fmtProductQty(cashier.currentStock, product.unit)} / ${fmtProductQty(cashier.maxCapacity, product.unit)}`
                                                    : "—"}
                                            </p>
                                        </div>
                                    </div>
                                </div>
                                {!godown && !cashier && (
                                    <p className="text-xs text-muted-foreground">No stock records for this product yet.</p>
                                )}
                            </div>
                        </GlassCard>
                    </div>
                </div>
            </div>
        </div>
    );
}
