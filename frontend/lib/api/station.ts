import { fetchWithAuth } from './fetch-with-auth';

// In the browser, derive the API URL from the current hostname so it works
// on any deployment (localhost, EC2 public IP, custom domain) without needing
// NEXT_PUBLIC_API_URL to be baked in at build time.
const getApiBaseUrl = () => {
    if (typeof window !== 'undefined') {
        // Browser: use same hostname, port 8080
        return process.env.NEXT_PUBLIC_API_URL || `${window.location.protocol}//${window.location.hostname}:8080/api`;
    }
    // Server-side (SSR): use env var or fallback
    return process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';
};

export const API_BASE_URL = getApiBaseUrl();

// --- Page type (Spring Data Page response) ---
export interface PageResponse<T> {
    content: T[];
    totalElements: number;
    totalPages: number;
    size: number;
    number: number; // current page (0-based)
    first: boolean;
    last: boolean;
    empty: boolean;
}

// --- Types ---
export interface Supplier {
    id: number;
    name: string;
    contactPerson?: string;
    phone?: string;
    email?: string;
    active: boolean;
}

export interface OilType {
    id: number;
    name: string;
    description?: string;
    active: boolean;
}

export interface GradeType {
    id: number;
    oilType?: OilType;
    name: string;
    description?: string;
    active: boolean;
}

export interface Product {
    id: number;
    name: string;
    hsnCode: string;
    price: number;
    category: string;
    unit: string;
    volume?: number;
    brand?: string;
    active: boolean;
    supplier?: Supplier;
    oilType?: OilType;
    gradeType?: GradeType;
    fuelFamily?: string;
}

export interface Tank {
    id: number;
    name: string;
    capacity: number;
    availableStock: number;
    thresholdStock?: number;
    product: Product;
    active: boolean;
}

export interface Pump {
    id: number;
    name: string;
    active: boolean;
}

export interface Nozzle {
    id: number;
    nozzleName: string;
    nozzleNumber?: string;
    nozzleCompany?: string;
    stampingExpiryDate?: string;
    tank: Tank;
    pump: Pump;
    active: boolean;
}

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

export interface Vehicle {
    id: number;
    vehicleNumber: string;
    vehicleType?: { id: number; name: string };
    preferredProduct?: Product;
    maxCapacity?: number;
    maxLitersPerMonth?: number;
    consumedLiters?: number;
    status?: string;
    active: boolean;
    customer?: Customer;
}

export interface Customer {
    id: number;
    name: string;
    username?: string;
    address?: string;
    phoneNumbers?: string;
    active: boolean;
    statementGrouping?: string;
}

export interface User {
    id: number;
    name: string;
    username: string;
    role: {
        id: number;
        roleType: string;
    };
}

export interface InvoiceProduct {
    id?: number;
    product: Product;
    nozzle?: Nozzle;
    quantity: number;
    unitPrice: number;
    amount: number;
    grossAmount?: number;
    discountRate?: number;
    discountAmount?: number;
}

export interface InvoiceBill {
    id?: number;
    date: string;
    billDesc?: string;
    pumpBillPic?: string;
    billPic?: string;
    signatoryName?: string;
    signatoryCellNo?: string;
    vehicleKM?: number;
    readingOpen?: number;
    readingClose?: number;
    customerGST?: string;
    grossAmount?: number;
    totalDiscount?: number;
    netAmount: number;
    billNo?: string;
    billType: 'CASH' | 'CREDIT';
    paymentMode?: string;
    indentNo?: string;
    indentPic?: string;
    status: string;
    paymentStatus?: 'PAID' | 'NOT_PAID';
    statement?: Statement;
    customer?: Customer | any;
    vehicle?: Vehicle;
    driverName?: string;
    driverPhone?: string;
    raisedBy?: User;
    products: InvoiceProduct[];
}

export interface PaymentMode {
    id: number;
    modeName: string;
}

export interface Statement {
    id?: number;
    statementNo: string;
    customer: Customer;
    fromDate: string;
    toDate: string;
    statementDate: string;
    numberOfBills: number;
    totalAmount: number;
    roundingAmount: number;
    netAmount: number;
    receivedAmount: number;
    balanceAmount: number;
    status: 'PAID' | 'NOT_PAID';
    statementPdfUrl?: string;
}

export interface Payment {
    id?: number;
    paymentDate: string;
    amount: number;
    paymentMode: PaymentMode;
    referenceNo?: string;
    customer?: Customer;
    statement?: Statement;
    invoiceBill?: InvoiceBill;
    shiftId?: number;
    remarks?: string;
    proofImageKey?: string;
}

export interface LedgerEntry {
    date: string;
    type: 'DEBIT' | 'CREDIT';
    description: string;
    referenceId: number;
    debitAmount: number;
    creditAmount: number;
    runningBalance: number;
}

export interface CustomerLedger {
    customerId: number;
    fromDate: string;
    toDate: string;
    openingBalance: number;
    closingBalance: number;
    totalDebits: number;
    totalCredits: number;
    entries: LedgerEntry[];
}

// --- Fetch Functions ---

const handleResponse = async (res: Response) => {
    if (!res.ok) {
        const error = await res.text();
        throw new Error(error || 'Network response was not ok');
    }
    // Handle 204 No Content for DELETE
    if (res.status === 204 || res.headers.get('content-length') === '0') {
        return null;
    }
    return res.json();
};

// ... existing products functions ...

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


// Products
export const getActiveProducts = (): Promise<Product[]> =>
    fetchWithAuth(`${API_BASE_URL}/products/active`).then(handleResponse);

export const getActiveNonFuelProducts = (): Promise<Product[]> =>
    fetchWithAuth(`${API_BASE_URL}/products/active/non-fuel`).then(handleResponse);

export const getFuelProducts = (): Promise<Product[]> =>
    fetchWithAuth(`${API_BASE_URL}/products/category/Fuel`).then(handleResponse);

export const getProducts = (): Promise<Product[]> =>
    fetchWithAuth(`${API_BASE_URL}/products`).then(handleResponse);

export const createProduct = (product: Partial<Product>): Promise<Product> =>
    fetchWithAuth(`${API_BASE_URL}/products`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(product),
    }).then(handleResponse);

export const updateProduct = (id: number, product: Partial<Product>): Promise<Product> =>
    fetchWithAuth(`${API_BASE_URL}/products/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(product),
    }).then(handleResponse);

export const deleteProduct = (id: number): Promise<void> =>
    fetchWithAuth(`${API_BASE_URL}/products/${id}`, { method: 'DELETE' }).then(handleResponse);

// Tanks
export const getTanks = (): Promise<Tank[]> =>
    fetchWithAuth(`${API_BASE_URL}/tanks`).then(handleResponse);

export const createTank = (tank: Partial<Tank>): Promise<Tank> =>
    fetchWithAuth(`${API_BASE_URL}/tanks`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(tank),
    }).then(handleResponse);

export const updateTank = (id: number, tank: Partial<Tank>): Promise<Tank> =>
    fetchWithAuth(`${API_BASE_URL}/tanks/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(tank),
    }).then(handleResponse);

export const deleteTank = (id: number): Promise<void> =>
    fetchWithAuth(`${API_BASE_URL}/tanks/${id}`, { method: 'DELETE' }).then(handleResponse);

export const getLowStockTanks = (): Promise<Tank[]> =>
    fetchWithAuth(`${API_BASE_URL}/tanks/low-stock`).then(handleResponse);

// Stock Alerts
export interface StockAlert {
    id: number;
    tank: Tank;
    availableStock: number;
    thresholdStock: number;
    message: string;
    active: boolean;
    acknowledgedAt?: string;
    acknowledgedBy?: string;
    notifiedVia?: string;
    createdAt: string;
}

export const getActiveStockAlerts = (): Promise<StockAlert[]> =>
    fetchWithAuth(`${API_BASE_URL}/stock-alerts`).then(handleResponse);

