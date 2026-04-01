"use client";

import { useEffect, useState } from "react";
import { GlassCard } from "@/components/ui/glass-card";
import {
    getEmployeeDashboard,
    EmployeeDashboardData,
} from "@/lib/api/station";
import {
    CalendarCheck,
    CalendarDays,
    IndianRupee,
    Wallet,
    Clock,
    Loader2,
    AlertCircle,
    User,
    CheckCircle2,
    XCircle,
    MinusCircle,
} from "lucide-react";

function formatCurrency(val?: number | null) {
    if (val == null) return "0.00";
    return val.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function formatDate(dt?: string | null) {
    if (!dt) return "-";
    return new Date(dt).toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "numeric" });
}

function formatTime(t?: string | null) {
    if (!t) return "-";
    // Handle HH:mm:ss format
    const parts = t.split(":");
    if (parts.length >= 2) {
        const h = parseInt(parts[0]);
        const m = parts[1];
        const ampm = h >= 12 ? "PM" : "AM";
        const h12 = h % 12 || 12;
        return `${h12}:${m} ${ampm}`;
    }
    return t;
}

function monthName(month: number) {
    return new Date(2000, month - 1).toLocaleString("en-IN", { month: "short" });
}

const statusColors: Record<string, string> = {
    PRESENT: "text-green-500",
    ABSENT: "text-red-500",
    HALF_DAY: "text-amber-500",
    ON_LEAVE: "text-blue-500",
    NOT_MARKED: "text-muted-foreground",
    PENDING: "text-amber-500",
    APPROVED: "text-green-500",
    REJECTED: "text-red-500",
    PAID: "text-green-500",
    DRAFT: "text-muted-foreground",
    GIVEN: "text-amber-500",
    PARTIALLY_RETURNED: "text-blue-500",
    RETURNED: "text-green-500",
    SETTLED: "text-green-500",
    DEDUCTED: "text-muted-foreground",
    CANCELLED: "text-red-500",
};

function StatusBadge({ status }: { status: string }) {
    const color = statusColors[status] || "text-muted-foreground";
    return (
        <span className={`inline-flex items-center gap-1 text-xs font-medium ${color}`}>
            <span className={`w-1.5 h-1.5 rounded-full ${color.replace("text-", "bg-")}`} />
            {status.replace(/_/g, " ")}
        </span>
    );
}

