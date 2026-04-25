"use client";

import { useEffect, useMemo, useState } from "react";
import { Loader2, Search, X } from "lucide-react";
import { listContacts, startDirectConversation, type Contact, type Conversation } from "@/lib/api/station/messages";
import { InitialsAvatar } from "./InitialsAvatar";

interface NewConversationModalProps {
    onClose: () => void;
    onOpened: (conv: Conversation) => void;
}

export function NewConversationModal({ onClose, onOpened }: NewConversationModalProps) {
    const [contacts, setContacts] = useState<Contact[]>([]);
    const [loading, setLoading] = useState(true);
    const [query, setQuery] = useState("");
    const [error, setError] = useState<string | null>(null);
    const [starting, setStarting] = useState<number | null>(null);

    useEffect(() => {
        let cancelled = false;
        listContacts()
            .then(data => { if (!cancelled) setContacts(data); })
            .catch(() => { if (!cancelled) setError("Could not load contacts"); })
            .finally(() => { if (!cancelled) setLoading(false); });
        return () => { cancelled = true; };
    }, []);

    const filtered = useMemo(() => {
        const q = query.trim().toLowerCase();
        if (!q) return contacts;
        return contacts.filter(c =>
            c.name.toLowerCase().includes(q)
            || (c.designation ?? "").toLowerCase().includes(q)
            || (c.role ?? "").toLowerCase().includes(q)
        );
    }, [contacts, query]);

    const pick = async (c: Contact) => {
        setStarting(c.userId);
        try {
            const conv = await startDirectConversation(c.userId);
            onOpened(conv);
        } catch (e) {
            setError(e instanceof Error ? e.message : "Failed to start conversation");
        } finally {
            setStarting(null);
        }
    };

    return (
        <div className="fixed inset-0 z-[70] flex items-center justify-center bg-black/60 backdrop-blur-sm" onClick={onClose}>
            <div
                className="relative w-full max-w-sm bg-card border border-border rounded-2xl shadow-2xl overflow-hidden"
                onClick={(e) => e.stopPropagation()}
            >
                <div className="flex items-center justify-between px-4 py-3 border-b border-border bg-muted/30">
                    <h3 className="text-sm font-semibold text-foreground">New message</h3>
                    <button onClick={onClose} className="text-muted-foreground hover:text-foreground" aria-label="Close">
                        <X className="w-4 h-4" />
                    </button>
                </div>

                <div className="p-3 border-b border-border">
                    <div className="relative">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                        <input
                            autoFocus
                            value={query}
                            onChange={(e) => setQuery(e.target.value)}
                            placeholder="Search people…"
                            className="w-full bg-background border border-border rounded-lg pl-9 pr-3 py-2 text-sm text-foreground focus:outline-none focus:ring-1 focus:ring-primary/40"
                        />
                    </div>
                </div>

                <div className="max-h-80 overflow-y-auto">
                    {loading ? (
                        <div className="flex items-center justify-center h-24 text-muted-foreground text-sm">
                            <Loader2 className="w-4 h-4 animate-spin mr-2" /> Loading…
                        </div>
                    ) : error ? (
                        <div className="px-4 py-6 text-center text-xs text-destructive">{error}</div>
                    ) : filtered.length === 0 ? (
                        <div className="px-4 py-6 text-center text-xs text-muted-foreground">
                            {contacts.length === 0 ? "No contacts available." : "No matches."}
                        </div>
                    ) : (
                        <ul className="divide-y divide-border">
                            {filtered.map(c => (
                                <li key={c.userId}>
                                    <button
                                        onClick={() => pick(c)}
                                        disabled={starting != null}
                                        className="w-full flex items-center gap-3 px-3 py-2.5 hover:bg-muted/30 transition-colors text-left disabled:opacity-50"
                                    >
                                        <InitialsAvatar name={c.name} size="sm" />
                                        <div className="flex-1 min-w-0">
                                            <p className="text-sm text-foreground truncate">{c.name}</p>
                                            <p className="text-[10px] text-muted-foreground truncate">
                                                {[c.designation, c.role].filter(Boolean).join(" · ")}
                                            </p>
                                        </div>
                                        {starting === c.userId && <Loader2 className="w-4 h-4 animate-spin text-muted-foreground" />}
                                    </button>
                                </li>
                            ))}
                        </ul>
                    )}
                </div>
            </div>
        </div>
    );
}
