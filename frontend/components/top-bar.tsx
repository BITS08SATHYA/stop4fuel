"use client";

import { Menu } from "lucide-react";
import { useSidebar } from "@/components/sidebar-context";

export function TopBar() {
    const { toggle } = useSidebar();
    return (
        <header className="sticky top-0 z-30 h-14 flex items-center gap-3 px-3 sm:px-4 border-b border-border bg-card/80 backdrop-blur supports-[backdrop-filter]:bg-card/60">
            <button
                onClick={toggle}
                aria-label="Toggle sidebar"
                className="p-2 rounded-md text-muted-foreground hover:bg-muted hover:text-foreground transition-colors"
            >
                <Menu className="w-5 h-5" />
            </button>
            <div className="flex items-center gap-2 font-bold text-lg">
                <img src="/logo-icon.svg" alt="StopForFuel" className="w-6 h-6" />
                <span className="text-foreground">Stop<span className="text-gradient">ForFuel</span></span>
            </div>
        </header>
    );
}
