"use client";

/**
 * In-memory pub/sub for SSE-delivered messaging events. Mirrors the shape of
 * approval-bus but keyed per conversation so the dock can update rows
 * independently and compute an optimistic unread bump between server
 * refetches.
 */

export type MessageEventType = "MESSAGE_CREATED" | "CONVERSATION_CREATED" | "MESSAGE_READ";

export interface OtherParticipant {
    userId: number;
    name: string;
    role: string | null;
    designation: string | null;
}

export interface MessageBusEvent {
    type: MessageEventType;
    conversationId: number;
    // MESSAGE_CREATED
    messageId?: number;
    senderId?: number;
    senderName?: string;
    text?: string;
    preview?: string;
    createdAt?: string;
    // CONVERSATION_CREATED
    otherParticipant?: OtherParticipant;
    // MESSAGE_READ
    userId?: number;
    lastReadMessageId?: number;
    // Local
    receivedAt: number;
}

type Listener = () => void;
type ArriveListener = (event: MessageBusEvent) => void;

interface ConversationState {
    lastPreview?: string;
    lastEventAt?: number;
    unreadBump: number; // incremented locally; dock reconciles with server counts
}

class MessageBus {
    private state = new Map<number, ConversationState>();
    private totalUnreadBump = 0;
    private listeners = new Set<Listener>();
    private arriveListeners = new Set<ArriveListener>();

    emit(raw: Omit<MessageBusEvent, "receivedAt">): void {
        const event: MessageBusEvent = { ...raw, receivedAt: Date.now() };
        const id = event.conversationId;
        const prev = this.state.get(id) ?? { unreadBump: 0 };

        switch (event.type) {
            case "MESSAGE_CREATED": {
                prev.lastPreview = event.preview ?? event.text ?? prev.lastPreview;
                prev.lastEventAt = event.receivedAt;
                prev.unreadBump += 1;
                this.totalUnreadBump += 1;
                break;
            }
            case "CONVERSATION_CREATED": {
                prev.lastEventAt = event.receivedAt;
                break;
            }
            case "MESSAGE_READ": {
                // No state mutation here — the dock re-fetches the authoritative
                // count. We still notify listeners so UI can react.
                break;
            }
        }

        this.state.set(id, prev);
        this.arriveListeners.forEach(l => l(event));
        this.listeners.forEach(l => l());
    }

    /** Called by the thread view when the user opens a conversation so the optimistic bump drains. */
    clearUnreadFor(conversationId: number): void {
        const prev = this.state.get(conversationId);
        if (!prev) return;
        this.totalUnreadBump = Math.max(0, this.totalUnreadBump - prev.unreadBump);
        prev.unreadBump = 0;
        this.state.set(conversationId, prev);
        this.listeners.forEach(l => l());
    }

    getState(conversationId: number): ConversationState | undefined {
        return this.state.get(conversationId);
    }

    getTotalUnreadBump(): number {
        return this.totalUnreadBump;
    }

    resetBumps(): void {
        this.totalUnreadBump = 0;
        this.state.forEach(s => { s.unreadBump = 0; });
        this.listeners.forEach(l => l());
    }

    subscribe(listener: Listener): () => void {
        this.listeners.add(listener);
        return () => this.listeners.delete(listener);
    }

    onArrive(listener: ArriveListener): () => void {
        this.arriveListeners.add(listener);
        return () => this.arriveListeners.delete(listener);
    }
}

export const messageBus = new MessageBus();
