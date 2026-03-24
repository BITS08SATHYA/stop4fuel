"use client";

import { useState, useEffect } from "react";
import {
    CalendarCheck,
    Search,
    Check,
    X,
    Clock,
    UserCheck,
    UserX,
    AlertCircle,
} from "lucide-react";
import { GlassCard } from "@/components/ui/glass-card";
import { FormErrorBanner, FieldError } from "@/components/ui/field-error";
import { Badge } from "@/components/ui/badge";
import {
    getEmployees,
    getDailyAttendance,
    markAttendance,
    EmployeeType,
    Attendance,
} from "@/lib/api/station";

const statusOptions = [
    { value: "PRESENT", label: "Present", color: "text-emerald-500 bg-emerald-500/10" },
    { value: "ABSENT", label: "Absent", color: "text-red-500 bg-red-500/10" },
    { value: "HALF_DAY", label: "Half Day", color: "text-amber-500 bg-amber-500/10" },
    { value: "ON_LEAVE", label: "On Leave", color: "text-blue-500 bg-blue-500/10" },
];

export default function AttendancePage() {
    const [employees, setEmployees] = useState<EmployeeType[]>([]);
    const [attendance, setAttendance] = useState<Attendance[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [selectedDate, setSelectedDate] = useState(new Date().toISOString().split("T")[0]);
    const [searchQuery, setSearchQuery] = useState("");
    const [saving, setSaving] = useState<number | null>(null);
    const [apiError, setApiError] = useState("");
    const [dateError, setDateError] = useState("");
    const [timeErrors, setTimeErrors] = useState<Record<number, string>>({});

    useEffect(() => {
        loadData();
    }, [selectedDate]);

    const loadData = async () => {
        setIsLoading(true);
        try {
            const [emps, att] = await Promise.all([
                getEmployees(),
                getDailyAttendance(selectedDate),
            ]);
            setEmployees(emps.filter(e => e.status?.toUpperCase() === "ACTIVE"));
            setAttendance(att);
        } catch (e) {
            console.error(e);
        }
        setIsLoading(false);
    };

    const getAttendanceForEmployee = (empId: number): Attendance | undefined => {
        return attendance.find((a) => a.employee?.id === empId);
    };

    const handleDateChange = (newDate: string) => {
        const selected = new Date(newDate);
        const today = new Date();
        today.setHours(23, 59, 59, 999);
        if (selected > today) {
            setDateError("Date cannot be in the future");
            return;
        }
        setDateError("");
        setSelectedDate(newDate);
    };

    const handleMarkAttendance = async (empId: number, status: string, checkIn?: string, checkOut?: string) => {
        // Validate check-out is after check-in
        if (checkIn && checkOut && checkOut <= checkIn) {
            setTimeErrors((prev) => ({ ...prev, [empId]: "Check-out must be after check-in" }));
            return;
        }
        setTimeErrors((prev) => {
            const next = { ...prev };
            delete next[empId];
            return next;
        });
        setSaving(empId);
        try {
            await markAttendance(empId, {
                date: selectedDate,
                status,
                checkInTime: checkIn || undefined,
                checkOutTime: checkOut || undefined,
                source: "MANUAL",
            });
            await loadData();
        } catch (e) {
            setApiError("Failed to mark attendance");
        }
        setSaving(null);
    };

    const filteredEmployees = employees.filter((emp) =>
        !searchQuery || emp.name?.toLowerCase().includes(searchQuery.toLowerCase())
    );

    // Stats
    const presentCount = attendance.filter((a) => a.status === "PRESENT").length;
    const absentCount = attendance.filter((a) => a.status === "ABSENT").length;
    const halfDayCount = attendance.filter((a) => a.status === "HALF_DAY").length;
    const onLeaveCount = attendance.filter((a) => a.status === "ON_LEAVE").length;
    const unmarkedCount = employees.length - attendance.length;

    if (isLoading) {
        return (
            <div className="p-6 h-screen overflow-hidden bg-background flex items-center justify-center">
                <div className="flex flex-col items-center">
                    <div className="w-12 h-12 border-4 border-primary/20 border-t-primary rounded-full animate-spin mb-4" />
                    <p className="animate-pulse text-muted-foreground">Loading attendance...</p>
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
                            Daily <span className="text-gradient">Attendance</span>
                        </h1>
                        <p className="text-muted-foreground mt-2">Mark and track employee attendance</p>
                    </div>
                    <div>
                        <input
                            type="date"
                            value={selectedDate}
                            max={new Date().toISOString().split("T")[0]}
                            onChange={(e) => handleDateChange(e.target.value)}
                            className={`bg-background border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50 ${dateError ? "border-red-500 focus:ring-red-500/50" : "border-border"}`}
                        />
                        <FieldError error={dateError} />
                    </div>
                </div>

                {/* Stats Cards */}
                <div className="grid grid-cols-2 md:grid-cols-5 gap-3 mb-6">
                    <GlassCard className="p-4 text-center">
                        <UserCheck className="w-5 h-5 mx-auto text-emerald-500 mb-1" />
                        <p className="text-2xl font-bold text-emerald-500">{presentCount}</p>
                        <p className="text-xs text-muted-foreground">Present</p>
                    </GlassCard>
                    <GlassCard className="p-4 text-center">
                        <UserX className="w-5 h-5 mx-auto text-red-500 mb-1" />
                        <p className="text-2xl font-bold text-red-500">{absentCount}</p>
                        <p className="text-xs text-muted-foreground">Absent</p>
                    </GlassCard>
                    <GlassCard className="p-4 text-center">
                        <Clock className="w-5 h-5 mx-auto text-amber-500 mb-1" />
                        <p className="text-2xl font-bold text-amber-500">{halfDayCount}</p>
                        <p className="text-xs text-muted-foreground">Half Day</p>
                    </GlassCard>
                    <GlassCard className="p-4 text-center">
                        <CalendarCheck className="w-5 h-5 mx-auto text-blue-500 mb-1" />
                        <p className="text-2xl font-bold text-blue-500">{onLeaveCount}</p>
                        <p className="text-xs text-muted-foreground">On Leave</p>
                    </GlassCard>
                    <GlassCard className="p-4 text-center">
                        <AlertCircle className="w-5 h-5 mx-auto text-muted-foreground mb-1" />
                        <p className="text-2xl font-bold text-muted-foreground">{unmarkedCount}</p>
                        <p className="text-xs text-muted-foreground">Unmarked</p>
                    </GlassCard>
                </div>

                <FormErrorBanner message={apiError} onDismiss={() => setApiError("")} />

                {/* Search */}
                <div className="mb-4">
                    <div className="relative max-w-md">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                        <input
                            type="text"
                            placeholder="Search employees..."
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            className="w-full bg-background border border-border rounded-xl pl-10 pr-4 py-2.5 text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                        />
                    </div>
                </div>

                {/* Attendance Grid */}
                <div className="flex-1 overflow-y-auto">
                    <GlassCard className="overflow-hidden border-none p-0">
                        <div className="overflow-x-auto">
                            <table className="w-full text-left border-collapse">
                                <thead>
                                    <tr className="bg-white/5 border-b border-border/50">
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center w-12">#</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Employee</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Designation</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center">Check In</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center">Check Out</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center">Hours</th>
                                        <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-muted-foreground text-center">Status</th>
                                    </tr>
                                </thead>
                                <tbody className="divide-y divide-border/30">
                                    {filteredEmployees.map((emp, idx) => {
                                        const att = getAttendanceForEmployee(emp.id);
                                        return (
                                            <tr key={emp.id} className="hover:bg-white/5 transition-colors">
                                                <td className="px-6 py-4 text-xs font-mono text-muted-foreground text-center">{idx + 1}</td>
                                                <td className="px-6 py-4 font-medium text-foreground">{emp.name}</td>
                                                <td className="px-6 py-4 text-sm text-muted-foreground">{emp.designation}</td>
                                                <td className="px-4 py-3 text-center">
                                                    <input
                                                        type="time"
                                                        defaultValue={att?.checkInTime || ""}
                                                        onBlur={(e) => {
                                                            if (e.target.value) {
                                                                handleMarkAttendance(emp.id, att?.status || "PRESENT", e.target.value, att?.checkOutTime || undefined);
                                                            }
                                                        }}
                                                        className="bg-background border border-border rounded-lg px-2 py-1.5 text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50 w-28"
                                                    />
                                                </td>
                                                <td className="px-4 py-3 text-center">
                                                    <input
                                                        type="time"
                                                        defaultValue={att?.checkOutTime || ""}
                                                        onBlur={(e) => {
                                                            if (e.target.value) {
                                                                handleMarkAttendance(emp.id, att?.status || "PRESENT", att?.checkInTime || undefined, e.target.value);
                                                            }
                                                        }}
                                                        className={`bg-background border rounded-lg px-2 py-1.5 text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50 w-28 ${timeErrors[emp.id] ? "border-red-500 focus:ring-red-500/50" : "border-border"}`}
                                                    />
                                                    {timeErrors[emp.id] && (
                                                        <p className="text-[10px] text-red-500 mt-0.5">{timeErrors[emp.id]}</p>
                                                    )}
                                                </td>
                                                <td className="px-6 py-4 text-center text-sm font-medium text-primary">
                                                    {att?.totalHoursWorked ? att.totalHoursWorked.toFixed(1) + "h" : "-"}
                                                </td>
                                                <td className="px-4 py-3 text-center">
                                                    <div className="flex justify-center gap-1">
                                                        {statusOptions.map((opt) => (
                                                            <button
                                                                key={opt.value}
                                                                onClick={() => handleMarkAttendance(emp.id, opt.value, att?.checkInTime || undefined, att?.checkOutTime || undefined)}
                                                                disabled={saving === emp.id}
                                                                className={`px-2 py-1 rounded-md text-[10px] font-bold uppercase transition-all ${
                                                                    att?.status === opt.value
                                                                        ? opt.color + " ring-2 ring-offset-1 ring-current"
                                                                        : "bg-muted/50 text-muted-foreground hover:bg-muted"
                                                                }`}
                                                                title={opt.label}
                                                            >
                                                                {opt.label.charAt(0)}
                                                            </button>
                                                        ))}
                                                    </div>
                                                </td>
                                            </tr>
                                        );
                                    })}
                                </tbody>
                            </table>
                        </div>
                    </GlassCard>
                </div>
            </div>
        </div>
    );
}
