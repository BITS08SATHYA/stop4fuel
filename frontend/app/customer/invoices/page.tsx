"use client";

import { useEffect, useState, useCallback } from "react";
import { GlassCard } from "@/components/ui/glass-card";
import { TablePagination } from "@/components/ui/table-pagination";
import { getMyInvoices, CustomerInvoice } from "@/lib/api/station";
import { Loader2, FileText } from "lucide-react";
import { StyledSelect } from "@/components/ui/styled-select";

function formatCurrency(val?: number | null) {
    if (val == null) return "0.00";
    return val.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function formatDateTime(dt?: string | null) {
    if (!dt) return "-";
    return new Date(dt).toLocaleString("en-IN", { day: "2-digit", month: "short", hour: "2-digit", minute: "2-digit" });
}

export default function CustomerInvoicesPage() {
    const [invoices, setInvoices] = useState<CustomerInvoice[]>([]);
    const [loading, setLoading] = useState(true);
    const [page, setPage] = useState(0);
    const [pageSize] = useState(20);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);
    const [paymentStatus, setPaymentStatus] = useState<string>("");

    const fetchData = useCallback(async () => {
        setLoading(true);
        try {
            const res = await getMyInvoices(page, pageSize, paymentStatus || undefined);
            setInvoices(res.content);
            setTotalPages(res.totalPages);
            setTotalElements(res.totalElements);
        } catch {
            setInvoices([]);
        } finally {
            setLoading(false);
        }
    }, [page, pageSize, paymentStatus]);

    useEffect(() => { fetchData(); }, [fetchData]);

    return (
        <div className="space-y-4">
            <div className="flex items-center justify-between">
                <h1 className="text-xl font-bold flex items-center gap-2">
                    <FileText className="w-5 h-5" /> My Invoices
                </h1>
                <StyledSelect
                    value={paymentStatus}
                    onChange={(val) => { setPaymentStatus(val); setPage(0); }}
                    options={[
                        { value: "", label: "All Status" },
                        { value: "NOT_PAID", label: "Not Paid" },
                        { value: "PAID", label: "Paid" },
                    ]}
                    placeholder="All Status"
                    className="min-w-[140px]"
                />
            </div>

            <GlassCard className="p-0 overflow-hidden">
                {loading ? (
                    <div className="flex justify-center py-12"><Loader2 className="w-6 h-6 animate-spin text-primary" /></div>
                ) : invoices.length === 0 ? (
                    <p className="text-sm text-muted-foreground text-center py-12">No invoices found</p>
                ) : (
                    <div className="overflow-x-auto">
                        <table className="w-full text-sm">
                            <thead>
                                <tr className="border-b border-border bg-muted/30 text-muted-foreground text-xs">
                                    <th className="text-left px-4 py-3">Bill No</th>
                                    <th className="text-left px-4 py-3">Date</th>
                                    <th className="text-left px-4 py-3">Vehicle</th>
                                    <th className="text-left px-4 py-3">Type</th>
                                    <th className="text-right px-4 py-3">Amount</th>
                                    <th className="text-left px-4 py-3">Payment</th>
                                    <th className="text-left px-4 py-3">Status</th>
                                </tr>
                            </thead>
                            <tbody>
                                {invoices.map((inv) => (
                                    <tr key={inv.id} className="border-b border-border/50 hover:bg-muted/20">
                                        <td className="px-4 py-3 font-medium">{inv.billNo}</td>
                                        <td className="px-4 py-3 text-muted-foreground">{formatDateTime(inv.date)}</td>
                                        <td className="px-4 py-3">{inv.vehicleNumber || "-"}</td>
                                        <td className="px-4 py-3">
                                            <span className={`text-xs font-medium ${inv.billType === "CASH" ? "text-green-500" : "text-blue-500"}`}>
                                                {inv.billType}
                                            </span>
                                        </td>
                                        <td className="px-4 py-3 text-right font-medium">{formatCurrency(inv.netAmount)}</td>
                                        <td className="px-4 py-3 text-muted-foreground">{inv.paymentMode || "-"}</td>
                                        <td className="px-4 py-3">
                                            <span className={`text-xs font-medium ${inv.paymentStatus === "PAID" ? "text-green-500" : "text-amber-500"}`}>
                                                {inv.paymentStatus?.replace(/_/g, " ")}
                                            </span>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}
            </GlassCard>
            {totalPages > 1 && (
                <TablePagination page={page} totalPages={totalPages} totalElements={totalElements} pageSize={pageSize} onPageChange={setPage} />
            )}
        </div>
    );
}
