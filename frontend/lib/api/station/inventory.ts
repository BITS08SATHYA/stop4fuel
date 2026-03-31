import { API_BASE_URL, handleResponse } from './common';
import { fetchWithAuth } from '../fetch-with-auth';
import type { Tank, Nozzle } from './infrastructure';
import type { Product, Supplier } from './products';

// --- Types ---
export interface TankInventory {
    id: number;
    date: string;
    tank: Tank;
    openDip?: string;
    openStock?: number;
    incomeStock?: number;
    totalStock?: number;
    closeDip?: string;
    closeStock?: number;
    saleStock?: number;
}

export interface NozzleInventory {
    id: number;
    date: string;
    nozzle: Nozzle;
    openMeterReading: number;
    closeMeterReading: number;
    sales: number;
    rate?: number;
    amount?: number;
}

export interface ProductInventory {
    id: number;
    date: string;
    product: Product;
    openStock?: number;
    incomeStock?: number;
    totalStock?: number;
    closeStock?: number;
    sales?: number;
    rate?: number;
    amount?: number;
}

export interface GodownStock {
    id: number;
    product: Product;
    currentStock: number;
    reorderLevel: number;
    maxStock: number;
    location?: string;
    lastRestockDate?: string;
}

export interface CashierStock {
    id: number;
    product: Product;
    currentStock: number;
    maxCapacity: number;
}

export interface StockTransfer {
    id: number;
    product: Product;
    quantity: number;
    fromLocation: 'GODOWN' | 'CASHIER';
    toLocation: 'GODOWN' | 'CASHIER';
    transferDate: string;
    remarks?: string;
    transferredBy?: string;
}

export interface PurchaseOrderItem {
    id?: number;
    product: Product;
    orderedQty: number;
    receivedQty: number;
    unitPrice: number;
    totalPrice: number;
}

export interface PurchaseOrder {
    id?: number;
    supplier: Supplier;
    orderDate: string;
    expectedDeliveryDate?: string;
    status: 'DRAFT' | 'ORDERED' | 'PARTIALLY_RECEIVED' | 'RECEIVED' | 'CANCELLED';
    totalAmount: number;
    remarks?: string;
    items: PurchaseOrderItem[];
}

export interface ReceiveItemDTO {
    itemId: number;
    receivedQty: number;
}

export interface PurchaseInvoiceItem {
    id?: number;
    product: Product;
    quantity: number;
    unitPrice: number;
    totalPrice: number;
}

export interface PurchaseInvoice {
    id?: number;
    supplier: Supplier;
    purchaseOrder?: PurchaseOrder;
    invoiceNumber: string;
    invoiceDate: string;
    deliveryDate?: string;
    invoiceType: 'FUEL' | 'NON_FUEL';
    status: 'PENDING' | 'VERIFIED' | 'PAID';
    totalAmount: number;
    remarks?: string;
    pdfFilePath?: string;
    items: PurchaseInvoiceItem[];
}

// Utility Bills
export interface UtilityBill {
    id: number;
    billType: string; // ELECTRICITY, WATER
    provider: string;
    consumerNumber: string;
    billDate: string;
    dueDate: string;
    billAmount: number;
    paidAmount: number;
    status: string; // PENDING, PAID, OVERDUE
    unitsConsumed?: number;
    billPeriod?: string;
    remarks?: string;
}

// Daily Inventory - Nozzles
export const getNozzleInventories = (params?: { fromDate?: string; toDate?: string; nozzleId?: number; productId?: number }): Promise<NozzleInventory[]> => {
    const query = new URLSearchParams();
    if (params?.fromDate) query.set('fromDate', params.fromDate);
    if (params?.toDate) query.set('toDate', params.toDate);
    if (params?.nozzleId) query.set('nozzleId', String(params.nozzleId));
    if (params?.productId) query.set('productId', String(params.productId));
    const qs = query.toString();
    return fetchWithAuth(`${API_BASE_URL}/inventory/nozzles${qs ? '?' + qs : ''}`).then(handleResponse);
};

export const downloadNozzleInventoryReport = (fromDate: string, toDate: string, format: 'pdf' | 'excel', nozzleId?: number, productId?: number, reportType?: string): Promise<Blob> => {
    const query = new URLSearchParams({ fromDate, toDate, format });
    if (nozzleId) query.set('nozzleId', String(nozzleId));
    if (productId) query.set('productId', String(productId));
    if (reportType) query.set('reportType', reportType);
    return fetchWithAuth(`${API_BASE_URL}/inventory/nozzles/report?${query.toString()}`).then(res => {
        if (!res.ok) throw new Error('Report generation failed');
        return res.blob();
    });
};

