"use client";

import { useState, useEffect } from "react";
import {
    Plus,
    Search,
    Check,
    X,
    CalendarDays,
    Clock,
    ChevronDown,
} from "lucide-react";
import { Modal } from "@/components/ui/modal";
import { GlassCard } from "@/components/ui/glass-card";
import { Badge } from "@/components/ui/badge";
import { TablePagination, useClientPagination } from "@/components/ui/table-pagination";
import { useFormValidation, required, min } from "@/lib/validation";
import { FieldError, inputErrorClass, FormErrorBanner } from "@/components/ui/field-error";
import {
    getLeaveTypes,
    createLeaveType,
    updateLeaveType,
    deleteLeaveType,
    getLeaveRequests,
    createLeaveRequest,
    approveLeaveRequest,
    rejectLeaveRequest,
    getEmployeeLeaveBalances,
    initializeLeaveBalances,
    getEmployees,
    LeaveType,
    LeaveRequest,
    LeaveBalance,
    EmployeeType,
} from "@/lib/api/station";

export default function LeaveManagementPage() {
    const [activeTab, setActiveTab] = useState<"requests" | "types" | "balances">("requests");
    const [employees, setEmployees] = useState<EmployeeType[]>([]);
    const [leaveTypes, setLeaveTypes] = useState<LeaveType[]>([]);
    const [leaveRequests, setLeaveRequests] = useState<LeaveRequest[]>([]);
    const [leaveBalances, setLeaveBalances] = useState<LeaveBalance[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [searchQuery, setSearchQuery] = useState("");
    const [statusFilter, setStatusFilter] = useState<string>("ALL");

    // Request Modal
    const [isRequestModalOpen, setIsRequestModalOpen] = useState(false);
    const [selectedEmployeeId, setSelectedEmployeeId] = useState("");
    const [selectedLeaveTypeId, setSelectedLeaveTypeId] = useState("");
    const [fromDate, setFromDate] = useState("");
    const [toDate, setToDate] = useState("");
    const [numberOfDays, setNumberOfDays] = useState("");
    const [reason, setReason] = useState("");

    // Type Modal
    const [isTypeModalOpen, setIsTypeModalOpen] = useState(false);
    const [editingType, setEditingType] = useState<LeaveType | null>(null);
    const [typeName, setTypeName] = useState("");
    const [maxDaysPerYear, setMaxDaysPerYear] = useState("");
    const [carryForward, setCarryForward] = useState(false);
    const [maxCarryForwardDays, setMaxCarryForwardDays] = useState("0");

    // Balance
    const [balanceEmployeeId, setBalanceEmployeeId] = useState("");
    const [balanceYear, setBalanceYear] = useState(new Date().getFullYear().toString());

    // Validation
    const { errors: reqErrors, validate: validateReq, clearError: clearReqError, clearAllErrors: clearAllReqErrors } = useFormValidation({
        employeeId: [required("Employee is required")],
        leaveTypeId: [required("Leave type is required")],
        fromDate: [required("From date is required")],
        toDate: [required("To date is required")],
    });
    const { errors: typeErrors, validate: validateType, clearError: clearTypeError, clearAllErrors: clearAllTypeErrors } = useFormValidation({
        typeName: [required("Type name is required")],
        maxDaysPerYear: [required("Max days is required"), min(1, "Must be at least 1")],
    });
    const [apiError, setApiError] = useState("");

    useEffect(() => {
        loadData();
    }, []);

    const loadData = async () => {
        setIsLoading(true);
        try {
            const [emps, types, requests] = await Promise.all([
                getEmployees(),
                getLeaveTypes(),
                getLeaveRequests(),
            ]);
            setEmployees(emps);
            setLeaveTypes(types);
            setLeaveRequests(requests);
        } catch (e) {
            console.error(e);
        }
        setIsLoading(false);
    };

    // Calculate days between dates
    useEffect(() => {
        if (fromDate && toDate) {
            const from = new Date(fromDate);
            const to = new Date(toDate);
            const diff = Math.ceil((to.getTime() - from.getTime()) / (1000 * 60 * 60 * 24)) + 1;
            setNumberOfDays(diff > 0 ? diff.toString() : "1");
        }
    }, [fromDate, toDate]);

    const filteredRequests = leaveRequests.filter((r) => {
        const matchesSearch =
            !searchQuery ||
            r.employee?.name?.toLowerCase().includes(searchQuery.toLowerCase());
        const matchesStatus = statusFilter === "ALL" || r.status === statusFilter;
        return matchesSearch && matchesStatus;
    });

    const { page, setPage, totalPages, totalElements, pageSize, paginatedData } =
        useClientPagination(filteredRequests, 8);

    const handleSubmitRequest = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!validateReq({ employeeId: selectedEmployeeId, leaveTypeId: selectedLeaveTypeId, fromDate, toDate })) return;
        try {
            await createLeaveRequest(Number(selectedEmployeeId), {
                leaveType: { id: Number(selectedLeaveTypeId) } as LeaveType,
                fromDate,
                toDate,
                numberOfDays: parseFloat(numberOfDays),
                reason,
            });
            setIsRequestModalOpen(false);
            resetRequestForm();
            loadData();
        } catch (e) {
            setApiError("Failed to submit request");
        }
    };

    const resetRequestForm = () => {
        setSelectedEmployeeId("");
        setSelectedLeaveTypeId("");
        setFromDate("");
        setToDate("");
        setNumberOfDays("");
        setReason("");
    };

    const handleApprove = async (id: number) => {
        try {
            await approveLeaveRequest(id, { approvedBy: "Manager" });
            loadData();
        } catch (e) {
            setApiError("Failed to approve");
        }
    };

    const handleReject = async (id: number) => {
        const remarks = prompt("Reason for rejection:");
        if (remarks === null) return;
        try {
            await rejectLeaveRequest(id, { remarks });
            loadData();
        } catch (e) {
            setApiError("Failed to reject");
        }
    };

    const handleSaveType = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!validateType({ typeName, maxDaysPerYear })) return;
        try {
            const data = {
                typeName,
                maxDaysPerYear: parseInt(maxDaysPerYear),
                carryForward,
                maxCarryForwardDays: parseInt(maxCarryForwardDays),
            };
            if (editingType) {
                await updateLeaveType(editingType.id, data);
            } else {
                await createLeaveType(data);
            }
            setIsTypeModalOpen(false);
            setEditingType(null);
            resetTypeForm();
            loadData();
        } catch (e) {
            setApiError("Failed to save leave type");
        }
    };

    const resetTypeForm = () => {
        setTypeName("");
        setMaxDaysPerYear("");
        setCarryForward(false);
        setMaxCarryForwardDays("0");
    };

    const handleEditType = (lt: LeaveType) => {
        setEditingType(lt);
        setTypeName(lt.typeName);
        setMaxDaysPerYear(lt.maxDaysPerYear?.toString() || "");
        setCarryForward(lt.carryForward || false);
        setMaxCarryForwardDays(lt.maxCarryForwardDays?.toString() || "0");
        setIsTypeModalOpen(true);
    };

    const handleDeleteType = async (id: number) => {
        if (!confirm("Delete this leave type?")) return;
        await deleteLeaveType(id);
        loadData();
    };

    const handleLoadBalances = async () => {
        if (!balanceEmployeeId) return;
        try {
            const balances = await getEmployeeLeaveBalances(Number(balanceEmployeeId), parseInt(balanceYear));
            setLeaveBalances(balances);
            if (balances.length === 0) {
                // Initialize
                const initialized = await initializeLeaveBalances(Number(balanceEmployeeId), parseInt(balanceYear));
                setLeaveBalances(initialized);
            }
        } catch (e) {
            console.error(e);
        }
    };

    const tabs = [
        { key: "requests" as const, label: "Leave Requests" },
        { key: "types" as const, label: "Leave Types" },
        { key: "balances" as const, label: "Balances" },
    ];

    if (isLoading) {
        return (
            <div className="p-6 h-screen overflow-hidden bg-background flex items-center justify-center">
                <div className="flex flex-col items-center">
                    <div className="w-12 h-12 border-4 border-primary/20 border-t-primary rounded-full animate-spin mb-4" />
                    <p className="animate-pulse text-muted-foreground">Loading leave management...</p>
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
                            Leave <span className="text-gradient">Management</span>
                        </h1>
                        <p className="text-muted-foreground mt-2">Manage employee leave requests, types, and balances</p>
                    </div>
                    {activeTab === "requests" && (
                        <button
                            onClick={() => { clearAllReqErrors(); setApiError(""); setIsRequestModalOpen(true); }}
                            className="btn-gradient px-6 py-3 rounded-xl font-medium flex items-center gap-2"
                        >
                            <Plus className="w-5 h-5" />
                            New Request
                        </button>
                    )}
                    {activeTab === "types" && (
                        <button
                            onClick={() => {
                                setEditingType(null);
                                resetTypeForm();
                                clearAllTypeErrors();
                                setApiError("");
                                setIsTypeModalOpen(true);
                            }}
                            className="btn-gradient px-6 py-3 rounded-xl font-medium flex items-center gap-2"
                        >
                            <Plus className="w-5 h-5" />
                            Add Type
                        </button>
                    )}
                </div>

                {/* Tabs */}
                <div className="flex gap-1 mb-6 bg-muted/50 p-1 rounded-xl w-fit">
                    {tabs.map((tab) => (
                        <button
                            key={tab.key}
                            onClick={() => setActiveTab(tab.key)}
                            className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
                                activeTab === tab.key
                                    ? "bg-primary text-primary-foreground"
                                    : "text-muted-foreground hover:text-foreground"
                            }`}
                        >
                            {tab.label}
                        </button>
                    ))}
                </div>

                {/* Requests Tab */}
                {activeTab === "requests" && (
                    <div className="flex-1 overflow-hidden flex flex-col">
                        {/* Filters */}
                        <div className="mb-4 flex gap-3 items-center">
                            <div className="relative flex-1 max-w-md">
                                <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                                <input
                                    type="text"
                                    placeholder="Search by employee name..."
                                    value={searchQuery}
                                    onChange={(e) => setSearchQuery(e.target.value)}
                                    className="w-full bg-background border border-border rounded-xl pl-10 pr-4 py-2.5 text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                                />
                            </div>
                            <select
                                value={statusFilter}
                                onChange={(e) => setStatusFilter(e.target.value)}
                                className="bg-background border border-border rounded-xl px-4 py-2.5 text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50 appearance-none"
                            >
                                <option value="ALL">All Status</option>
                                <option value="PENDING">Pending</option>
                                <option value="APPROVED">Approved</option>
                                <option value="REJECTED">Rejected</option>
                            </select>
                        </div>

                        {filteredRequests.length === 0 ? (
                            <div className="text-center py-20 bg-black/5 dark:bg-white/5 rounded-2xl border border-dashed border-border">
                                <CalendarDays className="w-16 h-16 mx-auto text-muted-foreground mb-4 opacity-50" />
                                <h3 className="text-xl font-semibold text-foreground mb-2">No Leave Requests</h3>
                                <p className="text-muted-foreground mb-6">Create the first leave request to get started.</p>
                            </div>
                        ) : (
                            <GlassCard className="overflow-hidden border-none p-0 flex-1">
                                <div className="overflow-x-auto">
                                    <table className="w-full text-left border-collapse">
                                        <thead>
                                            <tr className="bg-white/5 border-b border-border/50">
                                                <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center w-12">#</th>
                                                <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Employee</th>
                                                <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Leave Type</th>
                                                <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">From</th>
                                                <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">To</th>
                                                <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center">Days</th>
                                                <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center">Status</th>
                                                <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center w-32">Actions</th>
                                            </tr>
                                        </thead>
                                        <tbody className="divide-y divide-border/30">
                                            {paginatedData.map((r, idx) => (
                                                <tr key={r.id} className="hover:bg-white/5 transition-colors group">
                                                    <td className="px-6 py-4 text-xs font-mono text-muted-foreground text-center">{page * pageSize + idx + 1}</td>
                                                    <td className="px-6 py-4 font-medium text-foreground">{r.employee?.name}</td>
                                                    <td className="px-6 py-4 text-muted-foreground">{r.leaveType?.typeName}</td>
                                                    <td className="px-6 py-4 text-sm">{r.fromDate}</td>
                                                    <td className="px-6 py-4 text-sm">{r.toDate}</td>
                                                    <td className="px-6 py-4 text-center font-bold text-primary">{r.numberOfDays}</td>
                                                    <td className="px-6 py-4 text-center">
                                                        <Badge
                                                            variant={
                                                                r.status === "APPROVED" ? "success" :
                                                                r.status === "REJECTED" ? "danger" : "warning"
                                                            }
                                                        >
                                                            {r.status}
                                                        </Badge>
                                                    </td>
                                                    <td className="px-6 py-4">
                                                        {r.status === "PENDING" && (
                                                            <div className="flex justify-center gap-2">
                                                                <button
                                                                    onClick={() => handleApprove(r.id)}
                                                                    className="p-2 rounded-lg hover:bg-emerald-500/10 text-emerald-500"
                                                                    title="Approve"
                                                                >
                                                                    <Check className="w-4 h-4" />
                                                                </button>
                                                                <button
                                                                    onClick={() => handleReject(r.id)}
                                                                    className="p-2 rounded-lg hover:bg-red-500/10 text-red-500"
                                                                    title="Reject"
                                                                >
                                                                    <X className="w-4 h-4" />
                                                                </button>
                                                            </div>
                                                        )}
                                                    </td>
                                                </tr>
                                            ))}
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
                        )}
                    </div>
                )}

                {/* Types Tab */}
                {activeTab === "types" && (
                    <div className="flex-1">
                        {leaveTypes.length === 0 ? (
                            <div className="text-center py-20 bg-black/5 dark:bg-white/5 rounded-2xl border border-dashed border-border">
                                <CalendarDays className="w-16 h-16 mx-auto text-muted-foreground mb-4 opacity-50" />
                                <h3 className="text-xl font-semibold text-foreground mb-2">No Leave Types</h3>
                                <p className="text-muted-foreground mb-6">Create leave types like Casual Leave, Sick Leave, etc.</p>
                            </div>
                        ) : (
                            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                                {leaveTypes.map((lt) => (
                                    <GlassCard key={lt.id} className="p-5">
                                        <div className="flex justify-between items-start mb-3">
                                            <h3 className="text-lg font-semibold text-foreground">{lt.typeName}</h3>
                                            <div className="flex gap-1">
                                                <button onClick={() => handleEditType(lt)} className="p-1.5 rounded-lg hover:bg-white/10 text-muted-foreground">
                                                    <CalendarDays className="w-4 h-4" />
                                                </button>
                                                <button onClick={() => handleDeleteType(lt.id)} className="p-1.5 rounded-lg hover:bg-red-500/10 text-red-500">
                                                    <X className="w-4 h-4" />
                                                </button>
                                            </div>
                                        </div>
                                        <div className="space-y-2 text-sm">
                                            <div className="flex justify-between">
                                                <span className="text-muted-foreground">Max Days/Year</span>
                                                <span className="font-bold text-primary">{lt.maxDaysPerYear}</span>
                                            </div>
                                            <div className="flex justify-between">
                                                <span className="text-muted-foreground">Carry Forward</span>
                                                <Badge variant={lt.carryForward ? "success" : "outline"}>
                                                    {lt.carryForward ? "Yes" : "No"}
                                                </Badge>
                                            </div>
                                            {lt.carryForward && (
                                                <div className="flex justify-between">
                                                    <span className="text-muted-foreground">Max Carry</span>
                                                    <span className="font-medium">{lt.maxCarryForwardDays} days</span>
                                                </div>
                                            )}
                                        </div>
                                    </GlassCard>
                                ))}
                            </div>
                        )}
                    </div>
                )}

                {/* Balances Tab */}
                {activeTab === "balances" && (
                    <div className="flex-1">
                        <div className="flex gap-3 mb-6 items-end">
                            <div>
                                <label className="block text-sm font-medium text-foreground mb-1.5">Employee</label>
                                <select
                                    value={balanceEmployeeId}
                                    onChange={(e) => setBalanceEmployeeId(e.target.value)}
                                    className="bg-background border border-border rounded-xl px-4 py-2.5 text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50 appearance-none min-w-[200px]"
                                >
                                    <option value="">Select Employee</option>
                                    {employees.filter(e => e.status === "Active").map((emp) => (
                                        <option key={emp.id} value={emp.id}>{emp.name}</option>
                                    ))}
                                </select>
                            </div>
                            <div>
                                <label className="block text-sm font-medium text-foreground mb-1.5">Year</label>
                                <input
                                    type="number"
                                    value={balanceYear}
                                    onChange={(e) => setBalanceYear(e.target.value)}
                                    className="bg-background border border-border rounded-xl px-4 py-2.5 text-sm w-24 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                                />
                            </div>
                            <button
                                onClick={handleLoadBalances}
                                disabled={!balanceEmployeeId}
                                className="btn-gradient px-6 py-2.5 rounded-xl font-medium disabled:opacity-50"
                            >
                                Load Balances
                            </button>
                        </div>

                        {leaveBalances.length > 0 && (
                            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                                {leaveBalances.map((bal) => (
                                    <GlassCard key={bal.id} className="p-5">
                                        <h3 className="text-lg font-semibold text-foreground mb-3">{bal.leaveType?.typeName}</h3>
                                        <div className="space-y-2 text-sm">
                                            <div className="flex justify-between">
                                                <span className="text-muted-foreground">Allotted</span>
                                                <span className="font-bold text-primary">{bal.totalAllotted}</span>
                                            </div>
                                            <div className="flex justify-between">
                                                <span className="text-muted-foreground">Used</span>
                                                <span className="font-bold text-destructive">{bal.used}</span>
                                            </div>
                                            <div className="flex justify-between border-t border-border pt-2">
                                                <span className="text-muted-foreground">Remaining</span>
                                                <span className="font-bold text-emerald-500">{bal.remaining}</span>
                                            </div>
                                        </div>
                                        {/* Progress bar */}
                                        <div className="mt-3 h-2 bg-muted rounded-full overflow-hidden">
                                            <div
                                                className="h-full bg-primary rounded-full transition-all"
                                                style={{ width: `${bal.totalAllotted > 0 ? (bal.remaining / bal.totalAllotted) * 100 : 0}%` }}
                                            />
                                        </div>
                                    </GlassCard>
                                ))}
                            </div>
                        )}
                    </div>
                )}
            </div>

            {/* Request Modal */}
            <Modal isOpen={isRequestModalOpen} onClose={() => setIsRequestModalOpen(false)} title="New Leave Request">
                <form onSubmit={handleSubmitRequest} className="space-y-4">
                    <FormErrorBanner message={apiError} onDismiss={() => setApiError("")} />
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5">
                                Employee <span className="text-red-500">*</span>
                            </label>
                            <select
                                value={selectedEmployeeId}
                                onChange={(e) => { setSelectedEmployeeId(e.target.value); clearReqError("employeeId"); }}
                                className={`w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50 appearance-none ${inputErrorClass(reqErrors.employeeId)}`}
                            >
                                <option value="">Select Employee</option>
                                {employees.filter(e => e.status === "Active").map((emp) => (
                                    <option key={emp.id} value={emp.id}>{emp.name}</option>
                                ))}
                            </select>
                            <FieldError error={reqErrors.employeeId} />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5">
                                Leave Type <span className="text-red-500">*</span>
                            </label>
                            <select
                                value={selectedLeaveTypeId}
                                onChange={(e) => { setSelectedLeaveTypeId(e.target.value); clearReqError("leaveTypeId"); }}
                                className={`w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50 appearance-none ${inputErrorClass(reqErrors.leaveTypeId)}`}
                            >
                                <option value="">Select Type</option>
                                {leaveTypes.map((lt) => (
                                    <option key={lt.id} value={lt.id}>{lt.typeName}</option>
                                ))}
                            </select>
                            <FieldError error={reqErrors.leaveTypeId} />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5">
                                From Date <span className="text-red-500">*</span>
                            </label>
                            <input
                                type="date"
                                value={fromDate}
                                onChange={(e) => { setFromDate(e.target.value); clearReqError("fromDate"); }}
                                className={`w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50 ${inputErrorClass(reqErrors.fromDate)}`}
                            />
                            <FieldError error={reqErrors.fromDate} />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5">
                                To Date <span className="text-red-500">*</span>
                            </label>
                            <input
                                type="date"
                                value={toDate}
                                onChange={(e) => { setToDate(e.target.value); clearReqError("toDate"); }}
                                className={`w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50 ${inputErrorClass(reqErrors.toDate)}`}
                            />
                            <FieldError error={reqErrors.toDate} />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5">Days</label>
                            <input
                                type="number"
                                step="0.5"
                                value={numberOfDays}
                                onChange={(e) => setNumberOfDays(e.target.value)}
                                className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                            />
                        </div>
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-foreground mb-1.5">Reason</label>
                        <textarea
                            value={reason}
                            onChange={(e) => setReason(e.target.value)}
                            rows={3}
                            className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                            placeholder="Reason for leave..."
                        />
                    </div>
                    <div className="flex justify-end gap-3 pt-4 border-t border-border">
                        <button type="button" onClick={() => setIsRequestModalOpen(false)} className="px-6 py-2.5 rounded-xl font-medium text-foreground bg-black/5 dark:bg-white/5 hover:bg-black/10 dark:hover:bg-white/10 transition-colors">
                            Cancel
                        </button>
                        <button type="submit" className="btn-gradient px-8 py-2.5 rounded-xl font-medium">
                            Submit Request
                        </button>
                    </div>
                </form>
            </Modal>

            {/* Type Modal */}
            <Modal isOpen={isTypeModalOpen} onClose={() => setIsTypeModalOpen(false)} title={editingType ? "Edit Leave Type" : "Add Leave Type"}>
                <form onSubmit={handleSaveType} className="space-y-4">
                    <FormErrorBanner message={apiError} onDismiss={() => setApiError("")} />
                    <div>
                        <label className="block text-sm font-medium text-foreground mb-1.5">
                            Type Name <span className="text-red-500">*</span>
                        </label>
                        <input
                            type="text"
                            value={typeName}
                            onChange={(e) => { setTypeName(e.target.value); clearTypeError("typeName"); }}
                            className={`w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50 ${inputErrorClass(typeErrors.typeName)}`}
                            placeholder="e.g., Casual Leave"
                        />
                        <FieldError error={typeErrors.typeName} />
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-foreground mb-1.5">
                            Max Days Per Year <span className="text-red-500">*</span>
                        </label>
                        <input
                            type="number"
                            value={maxDaysPerYear}
                            onChange={(e) => { setMaxDaysPerYear(e.target.value); clearTypeError("maxDaysPerYear"); }}
                            className={`w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50 ${inputErrorClass(typeErrors.maxDaysPerYear)}`}
                        />
                        <FieldError error={typeErrors.maxDaysPerYear} />
                    </div>
                    <div className="flex items-center justify-between p-4 bg-black/5 dark:bg-white/5 rounded-xl border border-border">
                        <div>
                            <p className="font-medium text-foreground text-sm">Carry Forward</p>
                            <p className="text-xs text-muted-foreground">Can unused days be carried to next year?</p>
                        </div>
                        <label className="relative inline-flex items-center cursor-pointer">
                            <input type="checkbox" className="sr-only peer" checked={carryForward} onChange={(e) => setCarryForward(e.target.checked)} />
                            <div className="w-11 h-6 bg-gray-200 peer-focus:ring-4 peer-focus:ring-primary/20 rounded-full peer dark:bg-gray-700 peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-primary"></div>
                        </label>
                    </div>
                    {carryForward && (
                        <div>
                            <label className="block text-sm font-medium text-foreground mb-1.5">Max Carry Forward Days</label>
                            <input
                                type="number"
                                value={maxCarryForwardDays}
                                onChange={(e) => setMaxCarryForwardDays(e.target.value)}
                                className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                            />
                        </div>
                    )}
                    <div className="flex justify-end gap-3 pt-4 border-t border-border">
                        <button type="button" onClick={() => setIsTypeModalOpen(false)} className="px-6 py-2.5 rounded-xl font-medium text-foreground bg-black/5 dark:bg-white/5 hover:bg-black/10 dark:hover:bg-white/10 transition-colors">
                            Cancel
                        </button>
                        <button type="submit" className="btn-gradient px-8 py-2.5 rounded-xl font-medium">
                            {editingType ? "Update" : "Create"}
                        </button>
                    </div>
                </form>
            </Modal>
        </div>
    );
}
