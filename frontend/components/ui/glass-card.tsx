import { cn } from "@/lib/utils";

interface GlassCardProps extends React.HTMLAttributes<HTMLDivElement> {
    children: React.ReactNode;
    className?: string;
}

export function GlassCard({ children, className, ...props }: GlassCardProps) {
    return (
        <div
            className={cn(
                "glass-card rounded-2xl p-6 transition-all duration-300 hover:bg-white/10",
                className
            )}
            {...props}
        >
            {children}
        </div>
    );
}
