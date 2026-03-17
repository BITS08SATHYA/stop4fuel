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

export interface GradeType {
    id: number;
    oilType?: string;
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
    gradeType?: GradeType;
}

export interface Tank {
    id: number;
    name: string;
    capacity: number;
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
}

export interface Vehicle {
    id: number;
    vehicleNumber: string; // Corrected from registrationNumber based on entity
    model?: string;
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
export const getNozzleInventories = (): Promise<NozzleInventory[]> =>
    fetch(`${API_BASE_URL}/inventory/nozzles`).then(handleResponse);

export const createNozzleInventory = (inventory: Partial<NozzleInventory>): Promise<NozzleInventory> =>
    fetch(`${API_BASE_URL}/inventory/nozzles`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(inventory),
    }).then(handleResponse);

export const deleteNozzleInventory = (id: number): Promise<void> =>
    fetch(`${API_BASE_URL}/inventory/nozzles/${id}`, { method: 'DELETE' }).then(handleResponse);

// Daily Inventory - Tanks
export const getTankInventories = (): Promise<TankInventory[]> =>
    fetch(`${API_BASE_URL}/inventory/tanks`).then(handleResponse);

export const createTankInventory = (inventory: Partial<TankInventory>): Promise<TankInventory> =>
    fetch(`${API_BASE_URL}/inventory/tanks`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(inventory),
    }).then(handleResponse);

export const deleteTankInventory = (id: number): Promise<void> =>
    fetch(`${API_BASE_URL}/inventory/tanks/${id}`, { method: 'DELETE' }).then(handleResponse);

// Daily Inventory - Products
export const getProductInventories = (): Promise<ProductInventory[]> =>
    fetch(`${API_BASE_URL}/inventory/products`).then(handleResponse);

export const createProductInventory = (inventory: Partial<ProductInventory>): Promise<ProductInventory> =>
    fetch(`${API_BASE_URL}/inventory/products`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(inventory),
    }).then(handleResponse);
export const deleteProductInventory = (id: number): Promise<void> =>
    fetch(`${API_BASE_URL}/inventory/products/${id}`, { method: 'DELETE' }).then(handleResponse);

export const updateTankInventory = (id: number, inventory: Partial<TankInventory>): Promise<TankInventory> =>
    fetch(`${API_BASE_URL}/inventory/tanks/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(inventory),
    }).then(handleResponse);

export const updateNozzleInventory = (id: number, inventory: Partial<NozzleInventory>): Promise<NozzleInventory> =>
    fetch(`${API_BASE_URL}/inventory/nozzles/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(inventory),
    }).then(handleResponse);

export const updateProductInventory = (id: number, inventory: Partial<ProductInventory>): Promise<ProductInventory> =>
    fetch(`${API_BASE_URL}/inventory/products/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(inventory),
    }).then(handleResponse);


// Products
export const getActiveProducts = (): Promise<Product[]> =>
    fetch(`${API_BASE_URL}/products/active`).then(handleResponse);

export const getFuelProducts = (): Promise<Product[]> =>
    fetch(`${API_BASE_URL}/products/category/Fuel`).then(handleResponse);

export const getProducts = (): Promise<Product[]> =>
    fetch(`${API_BASE_URL}/products`).then(handleResponse);

export const createProduct = (product: Partial<Product>): Promise<Product> =>
    fetch(`${API_BASE_URL}/products`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(product),
    }).then(handleResponse);

export const updateProduct = (id: number, product: Partial<Product>): Promise<Product> =>
    fetch(`${API_BASE_URL}/products/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(product),
    }).then(handleResponse);

export const deleteProduct = (id: number): Promise<void> =>
    fetch(`${API_BASE_URL}/products/${id}`, { method: 'DELETE' }).then(handleResponse);

// Tanks
export const getTanks = (): Promise<Tank[]> =>
    fetch(`${API_BASE_URL}/tanks`).then(handleResponse);

export const createTank = (tank: Partial<Tank>): Promise<Tank> =>
    fetch(`${API_BASE_URL}/tanks`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(tank),
    }).then(handleResponse);

export const updateTank = (id: number, tank: Partial<Tank>): Promise<Tank> =>
    fetch(`${API_BASE_URL}/tanks/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(tank),
    }).then(handleResponse);

export const deleteTank = (id: number): Promise<void> =>
    fetch(`${API_BASE_URL}/tanks/${id}`, { method: 'DELETE' }).then(handleResponse);

// Pumps
export const getPumps = (): Promise<Pump[]> =>
    fetch(`${API_BASE_URL}/pumps`).then(handleResponse);

export const createPump = (pump: Partial<Pump>): Promise<Pump> =>
    fetch(`${API_BASE_URL}/pumps`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(pump),
    }).then(handleResponse);

export const updatePump = (id: number, pump: Partial<Pump>): Promise<Pump> =>
    fetch(`${API_BASE_URL}/pumps/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(pump),
    }).then(handleResponse);

