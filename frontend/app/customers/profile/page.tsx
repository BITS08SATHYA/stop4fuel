"use client";

import React from "react";

export default function ProfilePage() {
    return (
        <div className="p-8 min-h-screen bg-background text-foreground">
            <div className="max-w-7xl mx-auto">
                <div className="mb-8">
                    <h1 className="text-4xl font-bold tracking-tight">
                        Customer <span className="text-gradient">Profile</span>
                    </h1>
                    <p className="text-muted-foreground mt-2">
                        Detailed information and management for individual customers.
                    </p>
                </div>

                <div className="bg-card border border-border rounded-2xl p-12 text-center shadow-xl">
                    <div className="max-w-md mx-auto">
                        <div className="w-16 h-16 bg-primary/10 rounded-full flex items-center justify-center mx-auto mb-6">
                            <span className="text-2xl">👤</span>
                        </div>
                        <h2 className="text-2xl font-bold mb-4">Enhanced Profiles Coming Soon</h2>
                        <p className="text-muted-foreground mb-8">
                            A comprehensive 360-degree view of your customers, including transaction history and real-time statistics.
                        </p>
                        <div className="h-2 bg-muted rounded-full overflow-hidden">
                            <div className="w-2/3 h-full bg-primary animate-pulse" />
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}
