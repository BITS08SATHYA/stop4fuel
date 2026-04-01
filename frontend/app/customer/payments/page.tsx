"use client";

import { useEffect, useState, useCallback } from "react";
import { GlassCard } from "@/components/ui/glass-card";
import { TablePagination } from "@/components/ui/table-pagination";
import { getMyPayments, CustomerPayment } from "@/lib/api/station";
import { Loader2, CreditCard } from "lucide-react";

function formatCurrency(val?: number | null) {
    if (val == null) return "0.00";
    return val.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function formatDateTime(dt?: string | null) {
    if (!dt) return "-";
    return new Date(dt).toLocaleString("en-IN", { day: "2-digit", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" });
}

export default function CustomerPaymentsPage() {
    const [payments, setPayments] = useState<CustomerPayment[]>([]);
    const [loading, setLoading] = useState(true);
    const [page, setPage] = useState(0);
    const [pageSize] = useState(10);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);

    const fetchData = useCallback(async () => {
        setLoading(true);
        try {
            const res = await getMyPayments(page, pageSize);
            setPayments(res.content);
            setTotalPages(res.totalPages);
            setTotalElements(res.totalElements);
        } catch {
            setPayments([]);
        } finally {
            setLoading(false);
        }
    }, [page, pageSize]);

    useEffect(() => { fetchData(); }, [fetchData]);

    return (
        <div className="space-y-4">
            <h1 className="text-xl font-bold flex items-center gap-2">
                <CreditCard className="w-5 h-5" /> My Payments
            </h1>

            <GlassCard className="p-0 overflow-hidden">
                {loading ? (
                    <div className="flex justify-center py-12"><Loader2 className="w-6 h-6 animate-spin text-primary" /></div>
                ) : payments.length === 0 ? (
                    <p className="text-sm text-muted-foreground text-center py-12">No payments found</p>
                ) : (
                    <table className="w-full text-sm">
                        <thead>
                            <tr className="border-b border-border bg-muted/30 text-muted-foreground text-xs">
                                <th className="text-left px-4 py-3">Date</th>
                                <th className="text-right px-4 py-3">Amount</th>
                                <th className="text-left px-4 py-3">Mode</th>
                                <th className="text-left px-4 py-3">Reference</th>
                                <th className="text-left px-4 py-3">Statement</th>
                                <th className="text-left px-4 py-3">Bill</th>
                                <th className="text-left px-4 py-3">Remarks</th>
                            </tr>
                        </thead>
                        <tbody>
                            {payments.map((p) => (
                                <tr key={p.id} className="border-b border-border/50 hover:bg-muted/20">
                                    <td className="px-4 py-3">{formatDateTime(p.paymentDate)}</td>
                                    <td className="px-4 py-3 text-right font-medium text-green-500">{formatCurrency(p.amount)}</td>
                                    <td className="px-4 py-3">
                                        <span className="bg-primary/10 text-primary px-2 py-0.5 rounded text-xs">
                                            {p.paymentModeName || "N/A"}
                                        </span>
                                    </td>
                                    <td className="px-4 py-3 text-muted-foreground">{p.referenceNo || "-"}</td>
                                    <td className="px-4 py-3 text-muted-foreground">{p.statementNo || "-"}</td>
                                    <td className="px-4 py-3 text-muted-foreground">{p.billNo || "-"}</td>
                                    <td className="px-4 py-3 text-muted-foreground">{p.remarks || "-"}</td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                )}
            </GlassCard>
            {totalPages > 1 && (
                <TablePagination page={page} totalPages={totalPages} totalElements={totalElements} pageSize={pageSize} onPageChange={setPage} />
            )}
        </div>
    );
}
