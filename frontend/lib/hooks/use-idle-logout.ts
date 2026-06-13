"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useAuth } from "@/lib/auth/auth-context";

// Sign the user out after this much inactivity. Overridable via env for tuning/testing.
const IDLE_MS = (Number(process.env.NEXT_PUBLIC_IDLE_TIMEOUT_MINUTES) || 10) * 60_000;
// How long before logout to show the "still there?" warning.
const WARN_MS = 60_000;
// Activity that resets the timer.
const ACTIVITY_EVENTS = ["mousemove", "mousedown", "keydown", "scroll", "touchstart", "click"] as const;
// Shared across tabs so activity in one tab keeps the others alive.
const STORAGE_KEY = "sff-last-activity";
// Throttle localStorage writes so high-frequency events (mousemove/scroll) don't thrash it.
const WRITE_THROTTLE_MS = 2_000;

interface IdleLogoutState {
    showWarning: boolean;
    secondsLeft: number;
    /** Reset the timer and dismiss the warning. */
    stayActive: () => void;
}

export function useIdleLogout(): IdleLogoutState {
    const { isAuthenticated, logout } = useAuth();
    const [showWarning, setShowWarning] = useState(false);
    const [secondsLeft, setSecondsLeft] = useState(Math.ceil(WARN_MS / 1000));

    const lastActivityRef = useRef<number>(Date.now());
    const lastWriteRef = useRef<number>(0);
    const loggedOutRef = useRef<boolean>(false);

    const readLastActivity = useCallback((): number => {
        let stored = 0;
        try {
            stored = Number(localStorage.getItem(STORAGE_KEY)) || 0;
        } catch {
            // localStorage unavailable — fall back to the in-memory value
        }
        return Math.max(lastActivityRef.current, stored);
    }, []);

    const markActivity = useCallback(() => {
        const now = Date.now();
        lastActivityRef.current = now;
        if (now - lastWriteRef.current > WRITE_THROTTLE_MS) {
            lastWriteRef.current = now;
            try {
                localStorage.setItem(STORAGE_KEY, String(now));
            } catch {
                // ignore
            }
        }
        setShowWarning((prev) => (prev ? false : prev));
    }, []);

    const stayActive = useCallback(() => {
        const now = Date.now();
        lastActivityRef.current = now;
        lastWriteRef.current = now;
        try {
            localStorage.setItem(STORAGE_KEY, String(now));
        } catch {
            // ignore
        }
        setShowWarning(false);
    }, []);

    useEffect(() => {
        if (!isAuthenticated) return;

        // Start the clock fresh on mount.
        markActivity();
        loggedOutRef.current = false;

        ACTIVITY_EVENTS.forEach((evt) =>
            window.addEventListener(evt, markActivity, { passive: true }),
        );

        const tick = () => {
            if (loggedOutRef.current) return;
            const elapsed = Date.now() - readLastActivity();

            if (elapsed >= IDLE_MS) {
                loggedOutRef.current = true;
                setShowWarning(false);
                logout();
                return;
            }

            if (elapsed >= IDLE_MS - WARN_MS) {
                setShowWarning(true);
                setSecondsLeft(Math.max(0, Math.ceil((IDLE_MS - elapsed) / 1000)));
            } else {
                setShowWarning((prev) => (prev ? false : prev));
            }
        };

        const id = window.setInterval(tick, 1_000);

        const onVisible = () => {
            if (document.visibilityState === "visible") tick();
        };
        document.addEventListener("visibilitychange", onVisible);

        return () => {
            ACTIVITY_EVENTS.forEach((evt) => window.removeEventListener(evt, markActivity));
            document.removeEventListener("visibilitychange", onVisible);
            window.clearInterval(id);
        };
    }, [isAuthenticated, markActivity, readLastActivity, logout]);

    return { showWarning, secondsLeft, stayActive };
}
