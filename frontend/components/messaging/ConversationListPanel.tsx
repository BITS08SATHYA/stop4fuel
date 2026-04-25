"use client";

import { useCallback, useEffect, useState } from "react";
import { Loader2, PenSquare } from "lucide-react";
import { listConversations, type Conversation } from "@/lib/api/station/messages";
import { messageBus } from "@/lib/notifications/message-bus";
import { InitialsAvatar } from "./InitialsAvatar";

interface ConversationListPanelProps {
    onOpenConversation: (id: number) => void;
    onStartNew: () => void;
}

function formatTime(iso?: string | null): string {
    if (!iso) return "";
    const d = new Date(iso);
    const now = new Date();
    const sameDay = d.toDateString() === now.toDateString();
    if (sameDay) {
        return d.toLocaleTimeString([], { hour: "numeric", minute: "2-digit" });
    }
    const diffDays = Math.floor((now.getTime() - d.getTime()) / (1000 * 60 * 60 * 24));
    if (diffDays < 7) return d.toLocaleDateString([], { weekday: "short" });
    return d.toLocaleDateString([], { day: "numeric", month: "short" });
}

export function ConversationListPanel({ onOpenConversation, onStartNew }: ConversationListPanelProps) {
    const [conversations, setConversations] = useState<Conversation[]>([]);
    const [loading, setLoading] = useState(true);

    const refresh = useCallback(async () => {
        try {
            const data = await listConversations();
            setConversations(data);
        } catch {
            // transient — leave the previous list on screen
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        refresh();
        const unsub = messageBus.onArrive(() => {
            // Any bus event (new message, new conversation, read) can reshape the list.
            refresh();
        });
        const onVisible = () => {
            if (document.visibilityState === "visible") refresh();
        };
        document.addEventListener("visibilitychange", onVisible);
        return () => {
            unsub();
            document.removeEventListener("visibilitychange", onVisible);
        };
    }, [refresh]);

    return (
        <div className="flex flex-col h-full min-h-0">
            <div className="flex-1 overflow-y-auto">
                {loading && conversations.length === 0 ? (
                    <div className="flex items-center justify-center h-32 text-muted-foreground">
                        <Loader2 className="w-4 h-4 animate-spin mr-2" />
                        Loading…
                    </div>
                ) : conversations.length === 0 ? (
                    <div className="flex flex-col items-center justify-center h-full gap-3 px-6 text-center">
                        <p className="text-sm text-muted-foreground">No conversations yet.</p>
                        <button
                            onClick={onStartNew}
                            className="text-sm text-primary hover:underline"
                        >
                            Start a new message
                        </button>
                    </div>
                ) : (
                    <ul className="divide-y divide-border">
                        {conversations.map((c) => {
                            const other = c.otherParticipant;
                            const name = other?.name ?? "Unknown";
                            const subtitle = [other?.designation, other?.role]
                                .filter(Boolean)
                                .join(" · ");
                            const unread = c.unreadCount > 0;
                            return (
                                <li key={c.id}>
                                    <button
                                        onClick={() => onOpenConversation(c.id)}
                                        className="w-full flex items-start gap-3 px-3 py-3 hover:bg-muted/30 transition-colors text-left"
                                    >
                                        <InitialsAvatar name={name} />
                                        <div className="flex-1 min-w-0">
                                            <div className="flex items-center justify-between gap-2">
                                                <span className={`truncate text-sm ${unread ? "font-semibold text-foreground" : "text-foreground/90"}`}>
                                                    {name}
                                                </span>
                                                <span className="text-[10px] text-muted-foreground shrink-0">
                                                    {formatTime(c.lastMessageAt ?? c.updatedAt)}
                                                </span>
                                            </div>
                                            {subtitle && (
                                                <p className="text-[10px] text-muted-foreground/80 truncate">{subtitle}</p>
                                            )}
                                            <p className={`text-xs truncate ${unread ? "text-foreground" : "text-muted-foreground"}`}>
                                                {c.lastMessagePreview ?? "Say hi."}
                                            </p>
                                        </div>
                                        {unread && (
                                            <span className="min-w-[20px] h-5 px-1.5 rounded-full bg-primary text-primary-foreground text-[10px] font-bold flex items-center justify-center">
                                                {c.unreadCount > 99 ? "99+" : c.unreadCount}
                                            </span>
                                        )}
                                    </button>
                                </li>
                            );
                        })}
                    </ul>
                )}
            </div>

            <div className="border-t border-border p-2">
                <button
                    onClick={onStartNew}
                    className="w-full flex items-center justify-center gap-2 px-3 py-2 rounded-lg bg-primary/10 text-primary hover:bg-primary/20 transition-colors text-sm font-medium"
                >
                    <PenSquare className="w-4 h-4" />
                    New message
                </button>
            </div>
        </div>
    );
}
