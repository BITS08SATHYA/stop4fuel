"use client";

import { useState, useEffect } from "react";
import { TablePagination } from "@/components/ui/table-pagination";
import { GlassCard } from "@/components/ui/glass-card";
import { Badge } from "@/components/ui/badge";
import { Modal } from "@/components/ui/modal";
import {
    Plus, Eye, Trash2, Calendar, User, Filter, Search, FileText, Download, Loader2,
    FileClock, FileCheck2, Receipt, TrendingUp, IndianRupee, Percent
} from "lucide-react";
import {
    getStatements, generateStatement, getStatementBills,
    getCustomers, deleteStatement, removeBillFromStatement,
    getVehiclesByCustomer, getProducts, previewStatementBills,
    generateStatementPdf, getStatementPdfUrl, getStatementStats,
    type Statement, type InvoiceBill, type Customer, type Vehicle,
    type Product, type PageResponse, type StatementStats
} from "@/lib/api/station";

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
    const [tableSearch, setTableSearch] = useState("");
    const [searchInput, setSearchInput] = useState("");
    const [filterFromDate, setFilterFromDate] = useState("");
    const [filterToDate, setFilterToDate] = useState("");
    const [searchTimeout, setSearchTimeout] = useState<NodeJS.Timeout | null>(null);
    const [stats, setStats] = useState<StatementStats | null>(null);

    useEffect(() => {
        loadCustomers();
        loadProducts();
        loadStats();
    }, []);

    useEffect(() => {
        loadStatements();
    }, [page, filterStatus, filterFromDate, filterToDate, tableSearch]);

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
            const custs = await getCustomers();
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
                tableSearch || undefined
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
            // Select all bills by default
            setSelectedBillIds(new Set(bills.map(b => b.id!)));
            setUseBillSelection(false);
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
                    <button
                        onClick={() => setShowGenerateModal(true)}
                        className="btn-gradient px-6 py-3 rounded-xl font-medium flex items-center gap-2"
                    >
                        <Plus className="w-5 h-5" />
                        Generate Statement
                    </button>
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
                    <select
                        value={filterStatus}
                        onChange={(e) => handleFilterChange(e.target.value)}
                        className="px-4 py-2 bg-card border border-border rounded-xl text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
                    >
                        <option value="ALL">All Status</option>
                        <option value="NOT_PAID">Outstanding</option>
                        <option value="PAID">Paid</option>
                    </select>
                </div>

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
                                                <Badge variant={stmt.status === "PAID" ? "success" : "warning"}>
                                                    {stmt.status === "PAID" ? "PAID" : "NOT PAID"}
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
                                                                const url = await getStatementPdfUrl(stmt.id!);
                                                                window.open(url, "_blank");
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
                                                                } catch (e) {
                                                                    console.error("Failed to generate PDF", e);
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
                                                    {stmt.status !== "PAID" && (
                                                        <button
                                                            onClick={() => handleDelete(stmt.id!)}
                                                            className="p-1.5 rounded-md hover:bg-rose-500/20 text-rose-400 transition-colors"
                                                            title="Delete"
                                                        >
                                                            <Trash2 className="w-4 h-4" />
                                                        </button>
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
                        <select
                            value={selectedCustomerId}
                            onChange={(e) => setSelectedCustomerId(e.target.value ? Number(e.target.value) : "")}
                            className="w-full px-4 py-2 bg-card border border-border rounded-lg text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                        >
                            <option value="">Select customer...</option>
                            {customers.map((c: any) => (
                                <option key={c.id} value={c.id}>{c.name}</option>
                            ))}
                        </select>
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
                                <select
                                    value={filterVehicleId}
                                    onChange={(e) => { setFilterVehicleId(e.target.value ? Number(e.target.value) : ""); setShowPreview(false); }}
                                    className="w-full px-3 py-2 bg-card border border-border rounded-lg text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
                                    disabled={!selectedCustomerId}
                                >
                                    <option value="">All Vehicles</option>
                                    {vehicles.map(v => (
                                        <option key={v.id} value={v.id}>{v.vehicleNumber}</option>
                                    ))}
                                </select>
                            </div>
                            <div>
                                <label className="block text-xs text-muted-foreground mb-1">Product</label>
                                <select
                                    value={filterProductId}
                                    onChange={(e) => { setFilterProductId(e.target.value ? Number(e.target.value) : ""); setShowPreview(false); }}
                                    className="w-full px-3 py-2 bg-card border border-border rounded-lg text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
                                >
                                    <option value="">All Products</option>
                                    {products.map(p => (
                                        <option key={p.id} value={p.id}>{p.name}</option>
                                    ))}
                                </select>
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
                                                        {bill.vehicle?.customer && selectedCustomerId && bill.vehicle.customer.id !== Number(selectedCustomerId) && (
                                                            <span className="block text-[9px] text-amber-500 font-medium">(owned by {bill.vehicle.customer.name})</span>
                                                        )}
                                                    </td>
                                                    <td className="py-2 px-3 text-muted-foreground">
                                                        {bill.products?.map(p => p.product?.name).filter(Boolean).join(", ") || "-"}
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
                onClose={() => setShowDetailModal(false)}
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

                        {/* Bills Table */}
                        <div>
                            <h4 className="text-sm font-semibold text-muted-foreground mb-3">
                                Bills ({detailBills.length})
                            </h4>
                            <div className="overflow-x-auto">
                                <table className="w-full text-sm">
                                    <thead>
                                        <tr className="border-b border-border text-muted-foreground">
                                            <th className="text-left py-2 px-3">#</th>
                                            <th className="text-left py-2 px-3">Bill No</th>
                                            <th className="text-left py-2 px-3">Date</th>
                                            <th className="text-left py-2 px-3">Vehicle</th>
                                            <th className="text-left py-2 px-3">Indent No</th>
                                            <th className="text-right py-2 px-3">Amount</th>
                                            <th className="text-center py-2 px-3">Status</th>
                                            <th className="text-center py-2 px-3">Action</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {detailBills.map((bill, idx) => (
                                            <tr key={bill.id} className="border-b border-border/50">
                                                <td className="py-2 px-3 text-muted-foreground">{idx + 1}</td>
                                                <td className="py-2 px-3 font-mono font-semibold">{bill.billNo || "-"}</td>
                                                <td className="py-2 px-3">
                                                    {bill.date ? new Date(bill.date).toLocaleDateString("en-IN") : "-"}
                                                </td>
                                                <td className="py-2 px-3">
                                                    {bill.vehicle?.vehicleNumber || "-"}
                                                    {bill.vehicle?.customer && detailStatement.customer && bill.vehicle.customer.id !== detailStatement.customer.id && (
                                                        <span className="block text-[10px] text-amber-500 font-medium">(owned by {bill.vehicle.customer.name})</span>
                                                    )}
                                                </td>
                                                <td className="py-2 px-3">{bill.indentNo || "-"}</td>
                                                <td className="py-2 px-3 text-right font-medium">
                                                    {Number(bill.netAmount).toLocaleString("en-IN", { minimumFractionDigits: 2 })}
                                                </td>
                                                <td className="py-2 px-3 text-center">
                                                    <Badge variant={bill.paymentStatus === "PAID" ? "success" : "warning"}>
                                                        {bill.paymentStatus || "NOT PAID"}
                                                    </Badge>
                                                </td>
                                                <td className="py-2 px-3 text-center">
                                                    {detailStatement.status !== "PAID" && (
                                                        <button
                                                            onClick={() => handleRemoveBill(detailStatement.id!, bill.id!)}
                                                            className="text-rose-400 hover:text-rose-300 text-xs"
                                                            title="Remove from statement"
                                                        >
                                                            Remove
                                                        </button>
                                                    )}
                                                </td>
                                            </tr>
                                        ))}
                                    </tbody>
                                    <tfoot>
                                        <tr className="border-t border-border">
                                            <td colSpan={5} className="py-2 px-3 text-right font-semibold text-muted-foreground">
                                                Total
                                            </td>
                                            <td className="py-2 px-3 text-right font-bold text-foreground">
                                                {Number(detailStatement.totalAmount).toLocaleString("en-IN", { minimumFractionDigits: 2 })}
                                            </td>
                                            <td colSpan={2}></td>
                                        </tr>
                                        <tr>
                                            <td colSpan={5} className="py-1 px-3 text-right text-sm text-muted-foreground">
                                                Rounding
                                            </td>
                                            <td className="py-1 px-3 text-right text-sm text-muted-foreground">
                                                {Number(detailStatement.roundingAmount).toLocaleString("en-IN", { minimumFractionDigits: 2 })}
                                            </td>
                                            <td colSpan={2}></td>
                                        </tr>
                                        <tr className="border-t border-border">
                                            <td colSpan={5} className="py-2 px-3 text-right font-bold text-foreground">
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
                    </div>
                )}
            </Modal>
        </div>
    );
}
