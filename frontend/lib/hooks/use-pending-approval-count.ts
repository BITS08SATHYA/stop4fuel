"use client";

import { useEffect, useRef, useState } from "react";
import { getPendingApprovalCount } from "@/lib/api/station";
import { useAuth } from "@/lib/auth/auth-context";

const POLL_INTERVAL_MS = 30_000;

function fireApprovalDesktopNotification(delta: number) {
    if (typeof window === "undefined") return;
    if (!("Notification" in window)) return;
    if (Notification.permission !== "granted") return;
    try {
        const n = new Notification("New approval request", {
            body: `${delta} new approval${delta > 1 ? "s" : ""} pending review`,
            tag: "sff-approvals",
            icon: "/favicon.ico",
        });
        n.onclick = () => {
            window.focus();
            if (window.location.pathname !== "/approvals") {
                window.location.href = "/approvals";
            }
            n.close();
        };
    } catch {
        // Safari/older browsers may throw on constructor use — ignore.
    }
}

export function usePendingApprovalCount(): number {
    const { hasPermission } = useAuth();
    const canView = hasPermission("APPROVAL_REQUEST_VIEW");
    const canApprove = hasPermission("APPROVAL_REQUEST_APPROVE");
    const [count, setCount] = useState(0);
    const prevCountRef = useRef<number | null>(null);

    useEffect(() => {
        if (!canView) {
            setCount(0);
            prevCountRef.current = null;
            return;
        }

        let cancelled = false;

        const fetchOnce = async () => {
            try {
                const c = await getPendingApprovalCount();
                if (cancelled) return;
                const prev = prevCountRef.current;
                if (canApprove && prev !== null && c > prev) {
                    fireApprovalDesktopNotification(c - prev);
                }
                prevCountRef.current = c;
                setCount(c);
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
    }, [canView, canApprove]);

    return count;
}
