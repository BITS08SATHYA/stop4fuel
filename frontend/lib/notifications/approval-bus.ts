"use client";

export type ApprovalEventType =
    | "APPROVAL_REQUEST_CREATED"
    | "APPROVAL_REQUEST_APPROVED"
    | "APPROVAL_REQUEST_REJECTED";

export interface ApprovalEvent {
    type: ApprovalEventType;
    requestId: number;
    requestType: string;
    customerId?: number | string;
    requestedBy?: number | string;
    createdAt?: string;
    reviewNote?: string;
    reviewedAt?: string;
    receivedAt: number;
    localId: string;
}

type Listener = () => void;

const HISTORY_LIMIT = 50;

class ApprovalBus {
    private history: ApprovalEvent[] = [];
    private unread = new Set<string>();
    private listeners = new Set<Listener>();
    private arriveListeners = new Set<(e: ApprovalEvent) => void>();

    emit(raw: Omit<ApprovalEvent, "receivedAt" | "localId">) {
        const event: ApprovalEvent = {
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

    getHistory(): ApprovalEvent[] {
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

    /** Subscribe to state changes (history/unread). */
    subscribe(listener: Listener): () => void {
        this.listeners.add(listener);
        return () => this.listeners.delete(listener);
    }

    /** Subscribe to individual arrival events — useful for one-shot UI like a toast. */
    onArrive(listener: (event: ApprovalEvent) => void): () => void {
        this.arriveListeners.add(listener);
        return () => this.arriveListeners.delete(listener);
    }
}

export const approvalBus = new ApprovalBus();
