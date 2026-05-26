"use client";

export type StockEventType = "STOCK_SHIFT_CLOSE_SUMMARY";

export interface StockTankRow {
    tankName: string;
    productName: string;
    currentLiters: number;
    soldLiters: number;
    pricePerLiter: number;
    lowStock: boolean;
}

export interface StockProductRow {
    productName: string;
    unit: string;
    currentUnits: number;
    soldUnits: number;
    priceEach: number;
    lowStock: boolean;
}

export interface StockEvent {
    type: StockEventType;
    shiftId: number;
    scid?: number;
    companyName?: string;
    closedAt?: string;
    tanks: StockTankRow[];
    products: StockProductRow[];
    lowStockCount: number;
    receivedAt: number;
    localId: string;
}

type Listener = () => void;

const HISTORY_LIMIT = 20;

class StockBus {
    private history: StockEvent[] = [];
    private unread = new Set<string>();
    private listeners = new Set<Listener>();
    private arriveListeners = new Set<(e: StockEvent) => void>();

    emit(raw: Omit<StockEvent, "receivedAt" | "localId">) {
        const event: StockEvent = {
            ...raw,
            receivedAt: Date.now(),
            localId:
                typeof crypto !== "undefined" && crypto.randomUUID
                    ? crypto.randomUUID()
                    : `${Date.now()}-${Math.random().toString(36).slice(2)}`,
        };
        this.history.unshift(event);
        if (this.history.length > HISTORY_LIMIT) this.history.pop();
        this.unread.add(event.localId);
        this.arriveListeners.forEach(l => l(event));
        this.listeners.forEach(l => l());
    }

    getHistory(): StockEvent[] {
        return this.history;
    }

    getUnreadCount(): number {
        return this.unread.size;
    }

    markAllRead() {
        if (this.unread.size === 0) return;
        this.unread.clear();
        this.listeners.forEach(l => l());
    }

    subscribe(listener: Listener): () => void {
        this.listeners.add(listener);
        return () => this.listeners.delete(listener);
    }

    onArrive(listener: (event: StockEvent) => void): () => void {
        this.arriveListeners.add(listener);
        return () => this.arriveListeners.delete(listener);
    }
}

export const stockBus = new StockBus();