export const deletePump = (id: number): Promise<void> =>
    fetch(`${API_BASE_URL}/pumps/${id}`, { method: 'DELETE' }).then(handleResponse);

// Nozzles
export const getNozzles = (): Promise<Nozzle[]> =>
    fetch(`${API_BASE_URL}/nozzles`).then(handleResponse);

export const createNozzle = (nozzle: Partial<Nozzle>): Promise<Nozzle> =>
    fetch(`${API_BASE_URL}/nozzles`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(nozzle),
    }).then(handleResponse);

export const updateNozzle = (id: number, nozzle: Partial<Nozzle>): Promise<Nozzle> =>
    fetch(`${API_BASE_URL}/nozzles/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(nozzle),
    }).then(handleResponse);

export const deleteNozzle = (id: number): Promise<void> =>
    fetch(`${API_BASE_URL}/nozzles/${id}`, { method: 'DELETE' }).then(handleResponse);

// Suppliers
export const getSuppliers = (): Promise<Supplier[]> =>
    fetch(`${API_BASE_URL}/suppliers`).then(handleResponse);

export const getActiveSuppliers = (): Promise<Supplier[]> =>
    fetch(`${API_BASE_URL}/suppliers/active`).then(handleResponse);

export const createSupplier = (supplier: Partial<Supplier>): Promise<Supplier> =>
    fetch(`${API_BASE_URL}/suppliers`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(supplier),
    }).then(handleResponse);

export const updateSupplier = (id: number, supplier: Partial<Supplier>): Promise<Supplier> =>
    fetch(`${API_BASE_URL}/suppliers/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(supplier),
    }).then(handleResponse);

export const deleteSupplier = (id: number): Promise<void> =>
    fetch(`${API_BASE_URL}/suppliers/${id}`, { method: 'DELETE' }).then(handleResponse);

export const toggleSupplierStatus = (id: number): Promise<Supplier> =>
    fetch(`${API_BASE_URL}/suppliers/${id}/toggle-status`, { method: 'PATCH' }).then(handleResponse);

// Grade Types
export const getGradeTypes = (): Promise<GradeType[]> =>
    fetch(`${API_BASE_URL}/grades`).then(handleResponse);

export const getActiveGradeTypes = (): Promise<GradeType[]> =>
    fetch(`${API_BASE_URL}/grades/active`).then(handleResponse);

export const createGradeType = (grade: Partial<GradeType>): Promise<GradeType> =>
    fetch(`${API_BASE_URL}/grades`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(grade),
    }).then(handleResponse);

