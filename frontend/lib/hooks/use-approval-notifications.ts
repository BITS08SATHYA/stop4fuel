"use client";

import { useEffect } from "react";
import { useAuth } from "@/lib/auth/auth-context";

export function useApprovalNotificationPermission(): void {
    const { hasPermission } = useAuth();
    const canApprove = hasPermission("APPROVAL_REQUEST_APPROVE");

    useEffect(() => {
        if (!canApprove) return;
        if (typeof window === "undefined") return;
        if (!("Notification" in window)) return;
        if (Notification.permission !== "default") return;
        Notification.requestPermission().catch(() => {
            // User dismissed or browser blocked — nothing to do.
        });
    }, [canApprove]);
}