export const checkStockAlerts = (): Promise<StockAlert[]> =>
    fetchWithAuth(`${API_BASE_URL}/stock-alerts/check`, { method: 'POST' }).then(handleResponse);

export const acknowledgeStockAlert = (id: number): Promise<StockAlert> =>
    fetchWithAuth(`${API_BASE_URL}/stock-alerts/${id}/acknowledge`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({}),
    }).then(handleResponse);

export const acknowledgeAllStockAlerts = (): Promise<void> =>
    fetchWithAuth(`${API_BASE_URL}/stock-alerts/acknowledge-all`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({}),
    }).then(handleResponse);

// Notification Config
export interface NotificationConfig {
    id?: number;
    alertType: string;
    enabled: boolean;
    notifyRoles: string[];
    channels: string[];
}

export interface RoleOption {
    id: number;
    roleType: string;
}

export const getNotificationConfigs = (): Promise<NotificationConfig[]> =>
    fetchWithAuth(`${API_BASE_URL}/notification-config`).then(handleResponse);

export const getNotificationConfigByType = (alertType: string): Promise<NotificationConfig> =>
    fetchWithAuth(`${API_BASE_URL}/notification-config/${alertType}`).then(handleResponse);

export const saveNotificationConfig = (config: NotificationConfig): Promise<NotificationConfig> =>
    fetchWithAuth(`${API_BASE_URL}/notification-config`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(config),
    }).then(handleResponse);

export const getAvailableRoles = (): Promise<RoleOption[]> =>
    fetchWithAuth(`${API_BASE_URL}/notification-config/roles`).then(handleResponse);

// Pumps
export const getPumps = (): Promise<Pump[]> =>
    fetchWithAuth(`${API_BASE_URL}/pumps`).then(handleResponse);

export const createPump = (pump: Partial<Pump>): Promise<Pump> =>
    fetchWithAuth(`${API_BASE_URL}/pumps`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(pump),
    }).then(handleResponse);

export const updatePump = (id: number, pump: Partial<Pump>): Promise<Pump> =>
    fetchWithAuth(`${API_BASE_URL}/pumps/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(pump),
    }).then(handleResponse);

export const deletePump = (id: number): Promise<void> =>
    fetchWithAuth(`${API_BASE_URL}/pumps/${id}`, { method: 'DELETE' }).then(handleResponse);

// Nozzles
export const getNozzles = (): Promise<Nozzle[]> =>
    fetchWithAuth(`${API_BASE_URL}/nozzles`).then(handleResponse);

export const createNozzle = (nozzle: Partial<Nozzle>): Promise<Nozzle> =>
    fetchWithAuth(`${API_BASE_URL}/nozzles`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(nozzle),
    }).then(handleResponse);

export const updateNozzle = (id: number, nozzle: Partial<Nozzle>): Promise<Nozzle> =>
    fetchWithAuth(`${API_BASE_URL}/nozzles/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(nozzle),
    }).then(handleResponse);

export const deleteNozzle = (id: number): Promise<void> =>
    fetchWithAuth(`${API_BASE_URL}/nozzles/${id}`, { method: 'DELETE' }).then(handleResponse);

// Suppliers
export const getSuppliers = (): Promise<Supplier[]> =>
    fetchWithAuth(`${API_BASE_URL}/suppliers`).then(handleResponse);

export const getActiveSuppliers = (): Promise<Supplier[]> =>
    fetchWithAuth(`${API_BASE_URL}/suppliers/active`).then(handleResponse);

export const createSupplier = (supplier: Partial<Supplier>): Promise<Supplier> =>
    fetchWithAuth(`${API_BASE_URL}/suppliers`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(supplier),
    }).then(handleResponse);

export const updateSupplier = (id: number, supplier: Partial<Supplier>): Promise<Supplier> =>
    fetchWithAuth(`${API_BASE_URL}/suppliers/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(supplier),
    }).then(handleResponse);

export const deleteSupplier = (id: number): Promise<void> =>
    fetchWithAuth(`${API_BASE_URL}/suppliers/${id}`, { method: 'DELETE' }).then(handleResponse);

export const toggleSupplierStatus = (id: number): Promise<Supplier> =>
    fetchWithAuth(`${API_BASE_URL}/suppliers/${id}/toggle-status`, { method: 'PATCH' }).then(handleResponse);

// Oil Types
export const getOilTypes = (): Promise<OilType[]> =>
    fetchWithAuth(`${API_BASE_URL}/oil-types`).then(handleResponse);

export const getActiveOilTypes = (): Promise<OilType[]> =>
    fetchWithAuth(`${API_BASE_URL}/oil-types/active`).then(handleResponse);

export const createOilType = (oilType: Partial<OilType>): Promise<OilType> =>
    fetchWithAuth(`${API_BASE_URL}/oil-types`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(oilType),
    }).then(handleResponse);

export const updateOilType = (id: number, oilType: Partial<OilType>): Promise<OilType> =>
    fetchWithAuth(`${API_BASE_URL}/oil-types/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(oilType),
    }).then(handleResponse);

export const deleteOilType = (id: number): Promise<void> =>
    fetchWithAuth(`${API_BASE_URL}/oil-types/${id}`, { method: 'DELETE' }).then(handleResponse);

export const toggleOilTypeStatus = (id: number): Promise<OilType> =>
    fetchWithAuth(`${API_BASE_URL}/oil-types/${id}/toggle-status`, { method: 'PATCH' }).then(handleResponse);

// Grade Types
export const getGradeTypes = (): Promise<GradeType[]> =>
    fetchWithAuth(`${API_BASE_URL}/grades`).then(handleResponse);

export const getActiveGradeTypes = (): Promise<GradeType[]> =>
    fetchWithAuth(`${API_BASE_URL}/grades/active`).then(handleResponse);

export const createGradeType = (grade: Partial<GradeType>): Promise<GradeType> =>
    fetchWithAuth(`${API_BASE_URL}/grades`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(grade),
    }).then(handleResponse);

export const updateGradeType = (id: number, grade: Partial<GradeType>): Promise<GradeType> =>
    fetchWithAuth(`${API_BASE_URL}/grades/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(grade),
    }).then(handleResponse);

export const deleteGradeType = (id: number): Promise<void> =>
    fetchWithAuth(`${API_BASE_URL}/grades/${id}`, { method: 'DELETE' }).then(handleResponse);

export const toggleGradeStatus = (id: number): Promise<GradeType> =>
    fetchWithAuth(`${API_BASE_URL}/grades/${id}/toggle-status`, { method: 'PATCH' }).then(handleResponse);

export const getGradesByOilType = (oilTypeId: number): Promise<GradeType[]> =>
    fetchWithAuth(`${API_BASE_URL}/grades/oil-type/${oilTypeId}`).then(handleResponse);

// Invoices
export const getInvoices = (): Promise<InvoiceBill[]> =>
    fetchWithAuth(`${API_BASE_URL}/invoices`).then(handleResponse);

export const getInvoicesByShift = (shiftId: number): Promise<InvoiceBill[]> =>
    fetchWithAuth(`${API_BASE_URL}/invoices/shift/${shiftId}`).then(handleResponse);

export const createInvoice = (invoice: Partial<InvoiceBill>): Promise<InvoiceBill> =>
    fetchWithAuth(`${API_BASE_URL}/invoices`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(invoice),
    }).then(handleResponse);

export const updateInvoice = (id: number, invoice: Partial<InvoiceBill>): Promise<InvoiceBill> =>
    fetchWithAuth(`${API_BASE_URL}/invoices/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(invoice),
    }).then(handleResponse);

export const deleteInvoice = (id: number): Promise<void> =>
    fetchWithAuth(`${API_BASE_URL}/invoices/${id}`, { method: 'DELETE' }).then(handleResponse);

