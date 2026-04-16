"use client";

import { useEffect, useState } from "react";
import { getPendingApprovalCount } from "@/lib/api/station";
import { useAuth } from "@/lib/auth/auth-context";
import { approvalBus } from "@/lib/notifications/approval-bus";

// Poll is now a backstop for when the SSE stream is down or the user just
// opened the app. The SSE stream itself is the primary path for freshness,
// and the approval bus nudges a re-fetch on every real-time event.
const POLL_INTERVAL_MS = 60_000;

export function usePendingApprovalCount(): number {
    const { hasPermission } = useAuth();
    const canView = hasPermission("APPROVAL_REQUEST_VIEW");
    const [count, setCount] = useState(0);

    useEffect(() => {
        if (!canView) {
            setCount(0);
            return;
        }

        let cancelled = false;

        const fetchOnce = async () => {
            try {
                const c = await getPendingApprovalCount();
                if (!cancelled) setCount(c);
            } catch {
                // ignore transient errors so UI never breaks
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

        // Re-fetch immediately whenever the SSE stream delivers an approval event
        const unsubBus = approvalBus.onArrive(() => {
            if (!cancelled) fetchOnce();
        });

        return () => {
            cancelled = true;
            window.clearInterval(id);
            document.removeEventListener("visibilitychange", onVisible);
            unsubBus();
        };
    }, [canView]);

    return count;
}
