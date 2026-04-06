"use client";

import { useState, useEffect, useCallback } from "react";
import { Plus, Pencil, Trash2, Search, Eye, ChevronLeft, ChevronRight } from "lucide-react";
import { EmployeeAvatar } from "@/components/ui/employee-avatar";
import { EmployeeFormModal } from "@/components/employees/employee-form-modal";
import { EmployeeProfileModal } from "@/components/employees/employee-profile-modal";
import type { Employee } from "@/components/employees/types";
import { emptyEmployee, formatRupees, API_BASE } from "@/components/employees/types";
import { PermissionGate } from "@/components/permission-gate";
import { fetchWithAuth } from "@/lib/api/fetch-with-auth";

export function EmployeeList() {
    const [employees, setEmployees] = useState<Employee[]>([]);
    const [searchQuery, setSearchQuery] = useState("");
    const [statusFilter, setStatusFilter] = useState<string>("");
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);

    // Modal state
    const [formEmployee, setFormEmployee] = useState<any | null>(null);
    const [formIsEditing, setFormIsEditing] = useState(false);
    const [profileEmployee, setProfileEmployee] = useState<Employee | null>(null);

    const fetchEmployees = useCallback(async () => {
        try {
            const params = new URLSearchParams({
                page: page.toString(),
                size: "10",
            });
            if (searchQuery) params.set("search", searchQuery);
            if (statusFilter) params.set("status", statusFilter);
            const res = await fetchWithAuth(`${API_BASE}?${params}`);
            if (res.ok) {
                const data = await res.json();
                if (data.content && Array.isArray(data.content)) {
                    setEmployees(data.content);
                    setTotalPages(data.totalPages);
                    setTotalElements(data.totalElements);
                } else if (Array.isArray(data)) {
                    setEmployees(data);
                    setTotalPages(1);
                    setTotalElements(data.length);
                }
            }
        } catch (error) { console.error("Failed to fetch employees", error); }
    }, [page, searchQuery, statusFilter]);

    useEffect(() => { fetchEmployees(); }, [fetchEmployees]);

    // Debounce search
    const [searchInput, setSearchInput] = useState("");
    useEffect(() => {
        const timer = setTimeout(() => {
            setSearchQuery(searchInput);
            setPage(0);
        }, 300);
        return () => clearTimeout(timer);
    }, [searchInput]);

    const handleDelete = async (id: number) => {
        if (!confirm("Are you sure you want to delete this employee?")) return;
        try {
            await fetchWithAuth(`${API_BASE}/${id}`, { method: "DELETE" });
            fetchEmployees();
        } catch (error) { console.error("Failed to delete employee", error); }
    };

    const openAdd = () => { setFormEmployee({ ...emptyEmployee }); setFormIsEditing(false); };
    const openEdit = async (emp: Employee) => {
        try {
            const res = await fetchWithAuth(`${API_BASE}/${emp.id}`);
            if (res.ok) {
                const full = await res.json();
                // Ensure no null values for form inputs
                const merged = { ...emptyEmployee, ...Object.fromEntries(
                    Object.entries(full).map(([k, v]) => [k, v ?? (emptyEmployee as any)[k] ?? ""])
                )};
                setFormEmployee(merged);
                setFormIsEditing(true);
            }
        } catch (error) {
            console.error("Failed to fetch employee details", error);
        }
    };

    return (
        <div className="space-y-6">
            {/* Header */}
            <div className="flex items-center justify-between">
                <div>
                    <h2 className="text-2xl font-bold tracking-tight">Employees</h2>
                    <p className="text-muted-foreground">Manage your staff and their details.</p>
                </div>
                <PermissionGate permission="EMPLOYEE_CREATE">
                    <button
                        onClick={openAdd}
                        className="inline-flex items-center justify-center rounded-md text-sm font-medium ring-offset-background transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 bg-primary text-primary-foreground hover:bg-primary/90 h-10 px-4 py-2"
                    >
                        <Plus className="mr-2 h-4 w-4" /> Add Employee
                    </button>
                </PermissionGate>
            </div>

            {/* Filter Bar */}
            <div className="flex flex-wrap gap-3 items-center">
                <div className="relative flex-1 min-w-[200px] max-w-md">
                    <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                    <input
                        type="text"
                        placeholder="Search by name or employee code..."
                        value={searchInput}
                        onChange={(e) => setSearchInput(e.target.value)}
                        className="w-full pl-10 pr-4 py-2 bg-background border border-border rounded-lg text-foreground text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                    />
                </div>
                <select
                    value={statusFilter}
                    onChange={(e) => { setStatusFilter(e.target.value); setPage(0); }}
                    className="px-4 py-2 bg-background border border-border rounded-lg text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
                >
                    <option value="">All Status</option>
                    <option value="ACTIVE">Active</option>
                    <option value="INACTIVE">Inactive</option>
                </select>
            </div>

            {/* Employee Table */}
            <div className="rounded-md border">
                <table className="w-full text-sm text-left">
                    <thead className="bg-muted/50 text-muted-foreground">
                        <tr>
                            <th className="p-4 font-medium">Name</th>
                            <th className="p-4 font-medium">Designation</th>
                            <th className="p-4 font-medium">Contact</th>
                            <th className="p-4 font-medium">Salary</th>
                            <th className="p-4 font-medium">Status</th>
                            <th className="p-4 font-medium text-right">Actions</th>
                        </tr>
                    </thead>
                    <tbody>
                        {employees.length === 0 ? (
                            <tr><td colSpan={6} className="p-4 text-center text-muted-foreground">No employees found. Add one to get started.</td></tr>
                        ) : employees.map((emp) => (
                            <tr key={emp.id} className="border-t hover:bg-muted/50">
                                <td className="p-4 font-medium">
                                    <div className="flex items-center gap-3">
                                        <EmployeeAvatar employeeId={emp.id} name={emp.name} photoUrl={emp.photoUrl} size="sm" />
                                        <div>
                                            {emp.name}
                                            {emp.employeeCode && <span className="text-xs text-muted-foreground ml-2">({emp.employeeCode})</span>}
                                        </div>
                                    </div>
                                </td>
                                <td className="p-4">{emp.designation}</td>
                                <td className="p-4">
                                    <div className="flex flex-col">
                                        <span>{emp.email}</span>
                                        <span className="text-xs text-muted-foreground">{emp.phone}</span>
                                    </div>
                                </td>
                                <td className="p-4">{formatRupees(emp.salary)}</td>
                                <td className="p-4">
                                    <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${emp.status?.toUpperCase() === "ACTIVE" ? "bg-green-100 text-green-800 dark:bg-green-900/40 dark:text-green-300" : "bg-gray-100 text-gray-800 dark:bg-gray-900/40 dark:text-gray-300"}`}>
                                        {emp.status}
                                    </span>
                                </td>
                                <td className="p-4 text-right">
                                    <div className="flex justify-end gap-2">
                                        <button onClick={() => setProfileEmployee(emp)} className="p-2 hover:bg-muted rounded-md" title="View Profile"><Eye className="w-4 h-4" /></button>
                                        <PermissionGate permission="EMPLOYEE_UPDATE">
                                            <button onClick={() => openEdit(emp)} className="p-2 hover:bg-muted rounded-md" title="Edit"><Pencil className="w-4 h-4" /></button>
                                            <button onClick={() => handleDelete(emp.id)} className="p-2 hover:bg-red-100 text-red-600 rounded-md" title="Delete"><Trash2 className="w-4 h-4" /></button>
                                        </PermissionGate>
                                    </div>
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>

            {/* Pagination */}
            <div className="flex items-center justify-between">
                <div className="text-sm text-muted-foreground">
                    {totalElements > 0 ? `Showing ${page * 10 + 1}-${Math.min((page + 1) * 10, totalElements)} of ${totalElements}` : "No results"}
                </div>
                <div className="flex items-center gap-2">
                    <button
                        onClick={() => setPage(p => Math.max(0, p - 1))}
                        disabled={page === 0}
                        className="p-2 border border-border rounded-lg hover:bg-muted disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                        <ChevronLeft className="w-4 h-4" />
                    </button>
                    <span className="text-sm text-muted-foreground px-2">
                        Page {page + 1} of {totalPages || 1}
                    </span>
                    <button
                        onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}
                        disabled={page >= totalPages - 1}
                        className="p-2 border border-border rounded-lg hover:bg-muted disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                        <ChevronRight className="w-4 h-4" />
                    </button>
                </div>
            </div>

            {/* Form Modal */}
            {formEmployee && (
                <EmployeeFormModal
                    employee={formEmployee}
                    isEditing={formIsEditing}
                    onClose={() => setFormEmployee(null)}
                    onSaved={() => { setFormEmployee(null); fetchEmployees(); }}
                />
            )}

            {/* Profile Modal */}
            {profileEmployee && (
                <EmployeeProfileModal
                    employee={profileEmployee}
                    onClose={() => setProfileEmployee(null)}
                />
            )}
        </div>
    );
}
