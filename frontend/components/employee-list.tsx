"use client";

import { useState, useEffect } from "react";
import { Plus, Pencil, Trash2, Search, Eye } from "lucide-react";
import { EmployeeAvatar } from "@/components/ui/employee-avatar";
import { EmployeeFormModal } from "@/components/employees/employee-form-modal";
import { EmployeeProfileModal } from "@/components/employees/employee-profile-modal";
import type { Employee } from "@/components/employees/types";
import { emptyEmployee, formatRupees, API_BASE } from "@/components/employees/types";

export function EmployeeList() {
    const [employees, setEmployees] = useState<Employee[]>([]);
    const [searchQuery, setSearchQuery] = useState("");
    const [statusFilter, setStatusFilter] = useState<string>("ALL");

    // Modal state
    const [formEmployee, setFormEmployee] = useState<any | null>(null);
    const [formIsEditing, setFormIsEditing] = useState(false);
    const [profileEmployee, setProfileEmployee] = useState<Employee | null>(null);

    useEffect(() => { fetchEmployees(); }, []);

    const fetchEmployees = async () => {
        try {
            const res = await fetch(API_BASE);
            if (res.ok) setEmployees(await res.json());
        } catch (error) { console.error("Failed to fetch employees", error); }
    };

    const handleDelete = async (id: number) => {
        if (!confirm("Are you sure you want to delete this employee?")) return;
        try {
            await fetch(`${API_BASE}/${id}`, { method: "DELETE" });
            fetchEmployees();
        } catch (error) { console.error("Failed to delete employee", error); }
    };

    const openAdd = () => { setFormEmployee({ ...emptyEmployee }); setFormIsEditing(false); };
    const openEdit = (emp: Employee) => { setFormEmployee(emp); setFormIsEditing(true); };

    const filteredEmployees = employees.filter((emp) => {
        const q = searchQuery.toLowerCase();
        const matchesSearch = !searchQuery
            || emp.name?.toLowerCase().includes(q)
            || emp.designation?.toLowerCase().includes(q)
            || emp.email?.toLowerCase().includes(q)
            || emp.phone?.includes(q);
        const matchesStatus = statusFilter === "ALL" || emp.status === statusFilter;
        return matchesSearch && matchesStatus;
    });

    return (
        <div className="space-y-6">
            {/* Header */}
            <div className="flex items-center justify-between">
                <div>
                    <h2 className="text-2xl font-bold tracking-tight">Employees</h2>
                    <p className="text-muted-foreground">Manage your staff and their details.</p>
                </div>
                <button
                    onClick={openAdd}
                    className="inline-flex items-center justify-center rounded-md text-sm font-medium ring-offset-background transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 bg-primary text-primary-foreground hover:bg-primary/90 h-10 px-4 py-2"
                >
                    <Plus className="mr-2 h-4 w-4" /> Add Employee
                </button>
            </div>

            {/* Filter Bar */}
            <div className="flex flex-wrap gap-3 items-center">
                <div className="relative flex-1 min-w-[200px] max-w-md">
                    <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                    <input
                        type="text"
                        placeholder="Search by name, designation, email, phone..."
                        value={searchQuery}
                        onChange={(e) => setSearchQuery(e.target.value)}
                        className="w-full pl-10 pr-4 py-2 bg-background border border-border rounded-lg text-foreground text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                    />
                </div>
                <select
                    value={statusFilter}
                    onChange={(e) => setStatusFilter(e.target.value)}
                    className="px-4 py-2 bg-background border border-border rounded-lg text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
                >
                    <option value="ALL">All Status</option>
                    <option value="Active">Active</option>
                    <option value="Inactive">Inactive</option>
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
                        {filteredEmployees.length === 0 ? (
                            <tr><td colSpan={6} className="p-4 text-center text-muted-foreground">No employees found. Add one to get started.</td></tr>
                        ) : filteredEmployees.map((emp) => (
                            <tr key={emp.id} className="border-t hover:bg-muted/50">
                                <td className="p-4 font-medium">
                                    <div className="flex items-center gap-3">
                                        <EmployeeAvatar employeeId={emp.id} name={emp.name} photoUrl={emp.photoUrl} size="sm" />
                                        {emp.name}
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
                                    <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${emp.status === "Active" ? "bg-green-100 text-green-800 dark:bg-green-900/40 dark:text-green-300" : "bg-gray-100 text-gray-800 dark:bg-gray-900/40 dark:text-gray-300"}`}>
                                        {emp.status}
                                    </span>
                                </td>
                                <td className="p-4 text-right">
                                    <div className="flex justify-end gap-2">
                                        <button onClick={() => setProfileEmployee(emp)} className="p-2 hover:bg-muted rounded-md" title="View Profile"><Eye className="w-4 h-4" /></button>
                                        <button onClick={() => openEdit(emp)} className="p-2 hover:bg-muted rounded-md" title="Edit"><Pencil className="w-4 h-4" /></button>
                                        <button onClick={() => handleDelete(emp.id)} className="p-2 hover:bg-red-100 text-red-600 rounded-md" title="Delete"><Trash2 className="w-4 h-4" /></button>
                                    </div>
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
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
