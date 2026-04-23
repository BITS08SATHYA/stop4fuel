"use client";

import { useCallback, useEffect, useState } from "react";
import { GlassCard } from "@/components/ui/glass-card";
import { PermissionGate } from "@/components/permission-gate";
import { showToast } from "@/components/ui/toast";
import {
    Loader2, RefreshCw, Clock, CheckCircle2, XCircle,
    Truck, ShieldOff, TrendingUp, Gauge, Receipt, FileText,
} from "lucide-react";
import {
    listMyApprovals,
    parseApprovalPayload,
    type ApprovalRequest,
    type ApprovalRequestStatus,
    type ApprovalRequestType,
} from "@/lib/api/station";

const TYPE_LABEL: Record<ApprovalRequestType, string> = {
    ADD_VEHICLE: "Add Vehicle",
    UNBLOCK_CUSTOMER: "Unblock Customer",
    RAISE_CREDIT_LIMIT: "Raise Credit Limit",
    RAISE_VEHICLE_LIMIT: "Raise Vehicle Limit",
    RECORD_STATEMENT_PAYMENT: "Statement Payment",
    RECORD_INVOICE_PAYMENT: "Invoice Payment",
};

const TYPE_ICON: Record<ApprovalRequestType, React.ComponentType<{ className?: string }>> = {
    ADD_VEHICLE: Truck,
    UNBLOCK_CUSTOMER: ShieldOff,
    RAISE_CREDIT_LIMIT: TrendingUp,
    RAISE_VEHICLE_LIMIT: Gauge,
    RECORD_STATEMENT_PAYMENT: Receipt,
    RECORD_INVOICE_PAYMENT: FileText,
};

const STATUS_STYLE: Record<ApprovalRequestStatus, { tone: string; icon: React.ComponentType<{ className?: string }> }> = {
    PENDING:  { tone: "bg-amber-500/15 text-amber-400",  icon: Clock },
    APPROVED: { tone: "bg-green-500/15 text-green-400",  icon: CheckCircle2 },
    REJECTED: { tone: "bg-red-500/15 text-red-400",      icon: XCircle },
};

function formatDateTime(dateStr?: string | null) {
    if (!dateStr) return "-";
    const d = new Date(dateStr);
    return d.toLocaleString("en-IN", {
        day: "2-digit", month: "short", year: "numeric",
        hour: "2-digit", minute: "2-digit",
    });
}

function MyApprovalsInner() {
    const [requests, setRequests] = useState<ApprovalRequest[]>([]);
    const [loading, setLoading] = useState(true);

    const load = useCallback(async () => {
        setLoading(true);
        try {
            const data = await listMyApprovals();
            setRequests(data);
        } catch (e: unknown) {
            const msg = e instanceof Error ? e.message : "Failed to load requests";
            showToast.error(msg);
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => { load(); }, [load]);

    return (
        <div className="p-6 space-y-4">
            <div className="flex items-center justify-between">
                <div>
                    <h1 className="text-2xl font-bold">My Requests</h1>
                    <p className="text-sm text-muted-foreground">Requests you have submitted and their status</p>
                </div>
                <button
                    onClick={load}
                    className="inline-flex items-center gap-2 px-3 py-1.5 text-sm rounded-md border border-border hover:bg-muted transition-colors"
                >
                    <RefreshCw className="w-4 h-4" /> Refresh
                </button>
            </div>

            <GlassCard className="p-0 overflow-hidden">
                {loading && (
                    <div className="p-8 flex items-center justify-center text-muted-foreground">
                        <Loader2 className="w-4 h-4 animate-spin mr-2" /> Loading…
                    </div>
                )}
                {!loading && requests.length === 0 && (
                    <div className="p-10 text-center text-muted-foreground text-sm">You haven&apos;t submitted any requests yet</div>
                )}
                {!loading && requests.length > 0 && (
                    <div className="divide-y divide-border">
                        {requests.map(r => {
                            const Icon = TYPE_ICON[r.requestType];
                            const statusMeta = STATUS_STYLE[r.status];
                            const StatusIcon = statusMeta.icon;
                            const payload = parseApprovalPayload(r);
                            const kv = Object.entries(payload);
                            return (
                                <div key={r.id} className="p-4">
                                    <div className="flex items-center gap-2">
                                        <Icon className="w-4 h-4 text-muted-foreground" />
                                        <span className="font-medium text-sm">{TYPE_LABEL[r.requestType]}</span>
                                        <span className="text-xs text-muted-foreground">#{r.id}</span>
                                        <span className={"ml-auto inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-medium " + statusMeta.tone}>
                                            <StatusIcon className="w-3 h-3" /> {r.status}
                                        </span>
                                    </div>
                                    <div className="mt-1 text-xs text-muted-foreground">Submitted {formatDateTime(r.createdAt)}</div>

                                    {kv.length > 0 && (
                                        <div className="mt-2 text-xs text-muted-foreground flex flex-wrap gap-x-3 gap-y-1">
                                            {kv.map(([k, v]) => (
                                                <span key={k}><span className="opacity-60">{k}:</span> {v === null || v === undefined ? "-" : String(v)}</span>
                                            ))}
                                        </div>
                                    )}

                                    {r.requestNote && (
                                        <div className="mt-2 text-xs">
                                            <span className="text-muted-foreground">Your note: </span>
                                            <span>{r.requestNote}</span>
                                        </div>
                                    )}

                                    {r.reviewNote && (
                                        <div className="mt-2 p-2 rounded bg-muted/40 text-sm">
                                            <span className="text-xs text-muted-foreground">Admin remark:</span>
                                            <div>{r.reviewNote}</div>
                                            {r.reviewedAt && (
                                                <div className="text-xs text-muted-foreground mt-0.5">at {formatDateTime(r.reviewedAt)}</div>
                                            )}
                                        </div>
                                    )}
                                </div>
                            );
                        })}
                    </div>
                )}
            </GlassCard>
        </div>
    );
}

export default function MyApprovalsPage() {
    return (
        <PermissionGate permission="APPROVAL_REQUEST_CREATE">
            <MyApprovalsInner />
        </PermissionGate>
    );
}
