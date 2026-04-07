"use client";

import React, { createContext, useContext, useState, useCallback, useEffect } from "react";
import { X, CheckCircle, AlertCircle, Info } from "lucide-react";

type ToastType = "success" | "error" | "info";

interface Toast {
    id: number;
    message: string;
    type: ToastType;
}

// --- Global imperative API (works without hooks) ---
type ToastListener = (message: string, type: ToastType) => void;
let globalListener: ToastListener | null = null;

export const showToast = {
    success: (message: string) => globalListener?.(message, "success"),
    error: (message: string) => globalListener?.(message, "error"),
    info: (message: string) => globalListener?.(message, "info"),
};

// --- React context API (for components that prefer hooks) ---
const ToastContext = createContext(showToast);

let nextId = 0;

export function ToastProvider({ children }: { children: React.ReactNode }) {
    const [toasts, setToasts] = useState<Toast[]>([]);

    const addToast = useCallback((message: string, type: ToastType) => {
        const id = ++nextId;
        setToasts((prev) => [...prev, { id, message, type }]);
        setTimeout(() => {
            setToasts((prev) => prev.filter((t) => t.id !== id));
        }, 4000);
    }, []);

    // Register as the global listener
    useEffect(() => {
        globalListener = addToast;
        return () => { globalListener = null; };
    }, [addToast]);

    const removeToast = useCallback((id: number) => {
        setToasts((prev) => prev.filter((t) => t.id !== id));
    }, []);

    const icons: Record<ToastType, React.ReactNode> = {
        success: <CheckCircle className="w-4 h-4 text-green-500 shrink-0" />,
        error: <AlertCircle className="w-4 h-4 text-red-500 shrink-0" />,
        info: <Info className="w-4 h-4 text-blue-500 shrink-0" />,
    };

    const borderColors: Record<ToastType, string> = {
        success: "border-green-500/30",
        error: "border-red-500/30",
        info: "border-blue-500/30",
    };

    return (
        <ToastContext.Provider value={showToast}>
            {children}
            <div className="fixed bottom-4 right-4 z-[9999] flex flex-col gap-2 max-w-sm">
                {toasts.map((t) => (
                    <div
                        key={t.id}
                        className={`flex items-start gap-2 px-4 py-3 rounded-xl bg-card border ${borderColors[t.type]} shadow-xl text-sm text-foreground animate-in slide-in-from-right-5 fade-in duration-200`}
                    >
                        {icons[t.type]}
                        <span className="flex-1">{t.message}</span>
                        <button onClick={() => removeToast(t.id)} className="text-muted-foreground hover:text-foreground shrink-0">
                            <X className="w-3.5 h-3.5" />
                        </button>
                    </div>
                ))}
            </div>
        </ToastContext.Provider>
    );
}

export function useToast() {
    return useContext(ToastContext);
}
