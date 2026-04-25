"use client";

import { useEffect, useState } from "react";
import { ChevronDown, MessageCircle } from "lucide-react";
import { useAuth } from "@/lib/auth/auth-context";
import { useMessagingUnreadCount } from "@/lib/hooks/use-messaging-unread-count";
import { listConversations, type Conversation } from "@/lib/api/station/messages";
import { ConversationListPanel } from "./ConversationListPanel";
import { ConversationThreadPanel } from "./ConversationThreadPanel";
import { NewConversationModal } from "./NewConversationModal";

type ViewState = "collapsed" | "list" | "thread";

const STORAGE_KEY = "sff.messaging.dock";

export function MessengerDock() {
    const { isAuthenticated, user } = useAuth();
    const unread = useMessagingUnreadCount();

    const [view, setView] = useState<ViewState>("collapsed");
    const [activeConversation, setActiveConversation] = useState<Conversation | null>(null);
    const [showNewModal, setShowNewModal] = useState(false);

    // Restore collapsed/list state across route changes within a tab.
    useEffect(() => {
        if (typeof window === "undefined") return;
        const persisted = sessionStorage.getItem(STORAGE_KEY);
        if (persisted === "list") setView("list");
    }, []);

    useEffect(() => {
        if (typeof window === "undefined") return;
        if (view === "list" || view === "collapsed") {
            sessionStorage.setItem(STORAGE_KEY, view);
        }
    }, [view]);

    if (!isAuthenticated || !user?.id) return null;

    const openConversationById = async (id: number) => {
        // Hydrate the conversation from the list endpoint so we have participant info.
        try {
            const all = await listConversations();
            const found = all.find(c => c.id === id);
            if (found) {
                setActiveConversation(found);
                setView("thread");
            }
        } catch {
            // leave user on the list view on failure
        }
    };

    if (view === "collapsed") {
        return (
            <button
                onClick={() => setView("list")}
                className="fixed bottom-4 right-4 z-[60] flex items-center gap-2 px-4 py-2.5 rounded-full bg-primary text-primary-foreground shadow-xl hover:brightness-110 transition-all"
                aria-label="Open messenger"
            >
                <MessageCircle className="w-5 h-5" />
                <span className="text-sm font-semibold">Messages</span>
                {unread > 0 && (
                    <span className="min-w-[20px] h-5 px-1.5 rounded-full bg-destructive text-destructive-foreground text-[10px] font-bold flex items-center justify-center">
                        {unread > 99 ? "99+" : unread}
                    </span>
                )}
            </button>
        );
    }

    return (
        <>
            <div className="fixed bottom-4 right-4 z-[60] w-[360px] max-w-[calc(100vw-2rem)] h-[520px] max-h-[calc(100vh-2rem)] glass-card rounded-2xl shadow-2xl border border-border overflow-hidden flex flex-col">
                {view === "list" && (
                    <>
                        <div className="flex items-center justify-between px-3 py-2 border-b border-border">
                            <div className="flex items-center gap-2">
                                <MessageCircle className="w-4 h-4 text-primary" />
                                <span className="text-sm font-semibold text-foreground">Messages</span>
                                {unread > 0 && (
                                    <span className="min-w-[18px] h-[18px] px-1.5 rounded-full bg-primary text-primary-foreground text-[10px] font-bold flex items-center justify-center">
                                        {unread > 99 ? "99+" : unread}
                                    </span>
                                )}
                            </div>
                            <button
                                onClick={() => setView("collapsed")}
                                className="text-muted-foreground hover:text-foreground p-1 rounded"
                                aria-label="Minimize"
                            >
                                <ChevronDown className="w-4 h-4" />
                            </button>
                        </div>
                        <div className="flex-1 min-h-0">
                            <ConversationListPanel
                                onOpenConversation={openConversationById}
                                onStartNew={() => setShowNewModal(true)}
                            />
                        </div>
                    </>
                )}

                {view === "thread" && activeConversation && (
                    <ConversationThreadPanel
                        conversation={activeConversation}
                        onBack={() => { setView("list"); setActiveConversation(null); }}
                        onClose={() => { setView("collapsed"); setActiveConversation(null); }}
                    />
                )}
            </div>

            {showNewModal && (
                <NewConversationModal
                    onClose={() => setShowNewModal(false)}
                    onOpened={(conv) => {
                        setShowNewModal(false);
                        setActiveConversation(conv);
                        setView("thread");
                    }}
                />
            )}
        </>
    );
}
