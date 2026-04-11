"use client";

import { Suspense, useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Phone, ArrowLeft, ShieldCheck } from "lucide-react";
import { useAuth } from "@/lib/auth/auth-context";
import Image from "next/image";

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
            <div className="flex items-center justify-center min-h-screen bg-[#0D1117]">
                <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-[#FFB300]" />
            </div>
        );
    }

    return (
        <div className="flex min-h-screen">
            {/* Left panel — branding */}
            <div className="hidden lg:flex lg:w-1/2 bg-[#0D1117] relative overflow-hidden flex-col items-center justify-center p-12">
                {/* Ambient glow */}
                <div className="absolute top-1/4 left-1/2 -translate-x-1/2 w-[400px] h-[400px] bg-[#FFB300] rounded-full opacity-[0.04] blur-[120px]" />
                <div className="absolute bottom-0 left-0 right-0 h-px bg-gradient-to-r from-transparent via-[#FFB300]/30 to-transparent" />

                {/* Logo */}
                <div className="relative z-10 flex flex-col items-center text-center">
                    <Image src="/logo-icon.svg" alt="StopForFuel" width={64} height={84} className="mb-6" />
                    <h2 className="text-3xl font-extrabold text-white tracking-tight mb-2">
                        Stop<span className="text-[#FFCA28]">ForFuel</span>
                    </h2>
                    <p className="text-[#94A3B8] text-sm max-w-xs mb-10">
                        Fuel Station Management System
                    </p>

                    {/* Feature highlights */}
                    <div className="space-y-4 text-left">
                        {[
                            "Real-time fuel & inventory tracking",
                            "Credit management & payment ledger",
                            "Shift reports with cash reconciliation",
                        ].map((feat) => (
                            <div key={feat} className="flex items-center gap-3">
                                <ShieldCheck className="w-4 h-4 text-[#FFB300] flex-shrink-0" />
                                <span className="text-sm text-[#CBD5E1]">{feat}</span>
                            </div>
                        ))}
                    </div>

                    <div className="mt-12 text-xs text-[#64748B]">
                        Est. 1965 &middot; Chennai&apos;s most trusted fuel station
                    </div>
                </div>
            </div>

            {/* Right panel — login form */}
            <div className="flex-1 flex items-center justify-center bg-[#161B22] p-6">
                <div className="w-full max-w-sm">
                    {/* Mobile logo */}
                    <div className="lg:hidden flex flex-col items-center mb-8">
                        <Image src="/logo-icon.svg" alt="StopForFuel" width={48} height={64} className="mb-4" />
                        <h1 className="text-2xl font-bold text-white">
                            Stop<span className="text-[#FFCA28]">ForFuel</span>
                        </h1>
                        <p className="text-[#94A3B8] text-sm mt-1">Fuel Station Management System</p>
                    </div>

                    <div className="hidden lg:block mb-8">
                        <h1 className="text-2xl font-bold text-white">Welcome back</h1>
                        <p className="text-[#94A3B8] text-sm mt-1">Sign in to your account</p>
                    </div>

                    <form onSubmit={handleSubmit} className="space-y-5">
                        {error && (
                            <div className="p-3 text-sm text-red-400 bg-red-500/10 border border-red-500/20 rounded-xl">
                                {error}
                            </div>
                        )}

                        <div className="space-y-2">
                            <label htmlFor="phone" className="text-sm font-medium text-[#CBD5E1]">
                                Mobile Number
                            </label>
                            <div className="relative">
                                <span className="absolute left-3 top-1/2 -translate-y-1/2 text-[#64748B] text-sm flex items-center gap-1.5">
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
                                    className="w-full pl-20 pr-3 py-3 bg-[#0D1117] border border-[#21283B] rounded-xl text-white placeholder:text-[#475569] focus:outline-none focus:ring-2 focus:ring-[#FFB300]/40 focus:border-[#FFB300] transition-colors"
                                />
                            </div>
                        </div>

                        <div className="space-y-2">
                            <label htmlFor="passcode" className="text-sm font-medium text-[#CBD5E1]">
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
                                className="w-full px-3 py-3 bg-[#0D1117] border border-[#21283B] rounded-xl text-white placeholder:text-[#475569] focus:outline-none focus:ring-2 focus:ring-[#FFB300]/40 focus:border-[#FFB300] tracking-[0.5em] text-center text-lg transition-colors"
                            />
                        </div>

                        <button
                            type="submit"
                            disabled={isSubmitting}
                            className="w-full py-3 px-4 bg-gradient-to-r from-[#FFC107] to-[#FF8F00] text-[#0D1117] rounded-xl font-bold hover:from-[#FFCA28] hover:to-[#FFB300] transition-all shadow-lg shadow-[#FFB300]/20 disabled:opacity-50 disabled:cursor-not-allowed"
                        >
                            {isSubmitting ? "Signing in..." : "Sign In"}
                        </button>

                        <div className="flex items-center justify-between text-sm">
                            <a href="/forgot-passcode" className="text-[#FFB300] hover:text-[#FFCA28] transition-colors">
                                Forgot Passcode?
                            </a>
                            <span className="text-[#64748B]">
                                Contact admin for an account
                            </span>
                        </div>
                        <a
                            href={process.env.NEXT_PUBLIC_LANDING_URL || '/'}
                            className="flex items-center justify-center gap-1.5 text-sm text-[#64748B] hover:text-[#94A3B8] transition-colors"
                        >
                            <ArrowLeft className="w-3.5 h-3.5" />
                            Back to website
                        </a>
                    </form>
                </div>
            </div>
        </div>
    );
}

export default function LoginPage() {
    return (
        <Suspense fallback={
            <div className="flex items-center justify-center min-h-screen bg-[#0D1117]">
                <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-[#FFB300]" />
            </div>
        }>
            <LoginContent />
        </Suspense>
    );
}
