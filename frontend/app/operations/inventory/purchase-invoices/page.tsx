"use client";

import { useEffect, useState, useMemo } from "react";
import { TablePagination, useClientPagination } from "@/components/ui/table-pagination";
import { GlassCard } from "@/components/ui/glass-card";
import { Modal } from "@/components/ui/modal";
import { StyledSelect } from "@/components/ui/styled-select";
import {
    getPurchaseInvoices,
    getActiveProducts,
    getActiveSuppliers,
    getPurchaseOrders,
    createPurchaseInvoice,
    updatePurchaseInvoice,
    deletePurchaseInvoice,
    updatePurchaseInvoiceStatus,
    uploadPurchaseInvoicePdf,
    getPurchaseInvoicePdfUrl,
    downloadPurchaseInvoiceReport,
    extractPurchaseInvoicePdf,
    ExtractionResult,
    PurchaseInvoice,
    Product,
    Supplier,
    PurchaseOrder,
} from "@/lib/api/station";
import { FileText, Plus, Search, Trash2, Eye, Edit3, Upload, ExternalLink, Download, Calendar, Sparkles, AlertTriangle } from "lucide-react";
import { useFormValidation, required } from "@/lib/validation";
import { FieldError, inputErrorClass, FormErrorBanner } from "@/components/ui/field-error";
import { PermissionGate } from "@/components/permission-gate";

const STATUS_COLORS: Record<string, string> = {
    PENDING: "bg-yellow-500/10 text-yellow-500",
    VERIFIED: "bg-blue-500/10 text-blue-500",
    PAID: "bg-green-500/10 text-green-500",
};

const TYPE_COLORS: Record<string, string> = {
    FUEL: "bg-orange-500/10 text-orange-500",
    NON_FUEL: "bg-purple-500/10 text-purple-500",
};

interface LineItem {
    productId: string;
    quantity: string;
    unitPrice: string;
    basicPrice: string;
    taxPercent: string;
    taxAmount: string;
    additionalTaxAmount: string;
    totalPrice: string;
}

const EMPTY_LINE_ITEM: LineItem = {
    productId: "",
    quantity: "",
    unitPrice: "",
    basicPrice: "",
    taxPercent: "",
    taxAmount: "",
    additionalTaxAmount: "",
    totalPrice: "",
};

