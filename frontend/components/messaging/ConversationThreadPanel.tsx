"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { ArrowLeft, Loader2, X } from "lucide-react";
import {
    listMessages,
    markConversationRead,
    sendMessage,
    type Message,
    type Conversation,
} from "@/lib/api/station/messages";
import { messageBus } from "@/lib/notifications/message-bus";
import { useAuth } from "@/lib/auth/auth-context";
import { InitialsAvatar } from "./InitialsAvatar";
import { MessageComposer } from "./MessageComposer";

interface ConversationThreadPanelProps {
    conversation: Conversation;
    onBack: () => void;
    onClose: () => void;
}

interface LocalMessage extends Message {
    _pending?: boolean;
    _failed?: boolean;
    _tempKey?: string;
}

const PAGE_SIZE = 50;

function sameDay(a: string, b: string): boolean {
    return new Date(a).toDateString() === new Date(b).toDateString();
}

function formatDayHeader(iso: string): string {
    const d = new Date(iso);
    const now = new Date();
    const sameDayNow = d.toDateString() === now.toDateString();
    if (sameDayNow) return "Today";
    const yesterday = new Date(now.getTime() - 24 * 60 * 60 * 1000);
    if (d.toDateString() === yesterday.toDateString()) return "Yesterday";
    return d.toLocaleDateString([], { weekday: "short", day: "numeric", month: "short" });
}

function formatTime(iso: string): string {
    return new Date(iso).toLocaleTimeString([], { hour: "numeric", minute: "2-digit" });
}