export const uploadInvoiceFile = (id: number, type: string, file: File): Promise<InvoiceBill> => {
    const formData = new FormData();
    formData.append('file', file);
    return fetchWithAuth(`${API_BASE_URL}/invoices/${id}/upload/${type}`, {
        method: 'POST',
        body: formData,
    }).then(handleResponse);
};

export const getInvoiceFileUrl = (id: number, type: string): Promise<string> =>
    fetchWithAuth(`${API_BASE_URL}/invoices/${id}/file-url?type=${type}`).then(handleResponse).then((data: { url: string }) => data.url);

// Customers & Vehicles Search
export const getCustomers = (search?: string): Promise<any> => {
    const url = search ? `${API_BASE_URL}/customers?search=${encodeURIComponent(search)}` : `${API_BASE_URL}/customers`;
    return fetchWithAuth(url).then(handleResponse);
};

export const getVehicles = (search?: string): Promise<Vehicle[]> => {
    const url = search ? `${API_BASE_URL}/vehicles?search=${encodeURIComponent(search)}` : `${API_BASE_URL}/vehicles`;
    return fetchWithAuth(url).then(handleResponse);
};

export const getVehiclesByCustomer = (customerId: number): Promise<Vehicle[]> =>
    fetchWithAuth(`${API_BASE_URL}/vehicles/customer/${customerId}`).then(handleResponse);

export const searchVehicles = (query: string): Promise<Vehicle[]> =>
    fetchWithAuth(`${API_BASE_URL}/vehicles/search?q=${encodeURIComponent(query)}`).then(handleResponse);

// Payment Modes
export const getPaymentModes = (): Promise<PaymentMode[]> =>
    fetchWithAuth(`${API_BASE_URL}/payment-modes`).then(handleResponse);

// Statements
export const getStatements = (
    page = 0, size = 10, customerId?: number, status?: string,
    fromDate?: string, toDate?: string, search?: string, categoryType?: string
): Promise<PageResponse<Statement>> => {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (customerId) params.append('customerId', String(customerId));
    if (status) params.append('status', status);
    if (fromDate) params.append('fromDate', fromDate);
    if (toDate) params.append('toDate', toDate);
    if (search) params.append('search', search);
    if (categoryType) params.append('categoryType', categoryType);
    return fetchWithAuth(`${API_BASE_URL}/statements?${params}`).then(handleResponse);
};

export const getStatementById = (id: number): Promise<Statement> =>
    fetchWithAuth(`${API_BASE_URL}/statements/${id}`).then(handleResponse);

export const getStatementsByCustomer = (customerId: number): Promise<Statement[]> =>
    fetchWithAuth(`${API_BASE_URL}/statements/customer/${customerId}`).then(handleResponse);

export const getOutstandingStatements = (): Promise<Statement[]> =>
    fetchWithAuth(`${API_BASE_URL}/statements/outstanding`).then(handleResponse);

export const getOutstandingByCustomer = (customerId: number): Promise<Statement[]> =>
    fetchWithAuth(`${API_BASE_URL}/statements/outstanding/customer/${customerId}`).then(handleResponse);

export const generateStatement = (
    customerId: number, fromDate: string, toDate: string,
    filters?: { vehicleId?: number; productId?: number; billIds?: number[] }
): Promise<Statement> => {
    const params = new URLSearchParams({
        customerId: String(customerId),
        fromDate,
        toDate,
    });
    if (filters?.vehicleId) params.append('vehicleId', String(filters.vehicleId));
    if (filters?.productId) params.append('productId', String(filters.productId));
    if (filters?.billIds?.length) {
        filters.billIds.forEach(id => params.append('billIds', String(id)));
    }
    return fetchWithAuth(`${API_BASE_URL}/statements/generate?${params}`, {
        method: 'POST',
    }).then(handleResponse);
};

export const previewStatementBills = (
    customerId: number, fromDate: string, toDate: string,
    filters?: { vehicleId?: number; productId?: number }
): Promise<InvoiceBill[]> => {
    const params = new URLSearchParams({
        customerId: String(customerId),
        fromDate,
        toDate,
    });
    if (filters?.vehicleId) params.append('vehicleId', String(filters.vehicleId));
    if (filters?.productId) params.append('productId', String(filters.productId));
    return fetchWithAuth(`${API_BASE_URL}/statements/preview?${params}`).then(handleResponse);
};

export const getStatementBills = (statementId: number): Promise<InvoiceBill[]> =>
    fetchWithAuth(`${API_BASE_URL}/statements/${statementId}/bills`).then(handleResponse);

export const removeBillFromStatement = (statementId: number, billId: number): Promise<Statement> =>
    fetchWithAuth(`${API_BASE_URL}/statements/${statementId}/bills/${billId}`, {
        method: 'DELETE',
    }).then(handleResponse);

export const deleteStatement = (id: number): Promise<void> =>
    fetchWithAuth(`${API_BASE_URL}/statements/${id}`, { method: 'DELETE' }).then(handleResponse);

export const generateStatementPdf = (id: number): Promise<Statement> =>
    fetchWithAuth(`${API_BASE_URL}/statements/${id}/generate-pdf`, { method: 'POST' }).then(handleResponse);

export const getStatementPdfUrl = (id: number): Promise<string> =>
    fetchWithAuth(`${API_BASE_URL}/statements/${id}/pdf-url`).then(handleResponse).then((data: { url: string }) => data.url);

export interface StatementStats {
    statementsLastMonth: number;
    paidLastMonth: number;
    amountGeneratedLastMonth: number;
    amountCollectedLastMonth: number;
    totalStatements: number;
    totalPaid: number;
    paidPercentage: number;
    totalUnpaidAmount: number;
    totalNetAmount: number;
    totalReceivedAmount: number;
    collectionRate: number;
    avgStatementAmount: number;
}

export const getStatementStats = (): Promise<StatementStats> =>
    fetchWithAuth(`${API_BASE_URL}/statements/stats`).then(handleResponse);

// Payments
export const getPayments = (page = 0, size = 10, categoryType?: string): Promise<PageResponse<Payment>> => {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (categoryType) params.append('categoryType', categoryType);
    return fetchWithAuth(`${API_BASE_URL}/payments?${params}`).then(handleResponse);
};

export const getPaymentsByCustomer = (customerId: number, page = 0, size = 10): Promise<PageResponse<Payment>> =>
    fetchWithAuth(`${API_BASE_URL}/payments/customer/${customerId}?page=${page}&size=${size}`).then(handleResponse);

export const getPaymentsByStatement = (statementId: number): Promise<Payment[]> =>
    fetchWithAuth(`${API_BASE_URL}/payments/statement/${statementId}`).then(handleResponse);

export const getPaymentsByBill = (invoiceBillId: number): Promise<Payment[]> =>
    fetchWithAuth(`${API_BASE_URL}/payments/bill/${invoiceBillId}`).then(handleResponse);

export const getPaymentsByShift = (shiftId: number): Promise<Payment[]> =>
    fetchWithAuth(`${API_BASE_URL}/payments/shift/${shiftId}`).then(handleResponse);

export const recordStatementPayment = (statementId: number, payment: Partial<Payment>): Promise<Payment> =>
    fetchWithAuth(`${API_BASE_URL}/payments/statement/${statementId}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payment),
    }).then(handleResponse);

export const recordBillPayment = (invoiceBillId: number, payment: Partial<Payment>): Promise<Payment> =>
    fetchWithAuth(`${API_BASE_URL}/payments/bill/${invoiceBillId}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payment),
    }).then(handleResponse);

export const uploadPaymentProof = (paymentId: number, file: File): Promise<Payment> => {
    const formData = new FormData();
    formData.append('file', file);
    return fetchWithAuth(`${API_BASE_URL}/payments/${paymentId}/upload-proof`, {
        method: 'POST',
        body: formData,
    }).then(handleResponse);
};

export const getPaymentProofUrl = (paymentId: number): Promise<{ url: string }> =>
    fetchWithAuth(`${API_BASE_URL}/payments/${paymentId}/proof-url`).then(handleResponse);

