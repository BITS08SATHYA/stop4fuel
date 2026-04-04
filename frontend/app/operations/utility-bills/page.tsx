"use client";

import { useState, useEffect, useRef } from "react";
import {
    Plus,
    Search,
    Pencil,
    Trash2,
    Upload,
    Zap,
    Droplets,
    FileText,
    AlertCircle,
} from "lucide-react";
import { Modal } from "@/components/ui/modal";
import { StyledSelect } from "@/components/ui/styled-select";
import { GlassCard } from "@/components/ui/glass-card";
import { Badge } from "@/components/ui/badge";
import { TablePagination, useClientPagination } from "@/components/ui/table-pagination";
import {
    getUtilityBills,
    createUtilityBill,
    updateUtilityBill,
    deleteUtilityBill,
    uploadUtilityBillPdf,
    UtilityBill,
} from "@/lib/api/station";
import { PermissionGate } from "@/components/permission-gate";

const formatRupees = (val: number) => `₹${val.toLocaleString("en-IN")}`;

export default function UtilityBillsPage() {
    const [bills, setBills] = useState<UtilityBill[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [editingBill, setEditingBill] = useState<UtilityBill | null>(null);
    const [searchQuery, setSearchQuery] = useState("");
    const [typeFilter, setTypeFilter] = useState("ALL");
    const [statusFilter, setStatusFilter] = useState("ALL");
    const fileInputRef = useRef<HTMLInputElement>(null);

    // Form state
    const [billType, setBillType] = useState("ELECTRICITY");
    const [provider, setProvider] = useState("TNEB");
    const [consumerNumber, setConsumerNumber] = useState("");
    const [billDate, setBillDate] = useState("");
    const [dueDate, setDueDate] = useState("");
    const [billAmount, setBillAmount] = useState("");
    const [paidAmount, setPaidAmount] = useState("0");
    const [status, setStatus] = useState("PENDING");
    const [unitsConsumed, setUnitsConsumed] = useState("");
    const [billPeriod, setBillPeriod] = useState("");
    const [remarks, setRemarks] = useState("");

    // Upload state
    const [isUploading, setIsUploading] = useState(false);
    const [parsedBill, setParsedBill] = useState<UtilityBill | null>(null);

    useEffect(() => { loadBills(); }, []);

    const loadBills = async () => {
        setIsLoading(true);
        try {
            const data = await getUtilityBills();
            setBills(data);
        } catch (e) { console.error(e); }
        setIsLoading(false);
    };

    const resetForm = () => {
        setBillType("ELECTRICITY");
        setProvider("TNEB");
        setConsumerNumber("");
        setBillDate("");
        setDueDate("");
        setBillAmount("");
        setPaidAmount("0");
        setStatus("PENDING");
        setUnitsConsumed("");
        setBillPeriod("");
        setRemarks("");
        setParsedBill(null);
    };

    const handleEdit = (bill: UtilityBill) => {
        setEditingBill(bill);
        setBillType(bill.billType);
        setProvider(bill.provider);
        setConsumerNumber(bill.consumerNumber);
        setBillDate(bill.billDate);
        setDueDate(bill.dueDate);
        setBillAmount(bill.billAmount?.toString() || "");
        setPaidAmount(bill.paidAmount?.toString() || "0");
        setStatus(bill.status);
        setUnitsConsumed(bill.unitsConsumed?.toString() || "");
        setBillPeriod(bill.billPeriod || "");
        setRemarks(bill.remarks || "");
        setIsModalOpen(true);
    };

    const handleSave = async (e: React.FormEvent) => {
        e.preventDefault();
        try {
            const data = {
                billType,
                provider,
                consumerNumber,
                billDate,
                dueDate,
                billAmount: parseFloat(billAmount),
                paidAmount: parseFloat(paidAmount),
                status,
                unitsConsumed: unitsConsumed ? parseFloat(unitsConsumed) : undefined,
                billPeriod: billPeriod || undefined,
                remarks: remarks || undefined,
            };
            if (editingBill) {
                await updateUtilityBill(editingBill.id, data);
            } else {
                await createUtilityBill(data);
            }
            setIsModalOpen(false);
            setEditingBill(null);
            resetForm();
            loadBills();
        } catch (e) {
            alert("Failed to save bill");
        }
    };

    const handleDelete = async (id: number) => {
        if (!confirm("Delete this utility bill?")) return;
        await deleteUtilityBill(id);
        loadBills();
    };

    const handlePdfUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
        const file = e.target.files?.[0];
        if (!file) return;
        setIsUploading(true);
        try {
            const parsed = await uploadUtilityBillPdf(file);
            setParsedBill(parsed);
            // Pre-fill form
            setBillType(parsed.billType || "ELECTRICITY");
            setProvider(parsed.provider || "TNEB");
            setConsumerNumber(parsed.consumerNumber || "");
            setBillDate(parsed.billDate || "");
            setDueDate(parsed.dueDate || "");
            setBillAmount(parsed.billAmount?.toString() || "");
            setUnitsConsumed(parsed.unitsConsumed?.toString() || "");
            setStatus("PENDING");
            setPaidAmount("0");
            setEditingBill(null);
            setIsModalOpen(true);
        } catch (err) {
            alert("Failed to parse PDF. Please enter details manually.");
            resetForm();
            setEditingBill(null);
            setIsModalOpen(true);
        }
        setIsUploading(false);
        if (fileInputRef.current) fileInputRef.current.value = "";
    };

    const filtered = bills.filter((b) => {
        const matchesSearch = !searchQuery ||
            b.provider?.toLowerCase().includes(searchQuery.toLowerCase()) ||
            b.consumerNumber?.toLowerCase().includes(searchQuery.toLowerCase());
        const matchesType = typeFilter === "ALL" || b.billType === typeFilter;
        const matchesStatus = statusFilter === "ALL" || b.status === statusFilter;
        return matchesSearch && matchesType && matchesStatus;
    });

    const { page, setPage, totalPages, totalElements, pageSize, paginatedData } =
        useClientPagination(filtered, 8);

    // Stats
    const totalBilled = bills.reduce((s, b) => s + (b.billAmount || 0), 0);
    const totalPaid = bills.reduce((s, b) => s + (b.paidAmount || 0), 0);
    const pendingCount = bills.filter((b) => b.status === "PENDING").length;
    const overdueCount = bills.filter((b) => b.status === "OVERDUE").length;

    if (isLoading) {
        return (
            <div className="p-6 h-screen overflow-hidden bg-background flex items-center justify-center">
                <div className="flex flex-col items-center">
                    <div className="w-12 h-12 border-4 border-primary/20 border-t-primary rounded-full animate-spin mb-4" />
                    <p className="animate-pulse text-muted-foreground">Loading bills...</p>
                </div>
            </div>
        );
    }

    return (
        <div className="p-6 h-screen overflow-hidden bg-background transition-colors duration-300">
            <div className="max-w-7xl mx-auto h-full flex flex-col">
                {/* Header */}
                <div className="flex justify-between items-center mb-6">
                    <div>
                        <h1 className="text-4xl font-bold text-foreground tracking-tight">
                            Utility <span className="text-gradient">Bills</span>
                        </h1>
                        <p className="text-muted-foreground mt-2">Track electricity, water, and other utility bills</p>
                    </div>
                    <PermissionGate permission="FINANCE_MANAGE">
                        <div className="flex gap-3">
                            <input
                                ref={fileInputRef}
                                type="file"
                                accept=".pdf"
                                onChange={handlePdfUpload}
                                className="hidden"
                            />
                            <button
                                onClick={() => fileInputRef.current?.click()}
                                disabled={isUploading}
                                className="px-5 py-3 rounded-xl font-medium flex items-center gap-2 border border-border text-foreground hover:bg-muted transition-colors"
                            >
                                <Upload className="w-5 h-5" />
                                {isUploading ? "Parsing..." : "Upload PDF"}
                            </button>
                            <button
                                onClick={() => { resetForm(); setEditingBill(null); setIsModalOpen(true); }}
                                className="btn-gradient px-6 py-3 rounded-xl font-medium flex items-center gap-2"
                            >
                                <Plus className="w-5 h-5" />
                                Add Bill
                            </button>
                        </div>
                    </PermissionGate>
                </div>

                {/* Stats */}
                <div className="grid grid-cols-2 md:grid-cols-4 gap-3 mb-6">
                    <GlassCard className="p-4 text-center">
                        <Zap className="w-5 h-5 mx-auto text-primary mb-1" />
                        <p className="text-xl font-bold text-primary">{formatRupees(totalBilled)}</p>
                        <p className="text-xs text-muted-foreground">Total Billed</p>
                    </GlassCard>
                    <GlassCard className="p-4 text-center">
                        <FileText className="w-5 h-5 mx-auto text-emerald-500 mb-1" />
                        <p className="text-xl font-bold text-emerald-500">{formatRupees(totalPaid)}</p>
                        <p className="text-xs text-muted-foreground">Total Paid</p>
                    </GlassCard>
                    <GlassCard className="p-4 text-center">
                        <AlertCircle className="w-5 h-5 mx-auto text-amber-500 mb-1" />
                        <p className="text-2xl font-bold text-amber-500">{pendingCount}</p>
                        <p className="text-xs text-muted-foreground">Pending</p>
                    </GlassCard>
                    <GlassCard className="p-4 text-center">
                        <AlertCircle className="w-5 h-5 mx-auto text-red-500 mb-1" />
                        <p className="text-2xl font-bold text-red-500">{overdueCount}</p>
                        <p className="text-xs text-muted-foreground">Overdue</p>
                    </GlassCard>
                </div>

                {/* Filters */}
                <div className="mb-4 flex gap-3 items-center flex-wrap">
                    <div className="relative flex-1 max-w-md">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                        <input
                            type="text"
                            placeholder="Search by provider or consumer number..."
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            className="w-full bg-background border border-border rounded-xl pl-10 pr-4 py-2.5 text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                        />
                    </div>
                    <StyledSelect
                        value={typeFilter}
                        onChange={(val) => setTypeFilter(val)}
                        options={[
                            { value: "ALL", label: "All Types" },
                            { value: "ELECTRICITY", label: "Electricity" },
                            { value: "WATER", label: "Water" },
                        ]}
                        className="min-w-[140px]"
                    />
                    <StyledSelect
                        value={statusFilter}
                        onChange={(val) => setStatusFilter(val)}
                        options={[
                            { value: "ALL", label: "All Status" },
                            { value: "PENDING", label: "Pending" },
                            { value: "PAID", label: "Paid" },
                            { value: "OVERDUE", label: "Overdue" },
                        ]}
                        className="min-w-[140px]"
                    />
                </div>

                {/* Table */}
                {filtered.length === 0 ? (
                    <div className="text-center py-20 bg-black/5 dark:bg-white/5 rounded-2xl border border-dashed border-border">
                        <Zap className="w-16 h-16 mx-auto text-muted-foreground mb-4 opacity-50" />
                        <h3 className="text-xl font-semibold text-foreground mb-2">No Utility Bills</h3>
                        <p className="text-muted-foreground mb-6">Add a bill manually or upload a TNEB PDF.</p>
                    </div>
                ) : (
                    <GlassCard className="overflow-hidden border-none p-0 flex-1">
                        <div className="overflow-x-auto">
                            <table className="w-full text-left border-collapse">
                                <thead>
                                    <tr className="bg-white/5 border-b border-border/50">
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center w-12">#</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Type</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Provider</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Consumer No</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Bill Date</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Due Date</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right">Amount</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center">Units</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center">Status</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center w-28">Actions</th>
                                    </tr>
                                </thead>
                                <tbody className="divide-y divide-border/30">
                                    {paginatedData.map((b, idx) => (
                                        <tr key={b.id} className="hover:bg-white/5 transition-colors group">
                                            <td className="px-6 py-4 text-xs font-mono text-muted-foreground text-center">{page * pageSize + idx + 1}</td>
                                            <td className="px-6 py-4">
                                                <span className="flex items-center gap-1.5">
                                                    {b.billType === "ELECTRICITY" ? <Zap className="w-4 h-4 text-amber-500" /> : <Droplets className="w-4 h-4 text-blue-500" />}
                                                    {b.billType}
                                                </span>
                                            </td>
                                            <td className="px-6 py-4 font-medium text-foreground">{b.provider}</td>
                                            <td className="px-6 py-4 text-sm text-muted-foreground">{b.consumerNumber}</td>
                                            <td className="px-6 py-4 text-sm">{b.billDate}</td>
                                            <td className="px-6 py-4 text-sm">{b.dueDate}</td>
                                            <td className="px-6 py-4 text-right font-bold text-primary">{formatRupees(b.billAmount || 0)}</td>
                                            <td className="px-6 py-4 text-center text-sm">{b.unitsConsumed || "-"}</td>
                                            <td className="px-6 py-4 text-center">
                                                <Badge variant={b.status === "PAID" ? "success" : b.status === "OVERDUE" ? "danger" : "warning"}>
                                                    {b.status}
                                                </Badge>
                                            </td>
                                            <td className="px-6 py-4">
                                                <div className="flex justify-center gap-2 opacity-100 md:opacity-0 group-hover:opacity-100 transition-opacity">
                                                    <PermissionGate permission="FINANCE_MANAGE">
                                                        <button onClick={() => handleEdit(b)} className="p-2 rounded-lg hover:bg-white/10"><Pencil className="w-4 h-4" /></button>
                                                    </PermissionGate>
                                                    <PermissionGate permission="FINANCE_MANAGE">
                                                        <button onClick={() => handleDelete(b.id)} className="p-2 rounded-lg hover:bg-red-500/10 text-red-500"><Trash2 className="w-4 h-4" /></button>
                                                    </PermissionGate>
                                                </div>
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                        <TablePagination page={page} totalPages={totalPages} totalElements={totalElements} pageSize={pageSize} onPageChange={setPage} />
                    </GlassCard>
                )}
            </div>

            {/* Modal */}
            <Modal isOpen={isModalOpen} onClose={() => { setIsModalOpen(false); setEditingBill(null); resetForm(); }} title={editingBill ? "Edit Utility Bill" : parsedBill ? "Confirm Parsed Bill" : "Add Utility Bill"}>
                {parsedBill && !editingBill && (
                    <div className="mb-4 p-3 bg-emerald-500/10 border border-emerald-500/20 rounded-xl text-sm text-emerald-500">
                        PDF parsed successfully. Review and save the details below.
                    </div>
                )}
                <form onSubmit={handleSave} className="space-y-4">
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5">Bill Type <span className="text-red-500">*</span></label>
                            <StyledSelect
                                value={billType}
                                onChange={(val) => setBillType(val)}
                                options={[
                                    { value: "ELECTRICITY", label: "Electricity" },
                                    { value: "WATER", label: "Water" },
                                ]}
                                className="w-full"
                            />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5">Provider <span className="text-red-500">*</span></label>
                            <input type="text" required value={provider} onChange={(e) => setProvider(e.target.value)} className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50" placeholder="e.g., TNEB" />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5">Consumer Number</label>
                            <input type="text" value={consumerNumber} onChange={(e) => setConsumerNumber(e.target.value)} className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50" />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5">Units Consumed</label>
                            <input type="number" step="0.01" value={unitsConsumed} onChange={(e) => setUnitsConsumed(e.target.value)} className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50" />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5">Bill Date <span className="text-red-500">*</span></label>
                            <input type="date" required value={billDate} onChange={(e) => setBillDate(e.target.value)} className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50" />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5">Due Date</label>
                            <input type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50" />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5">Bill Amount <span className="text-red-500">*</span></label>
                            <input type="number" step="0.01" required value={billAmount} onChange={(e) => setBillAmount(e.target.value)} className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50" />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5">Paid Amount</label>
                            <input type="number" step="0.01" value={paidAmount} onChange={(e) => setPaidAmount(e.target.value)} className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50" />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5">Status</label>
                            <StyledSelect
                                value={status}
                                onChange={(val) => setStatus(val)}
                                options={[
                                    { value: "PENDING", label: "Pending" },
                                    { value: "PAID", label: "Paid" },
                                    { value: "OVERDUE", label: "Overdue" },
                                ]}
                                className="w-full"
                            />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5">Bill Period</label>
                            <input type="text" value={billPeriod} onChange={(e) => setBillPeriod(e.target.value)} className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50" placeholder="e.g., Jan 2026" />
                        </div>
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-foreground mb-1.5">Remarks</label>
                        <textarea value={remarks} onChange={(e) => setRemarks(e.target.value)} rows={2} className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50" />
                    </div>
                    <div className="flex justify-end gap-3 pt-4 border-t border-border">
                        <button type="button" onClick={() => { setIsModalOpen(false); setEditingBill(null); resetForm(); }} className="px-6 py-2.5 rounded-xl font-medium text-foreground bg-black/5 dark:bg-white/5 hover:bg-black/10 dark:hover:bg-white/10 transition-colors">Cancel</button>
                        <button type="submit" className="btn-gradient px-8 py-2.5 rounded-xl font-medium">Save</button>
                    </div>
                </form>
            </Modal>
        </div>
    );
}
