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
    PurchaseInvoice,
    Product,
    Supplier,
    PurchaseOrder,
} from "@/lib/api/station";
import { FileText, Plus, Search, Trash2, Eye, Edit3, Upload, ExternalLink, Download, Calendar } from "lucide-react";
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
}

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
    const [invoiceDate, setInvoiceDate] = useState(new Date().toISOString().split("T")[0]);
    const [deliveryDate, setDeliveryDate] = useState("");
    const [purchaseOrderId, setPurchaseOrderId] = useState("");
    const [remarks, setRemarks] = useState("");
    const [pdfFile, setPdfFile] = useState<File | null>(null);
    const [lineItems, setLineItems] = useState<LineItem[]>([{ productId: "", quantity: "", unitPrice: "" }]);
    const [apiError, setApiError] = useState("");

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
            return products.filter((p) => p.category === "FUEL");
        }
        return products.filter((p) => p.category === "LUBRICANT" || p.category === "ACCESSORY");
    }, [products, invoiceType]);

    // Filter POs by selected supplier
    const filteredPOs = useMemo(() => {
        if (!supplierId) return [];
        return purchaseOrders.filter((po) => po.supplier?.id === Number(supplierId));
    }, [purchaseOrders, supplierId]);

    const addLineItem = () => {
        setLineItems([...lineItems, { productId: "", quantity: "", unitPrice: "" }]);
    };

    const removeLineItem = (idx: number) => {
        setLineItems(lineItems.filter((_, i) => i !== idx));
    };

    const updateLineItem = (idx: number, field: keyof LineItem, value: string) => {
        const updated = [...lineItems];
        updated[idx] = { ...updated[idx], [field]: value };
        setLineItems(updated);
    };

    const calcTotal = () => {
        return lineItems.reduce((sum, item) => {
            return sum + (Number(item.quantity) || 0) * (Number(item.unitPrice) || 0);
        }, 0);
    };

    const handleSave = async (e: React.FormEvent) => {
        e.preventDefault();
        setApiError("");
        if (!validate({ supplierId, invoiceNumber, invoiceDate })) return;
        try {
            const items = lineItems
                .filter((li) => li.productId && li.quantity)
                .map((li) => ({
                    product: { id: Number(li.productId) },
                    quantity: Number(li.quantity),
                    unitPrice: Number(li.unitPrice) || 0,
                    totalPrice: (Number(li.quantity) || 0) * (Number(li.unitPrice) || 0),
                }));

            const payload: any = {
                supplier: { id: Number(supplierId) },
                invoiceNumber,
                invoiceDate,
                deliveryDate: deliveryDate || null,
                invoiceType,
                status: "PENDING",
                totalAmount: calcTotal(),
                remarks: remarks || null,
                items,
            };

            if (purchaseOrderId) {
                payload.purchaseOrder = { id: Number(purchaseOrderId) };
            }

            let saved: PurchaseInvoice;
            if (editingId) {
                saved = await updatePurchaseInvoice(editingId, payload);
            } else {
                saved = await createPurchaseInvoice(payload);
            }

            // Upload PDF if selected
            if (pdfFile && saved.id) {
                await uploadPurchaseInvoicePdf(saved.id, pdfFile);
            }

            setIsModalOpen(false);
            resetForm();
            loadData();
        } catch (err: any) {
            console.error("Failed to save invoice", err);
            setApiError(err.message || "Error saving purchase invoice");
        }
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
        setInvoiceDate(invoice.invoiceDate);
        setDeliveryDate(invoice.deliveryDate || "");
        setPurchaseOrderId(invoice.purchaseOrder?.id ? String(invoice.purchaseOrder.id) : "");
        setRemarks(invoice.remarks || "");
        setPdfFile(null);
        setLineItems(
            invoice.items.map((item) => ({
                productId: String(item.product.id),
                quantity: String(item.quantity),
                unitPrice: String(item.unitPrice || ""),
            }))
        );
        setIsModalOpen(true);
    };

    const resetForm = () => {
        setEditingId(null);
        setInvoiceType("FUEL");
        setSupplierId("");
        setInvoiceNumber("");
        setInvoiceDate(new Date().toISOString().split("T")[0]);
        setDeliveryDate("");
        setPurchaseOrderId("");
        setRemarks("");
        setPdfFile(null);
        setLineItems([{ productId: "", quantity: "", unitPrice: "" }]);
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
                                                <td className="px-4 py-4 text-sm font-medium text-foreground">{inv.invoiceNumber}</td>
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
            <Modal isOpen={isModalOpen} onClose={() => { setIsModalOpen(false); resetForm(); }} title={editingId ? "Edit Purchase Invoice" : "New Purchase Invoice"}>
                <form onSubmit={handleSave} className="space-y-4">
                    <FormErrorBanner message={apiError} />

                    {/* Invoice Type Toggle */}
                    <div>
                        <label className="block text-sm font-medium text-foreground mb-1.5">Invoice Type</label>
                        <div className="flex gap-2">
                            <button
                                type="button"
                                onClick={() => { setInvoiceType("FUEL"); setLineItems([{ productId: "", quantity: "", unitPrice: "" }]); }}
                                className={`flex-1 py-2.5 rounded-xl text-sm font-medium border transition-colors ${invoiceType === "FUEL" ? "bg-orange-500/10 text-orange-500 border-orange-500/30" : "bg-background border-border text-muted-foreground hover:bg-muted"}`}
                            >
                                Fuel
                            </button>
                            <button
                                type="button"
                                onClick={() => { setInvoiceType("NON_FUEL"); setLineItems([{ productId: "", quantity: "", unitPrice: "" }]); }}
                                className={`flex-1 py-2.5 rounded-xl text-sm font-medium border transition-colors ${invoiceType === "NON_FUEL" ? "bg-purple-500/10 text-purple-500 border-purple-500/30" : "bg-background border-border text-muted-foreground hover:bg-muted"}`}
                            >
                                Non-Fuel
                            </button>
                        </div>
                    </div>

                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
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
                                placeholder="e.g. INV-2026-001"
                            />
                            <FieldError error={errors.invoiceNumber} />
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
                        <div className="space-y-2">
                            {lineItems.map((li, idx) => {
                                const selectedProduct = filteredProducts.find((p) => String(p.id) === li.productId);
                                return (
                                    <div key={idx} className="grid grid-cols-12 gap-2 items-center">
                                        <div className={invoiceType === "FUEL" ? "col-span-4" : "col-span-5"}>
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
                                        {invoiceType === "FUEL" && (
                                            <div className="col-span-1">
                                                <div className="text-xs text-muted-foreground bg-black/5 dark:bg-white/5 rounded-lg px-2 py-2.5 text-center truncate" title={selectedProduct?.gradeType?.name || "-"}>
                                                    {selectedProduct?.gradeType?.name || "-"}
                                                </div>
                                            </div>
                                        )}
                                        <div className="col-span-2">
                                            <input type="number" placeholder="Qty" value={li.quantity} onChange={(e) => updateLineItem(idx, "quantity", e.target.value)} className="w-full bg-background border border-border rounded-lg px-3 py-2 text-foreground text-sm" required min="0.01" step="any" />
                                        </div>
                                        <div className="col-span-2">
                                            <input type="number" placeholder="Rate" value={li.unitPrice} onChange={(e) => updateLineItem(idx, "unitPrice", e.target.value)} className="w-full bg-background border border-border rounded-lg px-3 py-2 text-foreground text-sm" min="0" step="any" />
                                        </div>
                                        <div className="col-span-2 text-right text-sm font-mono font-medium text-foreground">
                                            ₹{((Number(li.quantity) || 0) * (Number(li.unitPrice) || 0)).toLocaleString()}
                                        </div>
                                        <div className="col-span-1 text-center">
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
                        <div className="text-right mt-3 text-sm font-bold text-foreground">
                            Total: ₹{calcTotal().toLocaleString()}
                        </div>
                    </div>

                    <div>
                        <label className="block text-sm font-medium text-foreground mb-1.5">Remarks</label>
                        <textarea value={remarks} onChange={(e) => setRemarks(e.target.value)} className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground" placeholder="Optional notes..." rows={2} />
                    </div>

                    {/* PDF Upload */}
                    <div>
                        <label className="block text-sm font-medium text-foreground mb-1.5">
                            Invoice PDF <span className="text-muted-foreground text-xs">(optional)</span>
                        </label>
                        <div className="flex items-center gap-3">
                            <label className="flex items-center gap-2 px-4 py-2.5 bg-background border border-border rounded-xl text-sm text-muted-foreground hover:bg-muted cursor-pointer transition-colors">
                                <Upload className="w-4 h-4" />
                                {pdfFile ? pdfFile.name : "Choose file..."}
                                <input
                                    type="file"
                                    accept=".pdf"
                                    onChange={(e) => setPdfFile(e.target.files?.[0] || null)}
                                    className="hidden"
                                />
                            </label>
                            {pdfFile && (
                                <button type="button" onClick={() => setPdfFile(null)} className="text-xs text-muted-foreground hover:text-red-500">Remove</button>
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
                                <tr className="border-t border-border">
                                    <td colSpan={viewInvoice.invoiceType === "FUEL" ? 4 : 3} className="py-2 text-right font-bold text-foreground">Total:</td>
                                    <td className="py-2 text-right font-mono font-bold text-primary">₹{Number(viewInvoice.totalAmount || 0).toLocaleString()}</td>
                                </tr>
                            </tfoot>
                        </table>
                    </div>
                )}
            </Modal>
        </div>
    );
}
