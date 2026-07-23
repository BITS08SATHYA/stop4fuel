"use client";

import { useState, useEffect, useCallback, Fragment } from "react";
import { GlassCard } from "@/components/ui/glass-card";
import { TablePagination } from "@/components/ui/table-pagination";
import { Modal } from "@/components/ui/modal";
import {
    Search, Filter, ChevronDown, ChevronRight,
    Package, RotateCcw, Pencil, Trash2, Plus, X, Save, Hash, Move, RefreshCw
} from "lucide-react";
import { printInvoice, getPrinterTarget, setPrinterTarget, getDotMatrixPrinter, setDotMatrixPrinter, type PrinterTarget } from "@/lib/invoice-print";
import { listPrintAgentPrinters } from "@/lib/print-agent";
import { FormErrorBanner } from "@/components/ui/field-error";
import { StyledSelect } from "@/components/ui/styled-select";
import { PrintMenuButton } from "@/components/ui/print-menu-button";
import { DotMatrixSettings } from "@/components/ui/dotmatrix-settings";
import { PermissionGate } from "@/components/permission-gate";
import {
    getInvoiceHistory, getProductSalesSummary, updateInvoice, deleteInvoice,
    getActiveProducts, getNozzles, getCustomers, getVehiclesByCustomer, searchVehicles,
    getInvoiceSequence, setInvoiceSequence, getActiveShift, moveInvoice,
    getShiftsForMove, recomputeShiftReport, unfinalizeShiftForMove,
    type InvoiceBill, type InvoiceProduct, type PageResponse, type ProductSalesSummary,
    type Product, type Nozzle, type Vehicle, type Customer, type BillSequenceView,
    type CoveringShift,
    API_BASE_URL
} from "@/lib/api/station";
import { fetchWithAuth } from "@/lib/api/fetch-with-auth";
import { useAuth } from "@/lib/auth/auth-context";

interface PostableShift {
    id: number;
    status: string;
    startTime?: string;
    endTime?: string | null;
    attendantName?: string | null;
    reportId?: number | null;
    reportStatus?: string | null;
}

// A shift is a valid move target (bill can be dropped into it) when it's OPEN/REVIEW, or CLOSED
// with a report that isn't FINALIZED (DRAFT or no report). RECONCILED / CLOSED-FINALIZED must be
// un-finalized first. Mirrors the backend moveInvoice target guard.
const isShiftMovable = (s?: PostableShift | null): boolean =>
    !!s && ((s.status === "OPEN" || s.status === "REVIEW") ||
            (s.status === "CLOSED" && s.reportStatus !== "FINALIZED"));

// Format a Date as a <input type="datetime-local"> value (YYYY-MM-DDTHH:mm) in
// LOCAL time. Using toISOString() here emits UTC and made the date filters show
// times 5h30m behind IST.
const toLocalInput = (d: Date): string => {
    const pad = (n: number) => String(n).padStart(2, "0");
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
};

interface EditLine {
    id?: number;
    product: Product | null;
    nozzle: Nozzle | null;
    quantity: string;
    unitPrice: string;
    discountRate: string;
    grossAmount: number;
    discountAmount: number;
    amount: number;
}