// Ledger
export const getOpeningBalance = (customerId: number, asOfDate: string): Promise<number> =>
    fetchWithAuth(`${API_BASE_URL}/ledger/opening-balance?customerId=${customerId}&asOfDate=${asOfDate}`).then(handleResponse);

export const getCustomerLedger = (customerId: number, fromDate: string, toDate: string): Promise<CustomerLedger> =>
    fetchWithAuth(`${API_BASE_URL}/ledger/customer/${customerId}?fromDate=${fromDate}&toDate=${toDate}`).then(handleResponse);

export const getOutstandingBills = (customerId: number): Promise<InvoiceBill[]> =>
    fetchWithAuth(`${API_BASE_URL}/ledger/outstanding/${customerId}`).then(handleResponse);

// Customer Categories
export interface CustomerCategoryType {
    id: number;
    categoryName: string;
    categoryType: string;
    description?: string;
}

export const getCustomerCategories = (): Promise<CustomerCategoryType[]> =>
    fetchWithAuth(`${API_BASE_URL}/customer-categories`).then(handleResponse);

export const createCustomerCategory = (category: Partial<CustomerCategoryType>): Promise<CustomerCategoryType> =>
    fetchWithAuth(`${API_BASE_URL}/customer-categories`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(category),
    }).then(handleResponse);

export const updateCustomerCategory = (id: number, category: Partial<CustomerCategoryType>): Promise<CustomerCategoryType> =>
    fetchWithAuth(`${API_BASE_URL}/customer-categories/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(category),
    }).then(handleResponse);

export const deleteCustomerCategory = (id: number): Promise<void> =>
    fetchWithAuth(`${API_BASE_URL}/customer-categories/${id}`, { method: 'DELETE' }).then(handleResponse);

// Credit Management
export interface CreditCustomerSummary {
    customerId: number;
    customerName: string;
    phoneNumbers: string[] | null;
    groupName: string | null;
    categoryType: string | null;
    categoryName: string | null;
    status: string | null;
    creditLimitAmount: number | null;
    ledgerBalance: number;
    totalBilled: number;
    totalPaid: number;
    totalOutstanding: number;
    aging0to30: number;
    aging31to60: number;
    aging61to90: number;
    aging90Plus: number;
    pendingBillCount: number;
    totalBillCount: number;
    totalPaymentCount: number;
    pendingStatementCount: number;
}

export interface CreditOverview {
    totalOutstanding: number;
    totalAging0to30: number;
    totalAging31to60: number;
    totalAging61to90: number;
    totalAging90Plus: number;
    totalCreditCustomers: number;
    totalCustomers: number;
    govtOutstanding: number;
    nonGovtOutstanding: number;
    customers: CreditCustomerSummary[];
}

export interface CreditCustomerDetail {
    unpaidBills: InvoiceBill[];
    paidBills: InvoiceBill[];
    statements: Statement[];
    payments: Payment[];
}

export const getCreditOverview = (categoryType?: string): Promise<CreditOverview> => {
    const params = new URLSearchParams();
    if (categoryType) params.append('categoryType', categoryType);
    const qs = params.toString();
    return fetchWithAuth(`${API_BASE_URL}/credit/overview${qs ? `?${qs}` : ''}`).then(handleResponse);
};

export const getCreditCustomerDetail = (customerId: number): Promise<CreditCustomerDetail> =>
    fetchWithAuth(`${API_BASE_URL}/credit/customer/${customerId}`).then(handleResponse);

// Update Customer Credit Limits (for "Set as Limit" workflow)
export const updateCustomerCreditLimits = (id: number, limits: { creditLimitAmount?: number | null; creditLimitLiters?: number | null }): Promise<any> =>
    fetchWithAuth(`${API_BASE_URL}/customers/${id}/credit-limits`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(limits)
    }).then(handleResponse);

// Update Vehicle Liter Limit (for "Set as Limit" workflow)
export const updateVehicleLiterLimit = (id: number, maxLitersPerMonth: number | null): Promise<Vehicle> =>
    fetchWithAuth(`${API_BASE_URL}/vehicles/${id}/liter-limit`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ maxLitersPerMonth })
    }).then(handleResponse);

// Customer Credit Info (for invoice-time validation)
export const getCustomerCreditInfo = (id: number): Promise<{
    creditLimitAmount: number | null;
    creditLimitLiters: number | null;
    consumedLiters: number | null;
    ledgerBalance: number;
    totalBilled: number;
    totalPaid: number;
}> => fetchWithAuth(`${API_BASE_URL}/customers/${id}/credit-info`).then(handleResponse);

// Block / Unblock Customer
export const blockCustomer = (id: number): Promise<any> =>
    fetchWithAuth(`${API_BASE_URL}/customers/${id}/block`, { method: 'PATCH' }).then(handleResponse);

export const unblockCustomer = (id: number): Promise<any> =>
    fetchWithAuth(`${API_BASE_URL}/customers/${id}/unblock`, { method: 'PATCH' }).then(handleResponse);

// Paginated Customer Invoices
export const getCustomerInvoices = (
    customerId: number,
    page = 0,
    size = 20,
    filters?: { billType?: string; paymentStatus?: string; fromDate?: string; toDate?: string }
): Promise<PageResponse<InvoiceBill>> => {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (filters?.billType) params.append('billType', filters.billType);
    if (filters?.paymentStatus) params.append('paymentStatus', filters.paymentStatus);
    if (filters?.fromDate) params.append('fromDate', filters.fromDate);
    if (filters?.toDate) params.append('toDate', filters.toDate);
    return fetchWithAuth(`${API_BASE_URL}/invoices/customer/${customerId}?${params}`).then(handleResponse);
};

// Product Sales Summary
export interface ProductSalesSummary {
    productId: number;
    productName: string;
    totalQuantity: number;
    totalAmount: number;
    totalGrossAmount: number;
    totalDiscount: number;
}

// Invoice History (paginated + filtered)
export const getInvoiceHistory = (
    page = 0,
    size = 20,
    filters?: { billType?: string; paymentStatus?: string; fromDate?: string; toDate?: string; search?: string; categoryType?: string }
): Promise<PageResponse<InvoiceBill>> => {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (filters?.billType) params.append('billType', filters.billType);
    if (filters?.paymentStatus) params.append('paymentStatus', filters.paymentStatus);
    if (filters?.fromDate) params.append('fromDate', filters.fromDate);
    if (filters?.toDate) params.append('toDate', filters.toDate);
    if (filters?.search) params.append('search', filters.search);
    if (filters?.categoryType) params.append('categoryType', filters.categoryType);
    return fetchWithAuth(`${API_BASE_URL}/invoices/history?${params}`).then(handleResponse);
};

// Product Sales Summary for history filters
export const getProductSalesSummary = (
    filters?: { billType?: string; paymentStatus?: string; fromDate?: string; toDate?: string; categoryType?: string }
): Promise<ProductSalesSummary[]> => {
    const params = new URLSearchParams();
    if (filters?.billType) params.append('billType', filters.billType);
    if (filters?.paymentStatus) params.append('paymentStatus', filters.paymentStatus);
    if (filters?.fromDate) params.append('fromDate', filters.fromDate);
    if (filters?.toDate) params.append('toDate', filters.toDate);
    if (filters?.categoryType) params.append('categoryType', filters.categoryType);
    return fetchWithAuth(`${API_BASE_URL}/invoices/history/product-summary?${params}`).then(handleResponse);
};

// Incentives
export interface Incentive {
    id?: number;
    customer: { id: number } | Customer;
    product: { id: number } | Product;
    minQuantity?: number;
    discountRate: number;
    active: boolean;
}

export const getAllIncentives = (): Promise<Incentive[]> =>
    fetchWithAuth(`${API_BASE_URL}/incentives`).then(handleResponse);

export const getIncentivesByCustomer = (customerId: number): Promise<Incentive[]> =>
    fetchWithAuth(`${API_BASE_URL}/incentives/customer/${customerId}`).then(handleResponse);

export const createIncentive = (incentive: Partial<Incentive>): Promise<Incentive> =>
    fetchWithAuth(`${API_BASE_URL}/incentives`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(incentive),
    }).then(handleResponse);

export const updateIncentive = (id: number, incentive: Partial<Incentive>): Promise<Incentive> =>
    fetchWithAuth(`${API_BASE_URL}/incentives/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(incentive),
    }).then(handleResponse);

