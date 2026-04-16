"use client";

import { useEffect, useRef, useState, useSyncExternalStore } from "react";
import { useRouter } from "next/navigation";
import { Bell, CheckCircle2, XCircle, Check } from "lucide-react";
import { approvalBus, type ApprovalEvent } from "@/lib/notifications/approval-bus";

/**
 * Bell icon in the top bar with an unread badge. Opens a dropdown showing the
 * last 20 approval events received via SSE during this session.
 */
export function NotificationBell() {
    const history = useSyncExternalStore(
        listener => approvalBus.subscribe(listener),
        () => approvalBus.getHistory(),
        () => [],
    );
    const unread = useSyncExternalStore(
        listener => approvalBus.subscribe(listener),
        () => approvalBus.getUnreadCount(),
        () => 0,
    );

    const [open, setOpen] = useState(false);
    const wrapperRef = useRef<HTMLDivElement>(null);
    const router = useRouter();

    useEffect(() => {
        if (!open) return;
        const onClick = (e: MouseEvent) => {
            if (!wrapperRef.current?.contains(e.target as Node)) setOpen(false);
        };
        document.addEventListener("mousedown", onClick);
        return () => document.removeEventListener("mousedown", onClick);
    }, [open]);

    const onOpen = () => {
        setOpen(v => !v);
    };

    const onClickItem = (ev: ApprovalEvent) => {
        router.push("/approvals");
        setOpen(false);
        approvalBus.markAllRead();
        void ev;
    };

    return (
        <div className="relative" ref={wrapperRef}>
            <button
                onClick={onOpen}
                aria-label="Notifications"
                className="relative p-2 rounded-md text-muted-foreground hover:bg-muted hover:text-foreground transition-colors"
            >
                <Bell className="w-5 h-5" />
                {unread > 0 && (
                    <span className="absolute -top-0.5 -right-0.5 min-w-[18px] h-[18px] px-1 rounded-full bg-red-500 text-white text-[10px] font-bold flex items-center justify-center">
                        {unread > 99 ? "99+" : unread}
                    </span>
                )}
            </button>

            {open && (
                <div className="absolute right-0 mt-2 w-[min(92vw,360px)] rounded-xl border border-border bg-background shadow-xl overflow-hidden">
                    <div className="flex items-center justify-between px-3 py-2 border-b border-border">
                        <div className="font-semibold text-sm">Notifications</div>
                        {unread > 0 && (
                            <button
                                onClick={() => approvalBus.markAllRead()}
                                className="text-xs text-muted-foreground hover:text-foreground flex items-center gap-1"
                            >
                                <Check className="w-3.5 h-3.5" /> Mark all read
                            </button>
                        )}
                    </div>

                    <div className="max-h-[60vh] overflow-y-auto">
                        {history.length === 0 ? (
                            <div className="p-6 text-center text-xs text-muted-foreground">
                                No notifications yet
                            </div>
                        ) : (
                            history.map(ev => (
                                <NotificationRow key={ev.localId} event={ev} onClick={() => onClickItem(ev)} />
                            ))
                        )}
                    </div>
                </div>
            )}
        </div>
    );
}

function NotificationRow({ event, onClick }: { event: ApprovalEvent; onClick: () => void }) {
    const meta = metaFor(event.type);
    const Icon = meta.icon;
    return (
        <button
            onClick={onClick}
            className="w-full text-left px-3 py-2.5 border-b border-border/50 hover:bg-muted/50 transition flex gap-2.5 items-start"
        >
            <div className={`${meta.tone} rounded-full h-8 w-8 flex items-center justify-center shrink-0 mt-0.5`}>
                <Icon className="h-4 w-4" />
            </div>
            <div className="flex-1 min-w-0">
                <div className="text-sm font-medium truncate">{meta.title}</div>
                <div className="text-xs text-muted-foreground truncate capitalize">
                    #{event.requestId} &middot; {event.requestType.replace(/_/g, " ").toLowerCase()}
                </div>
                <div className="text-[10px] text-muted-foreground mt-0.5">
                    {relativeTime(event.receivedAt)}
                </div>
            </div>
        </button>
    );
}

function metaFor(type: ApprovalEvent["type"]) {
    switch (type) {
        case "APPROVAL_REQUEST_CREATED":
            return { icon: Bell, title: "New approval request", tone: "bg-amber-500/15 text-amber-500" };
        case "APPROVAL_REQUEST_APPROVED":
            return { icon: CheckCircle2, title: "Request approved", tone: "bg-green-500/15 text-green-500" };
        case "APPROVAL_REQUEST_REJECTED":
            return { icon: XCircle, title: "Request rejected", tone: "bg-red-500/15 text-red-500" };
    }
}

function relativeTime(ts: number): string {
    const diff = Date.now() - ts;
    if (diff < 60_000) return "just now";
    if (diff < 3_600_000) return `${Math.floor(diff / 60_000)}m ago`;
    if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)}h ago`;
    return new Date(ts).toLocaleDateString();
}