export function EmployeeDashboard() {
    const [data, setData] = useState<EmployeeDashboardData | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");

    useEffect(() => {
        getEmployeeDashboard()
            .then(setData)
            .catch((err) => setError(err.message || "Failed to load dashboard"))
            .finally(() => setLoading(false));
    }, []);

    if (loading) {
        return (
            <div className="flex items-center justify-center h-[60vh]">
                <Loader2 className="w-8 h-8 animate-spin text-primary" />
            </div>
        );
    }

    if (error || !data) {
        return (
            <div className="flex items-center justify-center h-[60vh]">
                <GlassCard className="p-8 text-center max-w-md">
                    <AlertCircle className="w-10 h-10 text-red-500 mx-auto mb-3" />
                    <p className="text-sm text-muted-foreground">{error || "No data available"}</p>
                </GlassCard>
            </div>
        );
    }

    const { personalInfo, attendanceSummary, leaveSummary, salarySummary, advanceSummary } = data;
    const attendancePercent = attendanceSummary.totalWorkingDays > 0
        ? Math.round((attendanceSummary.thisMonthPresent / attendanceSummary.totalWorkingDays) * 100)
        : 0;

    const totalLeaveRemaining = leaveSummary.balances.reduce((sum, b) => sum + (b.remaining || 0), 0);
    const totalLeaveAllotted = leaveSummary.balances.reduce((sum, b) => sum + (b.totalAllotted || 0), 0);

    return (
        <div className="space-y-6">
            {/* Personal Info Banner */}
            <GlassCard className="p-6">
                <div className="flex items-center gap-4">
                    <div className="w-14 h-14 rounded-full bg-primary/20 flex items-center justify-center text-primary font-bold text-xl">
                        {personalInfo.photoUrl ? (
                            <img src={personalInfo.photoUrl} alt="" className="w-14 h-14 rounded-full object-cover" />
                        ) : (
                            personalInfo.name?.charAt(0)?.toUpperCase() || <User className="w-6 h-6" />
                        )}
                    </div>
                    <div className="flex-1">
                        <h1 className="text-xl font-bold">{personalInfo.name}</h1>
                        <div className="flex items-center gap-4 text-sm text-muted-foreground mt-1">
                            {personalInfo.designation && (
                                <span className="bg-primary/10 text-primary px-2 py-0.5 rounded text-xs font-medium">
                                    {personalInfo.designation}
                                </span>
                            )}
                            {personalInfo.employeeCode && <span>ID: {personalInfo.employeeCode}</span>}
                            {personalInfo.joinDate && <span>Joined: {formatDate(personalInfo.joinDate)}</span>}
                            {personalInfo.phone && <span>{personalInfo.phone}</span>}
                        </div>
                    </div>
                    {/* Today's status */}
                    <div className="text-right">
                        <p className="text-[10px] uppercase tracking-wider text-muted-foreground font-bold">Today</p>
                        <div className="mt-1">
                            {attendanceSummary.todayStatus === "PRESENT" ? (
                                <div className="flex items-center gap-1 text-green-500">
                                    <CheckCircle2 className="w-4 h-4" />
                                    <span className="text-sm font-medium">Present</span>
                                </div>
                            ) : attendanceSummary.todayStatus === "ABSENT" ? (
                                <div className="flex items-center gap-1 text-red-500">
                                    <XCircle className="w-4 h-4" />
                                    <span className="text-sm font-medium">Absent</span>
                                </div>
                            ) : (
                                <div className="flex items-center gap-1 text-muted-foreground">
                                    <MinusCircle className="w-4 h-4" />
                                    <span className="text-sm font-medium">Not Marked</span>
                                </div>
                            )}
                            {attendanceSummary.todayCheckIn && (
                                <p className="text-xs text-muted-foreground mt-0.5">
                                    In: {formatTime(attendanceSummary.todayCheckIn)}
                                    {attendanceSummary.todayCheckOut && ` · Out: ${formatTime(attendanceSummary.todayCheckOut)}`}
                                </p>
                            )}
                        </div>
                    </div>
                </div>
            </GlassCard>

            {/* KPI Cards */}
            <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
                <GlassCard className="p-4">
                    <div className="flex items-center gap-3">
                        <div className="p-2 bg-green-500/10 rounded-lg">
                            <CalendarCheck className="w-5 h-5 text-green-500" />
                        </div>
                        <div>
                            <p className="text-[10px] uppercase tracking-wider text-muted-foreground font-bold">Attendance</p>
                            <p className="text-2xl font-bold text-green-500 mt-1">
                                {attendanceSummary.thisMonthPresent}/{attendanceSummary.totalWorkingDays}
                            </p>
                            <p className="text-xs text-muted-foreground">{attendancePercent}% this month</p>
                        </div>
                    </div>
                </GlassCard>

                <GlassCard className="p-4">
                    <div className="flex items-center gap-3">
                        <div className="p-2 bg-blue-500/10 rounded-lg">
                            <CalendarDays className="w-5 h-5 text-blue-500" />
                        </div>
                        <div>
                            <p className="text-[10px] uppercase tracking-wider text-muted-foreground font-bold">Leave Balance</p>
                            <p className="text-2xl font-bold text-blue-500 mt-1">
                                {totalLeaveRemaining}/{totalLeaveAllotted}
                            </p>
                            <p className="text-xs text-muted-foreground">
                                {leaveSummary.pendingRequests.length} pending
                            </p>
                        </div>
                    </div>
                </GlassCard>

                <GlassCard className="p-4">
                    <div className="flex items-center gap-3">
                        <div className="p-2 bg-emerald-500/10 rounded-lg">
                            <IndianRupee className="w-5 h-5 text-emerald-500" />
                        </div>
                        <div>
                            <p className="text-[10px] uppercase tracking-wider text-muted-foreground font-bold">Salary</p>
                            <p className="text-2xl font-bold text-emerald-500 mt-1">
                                {formatCurrency(salarySummary.currentSalary)}
                            </p>
                            <p className="text-xs text-muted-foreground">
                                {salarySummary.salaryDay ? `Day ${salarySummary.salaryDay}` : "Monthly"}
                            </p>
                        </div>
                    </div>
                </GlassCard>

                <GlassCard className="p-4">
                    <div className="flex items-center gap-3">
                        <div className="p-2 bg-amber-500/10 rounded-lg">
                            <Wallet className="w-5 h-5 text-amber-500" />
                        </div>
                        <div>
                            <p className="text-[10px] uppercase tracking-wider text-muted-foreground font-bold">Advances</p>
                            <p className="text-2xl font-bold text-amber-500 mt-1">
                                {formatCurrency(advanceSummary.totalOutstanding)}
                            </p>
                            <p className="text-xs text-muted-foreground">outstanding</p>
                        </div>
                    </div>
                </GlassCard>
            </div>

            {/* Attendance & Leave Row */}
            <div className="grid md:grid-cols-2 gap-6">
                {/* Recent Attendance */}
                <GlassCard className="p-5">
                    <h3 className="text-sm font-bold mb-3 flex items-center gap-2">
                        <Clock className="w-4 h-4 text-muted-foreground" />
                        Recent Attendance
                    </h3>
                    {attendanceSummary.recentAttendance.length === 0 ? (
                        <p className="text-sm text-muted-foreground">No attendance records this month</p>
                    ) : (
                        <div className="overflow-x-auto">
                            <table className="w-full text-sm">
                                <thead>
                                    <tr className="border-b border-border text-muted-foreground text-xs">
                                        <th className="text-left py-2">Date</th>
                                        <th className="text-left py-2">Status</th>
                                        <th className="text-left py-2">In</th>
                                        <th className="text-left py-2">Out</th>
                                        <th className="text-right py-2">Hours</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {attendanceSummary.recentAttendance.map((a) => (
                                        <tr key={a.date} className="border-b border-border/50">
                                            <td className="py-2">{formatDate(a.date)}</td>
                                            <td className="py-2"><StatusBadge status={a.status} /></td>
                                            <td className="py-2 text-muted-foreground">{formatTime(a.checkIn)}</td>
                                            <td className="py-2 text-muted-foreground">{formatTime(a.checkOut)}</td>
                                            <td className="py-2 text-right text-muted-foreground">
                                                {a.hoursWorked != null ? `${a.hoursWorked.toFixed(1)}h` : "-"}
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    )}
                </GlassCard>

                {/* Leave Requests */}
                <GlassCard className="p-5">
                    <h3 className="text-sm font-bold mb-3 flex items-center gap-2">
                        <CalendarDays className="w-4 h-4 text-muted-foreground" />
                        Leave Requests
                    </h3>

                    {/* Leave Balances */}
                    {leaveSummary.balances.length > 0 && (
                        <div className="grid grid-cols-2 gap-2 mb-4">
                            {leaveSummary.balances.map((b) => (
                                <div key={b.leaveType} className="bg-muted/50 rounded-md px-3 py-2">
                                    <p className="text-xs text-muted-foreground">{b.leaveType}</p>
                                    <p className="text-sm font-medium">
                                        {b.remaining}/{b.totalAllotted}
                                        <span className="text-xs text-muted-foreground ml-1">remaining</span>
                                    </p>
                                </div>
                            ))}
                        </div>
                    )}

                    {/* Recent Leave Requests */}
                    {leaveSummary.recentRequests.length === 0 ? (
                        <p className="text-sm text-muted-foreground">No leave requests</p>
                    ) : (
                        <div className="space-y-2">
                            {leaveSummary.recentRequests.map((r) => (
                                <div key={r.id} className="flex items-center justify-between border-b border-border/50 py-2">
                                    <div>
                                        <p className="text-sm font-medium">{r.leaveType}</p>
                                        <p className="text-xs text-muted-foreground">
                                            {formatDate(r.fromDate)} - {formatDate(r.toDate)} · {r.days} day{r.days !== 1 ? "s" : ""}
                                        </p>
                                    </div>
                                    <StatusBadge status={r.status} />
                                </div>
                            ))}
                        </div>
                    )}
                </GlassCard>
            </div>

            {/* Salary History */}
            <GlassCard className="p-5">
                <h3 className="text-sm font-bold mb-3 flex items-center gap-2">
                    <IndianRupee className="w-4 h-4 text-muted-foreground" />
                    Salary History
                </h3>
                {salarySummary.recentPayments.length === 0 ? (
                    <p className="text-sm text-muted-foreground">No salary records</p>
                ) : (
                    <div className="overflow-x-auto">
                        <table className="w-full text-sm">
                            <thead>
                                <tr className="border-b border-border text-muted-foreground text-xs">
                                    <th className="text-left py-2">Period</th>
                                    <th className="text-right py-2">Base</th>
                                    <th className="text-right py-2">Deductions</th>
                                    <th className="text-right py-2">Net Pay</th>
                                    <th className="text-left py-2">Status</th>
                                    <th className="text-left py-2">Paid On</th>
                                </tr>
                            </thead>
                            <tbody>
                                {salarySummary.recentPayments.map((sp) => {
                                    const totalDeductions = (sp.advanceDeduction || 0) + (sp.lopDeduction || 0) + (sp.otherDeductions || 0);
                                    return (
                                        <tr key={`${sp.year}-${sp.month}`} className="border-b border-border/50">
                                            <td className="py-2 font-medium">{monthName(sp.month)} {sp.year}</td>
                                            <td className="py-2 text-right text-muted-foreground">{formatCurrency(sp.baseSalary)}</td>
                                            <td className="py-2 text-right text-red-400">
                                                {totalDeductions > 0 ? `-${formatCurrency(totalDeductions)}` : "-"}
                                            </td>
                                            <td className="py-2 text-right font-medium">{formatCurrency(sp.netPayable)}</td>
                                            <td className="py-2"><StatusBadge status={sp.status} /></td>
                                            <td className="py-2 text-muted-foreground">{formatDate(sp.paymentDate)}</td>
                                        </tr>
                                    );
                                })}
                            </tbody>
                        </table>
                    </div>
                )}
            </GlassCard>

            {/* Advances */}
            {advanceSummary.recentAdvances.length > 0 && (
                <GlassCard className="p-5">
                    <h3 className="text-sm font-bold mb-3 flex items-center gap-2">
                        <Wallet className="w-4 h-4 text-muted-foreground" />
                        Recent Advances
                    </h3>
                    <div className="overflow-x-auto">
                        <table className="w-full text-sm">
                            <thead>
                                <tr className="border-b border-border text-muted-foreground text-xs">
                                    <th className="text-left py-2">Date</th>
                                    <th className="text-left py-2">Purpose</th>
                                    <th className="text-right py-2">Amount</th>
                                    <th className="text-right py-2">Returned</th>
                                    <th className="text-left py-2">Status</th>
                                </tr>
                            </thead>
                            <tbody>
                                {advanceSummary.recentAdvances.map((a) => (
                                    <tr key={a.id} className="border-b border-border/50">
                                        <td className="py-2">{formatDate(a.date)}</td>
                                        <td className="py-2 text-muted-foreground">{a.purpose || "-"}</td>
                                        <td className="py-2 text-right font-medium">{formatCurrency(a.amount)}</td>
                                        <td className="py-2 text-right text-muted-foreground">
                                            {a.returnedAmount ? formatCurrency(a.returnedAmount) : "-"}
                                        </td>
                                        <td className="py-2">{a.status && <StatusBadge status={a.status} />}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                </GlassCard>
            )}
        </div>
    );
}