export const createNozzleInventory = (inventory: Partial<NozzleInventory>): Promise<NozzleInventory> =>
    fetchWithAuth(`${API_BASE_URL}/inventory/nozzles`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(inventory),
    }).then(handleResponse);

export const deleteNozzleInventory = (id: number): Promise<void> =>
    fetchWithAuth(`${API_BASE_URL}/inventory/nozzles/${id}`, { method: 'DELETE' }).then(handleResponse);

// Daily Inventory - Tanks
export const getTankInventories = (params?: { fromDate?: string; toDate?: string; tankId?: number }): Promise<TankInventory[]> => {
    const query = new URLSearchParams();
    if (params?.fromDate) query.set('fromDate', params.fromDate);
    if (params?.toDate) query.set('toDate', params.toDate);
    if (params?.tankId) query.set('tankId', String(params.tankId));
    const qs = query.toString();
    return fetchWithAuth(`${API_BASE_URL}/inventory/tanks${qs ? '?' + qs : ''}`).then(handleResponse);
};

export const downloadTankDipReport = (fromDate: string, toDate: string, format: 'pdf' | 'excel', tankId?: number): Promise<Blob> => {
    const query = new URLSearchParams({ fromDate, toDate, format });
    if (tankId) query.set('tankId', String(tankId));
    return fetchWithAuth(`${API_BASE_URL}/inventory/tanks/report?${query.toString()}`).then(res => {
        if (!res.ok) throw new Error('Report generation failed');
        return res.blob();
    });
};

export const createTankInventory = (inventory: Partial<TankInventory>): Promise<TankInventory> =>
    fetchWithAuth(`${API_BASE_URL}/inventory/tanks`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(inventory),
    }).then(handleResponse);

export const deleteTankInventory = (id: number): Promise<void> =>
    fetchWithAuth(`${API_BASE_URL}/inventory/tanks/${id}`, { method: 'DELETE' }).then(handleResponse);

// Daily Inventory - Products
export const getProductInventories = (params?: { fromDate?: string; toDate?: string; productId?: number }): Promise<ProductInventory[]> => {
    const query = new URLSearchParams();
    if (params?.fromDate) query.set('fromDate', params.fromDate);
    if (params?.toDate) query.set('toDate', params.toDate);
    if (params?.productId) query.set('productId', String(params.productId));
    const qs = query.toString();
    return fetchWithAuth(`${API_BASE_URL}/inventory/products${qs ? '?' + qs : ''}`).then(handleResponse);
};

export const downloadProductInventoryReport = (fromDate: string, toDate: string, format: 'pdf' | 'excel', productId?: number): Promise<Blob> => {
    const query = new URLSearchParams({ fromDate, toDate, format });
    if (productId) query.set('productId', String(productId));
    return fetchWithAuth(`${API_BASE_URL}/inventory/products/report?${query.toString()}`).then(res => {
        if (!res.ok) throw new Error('Report generation failed');
        return res.blob();
    });
};

export const createProductInventory = (inventory: Partial<ProductInventory>): Promise<ProductInventory> =>
    fetchWithAuth(`${API_BASE_URL}/inventory/products`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(inventory),
    }).then(handleResponse);

export const deleteProductInventory = (id: number): Promise<void> =>
    fetchWithAuth(`${API_BASE_URL}/inventory/products/${id}`, { method: 'DELETE' }).then(handleResponse);

export const updateTankInventory = (id: number, inventory: Partial<TankInventory>): Promise<TankInventory> =>
    fetchWithAuth(`${API_BASE_URL}/inventory/tanks/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(inventory),
    }).then(handleResponse);

export const updateNozzleInventory = (id: number, inventory: Partial<NozzleInventory>): Promise<NozzleInventory> =>
    fetchWithAuth(`${API_BASE_URL}/inventory/nozzles/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(inventory),
    }).then(handleResponse);

