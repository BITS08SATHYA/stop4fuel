"use client";

import React, { useEffect, useState } from "react";
import { fetchWithAuth } from "@/lib/api/fetch-with-auth";
import { X, Loader2, Phone } from "lucide-react";

const getApiBaseUrl = () => {
    if (typeof window !== "undefined") {
        return process.env.NEXT_PUBLIC_API_URL || `${window.location.protocol}//${window.location.hostname}:8080/api`;
    }
    return process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api";
};

interface Designation {
    id: number;
    name: string;
    defaultRole: string | null;
}

interface CreateUserModalProps {
    onClose: () => void;
    onCreated: (name: string, passcode: string) => void;
}

export function CreateUserModal({ onClose, onCreated }: CreateUserModalProps) {
    const [name, setName] = useState("");
    const [phone, setPhone] = useState("");
    const [userType, setUserType] = useState<"EMPLOYEE" | "CUSTOMER">("EMPLOYEE");
    const [designation, setDesignation] = useState("");
    const [roleType, setRoleType] = useState("");
    const [designations, setDesignations] = useState<Designation[]>([]);
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState("");

    useEffect(() => {
        fetchWithAuth(`${getApiBaseUrl()}/designations`)
            .then((res) => res.json())
            .then((data) => setDesignations(data))
            .catch(() => {});
    }, []);

    useEffect(() => {
        if (designation && designations.length > 0) {
            const desig = designations.find((d) => d.name === designation);
            if (desig?.defaultRole) {
                setRoleType(desig.defaultRole);
            }
        }
    }, [designation, designations]);

    useEffect(() => {
        if (userType === "CUSTOMER") {
            setDesignation("");
            setRoleType("CUSTOMER");
        } else {
            setRoleType("");
        }
    }, [userType]);

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setError("");

        if (!name.trim()) {
            setError("Name is required");
            return;
        }
        if (!/^[6-9]\d{9}$/.test(phone)) {
            setError("Enter a valid 10-digit mobile number");
            return;
        }
        if (!roleType) {
            setError("Role is required");
            return;
        }

        setSubmitting(true);
        try {
            const res = await fetchWithAuth(`${getApiBaseUrl()}/admin/users`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    name: name.trim(),
                    phone,
                    userType,
                    designation: designation || null,
                    roleType,
                }),
            });

            if (!res.ok) {
                const data = await res.json().catch(() => ({}));
                setError(data.message || data.error || "Failed to create user");
                return;
            }

            const data = await res.json();
            onCreated(name.trim(), data.passcode);
        } catch {
            setError("Failed to create user");
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
            <div className="bg-background rounded-xl p-6 w-full max-w-md shadow-xl border border-border">
                <div className="flex items-center justify-between mb-4">
                    <h2 className="text-lg font-semibold text-foreground">Create User</h2>
                    <button onClick={onClose} className="text-muted-foreground hover:text-foreground">
                        <X className="w-5 h-5" />
                    </button>
                </div>

                <form onSubmit={handleSubmit} className="space-y-4">
                    {error && (
                        <div className="p-3 text-sm text-red-600 bg-red-50 dark:bg-red-950/30 dark:text-red-400 rounded-lg">
                            {error}
                        </div>
                    )}

                    <div className="space-y-1.5">
                        <label className="text-sm font-medium text-foreground">Name</label>
                        <input
                            type="text"
                            value={name}
                            onChange={(e) => setName(e.target.value)}
                            placeholder="Full name"
                            required
                            className="w-full px-3 py-2 border border-input rounded-lg bg-background text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                        />
                    </div>

                    <div className="space-y-1.5">
                        <label className="text-sm font-medium text-foreground">Mobile Number</label>
                        <div className="relative">
                            <span className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground text-sm flex items-center gap-1">
                                <Phone className="w-4 h-4" />
                                +91
                            </span>
                            <input
                                type="tel"
                                inputMode="numeric"
                                value={phone}
                                onChange={(e) => {
                                    const val = e.target.value.replace(/\D/g, "").slice(0, 10);
                                    setPhone(val);
                                }}
                                placeholder="9840011111"
                                required
                                className="w-full pl-20 pr-3 py-2 border border-input rounded-lg bg-background text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                            />
                        </div>
                    </div>

                    <div className="space-y-1.5">
                        <label className="text-sm font-medium text-foreground">User Type</label>
                        <div className="flex gap-3">
                            <label className="flex items-center gap-2 cursor-pointer">
                                <input
                                    type="radio"
                                    name="userType"
                                    value="EMPLOYEE"
                                    checked={userType === "EMPLOYEE"}
                                    onChange={() => setUserType("EMPLOYEE")}
                                    className="accent-primary"
                                />
                                <span className="text-sm text-foreground">Employee</span>
                            </label>
                            <label className="flex items-center gap-2 cursor-pointer">
                                <input
                                    type="radio"
                                    name="userType"
                                    value="CUSTOMER"
                                    checked={userType === "CUSTOMER"}
                                    onChange={() => setUserType("CUSTOMER")}
                                    className="accent-primary"
                                />
                                <span className="text-sm text-foreground">Customer</span>
                            </label>
                        </div>
                    </div>

                    {userType === "EMPLOYEE" && (
                        <div className="space-y-1.5">
                            <label className="text-sm font-medium text-foreground">Designation</label>
                            <select
                                value={designation}
                                onChange={(e) => setDesignation(e.target.value)}
                                className="w-full px-3 py-2 border border-input rounded-lg bg-background text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                            >
                                <option value="">Select designation</option>
                                {designations.map((d) => (
                                    <option key={d.id} value={d.name}>
                                        {d.name} {d.defaultRole ? `(${d.defaultRole})` : ""}
                                    </option>
                                ))}
                            </select>
                        </div>
                    )}

                    <div className="space-y-1.5">
                        <label className="text-sm font-medium text-foreground">Role</label>
                        <select
                            value={roleType}
                            onChange={(e) => setRoleType(e.target.value)}
                            className="w-full px-3 py-2 border border-input rounded-lg bg-background text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                        >
                            <option value="">Select role</option>
                            <option value="ADMIN">Admin</option>
                            <option value="CASHIER">Cashier</option>
                            <option value="EMPLOYEE">Employee</option>
                            <option value="CUSTOMER">Customer</option>
                        </select>
                        {designation && roleType && (
                            <p className="text-xs text-muted-foreground">
                                Auto-set from designation. You can change it.
                            </p>
                        )}
                    </div>

                    <div className="flex gap-3 pt-2">
                        <button
                            type="button"
                            onClick={onClose}
                            className="flex-1 py-2 px-4 border border-input rounded-lg text-sm hover:bg-muted transition-colors"
                        >
                            Cancel
                        </button>
                        <button
                            type="submit"
                            disabled={submitting}
                            className="flex-1 py-2 px-4 bg-primary text-primary-foreground rounded-lg text-sm hover:bg-primary/90 transition-colors disabled:opacity-50 flex items-center justify-center gap-2"
                        >
                            {submitting && <Loader2 className="w-4 h-4 animate-spin" />}
                            Create User
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
}