export default function InvoiceHistoryPage() {
    const { user } = useAuth();
    const isShiftPickerAllowed = user?.role === "OWNER" || user?.role === "ADMIN";

    const [invoices, setInvoices] = useState<InvoiceBill[]>([]);
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);
    const [loading, setLoading] = useState(true);

    // Admin: move/re-date an existing invoice to the shift that covers the corrected date.
    const [moveInvoiceTarget, setMoveInvoiceTarget] = useState<InvoiceBill | null>(null);
    const [movableShifts, setMovableShifts] = useState<PostableShift[]>([]);
    const [moveTargetShiftId, setMoveTargetShiftId] = useState<number | null>(null);
    const [moveBillDate, setMoveBillDate] = useState<string>("");
    const [moveSubmitting, setMoveSubmitting] = useState(false);
    const [moveError, setMoveError] = useState<string>("");
    // Un-finalize / recompute of the selected shift's report runs inline in the Move dialog.
    const [reportActionBusy, setReportActionBusy] = useState(false);

    const [productSummary, setProductSummary] = useState<ProductSalesSummary[]>([]);
    const [summaryLoading, setSummaryLoading] = useState(true);

    const [expandedRowId, setExpandedRowId] = useState<number | null>(null);
    const [companyInfo, setCompanyInfo] = useState<{ name: string; address: string; phone: string; gstNo: string; site?: string } | null>(null);

    // Printer the row print icons default to. Seeded "thermal" for SSR, then
    // hydrated from the shared remembered choice on mount (same localStorage key
    // as the new-invoice screen). dmPrinter is the MSP 250 Windows printer name.
    const [printTarget, setPrintTarget] = useState<PrinterTarget>("thermal");
    const [dmPrinter, setDmPrinter] = useState("");
    // Windows printers the local agent can see — populates the MSP 250 picker so
    // the cashier selects the exact name instead of typing it. [] when the agent
    // is down/old (no /printers); we then fall back to a free-text field.
    const [agentPrinters, setAgentPrinters] = useState<string[]>([]);
    useEffect(() => { setPrintTarget(getPrinterTarget()); setDmPrinter(getDotMatrixPrinter()); }, []);
    useEffect(() => { listPrintAgentPrinters().then(setAgentPrinters); }, []);

    useEffect(() => {
        fetchWithAuth(`${API_BASE_URL}/companies/print-info`).then(r => r.ok ? r.json() : null).then(c => {
            if (c) {
                setCompanyInfo({ name: c.name, address: c.address, phone: c.phone, gstNo: c.gstNo, site: c.site });
            }
        }).catch(() => {});
    }, []);

    // Date window: FROM = current shift's start time, TO = now (set in the effect
    // below once the active shift loads). Until then, start with a synchronous
    // placeholder of today 00:00 -> now so nothing renders a UTC-shifted time.
    const startOfTodayInit = new Date();
    startOfTodayInit.setHours(0, 0, 0, 0);
    const [filters, setFilters] = useState({
        billType: "",
        paymentStatus: "",
        categoryType: "",
        fromDate: toLocalInput(startOfTodayInit),
        toDate: toLocalInput(new Date()),
        search: "",
    });
    const [appliedFilters, setAppliedFilters] = useState({ ...filters });
    const pageSize = 10;

    // Bill-numbering sequence modal state
    const [seqModal, setSeqModal] = useState(false);
    const [seqType, setSeqType] = useState<'CREDIT' | 'CASH'>('CREDIT');
    const [seqData, setSeqData] = useState<BillSequenceView | null>(null);
    const [seqInput, setSeqInput] = useState("");
    const [seqLoading, setSeqLoading] = useState(false);
    const [seqSubmitting, setSeqSubmitting] = useState(false);
    const [seqError, setSeqError] = useState("");

    const loadSeq = useCallback(async (type: 'CREDIT' | 'CASH') => {
        setSeqLoading(true);
        setSeqError("");
        try {
            const data = await getInvoiceSequence(type);
            setSeqData(data);
            setSeqInput(String(data.nextNumber));
        } catch (e: any) {
            setSeqError(e?.message || "Failed to load sequence");
            setSeqData(null);
        } finally {
            setSeqLoading(false);
        }
    }, []);

    const openSeqModal = async () => {
        setSeqModal(true);
        setSeqType('CREDIT');
        await loadSeq('CREDIT');
    };

    const switchSeqType = async (type: 'CREDIT' | 'CASH') => {
        setSeqType(type);
        await loadSeq(type);
    };

    const submitSeq = async () => {
        const n = Number.parseInt(seqInput, 10);
        if (!Number.isFinite(n) || n < 1) {
            setSeqError("Next number must be a positive integer");
            return;
        }
        const prefix = seqType === 'CREDIT' ? 'A' : 'C';
        setSeqSubmitting(true);
        setSeqError("");
        try {
            const updated = await setInvoiceSequence(seqType, n);
            setSeqData(updated);
            setSeqInput(String(updated.nextNumber));
            setSeqError("");
            setSeqModal(false);
        } catch (e: any) {
            setSeqError(e?.message || `Failed to set ${prefix} sequence`);
        } finally {
            setSeqSubmitting(false);
        }
    };

    // Edit modal state
    const [editModal, setEditModal] = useState(false);
    const [editInvoice, setEditInvoice] = useState<InvoiceBill | null>(null);
    const [editLines, setEditLines] = useState<EditLine[]>([]);
    const [editDriverName, setEditDriverName] = useState("");
    const [editDriverPhone, setEditDriverPhone] = useState("");
    const [editIndentNo, setEditIndentNo] = useState("");
    const [editPaymentMode, setEditPaymentMode] = useState("");
    const [editVehicleKM, setEditVehicleKM] = useState("");
    const [editManualDiscount, setEditManualDiscount] = useState("");
    const [editBillType, setEditBillType] = useState<"CASH" | "CREDIT">("CASH");
    const [editCustomer, setEditCustomer] = useState<{ id: number; name: string; username?: string } | null>(null);
    const [editVehicle, setEditVehicle] = useState<{ id: number; vehicleNumber: string } | null>(null);
    const [editCustomerSearch, setEditCustomerSearch] = useState("");
    const [editCustomerSuggestions, setEditCustomerSuggestions] = useState<Customer[]>([]);
    const [editCustomerVehicles, setEditCustomerVehicles] = useState<Vehicle[]>([]);
    const [editVehicleSearch, setEditVehicleSearch] = useState("");
    const [editVehicleResults, setEditVehicleResults] = useState<Vehicle[]>([]);
    const [saving, setSaving] = useState(false);
    const [editError, setEditError] = useState("");

    // Delete confirm
    const [deleteConfirm, setDeleteConfirm] = useState<InvoiceBill | null>(null);
    const [deleting, setDeleting] = useState(false);

    // Products & Nozzles for edit form
    const [allProducts, setAllProducts] = useState<Product[]>([]);
    const [allNozzles, setAllNozzles] = useState<Nozzle[]>([]);

    useEffect(() => {
        Promise.all([getActiveProducts(), getNozzles()]).then(([p, n]) => {
            setAllProducts(p);
            setAllNozzles(n.filter((nz: Nozzle) => nz.active));
        }).catch(() => {});
    }, []);

    const buildFilterParams = useCallback((f: typeof filters) => {
        const params: any = {};
        if (f.billType) params.billType = f.billType;
        if (f.paymentStatus) params.paymentStatus = f.paymentStatus;
        if (f.categoryType) params.categoryType = f.categoryType;
        if (f.fromDate) params.fromDate = f.fromDate + ":00";
        // :59 so a bill created within the selected end minute (e.g. "now") is included.
        if (f.toDate) params.toDate = f.toDate + ":59";
        if (f.search) params.search = f.search;
        return params;
    }, []);

    const fetchInvoices = useCallback(async (p: number, f: typeof filters) => {
        setLoading(true);
        try {
            const data = await getInvoiceHistory(p, pageSize, buildFilterParams(f));
            setInvoices(data.content);
            setTotalPages(data.totalPages);
            setTotalElements(data.totalElements);
        } catch (e) {
            console.error("Failed to load invoices:", e);
        } finally {
            setLoading(false);
        }
    }, [buildFilterParams]);

    const fetchSummary = useCallback(async (f: typeof filters) => {
        setSummaryLoading(true);
        try {
            const data = await getProductSalesSummary(buildFilterParams(f));
            setProductSummary(data);
        } catch (e) {
            console.error("Failed to load summary:", e);
        } finally {
            setSummaryLoading(false);
        }
    }, [buildFilterParams]);

    // Default the date window to the current shift: FROM = shift start, TO = now.
    // Falls back to today 00:00 -> now when no shift is open.
    const loadDefaultWindow = useCallback(async () => {
        let from = new Date();
        from.setHours(0, 0, 0, 0);
        try {
            const shift = await getActiveShift();
            if (shift?.startTime) from = new Date(shift.startTime);
        } catch { /* no active shift -> keep today 00:00 */ }
        return { fromDate: toLocalInput(from), toDate: toLocalInput(new Date()) };
    }, []);

    useEffect(() => {
        let cancelled = false;
        loadDefaultWindow().then(win => {
            if (cancelled) return;
            setFilters(f => ({ ...f, ...win }));
            setAppliedFilters(f => ({ ...f, ...win }));
        });
        return () => { cancelled = true; };
    }, [loadDefaultWindow]);

    useEffect(() => {
        fetchInvoices(page, appliedFilters);
    }, [page, appliedFilters, fetchInvoices]);

    useEffect(() => {
        fetchSummary(appliedFilters);
    }, [appliedFilters, fetchSummary]);

    const handleApply = () => {
        setPage(0);
        setExpandedRowId(null);
        setAppliedFilters({ ...filters });
    };

    const handleReset = async () => {
        const win = await loadDefaultWindow();
        const defaultFilters = {
            billType: "",
            paymentStatus: "",
            categoryType: "",
            search: "",
            ...win,
        };
        setFilters(defaultFilters);
        setPage(0);
        setExpandedRowId(null);
        setAppliedFilters(defaultFilters);
    };

    const refresh = () => {
        fetchInvoices(page, appliedFilters);
        fetchSummary(appliedFilters);
    };

    // --- Admin: move / re-date an invoice ---
    // Load ALL recent shifts (any status) with report state so the picker lists everything and the
    // admin can un-finalize whichever they select. Maps CoveringShift.shiftId → local .id.
    const reloadMoveShifts = useCallback(async () => {
        const shifts: CoveringShift[] = await getShiftsForMove(100);
        const mapped: PostableShift[] = shifts.map(s => ({
            id: s.shiftId,
            status: s.status,
            startTime: s.startTime,
            endTime: s.endTime ?? null,
            attendantName: s.attendantName ?? null,
            reportId: s.reportId ?? null,
            reportStatus: s.reportStatus ?? null,
        }));
        setMovableShifts(mapped);
        return mapped;
    }, []);

    const openMoveInvoice = async (inv: InvoiceBill) => {
        setMoveInvoiceTarget(inv);
        setMoveTargetShiftId(null);
        setMoveBillDate("");
        setMoveError("");
        try {
            await reloadMoveShifts();
        } catch (e) {
            setMoveError(e instanceof Error ? e.message : "Failed to load shifts");
            setMovableShifts([]);
        }
    };

    const closeMoveInvoice = () => {
        setMoveInvoiceTarget(null);
        setMovableShifts([]);
        setMoveTargetShiftId(null);
        setMoveBillDate("");
        setMoveError("");
    };

    // Un-finalize the selected shift (report → DRAFT, or RECONCILED → CLOSED) so the bill can be
    // moved into it. Reloads the list to pick up the new status and keeps the shift selected.
    const handleUnfinalizeShift = async (shiftId: number) => {
        setReportActionBusy(true);
        setMoveError("");
        try {
            await unfinalizeShiftForMove(shiftId, user?.name || user?.username);
            await reloadMoveShifts();
            setMoveTargetShiftId(shiftId);
        } catch (e) {
            setMoveError(e instanceof Error ? e.message : "Failed to un-finalize shift");
        } finally {
            setReportActionBusy(false);
        }
    };

    // Recompute the selected shift's (DRAFT) report from source data.
    const handleRecomputeShift = async (reportId: number) => {
        setReportActionBusy(true);
        setMoveError("");
        try {
            await recomputeShiftReport(reportId);
            await reloadMoveShifts();
        } catch (e) {
            setMoveError(e instanceof Error ? e.message : "Failed to recompute report");
        } finally {
            setReportActionBusy(false);
        }
    };

    const submitMoveInvoice = async () => {
        if (!moveInvoiceTarget || !moveTargetShiftId || !moveBillDate) return;
        setMoveSubmitting(true);
        setMoveError("");
        try {
            await moveInvoice(moveInvoiceTarget.id!, moveTargetShiftId, moveBillDate);
            closeMoveInvoice();
            refresh();
        } catch (e) {
            setMoveError(e instanceof Error ? e.message : "Failed to move invoice");
        } finally {
            setMoveSubmitting(false);
        }
    };

    const fmt = (n: number) => n.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });

    // --- Edit ---
    const openEdit = async (inv: InvoiceBill) => {
        setEditInvoice(inv);
        setEditDriverName(inv.driverName || "");
        setEditDriverPhone(inv.driverPhone || "");
        setEditIndentNo(inv.indentNo || "");
        setEditPaymentMode(inv.paymentMode || "");
        setEditVehicleKM(inv.vehicleKM ? String(inv.vehicleKM) : "");
        setEditManualDiscount(inv.totalDiscount ? String(inv.totalDiscount) : "");
        setEditBillType((inv.billType as "CASH" | "CREDIT") || "CASH");
        setEditCustomer(inv.customer || null);
        setEditVehicle(inv.vehicle || null);
        setEditCustomerSearch(inv.customer?.name || "");
        setEditCustomerSuggestions([]);
        setEditVehicleSearch("");
        setEditVehicleResults([]);
        setEditError("");
        // Load customer vehicles if customer exists
        if (inv.customer?.id) {
            try {
                const vehicles = await getVehiclesByCustomer(inv.customer.id);
                setEditCustomerVehicles(vehicles);
            } catch { setEditCustomerVehicles([]); }
        } else {
            setEditCustomerVehicles([]);
        }
        setEditLines(
            (inv.products || []).map(ip => ({
                id: ip.id,
                product: ip.productId ? (allProducts.find(p => p.id === ip.productId) || { id: ip.productId, name: ip.productName } as any) : null,
                nozzle: ip.nozzleId ? (allNozzles.find(n => n.id === ip.nozzleId) || { id: ip.nozzleId, nozzleName: ip.nozzleName } as any) : null,
                quantity: ip.quantity != null ? String(ip.quantity) : "",
                unitPrice: ip.unitPrice != null ? String(ip.unitPrice) : "",
                discountRate: ip.discountRate != null ? String(ip.discountRate) : "",
                grossAmount: ip.grossAmount || 0,
                discountAmount: ip.discountAmount || 0,
                amount: ip.amount || 0,
            }))
        );
        setEditModal(true);
    };

    const updateEditLine = (index: number, updates: Partial<EditLine>) => {
        setEditLines(prev => {
            const lines = [...prev];
            const line = { ...lines[index], ...updates };

            const qty = parseFloat(line.quantity) || 0;
            const price = parseFloat(line.unitPrice) || 0;
            const gross = qty * price;
            line.grossAmount = gross;

            const discRate = parseFloat(line.discountRate) || 0;
            if (discRate > 0) {
                const discAmt = discRate * qty;
                line.discountAmount = discAmt;
                line.amount = gross - discAmt;
            } else {
                line.discountAmount = 0;
                line.amount = gross;
            }

            lines[index] = line;
            return lines;
        });
    };

    const addEditLine = () => {
        setEditLines(prev => [...prev, {
            product: null, nozzle: null, quantity: "", unitPrice: "",
            discountRate: "", grossAmount: 0, discountAmount: 0, amount: 0,
        }]);
    };

    const removeEditLine = (index: number) => {
        setEditLines(prev => prev.filter((_, i) => i !== index));
    };

    const editTotalGross = editLines.reduce((s, l) => s + l.grossAmount, 0);
    const editProductDiscount = editLines.reduce((s, l) => s + l.discountAmount, 0);
    const editManualDiscountNum = editBillType === "CASH" && editManualDiscount ? parseFloat(editManualDiscount) || 0 : 0;
    const editTotalDiscount = editProductDiscount + editManualDiscountNum;
    const editNetAmount = editTotalGross - editTotalDiscount;

    const handleSaveEdit = async () => {
        if (!editInvoice?.id) return;
        if (editLines.length === 0) {
            setEditError("At least one product line is required.");
            return;
        }
        setSaving(true);
        setEditError("");
        try {
            await updateInvoice(editInvoice.id, {
                billType: editBillType,
                driverName: editDriverName || undefined,
                driverPhone: editDriverPhone || undefined,
                indentNo: editIndentNo || undefined,
                paymentMode: editPaymentMode || undefined,
                vehicleKM: editVehicleKM ? Number(editVehicleKM) : undefined,
                totalDiscount: editManualDiscountNum > 0 ? editManualDiscountNum : undefined,
                netAmount: editNetAmount > 0 ? editNetAmount : undefined,
                customer: editCustomer ? { id: editCustomer.id } as any : undefined,
                vehicle: editVehicle ? { id: editVehicle.id } as any : undefined,
                products: editLines.map(l => ({
                    product: l.product ? { id: l.product.id } as any : undefined,
                    nozzle: l.nozzle ? { id: l.nozzle.id } as any : undefined,
                    quantity: parseFloat(l.quantity) || 0,
                    unitPrice: parseFloat(l.unitPrice) || 0,
                    amount: l.amount,
                    discountRate: parseFloat(l.discountRate) || undefined,
                })) as any[],
            });
            setEditModal(false);
            refresh();
        } catch (e: any) {
            setEditError(e.message || "Failed to update invoice");
        } finally {
            setSaving(false);
        }
    };

    // --- Delete ---
    const handleDelete = async () => {
        if (!deleteConfirm?.id) return;
        setDeleting(true);
        try {
            await deleteInvoice(deleteConfirm.id);
            setDeleteConfirm(null);
            setExpandedRowId(null);
            refresh();
        } catch (e: any) {
            setEditError(e.message || "Failed to delete invoice");
        } finally {
            setDeleting(false);
        }
    };

    return (
        <div className="p-6 space-y-6 max-w-[1400px] mx-auto">
            {/* Header */}
            <div className="flex items-start justify-between gap-3">
                <div>
                    <h1 className="text-2xl font-bold text-foreground">Invoice History</h1>
                    <p className="text-sm text-muted-foreground mt-1">Browse, filter, edit, and analyze all invoices</p>
                </div>
                <PermissionGate permission="INVOICE_VIEW">
                    <button
                        onClick={openSeqModal}
                        title="View / set the next bill number"
                        className="shrink-0 flex items-center gap-2 px-3 py-2 text-sm font-medium rounded-lg border border-border text-muted-foreground hover:bg-muted transition-colors"
                    >
                        <Hash className="w-4 h-4" />
                        Bill numbering
                    </button>
                </PermissionGate>
            </div>

            {/* Filter Bar */}
            <GlassCard className="p-4 relative z-10">
                <div className="flex flex-wrap items-end gap-3">
                    <div className="flex-1 min-w-[180px]">
                        <label className="block text-[10px] font-bold uppercase tracking-widest text-muted-foreground mb-1">From Date</label>
                        <input
                            type="datetime-local"
                            value={filters.fromDate}
                            onChange={e => setFilters(f => ({ ...f, fromDate: e.target.value }))}
                            className="w-full px-3 py-2 text-sm rounded-lg border border-border bg-background text-foreground"
                        />
                    </div>
                    <div className="flex-1 min-w-[180px]">
                        <label className="block text-[10px] font-bold uppercase tracking-widest text-muted-foreground mb-1">To Date</label>
                        <input
                            type="datetime-local"
                            value={filters.toDate}
                            onChange={e => setFilters(f => ({ ...f, toDate: e.target.value }))}
                            className="w-full px-3 py-2 text-sm rounded-lg border border-border bg-background text-foreground"
                        />
                    </div>
                    <div className="min-w-[130px]">
                        <label className="block text-[10px] font-bold uppercase tracking-widest text-muted-foreground mb-1">Bill Type</label>
                        <StyledSelect
                            value={filters.billType}
                            onChange={(val) => setFilters(f => ({ ...f, billType: val }))}
                            options={[
                                { value: "", label: "All" },
                                { value: "CASH", label: "Cash" },
                                { value: "CREDIT", label: "Credit" },
                            ]}
                        />
                    </div>
                    <div className="min-w-[140px]">
                        <label className="block text-[10px] font-bold uppercase tracking-widest text-muted-foreground mb-1">Payment Status</label>
                        <StyledSelect
                            value={filters.paymentStatus}
                            onChange={(val) => setFilters(f => ({ ...f, paymentStatus: val }))}
                            options={[
                                { value: "", label: "All" },
                                { value: "PAID", label: "Paid" },
                                { value: "NOT_PAID", label: "Not Paid" },
                            ]}
                        />
                    </div>
                    <div className="min-w-[150px]">
                        <label className="block text-[10px] font-bold uppercase tracking-widest text-muted-foreground mb-1">Category</label>
                        <StyledSelect
                            value={filters.categoryType}
                            onChange={(val) => setFilters(f => ({ ...f, categoryType: val }))}
                            options={[
                                { value: "", label: "All" },
                                { value: "GOVERNMENT", label: "Government" },
                                { value: "NON_GOVERNMENT", label: "Non-Government" },
                            ]}
                        />
                    </div>
                    <div className="flex-1 min-w-[200px]">
                        <label className="block text-[10px] font-bold uppercase tracking-widest text-muted-foreground mb-1">Search</label>
                        <div className="relative">
                            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                            <input
                                type="text"
                                placeholder="Bill no, customer, vehicle..."
                                value={filters.search}
                                onChange={e => setFilters(f => ({ ...f, search: e.target.value }))}
                                onKeyDown={e => e.key === 'Enter' && handleApply()}
                                className="w-full pl-9 pr-3 py-2 text-sm rounded-lg border border-border bg-background text-foreground"
                            />
                        </div>
                    </div>
                    <button onClick={handleApply} className="px-4 py-2 text-sm font-medium rounded-lg bg-primary text-primary-foreground hover:bg-primary/90 transition-colors">
                        <Filter className="w-4 h-4 inline mr-1" />Apply
                    </button>
                    <button onClick={handleReset} className="px-4 py-2 text-sm font-medium rounded-lg border border-border text-muted-foreground hover:bg-muted transition-colors">
                        <RotateCcw className="w-4 h-4 inline mr-1" />Reset
                    </button>
                    {/* Printer preference — the default the row print icons use. Not a
                        filter, so it sits past the Apply/Reset and is unaffected by Reset. */}
                    <div className="min-w-[170px]">
                        <label className="block text-[10px] font-bold uppercase tracking-widest text-muted-foreground mb-1">Printer</label>
                        <StyledSelect
                            value={printTarget}
                            onChange={(val) => { setPrintTarget(val as PrinterTarget); setPrinterTarget(val as PrinterTarget); }}
                            options={[
                                { value: "thermal", label: "Thermal — RP 3230" },
                                { value: "dotmatrix", label: "Dot-matrix — MSP 250" },
                            ]}
                        />
                    </div>
                    {/* MSP 250 target printer. Always shown (not gated on the default
                        target) so per-row dot-matrix overrides also route correctly.
                        When the agent lists printers we offer a dropdown of exact
                        Windows names; otherwise (agent down/old) we fall back to a
                        free-text field. */}
                    <div className="min-w-[200px]">
                        <label className="block text-[10px] font-bold uppercase tracking-widest text-muted-foreground mb-1">Dot-matrix printer</label>
                        {agentPrinters.length > 0 ? (
                            <StyledSelect
                                value={dmPrinter}
                                onChange={(val) => { setDmPrinter(val); setDotMatrixPrinter(val); }}
                                options={[
                                    { value: "", label: "Agent default" },
                                    ...agentPrinters.map(p => ({ value: p, label: p })),
                                ]}
                            />
                        ) : (
                            <input
                                type="text"
                                value={dmPrinter}
                                onChange={e => setDmPrinter(e.target.value)}
                                onBlur={() => setDotMatrixPrinter(dmPrinter)}
                                placeholder="Windows printer name (optional)"
                                title="Exact Windows printer name for the MSP 250. Leave blank to use the agent's default printer."
                                className="w-full px-3 py-2 text-sm rounded-lg border border-border bg-background text-foreground"
                            />
                        )}
                    </div>
                    {/* Speed + alignment for the pre-printed MSP 250 slip. Sits
                        beside the printer pickers because it is the same job:
                        getting a bill onto the right paper, in the right place. */}
                    <div className="self-end pb-[1px]">
                        <DotMatrixSettings company={companyInfo} />
                    </div>
                </div>
            </GlassCard>

            {/* Product Sales Summary */}
            <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3">
                {summaryLoading ? (
                    Array.from({ length: 3 }).map((_, i) => (
                        <GlassCard key={i} className="p-4 animate-pulse">
                            <div className="h-4 bg-muted rounded w-2/3 mb-3" />
                            <div className="h-6 bg-muted rounded w-1/2 mb-2" />
                            <div className="h-4 bg-muted rounded w-3/4" />
                        </GlassCard>
                    ))
                ) : productSummary.length > 0 ? (
                    productSummary.map(ps => (
                        <GlassCard key={ps.productId} className="p-4">
                            <div className="flex items-center gap-2 mb-2">
                                <Package className="w-4 h-4 text-primary" />
                                <span className="text-xs font-bold text-muted-foreground uppercase tracking-wider">{ps.productName}</span>
                            </div>
                            <div className="text-lg font-bold text-foreground">{fmt(ps.totalQuantity)} L</div>
                            <div className="text-sm text-muted-foreground mt-1">₹{fmt(ps.totalAmount)}</div>
                            {ps.totalDiscount > 0 && (
                                <div className="text-[10px] text-orange-500 mt-1">Disc: ₹{fmt(ps.totalDiscount)}</div>
                            )}
                        </GlassCard>
                    ))
                ) : (
                    <div className="col-span-full text-sm text-muted-foreground text-center py-4">No product sales in this range</div>
                )}
            </div>

            {/* Invoice Table */}
            <GlassCard className="overflow-hidden border-none p-0">
                <div className="overflow-x-auto">
                    <table className="w-full text-left border-collapse">
                        <thead>
                            <tr className="bg-white/5 border-b border-border/50">
                                <th className="px-4 py-3 w-8" />
                                <th className="px-4 py-3 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Bill No</th>
                                <th className="px-4 py-3 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Date / Time</th>
                                <th className="px-4 py-3 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Customer</th>
                                <th className="px-4 py-3 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Vehicle</th>
                                <th className="px-4 py-3 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Type</th>
                                <th className="px-4 py-3 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Payment</th>
                                <th className="px-4 py-3 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Product</th>
                                <th className="px-4 py-3 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right">Qty (L)</th>
                                <th className="px-4 py-3 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right">Net Amount</th>
                                <th className="px-4 py-3 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center">Actions</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-border/30">
                            {loading ? (
                                Array.from({ length: 5 }).map((_, i) => (
                                    <tr key={i} className="animate-pulse">
                                        <td className="px-4 py-4" colSpan={11}>
                                            <div className="h-4 bg-muted rounded w-full" />
                                        </td>
                                    </tr>
                                ))
                            ) : invoices.length === 0 ? (
                                <tr>
                                    <td colSpan={11} className="px-4 py-12 text-center text-muted-foreground">
                                        No invoices found for the selected filters.
                                    </td>
                                </tr>
                            ) : (
                                invoices.map(inv => {
                                    const isExpanded = expandedRowId === inv.id;
                                    return (
                                        <Fragment key={inv.id}>
                                            <tr
                                                onClick={() => setExpandedRowId(isExpanded ? null : (inv.id ?? null))}
                                                className="hover:bg-white/5 transition-colors cursor-pointer"
                                            >
                                                <td className="px-4 py-3 text-muted-foreground">
                                                    {isExpanded ? <ChevronDown className="w-4 h-4" /> : <ChevronRight className="w-4 h-4" />}
                                                </td>
                                                <td className="px-4 py-3 font-mono font-bold text-foreground text-sm">{inv.billNo || "—"}</td>
                                                <td className="px-4 py-3">
                                                    <div className="text-sm font-medium text-foreground">{new Date(inv.date).toLocaleDateString()}</div>
                                                    <div className="text-[10px] text-muted-foreground font-mono">{new Date(inv.date).toLocaleTimeString()}</div>
                                                </td>
                                                <td className="px-4 py-3 text-sm text-foreground">{inv.customer?.name || inv.signatoryName || <span className="text-muted-foreground italic">Walk-in</span>}</td>
                                                <td className="px-4 py-3 text-sm font-mono text-foreground">{inv.vehicle?.vehicleNumber || inv.billDesc || "—"}</td>
                                                <td className="px-4 py-3">
                                                    <span className={`px-2 py-0.5 rounded-full text-[10px] font-bold uppercase ${
                                                        inv.billType === 'CASH'
                                                            ? 'bg-green-500/10 text-green-500 border border-green-500/20'
                                                            : 'bg-blue-500/10 text-blue-500 border border-blue-500/20'
                                                    }`}>{inv.billType}</span>
                                                </td>
                                                <td className="px-4 py-3">
                                                    <span className={`px-2 py-0.5 rounded-full text-[10px] font-bold uppercase ${
                                                        inv.paymentStatus === 'PAID'
                                                            ? 'bg-emerald-500/10 text-emerald-500 border border-emerald-500/20'
                                                            : 'bg-amber-500/10 text-amber-500 border border-amber-500/20'
                                                    }`}>{inv.paymentStatus === 'NOT_PAID' ? 'Unpaid' : 'Paid'}</span>
                                                </td>
                                                <td className="px-4 py-3 text-sm text-foreground truncate max-w-[160px]" title={inv.products?.map(p => p.productName).join(', ')}>
                                                    {inv.products && inv.products.length > 0
                                                        ? inv.products.length === 1
                                                            ? inv.products[0].productName
                                                            : `${inv.products[0].productName} +${inv.products.length - 1}`
                                                        : "—"}
                                                </td>
                                                <td className="px-4 py-3 text-right font-mono text-sm text-foreground">
                                                    {inv.products && inv.products.length > 0
                                                        ? inv.products.reduce((sum, p) => sum + (p.quantity || 0), 0).toFixed(2)
                                                        : "—"}
                                                </td>
                                                <td className="px-4 py-3 text-right font-bold text-foreground">
                                                    ₹{fmt(inv.netAmount || 0)}
                                                </td>
                                                <td className="px-4 py-3 text-center" onClick={e => e.stopPropagation()}>
                                                    <div className="flex items-center justify-center gap-1">
                                                        {companyInfo && (
                                                            <PrintMenuButton
                                                                defaultTarget={printTarget}
                                                                onPrint={(t) => printInvoice(inv, companyInfo, t)}
                                                            />
                                                        )}
                                                        <PermissionGate permission="INVOICE_MODIFY">
                                                            <button
                                                                onClick={() => openEdit(inv)}
                                                                className="p-1.5 rounded-md text-muted-foreground hover:text-primary hover:bg-primary/10 transition-colors"
                                                                title="Edit"
                                                            >
                                                                <Pencil className="w-3.5 h-3.5" />
                                                            </button>
                                                        </PermissionGate>
                                                        {isShiftPickerAllowed && (
                                                            <button
                                                                onClick={() => openMoveInvoice(inv)}
                                                                className="p-1.5 rounded-md text-muted-foreground hover:text-amber-500 hover:bg-amber-500/10 transition-colors"
                                                                title="Change date / move to correct shift (admin)"
                                                            >
                                                                <Move className="w-3.5 h-3.5" />
                                                            </button>
                                                        )}
                                                        <PermissionGate permission="INVOICE_DELETE">
                                                            <button
                                                                onClick={() => setDeleteConfirm(inv)}
                                                                className="p-1.5 rounded-md text-muted-foreground hover:text-red-500 hover:bg-red-500/10 transition-colors"
                                                                title="Delete"
                                                            >
                                                                <Trash2 className="w-3.5 h-3.5" />
                                                            </button>
                                                        </PermissionGate>
                                                    </div>
                                                </td>
                                            </tr>
                                            {isExpanded && (
                                                <tr key={`${inv.id}-detail`} className="bg-muted/30">
                                                    <td colSpan={11} className="px-6 py-4">
                                                        {inv.products && inv.products.length > 0 && (
                                                            <div className="mb-3">
                                                                <div className="text-[10px] font-bold uppercase tracking-widest text-muted-foreground mb-2">Product Details</div>
                                                                <table className="w-full text-sm">
                                                                    <thead>
                                                                        <tr className="text-[10px] font-bold uppercase tracking-widest text-muted-foreground">
                                                                            <th className="text-left py-1 pr-4">Product</th>
                                                                            <th className="text-left py-1 pr-4">Nozzle</th>
                                                                            <th className="text-right py-1 pr-4">Qty (L)</th>
                                                                            <th className="text-right py-1 pr-4">Rate</th>
                                                                            <th className="text-right py-1 pr-4">Gross</th>
                                                                            <th className="text-right py-1 pr-4">Discount</th>
                                                                            <th className="text-right py-1">Amount</th>
                                                                        </tr>
                                                                    </thead>
                                                                    <tbody>
                                                                        {inv.products.map((ip, idx) => (
                                                                            <tr key={idx} className="border-t border-border/20">
                                                                                <td className="py-1.5 pr-4 text-foreground">{ip.productName || "—"}</td>
                                                                                <td className="py-1.5 pr-4 font-mono text-muted-foreground">{ip.nozzleName || "—"}</td>
                                                                                <td className="py-1.5 pr-4 text-right font-mono">{ip.quantity?.toFixed(2)}</td>
                                                                                <td className="py-1.5 pr-4 text-right font-mono">₹{ip.unitPrice?.toFixed(2)}</td>
                                                                                <td className="py-1.5 pr-4 text-right font-mono">₹{(ip.grossAmount || 0).toFixed(2)}</td>
                                                                                <td className="py-1.5 pr-4 text-right font-mono text-orange-500">
                                                                                    {ip.discountAmount ? `₹${ip.discountAmount.toFixed(2)}` : "—"}
                                                                                </td>
                                                                                <td className="py-1.5 text-right font-mono font-bold">₹{ip.amount?.toFixed(2)}</td>
                                                                            </tr>
                                                                        ))}
                                                                    </tbody>
                                                                </table>
                                                                {/* Totals row */}
                                                                <div className="flex justify-end gap-6 mt-2 pt-2 border-t border-border/30 text-sm font-mono">
                                                                    <span className="text-muted-foreground">Gross: <strong className="text-foreground">₹{fmt(inv.grossAmount || 0)}</strong></span>
                                                                    {(inv.totalDiscount || 0) > 0 && (
                                                                        <span className="text-orange-500">Discount: <strong>₹{fmt(inv.totalDiscount || 0)}</strong></span>
                                                                    )}
                                                                    <span className="text-foreground">Net: <strong className="text-primary">₹{fmt(inv.netAmount || 0)}</strong></span>
                                                                </div>
                                                            </div>
                                                        )}
                                                        <div className="flex flex-wrap gap-4 text-xs text-muted-foreground">
                                                            {inv.driverName && <span><strong>Driver:</strong> {inv.driverName} {inv.driverPhone && `(${inv.driverPhone})`}</span>}
                                                            {inv.indentNo && <span><strong>Indent No:</strong> {inv.indentNo}</span>}
                                                            {inv.paymentMode && <span><strong>Payment Mode:</strong> {inv.paymentMode}</span>}
                                                            {inv.vehicleKM && <span><strong>KM:</strong> {inv.vehicleKM}</span>}
                                                        </div>
                                                    </td>
                                                </tr>
                                            )}
                                        </Fragment>
                                    );
                                })
                            )}
                        </tbody>
                    </table>
                </div>

                {/* Pagination */}
                <TablePagination
                    page={page}
                    totalPages={totalPages}
                    totalElements={totalElements}
                    pageSize={pageSize}
                    onPageChange={(p) => { setPage(p); setExpandedRowId(null); }}
                />
            </GlassCard>

            {/* Edit Invoice Modal */}
            <Modal isOpen={editModal} onClose={() => setEditModal(false)} title={`Edit Invoice — ${editInvoice?.billNo || ''}`}>
                <div className="space-y-4 max-h-[70vh] overflow-y-auto">
                    {editError && (
                        <div className="p-3 text-sm text-red-500 bg-red-500/10 border border-red-500/20 rounded-lg">{editError}</div>
                    )}

                    {/* Bill Type Toggle */}
                    <div>
                        <label className="block text-[9px] font-bold uppercase text-muted-foreground mb-1">Bill Type</label>
                        <div className="flex gap-2">
                            <button type="button" onClick={() => setEditBillType("CASH")}
                                className={`px-4 py-1.5 text-sm font-medium rounded-lg border transition-colors ${editBillType === "CASH" ? "bg-primary text-primary-foreground border-primary" : "bg-muted border-border text-muted-foreground"}`}>
                                Cash
                            </button>
                            <button type="button" onClick={() => setEditBillType("CREDIT")}
                                className={`px-4 py-1.5 text-sm font-medium rounded-lg border transition-colors ${editBillType === "CREDIT" ? "bg-primary text-primary-foreground border-primary" : "bg-muted border-border text-muted-foreground"}`}>
                                Credit
                            </button>
                        </div>
                    </div>

                    {/* Customer & Vehicle */}
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                        <div className="relative">
                            <label className="block text-[9px] font-bold uppercase text-muted-foreground mb-0.5">Customer</label>
                            <input
                                value={editCustomerSearch}
                                onChange={async (e) => {
                                    const q = e.target.value;
                                    setEditCustomerSearch(q);
                                    if (q.length >= 2) {
                                        try {
                                            const res = await getCustomers(q);
                                            setEditCustomerSuggestions(Array.isArray(res) ? res : res.content || []);
                                        } catch { setEditCustomerSuggestions([]); }
                                    } else {
                                        setEditCustomerSuggestions([]);
                                    }
                                }}
                                placeholder={editCustomer ? editCustomer.name : "Search customer..."}
                                className="w-full px-2 py-1.5 text-sm rounded border border-border bg-background text-foreground"
                            />
                            {editCustomerSuggestions.length > 0 && (
                                <div className="absolute z-50 w-full mt-1 bg-background border border-border rounded-lg shadow-lg max-h-40 overflow-y-auto">
                                    {editCustomerSuggestions.map((c: any) => (
                                        <button key={c.id} type="button"
                                            onClick={async () => {
                                                setEditCustomer(c);
                                                setEditCustomerSearch(c.name);
                                                setEditCustomerSuggestions([]);
                                                setEditVehicle(null);
                                                try {
                                                    const vehicles = await getVehiclesByCustomer(c.id);
                                                    setEditCustomerVehicles(vehicles);
                                                } catch { setEditCustomerVehicles([]); }
                                            }}
                                            className="w-full px-3 py-2 text-left text-sm hover:bg-muted transition-colors">
                                            {c.name} {c.phone && <span className="text-muted-foreground ml-1">({c.phone})</span>}
                                        </button>
                                    ))}
                                </div>
                            )}
                            {editCustomer && (
                                <button type="button" onClick={() => { setEditCustomer(null); setEditCustomerSearch(""); setEditCustomerVehicles([]); setEditVehicle(null); }}
                                    className="absolute right-2 top-6 p-0.5 text-muted-foreground hover:text-red-500">
                                    <X className="w-3 h-3" />
                                </button>
                            )}
                        </div>
                        <div>
                            <label className="block text-[9px] font-bold uppercase text-muted-foreground mb-0.5">Vehicle</label>
                            <StyledSelect
                                value={String(editVehicle?.id || "")}
                                onChange={(val) => {
                                    const vid = Number(val);
                                    const v = editCustomerVehicles.find((v: any) => v.id === vid) || editVehicleResults.find(v => v.id === vid) || null;
                                    setEditVehicle(v);
                                }}
                                options={[
                                    { value: "", label: "— No vehicle —" },
                                    ...editCustomerVehicles.map((v: any) => ({ value: String(v.id), label: v.vehicleNumber })),
                                ]}
                            />
                            {/* Vehicle search for non-customer vehicles */}
                            <input
                                value={editVehicleSearch}
                                onChange={async (e) => {
                                    const q = e.target.value;
                                    setEditVehicleSearch(q);
                                    if (q.length >= 3) {
                                        try {
                                            const results = await searchVehicles(q);
                                            const custIds = new Set(editCustomerVehicles.map((v: any) => v.id));
                                            setEditVehicleResults(results.filter(v => !custIds.has(v.id)));
                                        } catch { setEditVehicleResults([]); }
                                    } else { setEditVehicleResults([]); }
                                }}
                                placeholder="Search other vehicle..."
                                className="w-full px-2 py-1.5 text-sm rounded border border-border bg-background text-foreground mt-1"
                            />
                            {editVehicleResults.length > 0 && (
                                <div className="mt-1 bg-background border border-border rounded-lg shadow-lg max-h-32 overflow-y-auto">
                                    {editVehicleResults.map(v => (
                                        <button key={v.id} type="button"
                                            onClick={() => { setEditVehicle(v); setEditVehicleSearch(""); setEditVehicleResults([]); }}
                                            className="w-full px-3 py-1.5 text-left text-sm hover:bg-muted transition-colors">
                                            {v.vehicleNumber} {(v as any).customer && <span className="text-muted-foreground ml-1">({(v as any).customer.name})</span>}
                                        </button>
                                    ))}
                                </div>
                            )}
                        </div>
                    </div>

                    {/* Product Lines */}
                    <div>
                        <div className="flex items-center justify-between mb-2">
                            <span className="text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Product Line Items</span>
                            <button onClick={addEditLine} className="flex items-center gap-1 px-2 py-1 text-xs font-medium rounded-md bg-primary/10 text-primary hover:bg-primary/20 transition-colors">
                                <Plus className="w-3 h-3" /> Add Line
                            </button>
                        </div>

                        <div className="space-y-2">
                            {editLines.map((line, idx) => (
                                <div key={idx} className="grid grid-cols-12 gap-2 items-end p-3 rounded-lg bg-muted/30 border border-border/30">
                                    <div className="col-span-3">
                                        <label className="block text-[9px] font-bold uppercase text-muted-foreground mb-0.5">Product</label>
                                        <StyledSelect
                                            value={String(line.product?.id || "")}
                                            onChange={(val) => {
                                                const p = allProducts.find(pr => pr.id === Number(val)) || null;
                                                updateEditLine(idx, {
                                                    product: p,
                                                    unitPrice: p ? String(p.price) : line.unitPrice,
                                                });
                                            }}
                                            options={[
                                                { value: "", label: "Select..." },
                                                ...allProducts.map(p => ({ value: String(p.id), label: p.name })),
                                            ]}
                                        />
                                    </div>
                                    <div className="col-span-2">
                                        <label className="block text-[9px] font-bold uppercase text-muted-foreground mb-0.5">Nozzle</label>
                                        <StyledSelect
                                            value={String(line.nozzle?.id || "")}
                                            onChange={(val) => {
                                                const n = allNozzles.find(nz => nz.id === Number(val)) || null;
                                                updateEditLine(idx, { nozzle: n });
                                            }}
                                            options={[
                                                { value: "", label: "—" },
                                                ...allNozzles.map(n => ({ value: String(n.id), label: n.nozzleName })),
                                            ]}
                                        />
                                    </div>
                                    <div className="col-span-1">
                                        <label className="block text-[9px] font-bold uppercase text-muted-foreground mb-0.5">Qty</label>
                                        <input
                                            type="number" step="0.01" value={line.quantity}
                                            onChange={e => updateEditLine(idx, { quantity: e.target.value })}
                                            className="w-full px-2 py-1.5 text-sm rounded border border-border bg-background text-foreground text-right"
                                        />
                                    </div>
                                    <div className="col-span-1">
                                        <label className="block text-[9px] font-bold uppercase text-muted-foreground mb-0.5">Rate</label>
                                        <input
                                            type="number" step="0.01" value={line.unitPrice}
                                            onChange={e => updateEditLine(idx, { unitPrice: e.target.value })}
                                            className="w-full px-2 py-1.5 text-sm rounded border border-border bg-background text-foreground text-right"
                                        />
                                    </div>
                                    <div className="col-span-1">
                                        <label className="block text-[9px] font-bold uppercase text-muted-foreground mb-0.5">Disc/L</label>
                                        <input
                                            type="number" step="0.01" value={line.discountRate}
                                            onChange={e => updateEditLine(idx, { discountRate: e.target.value })}
                                            className="w-full px-2 py-1.5 text-sm rounded border border-border bg-background text-foreground text-right"
                                            placeholder="0"
                                        />
                                    </div>
                                    <div className="col-span-1">
                                        <label className="block text-[9px] font-bold uppercase text-muted-foreground mb-0.5">Gross</label>
                                        <div className="px-2 py-1.5 text-sm font-mono text-muted-foreground text-right">₹{line.grossAmount.toFixed(2)}</div>
                                    </div>
                                    <div className="col-span-1">
                                        <label className="block text-[9px] font-bold uppercase text-muted-foreground mb-0.5">Disc</label>
                                        <div className="px-2 py-1.5 text-sm font-mono text-orange-500 text-right">{line.discountAmount > 0 ? `₹${line.discountAmount.toFixed(2)}` : "—"}</div>
                                    </div>
                                    <div className="col-span-1">
                                        <label className="block text-[9px] font-bold uppercase text-muted-foreground mb-0.5">Amount</label>
                                        <div className="px-2 py-1.5 text-sm font-mono font-bold text-foreground text-right">₹{line.amount.toFixed(2)}</div>
                                    </div>
                                    <div className="col-span-1 flex items-end justify-center">
                                        <button onClick={() => removeEditLine(idx)} className="p-1.5 text-muted-foreground hover:text-red-500 transition-colors">
                                            <X className="w-4 h-4" />
                                        </button>
                                    </div>
                                </div>
                            ))}
                        </div>

                        {/* Manual Discount (CASH only) */}
                        {editBillType === "CASH" && (
                            <div className="flex items-center gap-3 mt-3 pt-3 border-t border-border/50">
                                <label className="text-xs font-bold uppercase text-muted-foreground whitespace-nowrap">Discount (₹)</label>
                                <input
                                    type="number"
                                    step="0.01"
                                    min="0"
                                    value={editManualDiscount}
                                    onChange={e => setEditManualDiscount(e.target.value)}
                                    placeholder="0.00"
                                    className="w-32 px-2 py-1.5 text-sm rounded border border-border bg-background text-foreground font-mono"
                                />
                            </div>
                        )}

                        {/* Totals */}
                        <div className="flex justify-end gap-6 mt-3 pt-3 border-t border-border/50 text-sm font-mono">
                            <span className="text-muted-foreground">Gross: <strong className="text-foreground">₹{fmt(editTotalGross)}</strong></span>
                            {editTotalDiscount > 0 && (
                                <span className="text-orange-500">Discount: <strong>₹{fmt(editTotalDiscount)}</strong></span>
                            )}
                            <span className="text-foreground text-base">Net Amount: <strong className="text-primary">₹{fmt(editNetAmount)}</strong></span>
                        </div>
                    </div>

                    {/* Other fields */}
                    <div className="grid grid-cols-2 md:grid-cols-4 gap-3 pt-2 border-t border-border/30">
                        <div>
                            <label className="block text-[9px] font-bold uppercase text-muted-foreground mb-0.5">Driver Name</label>
                            <input value={editDriverName} onChange={e => setEditDriverName(e.target.value)}
                                className="w-full px-2 py-1.5 text-sm rounded border border-border bg-background text-foreground" />
                        </div>
                        <div>
                            <label className="block text-[9px] font-bold uppercase text-muted-foreground mb-0.5">Driver Phone</label>
                            <input value={editDriverPhone} onChange={e => setEditDriverPhone(e.target.value)}
                                className="w-full px-2 py-1.5 text-sm rounded border border-border bg-background text-foreground" />
                        </div>
                        <div>
                            <label className="block text-[9px] font-bold uppercase text-muted-foreground mb-0.5">Indent No</label>
                            <input value={editIndentNo} onChange={e => setEditIndentNo(e.target.value)}
                                className="w-full px-2 py-1.5 text-sm rounded border border-border bg-background text-foreground" />
                        </div>
                        <div>
                            <label className="block text-[9px] font-bold uppercase text-muted-foreground mb-0.5">Vehicle KM</label>
                            <input type="number" value={editVehicleKM} onChange={e => setEditVehicleKM(e.target.value)}
                                className="w-full px-2 py-1.5 text-sm rounded border border-border bg-background text-foreground" />
                        </div>
                    </div>

                    {/* Save button */}
                    <div className="flex justify-end gap-2 pt-3">
                        <button onClick={() => setEditModal(false)}
                            className="px-4 py-2 text-sm rounded-lg border border-border text-muted-foreground hover:bg-muted transition-colors">
                            Cancel
                        </button>
                        <button onClick={handleSaveEdit} disabled={saving}
                            className="flex items-center gap-2 px-4 py-2 text-sm font-medium rounded-lg bg-primary text-primary-foreground hover:bg-primary/90 disabled:opacity-50 transition-colors">
                            <Save className="w-4 h-4" />
                            {saving ? "Saving..." : "Save Changes"}
                        </button>
                    </div>
                </div>
            </Modal>

            {/* Delete Confirmation Modal */}
            <Modal isOpen={!!deleteConfirm} onClose={() => setDeleteConfirm(null)} title="Delete Invoice">
                <div className="space-y-4">
                    <FormErrorBanner message={editError} onDismiss={() => setEditError("")} />
                    <p className="text-sm text-foreground">
                        Are you sure you want to delete invoice <strong className="font-mono">{deleteConfirm?.billNo}</strong>?
                        This action cannot be undone.
                    </p>
                    {deleteConfirm && (
                        <div className="p-3 rounded-lg bg-muted/50 text-sm space-y-1">
                            <div>Customer: <strong>{deleteConfirm.customer?.name || "Walk-in"}</strong></div>
                            <div>Amount: <strong>₹{fmt(deleteConfirm.netAmount || 0)}</strong></div>
                            <div>Date: <strong>{new Date(deleteConfirm.date).toLocaleString()}</strong></div>
                        </div>
                    )}
                    <div className="flex justify-end gap-2">
                        <button onClick={() => setDeleteConfirm(null)}
                            className="px-4 py-2 text-sm rounded-lg border border-border text-muted-foreground hover:bg-muted transition-colors">
                            Cancel
                        </button>
                        <button onClick={handleDelete} disabled={deleting}
                            className="flex items-center gap-2 px-4 py-2 text-sm font-medium rounded-lg bg-red-500 text-white hover:bg-red-600 disabled:opacity-50 transition-colors">
                            <Trash2 className="w-4 h-4" />
                            {deleting ? "Deleting..." : "Delete Invoice"}
                        </button>
                    </div>
                </div>
            </Modal>

            {/* Move / Change Date Modal (admin) */}
            <Modal isOpen={!!moveInvoiceTarget} onClose={closeMoveInvoice} title={`Change Date / Move — ${moveInvoiceTarget?.billNo || ""}`}>
                {moveInvoiceTarget && (() => {
                    const selected = movableShifts.find(s => s.id === moveTargetShiftId);
                    const selectedStart = selected?.startTime ? new Date(selected.startTime) : null;
                    const selectedEndRaw = selected?.endTime ? new Date(selected.endTime) : null;
                    const selectedEnd = selectedEndRaw ?? new Date();
                    const toLocalIn = (d: Date) => {
                        const tzOffsetMs = d.getTimezoneOffset() * 60_000;
                        return new Date(d.getTime() - tzOffsetMs).toISOString().slice(0, 16);
                    };
                    const maxAttr = toLocalIn(selectedEnd);
                    const billDateValid = (() => {
                        if (!moveBillDate || !selectedStart) return false;
                        const d = new Date(moveBillDate);
                        if (Number.isNaN(d.getTime())) return false;
                        return d >= selectedStart && d <= selectedEnd;
                    })();
                    const selectedMovable = isShiftMovable(selected);
                    // Date-first: given the corrected date, find the shift whose window covers it (any status).
                    const coveringShiftFor = (dateStr: string) => {
                        if (!dateStr) return null;
                        const d = new Date(dateStr);
                        if (Number.isNaN(d.getTime())) return null;
                        return movableShifts.find(s => {
                            if (s.id === moveInvoiceTarget.shiftId) return false;
                            const st = s.startTime ? new Date(s.startTime) : null;
                            const en = s.endTime ? new Date(s.endTime) : new Date();
                            return st != null && d >= st && d <= en;
                        }) ?? null;
                    };
                    const noCoveringShift = !!moveBillDate && !selected;
                    return (
                        <div className="space-y-4">
                            {moveError && <FormErrorBanner message={moveError} onDismiss={() => setMoveError("")} />}
                            <p className="text-xs text-muted-foreground">
                                Currently on Shift #{moveInvoiceTarget.shiftId ?? "—"}, dated {new Date(moveInvoiceTarget.date).toLocaleString()}. Set the correct bill date — the shift that covers it is selected automatically. Audit-logged.
                            </p>
                            <div>
                                <label className="text-xs font-bold uppercase tracking-wide text-muted-foreground">Correct bill date/time</label>
                                <input
                                    type="datetime-local"
                                    value={moveBillDate}
                                    max={maxAttr}
                                    onChange={(e) => {
                                        const v = e.target.value;
                                        setMoveBillDate(v);
                                        const cover = coveringShiftFor(v);
                                        setMoveTargetShiftId(cover ? cover.id : null);
                                    }}
                                    className="mt-1 w-full px-3 py-2 border border-border rounded-lg bg-background text-sm"
                                />
                                <p className="text-[10px] text-muted-foreground mt-1">
                                    Pick the date the fuel was actually issued — the matching shift follows. Any shift can be selected below and un-finalized.
                                </p>
                            </div>
                            <div>
                                <label className="text-xs font-bold uppercase tracking-wide text-muted-foreground">
                                    {selected ? "Shift (auto-detected)" : "Shift"}
                                </label>
                                <StyledSelect
                                    value={moveTargetShiftId != null ? String(moveTargetShiftId) : ""}
                                    onChange={(v) => setMoveTargetShiftId(v ? Number(v) : null)}
                                    options={[
                                        { value: "", label: "— Select shift —" },
                                        ...movableShifts
                                            .filter(s => s.id !== moveInvoiceTarget.shiftId)
                                            .map(s => ({
                                                value: String(s.id),
                                                label: `Shift #${s.id} · ${s.startTime ? new Date(s.startTime).toLocaleString("en-IN", { day: "2-digit", month: "short", hour: "2-digit", minute: "2-digit" }) : "—"} · ${s.status}`,
                                            })),
                                    ]}
                                    className="mt-1"
                                />
                                {movableShifts.length === 0 && (
                                    <p className="text-xs text-amber-500 mt-2">
                                        No shifts found.
                                    </p>
                                )}
                                {noCoveringShift && movableShifts.length > 0 && (
                                    <p className="text-xs text-amber-500 mt-2">
                                        No shift covers {new Date(moveBillDate).toLocaleString("en-IN")}. Pick one manually from the list below.
                                    </p>
                                )}
                                {selected && (
                                    <p className="text-[10px] text-muted-foreground mt-1">
                                        Window: {selectedStart?.toLocaleString("en-IN") ?? "—"} → {selectedEndRaw?.toLocaleString("en-IN") ?? "now"}
                                        {!billDateValid && " — date is outside this shift"}
                                    </p>
                                )}
                            </div>

                            {/* Selected shift's status actions — un-finalize a RECONCILED or
                                FINALIZED-report shift in place so it becomes a valid move target,
                                and recompute a DRAFT report after moving. */}
                            {selected && !selectedMovable && (
                                <div className="rounded-lg border border-amber-500/40 bg-amber-500/5 p-3 space-y-2">
                                    <div className="text-xs text-foreground">
                                        Shift #{selected.id} is <span className="font-semibold text-amber-500">{selected.status}</span>
                                        {selected.reportStatus ? <> · report <span className="font-semibold text-amber-500">{selected.reportStatus}</span></> : " · no report"}.
                                    </div>
                                    <p className="text-[11px] text-amber-500">
                                        The bill can&apos;t be moved into it until it&apos;s un-finalized. This reverts the shift to CLOSED (report → DRAFT if one exists) and is audit-logged.
                                    </p>
                                    <button
                                        onClick={() => handleUnfinalizeShift(selected.id)}
                                        disabled={reportActionBusy}
                                        className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg border border-amber-500/40 text-amber-500 hover:bg-amber-500/10 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
                                    >
                                        <RotateCcw className="w-3.5 h-3.5" />
                                        {reportActionBusy ? "Working…" : "Un-finalize this shift"}
                                    </button>
                                </div>
                            )}
                            {selected && selectedMovable && selected.reportStatus === "DRAFT" && selected.reportId != null && (
                                <button
                                    onClick={() => handleRecomputeShift(selected.reportId!)}
                                    disabled={reportActionBusy}
                                    className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg border border-border text-muted-foreground hover:bg-muted disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
                                >
                                    <RefreshCw className={`w-3.5 h-3.5 ${reportActionBusy ? "animate-spin" : ""}`} />
                                    {reportActionBusy ? "Recomputing…" : "Recompute report"}
                                </button>
                            )}

                            <div className="flex justify-end gap-2">
                                <button
                                    onClick={closeMoveInvoice}
                                    className="px-4 py-2 text-sm rounded-lg border border-border text-muted-foreground hover:bg-muted transition-colors"
                                >
                                    Cancel
                                </button>
                                <button
                                    onClick={submitMoveInvoice}
                                    disabled={moveSubmitting || !moveTargetShiftId || !billDateValid || !selectedMovable}
                                    className="flex items-center gap-2 px-4 py-2 text-sm font-medium rounded-lg bg-amber-500 text-white hover:bg-amber-600 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
                                >
                                    <Move className="w-4 h-4" />
                                    {moveSubmitting ? "Moving…" : "Move invoice"}
                                </button>
                            </div>
                        </div>
                    );
                })()}
            </Modal>

            {/* Bill Numbering Sequence Modal */}
            <Modal isOpen={seqModal} onClose={() => setSeqModal(false)} title="Bill Numbering">
                <div className="p-6 space-y-4">
                    {/* Type toggle */}
                    <div className="flex gap-2">
                        {(['CREDIT', 'CASH'] as const).map((t) => (
                            <button
                                key={t}
                                onClick={() => switchSeqType(t)}
                                disabled={seqSubmitting}
                                className={`flex-1 px-3 py-2 rounded-lg text-sm font-medium border transition-colors disabled:opacity-50 ${
                                    seqType === t
                                        ? "bg-primary/10 border-primary/30 text-primary"
                                        : "bg-card border-border text-muted-foreground hover:bg-muted"
                                }`}
                            >
                                {t === 'CREDIT' ? 'Credit (A…)' : 'Cash (C…)'}
                            </button>
                        ))}
                    </div>

                    {seqLoading || !seqData ? (
                        <div className="text-center py-8 text-muted-foreground">
                            {seqError ? <span className="text-red-400">{seqError}</span> : "Loading…"}
                        </div>
                    ) : (() => {
                        const series = seqData.nextBillNo.replace(/\d+$/, ""); // e.g. "A26/"
                        return (
                        <>
                            <div className="rounded-lg border border-border bg-card/50 p-3 text-sm space-y-1">
                                <div>Last issued: <span className="font-mono font-semibold text-foreground">{series}{seqData.lastNumber}</span></div>
                                <div>Next will be: <span className="font-mono font-semibold text-foreground">{seqData.nextBillNo}</span></div>
                                {seqData.highestInDb != null ? (() => {
                                    const maxDb = seqData.highestInDb;
                                    const next = seqData.nextNumber;
                                    let pillText = ""; let pillClass = "";
                                    if (next === maxDb + 1) {
                                        pillText = "✓ in sync";
                                        pillClass = "bg-emerald-500/15 border-emerald-500/40 text-emerald-300";
                                    } else if (next <= maxDb) {
                                        pillText = `⚠ would duplicate`;
                                        pillClass = "bg-yellow-500/15 border-yellow-500/40 text-yellow-300";
                                    } else {
                                        pillText = `gap of ${next - maxDb - 1}`;
                                        pillClass = "bg-blue-500/15 border-blue-500/40 text-blue-300";
                                    }
                                    return (
                                        <div className="flex items-center gap-2 flex-wrap">
                                            <span>Highest bill: <span className="font-mono font-semibold text-foreground">{series}{maxDb}</span></span>
                                            <span className={`text-[10px] uppercase tracking-wide font-bold px-1.5 py-0.5 rounded border ${pillClass}`}>{pillText}</span>
                                        </div>
                                    );
                                })() : (
                                    <div className="text-muted-foreground text-xs">Highest bill: <span className="italic">none yet this year</span></div>
                                )}
                            </div>

                            <div>
                                <label className="block text-sm font-medium text-muted-foreground mb-1">Set next number</label>
                                <input
                                    type="number" min="1" step="1"
                                    value={seqInput}
                                    onChange={(e) => setSeqInput(e.target.value)}
                                    onKeyDown={(e) => { if (e.key === "Enter") submitSeq(); }}
                                    autoFocus
                                    className="w-full bg-white/5 border border-white/10 rounded-lg px-3 py-2 text-foreground font-mono focus:outline-none focus:ring-2 focus:ring-primary/40"
                                />
                                {(() => {
                                    const n = Number.parseInt(seqInput, 10);
                                    if (!Number.isFinite(n) || n < 1) {
                                        return <p className="text-xs text-red-300 mt-1">Must be a positive integer.</p>;
                                    }
                                    const dupes = seqData.highestInDb != null && n <= seqData.highestInDb;
                                    return (
                                        <p className={`text-xs mt-1 ${dupes ? "text-yellow-300" : "text-muted-foreground"}`}>
                                            {dupes && "⚠ "}Next bill will be <span className="font-mono">{series}{n}</span>
                                            {dupes && ` — but ${series}${n} already exists; this will be rejected.`}
                                        </p>
                                    );
                                })()}
                            </div>

                            {seqError && <p className="text-xs text-red-400">{seqError}</p>}

                            <p className="text-xs text-muted-foreground">
                                Forward-only — existing bills are not renumbered. Only affects the next bill created.
                            </p>

                            <div className="flex justify-end gap-2 pt-2">
                                <button onClick={() => setSeqModal(false)} disabled={seqSubmitting}
                                    className="px-4 py-2 rounded-lg border border-border text-foreground hover:bg-muted disabled:opacity-50">
                                    Cancel
                                </button>
                                <button onClick={submitSeq} disabled={seqSubmitting || !seqInput.trim()}
                                    className="btn-gradient px-5 py-2 rounded-lg font-medium disabled:opacity-50">
                                    {seqSubmitting ? "Saving…" : "Save"}
                                </button>
                            </div>
                        </>
                        );
                    })()}
                </div>
            </Modal>
        </div>
    );
}
