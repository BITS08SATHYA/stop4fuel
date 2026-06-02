"use client";

import { useEffect, useState } from "react";
import { GlassCard } from "@/components/ui/glass-card";
import {
    getNotificationConfigs,
    saveNotificationConfig,
    getAvailableRoles,
    sendTestNotification,
    NotificationConfig,
    RoleOption,
} from "@/lib/api/station";
import { Save, AlertTriangle, Boxes, BellRing } from "lucide-react";
import { ToggleSwitch } from "@/components/ui/toggle-switch";
import { PermissionGate } from "@/components/permission-gate";
import { useAuth } from "@/lib/auth/auth-context";

interface AlertTypeDef {
    value: string;
    label: string;
    description: string;
    icon: typeof AlertTriangle;
    iconTone: string;
    requiresPermission?: string;
    extraFields?: ("lowStockThreshold" | "emailRecipients")[];
    defaultChannels?: string[];
    defaultRoles?: string[];
}

const ALERT_TYPES: AlertTypeDef[] = [
    {
        value: "LOW_STOCK",
        label: "Low Stock Alert",
        description: "Triggered when a tank's available stock drops to or below its threshold level.",
        icon: AlertTriangle,
        iconTone: "bg-amber-500/10 text-amber-500",
        defaultChannels: ["DASHBOARD"],
    },
    {
        value: "SHIFT_CLOSE_STOCK",
        label: "Shift-Close Stock Summary",
        description: "On every shift close, send today's tank levels, sales, and prices to selected roles.",
        icon: Boxes,
        iconTone: "bg-emerald-500/10 text-emerald-500",
        requiresPermission: "STOCK_NOTIFICATION_CONFIGURE",
        extraFields: ["lowStockThreshold", "emailRecipients"],
        defaultChannels: ["SSE", "PUSH", "EMAIL"],
        defaultRoles: ["OWNER", "ADMIN", "CASHIER"],
    },
];

const CHANNELS = [
    { value: "DASHBOARD", label: "Dashboard", description: "Show alerts on the operational dashboard" },
    { value: "SSE", label: "In-app toast", description: "Pop a toast for users with the app open" },
    { value: "PUSH", label: "Android push", description: "Send to registered devices via SNS → FCM" },
    { value: "EMAIL", label: "Email", description: "Send email notifications (requires AWS SES configuration)" },
    { value: "SMS", label: "SMS", description: "Send SMS notifications (requires AWS SNS configuration)" },
];

interface FormState {
    enabled: boolean;
    notifyRoles: string[];
    channels: string[];
    lowStockThreshold: string;
    emailRecipients: string;
}

const blankForm = (def: AlertTypeDef): FormState => ({
    enabled: true,
    notifyRoles: def.defaultRoles ?? [],
    channels: def.defaultChannels ?? ["DASHBOARD"],
    lowStockThreshold: "",
    emailRecipients: "",
});

