"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { GlassCard } from "@/components/ui/glass-card";
import { PermissionGate } from "@/components/permission-gate";
import { Paperclip, Upload, Trash2, RefreshCw, X, CheckCircle2, CircleDashed } from "lucide-react";
import {
    getShifts,
    getInvoicesByShift,
    getInvoicePhotos,
    deleteInvoicePhoto,
    uploadInvoiceFile,
    getInvoiceFileUrl,
    type Shift,
    type InvoiceBill,
    type InvoicePhoto,
} from "@/lib/api/station";

const PHOTO_TYPES: { key: string; label: string }[] = [
    { key: "bill-pic", label: "Bill" },
    { key: "pump-bill-pic", label: "Pump Bill" },
    { key: "indent-pic", label: "Indent" },
];

export default function InvoiceUploadsPage() {
    return (
        <PermissionGate permission="INVOICE_VIEW">
            <UploadsPageInner />
        </PermissionGate>
    );
}

function UploadsPageInner() {
    const [shifts, setShifts] = useState<Shift[]>([]);
    const [shiftId, setShiftId] = useState<number | null>(null);
    const [bills, setBills] = useState<InvoiceBill[]>([]);
    const [loading, setLoading] = useState(false);
    const [showCash, setShowCash] = useState(true);
    const [showCredit, setShowCredit] = useState(true);
    const [selectedBillId, setSelectedBillId] = useState<number | null>(null);

    // photos per bill — keyed by bill id, then by photoType
    const [photosByBill, setPhotosByBill] = useState<Record<number, InvoicePhoto[]>>({});

    useEffect(() => {
        getShifts()
            .then((s) => {
                const sorted = [...s].sort((a, b) => b.id - a.id);
                setShifts(sorted);
            })
            .catch(() => setShifts([]));
    }, []);

    const loadBills = useCallback(async (id: number) => {
        setLoading(true);
        try {
            const list = await getInvoicesByShift(id);
            setBills(list);
            // batch fetch photos
            const photoMap: Record<number, InvoicePhoto[]> = {};
            await Promise.all(
                list.map(async (b) => {
                    if (b.id != null) {
                        try {
                            photoMap[b.id] = await getInvoicePhotos(b.id);
                        } catch {
                            photoMap[b.id] = [];
                        }
                    }
                })
            );
            setPhotosByBill(photoMap);
        } catch {
            setBills([]);
            setPhotosByBill({});
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        if (shiftId != null) {
            setSelectedBillId(null);
            loadBills(shiftId);
        }
    }, [shiftId, loadBills]);

    const filtered = useMemo(
        () =>
            bills.filter(
                (b) =>
                    (showCash && b.billType === "CASH") ||
                    (showCredit && b.billType === "CREDIT")
            ),
        [bills, showCash, showCredit]
    );

    const selectedBill = filtered.find((b) => b.id === selectedBillId) ?? null;

    const refreshPhotosForBill = useCallback(async (billId: number) => {
        try {
            const photos = await getInvoicePhotos(billId);
            setPhotosByBill((prev) => ({ ...prev, [billId]: photos }));
        } catch {
            // ignore
        }
    }, []);

    return (
        <div className="p-4 md:p-6 space-y-4">
            <div className="flex items-center gap-2">
                <Paperclip className="w-5 h-5 text-orange-500" />
                <h1 className="text-xl md:text-2xl font-bold">Invoice Uploads</h1>
            </div>

            <GlassCard className="p-4">
                <div className="flex flex-col md:flex-row md:items-center gap-3 md:gap-4">
                    <div className="flex items-center gap-2">
                        <label className="text-sm font-medium text-muted-foreground">Shift</label>
                        <select
                            value={shiftId ?? ""}
                            onChange={(e) =>
                                setShiftId(e.target.value ? Number(e.target.value) : null)
                            }
                            className="border border-border rounded-md px-3 py-2 text-sm bg-background min-w-[260px]"
                        >
                            <option value="">Select a shift…</option>
                            {shifts.map((s) => (
                                <option key={s.id} value={s.id}>
                                    #{s.id} — {new Date(s.startTime).toLocaleString()}
                                    {s.attendant?.name ? ` — ${s.attendant.name}` : ""} — {s.status}
                                </option>
                            ))}
                        </select>
                    </div>

                    <div className="flex items-center gap-4 text-sm">
                        <label className="inline-flex items-center gap-2 cursor-pointer">
                            <input
                                type="checkbox"
                                checked={showCash}
                                onChange={(e) => setShowCash(e.target.checked)}
                            />
                            Cash
                        </label>
                        <label className="inline-flex items-center gap-2 cursor-pointer">
                            <input
                                type="checkbox"
                                checked={showCredit}
                                onChange={(e) => setShowCredit(e.target.checked)}
                            />
                            Credit
                        </label>
                    </div>

                    {shiftId != null && (
                        <button
                            onClick={() => loadBills(shiftId)}
                            className="ml-auto inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-orange-500"
                            title="Reload bills"
                        >
                            <RefreshCw className="w-4 h-4" /> Reload
                        </button>
                    )}
                </div>
            </GlassCard>

            <div className="grid grid-cols-1 lg:grid-cols-5 gap-4">
                {/* Bills table */}
                <GlassCard className="lg:col-span-3 p-0 overflow-hidden">
                    <div className="overflow-x-auto">
                        <table className="w-full text-sm">
                            <thead className="bg-muted/40 text-xs uppercase text-muted-foreground">
                                <tr>
                                    <th className="text-left px-3 py-2">Bill #</th>
                                    <th className="text-left px-3 py-2">Customer</th>
                                    <th className="text-left px-3 py-2">Vehicle</th>
                                    <th className="text-right px-3 py-2">Amount</th>
                                    <th className="text-center px-3 py-2">Type</th>
                                    {PHOTO_TYPES.map((t) => (
                                        <th key={t.key} className="text-center px-2 py-2">
                                            {t.label}
                                        </th>
                                    ))}
                                </tr>
                            </thead>
                            <tbody>
                                {loading && (
                                    <tr>
                                        <td colSpan={8} className="text-center py-6 text-muted-foreground">
                                            Loading…
                                        </td>
                                    </tr>
                                )}
                                {!loading && shiftId == null && (
                                    <tr>
                                        <td colSpan={8} className="text-center py-6 text-muted-foreground">
                                            Pick a shift to view its invoice bills.
                                        </td>
                                    </tr>
                                )}
                                {!loading && shiftId != null && filtered.length === 0 && (
                                    <tr>
                                        <td colSpan={8} className="text-center py-6 text-muted-foreground">
                                            No bills for this shift / filter.
                                        </td>
                                    </tr>
                                )}
                                {!loading &&
                                    filtered.map((b) => {
                                        const photos = (b.id != null && photosByBill[b.id]) || [];
                                        const hasType = (t: string) =>
                                            photos.some((p) => p.photoType === t);
                                        const active = b.id === selectedBillId;
                                        return (
                                            <tr
                                                key={b.id}
                                                onClick={() => setSelectedBillId(b.id ?? null)}
                                                className={`cursor-pointer border-t border-border transition-colors ${
                                                    active
                                                        ? "bg-orange-500/10"
                                                        : "hover:bg-muted/30"
                                                }`}
                                            >
                                                <td className="px-3 py-2 font-mono">
                                                    {b.billNo || b.id}
                                                </td>
                                                <td className="px-3 py-2">
                                                    {b.customer?.name || "—"}
                                                </td>
                                                <td className="px-3 py-2">
                                                    {b.vehicle?.vehicleNumber || "—"}
                                                </td>
                                                <td className="px-3 py-2 text-right">
                                                    ₹{b.netAmount.toFixed(2)}
                                                </td>
                                                <td className="px-3 py-2 text-center">
                                                    <span
                                                        className={`text-xs font-medium px-2 py-0.5 rounded ${
                                                            b.billType === "CREDIT"
                                                                ? "bg-blue-500/10 text-blue-600"
                                                                : "bg-emerald-500/10 text-emerald-600"
                                                        }`}
                                                    >
                                                        {b.billType}
                                                    </span>
                                                </td>
                                                {PHOTO_TYPES.map((t) => (
                                                    <td key={t.key} className="px-2 py-2 text-center">
                                                        {hasType(t.key) ? (
                                                            <CheckCircle2 className="w-4 h-4 text-emerald-500 inline" />
                                                        ) : (
                                                            <CircleDashed className="w-4 h-4 text-muted-foreground/50 inline" />
                                                        )}
                                                    </td>
                                                ))}
                                            </tr>
                                        );
                                    })}
                            </tbody>
                        </table>
                    </div>
                </GlassCard>

                {/* Side pane */}
                <GlassCard className="lg:col-span-2 p-4">
                    {!selectedBill ? (
                        <div className="text-sm text-muted-foreground text-center py-12">
                            Click a bill to view and manage its uploaded images.
                        </div>
                    ) : (
                        <SidePane
                            bill={selectedBill}
                            photos={(selectedBill.id != null && photosByBill[selectedBill.id]) || []}
                            onChanged={() => selectedBill.id != null && refreshPhotosForBill(selectedBill.id)}
                            onClose={() => setSelectedBillId(null)}
                        />
                    )}
                </GlassCard>
            </div>
        </div>
    );
}

function SidePane({
    bill,
    photos,
    onChanged,
    onClose,
}: {
    bill: InvoiceBill;
    photos: InvoicePhoto[];
    onChanged: () => void;
    onClose: () => void;
}) {
    const [activeType, setActiveType] = useState<string>(PHOTO_TYPES[0].key);
    const [previewUrl, setPreviewUrl] = useState<string | null>(null);
    const [previewLoading, setPreviewLoading] = useState(false);
    const [busy, setBusy] = useState(false);
    const fileInputRef = useRef<HTMLInputElement | null>(null);

    const photosOfType = photos.filter((p) => p.photoType === activeType);
    const firstPhoto = photosOfType[0];

    useEffect(() => {
        setPreviewUrl(null);
        if (bill.id == null || !firstPhoto) return;
        let cancelled = false;
        setPreviewLoading(true);
        getInvoiceFileUrl(bill.id, activeType)
            .then((url) => {
                if (!cancelled) setPreviewUrl(url);
            })
            .catch(() => {
                if (!cancelled) setPreviewUrl(null);
            })
            .finally(() => {
                if (!cancelled) setPreviewLoading(false);
            });
        return () => {
            cancelled = true;
        };
    }, [bill.id, activeType, firstPhoto?.id]);

    const doUpload = async (file: File) => {
        if (bill.id == null) return;
        setBusy(true);
        try {
            await uploadInvoiceFile(bill.id, activeType, file);
            onChanged();
        } catch (e) {
            alert("Upload failed");
        } finally {
            setBusy(false);
            if (fileInputRef.current) fileInputRef.current.value = "";
        }
    };

    const doReplace = async (file: File) => {
        if (bill.id == null) return;
        setBusy(true);
        try {
            // delete all existing photos of this type then upload new
            await Promise.all(
                photosOfType.map((p) => deleteInvoicePhoto(bill.id!, p.id))
            );
            await uploadInvoiceFile(bill.id, activeType, file);
            onChanged();
        } catch {
            alert("Replace failed");
        } finally {
            setBusy(false);
            if (fileInputRef.current) fileInputRef.current.value = "";
        }
    };

    const doDelete = async (photoId: number) => {
        if (bill.id == null) return;
        if (!confirm("Delete this image?")) return;
        setBusy(true);
        try {
            await deleteInvoicePhoto(bill.id, photoId);
            onChanged();
        } catch {
            alert("Delete failed");
        } finally {
            setBusy(false);
        }
    };

    const [mode, setMode] = useState<"upload" | "replace">("upload");

    return (
        <div className="space-y-3">
            <div className="flex items-start justify-between gap-2">
                <div>
                    <div className="text-sm font-semibold">
                        Bill {bill.billNo || `#${bill.id}`}
                    </div>
                    <div className="text-xs text-muted-foreground">
                        {bill.customer?.name || "—"}
                        {bill.vehicle?.vehicleNumber ? ` • ${bill.vehicle.vehicleNumber}` : ""} • ₹
                        {bill.netAmount.toFixed(2)} • {bill.billType}
                    </div>
                </div>
                <button
                    onClick={onClose}
                    className="text-muted-foreground hover:text-foreground"
                    title="Close"
                >
                    <X className="w-4 h-4" />
                </button>
            </div>

            {/* tabs */}
            <div className="flex gap-1 border-b border-border">
                {PHOTO_TYPES.map((t) => {
                    const count = photos.filter((p) => p.photoType === t.key).length;
                    const active = activeType === t.key;
                    return (
                        <button
                            key={t.key}
                            onClick={() => setActiveType(t.key)}
                            className={`px-3 py-2 text-sm font-medium border-b-2 transition-colors ${
                                active
                                    ? "border-orange-500 text-orange-500"
                                    : "border-transparent text-muted-foreground hover:text-foreground"
                            }`}
                        >
                            {t.label}
                            {count > 0 && (
                                <span className="ml-1.5 text-xs bg-emerald-500/15 text-emerald-600 rounded px-1.5 py-0.5">
                                    {count}
                                </span>
                            )}
                        </button>
                    );
                })}
            </div>

            {/* preview */}
            <div className="bg-muted/30 border border-border rounded-md min-h-[280px] flex items-center justify-center overflow-hidden">
                {previewLoading ? (
                    <span className="text-xs text-muted-foreground">Loading preview…</span>
                ) : previewUrl ? (
                    <img
                        src={previewUrl}
                        alt="Invoice"
                        className="max-h-[420px] w-auto object-contain"
                    />
                ) : (
                    <span className="text-xs text-muted-foreground">No image uploaded</span>
                )}
            </div>

            {/* photo list with per-photo delete */}
            {photosOfType.length > 0 && (
                <div className="space-y-1">
                    {photosOfType.map((p) => (
                        <div
                            key={p.id}
                            className="flex items-center justify-between text-xs bg-muted/30 rounded px-2 py-1.5"
                        >
                            <span className="truncate">
                                {p.originalFilename || p.s3Key.split("/").pop()}
                            </span>
                            <button
                                onClick={() => doDelete(p.id)}
                                disabled={busy}
                                className="text-destructive hover:text-destructive/80 disabled:opacity-50"
                                title="Delete"
                            >
                                <Trash2 className="w-3.5 h-3.5" />
                            </button>
                        </div>
                    ))}
                </div>
            )}

            {/* upload / replace */}
            <PermissionGate permission="INVOICE_CREATE">
                <div className="flex items-center gap-2 pt-1">
                    <input
                        ref={fileInputRef}
                        type="file"
                        accept="image/*,application/pdf"
                        onChange={(e) => {
                            const f = e.target.files?.[0];
                            if (!f) return;
                            if (mode === "replace" && photosOfType.length > 0) doReplace(f);
                            else doUpload(f);
                        }}
                        className="hidden"
                        id="invoice-upload-input"
                    />
                    <label
                        htmlFor="invoice-upload-input"
                        onClick={() => setMode("upload")}
                        className={`inline-flex items-center gap-1.5 px-3 py-1.5 text-sm rounded-md cursor-pointer transition-colors ${
                            busy
                                ? "bg-muted text-muted-foreground cursor-not-allowed"
                                : "bg-orange-500 text-white hover:bg-orange-600"
                        }`}
                    >
                        <Upload className="w-4 h-4" />
                        {busy ? "Working…" : "Upload"}
                    </label>
                    {photosOfType.length > 0 && (
                        <label
                            htmlFor="invoice-upload-input"
                            onClick={() => setMode("replace")}
                            className="inline-flex items-center gap-1.5 px-3 py-1.5 text-sm rounded-md cursor-pointer border border-border hover:bg-muted transition-colors"
                        >
                            <RefreshCw className="w-4 h-4" /> Replace
                        </label>
                    )}
                </div>
            </PermissionGate>
        </div>
    );
}
