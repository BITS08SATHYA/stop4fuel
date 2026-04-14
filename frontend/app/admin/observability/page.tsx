"use client";

import { useCallback, useEffect, useState } from "react";
import { Activity, ExternalLink } from "lucide-react";
import { GlassCard } from "@/components/ui/glass-card";
import { RouteGuard } from "@/components/route-guard";
import { fetchWithAuth } from "@/lib/api/fetch-with-auth";

const POLL_MS = 10_000;

function getActuatorBase(): string {
    if (typeof window === "undefined") return "";
    const host = window.location.hostname;
    if (host.startsWith("devapp.")) {
        return `${window.location.protocol}//devapi.${host.slice(7)}/actuator`;
    }
    const envUrl = process.env.NEXT_PUBLIC_API_URL;
    if (envUrl) return envUrl.replace(/\/api\/?$/, "") + "/actuator";
    return `${window.location.protocol}//${host}:8080/actuator`;
}

type HealthStatus = "UP" | "DOWN" | "UNKNOWN" | "OUT_OF_SERVICE";

interface HealthResponse {
    status: HealthStatus;
    components?: Record<string, { status: HealthStatus }>;
}

interface MetricMeasurement {
    statistic: string;
    value: number;
}

interface MetricResponse {
    name: string;
    measurements: MetricMeasurement[];
    availableTags?: { tag: string; values: string[] }[];
}

async function fetchMetric(base: string, name: string): Promise<MetricResponse | null> {
    try {
        const res = await fetchWithAuth(`${base}/metrics/${encodeURIComponent(name)}`);
        if (!res.ok) return null;
        return (await res.json()) as MetricResponse;
    } catch {
        return null;
    }
}

function stat(m: MetricResponse | null, key: string): number {
    if (!m) return 0;
    return m.measurements.find((x) => x.statistic === key)?.value ?? 0;
}

function fmtMs(seconds: number): string {
    if (!seconds) return "—";
    const ms = seconds * 1000;
    return ms < 1000 ? `${ms.toFixed(0)} ms` : `${(ms / 1000).toFixed(2)} s`;
}

function fmtMB(bytes: number): string {
    if (!bytes) return "—";
    return `${(bytes / (1024 * 1024)).toFixed(0)} MB`;
}

