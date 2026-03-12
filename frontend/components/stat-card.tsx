import { LucideIcon } from "lucide-react";
import { cn } from "@/lib/utils";

interface StatCardProps {
    title: string;
    value: string;
    trend?: string;
    trendUp?: boolean;
    icon: LucideIcon;
    className?: string;
}

export function StatCard({ title, value, trend, trendUp, icon: Icon, className }: StatCardProps) {
    return (
        <div className={cn("rounded-xl border bg-card p-6 text-card-foreground shadow-sm", className)}>
            <div className="flex flex-row items-center justify-between space-y-0 pb-2">
                <h3 className="tracking-tight text-sm font-medium text-muted-foreground">{title}</h3>
                <Icon className="h-4 w-4 text-muted-foreground" />
            </div>
            <div className="content-center">
                <div className="text-2xl font-bold">{value}</div>
                {trend && (
                    <p className={cn("text-xs", trendUp ? "text-green-500" : "text-red-500")}>
                        {trend}
                    </p>
                )}
            </div>
        </div>
    );
}
