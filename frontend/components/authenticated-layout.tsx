"use client";

import { usePathname, useRouter } from "next/navigation";
import { useEffect } from "react";
import { configureAmplify } from "@/lib/auth/amplify-config";
import { AuthProvider, useAuth } from "@/lib/auth/auth-context";
import { AppSidebar } from "@/components/app-sidebar";

// Initialize Amplify on client side
if (typeof window !== "undefined") {
    configureAmplify();
}

const PUBLIC_PATHS = ["/login", "/auth/callback"];

function LayoutContent({ children }: { children: React.ReactNode }) {
    const pathname = usePathname();
    const router = useRouter();
    const { isLoading, isAuthenticated } = useAuth();
    const isPublicPath = PUBLIC_PATHS.some(p => pathname.startsWith(p));

    useEffect(() => {
        if (!isLoading && !isAuthenticated && !isPublicPath) {
            // In dev mode (no Cognito configured), don't redirect
            if (!process.env.NEXT_PUBLIC_COGNITO_USER_POOL_ID) return;
            router.push("/login");
        }
    }, [isLoading, isAuthenticated, isPublicPath, router]);

    if (isPublicPath) {
        return <main className="flex-1 overflow-y-auto bg-background">{children}</main>;
    }

    if (isLoading) {
        return (
            <div className="flex items-center justify-center w-full h-full">
                <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary" />
            </div>
        );
    }

    return (
        <>
            <AppSidebar />
            <main className="flex-1 overflow-y-auto bg-background">{children}</main>
        </>
    );
}

export function AuthenticatedLayout({ children }: { children: React.ReactNode }) {
    return (
        <AuthProvider>
            <LayoutContent>{children}</LayoutContent>
        </AuthProvider>
    );
}
