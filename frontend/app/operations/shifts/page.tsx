"use client";

import { useEffect, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import { GlassCard } from "@/components/ui/glass-card";
import { StyledSelect } from "@/components/ui/styled-select";
import { Modal } from "@/components/ui/modal";
import { InvoiceAutocomplete } from "@/components/ui/invoice-autocomplete";
import {
    getActiveShift,
    openShift,
    getShiftCashiers,
    changeShiftAttendant,
    CashierUser,
    getEAdvancesByShift,
    getEAdvanceSummary,
    createEAdvance,
    deleteEAdvance,
    getExpensesByShift,
    getExpenseShiftTotal,
    createExpense,
    deleteExpense,
    getUpiCompanies,
    createUpiCompany,
    getExpenseTypes,
    createExpenseType,
    getInvoicesByShift,
    getPaymentsByShift,
    getIncentivePaymentsByShift,
    getShiftClosingData,
    getExternalCashInflowsByShift,
    Shift,
    EAdvance,
    ShiftExpense,
    EAdvanceSummary,
    UpiCompany,
    ExpenseType,
    InvoiceBill,
    Payment,
    IncentivePayment,
    ExternalCashInflow,
    NozzleReadingRow,
} from "@/lib/api/station";
import { getStationExpensesByShift, getStationExpenseShiftTotal } from "@/lib/api/station/advances";
import { fetchAdvancesByShift, OperationalAdvance } from "@/app/operations/advances/advances-api";
import {
    Clock,
    Play,
    Square,
    Plus,
    Trash2,
    Banknote,
    CreditCard,
    Smartphone,
    Building2,
    FileText,
    Receipt,
    Wallet,
    ChevronDown,
    ChevronUp,
    DollarSign,
    ArrowDownLeft,
    ArrowUpRight,
    TrendingDown,
    User,
} from "lucide-react";
import { PermissionGate } from "@/components/permission-gate";
import { useAuth } from "@/lib/auth/auth-context";

// --- Constants ---

const TXN_TYPES = [
    { value: "UPI", label: "UPI", icon: Smartphone, color: "text-purple-500 bg-purple-500/10" },
    { value: "CARD", label: "Card", icon: CreditCard, color: "text-blue-500 bg-blue-500/10" },
    { value: "CHEQUE", label: "Cheque", icon: FileText, color: "text-amber-500 bg-amber-500/10" },
    { value: "BANK_TRANSFER", label: "Bank Transfer", icon: Building2, color: "text-cyan-500 bg-cyan-500/10" },
    { value: "CCMS", label: "CCMS", icon: Receipt, color: "text-pink-500 bg-pink-500/10" },
    { value: "EXPENSE", label: "Expense", icon: Wallet, color: "text-red-500 bg-red-500/10" },
];

function getTxnMeta(type: string) {
    return TXN_TYPES.find((t) => t.value === type) || { value: type, label: type, icon: Banknote, color: "text-gray-500 bg-gray-500/10" };
}

function formatDateTime(dt?: string) {
    if (!dt) return "-";
    return new Date(dt).toLocaleString("en-IN", {
        day: "2-digit", month: "short", year: "numeric",
        hour: "2-digit", minute: "2-digit",
    });
}

function formatCurrency(val?: number) {
    if (val == null) return "0.00";
    return val.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function abbreviateProducts(products: InvoiceBill["products"]): string {
    if (!products || products.length === 0) return "-";
    return products.map(p => {
        const name = p.productName?.length > 6 ? p.productName.substring(0, 6) : p.productName;
        return `${name}:${p.quantity}`;
    }).join(" ");
}

// --- Types ---

interface MeterReadingLocal {
    nozzleId: number;
    pumpName: string;
    nozzleName: string;
    productName: string;
    productPrice: number;
    openReading: number;
    closeReading: string;
    testQuantity: string;
}

export default function ShiftsPage() {
    const router = useRouter();
    const { user } = useAuth();
    const [activeShift, setActiveShift] = useState<Shift | null>(null);
    const [isLoading, setIsLoading] = useState(true);

    // Cashier selection for opening shift or changing attendant
    const [showCashierModal, setShowCashierModal] = useState(false);
    const [cashierModalMode, setCashierModalMode] = useState<"open" | "change">("open");
    const [cashiers, setCashiers] = useState<CashierUser[]>([]);
    const [selectedCashierId, setSelectedCashierId] = useState<number | "">("");
    const [cashierSearch, setCashierSearch] = useState("");
    const [openingShift, setOpeningShift] = useState(false);

    // All data
    const [invoices, setInvoices] = useState<InvoiceBill[]>([]);
    const [eAdvances, setEAdvances] = useState<EAdvance[]>([]);
    const [eAdvanceSummary, setEAdvanceSummary] = useState<EAdvanceSummary | null>(null);
    const [expenses, setExpenses] = useState<ShiftExpense[]>([]);
    const [expenseTotal, setExpenseTotal] = useState(0);
    const [opAdvances, setOpAdvances] = useState<OperationalAdvance[]>([]);
    const [payments, setPayments] = useState<Payment[]>([]);
    const [incentivePayments, setIncentivePayments] = useState<IncentivePayment[]>([]);
    const [cashInflows, setCashInflows] = useState<ExternalCashInflow[]>([]);
    const [nozzleReadings, setNozzleReadings] = useState<NozzleReadingRow[]>([]);

    // Meter readings (local only)
    const [meterReadings, setMeterReadings] = useState<MeterReadingLocal[]>([]);
    const [showMeterReadings, setShowMeterReadings] = useState(false);

    // Add transaction modal
    const [isAddModalOpen, setIsAddModalOpen] = useState(false);
    const [txnType, setTxnType] = useState("UPI");
    const [txnAmount, setTxnAmount] = useState("");
    const [txnRemarks, setTxnRemarks] = useState("");
    // UPI fields
    const [upiCompanies, setUpiCompanies] = useState<UpiCompany[]>([]);
    const [selectedUpiCompanyId, setSelectedUpiCompanyId] = useState("");
    const [newUpiCompanyName, setNewUpiCompanyName] = useState("");
    // Card fields
    const [cardBatchId, setCardBatchId] = useState("");
    const [cardTid, setCardTid] = useState("");
    const [cardCustomerName, setCardCustomerName] = useState("");
    const [cardCustomerPhone, setCardCustomerPhone] = useState("");
    const [cardBankName, setCardBankName] = useState("");
    const [cardLast4, setCardLast4] = useState("");
    // Cheque fields
    const [chequeBankName, setChequeBankName] = useState("");
    const [chequeInFavorOf, setChequeInFavorOf] = useState("");
    const [chequeNo, setChequeNo] = useState("");
    const [chequeDate, setChequeDate] = useState("");
    // Bank fields
    const [bankName, setBankName] = useState("");
    // CCMS fields
    const [ccmsNumber, setCcmsNumber] = useState("");
    // Expense fields
    const [expenseDescription, setExpenseDescription] = useState("");
    const [expenseTypes, setExpenseTypes] = useState<ExpenseType[]>([]);
    const [selectedExpenseTypeId, setSelectedExpenseTypeId] = useState("");
    const [newExpenseTypeName, setNewExpenseTypeName] = useState("");
    // Invoice linking
    const [linkedInvoice, setLinkedInvoice] = useState<{ id: number; billNo?: string; billType?: string; netAmount?: number } | null>(null);

    const viewingShift = activeShift;

    const loadAllData = useCallback(async (shiftId: number) => {
        try {
            const [
                invoicesData,
                eAdvData,
                eAdvSummaryData,
                expensesData,
                ,
                stationExpensesData,
                ,
                opAdvData,
                paymentsData,
                incentivesData,
                cashInflowsData,
                closingData,
            ] = await Promise.all([
                getInvoicesByShift(shiftId),
                getEAdvancesByShift(shiftId),
                getEAdvanceSummary(shiftId),
                getExpensesByShift(shiftId),
                getExpenseShiftTotal(shiftId),
                getStationExpensesByShift(shiftId).catch(() => []),
                getStationExpenseShiftTotal(shiftId).catch(() => 0),
                fetchAdvancesByShift(shiftId).catch(() => [] as OperationalAdvance[]),
                getPaymentsByShift(shiftId),
                getIncentivePaymentsByShift(shiftId),
                getExternalCashInflowsByShift(shiftId).catch(() => [] as ExternalCashInflow[]),
                getShiftClosingData(shiftId).catch(() => null),
            ]);

            // Merge both expense sources (Expense + StationExpense)
            const mergedExpenses = [
                ...expensesData,
                ...stationExpensesData.map((se: any) => ({
                    ...se,
                    expenseType: se.expenseType,
                    description: se.description,
                    remarks: se.description,
                })),
            ];

            setInvoices(invoicesData);
            setEAdvances(eAdvData);
            setEAdvanceSummary(eAdvSummaryData);
            setExpenses(mergedExpenses);
            const mergedTotal = mergedExpenses.reduce((sum: number, e: any) => sum + (Number(e.amount) || 0), 0);
            setExpenseTotal(mergedTotal);
            setOpAdvances(opAdvData);
            setPayments(paymentsData);
            setIncentivePayments(incentivesData);
            setCashInflows(cashInflowsData);

            if (closingData?.nozzleReadings) {
                setNozzleReadings(closingData.nozzleReadings);
                setMeterReadings(closingData.nozzleReadings.map(nr => ({
                    nozzleId: nr.nozzleId,
                    pumpName: nr.pumpName || "",
                    nozzleName: nr.nozzleName,
                    productName: nr.productName || "",
                    productPrice: nr.productPrice || 0,
                    openReading: nr.openMeterReading,
                    closeReading: "",
                    testQuantity: "",
                })));
            }
        } catch (err) {
            console.error("Failed to load shift data", err);
        }
    }, []);

    const loadData = useCallback(async () => {
        setIsLoading(true);
        try {
            const active = await getActiveShift();
            setActiveShift(active);
            if (active) {
                await loadAllData(active.id);
            }
        } catch (err) {
            console.error("Failed to load shift data", err);
        } finally {
            setIsLoading(false);
        }
    }, [loadAllData]);

    useEffect(() => {
        loadData();
    }, [loadData]);

    const showCashierSelection = async (mode: "open" | "change") => {
        try {
            const list = await getShiftCashiers();
            setCashiers(list);
            setSelectedCashierId(mode === "change" && activeShift?.attendant?.id ? activeShift.attendant.id : "");
            setCashierModalMode(mode);
            setCashierSearch("");
            setShowCashierModal(true);
        } catch (err: any) {
            alert(err.message || "Failed to load cashiers");
        }
    };

    const handleOpenShift = async () => {
        const isOwnerOrAdmin = user?.role === "OWNER" || user?.role === "ADMIN";
        if (isOwnerOrAdmin) {
            showCashierSelection("open");
        } else {
            try {
                const shift = await openShift({});
                setActiveShift(shift);
                await loadAllData(shift.id);
            } catch (err: any) {
                alert(err.message || "Failed to open shift");
            }
        }
    };

    const handleConfirmCashierAction = async () => {
        if (!selectedCashierId) {
            alert("Please select a cashier");
            return;
        }
        setOpeningShift(true);
        try {
            if (cashierModalMode === "open") {
                const shift = await openShift({ attendant: { id: Number(selectedCashierId), name: "" } } as any);
                setActiveShift(shift);
                await loadAllData(shift.id);
            } else if (activeShift) {
                const updated = await changeShiftAttendant(activeShift.id, Number(selectedCashierId));
                setActiveShift(updated);
            }
            setShowCashierModal(false);
        } catch (err: any) {
            alert(err.message || "Failed to update shift");
        } finally {
            setOpeningShift(false);
        }
    };

    const handleCloseShift = () => {
        if (!activeShift) return;
        router.push(`/operations/shifts/close/${activeShift.id}`);
    };

    const resetForm = () => {
        setTxnType("UPI");
        setTxnAmount("");
        setTxnRemarks("");
        setSelectedUpiCompanyId("");
        setNewUpiCompanyName("");
        setCardBatchId(""); setCardTid(""); setCardCustomerName(""); setCardCustomerPhone(""); setCardBankName(""); setCardLast4("");
        setChequeBankName(""); setChequeInFavorOf(""); setChequeNo(""); setChequeDate("");
        setBankName("");
        setCcmsNumber("");
        setExpenseDescription(""); setSelectedExpenseTypeId(""); setNewExpenseTypeName("");
        setLinkedInvoice(null);
    };

    const handleOpenAddModal = async () => {
        resetForm();
        const [upi, exp] = await Promise.all([getUpiCompanies(), getExpenseTypes()]);
        setUpiCompanies(upi);
        setExpenseTypes(exp);
        setIsAddModalOpen(true);
    };

    const handleAddTransaction = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!viewingShift) return;

        try {
            if (txnType === "EXPENSE") {
                const payload: Partial<ShiftExpense> = {
                    shiftId: viewingShift.id,
                    amount: Number(txnAmount),
                    description: expenseDescription || undefined,
                    remarks: txnRemarks || undefined,
                };
                if (newExpenseTypeName) {
                    const created = await createExpenseType({ name: newExpenseTypeName });
                    payload.expenseType = created;
                } else if (selectedExpenseTypeId) {
                    payload.expenseType = { id: Number(selectedExpenseTypeId), name: "" };
                }
                await createExpense(payload);
            } else {
                const payload: Partial<EAdvance> = {
                    advanceType: txnType,
                    shiftId: viewingShift.id,
                    amount: Number(txnAmount),
                    remarks: txnRemarks || undefined,
                };

                if (txnType === "UPI") {
                    if (newUpiCompanyName) {
                        const created = await createUpiCompany({ companyName: newUpiCompanyName });
                        payload.upiCompany = created;
                    } else if (selectedUpiCompanyId) {
                        payload.upiCompany = { id: Number(selectedUpiCompanyId), companyName: "" };
                    }
                } else if (txnType === "CARD") {
                    payload.batchId = cardBatchId || undefined;
                    payload.tid = cardTid || undefined;
                    payload.customerName = cardCustomerName || undefined;
                    payload.customerPhone = cardCustomerPhone || undefined;
                    payload.bankName = cardBankName || undefined;
                    payload.cardLast4Digit = cardLast4 || undefined;
                } else if (txnType === "CHEQUE") {
                    payload.bankName = chequeBankName || undefined;
                    payload.inFavorOf = chequeInFavorOf || undefined;
                    payload.chequeNo = chequeNo || undefined;
                    payload.chequeDate = chequeDate || undefined;
                } else if (txnType === "BANK_TRANSFER") {
                    payload.bankName = bankName || undefined;
                } else if (txnType === "CCMS") {
                    payload.ccmsNumber = ccmsNumber || undefined;
                }

                if (linkedInvoice) {
                    payload.invoiceBill = { id: linkedInvoice.id };
                }

                await createEAdvance(payload);
            }

            setIsAddModalOpen(false);
            await loadAllData(viewingShift.id);
        } catch (err: any) {
            alert(err.message || "Failed to add transaction");
        }
    };

    const handleDeleteEAdvance = async (id: number) => {
        if (!confirm("Delete this entry?")) return;
        try {
            await deleteEAdvance(id);
            if (viewingShift) await loadAllData(viewingShift.id);
        } catch {
            alert("Failed to delete entry");
        }
    };

    const handleDeleteExpense = async (id: number) => {
        if (!confirm("Delete this expense?")) return;
        try {
            await deleteExpense(id);
            if (viewingShift) await loadAllData(viewingShift.id);
        } catch {
            alert("Failed to delete expense");
        }
    };

    // Computed values
    const creditInvoices = invoices.filter(inv => inv.billType === "CREDIT");
    const cashInvoices = invoices.filter(inv => inv.billType === "CASH");

    const totalRevenue = invoices.reduce((sum, inv) => sum + (inv.netAmount || 0), 0);
    const creditBillTotal = creditInvoices.reduce((sum, inv) => sum + (inv.netAmount || 0), 0);

    const eAdvanceTotal = eAdvanceSummary?.total || 0;
    const opAdvanceTotal = opAdvances.reduce((sum, a) => sum + (a.amount || 0), 0);
    const incentiveTotal = incentivePayments.reduce((sum, ip) => sum + (ip.amount || 0), 0);
    const totalAdvances = eAdvanceTotal + opAdvanceTotal + expenseTotal + incentiveTotal;

    const billPaymentTotal = payments.filter(p => p.invoiceBill).reduce((sum, p) => sum + (p.amount || 0), 0);
    const statementPaymentTotal = payments.filter(p => p.statement).reduce((sum, p) => sum + (p.amount || 0), 0);
    const totalPayments = billPaymentTotal + statementPaymentTotal;
    const cashInflowTotal = cashInflows.reduce((sum, ci) => sum + (ci.amount || 0), 0);

    const cashBalance = totalRevenue - creditBillTotal - totalAdvances + totalPayments + cashInflowTotal;

    const isViewingActive = viewingShift?.status === "OPEN";

    // Meter reading handlers
    const updateMeterReading = (index: number, field: "closeReading" | "testQuantity", value: string) => {
        setMeterReadings(prev => {
            const updated = [...prev];
            updated[index] = { ...updated[index], [field]: value };
            return updated;
        });
    };

    const computeSales = (mr: MeterReadingLocal) => {
        const close = parseFloat(mr.closeReading);
        if (isNaN(close)) return null;
        const test = parseFloat(mr.testQuantity) || 0;
        return close - mr.openReading - test;
    };

    const computeAmount = (mr: MeterReadingLocal) => {
        const sales = computeSales(mr);
        if (sales === null) return null;
        return sales * mr.productPrice;
    };

    // Group meter readings by product for totals
    const productTotals = meterReadings.reduce((acc, mr) => {
        const sales = computeSales(mr);
        const amount = computeAmount(mr);
        if (sales !== null && amount !== null) {
            if (!acc[mr.productName]) acc[mr.productName] = { sales: 0, amount: 0 };
            acc[mr.productName].sales += sales;
            acc[mr.productName].amount += amount;
        }
        return acc;
    }, {} as Record<string, { sales: number; amount: number }>);

    const meterGrandTotal = Object.values(productTotals).reduce((sum, pt) => sum + pt.amount, 0);

    return (
        <div className="p-4 md:p-6 min-h-screen bg-background transition-colors duration-300">
            <div className="max-w-7xl mx-auto">
                {/* Header */}
                <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 mb-6">
                    <div>
                        <h1 className="text-3xl md:text-4xl font-bold text-foreground tracking-tight">
                            Shift <span className="text-gradient">Register</span>
                        </h1>
                        <p className="text-muted-foreground mt-1 text-sm">
                            {activeShift
                                ? `Shift #${activeShift.id}${activeShift.attendant?.name ? ` · ${activeShift.attendant.name}` : ""} · Started ${formatDateTime(activeShift.startTime)}`
                                : "No active shift"}
                        </p>
                    </div>
                    <div className="flex items-center gap-3">
                        {activeShift ? (
                            <>
                                <div className="flex items-center gap-2 px-4 py-2 bg-green-500/10 border border-green-500/20 rounded-xl">
                                    <div className="w-2 h-2 rounded-full bg-green-500 animate-pulse" />
                                    <span className="text-sm font-medium text-green-500">
                                        Active
                                    </span>
                                </div>
                                <PermissionGate permission="SHIFT_UPDATE">
                                    <button
                                        onClick={() => showCashierSelection("change")}
                                        className="px-4 py-2.5 rounded-xl font-medium flex items-center gap-2 bg-primary/10 text-primary border border-primary/20 hover:bg-primary/20 transition-colors text-sm"
                                    >
                                        <User className="w-4 h-4" />
                                        Change Cashier
                                    </button>
                                </PermissionGate>
                                <PermissionGate permission="SHIFT_UPDATE">
                                    <button
                                        onClick={handleCloseShift}
                                        className="px-5 py-2.5 rounded-xl font-medium flex items-center gap-2 bg-red-500/10 text-red-500 border border-red-500/20 hover:bg-red-500/20 transition-colors"
                                    >
                                        <Square className="w-4 h-4" />
                                        Close Shift
                                    </button>
                                </PermissionGate>
                            </>
                        ) : (
                            <PermissionGate permission="SHIFT_CREATE">
                                <button
                                    onClick={handleOpenShift}
                                    className="btn-gradient px-6 py-3 rounded-xl font-medium flex items-center gap-2 shadow-lg hover:shadow-xl transition-all"
                                >
                                    <Play className="w-5 h-5" />
                                    Open New Shift
                                </button>
                            </PermissionGate>
                        )}
                    </div>
                </div>

                {isLoading ? (
                    <div className="flex flex-col items-center justify-center py-20 text-muted-foreground">
                        <div className="w-12 h-12 border-4 border-primary/20 border-t-primary rounded-full animate-spin mb-4" />
                        <p className="animate-pulse">Loading shift data...</p>
                    </div>
                ) : !viewingShift ? (
                    <div className="text-center py-20 bg-black/5 dark:bg-white/5 rounded-2xl border border-dashed border-border">
                        <Clock className="w-16 h-16 mx-auto text-muted-foreground mb-4 opacity-50" />
                        <h3 className="text-xl font-semibold text-foreground mb-2">No Active Shift</h3>
                        <p className="text-muted-foreground mb-6 max-w-md mx-auto">
                            Open a new shift to start recording transactions.
                        </p>
                        <PermissionGate permission="SHIFT_CREATE">
                            <button
                                onClick={handleOpenShift}
                                className="bg-primary/10 text-primary hover:bg-primary/20 px-6 py-2 rounded-xl font-medium transition-colors"
                            >
                                Open Shift
                            </button>
                        </PermissionGate>
                    </div>
                ) : (
                    <>
                        {/* Summary Row - 4 cards */}
                        <div className="grid grid-cols-2 lg:grid-cols-4 gap-3 mb-6">
                            <SummaryCard
                                label="Total Revenue"
                                value={totalRevenue}
                                color="text-green-500"
                                bgColor="bg-green-500/10"
                                icon={DollarSign}
                            />
                            <SummaryCard
                                label="Total Advances"
                                value={totalAdvances}
                                color="text-orange-500"
                                bgColor="bg-orange-500/10"
                                icon={ArrowUpRight}
                            />
                            <SummaryCard
                                label="Cash Balance"
                                value={cashBalance}
                                color="text-blue-500"
                                bgColor="bg-blue-500/10"
                                icon={Wallet}
                            />
                            <SummaryCard
                                label="Credit Bills"
                                value={creditBillTotal}
                                color="text-red-500"
                                bgColor="bg-red-500/10"
                                icon={TrendingDown}
                            />
                        </div>

                        {/* Two-Column Layout */}
                        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4 mb-6">
                            {/* LEFT COLUMN - Money OUT */}
                            <div className="space-y-4">
                                {/* Credit Invoices */}
                                <GlassCard className="p-4">
                                    <div className="flex justify-between items-center mb-3">
                                        <h3 className="text-sm font-semibold text-muted-foreground">CREDIT INVOICES ({creditInvoices.length})</h3>
                                        <span className="text-sm font-bold text-red-500">{formatCurrency(creditBillTotal)}</span>
                                    </div>
                                    {creditInvoices.length === 0 ? (
                                        <p className="text-xs text-muted-foreground py-2">No credit invoices</p>
                                    ) : (
                                        <div className="overflow-x-auto">
                                            <table className="w-full text-xs">
                                                <thead>
                                                    <tr className="border-b border-border/30">
                                                        <th className="text-left py-1.5 text-muted-foreground font-medium">Customer</th>
                                                        <th className="text-left py-1.5 text-muted-foreground font-medium">Bill#</th>
                                                        <th className="text-left py-1.5 text-muted-foreground font-medium">Products</th>
                                                        <th className="text-right py-1.5 text-muted-foreground font-medium">Amount</th>
                                                    </tr>
                                                </thead>
                                                <tbody className="divide-y divide-border/20">
                                                    {creditInvoices.map(inv => (
                                                        <tr key={inv.id} className="hover:bg-white/5">
                                                            <td className="py-1.5 text-foreground">{inv.customer?.name || "-"}</td>
                                                            <td className="py-1.5 text-foreground font-mono">{inv.billNo || "-"}</td>
                                                            <td className="py-1.5 text-muted-foreground">{abbreviateProducts(inv.products)}</td>
                                                            <td className="py-1.5 text-right font-medium text-foreground">{formatCurrency(inv.netAmount)}</td>
                                                        </tr>
                                                    ))}
                                                </tbody>
                                            </table>
                                        </div>
                                    )}
                                </GlassCard>

                                {/* E-Advances */}
                                <GlassCard className="p-4">
                                    <div className="flex justify-between items-center mb-3">
                                        <h3 className="text-sm font-semibold text-muted-foreground">E-ADVANCES ({eAdvances.length})</h3>
                                        <div className="flex items-center gap-2">
                                            <span className="text-sm font-bold text-blue-500">{formatCurrency(eAdvanceTotal)}</span>
                                            {isViewingActive && (
                                                <PermissionGate permission="SHIFT_CREATE">
                                                    <button
                                                        onClick={handleOpenAddModal}
                                                        className="text-xs px-2 py-1 rounded-lg bg-primary/10 text-primary hover:bg-primary/20 transition-colors flex items-center gap-1"
                                                    >
                                                        <Plus className="w-3 h-3" />
                                                        Add Entry
                                                    </button>
                                                </PermissionGate>
                                            )}
                                        </div>
                                    </div>
                                    {eAdvances.length === 0 ? (
                                        <p className="text-xs text-muted-foreground py-2">No e-advance entries</p>
                                    ) : (
                                        <div className="overflow-x-auto">
                                            <table className="w-full text-xs">
                                                <thead>
                                                    <tr className="border-b border-border/30">
                                                        <th className="text-left py-1.5 text-muted-foreground font-medium">Type</th>
                                                        <th className="text-left py-1.5 text-muted-foreground font-medium">Details</th>
                                                        <th className="text-right py-1.5 text-muted-foreground font-medium">Amount</th>
                                                        {isViewingActive && <th className="w-8"></th>}
                                                    </tr>
                                                </thead>
                                                <tbody className="divide-y divide-border/20">
                                                    {eAdvances.map(ea => {
                                                        const meta = getTxnMeta(ea.advanceType);
                                                        const Icon = meta.icon;
                                                        const details = getEAdvanceDetails(ea);
                                                        return (
                                                            <tr key={ea.id} className="hover:bg-white/5 group">
                                                                <td className="py-1.5">
                                                                    <span className={`inline-flex items-center gap-1 px-1.5 py-0.5 rounded-md text-[10px] font-medium ${meta.color}`}>
                                                                        <Icon className="w-3 h-3" />
                                                                        {meta.label}
                                                                    </span>
                                                                </td>
                                                                <td className="py-1.5 text-muted-foreground truncate max-w-[160px]">{details}</td>
                                                                <td className="py-1.5 text-right font-medium text-foreground">{formatCurrency(ea.amount)}</td>
                                                                {isViewingActive && (
                                                                    <td className="py-1.5 text-center">
                                                                        <PermissionGate permission="SHIFT_DELETE">
                                                                            <button
                                                                                onClick={() => handleDeleteEAdvance(ea.id!)}
                                                                                className="p-1 rounded hover:bg-red-500/10 text-muted-foreground hover:text-red-500 opacity-0 group-hover:opacity-100 transition-all"
                                                                            >
                                                                                <Trash2 className="w-3 h-3" />
                                                                            </button>
                                                                        </PermissionGate>
                                                                    </td>
                                                                )}
                                                            </tr>
                                                        );
                                                    })}
                                                </tbody>
                                            </table>
                                        </div>
                                    )}
                                </GlassCard>

                                {/* Operational Advances */}
                                <GlassCard className="p-4">
                                    <div className="flex justify-between items-center mb-3">
                                        <h3 className="text-sm font-semibold text-muted-foreground">OPERATIONAL ADVANCES ({opAdvances.length})</h3>
                                        <span className="text-sm font-bold text-purple-500">{formatCurrency(opAdvanceTotal)}</span>
                                    </div>
                                    {opAdvances.length === 0 ? (
                                        <p className="text-xs text-muted-foreground py-2">No operational advances</p>
                                    ) : (
                                        <div className="overflow-x-auto">
                                            <table className="w-full text-xs">
                                                <thead>
                                                    <tr className="border-b border-border/30">
                                                        <th className="text-left py-1.5 text-muted-foreground font-medium">Type</th>
                                                        <th className="text-left py-1.5 text-muted-foreground font-medium">Recipient</th>
                                                        <th className="text-right py-1.5 text-muted-foreground font-medium">Amount</th>
                                                    </tr>
                                                </thead>
                                                <tbody className="divide-y divide-border/20">
                                                    {opAdvances.map(oa => (
                                                        <tr key={oa.id} className="hover:bg-white/5">
                                                            <td className="py-1.5">
                                                                <span className="text-[10px] px-1.5 py-0.5 rounded-md bg-purple-500/10 text-purple-500 font-medium">
                                                                    {oa.advanceType?.replace(/_/g, " ") || "-"}
                                                                </span>
                                                            </td>
                                                            <td className="py-1.5 text-foreground">{oa.recipientName || oa.employee?.name || "-"}</td>
                                                            <td className="py-1.5 text-right font-medium text-foreground">{formatCurrency(oa.amount)}</td>
                                                        </tr>
                                                    ))}
                                                </tbody>
                                            </table>
                                        </div>
                                    )}
                                </GlassCard>

                                {/* Expenses */}
                                <GlassCard className="p-4">
                                    <div className="flex justify-between items-center mb-3">
                                        <h3 className="text-sm font-semibold text-muted-foreground">EXPENSES ({expenses.length})</h3>
                                        <span className="text-sm font-bold text-red-500">{formatCurrency(expenseTotal)}</span>
                                    </div>
                                    {expenses.length === 0 ? (
                                        <p className="text-xs text-muted-foreground py-2">No expenses</p>
                                    ) : (
                                        <div className="overflow-x-auto">
                                            <table className="w-full text-xs">
                                                <thead>
                                                    <tr className="border-b border-border/30">
                                                        <th className="text-left py-1.5 text-muted-foreground font-medium">Type</th>
                                                        <th className="text-left py-1.5 text-muted-foreground font-medium">Description</th>
                                                        <th className="text-right py-1.5 text-muted-foreground font-medium">Amount</th>
                                                        {isViewingActive && <th className="w-8"></th>}
                                                    </tr>
                                                </thead>
                                                <tbody className="divide-y divide-border/20">
                                                    {expenses.map(exp => (
                                                        <tr key={exp.id} className="hover:bg-white/5 group">
                                                            <td className="py-1.5">
                                                                <span className="text-[10px] px-1.5 py-0.5 rounded-md bg-red-500/10 text-red-500 font-medium">
                                                                    {exp.expenseType?.name || "Expense"}
                                                                </span>
                                                            </td>
                                                            <td className="py-1.5 text-muted-foreground truncate max-w-[160px]">{exp.description || exp.remarks || "-"}</td>
                                                            <td className="py-1.5 text-right font-medium text-foreground">{formatCurrency(exp.amount)}</td>
                                                            {isViewingActive && (
                                                                <td className="py-1.5 text-center">
                                                                    <PermissionGate permission="SHIFT_DELETE">
                                                                        <button
                                                                            onClick={() => handleDeleteExpense(exp.id!)}
                                                                            className="p-1 rounded hover:bg-red-500/10 text-muted-foreground hover:text-red-500 opacity-0 group-hover:opacity-100 transition-all"
                                                                        >
                                                                            <Trash2 className="w-3 h-3" />
                                                                        </button>
                                                                    </PermissionGate>
                                                                </td>
                                                            )}
                                                        </tr>
                                                    ))}
                                                </tbody>
                                            </table>
                                        </div>
                                    )}
                                </GlassCard>

                                {/* Incentives */}
                                <GlassCard className="p-4">
                                    <div className="flex justify-between items-center mb-3">
                                        <h3 className="text-sm font-semibold text-muted-foreground">INCENTIVES ({incentivePayments.length})</h3>
                                        <span className="text-sm font-bold text-amber-500">{formatCurrency(incentiveTotal)}</span>
                                    </div>
                                    {incentivePayments.length === 0 ? (
                                        <p className="text-xs text-muted-foreground py-2">No incentive payments</p>
                                    ) : (
                                        <div className="overflow-x-auto">
                                            <table className="w-full text-xs">
                                                <thead>
                                                    <tr className="border-b border-border/30">
                                                        <th className="text-left py-1.5 text-muted-foreground font-medium">Customer</th>
                                                        <th className="text-left py-1.5 text-muted-foreground font-medium">Description</th>
                                                        <th className="text-right py-1.5 text-muted-foreground font-medium">Amount</th>
                                                    </tr>
                                                </thead>
                                                <tbody className="divide-y divide-border/20">
                                                    {incentivePayments.map(ip => (
                                                        <tr key={ip.id} className="hover:bg-white/5">
                                                            <td className="py-1.5 text-foreground">{ip.customer?.name || "-"}</td>
                                                            <td className="py-1.5 text-muted-foreground truncate max-w-[160px]">{ip.description || "-"}</td>
                                                            <td className="py-1.5 text-right font-medium text-foreground">{formatCurrency(ip.amount)}</td>
                                                        </tr>
                                                    ))}
                                                </tbody>
                                            </table>
                                        </div>
                                    )}
                                </GlassCard>
                            </div>

                            {/* RIGHT COLUMN - Money IN */}
                            <div className="space-y-4">
                                {/* Cash Invoices */}
                                <GlassCard className="p-4">
                                    <div className="flex justify-between items-center mb-3">
                                        <h3 className="text-sm font-semibold text-muted-foreground">CASH INVOICES ({cashInvoices.length})</h3>
                                        <span className="text-sm font-bold text-green-500">
                                            {formatCurrency(cashInvoices.reduce((s, i) => s + (i.netAmount || 0), 0))}
                                        </span>
                                    </div>
                                    {cashInvoices.length === 0 ? (
                                        <p className="text-xs text-muted-foreground py-2">No cash invoices</p>
                                    ) : (
                                        <div className="overflow-x-auto">
                                            <table className="w-full text-xs">
                                                <thead>
                                                    <tr className="border-b border-border/30">
                                                        <th className="text-left py-1.5 text-muted-foreground font-medium">Bill#</th>
                                                        <th className="text-left py-1.5 text-muted-foreground font-medium">Products</th>
                                                        <th className="text-left py-1.5 text-muted-foreground font-medium">Mode</th>
                                                        <th className="text-right py-1.5 text-muted-foreground font-medium">Amount</th>
                                                    </tr>
                                                </thead>
                                                <tbody className="divide-y divide-border/20">
                                                    {cashInvoices.map(inv => (
                                                        <tr key={inv.id} className="hover:bg-white/5">
                                                            <td className="py-1.5 text-foreground font-mono">{inv.billNo || "-"}</td>
                                                            <td className="py-1.5 text-muted-foreground">{abbreviateProducts(inv.products)}</td>
                                                            <td className="py-1.5">
                                                                <span className="text-[10px] px-1.5 py-0.5 rounded-md bg-white/5 border border-border text-muted-foreground">
                                                                    {inv.paymentMode || "CASH"}
                                                                </span>
                                                            </td>
                                                            <td className="py-1.5 text-right font-medium text-foreground">{formatCurrency(inv.netAmount)}</td>
                                                        </tr>
                                                    ))}
                                                </tbody>
                                            </table>
                                        </div>
                                    )}
                                </GlassCard>

                                {/* Bill / Statement Payments */}
                                <GlassCard className="p-4">
                                    <div className="flex justify-between items-center mb-3">
                                        <h3 className="text-sm font-semibold text-muted-foreground">BILL/STATEMENT PAYMENTS ({payments.length})</h3>
                                        <span className="text-sm font-bold text-emerald-500">{formatCurrency(totalPayments)}</span>
                                    </div>
                                    {payments.length === 0 ? (
                                        <p className="text-xs text-muted-foreground py-2">No payments received</p>
                                    ) : (
                                        <div className="overflow-x-auto">
                                            <table className="w-full text-xs">
                                                <thead>
                                                    <tr className="border-b border-border/30">
                                                        <th className="text-left py-1.5 text-muted-foreground font-medium">Type</th>
                                                        <th className="text-left py-1.5 text-muted-foreground font-medium">Customer</th>
                                                        <th className="text-left py-1.5 text-muted-foreground font-medium">Mode</th>
                                                        <th className="text-right py-1.5 text-muted-foreground font-medium">Amount</th>
                                                    </tr>
                                                </thead>
                                                <tbody className="divide-y divide-border/20">
                                                    {payments.map(p => {
                                                        const type = p.invoiceBill ? "BILL" : p.statement ? "STMT" : "OTHER";
                                                        return (
                                                            <tr key={p.id} className="hover:bg-white/5">
                                                                <td className="py-1.5">
                                                                    <span className={`text-[10px] px-1.5 py-0.5 rounded-md font-medium ${
                                                                        type === "BILL" ? "bg-blue-500/10 text-blue-500" : "bg-teal-500/10 text-teal-500"
                                                                    }`}>
                                                                        {type}
                                                                    </span>
                                                                </td>
                                                                <td className="py-1.5 text-foreground">{p.customer?.name || "-"}</td>
                                                                <td className="py-1.5">
                                                                    <span className="text-[10px] px-1.5 py-0.5 rounded-md bg-white/5 border border-border text-muted-foreground">
                                                                        {p.paymentMode || "-"}
                                                                    </span>
                                                                </td>
                                                                <td className="py-1.5 text-right font-medium text-foreground">{formatCurrency(p.amount)}</td>
                                                            </tr>
                                                        );
                                                    })}
                                                </tbody>
                                            </table>
                                        </div>
                                    )}
                                </GlassCard>

                                {/* Cash Inflows */}
                                <GlassCard className="p-4">
                                    <div className="flex justify-between items-center mb-3">
                                        <h3 className="text-sm font-semibold text-muted-foreground">CASH INFLOWS ({cashInflows.length})</h3>
                                        <span className="text-sm font-bold text-cyan-500">{formatCurrency(cashInflowTotal)}</span>
                                    </div>
                                    {cashInflows.length === 0 ? (
                                        <p className="text-xs text-muted-foreground py-2">No external cash inflows</p>
                                    ) : (
                                        <div className="overflow-x-auto">
                                            <table className="w-full text-xs">
                                                <thead>
                                                    <tr className="border-b border-border/30">
                                                        <th className="text-left py-1.5 text-muted-foreground font-medium">Source</th>
                                                        <th className="text-right py-1.5 text-muted-foreground font-medium">Amount</th>
                                                    </tr>
                                                </thead>
                                                <tbody className="divide-y divide-border/20">
                                                    {cashInflows.map(ci => (
                                                        <tr key={ci.id} className="hover:bg-white/5">
                                                            <td className="py-1.5 text-foreground">{ci.source || ci.purpose || "-"}</td>
                                                            <td className="py-1.5 text-right font-medium text-foreground">{formatCurrency(ci.amount)}</td>
                                                        </tr>
                                                    ))}
                                                </tbody>
                                            </table>
                                        </div>
                                    )}
                                </GlassCard>
                            </div>
                        </div>

                        {/* Collapsible Meter Readings */}
                        {meterReadings.length > 0 && (
                            <GlassCard className="p-4 mb-6">
                                <button
                                    onClick={() => setShowMeterReadings(!showMeterReadings)}
                                    className="w-full flex justify-between items-center"
                                >
                                    <h3 className="text-sm font-semibold text-muted-foreground">
                                        NOZZLE METER READINGS ({meterReadings.length})
                                    </h3>
                                    <div className="flex items-center gap-2">
                                        {meterGrandTotal > 0 && (
                                            <span className="text-sm font-bold text-foreground">{formatCurrency(meterGrandTotal)}</span>
                                        )}
                                        {showMeterReadings ? <ChevronUp className="w-4 h-4 text-muted-foreground" /> : <ChevronDown className="w-4 h-4 text-muted-foreground" />}
                                    </div>
                                </button>

                                {showMeterReadings && (
                                    <div className="mt-4">
                                        <p className="text-[10px] text-muted-foreground mb-3 italic">
                                            Local calculator only -- values are not saved to the database.
                                        </p>
                                        <div className="overflow-x-auto">
                                            <table className="w-full text-xs">
                                                <thead>
                                                    <tr className="border-b border-border/30">
                                                        <th className="text-left py-1.5 text-muted-foreground font-medium">Pump</th>
                                                        <th className="text-left py-1.5 text-muted-foreground font-medium">Nozzle</th>
                                                        <th className="text-left py-1.5 text-muted-foreground font-medium">Product</th>
                                                        <th className="text-right py-1.5 text-muted-foreground font-medium">Open</th>
                                                        <th className="text-center py-1.5 text-muted-foreground font-medium">Close</th>
                                                        <th className="text-center py-1.5 text-muted-foreground font-medium">Test</th>
                                                        <th className="text-right py-1.5 text-muted-foreground font-medium">Sales</th>
                                                        <th className="text-right py-1.5 text-muted-foreground font-medium">Amount</th>
                                                    </tr>
                                                </thead>
                                                <tbody className="divide-y divide-border/20">
                                                    {meterReadings.map((mr, idx) => {
                                                        const sales = computeSales(mr);
                                                        const amount = computeAmount(mr);
                                                        return (
                                                            <tr key={mr.nozzleId}>
                                                                <td className="py-1.5 text-foreground">{mr.pumpName}</td>
                                                                <td className="py-1.5 text-foreground">{mr.nozzleName}</td>
                                                                <td className="py-1.5 text-muted-foreground">{mr.productName}</td>
                                                                <td className="py-1.5 text-right font-mono text-foreground">{mr.openReading.toFixed(2)}</td>
                                                                <td className="py-1.5 text-center">
                                                                    <input
                                                                        type="number"
                                                                        step="0.01"
                                                                        value={mr.closeReading}
                                                                        onChange={(e) => updateMeterReading(idx, "closeReading", e.target.value)}
                                                                        className="w-24 bg-background border border-border rounded px-2 py-1 text-foreground font-mono text-right focus:outline-none focus:ring-1 focus:ring-primary/50"
                                                                        placeholder="0.00"
                                                                    />
                                                                </td>
                                                                <td className="py-1.5 text-center">
                                                                    <input
                                                                        type="number"
                                                                        step="0.01"
                                                                        value={mr.testQuantity}
                                                                        onChange={(e) => updateMeterReading(idx, "testQuantity", e.target.value)}
                                                                        className="w-20 bg-background border border-border rounded px-2 py-1 text-foreground font-mono text-right focus:outline-none focus:ring-1 focus:ring-primary/50"
                                                                        placeholder="0"
                                                                    />
                                                                </td>
                                                                <td className="py-1.5 text-right font-mono text-foreground">
                                                                    {sales !== null ? sales.toFixed(2) : "-"}
                                                                </td>
                                                                <td className="py-1.5 text-right font-mono font-medium text-foreground">
                                                                    {amount !== null ? formatCurrency(amount) : "-"}
                                                                </td>
                                                            </tr>
                                                        );
                                                    })}
                                                </tbody>
                                                {Object.keys(productTotals).length > 0 && (
                                                    <tfoot className="border-t border-border">
                                                        {Object.entries(productTotals).map(([product, totals]) => (
                                                            <tr key={product} className="text-muted-foreground">
                                                                <td colSpan={6} className="py-1.5 text-right text-xs font-medium">{product} Total:</td>
                                                                <td className="py-1.5 text-right font-mono text-xs">{totals.sales.toFixed(2)}</td>
                                                                <td className="py-1.5 text-right font-mono text-xs font-medium">{formatCurrency(totals.amount)}</td>
                                                            </tr>
                                                        ))}
                                                        <tr className="font-bold text-foreground">
                                                            <td colSpan={6} className="py-2 text-right text-sm">Grand Total:</td>
                                                            <td className="py-2 text-right font-mono text-sm">
                                                                {Object.values(productTotals).reduce((s, pt) => s + pt.sales, 0).toFixed(2)}
                                                            </td>
                                                            <td className="py-2 text-right font-mono text-sm">{formatCurrency(meterGrandTotal)}</td>
                                                        </tr>
                                                    </tfoot>
                                                )}
                                            </table>
                                        </div>
                                    </div>
                                )}
                            </GlassCard>
                        )}
                    </>
                )}

                {/* Link to Shift History */}
                <div className="mt-6 text-center">
                    <button
                        onClick={() => router.push("/operations/shifts/history")}
                        className="text-sm text-primary hover:text-primary/80 transition-colors font-medium"
                    >
                        View All Past Shifts →
                    </button>
                </div>
            </div>

            {/* Add Transaction Modal (preserved from original) */}
            <Modal
                isOpen={isAddModalOpen}
                onClose={() => setIsAddModalOpen(false)}
                title="Add Shift Entry"
            >
                <form onSubmit={handleAddTransaction} className="space-y-4">
                    {/* Transaction Type Selector */}
                    <div>
                        <label className="block text-sm font-medium text-foreground mb-2">Entry Type</label>
                        <div className="grid grid-cols-3 gap-2">
                            {TXN_TYPES.map((t) => {
                                const Icon = t.icon;
                                return (
                                    <button
                                        key={t.value}
                                        type="button"
                                        onClick={() => setTxnType(t.value)}
                                        className={`flex flex-col items-center gap-1.5 p-3 rounded-xl border transition-all text-xs font-medium ${
                                            txnType === t.value
                                                ? 'border-primary bg-primary/10 text-primary'
                                                : 'border-border bg-card text-muted-foreground hover:border-primary/30'
                                        }`}
                                    >
                                        <Icon className="w-4 h-4" />
                                        {t.label}
                                    </button>
                                );
                            })}
                        </div>
                    </div>

                    {/* Amount */}
                    <div>
                        <label className="block text-sm font-medium text-foreground mb-1.5">
                            Amount <span className="text-red-500">*</span>
                        </label>
                        <input
                            type="number"
                            step="0.01"
                            min="0"
                            required
                            value={txnAmount}
                            onChange={(e) => setTxnAmount(e.target.value)}
                            className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground font-mono focus:outline-none focus:ring-2 focus:ring-primary/50"
                            placeholder="0.00"
                        />
                    </div>

                    {/* Type-specific fields */}
                    {txnType === "UPI" && (
                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5">UPI Company</label>
                            <StyledSelect
                                value={selectedUpiCompanyId}
                                onChange={(val) => { setSelectedUpiCompanyId(val); setNewUpiCompanyName(""); }}
                                options={[
                                    { value: "", label: "Select or add new..." },
                                    ...upiCompanies.map((u) => ({ value: String(u.id), label: u.companyName })),
                                ]}
                                className="mb-2"
                            />
                            {!selectedUpiCompanyId && (
                                <input
                                    type="text"
                                    value={newUpiCompanyName}
                                    onChange={(e) => setNewUpiCompanyName(e.target.value)}
                                    className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                                    placeholder="New UPI company name..."
                                />
                            )}
                        </div>
                    )}

                    {txnType === "CARD" && (
                        <div className="grid grid-cols-2 gap-3">
                            <InputField label="Bank Name" value={cardBankName} onChange={setCardBankName} placeholder="e.g. HDFC" />
                            <InputField label="Card Last 4 Digits" value={cardLast4} onChange={setCardLast4} placeholder="1234" />
                            <InputField label="Batch ID" value={cardBatchId} onChange={setCardBatchId} placeholder="Batch ID" />
                            <InputField label="Terminal ID (TID)" value={cardTid} onChange={setCardTid} placeholder="TID" />
                            <InputField label="Customer Name" value={cardCustomerName} onChange={setCardCustomerName} placeholder="Name" />
                            <InputField label="Customer Phone" value={cardCustomerPhone} onChange={setCardCustomerPhone} placeholder="Phone" />
                        </div>
                    )}

                    {txnType === "CHEQUE" && (
                        <div className="grid grid-cols-2 gap-3">
                            <InputField label="Bank Name" value={chequeBankName} onChange={setChequeBankName} placeholder="e.g. SBI" />
                            <InputField label="Cheque No" value={chequeNo} onChange={setChequeNo} placeholder="Cheque number" />
                            <InputField label="In Favor Of" value={chequeInFavorOf} onChange={setChequeInFavorOf} placeholder="Payee name" />
                            <div>
                                <label className="block text-xs font-medium text-foreground mb-1">Cheque Date</label>
                                <input
                                    type="date"
                                    value={chequeDate}
                                    onChange={(e) => setChequeDate(e.target.value)}
                                    className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                                />
                            </div>
                        </div>
                    )}

                    {txnType === "BANK_TRANSFER" && (
                        <InputField label="Bank Name" value={bankName} onChange={setBankName} placeholder="e.g. ICICI" />
                    )}

                    {txnType === "CCMS" && (
                        <InputField label="CCMS Number" value={ccmsNumber} onChange={setCcmsNumber} placeholder="CCMS ref number" />
                    )}

                    {txnType === "EXPENSE" && (
                        <div className="space-y-3">
                            <InputField label="Description" value={expenseDescription} onChange={setExpenseDescription} placeholder="What was the expense for?" />
                            <div>
                                <label className="block text-xs font-medium text-foreground mb-1">Expense Type</label>
                                <StyledSelect
                                    value={selectedExpenseTypeId}
                                    onChange={(val) => { setSelectedExpenseTypeId(val); setNewExpenseTypeName(""); }}
                                    options={[
                                        { value: "", label: "Select or add new..." },
                                        ...expenseTypes.map((et) => ({ value: String(et.id), label: et.name })),
                                    ]}
                                    className="mb-2"
                                />
                                {!selectedExpenseTypeId && (
                                    <input
                                        type="text"
                                        value={newExpenseTypeName}
                                        onChange={(e) => setNewExpenseTypeName(e.target.value)}
                                        className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                                        placeholder="New expense type..."
                                    />
                                )}
                            </div>
                        </div>
                    )}

                    {/* Link to Invoice (only for E-Advances, not Expenses) */}
                    {txnType !== "EXPENSE" && (
                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5">Link to Invoice (Optional)</label>
                            <InvoiceAutocomplete
                                value={linkedInvoice}
                                onChange={setLinkedInvoice}
                                placeholder="Search by bill #..."
                            />
                        </div>
                    )}

                    {/* Remarks */}
                    <div>
                        <label className="block text-sm font-medium text-foreground mb-1.5">Remarks</label>
                        <input
                            type="text"
                            value={txnRemarks}
                            onChange={(e) => setTxnRemarks(e.target.value)}
                            className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                            placeholder="Optional notes..."
                        />
                    </div>

                    <div className="flex justify-end gap-3 pt-6 border-t border-border mt-6">
                        <button
                            type="button"
                            onClick={() => setIsAddModalOpen(false)}
                            className="px-6 py-2.5 rounded-xl font-medium text-foreground bg-black/5 dark:bg-white/5 hover:bg-black/10 dark:hover:bg-white/10 transition-colors"
                        >
                            Cancel
                        </button>
                        <button
                            type="submit"
                            className="btn-gradient px-8 py-2.5 rounded-xl font-medium shadow-lg hover:shadow-xl transition-all"
                        >
                            Add Entry
                        </button>
                    </div>
                </form>
            </Modal>

            {/* Cashier Selection Modal for OWNER/ADMIN */}
            <Modal
                isOpen={showCashierModal}
                onClose={() => setShowCashierModal(false)}
                title={cashierModalMode === "open" ? "Select Cashier for Shift" : "Change Shift Cashier"}
            >
                <div className="p-6 space-y-4">
                    <p className="text-sm text-muted-foreground">Choose which cashier will be in charge of this shift.</p>
                    <input
                        type="text"
                        placeholder="Search by name or phone..."
                        value={cashierSearch}
                        onChange={(e) => setCashierSearch(e.target.value)}
                        className="w-full px-3 py-2 bg-card border border-border rounded-lg text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                    />
                    <div className="space-y-2 max-h-[50vh] overflow-y-auto">
                        {cashiers
                            .filter(c => {
                                const q = cashierSearch.toLowerCase();
                                if (!q) return true;
                                return c.name?.toLowerCase().includes(q) || c.phone?.includes(q);
                            })
                            .map(c => (
                            <button
                                key={c.id}
                                onClick={() => setSelectedCashierId(c.id)}
                                className={`w-full text-left px-4 py-3 rounded-xl border transition-all flex items-center justify-between ${
                                    selectedCashierId === c.id
                                        ? "border-orange-500 bg-orange-500/10 text-foreground"
                                        : "border-border hover:bg-muted/50 text-foreground"
                                }`}
                            >
                                <div>
                                    <div className="font-medium">{c.name}</div>
                                    <div className="text-xs text-muted-foreground">{c.role}{c.phone ? ` · ${c.phone}` : ""}</div>
                                </div>
                                {selectedCashierId === c.id && (
                                    <div className="w-5 h-5 rounded-full bg-orange-500 flex items-center justify-center">
                                        <svg className="w-3 h-3 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={3} d="M5 13l4 4L19 7" />
                                        </svg>
                                    </div>
                                )}
                            </button>
                        ))}
                        {cashiers.filter(c => {
                            const q = cashierSearch.toLowerCase();
                            return !q || c.name?.toLowerCase().includes(q) || c.phone?.includes(q);
                        }).length === 0 && (
                            <p className="text-center text-muted-foreground py-4">No cashiers found</p>
                        )}
                    </div>
                    <div className="flex justify-end gap-3 pt-2 border-t border-border">
                        <button
                            onClick={() => setShowCashierModal(false)}
                            className="px-4 py-2 rounded-lg border border-border text-muted-foreground hover:bg-muted transition-colors"
                        >
                            Cancel
                        </button>
                        <button
                            onClick={handleConfirmCashierAction}
                            disabled={!selectedCashierId || openingShift}
                            className="btn-gradient px-6 py-2 rounded-lg font-medium disabled:opacity-50"
                        >
                            {openingShift
                                ? (cashierModalMode === "open" ? "Opening..." : "Updating...")
                                : (cashierModalMode === "open" ? "Open Shift" : "Update Cashier")}
                        </button>
                    </div>
                </div>
            </Modal>
        </div>
    );
}

