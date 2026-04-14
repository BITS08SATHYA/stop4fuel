"use client";

import { createContext, useContext, useEffect, useState } from "react";

type SidebarCtx = {
    open: boolean;
    toggle: () => void;
    close: () => void;
};

const SidebarContext = createContext<SidebarCtx | null>(null);

export function SidebarProvider({ children }: { children: React.ReactNode }) {
    const [open, setOpen] = useState(true);

    useEffect(() => {
        const stored = typeof window !== "undefined" ? localStorage.getItem("sidebar:open") : null;
        if (stored !== null) {
            setOpen(stored === "1");
        } else if (typeof window !== "undefined") {
            setOpen(window.innerWidth >= 1024);
        }
    }, []);

    useEffect(() => {
        if (typeof window !== "undefined") localStorage.setItem("sidebar:open", open ? "1" : "0");
    }, [open]);

    return (
        <SidebarContext.Provider
            value={{ open, toggle: () => setOpen((v) => !v), close: () => setOpen(false) }}
        >
            {children}
        </SidebarContext.Provider>
    );
}

export function useSidebar() {
    const ctx = useContext(SidebarContext);
    if (!ctx) throw new Error("useSidebar must be used within SidebarProvider");
    return ctx;
}
