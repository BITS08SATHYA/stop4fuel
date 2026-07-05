"use client";

import { Package } from "lucide-react";
import { cn } from "@/lib/utils";

interface ProductImageProps {
    name: string;
    /** Presigned/static image URL — not available yet, placeholder renders until product photos land. */
    src?: string;
    size?: "card" | "lg";
    className?: string;
}

export function ProductImage({ name, src, size = "card", className }: ProductImageProps) {
    if (src) {
        return (
            <img
                src={src}
                alt={name}
                className={cn(
                    "w-full object-cover rounded-xl",
                    size === "lg" ? "h-56" : "h-32",
                    className,
                )}
            />
        );
    }

    return (
        <div
            className={cn(
                "w-full rounded-xl bg-gradient-to-br from-primary/15 via-primary/5 to-transparent",
                "border border-border/50 flex flex-col items-center justify-center gap-2",
                size === "lg" ? "h-56" : "h-32",
                className,
            )}
        >
            <Package className={cn("text-primary/60", size === "lg" ? "w-14 h-14" : "w-9 h-9")} />
            {size === "lg" && (
                <span className="text-[10px] uppercase tracking-widest text-muted-foreground">
                    Photo coming soon
                </span>
            )}
        </div>
    );
}
