"use client";

import { Suspense, useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Fuel, Eye, EyeOff } from "lucide-react";
import { useAuth } from "@/lib/auth/auth-context";

function LoginContent() {
    const { isAuthenticated, isLoading, login } = useAuth();
    const router = useRouter();
    const searchParams = useSearchParams();
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const [showPassword, setShowPassword] = useState(false);
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
        setIsSubmitting(true);

        try {
            const result = await login(email, password);
            if (!result.success) {
                if (result.challengeName === 'CONFIRM_SIGN_IN_WITH_NEW_PASSWORD_REQUIRED') {
                    setError("You need to set a new password. Please contact your admin.");
                } else {
                    setError(result.error || "Sign in failed. Please check your credentials.");
                }
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
                        <label htmlFor="email" className="text-sm font-medium text-foreground">
                            Email
                        </label>
                        <input
                            id="email"
                            type="email"
                            value={email}
                            onChange={(e) => setEmail(e.target.value)}
                            placeholder="name@host.com"
                            required
                            autoComplete="email"
                            className="w-full px-3 py-2 border border-input rounded-lg bg-background text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/50 focus:border-primary"
                        />
                    </div>

                    <div className="space-y-2">
                        <label htmlFor="password" className="text-sm font-medium text-foreground">
                            Password
                        </label>
                        <div className="relative">
                            <input
                                id="password"
                                type={showPassword ? "text" : "password"}
                                value={password}
                                onChange={(e) => setPassword(e.target.value)}
                                placeholder="Password"
                                required
                                autoComplete="current-password"
                                className="w-full px-3 py-2 pr-10 border border-input rounded-lg bg-background text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/50 focus:border-primary"
                            />
                            <button
                                type="button"
                                onClick={() => setShowPassword(!showPassword)}
                                className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                                tabIndex={-1}
                            >
                                {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                            </button>
                        </div>
                    </div>

                    <button
                        type="submit"
                        disabled={isSubmitting}
                        className="w-full py-3 px-4 bg-primary text-primary-foreground rounded-lg font-medium hover:bg-primary/90 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                        {isSubmitting ? "Signing in..." : "Sign In"}
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
