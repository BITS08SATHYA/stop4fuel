"use client";

import { useEffect, useState } from "react";
import { getUnreadCount } from "@/lib/api/station/messages";
import { useAuth } from "@/lib/auth/auth-context";
import { messageBus } from "@/lib/notifications/message-bus";

// Backstop poll; the SSE stream drives freshness between ticks.
const POLL_INTERVAL_MS = 60_000;

/**
 * Total unread messages for the authenticated user, across every conversation.
 * Combines a 60-second visibility-aware poll with immediate refetches on every
 * message-bus arrival so the dock badge stays accurate without hammering the API.
 */
export function useMessagingUnreadCount(): number {
    const { isAuthenticated } = useAuth();
    const [count, setCount] = useState(0);

    useEffect(() => {
        if (!isAuthenticated) {
            setCount(0);
            return;
        }

        let cancelled = false;

        const fetchOnce = async () => {
            try {
                const c = await getUnreadCount();
                if (!cancelled) setCount(c);
            } catch {
                // ignore transient errors
            }
        };

        fetchOnce();
        const id = window.setInterval(() => {
            if (document.visibilityState === "visible") fetchOnce();
        }, POLL_INTERVAL_MS);

        const onVisible = () => {
            if (document.visibilityState === "visible") fetchOnce();
        };
        document.addEventListener("visibilitychange", onVisible);

        const unsubBus = messageBus.onArrive(() => {
            if (!cancelled) fetchOnce();
        });

        return () => {
            cancelled = true;
            window.clearInterval(id);
            document.removeEventListener("visibilitychange", onVisible);
            unsubBus();
        };
    }, [isAuthenticated]);

    return count;
}
