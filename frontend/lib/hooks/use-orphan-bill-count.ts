"use client";

import { useEffect, useState } from "react";
import { getOrphanBillCount } from "@/lib/api/station";
import { useAuth } from "@/lib/auth/auth-context";

const POLL_INTERVAL_MS = 5 * 60_000;

/**
 * Sidebar badge count for the Orphan Bills page. Less time-sensitive than the approval queue,
 * so we poll every 5 minutes (vs 1 min) and re-fetch on tab visibility. Gated on
 * PAYMENT_UPDATE — same gate the page itself uses.
 */
export function useOrphanBillCount(): number {
    const { hasPermission } = useAuth();
    const canView = hasPermission("PAYMENT_UPDATE");
    const [count, setCount] = useState(0);

    useEffect(() => {
        if (!canView) { setCount(0); return; }
        let cancelled = false;

        const fetchOnce = async () => {
            try {
                const r = await getOrphanBillCount(false);
                if (!cancelled) setCount(r.count);
            } catch {
                // transient errors don't break the UI
            }
        };

        fetchOnce();
        const id = window.setInterval(() => {
            if (document.visibilityState === "visible") fetchOnce();
        }, POLL_INTERVAL_MS);
        const onVisible = () => { if (document.visibilityState === "visible") fetchOnce(); };
        document.addEventListener("visibilitychange", onVisible);

        return () => {
            cancelled = true;
            window.clearInterval(id);
            document.removeEventListener("visibilitychange", onVisible);
        };
    }, [canView]);

    return count;
}
