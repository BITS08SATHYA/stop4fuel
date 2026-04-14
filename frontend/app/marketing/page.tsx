"use client";

import { GlassCard } from "@/components/ui/glass-card";
import { Megaphone, Gift, Tag, TrendingUp, Construction } from "lucide-react";

export default function MarketingPage() {
    return (
        <div className="p-4 sm:p-6 lg:p-8 min-h-screen bg-background transition-colors duration-300">
            <div className="max-w-7xl mx-auto">
                {/* Header */}
                <div className="mb-8">
                    <h1 className="text-4xl font-bold text-foreground tracking-tight">
                        Marketing <span className="text-gradient">Management</span>
                    </h1>
                    <p className="text-muted-foreground mt-2">
                        Manage promotions, offers, and marketing campaigns for your fuel station.
                    </p>
                </div>

                {/* Coming Soon */}
                <div className="text-center py-20 bg-black/5 dark:bg-white/5 rounded-2xl border border-dashed border-border">
                    <Construction className="w-16 h-16 mx-auto text-muted-foreground mb-4 opacity-50" />
                    <h3 className="text-xl font-semibold text-foreground mb-2">Coming Soon</h3>
                    <p className="text-muted-foreground mb-8 max-w-lg mx-auto">
                        Marketing management features are under development. This module will help you manage
                        promotions, loyalty programs, and customer engagement campaigns.
                    </p>

                    {/* Planned Features */}
                    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 max-w-4xl mx-auto px-8">
                        <GlassCard className="text-center">
                            <div className="p-2.5 rounded-xl bg-purple-500/10 text-purple-500 w-fit mx-auto mb-3">
                                <Megaphone className="w-5 h-5" />
                            </div>
                            <h4 className="text-sm font-semibold text-foreground">Campaigns</h4>
                            <p className="text-xs text-muted-foreground mt-1">Create and track marketing campaigns</p>
                        </GlassCard>
                        <GlassCard className="text-center">
                            <div className="p-2.5 rounded-xl bg-green-500/10 text-green-500 w-fit mx-auto mb-3">
                                <Tag className="w-5 h-5" />
                            </div>
                            <h4 className="text-sm font-semibold text-foreground">Offers & Discounts</h4>
                            <p className="text-xs text-muted-foreground mt-1">Manage special offers and pricing</p>
                        </GlassCard>
                        <GlassCard className="text-center">
                            <div className="p-2.5 rounded-xl bg-amber-500/10 text-amber-500 w-fit mx-auto mb-3">
                                <Gift className="w-5 h-5" />
                            </div>
                            <h4 className="text-sm font-semibold text-foreground">Loyalty Programs</h4>
                            <p className="text-xs text-muted-foreground mt-1">Customer rewards and loyalty points</p>
                        </GlassCard>
                        <GlassCard className="text-center">
                            <div className="p-2.5 rounded-xl bg-blue-500/10 text-blue-500 w-fit mx-auto mb-3">
                                <TrendingUp className="w-5 h-5" />
                            </div>
                            <h4 className="text-sm font-semibold text-foreground">Analytics</h4>
                            <p className="text-xs text-muted-foreground mt-1">Track campaign performance</p>
                        </GlassCard>
                    </div>
                </div>
            </div>
        </div>
    );
}
