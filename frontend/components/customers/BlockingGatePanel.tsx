"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import {
    ShieldCheck, ShieldAlert, ShieldOff,
    IndianRupee, Droplet, Clock, Car, Gauge,
    CheckCircle2, AlertTriangle, XCircle, MinusCircle,
    Loader2,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { getBlockingStatus, type BlockingStatus, type BlockingGate, type GateState } from "@/lib/api/station/customers";

interface BlockingGatePanelProps {
    customerId: number;
    vehicleId?: number;
    invoiceAmount?: number;
    invoiceLiters?: number;
    variant?: "inline" | "section" | "modal";
    onForceUnblockClick?: () => void;
    className?: string;
    // Bump to force a refetch after actions that change the server-side state
    // but don't change any of the other props (e.g. toggling force-unblock).
    refreshKey?: number | string;
}

const GATE_ICONS: Record<string, typeof ShieldCheck> = {
    CUSTOMER_STATUS: ShieldCheck,
    CREDIT_AMOUNT: IndianRupee,
    CREDIT_LITERS: Droplet,
    AGING: Clock,
    VEHICLE_STATUS: Car,
    VEHICLE_MONTHLY_LITERS: Gauge,
};

const STATE_META: Record<GateState, { label: string; chip: string; bar: string; text: string; Icon: typeof CheckCircle2 }> = {
    PASS: { label: "PASS", chip: "bg-emerald-500/15 text-emerald-500 border-emerald-500/30", bar: "bg-emerald-500", text: "text-emerald-500", Icon: CheckCircle2 },
    WARN: { label: "WARN", chip: "bg-amber-500/15 text-amber-500 border-amber-500/30", bar: "bg-amber-500", text: "text-amber-500", Icon: AlertTriangle },
    FAIL: { label: "BLOCKED", chip: "bg-rose-500/15 text-rose-500 border-rose-500/30", bar: "bg-rose-500", text: "text-rose-500", Icon: XCircle },
    SKIPPED: { label: "—", chip: "bg-muted/40 text-muted-foreground border-border", bar: "bg-muted-foreground/30", text: "text-muted-foreground", Icon: MinusCircle },
};

const OVERALL_META: Record<BlockingStatus["overall"], { label: string; classes: string; Icon: typeof ShieldCheck }> = {
    PASS: { label: "All checks passing", classes: "bg-emerald-500/15 text-emerald-500 border-emerald-500/30", Icon: ShieldCheck },
    WARN: { label: "Approaching a threshold", classes: "bg-amber-500/15 text-amber-500 border-amber-500/30", Icon: ShieldAlert },
    BLOCKED: { label: "Invoice will be blocked", classes: "bg-rose-500/15 text-rose-500 border-rose-500/30", Icon: ShieldOff },
    OVERRIDE: { label: "Force-unblock override active", classes: "bg-indigo-500/15 text-indigo-400 border-indigo-500/30", Icon: ShieldCheck },
};

function formatValue(v: number | string | null): string {
    if (v === null || v === undefined) return "—";
    if (typeof v === "string") return v;
    if (Number.isInteger(v)) return String(v);
    return v.toFixed(2);
}

function GateCard({ gate, compact }: { gate: BlockingGate; compact: boolean }) {
    const meta = STATE_META[gate.state];
    const GateIcon = GATE_ICONS[gate.key] ?? ShieldCheck;
    const pct = gate.progressPercent != null ? Math.min(gate.progressPercent, 150) : null;
    const barClass = gate.state === "FAIL" ? "bg-rose-500"
        : gate.state === "WARN" ? "bg-amber-500"
            : gate.state === "PASS" ? "bg-emerald-500"
                : "bg-muted-foreground/30";

    return (
        <div className={cn(
            "rounded-xl border border-border/70 bg-card/40 flex flex-col gap-2",
            compact ? "p-3" : "p-4"
        )}>
            <div className="flex items-center justify-between gap-2">
                <div className="flex items-center gap-2 min-w-0">
                    <GateIcon className="w-4 h-4 text-muted-foreground shrink-0" />
                    <span className="text-xs font-medium text-muted-foreground truncate">{gate.label}</span>
                </div>
                <span className={cn(
                    "inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-[10px] font-semibold",
                    meta.chip
                )}>
                    <meta.Icon className="w-3 h-3" />
                    {meta.label}
                </span>
            </div>

            <div className="text-sm font-semibold text-foreground">
                {gate.state === "SKIPPED" ? (
                    <span className="text-muted-foreground">Not applicable</span>
                ) : (
                    <>
                        <span>{formatValue(gate.value)}</span>
                        {gate.limit !== null && gate.limit !== undefined && (
                            <span className="text-muted-foreground font-normal"> / {formatValue(gate.limit)}</span>
                        )}
                    </>
                )}
            </div>

            {pct !== null && gate.state !== "SKIPPED" && (
                <div className="h-1.5 w-full rounded-full bg-muted/50 overflow-hidden">
                    <div
                        className={cn("h-full transition-all", barClass)}
                        style={{ width: `${Math.min(pct, 100)}%` }}
                    />
                </div>
            )}

            <p className="text-[11px] leading-tight text-muted-foreground">{gate.detail}</p>
        </div>
    );
}

