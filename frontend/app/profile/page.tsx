"use client";

import { useEffect, useState } from "react";
import { GlassCard } from "@/components/ui/glass-card";
import { fetchWithAuth } from "@/lib/api/fetch-with-auth";
import { User, Loader2, Save, CheckCircle2 } from "lucide-react";

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

interface Profile {
    id: number;
    name: string;
    phone: string | null;
    email: string | null;
    role: string | null;
    designation: string | null;
    employeeCode: string | null;
    department: string | null;
    joinDate: string | null;
    status: string | null;
}

export default function ProfilePage() {
    const [profile, setProfile] = useState<Profile | null>(null);
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [saved, setSaved] = useState(false);
    const [name, setName] = useState("");
    const [phone, setPhone] = useState("");

    useEffect(() => {
        fetchWithAuth(`${getApiBaseUrl()}/profile`)
            .then((res) => res.json())
            .then((data) => {
                setProfile(data);
                setName(data.name || "");
                setPhone(data.phone || "");
            })
            .catch(() => {})
            .finally(() => setLoading(false));
    }, []);

    const handleSave = async () => {
        setSaving(true);
        setSaved(false);
        try {
            const res = await fetchWithAuth(`${getApiBaseUrl()}/profile`, {
                method: "PUT",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ name, phone }),
            });
            if (res.ok) {
                const data = await res.json();
                setProfile(data);
                setSaved(true);
                setTimeout(() => setSaved(false), 3000);
            }
        } catch {
            // ignore
        } finally {
            setSaving(false);
        }
    };

    if (loading) {
        return (
            <div className="flex items-center justify-center h-[60vh]">
                <Loader2 className="w-8 h-8 animate-spin text-primary" />
            </div>
        );
    }

    if (!profile) {
        return <p className="text-center text-muted-foreground py-12">Failed to load profile</p>;
    }

    return (
        <div className="max-w-2xl mx-auto space-y-6">
            <h1 className="text-xl font-bold flex items-center gap-2">
                <User className="w-5 h-5" /> My Profile
            </h1>

            <GlassCard className="p-6 space-y-4">
                <div className="grid sm:grid-cols-2 gap-4">
                    <div>
                        <label className="block text-xs font-medium text-muted-foreground mb-1">Name</label>
                        <input
                            type="text"
                            value={name}
                            onChange={(e) => setName(e.target.value)}
                            className="w-full border border-border rounded-md px-3 py-2 text-sm bg-background"
                        />
                    </div>
                    <div>
                        <label className="block text-xs font-medium text-muted-foreground mb-1">Phone</label>
                        <input
                            type="tel"
                            value={phone}
                            onChange={(e) => setPhone(e.target.value.replace(/\D/g, "").slice(0, 10))}
                            className="w-full border border-border rounded-md px-3 py-2 text-sm bg-background"
                        />
                    </div>
                </div>

                <div className="flex items-center gap-3">
                    <button
                        onClick={handleSave}
                        disabled={saving}
                        className="flex items-center gap-2 bg-primary text-primary-foreground rounded-md px-4 py-2 text-sm font-medium hover:bg-primary/90 disabled:opacity-50"
                    >
                        <Save className="w-4 h-4" />
                        {saving ? "Saving..." : "Save Changes"}
                    </button>
                    {saved && (
                        <span className="flex items-center gap-1 text-green-500 text-sm">
                            <CheckCircle2 className="w-4 h-4" /> Saved
                        </span>
                    )}
                </div>
            </GlassCard>

            {/* Read-only info */}
            <GlassCard className="p-6">
                <h3 className="text-sm font-bold mb-3">Account Details</h3>
                <div className="grid sm:grid-cols-2 gap-4 text-sm">
                    <div>
                        <span className="text-muted-foreground">Role</span>
                        <p className="font-medium">{profile.role || "-"}</p>
                    </div>
                    {profile.designation && (
                        <div>
                            <span className="text-muted-foreground">Designation</span>
                            <p className="font-medium">{profile.designation}</p>
                        </div>
                    )}
                    {profile.employeeCode && (
                        <div>
                            <span className="text-muted-foreground">Employee Code</span>
                            <p className="font-medium">{profile.employeeCode}</p>
                        </div>
                    )}
                    {profile.department && (
                        <div>
                            <span className="text-muted-foreground">Department</span>
                            <p className="font-medium">{profile.department}</p>
                        </div>
                    )}
                    <div>
                        <span className="text-muted-foreground">Email</span>
                        <p className="font-medium">{profile.email || "-"}</p>
                    </div>
                    <div>
                        <span className="text-muted-foreground">Joined</span>
                        <p className="font-medium">{profile.joinDate || "-"}</p>
                    </div>
                    <div>
                        <span className="text-muted-foreground">Status</span>
                        <p className={`font-medium ${profile.status === "ACTIVE" ? "text-green-500" : "text-red-500"}`}>
                            {profile.status}
                        </p>
                    </div>
                </div>
            </GlassCard>
        </div>
    );
}
