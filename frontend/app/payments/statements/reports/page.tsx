"use client";

import { useState, useEffect, useMemo } from "react";
import { GlassCard } from "@/components/ui/glass-card";
import { StyledSelect } from "@/components/ui/styled-select";
import {
    FileText, FileSpreadsheet, Download, CheckCircle2, Loader2,
    Calendar, IndianRupee, Receipt, TrendingUp, ArrowUpDown, ArrowUp, ArrowDown
} from "lucide-react";
import {
    getStatements,
    generateStatementPdf,
    getStatementPdfUrl,
    exportStatementsExcel,
    bulkGenerateStatementPdfs,
    type Statement,
} from "@/lib/api/station";
import { showToast } from "@/components/ui/toast";

function getCurrentMonthRange() {
    const now = new Date();
    const y = now.getFullYear();
    const m = String(now.getMonth() + 1).padStart(2, "0");
    return {
        fromDate: `${y}-${m}-01`,
        toDate: `${y}-${m}-${String(now.getDate()).padStart(2, "0")}`,
    };
}

function formatCurrency(amount: number) {
    return new Intl.NumberFormat("en-IN", {
        style: "currency",
        currency: "INR",
        minimumFractionDigits: 2,
    }).format(amount);
}

type SortKey = "statementNo" | "customerName" | "netAmount" | "categoryType";
type SortDir = "asc" | "desc";