export function ConversationThreadPanel({ conversation, onBack, onClose }: ConversationThreadPanelProps) {
    const { user } = useAuth();
    const callerId = user?.id ?? null;

    const [messages, setMessages] = useState<LocalMessage[]>([]);
    const [loading, setLoading] = useState(true);
    const [loadingOlder, setLoadingOlder] = useState(false);
    const [hasMore, setHasMore] = useState(true);
    const scrollerRef = useRef<HTMLDivElement | null>(null);
    const atBottomRef = useRef(true);
    const [newMessagesBelow, setNewMessagesBelow] = useState(0);

    const scrollToBottom = useCallback(() => {
        const el = scrollerRef.current;
        if (el) el.scrollTop = el.scrollHeight;
        setNewMessagesBelow(0);
    }, []);

    // Initial page
    useEffect(() => {
        let cancelled = false;
        setLoading(true);
        setHasMore(true);
        listMessages(conversation.id, { size: PAGE_SIZE })
            .then(ms => {
                if (cancelled) return;
                setMessages(ms);
                setHasMore(ms.length >= PAGE_SIZE);
                // Scroll to bottom after paint.
                requestAnimationFrame(() => scrollToBottom());
                // Mark read, and drain the bus's optimistic bump.
                markConversationRead(conversation.id).catch(() => {});
                messageBus.clearUnreadFor(conversation.id);
            })
            .catch(() => {})
            .finally(() => { if (!cancelled) setLoading(false); });
        return () => { cancelled = true; };
    }, [conversation.id, scrollToBottom]);

    // Subscribe to new messages via bus; fetch on arrival to get full fidelity (ids, etc.).
    useEffect(() => {
        const unsub = messageBus.onArrive((evt) => {
            if (evt.type !== "MESSAGE_CREATED") return;
            if (evt.conversationId !== conversation.id) return;
            // Refetch tail — cheaper than reconciling event payload shapes.
            listMessages(conversation.id, { size: PAGE_SIZE })
                .then(ms => {
                    setMessages(prev => {
                        // Preserve pending/failed optimistic bubbles.
                        const pending = prev.filter(m => m._pending || m._failed);
                        const existingIds = new Set(ms.map(m => m.id));
                        const keptPending = pending.filter(p => !existingIds.has(p.id));
                        return [...ms, ...keptPending];
                    });
                    if (atBottomRef.current) {
                        requestAnimationFrame(scrollToBottom);
                    } else {
                        setNewMessagesBelow(n => n + 1);
                    }
                    markConversationRead(conversation.id).catch(() => {});
                    messageBus.clearUnreadFor(conversation.id);
                })
                .catch(() => {});
        });
        return () => unsub();
    }, [conversation.id, scrollToBottom]);

    // Track whether user is scrolled to the bottom so auto-scroll feels right.
    useEffect(() => {
        const el = scrollerRef.current;
        if (!el) return;
        const onScroll = () => {
            const gap = el.scrollHeight - el.scrollTop - el.clientHeight;
            atBottomRef.current = gap < 80;
            if (atBottomRef.current) setNewMessagesBelow(0);
        };
        el.addEventListener("scroll", onScroll);
        return () => el.removeEventListener("scroll", onScroll);
    }, []);

    // Keyset pagination when scrolled to top.
    const loadOlder = useCallback(async () => {
        if (loadingOlder || !hasMore) return;
        const oldest = messages.find(m => !m._pending && !m._failed);
        if (!oldest) return;
        setLoadingOlder(true);
        const el = scrollerRef.current;
        const prevScrollHeight = el?.scrollHeight ?? 0;
        try {
            const older = await listMessages(conversation.id, { before: oldest.id, size: PAGE_SIZE });
            if (older.length === 0) {
                setHasMore(false);
            } else {
                setMessages(prev => [...older, ...prev]);
                // Preserve scroll position so the viewport doesn't jump.
                requestAnimationFrame(() => {
                    if (!el) return;
                    const newScrollHeight = el.scrollHeight;
                    el.scrollTop = newScrollHeight - prevScrollHeight;
                });
                if (older.length < PAGE_SIZE) setHasMore(false);
            }
        } catch {
            // ignore
        } finally {
            setLoadingOlder(false);
        }
    }, [conversation.id, hasMore, loadingOlder, messages]);

    useEffect(() => {
        const el = scrollerRef.current;
        if (!el) return;
        const onScroll = () => {
            if (el.scrollTop < 60) loadOlder();
        };
        el.addEventListener("scroll", onScroll);
        return () => el.removeEventListener("scroll", onScroll);
    }, [loadOlder]);

    const handleSend = async (text: string) => {
        if (callerId == null) return;
        const tempKey = `tmp-${Date.now()}-${Math.random().toString(36).slice(2)}`;
        const optimistic: LocalMessage = {
            id: -Date.now(),
            conversationId: conversation.id,
            senderUserId: callerId,
            senderName: user?.name ?? "You",
            text,
            createdAt: new Date().toISOString(),
            _pending: true,
            _tempKey: tempKey,
        };
        setMessages(prev => [...prev, optimistic]);
        requestAnimationFrame(scrollToBottom);
        try {
            const saved = await sendMessage(conversation.id, text);
            setMessages(prev => prev.map(m => m._tempKey === tempKey ? { ...saved } : m));
        } catch {
            setMessages(prev => prev.map(m => m._tempKey === tempKey ? { ...m, _pending: false, _failed: true } : m));
        }
    };

    const other = conversation.otherParticipant;
    const otherName = other?.name ?? "Unknown";
    const otherSubtitle = [other?.designation, other?.role].filter(Boolean).join(" · ");

    return (
        <div className="flex flex-col h-full min-h-0">
            {/* Header */}
            <div className="flex items-center gap-2 border-b border-border px-3 py-2">
                <button
                    onClick={onBack}
                    className="p-1 rounded hover:bg-muted/40 text-muted-foreground"
                    aria-label="Back"
                >
                    <ArrowLeft className="w-4 h-4" />
                </button>
                <InitialsAvatar name={otherName} size="sm" />
                <div className="flex-1 min-w-0">
                    <p className="text-sm font-semibold text-foreground truncate">{otherName}</p>
                    {otherSubtitle && <p className="text-[10px] text-muted-foreground truncate">{otherSubtitle}</p>}
                </div>
                <button
                    onClick={onClose}
                    className="p-1 rounded hover:bg-muted/40 text-muted-foreground"
                    aria-label="Close"
                >
                    <X className="w-4 h-4" />
                </button>
            </div>

            {/* Messages */}
            <div ref={scrollerRef} className="flex-1 overflow-y-auto px-3 py-2 space-y-1 relative">
                {loadingOlder && (
                    <div className="flex items-center justify-center py-2 text-xs text-muted-foreground">
                        <Loader2 className="w-3 h-3 animate-spin mr-1" /> Loading older…
                    </div>
                )}
                {!hasMore && messages.length >= PAGE_SIZE && (
                    <div className="text-center text-[10px] text-muted-foreground py-2">
                        Start of conversation
                    </div>
                )}
                {loading ? (
                    <div className="flex items-center justify-center h-32 text-muted-foreground text-sm">
                        <Loader2 className="w-4 h-4 animate-spin mr-2" /> Loading…
                    </div>
                ) : messages.length === 0 ? (
                    <div className="flex items-center justify-center h-32 text-muted-foreground text-xs">
                        Say hi to {otherName}.
                    </div>
                ) : (
                    messages.map((m, idx) => {
                        const prev = messages[idx - 1];
                        const showDay = !prev || !sameDay(prev.createdAt, m.createdAt);
                        const mine = m.senderUserId === callerId;
                        return (
                            <div key={m._tempKey ?? m.id}>
                                {showDay && (
                                    <div className="text-center text-[10px] text-muted-foreground my-2">
                                        {formatDayHeader(m.createdAt)}
                                    </div>
                                )}
                                <div className={`flex ${mine ? "justify-end" : "justify-start"}`}>
                                    <div
                                        className={`max-w-[75%] rounded-2xl px-3 py-1.5 text-sm whitespace-pre-wrap break-words ${
                                            mine
                                                ? "bg-primary text-primary-foreground rounded-br-sm"
                                                : "bg-muted/60 text-foreground rounded-bl-sm"
                                        } ${m._pending ? "opacity-70" : ""} ${m._failed ? "ring-1 ring-destructive/50" : ""}`}
                                    >
                                        {m.text}
                                        <div className={`text-[9px] mt-0.5 ${mine ? "text-primary-foreground/70" : "text-muted-foreground"}`}>
                                            {m._pending ? "Sending…" : m._failed ? "Failed to send" : formatTime(m.createdAt)}
                                        </div>
                                    </div>
                                </div>
                            </div>
                        );
                    })
                )}
            </div>

            {newMessagesBelow > 0 && (
                <button
                    onClick={scrollToBottom}
                    className="absolute bottom-20 right-4 bg-primary text-primary-foreground text-[10px] font-semibold px-3 py-1 rounded-full shadow-lg"
                >
                    {newMessagesBelow} new ↓
                </button>
            )}

            <MessageComposer onSend={handleSend} />
        </div>
    );
}
