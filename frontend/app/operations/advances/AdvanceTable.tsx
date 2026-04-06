"use client";

import { Eye, Undo2, XCircle } from "lucide-react";
import { GlassCard } from "@/components/ui/glass-card";
import { TablePagination, useClientPagination } from "@/components/ui/table-pagination";
import { PermissionGate } from "@/components/permission-gate";
import {
    OperationalAdvance,
    STATUS_CONFIG,
    getAdvanceTypeMeta,
    formatDateTime,
    formatCurrency,
} from "./advances-api";

interface AdvanceTableProps {
    filtered: OperationalAdvance[];
    onOpenDetail: (adv: OperationalAdvance) => void;
    onOpenReturn: (adv: OperationalAdvance) => void;
    onCancel: (adv: OperationalAdvance) => void;
}

export function AdvanceTable({ filtered, onOpenDetail, onOpenReturn, onCancel }: AdvanceTableProps) {
    const { page, setPage, totalPages, totalElements, pageSize, paginatedData: pagedAdvances } = useClientPagination(filtered);

    return (
        <GlassCard className="overflow-hidden border-none p-0 mb-6">
            <div className="overflow-x-auto">
                <table className="w-full text-left border-collapse">
                    <thead>
                        <tr className="bg-white/5 border-b border-border/50">
                            <th className="px-4 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Date</th>
                            <th className="px-4 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Type</th>
                            <th className="px-4 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Recipient</th>
                            <th className="px-4 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right">Amount</th>
                            <th className="px-4 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right">Bills</th>
                            <th className="px-4 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right">Returned</th>
                            <th className="px-4 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right">Outstanding</th>
                            <th className="px-4 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center">Status</th>
                            <th className="px-4 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center">Actions</th>
                        </tr>
                    </thead>
                    <tbody className="divide-y divide-border/30">
                        {filtered.length === 0 ? (
                            <tr>
                                <td colSpan={9} className="px-6 py-12 text-center text-muted-foreground">
                                    No advances found
                                </td>
                            </tr>
                        ) : (
                            pagedAdvances.map((adv) => {
                                const meta = getAdvanceTypeMeta(adv.advanceType);
                                const Icon = meta.icon;
                                const utilized = adv.utilizedAmount || 0;
                                const returned = adv.returnedAmount || 0;
                                const outstanding = adv.amount - returned - utilized;
                                const statusCfg = STATUS_CONFIG[adv.status] || STATUS_CONFIG.GIVEN;
                                const canReturn = adv.status === "GIVEN" || adv.status === "PARTIALLY_RETURNED";
                                const canCancel = adv.status === "GIVEN";

                                return (
                                    <tr key={adv.id} className="hover:bg-white/5 transition-colors group">
                                        <td className="px-4 py-3 text-xs text-muted-foreground whitespace-nowrap">
                                            {formatDateTime(adv.advanceDate)}
                                        </td>
                                        <td className="px-4 py-3">
                                            <div className="flex items-center gap-2">
                                                <div className={`p-1.5 rounded-lg ${meta.color}`}>
                                                    <Icon className="w-3.5 h-3.5" />
                                                </div>
                                                <span className="text-sm font-medium text-foreground">
                                                    {meta.label}
                                                </span>
                                            </div>
                                        </td>
                                        <td className="px-4 py-3">
                                            <div>
                                                <p className="text-sm font-medium text-foreground">{adv.recipientName}</p>
                                                {adv.employee && (
                                                    <p className="text-[10px] text-primary">{adv.employee.designation || "Employee"}</p>
                                                )}
                                                {adv.recipientPhone && !adv.employee && (
                                                    <p className="text-[10px] text-muted-foreground">{adv.recipientPhone}</p>
                                                )}
                                            </div>
                                        </td>
                                        <td className="px-4 py-3 text-right">
                                            <span className="text-sm font-bold text-foreground">
                                                {formatCurrency(adv.amount)}
                                            </span>
                                        </td>
                                        <td className="px-4 py-3 text-right">
                                            <span className={`text-sm ${utilized > 0 ? "text-teal-500 font-medium" : "text-muted-foreground"}`}>
                                                {utilized > 0 ? formatCurrency(utilized) : "-"}
                                            </span>
                                        </td>
                                        <td className="px-4 py-3 text-right">
                                            <span className="text-sm text-foreground">
                                                {returned > 0 ? formatCurrency(returned) : "-"}
                                            </span>
                                        </td>
                                        <td className="px-4 py-3 text-right">
                                            <span className={`text-sm font-bold ${outstanding > 0 ? "text-red-500" : "text-foreground"}`}>
                                                {adv.status === "CANCELLED" ? "-" : formatCurrency(Math.max(0, outstanding))}
                                            </span>
                                        </td>
                                        <td className="px-4 py-3 text-center">
                                            <span className={`px-2.5 py-1 rounded-full text-[10px] font-bold uppercase ${statusCfg.color}`}>
                                                {statusCfg.label}
                                            </span>
                                        </td>
                                        <td className="px-4 py-3 text-center">
                                            <div className="flex items-center justify-center gap-1">
                                                <button
                                                    onClick={() => onOpenDetail(adv)}
                                                    title="View details & assign invoices"
                                                    className="p-1.5 rounded-lg hover:bg-primary/10 text-muted-foreground hover:text-primary transition-all"
                                                >
                                                    <Eye className="w-3.5 h-3.5" />
                                                </button>
                                                {canReturn && (
                                                    <PermissionGate permission="FINANCE_UPDATE">
                                                        <button
                                                            onClick={() => onOpenReturn(adv)}
                                                            title="Record return"
                                                            className="p-1.5 rounded-lg hover:bg-green-500/10 text-muted-foreground hover:text-green-500 opacity-100 md:opacity-0 group-hover:opacity-100 transition-all"
                                                        >
                                                            <Undo2 className="w-3.5 h-3.5" />
                                                        </button>
                                                    </PermissionGate>
                                                )}
                                                {canCancel && (
                                                    <PermissionGate permission="FINANCE_UPDATE">
                                                        <button
                                                            onClick={() => onCancel(adv)}
                                                            title="Cancel advance"
                                                            className="p-1.5 rounded-lg hover:bg-red-500/10 text-muted-foreground hover:text-red-500 opacity-100 md:opacity-0 group-hover:opacity-100 transition-all"
                                                        >
                                                            <XCircle className="w-3.5 h-3.5" />
                                                        </button>
                                                    </PermissionGate>
                                                )}
                                            </div>
                                        </td>
                                    </tr>
                                );
                            })
                        )}
                    </tbody>
                </table>
            </div>
            <TablePagination
                page={page}
                totalPages={totalPages}
                totalElements={totalElements}
                pageSize={pageSize}
                onPageChange={setPage}
            />
        </GlassCard>
    );
}
