"use client";

import { useEffect, useState } from "react";
import { GlassCard } from "@/components/ui/glass-card";
import {
    getNotificationConfigs,
    saveNotificationConfig,
    getAvailableRoles,
    NotificationConfig,
    RoleOption,
} from "@/lib/api/station";
import { Bell, Save, AlertTriangle } from "lucide-react";
import { ToggleSwitch } from "@/components/ui/toggle-switch";
import { PermissionGate } from "@/components/permission-gate";

const ALERT_TYPES = [
    { value: "LOW_STOCK", label: "Low Stock Alert", description: "Triggered when a tank's available stock drops to or below its threshold level." },
];

const CHANNELS = [
    { value: "DASHBOARD", label: "Dashboard", description: "Show alerts on the operational dashboard" },
    { value: "EMAIL", label: "Email", description: "Send email notifications (requires AWS SES configuration)" },
    { value: "SMS", label: "SMS", description: "Send SMS notifications (requires AWS SNS configuration)" },
];

export default function NotificationSettingsPage() {
    const [configs, setConfigs] = useState<NotificationConfig[]>([]);
    const [roles, setRoles] = useState<RoleOption[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [saving, setSaving] = useState<string | null>(null);
    const [saveSuccess, setSaveSuccess] = useState<string | null>(null);

    // Local form state per alert type
    const [formState, setFormState] = useState<Record<string, { enabled: boolean; notifyRoles: string[]; channels: string[] }>>({});

    useEffect(() => {
        Promise.all([getNotificationConfigs(), getAvailableRoles()])
            .then(([cfgs, rls]) => {
                setConfigs(cfgs);
                setRoles(rls);

                // Build initial form state
                const state: Record<string, { enabled: boolean; notifyRoles: string[]; channels: string[] }> = {};
                for (const alertType of ALERT_TYPES) {
                    const existing = cfgs.find((c) => c.alertType === alertType.value);
                    state[alertType.value] = {
                        enabled: existing?.enabled ?? true,
                        notifyRoles: existing?.notifyRoles ?? [],
                        channels: existing?.channels ?? ["DASHBOARD"],
                    };
                }
                setFormState(state);
            })
            .catch(console.error)
            .finally(() => setIsLoading(false));
    }, []);

    const toggleRole = (alertType: string, roleType: string) => {
        setFormState((prev) => {
            const current = prev[alertType];
            const roles = current.notifyRoles.includes(roleType)
                ? current.notifyRoles.filter((r) => r !== roleType)
                : [...current.notifyRoles, roleType];
            return { ...prev, [alertType]: { ...current, notifyRoles: roles } };
        });
    };

    const toggleChannel = (alertType: string, channel: string) => {
        setFormState((prev) => {
            const current = prev[alertType];
            const channels = current.channels.includes(channel)
                ? current.channels.filter((c) => c !== channel)
                : [...current.channels, channel];
            return { ...prev, [alertType]: { ...current, channels } };
        });
    };

    const toggleEnabled = (alertType: string) => {
        setFormState((prev) => ({
            ...prev,
            [alertType]: { ...prev[alertType], enabled: !prev[alertType].enabled },
        }));
    };

    const handleSave = async (alertType: string) => {
        setSaving(alertType);
        setSaveSuccess(null);
        try {
            const state = formState[alertType];
            await saveNotificationConfig({
                alertType,
                enabled: state.enabled,
                notifyRoles: state.notifyRoles,
                channels: state.channels,
            });
            setSaveSuccess(alertType);
            setTimeout(() => setSaveSuccess(null), 3000);
        } catch (err) {
            console.error("Failed to save notification config", err);
        } finally {
            setSaving(null);
        }
    };

    if (isLoading) {
        return (
            <div className="min-h-screen bg-background p-8 flex items-center justify-center">
                <div className="w-12 h-12 border-4 border-primary/20 border-t-primary rounded-full animate-spin" />
            </div>
        );
    }

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
                    {ALERT_TYPES.map((alertType) => {
                        const state = formState[alertType.value];
                        if (!state) return null;

                        return (
                            <GlassCard key={alertType.value}>
                                <div className="flex items-center justify-between mb-4">
                                    <div className="flex items-center gap-3">
                                        <div className="p-2 rounded-lg bg-amber-500/10">
                                            <AlertTriangle className="w-5 h-5 text-amber-500" />
                                        </div>
                                        <div>
                                            <h3 className="text-lg font-semibold text-foreground">{alertType.label}</h3>
                                            <p className="text-sm text-muted-foreground">{alertType.description}</p>
                                        </div>
                                    </div>
                                    <ToggleSwitch
                                        checked={state.enabled}
                                        onChange={() => toggleEnabled(alertType.value)}
                                        label={state.enabled ? "Enabled" : "Disabled"}
                                    />
                                </div>

                                {state.enabled && (
                                    <div className="space-y-5 mt-4 pt-4 border-t border-border">
                                        {/* Roles to notify */}
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
                                                            onClick={() => toggleRole(alertType.value, role.roleType)}
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

                                        {/* Notification channels */}
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
                                                                onChange={() => toggleChannel(alertType.value, channel.value)}
                                                                className="w-4 h-4 rounded border-border text-primary focus:ring-primary/50"
                                                            />
                                                            <div>
                                                                <span className="text-sm font-medium text-foreground">{channel.label}</span>
                                                                <p className="text-xs text-muted-foreground">{channel.description}</p>
                                                            </div>
                                                        </label>
                                                    );
                                                })}
                                            </div>
                                        </div>

                                        {/* Save button */}
                                        <PermissionGate permission="SETTINGS_UPDATE">
                                            <div className="flex items-center gap-3 pt-2">
                                                <button
                                                    onClick={() => handleSave(alertType.value)}
                                                    disabled={saving === alertType.value}
                                                    className="btn-gradient px-6 py-2.5 rounded-xl font-medium flex items-center gap-2 disabled:opacity-50"
                                                >
                                                    <Save className="w-4 h-4" />
                                                    {saving === alertType.value ? "Saving..." : "Save Configuration"}
                                                </button>
                                                {saveSuccess === alertType.value && (
                                                    <span className="text-sm text-green-500 font-medium">Saved successfully!</span>
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
