"use client";

import { useEffect, useState, useRef } from "react";
import { getPaytmStatus, type PaytmStatusResponse } from "@/lib/api/station/payments";

interface PaytmPosStatusProps {
    merchantTxnId: string;
    amount: number;
    onSuccess: (status: PaytmStatusResponse) => void;
    onFailure: (status: PaytmStatusResponse) => void;
    onClose: () => void;
}

export function PaytmPosStatus({ merchantTxnId, amount, onSuccess, onFailure, onClose }: PaytmPosStatusProps) {
    const [status, setStatus] = useState<PaytmStatusResponse | null>(null);
    const [polling, setPolling] = useState(true);
    const intervalRef = useRef<NodeJS.Timeout | null>(null);

    useEffect(() => {
        if (!merchantTxnId) return;

        const poll = async () => {
            try {
                const res = await getPaytmStatus(merchantTxnId);
                setStatus(res);
                if (res.status !== "INITIATED") {
                    setPolling(false);
                    if (intervalRef.current) clearInterval(intervalRef.current);
                    if (res.status === "SUCCESS") {
                        setTimeout(() => onSuccess(res), 2000);
                    } else {
                        setTimeout(() => onFailure(res), 3000);
                    }
                }
            } catch {
                // Keep polling on error
            }
        };

        poll();
        intervalRef.current = setInterval(poll, 3000);

        return () => {
            if (intervalRef.current) clearInterval(intervalRef.current);
        };
    }, [merchantTxnId]);

    const currentStatus = status?.status || "INITIATED";

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm">
            <div className="bg-card border border-border rounded-2xl shadow-2xl p-8 max-w-md w-full mx-4 text-center">
                {/* Header */}
                <div className="mb-6">
                    <div className="text-xs font-bold uppercase tracking-widest text-muted-foreground mb-1">
                        Paytm POS
                    </div>
                    <div className="text-2xl font-bold text-foreground">
                        Rs. {amount.toLocaleString("en-IN", { minimumFractionDigits: 2 })}
                    </div>
                </div>

                {/* Status Display */}
                {currentStatus === "INITIATED" && (
                    <div className="space-y-4">
                        <div className="flex justify-center">
                            <div className="w-16 h-16 border-4 border-sky-500/30 border-t-sky-500 rounded-full animate-spin" />
                        </div>
                        <div className="text-lg font-semibold text-sky-400">
                            Waiting for POS terminal...
                        </div>
                        <div className="text-sm text-muted-foreground">
                            Ask the customer to tap, swipe, or scan on the POS device
                        </div>
                    </div>
                )}

                {currentStatus === "SUCCESS" && (
                    <div className="space-y-4">
                        <div className="flex justify-center">
                            <div className="w-16 h-16 rounded-full bg-green-500/20 flex items-center justify-center">
                                <svg className="w-8 h-8 text-green-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={3}>
                                    <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
                                </svg>
                            </div>
                        </div>
                        <div className="text-lg font-semibold text-green-400">
                            Payment Successful!
                        </div>
                        {status?.paymentMode && (
                            <div className="text-sm text-muted-foreground">
                                Paid via {status.paymentMode}
                                {status.bankTxnId && ` | Ref: ${status.bankTxnId}`}
                            </div>
                        )}
                    </div>
                )}

                {(currentStatus === "FAILED" || currentStatus === "TIMEOUT") && (
                    <div className="space-y-4">
                        <div className="flex justify-center">
                            <div className="w-16 h-16 rounded-full bg-red-500/20 flex items-center justify-center">
                                <svg className="w-8 h-8 text-red-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={3}>
                                    <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                                </svg>
                            </div>
                        </div>
                        <div className="text-lg font-semibold text-red-400">
                            {currentStatus === "TIMEOUT" ? "Payment Timed Out" : "Payment Failed"}
                        </div>
                        {status?.respMsg && (
                            <div className="text-sm text-muted-foreground">{status.respMsg}</div>
                        )}
                    </div>
                )}

                {/* Transaction Reference */}
                <div className="mt-6 pt-4 border-t border-border">
                    <div className="text-xs text-muted-foreground">
                        Ref: {merchantTxnId}
                    </div>
                </div>

                {/* Close button — only show after terminal state or if user wants to dismiss */}
                {!polling && (
                    <button
                        onClick={onClose}
                        className="mt-4 px-6 py-2 bg-primary text-primary-foreground rounded-lg text-sm font-medium hover:opacity-90 transition"
                    >
                        Close
                    </button>
                )}
                {polling && (
                    <button
                        onClick={() => {
                            if (intervalRef.current) clearInterval(intervalRef.current);
                            setPolling(false);
                            onClose();
                        }}
                        className="mt-4 px-6 py-2 bg-muted text-muted-foreground rounded-lg text-sm font-medium hover:opacity-90 transition"
                    >
                        Cancel
                    </button>
                )}
            </div>
        </div>
    );
}