export const updateGradeType = (id: number, grade: Partial<GradeType>): Promise<GradeType> =>
    fetch(`${API_BASE_URL}/grades/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(grade),
    }).then(handleResponse);

export const deleteGradeType = (id: number): Promise<void> =>
    fetch(`${API_BASE_URL}/grades/${id}`, { method: 'DELETE' }).then(handleResponse);

export const toggleGradeStatus = (id: number): Promise<GradeType> =>
    fetch(`${API_BASE_URL}/grades/${id}/toggle-status`, { method: 'PATCH' }).then(handleResponse);

// Invoices
export const getInvoices = (): Promise<InvoiceBill[]> =>
    fetch(`${API_BASE_URL}/invoices`).then(handleResponse);

export const getInvoicesByShift = (shiftId: number): Promise<InvoiceBill[]> =>
    fetch(`${API_BASE_URL}/invoices/shift/${shiftId}`).then(handleResponse);

export const createInvoice = (invoice: Partial<InvoiceBill>): Promise<InvoiceBill> =>
    fetch(`${API_BASE_URL}/invoices`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(invoice),
    }).then(handleResponse);

export const deleteInvoice = (id: number): Promise<void> =>
    fetch(`${API_BASE_URL}/invoices/${id}`, { method: 'DELETE' }).then(handleResponse);

// Customers & Vehicles Search
export const getCustomers = (search?: string): Promise<any> => {
    const url = search ? `${API_BASE_URL}/customers?search=${encodeURIComponent(search)}` : `${API_BASE_URL}/customers`;
    return fetch(url).then(handleResponse);
};

export const getVehicles = (search?: string): Promise<Vehicle[]> => {
    const url = search ? `${API_BASE_URL}/vehicles?search=${encodeURIComponent(search)}` : `${API_BASE_URL}/vehicles`;
    return fetch(url).then(handleResponse);
};

export const getVehiclesByCustomer = (customerId: number): Promise<Vehicle[]> =>
    fetch(`${API_BASE_URL}/vehicles/customer/${customerId}`).then(handleResponse);

// Payment Modes
export const getPaymentModes = (): Promise<PaymentMode[]> =>
    fetch(`${API_BASE_URL}/payment-modes`).then(handleResponse);

// Statements
export const getStatements = (
    page = 0, size = 10, customerId?: number, status?: string
): Promise<PageResponse<Statement>> => {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (customerId) params.append('customerId', String(customerId));
    if (status) params.append('status', status);
    return fetch(`${API_BASE_URL}/statements?${params}`).then(handleResponse);
};

export const getStatementById = (id: number): Promise<Statement> =>
    fetch(`${API_BASE_URL}/statements/${id}`).then(handleResponse);

export const getStatementsByCustomer = (customerId: number): Promise<Statement[]> =>
    fetch(`${API_BASE_URL}/statements/customer/${customerId}`).then(handleResponse);

export const getOutstandingStatements = (): Promise<Statement[]> =>
    fetch(`${API_BASE_URL}/statements/outstanding`).then(handleResponse);

export const getOutstandingByCustomer = (customerId: number): Promise<Statement[]> =>
    fetch(`${API_BASE_URL}/statements/outstanding/customer/${customerId}`).then(handleResponse);

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
    return fetch(`${API_BASE_URL}/statements/generate?${params}`, {
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
    return fetch(`${API_BASE_URL}/statements/preview?${params}`).then(handleResponse);
};

export const getStatementBills = (statementId: number): Promise<InvoiceBill[]> =>
    fetch(`${API_BASE_URL}/statements/${statementId}/bills`).then(handleResponse);

export const removeBillFromStatement = (statementId: number, billId: number): Promise<Statement> =>
    fetch(`${API_BASE_URL}/statements/${statementId}/bills/${billId}`, {
        method: 'DELETE',
    }).then(handleResponse);

export const deleteStatement = (id: number): Promise<void> =>
    fetch(`${API_BASE_URL}/statements/${id}`, { method: 'DELETE' }).then(handleResponse);

// Payments
export const getPayments = (page = 0, size = 10): Promise<PageResponse<Payment>> =>
    fetch(`${API_BASE_URL}/payments?page=${page}&size=${size}`).then(handleResponse);

export const getPaymentsByCustomer = (customerId: number, page = 0, size = 10): Promise<PageResponse<Payment>> =>
    fetch(`${API_BASE_URL}/payments/customer/${customerId}?page=${page}&size=${size}`).then(handleResponse);

export const getPaymentsByStatement = (statementId: number): Promise<Payment[]> =>
    fetch(`${API_BASE_URL}/payments/statement/${statementId}`).then(handleResponse);

export const getPaymentsByBill = (invoiceBillId: number): Promise<Payment[]> =>
    fetch(`${API_BASE_URL}/payments/bill/${invoiceBillId}`).then(handleResponse);

export const recordStatementPayment = (statementId: number, payment: Partial<Payment>): Promise<Payment> =>
    fetch(`${API_BASE_URL}/payments/statement/${statementId}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payment),
    }).then(handleResponse);

