"use client";

import { useEffect, useMemo, useState } from "react";
import { GlassCard } from "@/components/ui/glass-card";
import { Modal } from "@/components/ui/modal";
import { StyledSelect } from "@/components/ui/styled-select";
import { PermissionGate } from "@/components/permission-gate";
import { showToast } from "@/components/ui/toast";
import {
    getProducts,
    getProductPriceHistory,
    createProductPriceHistory,
    deleteProductPriceHistory,
    Product,
    ProductPriceHistoryEntry,
} from "@/lib/api/station";
import { History, Plus, Trash2, TrendingUp, TrendingDown, Minus } from "lucide-react";

function fmtPrice(n: number): string {
    return `₹${n.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 4 })}`;
}

function defaultFromDate(): string {
    const d = new Date();
    d.setDate(d.getDate() - 90);
    return d.toISOString().slice(0, 10);
}

function todayISO(): string {
    return new Date().toISOString().slice(0, 10);
}

export default function PriceHistoryPage() {
    const [products, setProducts] = useState<Product[]>([]);
    const [entries, setEntries] = useState<ProductPriceHistoryEntry[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [productFilter, setProductFilter] = useState<string>("ALL");
    const [from, setFrom] = useState<string>(defaultFromDate());
    const [to, setTo] = useState<string>(todayISO());

    const [isAddOpen, setIsAddOpen] = useState(false);
    const [addProductId, setAddProductId] = useState<string>("");
    const [addDate, setAddDate] = useState<string>(todayISO());
    const [addPrice, setAddPrice] = useState<string>("");
    const [addSubmitting, setAddSubmitting] = useState(false);

    const loadEntries = async () => {
        setIsLoading(true);
        try {
            const params: { productId?: number; from?: string; to?: string } = { from, to };
            if (productFilter !== "ALL") params.productId = Number(productFilter);
            const list = await getProductPriceHistory(params);
            setEntries(list);
        } catch (e) {
            showToast.error(e instanceof Error ? e.message : "Failed to load price history");
        } finally {
            setIsLoading(false);
        }
    };

    useEffect(() => {
        getProducts().then(setProducts).catch(() => setProducts([]));
    }, []);

    useEffect(() => {
        loadEntries();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [productFilter, from, to]);

    // Compute delta vs previous (older) snapshot per product.
    const rows = useMemo(() => {
        const byProduct = new Map<number, ProductPriceHistoryEntry[]>();
        for (const e of entries) {
            const arr = byProduct.get(e.productId) || [];
            arr.push(e);
            byProduct.set(e.productId, arr);
        }
        const enriched: Array<ProductPriceHistoryEntry & { delta: number | null }> = [];
        for (const list of byProduct.values()) {
            const sorted = [...list].sort((a, b) => a.effectiveDate.localeCompare(b.effectiveDate));
            sorted.forEach((e, i) => {
                const prev = i > 0 ? sorted[i - 1].price : null;
                const delta = prev != null ? e.price - prev : null;
                enriched.push({ ...e, delta });
            });
        }
        // Display newest first
        return enriched.sort((a, b) => {
            const d = b.effectiveDate.localeCompare(a.effectiveDate);
            if (d !== 0) return d;
            return a.productName.localeCompare(b.productName);
        });
    }, [entries]);

    const productOptions = useMemo(() => {
        const opts = [{ value: "ALL", label: "All Products" }];
        for (const p of products) {
            opts.push({ value: String(p.id), label: p.name });
        }
        return opts;
    }, [products]);

    const handleAdd = async () => {
        if (!addProductId || !addDate || !addPrice) {
            showToast.error("Product, date and price are all required");
            return;
        }
        const priceNum = Number(addPrice);
        if (Number.isNaN(priceNum) || priceNum < 0) {
            showToast.error("Price must be a non-negative number");
            return;
        }
        setAddSubmitting(true);
        try {
            await createProductPriceHistory({
                product: { id: Number(addProductId) },
                effectiveDate: addDate,
                price: priceNum,
            });
            showToast.success("Price entry added");
            setIsAddOpen(false);
            setAddProductId("");
            setAddPrice("");
            setAddDate(todayISO());
            loadEntries();
        } catch (e) {
            showToast.error(e instanceof Error ? e.message : "Failed to add price entry");
        } finally {
            setAddSubmitting(false);
        }
    };

    const handleDelete = async (id: number) => {
        if (!confirm("Delete this price entry? This affects how past shifts resolve their rates.")) return;
        try {
            await deleteProductPriceHistory(id);
            showToast.success("Entry deleted");
            loadEntries();
        } catch (e) {
            showToast.error(e instanceof Error ? e.message : "Failed to delete");
        }
    };

    return (
        <div className="space-y-6">
            <div className="flex items-center justify-between flex-wrap gap-3">
                <div>
                    <h1 className="text-3xl font-bold flex items-center gap-2">
                        <History className="w-7 h-7 text-primary" />
                        Price <span className="text-primary">History</span>
                    </h1>
                    <p className="text-muted-foreground text-sm mt-1">
                        Daily snapshots of each product&apos;s catalog price. Used to resolve historical
                        shift revenue without drift when prices change later.
                    </p>
                </div>
                <PermissionGate permission="PRODUCT_CREATE">
                    <button
                        onClick={() => setIsAddOpen(true)}
                        className="flex items-center gap-2 px-4 py-2 rounded-lg bg-primary text-primary-foreground hover:bg-primary/90"
                    >
                        <Plus className="w-4 h-4" />
                        Add Price Entry
                    </button>
                </PermissionGate>
            </div>

            <GlassCard className="p-4">
                <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
                    <div>
                        <label className="block text-xs text-muted-foreground mb-1">Product</label>
                        <StyledSelect
                            value={productFilter}
                            onChange={setProductFilter}
                            options={productOptions}
                            placeholder="All Products"
                        />
                    </div>
                    <div>
                        <label className="block text-xs text-muted-foreground mb-1">From</label>
                        <input
                            type="date"
                            value={from}
                            onChange={(e) => setFrom(e.target.value)}
                            className="w-full px-3 py-2 border rounded-lg bg-background"
                        />
                    </div>
                    <div>
                        <label className="block text-xs text-muted-foreground mb-1">To</label>
                        <input
                            type="date"
                            value={to}
                            onChange={(e) => setTo(e.target.value)}
                            className="w-full px-3 py-2 border rounded-lg bg-background"
                        />
                    </div>
                </div>
            </GlassCard>

            <GlassCard className="p-0 overflow-hidden">
                <div className="overflow-x-auto">
                    <table className="w-full text-sm">
                        <thead className="bg-muted/40">
                            <tr>
                                <th className="text-left px-4 py-3 font-semibold">Effective Date</th>
                                <th className="text-left px-4 py-3 font-semibold">Product</th>
                                <th className="text-right px-4 py-3 font-semibold">Price</th>
                                <th className="text-right px-4 py-3 font-semibold">Δ vs Previous</th>
                                <PermissionGate permission="PRODUCT_CREATE">
                                    <th className="text-right px-4 py-3 font-semibold w-16">Actions</th>
                                </PermissionGate>
                            </tr>
                        </thead>
                        <tbody>
                            {isLoading ? (
                                <tr>
                                    <td colSpan={5} className="text-center py-8 text-muted-foreground">
                                        Loading…
                                    </td>
                                </tr>
                            ) : rows.length === 0 ? (
                                <tr>
                                    <td colSpan={5} className="text-center py-8 text-muted-foreground">
                                        No price history entries for the selected filters.
                                    </td>
                                </tr>
                            ) : (
                                rows.map((r) => (
                                    <tr key={r.id} className="border-t hover:bg-muted/20">
                                        <td className="px-4 py-2.5">{r.effectiveDate}</td>
                                        <td className="px-4 py-2.5">{r.productName}</td>
                                        <td className="px-4 py-2.5 text-right font-medium">{fmtPrice(r.price)}</td>
                                        <td className="px-4 py-2.5 text-right">
                                            {r.delta == null ? (
                                                <span className="text-muted-foreground">—</span>
                                            ) : r.delta > 0 ? (
                                                <span className="text-red-500 inline-flex items-center gap-1 justify-end">
                                                    <TrendingUp className="w-3.5 h-3.5" />+{r.delta.toFixed(2)}
                                                </span>
                                            ) : r.delta < 0 ? (
                                                <span className="text-emerald-500 inline-flex items-center gap-1 justify-end">
                                                    <TrendingDown className="w-3.5 h-3.5" />{r.delta.toFixed(2)}
                                                </span>
                                            ) : (
                                                <span className="text-muted-foreground inline-flex items-center gap-1 justify-end">
                                                    <Minus className="w-3.5 h-3.5" />0.00
                                                </span>
                                            )}
                                        </td>
                                        <PermissionGate permission="PRODUCT_CREATE">
                                            <td className="px-4 py-2.5 text-right">
                                                <button
                                                    onClick={() => handleDelete(r.id)}
                                                    className="p-1.5 text-red-500 hover:bg-red-500/10 rounded"
                                                    title="Delete entry"
                                                >
                                                    <Trash2 className="w-4 h-4" />
                                                </button>
                                            </td>
                                        </PermissionGate>
                                    </tr>
                                ))
                            )}
                        </tbody>
                    </table>
                </div>
                {rows.length > 0 && (
                    <div className="px-4 py-2 text-xs text-muted-foreground border-t bg-muted/20">
                        {rows.length} entr{rows.length === 1 ? "y" : "ies"} · newest first · Δ compares each row to
                        the preceding snapshot for the same product
                    </div>
                )}
            </GlassCard>

            <Modal isOpen={isAddOpen} onClose={() => setIsAddOpen(false)} title="Add Price Entry">
                <div className="space-y-4">
                    <p className="text-xs text-muted-foreground">
                        Inserts a snapshot row directly. Use this to back-fill missing history for shifts
                        that need to resolve to a specific rate on a given date.
                    </p>
                    <div>
                        <label className="block text-sm font-medium mb-1">Product</label>
                        <StyledSelect
                            value={addProductId}
                            onChange={setAddProductId}
                            options={products.map((p) => ({ value: String(p.id), label: p.name }))}
                            placeholder="Select product"
                        />
                    </div>
                    <div>
                        <label className="block text-sm font-medium mb-1">Effective Date</label>
                        <input
                            type="date"
                            value={addDate}
                            onChange={(e) => setAddDate(e.target.value)}
                            className="w-full px-3 py-2 border rounded-lg bg-background"
                        />
                    </div>
                    <div>
                        <label className="block text-sm font-medium mb-1">Price (₹)</label>
                        <input
                            type="number"
                            step="0.01"
                            min="0"
                            value={addPrice}
                            onChange={(e) => setAddPrice(e.target.value)}
                            placeholder="e.g. 108.42"
                            className="w-full px-3 py-2 border rounded-lg bg-background"
                        />
                    </div>
                    <div className="flex justify-end gap-2 pt-2">
                        <button
                            onClick={() => setIsAddOpen(false)}
                            className="px-4 py-2 rounded-lg border hover:bg-muted/40"
                        >
                            Cancel
                        </button>
                        <button
                            onClick={handleAdd}
                            disabled={addSubmitting}
                            className="px-4 py-2 rounded-lg bg-primary text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
                        >
                            {addSubmitting ? "Adding…" : "Add"}
                        </button>
                    </div>
                </div>
            </Modal>
        </div>
    );
}