export function BlockingGatePanel({
    customerId,
    vehicleId,
    invoiceAmount,
    invoiceLiters,
    variant = "section",
    onForceUnblockClick,
    className,
    refreshKey,
}: BlockingGatePanelProps) {
    const [status, setStatus] = useState<BlockingStatus | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

    // Round invoice params so a stream of keystrokes doesn't hammer the endpoint with sub-paisa deltas
    const roundedAmount = useMemo(
        () => invoiceAmount != null ? Math.round(invoiceAmount * 100) / 100 : undefined,
        [invoiceAmount]
    );
    const roundedLiters = useMemo(
        () => invoiceLiters != null ? Math.round(invoiceLiters * 100) / 100 : undefined,
        [invoiceLiters]
    );

    useEffect(() => {
        if (!customerId) return;
        if (debounceRef.current) clearTimeout(debounceRef.current);
        const opts = { vehicleId, invoiceAmount: roundedAmount, invoiceLiters: roundedLiters };
        debounceRef.current = setTimeout(async () => {
            setLoading(true);
            setError(null);
            try {
                const s = await getBlockingStatus(customerId, opts);
                setStatus(s);
            } catch (e: unknown) {
                setError(e instanceof Error ? e.message : "Failed to load blocking status");
            } finally {
                setLoading(false);
            }
        }, 250);
        return () => {
            if (debounceRef.current) clearTimeout(debounceRef.current);
        };
    }, [customerId, vehicleId, roundedAmount, roundedLiters, refreshKey]);

    if (!customerId) return null;

    const compact = variant === "inline";
    const containerClass = variant === "modal"
        ? "w-full"
        : cn(
            "glass-card rounded-2xl transition-all duration-300",
            compact ? "p-3 sm:p-4" : "p-4 sm:p-5 md:p-6"
        );

    if (!status && loading) {
        return (
            <div className={cn(containerClass, className)}>
                <div className="flex items-center gap-2 text-sm text-muted-foreground">
                    <Loader2 className="w-4 h-4 animate-spin" />
                    Checking blocking status…
                </div>
            </div>
        );
    }

    if (error && !status) {
        return (
            <div className={cn(containerClass, "border border-rose-500/30", className)}>
                <div className="text-sm text-rose-500">Could not load blocking status: {error}</div>
            </div>
        );
    }

    if (!status) return null;

    const overall = OVERALL_META[status.overall];

    return (
        <div className={cn(containerClass, className)}>
            {/* Header */}
            <div className="flex items-start justify-between gap-3 mb-3 flex-wrap">
                <div className="min-w-0">
                    <h3 className={cn("font-semibold text-foreground", compact ? "text-sm" : "text-base")}>
                        Why <span className="text-primary">{status.customerName}</span>
                        {" "}{status.overall === "PASS" ? "can take" : "cannot take"} a credit invoice
                    </h3>
                    {loading && (
                        <span className="inline-flex items-center gap-1 text-[11px] text-muted-foreground mt-0.5">
                            <Loader2 className="w-3 h-3 animate-spin" /> updating…
                        </span>
                    )}
                </div>
                <span className={cn(
                    "inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-semibold",
                    overall.classes
                )}>
                    <overall.Icon className="w-3.5 h-3.5" />
                    {overall.label}
                </span>
            </div>

            {/* Force-unblock banner */}
            {status.forceUnblocked && (
                <div className="mb-3 rounded-lg border border-indigo-500/30 bg-indigo-500/10 px-3 py-2 text-xs text-indigo-400">
                    <strong>Force-Unblock ON.</strong> Owner has bypassed all credit checks. The invoice
                    will go through even if gates below say FAIL.
                </div>
            )}

            {/* Gate grid */}
            <div className={cn(
                "grid gap-2",
                compact
                    ? "grid-cols-2 md:grid-cols-3 lg:grid-cols-6"
                    : "grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6"
            )}>
                {status.gates.map((g) => (
                    <GateCard key={g.key} gate={g} compact={compact} />
                ))}
            </div>

            {/* Footer: reason + action */}
            <div className="mt-3 rounded-lg bg-muted/30 px-3 py-2">
                <p className={cn(
                    "font-semibold",
                    compact ? "text-xs" : "text-sm",
                    status.overall === "BLOCKED" ? "text-rose-500"
                        : status.overall === "WARN" ? "text-amber-500"
                            : status.overall === "OVERRIDE" ? "text-indigo-400"
                                : "text-emerald-500"
                )}>
                    {status.primaryReason}
                </p>
                <p className="text-[11px] text-muted-foreground mt-0.5">
                    Action: {status.suggestedAction}
                </p>
                {onForceUnblockClick && status.overall === "BLOCKED" && !status.forceUnblocked && (
                    <button
                        onClick={onForceUnblockClick}
                        className="mt-2 inline-flex items-center gap-1 rounded-md border border-indigo-500/40 bg-indigo-500/10 px-2.5 py-1 text-[11px] font-medium text-indigo-400 hover:bg-indigo-500/20 transition-colors"
                    >
                        Owner: Force Unblock…
                    </button>
                )}
            </div>
        </div>
    );
}
