"use client";

import { useEffect, useState } from "react";
import { GlassCard } from "@/components/ui/glass-card";
import { Modal } from "@/components/ui/modal";
import {
    getPurchaseOrders,
    getActiveProducts,
    getActiveSuppliers,
    createPurchaseOrder,
    updatePurchaseOrder,
    receivePurchaseOrder,
    cancelPurchaseOrder,
    PurchaseOrder,
    PurchaseOrderItem,
    ReceiveItemDTO,
    Product,
    Supplier,
} from "@/lib/api/station";
import { ClipboardList, Plus, Search, Package, Trash2, CheckCircle, XCircle, Eye } from "lucide-react";

const STATUS_COLORS: Record<string, string> = {
    DRAFT: "bg-gray-500/10 text-gray-500",
    ORDERED: "bg-blue-500/10 text-blue-500",
    PARTIALLY_RECEIVED: "bg-yellow-500/10 text-yellow-500",
    RECEIVED: "bg-green-500/10 text-green-500",
    CANCELLED: "bg-red-500/10 text-red-500",
};

interface LineItem {
    productId: string;
    orderedQty: string;
    unitPrice: string;
}

export default function PurchaseOrdersPage() {
    const [orders, setOrders] = useState<PurchaseOrder[]>([]);
    const [products, setProducts] = useState<Product[]>([]);
    const [suppliers, setSuppliers] = useState<Supplier[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [isReceiveModalOpen, setIsReceiveModalOpen] = useState(false);
    const [isViewModalOpen, setIsViewModalOpen] = useState(false);
    const [editingId, setEditingId] = useState<number | null>(null);
    const [searchQuery, setSearchQuery] = useState("");
    const [statusFilter, setStatusFilter] = useState("");
    const [viewOrder, setViewOrder] = useState<PurchaseOrder | null>(null);
    const [receiveOrder, setReceiveOrder] = useState<PurchaseOrder | null>(null);
    const [receiveItems, setReceiveItems] = useState<Record<number, string>>({});

    // Form state
    const [supplierId, setSupplierId] = useState("");
    const [orderDate, setOrderDate] = useState(new Date().toISOString().split("T")[0]);
    const [expectedDeliveryDate, setExpectedDeliveryDate] = useState("");
    const [remarks, setRemarks] = useState("");
    const [lineItems, setLineItems] = useState<LineItem[]>([{ productId: "", orderedQty: "", unitPrice: "" }]);

    const loadData = async () => {
        setIsLoading(true);
        try {
            const [orderData, productData, supplierData] = await Promise.all([
                getPurchaseOrders(statusFilter || undefined),
                getActiveProducts(),
                getActiveSuppliers(),
            ]);
            setOrders(orderData);
            setProducts(productData.filter((p) => p.category !== "FUEL"));
            setSuppliers(supplierData);
        } catch (err) {
            console.error("Failed to load PO data", err);
        }
        setIsLoading(false);
    };

    useEffect(() => {
        loadData();
    }, [statusFilter]);

    const addLineItem = () => {
        setLineItems([...lineItems, { productId: "", orderedQty: "", unitPrice: "" }]);
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
            return sum + (Number(item.orderedQty) || 0) * (Number(item.unitPrice) || 0);
        }, 0);
    };

    const handleSave = async (e: React.FormEvent) => {
        e.preventDefault();
        try {
            const items = lineItems
                .filter((li) => li.productId && li.orderedQty)
                .map((li) => ({
                    product: { id: Number(li.productId) },
                    orderedQty: Number(li.orderedQty),
                    receivedQty: 0,
                    unitPrice: Number(li.unitPrice) || 0,
                    totalPrice: (Number(li.orderedQty) || 0) * (Number(li.unitPrice) || 0),
                }));

            const payload = {
                supplier: { id: Number(supplierId) },
                orderDate,
                expectedDeliveryDate: expectedDeliveryDate || null,
                status: "DRAFT",
                totalAmount: calcTotal(),
                remarks: remarks || null,
                items,
            };

            if (editingId) {
                await updatePurchaseOrder(editingId, payload as any);
            } else {
                await createPurchaseOrder(payload as any);
            }
            setIsModalOpen(false);
            resetForm();
            loadData();
        } catch (err) {
            console.error("Failed to save PO", err);
            alert("Error saving purchase order");
        }
    };

    const handleCancel = async (id: number) => {
        if (!confirm("Are you sure you want to cancel this purchase order?")) return;
        try {
            await cancelPurchaseOrder(id);
            loadData();
        } catch (err: any) {
            alert(err.message || "Error cancelling order");
        }
    };

    const handleView = (order: PurchaseOrder) => {
        setViewOrder(order);
        setIsViewModalOpen(true);
    };

    const handleReceiveOpen = (order: PurchaseOrder) => {
        setReceiveOrder(order);
        const initial: Record<number, string> = {};
        order.items.forEach((item) => {
            if (item.id) initial[item.id] = "";
        });
        setReceiveItems(initial);
        setIsReceiveModalOpen(true);
    };

    const handleReceive = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!receiveOrder) return;
        try {
            const items: ReceiveItemDTO[] = Object.entries(receiveItems)
                .filter(([, qty]) => Number(qty) > 0)
                .map(([itemId, qty]) => ({
                    itemId: Number(itemId),
                    receivedQty: Number(qty),
                }));
            if (items.length === 0) {
                alert("Please enter quantities for at least one item");
                return;
            }
            await receivePurchaseOrder(receiveOrder.id!, items);
            setIsReceiveModalOpen(false);
            setReceiveOrder(null);
            loadData();
        } catch (err: any) {
            alert(err.message || "Error receiving delivery");
        }
    };

    const handleEdit = (order: PurchaseOrder) => {
        setEditingId(order.id!);
        setSupplierId(String(order.supplier.id));
        setOrderDate(order.orderDate);
        setExpectedDeliveryDate(order.expectedDeliveryDate || "");
        setRemarks(order.remarks || "");
        setLineItems(
            order.items.map((item) => ({
                productId: String(item.product.id),
                orderedQty: String(item.orderedQty),
                unitPrice: String(item.unitPrice || ""),
            }))
        );
        setIsModalOpen(true);
    };

    const resetForm = () => {
        setEditingId(null);
        setSupplierId("");
        setOrderDate(new Date().toISOString().split("T")[0]);
        setExpectedDeliveryDate("");
        setRemarks("");
        setLineItems([{ productId: "", orderedQty: "", unitPrice: "" }]);
    };

    const filtered = orders.filter((o) => {
        const q = searchQuery.toLowerCase();
        return !searchQuery || o.supplier?.name?.toLowerCase().includes(q) || o.remarks?.toLowerCase().includes(q);
    });

    return (
        <div className="p-8 min-h-screen bg-background transition-colors duration-300">
            <div className="max-w-7xl mx-auto">
                <div className="flex justify-between items-center mb-8">
                    <div>
                        <h1 className="text-4xl font-bold text-foreground tracking-tight">
                            Purchase <span className="text-gradient">Orders</span>
                        </h1>
                        <p className="text-muted-foreground mt-2">
                            Order non-fuel products from suppliers and receive deliveries.
                        </p>
                    </div>
                    <button
                        onClick={() => { resetForm(); setIsModalOpen(true); }}
                        className="btn-gradient px-6 py-3 rounded-xl font-medium flex items-center gap-2 shadow-lg hover:shadow-xl transition-all"
                    >
                        <Plus className="w-5 h-5" />
                        Create PO
                    </button>
                </div>

                {isLoading ? (
                    <div className="flex flex-col items-center justify-center py-20 text-muted-foreground">
                        <div className="w-12 h-12 border-4 border-primary/20 border-t-primary rounded-full animate-spin mb-4"></div>
                        <p className="animate-pulse">Loading purchase orders...</p>
                    </div>
                ) : orders.length === 0 && !statusFilter ? (
                    <div className="text-center py-20 bg-black/5 dark:bg-white/5 rounded-2xl border border-dashed border-border">
                        <ClipboardList className="w-16 h-16 mx-auto text-muted-foreground mb-4 opacity-50" />
                        <h3 className="text-xl font-semibold text-foreground mb-2">No Purchase Orders</h3>
                        <p className="text-muted-foreground mb-6">Create your first purchase order to start ordering from suppliers.</p>
                    </div>
                ) : (
                    <>
                        <div className="mb-6 flex flex-wrap gap-3 items-center">
                            <div className="relative flex-1 min-w-[200px] max-w-md">
                                <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                                <input
                                    type="text"
                                    placeholder="Search by supplier, remarks..."
                                    value={searchQuery}
                                    onChange={(e) => setSearchQuery(e.target.value)}
                                    className="w-full pl-10 pr-4 py-2.5 bg-card border border-border rounded-xl text-foreground text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                                />
                            </div>
                            <select
                                value={statusFilter}
                                onChange={(e) => setStatusFilter(e.target.value)}
                                className="px-4 py-2.5 bg-card border border-border rounded-xl text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
                            >
                                <option value="">All Statuses</option>
                                <option value="DRAFT">Draft</option>
                                <option value="ORDERED">Ordered</option>
                                <option value="PARTIALLY_RECEIVED">Partially Received</option>
                                <option value="RECEIVED">Received</option>
                                <option value="CANCELLED">Cancelled</option>
                            </select>
                            {(searchQuery || statusFilter) && (
                                <button onClick={() => { setSearchQuery(""); setStatusFilter(""); }} className="px-3 py-2.5 text-xs text-muted-foreground hover:text-foreground">
                                    Clear
                                </button>
                            )}
                        </div>

                        <GlassCard className="overflow-hidden border-none p-0">
                            <div className="overflow-x-auto">
                                <table className="w-full text-left border-collapse">
                                    <thead>
                                        <tr className="bg-white/5 border-b border-border/50">
                                            <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center w-16">#</th>
                                            <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Date</th>
                                            <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Supplier</th>
                                            <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center">Items</th>
                                            <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right">Total</th>
                                            <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center">Status</th>
                                            <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center w-36">Actions</th>
                                        </tr>
                                    </thead>
                                    <tbody className="divide-y divide-border/30">
                                        {filtered.map((order, idx) => (
                                            <tr key={order.id} className="hover:bg-white/5 transition-colors">
                                                <td className="px-6 py-4 text-xs font-mono text-muted-foreground text-center">{idx + 1}</td>
                                                <td className="px-6 py-4">
                                                    <div className="text-sm font-medium text-foreground">{new Date(order.orderDate).toLocaleDateString()}</div>
                                                    {order.expectedDeliveryDate && (
                                                        <div className="text-[10px] text-muted-foreground">Expected: {new Date(order.expectedDeliveryDate).toLocaleDateString()}</div>
                                                    )}
                                                </td>
                                                <td className="px-6 py-4 text-sm font-medium text-foreground">{order.supplier?.name}</td>
                                                <td className="px-6 py-4 text-center text-sm font-mono text-muted-foreground">{order.items?.length || 0}</td>
                                                <td className="px-6 py-4 text-right font-bold font-mono text-foreground">
                                                    {order.totalAmount != null ? `₹${Number(order.totalAmount).toLocaleString()}` : '-'}
                                                </td>
                                                <td className="px-6 py-4 text-center">
                                                    <span className={`px-3 py-1 rounded-full text-[10px] font-bold uppercase ${STATUS_COLORS[order.status] || ''}`}>
                                                        {order.status?.replace('_', ' ')}
                                                    </span>
                                                </td>
                                                <td className="px-6 py-4">
                                                    <div className="flex justify-center gap-1">
                                                        <button onClick={() => handleView(order)} className="p-1.5 rounded-lg hover:bg-white/10 text-muted-foreground hover:text-foreground transition-colors" title="View">
                                                            <Eye className="w-4 h-4" />
                                                        </button>
                                                        {order.status === "DRAFT" && (
                                                            <button onClick={() => handleEdit(order)} className="p-1.5 rounded-lg hover:bg-white/10 text-muted-foreground hover:text-blue-500 transition-colors" title="Edit">
                                                                <Package className="w-4 h-4" />
                                                            </button>
                                                        )}
                                                        {(order.status === "ORDERED" || order.status === "PARTIALLY_RECEIVED") && (
                                                            <button onClick={() => handleReceiveOpen(order)} className="p-1.5 rounded-lg hover:bg-green-500/10 text-muted-foreground hover:text-green-500 transition-colors" title="Receive">
                                                                <CheckCircle className="w-4 h-4" />
                                                            </button>
                                                        )}
                                                        {(order.status === "DRAFT" || order.status === "ORDERED") && (
                                                            <button onClick={() => handleCancel(order.id!)} className="p-1.5 rounded-lg hover:bg-red-500/10 text-muted-foreground hover:text-red-500 transition-colors" title="Cancel">
                                                                <XCircle className="w-4 h-4" />
                                                            </button>
                                                        )}
                                                    </div>
                                                </td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                        </GlassCard>
                    </>
                )}
            </div>

            {/* Create/Edit PO Modal */}
            <Modal isOpen={isModalOpen} onClose={() => { setIsModalOpen(false); resetForm(); }} title={editingId ? "Edit Purchase Order" : "Create Purchase Order"}>
                <form onSubmit={handleSave} className="space-y-4">
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5">Supplier</label>
                            <select required value={supplierId} onChange={(e) => setSupplierId(e.target.value)} className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground">
                                <option value="">Select supplier...</option>
                                {suppliers.map((s) => (
                                    <option key={s.id} value={s.id}>{s.name}</option>
                                ))}
                            </select>
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5">Order Date</label>
                            <input type="date" required value={orderDate} onChange={(e) => setOrderDate(e.target.value)} className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground" />
                        </div>
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-foreground mb-1.5">Expected Delivery Date</label>
                        <input type="date" value={expectedDeliveryDate} onChange={(e) => setExpectedDeliveryDate(e.target.value)} className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground" />
                    </div>

                    {/* Line Items */}
                    <div>
                        <div className="flex justify-between items-center mb-2">
                            <label className="text-sm font-medium text-foreground">Items</label>
                            <button type="button" onClick={addLineItem} className="text-xs text-primary hover:underline">+ Add Item</button>
                        </div>
                        <div className="space-y-2">
                            {lineItems.map((li, idx) => (
                                <div key={idx} className="grid grid-cols-12 gap-2 items-center">
                                    <div className="col-span-5">
                                        <select value={li.productId} onChange={(e) => updateLineItem(idx, "productId", e.target.value)} className="w-full bg-background border border-border rounded-lg px-3 py-2 text-foreground text-sm" required>
                                            <option value="">Product...</option>
                                            {products.map((p) => (
                                                <option key={p.id} value={p.id}>{p.name}</option>
                                            ))}
                                        </select>
                                    </div>
                                    <div className="col-span-2">
                                        <input type="number" placeholder="Qty" value={li.orderedQty} onChange={(e) => updateLineItem(idx, "orderedQty", e.target.value)} className="w-full bg-background border border-border rounded-lg px-3 py-2 text-foreground text-sm" required min="0.01" step="any" />
                                    </div>
                                    <div className="col-span-2">
                                        <input type="number" placeholder="Price" value={li.unitPrice} onChange={(e) => updateLineItem(idx, "unitPrice", e.target.value)} className="w-full bg-background border border-border rounded-lg px-3 py-2 text-foreground text-sm" min="0" step="any" />
                                    </div>
                                    <div className="col-span-2 text-right text-sm font-mono font-medium text-foreground">
                                        ₹{((Number(li.orderedQty) || 0) * (Number(li.unitPrice) || 0)).toLocaleString()}
                                    </div>
                                    <div className="col-span-1 text-center">
                                        {lineItems.length > 1 && (
                                            <button type="button" onClick={() => removeLineItem(idx)} className="text-muted-foreground hover:text-red-500">
                                                <Trash2 className="w-3.5 h-3.5" />
                                            </button>
                                        )}
                                    </div>
                                </div>
                            ))}
                        </div>
                        <div className="text-right mt-3 text-sm font-bold text-foreground">
                            Total: ₹{calcTotal().toLocaleString()}
                        </div>
                    </div>

                    <div>
                        <label className="block text-sm font-medium text-foreground mb-1.5">Remarks</label>
                        <textarea value={remarks} onChange={(e) => setRemarks(e.target.value)} className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground" placeholder="Optional notes..." rows={2} />
                    </div>

                    <div className="flex justify-end gap-3 pt-6 border-t border-border mt-6">
                        <button type="button" onClick={() => setIsModalOpen(false)} className="px-6 py-2.5 rounded-xl font-medium text-foreground bg-black/5 dark:bg-white/5 hover:bg-black/10 transition-colors">Cancel</button>
                        <button type="submit" className="btn-gradient px-8 py-2.5 rounded-xl font-medium shadow-lg hover:shadow-xl transition-all">
                            {editingId ? "Update PO" : "Create PO"}
                        </button>
                    </div>
                </form>
            </Modal>

            {/* View PO Modal */}
            <Modal isOpen={isViewModalOpen} onClose={() => { setIsViewModalOpen(false); setViewOrder(null); }} title="Purchase Order Details">
                {viewOrder && (
                    <div className="space-y-4">
                        <div className="grid grid-cols-2 gap-4 text-sm">
                            <div><span className="text-muted-foreground">Supplier:</span> <span className="font-medium text-foreground">{viewOrder.supplier?.name}</span></div>
                            <div><span className="text-muted-foreground">Status:</span> <span className={`ml-2 px-2 py-0.5 rounded-full text-[10px] font-bold uppercase ${STATUS_COLORS[viewOrder.status]}`}>{viewOrder.status?.replace('_', ' ')}</span></div>
                            <div><span className="text-muted-foreground">Order Date:</span> <span className="font-medium text-foreground">{new Date(viewOrder.orderDate).toLocaleDateString()}</span></div>
                            <div><span className="text-muted-foreground">Expected:</span> <span className="font-medium text-foreground">{viewOrder.expectedDeliveryDate ? new Date(viewOrder.expectedDeliveryDate).toLocaleDateString() : '-'}</span></div>
                        </div>
                        {viewOrder.remarks && <div className="text-sm text-muted-foreground bg-black/5 dark:bg-white/5 rounded-xl p-3">{viewOrder.remarks}</div>}
                        <table className="w-full text-sm border-collapse">
                            <thead>
                                <tr className="border-b border-border">
                                    <th className="text-left py-2 text-muted-foreground font-medium">Product</th>
                                    <th className="text-right py-2 text-muted-foreground font-medium">Ordered</th>
                                    <th className="text-right py-2 text-muted-foreground font-medium">Received</th>
                                    <th className="text-right py-2 text-muted-foreground font-medium">Unit Price</th>
                                    <th className="text-right py-2 text-muted-foreground font-medium">Total</th>
                                </tr>
                            </thead>
                            <tbody>
                                {viewOrder.items?.map((item, idx) => (
                                    <tr key={idx} className="border-b border-border/30">
                                        <td className="py-2 font-medium text-foreground">{item.product?.name}</td>
                                        <td className="py-2 text-right font-mono">{item.orderedQty}</td>
                                        <td className={`py-2 text-right font-mono ${item.receivedQty >= item.orderedQty ? 'text-green-500' : 'text-yellow-500'}`}>{item.receivedQty || 0}</td>
                                        <td className="py-2 text-right font-mono">₹{Number(item.unitPrice || 0).toLocaleString()}</td>
                                        <td className="py-2 text-right font-mono font-bold">₹{Number(item.totalPrice || 0).toLocaleString()}</td>
                                    </tr>
                                ))}
                            </tbody>
                            <tfoot>
                                <tr className="border-t border-border">
                                    <td colSpan={4} className="py-2 text-right font-bold text-foreground">Total:</td>
                                    <td className="py-2 text-right font-mono font-bold text-primary">₹{Number(viewOrder.totalAmount || 0).toLocaleString()}</td>
                                </tr>
                            </tfoot>
                        </table>
                    </div>
                )}
            </Modal>

            {/* Receive Delivery Modal */}
            <Modal isOpen={isReceiveModalOpen} onClose={() => { setIsReceiveModalOpen(false); setReceiveOrder(null); }} title="Receive Delivery">
                {receiveOrder && (
                    <form onSubmit={handleReceive} className="space-y-4">
                        <p className="text-sm text-muted-foreground">Enter the quantity received for each item:</p>
                        <div className="space-y-3">
                            {receiveOrder.items?.map((item) => {
                                const remaining = item.orderedQty - (item.receivedQty || 0);
                                return (
                                    <div key={item.id} className="flex items-center gap-4 p-3 bg-black/5 dark:bg-white/5 rounded-xl">
                                        <div className="flex-1">
                                            <div className="text-sm font-medium text-foreground">{item.product?.name}</div>
                                            <div className="text-[10px] text-muted-foreground">
                                                Ordered: {item.orderedQty} | Received: {item.receivedQty || 0} | Remaining: {remaining}
                                            </div>
                                        </div>
                                        <div className="w-28">
                                            <input
                                                type="number"
                                                value={receiveItems[item.id!] || ""}
                                                onChange={(e) => setReceiveItems({ ...receiveItems, [item.id!]: e.target.value })}
                                                className="w-full bg-background border border-border rounded-lg px-3 py-2 text-foreground text-sm text-center"
                                                placeholder="0"
                                                min="0"
                                                max={remaining}
                                                step="any"
                                            />
                                        </div>
                                    </div>
                                );
                            })}
                        </div>
                        <div className="flex justify-end gap-3 pt-6 border-t border-border mt-6">
                            <button type="button" onClick={() => setIsReceiveModalOpen(false)} className="px-6 py-2.5 rounded-xl font-medium text-foreground bg-black/5 dark:bg-white/5 hover:bg-black/10 transition-colors">Cancel</button>
                            <button type="submit" className="btn-gradient px-8 py-2.5 rounded-xl font-medium shadow-lg hover:shadow-xl transition-all">
                                Confirm Receipt
                            </button>
                        </div>
                    </form>
                )}
            </Modal>
        </div>
    );
}
