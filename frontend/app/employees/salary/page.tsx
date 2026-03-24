"use client";

import { useState, useEffect } from "react";
import {
    IndianRupee,
    PlayCircle,
    Check,
    ChevronDown,
    Wallet,
    TrendingDown,
    Banknote,
} from "lucide-react";
import { GlassCard } from "@/components/ui/glass-card";
import { Badge } from "@/components/ui/badge";
import { TablePagination, useClientPagination } from "@/components/ui/table-pagination";
import { FormErrorBanner } from "@/components/ui/field-error";
import {
    getMonthlyPayments,
    processMonthlyPayroll,
    markSalaryAsPaid,
    SalaryPayment,
} from "@/lib/api/station";

const months = [
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
];

const formatRupees = (val: number) => `₹${val.toLocaleString("en-IN")}`;

export default function SalaryProcessingPage() {
    const [payments, setPayments] = useState<SalaryPayment[]>([]);
    const [isLoading, setIsLoading] = useState(false);
    const now = new Date();
    const [selectedMonth, setSelectedMonth] = useState(now.getMonth() + 1);
    const [selectedYear, setSelectedYear] = useState(now.getFullYear());
    const [processing, setProcessing] = useState(false);
    const [apiError, setApiError] = useState("");

    useEffect(() => {
        loadPayments();
    }, [selectedMonth, selectedYear]);

    const loadPayments = async () => {
        setIsLoading(true);
        try {
            const data = await getMonthlyPayments(selectedMonth, selectedYear);
            setPayments(data);
        } catch (e) {
            console.error(e);
        }
        setIsLoading(false);
    };

    const handleProcess = async () => {
        setProcessing(true);
        try {
            const data = await processMonthlyPayroll(selectedMonth, selectedYear);
            setPayments(data);
        } catch (e) {
            setApiError("Failed to process payroll");
        }
        setProcessing(false);
    };

    const handleMarkPaid = async (id: number) => {
        try {
            await markSalaryAsPaid(id, "CASH");
            loadPayments();
        } catch (e) {
            setApiError("Failed to mark as paid");
        }
    };

    const handleMarkAllPaid = async () => {
        const drafts = payments.filter((p) => p.status === "DRAFT");
        for (const d of drafts) {
            await markSalaryAsPaid(d.id, "CASH");
        }
        loadPayments();
    };

    const { page, setPage, totalPages, totalElements, pageSize, paginatedData } =
        useClientPagination(payments, 8);

    // Stats
    const totalBase = payments.reduce((s, p) => s + (p.baseSalary || 0), 0);
    const totalLop = payments.reduce((s, p) => s + (p.lopDeduction || 0), 0);
    const totalDeductions = payments.reduce((s, p) => s + (p.advanceDeduction || 0) + (p.lopDeduction || 0) + (p.otherDeductions || 0), 0);
    const totalNet = payments.reduce((s, p) => s + (p.netPayable || 0), 0);
    const paidCount = payments.filter((p) => p.status === "PAID").length;
    const draftCount = payments.filter((p) => p.status === "DRAFT").length;

    return (
        <div className="p-6 h-screen overflow-hidden bg-background transition-colors duration-300">
            <div className="max-w-7xl mx-auto h-full flex flex-col">
                {/* Header */}
                <div className="flex justify-between items-center mb-6">
                    <div>
                        <h1 className="text-4xl font-bold text-foreground tracking-tight">
                            Salary <span className="text-gradient">Processing</span>
                        </h1>
                        <p className="text-muted-foreground mt-2">Process monthly payroll for all employees</p>
                    </div>
                    <div className="flex gap-3">
                        <select
                            value={selectedMonth}
                            onChange={(e) => setSelectedMonth(Number(e.target.value))}
                            className="bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50 appearance-none"
                        >
                            {months.map((m, i) => (
                                <option key={i} value={i + 1}>{m}</option>
                            ))}
                        </select>
                        <input
                            type="number"
                            value={selectedYear}
                            onChange={(e) => setSelectedYear(Number(e.target.value))}
                            className="bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50 w-24"
                        />
                        <button
                            onClick={handleProcess}
                            disabled={processing}
                            className="btn-gradient px-6 py-3 rounded-xl font-medium flex items-center gap-2 disabled:opacity-50"
                        >
                            <PlayCircle className="w-5 h-5" />
                            {processing ? "Processing..." : "Process Payroll"}
                        </button>
                    </div>
                </div>

                <FormErrorBanner message={apiError} onDismiss={() => setApiError("")} />

                {/* Stats */}
                {payments.length > 0 && (
                    <div className="grid grid-cols-2 md:grid-cols-5 gap-3 mb-6">
                        <GlassCard className="p-4 text-center">
                            <Wallet className="w-5 h-5 mx-auto text-primary mb-1" />
                            <p className="text-xl font-bold text-primary">{formatRupees(totalBase)}</p>
                            <p className="text-xs text-muted-foreground">Total Base</p>
                        </GlassCard>
                        <GlassCard className="p-4 text-center">
                            <TrendingDown className="w-5 h-5 mx-auto text-red-500 mb-1" />
                            <p className="text-xl font-bold text-red-500">{formatRupees(totalDeductions)}</p>
                            <p className="text-xs text-muted-foreground">Deductions</p>
                        </GlassCard>
                        <GlassCard className="p-4 text-center">
                            <Banknote className="w-5 h-5 mx-auto text-emerald-500 mb-1" />
                            <p className="text-xl font-bold text-emerald-500">{formatRupees(totalNet)}</p>
                            <p className="text-xs text-muted-foreground">Net Payable</p>
                        </GlassCard>
                        <GlassCard className="p-4 text-center">
                            <Check className="w-5 h-5 mx-auto text-emerald-500 mb-1" />
                            <p className="text-2xl font-bold text-emerald-500">{paidCount}</p>
                            <p className="text-xs text-muted-foreground">Paid</p>
                        </GlassCard>
                        <GlassCard className="p-4 text-center">
                            <IndianRupee className="w-5 h-5 mx-auto text-amber-500 mb-1" />
                            <p className="text-2xl font-bold text-amber-500">{draftCount}</p>
                            <p className="text-xs text-muted-foreground">Draft</p>
                        </GlassCard>
                    </div>
                )}

                {/* Table */}
                {isLoading ? (
                    <div className="flex flex-col items-center justify-center py-20 text-muted-foreground">
                        <div className="w-12 h-12 border-4 border-primary/20 border-t-primary rounded-full animate-spin mb-4" />
                        <p className="animate-pulse">Loading salary data...</p>
                    </div>
                ) : payments.length === 0 ? (
                    <div className="text-center py-20 bg-black/5 dark:bg-white/5 rounded-2xl border border-dashed border-border">
                        <IndianRupee className="w-16 h-16 mx-auto text-muted-foreground mb-4 opacity-50" />
                        <h3 className="text-xl font-semibold text-foreground mb-2">No Payroll Data</h3>
                        <p className="text-muted-foreground mb-6">Click &quot;Process Payroll&quot; to generate salary for {months[selectedMonth - 1]} {selectedYear}.</p>
                    </div>
                ) : (
                    <div className="flex-1 overflow-hidden flex flex-col">
                        {draftCount > 0 && (
                            <div className="mb-3 flex justify-end">
                                <button onClick={handleMarkAllPaid} className="bg-emerald-500/10 text-emerald-500 hover:bg-emerald-500/20 px-4 py-2 rounded-xl text-sm font-medium transition-colors">
                                    Mark All as Paid ({draftCount})
                                </button>
                            </div>
                        )}
                        <GlassCard className="overflow-hidden border-none p-0 flex-1 flex flex-col">
                            <div className="overflow-auto flex-1">
                                <table className="w-full text-left border-collapse">
                                    <thead>
                                        <tr className="bg-white/5 border-b border-border/50">
                                            <th className="px-4 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center w-10">#</th>
                                            <th className="px-4 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Employee</th>
                                            <th className="px-4 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center">Pay Day</th>
                                            <th className="px-4 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right">Base</th>
                                            <th className="px-4 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right">Adv Ded.</th>
                                            <th className="px-4 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right">LOP</th>
                                            <th className="px-4 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right">Incentive</th>
                                            <th className="px-4 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right">Other</th>
                                            <th className="px-4 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-right">Net</th>
                                            <th className="px-4 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center">Status</th>
                                            <th className="px-4 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center">Paid On</th>
                                        </tr>
                                    </thead>
                                    <tbody className="divide-y divide-border/30">
                                        {paginatedData.map((p, idx) => (
                                            <tr key={p.id} className="hover:bg-white/5 transition-colors">
                                                <td className="px-4 py-4 text-xs font-mono text-muted-foreground text-center">{page * pageSize + idx + 1}</td>
                                                <td className="px-4 py-4">
                                                    <p className="font-medium text-foreground">{p.employee?.name}</p>
                                                    <p className="text-xs text-muted-foreground">{p.employee?.designation}</p>
                                                </td>
                                                <td className="px-4 py-4 text-center">
                                                    <span className="text-xs font-medium text-foreground">
                                                        {p.employee?.salaryDay ? `${p.employee.salaryDay}${p.employee.salaryDay === 1 ? "st" : p.employee.salaryDay === 2 ? "nd" : p.employee.salaryDay === 3 ? "rd" : "th"}` : "-"}
                                                    </span>
                                                </td>
                                                <td className="px-4 py-4 text-right font-medium">{formatRupees(p.baseSalary || 0)}</td>
                                                <td className="px-4 py-4 text-right text-red-500">{p.advanceDeduction > 0 ? `-${formatRupees(p.advanceDeduction)}` : "-"}</td>
                                                <td className="px-4 py-4 text-right text-red-500">
                                                    {p.lopDays > 0 ? (
                                                        <span title={`${p.lopDays} day${p.lopDays > 1 ? "s" : ""} LOP`}>
                                                            -{formatRupees(p.lopDeduction)} <span className="text-[10px]">({p.lopDays}d)</span>
                                                        </span>
                                                    ) : "-"}
                                                </td>
                                                <td className="px-4 py-4 text-right text-emerald-500">{p.incentiveAmount > 0 ? `+${formatRupees(p.incentiveAmount)}` : "-"}</td>
                                                <td className="px-4 py-4 text-right text-red-500">{p.otherDeductions > 0 ? `-${formatRupees(p.otherDeductions)}` : "-"}</td>
                                                <td className="px-4 py-4 text-right font-bold text-primary">{formatRupees(p.netPayable || 0)}</td>
                                                <td className="px-4 py-4 text-center">
                                                    <Badge variant={p.status === "PAID" ? "success" : "warning"}>
                                                        {p.status}
                                                    </Badge>
                                                </td>
                                                <td className="px-4 py-4 text-center">
                                                    {p.status === "DRAFT" && (
                                                        <button
                                                            onClick={() => handleMarkPaid(p.id)}
                                                            className="px-3 py-1.5 rounded-lg text-xs font-medium bg-emerald-500/10 text-emerald-500 hover:bg-emerald-500/20 transition-colors"
                                                        >
                                                            Pay
                                                        </button>
                                                    )}
                                                    {p.status === "PAID" && p.paymentDate && (
                                                        <span className="text-xs text-muted-foreground">{p.paymentDate}</span>
                                                    )}
                                                </td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                            <TablePagination page={page} totalPages={totalPages} totalElements={totalElements} pageSize={pageSize} onPageChange={setPage} />
                        </GlassCard>
                    </div>
                )}
            </div>
        </div>
    );
}
