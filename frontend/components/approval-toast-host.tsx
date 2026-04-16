"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Bell, CheckCircle2, XCircle, X } from "lucide-react";
import { approvalBus, type ApprovalEvent } from "@/lib/notifications/approval-bus";
import { useAuth } from "@/lib/auth/auth-context";

const DISMISS_MS = 6_000;

interface ToastItem extends ApprovalEvent {
    dismissAt: number;
}

export function ApprovalToastHost() {
    const { hasPermission } = useAuth();
    const canApprove = hasPermission("APPROVAL_REQUEST_APPROVE");
    const [items, setItems] = useState<ToastItem[]>([]);
    const router = useRouter();

    useEffect(() => {
        const unsub = approvalBus.onArrive(event => {
            // Cashiers get approved/rejected events; approvers get created events.
            if (event.type === "APPROVAL_REQUEST_CREATED" && !canApprove) return;
            setItems(prev => [
                { ...event, dismissAt: Date.now() + DISMISS_MS },
                ...prev,
            ].slice(0, 4));
        });
        return unsub;
    }, [canApprove]);

    useEffect(() => {
        if (items.length === 0) return;
        const tick = window.setInterval(() => {
            const now = Date.now();
            setItems(prev => prev.filter(i => i.dismissAt > now));
        }, 500);
        return () => window.clearInterval(tick);
    }, [items.length]);

    const dismiss = (localId: string) =>
        setItems(prev => prev.filter(i => i.localId !== localId));

    if (items.length === 0) return null;

    return (
        <div className="fixed bottom-4 right-4 z-[60] flex flex-col gap-2 w-[min(92vw,360px)]">
            {items.map(item => (
                <ApprovalToastCard
                    key={item.localId}
                    item={item}
                    onDismiss={() => dismiss(item.localId)}
                    onView={() => {
                        router.push("/approvals");
                        dismiss(item.localId);
                    }}
                />
            ))}
        </div>
    );
}

interface CardProps {
    item: ToastItem;
    onDismiss: () => void;
    onView: () => void;
}

function ApprovalToastCard({ item, onDismiss, onView }: CardProps) {
    const meta = metaFor(item.type);
    const Icon = meta.icon;
    const label = item.requestType.replace(/_/g, " ").toLowerCase();

    return (
        <div
            className="bg-background/95 backdrop-blur border border-border rounded-xl shadow-lg p-3 pr-2 flex gap-3 animate-in slide-in-from-right-4 fade-in duration-300"
            role="status"
        >
            <div className={`${meta.tone} rounded-full h-9 w-9 flex items-center justify-center shrink-0`}>
                <Icon className="h-5 w-5" />
            </div>
            <div className="flex-1 min-w-0">
                <div className="text-sm font-semibold text-foreground">{meta.title}</div>
                <div className="text-xs text-muted-foreground mt-0.5 capitalize">
                    #{item.requestId} &middot; {label}
                </div>
                <div className="flex gap-2 mt-2">
                    <button
                        onClick={onView}
                        className="text-xs font-medium px-2.5 py-1 rounded-md bg-primary text-primary-foreground hover:opacity-90 transition"
                    >
                        View
                    </button>
                    <button
                        onClick={onDismiss}
                        className="text-xs font-medium px-2.5 py-1 rounded-md hover:bg-muted transition text-muted-foreground"
                    >
                        Dismiss
                    </button>
                </div>
            </div>
            <button
                onClick={onDismiss}
                className="self-start text-muted-foreground hover:text-foreground transition"
                aria-label="Dismiss"
            >
                <X className="h-4 w-4" />
            </button>
        </div>
    );
}

function metaFor(type: ApprovalEvent["type"]) {
    switch (type) {
        case "APPROVAL_REQUEST_CREATED":
            return { icon: Bell, title: "New approval request", tone: "bg-amber-500/15 text-amber-500" };
        case "APPROVAL_REQUEST_APPROVED":
            return { icon: CheckCircle2, title: "Your request was approved", tone: "bg-green-500/15 text-green-500" };
        case "APPROVAL_REQUEST_REJECTED":
            return { icon: XCircle, title: "Your request was rejected", tone: "bg-red-500/15 text-red-500" };
    }
}
