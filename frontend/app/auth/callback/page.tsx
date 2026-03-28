"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Fuel } from "lucide-react";
import { Hub } from "aws-amplify/utils";
import { getCurrentUser } from "aws-amplify/auth";

export default function AuthCallbackPage() {
    const router = useRouter();
    const [error, setError] = useState("");

    useEffect(() => {
        let cancelled = false;

        // Listen for Amplify auth events
        const unsubscribe = Hub.listen("auth", ({ payload }) => {
            if (cancelled) return;
            if (payload.event === "signInWithRedirect") {
                router.push("/dashboard");
            } else if (payload.event === "signInWithRedirect_failure") {
                console.error("Sign-in redirect failed:", payload.data);
                setError("Authentication failed. Redirecting to login...");
                setTimeout(() => router.push("/login"), 3000);
            }
        });

        // Also check if user is already authenticated (page reload)
        const checkExisting = async () => {
            try {
                await getCurrentUser();
                if (!cancelled) router.push("/dashboard");
            } catch {
                // Not yet authenticated — Hub listener will handle it
            }
        };
        checkExisting();

        return () => {
            cancelled = true;
            unsubscribe();
        };
    }, [router]);

    return (
        <div className="flex flex-col items-center justify-center min-h-screen bg-background gap-4">
            <Fuel className="w-12 h-12 text-primary" />
            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary" />
            {error ? (
                <p className="text-rose-400 text-sm">{error}</p>
            ) : (
                <p className="text-muted-foreground">Signing you in...</p>
            )}
        </div>
    );
}
