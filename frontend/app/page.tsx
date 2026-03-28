"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth/auth-context";
import { Fuel } from "lucide-react";

export default function RootPage() {
    const router = useRouter();
    const { isLoading, isAuthenticated } = useAuth();

    useEffect(() => {
        if (!isLoading && isAuthenticated) {
            router.replace("/dashboard");
        }
    }, [isLoading, isAuthenticated, router]);

    if (isLoading) {
        return (
            <div className="flex items-center justify-center min-h-screen bg-background">
                <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary" />
            </div>
        );
    }

    if (isAuthenticated) {
        return null; // Will redirect to /dashboard
    }

    // Landing page for unauthenticated users
    return (
        <div className="min-h-screen bg-gradient-to-br from-slate-950 via-slate-900 to-slate-950 text-white">
            {/* Nav */}
            <nav className="fixed top-0 left-0 right-0 z-50 backdrop-blur-xl bg-slate-950/70 border-b border-white/5 px-6 py-4">
                <div className="max-w-7xl mx-auto flex items-center justify-between">
                    <div className="flex items-center gap-3">
                        <div className="w-10 h-10 bg-gradient-to-br from-orange-500 to-orange-400 rounded-xl flex items-center justify-center shadow-lg shadow-orange-500/30">
                            <Fuel className="w-5 h-5 text-white" />
                        </div>
                        <span className="text-xl font-bold tracking-tight">
                            Stop<span className="text-orange-500">For</span>Fuel
                        </span>
                    </div>
                    <div className="flex items-center gap-4">
                        <a href="#features" className="hidden md:inline text-sm text-gray-400 hover:text-white transition-colors">Features</a>
                        <a href="#about" className="hidden md:inline text-sm text-gray-400 hover:text-white transition-colors">About</a>
                        <button
                            onClick={() => router.push("/login")}
                            className="px-5 py-2.5 bg-gradient-to-r from-orange-500 to-orange-600 text-white rounded-xl text-sm font-semibold shadow-lg shadow-orange-500/30 hover:shadow-orange-500/50 hover:-translate-y-0.5 transition-all"
                        >
                            Login
                        </button>
                    </div>
                </div>
            </nav>

            {/* Hero */}
            <section className="relative min-h-screen flex items-center pt-24 pb-16 px-6">
                <div className="max-w-7xl mx-auto w-full grid lg:grid-cols-2 gap-12 items-center">
                    <div className="lg:text-left text-center">
                        <div className="inline-flex items-center gap-2 bg-orange-500/10 border border-orange-500/25 px-4 py-1.5 rounded-full text-sm font-semibold text-orange-400 mb-6">
                            Trusted Since 1965
                        </div>
                        <h1 className="text-5xl md:text-6xl lg:text-7xl font-extrabold leading-[1.05] tracking-tight mb-6">
                            <span className="bg-gradient-to-r from-orange-500 to-amber-400 bg-clip-text text-transparent">
                                61 Years
                            </span>
                            <br />
                            of Fueling Your
                            <br />
                            Every Journey
                        </h1>
                        <p className="text-lg text-gray-400 max-w-lg mx-auto lg:mx-0 mb-8">
                            Chennai&apos;s most trusted fuel station. Premium quality fuel, transparent pricing, and unwavering commitment to every customer.
                        </p>
                        <div className="flex flex-wrap gap-4 justify-center lg:justify-start">
                            <button
                                onClick={() => router.push("/login")}
                                className="inline-flex items-center gap-2 bg-gradient-to-r from-orange-500 to-orange-600 text-white px-8 py-4 rounded-xl font-semibold text-base shadow-lg shadow-orange-500/30 hover:shadow-orange-500/50 hover:-translate-y-1 transition-all"
                            >
                                <Fuel className="w-5 h-5" />
                                Station Login
                            </button>
                        </div>
                    </div>

                    {/* Stats cards */}
                    <div className="grid grid-cols-2 gap-4">
                        {[
                            { label: "Years of Service", value: "61+" },
                            { label: "Daily Customers", value: "500+" },
                            { label: "Fuel Products", value: "4" },
                            { label: "Customer Trust", value: "100%" },
                        ].map((stat) => (
                            <div
                                key={stat.label}
                                className="bg-white/5 backdrop-blur-sm border border-white/10 rounded-2xl p-6 text-center hover:bg-white/10 transition-colors"
                            >
                                <div className="text-3xl font-extrabold bg-gradient-to-r from-orange-400 to-amber-300 bg-clip-text text-transparent">
                                    {stat.value}
                                </div>
                                <div className="text-sm text-gray-400 mt-1">{stat.label}</div>
                            </div>
                        ))}
                    </div>
                </div>
            </section>

            {/* Features */}
            <section id="features" className="py-20 px-6">
                <div className="max-w-7xl mx-auto">
                    <h2 className="text-3xl font-bold text-center mb-12">
                        Complete Station <span className="text-orange-500">Management</span>
                    </h2>
                    <div className="grid md:grid-cols-3 gap-6">
                        {[
                            { title: "Fuel Operations", desc: "Track tanks, pumps, nozzles, meter readings, and inventory in real-time." },
                            { title: "Credit & Payments", desc: "Manage credit customers, statements, invoices, and payment tracking." },
                            { title: "Shift Management", desc: "Complete shift lifecycle with closing reports, cash reconciliation." },
                            { title: "Employee Management", desc: "Attendance, salary, leave tracking, and advance management." },
                            { title: "Analytics Dashboard", desc: "Revenue trends, credit aging, payment analytics, and insights." },
                            { title: "Reports & Export", desc: "PDF and Excel reports with JasperReports for all modules." },
                        ].map((f) => (
                            <div
                                key={f.title}
                                className="bg-white/5 border border-white/10 rounded-2xl p-6 hover:border-orange-500/30 hover:bg-white/[0.07] transition-all"
                            >
                                <h3 className="text-lg font-semibold mb-2">{f.title}</h3>
                                <p className="text-sm text-gray-400">{f.desc}</p>
                            </div>
                        ))}
                    </div>
                </div>
            </section>

            {/* Footer */}
            <footer className="border-t border-white/10 py-8 px-6 text-center text-sm text-gray-500">
                <p>&copy; {new Date().getFullYear()} StopForFuel. All rights reserved.</p>
            </footer>
        </div>
    );
}