// --- Helper Components ---

function SummaryCard({ label, value, color, bgColor, icon: Icon }: {
    label: string; value: number; color: string; bgColor: string; icon: any;
}) {
    return (
        <GlassCard className="p-4">
            <div className="flex items-center gap-3">
                <div className={`p-2 rounded-xl ${bgColor}`}>
                    <Icon className={`w-5 h-5 ${color}`} />
                </div>
                <div>
                    <p className="text-xs text-muted-foreground">{label}</p>
                    <p className={`text-xl font-bold ${color}`}>{formatCurrency(value)}</p>
                </div>
            </div>
        </GlassCard>
    );
}

function InputField({ label, value, onChange, placeholder }: { label: string; value: string; onChange: (v: string) => void; placeholder?: string }) {
    return (
        <div>
            <label className="block text-xs font-medium text-foreground mb-1">{label}</label>
            <input
                type="text"
                value={value}
                onChange={(e) => onChange(e.target.value)}
                className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                placeholder={placeholder}
            />
        </div>
    );
}

function getEAdvanceDetails(ea: EAdvance): string {
    const parts: string[] = [];
    if (ea.upiCompany?.companyName) parts.push(ea.upiCompany.companyName);
    if (ea.bankName) parts.push(ea.bankName);
    if (ea.cardLast4Digit) parts.push(`****${ea.cardLast4Digit}`);
    if (ea.customerName) parts.push(ea.customerName);
    if (ea.chequeNo) parts.push(`Chq: ${ea.chequeNo}`);
    if (ea.ccmsNumber) parts.push(`CCMS: ${ea.ccmsNumber}`);
    if (ea.remarks) parts.push(ea.remarks);
    return parts.join(" | ") || "-";
}
