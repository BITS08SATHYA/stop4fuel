"use client";

import { useEffect, useState, useMemo } from "react";
import { Modal } from "@/components/ui/modal";
import {
    Search,
    FileText,
    Link2,
    Unlink,
    Receipt,
    ChevronRight,
} from "lucide-react";
import {
    OperationalAdvance,
    InvoiceBill,
    StatementRef,
    STATUS_CONFIG,
    getAdvanceTypeMeta,
    formatDateTime,
    formatCurrency,
    fetchAssignedInvoices,
    fetchShiftInvoices,
    assignInvoice,
    unassignInvoice,
    fetchOutstandingStatements,
    assignStatement,
    unassignStatement,
} from "./advances-api";

interface AdvanceDetailModalProps {
    isOpen: boolean;
    onClose: () => void;
    advance: OperationalAdvance | null;
    onDataChanged: () => void;
}

export function AdvanceDetailModal({ isOpen, onClose, advance, onDataChanged }: AdvanceDetailModalProps) {
    const [assignedInvoices, setAssignedInvoices] = useState<InvoiceBill[]>([]);
    const [availableInvoices, setAvailableInvoices] = useState<InvoiceBill[]>([]);
    const [showAssignPanel, setShowAssignPanel] = useState(false);
    const [invoiceSearch, setInvoiceSearch] = useState("");
    const [availableStatements, setAvailableStatements] = useState<StatementRef[]>([]);
    const [showStatementPanel, setShowStatementPanel] = useState(false);
    const [detailAdvance, setDetailAdvance] = useState<OperationalAdvance | null>(null);

    useEffect(() => {
        setDetailAdvance(advance);
    }, [advance]);

    useEffect(() => {
        if (isOpen && advance) {
            setShowAssignPanel(false);
            setInvoiceSearch("");
            setShowStatementPanel(false);
            fetchAssignedInvoices(advance.id)
                .then(setAssignedInvoices)
                .catch(() => setAssignedInvoices([]));
        }
    }, [isOpen, advance]);

    const handleOpenAssignPanel = async () => {
        setShowAssignPanel(true);
        if (detailAdvance?.shiftId) {
            try {
                const all = await fetchShiftInvoices(detailAdvance.shiftId);
                const assignedIds = new Set(assignedInvoices.map((inv) => inv.id));
                setAvailableInvoices(all.filter((inv) => !assignedIds.has(inv.id) && !inv.operationalAdvance));
            } catch {
                setAvailableInvoices([]);
            }
        }
    };

    const handleAssignInvoice = async (invoiceId: number) => {
        if (!detailAdvance) return;
        try {
            const updated = await assignInvoice(detailAdvance.id, invoiceId);
            setDetailAdvance(updated);
            const invoices = await fetchAssignedInvoices(detailAdvance.id);
            setAssignedInvoices(invoices);
            setAvailableInvoices((prev) => prev.filter((inv) => inv.id !== invoiceId));
            onDataChanged();
        } catch (err: any) {
            alert(err.message || "Failed to assign invoice");
        }
    };

    const handleUnassignInvoice = async (invoiceId: number) => {
        if (!detailAdvance) return;
        try {
            const updated = await unassignInvoice(detailAdvance.id, invoiceId);
            setDetailAdvance(updated);
            const invoices = await fetchAssignedInvoices(detailAdvance.id);
            setAssignedInvoices(invoices);
            onDataChanged();
        } catch (err: any) {
            alert(err.message || "Failed to unassign invoice");
        }
    };

    const handleOpenStatementPanel = async () => {
        setShowStatementPanel(true);
        try {
            const stmts = await fetchOutstandingStatements();
            setAvailableStatements(stmts);
        } catch {
            setAvailableStatements([]);
        }
    };

    const handleAssignStatement = async (statementId: number) => {
        if (!detailAdvance) return;
        try {
            const updated = await assignStatement(detailAdvance.id, statementId);
            setDetailAdvance(updated);
            setShowStatementPanel(false);
            onDataChanged();
        } catch (err: any) {
            alert(err.message || "Failed to assign statement");
        }
    };

    const handleUnassignStatement = async () => {
        if (!detailAdvance) return;
        try {
            const updated = await unassignStatement(detailAdvance.id);
            setDetailAdvance(updated);
            onDataChanged();
        } catch (err: any) {
            alert(err.message || "Failed to unassign statement");
        }
    };

    const filteredAvailableInvoices = useMemo(() => {
        if (!invoiceSearch) return availableInvoices;
        const q = invoiceSearch.toLowerCase();
        return availableInvoices.filter(
            (inv) =>
                inv.billNo?.toLowerCase().includes(q) ||
                inv.customer?.name?.toLowerCase().includes(q) ||
                inv.vehicle?.vehicleNumber?.toLowerCase().includes(q)
        );
    }, [availableInvoices, invoiceSearch]);

    const handleClose = () => {
        setDetailAdvance(null);
        onClose();
    };

    return (
        <Modal
            isOpen={isOpen}
            onClose={handleClose}
            title="Advance Details"
        >
            {detailAdvance && (
                <div className="space-y-5">
                    {/* Advance Summary */}
                    <div className="p-4 bg-white/5 rounded-xl border border-border">
                        <div className="flex items-center gap-2 mb-4">
                            {(() => {
                                const meta = getAdvanceTypeMeta(detailAdvance.advanceType);
                                const Icon = meta.icon;
                                return (
                                    <>
                                        <div className={`p-1.5 rounded-lg ${meta.color}`}>
                                            <Icon className="w-4 h-4" />
                                        </div>
                                        <span className="text-sm font-bold text-foreground">{meta.label}</span>
                                    </>
                                );
                            })()}
                            <span className={`ml-auto px-2.5 py-1 rounded-full text-[10px] font-bold uppercase ${(STATUS_CONFIG[detailAdvance.status] || STATUS_CONFIG.GIVEN).color}`}>
                                {(STATUS_CONFIG[detailAdvance.status] || STATUS_CONFIG.GIVEN).label}
                            </span>
                        </div>

                        <div className="grid grid-cols-2 gap-x-6 gap-y-3 text-sm">
                            <div>
                                <p className="text-[10px] uppercase tracking-wider text-muted-foreground">Recipient</p>
                                <p className="font-medium text-foreground">{detailAdvance.recipientName}</p>
                                {detailAdvance.employee && (
                                    <p className="text-[10px] text-primary mt-0.5">{detailAdvance.employee.designation || "Employee"}</p>
                                )}
                            </div>
                            <div>
                                <p className="text-[10px] uppercase tracking-wider text-muted-foreground">Date</p>
                                <p className="font-medium text-foreground">{formatDateTime(detailAdvance.advanceDate)}</p>
                            </div>
                            {detailAdvance.purpose && (
                                <div className="col-span-2">
                                    <p className="text-[10px] uppercase tracking-wider text-muted-foreground">Purpose</p>
                                    <p className="font-medium text-foreground">{detailAdvance.purpose}</p>
                                </div>
                            )}
                        </div>

                        {/* Financial Breakdown */}
                        <div className="mt-4 pt-4 border-t border-border">
                            <div className="grid grid-cols-4 gap-3 text-sm">
                                <div>
                                    <p className="text-[10px] uppercase tracking-wider text-muted-foreground">Amount</p>
                                    <p className="font-bold text-foreground text-base">{formatCurrency(detailAdvance.amount)}</p>
                                </div>
                                <div>
                                    <p className="text-[10px] uppercase tracking-wider text-muted-foreground">Bills</p>
                                    <p className="font-bold text-teal-500">{formatCurrency(detailAdvance.utilizedAmount || 0)}</p>
                                </div>
                                <div>
                                    <p className="text-[10px] uppercase tracking-wider text-muted-foreground">Returned</p>
                                    <p className="font-bold text-green-500">{formatCurrency(detailAdvance.returnedAmount || 0)}</p>
                                </div>
                                <div>
                                    <p className="text-[10px] uppercase tracking-wider text-muted-foreground">Outstanding</p>
                                    <p className="font-bold text-red-500">
                                        {formatCurrency(Math.max(0, detailAdvance.amount - (detailAdvance.returnedAmount || 0) - (detailAdvance.utilizedAmount || 0)))}
                                    </p>
                                </div>
                            </div>
                        </div>
                    </div>

                    {/* Assigned Invoices */}
                    <div>
                        <div className="flex items-center justify-between mb-3">
                            <h3 className="text-sm font-bold text-foreground flex items-center gap-2">
                                <FileText className="w-4 h-4 text-primary" />
                                Assigned Invoices ({assignedInvoices.length})
                            </h3>
                            {detailAdvance.status !== "CANCELLED" && detailAdvance.status !== "RETURNED" && detailAdvance.status !== "SETTLED" && (
                                <button
                                    onClick={handleOpenAssignPanel}
                                    className="text-xs font-medium text-primary hover:text-primary/80 flex items-center gap-1 transition-colors"
                                >
                                    <Link2 className="w-3.5 h-3.5" />
                                    Assign Invoice
                                </button>
                            )}
                        </div>

                        {assignedInvoices.length === 0 ? (
                            <div className="text-center py-6 text-muted-foreground text-sm border border-dashed border-border rounded-xl">
                                No invoices assigned to this advance yet.
                            </div>
                        ) : (
                            <div className="space-y-2">
                                {assignedInvoices.map((inv) => (
                                    <div
                                        key={inv.id}
                                        className="flex items-center justify-between p-3 bg-card border border-border rounded-xl hover:bg-white/5 transition-colors"
                                    >
                                        <div className="flex items-center gap-3">
                                            <div className="p-1.5 rounded-lg bg-teal-500/10 text-teal-500">
                                                <Receipt className="w-3.5 h-3.5" />
                                            </div>
                                            <div>
                                                <p className="text-sm font-medium text-foreground">
                                                    {inv.billNo || `INV-${inv.id}`}
                                                    <span className={`ml-2 px-1.5 py-0.5 rounded text-[9px] font-bold ${inv.billType === "CASH" ? "bg-green-500/10 text-green-500" : "bg-amber-500/10 text-amber-500"}`}>
                                                        {inv.billType}
                                                    </span>
                                                </p>
                                                <p className="text-[10px] text-muted-foreground">
                                                    {inv.customer?.name || "Walk-in"} {inv.vehicle ? `| ${inv.vehicle.vehicleNumber}` : ""} | {formatDateTime(inv.date)}
                                                </p>
                                            </div>
                                        </div>
                                        <div className="flex items-center gap-3">
                                            <span className="text-sm font-bold text-foreground">{formatCurrency(inv.netAmount)}</span>
                                            {detailAdvance.status !== "CANCELLED" && detailAdvance.status !== "RETURNED" && detailAdvance.status !== "SETTLED" && (
                                                <button
                                                    onClick={() => handleUnassignInvoice(inv.id)}
                                                    title="Unassign invoice"
                                                    className="p-1 rounded-lg hover:bg-red-500/10 text-muted-foreground hover:text-red-500 transition-all"
                                                >
                                                    <Unlink className="w-3.5 h-3.5" />
                                                </button>
                                            )}
                                        </div>
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>

                    {/* Assign Invoice Panel */}
                    {showAssignPanel && (
                        <div className="border border-primary/30 rounded-xl p-4 bg-primary/5">
                            <div className="flex items-center justify-between mb-3">
                                <h4 className="text-sm font-bold text-foreground">
                                    Available Invoices (Shift #{detailAdvance.shiftId})
                                </h4>
                                <button
                                    onClick={() => setShowAssignPanel(false)}
                                    className="text-xs text-muted-foreground hover:text-foreground"
                                >
                                    Close
                                </button>
                            </div>

                            <div className="relative mb-3">
                                <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-muted-foreground" />
                                <input
                                    type="text"
                                    placeholder="Search by bill no, customer, vehicle..."
                                    value={invoiceSearch}
                                    onChange={(e) => setInvoiceSearch(e.target.value)}
                                    className="w-full pl-9 pr-4 py-2 bg-background border border-border rounded-lg text-foreground text-xs placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                                />
                            </div>

                            <div className="max-h-48 overflow-y-auto space-y-1.5">
                                {filteredAvailableInvoices.length === 0 ? (
                                    <p className="text-center py-4 text-muted-foreground text-xs">
                                        No unassigned invoices available in this shift.
                                    </p>
                                ) : (
                                    filteredAvailableInvoices.map((inv) => (
                                        <div
                                            key={inv.id}
                                            className="flex items-center justify-between p-2.5 bg-card border border-border rounded-lg hover:border-primary/30 transition-colors cursor-pointer"
                                            onClick={() => handleAssignInvoice(inv.id)}
                                        >
                                            <div>
                                                <p className="text-xs font-medium text-foreground">
                                                    {inv.billNo || `INV-${inv.id}`}
                                                    <span className={`ml-1.5 px-1.5 py-0.5 rounded text-[9px] font-bold ${inv.billType === "CASH" ? "bg-green-500/10 text-green-500" : "bg-amber-500/10 text-amber-500"}`}>
                                                        {inv.billType}
                                                    </span>
                                                </p>
                                                <p className="text-[10px] text-muted-foreground">
                                                    {inv.customer?.name || "Walk-in"} {inv.vehicle ? `| ${inv.vehicle.vehicleNumber}` : ""}
                                                </p>
                                            </div>
                                            <div className="flex items-center gap-2">
                                                <span className="text-xs font-bold text-foreground">{formatCurrency(inv.netAmount)}</span>
                                                <ChevronRight className="w-3.5 h-3.5 text-primary" />
                                            </div>
                                        </div>
                                    ))
                                )}
                            </div>
                        </div>
                    )}

                    {/* Linked Statement */}
                    <div>
                        <div className="flex items-center justify-between mb-3">
                            <h3 className="text-sm font-bold text-foreground flex items-center gap-2">
                                <FileText className="w-4 h-4 text-purple-500" />
                                Linked Statement
                            </h3>
                            {!detailAdvance.statement && detailAdvance.status !== "CANCELLED" && detailAdvance.status !== "RETURNED" && detailAdvance.status !== "SETTLED" && (
                                <button
                                    onClick={handleOpenStatementPanel}
                                    className="text-xs font-medium text-purple-500 hover:text-purple-400 flex items-center gap-1 transition-colors"
                                >
                                    <Link2 className="w-3.5 h-3.5" />
                                    Assign Statement
                                </button>
                            )}
                        </div>

                        {detailAdvance.statement ? (
                            <div className="flex items-center justify-between p-3 bg-card border border-border rounded-xl">
                                <div className="flex items-center gap-3">
                                    <div className="p-1.5 rounded-lg bg-purple-500/10 text-purple-500">
                                        <FileText className="w-3.5 h-3.5" />
                                    </div>
                                    <div>
                                        <p className="text-sm font-medium text-foreground">
                                            Stmt #{detailAdvance.statement.statementNo}
                                            <span className={`ml-2 px-1.5 py-0.5 rounded text-[9px] font-bold ${detailAdvance.statement.status === "PAID" ? "bg-green-500/10 text-green-500" : "bg-red-500/10 text-red-500"}`}>
                                                {detailAdvance.statement.status}
                                            </span>
                                        </p>
                                        <p className="text-[10px] text-muted-foreground">
                                            {detailAdvance.statement.customer?.name || "-"} | Net: {formatCurrency(detailAdvance.statement.netAmount)}
                                        </p>
                                    </div>
                                </div>
                                <div className="flex items-center gap-3">
                                    <span className="text-sm font-bold text-foreground">{formatCurrency(detailAdvance.statement.totalAmount)}</span>
                                    {detailAdvance.status !== "CANCELLED" && detailAdvance.status !== "RETURNED" && detailAdvance.status !== "SETTLED" && (
                                        <button
                                            onClick={handleUnassignStatement}
                                            title="Unassign statement"
                                            className="p-1 rounded-lg hover:bg-red-500/10 text-muted-foreground hover:text-red-500 transition-all"
                                        >
                                            <Unlink className="w-3.5 h-3.5" />
                                        </button>
                                    )}
                                </div>
                            </div>
                        ) : (
                            <div className="text-center py-4 text-muted-foreground text-sm border border-dashed border-border rounded-xl">
                                No statement linked.
                            </div>
                        )}
                    </div>

                    {/* Statement Assignment Panel */}
                    {showStatementPanel && (
                        <div className="border border-purple-500/30 rounded-xl p-4 bg-purple-500/5">
                            <div className="flex items-center justify-between mb-3">
                                <h4 className="text-sm font-bold text-foreground">Outstanding Statements</h4>
                                <button onClick={() => setShowStatementPanel(false)} className="text-xs text-muted-foreground hover:text-foreground">Close</button>
                            </div>
                            <div className="max-h-48 overflow-y-auto space-y-1.5">
                                {availableStatements.length === 0 ? (
                                    <p className="text-center py-4 text-muted-foreground text-xs">No outstanding statements available.</p>
                                ) : (
                                    availableStatements.map((stmt) => (
                                        <div
                                            key={stmt.id}
                                            className="flex items-center justify-between p-2.5 bg-card border border-border rounded-lg hover:border-purple-500/30 transition-colors cursor-pointer"
                                            onClick={() => handleAssignStatement(stmt.id)}
                                        >
                                            <div>
                                                <p className="text-xs font-medium text-foreground">
                                                    Stmt #{stmt.statementNo}
                                                </p>
                                                <p className="text-[10px] text-muted-foreground">{stmt.customer?.name || "-"}</p>
                                            </div>
                                            <div className="flex items-center gap-2">
                                                <span className="text-xs font-bold text-foreground">{formatCurrency(stmt.balanceAmount)}</span>
                                                <ChevronRight className="w-3.5 h-3.5 text-purple-500" />
                                            </div>
                                        </div>
                                    ))
                                )}
                            </div>
                        </div>
                    )}

                    {/* Return Info */}
                    {detailAdvance.returnDate && (
                        <div className="p-3 bg-green-500/5 border border-green-500/20 rounded-xl">
                            <p className="text-xs text-green-500 font-medium">
                                Returned on {formatDateTime(detailAdvance.returnDate)}
                            </p>
                            {detailAdvance.returnRemarks && (
                                <p className="text-xs text-muted-foreground mt-1">{detailAdvance.returnRemarks}</p>
                            )}
                        </div>
                    )}
                </div>
            )}
        </Modal>
    );
}
