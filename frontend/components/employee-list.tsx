"use client";

import { useState, useEffect } from "react";
import { Plus, Pencil, Trash2, Users, Search } from "lucide-react";

interface Employee {
    id: number;
    name: string;
    designation: string;
    email: string;
    phone: string;
    salary: number;
    joinDate: string;
    status: string;
}

const emptyEmployee: Omit<Employee, "id"> = {
    name: "",
    designation: "",
    email: "",
    phone: "",
    salary: 0,
    joinDate: "",
    status: "Active",
};

export function EmployeeList() {
    const [employees, setEmployees] = useState<Employee[]>([]);
    const [isLoading, setIsLoading] = useState(false);
    const [showForm, setShowForm] = useState(false);
    const [currentEmployee, setCurrentEmployee] = useState<any>(emptyEmployee);
    const [isEditing, setIsEditing] = useState(false);
    const [searchQuery, setSearchQuery] = useState("");
    const [statusFilter, setStatusFilter] = useState<string>("ALL");

    useEffect(() => {
        fetchEmployees();
    }, []);

    const fetchEmployees = async () => {
        try {
            const response = await fetch("http://localhost:8080/api/employees");
            if (response.ok) {
                const data = await response.json();
                setEmployees(data);
            }
        } catch (error) {
            console.error("Failed to fetch employees", error);
        }
    };

    const handleDelete = async (id: number) => {
        if (!confirm("Are you sure you want to delete this employee?")) return;
        try {
            await fetch(`http://localhost:8080/api/employees/${id}`, { method: "DELETE" });
            fetchEmployees();
        } catch (error) {
            console.error("Failed to delete employee", error);
        }
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setIsLoading(true);
        try {
            const url = isEditing
                ? `http://localhost:8080/api/employees/${currentEmployee.id}`
                : "http://localhost:8080/api/employees";
            const method = isEditing ? "PUT" : "POST";

            await fetch(url, {
                method,
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(currentEmployee),
            });

            setShowForm(false);
            fetchEmployees();
            setCurrentEmployee(emptyEmployee);
            setIsEditing(false);
        } catch (error) {
            console.error("Failed to save employee", error);
        } finally {
            setIsLoading(false);
        }
    };

    return (
        <div className="space-y-6">
            <div className="flex items-center justify-between">
                <div>
                    <h2 className="text-2xl font-bold tracking-tight">Employees</h2>
                    <p className="text-muted-foreground">Manage your staff and their details.</p>
                </div>
                <button
                    onClick={() => {
                        setCurrentEmployee(emptyEmployee);
                        setIsEditing(false);
                        setShowForm(true);
                    }}
                    className="inline-flex items-center justify-center rounded-md text-sm font-medium ring-offset-background transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 bg-primary text-primary-foreground hover:bg-primary/90 h-10 px-4 py-2"
                >
                    <Plus className="mr-2 h-4 w-4" />
                    Add Employee
                </button>
            </div>

            {showForm && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
                    <div className="bg-background p-6 rounded-lg w-full max-w-lg space-y-4">
                        <h3 className="text-lg font-semibold">{isEditing ? "Edit Employee" : "Add New Employee"}</h3>
                        <form onSubmit={handleSubmit} className="space-y-4">
                            <div className="grid grid-cols-2 gap-4">
                                <div className="space-y-2">
                                    <label className="text-sm font-medium">Name</label>
                                    <input
                                        required
                                        className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                                        value={currentEmployee.name}
                                        onChange={(e) => setCurrentEmployee({ ...currentEmployee, name: e.target.value })}
                                    />
                                </div>
                                <div className="space-y-2">
                                    <label className="text-sm font-medium">Designation</label>
                                    <input
                                        required
                                        className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                                        value={currentEmployee.designation}
                                        onChange={(e) => setCurrentEmployee({ ...currentEmployee, designation: e.target.value })}
                                    />
                                </div>
                                <div className="space-y-2">
                                    <label className="text-sm font-medium">Email</label>
                                    <input
                                        type="email"
                                        required
                                        className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                                        value={currentEmployee.email}
                                        onChange={(e) => setCurrentEmployee({ ...currentEmployee, email: e.target.value })}
                                    />
                                </div>
                                <div className="space-y-2">
                                    <label className="text-sm font-medium">Phone</label>
                                    <input
                                        required
                                        className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                                        value={currentEmployee.phone}
                                        onChange={(e) => setCurrentEmployee({ ...currentEmployee, phone: e.target.value })}
                                    />
                                </div>
                                <div className="space-y-2">
                                    <label className="text-sm font-medium">Salary</label>
                                    <input
                                        type="number"
                                        required
                                        className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                                        value={currentEmployee.salary}
                                        onChange={(e) => setCurrentEmployee({ ...currentEmployee, salary: Number(e.target.value) })}
                                    />
                                </div>
                                <div className="space-y-2">
                                    <label className="text-sm font-medium">Join Date</label>
                                    <input
                                        type="date"
                                        required
                                        className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                                        value={currentEmployee.joinDate}
                                        onChange={(e) => setCurrentEmployee({ ...currentEmployee, joinDate: e.target.value })}
                                    />
                                </div>
                            </div>
                            <div className="flex justify-end gap-2">
                                <button
                                    type="button"
                                    onClick={() => setShowForm(false)}
                                    className="px-4 py-2 text-sm font-medium border rounded-md hover:bg-muted"
                                >
                                    Cancel
                                </button>
                                <button
                                    type="submit"
                                    disabled={isLoading}
                                    className="px-4 py-2 text-sm font-medium bg-primary text-primary-foreground rounded-md hover:bg-primary/90"
                                >
                                    {isLoading ? "Saving..." : "Save"}
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}

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
                            <tr>
                                <td colSpan={6} className="p-4 text-center text-muted-foreground">
                                    No employees found. Add one to get started.
                                </td>
                            </tr>
                        ) : (
                            employees.filter((emp) => {
                                const q = searchQuery.toLowerCase();
                                const matchesSearch = !searchQuery || emp.name?.toLowerCase().includes(q) || emp.designation?.toLowerCase().includes(q) || emp.email?.toLowerCase().includes(q) || emp.phone?.includes(q);
                                const matchesStatus = statusFilter === "ALL" || emp.status === statusFilter;
                                return matchesSearch && matchesStatus;
                            }).map((emp) => (
                                <tr key={emp.id} className="border-t hover:bg-muted/50">
                                    <td className="p-4 font-medium">{emp.name}</td>
                                    <td className="p-4">{emp.designation}</td>
                                    <td className="p-4">
                                        <div className="flex flex-col">
                                            <span>{emp.email}</span>
                                            <span className="text-xs text-muted-foreground">{emp.phone}</span>
                                        </div>
                                    </td>
                                    <td className="p-4">${emp.salary.toLocaleString()}</td>
                                    <td className="p-4">
                                        <span className="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium bg-green-100 text-green-800">
                                            {emp.status}
                                        </span>
                                    </td>
                                    <td className="p-4 text-right">
                                        <div className="flex justify-end gap-2">
                                            <button
                                                onClick={() => {
                                                    setCurrentEmployee(emp);
                                                    setIsEditing(true);
                                                    setShowForm(true);
                                                }}
                                                className="p-2 hover:bg-muted rounded-md"
                                            >
                                                <Pencil className="w-4 h-4" />
                                            </button>
                                            <button
                                                onClick={() => handleDelete(emp.id)}
                                                className="p-2 hover:bg-red-100 text-red-600 rounded-md"
                                            >
                                                <Trash2 className="w-4 h-4" />
                                            </button>
                                        </div>
                                    </td>
                                </tr>
                            ))
                        )}
                    </tbody>
                </table>
            </div>
        </div>
    );
}
