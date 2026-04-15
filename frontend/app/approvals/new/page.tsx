"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { GlassCard } from "@/components/ui/glass-card";
import { PermissionGate } from "@/components/permission-gate";
import { CustomerAutocomplete } from "@/components/ui/customer-autocomplete";
import { showToast } from "@/components/ui/toast";
import { Loader2, Truck, ShieldOff, TrendingUp, Send } from "lucide-react";
import {
    submitApprovalRequest,
    type ApprovalRequestType,
} from "@/lib/api/station";

type BuilderType = Exclude<ApprovalRequestType, "RECORD_STATEMENT_PAYMENT" | "RECORD_INVOICE_PAYMENT">;

const TABS: { type: BuilderType; label: string; icon: React.ComponentType<{ className?: string }>; hint: string }[] = [
    { type: "ADD_VEHICLE",        label: "Add Vehicle",        icon: Truck,      hint: "Request admin to register a new vehicle under a customer" },
    { type: "UNBLOCK_CUSTOMER",   label: "Unblock Customer",   icon: ShieldOff,  hint: "Ask admin to reactivate a blocked customer" },
    { type: "RAISE_CREDIT_LIMIT", label: "Raise Credit Limit", icon: TrendingUp, hint: "Propose a higher credit limit (amount and/or liters)" },
];

