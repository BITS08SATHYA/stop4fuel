"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { GlassCard } from "@/components/ui/glass-card";
import { Badge } from "@/components/ui/badge";
import { ProductImage } from "@/components/ui/product-image";
import { getActiveNonFuelProducts, Product } from "@/lib/api/station";
import { Package, Search } from "lucide-react";

export default function ProductProfilesPage() {
    const [products, setProducts] = useState<Product[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [search, setSearch] = useState("");

    useEffect(() => {
        getActiveNonFuelProducts()
            .then(setProducts)
            .catch((err) => setError(err.message || "Failed to load products"))
            .finally(() => setIsLoading(false));
    }, []);

    const filtered = products.filter((p) => {
        const q = search.trim().toLowerCase();
        if (!q) return true;
        return (
            p.name.toLowerCase().includes(q) ||
            (p.brand || "").toLowerCase().includes(q) ||
            (p.gradeType?.name || "").toLowerCase().includes(q)
        );
    });

    if (isLoading) {
        return (
            <div className="min-h-screen bg-background p-8 flex flex-col items-center justify-center">
                <div className="w-12 h-12 border-4 border-primary/20 border-t-primary rounded-full animate-spin mb-4" />
                <p className="text-muted-foreground animate-pulse">Loading product profiles...</p>
            </div>
        );
    }

    if (error) {
        return (
            <div className="min-h-screen bg-background p-8 flex flex-col items-center justify-center">
                <p className="text-red-500 mb-2">Failed to load product profiles</p>
                <p className="text-muted-foreground text-sm">{error}</p>
            </div>
        );
    }

    return (
        <div className="min-h-screen bg-background p-6 md:p-8 transition-colors duration-300">
            <div className="max-w-7xl mx-auto space-y-6">
                <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
                    <div>
                        <h1 className="text-4xl font-bold text-foreground tracking-tight">
                            <span className="text-gradient">Product Profiles</span>
                        </h1>
                        <p className="text-muted-foreground mt-1">
                            Non-fuel products &mdash; stock, pricing and sales history
                        </p>
                    </div>
                    <div className="relative">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                        <input
                            type="text"
                            value={search}
                            onChange={(e) => setSearch(e.target.value)}
                            placeholder="Search name, brand, grade..."
                            className="pl-9 pr-3 py-2.5 bg-card border border-border rounded-xl text-sm w-64 focus:outline-none focus:ring-2 focus:ring-primary/40"
                        />
                    </div>
                </div>

                {filtered.length === 0 ? (
                    <GlassCard className="flex flex-col items-center justify-center py-16">
                        <Package className="w-10 h-10 text-muted-foreground mb-3" />
                        <p className="text-muted-foreground">
                            {products.length === 0 ? "No non-fuel products found" : "No products match your search"}
                        </p>
                    </GlassCard>
                ) : (
                    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
                        {filtered.map((p) => (
                            <Link key={p.id} href={`/operations/inventory/product-profiles/${p.id}`} className="group">
                                <GlassCard className="h-full p-4 space-y-3 transition-all group-hover:border-primary/30 group-hover:-translate-y-0.5">
                                    <ProductImage name={p.name} size="card" />
                                    <div>
                                        <h3 className="text-sm font-bold text-foreground truncate group-hover:text-primary transition-colors">
                                            {p.name}
                                        </h3>
                                        <p className="text-xs text-muted-foreground truncate">
                                            {[p.brand, p.oilType?.name].filter(Boolean).join(" · ") || "—"}
                                        </p>
                                    </div>
                                    <div className="flex items-center justify-between">
                                        {p.gradeType?.name ? (
                                            <Badge className="text-[10px]">{p.gradeType.name}</Badge>
                                        ) : (
                                            <span />
                                        )}
                                        <div className="text-right">
                                            <span className="text-base font-bold text-foreground">
                                                &#8377;{(p.price ?? 0).toLocaleString("en-IN", { minimumFractionDigits: 2 })}
                                            </span>
                                            <span className="text-[10px] text-muted-foreground ml-1">/ {p.unit?.toLowerCase()}</span>
                                        </div>
                                    </div>
                                </GlassCard>
                            </Link>
                        ))}
                    </div>
                )}
            </div>
        </div>
    );
}
