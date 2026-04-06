"use client";

import { useState, useEffect } from "react";
import { TablePagination } from "@/components/ui/table-pagination";
import { StyledSelect } from "@/components/ui/styled-select";
import { GlassCard } from "@/components/ui/glass-card";
import { Badge } from "@/components/ui/badge";
import { Modal } from "@/components/ui/modal";
import { CustomerAutocomplete } from "@/components/ui/customer-autocomplete";
import { Fragment } from "react";
import {
    Plus, Eye, Trash2, Calendar, User, Filter, Search, FileText, Download, Loader2,
    FileClock, FileCheck2, Receipt, TrendingUp, IndianRupee, Percent, ChevronDown, ChevronRight,
    CheckCircle2, Zap
} from "lucide-react";
import {
    getStatements, generateStatement, getStatementBills,
    getCustomers, deleteStatement, removeBillFromStatement,
    getVehiclesByCustomer, getProducts, previewStatementBills,
    generateStatementPdf, getStatementPdfUrl, getStatementStats,
    approveStatement, autoGenerateStatementDrafts,
    updateCustomerCreditLimits, updateVehicleLiterLimit,
    type Statement, type InvoiceBill, type Customer, type Vehicle,
    type Product, type PageResponse, type StatementStats
} from "@/lib/api/station";
import { PermissionGate } from "@/components/permission-gate";