function NewRequestInner() {
    const router = useRouter();
    const [type, setType] = useState<BuilderType>("ADD_VEHICLE");
    const [customerId, setCustomerId] = useState<string | number>("");
    const [note, setNote] = useState("");
    const [submitting, setSubmitting] = useState(false);

    // ADD_VEHICLE
    const [vehicleNumber, setVehicleNumber] = useState("");
    const [maxCapacity, setMaxCapacity] = useState("");
    const [maxLitersPerMonth, setMaxLitersPerMonth] = useState("");

    // UNBLOCK_CUSTOMER
    const [unblockReason, setUnblockReason] = useState("");

    // RAISE_CREDIT_LIMIT
    const [creditLimitAmount, setCreditLimitAmount] = useState("");
    const [creditLimitLiters, setCreditLimitLiters] = useState("");

    const resetForm = () => {
        setCustomerId("");
        setNote("");
        setVehicleNumber(""); setMaxCapacity(""); setMaxLitersPerMonth("");
        setUnblockReason("");
        setCreditLimitAmount(""); setCreditLimitLiters("");
    };

    const buildPayload = (): Record<string, unknown> | string => {
        switch (type) {
            case "ADD_VEHICLE": {
                if (!vehicleNumber.trim()) return "Vehicle number is required";
                return {
                    vehicleNumber: vehicleNumber.trim(),
                    maxCapacity: maxCapacity ? Number(maxCapacity) : null,
                    maxLitersPerMonth: maxLitersPerMonth ? Number(maxLitersPerMonth) : null,
                };
            }
            case "UNBLOCK_CUSTOMER":
                return { reason: unblockReason.trim() };
            case "RAISE_CREDIT_LIMIT": {
                if (!creditLimitAmount && !creditLimitLiters) return "Enter at least one of amount or liters";
                const p: Record<string, unknown> = {};
                if (creditLimitAmount) p.creditLimitAmount = Number(creditLimitAmount);
                if (creditLimitLiters) p.creditLimitLiters = Number(creditLimitLiters);
                return p;
            }
        }
    };

    const onSubmit = async () => {
        if (!customerId) {
            showToast.error("Please select a customer");
            return;
        }
        const payload = buildPayload();
        if (typeof payload === "string") {
            showToast.error(payload);
            return;
        }
        setSubmitting(true);
        try {
            await submitApprovalRequest({
                requestType: type,
                customerId: Number(customerId),
                payload,
                note: note.trim() || undefined,
            });
            showToast.success("Request submitted — admin will review it shortly");
            resetForm();
            router.push("/approvals/mine");
        } catch (e: unknown) {
            const msg = e instanceof Error ? e.message : "Submit failed";
            showToast.error(msg);
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <div className="p-6 space-y-4 max-w-3xl">
            <div>
                <h1 className="text-2xl font-bold">New Request</h1>
                <p className="text-sm text-muted-foreground">Submit a request for admin approval</p>
            </div>

            {/* Type tabs */}
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-2">
                {TABS.map(tab => {
                    const Icon = tab.icon;
                    const active = tab.type === type;
                    return (
                        <button
                            key={tab.type}
                            onClick={() => setType(tab.type)}
                            className={"text-left p-3 rounded-lg border transition-colors " +
                                (active
                                    ? "border-orange-500 bg-orange-500/10 text-orange-400"
                                    : "border-border hover:bg-muted/50")}
                        >
                            <div className="flex items-center gap-2 font-medium text-sm">
                                <Icon className="w-4 h-4" /> {tab.label}
                            </div>
                            <div className="mt-1 text-xs text-muted-foreground">{tab.hint}</div>
                        </button>
                    );
                })}
            </div>

            <GlassCard className="p-4 space-y-4">
                <div>
                    <label className="text-xs text-muted-foreground">Customer *</label>
                    <div className="mt-1">
                        <CustomerAutocomplete value={customerId} onChange={(id) => setCustomerId(id)} />
                    </div>
                </div>

                {type === "ADD_VEHICLE" && (
                    <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                        <Field label="Vehicle number *" value={vehicleNumber} onChange={setVehicleNumber} placeholder="e.g. TN 28 AA 1234" />
                        <Field label="Max capacity (L)" value={maxCapacity} onChange={setMaxCapacity} placeholder="optional" type="number" />
                        <Field label="Max liters / month" value={maxLitersPerMonth} onChange={setMaxLitersPerMonth} placeholder="optional" type="number" />
                    </div>
                )}

                {type === "UNBLOCK_CUSTOMER" && (
                    <Field label="Reason (optional)" value={unblockReason} onChange={setUnblockReason} placeholder="e.g. Customer paid outstanding dues" />
                )}

                {type === "RAISE_CREDIT_LIMIT" && (
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                        <Field label="New credit limit (₹)" value={creditLimitAmount} onChange={setCreditLimitAmount} type="number" placeholder="e.g. 50000" />
                        <Field label="New credit limit (liters)" value={creditLimitLiters} onChange={setCreditLimitLiters} type="number" placeholder="optional" />
                    </div>
                )}

                <div>
                    <label className="text-xs text-muted-foreground">Note to admin (optional)</label>
                    <textarea
                        value={note}
                        onChange={e => setNote(e.target.value)}
                        rows={3}
                        placeholder="Add any context that will help admin approve quickly"
                        className="mt-1 w-full px-3 py-2 rounded-md bg-background border border-border text-sm"
                    />
                </div>

                <div className="flex items-center gap-2 pt-1">
                    <button
                        onClick={onSubmit}
                        disabled={submitting}
                        className="inline-flex items-center gap-2 px-4 py-2 rounded-md bg-orange-500 text-white text-sm font-medium disabled:opacity-50"
                    >
                        {submitting ? <Loader2 className="w-4 h-4 animate-spin" /> : <Send className="w-4 h-4" />}
                        Submit request
                    </button>
                    <button
                        onClick={resetForm}
                        disabled={submitting}
                        className="px-4 py-2 rounded-md border border-border text-sm disabled:opacity-50"
                    >Reset</button>
                </div>
            </GlassCard>
        </div>
    );
}

function Field({ label, value, onChange, placeholder, type = "text" }: {
    label: string;
    value: string;
    onChange: (v: string) => void;
    placeholder?: string;
    type?: string;
}) {
    return (
        <div>
            <label className="text-xs text-muted-foreground">{label}</label>
            <input
                type={type}
                value={value}
                onChange={e => onChange(e.target.value)}
                placeholder={placeholder}
                className="mt-1 w-full px-3 py-2 rounded-md bg-background border border-border text-sm"
            />
        </div>
    );
}

export default function NewRequestPage() {
    return (
        <PermissionGate permission="APPROVAL_REQUEST_CREATE">
            <NewRequestInner />
        </PermissionGate>
    );
}
