import { cn } from "@/lib/utils";

interface BadgeProps {
    children: React.ReactNode;
    variant?: "default" | "success" | "warning" | "danger" | "outline";
    className?: string;
}

export function Badge({ children, variant = "default", className }: BadgeProps) {
    const variants = {
        default: "bg-primary/20 text-primary border-primary/20",
        success: "bg-emerald-500/20 text-emerald-400 border-emerald-500/20",
        warning: "bg-amber-500/20 text-amber-400 border-amber-500/20",
        danger: "bg-rose-500/20 text-rose-400 border-rose-500/20",
        outline: "bg-transparent border-white/20 text-muted-foreground",
    };

    return (
        <span className={cn(
            "px-2.5 py-0.5 rounded-full text-xs font-medium border backdrop-blur-sm",
            variants[variant],
            className
        )}>
            {children}
        </span>
    );
}