export const updateProductInventory = (id: number, inventory: Partial<ProductInventory>): Promise<ProductInventory> =>
    fetchWithAuth(`${API_BASE_URL}/inventory/products/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(inventory),
    }).then(handleResponse);

// --- Godown Stock ---
export const getGodownStocks = (): Promise<GodownStock[]> =>
    fetchWithAuth(`${API_BASE_URL}/godown`).then(handleResponse);

export const getGodownStockByProduct = (productId: number): Promise<GodownStock[]> =>
    fetchWithAuth(`${API_BASE_URL}/godown?productId=${productId}`).then(handleResponse);

export const getGodownLowStock = (): Promise<GodownStock[]> =>
    fetchWithAuth(`${API_BASE_URL}/godown/low-stock`).then(handleResponse);

export const createGodownStock = (stock: Partial<GodownStock>): Promise<GodownStock> =>
    fetchWithAuth(`${API_BASE_URL}/godown`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(stock),
    }).then(handleResponse);

export const updateGodownStock = (id: number, stock: Partial<GodownStock>): Promise<GodownStock> =>
    fetchWithAuth(`${API_BASE_URL}/godown/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(stock),
    }).then(handleResponse);

export const deleteGodownStock = (id: number): Promise<void> =>
    fetchWithAuth(`${API_BASE_URL}/godown/${id}`, { method: 'DELETE' }).then(handleResponse);

// --- Cashier Stock ---
export const getCashierStocks = (): Promise<CashierStock[]> =>
    fetchWithAuth(`${API_BASE_URL}/cashier-stock`).then(handleResponse);

export const getCashierStockByProduct = (productId: number): Promise<CashierStock[]> =>
    fetchWithAuth(`${API_BASE_URL}/cashier-stock?productId=${productId}`).then(handleResponse);

export const createCashierStock = (stock: Partial<CashierStock>): Promise<CashierStock> =>
    fetchWithAuth(`${API_BASE_URL}/cashier-stock`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(stock),
    }).then(handleResponse);

export const updateCashierStock = (id: number, stock: Partial<CashierStock>): Promise<CashierStock> =>
    fetchWithAuth(`${API_BASE_URL}/cashier-stock/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(stock),
    }).then(handleResponse);

export const deleteCashierStock = (id: number): Promise<void> =>
    fetchWithAuth(`${API_BASE_URL}/cashier-stock/${id}`, { method: 'DELETE' }).then(handleResponse);

// --- Stock Transfers ---
export const getStockTransfers = (productId?: number, from?: string, to?: string): Promise<StockTransfer[]> => {
    const params = new URLSearchParams();
    if (productId) params.append('productId', String(productId));
    if (from) params.append('from', from);
    if (to) params.append('to', to);
    const qs = params.toString();
    return fetchWithAuth(`${API_BASE_URL}/stock-transfers${qs ? `?${qs}` : ''}`).then(handleResponse);
};

export const createStockTransfer = (transfer: Partial<StockTransfer>): Promise<StockTransfer> =>
    fetchWithAuth(`${API_BASE_URL}/stock-transfers`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(transfer),
    }).then(handleResponse);

export const updateStockTransfer = (id: number, transfer: Partial<StockTransfer>): Promise<StockTransfer> =>
    fetchWithAuth(`${API_BASE_URL}/stock-transfers/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(transfer),
    }).then(handleResponse);

export const deleteStockTransfer = (id: number): Promise<void> =>
    fetchWithAuth(`${API_BASE_URL}/stock-transfers/${id}`, { method: 'DELETE' }).then(handleResponse);

// --- Purchase Orders ---
export const getPurchaseOrders = (status?: string, supplierId?: number): Promise<PurchaseOrder[]> => {
    const params = new URLSearchParams();
    if (status) params.append('status', status);
    if (supplierId) params.append('supplierId', String(supplierId));
    const qs = params.toString();
    return fetchWithAuth(`${API_BASE_URL}/purchase-orders${qs ? `?${qs}` : ''}`).then(handleResponse);
};

export const getPurchaseOrderById = (id: number): Promise<PurchaseOrder> =>
    fetchWithAuth(`${API_BASE_URL}/purchase-orders/${id}`).then(handleResponse);

export const createPurchaseOrder = (order: Partial<PurchaseOrder>): Promise<PurchaseOrder> =>
    fetchWithAuth(`${API_BASE_URL}/purchase-orders`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(order),
    }).then(handleResponse);