export const deleteIncentive = (id: number): Promise<void> =>
    fetchWithAuth(`${API_BASE_URL}/incentives/${id}`, { method: 'DELETE' }).then(handleResponse);

// Shifts
export interface Shift {
    id: number;
    startTime: string;
    endTime?: string;
    status: string; // OPEN, REVIEW, CLOSED
    attendant?: User;
    scid?: number;
}

export const getShifts = (): Promise<Shift[]> =>
    fetchWithAuth(`${API_BASE_URL}/shifts`).then(handleResponse);

export const getActiveShift = (): Promise<Shift | null> =>
    fetchWithAuth(`${API_BASE_URL}/shifts/active`).then(handleResponse);

export const openShift = (shift: Partial<Shift>): Promise<Shift> =>
    fetchWithAuth(`${API_BASE_URL}/shifts/open`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(shift),
    }).then(handleResponse);

export const closeShift = (id: number): Promise<Shift> =>
    fetchWithAuth(`${API_BASE_URL}/shifts/${id}/close`, {
        method: 'POST',
    }).then(handleResponse);

// EAdvance (electronic advance entries: Card, UPI, Cheque, CCMS, Bank Transfer)
export interface EAdvance {
    id?: number;
    shiftId?: number;
    transactionDate?: string;
    amount: number;
    advanceType: string; // CARD, UPI, CHEQUE, CCMS, BANK_TRANSFER
    remarks?: string;
    // UPI-specific
    upiCompany?: UpiCompany;
    // Card-specific
    batchId?: string;
    tid?: string;
    customerName?: string;
    customerPhone?: string;
    cardLast4Digit?: string;
    // Shared: Card, Cheque, Bank
    bankName?: string;
    // Cheque-specific
    chequeNo?: string;
    chequeDate?: string;
    inFavorOf?: string;
    // CCMS-specific
    ccmsNumber?: string;
    // Source references
    invoiceBill?: { id: number; billNo?: string; billType?: string; netAmount?: number; customer?: { id: number; name: string } | null } | null;
    payment?: { id: number; amount?: number; customer?: { id: number; name: string } | null } | null;
}

export interface UpiCompany {
    id: number;
    companyName: string;
}

export interface ExpenseType {
    id: number;
    typeName: string;
}

// Expense (shift-level expenses)
export interface ShiftExpense {
    id?: number;
    shiftId?: number;
    expenseDate?: string;
    amount: number;
    description?: string;
    remarks?: string;
    expenseType?: ExpenseType;
}

export interface EAdvanceSummary {
    card: number;
    upi: number;
    cheque: number;
    ccms: number;
    bank_transfer: number;
    total: number;
}

export const getEAdvancesByShift = (shiftId: number): Promise<EAdvance[]> =>
    fetchWithAuth(`${API_BASE_URL}/e-advances/shift/${shiftId}`).then(handleResponse);

export const getEAdvanceSummary = (shiftId: number): Promise<EAdvanceSummary> =>
    fetchWithAuth(`${API_BASE_URL}/e-advances/shift/${shiftId}/summary`).then(handleResponse);

export const createEAdvance = (eAdvance: Partial<EAdvance>): Promise<EAdvance> =>
    fetchWithAuth(`${API_BASE_URL}/e-advances`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(eAdvance),
    }).then(handleResponse);

export const deleteEAdvance = (id: number): Promise<void> =>
    fetchWithAuth(`${API_BASE_URL}/e-advances/${id}`, { method: 'DELETE' }).then(handleResponse);

export const getExpensesByShift = (shiftId: number): Promise<ShiftExpense[]> =>
    fetchWithAuth(`${API_BASE_URL}/expenses/shift/${shiftId}`).then(handleResponse);

export const getExpenseShiftTotal = (shiftId: number): Promise<number> =>
    fetchWithAuth(`${API_BASE_URL}/expenses/shift/${shiftId}/total`).then(handleResponse);

export const createExpense = (expense: Partial<ShiftExpense>): Promise<ShiftExpense> =>
    fetchWithAuth(`${API_BASE_URL}/expenses`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(expense),
    }).then(handleResponse);

export const deleteExpense = (id: number): Promise<void> =>
    fetchWithAuth(`${API_BASE_URL}/expenses/${id}`, { method: 'DELETE' }).then(handleResponse);

// Incentive Payments
export interface IncentivePayment {
    id?: number;
    shiftId?: number;
    paymentDate?: string;
    amount: number;
    description?: string;
    customer?: { id: number; name?: string } | null;
    invoiceBill?: { id: number; billNo?: string; billType?: string; netAmount?: number } | null;
    statement?: { id: number; statementNo?: string } | null;
}

export const getIncentivePayments = (): Promise<IncentivePayment[]> =>
    fetchWithAuth(`${API_BASE_URL}/incentive-payments`).then(handleResponse);

export const getIncentivePaymentsByShift = (shiftId: number): Promise<IncentivePayment[]> =>
    fetchWithAuth(`${API_BASE_URL}/incentive-payments/shift/${shiftId}`).then(handleResponse);

export const getIncentivePaymentsByCustomer = (customerId: number): Promise<IncentivePayment[]> =>
    fetchWithAuth(`${API_BASE_URL}/incentive-payments/customer/${customerId}`).then(handleResponse);

export const createIncentivePayment = (payment: Partial<IncentivePayment>): Promise<IncentivePayment> =>
    fetchWithAuth(`${API_BASE_URL}/incentive-payments`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payment),
    }).then(handleResponse);

export const deleteIncentivePayment = (id: number): Promise<void> =>
    fetchWithAuth(`${API_BASE_URL}/incentive-payments/${id}`, { method: 'DELETE' }).then(handleResponse);

export const getUpiCompanies = (): Promise<UpiCompany[]> =>
    fetchWithAuth(`${API_BASE_URL}/upi-companies`).then(handleResponse);

export const createUpiCompany = (company: Partial<UpiCompany>): Promise<UpiCompany> =>
    fetchWithAuth(`${API_BASE_URL}/upi-companies`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(company),
    }).then(handleResponse);

export const getExpenseTypes = (): Promise<ExpenseType[]> =>
    fetchWithAuth(`${API_BASE_URL}/expense-types`).then(handleResponse);

export const createExpenseType = (type: Partial<ExpenseType>): Promise<ExpenseType> =>
    fetchWithAuth(`${API_BASE_URL}/expense-types`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(type),
    }).then(handleResponse);

// Dashboard
export interface DashboardDailyRevenue {
    date: string;
    revenue: number;
    invoiceCount: number;
    fuelVolume: number;
}

export interface DashboardProductSales {
    productName: string;
    quantity: number;
    amount: number;
}

export interface DashboardTankStatus {
    tankId: number;
    tankName: string;
    productName: string | null;
    capacity: number;
    currentStock: number;
    active: boolean;
    lastReadingDate: string | null;
}

