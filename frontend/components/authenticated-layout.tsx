"use client";

import { usePathname, useRouter } from "next/navigation";
import { useEffect } from "react";
import { configureAmplify } from "@/lib/auth/amplify-config";
import { AuthProvider, useAuth } from "@/lib/auth/auth-context";
import { AppSidebar } from "@/components/app-sidebar";
import { TopBar } from "@/components/top-bar";
import { SidebarProvider, useSidebar } from "@/components/sidebar-context";
import { ToastProvider } from "@/components/ui/toast";

// Initialize Amplify on client side
if (typeof window !== "undefined") {
    configureAmplify();
}

const PUBLIC_PATHS = ["/login", "/auth/callback"];
const ROOT_PATH = "/";

function LayoutContent({ children }: { children: React.ReactNode }) {
    const pathname = usePathname();
    const router = useRouter();
    const { isLoading, isAuthenticated } = useAuth();
    const isPublicPath = PUBLIC_PATHS.some(p => pathname.startsWith(p)) || pathname === ROOT_PATH;

    useEffect(() => {
        if (!isLoading && !isAuthenticated && !isPublicPath) {
            // In dev mode (no Cognito configured), don't redirect
            if (!process.env.NEXT_PUBLIC_COGNITO_USER_POOL_ID) return;
            router.push("/login");
        }
    }, [isLoading, isAuthenticated, isPublicPath, router]);

    if (isPublicPath) {
        return <main className="h-full overflow-y-auto bg-background">{children}</main>;
    }

    if (isLoading) {
        return (
            <div className="flex items-center justify-center w-full h-full">
                <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary" />
            </div>
        );
    }

    return <AppShell>{children}</AppShell>;
}

function AppShell({ children }: { children: React.ReactNode }) {
    const { open, close } = useSidebar();
    return (
        <div className="flex h-screen overflow-hidden">
            <AppSidebar />
            {open && (
                <div
                    aria-hidden
                    onClick={close}
                    className="fixed inset-0 z-30 bg-black/40 lg:hidden"
                />
            )}
            <div className="flex-1 flex flex-col min-w-0">
                <TopBar />
                <main className="flex-1 overflow-y-auto bg-background">{children}</main>
            </div>
        </div>
    );
}

export function AuthenticatedLayout({ children }: { children: React.ReactNode }) {
    return (
        <AuthProvider>
            <ToastProvider>
                <SidebarProvider>
                    <LayoutContent>{children}</LayoutContent>
                </SidebarProvider>
            </ToastProvider>
        </AuthProvider>
    );
}
