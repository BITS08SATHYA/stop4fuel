"use client";

import { useEffect, useState, useCallback } from "react";
import { useParams, useRouter } from "next/navigation";
import { GlassCard } from "@/components/ui/glass-card";
import {
    getShiftClosingData,
    submitShiftForReview,
    getInvoicesByShift,
    getPaymentsByShift,
    ShiftClosingData,
    NozzleReadingInput,
    TankDipInput,
    InvoiceBill,
    Payment,
} from "@/lib/api/station";
import { showToast } from "@/components/ui/toast";
import {
    ArrowLeft,
    Fuel,
    Gauge,
    SendHorizonal,
    Loader2,
    AlertCircle,
    Droplets,
    Banknote,
    CreditCard,
    Smartphone,
    Receipt,
    Building2,
    FileText,
    Wallet,
    Info,
    FlaskConical,
    ChevronDown,
    ChevronUp,
    List,
} from "lucide-react";

function fmtCur(v?: number | null) {
    if (v == null) return "0.00";
    return v.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function fmtDT(dt?: string) {
    if (!dt) return "-";
    return new Date(dt).toLocaleString("en-IN", {
        day: "2-digit", month: "short", year: "numeric",
        hour: "2-digit", minute: "2-digit",
    });
}

const E_ADV_ICONS: Record<string, typeof CreditCard> = {
    CARD: CreditCard, UPI: Smartphone, CCMS: Receipt,
    CHEQUE: FileText, BANK_TRANSFER: Building2,
};

const E_ADV_COLORS: Record<string, string> = {
    CARD: "text-blue-500", UPI: "text-purple-500", CCMS: "text-pink-500",
    CHEQUE: "text-amber-500", BANK_TRANSFER: "text-cyan-500",
};

export default function ShiftClosingWorkspace() {
    const params = useParams();
    const router = useRouter();
    const shiftId = Number(params.shiftId);

    const [data, setData] = useState<ShiftClosingData | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [isSubmitting, setIsSubmitting] = useState(false);

    // Nozzle close readings (keyed by nozzleId)
    const [nozzleCloseReadings, setNozzleCloseReadings] = useState<Record<number, string>>({});
    const [nozzleTestQty, setNozzleTestQty] = useState<Record<number, string>>({});

    // Tank close readings (keyed by tankId)
    const [tankIncomeStock, setTankIncomeStock] = useState<Record<number, string>>({});
    const [tankCloseDip, setTankCloseDip] = useState<Record<number, string>>({});
    const [tankCloseStock, setTankCloseStock] = useState<Record<number, string>>({});

    // Collapsible invoice/payment sections
    const [showInvoices, setShowInvoices] = useState(false);
    const [showPayments, setShowPayments] = useState(false);
    const [invoices, setInvoices] = useState<InvoiceBill[]>([]);
    const [payments, setPayments] = useState<Payment[]>([]);
    const [invoicesLoading, setInvoicesLoading] = useState(false);
    const [paymentsLoading, setPaymentsLoading] = useState(false);

    const loadData = useCallback(async () => {
        try {
            setIsLoading(true);
            setError(null);
            const result = await getShiftClosingData(shiftId);
            setData(result);
        } catch (err: any) {
            setError(err.message || "Failed to load closing data");
        } finally {
            setIsLoading(false);
        }
    }, [shiftId]);

    useEffect(() => { loadData(); }, [loadData]);

    // Pre-fill close readings from existing data (when editing a previously submitted shift)
    useEffect(() => {
        if (!data) return;
        const closeR: Record<number, string> = {};
        const testQ: Record<number, string> = {};
        for (const r of data.nozzleReadings) {
            if (r.closeMeterReading != null) closeR[r.nozzleId] = String(r.closeMeterReading);
            if (r.testQuantity != null) testQ[r.nozzleId] = String(r.testQuantity);
        }
        if (Object.keys(closeR).length > 0) setNozzleCloseReadings(closeR);
        if (Object.keys(testQ).length > 0) setNozzleTestQty(testQ);

        const incS: Record<number, string> = {};
        const clD: Record<number, string> = {};
        const clS: Record<number, string> = {};
        for (const t of data.tankDips) {
            if (t.incomeStock != null) incS[t.tankId] = String(t.incomeStock);
            if (t.closeDip != null) clD[t.tankId] = t.closeDip;
            if (t.closeStock != null) clS[t.tankId] = String(t.closeStock);
        }
        if (Object.keys(incS).length > 0) setTankIncomeStock(incS);
        if (Object.keys(clD).length > 0) setTankCloseDip(clD);
        if (Object.keys(clS).length > 0) setTankCloseStock(clS);
    }, [data]);

    // Lazy-load invoices when section is expanded
    useEffect(() => {
        if (showInvoices && invoices.length === 0 && !invoicesLoading) {
            setInvoicesLoading(true);
            getInvoicesByShift(shiftId).then(setInvoices).catch(() => {}).finally(() => setInvoicesLoading(false));
        }
    }, [showInvoices, shiftId, invoices.length, invoicesLoading]);

    // Lazy-load payments when section is expanded
    useEffect(() => {
        if (showPayments && payments.length === 0 && !paymentsLoading) {
            setPaymentsLoading(true);
            getPaymentsByShift(shiftId).then(setPayments).catch(() => {}).finally(() => setPaymentsLoading(false));
        }
    }, [showPayments, shiftId, payments.length, paymentsLoading]);

    // Compute sales per nozzle
    const getNozzleSales = (nozzleId: number, openReading: number) => {
        const closeStr = nozzleCloseReadings[nozzleId];
        if (!closeStr) return null;
        const close = parseFloat(closeStr);
        if (isNaN(close)) return null;
        return close - openReading;
    };

    const getNozzleAmount = (nozzleId: number, openReading: number, price?: number) => {
        const sales = getNozzleSales(nozzleId, openReading);
        if (sales == null || !price) return null;
        const testQty = parseFloat(nozzleTestQty[nozzleId] || "0") || 0;
        return (sales - testQty) * price;
    };

    // Total fuel sales
    const totalFuelSales = data?.nozzleReadings.reduce((sum, n) => {
        const amt = getNozzleAmount(n.nozzleId, n.openMeterReading, n.productPrice);
        return sum + (amt || 0);
    }, 0) || 0;

    const handleSubmit = async () => {
        if (!data) return;

        // Validate all nozzle close readings filled
        const missingNozzles = data.nozzleReadings.filter(n => !nozzleCloseReadings[n.nozzleId]);
        if (missingNozzles.length > 0) {
            showToast.error(`Please enter close readings for: ${missingNozzles.map(n => n.nozzleName).join(", ")}`);
            return;
        }

        // Validate all tank close stock filled
        const missingTanks = data.tankDips.filter(t => !tankCloseStock[t.tankId]);
        if (missingTanks.length > 0) {
            showToast.error(`Please enter close stock for: ${missingTanks.map(t => t.tankName).join(", ")}`);
            return;
        }

        if (!confirm("Submit this shift for review? Meter readings and dip readings will be saved.")) return;

        try {
            setIsSubmitting(true);

            const nozzleReadings: NozzleReadingInput[] = data.nozzleReadings.map(n => ({
                nozzleId: n.nozzleId,
                openMeterReading: n.openMeterReading,
                closeMeterReading: parseFloat(nozzleCloseReadings[n.nozzleId]),
                testQuantity: parseFloat(nozzleTestQty[n.nozzleId] || "0") || undefined,
            }));

            const tankDips: TankDipInput[] = data.tankDips.map(t => ({
                tankId: t.tankId,
                openDip: t.openDip || undefined,
                openStock: t.openStock,
                incomeStock: parseFloat(tankIncomeStock[t.tankId] || "0") || undefined,
                closeDip: tankCloseDip[t.tankId] || "",
                closeStock: parseFloat(tankCloseStock[t.tankId]),
            }));

            await submitShiftForReview(shiftId, { nozzleReadings, tankDips });
            router.push(`/operations/shifts/report/${shiftId}`);
        } catch (err: any) {
            showToast.error(err.message || "Failed to submit for review");
        } finally {
            setIsSubmitting(false);
        }
    };

    if (isLoading) {
        return (
            <div className="flex items-center justify-center min-h-[60vh]">
                <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
            </div>
        );
    }

    if (error || !data) {
        return (
            <div className="p-6 max-w-4xl mx-auto">
                <GlassCard className="p-8 text-center">
                    <AlertCircle className="h-12 w-12 text-red-500 mx-auto mb-4" />
                    <h2 className="text-lg font-semibold mb-2">Error</h2>
                    <p className="text-muted-foreground mb-4">{error || "No data found"}</p>
                    <button onClick={() => router.back()} className="text-sm text-primary hover:underline">
                        Go back
                    </button>
                </GlassCard>
            </div>
        );
    }

    // Group nozzles by product for summary
    const productSales: Record<string, { litres: number; amount: number }> = {};
    data.nozzleReadings.forEach(n => {
        const sales = getNozzleSales(n.nozzleId, n.openMeterReading);
        const amt = getNozzleAmount(n.nozzleId, n.openMeterReading, n.productPrice);
        const pName = n.productName || "Unknown";
        if (!productSales[pName]) productSales[pName] = { litres: 0, amount: 0 };
        if (sales) productSales[pName].litres += sales;
        if (amt) productSales[pName].amount += amt;
    });

    // E-Advance grand total
    const eAdvTotal = data.eAdvanceTotals
        ? Object.values(data.eAdvanceTotals).reduce((s, v) => s + (v || 0), 0) : 0;

    // Op-advance total
    const opAdvTotal = data.opAdvanceTotals
        ? Object.values(data.opAdvanceTotals).reduce((s, v) => s + (v || 0), 0) : 0;

    return (
        <div className="p-4 md:p-6 max-w-6xl mx-auto space-y-6">
            {/* Header */}
            <div className="flex items-center gap-3">
                <button onClick={() => router.back()} className="p-2 rounded-lg hover:bg-accent transition-colors">
                    <ArrowLeft className="h-5 w-5" />
                </button>
                <div className="flex-1">
                    <h1 className="text-2xl font-bold">Shift Closing Workspace</h1>
                    <p className="text-sm text-muted-foreground">
                        Shift #{data.shiftId} &middot; {data.attendantName || "—"} &middot; Started {fmtDT(data.startTime)}
                    </p>
                </div>
                <span className={`px-3 py-1 rounded-full text-xs font-medium ${
                    data.shiftStatus === "OPEN" ? "bg-green-500/10 text-green-500" : "bg-amber-500/10 text-amber-500"
                }`}>
                    {data.shiftStatus}
                </span>
            </div>

            {/* Nozzle Meter Readings */}
            <GlassCard className="p-5">
                <div className="flex items-center gap-2 mb-4">
                    <Gauge className="h-5 w-5 text-primary" />
                    <h2 className="text-lg font-semibold">Nozzle Meter Readings</h2>
                </div>
                <div className="overflow-x-auto">
                    <table className="w-full text-sm">
                        <thead>
                            <tr className="border-b text-left text-muted-foreground">
                                <th className="py-2 pr-3">Pump</th>
                                <th className="py-2 pr-3">Nozzle</th>
                                <th className="py-2 pr-3">Product</th>
                                <th className="py-2 pr-3 text-right">Open</th>
                                <th className="py-2 pr-3 text-right">Close</th>
                                <th className="py-2 pr-3 text-right">Test (L)</th>
                                <th className="py-2 pr-3 text-right">Sales (L)</th>
                                <th className="py-2 text-right">Amount</th>
                            </tr>
                        </thead>
                        <tbody>
                            {data.nozzleReadings.map(n => {
                                const sales = getNozzleSales(n.nozzleId, n.openMeterReading);
                                const amt = getNozzleAmount(n.nozzleId, n.openMeterReading, n.productPrice);
                                return (
                                    <tr key={n.nozzleId} className="border-b border-border/50">
                                        <td className="py-2 pr-3 text-muted-foreground">{n.pumpName || "-"}</td>
                                        <td className="py-2 pr-3 font-medium">{n.nozzleName}</td>
                                        <td className="py-2 pr-3">{n.productName || "-"}</td>
                                        <td className="py-2 pr-3 text-right tabular-nums">{n.openMeterReading.toFixed(2)}</td>
                                        <td className="py-2 pr-3 text-right">
                                            <input
                                                type="number"
                                                step="0.01"
                                                className="w-28 text-right rounded border border-border bg-background px-2 py-1 tabular-nums focus:ring-2 focus:ring-primary/30 focus:outline-none"
                                                placeholder="Close"
                                                value={nozzleCloseReadings[n.nozzleId] || ""}
                                                onChange={e => setNozzleCloseReadings(prev => ({ ...prev, [n.nozzleId]: e.target.value }))}
                                            />
                                        </td>
                                        <td className="py-2 pr-3 text-right">
                                            <input
                                                type="number"
                                                step="0.01"
                                                className="w-20 text-right rounded border border-border bg-background px-2 py-1 tabular-nums focus:ring-2 focus:ring-primary/30 focus:outline-none"
                                                placeholder="0"
                                                value={nozzleTestQty[n.nozzleId] || ""}
                                                onChange={e => setNozzleTestQty(prev => ({ ...prev, [n.nozzleId]: e.target.value }))}
                                            />
                                        </td>
                                        <td className="py-2 pr-3 text-right tabular-nums font-medium">
                                            {sales != null ? sales.toFixed(2) : "-"}
                                        </td>
                                        <td className="py-2 text-right tabular-nums font-medium">
                                            {amt != null ? fmtCur(amt) : "-"}
                                        </td>
                                    </tr>
                                );
                            })}
                        </tbody>
                        {Object.keys(productSales).length > 0 && (
                            <tfoot>
                                {Object.entries(productSales).map(([product, { litres, amount }]) => (
                                    <tr key={product} className="text-muted-foreground">
                                        <td colSpan={6} className="py-1 pr-3 text-right text-xs">{product} Total:</td>
                                        <td className="py-1 pr-3 text-right tabular-nums text-xs">{litres.toFixed(2)}</td>
                                        <td className="py-1 text-right tabular-nums text-xs">{fmtCur(amount)}</td>
                                    </tr>
                                ))}
                                <tr className="font-semibold border-t">
                                    <td colSpan={6} className="py-2 pr-3 text-right">Total Fuel Sales:</td>
                                    <td className="py-2 pr-3 text-right tabular-nums">
                                        {Object.values(productSales).reduce((s, p) => s + p.litres, 0).toFixed(2)}
                                    </td>
                                    <td className="py-2 text-right tabular-nums">{fmtCur(totalFuelSales)}</td>
                                </tr>
                            </tfoot>
                        )}
                    </table>
                </div>
            </GlassCard>

            {/* Tank Dip Readings */}
            <GlassCard className="p-5">
                <div className="flex items-center gap-2 mb-4">
                    <Droplets className="h-5 w-5 text-primary" />
                    <h2 className="text-lg font-semibold">Tank Dip Readings</h2>
                </div>
                <div className="overflow-x-auto">
                    <table className="w-full text-sm">
                        <thead>
                            <tr className="border-b text-left text-muted-foreground">
                                <th className="py-2 pr-3">Tank</th>
                                <th className="py-2 pr-3">Product</th>
                                <th className="py-2 pr-3 text-right">Open Dip</th>
                                <th className="py-2 pr-3 text-right">Open Stock</th>
                                <th className="py-2 pr-3 text-right">Income (L)</th>
                                <th className="py-2 pr-3 text-right">Close Dip</th>
                                <th className="py-2 text-right">Close Stock (L)</th>
                            </tr>
                        </thead>
                        <tbody>
                            {data.tankDips.map(t => (
                                <tr key={t.tankId} className="border-b border-border/50">
                                    <td className="py-2 pr-3 font-medium">{t.tankName}</td>
                                    <td className="py-2 pr-3">{t.productName || "-"}</td>
                                    <td className="py-2 pr-3 text-right tabular-nums">{t.openDip || "-"}</td>
                                    <td className="py-2 pr-3 text-right tabular-nums">{t.openStock.toFixed(2)}</td>
                                    <td className="py-2 pr-3 text-right">
                                        <input
                                            type="number"
                                            step="0.01"
                                            className="w-24 text-right rounded border border-border bg-background px-2 py-1 tabular-nums focus:ring-2 focus:ring-primary/30 focus:outline-none"
                                            placeholder="0"
                                            value={tankIncomeStock[t.tankId] || ""}
                                            onChange={e => setTankIncomeStock(prev => ({ ...prev, [t.tankId]: e.target.value }))}
                                        />
                                    </td>
                                    <td className="py-2 pr-3 text-right">
                                        <input
                                            type="text"
                                            className="w-24 text-right rounded border border-border bg-background px-2 py-1 tabular-nums focus:ring-2 focus:ring-primary/30 focus:outline-none"
                                            placeholder="Dip"
                                            value={tankCloseDip[t.tankId] || ""}
                                            onChange={e => setTankCloseDip(prev => ({ ...prev, [t.tankId]: e.target.value }))}
                                        />
                                    </td>
                                    <td className="py-2 text-right">
                                        <input
                                            type="number"
                                            step="0.01"
                                            className="w-28 text-right rounded border border-border bg-background px-2 py-1 tabular-nums focus:ring-2 focus:ring-primary/30 focus:outline-none"
                                            placeholder="Stock"
                                            value={tankCloseStock[t.tankId] || ""}
                                            onChange={e => setTankCloseStock(prev => ({ ...prev, [t.tankId]: e.target.value }))}
                                        />
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            </GlassCard>

            {/* Shift Totals Summary */}
            <GlassCard className="p-5">
                <div className="flex items-center gap-2 mb-4">
                    <Banknote className="h-5 w-5 text-primary" />
                    <h2 className="text-lg font-semibold">Shift Totals</h2>
                    <span className="text-xs text-muted-foreground ml-2">(auto-computed from shift transactions)</span>
                </div>
                <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
                    {/* Fuel Sales */}
                    <div className="p-3 rounded-lg bg-accent/50">
                        <div className="flex items-center gap-2 text-xs text-muted-foreground mb-1">
                            <Fuel className="h-3.5 w-3.5" /> Fuel Sales
                        </div>
                        <p className="text-lg font-bold tabular-nums">{fmtCur(totalFuelSales)}</p>
                    </div>

                    {/* Credit Bills */}
                    <div className="p-3 rounded-lg bg-accent/50">
                        <div className="flex items-center gap-2 text-xs text-muted-foreground mb-1">
                            <FileText className="h-3.5 w-3.5" /> Credit Bills
                        </div>
                        <p className="text-lg font-bold tabular-nums">{fmtCur(data.creditBillTotal)}</p>
                    </div>

                    {/* Bill Payments */}
                    <div className="p-3 rounded-lg bg-accent/50">
                        <div className="flex items-center gap-2 text-xs text-muted-foreground mb-1">
                            <Banknote className="h-3.5 w-3.5" /> Bill Payments
                        </div>
                        <p className="text-lg font-bold tabular-nums">{fmtCur(data.billPaymentTotal)}</p>
                    </div>

                    {/* Statement Payments */}
                    <div className="p-3 rounded-lg bg-accent/50">
                        <div className="flex items-center gap-2 text-xs text-muted-foreground mb-1">
                            <Receipt className="h-3.5 w-3.5" /> Statement Payments
                        </div>
                        <p className="text-lg font-bold tabular-nums">{fmtCur(data.statementPaymentTotal)}</p>
                    </div>

                    {/* E-Advances breakdown */}
                    {data.eAdvanceTotals && Object.entries(data.eAdvanceTotals).map(([type, amount]) => {
                        if (!amount) return null;
                        const Icon = E_ADV_ICONS[type] || Banknote;
                        const color = E_ADV_COLORS[type] || "text-gray-500";
                        return (
                            <div key={type} className="p-3 rounded-lg bg-accent/50">
                                <div className={`flex items-center gap-2 text-xs mb-1 ${color}`}>
                                    <Icon className="h-3.5 w-3.5" /> {type.replace("_", " ")}
                                </div>
                                <p className="text-lg font-bold tabular-nums">{fmtCur(amount)}</p>
                            </div>
                        );
                    })}

                    {/* Op-Advances */}
                    {opAdvTotal > 0 && (
                        <div className="p-3 rounded-lg bg-accent/50">
                            <div className="flex items-center gap-2 text-xs text-muted-foreground mb-1">
                                <Wallet className="h-3.5 w-3.5" /> Op. Advances
                            </div>
                            <p className="text-lg font-bold tabular-nums">{fmtCur(opAdvTotal)}</p>
                        </div>
                    )}

                    {/* Expenses */}
                    <div className="p-3 rounded-lg bg-accent/50">
                        <div className="flex items-center gap-2 text-xs text-muted-foreground mb-1">
                            <Wallet className="h-3.5 w-3.5 text-red-500" /> Expenses
                        </div>
                        <p className="text-lg font-bold tabular-nums">{fmtCur(data.expenseTotal)}</p>
                    </div>

                    {/* Incentives */}
                    {(data.incentiveTotal ?? 0) > 0 && (
                        <div className="p-3 rounded-lg bg-accent/50">
                            <div className="flex items-center gap-2 text-xs text-muted-foreground mb-1">
                                <FlaskConical className="h-3.5 w-3.5" /> Incentives
                            </div>
                            <p className="text-lg font-bold tabular-nums">{fmtCur(data.incentiveTotal)}</p>
                        </div>
                    )}

                    {/* External Inflows */}
                    {(data.externalInflowTotal ?? 0) > 0 && (
                        <div className="p-3 rounded-lg bg-accent/50">
                            <div className="flex items-center gap-2 text-xs text-muted-foreground mb-1">
                                <Building2 className="h-3.5 w-3.5 text-green-500" /> Cash Inflows
                            </div>
                            <p className="text-lg font-bold tabular-nums">{fmtCur(data.externalInflowTotal)}</p>
                        </div>
                    )}

                    {/* Inflow Repayments */}
                    {(data.inflowRepaymentTotal ?? 0) > 0 && (
                        <div className="p-3 rounded-lg bg-accent/50">
                            <div className="flex items-center gap-2 text-xs text-muted-foreground mb-1">
                                <Building2 className="h-3.5 w-3.5 text-orange-500" /> Inflow Repayments
                            </div>
                            <p className="text-lg font-bold tabular-nums">{fmtCur(data.inflowRepaymentTotal)}</p>
                        </div>
                    )}
                </div>

                {/* Cash Balance Estimate */}
                <div className="mt-4 p-4 rounded-lg bg-primary/5 border border-primary/20">
                    <div className="flex items-center gap-2 mb-2">
                        <Info className="h-4 w-4 text-primary" />
                        <span className="text-sm font-semibold">Estimated Cash Balance</span>
                    </div>
                    <p className="text-2xl font-bold tabular-nums">
                        {fmtCur(
                            totalFuelSales
                            - (data.creditBillTotal || 0)
                            + (data.billPaymentTotal || 0)
                            + (data.statementPaymentTotal || 0)
                            + (data.externalInflowTotal || 0)
                            - eAdvTotal
                            - opAdvTotal
                            - (data.expenseTotal || 0)
                            - (data.incentiveTotal || 0)
                            - (data.inflowRepaymentTotal || 0)
                        )}
                    </p>
                    <p className="text-xs text-muted-foreground mt-1">
                        Fuel Sales - Credit Bills + Bill Payments + Statement Payments + Inflows - E-Advances - Op. Advances - Expenses - Incentives - Repayments
                    </p>
                </div>
            </GlassCard>

            {/* Shift Invoices (collapsible) */}
            <GlassCard className="p-5">
                <button
                    onClick={() => setShowInvoices(!showInvoices)}
                    className="flex items-center gap-2 w-full text-left"
                >
                    <List className="h-5 w-5 text-primary" />
                    <h2 className="text-lg font-semibold flex-1">Shift Invoices</h2>
                    <span className="text-xs text-muted-foreground mr-2">
                        {invoices.length > 0 ? `${invoices.length} invoices` : "Click to load"}
                    </span>
                    {showInvoices ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
                </button>
                {showInvoices && (
                    <div className="mt-4">
                        {invoicesLoading ? (
                            <div className="flex justify-center py-4"><Loader2 className="h-5 w-5 animate-spin text-muted-foreground" /></div>
                        ) : invoices.length === 0 ? (
                            <p className="text-sm text-muted-foreground text-center py-4">No invoices in this shift</p>
                        ) : (
                            <>
                                {/* Cash Invoices */}
                                {invoices.filter(i => i.billType === "CASH").length > 0 && (
                                    <div className="mb-4">
                                        <h3 className="text-sm font-semibold text-green-500 mb-2">
                                            Cash Invoices ({invoices.filter(i => i.billType === "CASH").length})
                                        </h3>
                                        <div className="overflow-x-auto">
                                            <table className="w-full text-xs">
                                                <thead>
                                                    <tr className="border-b text-muted-foreground">
                                                        <th className="text-left py-1.5 px-2">Bill#</th>
                                                        <th className="text-left py-1.5 px-2">Vehicle</th>
                                                        <th className="text-left py-1.5 px-2">Driver</th>
                                                        <th className="text-left py-1.5 px-2">Products</th>
                                                        <th className="text-left py-1.5 px-2">Mode</th>
                                                        <th className="text-right py-1.5 px-2">Amount</th>
                                                    </tr>
                                                </thead>
                                                <tbody>
                                                    {invoices.filter(i => i.billType === "CASH").map(inv => (
                                                        <tr key={inv.id} className="border-b border-border/30">
                                                            <td className="py-1 px-2">{inv.billNo || "-"}</td>
                                                            <td className="py-1 px-2">{inv.vehicle?.vehicleNumber || "-"}</td>
                                                            <td className="py-1 px-2">{inv.driverName || "-"}</td>
                                                            <td className="py-1 px-2">
                                                                {inv.products?.map(p => `${p.productName || "?"}: ${p.quantity}`).join(", ") || "-"}
                                                            </td>
                                                            <td className="py-1 px-2">
                                                                <span className="px-1.5 py-0.5 rounded bg-muted text-[10px] font-medium">{inv.paymentMode || "CASH"}</span>
                                                            </td>
                                                            <td className="py-1 px-2 text-right tabular-nums font-medium">{fmtCur(inv.netAmount)}</td>
                                                        </tr>
                                                    ))}
                                                    <tr className="border-t font-semibold">
                                                        <td colSpan={5} className="py-1.5 px-2 text-right">Total Cash</td>
                                                        <td className="py-1.5 px-2 text-right tabular-nums">
                                                            {fmtCur(invoices.filter(i => i.billType === "CASH").reduce((s, i) => s + (i.netAmount || 0), 0))}
                                                        </td>
                                                    </tr>
                                                </tbody>
                                            </table>
                                        </div>
                                    </div>
                                )}

                                {/* Credit Invoices */}
                                {invoices.filter(i => i.billType === "CREDIT").length > 0 && (
                                    <div>
                                        <h3 className="text-sm font-semibold text-amber-500 mb-2">
                                            Credit Invoices ({invoices.filter(i => i.billType === "CREDIT").length})
                                        </h3>
                                        <div className="overflow-x-auto">
                                            <table className="w-full text-xs">
                                                <thead>
                                                    <tr className="border-b text-muted-foreground">
                                                        <th className="text-left py-1.5 px-2">Bill#</th>
                                                        <th className="text-left py-1.5 px-2">Customer</th>
                                                        <th className="text-left py-1.5 px-2">Vehicle</th>
                                                        <th className="text-left py-1.5 px-2">Products</th>
                                                        <th className="text-right py-1.5 px-2">Amount</th>
                                                    </tr>
                                                </thead>
                                                <tbody>
                                                    {invoices.filter(i => i.billType === "CREDIT").map(inv => (
                                                        <tr key={inv.id} className="border-b border-border/30">
                                                            <td className="py-1 px-2">{inv.billNo || "-"}</td>
                                                            <td className="py-1 px-2">{inv.customer?.name || "-"}</td>
                                                            <td className="py-1 px-2">{inv.vehicle?.vehicleNumber || "-"}</td>
                                                            <td className="py-1 px-2">
                                                                {inv.products?.map(p => `${p.productName || "?"}: ${p.quantity}`).join(", ") || "-"}
                                                            </td>
                                                            <td className="py-1 px-2 text-right tabular-nums font-medium">{fmtCur(inv.netAmount)}</td>
                                                        </tr>
                                                    ))}
                                                    <tr className="border-t font-semibold">
                                                        <td colSpan={4} className="py-1.5 px-2 text-right">Total Credit</td>
                                                        <td className="py-1.5 px-2 text-right tabular-nums">
                                                            {fmtCur(invoices.filter(i => i.billType === "CREDIT").reduce((s, i) => s + (i.netAmount || 0), 0))}
                                                        </td>
                                                    </tr>
                                                </tbody>
                                            </table>
                                        </div>
                                    </div>
                                )}
                            </>
                        )}
                    </div>
                )}
            </GlassCard>

            {/* Shift Payments (collapsible) */}
            <GlassCard className="p-5">
                <button
                    onClick={() => setShowPayments(!showPayments)}
                    className="flex items-center gap-2 w-full text-left"
                >
                    <Banknote className="h-5 w-5 text-blue-500" />
                    <h2 className="text-lg font-semibold flex-1">Shift Payments</h2>
                    <span className="text-xs text-muted-foreground mr-2">
                        {payments.length > 0 ? `${payments.length} payments` : "Click to load"}
                    </span>
                    {showPayments ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
                </button>
                {showPayments && (
                    <div className="mt-4">
                        {paymentsLoading ? (
                            <div className="flex justify-center py-4"><Loader2 className="h-5 w-5 animate-spin text-muted-foreground" /></div>
                        ) : payments.length === 0 ? (
                            <p className="text-sm text-muted-foreground text-center py-4">No payments in this shift</p>
                        ) : (
                            <div className="overflow-x-auto">
                                <table className="w-full text-xs">
                                    <thead>
                                        <tr className="border-b text-muted-foreground">
                                            <th className="text-left py-1.5 px-2">Type</th>
                                            <th className="text-left py-1.5 px-2">Customer</th>
                                            <th className="text-left py-1.5 px-2">Reference</th>
                                            <th className="text-left py-1.5 px-2">Mode</th>
                                            <th className="text-left py-1.5 px-2">Remarks</th>
                                            <th className="text-right py-1.5 px-2">Amount</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {payments.map(p => (
                                            <tr key={p.id} className="border-b border-border/30">
                                                <td className="py-1 px-2">
                                                    <span className="px-1.5 py-0.5 rounded bg-muted text-[10px] font-medium">
                                                        {p.invoiceBill ? "BILL" : p.statement ? "STMT" : "OTHER"}
                                                    </span>
                                                </td>
                                                <td className="py-1 px-2">{p.customer?.name || "-"}</td>
                                                <td className="py-1 px-2">
                                                    {p.invoiceBill?.billNo || p.statement?.statementNo || p.referenceNo || "-"}
                                                </td>
                                                <td className="py-1 px-2">{p.paymentMode || "-"}</td>
                                                <td className="py-1 px-2 text-muted-foreground">{p.remarks || "-"}</td>
                                                <td className="py-1 px-2 text-right tabular-nums font-medium">{fmtCur(p.amount)}</td>
                                            </tr>
                                        ))}
                                        <tr className="border-t font-semibold">
                                            <td colSpan={5} className="py-1.5 px-2 text-right">Total</td>
                                            <td className="py-1.5 px-2 text-right tabular-nums">
                                                {fmtCur(payments.reduce((s, p) => s + (p.amount || 0), 0))}
                                            </td>
                                        </tr>
                                    </tbody>
                                </table>
                            </div>
                        )}
                    </div>
                )}
            </GlassCard>

            {/* Submit Button */}
            <div className="flex justify-end gap-3 pb-8">
                <button
                    onClick={() => router.back()}
                    className="px-4 py-2.5 rounded-lg border border-border text-sm font-medium hover:bg-accent transition-colors"
                >
                    Cancel
                </button>
                <button
                    onClick={handleSubmit}
                    disabled={isSubmitting}
                    className="px-6 py-2.5 rounded-lg bg-primary text-primary-foreground text-sm font-medium hover:bg-primary/90 transition-colors disabled:opacity-50 flex items-center gap-2"
                >
                    {isSubmitting ? (
                        <Loader2 className="h-4 w-4 animate-spin" />
                    ) : (
                        <SendHorizonal className="h-4 w-4" />
                    )}
                    Submit for Review
                </button>
            </div>
        </div>
    );
}
