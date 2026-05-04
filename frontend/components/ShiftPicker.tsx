"use client";

import { useEffect, useState } from "react";
import { ChevronRight } from "lucide-react";
import { fetchWithAuth } from "@/lib/api/fetch-with-auth";
import { API_BASE_URL } from "@/lib/api/station";

export interface PostableShift {
    id: number;
    status: string;
    startTime?: string;
    endTime?: string | null;
    attendantName?: string | null;
}

interface ShiftPickerProps {
    /** Currently selected shift (controlled). Pass `null` for "no selection" / posting to active. */
    value: number | null;
    onChange: (shiftId: number | null) => void;
    /** Optional active-shift id, rendered with an "active" badge. */
    activeShiftId?: number | null;
    /** Render the pill in the back-dated (amber) style if value !== activeShiftId. Default true. */
    backDatedAmber?: boolean;
    /** Limit on the postable-shift list. Default 10. */
    limit?: number;
    /** Optional override for the pill label when value is null. Default "Posting to: —". */
    placeholderLabel?: string;
}

/**
 * Pill-button + dropdown for picking a recent OPEN/REVIEW shift to post to. Lifted from the
 * inline implementation in `app/operations/invoices/page.tsx` (the create-bill wizard) so the
 * orphan-bills page and any future admin flow can reuse it. Backend route: GET /api/shifts/postable.
 */
export function ShiftPicker({
    value,
    onChange,
    activeShiftId = null,
    backDatedAmber = true,
    limit = 10,
    placeholderLabel = "Posting to: —",
}: ShiftPickerProps) {
    const [shifts, setShifts] = useState<PostableShift[]>([]);
    const [open, setOpen] = useState(false);

    useEffect(() => {
        let cancelled = false;
        (async () => {
            try {
                const res = await fetchWithAuth(`${API_BASE_URL}/shifts/postable?limit=${limit}`);
                if (!res.ok) return;
                const data: PostableShift[] = await res.json();
                if (!cancelled) setShifts(data);
            } catch (err) {
                console.error("Failed to load postable shifts", err);
            }
        })();
        return () => { cancelled = true; };
    }, [limit]);

    const formatLabel = (s: PostableShift) => {
        const dt = s.startTime ? new Date(s.startTime) : null;
        const date = dt ? dt.toLocaleDateString("en-IN", { day: "2-digit", month: "short" }) : "—";
        return `Shift #${s.id} · ${date} · ${s.status}`;
    };

    const isBackdated = backDatedAmber && value != null && value !== activeShiftId;
    const label = value
        ? `Posting to: Shift #${value}${isBackdated ? " (back-dated)" : " (active)"}`
        : placeholderLabel;

    return (
        <div className="flex justify-end relative">
            <button
                type="button"
                onClick={() => setOpen(v => !v)}
                className={`px-4 py-2 rounded-xl text-xs font-bold border transition-colors flex items-center gap-2 ${
                    isBackdated
                        ? "bg-amber-500/10 border-amber-500/30 text-amber-500"
                        : "bg-primary/10 border-primary/20 text-primary"
                }`}
            >
                {label}
                <ChevronRight size={12} className={`transition-transform ${open ? "rotate-90" : ""}`} />
            </button>
            {open && (
                <div className="absolute top-full right-0 mt-2 bg-background border border-border rounded-xl shadow-2xl z-40 w-80 max-h-80 overflow-y-auto">
                    <div className="px-3 py-2 text-[10px] font-bold uppercase tracking-widest text-muted-foreground border-b border-border">
                        Recent Shifts (OPEN / REVIEW only)
                    </div>
                    {shifts.length === 0 && (
                        <div className="px-3 py-3 text-xs text-muted-foreground">No postable shifts.</div>
                    )}
                    {shifts.map(s => {
                        const isCurrent = s.id === activeShiftId;
                        const isSelected = s.id === value;
                        return (
                            <button
                                key={s.id}
                                type="button"
                                onClick={() => { onChange(s.id); setOpen(false); }}
                                className={`w-full text-left px-3 py-2 text-sm border-b border-border/50 transition-colors hover:bg-muted/50 ${
                                    isSelected ? "bg-primary/5 text-primary font-bold" : "text-foreground"
                                }`}
                            >
                                <div className="flex items-center justify-between">
                                    <span>{formatLabel(s)}</span>
                                    {isCurrent && <span className="text-[9px] uppercase tracking-widest text-green-500">active</span>}
                                </div>
                            </button>
                        );
                    })}
                    <div className="px-3 py-2 text-[10px] text-muted-foreground border-t border-border">
                        To post to a closed/reconciled shift, reopen it first from its shift report.
                    </div>
                </div>
            )}
        </div>
    );
}
