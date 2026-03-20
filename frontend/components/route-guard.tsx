"use client";

import { useAuth } from "@/lib/auth/auth-context";
import { ShieldX } from "lucide-react";

interface RouteGuardProps {
    permission: string;
    children: React.ReactNode;
}

export function RouteGuard({ permission, children }: RouteGuardProps) {
    const { isLoading, isAuthenticated, hasPermission } = useAuth();

    if (isLoading) {
        return (
            <div className="flex items-center justify-center min-h-[50vh]">
                <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary" />
            </div>
        );
    }

    if (!isAuthenticated) {
        return null;
    }

    if (!hasPermission(permission)) {
        return (
            <div className="flex flex-col items-center justify-center min-h-[50vh] gap-4">
                <ShieldX className="w-16 h-16 text-destructive/50" />
                <h2 className="text-xl font-semibold">Access Denied</h2>
                <p className="text-muted-foreground text-center max-w-md">
                    You don&apos;t have permission to access this page.
                    Contact your station admin if you need access.
                </p>
            </div>
        );
    }

    return <>{children}</>;
}
