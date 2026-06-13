"use client";

import { Clock } from "lucide-react";
import { useAuth } from "@/lib/auth/auth-context";
import { useIdleLogout } from "@/lib/hooks/use-idle-logout";

/**
 * Mounts the inactivity timer for authenticated pages and renders a short countdown
 * warning before auto sign-out. Renders nothing until the warning fires.
 */
export function IdleLogout() {
    const { showWarning, secondsLeft, stayActive } = useIdleLogout();
    const { logout } = useAuth();

    if (!showWarning) return null;

    return (
        <div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/60 backdrop-blur-sm">
            <div
                role="alertdialog"
                aria-modal="true"
                aria-label="Inactivity sign-out warning"
                className="w-full max-w-sm mx-4 bg-card border border-border rounded-2xl shadow-2xl p-6 text-center"
            >
                <div className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-primary/10">
                    <Clock className="h-6 w-6 text-primary" />
                </div>
                <h3 className="text-lg font-semibold text-foreground">Still there?</h3>
                <p className="mt-2 text-sm text-muted-foreground">
                    You&apos;ll be signed out in{" "}
                    <span className="font-semibold text-foreground tabular-nums">{secondsLeft}s</span>{" "}
                    due to inactivity.
                </p>
                <div className="mt-6 flex flex-col gap-2">
                    <button
                        onClick={stayActive}
                        className="w-full py-2.5 px-4 bg-primary text-primary-foreground rounded-xl font-semibold hover:opacity-90 transition-opacity"
                    >
                        Stay signed in
                    </button>
                    <button
                        onClick={() => logout()}
                        className="w-full py-2 px-4 text-sm text-muted-foreground hover:text-foreground transition-colors"
                    >
                        Sign out now
                    </button>
                </div>
            </div>
        </div>
    );
}
