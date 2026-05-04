"use client";

import { useEffect, useState } from "react";
import { GlassCard } from "@/components/ui/glass-card";
import { StyledSelect } from "@/components/ui/styled-select";
import { useToast } from "@/components/ui/toast";
import {
    getInvoiceHistory,
    getInvoicesByShift,
    createInvoice,
    moveInvoice,
    getMovableShifts,
    getActiveProducts,
    getNozzles,
    getCustomers,
    getIncentivesByCustomer,
    searchVehicles,
    uploadInvoiceFile,
    getInvoiceFileUrl,
    Product,
    Nozzle,
    InvoiceBill,
    InvoiceProduct,
    Vehicle,
    Customer,
    Incentive,
    API_BASE_URL,
    getCustomerCreditInfo
} from "@/lib/api/station";
import { fetchWithAuth } from "@/lib/api/fetch-with-auth";
import { printInvoice } from "@/lib/invoice-print";
import { useAuth } from "@/lib/auth/auth-context";

interface PostableShift {
    id: number;
    status: string;
    startTime?: string;
    endTime?: string | null;
    attendantName?: string | null;
}

interface CustomerWithCredit extends Customer {
    creditLimitAmount?: number | null;
    creditLimitLiters?: number | null;
    consumedLiters?: number;
    ledgerBalance?: number;
    unbilledCredit?: number;
    status?: string;
    phoneNumbers?: string;
    forceUnblocked?: boolean;
    forceUnblockedBy?: string;
    forceUnblockedAt?: string;
    [key: string]: unknown;
}
import { FileUploadField } from "@/components/ui/file-upload-field";
import { BlockingGatePanel } from "@/components/customers/BlockingGatePanel";
import { ShiftPicker } from "@/components/ShiftPicker";
import {
    Receipt,
    Plus,
    History,
    CreditCard,
    Truck,
    Package,
    User,
    Check,
    ArrowLeft,
    ArrowRight,
    AlertTriangle,
    FileText,
    Search,
    Calendar,
    CheckCircle2,
    Trash2,
    ShieldAlert,
    Ban,
    Info,
    Move,
} from "lucide-react";
import Link from "next/link";
import { PermissionGate } from "@/components/permission-gate";