export default function PurchaseInvoicesPage() {
    const [invoices, setInvoices] = useState<PurchaseInvoice[]>([]);
    const [products, setProducts] = useState<Product[]>([]);
    const [suppliers, setSuppliers] = useState<Supplier[]>([]);
    const [purchaseOrders, setPurchaseOrders] = useState<PurchaseOrder[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [isViewModalOpen, setIsViewModalOpen] = useState(false);
    const [editingId, setEditingId] = useState<number | null>(null);
    const [searchQuery, setSearchQuery] = useState("");
    const [statusFilter, setStatusFilter] = useState("");
    const [typeFilter, setTypeFilter] = useState("");
    const [viewInvoice, setViewInvoice] = useState<PurchaseInvoice | null>(null);
    const [fromDate, setFromDate] = useState("");
    const [toDate, setToDate] = useState("");
    const [isDownloading, setIsDownloading] = useState(false);

    // Form state
    const [invoiceType, setInvoiceType] = useState<"FUEL" | "NON_FUEL">("FUEL");
    const [supplierId, setSupplierId] = useState("");
    const [invoiceNumber, setInvoiceNumber] = useState("");
    const [sapEntryNumber, setSapEntryNumber] = useState("");
    const [invoiceDate, setInvoiceDate] = useState(new Date().toISOString().split("T")[0]);
    const [deliveryDate, setDeliveryDate] = useState("");
    const [purchaseOrderId, setPurchaseOrderId] = useState("");
    const [remarks, setRemarks] = useState("");
    const [roundingAdjustment, setRoundingAdjustment] = useState("");
    const [pdfFile, setPdfFile] = useState<File | null>(null);
    const [lineItems, setLineItems] = useState<LineItem[]>([{ ...EMPTY_LINE_ITEM }]);
    const [apiError, setApiError] = useState("");
    const [extracting, setExtracting] = useState(false);
    const [extractionWarnings, setExtractionWarnings] = useState<string[]>([]);
    const [extractionInfo, setExtractionInfo] = useState<string | null>(null);
    const [autoCreateOnExtract, setAutoCreateOnExtract] = useState(false);

    const validationRules = useMemo(() => ({
        supplierId: [required("Supplier is required")],
        invoiceNumber: [required("Invoice number is required")],
        invoiceDate: [required("Invoice date is required")],
    }), []);
    const { errors, validate, clearError, clearAllErrors } = useFormValidation(validationRules);

    const loadData = async () => {
        setIsLoading(true);
        try {
            const [invoiceData, productData, supplierData, poData] = await Promise.all([
                getPurchaseInvoices(statusFilter || undefined, undefined, typeFilter || undefined),
                getActiveProducts(),
                getActiveSuppliers(),
                getPurchaseOrders(),
            ]);
            setInvoices(invoiceData);
            setProducts(productData);
            setSuppliers(supplierData);
            setPurchaseOrders(poData);
        } catch (err) {
            console.error("Failed to load data", err);
        }
        setIsLoading(false);
    };

    useEffect(() => {
        loadData();
    }, [statusFilter, typeFilter]);

    // Filter products based on invoice type
    const filteredProducts = useMemo(() => {
        if (invoiceType === "FUEL") {
            return products.filter((p) => (p.category || "").toLowerCase() === "fuel");
        }
        return products.filter((p) => (p.category || "").toLowerCase() !== "fuel");
    }, [products, invoiceType]);

    // Filter POs by selected supplier
    const filteredPOs = useMemo(() => {
        if (!supplierId) return [];
        return purchaseOrders.filter((po) => po.supplier?.id === Number(supplierId));
    }, [purchaseOrders, supplierId]);

    const addLineItem = () => {
        setLineItems([...lineItems, { ...EMPTY_LINE_ITEM }]);
    };

    const removeLineItem = (idx: number) => {
        setLineItems(lineItems.filter((_, i) => i !== idx));
    };

    const recalcLineItem = (li: LineItem): LineItem => {
        const qty = Number(li.quantity) || 0;
        const basicPrice = Number(li.basicPrice) || 0;
        const basicAmount = qty * basicPrice;
        let taxAmount = Number(li.taxAmount) || 0;
        const taxPercent = Number(li.taxPercent) || 0;
        // Only auto-derive taxAmount from percent if user hasn't explicitly typed taxAmount.
        if (li.taxPercent !== "" && li.taxAmount === "" && basicAmount > 0) {
            taxAmount = basicAmount * taxPercent / 100;
        }
        const addVat = Number(li.additionalTaxAmount) || 0;
        const totalPrice = basicAmount + taxAmount + addVat;
        const unitPrice = qty > 0 ? totalPrice / qty : 0;
        return {
            ...li,
            totalPrice: totalPrice ? totalPrice.toFixed(2) : "",
            unitPrice: unitPrice ? unitPrice.toFixed(4) : "",
        };
    };

    const updateLineItem = (idx: number, field: keyof LineItem, value: string) => {
        const updated = [...lineItems];
        updated[idx] = recalcLineItem({ ...updated[idx], [field]: value });
        setLineItems(updated);
    };

    const calcItemsSubtotal = () => {
        return lineItems.reduce((sum, item) => sum + (Number(item.totalPrice) || 0), 0);
    };

    const calcTotal = () => {
        return calcItemsSubtotal() + (Number(roundingAdjustment) || 0);
    };

    const buildItemsPayload = (items: LineItem[]) =>
        items
            .filter((li) => li.productId && li.quantity)
            .map((li) => {
                const qty = Number(li.quantity) || 0;
                const basicPrice = Number(li.basicPrice) || 0;
                const basicAmount = li.basicPrice ? qty * basicPrice : null;
                const taxPercent = li.taxPercent ? Number(li.taxPercent) : null;
                const taxAmount = li.taxAmount ? Number(li.taxAmount) : null;
                const additionalTaxAmount = li.additionalTaxAmount ? Number(li.additionalTaxAmount) : null;
                const totalPrice = Number(li.totalPrice) || 0;
                const unitPrice = qty > 0 ? totalPrice / qty : 0;
                return {
                    product: { id: Number(li.productId) },
                    quantity: qty,
                    unitPrice,
                    totalPrice,
                    basicPrice: li.basicPrice ? basicPrice : null,
                    basicAmount,
                    taxPercent,
                    taxAmount,
                    additionalTaxAmount,
                };
            });

    const submitInvoice = async (
        items: LineItem[],
        currentSupplierId: string,
        currentInvoiceNumber: string,
        currentSapEntryNumber: string,
        currentInvoiceDate: string,
        currentDeliveryDate: string,
        currentInvoiceType: "FUEL" | "NON_FUEL",
        currentRemarks: string,
        currentPurchaseOrderId: string,
        currentRoundingAdjustment: string,
        currentPdfFile: File | null,
    ) => {
        const itemsPayload = buildItemsPayload(items);
        const itemsSubtotal = items.reduce((sum, item) => sum + (Number(item.totalPrice) || 0), 0);
        const rounding = Number(currentRoundingAdjustment) || 0;
        const total = itemsSubtotal + rounding;
        const payload: any = {
            supplier: { id: Number(currentSupplierId) },
            invoiceNumber: currentInvoiceNumber,
            sapEntryNumber: currentSapEntryNumber || null,
            invoiceDate: currentInvoiceDate,
            deliveryDate: currentDeliveryDate || null,
            invoiceType: currentInvoiceType,
            status: "PENDING",
            totalAmount: total,
            roundingAdjustment: currentRoundingAdjustment ? rounding : null,
            remarks: currentRemarks || null,
            items: itemsPayload,
        };
        if (currentPurchaseOrderId) {
            payload.purchaseOrder = { id: Number(currentPurchaseOrderId) };
        }
        let saved: PurchaseInvoice;
        if (editingId) {
            saved = await updatePurchaseInvoice(editingId, payload);
        } else {
            saved = await createPurchaseInvoice(payload);
        }
        if (currentPdfFile && saved.id) {
            await uploadPurchaseInvoicePdf(saved.id, currentPdfFile);
        }
        return saved;
    };

    const handleSave = async (e: React.FormEvent) => {
        e.preventDefault();
        setApiError("");
        if (!validate({ supplierId, invoiceNumber, invoiceDate })) return;
        try {
            await submitInvoice(lineItems, supplierId, invoiceNumber, sapEntryNumber, invoiceDate, deliveryDate, invoiceType, remarks, purchaseOrderId, roundingAdjustment, pdfFile);
            setIsModalOpen(false);
            resetForm();
            loadData();
        } catch (err: any) {
            console.error("Failed to save invoice", err);
            setApiError(err.message || "Error saving purchase invoice");
        }
    };

    const handlePdfPick = async (file: File | null) => {
        setPdfFile(file);
        setExtractionWarnings([]);
        setExtractionInfo(null);
        if (!file) return;
        setExtracting(true);
        try {
            const result = await extractPurchaseInvoicePdf(file);
            const { newLineItems, warnings, info } = applyExtractionToForm(result);
            setExtractionWarnings(warnings);
            setExtractionInfo(info);

            // Auto-create flow: only if user opted in AND nothing is ambiguous AND we have required fields.
            const supplierMatched = result.supplier.matchedId != null;
            const allItemsMatched = newLineItems.length > 0
                && newLineItems.every((li) => li.productId && li.quantity);
            const hasInvoiceNumber = !!result.invoiceNumber;
            const hasDate = !!result.invoiceDate;

            if (autoCreateOnExtract && supplierMatched && allItemsMatched && hasInvoiceNumber && hasDate && !editingId) {
                try {
                    await submitInvoice(
                        newLineItems,
                        String(result.supplier.matchedId),
                        result.invoiceNumber!,
                        result.sapEntryNumber || "",
                        result.invoiceDate!,
                        result.deliveryDate || "",
                        result.invoiceType || invoiceType,
                        result.remarks || "",
                        "",
                        result.roundingAdjustment != null ? String(result.roundingAdjustment) : "",
                        file,
                    );
                    setIsModalOpen(false);
                    resetForm();
                    loadData();
                } catch (err: any) {
                    console.error("Auto-create failed", err);
                    setApiError(err.message || "Auto-create failed; please review and click Create Invoice");
                }
            }
        } catch (err: any) {
            console.error("PDF extraction failed", err);
            setExtractionWarnings([err.message || "PDF extraction failed"]);
        } finally {
            setExtracting(false);
        }
    };

    const applyExtractionToForm = (r: ExtractionResult): { newLineItems: LineItem[]; warnings: string[]; info: string | null } => {
        const warnings: string[] = [];
        if (r.invoiceType) setInvoiceType(r.invoiceType);
        setSupplierId(r.supplier.matchedId ? String(r.supplier.matchedId) : "");
        if (r.invoiceNumber) setInvoiceNumber(r.invoiceNumber);
        if (r.sapEntryNumber) setSapEntryNumber(r.sapEntryNumber);
        if (r.invoiceDate) setInvoiceDate(r.invoiceDate);
        if (r.deliveryDate) setDeliveryDate(r.deliveryDate);
        if (r.remarks) setRemarks(r.remarks);
        setRoundingAdjustment(r.roundingAdjustment != null ? String(r.roundingAdjustment) : "");
        clearAllErrors();

        if (!r.supplier.matchedId && r.supplier.extractedName) {
            const gst = r.supplier.extractedGstin ? ` (GSTIN ${r.supplier.extractedGstin})` : "";
            warnings.push(`Supplier from PDF: "${r.supplier.extractedName}"${gst} — no match in your suppliers list. Pick one from the dropdown.`);
        }

        const items: LineItem[] = (r.items || []).map((it) => {
            if (!it.matchedProductId && it.extractedDescription) {
                const hsn = it.extractedHsn ? ` (HSN ${it.extractedHsn})` : "";
                warnings.push(`Line "${it.extractedDescription}"${hsn} — no product match. Pick a product manually.`);
            }
            const qty = it.quantityLitres ?? 0;
            const basicPrice = it.basicPricePerLitre ?? 0;
            const basicAmount = it.basicAmount ?? (qty * basicPrice);
            const taxAmount = it.taxAmount ?? 0;
            const addVat = it.additionalTaxAmount ?? 0;
            const totalPrice = it.totalAmount ?? (basicAmount + taxAmount + addVat);
            const unitPrice = qty > 0 ? totalPrice / qty : 0;
            return {
                productId: it.matchedProductId ? String(it.matchedProductId) : "",
                quantity: qty ? String(qty) : "",
                basicPrice: basicPrice ? String(basicPrice) : "",
                taxPercent: it.taxPercent != null ? String(it.taxPercent) : "",
                taxAmount: taxAmount ? String(taxAmount) : "",
                additionalTaxAmount: addVat ? String(addVat) : "",
                totalPrice: totalPrice ? totalPrice.toFixed(2) : "",
                unitPrice: unitPrice ? unitPrice.toFixed(4) : "",
            };
        });
        const newLineItems = items.length > 0 ? items : [{ ...EMPTY_LINE_ITEM }];
        setLineItems(newLineItems);

        const info = r.items?.length
            ? `Extracted ${r.items.length} item${r.items.length > 1 ? "s" : ""} from PDF.`
            : "PDF parsed but no line items found.";
        return { newLineItems, warnings, info };
    };

    const handleDelete = async (id: number) => {
        if (!confirm("Are you sure you want to delete this purchase invoice?")) return;
        try {
            await deletePurchaseInvoice(id);
            loadData();
        } catch (err: any) {
            setApiError(err.message || "Error deleting invoice");
        }
    };

    const handleStatusUpdate = async (id: number, status: string) => {
        try {
            await updatePurchaseInvoiceStatus(id, status);
            loadData();
        } catch (err: any) {
            setApiError(err.message || "Error updating status");
        }
    };

    const handleView = (invoice: PurchaseInvoice) => {
        setViewInvoice(invoice);
        setIsViewModalOpen(true);
    };

    const handleDownload = async (format: 'pdf' | 'excel') => {
        if (!fromDate || !toDate) return;
        setIsDownloading(true);
        try {
            const blob = await downloadPurchaseInvoiceReport(fromDate, toDate, format);
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            const ext = format === 'pdf' ? 'pdf' : 'xlsx';
            a.download = `PurchaseInvoices_${fromDate}_${toDate}.${ext}`;
            document.body.appendChild(a);
            a.click();
            a.remove();
            window.URL.revokeObjectURL(url);
        } catch (err) {
            console.error("Failed to download report", err);
            setApiError("Error downloading report");
        }
        setIsDownloading(false);
    };

    const handleEdit = (invoice: PurchaseInvoice) => {
        clearAllErrors();
        setApiError("");
        setEditingId(invoice.id!);
        setInvoiceType(invoice.invoiceType);
        setSupplierId(String(invoice.supplier.id));
        setInvoiceNumber(invoice.invoiceNumber);
        setSapEntryNumber(invoice.sapEntryNumber || "");
        setInvoiceDate(invoice.invoiceDate);
        setDeliveryDate(invoice.deliveryDate || "");
        setPurchaseOrderId(invoice.purchaseOrder?.id ? String(invoice.purchaseOrder.id) : "");
        setRemarks(invoice.remarks || "");
        setRoundingAdjustment(invoice.roundingAdjustment != null ? String(invoice.roundingAdjustment) : "");
        setPdfFile(null);
        setExtractionWarnings([]);
        setExtractionInfo(null);
        setLineItems(
            invoice.items.map((item) => {
                const qty = item.quantity ?? 0;
                const totalPrice = Number(item.totalPrice ?? 0);
                const unitPrice = qty > 0 && totalPrice > 0 ? totalPrice / qty : Number(item.unitPrice ?? 0);
                return {
                    productId: String(item.product.id),
                    quantity: String(qty),
                    unitPrice: unitPrice ? unitPrice.toFixed(4) : "",
                    basicPrice: item.basicPrice != null ? String(item.basicPrice) : "",
                    taxPercent: item.taxPercent != null ? String(item.taxPercent) : "",
                    taxAmount: item.taxAmount != null ? String(item.taxAmount) : "",
                    additionalTaxAmount: item.additionalTaxAmount != null ? String(item.additionalTaxAmount) : "",
                    totalPrice: totalPrice ? totalPrice.toFixed(2) : "",
                };
            })
        );
        setIsModalOpen(true);
    };

    const resetForm = () => {
        setEditingId(null);
        setInvoiceType("FUEL");
        setSupplierId("");
        setInvoiceNumber("");
        setSapEntryNumber("");
        setInvoiceDate(new Date().toISOString().split("T")[0]);
        setDeliveryDate("");
        setPurchaseOrderId("");
        setRemarks("");
        setRoundingAdjustment("");
        setPdfFile(null);
        setExtractionWarnings([]);
        setExtractionInfo(null);
        setLineItems([{ ...EMPTY_LINE_ITEM }]);
    };

    const filtered = invoices.filter((inv) => {
        const q = searchQuery.toLowerCase();
        return (
            !searchQuery ||
            inv.invoiceNumber?.toLowerCase().includes(q) ||
            inv.supplier?.name?.toLowerCase().includes(q) ||
            inv.remarks?.toLowerCase().includes(q)
        );
    });

    const { page, setPage, totalPages, totalElements, pageSize, paginatedData } = useClientPagination(filtered);

    return (
        <div className="p-6 h-screen overflow-hidden bg-background transition-colors duration-300">
            <div className="max-w-7xl mx-auto">
                <div className="flex justify-between items-center mb-8">
                    <div>
                        <h1 className="text-4xl font-bold text-foreground tracking-tight">
                            Purchase <span className="text-gradient">Invoices</span>
                        </h1>
                        <p className="text-muted-foreground mt-2">
                            Manage supplier invoices for fuel and non-fuel purchases.
                        </p>
                    </div>
                    <PermissionGate permission="INVENTORY_CREATE">
                        <button
                            onClick={() => { resetForm(); clearAllErrors(); setApiError(""); setIsModalOpen(true); }}
                            className="btn-gradient px-6 py-3 rounded-xl font-medium flex items-center gap-2 shadow-lg hover:shadow-xl transition-all"
                        >
                            <Plus className="w-5 h-5" />
                            New Invoice
                        </button>
                    </PermissionGate>
                </div>

                {isLoading ? (
                    <div className="flex flex-col items-center justify-center py-20 text-muted-foreground">
                        <div className="w-12 h-12 border-4 border-primary/20 border-t-primary rounded-full animate-spin mb-4"></div>
                        <p className="animate-pulse">Loading purchase invoices...</p>
                    </div>
                ) : invoices.length === 0 && !statusFilter && !typeFilter ? (
                    <div className="text-center py-20 bg-black/5 dark:bg-white/5 rounded-2xl border border-dashed border-border">
                        <FileText className="w-16 h-16 mx-auto text-muted-foreground mb-4 opacity-50" />
                        <h3 className="text-xl font-semibold text-foreground mb-2">No Purchase Invoices</h3>
                        <p className="text-muted-foreground mb-6">Create your first purchase invoice to track supplier bills.</p>
                    </div>
                ) : (
                    <>
                        <div className="mb-6 flex flex-wrap gap-3 items-center">
                            <div className="relative flex-1 min-w-[200px] max-w-md">
                                <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                                <input
                                    type="text"
                                    placeholder="Search by invoice #, supplier, remarks..."
                                    value={searchQuery}
                                    onChange={(e) => setSearchQuery(e.target.value)}
                                    className="w-full pl-10 pr-4 py-2.5 bg-card border border-border rounded-xl text-foreground text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                                />
                            </div>
                            <StyledSelect
                                value={statusFilter}
                                onChange={(val) => setStatusFilter(val)}
                                options={[
                                    { value: "", label: "All Statuses" },
                                    { value: "PENDING", label: "Pending" },
                                    { value: "VERIFIED", label: "Verified" },
                                    { value: "PAID", label: "Paid" },
                                ]}
                                placeholder="All Statuses"
                                className="min-w-[140px]"
                            />
                            <StyledSelect
                                value={typeFilter}
                                onChange={(val) => setTypeFilter(val)}
                                options={[
                                    { value: "", label: "All Types" },
                                    { value: "FUEL", label: "Fuel" },
                                    { value: "NON_FUEL", label: "Non-Fuel" },
                                ]}
                                placeholder="All Types"
                                className="min-w-[140px]"
                            />
                            <div className="flex items-center gap-1.5">
                                <input
                                    type="date"
                                    value={fromDate}
                                    onChange={(e) => setFromDate(e.target.value)}
                                    className="px-3 py-2.5 bg-card border border-border rounded-xl text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
                                />
                                <span className="text-muted-foreground text-xs">to</span>
                                <input
                                    type="date"
                                    value={toDate}
                                    onChange={(e) => setToDate(e.target.value)}
                                    className="px-3 py-2.5 bg-card border border-border rounded-xl text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
                                />
                            </div>
                            {fromDate && toDate && (
                                <div className="flex gap-1.5">
                                    <button
                                        onClick={() => handleDownload('pdf')}
                                        disabled={isDownloading}
                                        className="px-3 py-2.5 bg-red-500/10 text-red-500 hover:bg-red-500/20 rounded-xl text-xs font-bold transition-colors disabled:opacity-50"
                                        title="Download PDF"
                                    >
                                        PDF
                                    </button>
                                    <button
                                        onClick={() => handleDownload('excel')}
                                        disabled={isDownloading}
                                        className="px-3 py-2.5 bg-green-500/10 text-green-500 hover:bg-green-500/20 rounded-xl text-xs font-bold transition-colors disabled:opacity-50"
                                        title="Download Excel"
                                    >
                                        Excel
                                    </button>
                                </div>
                            )}
                            {(searchQuery || statusFilter || typeFilter || fromDate || toDate) && (
                                <button onClick={() => { setSearchQuery(""); setStatusFilter(""); setTypeFilter(""); setFromDate(""); setToDate(""); }} className="px-3 py-2.5 text-xs text-muted-foreground hover:text-foreground">
                                    Clear
                                </button>
                            )}
                        </div>

                        <GlassCard className="overflow-hidden border-none p-0">
                            <div className="overflow-x-auto">
                                <table className="w-full text-left border-collapse">
                                    <thead>
                                        <tr className="bg-white/5 border-b border-border/50">
                                            <th className="px-4 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center w-12">#</th>
                                            <th className="px-4 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Invoice #</th>
                                            <th className="px-4 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center">Type</th>
                                            <th className="px-4 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Supplier</th>
                                            <th className="px-4 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Date</th>
                                            <th className="px-4 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center">Items</th>
                                            <th className="px-4 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right">Total</th>
                                            <th className="px-4 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center">Status</th>
                                            <th className="px-4 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center">PDF</th>
                                            <th className="px-4 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center w-40">Actions</th>
                                        </tr>
                                    </thead>
                                    <tbody className="divide-y divide-border/30">
                                        {paginatedData.map((inv, idx) => (
                                            <tr key={inv.id} className="hover:bg-white/5 transition-colors">
                                                <td className="px-4 py-4 text-xs font-mono text-muted-foreground text-center">{page * pageSize + idx + 1}</td>
                                                <td className="px-4 py-4">
                                                    <div className="text-sm font-medium text-foreground">{inv.invoiceNumber}</div>
                                                    {inv.sapEntryNumber && (
                                                        <div className="text-[10px] text-muted-foreground font-mono">SAP {inv.sapEntryNumber}</div>
                                                    )}
                                                </td>
                                                <td className="px-4 py-4 text-center">
                                                    <span className={`px-2 py-0.5 rounded-full text-[10px] font-bold uppercase ${TYPE_COLORS[inv.invoiceType] || ''}`}>
                                                        {inv.invoiceType === "NON_FUEL" ? "Non-Fuel" : "Fuel"}
                                                    </span>
                                                </td>
                                                <td className="px-4 py-4 text-sm text-foreground">{inv.supplier?.name}</td>
                                                <td className="px-4 py-4">
                                                    <div className="text-sm text-foreground">{new Date(inv.invoiceDate).toLocaleDateString()}</div>
                                                    {inv.deliveryDate && (
                                                        <div className="text-[10px] text-muted-foreground">Del: {new Date(inv.deliveryDate).toLocaleDateString()}</div>
                                                    )}
                                                </td>
                                                <td className="px-4 py-4 text-center text-sm font-mono text-muted-foreground">{inv.items?.length || 0}</td>
                                                <td className="px-4 py-4 text-right font-bold font-mono text-foreground">
                                                    {inv.totalAmount != null ? `₹${Number(inv.totalAmount).toLocaleString()}` : '-'}
                                                </td>
                                                <td className="px-4 py-4 text-center">
                                                    <span className={`px-3 py-1 rounded-full text-[10px] font-bold uppercase ${STATUS_COLORS[inv.status] || ''}`}>
                                                        {inv.status}
                                                    </span>
                                                </td>
                                                <td className="px-4 py-4 text-center">
                                                    {inv.pdfFilePath ? (
                                                        <button
                                                            onClick={async () => {
                                                                const url = await getPurchaseInvoicePdfUrl(inv.id!);
                                                                window.open(url, "_blank");
                                                            }}
                                                            className="inline-flex items-center text-primary hover:text-primary/80"
                                                            title="View PDF"
                                                        >
                                                            <ExternalLink className="w-4 h-4" />
                                                        </button>
                                                    ) : (
                                                        <span className="text-muted-foreground text-xs">—</span>
                                                    )}
                                                </td>
                                                <td className="px-4 py-4">
                                                    <div className="flex justify-center gap-1">
                                                        <button onClick={() => handleView(inv)} className="p-1.5 rounded-lg hover:bg-white/10 text-muted-foreground hover:text-foreground transition-colors" title="View">
                                                            <Eye className="w-4 h-4" />
                                                        </button>
                                                        <PermissionGate permission="INVENTORY_UPDATE">
                                                            {inv.status !== "PAID" && (
                                                                <>
                                                                    <button onClick={() => handleEdit(inv)} className="p-1.5 rounded-lg hover:bg-white/10 text-muted-foreground hover:text-blue-500 transition-colors" title="Edit">
                                                                        <Edit3 className="w-4 h-4" />
                                                                    </button>
                                                                    <button onClick={() => handleDelete(inv.id!)} className="p-1.5 rounded-lg hover:bg-red-500/10 text-muted-foreground hover:text-red-500 transition-colors" title="Delete">
                                                                        <Trash2 className="w-4 h-4" />
                                                                    </button>
                                                                </>
                                                            )}
                                                            {inv.status === "PENDING" && (
                                                                <button onClick={() => handleStatusUpdate(inv.id!, "VERIFIED")} className="p-1.5 rounded-lg hover:bg-blue-500/10 text-muted-foreground hover:text-blue-500 transition-colors" title="Mark Verified">
                                                                    <span className="text-xs font-bold">V</span>
                                                                </button>
                                                            )}
                                                            {inv.status === "VERIFIED" && (
                                                                <button onClick={() => handleStatusUpdate(inv.id!, "PAID")} className="p-1.5 rounded-lg hover:bg-green-500/10 text-muted-foreground hover:text-green-500 transition-colors" title="Mark Paid">
                                                                    <span className="text-xs font-bold">₹</span>
                                                                </button>
                                                            )}
                                                        </PermissionGate>
                                                    </div>
                                                </td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                            <TablePagination page={page} totalPages={totalPages} totalElements={totalElements} pageSize={pageSize} onPageChange={setPage} />
                        </GlassCard>
                    </>
                )}
            </div>

            {/* Create/Edit Invoice Modal */}
            <Modal isOpen={isModalOpen} onClose={() => { setIsModalOpen(false); resetForm(); }} title={editingId ? "Edit Purchase Invoice" : "New Purchase Invoice"} size="xl">
                <form onSubmit={handleSave} className="space-y-4">
                    <FormErrorBanner message={apiError} />

                    {/* PDF Auto-Extract banner */}
                    {!editingId && (
                        <div className="rounded-xl border border-primary/30 bg-primary/5 p-3">
                            <div className="flex items-center justify-between gap-3 flex-wrap">
                                <div className="flex items-center gap-2">
                                    <Sparkles className="w-4 h-4 text-primary" />
                                    <div>
                                        <div className="text-sm font-medium text-foreground">Skip the typing — drop the supplier PDF</div>
                                        <div className="text-xs text-muted-foreground">We&apos;ll auto-fill supplier, items, qty, rate &amp; tax. Review and click Create.</div>
                                    </div>
                                </div>
                                <label className="flex items-center gap-2 text-xs text-foreground cursor-pointer select-none">
                                    <input
                                        type="checkbox"
                                        checked={autoCreateOnExtract}
                                        onChange={(e) => setAutoCreateOnExtract(e.target.checked)}
                                        className="w-4 h-4 accent-primary"
                                    />
                                    <span>Auto-create on upload <span className="text-muted-foreground">(only if everything matches)</span></span>
                                </label>
                            </div>
                        </div>
                    )}

                    {extracting && (
                        <div className="rounded-xl border border-primary/30 bg-primary/10 p-3 flex items-center gap-3">
                            <div className="w-4 h-4 border-2 border-primary/30 border-t-primary rounded-full animate-spin" />
                            <span className="text-sm text-foreground">Reading PDF and extracting fields…</span>
                        </div>
                    )}

                    {extractionInfo && !extracting && (
                        <div className="rounded-xl border border-emerald-500/30 bg-emerald-500/10 p-3 text-sm text-foreground flex items-center gap-2">
                            <Sparkles className="w-4 h-4 text-emerald-500" />
                            {extractionInfo}
                        </div>
                    )}

                    {extractionWarnings.length > 0 && (
                        <div className="rounded-xl border border-amber-500/40 bg-amber-500/10 p-3 space-y-1">
                            <div className="flex items-center gap-2 text-sm font-medium text-amber-500">
                                <AlertTriangle className="w-4 h-4" />
                                Review needed before saving
                            </div>
                            <ul className="text-xs text-foreground list-disc list-inside space-y-0.5">
                                {extractionWarnings.map((w, i) => (
                                    <li key={i}>{w}</li>
                                ))}
                            </ul>
                        </div>
                    )}

                    {/* Invoice Type Toggle */}
                    <div>
                        <label className="block text-sm font-medium text-foreground mb-1.5">Invoice Type</label>
                        <div className="flex gap-2">
                            <button
                                type="button"
                                onClick={() => { setInvoiceType("FUEL"); setLineItems([{ ...EMPTY_LINE_ITEM }]); }}
                                className={`flex-1 py-2.5 rounded-xl text-sm font-medium border transition-colors ${invoiceType === "FUEL" ? "bg-orange-500/10 text-orange-500 border-orange-500/30" : "bg-background border-border text-muted-foreground hover:bg-muted"}`}
                            >
                                Fuel
                            </button>
                            <button
                                type="button"
                                onClick={() => { setInvoiceType("NON_FUEL"); setLineItems([{ ...EMPTY_LINE_ITEM }]); }}
                                className={`flex-1 py-2.5 rounded-xl text-sm font-medium border transition-colors ${invoiceType === "NON_FUEL" ? "bg-purple-500/10 text-purple-500 border-purple-500/30" : "bg-background border-border text-muted-foreground hover:bg-muted"}`}
                            >
                                Non-Fuel
                            </button>
                        </div>
                    </div>

                    <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5">Supplier</label>
                            <StyledSelect
                                value={supplierId}
                                onChange={(val) => { setSupplierId(val); setPurchaseOrderId(""); clearError("supplierId"); }}
                                options={[
                                    { value: "", label: "Select supplier..." },
                                    ...suppliers.map((s) => ({ value: String(s.id), label: s.name })),
                                ]}
                                placeholder="Select supplier..."
                                className={`w-full ${inputErrorClass(errors.supplierId)}`}
                            />
                            <FieldError error={errors.supplierId} />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5">Invoice Number</label>
                            <input
                                type="text"
                                value={invoiceNumber}
                                onChange={(e) => { setInvoiceNumber(e.target.value); clearError("invoiceNumber"); }}
                                className={`w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground ${inputErrorClass(errors.invoiceNumber)}`}
                                placeholder="e.g. 20274150B000997"
                            />
                            <FieldError error={errors.invoiceNumber} />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5">
                                SAP Entry No <span className="text-muted-foreground text-xs">(optional)</span>
                            </label>
                            <input
                                type="text"
                                value={sapEntryNumber}
                                onChange={(e) => setSapEntryNumber(e.target.value)}
                                className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground"
                                placeholder="e.g. 7005192190"
                            />
                        </div>
                    </div>

                    <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5">Invoice Date</label>
                            <input
                                type="date"
                                value={invoiceDate}
                                onChange={(e) => { setInvoiceDate(e.target.value); clearError("invoiceDate"); }}
                                className={`w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground ${inputErrorClass(errors.invoiceDate)}`}
                            />
                            <FieldError error={errors.invoiceDate} />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5">Delivery Date</label>
                            <input
                                type="date"
                                value={deliveryDate}
                                onChange={(e) => setDeliveryDate(e.target.value)}
                                className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground"
                            />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5">Purchase Order (optional)</label>
                            <StyledSelect
                                value={purchaseOrderId}
                                onChange={(val) => setPurchaseOrderId(val)}
                                options={[
                                    { value: "", label: "None" },
                                    ...filteredPOs.map((po) => ({ value: String(po.id), label: `PO #${po.id} — ${new Date(po.orderDate).toLocaleDateString()} (${po.status})` })),
                                ]}
                                placeholder="None"
                                className={`w-full ${!supplierId ? "opacity-50 pointer-events-none" : ""}`}
                            />
                        </div>
                    </div>

                    {/* Line Items */}
                    <div>
                        <div className="flex justify-between items-center mb-2">
                            <label className="text-sm font-medium text-foreground">Items</label>
                            <button type="button" onClick={addLineItem} className="text-xs text-primary hover:underline">+ Add Item</button>
                        </div>
                        <div className="hidden md:grid gap-2 items-center text-[10px] uppercase tracking-wider text-muted-foreground font-bold mb-1 px-1" style={{ gridTemplateColumns: "2.4fr 1fr 1fr 0.6fr 1fr 1fr 1.2fr 0.3fr" }}>
                            <div>Product</div>
                            <div className="text-right">Qty (L)</div>
                            <div className="text-right">Basic ₹/L</div>
                            <div className="text-right">Tax %</div>
                            <div className="text-right">Tax ₹</div>
                            <div className="text-right">Add&apos;l VAT ₹</div>
                            <div className="text-right">Landed Total ₹</div>
                            <div></div>
                        </div>
                        <div className="space-y-2">
                            {lineItems.map((li, idx) => {
                                const landedRate = Number(li.unitPrice) || 0;
                                return (
                                    <div key={idx} className="grid gap-2 items-center" style={{ gridTemplateColumns: "2.4fr 1fr 1fr 0.6fr 1fr 1fr 1.2fr 0.3fr" }}>
                                        <div>
                                            <StyledSelect
                                                value={li.productId}
                                                onChange={(val) => updateLineItem(idx, "productId", val)}
                                                options={[
                                                    { value: "", label: "Product..." },
                                                    ...filteredProducts.map((p) => ({ value: String(p.id), label: p.name })),
                                                ]}
                                                placeholder="Product..."
                                                className="w-full"
                                            />
                                        </div>
                                        <div>
                                            <input type="number" placeholder="Qty" value={li.quantity} onChange={(e) => updateLineItem(idx, "quantity", e.target.value)} className="w-full bg-background border border-border rounded-lg px-2 py-2 text-foreground text-xs text-right" required min="0.01" step="any" />
                                        </div>
                                        <div>
                                            <input type="number" placeholder="0.00" value={li.basicPrice} onChange={(e) => updateLineItem(idx, "basicPrice", e.target.value)} className="w-full bg-background border border-border rounded-lg px-2 py-2 text-foreground text-xs text-right" min="0" step="any" />
                                        </div>
                                        <div>
                                            <input type="number" placeholder="%" value={li.taxPercent} onChange={(e) => updateLineItem(idx, "taxPercent", e.target.value)} className="w-full bg-background border border-border rounded-lg px-2 py-2 text-foreground text-xs text-right" min="0" step="any" />
                                        </div>
                                        <div>
                                            <input type="number" placeholder="0.00" value={li.taxAmount} onChange={(e) => updateLineItem(idx, "taxAmount", e.target.value)} className="w-full bg-background border border-border rounded-lg px-2 py-2 text-foreground text-xs text-right" min="0" step="any" />
                                        </div>
                                        <div>
                                            <input type="number" placeholder="0.00" value={li.additionalTaxAmount} onChange={(e) => updateLineItem(idx, "additionalTaxAmount", e.target.value)} className="w-full bg-background border border-border rounded-lg px-2 py-2 text-foreground text-xs text-right" min="0" step="any" />
                                        </div>
                                        <div className="text-right pr-1">
                                            <div className="text-sm font-mono font-medium text-foreground">₹{(Number(li.totalPrice) || 0).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</div>
                                            {landedRate > 0 && (
                                                <div className="text-[10px] text-muted-foreground font-mono">₹{landedRate.toFixed(4)}/L landed</div>
                                            )}
                                        </div>
                                        <div className="text-center">
                                            {lineItems.length > 1 && (
                                                <button type="button" onClick={() => removeLineItem(idx)} className="text-muted-foreground hover:text-red-500">
                                                    <Trash2 className="w-3.5 h-3.5" />
                                                </button>
                                            )}
                                        </div>
                                    </div>
                                );
                            })}
                        </div>
                        <div className="mt-3 flex flex-col items-end gap-1.5 text-sm">
                            <div className="flex items-center gap-3 text-muted-foreground">
                                <span>Items Subtotal:</span>
                                <span className="font-mono w-32 text-right">₹{calcItemsSubtotal().toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</span>
                            </div>
                            <div className="flex items-center gap-3 text-muted-foreground">
                                <label htmlFor="rounding-input" className="cursor-pointer" title="ZRND / Rounding Off line on supplier invoices">Rounding Adjustment:</label>
                                <input
                                    id="rounding-input"
                                    type="number"
                                    value={roundingAdjustment}
                                    onChange={(e) => setRoundingAdjustment(e.target.value)}
                                    placeholder="0.00"
                                    step="any"
                                    className="w-32 bg-background border border-border rounded-lg px-2 py-1.5 text-foreground text-right font-mono text-sm"
                                />
                            </div>
                            <div className="flex items-center gap-3 font-bold text-foreground border-t border-border pt-1.5">
                                <span>Total:</span>
                                <span className="font-mono w-32 text-right">₹{calcTotal().toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</span>
                            </div>
                        </div>
                    </div>

                    <div>
                        <label className="block text-sm font-medium text-foreground mb-1.5">Remarks</label>
                        <textarea value={remarks} onChange={(e) => setRemarks(e.target.value)} className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground" placeholder="Optional notes..." rows={2} />
                    </div>

                    {/* PDF Upload */}
                    <div>
                        <label className="block text-sm font-medium text-foreground mb-1.5">
                            Invoice PDF <span className="text-muted-foreground text-xs">{editingId ? "(optional — replace attached PDF)" : "(drop here to auto-fill)"}</span>
                        </label>
                        <div className="flex items-center gap-3">
                            <label className={`flex items-center gap-2 px-4 py-2.5 bg-background border border-border rounded-xl text-sm text-muted-foreground hover:bg-muted cursor-pointer transition-colors ${extracting ? "pointer-events-none opacity-60" : ""}`}>
                                <Upload className="w-4 h-4" />
                                {pdfFile ? pdfFile.name : "Choose file..."}
                                <input
                                    type="file"
                                    accept=".pdf"
                                    onChange={(e) => {
                                        const f = e.target.files?.[0] || null;
                                        if (editingId) {
                                            // In edit mode, just attach the PDF; don't re-extract.
                                            setPdfFile(f);
                                        } else {
                                            handlePdfPick(f);
                                        }
                                    }}
                                    className="hidden"
                                />
                            </label>
                            {pdfFile && !extracting && (
                                <button type="button" onClick={() => { setPdfFile(null); setExtractionWarnings([]); setExtractionInfo(null); }} className="text-xs text-muted-foreground hover:text-red-500">Remove</button>
                            )}
                        </div>
                    </div>

                    <div className="flex justify-end gap-3 pt-6 border-t border-border mt-6">
                        <button type="button" onClick={() => setIsModalOpen(false)} className="px-6 py-2.5 rounded-xl font-medium text-foreground bg-black/5 dark:bg-white/5 hover:bg-black/10 transition-colors">Cancel</button>
                        <button type="submit" className="btn-gradient px-8 py-2.5 rounded-xl font-medium shadow-lg hover:shadow-xl transition-all">
                            {editingId ? "Update Invoice" : "Create Invoice"}
                        </button>
                    </div>
                </form>
            </Modal>

            {/* View Invoice Modal */}
            <Modal isOpen={isViewModalOpen} onClose={() => { setIsViewModalOpen(false); setViewInvoice(null); }} title="Purchase Invoice Details">
                {viewInvoice && (
                    <div className="space-y-4">
                        <div className="grid grid-cols-2 gap-4 text-sm">
                            <div><span className="text-muted-foreground">Invoice #:</span> <span className="font-medium text-foreground">{viewInvoice.invoiceNumber}</span></div>
                            <div><span className="text-muted-foreground">SAP Entry #:</span> <span className="font-medium text-foreground">{viewInvoice.sapEntryNumber || '-'}</span></div>
                            <div><span className="text-muted-foreground">Type:</span> <span className={`ml-2 px-2 py-0.5 rounded-full text-[10px] font-bold uppercase ${TYPE_COLORS[viewInvoice.invoiceType]}`}>{viewInvoice.invoiceType === "NON_FUEL" ? "Non-Fuel" : "Fuel"}</span></div>
                            <div><span className="text-muted-foreground">Supplier:</span> <span className="font-medium text-foreground">{viewInvoice.supplier?.name}</span></div>
                            <div><span className="text-muted-foreground">Status:</span> <span className={`ml-2 px-2 py-0.5 rounded-full text-[10px] font-bold uppercase ${STATUS_COLORS[viewInvoice.status]}`}>{viewInvoice.status}</span></div>
                            <div><span className="text-muted-foreground">Invoice Date:</span> <span className="font-medium text-foreground">{new Date(viewInvoice.invoiceDate).toLocaleDateString()}</span></div>
                            <div><span className="text-muted-foreground">Delivery Date:</span> <span className="font-medium text-foreground">{viewInvoice.deliveryDate ? new Date(viewInvoice.deliveryDate).toLocaleDateString() : '-'}</span></div>
                            {viewInvoice.purchaseOrder && (
                                <div className="col-span-2"><span className="text-muted-foreground">Linked PO:</span> <span className="font-medium text-foreground">PO #{viewInvoice.purchaseOrder.id}</span></div>
                            )}
                        </div>
                        {viewInvoice.remarks && <div className="text-sm text-muted-foreground bg-black/5 dark:bg-white/5 rounded-xl p-3">{viewInvoice.remarks}</div>}
                        {viewInvoice.pdfFilePath && (
                            <div>
                                <button
                                    onClick={async () => {
                                        const url = await getPurchaseInvoicePdfUrl(viewInvoice.id!);
                                        window.open(url, "_blank");
                                    }}
                                    className="inline-flex items-center gap-2 text-sm text-primary hover:underline"
                                >
                                    <ExternalLink className="w-4 h-4" /> View PDF
                                </button>
                            </div>
                        )}
                        <table className="w-full text-sm border-collapse">
                            <thead>
                                <tr className="border-b border-border">
                                    <th className="text-left py-2 text-muted-foreground font-medium">Product</th>
                                    {viewInvoice.invoiceType === "FUEL" && <th className="text-left py-2 text-muted-foreground font-medium">Grade</th>}
                                    <th className="text-right py-2 text-muted-foreground font-medium">Qty</th>
                                    <th className="text-right py-2 text-muted-foreground font-medium">Rate</th>
                                    <th className="text-right py-2 text-muted-foreground font-medium">Total</th>
                                </tr>
                            </thead>
                            <tbody>
                                {viewInvoice.items?.map((item, idx) => (
                                    <tr key={idx} className="border-b border-border/30">
                                        <td className="py-2 font-medium text-foreground">{item.product?.name}</td>
                                        {viewInvoice.invoiceType === "FUEL" && <td className="py-2 text-muted-foreground">{item.product?.gradeType?.name || '-'}</td>}
                                        <td className="py-2 text-right font-mono">{item.quantity}</td>
                                        <td className="py-2 text-right font-mono">₹{Number(item.unitPrice || 0).toLocaleString()}</td>
                                        <td className="py-2 text-right font-mono font-bold">₹{Number(item.totalPrice || 0).toLocaleString()}</td>
                                    </tr>
                                ))}
                            </tbody>
                            <tfoot>
                                {viewInvoice.roundingAdjustment != null && Number(viewInvoice.roundingAdjustment) !== 0 && (
                                    <tr className="text-muted-foreground">
                                        <td colSpan={viewInvoice.invoiceType === "FUEL" ? 4 : 3} className="py-1.5 text-right">Rounding Adjustment:</td>
                                        <td className="py-1.5 text-right font-mono">₹{Number(viewInvoice.roundingAdjustment).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</td>
                                    </tr>
                                )}
                                <tr className="border-t border-border">
                                    <td colSpan={viewInvoice.invoiceType === "FUEL" ? 4 : 3} className="py-2 text-right font-bold text-foreground">Total:</td>
                                    <td className="py-2 text-right font-mono font-bold text-primary">₹{Number(viewInvoice.totalAmount || 0).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</td>
                                </tr>
                            </tfoot>
                        </table>
                    </div>
                )}
            </Modal>
        </div>
    );
}
