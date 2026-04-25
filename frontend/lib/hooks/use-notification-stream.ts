"use client";

import { useEffect } from "react";
import { fetchAuthSession } from "aws-amplify/auth";
import { useAuth } from "@/lib/auth/auth-context";
import { API_BASE_URL } from "@/lib/api/station/common";
import { openStream, type SseEvent } from "@/lib/notifications/sse-client";
import { approvalBus, type ApprovalEventType } from "@/lib/notifications/approval-bus";
import { messageBus, type MessageEventType, type OtherParticipant } from "@/lib/notifications/message-bus";
import { playDing } from "@/lib/notifications/sound";

const DEV_MODE = !process.env.NEXT_PUBLIC_COGNITO_USER_POOL_ID;

/**
 * Opens a long-lived SSE connection to `/api/notifications/stream` and pipes
 * approval events into the approvalBus. Also fires a desktop browser
 * notification + plays a sound on each new approval.
 *
 * Gated on the user being authenticated; the stream auto-reconnects on drop.
 */
export function useNotificationStream(): void {
    const { isAuthenticated, hasPermission } = useAuth();
    const canApprove = hasPermission("APPROVAL_REQUEST_APPROVE");

    useEffect(() => {
        if (!isAuthenticated) return;

        const controller = new AbortController();
        openStream({
            url: `${API_BASE_URL}/notifications/stream`,
            getAuthToken: async () => {
                if (DEV_MODE) return null;
                try {
                    const session = await fetchAuthSession();
                    return session.tokens?.accessToken?.toString() ?? null;
                } catch {
                    return null;
                }
            },
            onEvent: (evt: SseEvent) => {
                if (evt.event === "approval") {
                    try {
                        const data = JSON.parse(evt.data) as {
                            type: ApprovalEventType;
                            requestId: number;
                            requestType: string;
                            customerId?: number | string;
                            requestedBy?: number | string;
                            createdAt?: string;
                            reviewNote?: string;
                            reviewedAt?: string;
                        };
                        approvalBus.emit(data);
                        if (canApprove && data.type === "APPROVAL_REQUEST_CREATED") {
                            fireApprovalDesktopNotification(data);
                            playDing();
                        }
                    } catch {
                        // malformed frame — ignore
                    }
                    return;
                }
                if (evt.event === "message") {
                    try {
                        const data = JSON.parse(evt.data) as {
                            type: MessageEventType;
                            conversationId: number;
                            messageId?: number;
                            senderId?: number;
                            senderName?: string;
                            text?: string;
                            preview?: string;
                            createdAt?: string;
                            otherParticipant?: OtherParticipant;
                            userId?: number;
                            lastReadMessageId?: number;
                        };
                        messageBus.emit(data);
                        if (data.type === "MESSAGE_CREATED") {
                            fireMessageDesktopNotification(data);
                            playDing();
                        }
                    } catch {
                        // malformed frame — ignore
                    }
                }
            },
            signal: controller.signal,
        });

        return () => controller.abort();
    }, [isAuthenticated, canApprove]);
}

function fireApprovalDesktopNotification(data: { requestId: number; requestType: string }) {
    if (typeof window === "undefined") return;
    if (!("Notification" in window)) return;
    if (Notification.permission !== "granted") return;
    try {
        const label = data.requestType.replace(/_/g, " ").toLowerCase();
        const n = new Notification("New approval request", {
            body: `#${data.requestId} \u2014 ${label}`,
            tag: `sff-approval-${data.requestId}`,
            icon: "/favicon.ico",
        });
        n.onclick = () => {
            window.focus();
            if (window.location.pathname !== "/approvals") {
                window.location.href = "/approvals";
            }
            n.close();
        };
    } catch {
        // ignore
    }
}

function fireMessageDesktopNotification(data: {
    conversationId: number;
    senderName?: string;
    preview?: string;
    text?: string;
}) {
    if (typeof window === "undefined") return;
    if (!("Notification" in window)) return;
    if (Notification.permission !== "granted") return;
    try {
        const title = data.senderName ?? "New message";
        const body = (data.preview ?? data.text ?? "").slice(0, 160);
        const n = new Notification(title, {
            body,
            tag: `sff-message-${data.conversationId}`,
            icon: "/favicon.ico",
        });
        n.onclick = () => {
            window.focus();
            n.close();
        };
    } catch {
        // ignore
    }
}
