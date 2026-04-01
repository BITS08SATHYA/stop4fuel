"use client";

import { Suspense, useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Fuel, Phone } from "lucide-react";
import { useAuth } from "@/lib/auth/auth-context";

function LoginContent() {
    const { isAuthenticated, isLoading, loginWithPasscode } = useAuth();
    const router = useRouter();
    const searchParams = useSearchParams();
    const [phone, setPhone] = useState("");
    const [passcode, setPasscode] = useState("");
    const [error, setError] = useState("");
    const [isSubmitting, setIsSubmitting] = useState(false);

    useEffect(() => {
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

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setError("");

        if (!/^[6-9]\d{9}$/.test(phone)) {
            setError("Enter a valid 10-digit mobile number");
            return;
        }
        if (!/^\d{4}$/.test(passcode)) {
            setError("Enter a valid 4-digit passcode");
            return;
        }

        setIsSubmitting(true);
        try {
            const result = await loginWithPasscode(phone, passcode);
            if (!result.success) {
                setError(result.error || "Sign in failed. Check your credentials.");
            }
        } catch {
            setError("An unexpected error occurred. Please try again.");
        } finally {
            setIsSubmitting(false);
        }
    };

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

                <form onSubmit={handleSubmit} className="space-y-4">
                    {error && (
                        <div className="p-3 text-sm text-red-600 bg-red-50 dark:bg-red-950/30 dark:text-red-400 rounded-lg">
                            {error}
                        </div>
                    )}

                    <div className="space-y-2">
                        <label htmlFor="phone" className="text-sm font-medium text-foreground">
                            Mobile Number
                        </label>
                        <div className="relative">
                            <span className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground text-sm flex items-center gap-1">
                                <Phone className="w-4 h-4" />
                                +91
                            </span>
                            <input
                                id="phone"
                                type="tel"
                                inputMode="numeric"
                                value={phone}
                                onChange={(e) => {
                                    const val = e.target.value.replace(/\D/g, "").slice(0, 10);
                                    setPhone(val);
                                }}
                                placeholder="9840011111"
                                required
                                autoComplete="tel"
                                className="w-full pl-20 pr-3 py-2 border border-input rounded-lg bg-background text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/50 focus:border-primary"
                            />
                        </div>
                    </div>

                    <div className="space-y-2">
                        <label htmlFor="passcode" className="text-sm font-medium text-foreground">
                            Passcode
                        </label>
                        <input
                            id="passcode"
                            type="password"
                            inputMode="numeric"
                            maxLength={4}
                            value={passcode}
                            onChange={(e) => {
                                const val = e.target.value.replace(/\D/g, "").slice(0, 4);
                                setPasscode(val);
                            }}
                            placeholder="4-digit passcode"
                            required
                            autoComplete="current-password"
                            className="w-full px-3 py-2 border border-input rounded-lg bg-background text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/50 focus:border-primary tracking-[0.5em] text-center text-lg"
                        />
                    </div>

                    <button
                        type="submit"
                        disabled={isSubmitting}
                        className="w-full py-3 px-4 bg-primary text-primary-foreground rounded-lg font-medium hover:bg-primary/90 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                        {isSubmitting ? "Signing in..." : "Sign In"}
                    </button>

                    <div className="flex items-center justify-between text-sm">
                        <a href="/forgot-passcode" className="text-primary hover:underline">
                            Forgot Passcode?
                        </a>
                        <span className="text-muted-foreground">
                            Contact admin for an account
                        </span>
                    </div>
                    <a
                        href={process.env.NEXT_PUBLIC_LANDING_URL || '/'}
                        className="text-center text-sm text-muted-foreground hover:text-foreground transition-colors block"
                    >
                        &larr; Back to website
                    </a>
                </form>
            </div>
        </div>
    );
}

export default function LoginPage() {
    return (
        <Suspense fallback={
            <div className="flex items-center justify-center min-h-screen bg-background">
                <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary" />
            </div>
        }>
            <LoginContent />
        </Suspense>
    );
}