export interface DashboardStats {
    todayRevenue: number;
    todayFuelVolume: number;
    todayInvoiceCount: number;
    todayCashInvoices: number;
    todayCreditInvoices: number;
    activeShiftId: number | null;
    activeShiftStartTime: string | null;
    shiftCash: number | null;
    shiftUpi: number | null;
    shiftCard: number | null;
    shiftExpense: number | null;
    shiftTotal: number | null;
    shiftNet: number | null;
    totalTanks: number;
    activeTanks: number;
    totalPumps: number;
    activePumps: number;
    totalNozzles: number;
    activeNozzles: number;
    totalOutstanding: number;
    totalCreditCustomers: number;
    creditAging0to30: number;
    creditAging31to60: number;
    creditAging61to90: number;
    creditAging90Plus: number;
    dailyRevenue: DashboardDailyRevenue[];
    productSales: DashboardProductSales[];
    tankStatuses: DashboardTankStatus[];
    recentInvoices: RecentInvoiceItem[];
}

export interface RecentInvoiceItem {
    id: number;
    date: string;
    customerName: string | null;
    billType: string;
    amount: number;
    paymentStatus: string;
}

export const getDashboardStats = (): Promise<DashboardStats> =>
    fetchWithAuth(`${API_BASE_URL}/dashboard/stats`).then(handleResponse);

// --- Invoice Analytics Dashboard ---
export interface InvoiceDailyTrend {
    date: string;
    totalCount: number;
    totalAmount: number;
    cashCount: number;
    cashAmount: number;
    creditCount: number;
    creditAmount: number;
}

export interface NameCountAmount {
    name: string;
    count: number;
    amount: number;
}

export interface ProductBreakdown {
    productName: string;
    quantity: number;
    amount: number;
}

export interface HourlyData {
    hour: number;
    count: number;
}

export interface InvoiceAnalytics {
    fromDate: string;
    toDate: string;
    totalInvoices: number;
    totalRevenue: number;
    avgInvoiceValue: number;
    cashCount: number;
    cashAmount: number;
    creditCount: number;
    creditAmount: number;
    paidCount: number;
    paidAmount: number;
    unpaidCount: number;
    unpaidAmount: number;
    dailyTrend: InvoiceDailyTrend[];
    paymentModeDistribution: NameCountAmount[];
    topCustomers: NameCountAmount[];
    productBreakdown: ProductBreakdown[];
    hourlyDistribution: HourlyData[];
}

export const getInvoiceAnalytics = (from?: string, to?: string): Promise<InvoiceAnalytics> => {
    const params = new URLSearchParams();
    if (from) params.append('from', from);
    if (to) params.append('to', to);
    const qs = params.toString();
    return fetchWithAuth(`${API_BASE_URL}/dashboard/invoice-analytics${qs ? `?${qs}` : ''}`).then(handleResponse);
};

// --- Payment Analytics Dashboard ---
export interface PaymentDailyTrend {
    date: string;
    count: number;
    amount: number;
}

export interface PaymentAnalytics {
    fromDate: string;
    toDate: string;
    totalCollected: number;
    totalPayments: number;
    avgPaymentAmount: number;
    totalOutstanding: number;
    creditCustomers: number;
    collectionRate: number;
    aging0to30: number;
    aging31to60: number;
    aging61to90: number;
    aging90Plus: number;
    dailyTrend: PaymentDailyTrend[];
    paymentModeBreakdown: NameCountAmount[];
    topCustomers: NameCountAmount[];
}

export const getPaymentAnalytics = (from?: string, to?: string): Promise<PaymentAnalytics> => {
    const params = new URLSearchParams();
    if (from) params.append('from', from);
    if (to) params.append('to', to);
    const qs = params.toString();
    return fetchWithAuth(`${API_BASE_URL}/dashboard/payment-analytics${qs ? `?${qs}` : ''}`).then(handleResponse);
};

// --- Godown & Cashier Stock Types ---
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

// ────────────────────────────────────────────────────────────
// Employee Management Types & APIs
// ────────────────────────────────────────────────────────────

export interface EmployeeType {
    id: number;
    name: string;
    designation: string;
    email: string;
    phone: string;
    salary: number;
    salaryDay?: number; // Day of month salary is due (1-31)
    joinDate: string;
    status: string;
    aadharNumber: string;
    additionalPhones: string;
    address: string;
    city: string;
    state: string;
    pincode: string;
    photoUrl: string;
    bankAccountNumber: string;
    bankName: string;
    bankIfsc: string;
    bankBranch: string;
    panNumber?: string;
    department?: string;
    employeeCode?: string;
    emergencyContact?: string;
    emergencyPhone?: string;
    bloodGroup?: string;
    dateOfBirth?: string;
    gender?: string;
    maritalStatus?: string;
    aadharDocUrl?: string;
    panDocUrl?: string;
}

export const getEmployees = (): Promise<EmployeeType[]> =>
    fetchWithAuth(`${API_BASE_URL}/employees?size=1000`).then(handleResponse).then(data => data.content ?? data);

export const getActiveEmployees = (): Promise<EmployeeType[]> =>
    fetchWithAuth(`${API_BASE_URL}/employees/active`).then(handleResponse);

export const uploadEmployeePhoto = (id: number, file: File): Promise<EmployeeType> => {
    const formData = new FormData();
    formData.append('file', file);
    return fetchWithAuth(`${API_BASE_URL}/employees/${id}/upload-photo`, {
        method: 'POST',
        body: formData,
    }).then(handleResponse);
};

export const uploadEmployeeAadharDoc = (id: number, file: File): Promise<EmployeeType> => {
    const formData = new FormData();
    formData.append('file', file);
    return fetchWithAuth(`${API_BASE_URL}/employees/${id}/upload-aadhar-doc`, {
        method: 'POST',
        body: formData,
    }).then(handleResponse);
};

export const uploadEmployeePanDoc = (id: number, file: File): Promise<EmployeeType> => {
    const formData = new FormData();
    formData.append('file', file);
    return fetchWithAuth(`${API_BASE_URL}/employees/${id}/upload-pan-doc`, {
        method: 'POST',
        body: formData,
    }).then(handleResponse);
};

export const getEmployeeFileUrl = (id: number, type: string): Promise<{ url: string }> =>
    fetchWithAuth(`${API_BASE_URL}/employees/${id}/file-url?type=${type}`).then(handleResponse);

// ────────────────────────────────────────────────────────────
// Leave Management Types & APIs
// ────────────────────────────────────────────────────────────

export interface LeaveType {
    id: number;
    typeName: string;
    maxDaysPerYear: number;
    carryForward: boolean;
    maxCarryForwardDays: number;
}

export interface LeaveBalance {
    id: number;
    employee: EmployeeType;
    leaveType: LeaveType;
    year: number;
    totalAllotted: number;
    used: number;
    remaining: number;
}

export interface LeaveRequest {
    id: number;
    employee: EmployeeType;
    leaveType: LeaveType;
    fromDate: string;
    toDate: string;
    numberOfDays: number;
    reason: string;
    status: string; // PENDING, APPROVED, REJECTED
    approvedBy?: string;
    remarks?: string;
    createdAt?: string;
}

export const getLeaveTypes = (): Promise<LeaveType[]> =>
    fetchWithAuth(`${API_BASE_URL}/leave-types`).then(handleResponse);

export const createLeaveType = (lt: Partial<LeaveType>): Promise<LeaveType> =>
    fetchWithAuth(`${API_BASE_URL}/leave-types`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(lt),
    }).then(handleResponse);