export default function NotificationSettingsPage() {
    const { hasPermission } = useAuth();
    const [roles, setRoles] = useState<RoleOption[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [saving, setSaving] = useState<string | null>(null);
    const [saveSuccess, setSaveSuccess] = useState<string | null>(null);
    const [testing, setTesting] = useState<string | null>(null);
    const [testMsg, setTestMsg] = useState<{ alertType: string; text: string; ok: boolean } | null>(null);
    const [formState, setFormState] = useState<Record<string, FormState>>({});

    useEffect(() => {
        Promise.all([getNotificationConfigs(), getAvailableRoles()])
            .then(([cfgs, rls]) => {
                setRoles(rls);
                const state: Record<string, FormState> = {};
                for (const def of ALERT_TYPES) {
                    const existing = cfgs.find((c) => c.alertType === def.value);
                    const base = blankForm(def);
                    state[def.value] = existing
                        ? {
                              enabled: existing.enabled,
                              notifyRoles: existing.notifyRoles ?? base.notifyRoles,
                              channels: existing.channels?.length ? existing.channels : base.channels,
                              lowStockThreshold:
                                  existing.lowStockThreshold != null ? String(existing.lowStockThreshold) : "",
                              emailRecipients: (existing.emailRecipients ?? []).join(", "),
                          }
                        : base;
                }
                setFormState(state);
            })
            .catch(console.error)
            .finally(() => setIsLoading(false));
    }, []);

    const updateForm = (alertType: string, patch: Partial<FormState>) =>
        setFormState((prev) => ({ ...prev, [alertType]: { ...prev[alertType], ...patch } }));

    const toggleRole = (alertType: string, roleType: string) => {
        const current = formState[alertType];
        const next = current.notifyRoles.includes(roleType)
            ? current.notifyRoles.filter((r) => r !== roleType)
            : [...current.notifyRoles, roleType];
        updateForm(alertType, { notifyRoles: next });
    };

    const toggleChannel = (alertType: string, channel: string) => {
        const current = formState[alertType];
        const next = current.channels.includes(channel)
            ? current.channels.filter((c) => c !== channel)
            : [...current.channels, channel];
        updateForm(alertType, { channels: next });
    };

    const handleSave = async (def: AlertTypeDef) => {
        const state = formState[def.value];
        setSaving(def.value);
        setSaveSuccess(null);
        try {
            const payload: NotificationConfig = {
                alertType: def.value,
                enabled: state.enabled,
                notifyRoles: state.notifyRoles,
                channels: state.channels,
            };
            if (def.extraFields?.includes("lowStockThreshold")) {
                const n = parseFloat(state.lowStockThreshold);
                payload.lowStockThreshold = Number.isFinite(n) && n > 0 ? n : null;
            }
            if (def.extraFields?.includes("emailRecipients")) {
                payload.emailRecipients = state.emailRecipients
                    .split(/[,\s]+/)
                    .map((s) => s.trim())
                    .filter(Boolean);
            }
            await saveNotificationConfig(payload);
            setSaveSuccess(def.value);
            setTimeout(() => setSaveSuccess(null), 3000);
        } catch (err) {
            console.error("Failed to save notification config", err);
        } finally {
            setSaving(null);
        }
    };

    const handleTest = async (def: AlertTypeDef) => {
        setTesting(def.value);
        setTestMsg(null);
        try {
            const r = await sendTestNotification(def.value);
            let text: string;
            let ok = false;
            if (!r.pushEnabled) {
                text = "Push is disabled on the server.";
            } else if (r.devices === 0) {
                text = "No registered device for your account — log into the Android app first.";
            } else if (r.sent > 0) {
                text = `Test push sent to ${r.sent} device${r.sent > 1 ? "s" : ""}. Check your phone.`;
                ok = true;
            } else {
                text = "Could not deliver — the device endpoint was disabled (stale token).";
            }
            setTestMsg({ alertType: def.value, text, ok });
            setTimeout(() => setTestMsg(null), 7000);
        } catch (err) {
            console.error("Test push failed", err);
            setTestMsg({ alertType: def.value, text: "Test failed — see console.", ok: false });
        } finally {
            setTesting(null);
        }
    };

    if (isLoading) {
        return (
            <div className="min-h-screen bg-background p-8 flex items-center justify-center">
                <div className="w-12 h-12 border-4 border-primary/20 border-t-primary rounded-full animate-spin" />
            </div>
        );
    }

    const visible = ALERT_TYPES.filter((a) => !a.requiresPermission || hasPermission(a.requiresPermission));

    return (
        <div className="p-4 sm:p-6 lg:p-8 min-h-screen bg-background transition-colors duration-300">
            <div className="max-w-4xl mx-auto">
                <div className="mb-8">
                    <h1 className="text-4xl font-bold text-foreground tracking-tight">
                        Notification <span className="text-gradient">Settings</span>
                    </h1>
                    <p className="text-muted-foreground mt-2">
                        Configure which roles receive alerts and through which channels.
                    </p>
                </div>

                <div className="space-y-6">
                    {visible.map((def) => {
                        const state = formState[def.value];
                        if (!state) return null;
                        const Icon = def.icon;

                        return (
                            <GlassCard key={def.value}>
                                <div className="flex items-center justify-between mb-4">
                                    <div className="flex items-center gap-3">
                                        <div className={`p-2 rounded-lg ${def.iconTone}`}>
                                            <Icon className="w-5 h-5" />
                                        </div>
                                        <div>
                                            <h3 className="text-lg font-semibold text-foreground">{def.label}</h3>
                                            <p className="text-sm text-muted-foreground">{def.description}</p>
                                        </div>
                                    </div>
                                    <ToggleSwitch
                                        checked={state.enabled}
                                        onChange={() => updateForm(def.value, { enabled: !state.enabled })}
                                        label={state.enabled ? "Enabled" : "Disabled"}
                                    />
                                </div>

                                {state.enabled && (
                                    <div className="space-y-5 mt-4 pt-4 border-t border-border">
                                        <div>
                                            <label className="block text-sm font-medium text-foreground mb-3">
                                                Roles to Notify
                                            </label>
                                            <div className="flex flex-wrap gap-2">
                                                {roles.map((role) => {
                                                    const isSelected = state.notifyRoles.includes(role.roleType);
                                                    return (
                                                        <button
                                                            key={role.id}
                                                            onClick={() => toggleRole(def.value, role.roleType)}
                                                            className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors border ${
                                                                isSelected
                                                                    ? "bg-primary/10 border-primary/30 text-primary"
                                                                    : "bg-card border-border text-muted-foreground hover:bg-muted"
                                                            }`}
                                                        >
                                                            {role.roleType}
                                                        </button>
                                                    );
                                                })}
                                            </div>
                                            {state.notifyRoles.length === 0 && (
                                                <p className="text-xs text-amber-500 mt-2">
                                                    No roles selected. Select at least one role to receive notifications.
                                                </p>
                                            )}
                                        </div>

                                        <div>
                                            <label className="block text-sm font-medium text-foreground mb-3">
                                                Notification Channels
                                            </label>
                                            <div className="space-y-2">
                                                {CHANNELS.map((channel) => {
                                                    const isSelected = state.channels.includes(channel.value);
                                                    return (
                                                        <label
                                                            key={channel.value}
                                                            className={`flex items-center gap-3 p-3 rounded-lg border cursor-pointer transition-colors ${
                                                                isSelected
                                                                    ? "bg-primary/5 border-primary/30"
                                                                    : "bg-card border-border hover:bg-muted"
                                                            }`}
                                                        >
                                                            <input
                                                                type="checkbox"
                                                                checked={isSelected}
                                                                onChange={() => toggleChannel(def.value, channel.value)}
                                                                className="w-4 h-4 rounded border-border text-primary focus:ring-primary/50"
                                                            />
                                                            <div>
                                                                <span className="text-sm font-medium text-foreground">
                                                                    {channel.label}
                                                                </span>
                                                                <p className="text-xs text-muted-foreground">
                                                                    {channel.description}
                                                                </p>
                                                            </div>
                                                        </label>
                                                    );
                                                })}
                                            </div>
                                        </div>

                                        {def.extraFields?.includes("lowStockThreshold") && (
                                            <div>
                                                <label className="block text-sm font-medium text-foreground mb-2">
                                                    Low-stock threshold (optional)
                                                </label>
                                                <input
                                                    type="number"
                                                    min={0}
                                                    step="0.01"
                                                    value={state.lowStockThreshold}
                                                    onChange={(e) =>
                                                        updateForm(def.value, { lowStockThreshold: e.target.value })
                                                    }
                                                    placeholder="e.g. 500"
                                                    className="w-full sm:w-64 px-3 py-2 rounded-lg bg-card border border-border text-sm focus:outline-none focus:ring-2 focus:ring-primary/40"
                                                />
                                                <p className="text-xs text-muted-foreground mt-1">
                                                    Tanks/products at or below this value are flagged "low" in the summary.
                                                </p>
                                            </div>
                                        )}

                                        {def.extraFields?.includes("emailRecipients") && (
                                            <div>
                                                <label className="block text-sm font-medium text-foreground mb-2">
                                                    Additional email recipients (optional)
                                                </label>
                                                <textarea
                                                    rows={2}
                                                    value={state.emailRecipients}
                                                    onChange={(e) =>
                                                        updateForm(def.value, { emailRecipients: e.target.value })
                                                    }
                                                    placeholder="owner@example.com, manager@example.com"
                                                    className="w-full px-3 py-2 rounded-lg bg-card border border-border text-sm focus:outline-none focus:ring-2 focus:ring-primary/40"
                                                />
                                                <p className="text-xs text-muted-foreground mt-1">
                                                    Comma- or space-separated. These are added on top of the role-based recipients.
                                                </p>
                                            </div>
                                        )}

                                        <PermissionGate permission="SETTINGS_UPDATE">
                                            <div className="flex flex-wrap items-center gap-3 pt-2">
                                                <button
                                                    onClick={() => handleSave(def)}
                                                    disabled={saving === def.value}
                                                    className="btn-gradient px-6 py-2.5 rounded-xl font-medium flex items-center gap-2 disabled:opacity-50"
                                                >
                                                    <Save className="w-4 h-4" />
                                                    {saving === def.value ? "Saving..." : "Save Configuration"}
                                                </button>
                                                <button
                                                    type="button"
                                                    onClick={() => handleTest(def)}
                                                    disabled={testing === def.value || !state.channels.includes("PUSH")}
                                                    title={
                                                        state.channels.includes("PUSH")
                                                            ? "Send a test push to your own device"
                                                            : "Enable the Android push channel to send a test"
                                                    }
                                                    className="px-5 py-2.5 rounded-xl font-medium flex items-center gap-2 border border-border bg-card hover:bg-muted disabled:opacity-50"
                                                >
                                                    <BellRing className="w-4 h-4" />
                                                    {testing === def.value ? "Sending..." : "Send test push"}
                                                </button>
                                                {saveSuccess === def.value && (
                                                    <span className="text-sm text-green-500 font-medium">
                                                        Saved successfully!
                                                    </span>
                                                )}
                                                {testMsg?.alertType === def.value && (
                                                    <span
                                                        className={`text-sm font-medium ${
                                                            testMsg.ok ? "text-green-500" : "text-amber-500"
                                                        }`}
                                                    >
                                                        {testMsg.text}
                                                    </span>
                                                )}
                                            </div>
                                        </PermissionGate>
                                    </div>
                                )}
                            </GlassCard>
                        );
                    })}
                </div>
            </div>
        </div>
    );
}
