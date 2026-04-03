"use client";

import { useState } from "react";
import Link from "next/link";
import { GlassCard } from "@/components/ui/glass-card";
import { Fuel, ArrowLeft, CheckCircle2 } from "lucide-react";

const getApiBaseUrl = () => {
    if (typeof window !== "undefined") {
        const host = window.location.hostname;
        if (host.startsWith("devapp.")) {
            return `${window.location.protocol}//devapi.${host.slice(7)}/api`;
        }
        return process.env.NEXT_PUBLIC_API_URL || `${window.location.protocol}//${host}:8080/api`;
    }
    return process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api";
};

export default function ForgotPasscodePage() {
    const [phone, setPhone] = useState("");
    const [submitted, setSubmitted] = useState(false);
    const [error, setError] = useState("");
    const [isSubmitting, setIsSubmitting] = useState(false);

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setError("");

        if (!/^[6-9]\d{9}$/.test(phone)) {
            setError("Enter a valid 10-digit mobile number");
            return;
        }

        setIsSubmitting(true);
        try {
            const res = await fetch(`${getApiBaseUrl()}/auth/forgot-passcode`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ phone }),
            });

            if (res.ok) {
                setSubmitted(true);
            } else {
                const data = await res.json();
                setError(data.error || "Something went wrong");
            }
        } catch {
            setError("Network error. Please try again.");
        } finally {
            setIsSubmitting(false);
        }
    };

    if (submitted) {
        return (
            <div className="min-h-screen flex items-center justify-center bg-background p-4">
                <GlassCard className="w-full max-w-md p-8 text-center">
                    <CheckCircle2 className="w-12 h-12 text-green-500 mx-auto mb-4" />
                    <h2 className="text-lg font-bold mb-2">Request Submitted</h2>
                    <p className="text-sm text-muted-foreground mb-6">
                        The station admin has been notified. Please contact them to receive your new passcode.
                    </p>
                    <Link href="/login" className="text-primary text-sm hover:underline flex items-center justify-center gap-1">
                        <ArrowLeft className="w-4 h-4" /> Back to Login
                    </Link>
                </GlassCard>
            </div>
        );
    }

    return (
        <div className="min-h-screen flex items-center justify-center bg-background p-4">
            <GlassCard className="w-full max-w-md p-8">
                <div className="text-center mb-6">
                    <div className="flex items-center justify-center gap-2 text-primary font-bold text-xl mb-2">
                        <Fuel className="w-6 h-6" />
                        <span>StopForFuel</span>
                    </div>
                    <h2 className="text-lg font-semibold">Forgot Passcode</h2>
                    <p className="text-sm text-muted-foreground mt-1">
                        Enter your registered mobile number. The admin will be notified to reset your passcode.
                    </p>
                </div>

                <form onSubmit={handleSubmit} className="space-y-4">
                    <div>
                        <label className="block text-sm font-medium mb-1">Mobile Number</label>
                        <input
                            type="tel"
                            value={phone}
                            onChange={(e) => setPhone(e.target.value.replace(/\D/g, "").slice(0, 10))}
                            placeholder="Enter 10-digit mobile number"
                            className="w-full border border-border rounded-md px-3 py-2 text-sm bg-background focus:outline-none focus:ring-2 focus:ring-primary"
                            autoFocus
                        />
                    </div>

                    {error && <p className="text-sm text-red-500">{error}</p>}

                    <button
                        type="submit"
                        disabled={isSubmitting}
                        className="w-full bg-primary text-primary-foreground rounded-md py-2 text-sm font-medium hover:bg-primary/90 disabled:opacity-50"
                    >
                        {isSubmitting ? "Submitting..." : "Request Reset"}
                    </button>
                </form>

                <div className="mt-4 text-center">
                    <Link href="/login" className="text-sm text-primary hover:underline flex items-center justify-center gap-1">
                        <ArrowLeft className="w-4 h-4" /> Back to Login
                    </Link>
                </div>
            </GlassCard>
        </div>
    );
}
