"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { GlassCard } from "@/components/ui/glass-card";
import { Badge } from "@/components/ui/badge";
import { Modal } from "@/components/ui/modal";
import { showToast } from "@/components/ui/toast";
import { PermissionGate } from "@/components/permission-gate";
import {
    CheckCircle2, XCircle, Clock, Loader2, RefreshCw,
    Truck, ShieldOff, TrendingUp, Gauge, Receipt, FileText,
} from "lucide-react";
import {
    listPendingApprovals,
    approveApprovalRequest,
    rejectApprovalRequest,
    type ApprovalRequest,
    type ApprovalRequestType,
} from "@/lib/api/station";

const TYPE_META: Record<ApprovalRequestType, { label: string; icon: React.ComponentType<{ className?: string }>; tone: string }> = {
    ADD_VEHICLE:              { label: "Add Vehicle",           icon: Truck,      tone: "bg-blue-500/15 text-blue-400" },
    UNBLOCK_CUSTOMER:         { label: "Unblock Customer",      icon: ShieldOff,  tone: "bg-green-500/15 text-green-400" },
    RAISE_CREDIT_LIMIT:       { label: "Raise Credit Limit",    icon: TrendingUp, tone: "bg-amber-500/15 text-amber-400" },
    RAISE_VEHICLE_LIMIT:      { label: "Raise Vehicle Limit",   icon: Gauge,      tone: "bg-indigo-500/15 text-indigo-400" },
    RECORD_STATEMENT_PAYMENT: { label: "Statement Payment",     icon: Receipt,    tone: "bg-purple-500/15 text-purple-400" },
    RECORD_INVOICE_PAYMENT:   { label: "Invoice Payment",       icon: FileText,   tone: "bg-cyan-500/15 text-cyan-400" },
};

function formatDateTime(dateStr?: string | null) {
    if (!dateStr) return "-";
    const d = new Date(dateStr);
    return d.toLocaleString("en-IN", {
        day: "2-digit", month: "short", year: "numeric",
        hour: "2-digit", minute: "2-digit",
    });
}

