"use client";

import { ShieldAlert, AlertTriangle, ShieldCheck } from "lucide-react";
import { cn } from "@/lib/utils";

interface CreditHealthBadgeProps {
    riskLevel: "HIGH" | "MEDIUM" | "LOW" | null | undefined;
    utilizationPercent?: number | null;
    oldestUnpaidDays?: number;
    size?: "sm" | "md";
    className?: string;
}

const riskConfig = {
    HIGH: {
        label: "High Risk",
        icon: ShieldAlert,
        classes: "bg-rose-500/15 text-rose-500 border-rose-500/30",
    },
    MEDIUM: {
        label: "Medium",
        icon: AlertTriangle,
        classes: "bg-amber-500/15 text-amber-500 border-amber-500/30",
    },
    LOW: {
        label: "Low Risk",
        icon: ShieldCheck,
        classes: "bg-emerald-500/15 text-emerald-500 border-emerald-500/30",
    },
};

export function CreditHealthBadge({
    riskLevel,
    utilizationPercent,
    oldestUnpaidDays,
    size = "sm",
    className,
}: CreditHealthBadgeProps) {
    if (!riskLevel) return null;

    const config = riskConfig[riskLevel];
    if (!config) return null;

    const Icon = config.icon;
    const isSmall = size === "sm";

    return (
        <div className="group relative inline-flex">
            <span
                className={cn(
                    "inline-flex items-center gap-1 rounded-full border font-medium",
                    isSmall ? "px-2 py-0.5 text-[10px]" : "px-2.5 py-1 text-xs",
                    config.classes,
                    className
                )}
            >
                <Icon className={isSmall ? "w-3 h-3" : "w-3.5 h-3.5"} />
                {config.label}
            </span>

            {/* Tooltip on hover */}
            {(utilizationPercent != null || oldestUnpaidDays != null) && (
                <div className="absolute bottom-full left-1/2 -translate-x-1/2 mb-1.5 px-2.5 py-1.5 rounded-lg bg-popover border border-border shadow-lg text-[10px] text-popover-foreground whitespace-nowrap opacity-0 group-hover:opacity-100 transition-opacity pointer-events-none z-50">
                    {utilizationPercent != null && (
                        <div>Utilization: {utilizationPercent.toFixed(1)}%</div>
                    )}
                    {oldestUnpaidDays != null && oldestUnpaidDays > 0 && (
                        <div>Oldest unpaid: {oldestUnpaidDays} days</div>
                    )}
                </div>
            )}
        </div>
    );
}
