"use client";

import { useEffect, useState } from "react";
import { getPendingApprovalCount } from "@/lib/api/station";
import { useAuth } from "@/lib/auth/auth-context";

const POLL_INTERVAL_MS = 30_000;

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

        return () => {
            cancelled = true;
            window.clearInterval(id);
            document.removeEventListener("visibilitychange", onVisible);
        };
    }, [canView]);

    return count;
}
