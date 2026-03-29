"use client";

import { useEffect } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Fuel } from "lucide-react";
import { useAuth } from "@/lib/auth/auth-context";

export default function LoginPage() {
    const { isAuthenticated, isLoading, login } = useAuth();
    const router = useRouter();
    const searchParams = useSearchParams();

    useEffect(() => {
        // Store returnTo so we can redirect after OAuth callback
        const returnTo = searchParams.get("returnTo");
        if (returnTo) {
            sessionStorage.setItem("sff-return-to", returnTo);
        }
    }, [searchParams]);

    useEffect(() => {
        if (!isLoading && isAuthenticated) {
            const returnTo = sessionStorage.getItem("sff-return-to");
            sessionStorage.removeItem("sff-return-to");
            router.push(returnTo || "/dashboard");
        }
    }, [isAuthenticated, isLoading, router]);

    if (isLoading) {
        return (
            <div className="flex items-center justify-center min-h-screen bg-background">
                <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary" />
            </div>
        );
    }

    return (
        <div className="flex items-center justify-center min-h-screen bg-background">
            <div className="w-full max-w-md p-8 space-y-8">
                <div className="flex flex-col items-center space-y-4">
                    <div className="flex items-center gap-3 text-primary">
                        <Fuel className="w-12 h-12" />
                        <h1 className="text-3xl font-bold">StopForFuel</h1>
                    </div>
                    <p className="text-muted-foreground text-center">
                        Fuel Station Management System
                    </p>
                </div>

                <div className="space-y-4">
                    <button
                        onClick={login}
                        className="w-full py-3 px-4 bg-primary text-primary-foreground rounded-lg font-medium hover:bg-primary/90 transition-colors"
                    >
                        Sign In
                    </button>
                    <p className="text-center text-sm text-muted-foreground">
                        Contact your station admin for an account
                    </p>
                    <a
                        href={process.env.NEXT_PUBLIC_LANDING_URL || '/'}
                        className="text-center text-sm text-muted-foreground hover:text-foreground transition-colors block"
                    >
                        &larr; Back to website
                    </a>
                </div>
            </div>
        </div>
    );
}