export const recordBillPayment = (invoiceBillId: number, payment: Partial<Payment>): Promise<Payment> =>
    fetch(`${API_BASE_URL}/payments/bill/${invoiceBillId}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payment),
    }).then(handleResponse);

// Ledger
export const getOpeningBalance = (customerId: number, asOfDate: string): Promise<number> =>
    fetch(`${API_BASE_URL}/ledger/opening-balance?customerId=${customerId}&asOfDate=${asOfDate}`).then(handleResponse);

export const getCustomerLedger = (customerId: number, fromDate: string, toDate: string): Promise<CustomerLedger> =>
    fetch(`${API_BASE_URL}/ledger/customer/${customerId}?fromDate=${fromDate}&toDate=${toDate}`).then(handleResponse);

export const getOutstandingBills = (customerId: number): Promise<InvoiceBill[]> =>
    fetch(`${API_BASE_URL}/ledger/outstanding/${customerId}`).then(handleResponse);

// Credit Management
export interface CreditCustomerSummary {
    customerId: number;
    customerName: string;
    phoneNumbers: string[] | null;
    groupName: string | null;
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
    customers: CreditCustomerSummary[];
}

export interface CreditCustomerDetail {
    unpaidBills: InvoiceBill[];
    paidBills: InvoiceBill[];
    statements: Statement[];
    payments: Payment[];
}

export const getCreditOverview = (): Promise<CreditOverview> =>
    fetch(`${API_BASE_URL}/credit/overview`).then(handleResponse);

export const getCreditCustomerDetail = (customerId: number): Promise<CreditCustomerDetail> =>
    fetch(`${API_BASE_URL}/credit/customer/${customerId}`).then(handleResponse);

// Block / Unblock Customer
export const blockCustomer = (id: number): Promise<any> =>
    fetch(`${API_BASE_URL}/customers/${id}/block`, { method: 'PATCH' }).then(handleResponse);

export const unblockCustomer = (id: number): Promise<any> =>
    fetch(`${API_BASE_URL}/customers/${id}/unblock`, { method: 'PATCH' }).then(handleResponse);

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
    return fetch(`${API_BASE_URL}/invoices/customer/${customerId}?${params}`).then(handleResponse);
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

export const getIncentivesByCustomer = (customerId: number): Promise<Incentive[]> =>
    fetch(`${API_BASE_URL}/incentives/customer/${customerId}`).then(handleResponse);

export const createIncentive = (incentive: Partial<Incentive>): Promise<Incentive> =>
    fetch(`${API_BASE_URL}/incentives`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(incentive),
    }).then(handleResponse);

export const updateIncentive = (id: number, incentive: Partial<Incentive>): Promise<Incentive> =>
    fetch(`${API_BASE_URL}/incentives/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(incentive),
    }).then(handleResponse);

export const deleteIncentive = (id: number): Promise<void> =>
    fetch(`${API_BASE_URL}/incentives/${id}`, { method: 'DELETE' }).then(handleResponse);

// Shifts
export interface Shift {
    id: number;
    startTime: string;
    endTime?: string;
    status: string; // OPEN, CLOSED, RECONCILED
    attendant?: User;
    scid?: number;
}

export const getShifts = (): Promise<Shift[]> =>
    fetch(`${API_BASE_URL}/shifts`).then(handleResponse);

export const getActiveShift = (): Promise<Shift | null> =>
    fetch(`${API_BASE_URL}/shifts/active`).then(handleResponse);

export const openShift = (shift: Partial<Shift>): Promise<Shift> =>
    fetch(`${API_BASE_URL}/shifts/open`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(shift),
    }).then(handleResponse);

export const closeShift = (id: number): Promise<Shift> =>
    fetch(`${API_BASE_URL}/shifts/${id}/close`, {
        method: 'POST',
    }).then(handleResponse);

// Shift Transactions
export interface ShiftTransaction {
    id?: number;
    txnType: string;
    shiftId?: number;
    transactionDate?: string;
    receivedAmount: number;
    remarks?: string;
    // UPI-specific
    upiCompany?: UpiCompany;
    // Card-specific
    batchId?: string;
    tid?: string;
    customerName?: string;
    customerPhone?: string;
    bankName?: string;
    cardLast4Digit?: string;
    // Cheque-specific
    inFavorOf?: string;
    chequeNo?: string;
    chequeDate?: string;
    // CCMS-specific
    ccmsNumber?: string;
    // Expense-specific
    expenseAmount?: number;
    expenseDescription?: string;
    expenseType?: ExpenseType;
}

export interface UpiCompany {
    id: number;
    companyName: string;
}

export interface ExpenseType {
    id: number;
    typeName: string;
}

export interface ShiftTransactionSummary {
    cash: number;
    upi: number;
    card: number;
    expense: number;
    total: number;
    net: number;
}

export const getShiftTransactions = (shiftId: number): Promise<ShiftTransaction[]> =>
    fetch(`${API_BASE_URL}/shift-transactions/shift/${shiftId}`).then(handleResponse);

export const getShiftTransactionSummary = (shiftId: number): Promise<ShiftTransactionSummary> =>
    fetch(`${API_BASE_URL}/shift-transactions/shift/${shiftId}/summary`).then(handleResponse);

export const createShiftTransaction = (transaction: Partial<ShiftTransaction>): Promise<ShiftTransaction> =>
    fetch(`${API_BASE_URL}/shift-transactions`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(transaction),
    }).then(handleResponse);

export const deleteShiftTransaction = (id: number): Promise<void> =>
    fetch(`${API_BASE_URL}/shift-transactions/${id}`, { method: 'DELETE' }).then(handleResponse);

export const getUpiCompanies = (): Promise<UpiCompany[]> =>
    fetch(`${API_BASE_URL}/upi-companies`).then(handleResponse);

export const createUpiCompany = (company: Partial<UpiCompany>): Promise<UpiCompany> =>
    fetch(`${API_BASE_URL}/upi-companies`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(company),
    }).then(handleResponse);

