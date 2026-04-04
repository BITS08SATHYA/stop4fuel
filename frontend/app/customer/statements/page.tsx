"use client";

import { useEffect, useState, useCallback } from "react";
import { GlassCard } from "@/components/ui/glass-card";
import { TablePagination } from "@/components/ui/table-pagination";
import { getMyStatements, CustomerStatement } from "@/lib/api/station";
import { Loader2, Receipt } from "lucide-react";
import { StyledSelect } from "@/components/ui/styled-select";

function formatCurrency(val?: number | null) {
    if (val == null) return "0.00";
    return val.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function formatDate(dt?: string | null) {
    if (!dt) return "-";
    return new Date(dt).toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "numeric" });
}

export default function CustomerStatementsPage() {
    const [statements, setStatements] = useState<CustomerStatement[]>([]);
    const [loading, setLoading] = useState(true);
    const [page, setPage] = useState(0);
    const [pageSize] = useState(10);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);
    const [status, setStatus] = useState<string>("");

    const fetchData = useCallback(async () => {
        setLoading(true);
        try {
            const res = await getMyStatements(page, pageSize, status || undefined);
            setStatements(res.content);
            setTotalPages(res.totalPages);
            setTotalElements(res.totalElements);
        } catch {
            setStatements([]);
        } finally {
            setLoading(false);
        }
    }, [page, pageSize, status]);

    useEffect(() => { fetchData(); }, [fetchData]);

    return (
        <div className="space-y-4">
            <div className="flex items-center justify-between">
                <h1 className="text-xl font-bold flex items-center gap-2">
                    <Receipt className="w-5 h-5" /> My Statements
                </h1>
                <StyledSelect
                    value={status}
                    onChange={(val) => { setStatus(val); setPage(0); }}
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
                ) : statements.length === 0 ? (
                    <p className="text-sm text-muted-foreground text-center py-12">No statements found</p>
                ) : (
                    <table className="w-full text-sm">
                        <thead>
                            <tr className="border-b border-border bg-muted/30 text-muted-foreground text-xs">
                                <th className="text-left px-4 py-3">Statement No</th>
                                <th className="text-left px-4 py-3">Period</th>
                                <th className="text-right px-4 py-3">Bills</th>
                                <th className="text-right px-4 py-3">Amount</th>
                                <th className="text-right px-4 py-3">Received</th>
                                <th className="text-right px-4 py-3">Balance</th>
                                <th className="text-left px-4 py-3">Status</th>
                            </tr>
                        </thead>
                        <tbody>
                            {statements.map((s) => (
                                <tr key={s.id} className="border-b border-border/50 hover:bg-muted/20">
                                    <td className="px-4 py-3 font-medium">{s.statementNo}</td>
                                    <td className="px-4 py-3 text-muted-foreground">
                                        {formatDate(s.fromDate)} - {formatDate(s.toDate)}
                                    </td>
                                    <td className="px-4 py-3 text-right">{s.numberOfBills}</td>
                                    <td className="px-4 py-3 text-right">{formatCurrency(s.netAmount)}</td>
                                    <td className="px-4 py-3 text-right text-green-500">{formatCurrency(s.receivedAmount)}</td>
                                    <td className="px-4 py-3 text-right font-medium">{formatCurrency(s.balanceAmount)}</td>
                                    <td className="px-4 py-3">
                                        <span className={`text-xs font-medium ${s.status === "PAID" ? "text-green-500" : "text-amber-500"}`}>
                                            {s.status?.replace(/_/g, " ")}
                                        </span>
                                    </td>
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
