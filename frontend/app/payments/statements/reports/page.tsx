"use client";

import { useState, useEffect } from "react";
import { GlassCard } from "@/components/ui/glass-card";
import { StyledSelect } from "@/components/ui/styled-select";
import {
    FileText, FileSpreadsheet, Download, CheckCircle2, Loader2,
    Calendar, IndianRupee, Receipt, TrendingUp
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
            if (!stmt.statementPdfUrl) {
                await generateStatementPdf(stmt.id!);
            }
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
                return <span className="px-2 py-0.5 text-xs font-medium rounded-full bg-green-500/10 text-green-600 dark:text-green-400">PAID</span>;
            case "NOT_PAID":
                return <span className="px-2 py-0.5 text-xs font-medium rounded-full bg-red-500/10 text-red-600 dark:text-red-400">NOT PAID</span>;
            default:
                return <span className="px-2 py-0.5 text-xs font-medium rounded-full bg-gray-500/10 text-gray-600 dark:text-gray-400">DRAFT</span>;
        }
    };

    return (
        <div className="space-y-6">
            {/* Header */}
            <div>
                <h1 className="text-2xl font-bold">
                    <span className="text-gradient">Statement Reports</span>
                </h1>
                <p className="text-muted-foreground text-sm mt-1">
                    Download and manage generated statement PDFs and Excel exports.
                </p>
            </div>

            {/* Filter Bar */}
            <GlassCard>
                <div className="flex items-center gap-4 flex-wrap">
                    <Calendar className="w-5 h-5 text-muted-foreground" />
                    <div className="flex items-center gap-2">
                        <label className="text-sm text-muted-foreground">From</label>
                        <input
                            type="date"
                            value={fromDate}
                            onChange={(e) => setFromDate(e.target.value)}
                            className="px-3 py-1.5 rounded-md border border-input bg-background text-sm"
                        />
                    </div>
                    <div className="flex items-center gap-2">
                        <label className="text-sm text-muted-foreground">To</label>
                        <input
                            type="date"
                            value={toDate}
                            onChange={(e) => setToDate(e.target.value)}
                            className="px-3 py-1.5 rounded-md border border-input bg-background text-sm"
                        />
                    </div>
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
                        className="px-4 py-1.5 text-sm font-medium rounded-md bg-orange-500 text-white hover:bg-orange-600 transition-colors disabled:opacity-50"
                    >
                        {loading ? <Loader2 className="w-4 h-4 animate-spin" /> : "Load"}
                    </button>
                </div>
            </GlassCard>

            {/* Summary Cards */}
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                <GlassCard>
                    <div className="flex items-center gap-3">
                        <div className="p-2 rounded-lg bg-orange-500/10">
                            <Receipt className="w-5 h-5 text-orange-500" />
                        </div>
                        <div>
                            <p className="text-xs text-muted-foreground">Total Statements</p>
                            <p className="text-xl font-bold">{totalStatements}</p>
                        </div>
                    </div>
                </GlassCard>
                <GlassCard>
                    <div className="flex items-center gap-3">
                        <div className="p-2 rounded-lg bg-blue-500/10">
                            <IndianRupee className="w-5 h-5 text-blue-500" />
                        </div>
                        <div>
                            <p className="text-xs text-muted-foreground">Total Amount</p>
                            <p className="text-xl font-bold">{formatCurrency(totalAmount)}</p>
                        </div>
                    </div>
                </GlassCard>
                <GlassCard>
                    <div className="flex items-center gap-3">
                        <div className="p-2 rounded-lg bg-green-500/10">
                            <CheckCircle2 className="w-5 h-5 text-green-500" />
                        </div>
                        <div>
                            <p className="text-xs text-muted-foreground">Collected</p>
                            <p className="text-xl font-bold text-green-600 dark:text-green-400">{formatCurrency(totalCollected)}</p>
                        </div>
                    </div>
                </GlassCard>
                <GlassCard>
                    <div className="flex items-center gap-3">
                        <div className="p-2 rounded-lg bg-red-500/10">
                            <TrendingUp className="w-5 h-5 text-red-500" />
                        </div>
                        <div>
                            <p className="text-xs text-muted-foreground">Outstanding</p>
                            <p className="text-xl font-bold text-red-600 dark:text-red-400">{formatCurrency(totalOutstanding)}</p>
                        </div>
                    </div>
                </GlassCard>
            </div>

            {/* Bulk Actions */}
            <div className="flex gap-3">
                <button
                    onClick={handleBulkGenerate}
                    disabled={isGenerating}
                    className="flex items-center gap-2 px-4 py-2 text-sm font-medium rounded-md bg-orange-500/10 text-orange-600 dark:text-orange-400 hover:bg-orange-500/20 transition-colors disabled:opacity-50"
                >
                    {isGenerating ? <Loader2 className="w-4 h-4 animate-spin" /> : <FileText className="w-4 h-4" />}
                    Generate All PDFs
                </button>
                <button
                    onClick={handleExcelDownload}
                    disabled={isDownloading}
                    className="flex items-center gap-2 px-4 py-2 text-sm font-medium rounded-md bg-green-500/10 text-green-600 dark:text-green-400 hover:bg-green-500/20 transition-colors disabled:opacity-50"
                >
                    {isDownloading ? <Loader2 className="w-4 h-4 animate-spin" /> : <FileSpreadsheet className="w-4 h-4" />}
                    Download Excel
                </button>
            </div>

            {/* Table */}
            <GlassCard>
                {loading ? (
                    <div className="flex items-center justify-center py-12">
                        <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
                    </div>
                ) : statements.length === 0 ? (
                    <div className="text-center py-12 text-muted-foreground">
                        No statements found for the selected period.
                    </div>
                ) : (
                    <div className="overflow-x-auto">
                        <table className="w-full text-sm">
                            <thead>
                                <tr className="border-b border-border text-left">
                                    <th className="px-3 py-2 text-xs font-medium text-muted-foreground">#</th>
                                    <th className="px-3 py-2 text-xs font-medium text-muted-foreground">Statement No</th>
                                    <th className="px-3 py-2 text-xs font-medium text-muted-foreground">Customer</th>
                                    <th className="px-3 py-2 text-xs font-medium text-muted-foreground">Period</th>
                                    <th className="px-3 py-2 text-xs font-medium text-muted-foreground text-center">Bills</th>
                                    <th className="px-3 py-2 text-xs font-medium text-muted-foreground text-right">Net Amount</th>
                                    <th className="px-3 py-2 text-xs font-medium text-muted-foreground text-right">Received</th>
                                    <th className="px-3 py-2 text-xs font-medium text-muted-foreground text-right">Balance</th>
                                    <th className="px-3 py-2 text-xs font-medium text-muted-foreground text-center">Status</th>
                                    <th className="px-3 py-2 text-xs font-medium text-muted-foreground text-center">Actions</th>
                                </tr>
                            </thead>
                            <tbody>
                                {statements.map((stmt, idx) => (
                                    <tr key={stmt.id} className="border-b border-border/50 hover:bg-muted/30 transition-colors">
                                        <td className="px-3 py-2 text-muted-foreground">{idx + 1}</td>
                                        <td className="px-3 py-2 font-medium">{stmt.statementNo}</td>
                                        <td className="px-3 py-2">{stmt.customer?.name || "-"}</td>
                                        <td className="px-3 py-2 text-muted-foreground text-xs">
                                            {stmt.fromDate} to {stmt.toDate}
                                        </td>
                                        <td className="px-3 py-2 text-center">{stmt.numberOfBills}</td>
                                        <td className="px-3 py-2 text-right font-medium">{formatCurrency(stmt.netAmount)}</td>
                                        <td className="px-3 py-2 text-right text-green-600 dark:text-green-400">
                                            {formatCurrency(stmt.receivedAmount)}
                                        </td>
                                        <td className={`px-3 py-2 text-right font-medium ${stmt.balanceAmount > 0 ? "text-red-600 dark:text-red-400" : "text-green-600 dark:text-green-400"}`}>
                                            {formatCurrency(stmt.balanceAmount)}
                                        </td>
                                        <td className="px-3 py-2 text-center">{statusBadge(stmt.status)}</td>
                                        <td className="px-3 py-2 text-center">
                                            {generatingPdfId === stmt.id ? (
                                                <Loader2 className="w-4 h-4 animate-spin inline" />
                                            ) : stmt.statementPdfUrl ? (
                                                <button
                                                    onClick={() => handleDownloadPdf(stmt)}
                                                    className="inline-flex items-center gap-1 px-2 py-1 text-xs font-medium rounded bg-blue-500/10 text-blue-600 dark:text-blue-400 hover:bg-blue-500/20 transition-colors"
                                                    title="Download PDF"
                                                >
                                                    <Download className="w-3.5 h-3.5" />
                                                    PDF
                                                </button>
                                            ) : (
                                                <button
                                                    onClick={() => handleGeneratePdf(stmt)}
                                                    className="inline-flex items-center gap-1 px-2 py-1 text-xs font-medium rounded bg-orange-500/10 text-orange-600 dark:text-orange-400 hover:bg-orange-500/20 transition-colors"
                                                    title="Generate PDF"
                                                >
                                                    <FileText className="w-3.5 h-3.5" />
                                                    Generate
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
