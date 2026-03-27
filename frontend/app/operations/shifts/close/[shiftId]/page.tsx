"use client";

import { useEffect, useState, useCallback } from "react";
import { useParams, useRouter } from "next/navigation";
import { GlassCard } from "@/components/ui/glass-card";
import {
    getShiftClosingData,
    submitShiftForReview,
    ShiftClosingData,
    NozzleReadingInput,
    TankDipInput,
} from "@/lib/api/station";
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
            alert(`Please enter close readings for: ${missingNozzles.map(n => n.nozzleName).join(", ")}`);
            return;
        }

        // Validate all tank close stock filled
        const missingTanks = data.tankDips.filter(t => !tankCloseStock[t.tankId]);
        if (missingTanks.length > 0) {
            alert(`Please enter close stock for: ${missingTanks.map(t => t.tankName).join(", ")}`);
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
            alert(err.message || "Failed to submit for review");
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
