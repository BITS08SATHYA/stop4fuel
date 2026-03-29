"use client";

import { useState } from "react";
import { Fuel, Loader2, ArrowLeft, CheckCircle, Droplets, BarChart3, Truck } from "lucide-react";
import { resetPassword, confirmResetPassword } from "aws-amplify/auth";
import Link from "next/link";

type Step = "request" | "confirm" | "done";

export default function ForgotPasswordPage() {
    const [step, setStep] = useState<Step>("request");
    const [email, setEmail] = useState("");
    const [code, setCode] = useState("");
    const [newPassword, setNewPassword] = useState("");
    const [error, setError] = useState("");
    const [isSubmitting, setIsSubmitting] = useState(false);

    const handleRequestCode = async (e: React.FormEvent) => {
        e.preventDefault();
        setError("");

        if (!email.trim()) {
            setError("Please enter your email address.");
            return;
        }

        setIsSubmitting(true);
        try {
            await resetPassword({ username: email.trim() });
            setStep("confirm");
        } catch (err: unknown) {
            const error = err as Error;
            if (error.name === "UserNotFoundException") {
                setError("No account found with that email.");
            } else if (error.name === "LimitExceededException") {
                setError("Too many attempts. Please try again later.");
            } else {
                setError(error.message || "Failed to send reset code.");
            }
        } finally {
            setIsSubmitting(false);
        }
    };

    const handleConfirmReset = async (e: React.FormEvent) => {
        e.preventDefault();
        setError("");

        if (!code.trim() || !newPassword.trim()) {
            setError("Please enter the code and a new password.");
            return;
        }

        if (newPassword.length < 8) {
            setError("Password must be at least 8 characters.");
            return;
        }

        setIsSubmitting(true);
        try {
            await confirmResetPassword({
                username: email.trim(),
                confirmationCode: code.trim(),
                newPassword,
            });
            setStep("done");
        } catch (err: unknown) {
            const error = err as Error;
            if (error.name === "CodeMismatchException") {
                setError("Invalid verification code.");
            } else if (error.name === "ExpiredCodeException") {
                setError("Code has expired. Please request a new one.");
            } else {
                setError(error.message || "Failed to reset password.");
            }
        } finally {
            setIsSubmitting(false);
        }
    };

    return (
        <div className="flex min-h-screen">
            {/* Left panel — branding */}
            <div className="hidden lg:flex lg:w-1/2 bg-zinc-950 flex-col justify-between p-12">
                <div>
                    <div className="flex items-center gap-3">
                        <div className="p-2.5 bg-orange-500 rounded-xl">
                            <Fuel className="w-6 h-6 text-white" />
                        </div>
                        <span className="text-xl font-bold text-white tracking-tight">StopForFuel</span>
                    </div>
                </div>

                <div className="space-y-6">
                    <h2 className="text-4xl font-bold text-white leading-tight">
                        Powering your<br />
                        station operations
                    </h2>
                    <p className="text-zinc-400 text-lg max-w-md">
                        Complete fuel station management — invoices, inventory, shifts, payments, and analytics in one place.
                    </p>
                    <div className="flex gap-6 pt-4">
                        <div className="flex items-center gap-2 text-zinc-500">
                            <Droplets className="w-5 h-5 text-orange-500" />
                            <span className="text-sm">Fuel Tracking</span>
                        </div>
                        <div className="flex items-center gap-2 text-zinc-500">
                            <BarChart3 className="w-5 h-5 text-orange-500" />
                            <span className="text-sm">Analytics</span>
                        </div>
                        <div className="flex items-center gap-2 text-zinc-500">
                            <Truck className="w-5 h-5 text-orange-500" />
                            <span className="text-sm">Fleet Management</span>
                        </div>
                    </div>
                </div>

                <p className="text-zinc-700 text-sm">
                    &copy; 2026 StopForFuel. All rights reserved.
                </p>
            </div>

            {/* Right panel — form */}
            <div className="w-full lg:w-1/2 bg-zinc-900 flex items-center justify-center p-6 sm:p-12">
                <div className="w-full max-w-sm space-y-8">
                    {/* Mobile branding */}
                    <div className="lg:hidden flex items-center gap-3 justify-center">
                        <div className="p-2.5 bg-orange-500 rounded-xl">
                            <Fuel className="w-6 h-6 text-white" />
                        </div>
                        <span className="text-xl font-bold text-white tracking-tight">StopForFuel</span>
                    </div>

                    <div>
                        <h1 className="text-2xl font-bold text-white">
                            {step === "done" ? "Password reset" : "Reset password"}
                        </h1>
                        <p className="text-zinc-500 mt-1">
                            {step === "request" && "Enter your email to receive a verification code."}
                            {step === "confirm" && "Check your email for the verification code."}
                            {step === "done" && "Your password has been updated successfully."}
                        </p>
                    </div>

                    {/* Error */}
                    {error && (
                        <div className="bg-red-500/10 border border-red-500/20 rounded-lg px-4 py-3 text-red-400 text-sm">
                            {error}
                        </div>
                    )}

                    {/* Step: Request code */}
                    {step === "request" && (
                        <form onSubmit={handleRequestCode} className="space-y-5">
                            <div className="space-y-1.5">
                                <label htmlFor="email" className="text-zinc-400 text-sm font-medium">
                                    Email Address
                                </label>
                                <input
                                    id="email"
                                    type="email"
                                    value={email}
                                    onChange={(e) => setEmail(e.target.value)}
                                    placeholder="Enter your email"
                                    autoComplete="email"
                                    autoFocus
                                    className="w-full px-4 py-2.5 bg-zinc-800 border border-zinc-700 rounded-lg text-white placeholder-zinc-600 focus:outline-none focus:ring-2 focus:ring-orange-500 focus:border-transparent transition-all"
                                />
                            </div>
                            <button
                                type="submit"
                                disabled={isSubmitting}
                                className="w-full py-2.5 px-4 bg-orange-500 hover:bg-orange-600 disabled:bg-orange-500/50 disabled:cursor-not-allowed text-white rounded-lg font-semibold transition-colors flex items-center justify-center gap-2"
                            >
                                {isSubmitting ? (
                                    <>
                                        <Loader2 className="w-4 h-4 animate-spin" />
                                        Sending code...
                                    </>
                                ) : (
                                    "Send Verification Code"
                                )}
                            </button>
                        </form>
                    )}

                    {/* Step: Confirm code + new password */}
                    {step === "confirm" && (
                        <form onSubmit={handleConfirmReset} className="space-y-5">
                            <div className="space-y-1.5">
                                <label htmlFor="code" className="text-zinc-400 text-sm font-medium">
                                    Verification Code
                                </label>
                                <input
                                    id="code"
                                    type="text"
                                    value={code}
                                    onChange={(e) => setCode(e.target.value)}
                                    placeholder="Enter 6-digit code"
                                    autoComplete="one-time-code"
                                    autoFocus
                                    className="w-full px-4 py-2.5 bg-zinc-800 border border-zinc-700 rounded-lg text-white placeholder-zinc-600 focus:outline-none focus:ring-2 focus:ring-orange-500 focus:border-transparent transition-all"
                                />
                            </div>
                            <div className="space-y-1.5">
                                <label htmlFor="newPassword" className="text-zinc-400 text-sm font-medium">
                                    New Password
                                </label>
                                <input
                                    id="newPassword"
                                    type="password"
                                    value={newPassword}
                                    onChange={(e) => setNewPassword(e.target.value)}
                                    placeholder="Enter new password (min 8 chars)"
                                    autoComplete="new-password"
                                    className="w-full px-4 py-2.5 bg-zinc-800 border border-zinc-700 rounded-lg text-white placeholder-zinc-600 focus:outline-none focus:ring-2 focus:ring-orange-500 focus:border-transparent transition-all"
                                />
                            </div>
                            <button
                                type="submit"
                                disabled={isSubmitting}
                                className="w-full py-2.5 px-4 bg-orange-500 hover:bg-orange-600 disabled:bg-orange-500/50 disabled:cursor-not-allowed text-white rounded-lg font-semibold transition-colors flex items-center justify-center gap-2"
                            >
                                {isSubmitting ? (
                                    <>
                                        <Loader2 className="w-4 h-4 animate-spin" />
                                        Resetting...
                                    </>
                                ) : (
                                    "Reset Password"
                                )}
                            </button>
                        </form>
                    )}

                    {/* Step: Done */}
                    {step === "done" && (
                        <div className="space-y-5">
                            <CheckCircle className="w-12 h-12 text-green-400" />
                            <p className="text-zinc-400 text-sm">
                                You can now sign in with your new password.
                            </p>
                            <Link
                                href="/login"
                                className="block w-full py-2.5 px-4 bg-orange-500 hover:bg-orange-600 text-white rounded-lg font-semibold transition-colors text-center"
                            >
                                Back to Sign In
                            </Link>
                        </div>
                    )}

                    {/* Back link */}
                    {step !== "done" && (
                        <div>
                            <Link
                                href="/login"
                                className="flex items-center gap-2 text-sm text-zinc-500 hover:text-orange-400 transition-colors"
                            >
                                <ArrowLeft className="w-4 h-4" />
                                Back to Sign In
                            </Link>
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
}
