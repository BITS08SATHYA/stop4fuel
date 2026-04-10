"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { GlassCard } from "@/components/ui/glass-card";
import { StyledSelect } from "@/components/ui/styled-select";
import { Badge } from "@/components/ui/badge";
import { TablePagination, useClientPagination } from "@/components/ui/table-pagination";
import {
    Clock, Search, FileText, Download, Eye, Loader2, RotateCcw, Pencil, User
} from "lucide-react";
import {
    getShifts, getShiftReportPdfUrl, reopenShiftToEdit, Shift,
    getShiftCashiers, changeShiftAttendant, CashierUser,
} from "@/lib/api/station";
import { Modal } from "@/components/ui/modal";
import { showToast } from "@/components/ui/toast";

const formatDateTime = (iso?: string) => {
    if (!iso) return "—";
    return new Date(iso).toLocaleString("en-IN", {
        day: "2-digit", month: "short", year: "numeric",
        hour: "2-digit", minute: "2-digit", hour12: true,
    });
};

const getDuration = (start?: string, end?: string) => {
    if (!start || !end) return "—";
    const ms = new Date(end).getTime() - new Date(start).getTime();
    const hrs = Math.floor(ms / 3600000);
    const mins = Math.floor((ms % 3600000) / 60000);
    return `${hrs}h ${mins}m`;
};

const statusColor: Record<string, string> = {
    OPEN: "bg-emerald-500/10 text-emerald-500 border-emerald-500/20",
    CLOSED: "bg-gray-500/10 text-gray-400 border-gray-500/20",
    RECONCILED: "bg-amber-500/10 text-amber-500 border-amber-500/20",
};