export default function StatementsPage() {
    const [statements, setStatements] = useState<Statement[]>([]);
    const [customers, setCustomers] = useState<Customer[]>([]);
    const [loading, setLoading] = useState(true);

    // Pagination
    const [page, setPage] = useState(0);
    const [pageSize] = useState(7);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);

    // Generate form
    const [showGenerateModal, setShowGenerateModal] = useState(false);
    const [selectedCustomerId, setSelectedCustomerId] = useState<number | "">("");
    const [fromDate, setFromDate] = useState("");
    const [toDate, setToDate] = useState("");
    const [generating, setGenerating] = useState(false);
    const [error, setError] = useState("");

    // Filters for generation
    const [vehicles, setVehicles] = useState<Vehicle[]>([]);
    const [products, setProducts] = useState<Product[]>([]);
    const [filterVehicleId, setFilterVehicleId] = useState<number | "">("");
    const [filterProductId, setFilterProductId] = useState<number | "">("");

    // Preview bills
    const [previewBills, setPreviewBills] = useState<InvoiceBill[]>([]);
    const [showPreview, setShowPreview] = useState(false);
    const [previewing, setPreviewing] = useState(false);
    const [generatingPdfId, setGeneratingPdfId] = useState<number | null>(null);
    const [selectedBillIds, setSelectedBillIds] = useState<Set<number>>(new Set());
    const [useBillSelection, setUseBillSelection] = useState(false);

    // Detail view
    const [showDetailModal, setShowDetailModal] = useState(false);
    const [detailStatement, setDetailStatement] = useState<Statement | null>(null);
    const [detailBills, setDetailBills] = useState<InvoiceBill[]>([]);

    // Filter
    const [filterStatus, setFilterStatus] = useState<string>("ALL");
    const [filterCategory, setFilterCategory] = useState<string>("");
    const [tableSearch, setTableSearch] = useState("");
    const [searchInput, setSearchInput] = useState("");
    const [filterFromDate, setFilterFromDate] = useState("");
    const [filterToDate, setFilterToDate] = useState("");
    const [searchTimeout, setSearchTimeout] = useState<NodeJS.Timeout | null>(null);
    const [stats, setStats] = useState<StatementStats | null>(null);
    const [expandedBillId, setExpandedBillId] = useState<number | null>(null);
    const [pdfError, setPdfError] = useState("");

    // Set as Limit
    const [setLimitSuccess, setSetLimitSuccess] = useState("");

    useEffect(() => {
        loadCustomers();
        loadProducts();
        loadStats();
    }, []);

    useEffect(() => {
        loadStatements();
    }, [page, filterStatus, filterCategory, filterFromDate, filterToDate, tableSearch]);

    // Load vehicles when customer changes in generate modal
    useEffect(() => {
        if (selectedCustomerId) {
            getVehiclesByCustomer(Number(selectedCustomerId))
                .then(setVehicles)
                .catch(() => setVehicles([]));
        } else {
            setVehicles([]);
        }
        // Reset filters when customer changes
        setFilterVehicleId("");
        setShowPreview(false);
        setPreviewBills([]);
        setSelectedBillIds(new Set());
        setUseBillSelection(false);
    }, [selectedCustomerId]);

    const loadCustomers = async () => {
        try {
            const custs = await getCustomers(undefined, 1000);
            setCustomers(Array.isArray(custs) ? custs : custs.content || []);
        } catch (e) {
            console.error("Failed to load customers", e);
        }
    };

    const loadProducts = async () => {
        try {
            setProducts(await getProducts());
        } catch (e) {
            console.error("Failed to load products", e);
        }
    };

    const loadStats = async () => {
        try {
            setStats(await getStatementStats());
        } catch (e) {
            console.error("Failed to load stats", e);
        }
    };

    const loadStatements = async () => {
        setLoading(true);
        try {
            const statusParam = filterStatus === "ALL" ? undefined : filterStatus;
            const result: PageResponse<Statement> = await getStatements(
                page, pageSize, undefined, statusParam,
                filterFromDate || undefined, filterToDate || undefined,
                tableSearch || undefined, filterCategory || undefined
            );
            setStatements(result.content);
            setTotalPages(result.totalPages);
            setTotalElements(result.totalElements);
        } catch (e) {
            console.error("Failed to load statements", e);
        } finally {
            setLoading(false);
        }
    };

    const handlePreview = async () => {
        if (!selectedCustomerId || !fromDate || !toDate) {
            setError("Please select customer and date range to preview");
            return;
        }
        setPreviewing(true);
        setError("");
        try {
            const bills = await previewStatementBills(
                Number(selectedCustomerId), fromDate, toDate,
                {
                    vehicleId: filterVehicleId ? Number(filterVehicleId) : undefined,
                    productId: filterProductId ? Number(filterProductId) : undefined,
                }
            );
            setPreviewBills(bills);
            setShowPreview(true);
            // For BILL_WISE customers, start with no bills selected so user picks individually
            const selectedCustomer = customers.find(c => c.id === Number(selectedCustomerId));
            if (selectedCustomer?.statementGrouping === "BILL_WISE") {
                setSelectedBillIds(new Set());
                setUseBillSelection(true);
            } else {
                setSelectedBillIds(new Set(bills.map(b => b.id!)));
                setUseBillSelection(false);
            }
        } catch (e: any) {
            setError(e.message || "Failed to preview bills");
        } finally {
            setPreviewing(false);
        }
    };

    const toggleBillSelection = (billId: number) => {
        setUseBillSelection(true);
        setSelectedBillIds(prev => {
            const next = new Set(prev);
            if (next.has(billId)) next.delete(billId);
            else next.add(billId);
            return next;
        });
    };

    const toggleAllBills = () => {
        if (selectedBillIds.size === previewBills.length) {
            setSelectedBillIds(new Set());
            setUseBillSelection(true);
        } else {
            setSelectedBillIds(new Set(previewBills.map(b => b.id!)));
            setUseBillSelection(false);
        }
    };

    const handleGenerate = async () => {
        if (!selectedCustomerId || !fromDate || !toDate) {
            setError("Please fill all required fields");
            return;
        }
        if (useBillSelection && selectedBillIds.size === 0) {
            setError("Please select at least one bill");
            return;
        }
        setGenerating(true);
        setError("");
        try {
            const filters: { vehicleId?: number; productId?: number; billIds?: number[] } = {};

            if (useBillSelection && selectedBillIds.size > 0) {
                // Bill-wise: user deselected some bills
                filters.billIds = Array.from(selectedBillIds);
            } else {
                // Filter-based generation
                if (filterVehicleId) filters.vehicleId = Number(filterVehicleId);
                if (filterProductId) filters.productId = Number(filterProductId);
            }

            await generateStatement(Number(selectedCustomerId), fromDate, toDate, filters);
            resetGenerateModal();
            loadStatements();
            loadStats();
        } catch (e: any) {
            setError(e.message || "Failed to generate statement");
        } finally {
            setGenerating(false);
        }
    };

    const resetGenerateModal = () => {
        setShowGenerateModal(false);
        setSelectedCustomerId("");
        setFromDate("");
        setToDate("");
        setFilterVehicleId("");
        setFilterProductId("");
        setShowPreview(false);
        setPreviewBills([]);
        setSelectedBillIds(new Set());
        setUseBillSelection(false);
        setError("");
    };

    const handleViewDetail = async (stmt: Statement) => {
        setDetailStatement(stmt);
        try {
            const bills = await getStatementBills(stmt.id!);
            setDetailBills(bills);
            setShowDetailModal(true);
        } catch (e) {
            console.error("Failed to load statement bills", e);
        }
    };

    const handleDelete = async (id: number) => {
        if (!confirm("Delete this statement? Bills will be unlinked.")) return;
        try {
            await deleteStatement(id);
            loadStatements();
            loadStats();
        } catch (e) {
            console.error("Failed to delete statement", e);
        }
    };

    const [approvingId, setApprovingId] = useState<number | null>(null);
    const [autoGenerating, setAutoGenerating] = useState(false);

    const handleApprove = async (id: number) => {
        if (!confirm("Approve this draft statement? A PDF will be generated.")) return;
        setApprovingId(id);
        try {
            await approveStatement(id);
            loadStatements();
            loadStats();
        } catch (e: any) {
            setPdfError(e.message || "Failed to approve statement");
            setTimeout(() => setPdfError(""), 4000);
        } finally {
            setApprovingId(null);
        }
    };

    const handleAutoGenerate = async () => {
        if (!confirm("Auto-generate draft statements for all eligible customers?")) return;
        setAutoGenerating(true);
        try {
            const result = await autoGenerateStatementDrafts();
            if (result.count > 0) {
                loadStatements();
                loadStats();
            }
            alert(`${result.count} draft statement(s) created.`);
        } catch (e: any) {
            setPdfError(e.message || "Failed to auto-generate drafts");
            setTimeout(() => setPdfError(""), 4000);
        } finally {
            setAutoGenerating(false);
        }
    };

    const handleRemoveBill = async (statementId: number, billId: number) => {
        if (!confirm("Remove this bill from the statement?")) return;
        try {
            const updated = await removeBillFromStatement(statementId, billId);
            setDetailStatement(updated);
            const bills = await getStatementBills(statementId);
            setDetailBills(bills);
            loadStatements();
        } catch (e) {
            console.error("Failed to remove bill", e);
        }
    };

    const handleFilterChange = (status: string) => {
        setFilterStatus(status);
        setPage(0); // reset to first page on filter change
    };

    const previewTotal = previewBills
        .filter(b => selectedBillIds.has(b.id!))
        .reduce((sum, b) => sum + Number(b.netAmount || 0), 0);

    if (loading) {
        return (
            <div className="p-8 flex items-center justify-center h-screen">
                <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary" />
            </div>
        );
    }

    return (
        <div className="p-6 bg-background transition-colors duration-300">
            <div className="max-w-7xl mx-auto">
                {/* Header */}
                <div className="flex justify-between items-center mb-8">
                    <div>
                        <h1 className="text-4xl font-bold text-foreground tracking-tight">
                            Credit <span className="text-gradient">Statements</span>
                        </h1>
                        <p className="text-muted-foreground mt-2">
                            Generate and manage monthly credit statements for statement customers.
                        </p>
                    </div>
                    <PermissionGate permission="PAYMENT_CREATE">
                        <div className="flex items-center gap-3">
                            <button
                                onClick={handleAutoGenerate}
                                disabled={autoGenerating}
                                className="px-5 py-3 rounded-xl font-medium flex items-center gap-2 border border-border bg-card hover:bg-muted transition-colors text-foreground disabled:opacity-50"
                            >
                                {autoGenerating ? <Loader2 className="w-4 h-4 animate-spin" /> : <Zap className="w-4 h-4" />}
                                Auto-Generate Drafts
                            </button>
                            <button
                                onClick={() => setShowGenerateModal(true)}
                                className="btn-gradient px-6 py-3 rounded-xl font-medium flex items-center gap-2"
                            >
                                <Plus className="w-5 h-5" />
                                Generate Statement
                            </button>
                        </div>
                    </PermissionGate>
                </div>

                {/* Filters */}
                <div className="flex flex-wrap gap-3 items-center mb-6">
                    <div className="relative flex-1 min-w-[200px] max-w-md">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                        <input
                            type="text"
                            placeholder="Search by customer name or statement no..."
                            value={searchInput}
                            onChange={(e) => {
                                const val = e.target.value;
                                setSearchInput(val);
                                if (searchTimeout) clearTimeout(searchTimeout);
                                setSearchTimeout(setTimeout(() => {
                                    setTableSearch(val);
                                    setPage(0);
                                }, 400));
                            }}
                            className="w-full pl-10 pr-4 py-2 bg-card border border-border rounded-xl text-foreground text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                        />
                    </div>
                    <div className="flex items-center gap-2">
                        <Calendar className="w-4 h-4 text-muted-foreground" />
                        <input
                            type="date"
                            value={filterFromDate}
                            onChange={(e) => { setFilterFromDate(e.target.value); setPage(0); }}
                            className="px-3 py-2 bg-card border border-border rounded-xl text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
                            placeholder="From"
                        />
                        <span className="text-muted-foreground text-sm">to</span>
                        <input
                            type="date"
                            value={filterToDate}
                            onChange={(e) => { setFilterToDate(e.target.value); setPage(0); }}
                            className="px-3 py-2 bg-card border border-border rounded-xl text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
                            placeholder="To"
                        />
                    </div>
                    <StyledSelect
                        value={filterCategory}
                        onChange={(val) => { setFilterCategory(val); setPage(0); }}
                        options={[
                            { value: "", label: "All Categories" },
                            { value: "GOVERNMENT", label: "Government" },
                            { value: "NON_GOVERNMENT", label: "Non-Government" },
                        ]}
                    />
                    <StyledSelect
                        value={filterStatus}
                        onChange={(val) => handleFilterChange(val)}
                        options={[
                            { value: "ALL", label: "All Status" },
                            { value: "DRAFT", label: "Draft" },
                            { value: "NOT_PAID", label: "Outstanding" },
                            { value: "PAID", label: "Paid" },
                        ]}
                    />
                </div>

                {/* PDF Error Toast */}
                {pdfError && (
                    <div className="mb-4 bg-rose-500/20 border border-rose-500/30 text-rose-400 px-4 py-2 rounded-lg text-sm">
                        {pdfError}
                    </div>
                )}

                {/* Table */}
                <GlassCard>
                    <div className="overflow-x-auto">
                        <table className="w-full text-sm">
                            <thead>
                                <tr className="border-b border-border text-muted-foreground">
                                    <th className="text-left py-3 px-4">Statement No</th>
                                    <th className="text-left py-3 px-4">Customer</th>
                                    <th className="text-left py-3 px-4">Date</th>
                                    <th className="text-right py-3 px-4">Bills</th>
                                    <th className="text-right py-3 px-4">Net Amount</th>
                                    <th className="text-right py-3 px-4">Received</th>
                                    <th className="text-right py-3 px-4">Balance</th>
                                    <th className="text-center py-3 px-4">Status</th>
                                    <th className="text-center py-3 px-4">Actions</th>
                                </tr>
                            </thead>
                            <tbody>
                                {statements.length === 0 ? (
                                    <tr>
                                        <td colSpan={9} className="text-center py-8 text-muted-foreground">
                                            No statements found
                                        </td>
                                    </tr>
                                ) : (
                                    statements.map((stmt) => (
                                        <tr key={stmt.id} className="border-b border-border/50 hover:bg-muted/50 transition-colors">
                                            <td className="py-3 px-4 font-mono font-semibold">{stmt.statementNo}</td>
                                            <td className="py-3 px-4">{stmt.customer?.name || "-"}</td>
                                            <td className="py-3 px-4 text-muted-foreground">{stmt.statementDate}</td>
                                            <td className="py-3 px-4 text-right">{stmt.numberOfBills}</td>
                                            <td className="py-3 px-4 text-right font-medium">
                                                {Number(stmt.netAmount).toLocaleString("en-IN", { minimumFractionDigits: 2 })}
                                            </td>
                                            <td className="py-3 px-4 text-right text-emerald-400">
                                                {Number(stmt.receivedAmount).toLocaleString("en-IN", { minimumFractionDigits: 2 })}
                                            </td>
                                            <td className="py-3 px-4 text-right font-medium text-amber-400">
                                                {Number(stmt.balanceAmount).toLocaleString("en-IN", { minimumFractionDigits: 2 })}
                                            </td>
                                            <td className="py-3 px-4 text-center whitespace-nowrap">
                                                <Badge variant={stmt.status === "PAID" ? "success" : stmt.status === "DRAFT" ? "outline" : "warning"}>
                                                    {stmt.status === "PAID" ? "PAID" : stmt.status === "DRAFT" ? "DRAFT" : "NOT PAID"}
                                                </Badge>
                                            </td>
                                            <td className="py-3 px-4 text-center">
                                                <div className="flex items-center justify-center gap-2">
                                                    <button
                                                        onClick={() => handleViewDetail(stmt)}
                                                        className="p-1.5 rounded-md hover:bg-primary/20 text-primary transition-colors"
                                                        title="View Details"
                                                    >
                                                        <Eye className="w-4 h-4" />
                                                    </button>
                                                    {stmt.statementPdfUrl ? (
                                                        <button
                                                            onClick={async () => {
                                                                try {
                                                                    const url = await getStatementPdfUrl(stmt.id!);
                                                                    window.open(url, "_blank");
                                                                } catch (e: any) {
                                                                    setPdfError(e.message || "Failed to download PDF");
                                                                    setTimeout(() => setPdfError(""), 4000);
                                                                }
                                                            }}
                                                            className="p-1.5 rounded-md hover:bg-primary/20 text-primary transition-colors"
                                                            title="Download PDF"
                                                        >
                                                            <Download className="w-4 h-4" />
                                                        </button>
                                                    ) : (
                                                        <button
                                                            disabled={generatingPdfId === stmt.id}
                                                            onClick={async () => {
                                                                setGeneratingPdfId(stmt.id!);
                                                                try {
                                                                    await generateStatementPdf(stmt.id!);
                                                                    loadStatements();
                                                                } catch (e: any) {
                                                                    setPdfError(e.message || "Failed to generate PDF");
                                                                    setTimeout(() => setPdfError(""), 4000);
                                                                } finally {
                                                                    setGeneratingPdfId(null);
                                                                }
                                                            }}
                                                            className="p-1.5 rounded-md hover:bg-primary/20 text-muted-foreground hover:text-primary transition-colors"
                                                            title="Generate PDF"
                                                        >
                                                            {generatingPdfId === stmt.id ? (
                                                                <Loader2 className="w-4 h-4 animate-spin" />
                                                            ) : (
                                                                <FileText className="w-4 h-4" />
                                                            )}
                                                        </button>
                                                    )}
                                                    {stmt.status === "DRAFT" && (
                                                        <PermissionGate permission="PAYMENT_UPDATE">
                                                            <button
                                                                onClick={() => handleApprove(stmt.id!)}
                                                                disabled={approvingId === stmt.id}
                                                                className="p-1.5 rounded-md hover:bg-emerald-500/20 text-emerald-400 transition-colors disabled:opacity-50"
                                                                title="Approve Draft"
                                                            >
                                                                {approvingId === stmt.id ? (
                                                                    <Loader2 className="w-4 h-4 animate-spin" />
                                                                ) : (
                                                                    <CheckCircle2 className="w-4 h-4" />
                                                                )}
                                                            </button>
                                                        </PermissionGate>
                                                    )}
                                                    {stmt.status !== "PAID" && (
                                                        <PermissionGate permission="PAYMENT_DELETE">
                                                            <button
                                                                onClick={() => handleDelete(stmt.id!)}
                                                                className="p-1.5 rounded-md hover:bg-rose-500/20 text-rose-400 transition-colors"
                                                                title="Delete"
                                                            >
                                                                <Trash2 className="w-4 h-4" />
                                                            </button>
                                                        </PermissionGate>
                                                    )}
                                                </div>
                                            </td>
                                        </tr>
                                    ))
                                )}
                            </tbody>
                        </table>
                    </div>

                    <TablePagination page={page} totalPages={totalPages} totalElements={totalElements} pageSize={pageSize} onPageChange={setPage} />
                </GlassCard>

                {/* Statement Analytics */}
                {stats && (
                    <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-4 mt-6">
                        <GlassCard>
                            <div className="flex justify-between items-start">
                                <div>
                                    <p className="text-[10px] uppercase tracking-wider text-muted-foreground font-bold">Last Month</p>
                                    <h3 className="text-2xl font-bold text-blue-500 mt-2">{stats.statementsLastMonth}</h3>
                                    <p className="text-xs text-muted-foreground mt-1">Statements generated</p>
                                </div>
                                <div className="p-2 bg-blue-500/10 rounded-lg">
                                    <FileClock className="w-5 h-5 text-blue-500" />
                                </div>
                            </div>
                        </GlassCard>

                        <GlassCard>
                            <div className="flex justify-between items-start">
                                <div>
                                    <p className="text-[10px] uppercase tracking-wider text-muted-foreground font-bold">Paid Last Month</p>
                                    <h3 className="text-2xl font-bold text-green-500 mt-2">{stats.paidLastMonth}</h3>
                                    <p className="text-xs text-muted-foreground mt-1">Payments received</p>
                                </div>
                                <div className="p-2 bg-green-500/10 rounded-lg">
                                    <FileCheck2 className="w-5 h-5 text-green-500" />
                                </div>
                            </div>
                        </GlassCard>

                        <GlassCard>
                            <div className="flex justify-between items-start">
                                <div>
                                    <p className="text-[10px] uppercase tracking-wider text-muted-foreground font-bold">Total Statements</p>
                                    <h3 className="text-2xl font-bold text-cyan-500 mt-2">{stats.totalStatements.toLocaleString("en-IN")}</h3>
                                    <p className="text-xs text-muted-foreground mt-1">All time</p>
                                </div>
                                <div className="p-2 bg-cyan-500/10 rounded-lg">
                                    <Receipt className="w-5 h-5 text-cyan-500" />
                                </div>
                            </div>
                        </GlassCard>

                        <GlassCard>
                            <div className="flex justify-between items-start">
                                <div>
                                    <p className="text-[10px] uppercase tracking-wider text-muted-foreground font-bold">Paid</p>
                                    <h3 className="text-2xl font-bold text-emerald-500 mt-2">{stats.totalPaid.toLocaleString("en-IN")}</h3>
                                    <p className="text-xs text-muted-foreground mt-1">{stats.paidPercentage.toFixed(1)}% of total</p>
                                </div>
                                <div className="p-2 bg-emerald-500/10 rounded-lg">
                                    <TrendingUp className="w-5 h-5 text-emerald-500" />
                                </div>
                            </div>
                        </GlassCard>

                        <GlassCard>
                            <div className="flex justify-between items-start">
                                <div>
                                    <p className="text-[10px] uppercase tracking-wider text-muted-foreground font-bold">Unpaid Amount</p>
                                    <h3 className="text-2xl font-bold text-amber-500 mt-2">
                                        ₹{Number(stats.totalUnpaidAmount).toLocaleString("en-IN", { maximumFractionDigits: 0 })}
                                    </h3>
                                    <p className="text-xs text-muted-foreground mt-1">Outstanding balance</p>
                                </div>
                                <div className="p-2 bg-amber-500/10 rounded-lg">
                                    <IndianRupee className="w-5 h-5 text-amber-500" />
                                </div>
                            </div>
                        </GlassCard>

                        <GlassCard>
                            <div className="flex justify-between items-start">
                                <div>
                                    <p className="text-[10px] uppercase tracking-wider text-muted-foreground font-bold">Collection Rate</p>
                                    <h3 className="text-2xl font-bold text-purple-500 mt-2">{stats.collectionRate.toFixed(1)}%</h3>
                                    <p className="text-xs text-muted-foreground mt-1">
                                        Avg ₹{Number(stats.avgStatementAmount).toLocaleString("en-IN", { maximumFractionDigits: 0 })}
                                    </p>
                                </div>
                                <div className="p-2 bg-purple-500/10 rounded-lg">
                                    <Percent className="w-5 h-5 text-purple-500" />
                                </div>
                            </div>
                        </GlassCard>
                    </div>
                )}
            </div>

            {/* Generate Statement Modal */}
            <Modal
                isOpen={showGenerateModal}
                onClose={resetGenerateModal}
                title="Generate Statement"
            >
                <div className="p-6 space-y-4 max-h-[85vh] overflow-y-auto">
                    {error && (
                        <div className="bg-rose-500/20 border border-rose-500/30 text-rose-400 px-4 py-2 rounded-lg text-sm">
                            {error}
                        </div>
                    )}

                    {/* Customer */}
                    <div>
                        <label className="block text-sm font-medium text-muted-foreground mb-1">
                            <User className="w-4 h-4 inline mr-1" />Customer
                        </label>
                        <CustomerAutocomplete
                            value={selectedCustomerId}
                            onChange={(id) => setSelectedCustomerId(id ? Number(id) : "")}
                            customers={customers}
                            placeholder="Search customer..."
                        />
                    </div>

                    {/* Date Range */}
                    <div className="grid grid-cols-2 gap-4">
                        <div>
                            <label className="block text-sm font-medium text-muted-foreground mb-1">
                                <Calendar className="w-4 h-4 inline mr-1" />From Date
                            </label>
                            <input
                                type="date"
                                value={fromDate}
                                onChange={(e) => { setFromDate(e.target.value); setShowPreview(false); }}
                                className="w-full px-4 py-2 bg-card border border-border rounded-lg text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                            />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-muted-foreground mb-1">
                                <Calendar className="w-4 h-4 inline mr-1" />To Date
                            </label>
                            <input
                                type="date"
                                value={toDate}
                                onChange={(e) => { setToDate(e.target.value); setShowPreview(false); }}
                                className="w-full px-4 py-2 bg-card border border-border rounded-lg text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                            />
                        </div>
                    </div>

                    {/* Optional Filters */}
                    <div className="border border-border/50 rounded-lg p-4 space-y-3">
                        <div className="flex items-center gap-2 text-sm font-medium text-muted-foreground">
                            <Filter className="w-4 h-4" />
                            Filters (Optional)
                        </div>
                        <div className="grid grid-cols-2 gap-4">
                            <div>
                                <label className="block text-xs text-muted-foreground mb-1">Vehicle</label>
                                <StyledSelect
                                    value={String(filterVehicleId)}
                                    onChange={(val) => { setFilterVehicleId(val ? Number(val) : ""); setShowPreview(false); }}
                                    options={[
                                        { value: "", label: "All Vehicles" },
                                        ...vehicles.map(v => ({ value: String(v.id), label: v.vehicleNumber })),
                                    ]}
                                    className="w-full"
                                />
                            </div>
                            <div>
                                <label className="block text-xs text-muted-foreground mb-1">Product</label>
                                <StyledSelect
                                    value={String(filterProductId)}
                                    onChange={(val) => { setFilterProductId(val ? Number(val) : ""); setShowPreview(false); }}
                                    options={[
                                        { value: "", label: "All Products" },
                                        ...products.map(p => ({ value: String(p.id), label: p.name })),
                                    ]}
                                    className="w-full"
                                />
                            </div>
                        </div>
                    </div>

                    {/* Preview Button */}
                    <button
                        onClick={handlePreview}
                        disabled={previewing || !selectedCustomerId || !fromDate || !toDate}
                        className="w-full px-4 py-2 rounded-lg border border-primary/50 text-primary hover:bg-primary/10 transition-colors font-medium text-sm flex items-center justify-center gap-2 disabled:opacity-50"
                    >
                        <Search className="w-4 h-4" />
                        {previewing ? "Loading..." : "Preview Bills"}
                    </button>

                    {/* Preview Bills Table */}
                    {showPreview && (
                        <div className="border border-border/50 rounded-lg overflow-hidden">
                            <div className="bg-muted/30 px-4 py-2 flex items-center justify-between">
                                <span className="text-sm font-medium text-foreground">
                                    {previewBills.length} bill{previewBills.length !== 1 ? "s" : ""} found
                                </span>
                                <span className="text-sm font-semibold text-primary">
                                    Selected Total: {previewTotal.toLocaleString("en-IN", { style: "currency", currency: "INR" })}
                                </span>
                            </div>
                            {previewBills.length === 0 ? (
                                <div className="px-4 py-6 text-center text-muted-foreground text-sm">
                                    No unlinked credit bills match the selected filters
                                </div>
                            ) : (
                                <div className="max-h-60 overflow-y-auto">
                                    <table className="w-full text-xs">
                                        <thead>
                                            <tr className="border-b border-border text-muted-foreground sticky top-0 bg-card">
                                                <th className="py-2 px-3 text-left">
                                                    <input
                                                        type="checkbox"
                                                        checked={selectedBillIds.size === previewBills.length}
                                                        onChange={toggleAllBills}
                                                        className="rounded border-border"
                                                    />
                                                </th>
                                                <th className="py-2 px-3 text-left">Date</th>
                                                <th className="py-2 px-3 text-left">Vehicle</th>
                                                <th className="py-2 px-3 text-left">Products</th>
                                                <th className="py-2 px-3 text-left">Indent No</th>
                                                <th className="py-2 px-3 text-right">Amount</th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            {previewBills.map(bill => (
                                                <tr
                                                    key={bill.id}
                                                    className={`border-b border-border/30 cursor-pointer transition-colors ${
                                                        selectedBillIds.has(bill.id!) ? "bg-primary/5" : "hover:bg-muted/30 opacity-50"
                                                    }`}
                                                    onClick={() => toggleBillSelection(bill.id!)}
                                                >
                                                    <td className="py-2 px-3">
                                                        <input
                                                            type="checkbox"
                                                            checked={selectedBillIds.has(bill.id!)}
                                                            onChange={() => toggleBillSelection(bill.id!)}
                                                            className="rounded border-border"
                                                        />
                                                    </td>
                                                    <td className="py-2 px-3">
                                                        {bill.date ? new Date(bill.date).toLocaleDateString("en-IN") : "-"}
                                                    </td>
                                                    <td className="py-2 px-3">
                                                        {bill.vehicle?.vehicleNumber || "-"}
                                                    </td>
                                                    <td className="py-2 px-3 text-muted-foreground">
                                                        {bill.products?.map(p => p.productName).filter(Boolean).join(", ") || "-"}
                                                    </td>
                                                    <td className="py-2 px-3">{bill.indentNo || "-"}</td>
                                                    <td className="py-2 px-3 text-right font-medium">
                                                        {Number(bill.netAmount).toLocaleString("en-IN", { minimumFractionDigits: 2 })}
                                                    </td>
                                                </tr>
                                            ))}
                                        </tbody>
                                    </table>
                                </div>
                            )}
                        </div>
                    )}

                    {/* Action Buttons */}
                    <div className="flex justify-end gap-3 pt-4">
                        <button
                            onClick={resetGenerateModal}
                            className="px-4 py-2 rounded-lg border border-border text-muted-foreground hover:bg-muted transition-colors"
                        >
                            Cancel
                        </button>
                        <button
                            onClick={handleGenerate}
                            disabled={generating || (showPreview && selectedBillIds.size === 0)}
                            className="btn-gradient px-6 py-2 rounded-lg font-medium disabled:opacity-50"
                        >
                            {generating ? "Generating..." : `Generate${showPreview ? ` (${selectedBillIds.size} bills)` : ""}`}
                        </button>
                    </div>
                </div>
            </Modal>

            {/* Statement Detail Modal */}
            <Modal
                isOpen={showDetailModal}
                onClose={() => { setShowDetailModal(false); setExpandedBillId(null); }}
                title={`Statement #${detailStatement?.statementNo || ""}`}
            >
                {detailStatement && (
                    <div className="p-6 space-y-6 max-h-[80vh] overflow-y-auto">
                        {/* Statement Header */}
                        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                            <div>
                                <div className="text-xs text-muted-foreground">Customer</div>
                                <div className="font-medium text-foreground">{detailStatement.customer?.name}</div>
                            </div>
                            <div>
                                <div className="text-xs text-muted-foreground">Period</div>
                                <div className="font-medium text-foreground">
                                    {detailStatement.fromDate} to {detailStatement.toDate}
                                </div>
                            </div>
                            <div>
                                <div className="text-xs text-muted-foreground">Net Amount</div>
                                <div className="font-bold text-foreground">
                                    {Number(detailStatement.netAmount).toLocaleString("en-IN", { style: "currency", currency: "INR" })}
                                </div>
                            </div>
                            <div>
                                <div className="text-xs text-muted-foreground">Balance</div>
                                <div className="font-bold text-amber-400">
                                    {Number(detailStatement.balanceAmount).toLocaleString("en-IN", { style: "currency", currency: "INR" })}
                                </div>
                            </div>
                        </div>

                        {/* Statement Actions */}
                        <div className="flex items-center gap-2 border-t border-b border-border/50 py-3">
                            <Badge variant={detailStatement.status === "PAID" ? "success" : "warning"}>
                                {detailStatement.status === "PAID" ? "PAID" : "NOT PAID"}
                            </Badge>
                            <span className="text-xs text-muted-foreground ml-auto">
                                Received: <span className="text-emerald-400 font-medium">{Number(detailStatement.receivedAmount).toLocaleString("en-IN", { style: "currency", currency: "INR" })}</span>
                            </span>
                            <div className="flex items-center gap-1 ml-4">
                                {detailStatement.statementPdfUrl ? (
                                    <button
                                        onClick={async () => {
                                            try {
                                                const url = await getStatementPdfUrl(detailStatement.id!);
                                                window.open(url, "_blank");
                                            } catch (e: any) {
                                                setPdfError(e.message || "Failed to download PDF");
                                                setTimeout(() => setPdfError(""), 4000);
                                            }
                                        }}
                                        className="px-3 py-1.5 rounded-lg text-xs font-medium bg-primary/10 text-primary hover:bg-primary/20 transition-colors flex items-center gap-1.5"
                                    >
                                        <Download className="w-3.5 h-3.5" /> Download PDF
                                    </button>
                                ) : (
                                    <button
                                        disabled={generatingPdfId === detailStatement.id}
                                        onClick={async () => {
                                            setGeneratingPdfId(detailStatement.id!);
                                            try {
                                                const updated = await generateStatementPdf(detailStatement.id!);
                                                setDetailStatement(updated);
                                                loadStatements();
                                            } catch (e: any) {
                                                setPdfError(e.message || "Failed to generate PDF");
                                                setTimeout(() => setPdfError(""), 4000);
                                            } finally {
                                                setGeneratingPdfId(null);
                                            }
                                        }}
                                        className="px-3 py-1.5 rounded-lg text-xs font-medium bg-muted text-muted-foreground hover:bg-primary/10 hover:text-primary transition-colors flex items-center gap-1.5 disabled:opacity-50"
                                    >
                                        {generatingPdfId === detailStatement.id ? (
                                            <Loader2 className="w-3.5 h-3.5 animate-spin" />
                                        ) : (
                                            <FileText className="w-3.5 h-3.5" />
                                        )}
                                        Generate PDF
                                    </button>
                                )}
                                {detailStatement.status !== "PAID" && (
                                    <PermissionGate permission="PAYMENT_DELETE">
                                        <button
                                            onClick={async () => {
                                                if (!confirm("Delete this statement? Bills will be unlinked.")) return;
                                                try {
                                                    await deleteStatement(detailStatement.id!);
                                                    setShowDetailModal(false);
                                                    loadStatements();
                                                    loadStats();
                                                } catch (e) {
                                                    console.error("Failed to delete statement", e);
                                                }
                                            }}
                                            className="px-3 py-1.5 rounded-lg text-xs font-medium bg-rose-500/10 text-rose-400 hover:bg-rose-500/20 transition-colors flex items-center gap-1.5"
                                        >
                                            <Trash2 className="w-3.5 h-3.5" /> Delete
                                        </button>
                                    </PermissionGate>
                                )}
                            </div>
                        </div>

                        {/* PDF Error in modal */}
                        {pdfError && (
                            <div className="bg-rose-500/20 border border-rose-500/30 text-rose-400 px-4 py-2 rounded-lg text-sm">
                                {pdfError}
                            </div>
                        )}

                        {/* Bills Table */}
                        <div>
                            <h4 className="text-sm font-semibold text-muted-foreground mb-3">
                                Bills ({detailBills.length})
                            </h4>
                            <div className="overflow-x-auto">
                                <table className="w-full text-sm">
                                    <thead>
                                        <tr className="border-b border-border text-muted-foreground">
                                            <th className="w-8 py-2 px-2"></th>
                                            <th className="text-left py-2 px-3">Bill No</th>
                                            <th className="text-left py-2 px-3">Date</th>
                                            <th className="text-left py-2 px-3">Vehicle</th>
                                            <th className="text-right py-2 px-3">Amount</th>
                                            <th className="text-center py-2 px-3">Status</th>
                                            <th className="text-center py-2 px-3">Action</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {detailBills.map((bill) => {
                                            const isExpanded = expandedBillId === bill.id;
                                            return (
                                                <Fragment key={bill.id}>
                                                    <tr
                                                        className="border-b border-border/50 hover:bg-muted/30 cursor-pointer transition-colors"
                                                        onClick={() => setExpandedBillId(isExpanded ? null : bill.id!)}
                                                    >
                                                        <td className="py-2 px-2 text-muted-foreground">
                                                            {isExpanded ? <ChevronDown className="w-4 h-4" /> : <ChevronRight className="w-4 h-4" />}
                                                        </td>
                                                        <td className="py-2 px-3 font-mono font-semibold">{bill.billNo || "-"}</td>
                                                        <td className="py-2 px-3">
                                                            {bill.date ? new Date(bill.date).toLocaleDateString("en-IN") : "-"}
                                                        </td>
                                                        <td className="py-2 px-3">
                                                            {bill.vehicle?.vehicleNumber || "-"}
                                                        </td>
                                                        <td className="py-2 px-3 text-right font-medium">
                                                            {Number(bill.netAmount).toLocaleString("en-IN", { minimumFractionDigits: 2 })}
                                                        </td>
                                                        <td className="py-2 px-3 text-center whitespace-nowrap">
                                                            <Badge variant={bill.paymentStatus === "PAID" ? "success" : "warning"}>
                                                                {bill.paymentStatus || "NOT PAID"}
                                                            </Badge>
                                                        </td>
                                                        <td className="py-2 px-3 text-center" onClick={(e) => e.stopPropagation()}>
                                                            {detailStatement.status !== "PAID" && (
                                                                <PermissionGate permission="PAYMENT_DELETE">
                                                                    <button
                                                                        onClick={() => handleRemoveBill(detailStatement.id!, bill.id!)}
                                                                        className="p-1 rounded-md hover:bg-rose-500/20 text-rose-400 transition-colors"
                                                                        title="Remove from statement"
                                                                    >
                                                                        <Trash2 className="w-3.5 h-3.5" />
                                                                    </button>
                                                                </PermissionGate>
                                                            )}
                                                        </td>
                                                    </tr>
                                                    {isExpanded && (
                                                        <tr className="bg-muted/20">
                                                            <td colSpan={7} className="px-6 py-3">
                                                                {bill.products && bill.products.length > 0 ? (
                                                                    <div>
                                                                        <div className="text-[10px] font-bold uppercase tracking-widest text-muted-foreground mb-2">Product Details</div>
                                                                        <table className="w-full text-xs">
                                                                            <thead>
                                                                                <tr className="text-[10px] font-bold uppercase tracking-widest text-muted-foreground">
                                                                                    <th className="text-left py-1 pr-4">Product</th>
                                                                                    <th className="text-left py-1 pr-4">Nozzle</th>
                                                                                    <th className="text-right py-1 pr-4">Qty</th>
                                                                                    <th className="text-right py-1 pr-4">Rate</th>
                                                                                    <th className="text-right py-1 pr-4">Discount</th>
                                                                                    <th className="text-right py-1">Amount</th>
                                                                                </tr>
                                                                            </thead>
                                                                            <tbody>
                                                                                {bill.products.map((ip: any, idx: number) => (
                                                                                    <tr key={idx} className="border-t border-border/20">
                                                                                        <td className="py-1.5 pr-4 text-foreground">{ip.productName || "-"}</td>
                                                                                        <td className="py-1.5 pr-4 text-muted-foreground font-mono">{ip.nozzleName || "-"}</td>
                                                                                        <td className="py-1.5 pr-4 text-right">{Number(ip.quantity || 0).toFixed(2)}</td>
                                                                                        <td className="py-1.5 pr-4 text-right">{Number(ip.rate || 0).toFixed(2)}</td>
                                                                                        <td className="py-1.5 pr-4 text-right text-amber-400">{Number(ip.discountAmount || 0).toFixed(2)}</td>
                                                                                        <td className="py-1.5 text-right font-medium">{Number(ip.amount || 0).toLocaleString("en-IN", { minimumFractionDigits: 2 })}</td>
                                                                                    </tr>
                                                                                ))}
                                                                            </tbody>
                                                                        </table>
                                                                        {bill.indentNo && (
                                                                            <div className="mt-2 text-xs text-muted-foreground">
                                                                                Indent No: <span className="text-foreground font-mono">{bill.indentNo}</span>
                                                                            </div>
                                                                        )}
                                                                    </div>
                                                                ) : (
                                                                    <div className="text-xs text-muted-foreground">No product details available</div>
                                                                )}
                                                            </td>
                                                        </tr>
                                                    )}
                                                </Fragment>
                                            );
                                        })}
                                    </tbody>
                                    <tfoot>
                                        <tr className="border-t border-border">
                                            <td colSpan={4} className="py-2 px-3 text-right font-semibold text-muted-foreground">
                                                Total
                                            </td>
                                            <td className="py-2 px-3 text-right font-bold text-foreground">
                                                {Number(detailStatement.totalAmount).toLocaleString("en-IN", { minimumFractionDigits: 2 })}
                                            </td>
                                            <td colSpan={2}></td>
                                        </tr>
                                        <tr>
                                            <td colSpan={4} className="py-1 px-3 text-right text-sm text-muted-foreground">
                                                Rounding
                                            </td>
                                            <td className="py-1 px-3 text-right text-sm text-muted-foreground">
                                                {Number(detailStatement.roundingAmount).toLocaleString("en-IN", { minimumFractionDigits: 2 })}
                                            </td>
                                            <td colSpan={2}></td>
                                        </tr>
                                        <tr className="border-t border-border">
                                            <td colSpan={4} className="py-2 px-3 text-right font-bold text-foreground">
                                                Net Amount
                                            </td>
                                            <td className="py-2 px-3 text-right font-bold text-primary text-lg">
                                                {Number(detailStatement.netAmount).toLocaleString("en-IN", { style: "currency", currency: "INR" })}
                                            </td>
                                            <td colSpan={2}></td>
                                        </tr>
                                    </tfoot>
                                </table>
                            </div>
                        </div>

                        {/* Vehicle-wise Summary & Set as Limit */}
                        {detailBills.length > 0 && (() => {
                            // Build vehicle-wise summary from bills
                            const vehicleMap = new Map<string, { id: number | null; number: string; totalAmount: number; totalLiters: number }>();
                            for (const bill of detailBills) {
                                const vNum = bill.vehicle?.vehicleNumber || "No Vehicle";
                                const vId = bill.vehicle?.id || null;
                                const existing = vehicleMap.get(vNum) || { id: vId, number: vNum, totalAmount: 0, totalLiters: 0 };
                                existing.totalAmount += Number(bill.netAmount || 0);
                                const billLiters = bill.products?.reduce((sum: number, p: any) => sum + Number(p.quantity || 0), 0) || 0;
                                existing.totalLiters += billLiters;
                                vehicleMap.set(vNum, existing);
                            }
                            const vehicleSummaries = Array.from(vehicleMap.values()).sort((a, b) => b.totalAmount - a.totalAmount);
                            const statementTotal = vehicleSummaries.reduce((s, v) => s + v.totalAmount, 0);
                            const statementLiters = vehicleSummaries.reduce((s, v) => s + v.totalLiters, 0);

                            return (
                                <div className="border-t border-border/50 pt-4">
                                    <h4 className="text-sm font-semibold text-muted-foreground mb-3 flex items-center justify-between">
                                        <span>Vehicle-wise Summary</span>
                                        <span className="text-xs font-normal">Use statement data to set credit limits</span>
                                    </h4>

                                    {setLimitSuccess && (
                                        <div className="mb-3 px-3 py-2 bg-emerald-500/10 border border-emerald-500/20 rounded-lg text-xs text-emerald-400 font-medium">
                                            {setLimitSuccess}
                                        </div>
                                    )}

                                    <div className="overflow-x-auto">
                                        <table className="w-full text-xs">
                                            <thead>
                                                <tr className="border-b border-border text-muted-foreground">
                                                    <th className="text-left py-2 px-3">Vehicle</th>
                                                    <th className="text-right py-2 px-3">Amount</th>
                                                    <th className="text-right py-2 px-3">Liters</th>
                                                    <th className="text-center py-2 px-3">Set Vehicle Limit</th>
                                                </tr>
                                            </thead>
                                            <tbody>
                                                {vehicleSummaries.map((vs) => (
                                                    <tr key={vs.number} className="border-b border-border/30">
                                                        <td className="py-2 px-3 font-medium text-foreground">{vs.number}</td>
                                                        <td className="py-2 px-3 text-right">{vs.totalAmount.toLocaleString("en-IN", { minimumFractionDigits: 2 })}</td>
                                                        <td className="py-2 px-3 text-right">{vs.totalLiters.toFixed(2)} L</td>
                                                        <td className="py-2 px-3 text-center">
                                                            {vs.id ? (
                                                                <button
                                                                    onClick={async () => {
                                                                        try {
                                                                            await updateVehicleLiterLimit(vs.id!, Math.round(vs.totalLiters));
                                                                            setSetLimitSuccess(`Set ${vs.number} limit to ${Math.round(vs.totalLiters)} L/month`);
                                                                            setTimeout(() => setSetLimitSuccess(""), 4000);
                                                                        } catch (e: any) {
                                                                            setSetLimitSuccess(`Failed: ${e.message || "Error"}`);
                                                                            setTimeout(() => setSetLimitSuccess(""), 4000);
                                                                        }
                                                                    }}
                                                                    className="px-2 py-1 rounded text-[10px] font-medium bg-cyan-500/10 text-cyan-400 hover:bg-cyan-500/20 transition-colors"
                                                                    title={`Set ${vs.number} monthly limit to ${Math.round(vs.totalLiters)} L`}
                                                                >
                                                                    Set {Math.round(vs.totalLiters)} L
                                                                </button>
                                                            ) : (
                                                                <span className="text-muted-foreground">—</span>
                                                            )}
                                                        </td>
                                                    </tr>
                                                ))}
                                            </tbody>
                                            <tfoot>
                                                <tr className="border-t border-border">
                                                    <td className="py-2 px-3 font-bold text-foreground">Total</td>
                                                    <td className="py-2 px-3 text-right font-bold">{statementTotal.toLocaleString("en-IN", { minimumFractionDigits: 2 })}</td>
                                                    <td className="py-2 px-3 text-right font-bold">{statementLiters.toFixed(2)} L</td>
                                                    <td></td>
                                                </tr>
                                            </tfoot>
                                        </table>
                                    </div>

                                    {/* Set Customer Limit buttons */}
                                    {detailStatement.customer && (
                                        <div className="mt-4 flex flex-wrap gap-2">
                                            <button
                                                onClick={async () => {
                                                    try {
                                                        await updateCustomerCreditLimits(detailStatement.customer!.id!, { creditLimitAmount: Math.round(statementTotal) });
                                                        setSetLimitSuccess(`Set ${detailStatement.customer!.name} credit limit to ₹${Math.round(statementTotal).toLocaleString("en-IN")}`);
                                                        setTimeout(() => setSetLimitSuccess(""), 4000);
                                                    } catch (e: any) {
                                                        setSetLimitSuccess(`Failed: ${e.message || "Error"}`);
                                                        setTimeout(() => setSetLimitSuccess(""), 4000);
                                                    }
                                                }}
                                                className="px-3 py-1.5 rounded-lg text-xs font-medium bg-amber-500/10 text-amber-400 hover:bg-amber-500/20 transition-colors"
                                                title={`Set customer credit limit to ₹${Math.round(statementTotal).toLocaleString("en-IN")}`}
                                            >
                                                Set Customer Limit: ₹{Math.round(statementTotal).toLocaleString("en-IN")}
                                            </button>
                                            <button
                                                onClick={async () => {
                                                    try {
                                                        await updateCustomerCreditLimits(detailStatement.customer!.id!, { creditLimitLiters: Math.round(statementLiters) });
                                                        setSetLimitSuccess(`Set ${detailStatement.customer!.name} liter limit to ${Math.round(statementLiters)} L`);
                                                        setTimeout(() => setSetLimitSuccess(""), 4000);
                                                    } catch (e: any) {
                                                        setSetLimitSuccess(`Failed: ${e.message || "Error"}`);
                                                        setTimeout(() => setSetLimitSuccess(""), 4000);
                                                    }
                                                }}
                                                className="px-3 py-1.5 rounded-lg text-xs font-medium bg-cyan-500/10 text-cyan-400 hover:bg-cyan-500/20 transition-colors"
                                                title={`Set customer liter limit to ${Math.round(statementLiters)} L`}
                                            >
                                                Set Customer Limit: {Math.round(statementLiters)} L
                                            </button>
                                        </div>
                                    )}
                                </div>
                            );
                        })()}
                    </div>
                )}
            </Modal>
        </div>
    );
}
