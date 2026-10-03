"use client";

import { useState } from "react";
import { PackagePlus } from "lucide-react";
import { useAuth } from "@/lib/auth/auth-context";
import { useToast } from "@/components/ui/toast";
import { addManualStock } from "@/lib/api/station";

// Matches InvoiceBillService's non-fuel stock refusals (not the tank ones):
//   "Insufficient stock for product 'X'. Available: 3.00, Required: 5."
//   "Product 'X' is out of stock (0 available)."
const SHORTFALL = /Insufficient stock for product '(.+?)'\. Available: ([\d.]+), Required: ([\d.]+)/;
const OUT_OF_STOCK = /Product '(.+?)' is out of stock/;

function parseShortfall(error: string): { name: string; shortfall?: number } | null {
    const s = SHORTFALL.exec(error);
    if (s) return { name: s[1], shortfall: Math.max(0, parseFloat(s[3]) - parseFloat(s[2])) };
    const o = OUT_OF_STOCK.exec(error);
    if (o) return { name: o[1] };
    return null;
}

interface AddStockPromptProps {
    error: string;
    lines: { product?: { id: number; name: string; category?: string } }[];
    onAdded: () => void;
}

/** Offers to add counter stock by hand when an invoice was refused for insufficient stock. */
export function AddStockPrompt({ error, lines, onAdded }: AddStockPromptProps) {
    const { hasPermission } = useAuth();
    const toast = useToast();
    const parsed = parseShortfall(error);
    const product = parsed ? lines.find(l => l.product?.name === parsed.name)?.product : undefined;

    const [open, setOpen] = useState(false);
    const [quantity, setQuantity] = useState("");
    const [reason, setReason] = useState("");
    const [saving, setSaving] = useState(false);

    if (!parsed || !product) return null;
    if (!hasPermission("INVENTORY_RECEIVE") && !hasPermission("INVENTORY_CREATE")) return null;

    const start = () => {
        setQuantity(parsed.shortfall ? String(Math.ceil(parsed.shortfall)) : "");
        setReason("");
        setOpen(true);
    };

    const submit = async () => {
        const qty = parseFloat(quantity);
        if (!(qty > 0) || !reason.trim()) return;
        setSaving(true);
        try {
            await addManualStock({ productId: product.id, quantity: qty, reason: reason.trim() });
            toast.success(`Added ${qty} to ${product.name}. Create the invoice again.`);
            setOpen(false);
            onAdded();
        } catch (e: unknown) {
            toast.error(e instanceof Error ? e.message : "Failed to add stock");
        } finally {
            setSaving(false);
        }
    };

    if (!open) {
        return (
            <button
                type="button"
                onClick={start}
                className="mt-2 inline-flex items-center gap-1.5 rounded-lg border border-emerald-500/40 bg-emerald-500/10 px-3 py-1.5 text-xs font-semibold text-emerald-600 dark:text-emerald-400 hover:bg-emerald-500/20 transition-colors tap-target"
            >
                <PackagePlus className="w-3.5 h-3.5" /> Add stock for {product.name}
            </button>
        );
    }

    return (
        <div className="mt-3 rounded-lg border border-border bg-card/60 p-3 space-y-2">
            <p className="text-xs text-muted-foreground">
                Adding stock for <span className="font-semibold text-foreground">{product.name}</span>.
                This is recorded under your name with the reason and shows in the shift&apos;s stock record.
            </p>
            <div className="flex flex-wrap gap-2">
                <input
                    type="number"
                    min="0"
                    step="any"
                    inputMode="decimal"
                    value={quantity}
                    onChange={e => setQuantity(e.target.value)}
                    placeholder="Quantity"
                    className="w-28 rounded-lg border border-border bg-background px-3 py-2 text-sm"
                />
                <input
                    type="text"
                    value={reason}
                    onChange={e => setReason(e.target.value)}
                    placeholder="Reason (e.g. new carton received from godown)"
                    maxLength={500}
                    className="flex-1 min-w-[12rem] rounded-lg border border-border bg-background px-3 py-2 text-sm"
                />
            </div>
            <div className="flex gap-2">
                <button
                    type="button"
                    onClick={submit}
                    disabled={saving || !(parseFloat(quantity) > 0) || !reason.trim()}
                    className="rounded-lg bg-emerald-600 px-3 py-1.5 text-xs font-semibold text-white disabled:opacity-50 tap-target"
                >
                    {saving ? "Adding…" : "Add stock"}
                </button>
                <button
                    type="button"
                    onClick={() => setOpen(false)}
                    className="rounded-lg border border-border px-3 py-1.5 text-xs font-medium tap-target"
                >
                    Cancel
                </button>
            </div>
        </div>
    );
}