export default function StatementReportsPage() {
    const { fromDate: defaultFrom, toDate: defaultTo } = getCurrentMonthRange();
    const [statements, setStatements] = useState<Statement[]>([]);
    const [fromDate, setFromDate] = useState(defaultFrom);
    const [toDate, setToDate] = useState(defaultTo);
    const [status, setStatus] = useState("ALL");
    const [loading, setLoading] = useState(false);
    const [isDownloading, setIsDownloading] = useState(false);
    const [isGenerating, setIsGenerating] = useState(false);
    const [generatingPdfId, setGeneratingPdfId] = useState<number | null>(null);

    // Sorting
    const [sortKey, setSortKey] = useState<SortKey>("statementNo");
    const [sortDir, setSortDir] = useState<SortDir>("desc");

    const sortedStatements = useMemo(() => {
        const sorted = [...statements];
        sorted.sort((a, b) => {
            let cmp = 0;
            switch (sortKey) {
                case "statementNo":
                    cmp = (a.statementNo || "").localeCompare(b.statementNo || "", undefined, { numeric: true });
                    break;
                case "customerName":
                    cmp = (a.customer?.name || "").localeCompare(b.customer?.name || "");
                    break;
                case "netAmount":
                    cmp = (a.netAmount || 0) - (b.netAmount || 0);
                    break;
                case "categoryType":
                    cmp = ((a.customer as any)?.categoryType || "").localeCompare((b.customer as any)?.categoryType || "");
                    break;
            }
            return sortDir === "asc" ? cmp : -cmp;
        });
        return sorted;
    }, [statements, sortKey, sortDir]);

    const handleSort = (key: SortKey) => {
        if (sortKey === key) {
            setSortDir(d => d === "asc" ? "desc" : "asc");
        } else {
            setSortKey(key);
            setSortDir("asc");
        }
    };

    const SortIcon = ({ col }: { col: SortKey }) => {
        if (sortKey !== col) return <ArrowUpDown className="w-3 h-3 opacity-40" />;
        return sortDir === "asc" ? <ArrowUp className="w-3 h-3" /> : <ArrowDown className="w-3 h-3" />;
    };

    const loadStatements = async () => {
        setLoading(true);
        try {
            const statusParam = status === "ALL" ? undefined : status;
            const res = await getStatements(0, 10000, undefined, statusParam, fromDate, toDate);
            setStatements(res.content || []);
        } catch {
            showToast.error("Failed to load statements");
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        loadStatements();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    // Summary calculations
    const totalStatements = statements.length;
    const totalAmount = statements.reduce((sum, s) => sum + (s.netAmount || 0), 0);
    const totalCollected = statements.reduce((sum, s) => sum + (s.receivedAmount || 0), 0);
    const totalOutstanding = statements.reduce((sum, s) => sum + (s.balanceAmount || 0), 0);

    const handleDownloadPdf = async (stmt: Statement) => {
        setGeneratingPdfId(stmt.id!);
        try {
            // Always re-generate to include latest payments
            await generateStatementPdf(stmt.id!);
            const url = await getStatementPdfUrl(stmt.id!);
            window.open(url, "_blank");
        } catch {
            showToast.error("Failed to download PDF");
        } finally {
            setGeneratingPdfId(null);
        }
    };

    const handleGeneratePdf = async (stmt: Statement) => {
        setGeneratingPdfId(stmt.id!);
        try {
            await generateStatementPdf(stmt.id!);
            showToast.success("PDF generated successfully");
            loadStatements();
        } catch {
            showToast.error("Failed to generate PDF");
        } finally {
            setGeneratingPdfId(null);
        }
    };

    const handleExcelDownload = async () => {
        setIsDownloading(true);
        try {
            const blob = await exportStatementsExcel(fromDate, toDate, status);
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement("a");
            a.href = url;
            a.download = `Statements_${fromDate}_to_${toDate}.xlsx`;
            document.body.appendChild(a);
            a.click();
            a.remove();
            window.URL.revokeObjectURL(url);
            showToast.success("Excel downloaded");
        } catch {
            showToast.error("Failed to download Excel");
        } finally {
            setIsDownloading(false);
        }
    };

    const handleBulkGenerate = async () => {
        setIsGenerating(true);
        try {
            const result = await bulkGenerateStatementPdfs(fromDate, toDate);
            showToast.success(`Generated ${result.generated} PDFs`);
            loadStatements();
        } catch {
            showToast.error("Failed to bulk generate PDFs");
        } finally {
            setIsGenerating(false);
        }
    };

    const statusBadge = (s: string) => {
        switch (s) {
            case "PAID":
                return <span className="px-2 py-0.5 text-[10px] font-bold rounded-full bg-green-500/10 text-green-500 border border-green-500/20">PAID</span>;
            case "NOT_PAID":
                return <span className="px-2 py-0.5 text-[10px] font-bold rounded-full bg-red-500/10 text-red-500 border border-red-500/20">NOT PAID</span>;
            default:
                return <span className="px-2 py-0.5 text-[10px] font-bold rounded-full bg-gray-500/10 text-gray-500 border border-gray-500/20">DRAFT</span>;
        }
    };

    const categoryBadge = (cat: string | undefined) => {
        if (!cat) return <span className="text-muted-foreground text-xs">-</span>;
        if (cat === "GOVERNMENT") return <span className="px-2 py-0.5 text-[10px] font-bold rounded-full bg-blue-500/10 text-blue-500 border border-blue-500/20">GOVT</span>;
        return <span className="px-2 py-0.5 text-[10px] font-bold rounded-full bg-amber-500/10 text-amber-500 border border-amber-500/20">NON-GOVT</span>;
    };

    return (
        <div className="p-6 h-screen overflow-hidden bg-background flex flex-col">
            {/* Header */}
            <div className="mb-4">
                <h1 className="text-3xl font-bold">
                    <span className="text-gradient">Statement Reports</span>
                </h1>
                <p className="text-muted-foreground text-sm mt-1">
                    Download and manage generated statement PDFs and Excel exports.
                </p>
            </div>

            {/* Filter Bar */}
            <div className="mb-4 flex items-center gap-3 flex-wrap">
                <Calendar className="w-4 h-4 text-muted-foreground" />
                <input
                    type="date"
                    value={fromDate}
                    onChange={(e) => setFromDate(e.target.value)}
                    className="px-3 py-1.5 rounded-lg border border-border bg-card text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
                />
                <span className="text-xs text-muted-foreground">to</span>
                <input
                    type="date"
                    value={toDate}
                    onChange={(e) => setToDate(e.target.value)}
                    className="px-3 py-1.5 rounded-lg border border-border bg-card text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
                />
                <StyledSelect
                    value={status}
                    onChange={setStatus}
                    className="w-36"
                    options={[
                        { value: "ALL", label: "All Status" },
                        { value: "DRAFT", label: "Draft" },
                        { value: "NOT_PAID", label: "Not Paid" },
                        { value: "PAID", label: "Paid" },
                    ]}
                />
                <button
                    onClick={loadStatements}
                    disabled={loading}
                    className="btn-gradient px-4 py-1.5 text-sm font-medium rounded-lg disabled:opacity-50"
                >
                    {loading ? <Loader2 className="w-4 h-4 animate-spin" /> : "Load"}
                </button>

                <div className="ml-auto flex gap-2">
                    <button
                        onClick={handleBulkGenerate}
                        disabled={isGenerating}
                        className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg bg-orange-500/10 text-orange-500 hover:bg-orange-500/20 transition-colors disabled:opacity-50"
                    >
                        {isGenerating ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <FileText className="w-3.5 h-3.5" />}
                        Generate All PDFs
                    </button>
                    <button
                        onClick={handleExcelDownload}
                        disabled={isDownloading}
                        className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg bg-green-500/10 text-green-500 hover:bg-green-500/20 transition-colors disabled:opacity-50"
                    >
                        {isDownloading ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <FileSpreadsheet className="w-3.5 h-3.5" />}
                        Excel
                    </button>
                </div>
            </div>

            {/* Summary Cards */}
            <div className="grid grid-cols-4 gap-3 mb-4">
                <GlassCard className="!p-3">
                    <div className="flex items-center gap-2">
                        <Receipt className="w-4 h-4 text-orange-500" />
                        <div>
                            <p className="text-[10px] text-muted-foreground uppercase font-bold">Statements</p>
                            <p className="text-lg font-bold">{totalStatements}</p>
                        </div>
                    </div>
                </GlassCard>
                <GlassCard className="!p-3">
                    <div className="flex items-center gap-2">
                        <IndianRupee className="w-4 h-4 text-blue-500" />
                        <div>
                            <p className="text-[10px] text-muted-foreground uppercase font-bold">Total Amount</p>
                            <p className="text-lg font-bold">{formatCurrency(totalAmount)}</p>
                        </div>
                    </div>
                </GlassCard>
                <GlassCard className="!p-3">
                    <div className="flex items-center gap-2">
                        <CheckCircle2 className="w-4 h-4 text-green-500" />
                        <div>
                            <p className="text-[10px] text-muted-foreground uppercase font-bold">Collected</p>
                            <p className="text-lg font-bold text-green-500">{formatCurrency(totalCollected)}</p>
                        </div>
                    </div>
                </GlassCard>
                <GlassCard className="!p-3">
                    <div className="flex items-center gap-2">
                        <TrendingUp className="w-4 h-4 text-red-500" />
                        <div>
                            <p className="text-[10px] text-muted-foreground uppercase font-bold">Outstanding</p>
                            <p className="text-lg font-bold text-red-500">{formatCurrency(totalOutstanding)}</p>
                        </div>
                    </div>
                </GlassCard>
            </div>

            {/* Table — fills remaining viewport */}
            <GlassCard className="flex-1 overflow-hidden !p-0 flex flex-col min-h-0">
                {loading ? (
                    <div className="flex items-center justify-center flex-1">
                        <div className="w-12 h-12 border-4 border-primary/20 border-t-primary rounded-full animate-spin" />
                    </div>
                ) : statements.length === 0 ? (
                    <div className="text-center py-12 text-muted-foreground flex-1 flex items-center justify-center">
                        No statements found for the selected period.
                    </div>
                ) : (
                    <div className="overflow-auto flex-1">
                        <table className="w-full text-sm">
                            <thead className="sticky top-0 bg-card z-10">
                                <tr className="border-b border-border">
                                    <th className="px-3 py-2.5 text-[10px] font-bold uppercase tracking-wider text-muted-foreground text-center w-10">#</th>
                                    <th className="px-3 py-2.5 text-[10px] font-bold uppercase tracking-wider text-muted-foreground cursor-pointer select-none" onClick={() => handleSort("statementNo")}>
                                        <span className="flex items-center gap-1">Statement No <SortIcon col="statementNo" /></span>
                                    </th>
                                    <th className="px-3 py-2.5 text-[10px] font-bold uppercase tracking-wider text-muted-foreground cursor-pointer select-none" onClick={() => handleSort("customerName")}>
                                        <span className="flex items-center gap-1">Customer <SortIcon col="customerName" /></span>
                                    </th>
                                    <th className="px-3 py-2.5 text-[10px] font-bold uppercase tracking-wider text-muted-foreground cursor-pointer select-none text-center" onClick={() => handleSort("categoryType")}>
                                        <span className="flex items-center justify-center gap-1">Category <SortIcon col="categoryType" /></span>
                                    </th>
                                    <th className="px-3 py-2.5 text-[10px] font-bold uppercase tracking-wider text-muted-foreground">Period</th>
                                    <th className="px-3 py-2.5 text-[10px] font-bold uppercase tracking-wider text-muted-foreground text-center">Bills</th>
                                    <th className="px-3 py-2.5 text-[10px] font-bold uppercase tracking-wider text-muted-foreground text-right cursor-pointer select-none" onClick={() => handleSort("netAmount")}>
                                        <span className="flex items-center justify-end gap-1">Net Amount <SortIcon col="netAmount" /></span>
                                    </th>
                                    <th className="px-3 py-2.5 text-[10px] font-bold uppercase tracking-wider text-muted-foreground text-right">Received</th>
                                    <th className="px-3 py-2.5 text-[10px] font-bold uppercase tracking-wider text-muted-foreground text-right">Balance</th>
                                    <th className="px-3 py-2.5 text-[10px] font-bold uppercase tracking-wider text-muted-foreground text-center">Status</th>
                                    <th className="px-3 py-2.5 text-[10px] font-bold uppercase tracking-wider text-muted-foreground text-center">Actions</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-border/30">
                                {sortedStatements.map((stmt, idx) => (
                                    <tr key={stmt.id} className="hover:bg-white/5 transition-colors">
                                        <td className="px-3 py-2 text-xs text-muted-foreground text-center">{idx + 1}</td>
                                        <td className="px-3 py-2 font-mono font-bold text-sm">{stmt.statementNo}</td>
                                        <td className="px-3 py-2 text-sm">{stmt.customer?.name || "-"}</td>
                                        <td className="px-3 py-2 text-center">{categoryBadge((stmt.customer as any)?.categoryType)}</td>
                                        <td className="px-3 py-2 text-xs text-muted-foreground">{stmt.fromDate} to {stmt.toDate}</td>
                                        <td className="px-3 py-2 text-center font-medium">{stmt.numberOfBills}</td>
                                        <td className="px-3 py-2 text-right font-bold">{formatCurrency(stmt.netAmount)}</td>
                                        <td className="px-3 py-2 text-right text-green-500">{formatCurrency(stmt.receivedAmount)}</td>
                                        <td className={`px-3 py-2 text-right font-bold ${stmt.balanceAmount > 0 ? "text-red-500" : "text-green-500"}`}>
                                            {formatCurrency(stmt.balanceAmount)}
                                        </td>
                                        <td className="px-3 py-2 text-center">{statusBadge(stmt.status)}</td>
                                        <td className="px-3 py-2 text-center">
                                            {generatingPdfId === stmt.id ? (
                                                <Loader2 className="w-4 h-4 animate-spin inline" />
                                            ) : stmt.statementPdfUrl ? (
                                                <button
                                                    onClick={() => handleDownloadPdf(stmt)}
                                                    className="inline-flex items-center gap-1 px-2 py-1 text-xs font-medium rounded bg-blue-500/10 text-blue-500 hover:bg-blue-500/20 transition-colors"
                                                >
                                                    <Download className="w-3.5 h-3.5" /> PDF
                                                </button>
                                            ) : (
                                                <button
                                                    onClick={() => handleGeneratePdf(stmt)}
                                                    className="inline-flex items-center gap-1 px-2 py-1 text-xs font-medium rounded bg-orange-500/10 text-orange-500 hover:bg-orange-500/20 transition-colors"
                                                >
                                                    <FileText className="w-3.5 h-3.5" /> Generate
                                                </button>
                                            )}
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}
            </GlassCard>
        </div>
    );
}