function formatRupee(n?: number | null) {
    if (n == null) return "-";
    return "\u20B9" + Number(n).toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function formatLitres(n?: number | null) {
    if (n == null) return "-";
    return Number(n).toLocaleString("en-IN", { maximumFractionDigits: 2 }) + " L";
}

/** One-line subtitle for the Pending list card — friendly identifier, customer, amount. */
function listSubtitle(req: ApprovalRequest): string | null {
    switch (req.requestType) {
        case "RECORD_INVOICE_PAYMENT": {
            const parts = [req.billNo, req.customerName, formatRupee(req.amount)].filter(Boolean) as string[];
            return parts.join(" \u00B7 ") || null;
        }
        case "RECORD_STATEMENT_PAYMENT": {
            const parts = [req.statementNo, req.customerName, formatRupee(req.amount)].filter(Boolean) as string[];
            return parts.join(" \u00B7 ") || null;
        }
        case "ADD_VEHICLE": {
            const parts = [req.vehicleNumber, req.customerName].filter(Boolean) as string[];
            return parts.join(" \u00B7 ") || null;
        }
        case "UNBLOCK_CUSTOMER":
            return req.customerName ?? null;
        case "RAISE_CREDIT_LIMIT": {
            const bits: string[] = [];
            if (req.customerName) bits.push(req.customerName);
            if (req.requestedCreditLimitAmount != null) {
                bits.push(`${formatRupee(req.currentCreditLimitAmount ?? 0)} \u2192 ${formatRupee(req.requestedCreditLimitAmount)}`);
            } else if (req.requestedCreditLimitLiters != null) {
                bits.push(`${formatLitres(req.currentCreditLimitLiters ?? 0)} \u2192 ${formatLitres(req.requestedCreditLimitLiters)}`);
            }
            return bits.join(" \u00B7 ") || null;
        }
        case "RAISE_VEHICLE_LIMIT": {
            const bits: string[] = [];
            if (req.vehicleNumber) bits.push(req.vehicleNumber);
            if (req.customerName) bits.push(req.customerName);
            if (req.requestedMaxLitersPerMonth != null) {
                bits.push(`${formatLitres(req.currentMaxLitersPerMonth ?? 0)}/mo \u2192 ${formatLitres(req.requestedMaxLitersPerMonth)}/mo`);
            } else if (req.requestedMaxCapacity != null) {
                bits.push(`${formatLitres(req.currentMaxCapacity ?? 0)} cap \u2192 ${formatLitres(req.requestedMaxCapacity)} cap`);
            }
            return bits.join(" \u00B7 ") || null;
        }
        default:
            return null;
    }
}

/** Per-type labeled detail rows shown inside the Detail pane. */
function renderDetailRows(req: ApprovalRequest): Array<[string, string]> {
    const p = req.payload || {};
    const rows: Array<[string, string]> = [];

    const pushIf = (label: string, value: string | null | undefined) => {
        if (value != null && value !== "") rows.push([label, value]);
    };

    switch (req.requestType) {
        case "RECORD_INVOICE_PAYMENT":
            pushIf("Bill No", req.billNo);
            pushIf("Customer", req.customerName);
            pushIf("Amount", formatRupee(req.amount));
            pushIf("Payment Mode", req.paymentMode);
            pushIf("Reference", strOrNull(p.referenceNo));
            pushIf("Remarks", strOrNull(p.remarks));
            pushIf("Payment Date", strOrNull(p.paymentDate));
            break;
        case "RECORD_STATEMENT_PAYMENT":
            pushIf("Statement No", req.statementNo);
            pushIf("Customer", req.customerName);
            pushIf("Amount", formatRupee(req.amount));
            pushIf("Payment Mode", req.paymentMode);
            pushIf("Reference", strOrNull(p.referenceNo));
            pushIf("Remarks", strOrNull(p.remarks));
            pushIf("Payment Date", strOrNull(p.paymentDate));
            break;
        case "ADD_VEHICLE":
            pushIf("Customer", req.customerName);
            pushIf("Vehicle No", req.vehicleNumber);
            pushIf("Vehicle Type ID", strOrNull(p.vehicleTypeId));
            pushIf("Preferred Product ID", strOrNull(p.preferredProductId));
            pushIf("Max Capacity", strOrNull(p.maxCapacity));
            pushIf("Max Litres / Month", strOrNull(p.maxLitersPerMonth));
            break;
        case "UNBLOCK_CUSTOMER":
            pushIf("Customer", req.customerName);
            pushIf("Reason", strOrNull(p.reason));
            break;
        case "RAISE_CREDIT_LIMIT":
            pushIf("Customer", req.customerName);
            if (req.requestedCreditLimitAmount != null) {
                rows.push(["Credit Limit (Amount)",
                    `${formatRupee(req.currentCreditLimitAmount ?? 0)} \u2192 ${formatRupee(req.requestedCreditLimitAmount)}`]);
            }
            if (req.requestedCreditLimitLiters != null) {
                rows.push(["Credit Limit (Litres)",
                    `${formatLitres(req.currentCreditLimitLiters ?? 0)} \u2192 ${formatLitres(req.requestedCreditLimitLiters)}`]);
            }
            break;
        case "RAISE_VEHICLE_LIMIT":
            pushIf("Customer", req.customerName);
            pushIf("Vehicle No", req.vehicleNumber);
            if (req.requestedMaxLitersPerMonth != null) {
                rows.push(["Max Litres / Month",
                    `${formatLitres(req.currentMaxLitersPerMonth ?? 0)} \u2192 ${formatLitres(req.requestedMaxLitersPerMonth)}`]);
            }
            if (req.requestedMaxCapacity != null) {
                rows.push(["Max Capacity",
                    `${formatLitres(req.currentMaxCapacity ?? 0)} \u2192 ${formatLitres(req.requestedMaxCapacity)}`]);
            }
            break;
    }
    return rows;
}

function strOrNull(v: unknown): string | null {
    if (v == null) return null;
    const s = String(v).trim();
    return s.length > 0 ? s : null;
}

function contextLink(req: ApprovalRequest): { href: string; label: string } | null {
    const p = req.payload || {};
    switch (req.requestType) {
        case "RECORD_STATEMENT_PAYMENT":
            return p.statementId ? { href: `/payments/explorer?statementId=${p.statementId}`, label: "Open statement" } : null;
        case "RECORD_INVOICE_PAYMENT":
            return p.invoiceBillId ? { href: `/operations/invoices/explorer?invoiceId=${p.invoiceBillId}`, label: "Open invoice" } : null;
        case "ADD_VEHICLE":
        case "UNBLOCK_CUSTOMER":
        case "RAISE_CREDIT_LIMIT":
        case "RAISE_VEHICLE_LIMIT":
            return req.customerId ? { href: `/customers/${req.customerId}`, label: "Open customer" } : null;
        default:
            return null;
    }
}

function ApprovalsPageInner() {
    const [requests, setRequests] = useState<ApprovalRequest[]>([]);
    const [selectedId, setSelectedId] = useState<number | null>(null);
    const [loading, setLoading] = useState(true);
    const [actioning, setActioning] = useState(false);
    const [rejectOpen, setRejectOpen] = useState(false);
    const [rejectNote, setRejectNote] = useState("");
    const [approveNote, setApproveNote] = useState("");

    const load = useCallback(async () => {
        setLoading(true);
        try {
            const data = await listPendingApprovals();
            setRequests(data);
            if (data.length > 0 && !data.some(r => r.id === selectedId)) {
                setSelectedId(data[0].id);
            } else if (data.length === 0) {
                setSelectedId(null);
            }
        } catch (e: unknown) {
            const msg = e instanceof Error ? e.message : "Failed to load requests";
            showToast.error(msg);
        } finally {
            setLoading(false);
        }
    }, [selectedId]);

    useEffect(() => { load(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

    const selected = useMemo(() => requests.find(r => r.id === selectedId) ?? null, [requests, selectedId]);
    const selectedDetails = useMemo(() => selected ? renderDetailRows(selected) : [], [selected]);
    const link = selected ? contextLink(selected) : null;

    const onApprove = async () => {
        if (!selected) return;
        setActioning(true);
        try {
            await approveApprovalRequest(selected.id, approveNote || undefined);
            showToast.success("Request approved");
            setApproveNote("");
            await load();
        } catch (e: unknown) {
            const msg = e instanceof Error ? e.message : "Approve failed";
            showToast.error(msg);
        } finally {
            setActioning(false);
        }
    };

    const onReject = async () => {
        if (!selected || !rejectNote.trim()) return;
        setActioning(true);
        try {
            await rejectApprovalRequest(selected.id, rejectNote.trim());
            showToast.success("Request rejected");
            setRejectOpen(false);
            setRejectNote("");
            await load();
        } catch (e: unknown) {
            const msg = e instanceof Error ? e.message : "Reject failed";
            showToast.error(msg);
        } finally {
            setActioning(false);
        }
    };

    return (
        <div className="p-6 space-y-4">
            <div className="flex items-center justify-between">
                <div>
                    <h1 className="text-2xl font-bold">Approvals</h1>
                    <p className="text-sm text-muted-foreground">Review and act on cashier requests</p>
                </div>
                <button
                    onClick={load}
                    className="inline-flex items-center gap-2 px-3 py-1.5 text-sm rounded-md border border-border hover:bg-muted transition-colors"
                >
                    <RefreshCw className="w-4 h-4" /> Refresh
                </button>
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
                {/* Queue */}
                <GlassCard className="p-0 overflow-hidden">
                    <div className="px-4 py-3 border-b border-border flex items-center gap-2">
                        <Clock className="w-4 h-4 text-muted-foreground" />
                        <span className="font-medium">Pending</span>
                        <Badge className="ml-auto">{requests.length}</Badge>
                    </div>
                    <div className="divide-y divide-border max-h-[70vh] overflow-y-auto">
                        {loading && (
                            <div className="p-6 flex items-center justify-center text-muted-foreground">
                                <Loader2 className="w-4 h-4 animate-spin mr-2" /> Loading…
                            </div>
                        )}
                        {!loading && requests.length === 0 && (
                            <div className="p-8 text-center text-muted-foreground text-sm">No pending requests</div>
                        )}
                        {!loading && requests.map(r => {
                            const meta = TYPE_META[r.requestType];
                            const Icon = meta.icon;
                            const active = r.id === selectedId;
                            const subtitle = listSubtitle(r);
                            return (
                                <button
                                    key={r.id}
                                    onClick={() => setSelectedId(r.id)}
                                    className={"w-full text-left px-4 py-3 hover:bg-muted/40 transition-colors " + (active ? "bg-muted/50" : "")}
                                >
                                    <div className="flex items-center gap-2">
                                        <span className={"inline-flex items-center gap-1.5 px-2 py-0.5 rounded text-xs font-medium " + meta.tone}>
                                            <Icon className="w-3.5 h-3.5" /> {meta.label}
                                        </span>
                                        <span className="ml-auto text-xs text-muted-foreground">#{r.id}</span>
                                    </div>
                                    {subtitle && (
                                        <div className="mt-1 text-sm font-medium line-clamp-1">{subtitle}</div>
                                    )}
                                    {r.requestNote && (
                                        <div className="mt-0.5 text-xs text-muted-foreground line-clamp-1 italic">
                                            &ldquo;{r.requestNote}&rdquo;
                                        </div>
                                    )}
                                    <div className="mt-0.5 text-xs text-muted-foreground">{formatDateTime(r.createdAt)}</div>
                                </button>
                            );
                        })}
                    </div>
                </GlassCard>

                {/* Detail */}
                <GlassCard className="p-0 overflow-hidden">
                    {!selected && (
                        <div className="p-10 text-center text-muted-foreground text-sm">Select a request to review</div>
                    )}
                    {selected && (
                        <>
                            <div className="px-4 py-3 border-b border-border flex items-center gap-2">
                                <span className={"inline-flex items-center gap-1.5 px-2 py-0.5 rounded text-xs font-medium " + TYPE_META[selected.requestType].tone}>
                                    {TYPE_META[selected.requestType].label}
                                </span>
                                <span className="text-sm font-medium ml-2">Request #{selected.id}</span>
                                {link && (
                                    <Link href={link.href} className="ml-auto text-xs text-orange-400 hover:underline">
                                        {link.label} →
                                    </Link>
                                )}
                            </div>

                            <div className="p-4 space-y-4">
                                <div className="grid grid-cols-2 gap-3 text-sm">
                                    <div>
                                        <div className="text-xs text-muted-foreground">Submitted at</div>
                                        <div>{formatDateTime(selected.createdAt)}</div>
                                    </div>
                                    <div>
                                        <div className="text-xs text-muted-foreground">Customer</div>
                                        <div>
                                            {selected.customerName ?? (selected.customerId ? `#${selected.customerId}` : "-")}
                                        </div>
                                    </div>
                                    <div>
                                        <div className="text-xs text-muted-foreground">Requested by (user id)</div>
                                        <div>{selected.requestedBy ?? "-"}</div>
                                    </div>
                                </div>

                                {selected.requestNote && (
                                    <div>
                                        <div className="text-xs text-muted-foreground mb-1">Cashier note</div>
                                        <div className="p-3 rounded-md bg-muted/40 text-sm whitespace-pre-wrap">{selected.requestNote}</div>
                                    </div>
                                )}

                                <div>
                                    <div className="text-xs text-muted-foreground mb-1">Details</div>
                                    <div className="rounded-md border border-border divide-y divide-border">
                                        {selectedDetails.map(([k, v]) => (
                                            <div key={k} className="flex px-3 py-1.5 text-sm">
                                                <span className="text-muted-foreground w-48 shrink-0">{k}</span>
                                                <span className="break-all">{v}</span>
                                            </div>
                                        ))}
                                        {selectedDetails.length === 0 && (
                                            <div className="px-3 py-2 text-sm text-muted-foreground">No additional details</div>
                                        )}
                                    </div>
                                </div>

                                <div>
                                    <label className="text-xs text-muted-foreground">Approval note (optional)</label>
                                    <input
                                        type="text"
                                        value={approveNote}
                                        onChange={e => setApproveNote(e.target.value)}
                                        placeholder="e.g. Verified with cashier on phone"
                                        className="mt-1 w-full px-3 py-2 rounded-md bg-background border border-border text-sm"
                                    />
                                </div>

                                <div className="flex items-center gap-2 pt-2">
                                    <button
                                        disabled={actioning}
                                        onClick={onApprove}
                                        className="inline-flex items-center gap-2 px-4 py-2 rounded-md bg-green-600/80 hover:bg-green-600 text-white text-sm font-medium disabled:opacity-50"
                                    >
                                        <CheckCircle2 className="w-4 h-4" /> Approve
                                    </button>
                                    <button
                                        disabled={actioning}
                                        onClick={() => { setRejectOpen(true); setRejectNote(""); }}
                                        className="inline-flex items-center gap-2 px-4 py-2 rounded-md bg-red-600/80 hover:bg-red-600 text-white text-sm font-medium disabled:opacity-50"
                                    >
                                        <XCircle className="w-4 h-4" /> Reject
                                    </button>
                                    {actioning && <Loader2 className="w-4 h-4 animate-spin text-muted-foreground" />}
                                </div>
                            </div>
                        </>
                    )}
                </GlassCard>
            </div>

            <Modal isOpen={rejectOpen} onClose={() => setRejectOpen(false)} title="Reject request">
                <div className="p-6 space-y-3">
                    <label className="text-sm">Reason (required — cashier will see this)</label>
                    <textarea
                        value={rejectNote}
                        onChange={e => setRejectNote(e.target.value)}
                        rows={4}
                        className="w-full px-3 py-2 rounded-md bg-background border border-border text-sm"
                        placeholder="e.g. Amount doesn't match the cheque photo"
                    />
                    <div className="flex items-center justify-end gap-2 pt-2">
                        <button
                            onClick={() => setRejectOpen(false)}
                            className="px-4 py-2 text-sm rounded-md border border-border hover:bg-muted"
                        >Cancel</button>
                        <button
                            disabled={!rejectNote.trim() || actioning}
                            onClick={onReject}
                            className="px-4 py-2 text-sm rounded-md bg-red-600 text-white disabled:opacity-50"
                        >Reject request</button>
                    </div>
                </div>
            </Modal>
        </div>
    );
}

export default function ApprovalsPage() {
    return (
        <PermissionGate permission="APPROVAL_REQUEST_VIEW">
            <ApprovalsPageInner />
        </PermissionGate>
    );
}