export const getExpenseTypes = (): Promise<ExpenseType[]> =>
    fetch(`${API_BASE_URL}/expense-types`).then(handleResponse);

export const createExpenseType = (type: Partial<ExpenseType>): Promise<ExpenseType> =>
    fetch(`${API_BASE_URL}/expense-types`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(type),
    }).then(handleResponse);

// Dashboard
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
    fetch(`${API_BASE_URL}/dashboard/stats`).then(handleResponse);

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
    fetch(`${API_BASE_URL}/godown`).then(handleResponse);

export const getGodownStockByProduct = (productId: number): Promise<GodownStock[]> =>
    fetch(`${API_BASE_URL}/godown?productId=${productId}`).then(handleResponse);

export const getGodownLowStock = (): Promise<GodownStock[]> =>
    fetch(`${API_BASE_URL}/godown/low-stock`).then(handleResponse);

export const createGodownStock = (stock: Partial<GodownStock>): Promise<GodownStock> =>
    fetch(`${API_BASE_URL}/godown`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(stock),
    }).then(handleResponse);

export const updateGodownStock = (id: number, stock: Partial<GodownStock>): Promise<GodownStock> =>
    fetch(`${API_BASE_URL}/godown/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(stock),
    }).then(handleResponse);

export const deleteGodownStock = (id: number): Promise<void> =>
    fetch(`${API_BASE_URL}/godown/${id}`, { method: 'DELETE' }).then(handleResponse);

// --- Cashier Stock ---
export const getCashierStocks = (): Promise<CashierStock[]> =>
    fetch(`${API_BASE_URL}/cashier-stock`).then(handleResponse);

export const getCashierStockByProduct = (productId: number): Promise<CashierStock[]> =>
    fetch(`${API_BASE_URL}/cashier-stock?productId=${productId}`).then(handleResponse);

export const createCashierStock = (stock: Partial<CashierStock>): Promise<CashierStock> =>
    fetch(`${API_BASE_URL}/cashier-stock`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(stock),
    }).then(handleResponse);

export const updateCashierStock = (id: number, stock: Partial<CashierStock>): Promise<CashierStock> =>
    fetch(`${API_BASE_URL}/cashier-stock/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(stock),
    }).then(handleResponse);

export const deleteCashierStock = (id: number): Promise<void> =>
    fetch(`${API_BASE_URL}/cashier-stock/${id}`, { method: 'DELETE' }).then(handleResponse);

// --- Stock Transfers ---
export const getStockTransfers = (productId?: number, from?: string, to?: string): Promise<StockTransfer[]> => {
    const params = new URLSearchParams();
    if (productId) params.append('productId', String(productId));
    if (from) params.append('from', from);
    if (to) params.append('to', to);
    const qs = params.toString();
    return fetch(`${API_BASE_URL}/stock-transfers${qs ? `?${qs}` : ''}`).then(handleResponse);
};

export const createStockTransfer = (transfer: Partial<StockTransfer>): Promise<StockTransfer> =>
    fetch(`${API_BASE_URL}/stock-transfers`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(transfer),
    }).then(handleResponse);

// --- Purchase Orders ---
export const getPurchaseOrders = (status?: string, supplierId?: number): Promise<PurchaseOrder[]> => {
    const params = new URLSearchParams();
    if (status) params.append('status', status);
    if (supplierId) params.append('supplierId', String(supplierId));
    const qs = params.toString();
    return fetch(`${API_BASE_URL}/purchase-orders${qs ? `?${qs}` : ''}`).then(handleResponse);
};

export const getPurchaseOrderById = (id: number): Promise<PurchaseOrder> =>
    fetch(`${API_BASE_URL}/purchase-orders/${id}`).then(handleResponse);

export const createPurchaseOrder = (order: Partial<PurchaseOrder>): Promise<PurchaseOrder> =>
    fetch(`${API_BASE_URL}/purchase-orders`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(order),
    }).then(handleResponse);

export const updatePurchaseOrder = (id: number, order: Partial<PurchaseOrder>): Promise<PurchaseOrder> =>
    fetch(`${API_BASE_URL}/purchase-orders/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(order),
    }).then(handleResponse);

export const receivePurchaseOrder = (id: number, items: ReceiveItemDTO[]): Promise<PurchaseOrder> =>
    fetch(`${API_BASE_URL}/purchase-orders/${id}/receive`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(items),
    }).then(handleResponse);

export const cancelPurchaseOrder = (id: number): Promise<PurchaseOrder> =>
    fetch(`${API_BASE_URL}/purchase-orders/${id}/cancel`, { method: 'PATCH' }).then(handleResponse);