export const updatePurchaseOrder = (id: number, order: Partial<PurchaseOrder>): Promise<PurchaseOrder> =>
    fetchWithAuth(`${API_BASE_URL}/purchase-orders/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(order),
    }).then(handleResponse);

export const receivePurchaseOrder = (id: number, items: ReceiveItemDTO[]): Promise<PurchaseOrder> =>
    fetchWithAuth(`${API_BASE_URL}/purchase-orders/${id}/receive`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(items),
    }).then(handleResponse);

export const cancelPurchaseOrder = (id: number): Promise<PurchaseOrder> =>
    fetchWithAuth(`${API_BASE_URL}/purchase-orders/${id}/cancel`, { method: 'PATCH' }).then(handleResponse);

// --- Purchase Invoices ---
export const getPurchaseInvoices = (status?: string, supplierId?: number, type?: string): Promise<PurchaseInvoice[]> => {
    const params = new URLSearchParams();
    if (status) params.append('status', status);
    if (supplierId) params.append('supplierId', String(supplierId));
    if (type) params.append('type', type);
    const qs = params.toString();
    return fetchWithAuth(`${API_BASE_URL}/purchase-invoices${qs ? `?${qs}` : ''}`).then(handleResponse);
};

export const getPurchaseInvoiceById = (id: number): Promise<PurchaseInvoice> =>
    fetchWithAuth(`${API_BASE_URL}/purchase-invoices/${id}`).then(handleResponse);

export const createPurchaseInvoice = (invoice: Partial<PurchaseInvoice>): Promise<PurchaseInvoice> =>
    fetchWithAuth(`${API_BASE_URL}/purchase-invoices`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(invoice),
    }).then(handleResponse);

export const updatePurchaseInvoice = (id: number, invoice: Partial<PurchaseInvoice>): Promise<PurchaseInvoice> =>
    fetchWithAuth(`${API_BASE_URL}/purchase-invoices/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(invoice),
    }).then(handleResponse);

export const deletePurchaseInvoice = (id: number): Promise<void> =>
    fetchWithAuth(`${API_BASE_URL}/purchase-invoices/${id}`, { method: 'DELETE' }).then(handleResponse);

export const updatePurchaseInvoiceStatus = (id: number, status: string): Promise<PurchaseInvoice> =>
    fetchWithAuth(`${API_BASE_URL}/purchase-invoices/${id}/status?status=${status}`, { method: 'PATCH' }).then(handleResponse);

export const uploadPurchaseInvoicePdf = (id: number, file: File): Promise<PurchaseInvoice> => {
    const formData = new FormData();
    formData.append('file', file);
    return fetchWithAuth(`${API_BASE_URL}/purchase-invoices/${id}/upload-pdf`, {
        method: 'POST',
        body: formData,
    }).then(handleResponse);
};

export const getPurchaseInvoicePdfUrl = (id: number): Promise<string> =>
    fetchWithAuth(`${API_BASE_URL}/purchase-invoices/${id}/pdf-url`).then(handleResponse).then((data: { url: string }) => data.url);

export const downloadPurchaseInvoiceReport = (fromDate: string, toDate: string, format: 'pdf' | 'excel'): Promise<Blob> => {
    const query = new URLSearchParams({ fromDate, toDate, format });
    return fetchWithAuth(`${API_BASE_URL}/purchase-invoices/report?${query.toString()}`).then(res => {
        if (!res.ok) throw new Error('Report generation failed');
        return res.blob();
    });
};

// --- Utility Bills ---
export const getUtilityBills = (type?: string): Promise<UtilityBill[]> => {
    const params = type ? `?type=${type}` : '';
    return fetchWithAuth(`${API_BASE_URL}/utility-bills${params}`).then(handleResponse);
};

export const getPendingUtilityBills = (): Promise<UtilityBill[]> =>
    fetchWithAuth(`${API_BASE_URL}/utility-bills/pending`).then(handleResponse);

export const createUtilityBill = (bill: Partial<UtilityBill>): Promise<UtilityBill> =>
    fetchWithAuth(`${API_BASE_URL}/utility-bills`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(bill),
    }).then(handleResponse);

export const updateUtilityBill = (id: number, bill: Partial<UtilityBill>): Promise<UtilityBill> =>
    fetchWithAuth(`${API_BASE_URL}/utility-bills/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(bill),
    }).then(handleResponse);

export const deleteUtilityBill = (id: number): Promise<void> =>
    fetchWithAuth(`${API_BASE_URL}/utility-bills/${id}`, { method: 'DELETE' }).then(handleResponse);

export const uploadUtilityBillPdf = (file: File): Promise<UtilityBill> => {
    const formData = new FormData();
    formData.append('file', file);
    return fetchWithAuth(`${API_BASE_URL}/utility-bills/upload-pdf`, {
        method: 'POST',
        body: formData,
    }).then(handleResponse);
};

export const uploadBulkUtilityBillPdfs = (files: File[]): Promise<UtilityBill[]> => {
    const formData = new FormData();
    files.forEach(f => formData.append('files', f));
    return fetchWithAuth(`${API_BASE_URL}/utility-bills/upload-bulk`, {
        method: 'POST',
        body: formData,
    }).then(handleResponse);
};
