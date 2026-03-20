"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { Fuel } from "lucide-react";

export default function AuthCallbackPage() {
    const router = useRouter();

    useEffect(() => {
        // Amplify handles the OAuth code exchange automatically
        // Just wait a moment and redirect to home
        const timer = setTimeout(() => {
            router.push("/");
        }, 2000);

        return () => clearTimeout(timer);
    }, [router]);

    return (
        <div className="flex flex-col items-center justify-center min-h-screen bg-background gap-4">
            <Fuel className="w-12 h-12 text-primary" />
            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary" />
            <p className="text-muted-foreground">Signing you in...</p>
        </div>
    );
}
