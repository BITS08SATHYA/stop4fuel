"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth/auth-context";
import { ShieldCheck, BarChart3, Users, Clock, CreditCard, FileText } from "lucide-react";
import Image from "next/image";

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
            <div className="flex items-center justify-center min-h-screen bg-[#0D1117]">
                <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-[#FFB300]" />
            </div>
        );
    }

    if (isAuthenticated) {
        return null;
    }

    const features = [
        { icon: ShieldCheck, title: "Fuel Operations", desc: "Track tanks, pumps, nozzles, meter readings, and inventory in real-time." },
        { icon: CreditCard, title: "Credit & Payments", desc: "Manage credit customers, statements, invoices, and payment tracking." },
        { icon: Clock, title: "Shift Management", desc: "Complete shift lifecycle with closing reports, cash reconciliation." },
        { icon: Users, title: "Employee Management", desc: "Attendance, salary, leave tracking, and advance management." },
        { icon: BarChart3, title: "Analytics Dashboard", desc: "Revenue trends, credit aging, payment analytics, and insights." },
        { icon: FileText, title: "Reports & Export", desc: "Printable shift reports, statements, and invoices for all modules." },
    ];

    return (
        <div className="min-h-screen bg-[#0D1117] text-white">
            {/* Ambient effects */}
            <div className="fixed inset-0 pointer-events-none">
                <div className="absolute -top-48 -right-48 w-[600px] h-[600px] bg-[#FFB300] rounded-full opacity-[0.03] blur-[120px]" />
                <div className="absolute -bottom-36 -left-36 w-[500px] h-[500px] bg-[#14B8A6] rounded-full opacity-[0.03] blur-[120px]" />
            </div>

            {/* Nav */}
            <nav className="fixed top-0 left-0 right-0 z-50 backdrop-blur-xl bg-[#0D1117]/80 border-b border-white/5 px-6 py-4">
                <div className="max-w-7xl mx-auto flex items-center justify-between">
                    <div className="flex items-center gap-3">
                        <Image src="/logo-icon.svg" alt="StopForFuel" width={28} height={37} />
                        <span className="text-xl font-bold tracking-tight">
                            Stop<span className="text-[#FFCA28]">ForFuel</span>
                        </span>
                    </div>
                    <div className="flex items-center gap-4">
                        <a href="#features" className="hidden md:inline text-sm text-[#94A3B8] hover:text-white transition-colors">Features</a>
                        <a href="#about" className="hidden md:inline text-sm text-[#94A3B8] hover:text-white transition-colors">About</a>
                        <button
                            onClick={() => router.push("/login")}
                            className="px-5 py-2.5 bg-gradient-to-r from-[#FFC107] to-[#FF8F00] text-[#0D1117] rounded-xl text-sm font-bold shadow-lg shadow-[#FFB300]/20 hover:shadow-[#FFB300]/40 hover:-translate-y-0.5 transition-all"
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
                        <div className="inline-flex items-center gap-2 bg-[#FFB300]/10 border border-[#FFB300]/20 px-4 py-1.5 rounded-full text-sm font-semibold text-[#FFCA28] mb-6">
                            Trusted Since 1965
                        </div>
                        <h1 className="text-5xl md:text-6xl lg:text-7xl font-extrabold leading-[1.05] tracking-tight mb-6">
                            <span className="bg-gradient-to-r from-[#FFB300] to-[#E65100] bg-clip-text text-transparent">
                                61 Years
                            </span>
                            <br />
                            of Fueling Your
                            <br />
                            Every Journey
                        </h1>
                        <p className="text-lg text-[#94A3B8] max-w-lg mx-auto lg:mx-0 mb-8">
                            Chennai&apos;s most trusted fuel station. Premium quality fuel, transparent pricing, and unwavering commitment to every customer.
                        </p>
                        <div className="flex flex-wrap gap-4 justify-center lg:justify-start">
                            <button
                                onClick={() => router.push("/login")}
                                className="inline-flex items-center gap-2 bg-gradient-to-r from-[#FFC107] to-[#FF8F00] text-[#0D1117] px-8 py-4 rounded-xl font-bold text-base shadow-lg shadow-[#FFB300]/20 hover:shadow-[#FFB300]/40 hover:-translate-y-1 transition-all"
                            >
                                <ShieldCheck className="w-5 h-5" />
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
                                className="bg-[#161B22] border border-[#21283B] rounded-2xl p-6 text-center hover:border-[#FFB300]/20 transition-colors"
                            >
                                <div className="text-3xl font-extrabold bg-gradient-to-r from-[#FFCA28] to-[#FFB300] bg-clip-text text-transparent">
                                    {stat.value}
                                </div>
                                <div className="text-sm text-[#94A3B8] mt-1">{stat.label}</div>
                            </div>
                        ))}
                    </div>
                </div>
            </section>

            {/* Features */}
            <section id="features" className="py-20 px-6">
                <div className="max-w-7xl mx-auto">
                    <h2 className="text-3xl font-bold text-center mb-12">
                        Complete Station <span className="text-[#FFB300]">Management</span>
                    </h2>
                    <div className="grid md:grid-cols-3 gap-6">
                        {features.map((f) => (
                            <div
                                key={f.title}
                                className="bg-[#161B22] border border-[#21283B] rounded-2xl p-6 hover:border-[#FFB300]/20 transition-all group"
                            >
                                <div className="w-10 h-10 rounded-xl bg-[#FFB300]/10 flex items-center justify-center mb-4 group-hover:bg-[#FFB300]/15 transition-colors">
                                    <f.icon className="w-5 h-5 text-[#FFB300]" />
                                </div>
                                <h3 className="text-lg font-semibold mb-2">{f.title}</h3>
                                <p className="text-sm text-[#94A3B8]">{f.desc}</p>
                            </div>
                        ))}
                    </div>
                </div>
            </section>

            {/* About */}
            <section id="about" className="py-20 px-6 border-t border-[#21283B]">
                <div className="max-w-3xl mx-auto text-center">
                    <Image src="/logo-icon.svg" alt="StopForFuel" width={48} height={64} className="mx-auto mb-6" />
                    <h2 className="text-2xl font-bold mb-4">
                        Proudly Serving Chennai Since <span className="text-[#FFB300]">1965</span>
                    </h2>
                    <p className="text-[#94A3B8] leading-relaxed">
                        What started as a humble fuel station has grown into Chennai&apos;s most trusted energy partner.
                        StopForFuel combines decades of experience with modern technology to deliver premium fuel operations,
                        transparent credit management, and real-time business insights.
                    </p>
                </div>
            </section>

            {/* Footer */}
            <footer className="border-t border-[#21283B] py-8 px-6 text-center text-sm text-[#64748B]">
                <p>&copy; {new Date().getFullYear()} StopForFuel. All rights reserved.</p>
            </footer>
        </div>
    );
}