export default function ShiftHistoryPage() {
    const router = useRouter();
    const [shifts, setShifts] = useState<Shift[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [searchQuery, setSearchQuery] = useState("");
    const [statusFilter, setStatusFilter] = useState("");
    const [downloadingId, setDownloadingId] = useState<number | null>(null);

    // Cashier assignment
    const [showCashierModal, setShowCashierModal] = useState(false);
    const [editingShiftId, setEditingShiftId] = useState<number | null>(null);
    const [cashiers, setCashiers] = useState<CashierUser[]>([]);
    const [selectedCashierId, setSelectedCashierId] = useState<number | "">("");
    const [cashierSearch, setCashierSearch] = useState("");
    const [savingCashier, setSavingCashier] = useState(false);

    useEffect(() => {
        loadShifts();
    }, []);

    const loadShifts = async () => {
        try {
            const data = await getShifts();
            setShifts(data.sort((a: Shift, b: Shift) => b.id - a.id));
        } catch (err) {
            console.error("Failed to load shifts", err);
        } finally {
            setIsLoading(false);
        }
    };

    const handleEditAttendant = async (shift: Shift) => {
        try {
            const list = await getShiftCashiers();
            setCashiers(list);
            setSelectedCashierId(shift.attendant?.id || "");
            setCashierSearch("");
            setEditingShiftId(shift.id);
            setShowCashierModal(true);
        } catch (err: any) {
            showToast.error(err.message || "Failed to load cashiers");
        }
    };

    const handleSaveAttendant = async () => {
        if (!editingShiftId || !selectedCashierId) return;
        setSavingCashier(true);
        try {
            await changeShiftAttendant(editingShiftId, Number(selectedCashierId));
            setShowCashierModal(false);
            loadShifts();
        } catch (err: any) {
            showToast.error(err.message || "Failed to update attendant");
        } finally {
            setSavingCashier(false);
        }
    };

    const handleDownloadPdf = async (shiftId: number) => {
        setDownloadingId(shiftId);
        try {
            const url = await getShiftReportPdfUrl(shiftId);
            window.open(url, "_blank");
        } catch (err) {
            console.error("Failed to get PDF", err);
        } finally {
            setDownloadingId(null);
        }
    };

    const filtered = shifts.filter((s) => {
        if (statusFilter && s.status !== statusFilter) return false;
        if (searchQuery) {
            const q = searchQuery.toLowerCase();
            const matchId = String(s.id).includes(q);
            const matchAttendant = s.attendant?.name?.toLowerCase().includes(q);
            if (!matchId && !matchAttendant) return false;
        }
        return true;
    });

    const { page, setPage, pageSize, paginatedData, totalPages, totalElements } = useClientPagination(filtered, 10);

    return (
        <div className="p-6 min-h-screen bg-background text-foreground">
            <div className="max-w-7xl mx-auto">
                {/* Header */}
                <div className="mb-8">
                    <h1 className="text-4xl font-bold text-foreground tracking-tight">
                        Shift <span className="text-gradient">History</span>
                    </h1>
                    <p className="text-muted-foreground mt-2">
                        View all past and current shifts with reports.
                    </p>
                </div>

                {/* Filters */}
                <div className="mb-4 flex flex-wrap gap-3 items-center">
                    <div className="relative flex-1 min-w-[200px] max-w-md">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                        <input
                            type="text"
                            placeholder="Search by shift # or attendant..."
                            value={searchQuery}
                            onChange={(e) => { setSearchQuery(e.target.value); setPage(0); }}
                            className="w-full pl-10 pr-4 py-2.5 bg-card border border-border rounded-xl text-foreground text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                        />
                    </div>
                    <StyledSelect
                        value={statusFilter}
                        onChange={(val) => { setStatusFilter(val); setPage(0); }}
                        options={[
                            { value: "", label: "All Statuses" },
                            { value: "OPEN", label: "Open" },
                            { value: "CLOSED", label: "Closed" },
                            { value: "RECONCILED", label: "Reconciled" },
                        ]}
                    />
                    <div className="ml-auto text-sm text-muted-foreground">
                        {filtered.length} shift{filtered.length !== 1 ? "s" : ""}
                    </div>
                </div>

                {/* Table */}
                {isLoading ? (
                    <div className="flex flex-col items-center justify-center py-20 text-muted-foreground">
                        <div className="w-12 h-12 border-4 border-primary/20 border-t-primary rounded-full animate-spin mb-4" />
                        <p className="animate-pulse">Loading shifts...</p>
                    </div>
                ) : (
                    <GlassCard>
                        <div className="overflow-x-auto">
                            <table className="w-full text-sm">
                                <thead>
                                    <tr className="border-b border-border text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                                        <th className="px-4 py-3">Shift #</th>
                                        <th className="px-4 py-3">Status</th>
                                        <th className="px-4 py-3">Start Time</th>
                                        <th className="px-4 py-3">End Time</th>
                                        <th className="px-4 py-3">Duration</th>
                                        <th className="px-4 py-3">Attendant</th>
                                        <th className="px-4 py-3 text-right">Actions</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {paginatedData.length === 0 ? (
                                        <tr>
                                            <td colSpan={7} className="px-4 py-12 text-center text-muted-foreground">
                                                No shifts found.
                                            </td>
                                        </tr>
                                    ) : (
                                        paginatedData.map((shift) => (
                                            <tr key={shift.id} className="border-b border-border/50 hover:bg-muted/30 transition-colors">
                                                <td className="px-4 py-3 font-bold text-foreground">#{shift.id}</td>
                                                <td className="px-4 py-3">
                                                    <span className={`px-2.5 py-1 rounded-full text-[10px] font-bold uppercase border ${statusColor[shift.status] || "bg-muted text-muted-foreground"}`}>
                                                        {shift.status}
                                                    </span>
                                                </td>
                                                <td className="px-4 py-3 text-muted-foreground">{formatDateTime(shift.startTime)}</td>
                                                <td className="px-4 py-3 text-muted-foreground">{formatDateTime(shift.endTime)}</td>
                                                <td className="px-4 py-3 text-muted-foreground">{getDuration(shift.startTime, shift.endTime)}</td>
                                                <td className="px-4 py-3 text-foreground">
                                                    <button
                                                        onClick={() => handleEditAttendant(shift)}
                                                        className="inline-flex items-center gap-1.5 hover:text-orange-500 transition-colors group"
                                                        title="Change attendant"
                                                    >
                                                        {shift.attendant?.name || "—"}
                                                        <Pencil className="w-3 h-3 opacity-0 group-hover:opacity-100 transition-opacity" />
                                                    </button>
                                                </td>
                                                <td className="px-4 py-3">
                                                    <div className="flex items-center justify-end gap-2">
                                                        {shift.status === "OPEN" && (
                                                            <button
                                                                onClick={() => router.push(`/operations/shifts/close/${shift.id}`)}
                                                                className="p-2 rounded-lg hover:bg-emerald-500/10 text-emerald-500 transition-colors"
                                                                title="Go to Shift Closing"
                                                            >
                                                                <Eye className="w-4 h-4" />
                                                            </button>
                                                        )}
                                                        {shift.status === "REVIEW" && (
                                                            <button
                                                                onClick={async () => {
                                                                    try {
                                                                        await reopenShiftToEdit(shift.id);
                                                                        router.push(`/operations/shifts/close/${shift.id}`);
                                                                    } catch (e: any) {
                                                                        showToast.error(e.message || "Failed to reopen shift");
                                                                    }
                                                                }}
                                                                className="p-2 rounded-lg hover:bg-orange-500/10 text-orange-500 transition-colors"
                                                                title="Reopen to Edit"
                                                            >
                                                                <RotateCcw className="w-4 h-4" />
                                                            </button>
                                                        )}
                                                        {(shift.status === "CLOSED" || shift.status === "RECONCILED") && (
                                                            <>
                                                                <button
                                                                    onClick={() => router.push(`/operations/shifts/report/${shift.id}`)}
                                                                    className="p-2 rounded-lg hover:bg-primary/10 text-primary transition-colors"
                                                                    title="View Report"
                                                                >
                                                                    <Eye className="w-4 h-4" />
                                                                </button>
                                                                <button
                                                                    onClick={() => handleDownloadPdf(shift.id)}
                                                                    disabled={downloadingId === shift.id}
                                                                    className="p-2 rounded-lg hover:bg-primary/10 text-primary transition-colors disabled:opacity-50"
                                                                    title="Download PDF"
                                                                >
                                                                    {downloadingId === shift.id
                                                                        ? <Loader2 className="w-4 h-4 animate-spin" />
                                                                        : <Download className="w-4 h-4" />
                                                                    }
                                                                </button>
                                                            </>
                                                        )}
                                                    </div>
                                                </td>
                                            </tr>
                                        ))
                                    )}
                                </tbody>
                            </table>
                        </div>
                        <TablePagination
                            page={page}
                            totalPages={totalPages}
                            totalElements={totalElements}
                            pageSize={pageSize}
                            onPageChange={setPage}
                        />
                    </GlassCard>
                )}
            </div>

            {/* Cashier Assignment Modal */}
            <Modal isOpen={showCashierModal} onClose={() => setShowCashierModal(false)} title="Set Shift Attendant">
                <div className="p-6 space-y-4">
                    <input
                        type="text"
                        placeholder="Search by name or phone..."
                        value={cashierSearch}
                        onChange={(e) => setCashierSearch(e.target.value)}
                        className="w-full px-3 py-2 bg-card border border-border rounded-lg text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                    />
                    <div className="space-y-2 max-h-[50vh] overflow-y-auto">
                        {cashiers
                            .filter(c => {
                                const q = cashierSearch.toLowerCase();
                                if (!q) return true;
                                return c.name?.toLowerCase().includes(q) || c.phone?.includes(q);
                            })
                            .map(c => (
                            <button
                                key={c.id}
                                onClick={() => setSelectedCashierId(c.id)}
                                className={`w-full text-left px-4 py-3 rounded-xl border transition-all flex items-center justify-between ${
                                    selectedCashierId === c.id
                                        ? "border-orange-500 bg-orange-500/10 text-foreground"
                                        : "border-border hover:bg-muted/50 text-foreground"
                                }`}
                            >
                                <div>
                                    <div className="font-medium">{c.name}</div>
                                    <div className="text-xs text-muted-foreground">{c.role}{c.phone ? ` · ${c.phone}` : ""}</div>
                                </div>
                                {selectedCashierId === c.id && (
                                    <div className="w-5 h-5 rounded-full bg-orange-500 flex items-center justify-center">
                                        <svg className="w-3 h-3 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={3} d="M5 13l4 4L19 7" />
                                        </svg>
                                    </div>
                                )}
                            </button>
                        ))}
                    </div>
                    <div className="flex justify-end gap-3 pt-2 border-t border-border">
                        <button onClick={() => setShowCashierModal(false)} className="px-4 py-2 rounded-lg border border-border text-muted-foreground hover:bg-muted transition-colors">
                            Cancel
                        </button>
                        <button
                            onClick={handleSaveAttendant}
                            disabled={!selectedCashierId || savingCashier}
                            className="btn-gradient px-6 py-2 rounded-lg font-medium disabled:opacity-50"
                        >
                            {savingCashier ? "Saving..." : "Update Attendant"}
                        </button>
                    </div>
                </div>
            </Modal>
        </div>
    );
}
