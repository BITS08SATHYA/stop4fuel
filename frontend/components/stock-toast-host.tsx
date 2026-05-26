"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Boxes, AlertTriangle, X } from "lucide-react";
import { stockBus, type StockEvent } from "@/lib/notifications/stock-bus";

const DISMISS_MS = 12_000;
const NUM = new Intl.NumberFormat(undefined, { maximumFractionDigits: 2, minimumFractionDigits: 2 });

interface ToastItem extends StockEvent {
    dismissAt: number;
}

export function StockToastHost() {
    const [items, setItems] = useState<ToastItem[]>([]);
    const router = useRouter();

    useEffect(() => {
        return stockBus.onArrive(event => {
            setItems(prev => [{ ...event, dismissAt: Date.now() + DISMISS_MS }, ...prev].slice(0, 3));
        });
    }, []);

    useEffect(() => {
        if (items.length === 0) return;
        const tick = window.setInterval(() => {
            const now = Date.now();
            setItems(prev => prev.filter(i => i.dismissAt > now));
        }, 500);
        return () => window.clearInterval(tick);
    }, [items.length]);

    const dismiss = (localId: string) => setItems(prev => prev.filter(i => i.localId !== localId));

    if (items.length === 0) return null;

    return (
        <div className="fixed bottom-4 right-4 z-[60] flex flex-col gap-2 w-[min(94vw,380px)]">
            {items.map(item => (
                <StockToastCard
                    key={item.localId}
                    item={item}
                    onDismiss={() => dismiss(item.localId)}
                    onView={() => {
                        router.push(`/operations/shifts/${item.shiftId}`);
                        dismiss(item.localId);
                    }}
                />
            ))}
        </div>
    );
}

interface CardProps {
    item: ToastItem;
    onDismiss: () => void;
    onView: () => void;
}

function StockToastCard({ item, onDismiss, onView }: CardProps) {
    const top = (item.tanks ?? []).slice(0, 3);
    const lowProducts = (item.products ?? []).filter(p => p.lowStock).slice(0, 3);

    return (
        <div
            className="bg-background/95 backdrop-blur border border-border rounded-xl shadow-lg p-3 pr-2 flex gap-3 animate-in slide-in-from-right-4 fade-in duration-300"
            role="status"
        >
            <div className="bg-emerald-500/15 text-emerald-500 rounded-full h-9 w-9 flex items-center justify-center shrink-0">
                <Boxes className="h-5 w-5" />
            </div>
            <div className="flex-1 min-w-0">
                <div className="text-sm font-semibold text-foreground">Shift #{item.shiftId} closed</div>
                <div className="text-xs text-muted-foreground mt-0.5">Today's stock summary</div>
                {top.length > 0 && (
                    <div className="mt-2 space-y-0.5">
                        {top.map(t => (
                            <div
                                key={t.tankName}
                                className="text-xs text-foreground flex items-center justify-between gap-2"
                            >
                                <span className="truncate">
                                    {t.tankName} <span className="text-muted-foreground">· {t.productName}</span>
                                </span>
                                <span className={`tabular-nums ${t.lowStock ? "text-red-500 font-medium" : ""}`}>
                                    {NUM.format(t.currentLiters)} L
                                </span>
                            </div>
                        ))}
                    </div>
                )}
                {lowProducts.length > 0 && (
                    <div className="mt-2 text-xs text-red-500 flex items-center gap-1.5">
                        <AlertTriangle className="h-3.5 w-3.5" />
                        {lowProducts.length} product(s) low
                    </div>
                )}
                <div className="flex gap-2 mt-2">
                    <button
                        onClick={onView}
                        className="text-xs font-medium px-2.5 py-1 rounded-md bg-primary text-primary-foreground hover:opacity-90 transition"
                    >
                        View shift
                    </button>
                    <button
                        onClick={onDismiss}
                        className="text-xs font-medium px-2.5 py-1 rounded-md hover:bg-muted transition text-muted-foreground"
                    >
                        Dismiss
                    </button>
                </div>
            </div>
            <button
                onClick={onDismiss}
                className="self-start text-muted-foreground hover:text-foreground transition"
                aria-label="Dismiss"
            >
                <X className="h-4 w-4" />
            </button>
        </div>
    );
}