export const updateLeaveType = (id: number, lt: Partial<LeaveType>): Promise<LeaveType> =>
    fetchWithAuth(`${API_BASE_URL}/leave-types/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(lt),
    }).then(handleResponse);

export const deleteLeaveType = (id: number): Promise<void> =>
    fetchWithAuth(`${API_BASE_URL}/leave-types/${id}`, { method: 'DELETE' }).then(handleResponse);

export const getEmployeeLeaveBalances = (employeeId: number, year?: number): Promise<LeaveBalance[]> => {
    const params = year ? `?year=${year}` : '';
    return fetchWithAuth(`${API_BASE_URL}/employees/${employeeId}/leave-balances${params}`).then(handleResponse);
};

export const initializeLeaveBalances = (employeeId: number, year?: number): Promise<LeaveBalance[]> => {
    const params = year ? `?year=${year}` : '';
    return fetchWithAuth(`${API_BASE_URL}/employees/${employeeId}/leave-balances/initialize${params}`, {
        method: 'POST',
    }).then(handleResponse);
};

export const getLeaveRequests = (status?: string): Promise<LeaveRequest[]> => {
    const params = status ? `?status=${status}` : '';
    return fetchWithAuth(`${API_BASE_URL}/leave-requests${params}`).then(handleResponse);
};

export const createLeaveRequest = (employeeId: number, request: Partial<LeaveRequest>): Promise<LeaveRequest> =>
    fetchWithAuth(`${API_BASE_URL}/employees/${employeeId}/leave-requests`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(request),
    }).then(handleResponse);

export const approveLeaveRequest = (id: number, body?: { approvedBy?: string; remarks?: string }): Promise<LeaveRequest> =>
    fetchWithAuth(`${API_BASE_URL}/leave-requests/${id}/approve`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body || {}),
    }).then(handleResponse);

export const rejectLeaveRequest = (id: number, body?: { remarks?: string }): Promise<LeaveRequest> =>
    fetchWithAuth(`${API_BASE_URL}/leave-requests/${id}/reject`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body || {}),
    }).then(handleResponse);

// ────────────────────────────────────────────────────────────
// Attendance Types & APIs
// ────────────────────────────────────────────────────────────

export interface Attendance {
    id: number;
    employee: EmployeeType;
    date: string;
    checkInTime?: string;
    checkOutTime?: string;
    totalHoursWorked?: number;
    shiftRefId?: number;
    status: string; // PRESENT, ABSENT, HALF_DAY, ON_LEAVE
    source: string; // MANUAL, GPS
    remarks?: string;
}

export const getDailyAttendance = (date: string): Promise<Attendance[]> =>
    fetchWithAuth(`${API_BASE_URL}/attendance/daily?date=${date}`).then(handleResponse);

export const getEmployeeAttendance = (employeeId: number, month: number, year: number): Promise<Attendance[]> =>
    fetchWithAuth(`${API_BASE_URL}/employees/${employeeId}/attendance?month=${month}&year=${year}`).then(handleResponse);

export const markAttendance = (employeeId: number, attendance: Partial<Attendance>): Promise<Attendance> =>
    fetchWithAuth(`${API_BASE_URL}/employees/${employeeId}/attendance`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(attendance),
    }).then(handleResponse);

export const markBulkAttendance = (attendances: Partial<Attendance>[]): Promise<Attendance[]> =>
    fetchWithAuth(`${API_BASE_URL}/attendance/bulk`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(attendances),
    }).then(handleResponse);

// ────────────────────────────────────────────────────────────
// Salary Payment Types & APIs
// ────────────────────────────────────────────────────────────

export interface SalaryPayment {
    id: number;
    employee: EmployeeType;
    month: number;
    year: number;
    baseSalary: number;
    advanceDeduction: number;
    lopDays: number;
    lopDeduction: number;
    incentiveAmount: number;
    otherDeductions: number;
    netPayable: number;
    paymentDate?: string;
    paymentMode?: string;
    status: string; // DRAFT, PAID
    remarks?: string;
}

export const getMonthlyPayments = (month: number, year: number): Promise<SalaryPayment[]> =>
    fetchWithAuth(`${API_BASE_URL}/salary?month=${month}&year=${year}`).then(handleResponse);

export const getEmployeeSalaryPayments = (employeeId: number): Promise<SalaryPayment[]> =>
    fetchWithAuth(`${API_BASE_URL}/salary/employee/${employeeId}`).then(handleResponse);

export const processMonthlyPayroll = (month: number, year: number): Promise<SalaryPayment[]> =>
    fetchWithAuth(`${API_BASE_URL}/salary/process?month=${month}&year=${year}`, {
        method: 'POST',
    }).then(handleResponse);

export const markSalaryAsPaid = (id: number, paymentMode?: string): Promise<SalaryPayment> =>
    fetchWithAuth(`${API_BASE_URL}/salary/${id}/pay`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ paymentMode: paymentMode || 'CASH' }),
    }).then(handleResponse);

export const updateSalaryPayment = (id: number, payment: Partial<SalaryPayment>): Promise<SalaryPayment> =>
    fetchWithAuth(`${API_BASE_URL}/salary/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payment),
    }).then(handleResponse);

// ────────────────────────────────────────────────────────────
// Utility Bill Types & APIs
// ────────────────────────────────────────────────────────────

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

// ────────────────────────────────────────────────────────────
// Station Expense Types & APIs
// ────────────────────────────────────────────────────────────

export interface StationExpense {
    id: number;
    expenseType?: ExpenseType;
    amount: number;
    expenseDate: string;
    description?: string;
    paidTo?: string;
    paymentMode?: string;
    recurringType: string; // ONE_TIME, MONTHLY, QUARTERLY, ANNUAL
}

export interface ExpenseSummary {
    totalAmount: number;
    count: number;
    byCategory: Record<string, number>;
    from: string;
    to: string;
}

export const getStationExpenses = (from?: string, to?: string): Promise<StationExpense[]> => {
    const params = new URLSearchParams();
    if (from) params.append('from', from);
    if (to) params.append('to', to);
    const qs = params.toString();
    return fetchWithAuth(`${API_BASE_URL}/station-expenses${qs ? `?${qs}` : ''}`).then(handleResponse);
};

export const createStationExpense = (expense: Partial<StationExpense>): Promise<StationExpense> =>
    fetchWithAuth(`${API_BASE_URL}/station-expenses`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(expense),
    }).then(handleResponse);

export const updateStationExpense = (id: number, expense: Partial<StationExpense>): Promise<StationExpense> =>
    fetchWithAuth(`${API_BASE_URL}/station-expenses/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(expense),
    }).then(handleResponse);

export const deleteStationExpense = (id: number): Promise<void> =>
    fetchWithAuth(`${API_BASE_URL}/station-expenses/${id}`, { method: 'DELETE' }).then(handleResponse);

export const getExpenseSummary = (from: string, to: string): Promise<ExpenseSummary> =>
    fetchWithAuth(`${API_BASE_URL}/station-expenses/summary?from=${from}&to=${to}`).then(handleResponse);

// --- Shift Closing Report Types ---
export interface ReportLineItem {
    id: number;
    section: string; // REVENUE, ADVANCE
    category: string;
    label: string;
    quantity?: number;
    rate?: number;
    amount: number;
    sourceEntityType?: string;
    sourceEntityId?: number;
    sortOrder: number;
    originalAmount?: number;
    transferredFromReportId?: number;
    transferredToReportId?: number;
}

export interface ReportCashBillBreakdown {
    id: number;
    productName: string;
    cashLitres: number;
    cardLitres: number;
    ccmsLitres: number;
    upiLitres: number;
    chequeLitres: number;
    totalLitres: number;
}

export interface ReportAuditLog {
    id: number;
    action: string;
    description: string;
    lineItemId?: number;
    previousValue?: number;
    newValue?: number;
    performedBy: string;
    performedAt: string;
}

export interface ShiftClosingReport {
    id: number;
    shift: Shift;
    reportDate: string;
    status: string; // DRAFT, FINALIZED
    finalizedBy?: string;
    finalizedAt?: string;
    reportPdfUrl?: string;
    totalRevenue: number;
    totalAdvances: number;
    balance: number;
    cashBillAmount: number;
    creditBillAmount: number;
    lineItems: ReportLineItem[];
    cashBillBreakdowns: ReportCashBillBreakdown[];
    auditLogs: ReportAuditLog[];
}

export interface ExternalCashInflow {
    id: number;
    amount: number;
    inflowDate: string;
    source: string;
    purpose?: string;
    remarks?: string;
    status: string;
    repaidAmount: number;
    shiftId?: number;
}

export interface CashInflowRepayment {
    id: number;
    cashInflow?: ExternalCashInflow;
    amount: number;
    repaymentDate: string;
    remarks?: string;
}

// --- Shift Closing Report API ---
export const generateShiftReport = (shiftId: number): Promise<ShiftClosingReport> =>
    fetchWithAuth(`${API_BASE_URL}/shift-reports/${shiftId}/generate`, { method: 'POST' }).then(handleResponse);

export const getShiftReport = (shiftId: number): Promise<ShiftClosingReport> =>
    fetchWithAuth(`${API_BASE_URL}/shift-reports/${shiftId}`).then(handleResponse);

export const getAllShiftReports = (status?: string): Promise<ShiftClosingReport[]> => {
    const qs = status ? `?status=${status}` : '';
    return fetchWithAuth(`${API_BASE_URL}/shift-reports${qs}`).then(handleResponse);
};

export const editReportLineItem = (reportId: number, lineItemId: number, amount: number, reason?: string): Promise<ShiftClosingReport> =>
    fetchWithAuth(`${API_BASE_URL}/shift-reports/${reportId}/line-items/${lineItemId}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ amount, reason }),
    }).then(handleResponse);