function ObservabilityInner() {
    const [health, setHealth] = useState<HealthResponse | null>(null);
    const [metrics, setMetrics] = useState<Record<string, MetricResponse | null>>({});
    const [lastUpdated, setLastUpdated] = useState<Date | null>(null);
    const [error, setError] = useState<string | null>(null);

    const load = useCallback(async () => {
        const base = getActuatorBase();
        try {
            const healthRes = await fetchWithAuth(`${base}/health`);
            const healthJson = healthRes.ok ? ((await healthRes.json()) as HealthResponse) : null;

            const names = [
                "invoices.created",
                "payments.recorded",
                "shifts.opened",
                "shifts.closed",
                "shift.close.duration",
                "ai.request.duration",
                "jvm.memory.used",
                "jvm.threads.live",
                "jvm.gc.pause",
                "hikaricp.connections.active",
                "hikaricp.connections.idle",
                "hikaricp.connections.pending",
            ];
            const results = await Promise.all(names.map((n) => fetchMetric(base, n)));
            const map: Record<string, MetricResponse | null> = {};
            names.forEach((n, i) => (map[n] = results[i]));

            setHealth(healthJson);
            setMetrics(map);
            setLastUpdated(new Date());
            setError(null);
        } catch (e) {
            setError((e as Error).message);
        }
    }, []);

    useEffect(() => {
        load();
        const id = setInterval(load, POLL_MS);
        return () => clearInterval(id);
    }, [load]);

    const overallUp = health?.status === "UP";

    return (
        <div className="space-y-6 p-4 md:p-6">
            <div className="flex items-center justify-between flex-wrap gap-2">
                <div className="flex items-center gap-2">
                    <Activity className="w-6 h-6 text-primary" />
                    <h1 className="text-2xl font-semibold">Observability</h1>
                </div>
                <div className="text-xs text-muted-foreground">
                    {lastUpdated ? `Updated ${lastUpdated.toLocaleTimeString()}` : "Loading…"}
                    {error && <span className="text-destructive ml-2">— {error}</span>}
                </div>
            </div>

            <GlassCard>
                <div className="flex items-center justify-between">
                    <div>
                        <div className="text-sm text-muted-foreground">Service Health</div>
                        <div className={`text-2xl font-semibold ${overallUp ? "text-green-500" : "text-destructive"}`}>
                            {health?.status ?? "…"}
                        </div>
                    </div>
                    <div className="text-right text-sm space-y-0.5">
                        {health?.components &&
                            Object.entries(health.components).map(([k, v]) => (
                                <div key={k} className="flex gap-2 justify-end">
                                    <span className="text-muted-foreground">{k}:</span>
                                    <span className={v.status === "UP" ? "text-green-500" : "text-destructive"}>
                                        {v.status}
                                    </span>
                                </div>
                            ))}
                    </div>
                </div>
            </GlassCard>

            <div>
                <h2 className="text-lg font-semibold mb-3">Business Counters (cumulative since boot)</h2>
                <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                    <Counter label="Invoices Created" value={stat(metrics["invoices.created"], "COUNT")} />
                    <Counter label="Payments Recorded" value={stat(metrics["payments.recorded"], "COUNT")} />
                    <Counter label="Shifts Opened" value={stat(metrics["shifts.opened"], "COUNT")} />
                    <Counter label="Shifts Closed" value={stat(metrics["shifts.closed"], "COUNT")} />
                </div>
            </div>

            <div>
                <h2 className="text-lg font-semibold mb-3">Latency</h2>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <GlassCard>
                        <div className="text-sm text-muted-foreground">Shift close duration</div>
                        <div className="flex gap-6 mt-2">
                            <Stat label="count" value={stat(metrics["shift.close.duration"], "COUNT").toFixed(0)} />
                            <Stat label="total" value={fmtMs(stat(metrics["shift.close.duration"], "TOTAL_TIME"))} />
                            <Stat label="max" value={fmtMs(stat(metrics["shift.close.duration"], "MAX"))} />
                        </div>
                    </GlassCard>
                    <GlassCard>
                        <div className="text-sm text-muted-foreground">AI request duration</div>
                        <div className="flex gap-6 mt-2">
                            <Stat label="count" value={stat(metrics["ai.request.duration"], "COUNT").toFixed(0)} />
                            <Stat label="total" value={fmtMs(stat(metrics["ai.request.duration"], "TOTAL_TIME"))} />
                            <Stat label="max" value={fmtMs(stat(metrics["ai.request.duration"], "MAX"))} />
                        </div>
                    </GlassCard>
                </div>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <GlassCard>
                    <div className="text-sm text-muted-foreground mb-2">JVM</div>
                    <div className="grid grid-cols-3 gap-3">
                        <Stat label="heap used" value={fmtMB(stat(metrics["jvm.memory.used"], "VALUE"))} />
                        <Stat label="threads" value={stat(metrics["jvm.threads.live"], "VALUE").toFixed(0)} />
                        <Stat label="gc max" value={fmtMs(stat(metrics["jvm.gc.pause"], "MAX"))} />
                    </div>
                </GlassCard>
                <GlassCard>
                    <div className="text-sm text-muted-foreground mb-2">DB Pool (HikariCP)</div>
                    <div className="grid grid-cols-3 gap-3">
                        <Stat label="active" value={stat(metrics["hikaricp.connections.active"], "VALUE").toFixed(0)} />
                        <Stat label="idle" value={stat(metrics["hikaricp.connections.idle"], "VALUE").toFixed(0)} />
                        <Stat label="pending" value={stat(metrics["hikaricp.connections.pending"], "VALUE").toFixed(0)} />
                    </div>
                </GlassCard>
            </div>

            <GlassCard>
                <a
                    href="https://ap-south-1.console.aws.amazon.com/cloudwatch/home?region=ap-south-1#dashboards:"
                    target="_blank"
                    rel="noreferrer"
                    className="flex items-center gap-2 text-primary hover:underline"
                >
                    <ExternalLink className="w-4 h-4" />
                    Open CloudWatch dashboard (prod history, alarms, SNS)
                </a>
            </GlassCard>
        </div>
    );
}

function Counter({ label, value }: { label: string; value: number }) {
    return (
        <GlassCard>
            <div className="text-sm text-muted-foreground">{label}</div>
            <div className="text-3xl font-semibold mt-1">{value.toLocaleString()}</div>
        </GlassCard>
    );
}

function Stat({ label, value }: { label: string; value: string }) {
    return (
        <div>
            <div className="text-xs text-muted-foreground">{label}</div>
            <div className="text-lg font-semibold">{value}</div>
        </div>
    );
}

export default function ObservabilityPage() {
    return (
        <RouteGuard permission="admin.observability">
            <ObservabilityInner />
        </RouteGuard>
    );
}
