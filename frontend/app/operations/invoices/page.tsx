"use client";

import { useEffect, useState } from "react";
import { GlassCard } from "@/components/ui/glass-card";
import {
    getInvoices,
    createInvoice,
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
    API_BASE_URL
} from "@/lib/api/station";
import { FileUploadField } from "@/components/ui/file-upload-field";
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
    Info
} from "lucide-react";
import Link from "next/link";

export default function InvoicesPage() {
    const [invoices, setInvoices] = useState<InvoiceBill[]>([]);
    const [products, setProducts] = useState<Product[]>([]);
    const [nozzles, setNozzles] = useState<Nozzle[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [activeTab, setActiveTab] = useState<'create' | 'history'>('create');
    const [currentStep, setCurrentStep] = useState(1);
    const [error, setError] = useState("");

    // Customer & Vehicle
    const [customerSearch, setCustomerSearch] = useState("");
    const [customerSuggestions, setCustomerSuggestions] = useState<any[]>([]);
    const [selectedCustomer, setSelectedCustomer] = useState<any>(undefined);
    const [customerVehicles, setCustomerVehicles] = useState<any[]>([]);
    const [selectedVehicle, setSelectedVehicle] = useState<any>(undefined);
    const [isSaving, setIsSaving] = useState(false);
    const [isWalkIn, setIsWalkIn] = useState(false);

    // Vehicle search (cross-customer)
    const [vehicleSearchQuery, setVehicleSearchQuery] = useState("");
    const [vehicleSearchResults, setVehicleSearchResults] = useState<Vehicle[]>([]);
    const [vehicleSearchTimeout, setVehicleSearchTimeout] = useState<NodeJS.Timeout | null>(null);

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

    const loadData = async () => {
        setIsLoading(true);
        try {
            const [invData, prodData, nozData] = await Promise.all([
                getInvoices(),
                getActiveProducts(),
                getNozzles()
            ]);
            setInvoices(invData.sort((a: any, b: any) => new Date(b.date).getTime() - new Date(a.date).getTime()));
            setProducts(prodData);
            setNozzles(nozData.filter((n: Nozzle) => n.active));
        } catch (err) {
            console.error("Failed to load data", err);
        } finally {
            setIsLoading(false);
        }
    };

    useEffect(() => {
        loadData();
    }, []);

    // Search customers
    const searchCustomers = async (val: string) => {
        setCustomerSearch(val);
        if (val.length < 2) { setCustomerSuggestions([]); return; }
        try {
            const data = await getCustomers(val);
            setCustomerSuggestions(data.content || []);
        } catch (err) { console.error(err); }
    };

    // Select customer and load their vehicles + incentives
    const selectCustomer = async (c: any) => {
        setSelectedCustomer(c);
        setCustomerSearch(c.name);
        setCustomerSuggestions([]);
        setSelectedVehicle(undefined);
        try {
            const [vehiclesRes, incentivesData] = await Promise.all([
                fetch(`${API_BASE_URL}/customers/${c.id}/vehicles`),
                getIncentivesByCustomer(c.id).catch(() => [])
            ]);
            if (vehiclesRes.ok) {
                setCustomerVehicles(await vehiclesRes.json());
            }
            setIncentives(incentivesData.filter((i: Incentive) => i.active));
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
            return;
        }
        const timeout = setTimeout(async () => {
            try {
                const results = await searchVehicles(query);
                // Exclude vehicles already shown in the customer's list
                const customerVehicleIds = new Set(customerVehicles.map((v: any) => v.id));
                setVehicleSearchResults(results.filter(v => !customerVehicleIds.has(v.id)));
            } catch (err) {
                console.error("Vehicle search failed", err);
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
        const gross = qty * price;
        line.grossAmount = gross;

        // Auto-apply incentive discount
        const productId = line.product?.id;
        const incentive = productId ? incentives.find((i: Incentive) => (i.product as any)?.id === productId || (i.product as any) === productId) : null;
        if (incentive && (incentive.minQuantity == null || qty >= incentive.minQuantity)) {
            line.discountRate = incentive.discountRate;
            line.discountAmount = incentive.discountRate * qty;
            line.amount = gross - line.discountAmount;
        } else {
            line.discountRate = null;
            line.discountAmount = null;
            line.amount = gross;
        }

        newLines[index] = line;
        setSelectedProducts(newLines);
    };

    const removeProductLine = (index: number) => {
        setSelectedProducts(selectedProducts.filter((_: any, i: number) => i !== index));
    };

    const calculateTotal = () => {
        return selectedProducts.reduce((sum: number, p: any) => sum + (p.amount || 0), 0);
    };

    // --- Vehicle fuel validation ---
    const isFuelProduct = (p: any) => p?.category?.toUpperCase() === "FUEL";

    const getFuelValidationErrors = () => {
        const errors: string[] = [];
        if (!selectedVehicle || selectedProducts.length === 0) return errors;

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
            const totalFuelQty = selectedProducts
                .filter((l: any) => isFuelProduct(l.product))
                .reduce((sum: number, l: any) => sum + (parseFloat(l.quantity) || 0), 0);

            if (totalFuelQty > selectedVehicle.maxCapacity) {
                errors.push(
                    `Total fuel quantity (${totalFuelQty} L) exceeds vehicle max tank capacity of ${selectedVehicle.maxCapacity} L.`
                );
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
        setVehicleSearchQuery("");
        setVehicleSearchResults([]);
    };

    const handleSave = async () => {
        setIsSaving(true);
        setError("");
        try {
            const payload: any = {
                billType,
                vehicleKM: vehicleKM ? Number(vehicleKM) : undefined,
                paymentMode: billType === 'CASH' ? paymentMode : undefined,
                indentNo: billType === 'CREDIT' ? indentNo : undefined,
                netAmount: calculateTotal(),
                status: 'PAID',
                products: selectedProducts.map((p: any) => ({
                    product: p.product ? { id: p.product.id } : undefined,
                    nozzle: p.nozzle ? { id: p.nozzle.id } : undefined,
                    quantity: parseFloat(p.quantity) || 0,
                    unitPrice: parseFloat(p.unitPrice) || 0,
                    amount: p.amount || 0
                })),
                customer: selectedCustomer ? { id: selectedCustomer.id } : undefined,
                vehicle: selectedVehicle ? { id: selectedVehicle.id } : undefined,
                driverName,
                driverPhone,
                date: new Date().toISOString()
            };

            const saved = await createInvoice(payload);
            setLastCreatedInvoice(saved);
            setCurrentStep(6);
            loadData();
        } catch (err: any) {
            console.error("Failed to save invoice", err);
            setError(err.message || "Error saving invoice");
        } finally {
            setIsSaving(false);
        }
    };

    const isCustomerBlocked = selectedCustomer && (selectedCustomer.status === "BLOCKED" || selectedCustomer.status === "INACTIVE");
    const isVehicleBlocked = selectedVehicle && (selectedVehicle.status === "BLOCKED" || selectedVehicle.status === "INACTIVE");

    const stepperSteps = isWalkIn
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
        setSelectedCustomer(undefined);
        setSelectedVehicle(undefined);
        setCustomerVehicles([]);
        setCustomerSearch("");
        setIncentives([]);
        setBillType('CASH');
        setCurrentStep(3);
    };

    const renderStep1 = () => (
        <div className="space-y-6">
            <GlassCard className="p-8">
                <h3 className="text-xl font-bold text-foreground mb-6 flex items-center gap-2">
                    <User className="text-primary" size={24} />
                    Select Customer
                </h3>

                {/* Walk-in option */}
                <div className="mb-6 p-5 bg-primary/5 border border-primary/15 rounded-2xl">
                    <div className="flex items-center justify-between">
                        <div>
                            <p className="font-bold text-foreground text-sm">Walk-in Customer?</p>
                            <p className="text-xs text-muted-foreground mt-0.5">
                                For passing-by customers paying cash/card/UPI — no registration needed.
                            </p>
                        </div>
                        <button
                            onClick={handleWalkIn}
                            className="px-6 py-3 bg-foreground text-background rounded-xl font-bold text-sm transition-all hover:opacity-90 flex items-center gap-2 shrink-0"
                        >
                            <User size={16} />
                            Walk-in Bill
                        </button>
                    </div>
                </div>

                <div className="relative flex items-center gap-4 mb-2">
                    <div className="flex-1 border-t border-border"></div>
                    <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest">Or select registered customer</span>
                    <div className="flex-1 border-t border-border"></div>
                </div>

                <div className="relative mt-4">
                    <Search className="absolute left-4 top-1/2 -translate-y-1/2 text-muted-foreground" size={20} />
                    <input
                        type="text"
                        placeholder="Search Customer Name or Phone..."
                        className="w-full bg-background border border-border rounded-2xl py-4 pl-12 pr-4 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50 transition-all font-medium text-lg"
                        value={customerSearch}
                        onChange={(e) => searchCustomers(e.target.value)}
                    />
                    {customerSearch && customerSuggestions.length > 0 && (
                        <div className="absolute top-full left-0 right-0 mt-2 bg-background/95 backdrop-blur-xl border border-border rounded-2xl shadow-2xl z-50 overflow-hidden max-h-60 overflow-y-auto">
                            {customerSuggestions.map((c: any) => (
                                <button
                                    key={c.id}
                                    className="w-full px-6 py-4 text-left hover:bg-primary/5 text-foreground transition-colors flex items-center justify-between group"
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
                        </div>
                    )}
                </div>
                {selectedCustomer && (
                    <div className={`mt-8 p-6 rounded-2xl flex items-center gap-6 ${
                        isCustomerBlocked
                            ? "bg-red-500/5 border border-red-500/20"
                            : "bg-green-500/5 border border-green-500/20"
                    }`}>
                        <div className={`w-16 h-16 rounded-full flex items-center justify-center ${
                            isCustomerBlocked ? "bg-red-500/10 text-red-500" : "bg-green-500/10 text-green-500"
                        }`}>
                            {isCustomerBlocked ? <Ban size={32} /> : <CheckCircle2 size={32} />}
                        </div>
                        <div className="flex-1">
                            <p className={`text-xs font-bold uppercase tracking-widest mb-1 ${
                                isCustomerBlocked ? "text-red-600/60" : "text-green-600/60"
                            }`}>
                                {isCustomerBlocked ? "Customer Blocked/Inactive" : "Customer Confirmed"}
                            </p>
                            <p className="text-foreground font-black text-2xl">{selectedCustomer.name}</p>
                            <p className="text-sm text-muted-foreground">{selectedCustomer.phoneNumbers}</p>
                            {selectedCustomer.creditLimitLiters && (
                                <p className="text-xs text-muted-foreground mt-1">
                                    Credit: {selectedCustomer.consumedLiters || 0} / {selectedCustomer.creditLimitLiters} L used
                                </p>
                            )}
                        </div>
                        <button
                            onClick={() => { setSelectedCustomer(undefined); setCustomerSearch(""); setCustomerVehicles([]); setSelectedVehicle(undefined); }}
                            className="text-muted-foreground hover:text-foreground p-2"
                        >
                            <Trash2 size={18} />
                        </button>
                    </div>
                )}
                {isCustomerBlocked && (
                    <div className="mt-4 p-4 bg-red-500/10 border border-red-500/20 rounded-xl flex items-start gap-3">
                        <ShieldAlert size={20} className="text-red-500 shrink-0 mt-0.5" />
                        <p className="text-sm text-red-600 dark:text-red-400 font-medium">
                            This customer is {selectedCustomer.status}. Invoice creation will be blocked by the system.
                        </p>
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
                onClick={() => { selectVehicle(v); setVehicleSearchQuery(""); setVehicleSearchResults([]); }}
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

                {vehicleSearchQuery.length >= 2 && vehicleSearchResults.length === 0 && (
                    <p className="text-sm text-muted-foreground mt-3 text-center">No other vehicles found matching &quot;{vehicleSearchQuery}&quot;</p>
                )}

                {/* Info banner for non-owned vehicle */}
                {isNonOwnedVehicle && (
                    <div className="mt-4 p-4 bg-blue-500/10 border border-blue-500/20 rounded-xl flex items-start gap-3">
                        <Info size={20} className="text-blue-500 shrink-0 mt-0.5" />
                        <p className="text-sm text-blue-600 dark:text-blue-400 font-medium">
                            This vehicle belongs to <span className="font-bold">{selectedVehicle.customer.name}</span>. The bill will be charged to <span className="font-bold">{selectedCustomer?.name}</span>.
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
                            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-5 gap-4 items-end">
                                <div className="lg:col-span-2">
                                    <label className="text-[10px] font-black text-muted-foreground uppercase tracking-widest mb-1 block">Product</label>
                                    <select
                                        className="w-full bg-muted border border-border rounded-xl p-3 text-foreground font-bold text-sm"
                                        value={line.product?.id || ""}
                                        onChange={(e) => {
                                            const p = products.find(prod => prod.id === parseInt(e.target.value));
                                            if (p) updateProductLine(idx, { product: p, unitPrice: String(p.price) });
                                        }}
                                    >
                                        <option value="">Select Product...</option>
                                        {products.map(p => <option key={p.id} value={p.id}>{p.name} ({p.category} - {p.unit})</option>)}
                                    </select>
                                </div>

                                {line.product?.category === "Fuel" && (
                                    <div>
                                        <label className="text-[10px] font-black text-muted-foreground uppercase tracking-widest mb-1 block">Nozzle</label>
                                        <select
                                            className="w-full bg-muted border border-border rounded-xl p-3 text-foreground text-sm"
                                            value={line.nozzle?.id || ""}
                                            onChange={(e) => {
                                                const n = nozzles.find(noz => noz.id === parseInt(e.target.value));
                                                updateProductLine(idx, { nozzle: n || null });
                                            }}
                                        >
                                            <option value="">Select Nozzle...</option>
                                            {nozzles
                                                .filter(n => n.tank?.product?.id === line.product?.id)
                                                .map(n => (
                                                    <option key={n.id} value={n.id}>
                                                        {n.nozzleName} ({n.pump.name})
                                                    </option>
                                                ))}
                                        </select>
                                    </div>
                                )}

                                <div>
                                    <label className="text-[10px] font-black text-muted-foreground uppercase tracking-widest mb-1 block">Qty</label>
                                    <input
                                        type="number"
                                        step="0.01"
                                        className="w-full bg-muted border border-border rounded-xl p-3 text-foreground font-bold text-sm"
                                        value={line.quantity}
                                        onChange={(e) => updateProductLine(idx, { quantity: e.target.value })}
                                        placeholder="0"
                                    />
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
                                {line.discountRate > 0 && (
                                    <div className="text-[10px] text-emerald-500 font-bold mb-0.5">
                                        Discount: ₹{line.discountRate}/unit &times; {parseFloat(line.quantity) || 0} = -₹{(line.discountAmount || 0).toFixed(2)}
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

                <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
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
                                <div className="grid grid-cols-3 gap-2">
                                    {["CASH", "CARD", "UPI", "CHEQUE", "BANK", "CCMS"].map(mode => (
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

                    <div className="p-8 bg-muted/30 border border-border rounded-3xl flex flex-col justify-center items-center text-center">
                        <p className="text-[10px] text-muted-foreground mb-2 uppercase tracking-[0.2em] font-black">Total Payable</p>
                        <p className="text-5xl font-black text-foreground mb-3">₹{calculateTotal().toLocaleString(undefined, { minimumFractionDigits: 2 })}</p>
                        <div className="w-full space-y-2 mt-6 text-left px-4">
                            <div className="flex justify-between text-xs">
                                <span className="text-muted-foreground font-bold">Customer:</span>
                                <span className="text-foreground font-bold">{isWalkIn ? "Walk-in" : selectedCustomer?.name}</span>
                            </div>
                            <div className="flex justify-between text-xs">
                                <span className="text-muted-foreground font-bold">Vehicle:</span>
                                <span className="text-foreground font-bold">{isWalkIn ? "—" : selectedVehicle?.vehicleNumber}</span>
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
                            <p className="font-bold text-foreground">{isWalkIn ? "Walk-in" : selectedCustomer?.name}</p>
                        </div>
                        <div className="p-4 bg-muted rounded-xl">
                            <p className="text-[10px] text-muted-foreground uppercase font-bold mb-1">Vehicle</p>
                            <p className="font-bold text-foreground">{isWalkIn ? "—" : selectedVehicle?.vehicleNumber}</p>
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

                    {/* Product lines */}
                    <div className="border border-border rounded-xl overflow-hidden">
                        <table className="w-full text-sm">
                            <thead>
                                <tr className="bg-muted/50 border-b border-border">
                                    <th className="px-4 py-3 text-left text-[10px] font-bold uppercase text-muted-foreground">Product</th>
                                    <th className="px-4 py-3 text-right text-[10px] font-bold uppercase text-muted-foreground">Qty</th>
                                    <th className="px-4 py-3 text-right text-[10px] font-bold uppercase text-muted-foreground">Rate</th>
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
                                        <td className="px-4 py-3 text-right font-bold">₹{(line.amount || 0).toFixed(2)}</td>
                                    </tr>
                                ))}
                            </tbody>
                            <tfoot>
                                <tr className="bg-primary/5 border-t border-primary/20">
                                    <td colSpan={3} className="px-4 py-4 text-right font-black uppercase text-sm">Total</td>
                                    <td className="px-4 py-4 text-right font-black text-primary text-xl">₹{calculateTotal().toFixed(2)}</td>
                                </tr>
                            </tfoot>
                        </table>
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

    const renderCreateTab = () => (
        <div className="max-w-4xl mx-auto pb-20">
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
                        <div className="flex justify-end">
                            <button
                                onClick={() => {
                                    resetForm();
                                    setLastCreatedInvoice(null);
                                    setActiveTab('history');
                                    loadData();
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
        <div className="p-8 min-h-screen bg-background">
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
                        <button
                            onClick={() => { setActiveTab('create'); resetForm(); }}
                            className={`flex items-center gap-2 px-6 py-2.5 rounded-xl font-medium transition-all ${activeTab === 'create' ? 'bg-primary text-primary-foreground shadow-lg shadow-primary/20' : 'text-muted-foreground hover:text-foreground'}`}
                        >
                            <Receipt className="w-4 h-4" />
                            New Bill
                        </button>
                        <button
                            onClick={() => setActiveTab('history')}
                            className={`flex items-center gap-2 px-6 py-2.5 rounded-xl font-medium transition-all ${activeTab === 'history' ? 'bg-primary text-primary-foreground shadow-lg shadow-primary/20' : 'text-muted-foreground hover:text-foreground'}`}
                        >
                            <History className="w-4 h-4" />
                            History
                        </button>
                    </div>
                </div>

                {activeTab === 'create' ? (
                    renderCreateTab()
                ) : (
                    <GlassCard className="p-6">
                        <div className="flex items-center justify-between mb-4">
                            <h3 className="text-sm font-bold text-foreground">Recent Invoices</h3>
                            <Link href="/operations/invoices/history" className="text-sm text-primary hover:underline font-medium flex items-center gap-1">
                                View Full History <ArrowRight className="w-4 h-4" />
                            </Link>
                        </div>
                        {invoices.length === 0 ? (
                            <div className="text-center text-muted-foreground py-8">
                                No invoices found. Create your first invoice using the &quot;New Bill&quot; tab.
                            </div>
                        ) : (
                            <div className="space-y-2">
                                {invoices.slice(0, 5).map((inv: any) => (
                                    <div key={inv.id} className="flex items-center justify-between py-2 border-b border-border/30 last:border-0">
                                        <div className="flex items-center gap-3">
                                            <span className="font-mono font-bold text-sm text-foreground">{inv.billNo || "—"}</span>
                                            <span className={`px-2 py-0.5 rounded-full text-[10px] font-bold uppercase ${
                                                inv.billType === 'CASH' ? 'bg-green-500/10 text-green-500 border border-green-500/20' : 'bg-blue-500/10 text-blue-500 border border-blue-500/20'
                                            }`}>{inv.billType}</span>
                                            <span className="text-sm text-muted-foreground">{inv.customer?.name || "Walk-in"}</span>
                                        </div>
                                        <div className="text-right">
                                            <span className="font-bold text-foreground">₹{(inv.netAmount || 0).toLocaleString(undefined, { minimumFractionDigits: 2 })}</span>
                                            <div className="text-[10px] text-muted-foreground">{new Date(inv.date).toLocaleDateString()}</div>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        )}
                    </GlassCard>
                )}
            </div>
        </div>
    );
}