export default function InvoicesPage() {
    const toast = useToast();
    const { user } = useAuth();
    const isShiftPickerAllowed = user?.role === "OWNER" || user?.role === "ADMIN";
    const [invoices, setInvoices] = useState<InvoiceBill[]>([]);
    const [products, setProducts] = useState<Product[]>([]);
    const [nozzles, setNozzles] = useState<Nozzle[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [activeTab, setActiveTab] = useState<'create' | 'history'>('create');
    const [currentStep, setCurrentStep] = useState(1);
    const [error, setError] = useState("");

    // Shift-scoping
    const [activeShiftId, setActiveShiftId] = useState<number | null>(null);
    const [targetShiftId, setTargetShiftId] = useState<number | null>(null);

    // Admin: move existing invoice to a different shift
    const [moveInvoiceTarget, setMoveInvoiceTarget] = useState<InvoiceBill | null>(null);
    const [movableShifts, setMovableShifts] = useState<PostableShift[]>([]);
    const [moveTargetShiftId, setMoveTargetShiftId] = useState<number | null>(null);
    const [moveBillDate, setMoveBillDate] = useState<string>("");
    const [moveSubmitting, setMoveSubmitting] = useState(false);
    const [viewMode, setViewMode] = useState<"shift" | "dates">("shift");
    const [historyFromDate, setHistoryFromDate] = useState("");
    const [historyToDate, setHistoryToDate] = useState("");

    // Customer & Vehicle
    const [customerSearch, setCustomerSearch] = useState("");
    const [customerSuggestions, setCustomerSuggestions] = useState<CustomerWithCredit[]>([]);
    const [selectedCustomer, setSelectedCustomer] = useState<CustomerWithCredit | undefined>(undefined);
    const [customerVehicles, setCustomerVehicles] = useState<Vehicle[]>([]);
    const [selectedVehicle, setSelectedVehicle] = useState<Vehicle | undefined>(undefined);
    const [isSaving, setIsSaving] = useState(false);
    const [isWalkIn, setIsWalkIn] = useState(false);
    const [walkInCustomerName, setWalkInCustomerName] = useState("");
    const [walkInVehicleNo, setWalkInVehicleNo] = useState("");
    const [walkInGST, setWalkInGST] = useState("");

    // Vehicle search (cross-customer)
    const [vehicleSearchQuery, setVehicleSearchQuery] = useState("");
    const [vehicleSearchResults, setVehicleSearchResults] = useState<Vehicle[]>([]);
    const [vehicleSearchTimeout, setVehicleSearchTimeout] = useState<NodeJS.Timeout | null>(null);
    const [vehicleSearchError, setVehicleSearchError] = useState<string | null>(null);

    // Step-1 unified search: vehicle plate hits run alongside customer name/phone hits.
    // Lets cashiers find a bus operator by typing the bus number plate (the natural
    // identifier they get from the driver) — falls into the same code path as picking
    // the customer manually, just skipping Step 2.
    const [vehicleSuggestions, setVehicleSuggestions] = useState<Vehicle[]>([]);
    const [step1SearchTimeout, setStep1SearchTimeout] = useState<NodeJS.Timeout | null>(null);
    const [vehicleQuickPicked, setVehicleQuickPicked] = useState(false);

    // Incentives
    const [incentives, setIncentives] = useState<Incentive[]>([]);

    // Form State
    const [billType, setBillType] = useState<'CASH' | 'CREDIT'>('CASH');
    const [driverName, setDriverName] = useState("");
    const [driverPhone, setDriverPhone] = useState("");
    const [paymentMode, setPaymentMode] = useState("CASH");
    const [indentNo, setIndentNo] = useState("");
    const [vehicleKM, setVehicleKM] = useState("");
    const [selectedProducts, setSelectedProducts] = useState<any[]>([]);
    const [lastCreatedInvoice, setLastCreatedInvoice] = useState<InvoiceBill | null>(null);
    const [manualDiscount, setManualDiscount] = useState("");
    const [companyInfo, setCompanyInfo] = useState<{ name: string; address: string; phone: string; gstNo: string; site?: string } | null>(null);

    const openMoveInvoice = async (inv: InvoiceBill) => {
        setMoveInvoiceTarget(inv);
        setMoveTargetShiftId(null);
        setMoveBillDate("");
        try {
            const shifts = await getMovableShifts(20);
            setMovableShifts(shifts.map(s => ({
                id: s.id,
                status: s.status,
                startTime: s.startTime,
                endTime: s.endTime ?? null,
                attendantName: s.attendant?.name ?? null,
            })));
        } catch (e) {
            toast.error(e instanceof Error ? e.message : "Failed to load movable shifts");
            setMovableShifts([]);
        }
    };

    const closeMoveInvoice = () => {
        setMoveInvoiceTarget(null);
        setMoveTargetShiftId(null);
        setMoveBillDate("");
        setMovableShifts([]);
    };

    const submitMoveInvoice = async () => {
        if (!moveInvoiceTarget || !moveTargetShiftId || !moveBillDate) return;
        setMoveSubmitting(true);
        try {
            await moveInvoice(moveInvoiceTarget.id!, moveTargetShiftId, moveBillDate);
            toast.success(`Moved ${moveInvoiceTarget.billNo || `#${moveInvoiceTarget.id}`} to Shift #${moveTargetShiftId}.`);
            closeMoveInvoice();
            // Refresh the current view so the moved bill leaves this shift's list
            if (viewMode === "shift" && activeShiftId) {
                await loadInvoices("shift", activeShiftId);
            } else if (viewMode === "dates" && historyFromDate && historyToDate) {
                await loadInvoices("dates", null, historyFromDate, historyToDate);
            }
        } catch (e) {
            toast.error(e instanceof Error ? e.message : "Failed to move invoice");
        } finally {
            setMoveSubmitting(false);
        }
    };

    const loadInvoices = async (mode: "shift" | "dates", shiftId?: number | null, from?: string, to?: string) => {
        setIsLoading(true);
        try {
            let invData: InvoiceBill[];
            if (mode === "shift" && shiftId) {
                invData = await getInvoicesByShift(shiftId);
            } else if (mode === "dates" && from && to) {
                // Server-side date filter via /api/invoices/history (paginated, EntityGraph-fetched).
                // We pull a generous page so the existing client-side table works unchanged for
                // typical date ranges; date filter happens in SQL, not after shipping every invoice.
                const result = await getInvoiceHistory(0, 500, {
                    fromDate: new Date(from).toISOString(),
                    toDate: new Date(to + "T23:59:59").toISOString(),
                });
                invData = result.content;
            } else {
                invData = [];
            }
            setInvoices(invData.sort((a: any, b: any) => new Date(b.date).getTime() - new Date(a.date).getTime()));
        } catch (err) {
            console.error("Failed to load invoices", err);
        } finally {
            setIsLoading(false);
        }
    };

    const loadData = async () => {
        setIsLoading(true);
        try {
            const [prodData, nozData, compRes] = await Promise.all([
                getActiveProducts(),
                getNozzles(),
                fetchWithAuth(`${API_BASE_URL}/companies/print-info`).then(r => r.ok ? r.json() : null)
            ]);
            setProducts(prodData);
            setNozzles(nozData.filter((n: Nozzle) => n.active));
            if (compRes) {
                const c = compRes;
                setCompanyInfo({ name: c.name, address: c.address, phone: c.phone, gstNo: c.gstNo, site: c.site });
            }

            // Fetch active shift and load shift invoices
            const shiftRes = await fetchWithAuth(`${API_BASE_URL}/shifts/active`);
            if (shiftRes.ok) {
                const text = await shiftRes.text();
                if (text) {
                    const shift = JSON.parse(text);
                    setActiveShiftId(shift.id);
                    const invData = await getInvoicesByShift(shift.id);
                    setInvoices(invData.sort((a: any, b: any) => new Date(b.date).getTime() - new Date(a.date).getTime()));
                    // Pre-fill date filters with shift start time
                    if (shift.startTime) {
                        setHistoryFromDate(shift.startTime.split("T")[0]);
                        setHistoryToDate(new Date().toISOString().split("T")[0]);
                    }
                } else {
                    setIsLoading(false);
                }
            } else {
                setIsLoading(false);
            }
        } catch (err) {
            console.error("Failed to load data", err);
        } finally {
            setIsLoading(false);
        }
    };

    useEffect(() => {
        loadData();
    }, []);

    // Default the target shift to the active one as soon as we know it; admin can override.
    useEffect(() => {
        if (targetShiftId == null && activeShiftId != null) setTargetShiftId(activeShiftId);
    }, [activeShiftId, targetShiftId]);

    // Step-1 unified search: customers (by name/phone) + vehicles (by plate) in parallel.
    // 300ms debounce so each keystroke doesn't fan out to two endpoints.
    const searchCustomers = (val: string) => {
        setCustomerSearch(val);
        if (step1SearchTimeout) clearTimeout(step1SearchTimeout);
        if (val.length < 2) {
            setCustomerSuggestions([]);
            setVehicleSuggestions([]);
            return;
        }
        const t = setTimeout(async () => {
            try {
                const [custRes, vehRes] = await Promise.all([
                    getCustomers(val).catch((e) => { console.error("Customer search failed", e); return { content: [] }; }),
                    searchVehicles(val).catch((e) => { console.error("Vehicle search failed", e); return [] as Vehicle[]; }),
                ]);
                setCustomerSuggestions(custRes.content || []);
                setVehicleSuggestions(vehRes || []);
            } catch (err) { console.error(err); }
        }, 300);
        setStep1SearchTimeout(t);
    };

    // Picking a vehicle suggestion auto-loads its owning customer (with credit info)
    // exactly like selectCustomer does, then jumps to Step 3 (skipping the Vehicle step
    // — same shortcut handleWalkIn uses). Critical: the credit-info merge MUST run, or
    // every credit-limit gate in getFuelValidationErrors silently returns "no limit"
    // and cashiers can blow past customer credit caps.
    const selectVehicleSuggestion = async (v: Vehicle) => {
        setCustomerSuggestions([]);
        setVehicleSuggestions([]);
        setSelectedVehicle(v);
        setVehicleQuickPicked(true);
        if (v.customer && v.customer.id) {
            setCustomerSearch(v.customer.name || v.vehicleNumber);
            try {
                const [vehiclesRes, incentivesData, creditInfo] = await Promise.all([
                    fetchWithAuth(`${API_BASE_URL}/customers/${v.customer.id}/vehicles`),
                    getIncentivesByCustomer(v.customer.id).catch(() => []),
                    getCustomerCreditInfo(v.customer.id).catch(() => null),
                ]);
                let merged: any = { ...v.customer };
                if (creditInfo) merged = { ...merged, ...creditInfo };
                setSelectedCustomer(merged);
                if (vehiclesRes.ok) {
                    setCustomerVehicles(await vehiclesRes.json());
                } else {
                    setCustomerVehicles([v]);
                }
                setIncentives(incentivesData.filter((i: Incentive) => i.active));
                if (creditInfo) {
                    const hasCreditLimit = (creditInfo.creditLimitAmount && Number(creditInfo.creditLimitAmount) > 0) ||
                        (creditInfo.creditLimitLiters && Number(creditInfo.creditLimitLiters) > 0);
                    if (hasCreditLimit) {
                        setBillType('CREDIT');
                    } else {
                        setBillType('CASH');
                        setPaymentMode('CASH');
                    }
                }
            } catch (err) { console.error(err); }
        } else {
            // Orphan vehicle (no owning customer) — fall through to walk-in CASH semantics
            // so the wizard still completes. Preserve the plate as the walk-in vehicle no.
            setIsWalkIn(true);
            setSelectedCustomer(undefined);
            setCustomerVehicles([v]);
            setIncentives([]);
            setBillType('CASH');
            setPaymentMode('CASH');
            setWalkInVehicleNo(v.vehicleNumber || "");
            setCustomerSearch(v.vehicleNumber || "");
        }
        setCurrentStep(3);
    };

    // Select customer and load their vehicles + incentives + credit info
    const selectCustomer = async (c: any) => {
        setSelectedCustomer(c);
        setCustomerSearch(c.name);
        setCustomerSuggestions([]);
        setSelectedVehicle(undefined);
        try {
            const [vehiclesRes, incentivesData, creditInfo] = await Promise.all([
                fetchWithAuth(`${API_BASE_URL}/customers/${c.id}/vehicles`),
                getIncentivesByCustomer(c.id).catch(() => []),
                getCustomerCreditInfo(c.id).catch(() => null)
            ]);
            if (vehiclesRes.ok) {
                setCustomerVehicles(await vehiclesRes.json());
            }
            setIncentives(incentivesData.filter((i: Incentive) => i.active));
            // Merge credit info (ledger balance) into selected customer
            if (creditInfo) {
                setSelectedCustomer((prev: any) => ({ ...prev, ...creditInfo }));
                // Auto-set bill type based on credit limits
                const hasCreditLimit = (creditInfo.creditLimitAmount && Number(creditInfo.creditLimitAmount) > 0) ||
                    (creditInfo.creditLimitLiters && Number(creditInfo.creditLimitLiters) > 0);
                if (hasCreditLimit) {
                    setBillType('CREDIT');
                } else {
                    setBillType('CASH');
                    setPaymentMode('CASH');
                }
            }
        } catch (err) { console.error(err); }
    };

    const selectVehicle = (v: any) => {
        setSelectedVehicle(v);
    };

    const handleVehicleSearch = (query: string) => {
        setVehicleSearchQuery(query);
        if (vehicleSearchTimeout) clearTimeout(vehicleSearchTimeout);
        if (query.length < 2) {
            setVehicleSearchResults([]);
            setVehicleSearchError(null);
            return;
        }
        const timeout = setTimeout(async () => {
            try {
                const results = await searchVehicles(query);
                setVehicleSearchResults(results);
                setVehicleSearchError(null);
            } catch (err: any) {
                console.error("Vehicle search failed", err);
                setVehicleSearchResults([]);
                setVehicleSearchError(err?.message || "Search failed — check connection or permissions");
            }
        }, 300);
        setVehicleSearchTimeout(timeout);
    };

    const addProductLine = () => {
        setSelectedProducts([...selectedProducts, {
            product: null,
            nozzle: null,
            quantity: "",
            unitPrice: "",
            amount: 0
        }]);
    };

    const updateProductLine = (index: number, updates: any) => {
        const newLines = [...selectedProducts];
        const line = { ...newLines[index], ...updates };

        const qty = parseFloat(line.quantity) || 0;
        const price = parseFloat(line.unitPrice) || 0;
        // When user entered amount directly, use that exact value instead of qty * price
        const gross = line.amountOverride != null ? line.amountOverride : qty * price;
        line.grossAmount = gross;
        // Clear the override so future qty/rate changes recalculate normally
        delete line.amountOverride;

        // Auto-apply incentive discount (customer-specific takes priority)
        const productId = line.product?.id;
        const incentive = productId ? incentives.find((i: Incentive) => (i.product as any)?.id === productId || (i.product as any) === productId) : null;
        if (incentive && (incentive.minQuantity == null || qty >= incentive.minQuantity)) {
            line.discountRate = incentive.discountRate;
            line.discountAmount = incentive.discountRate * qty;
            line.amount = gross - line.discountAmount;
            line.discountSource = 'incentive';
        } else if (line.product?.discountRate > 0 && line.applyProductDiscount !== false) {
            // Product-level discount fallback (cashier can toggle off)
            line.discountRate = line.product.discountRate;
            line.discountAmount = line.product.discountRate * qty;
            line.amount = gross - line.discountAmount;
            line.discountSource = 'product';
            if (line.applyProductDiscount === undefined) {
                line.applyProductDiscount = true;
            }
        } else {
            line.discountRate = null;
            line.discountAmount = null;
            line.amount = gross;
            line.discountSource = null;
        }

        newLines[index] = line;
        setSelectedProducts(newLines);
    };

    const removeProductLine = (index: number) => {
        setSelectedProducts(selectedProducts.filter((_: any, i: number) => i !== index));
    };

    const calculateTotal = () => {
        const subtotal = selectedProducts.reduce((sum: number, p: any) => sum + (p.amount || 0), 0);
        const discount = billType === 'CASH' && manualDiscount ? parseFloat(manualDiscount) || 0 : 0;
        return Math.max(subtotal - discount, 0);
    };

    // --- Vehicle fuel validation ---
    const isFuelProduct = (p: any) => p?.category?.toUpperCase() === "FUEL";

    const getFuelValidationErrors = () => {
        const errors: string[] = [];
        if (selectedProducts.length === 0) return errors;

        const totalFuelQty = selectedProducts
            .filter((l: any) => isFuelProduct(l.product))
            .reduce((sum: number, l: any) => sum + (parseFloat(l.quantity) || 0), 0);

        const totalInvoiceAmount = selectedProducts
            .reduce((sum: number, p: any) => sum + (p.amount || 0), 0);

        if (selectedVehicle) {
            const vehicleFuelFamily = selectedVehicle.preferredProduct?.fuelFamily;

            // Check fuel type compatibility
            if (vehicleFuelFamily) {
                for (const line of selectedProducts) {
                    if (isFuelProduct(line.product) && line.product?.fuelFamily
                        && line.product.fuelFamily !== vehicleFuelFamily) {
                        errors.push(
                            `${line.product.name} (${line.product.fuelFamily}) is not compatible with this vehicle's fuel type (${vehicleFuelFamily}).`
                        );
                    }
                }
            }

            // Check total fuel quantity against max capacity
            if (selectedVehicle.maxCapacity && selectedVehicle.maxCapacity > 0) {
                if (totalFuelQty > selectedVehicle.maxCapacity) {
                    errors.push(
                        `Total fuel quantity (${totalFuelQty} L) exceeds vehicle max tank capacity of ${selectedVehicle.maxCapacity} L.`
                    );
                }
            }

            // Check vehicle monthly liter limit (CREDIT invoices)
            if (billType === "CREDIT" && selectedVehicle.maxLitersPerMonth && selectedVehicle.maxLitersPerMonth > 0) {
                const consumed = selectedVehicle.consumedLiters || 0;
                const projected = consumed + totalFuelQty;
                if (projected > selectedVehicle.maxLitersPerMonth) {
                    const remaining = Math.max(0, selectedVehicle.maxLitersPerMonth - consumed);
                    errors.push(
                        `Vehicle monthly liter limit would be exceeded. Limit: ${selectedVehicle.maxLitersPerMonth} L, Consumed: ${consumed} L, This invoice: ${totalFuelQty.toFixed(2)} L, Remaining: ${remaining.toFixed(2)} L.`
                    );
                }
            }
        }

        // Check customer credit limits (CREDIT invoices only) — skip if force unblocked
        if (billType === "CREDIT" && selectedCustomer && !selectedCustomer.forceUnblocked) {
            // Amount-based limit — check against unbilled credit (current period), not total balance
            if (selectedCustomer.creditLimitAmount && Number(selectedCustomer.creditLimitAmount) > 0) {
                const unbilledCredit = (selectedCustomer.unbilledCredit ?? 0);
                const projectedUnbilled = unbilledCredit + totalInvoiceAmount;
                if (projectedUnbilled > Number(selectedCustomer.creditLimitAmount)) {
                    const remaining = Math.max(0, Number(selectedCustomer.creditLimitAmount) - unbilledCredit);
                    errors.push(
                        `Customer credit limit (₹) would be exceeded. Limit: ₹${Number(selectedCustomer.creditLimitAmount).toLocaleString("en-IN")}, Unbilled: ₹${unbilledCredit.toLocaleString("en-IN")}, This invoice: ₹${totalInvoiceAmount.toLocaleString("en-IN")}, Remaining: ₹${remaining.toLocaleString("en-IN")}.`
                    );
                }
            }

            // Liter-based limit
            if (selectedCustomer.creditLimitLiters && Number(selectedCustomer.creditLimitLiters) > 0 && totalFuelQty > 0) {
                const consumed = selectedCustomer.consumedLiters || 0;
                const projected = consumed + totalFuelQty;
                if (projected > Number(selectedCustomer.creditLimitLiters)) {
                    const remaining = Math.max(0, Number(selectedCustomer.creditLimitLiters) - consumed);
                    errors.push(
                        `Customer liter limit would be exceeded. Limit: ${Number(selectedCustomer.creditLimitLiters)} L, Consumed: ${consumed} L, This invoice: ${totalFuelQty.toFixed(2)} L, Remaining: ${remaining.toFixed(2)} L.`
                    );
                }
            }
        }

        return errors;
    };

    const fuelValidationErrors = getFuelValidationErrors();

    const resetForm = () => {
        setSelectedVehicle(undefined);
        setSelectedCustomer(undefined);
        setCustomerVehicles([]);
        setCustomerSearch("");
        setSelectedProducts([]);
        setIncentives([]);
        setVehicleKM("");
        setBillType('CASH');
        setPaymentMode('CASH');
        setIndentNo("");
        setDriverName("");
        setDriverPhone("");
        setCurrentStep(1);
        setError("");
        setIsWalkIn(false);
        setWalkInCustomerName("");
        setWalkInVehicleNo("");
        setWalkInGST("");
        setVehicleSearchQuery("");
        setVehicleSearchResults([]);
        setVehicleSearchError(null);
        setVehicleSuggestions([]);
        setCustomerSuggestions([]);
        setVehicleQuickPicked(false);
        setManualDiscount("");
    };

    const handleSave = async () => {
        setIsSaving(true);
        setError("");
        try {
            const payload: any = {
                billType,
                shiftId: (isShiftPickerAllowed && targetShiftId && targetShiftId !== activeShiftId)
                    ? targetShiftId : undefined,
                vehicleKM: vehicleKM ? Number(vehicleKM) : undefined,
                paymentMode: billType === 'CASH' ? paymentMode : undefined,
                indentNo: (billType === 'CREDIT' && indentNo) ? indentNo : undefined,
                netAmount: calculateTotal(),
                totalDiscount: (billType === 'CASH' && manualDiscount) ? parseFloat(manualDiscount) || 0 : undefined,
                status: 'PAID',
                products: selectedProducts.map((p: any) => ({
                    product: p.product ? { id: p.product.id } : undefined,
                    nozzle: p.nozzle ? { id: p.nozzle.id } : undefined,
                    quantity: parseFloat(p.quantity) || 0,
                    unitPrice: parseFloat(p.unitPrice) || 0,
                    amount: p.amount || 0,
                    grossAmount: p.grossAmount || undefined,
                    discountRate: p.discountRate || undefined,
                    discountAmount: p.discountAmount || undefined,
                })),
                customer: selectedCustomer ? { id: selectedCustomer.id } : undefined,
                vehicle: selectedVehicle ? { id: selectedVehicle.id } : undefined,
                driverName: driverName || undefined,
                driverPhone: driverPhone || undefined,
                signatoryName: walkInCustomerName || undefined,
                billDesc: walkInVehicleNo || undefined,
                customerGST: walkInGST || undefined,
                date: new Date().toISOString()
            };

            const saved = await createInvoice(payload);
            setLastCreatedInvoice(saved);
            setCurrentStep(6);
            toast.success("Invoice created successfully");

            // Print manually from success page via Print button
            loadInvoices(viewMode, activeShiftId, historyFromDate, historyToDate);
        } catch (err: any) {
            console.error("Failed to save invoice", err);
            setError(err.message || "Error saving invoice");
            toast.error(err.message || "Error saving invoice");
        } finally {
            setIsSaving(false);
        }
    };

    const isCustomerBlockedRaw = selectedCustomer && (selectedCustomer.status === "BLOCKED" || selectedCustomer.status === "INACTIVE");
    const isCustomerBlocked = isCustomerBlockedRaw && !selectedCustomer?.forceUnblocked;
    const isForceUnblockActive = isCustomerBlockedRaw && !!selectedCustomer?.forceUnblocked;
    const isVehicleBlocked = selectedVehicle && (selectedVehicle.status === "BLOCKED" || selectedVehicle.status === "INACTIVE");
    const isCreditCustomer = selectedCustomer && (
        (selectedCustomer.creditLimitAmount && Number(selectedCustomer.creditLimitAmount) > 0) ||
        (selectedCustomer.creditLimitLiters && Number(selectedCustomer.creditLimitLiters) > 0)
    );

    const stepperSteps = (isWalkIn || vehicleQuickPicked)
        ? [
            { step: 1, label: "Customer" },
            { step: 3, label: "Products" },
            { step: 4, label: "Payment" },
            { step: 5, label: "Confirm" }
        ]
        : [
            { step: 1, label: "Customer" },
            { step: 2, label: "Vehicle" },
            { step: 3, label: "Products" },
            { step: 4, label: "Payment" },
            { step: 5, label: "Confirm" }
        ];

    const renderStepper = () => (
        <div className="flex items-center justify-between mb-8 px-4">
            {stepperSteps.map(({ step, label }, idx) => (
                <div key={step} className="flex flex-col items-center relative flex-1">
                    <div className={`w-10 h-10 rounded-full flex items-center justify-center border-2 transition-all duration-300 ${
                        currentStep === step
                            ? "bg-primary border-primary text-primary-foreground shadow-lg shadow-primary/20 scale-110"
                            : currentStep > step
                                ? "bg-green-500 border-green-500 text-white"
                                : "bg-muted border-border text-muted-foreground"
                    }`}>
                        {currentStep > step ? <Check size={20} /> : idx + 1}
                    </div>
                    <span className={`text-[10px] mt-2 font-bold uppercase tracking-wider transition-colors ${currentStep === step ? "text-primary" : "text-muted-foreground"}`}>
                        {label}
                    </span>
                    {idx < stepperSteps.length - 1 && (
                        <div className={`absolute top-5 left-[60%] right-[-40%] h-[2px] -z-10 transition-colors ${currentStep > step ? "bg-green-500" : "bg-border"}`} />
                    )}
                </div>
            ))}
        </div>
    );

    const handleWalkIn = () => {
        setIsWalkIn(true);
        setVehicleQuickPicked(false);
        setSelectedCustomer(undefined);
        setSelectedVehicle(undefined);
        setCustomerVehicles([]);
        setCustomerSearch("");
        setCustomerSuggestions([]);
        setVehicleSuggestions([]);
        setIncentives([]);
        setBillType('CASH');
    };

    const renderStep1 = () => (
        <div className="space-y-6">
            <GlassCard className="p-8">
                <h3 className="text-xl font-bold text-foreground mb-6 flex items-center gap-2">
                    <User className="text-primary" size={24} />
                    Select Customer
                </h3>

                {/* Walk-in option */}
                <div className={`mb-6 p-5 border rounded-2xl transition-all ${isWalkIn ? 'bg-green-500/5 border-green-500/20' : 'bg-primary/5 border-primary/15'}`}>
                    <div className="flex items-center justify-between">
                        <div>
                            <p className="font-bold text-foreground text-sm">{isWalkIn ? 'Walk-in Customer' : 'Walk-in Customer?'}</p>
                            <p className="text-xs text-muted-foreground mt-0.5">
                                {isWalkIn ? 'Optionally enter customer details for the bill.' : 'For passing-by customers paying cash/card/UPI — no registration needed.'}
                            </p>
                        </div>
                        {!isWalkIn ? (
                            <button
                                onClick={handleWalkIn}
                                className="px-6 py-3 bg-foreground text-background rounded-xl font-bold text-sm transition-all hover:opacity-90 flex items-center gap-2 shrink-0"
                            >
                                <User size={16} />
                                Walk-in Bill
                            </button>
                        ) : (
                            <button
                                onClick={() => { setIsWalkIn(false); setVehicleQuickPicked(false); setWalkInCustomerName(""); setWalkInVehicleNo(""); setWalkInGST(""); }}
                                className="px-4 py-2 text-xs text-muted-foreground hover:text-foreground border border-border rounded-xl transition-colors"
                            >
                                Cancel
                            </button>
                        )}
                    </div>

                    {isWalkIn && (
                        <div className="mt-4 pt-4 border-t border-border/50 space-y-3">
                            <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
                                <div>
                                    <label className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest mb-1 block">Customer Name <span className="font-normal normal-case">(Optional)</span></label>
                                    <input
                                        type="text"
                                        value={walkInCustomerName}
                                        onChange={(e) => setWalkInCustomerName(e.target.value)}
                                        className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground text-sm"
                                        placeholder="e.g. Ravi Kumar"
                                    />
                                </div>
                                <div>
                                    <label className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest mb-1 block">Vehicle No <span className="font-normal normal-case">(Optional)</span></label>
                                    <input
                                        type="text"
                                        value={walkInVehicleNo}
                                        onChange={(e) => setWalkInVehicleNo(e.target.value.toUpperCase())}
                                        className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground text-sm font-mono"
                                        placeholder="e.g. TN 30 H 1234"
                                    />
                                </div>
                                <div>
                                    <label className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest mb-1 block">GST Number <span className="font-normal normal-case">(Optional)</span></label>
                                    <input
                                        type="text"
                                        value={walkInGST}
                                        onChange={(e) => setWalkInGST(e.target.value.toUpperCase())}
                                        className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground text-sm font-mono"
                                        placeholder="e.g. 33AABCS1234F1Z5"
                                        maxLength={15}
                                    />
                                </div>
                            </div>
                            <button
                                onClick={() => setCurrentStep(3)}
                                className="w-full py-3 btn-gradient text-white rounded-xl font-bold text-sm transition-all shadow-lg flex items-center justify-center gap-2"
                            >
                                Continue as Walk-in <ArrowRight size={16} />
                            </button>
                        </div>
                    )}
                </div>

                <div className="relative flex items-center gap-4 mb-2">
                    <div className="flex-1 border-t border-border"></div>
                    <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest">Or search any customer / vehicle</span>
                    <div className="flex-1 border-t border-border"></div>
                </div>

                <div className="relative mt-4">
                    <Search className="absolute left-4 top-1/2 -translate-y-1/2 text-muted-foreground" size={20} />
                    <input
                        type="text"
                        placeholder="Search by Customer Name, Phone or Vehicle Number..."
                        className="w-full bg-background border border-border rounded-2xl py-4 pl-12 pr-4 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50 transition-all font-medium text-lg"
                        value={customerSearch}
                        onChange={(e) => searchCustomers(e.target.value)}
                    />
                    {customerSearch && (vehicleSuggestions.length > 0 || customerSuggestions.length > 0) && (
                        <div className="absolute top-full left-0 right-0 mt-2 bg-background/95 backdrop-blur-xl border border-border rounded-2xl shadow-2xl z-50 overflow-hidden max-h-80 overflow-y-auto">
                            {vehicleSuggestions.length > 0 && (
                                <>
                                    <div className="px-4 py-2 text-[9px] font-bold text-muted-foreground uppercase tracking-widest bg-muted/30">
                                        Vehicles
                                    </div>
                                    {vehicleSuggestions.map((v: Vehicle) => (
                                        <button
                                            key={`v-${v.id}`}
                                            className="w-full px-6 py-3 text-left hover:bg-primary/5 text-foreground transition-colors flex items-center justify-between group"
                                            onClick={() => selectVehicleSuggestion(v)}
                                        >
                                            <div className="flex items-center gap-3 min-w-0">
                                                <Truck size={16} className="text-muted-foreground shrink-0" />
                                                <div className="min-w-0">
                                                    <div className="flex items-center gap-2 flex-wrap">
                                                        <span className="font-bold font-mono text-sm group-hover:text-primary transition-colors">{v.vehicleNumber}</span>
                                                        {v.vehicleType?.name && (
                                                            <span className="text-[9px] font-bold px-1.5 py-0.5 rounded bg-primary/10 text-primary uppercase tracking-wider">
                                                                {v.vehicleType.name}
                                                            </span>
                                                        )}
                                                        {v.status && v.status !== "ACTIVE" && (
                                                            <span className={`text-[9px] font-bold px-1.5 py-0.5 rounded ${
                                                                v.status === "BLOCKED" ? "bg-red-500/10 text-red-500" : "bg-yellow-500/10 text-yellow-500"
                                                            }`}>{v.status}</span>
                                                        )}
                                                    </div>
                                                    <span className="text-xs text-muted-foreground truncate block">
                                                        {v.customer?.name || "Unassigned"}
                                                    </span>
                                                </div>
                                            </div>
                                            <ArrowRight size={16} className="text-muted-foreground group-hover:text-primary opacity-0 group-hover:opacity-100 transition-all shrink-0" />
                                        </button>
                                    ))}
                                </>
                            )}
                            {customerSuggestions.length > 0 && (
                                <>
                                    <div className="px-4 py-2 text-[9px] font-bold text-muted-foreground uppercase tracking-widest bg-muted/30">
                                        Customers
                                    </div>
                                    {customerSuggestions.map((c: any) => (
                                        <button
                                            key={`c-${c.id}`}
                                            className="w-full px-6 py-3 text-left hover:bg-primary/5 text-foreground transition-colors flex items-center justify-between group"
                                            onClick={() => selectCustomer(c)}
                                        >
                                            <div>
                                                <span className="font-bold block group-hover:text-primary transition-colors">{c.name}</span>
                                                <span className="text-xs text-muted-foreground">{c.phoneNumbers || 'No Phone'}</span>
                                            </div>
                                            <div className="flex items-center gap-2">
                                                {c.status && c.status !== "ACTIVE" && (
                                                    <span className={`text-[9px] font-bold px-2 py-0.5 rounded-full ${
                                                        c.status === "BLOCKED" ? "bg-red-500/10 text-red-500" : "bg-yellow-500/10 text-yellow-500"
                                                    }`}>{c.status}</span>
                                                )}
                                                <Plus size={18} className="text-muted-foreground group-hover:text-primary opacity-0 group-hover:opacity-100 transition-all" />
                                            </div>
                                        </button>
                                    ))}
                                </>
                            )}
                        </div>
                    )}
                </div>
                {selectedCustomer && (
                    <div className={`mt-8 p-6 rounded-2xl flex items-center gap-6 ${
                        isCustomerBlocked
                            ? "bg-red-500/5 border border-red-500/20"
                            : isForceUnblockActive
                                ? "bg-indigo-500/5 border border-indigo-500/20"
                                : "bg-green-500/5 border border-green-500/20"
                    }`}>
                        <div className={`w-16 h-16 rounded-full flex items-center justify-center ${
                            isCustomerBlocked
                                ? "bg-red-500/10 text-red-500"
                                : isForceUnblockActive
                                    ? "bg-indigo-500/10 text-indigo-400"
                                    : "bg-green-500/10 text-green-500"
                        }`}>
                            {isCustomerBlocked ? <Ban size={32} /> : <CheckCircle2 size={32} />}
                        </div>
                        <div className="flex-1">
                            <p className={`text-xs font-bold uppercase tracking-widest mb-1 ${
                                isCustomerBlocked
                                    ? "text-red-600/60"
                                    : isForceUnblockActive
                                        ? "text-indigo-400/80"
                                        : "text-green-600/60"
                            }`}>
                                {isCustomerBlocked
                                    ? "Customer Blocked/Inactive"
                                    : isForceUnblockActive
                                        ? "Force-Unblock Override Active"
                                        : "Customer Confirmed"}
                            </p>
                            <p className="text-foreground font-black text-2xl">{selectedCustomer.name}</p>
                            <p className="text-sm text-muted-foreground">{selectedCustomer.phoneNumbers}</p>
                        </div>
                        <button
                            onClick={() => { setSelectedCustomer(undefined); setCustomerSearch(""); setCustomerVehicles([]); setSelectedVehicle(undefined); setVehicleQuickPicked(false); }}
                            className="text-muted-foreground hover:text-foreground p-2"
                        >
                            <Trash2 size={18} />
                        </button>
                    </div>
                )}
                {selectedCustomer && isCreditCustomer && (
                    <div className="mt-4">
                        <BlockingGatePanel
                            customerId={selectedCustomer.id}
                            vehicleId={selectedVehicle?.id}
                            invoiceAmount={calculateTotal() > 0 ? calculateTotal() : undefined}
                            invoiceLiters={(() => {
                                const litres = selectedProducts
                                    .filter((l: any) => isFuelProduct(l.product))
                                    .reduce((s: number, l: any) => s + (parseFloat(l.quantity) || 0), 0);
                                return litres > 0 ? litres : undefined;
                            })()}
                            variant="inline"
                        />
                    </div>
                )}
            </GlassCard>
            <div className="flex justify-end">
                <button
                    disabled={!selectedCustomer || !!isCustomerBlocked}
                    onClick={() => setCurrentStep(2)}
                    className="px-10 py-4 btn-gradient disabled:opacity-50 disabled:grayscale text-white rounded-2xl font-bold transition-all shadow-xl flex items-center gap-3 group"
                >
                    Next <ArrowRight size={20} className="group-hover:translate-x-1 transition-transform" />
                </button>
            </div>
        </div>
    );

    const isNonOwnedVehicle = selectedVehicle && selectedCustomer && selectedVehicle.customer && selectedVehicle.customer.id !== selectedCustomer.id;

    const renderVehicleCard = (v: any, showOwner?: boolean) => {
        const isBlocked = v.status === "BLOCKED" || v.status === "INACTIVE";
        const isSelected = selectedVehicle?.id === v.id;
        return (
            <button
                key={v.id}
                onClick={() => { selectVehicle(v); setVehicleSearchQuery(""); setVehicleSearchResults([]); setVehicleSearchError(null); }}
                className={`p-5 rounded-2xl border-2 text-left transition-all ${
                    isSelected
                        ? "border-primary bg-primary/5 shadow-lg"
                        : isBlocked
                            ? "border-red-500/20 bg-red-500/5 opacity-70"
                            : "border-border hover:border-primary/50 hover:bg-primary/5"
                }`}
            >
                <div className="flex items-center justify-between mb-2">
                    <p className="font-black text-lg text-foreground">{v.vehicleNumber}</p>
                    <span className={`text-[9px] font-bold px-2 py-0.5 rounded-full ${
                        v.status === "ACTIVE" || !v.status ? "bg-green-500/10 text-green-500"
                            : v.status === "BLOCKED" ? "bg-red-500/10 text-red-500"
                            : "bg-yellow-500/10 text-yellow-500"
                    }`}>
                        {v.status || "ACTIVE"}
                    </span>
                </div>
                <div className="text-xs text-muted-foreground space-y-1">
                    {v.vehicleType && <p>Type: {v.vehicleType.name}</p>}
                    {v.maxLitersPerMonth && (
                        <p>Limit: {v.consumedLiters || 0} / {v.maxLitersPerMonth} L</p>
                    )}
                    {showOwner && v.customer && (
                        <p className="text-amber-500 font-semibold">Owner: {v.customer.name}</p>
                    )}
                </div>
                {isSelected && (
                    <div className="mt-3 flex items-center gap-1 text-primary text-xs font-bold">
                        <CheckCircle2 size={14} /> Selected
                    </div>
                )}
            </button>
        );
    };

    const renderStep2 = () => (
        <div className="space-y-6">
            <GlassCard className="p-8">
                <h3 className="text-xl font-bold text-foreground mb-2 flex items-center gap-2">
                    <Truck className="text-primary" size={24} />
                    Select Vehicle
                </h3>
                <p className="text-muted-foreground text-sm mb-6">
                    Vehicles registered under <span className="text-primary font-bold">{selectedCustomer?.name}</span>
                </p>

                {customerVehicles.length === 0 ? (
                    <div className="text-center py-12 text-muted-foreground">
                        <Truck className="w-12 h-12 mx-auto mb-3 opacity-40" />
                        <p className="font-medium">No vehicles found for this customer.</p>
                        <p className="text-sm">You can search for any vehicle below.</p>
                    </div>
                ) : (
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        {customerVehicles.map((v: any) => renderVehicleCard(v))}
                    </div>
                )}

                {/* Cross-customer vehicle search */}
                <div className="relative flex items-center gap-4 mt-8 mb-4">
                    <div className="flex-1 border-t border-border"></div>
                    <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest">Or search for another vehicle</span>
                    <div className="flex-1 border-t border-border"></div>
                </div>

                <div className="relative">
                    <Search className="absolute left-4 top-1/2 -translate-y-1/2 text-muted-foreground" size={20} />
                    <input
                        type="text"
                        placeholder="Search any vehicle number (e.g. TN38...)"
                        className="w-full bg-background border border-border rounded-2xl py-4 pl-12 pr-4 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50 transition-all font-medium"
                        value={vehicleSearchQuery}
                        onChange={(e) => handleVehicleSearch(e.target.value)}
                    />
                </div>

                {vehicleSearchResults.length > 0 && (
                    <div className="mt-4 grid grid-cols-1 md:grid-cols-2 gap-4">
                        {vehicleSearchResults.map((v: any) => renderVehicleCard(v, true))}
                    </div>
                )}

                {vehicleSearchQuery.length >= 2 && vehicleSearchError && (
                    <p className="text-sm text-red-400 mt-3 text-center">⚠ {vehicleSearchError}</p>
                )}
                {vehicleSearchQuery.length >= 2 && !vehicleSearchError && vehicleSearchResults.length === 0 && (
                    <p className="text-sm text-muted-foreground mt-3 text-center">No other vehicles found matching &quot;{vehicleSearchQuery}&quot;</p>
                )}

                {/* Info banner for non-owned vehicle */}
                {isNonOwnedVehicle && (
                    <div className="mt-4 p-4 bg-blue-500/10 border border-blue-500/20 rounded-xl flex items-start gap-3">
                        <Info size={20} className="text-blue-500 shrink-0 mt-0.5" />
                        <p className="text-sm text-blue-600 dark:text-blue-400 font-medium">
                            This vehicle belongs to <span className="font-bold">{selectedVehicle.customer?.name}</span>. The bill will be charged to <span className="font-bold">{selectedCustomer?.name}</span>.
                        </p>
                    </div>
                )}

                {isVehicleBlocked && (
                    <div className="mt-4 p-4 bg-red-500/10 border border-red-500/20 rounded-xl flex items-start gap-3">
                        <ShieldAlert size={20} className="text-red-500 shrink-0 mt-0.5" />
                        <p className="text-sm text-red-600 dark:text-red-400 font-medium">
                            This vehicle is {selectedVehicle.status}. Invoice creation will be blocked by the system.
                        </p>
                    </div>
                )}

                <div className="mt-6">
                    <label className="text-xs font-bold text-muted-foreground uppercase tracking-widest mb-2 block">Vehicle KM (Optional)</label>
                    <input
                        type="number"
                        value={vehicleKM}
                        onChange={(e) => setVehicleKM(e.target.value)}
                        className="w-full max-w-xs bg-background border border-border rounded-xl px-4 py-3 text-foreground"
                        placeholder="Current odometer reading"
                    />
                </div>
            </GlassCard>
            <div className="flex justify-between items-center">
                <button onClick={() => setCurrentStep(1)} className="px-8 py-4 bg-muted hover:bg-muted/80 text-foreground rounded-2xl font-bold transition-all flex items-center gap-3 border border-border">
                    <ArrowLeft size={20} /> Back
                </button>
                <button
                    disabled={!selectedVehicle || !!isVehicleBlocked}
                    onClick={() => setCurrentStep(3)}
                    className="px-10 py-4 btn-gradient disabled:opacity-50 disabled:grayscale text-white rounded-2xl font-bold transition-all shadow-xl flex items-center gap-3 group"
                >
                    Next <ArrowRight size={20} className="group-hover:translate-x-1 transition-transform" />
                </button>
            </div>
        </div>
    );

    const renderStep3 = () => (
        <div className="space-y-6">
            <GlassCard className="p-8">
                <div className="flex items-center justify-between mb-8">
                    <h3 className="text-xl font-bold text-foreground flex items-center gap-2">
                        <Package className="text-primary" size={24} />
                        Add Products
                    </h3>
                    <button
                        onClick={addProductLine}
                        className="px-6 py-2.5 bg-primary/10 text-primary hover:bg-primary/20 rounded-xl text-sm font-black uppercase tracking-widest transition-all flex items-center gap-2 border border-primary/20"
                    >
                        <Plus size={18} /> Add Line
                    </button>
                </div>

                {/* Quick Select Product Buttons */}
                <div className="flex flex-wrap gap-2 mb-4">
                    {[
                        { label: "MS", matcher: (n: string, c: string) => c.toLowerCase() === "fuel" && n.toLowerCase().includes("petrol") && !n.toLowerCase().includes("additive") },
                        { label: "XP", matcher: (n: string, c: string) => c.toLowerCase() === "fuel" && (n.toLowerCase().includes("xtra premium") || n.toLowerCase().includes("extra premium")) },
                        { label: "HSD", matcher: (n: string, c: string) => c.toLowerCase() === "fuel" && n.toLowerCase().includes("diesel") && !n.toLowerCase().includes("additive") },
                        { label: "2T Loose", matcher: (n: string, c: string) => n.toLowerCase().includes("2t") && n.toLowerCase().includes("loose") },
                        { label: "2T 20ml", matcher: (n: string, c: string) => n.toLowerCase().includes("2t") && n.toLowerCase().includes("20") },
                        { label: "2T 40ml", matcher: (n: string, c: string) => n.toLowerCase().includes("2t") && n.toLowerCase().includes("40") },
                    ].map(({ label, matcher }) => {
                        const matched = products.find(p => matcher(p.name || "", p.category || ""));
                        if (!matched) return null;
                        return (
                            <button
                                key={label}
                                onClick={() => {
                                    const emptyIdx = selectedProducts.findIndex((l: any) => !l.product);
                                    const isFuel = matched.category?.toLowerCase() === "fuel";
                                    if (emptyIdx >= 0) {
                                        updateProductLine(emptyIdx, { product: matched, unitPrice: String(matched.price), amountMode: isFuel });
                                    } else {
                                        setSelectedProducts((prev: any[]) => [...prev, {
                                            product: matched,
                                            nozzle: null,
                                            quantity: "",
                                            unitPrice: String(matched.price),
                                            amountMode: isFuel,
                                            amount: 0
                                        }]);
                                    }
                                }}
                                className="px-4 py-1.5 bg-amber-500 hover:bg-amber-600 text-black rounded-full text-xs font-bold transition-all shadow-sm"
                            >
                                {label}
                            </button>
                        );
                    })}
                </div>

                {selectedProducts.length === 0 && (
                    <div className="text-center py-12 text-muted-foreground">
                        <Package className="w-12 h-12 mx-auto mb-3 opacity-40" />
                        <p className="font-medium">No products added yet.</p>
                        <p className="text-sm">Click "Add Line" to start adding products.</p>
                    </div>
                )}

                <div className="space-y-4">
                    {selectedProducts.map((line: any, idx: number) => (
                        <div key={idx} className="p-5 bg-background border border-border rounded-2xl space-y-4 relative border-l-4 border-l-primary">
                            <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-5 gap-4 items-end">
                                <div className="xl:col-span-2 relative">
                                    <label className="text-[10px] font-black text-muted-foreground uppercase tracking-widest mb-1 block">Product</label>
                                    <input
                                        type="text"
                                        placeholder="Type to search products..."
                                        value={line._productSearch ?? (line.product ? `${line.product.name} (${line.product.category})` : "")}
                                        onChange={(e) => {
                                            const val = e.target.value;
                                            updateProductLine(idx, { _productSearch: val, _showProductDropdown: true });
                                            if (!val) updateProductLine(idx, { product: null, unitPrice: "", _productSearch: "", _showProductDropdown: false });
                                        }}
                                        onFocus={() => updateProductLine(idx, { _showProductDropdown: true, _productSearch: line._productSearch ?? "" })}
                                        onBlur={() => setTimeout(() => updateProductLine(idx, { _showProductDropdown: false }), 200)}
                                        className="w-full px-3 py-2 bg-card border border-border rounded-lg text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
                                    />
                                    {line._showProductDropdown && (
                                        <div className="absolute z-50 mt-1 w-full bg-card border border-border rounded-lg shadow-lg max-h-48 overflow-y-auto">
                                            {products
                                                .filter(p => {
                                                    const q = (line._productSearch || "").toLowerCase();
                                                    if (!q) return true;
                                                    return p.name?.toLowerCase().includes(q) || p.category?.toLowerCase().includes(q);
                                                })
                                                .map(p => (
                                                    <button
                                                        key={p.id}
                                                        type="button"
                                                        onMouseDown={(e) => e.preventDefault()}
                                                        onClick={() => {
                                                            updateProductLine(idx, {
                                                                product: p,
                                                                unitPrice: String(p.price),
                                                                amountMode: p.category?.toLowerCase() === "fuel",
                                                                _productSearch: undefined,
                                                                _showProductDropdown: false,
                                                            });
                                                        }}
                                                        className="w-full text-left px-3 py-2 text-sm hover:bg-primary/10 transition-colors flex justify-between items-center"
                                                    >
                                                        <span className="font-medium">{p.name}</span>
                                                        <span className="text-xs text-muted-foreground ml-2">{p.category} - {p.unit}</span>
                                                    </button>
                                                ))}
                                            {products.filter(p => {
                                                const q = (line._productSearch || "").toLowerCase();
                                                return !q || p.name?.toLowerCase().includes(q) || p.category?.toLowerCase().includes(q);
                                            }).length === 0 && (
                                                <div className="px-3 py-2 text-sm text-muted-foreground">No products found</div>
                                            )}
                                        </div>
                                    )}
                                </div>

                                {line.product?.category?.toLowerCase() === "fuel" && (
                                    <div>
                                        <label className="text-[10px] font-black text-muted-foreground uppercase tracking-widest mb-1 block">Nozzle</label>
                                        <StyledSelect
                                            value={String(line.nozzle?.id || "")}
                                            onChange={(val) => {
                                                const n = nozzles.find(noz => noz.id === parseInt(val));
                                                updateProductLine(idx, { nozzle: n || null });
                                            }}
                                            options={[
                                                { value: "", label: "Select Nozzle..." },
                                                ...nozzles
                                                    .filter(n => n.tank?.productId === line.product?.id)
                                                    .map(n => ({ value: String(n.id), label: `${n.nozzleName} (${n.pump.name})` })),
                                            ]}
                                        />
                                    </div>
                                )}

                                <div>
                                    <div className="flex items-center justify-between mb-1">
                                        <label className="text-[10px] font-black text-muted-foreground uppercase tracking-widest">
                                            {line.amountMode ? "Amount (₹)" : `Qty${line.product?.category?.toLowerCase() === "fuel" ? " (L)" : ""}`}
                                        </label>
                                        {line.product?.category?.toLowerCase() === "fuel" && (
                                            <button
                                                type="button"
                                                onClick={() => updateProductLine(idx, { amountMode: !line.amountMode, quantity: "", amountInput: "" })}
                                                className="text-[9px] font-bold text-amber-500 hover:text-amber-400"
                                            >
                                                {line.amountMode ? "→ Liters" : "→ ₹ Amount"}
                                            </button>
                                        )}
                                    </div>
                                    {line.amountMode ? (
                                        <>
                                            <input
                                                type="number"
                                                step="0.01"
                                                className="w-full bg-muted border border-border rounded-xl p-3 text-foreground font-bold text-sm"
                                                value={line.amountInput || ""}
                                                onChange={(e) => {
                                                    const amt = parseFloat(e.target.value) || 0;
                                                    const price = parseFloat(line.unitPrice) || 0;
                                                    const qty = price > 0 ? (amt / price).toFixed(3) : "0";
                                                    updateProductLine(idx, { amountInput: e.target.value, quantity: qty, amountOverride: amt });
                                                }}
                                                placeholder="₹0"
                                            />
                                            {line.amountInput && parseFloat(line.unitPrice) > 0 && (
                                                <p className="text-[10px] text-primary font-bold mt-1">
                                                    = {(parseFloat(line.amountInput) / parseFloat(line.unitPrice)).toFixed(3)} L
                                                </p>
                                            )}
                                        </>
                                    ) : (
                                        <>
                                            <input
                                                type="number"
                                                step="0.01"
                                                className="w-full bg-muted border border-border rounded-xl p-3 text-foreground font-bold text-sm"
                                                value={line.quantity}
                                                onChange={(e) => updateProductLine(idx, { quantity: e.target.value })}
                                                placeholder="0"
                                            />
                                            {line.product?.category?.toLowerCase() === "fuel" && line.quantity && parseFloat(line.unitPrice) > 0 && (
                                                <p className="text-[10px] text-primary font-bold mt-1">
                                                    = ₹{(parseFloat(line.quantity) * parseFloat(line.unitPrice)).toFixed(2)}
                                                </p>
                                            )}
                                        </>
                                    )}
                                </div>

                                <div>
                                    <label className="text-[10px] font-black text-muted-foreground uppercase tracking-widest mb-1 block">Rate</label>
                                    <div className="flex items-center gap-2">
                                        <input
                                            type="number"
                                            step="0.01"
                                            className="w-full bg-muted border border-border rounded-xl p-3 text-foreground font-bold text-sm"
                                            value={line.unitPrice}
                                            onChange={(e) => updateProductLine(idx, { unitPrice: e.target.value })}
                                        />
                                        <button
                                            onClick={() => removeProductLine(idx)}
                                            className="p-3 text-red-500 hover:bg-red-500/10 rounded-xl transition-colors shrink-0"
                                        >
                                            <Trash2 size={18} />
                                        </button>
                                    </div>
                                </div>
                            </div>
                            <div className="text-right">
                                {/* Product discount toggle (only when no customer incentive) */}
                                {line.product?.discountRate > 0 && line.discountSource !== 'incentive' && (
                                    <div className="flex items-center justify-end gap-2 mb-1">
                                        <label className="flex items-center gap-2 cursor-pointer">
                                            <input
                                                type="checkbox"
                                                checked={line.applyProductDiscount !== false}
                                                onChange={(e) => updateProductLine(idx, { applyProductDiscount: e.target.checked })}
                                                className="rounded border-border accent-emerald-500"
                                            />
                                            <span className="text-[10px] text-emerald-600 dark:text-emerald-400 font-bold">
                                                Apply Discount (₹{line.product.discountRate}/{line.product.unit || 'unit'})
                                            </span>
                                        </label>
                                    </div>
                                )}
                                {line.discountRate > 0 && (
                                    <div className="text-[10px] text-emerald-500 font-bold mb-0.5">
                                        {line.discountSource === 'incentive' ? 'Incentive' : 'Discount'}: ₹{line.discountRate}/unit &times; {parseFloat(line.quantity) || 0} = -₹{(line.discountAmount || 0).toFixed(2)}
                                    </div>
                                )}
                                <span className="text-primary font-black text-lg">
                                    ₹{(line.amount || 0).toLocaleString(undefined, { minimumFractionDigits: 2 })}
                                </span>
                                {line.discountRate > 0 && (
                                    <span className="text-muted-foreground line-through text-xs ml-2">
                                        ₹{(line.grossAmount || 0).toFixed(2)}
                                    </span>
                                )}
                            </div>
                        </div>
                    ))}
                </div>

                {/* Fuel validation warnings */}
                {fuelValidationErrors.length > 0 && (
                    <div className="mt-6 p-4 bg-red-500/10 border border-red-500/20 rounded-xl space-y-2">
                        {fuelValidationErrors.map((err, i) => (
                            <div key={i} className="flex items-start gap-3">
                                <AlertTriangle size={18} className="text-red-500 shrink-0 mt-0.5" />
                                <p className="text-sm text-red-600 dark:text-red-400 font-medium">{err}</p>
                            </div>
                        ))}
                    </div>
                )}

                {/* Vehicle capacity info */}
                {selectedVehicle?.maxCapacity && selectedVehicle.maxCapacity > 0 && selectedProducts.some((l: any) => isFuelProduct(l.product)) && (
                    <div className="mt-4 p-3 bg-blue-500/10 border border-blue-500/20 rounded-xl flex items-center gap-3">
                        <Info size={16} className="text-blue-500 shrink-0" />
                        <p className="text-xs text-blue-600 dark:text-blue-400 font-medium">
                            Vehicle tank capacity: <span className="font-black">{selectedVehicle.maxCapacity} L</span>
                            {" | "}Fuel in this bill: <span className="font-black">
                                {selectedProducts.filter((l: any) => isFuelProduct(l.product)).reduce((s: number, l: any) => s + (parseFloat(l.quantity) || 0), 0).toFixed(2)} L
                            </span>
                        </p>
                    </div>
                )}

                {selectedProducts.length > 0 && (
                    <div className="mt-8 pt-6 border-t border-border flex justify-between items-end">
                        <div>
                            <p className="text-[10px] text-muted-foreground mb-1 uppercase tracking-widest font-black">Net Amount</p>
                            <p className="text-4xl font-black text-foreground">₹{calculateTotal().toLocaleString(undefined, { minimumFractionDigits: 2 })}</p>
                        </div>
                        <p className="text-sm text-muted-foreground">{selectedProducts.length} item(s)</p>
                    </div>
                )}
            </GlassCard>
            <div className="flex justify-between items-center">
                <button onClick={() => setCurrentStep(isWalkIn ? 1 : 2)} className="px-8 py-4 bg-muted hover:bg-muted/80 text-foreground rounded-2xl font-bold transition-all flex items-center gap-3 border border-border">
                    <ArrowLeft size={20} /> Back
                </button>
                <button
                    disabled={selectedProducts.length === 0 || calculateTotal() === 0 || fuelValidationErrors.length > 0}
                    onClick={() => setCurrentStep(4)}
                    className="px-10 py-4 btn-gradient disabled:opacity-50 disabled:grayscale text-white rounded-2xl font-bold transition-all shadow-xl flex items-center gap-3 group"
                >
                    Next <ArrowRight size={20} className="group-hover:translate-x-1 transition-transform" />
                </button>
            </div>
        </div>
    );

    const renderStep4 = () => (
        <div className="space-y-6">
            <GlassCard className="p-8">
                <h3 className="text-xl font-bold text-foreground mb-8 flex items-center gap-2">
                    <CreditCard className="text-primary" size={24} />
                    Payment & Driver
                </h3>

                <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 lg:gap-8">
                    <div className="space-y-6">
                        <div>
                            <label className="text-xs font-bold text-muted-foreground uppercase tracking-widest mb-2 block">Bill Type</label>
                            {isWalkIn ? (
                                <div className="py-4 px-4 rounded-2xl font-bold text-sm uppercase border-2 bg-primary text-primary-foreground border-primary text-center">
                                    Cash
                                </div>
                            ) : (
                                <div className="grid grid-cols-2 gap-4">
                                    <button
                                        onClick={() => setBillType("CASH")}
                                        className={`py-4 rounded-2xl font-bold text-sm uppercase border-2 transition-all ${
                                            billType === "CASH" ? "bg-primary text-primary-foreground border-primary shadow-lg" : "bg-muted border-border text-muted-foreground"
                                        }`}
                                    >
                                        Cash
                                    </button>
                                    <button
                                        onClick={() => setBillType("CREDIT")}
                                        className={`py-4 rounded-2xl font-bold text-sm uppercase border-2 transition-all ${
                                            billType === "CREDIT" ? "bg-primary text-primary-foreground border-primary shadow-lg" : "bg-muted border-border text-muted-foreground"
                                        }`}
                                    >
                                        Credit
                                    </button>
                                </div>
                            )}
                        </div>

                        {billType === "CASH" && (
                            <div>
                                <label className="text-xs font-bold text-muted-foreground uppercase tracking-widest mb-2 block">Payment Method</label>
                                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2">
                                    {["CASH", "CARD", "UPI", "CCMS"].map(mode => (
                                        <button
                                            key={mode}
                                            onClick={() => setPaymentMode(mode)}
                                            className={`py-3 rounded-xl text-xs font-bold uppercase border-2 transition-all ${
                                                paymentMode === mode ? "bg-foreground text-background border-foreground" : "bg-muted border-border text-muted-foreground"
                                            }`}
                                        >
                                            {mode}
                                        </button>
                                    ))}
                                </div>
                            </div>
                        )}

                        {billType === "CASH" && (
                            <div>
                                <label className="text-xs font-bold text-muted-foreground uppercase tracking-widest mb-2 block">Discount (Optional)</label>
                                <input
                                    type="number"
                                    value={manualDiscount}
                                    onChange={(e) => setManualDiscount(e.target.value)}
                                    className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground font-bold"
                                    placeholder="₹0.00"
                                    min="0"
                                    step="0.01"
                                />
                            </div>
                        )}

                        {billType === "CREDIT" && (
                            <div>
                                <label className="text-xs font-bold text-muted-foreground uppercase tracking-widest mb-2 block">Indent No.</label>
                                <input
                                    type="text"
                                    value={indentNo}
                                    onChange={(e) => setIndentNo(e.target.value)}
                                    className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground"
                                    placeholder="Enter indent/reference number"
                                />
                            </div>
                        )}

                        <div>
                            <label className="text-xs font-bold text-muted-foreground uppercase tracking-widest mb-2 block">
                                Driver Name {isWalkIn && <span className="text-muted-foreground font-normal normal-case">(Optional)</span>}
                            </label>
                            <input
                                type="text"
                                value={driverName}
                                onChange={(e) => setDriverName(e.target.value)}
                                className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground font-bold"
                                placeholder={isWalkIn ? "Optional for walk-in" : "Enter driver name"}
                            />
                        </div>

                        <div>
                            <label className="text-xs font-bold text-muted-foreground uppercase tracking-widest mb-2 block">Driver Phone (Optional)</label>
                            <input
                                type="text"
                                value={driverPhone}
                                onChange={(e) => setDriverPhone(e.target.value)}
                                className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground"
                                placeholder="Mobile number"
                            />
                        </div>
                    </div>

                    <div className="p-4 sm:p-6 lg:p-8 bg-muted/30 border border-border rounded-3xl flex flex-col justify-center items-center text-center">
                        <p className="text-[10px] text-muted-foreground mb-2 uppercase tracking-[0.2em] font-black">Total Payable</p>
                        <p className="text-5xl font-black text-foreground mb-3">₹{calculateTotal().toLocaleString(undefined, { minimumFractionDigits: 2 })}</p>
                        <div className="w-full space-y-2 mt-6 text-left px-4">
                            <div className="flex justify-between text-xs">
                                <span className="text-muted-foreground font-bold">Customer:</span>
                                <span className="text-foreground font-bold">{isWalkIn ? (walkInCustomerName || "Walk-in") : selectedCustomer?.name}</span>
                            </div>
                            <div className="flex justify-between text-xs">
                                <span className="text-muted-foreground font-bold">Vehicle:</span>
                                <span className="text-foreground font-bold">{isWalkIn ? (walkInVehicleNo || "—") : selectedVehicle?.vehicleNumber}</span>
                            </div>
                            <div className="flex justify-between text-xs">
                                <span className="text-muted-foreground font-bold">Type:</span>
                                <span className="text-foreground font-bold">{billType}</span>
                            </div>
                            <div className="flex justify-between text-xs">
                                <span className="text-muted-foreground font-bold">Items:</span>
                                <span className="text-foreground font-bold">{selectedProducts.length}</span>
                            </div>
                        </div>
                    </div>
                </div>
            </GlassCard>
            <div className="flex justify-between items-center">
                <button onClick={() => setCurrentStep(3)} className="px-8 py-4 bg-muted hover:bg-muted/80 text-foreground rounded-2xl font-bold transition-all flex items-center gap-3 border border-border">
                    <ArrowLeft size={20} /> Back
                </button>
                <button
                    onClick={() => setCurrentStep(5)}
                    disabled={!isWalkIn && !driverName}
                    className="px-10 py-4 btn-gradient disabled:opacity-50 text-white rounded-2xl font-bold transition-all shadow-xl flex items-center gap-3 group"
                >
                    Review <ArrowRight size={20} className="group-hover:translate-x-1 transition-transform" />
                </button>
            </div>
        </div>
    );

    const renderStep5 = () => (
        <div className="space-y-6">
            <GlassCard className="p-8">
                <h3 className="text-xl font-bold text-foreground mb-8 flex items-center gap-2">
                    <FileText className="text-primary" size={24} />
                    Review & Confirm
                </h3>

                <div className="space-y-6">
                    {/* Summary */}
                    <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                        <div className="p-4 bg-muted rounded-xl">
                            <p className="text-[10px] text-muted-foreground uppercase font-bold mb-1">Customer</p>
                            <p className="font-bold text-foreground">{isWalkIn ? (walkInCustomerName || "Walk-in") : selectedCustomer?.name}</p>
                        </div>
                        <div className="p-4 bg-muted rounded-xl">
                            <p className="text-[10px] text-muted-foreground uppercase font-bold mb-1">Vehicle</p>
                            <p className="font-bold text-foreground">{isWalkIn ? (walkInVehicleNo || "—") : selectedVehicle?.vehicleNumber}</p>
                        </div>
                        <div className="p-4 bg-muted rounded-xl">
                            <p className="text-[10px] text-muted-foreground uppercase font-bold mb-1">Bill Type</p>
                            <p className="font-bold text-foreground">{billType} {billType === "CASH" ? `(${paymentMode})` : ""}</p>
                        </div>
                        <div className="p-4 bg-muted rounded-xl">
                            <p className="text-[10px] text-muted-foreground uppercase font-bold mb-1">Driver</p>
                            <p className="font-bold text-foreground">{driverName || "—"}</p>
                        </div>
                    </div>
                    {walkInGST && (
                        <div className="p-3 bg-blue-500/5 border border-blue-500/20 rounded-xl flex items-center gap-2 text-sm">
                            <span className="text-muted-foreground font-bold">GST:</span>
                            <span className="font-mono font-bold text-foreground">{walkInGST}</span>
                        </div>
                    )}

                    {/* Product lines */}
                    <div className="border border-border rounded-xl overflow-hidden">
                        {(() => {
                            const hasAnyDiscount = selectedProducts.some((l: any) => l.discountRate > 0);
                            const totalGross = selectedProducts.reduce((s: number, l: any) => s + (l.grossAmount || l.amount || 0), 0);
                            const totalDiscount = selectedProducts.reduce((s: number, l: any) => s + (l.discountAmount || 0), 0);
                            return (
                                <table className="w-full text-sm">
                                    <thead>
                                        <tr className="bg-muted/50 border-b border-border">
                                            <th className="px-4 py-3 text-left text-[10px] font-bold uppercase text-muted-foreground">Product</th>
                                            <th className="px-4 py-3 text-right text-[10px] font-bold uppercase text-muted-foreground">Qty</th>
                                            <th className="px-4 py-3 text-right text-[10px] font-bold uppercase text-muted-foreground">Rate</th>
                                            {hasAnyDiscount && (
                                                <th className="px-4 py-3 text-right text-[10px] font-bold uppercase text-muted-foreground">Discount</th>
                                            )}
                                            <th className="px-4 py-3 text-right text-[10px] font-bold uppercase text-muted-foreground">Amount</th>
                                        </tr>
                                    </thead>
                                    <tbody className="divide-y divide-border/50">
                                        {selectedProducts.map((line: any, idx: number) => (
                                            <tr key={idx}>
                                                <td className="px-4 py-3 font-medium">
                                                    {line.product?.name || "Unknown"}
                                                    {line.nozzle && <span className="text-xs text-muted-foreground ml-1">({line.nozzle.nozzleName})</span>}
                                                </td>
                                                <td className="px-4 py-3 text-right font-mono">{line.quantity}</td>
                                                <td className="px-4 py-3 text-right font-mono">₹{parseFloat(line.unitPrice || 0).toFixed(2)}</td>
                                                {hasAnyDiscount && (
                                                    <td className="px-4 py-3 text-right">
                                                        {line.discountRate > 0 ? (
                                                            <span className="text-emerald-500 font-bold">-₹{(line.discountAmount || 0).toFixed(2)}</span>
                                                        ) : (
                                                            <span className="text-muted-foreground">—</span>
                                                        )}
                                                    </td>
                                                )}
                                                <td className="px-4 py-3 text-right font-bold">₹{(line.amount || 0).toFixed(2)}</td>
                                            </tr>
                                        ))}
                                    </tbody>
                                    <tfoot>
                                        {hasAnyDiscount && (
                                            <>
                                                <tr className="border-t border-border/50">
                                                    <td colSpan={hasAnyDiscount ? 4 : 3} className="px-4 py-2 text-right text-xs text-muted-foreground font-bold uppercase">Gross Total</td>
                                                    <td className="px-4 py-2 text-right font-mono text-muted-foreground">₹{totalGross.toFixed(2)}</td>
                                                </tr>
                                                <tr>
                                                    <td colSpan={hasAnyDiscount ? 4 : 3} className="px-4 py-2 text-right text-xs text-emerald-500 font-bold uppercase">Total Discount</td>
                                                    <td className="px-4 py-2 text-right font-bold text-emerald-500">-₹{totalDiscount.toFixed(2)}</td>
                                                </tr>
                                            </>
                                        )}
                                        {billType === "CASH" && manualDiscount && parseFloat(manualDiscount) > 0 && (
                                            <tr className="border-t border-border/50">
                                                <td colSpan={hasAnyDiscount ? 4 : 3} className="px-4 py-2 text-right text-xs text-emerald-500 font-bold uppercase">Manual Discount</td>
                                                <td className="px-4 py-2 text-right font-bold text-emerald-500">-₹{parseFloat(manualDiscount).toFixed(2)}</td>
                                            </tr>
                                        )}
                                        <tr className="bg-primary/5 border-t border-primary/20">
                                            <td colSpan={hasAnyDiscount ? 4 : 3} className="px-4 py-4 text-right font-black uppercase text-sm">Net Total</td>
                                            <td className="px-4 py-4 text-right font-black text-primary text-xl">₹{calculateTotal().toFixed(2)}</td>
                                        </tr>
                                    </tfoot>
                                </table>
                            );
                        })()}
                    </div>

                    {error && (
                        <div className="p-4 bg-red-500/10 border border-red-500/20 rounded-xl flex items-start gap-3">
                            <AlertTriangle size={20} className="text-red-500 shrink-0 mt-0.5" />
                            <p className="text-sm text-red-600 dark:text-red-400 font-medium">{error}</p>
                        </div>
                    )}

                    <div className="p-4 bg-yellow-500/5 border border-yellow-500/10 rounded-xl text-xs text-yellow-700 dark:text-yellow-400">
                        {isWalkIn
                            ? "This is a walk-in cash bill. No customer or vehicle records will be updated."
                            : "Submitting this invoice will automatically update consumed liters for the customer and vehicle, and deduct from inventory records."
                        }
                    </div>
                </div>
            </GlassCard>
            <div className="flex justify-between items-center">
                <button onClick={() => setCurrentStep(4)} className="px-8 py-4 bg-muted hover:bg-muted/80 text-foreground rounded-2xl font-bold transition-all flex items-center gap-3 border border-border">
                    <ArrowLeft size={20} /> Back
                </button>
                <button
                    onClick={handleSave}
                    disabled={isSaving}
                    className="px-12 py-5 btn-gradient disabled:opacity-50 text-white rounded-3xl font-black text-lg transition-all shadow-2xl hover:scale-[1.02] flex items-center gap-4 group"
                >
                    {isSaving ? "Creating Invoice..." : "Confirm & Create Invoice"}
                    {!isSaving && <CheckCircle2 size={24} className="group-hover:scale-110 transition-transform" />}
                </button>
            </div>
        </div>
    );

    const renderShiftTargetPill = () => (
        <div className="mb-6">
            <ShiftPicker
                value={targetShiftId}
                onChange={setTargetShiftId}
                activeShiftId={activeShiftId}
            />
        </div>
    );

    const renderCreateTab = () => (
        <div className="max-w-4xl mx-auto pb-20">
            {isShiftPickerAllowed && currentStep < 6 && renderShiftTargetPill()}
            {renderStepper()}
            <div className="mt-10">
                {currentStep === 1 && renderStep1()}
                {currentStep === 2 && renderStep2()}
                {currentStep === 3 && renderStep3()}
                {currentStep === 4 && renderStep4()}
                {currentStep === 5 && renderStep5()}
                {currentStep === 6 && lastCreatedInvoice && (
                    <div className="space-y-6">
                        <GlassCard className="p-8">
                            <div className="flex items-center gap-3 mb-6">
                                <div className="w-12 h-12 rounded-full bg-green-500/10 flex items-center justify-center">
                                    <CheckCircle2 className="w-6 h-6 text-green-500" />
                                </div>
                                <div>
                                    <h3 className="text-xl font-bold text-foreground">Invoice Created</h3>
                                    <p className="text-sm text-muted-foreground">
                                        Bill No: {lastCreatedInvoice.billNo} — ₹{lastCreatedInvoice.netAmount?.toFixed(2)}
                                    </p>
                                </div>
                            </div>

                            <h4 className="text-sm font-semibold text-muted-foreground uppercase tracking-wider mb-4">
                                Attach Documents (Optional)
                            </h4>
                            <div className="space-y-4">
                                <FileUploadField
                                    id="bill-pic-upload"
                                    label="Upload Bill Photo"
                                    accept="image/*"
                                    hint="Photo of the physical bill"
                                    currentUrl={lastCreatedInvoice.billPic || undefined}
                                    onUpload={async (file) => {
                                        await uploadInvoiceFile(lastCreatedInvoice.id!, "bill-pic", file);
                                    }}
                                    onView={async () => {
                                        const url = await getInvoiceFileUrl(lastCreatedInvoice.id!, "bill-pic");
                                        window.open(url, "_blank");
                                    }}
                                />
                                <FileUploadField
                                    id="pump-bill-pic-upload"
                                    label="Upload Pump Bill Photo"
                                    accept="image/*"
                                    hint="Photo of the pump meter bill"
                                    currentUrl={lastCreatedInvoice.pumpBillPic || undefined}
                                    onUpload={async (file) => {
                                        await uploadInvoiceFile(lastCreatedInvoice.id!, "pump-bill-pic", file);
                                    }}
                                    onView={async () => {
                                        const url = await getInvoiceFileUrl(lastCreatedInvoice.id!, "pump-bill-pic");
                                        window.open(url, "_blank");
                                    }}
                                />
                                <FileUploadField
                                    id="indent-pic-upload"
                                    label="Upload Indent Photo"
                                    accept="image/*"
                                    hint="Photo of the indent/authorization"
                                    currentUrl={lastCreatedInvoice.indentPic || undefined}
                                    onUpload={async (file) => {
                                        await uploadInvoiceFile(lastCreatedInvoice.id!, "indent-pic", file);
                                    }}
                                    onView={async () => {
                                        const url = await getInvoiceFileUrl(lastCreatedInvoice.id!, "indent-pic");
                                        window.open(url, "_blank");
                                    }}
                                />
                            </div>
                        </GlassCard>
                        <div className="flex justify-end gap-3">
                            {companyInfo && (
                                <button
                                    onClick={() => printInvoice(lastCreatedInvoice, companyInfo)}
                                    className="px-8 py-4 border border-border text-foreground rounded-2xl font-bold transition-all flex items-center gap-3 hover:bg-muted/50"
                                >
                                    <Receipt size={20} /> Print
                                </button>
                            )}
                            <button
                                onClick={() => {
                                    resetForm();
                                    setLastCreatedInvoice(null);
                                    setActiveTab('history');
                                    loadInvoices(viewMode, activeShiftId, historyFromDate, historyToDate);
                                }}
                                className="px-10 py-4 btn-gradient text-white rounded-2xl font-bold transition-all shadow-xl flex items-center gap-3"
                            >
                                Done <ArrowRight size={20} />
                            </button>
                        </div>
                    </div>
                )}
            </div>
        </div>
    );

    return (
        <div className="p-4 sm:p-6 lg:p-8 min-h-screen bg-background">
            <div className="max-w-7xl mx-auto">
                <div className="flex justify-between items-center mb-10">
                    <div>
                        <h1 className="text-4xl font-bold text-foreground tracking-tight">
                            Billing <span className="text-gradient">& POS</span>
                        </h1>
                        <p className="text-muted-foreground mt-2">
                            Generate and manage cash and credit invoices for fuel and products.
                        </p>
                    </div>

                    <div className="flex bg-black/5 dark:bg-white/5 p-1 rounded-2xl border border-border/50">
                        <PermissionGate permission="INVOICE_CREATE">
                            <button
                                onClick={() => { setActiveTab('create'); resetForm(); }}
                                className={`flex items-center gap-2 px-6 py-2.5 rounded-xl font-medium transition-all ${activeTab === 'create' ? 'bg-primary text-primary-foreground shadow-lg shadow-primary/20' : 'text-muted-foreground hover:text-foreground'}`}
                            >
                                <Receipt className="w-4 h-4" />
                                New Bill
                            </button>
                        </PermissionGate>
                        <button
                            onClick={() => setActiveTab('history')}
                            className={`flex items-center gap-2 px-6 py-2.5 rounded-xl font-medium transition-all ${activeTab === 'history' ? 'bg-primary text-primary-foreground shadow-lg shadow-primary/20' : 'text-muted-foreground hover:text-foreground'}`}
                        >
                            <History className="w-4 h-4" />
                            History
                        </button>
                    </div>
                </div>

                {/* Latest bills banner — show last cash AND last credit */}
                {(() => {
                    const allInvs = lastCreatedInvoice ? [lastCreatedInvoice, ...invoices.filter(i => i.id !== lastCreatedInvoice.id)] : invoices;
                    const latestCash = allInvs.find(i => i.billType === 'CASH');
                    const latestCredit = allInvs.find(i => i.billType === 'CREDIT');
                    if (!latestCash && !latestCredit) return null;

                    const renderBill = (bill: any, color: string, bgClass: string, borderClass: string) => (
                        <div className="flex items-center gap-2">
                            <span className={`px-2 py-0.5 rounded-full text-[10px] font-bold uppercase ${bgClass} ${color} border ${borderClass}`}>{bill.billType}</span>
                            <span className="font-mono font-bold text-sm text-foreground">{bill.billNo}</span>
                            {bill.vehicle?.vehicleNumber && (
                                <span className="font-mono text-xs text-primary">{bill.vehicle.vehicleNumber}</span>
                            )}
                            <span className="text-xs text-muted-foreground">{bill.customer?.name || "Walk-in"}</span>
                            <span className="font-bold text-foreground text-sm">₹{bill.netAmount?.toFixed(2)}</span>
                        </div>
                    );

                    return (
                        <div className="mb-4 p-3 bg-card border border-border rounded-xl flex items-center gap-4 flex-wrap">
                            <div className="flex items-center gap-1.5">
                                <CheckCircle2 className="w-4 h-4 text-green-500" />
                                <span className="text-sm font-bold text-foreground">Latest:</span>
                            </div>
                            {latestCash && renderBill(latestCash, "text-green-500", "bg-green-500/10", "border-green-500/20")}
                            {latestCash && latestCredit && <span className="text-border">|</span>}
                            {latestCredit && renderBill(latestCredit, "text-blue-500", "bg-blue-500/10", "border-blue-500/20")}
                        </div>
                    );
                })()}

                {activeTab === 'create' ? (
                    renderCreateTab()
                ) : (
                    <GlassCard className="p-6">
                        <div className="flex flex-col gap-4 mb-4">
                            <div className="flex items-center justify-between">
                                <h3 className="text-sm font-bold text-foreground">
                                    {viewMode === "shift" ? "Current Shift Invoices" : "Invoices by Date Range"}
                                </h3>
                                <Link href="/operations/invoices/history" className="text-sm text-primary hover:underline font-medium flex items-center gap-1">
                                    View Full History <ArrowRight className="w-4 h-4" />
                                </Link>
                            </div>

                            {/* View indicator */}
                            <div className="flex items-center gap-2 text-xs">
                                {viewMode === "shift" ? (
                                    <span className="px-2 py-1 bg-primary/10 text-primary rounded-lg font-medium">
                                        Shift #{activeShiftId || "—"}
                                    </span>
                                ) : (
                                    <span className="px-2 py-1 bg-amber-500/10 text-amber-500 rounded-lg font-medium">
                                        {historyFromDate} → {historyToDate}
                                    </span>
                                )}
                            </div>

                            {/* Shift / Date toggle */}
                            <div className="flex flex-wrap items-center gap-2">
                                {viewMode === "dates" && activeShiftId && (
                                    <button
                                        onClick={() => {
                                            setViewMode("shift");
                                            setHistoryFromDate("");
                                            setHistoryToDate("");
                                            loadInvoices("shift", activeShiftId);
                                        }}
                                        className="px-3 py-1.5 text-xs font-bold bg-primary/10 text-primary border border-primary/20 rounded-lg hover:bg-primary/20 transition-colors"
                                    >
                                        Current Shift
                                    </button>
                                )}
                                <div className="flex items-center gap-2">
                                    <Calendar className="w-3.5 h-3.5 text-muted-foreground" />
                                    <input
                                        type="date"
                                        value={historyFromDate}
                                        onChange={(e) => setHistoryFromDate(e.target.value)}
                                        className="bg-background border border-border rounded-lg px-2 py-1.5 text-xs text-foreground"
                                    />
                                    <span className="text-xs text-muted-foreground">to</span>
                                    <input
                                        type="date"
                                        value={historyToDate}
                                        onChange={(e) => setHistoryToDate(e.target.value)}
                                        className="bg-background border border-border rounded-lg px-2 py-1.5 text-xs text-foreground"
                                    />
                                    <button
                                        onClick={() => {
                                            if (historyFromDate && historyToDate) {
                                                setViewMode("dates");
                                                loadInvoices("dates", null, historyFromDate, historyToDate);
                                            }
                                        }}
                                        disabled={!historyFromDate || !historyToDate}
                                        className="px-3 py-1.5 text-xs font-bold bg-foreground text-background rounded-lg hover:opacity-90 transition-opacity disabled:opacity-40"
                                    >
                                        <Search className="w-3.5 h-3.5" />
                                    </button>
                                </div>
                            </div>
                        </div>

                        {/* Summary cards */}
                        {invoices.length > 0 && (
                            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3 mb-4">
                                <div className="bg-primary/5 border border-primary/10 rounded-xl p-3 text-center">
                                    <div className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest">Total</div>
                                    <div className="text-lg font-bold text-foreground">{invoices.length}</div>
                                </div>
                                <div className="bg-green-500/5 border border-green-500/10 rounded-xl p-3 text-center">
                                    <div className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest">Cash</div>
                                    <div className="text-lg font-bold text-green-500">₹{invoices.filter(i => i.billType === 'CASH').reduce((s, i) => s + (i.netAmount || 0), 0).toLocaleString("en-IN", { minimumFractionDigits: 2 })}</div>
                                </div>
                                <div className="bg-blue-500/5 border border-blue-500/10 rounded-xl p-3 text-center">
                                    <div className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest">Credit</div>
                                    <div className="text-lg font-bold text-blue-500">₹{invoices.filter(i => i.billType === 'CREDIT').reduce((s, i) => s + (i.netAmount || 0), 0).toLocaleString("en-IN", { minimumFractionDigits: 2 })}</div>
                                </div>
                            </div>
                        )}

                        {isLoading ? (
                            <div className="flex flex-col items-center justify-center py-12 text-muted-foreground">
                                <div className="w-12 h-12 border-4 border-primary/20 border-t-primary rounded-full animate-spin mb-4" />
                                <p className="animate-pulse">Loading invoices...</p>
                            </div>
                        ) : invoices.length === 0 ? (
                            <div className="text-center text-muted-foreground py-8">
                                {viewMode === "shift"
                                    ? (activeShiftId ? "No invoices in the current shift yet." : "No active shift found.")
                                    : "No invoices found for the selected date range."}
                            </div>
                        ) : (
                            <div className="space-y-2">
                                {invoices.slice(0, 10).map((inv: any) => (
                                    <div key={inv.id} className="flex items-center justify-between py-2 border-b border-border/30 last:border-0">
                                        <div className="flex items-center gap-3">
                                            <span className="font-mono font-bold text-sm text-foreground">{inv.billNo || "—"}</span>
                                            <span className={`px-2 py-0.5 rounded-full text-[10px] font-bold uppercase ${
                                                inv.billType === 'CASH' ? 'bg-green-500/10 text-green-500 border border-green-500/20' : 'bg-blue-500/10 text-blue-500 border border-blue-500/20'
                                            }`}>{inv.billType}</span>
                                            {inv.vehicle?.vehicleNumber && (
                                                <span className="font-mono text-xs text-primary">{inv.vehicle.vehicleNumber}</span>
                                            )}
                                            <span className="text-sm text-muted-foreground">{inv.customer?.name || "Walk-in"}</span>
                                            {inv.products && inv.products.length > 0 && (
                                                <span className="text-xs text-muted-foreground">
                                                    {inv.products.length === 1
                                                        ? inv.products[0].productName
                                                        : `${inv.products[0].productName} +${inv.products.length - 1}`}
                                                    {" · "}
                                                    {inv.products.reduce((s: number, p: any) => s + (p.quantity || 0), 0).toFixed(2)}L
                                                </span>
                                            )}
                                        </div>
                                        <div className="flex items-center gap-3">
                                            {isShiftPickerAllowed && (
                                                <button
                                                    type="button"
                                                    onClick={() => openMoveInvoice(inv)}
                                                    title="Move this invoice to a different shift (admin)"
                                                    className="p-1.5 rounded-lg text-amber-500 hover:bg-amber-500/10 transition-colors"
                                                >
                                                    <Move className="w-4 h-4" />
                                                </button>
                                            )}
                                            <div className="text-right">
                                                <span className="font-bold text-foreground">₹{(inv.netAmount || 0).toLocaleString(undefined, { minimumFractionDigits: 2 })}</span>
                                                <div className="text-[10px] text-muted-foreground">{new Date(inv.date).toLocaleDateString()}</div>
                                            </div>
                                        </div>
                                    </div>
                                ))}
                                {invoices.length > 10 && (
                                    <div className="text-center pt-2">
                                        <Link href="/operations/invoices/history" className="text-xs text-primary hover:underline">
                                            + {invoices.length - 10} more — View Full History
                                        </Link>
                                    </div>
                                )}
                            </div>
                        )}
                    </GlassCard>
                )}
            </div>

            {moveInvoiceTarget && (() => {
                const selected = movableShifts.find(s => s.id === moveTargetShiftId);
                const selectedStart = selected?.startTime ? new Date(selected.startTime) : null;
                const selectedEndRaw = selected?.endTime ? new Date(selected.endTime) : null;
                const selectedEnd = selectedEndRaw ?? new Date();
                const toLocalInput = (d: Date) => {
                    const tzOffsetMs = d.getTimezoneOffset() * 60_000;
                    return new Date(d.getTime() - tzOffsetMs).toISOString().slice(0, 16);
                };
                const minAttr = selectedStart ? toLocalInput(selectedStart) : undefined;
                const maxAttr = toLocalInput(selectedEnd);
                const billDateValid = (() => {
                    if (!moveBillDate || !selectedStart) return false;
                    const d = new Date(moveBillDate);
                    if (Number.isNaN(d.getTime())) return false;
                    if (d < selectedStart) return false;
                    if (d > selectedEnd) return false;
                    return true;
                })();
                return (
                    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 print:hidden">
                        <div className="bg-card rounded-xl p-6 w-full max-w-lg shadow-2xl">
                            <h3 className="text-lg font-semibold mb-1 flex items-center gap-2">
                                <Move className="w-5 h-5 text-amber-500" />
                                Move Invoice {moveInvoiceTarget.billNo || `#${moveInvoiceTarget.id}`}
                            </h3>
                            <p className="text-xs text-muted-foreground mb-4">
                                Currently on Shift #{moveInvoiceTarget.shiftId ?? "—"}. Pick the correct shift and a bill date inside its window. Audit-logged.
                            </p>
                            <div className="space-y-4">
                                <div>
                                    <label className="text-xs font-bold uppercase tracking-wide text-muted-foreground">Target shift</label>
                                    <StyledSelect
                                        value={moveTargetShiftId != null ? String(moveTargetShiftId) : ""}
                                        onChange={(v) => {
                                            const id = v ? Number(v) : null;
                                            setMoveTargetShiftId(id);
                                            setMoveBillDate("");
                                        }}
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
                                            No movable shifts. RECONCILED shifts must be un-finalized from their report page first.
                                        </p>
                                    )}
                                </div>
                                {selected && (
                                    <div>
                                        <label className="text-xs font-bold uppercase tracking-wide text-muted-foreground">New bill date/time</label>
                                        <input
                                            type="datetime-local"
                                            value={moveBillDate}
                                            min={minAttr}
                                            max={maxAttr}
                                            onChange={(e) => setMoveBillDate(e.target.value)}
                                            className="mt-1 w-full px-3 py-2 border border-border rounded-lg bg-background text-sm"
                                        />
                                        <p className="text-[10px] text-muted-foreground mt-1">
                                            Window: {selectedStart?.toLocaleString("en-IN") ?? "—"} → {selectedEndRaw?.toLocaleString("en-IN") ?? "now"}
                                        </p>
                                    </div>
                                )}
                            </div>
                            <div className="flex justify-end gap-2 mt-6">
                                <button
                                    type="button"
                                    onClick={closeMoveInvoice}
                                    className="px-4 py-2 text-sm rounded-lg bg-muted text-muted-foreground"
                                >
                                    Cancel
                                </button>
                                <button
                                    type="button"
                                    onClick={submitMoveInvoice}
                                    disabled={moveSubmitting || !moveTargetShiftId || !billDateValid}
                                    className="px-4 py-2 text-sm rounded-lg bg-amber-500 text-white disabled:opacity-40 disabled:cursor-not-allowed flex items-center gap-1.5"
                                >
                                    <Move className="w-4 h-4" />
                                    {moveSubmitting ? "Moving…" : "Move invoice"}
                                </button>
                            </div>
                        </div>
                    </div>
                );
            })()}
        </div>
    );
}