export const transferReportEntry = (reportId: number, lineItemId: number, targetReportId: number, reason?: string): Promise<ShiftClosingReport> =>
    fetchWithAuth(`${API_BASE_URL}/shift-reports/${reportId}/transfer`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ lineItemId, targetReportId, reason }),
    }).then(handleResponse);

export const finalizeShiftReport = (reportId: number, finalizedBy?: string): Promise<ShiftClosingReport> =>
    fetchWithAuth(`${API_BASE_URL}/shift-reports/${reportId}/finalize`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ finalizedBy }),
    }).then(handleResponse);

export const recomputeShiftReport = (reportId: number): Promise<ShiftClosingReport> =>
    fetchWithAuth(`${API_BASE_URL}/shift-reports/${reportId}/recompute`, { method: 'POST' }).then(handleResponse);

export const getReportAuditLog = (reportId: number): Promise<ReportAuditLog[]> =>
    fetchWithAuth(`${API_BASE_URL}/shift-reports/${reportId}/audit-log`).then(handleResponse);

// --- External Cash Inflow API ---
export const getExternalCashInflows = (): Promise<ExternalCashInflow[]> =>
    fetchWithAuth(`${API_BASE_URL}/cash-inflows`).then(handleResponse);

export const getExternalCashInflowsByShift = (shiftId: number): Promise<ExternalCashInflow[]> =>
    fetchWithAuth(`${API_BASE_URL}/cash-inflows/shift/${shiftId}`).then(handleResponse);

export const createExternalCashInflow = (inflow: Partial<ExternalCashInflow>): Promise<ExternalCashInflow> =>
    fetchWithAuth(`${API_BASE_URL}/cash-inflows`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(inflow),
    }).then(handleResponse);

// --- Print Data Types ---
export interface ShiftReportPrintData {
    companyName: string;
    employeeName: string;
    shiftId: number;
    shiftStart: string;
    shiftEnd: string;
    reportStatus: string;
    meterReadings: { pumpName: string; nozzleName: string; productName: string; openReading: number; closeReading: number; sales: number }[];
    tankReadings: { tankName: string; productName: string; openDip: string; openStock: number; incomeStock: number; totalStock: number; closeDip: string; closeStock: number; saleStock: number }[];
    salesDifferences: { productName: string; tankSale: number; meterSale: number; difference: number }[];
    cashBillDetails: { billNo: string; vehicleNo: string; driverName: string; products: string; paymentMode: string; amount: number }[];
    creditBillDetails: { customerName: string; billNo: string; vehicleNo: string; products: string; amount: number }[];
    stockSummary: { productName: string; openStock: number; receipt: number; totalStock: number; sales: number; rate: number; amount: number }[];
    stockPosition: { productName: string; godownStock: number; cashierStock: number; totalStock: number; lowStock: boolean }[];
    advanceEntries: { type: string; description: string; amount: number; reference?: string }[];
    paymentEntries: { type: string; customerName: string; reference: string; paymentMode: string; amount: number }[];
    paymentModeBreakdown: { mode: string; amount: number; billCount: number }[];
}

export const getShiftReportPrintData = (shiftId: number): Promise<ShiftReportPrintData> =>
    fetchWithAuth(`${API_BASE_URL}/shift-reports/${shiftId}/print-data`).then(handleResponse);

export const getShiftReportPdfUrl = (shiftId: number): Promise<string> =>
    fetchWithAuth(`${API_BASE_URL}/shift-reports/${shiftId}/pdf-url`).then(handleResponse).then((data: { url: string }) => data.url);

export const recordCashInflowRepayment = (inflowId: number, repayment: Partial<CashInflowRepayment>): Promise<CashInflowRepayment> =>
    fetchWithAuth(`${API_BASE_URL}/cash-inflows/${inflowId}/repay`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(repayment),
    }).then(handleResponse);

export const getCashInflowRepayments = (inflowId: number): Promise<CashInflowRepayment[]> =>
    fetchWithAuth(`${API_BASE_URL}/cash-inflows/${inflowId}/repayments`).then(handleResponse);

// --- Shift Closing Workspace ---

export interface NozzleReadingRow {
    nozzleId: number;
    nozzleName: string;
    pumpName?: string;
    productName?: string;
    productPrice?: number;
    openMeterReading: number;
}

export interface TankDipRow {
    tankId: number;
    tankName: string;
    productName?: string;
    capacity?: number;
    openDip?: string;
    openStock: number;
}

export interface ShiftClosingData {
    shiftId: number;
    shiftStatus: string;
    startTime: string;
    attendantName?: string;
    nozzleReadings: NozzleReadingRow[];
    tankDips: TankDipRow[];
    billPaymentTotal?: number;
    statementPaymentTotal?: number;
    externalInflowTotal?: number;
    creditBillTotal?: number;
    eAdvanceTotals?: Record<string, number>;
    opAdvanceTotals?: Record<string, number>;
    expenseTotal?: number;
    incentiveTotal?: number;
    inflowRepaymentTotal?: number;
}

export interface NozzleReadingInput {
    nozzleId: number;
    openMeterReading?: number;
    closeMeterReading: number;
    testQuantity?: number;
}

export interface TankDipInput {
    tankId: number;
    openDip?: string;
    openStock?: number;
    incomeStock?: number;
    closeDip: string;
    closeStock: number;
}

export interface ShiftClosingSubmit {
    nozzleReadings: NozzleReadingInput[];
    tankDips: TankDipInput[];
}

export const getShiftClosingData = (shiftId: number): Promise<ShiftClosingData> =>
    fetchWithAuth(`${API_BASE_URL}/shifts/${shiftId}/closing-data`).then(handleResponse);

export const submitShiftForReview = (shiftId: number, data: ShiftClosingSubmit): Promise<Shift> =>
    fetchWithAuth(`${API_BASE_URL}/shifts/${shiftId}/submit-for-review`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data),
    }).then(handleResponse);

export const approveShift = (shiftId: number): Promise<Shift> =>
    fetchWithAuth(`${API_BASE_URL}/shifts/${shiftId}/approve`, {
        method: 'POST',
    }).then(handleResponse);

export const reopenShift = (shiftId: number): Promise<Shift> =>
    fetchWithAuth(`${API_BASE_URL}/shifts/${shiftId}/reopen`, {
        method: 'POST',
    }).then(handleResponse);

export const downloadShiftReportPdf = async (shiftId: number): Promise<void> => {
    const res = await fetchWithAuth(`${API_BASE_URL}/shift-reports/${shiftId}/download-pdf`);
    if (!res.ok) throw new Error('Failed to download PDF');
    const blob = await res.blob();
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `shift-report-${shiftId}.pdf`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    window.URL.revokeObjectURL(url);
};
