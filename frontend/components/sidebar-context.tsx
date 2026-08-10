"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";

type SidebarCtx = {
    open: boolean;
    toggle: () => void;
    close: () => void;
};

const SidebarContext = createContext<SidebarCtx | null>(null);

/** Matches the `lg:` breakpoint the sidebar uses to switch between an overlay
 *  drawer and a docked column. */
const DOCKED_MIN_WIDTH = 1024;
const STORAGE_KEY = "sidebar:open";

const isDocked = () => typeof window !== "undefined" && window.innerWidth >= DOCKED_MIN_WIDTH;

export function SidebarProvider({ children }: { children: React.ReactNode }) {
    const [open, setOpen] = useState(true);
    // The persisted value is a *docked-width* preference only. Below `lg` the
    // sidebar is an overlay drawer, so it must never restore itself open —
    // otherwise rotating a tablet to portrait buries the page under it.
    const dockedPref = useRef(true);

    useEffect(() => {
        const stored = localStorage.getItem(STORAGE_KEY);
        if (stored !== null) dockedPref.current = stored === "1";

        const sync = () => setOpen(isDocked() ? dockedPref.current : false);
        sync();

        window.addEventListener("resize", sync);
        window.addEventListener("orientationchange", sync);
        return () => {
            window.removeEventListener("resize", sync);
            window.removeEventListener("orientationchange", sync);
        };
    }, []);

    const apply = useCallback((next: boolean) => {
        setOpen(next);
        // Only a deliberate toggle at docked width is worth remembering; opening
        // the drawer on a narrow screen is transient by nature.
        if (isDocked()) {
            dockedPref.current = next;
            localStorage.setItem(STORAGE_KEY, next ? "1" : "0");
        }
    }, []);

    const value = useMemo<SidebarCtx>(
        () => ({ open, toggle: () => apply(!open), close: () => apply(false) }),
        [open, apply]
    );

    return <SidebarContext.Provider value={value}>{children}</SidebarContext.Provider>;
}

export function useSidebar() {
    const ctx = useContext(SidebarContext);
    if (!ctx) throw new Error("useSidebar must be used within SidebarProvider");
    return ctx;
}
