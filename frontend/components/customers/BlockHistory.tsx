"use client";

import { useEffect, useState } from "react";
import { ShieldAlert, ShieldCheck, Clock, Bot, User } from "lucide-react";
import { getBlockHistory, type BlockEvent } from "@/lib/api/station/customers";

interface BlockHistoryProps {
    customerId: number;
    className?: string;
}

export function BlockHistory({ customerId, className }: BlockHistoryProps) {
    const [events, setEvents] = useState<BlockEvent[]>([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        setLoading(true);
        getBlockHistory(customerId)
            .then(setEvents)
            .catch(() => setEvents([]))
            .finally(() => setLoading(false));
    }, [customerId]);

    if (loading) {
        return (
            <div className={`flex items-center gap-2 text-xs text-muted-foreground ${className}`}>
                <div className="animate-spin rounded-full h-3 w-3 border-b border-primary" />
                Loading history...
            </div>
        );
    }

    if (events.length === 0) {
        return (
            <div className={`text-xs text-muted-foreground ${className}`}>
                No block/unblock history.
            </div>
        );
    }

    return (
        <div className={`space-y-2 ${className}`}>
            <h4 className="text-xs font-medium text-muted-foreground uppercase tracking-wider">Block History</h4>
            <div className="space-y-1.5 max-h-60 overflow-y-auto">
                {events.map((event) => (
                    <div
                        key={event.id}
                        className="flex items-start gap-2 p-2 rounded-lg bg-muted/30 border border-border/50 text-xs"
                    >
                        <div className="mt-0.5">
                            {event.eventType === "BLOCKED" ? (
                                <ShieldAlert className="w-3.5 h-3.5 text-rose-400" />
                            ) : (
                                <ShieldCheck className="w-3.5 h-3.5 text-emerald-400" />
                            )}
                        </div>
                        <div className="flex-1 min-w-0">
                            <div className="flex items-center gap-1.5">
                                <span className={event.eventType === "BLOCKED" ? "text-rose-400 font-medium" : "text-emerald-400 font-medium"}>
                                    {event.eventType}
                                </span>
                                <span className="text-muted-foreground">via</span>
                                <span className="inline-flex items-center gap-0.5">
                                    {event.triggerType === "MANUAL" ? (
                                        <User className="w-2.5 h-2.5" />
                                    ) : (
                                        <Bot className="w-2.5 h-2.5" />
                                    )}
                                    {event.triggerType === "AUTO_SCHEDULED" ? "Scheduled scan"
                                        : event.triggerType === "AUTO_INVOICE" ? "Invoice check"
                                        : event.performedByName}
                                </span>
                            </div>
                            {event.reason && (
                                <div className="text-muted-foreground mt-0.5 truncate">{event.reason}</div>
                            )}
                            {event.notes && (
                                <div className="text-muted-foreground mt-0.5 italic truncate">Note: {event.notes}</div>
                            )}
                            <div className="flex items-center gap-1 text-muted-foreground/70 mt-0.5">
                                <Clock className="w-2.5 h-2.5" />
                                {new Date(event.createdAt).toLocaleString("en-IN", {
                                    day: "2-digit", month: "short", year: "numeric",
                                    hour: "2-digit", minute: "2-digit"
                                })}
                            </div>
                        </div>
                    </div>
                ))}
            </div>
        </div>
    );
}
