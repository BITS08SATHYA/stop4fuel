"use client";

import { useState, useEffect } from "react";
import {
    Plus,
    Pencil,
    Trash2,
    Search,
    Eye,
    X,
    IndianRupee,
    User,
    MapPin,
    Landmark,
    History,
    Banknote,
    ChevronRight,
    FileText,
    Upload,
    Camera,
    Download,
    ExternalLink,
    Loader2,
} from "lucide-react";
import { Modal } from "@/components/ui/modal";
import { GlassCard } from "@/components/ui/glass-card";
import { useFormValidation, required, email, phone, min } from "@/lib/validation";
import { FieldError, inputErrorClass, FormErrorBanner } from "@/components/ui/field-error";
import { EmployeeAvatar } from "@/components/ui/employee-avatar";
import {
    uploadEmployeePhoto,
    uploadEmployeeAadharDoc,
    uploadEmployeePanDoc,
    getEmployeeFileUrl,
} from "@/lib/api/station";

// ── Types ──────────────────────────────────────────────────────────────

interface Employee {
    id: number;
    name: string;
    designation: string;
    email: string;
    phone: string;
    salary: number;
    joinDate: string;
    status: string;
    aadharNumber: string;
    additionalPhones: string;
    address: string;
    city: string;
    state: string;
    pincode: string;
    photoUrl: string;
    bankAccountNumber: string;
    bankName: string;
    bankIfsc: string;
    bankBranch: string;
    panNumber: string;
    department: string;
    employeeCode: string;
    emergencyContact: string;
    emergencyPhone: string;
    bloodGroup: string;
    dateOfBirth: string;
    gender: string;
    maritalStatus: string;
    aadharDocUrl: string;
    panDocUrl: string;
}

interface SalaryHistory {
    id: number;
    oldSalary: number;
    newSalary: number;
    effectiveDate: string;
    reason: string;
}

interface EmployeeAdvance {
    id: number;
    amount: number;
    advanceDate: string;
    advanceType: string;
    remarks: string;
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
    aadharNumber: "",
    additionalPhones: "",
    address: "",
    city: "",
    state: "",
    pincode: "",
    photoUrl: "",
    bankAccountNumber: "",
    bankName: "",
    bankIfsc: "",
    bankBranch: "",
    panNumber: "",
    department: "",
    employeeCode: "",
    emergencyContact: "",
    emergencyPhone: "",
    bloodGroup: "",
    dateOfBirth: "",
    gender: "",
    maritalStatus: "",
    aadharDocUrl: "",
    panDocUrl: "",
};

import { API_BASE_URL } from "@/lib/api/station";

const API_BASE = `${API_BASE_URL}/employees`;

const inputClass =
    "flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/50";

const formatRupees = (val: number) => `₹${val.toLocaleString("en-IN")}`;

// ── Advance type badge colours ─────────────────────────────────────────

const advanceTypeBadge: Record<string, string> = {
    SALARY_ADVANCE: "bg-blue-100 text-blue-800 dark:bg-blue-900/40 dark:text-blue-300",
    HOME_ADVANCE: "bg-purple-100 text-purple-800 dark:bg-purple-900/40 dark:text-purple-300",
    NIGHT_ADVANCE: "bg-indigo-100 text-indigo-800 dark:bg-indigo-900/40 dark:text-indigo-300",
};

const advanceStatusBadge: Record<string, string> = {
    PENDING: "bg-yellow-100 text-yellow-800 dark:bg-yellow-900/40 dark:text-yellow-300",
    DEDUCTED: "bg-green-100 text-green-800 dark:bg-green-900/40 dark:text-green-300",
    WAIVED: "bg-gray-100 text-gray-800 dark:bg-gray-900/40 dark:text-gray-300",
};

const formatLabel = (s: string) => s.replace(/_/g, " ").replace(/\b\w/g, (c) => c.toUpperCase());

// ── Main Component ─────────────────────────────────────────────────────

export function EmployeeList() {
    const [employees, setEmployees] = useState<Employee[]>([]);
    const [isLoading, setIsLoading] = useState(false);
    const [showForm, setShowForm] = useState(false);
    const [currentEmployee, setCurrentEmployee] = useState<any>(emptyEmployee);
    const [isEditing, setIsEditing] = useState(false);
    const [searchQuery, setSearchQuery] = useState("");
    const [statusFilter, setStatusFilter] = useState<string>("ALL");
    const [formTab, setFormTab] = useState<"personal" | "additional" | "address" | "bank" | "documents">("personal");

    // Profile modal state
    const [profileEmployee, setProfileEmployee] = useState<Employee | null>(null);
    const [profileTab, setProfileTab] = useState<"overview" | "salary" | "advances">("overview");

    // Salary history
    const [salaryHistory, setSalaryHistory] = useState<SalaryHistory[]>([]);
    const [showSalaryForm, setShowSalaryForm] = useState(false);
    const [salaryRevision, setSalaryRevision] = useState({ newSalary: "", effectiveDate: "", reason: "" });

    // Advances
    const [advances, setAdvances] = useState<EmployeeAdvance[]>([]);
    const [showAdvanceForm, setShowAdvanceForm] = useState(false);
    const [newAdvance, setNewAdvance] = useState({ amount: "", advanceDate: "", advanceType: "SALARY_ADVANCE", remarks: "" });

    // Document upload state
    const [uploadingPhoto, setUploadingPhoto] = useState(false);
    const [uploadingAadhar, setUploadingAadhar] = useState(false);
    const [uploadingPan, setUploadingPan] = useState(false);
    const [photoPreview, setPhotoPreview] = useState<string | null>(null);
    const [aadharPreview, setAadharPreview] = useState<string | null>(null);
    const [panPreview, setPanPreview] = useState<string | null>(null);

    const { errors: empErrors, validate: validateEmp, clearError: clearEmpError, clearAllErrors: clearAllEmpErrors } = useFormValidation({
        name: [required("Employee name is required")],
        designation: [required("Designation is required")],
        email: [required("Email is required"), email()],
        phone: [required("Phone is required"), phone()],
        salary: [required("Salary is required"), min(0)],
        joinDate: [required("Join date is required")],
    });

    const { errors: salaryErrors, validate: validateSalary, clearError: clearSalaryError, clearAllErrors: clearAllSalaryErrors } = useFormValidation({
        newSalary: [required("New salary is required"), min(0)],
        effectiveDate: [required("Effective date is required")],
    });

    const { errors: advanceErrors, validate: validateAdvance, clearError: clearAdvanceError, clearAllErrors: clearAllAdvanceErrors } = useFormValidation({
        amount: [required("Amount is required"), min(1, "Amount must be at least 1")],
        advanceDate: [required("Date is required")],
    });

    const [apiError, setApiError] = useState("");

    useEffect(() => {
        fetchEmployees();
    }, []);

    // ── Data Fetching ──────────────────────────────────────────────────

    const fetchEmployees = async () => {
        try {
            const response = await fetch(API_BASE);
            if (response.ok) {
                const data = await response.json();
                setEmployees(data);
            }
        } catch (error) {
            console.error("Failed to fetch employees", error);
        }
    };

    const fetchSalaryHistory = async (id: number) => {
        try {
            const res = await fetch(`${API_BASE}/${id}/salary-history`);
            if (res.ok) setSalaryHistory(await res.json());
        } catch (error) {
            console.error("Failed to fetch salary history", error);
        }
    };

    const fetchAdvances = async (id: number) => {
        try {
            const res = await fetch(`${API_BASE}/${id}/advances`);
            if (res.ok) setAdvances(await res.json());
        } catch (error) {
            console.error("Failed to fetch advances", error);
        }
    };

    // ── CRUD handlers ──────────────────────────────────────────────────

    const handleDelete = async (id: number) => {
        if (!confirm("Are you sure you want to delete this employee?")) return;
        try {
            await fetch(`${API_BASE}/${id}`, { method: "DELETE" });
            fetchEmployees();
        } catch (error) {
            console.error("Failed to delete employee", error);
        }
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!validateEmp(currentEmployee)) return;
        setIsLoading(true);
        try {
            const url = isEditing ? `${API_BASE}/${currentEmployee.id}` : API_BASE;
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
            setApiError("Failed to save employee");
        } finally {
            setIsLoading(false);
        }
    };

    const handleSalaryRevision = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!validateSalary(salaryRevision)) return;
        if (!profileEmployee) return;
        try {
            const res = await fetch(`${API_BASE}/${profileEmployee.id}/salary-revision`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    newSalary: Number(salaryRevision.newSalary),
                    effectiveDate: salaryRevision.effectiveDate,
                    reason: salaryRevision.reason,
                }),
            });
            if (res.ok) {
                setShowSalaryForm(false);
                setSalaryRevision({ newSalary: "", effectiveDate: "", reason: "" });
                fetchSalaryHistory(profileEmployee.id);
                fetchEmployees();
            }
        } catch (error) {
            console.error("Failed to submit salary revision", error);
        }
    };

    const handleAddAdvance = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!validateAdvance(newAdvance)) return;
        if (!profileEmployee) return;
        try {
            const res = await fetch(`${API_BASE}/${profileEmployee.id}/advances`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    amount: Number(newAdvance.amount),
                    advanceDate: newAdvance.advanceDate,
                    advanceType: newAdvance.advanceType,
                    remarks: newAdvance.remarks,
                }),
            });
            if (res.ok) {
                setShowAdvanceForm(false);
                setNewAdvance({ amount: "", advanceDate: "", advanceType: "SALARY_ADVANCE", remarks: "" });
                fetchAdvances(profileEmployee.id);
            }
        } catch (error) {
            console.error("Failed to record advance", error);
        }
    };

    const handleAdvanceStatusChange = async (advanceId: number, status: string) => {
        if (!profileEmployee) return;
        try {
            await fetch(`${API_BASE}/advances/${advanceId}/status?status=${status}`, { method: "PATCH" });
            fetchAdvances(profileEmployee.id);
        } catch (error) {
            console.error("Failed to update advance status", error);
        }
    };

    // ── Document upload handlers ──────────────────────────────────
    const handlePhotoUpload = async (file: File) => {
        if (!currentEmployee?.id) return;
        setUploadingPhoto(true);
        try {
            const updated = await uploadEmployeePhoto(currentEmployee.id, file);
            setCurrentEmployee({ ...currentEmployee, photoUrl: (updated as any).photoUrl });
            setPhotoPreview(URL.createObjectURL(file));
            fetchEmployees();
        } catch (error) {
            console.error("Failed to upload photo", error);
        } finally {
            setUploadingPhoto(false);
        }
    };

    const handleAadharUpload = async (file: File) => {
        if (!currentEmployee?.id) return;
        setUploadingAadhar(true);
        try {
            const updated = await uploadEmployeeAadharDoc(currentEmployee.id, file);
            setCurrentEmployee({ ...currentEmployee, aadharDocUrl: (updated as any).aadharDocUrl });
            setAadharPreview(file.type.startsWith("image/") ? URL.createObjectURL(file) : file.name);
            fetchEmployees();
        } catch (error) {
            console.error("Failed to upload Aadhar doc", error);
        } finally {
            setUploadingAadhar(false);
        }
    };

    const handlePanUpload = async (file: File) => {
        if (!currentEmployee?.id) return;
        setUploadingPan(true);
        try {
            const updated = await uploadEmployeePanDoc(currentEmployee.id, file);
            setCurrentEmployee({ ...currentEmployee, panDocUrl: (updated as any).panDocUrl });
            setPanPreview(file.type.startsWith("image/") ? URL.createObjectURL(file) : file.name);
            fetchEmployees();
        } catch (error) {
            console.error("Failed to upload PAN doc", error);
        } finally {
            setUploadingPan(false);
        }
    };

    const openProfile = (emp: Employee) => {
        setProfileEmployee(emp);
        setProfileTab("overview");
        setSalaryHistory([]);
        setAdvances([]);
        setShowSalaryForm(false);
        setShowAdvanceForm(false);
        fetchSalaryHistory(emp.id);
        fetchAdvances(emp.id);
    };

    const set = (field: string, value: any) => setCurrentEmployee({ ...currentEmployee, [field]: value });

    // ── Filtered list ──────────────────────────────────────────────────

    const filteredEmployees = employees.filter((emp) => {
        const q = searchQuery.toLowerCase();
        const matchesSearch =
            !searchQuery ||
            emp.name?.toLowerCase().includes(q) ||
            emp.designation?.toLowerCase().includes(q) ||
            emp.email?.toLowerCase().includes(q) ||
            emp.phone?.includes(q);
        const matchesStatus = statusFilter === "ALL" || emp.status === statusFilter;
        return matchesSearch && matchesStatus;
    });

    // ── Render ─────────────────────────────────────────────────────────

    return (
        <div className="space-y-6">
            {/* Header */}
            <div className="flex items-center justify-between">
                <div>
                    <h2 className="text-2xl font-bold tracking-tight">Employees</h2>
                    <p className="text-muted-foreground">Manage your staff and their details.</p>
                </div>
                <button
                    onClick={() => {
                        setCurrentEmployee(emptyEmployee);
                        setIsEditing(false);
                        setFormTab("personal");
                        clearAllEmpErrors();
                        setApiError("");
                        setPhotoPreview(null);
                        setAadharPreview(null);
                        setPanPreview(null);
                        setShowForm(true);
                    }}
                    className="inline-flex items-center justify-center rounded-md text-sm font-medium ring-offset-background transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 bg-primary text-primary-foreground hover:bg-primary/90 h-10 px-4 py-2"
                >
                    <Plus className="mr-2 h-4 w-4" />
                    Add Employee
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
                            <tr>
                                <td colSpan={6} className="p-4 text-center text-muted-foreground">
                                    No employees found. Add one to get started.
                                </td>
                            </tr>
                        ) : (
                            filteredEmployees.map((emp) => (
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
                                        <span
                                            className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${
                                                emp.status === "Active"
                                                    ? "bg-green-100 text-green-800 dark:bg-green-900/40 dark:text-green-300"
                                                    : "bg-gray-100 text-gray-800 dark:bg-gray-900/40 dark:text-gray-300"
                                            }`}
                                        >
                                            {emp.status}
                                        </span>
                                    </td>
                                    <td className="p-4 text-right">
                                        <div className="flex justify-end gap-2">
                                            <button
                                                onClick={() => openProfile(emp)}
                                                className="p-2 hover:bg-muted rounded-md"
                                                title="View Profile"
                                            >
                                                <Eye className="w-4 h-4" />
                                            </button>
                                            <button
                                                onClick={() => {
                                                    setCurrentEmployee(emp);
                                                    setIsEditing(true);
                                                    setFormTab("personal");
                                                    clearAllEmpErrors();
                                                    setApiError("");
                                                    setPhotoPreview(null);
                                                    setAadharPreview(null);
                                                    setPanPreview(null);
                                                    setShowForm(true);
                                                }}
                                                className="p-2 hover:bg-muted rounded-md"
                                                title="Edit"
                                            >
                                                <Pencil className="w-4 h-4" />
                                            </button>
                                            <button
                                                onClick={() => handleDelete(emp.id)}
                                                className="p-2 hover:bg-red-100 text-red-600 rounded-md"
                                                title="Delete"
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

            {/* ── Add / Edit Modal ──────────────────────────────────────── */}
            {showForm && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm">
                    <div className="relative w-full max-w-2xl bg-card border border-border rounded-2xl shadow-2xl overflow-hidden">
                        {/* Header */}
                        <div className="flex items-center justify-between px-6 py-4 border-b border-border bg-muted/30">
                            <h3 className="text-lg font-semibold text-foreground">
                                {isEditing ? "Edit Employee" : "Add New Employee"}
                            </h3>
                            <button
                                onClick={() => setShowForm(false)}
                                className="text-muted-foreground hover:text-foreground transition-colors"
                            >
                                <X className="w-5 h-5" />
                            </button>
                        </div>

                        {/* Tabs */}
                        <div className="flex border-b border-border">
                            {([
                                { key: "personal", label: "Personal Info", icon: User },
                                { key: "additional", label: "Additional", icon: User },
                                { key: "address", label: "Address", icon: MapPin },
                                { key: "bank", label: "Bank Details", icon: Landmark },
                                { key: "documents", label: "Documents", icon: FileText },
                            ] as const).map(({ key, label, icon: Icon }) => (
                                <button
                                    key={key}
                                    type="button"
                                    onClick={() => setFormTab(key)}
                                    className={`flex items-center gap-2 px-5 py-3 text-sm font-medium border-b-2 transition-colors ${
                                        formTab === key
                                            ? "border-primary text-primary"
                                            : "border-transparent text-muted-foreground hover:text-foreground"
                                    }`}
                                >
                                    <Icon className="w-4 h-4" />
                                    {label}
                                </button>
                            ))}
                        </div>

                        {/* Form */}
                        <form onSubmit={handleSubmit} className="p-6">
                            <FormErrorBanner message={apiError} onDismiss={() => setApiError("")} />
                            {formTab === "personal" && (
                                <div className="grid grid-cols-2 gap-4">
                                    <div className="space-y-2">
                                        <label className="text-sm font-medium">Name *</label>
                                        <input className={`${inputClass} ${inputErrorClass(empErrors.name)}`} value={currentEmployee.name} onChange={(e) => { set("name", e.target.value); clearEmpError("name"); }} />
                                        <FieldError error={empErrors.name} />
                                    </div>
                                    <div className="space-y-2">
                                        <label className="text-sm font-medium">Designation *</label>
                                        <input className={`${inputClass} ${inputErrorClass(empErrors.designation)}`} value={currentEmployee.designation} onChange={(e) => { set("designation", e.target.value); clearEmpError("designation"); }} />
                                        <FieldError error={empErrors.designation} />
                                    </div>
                                    <div className="space-y-2">
                                        <label className="text-sm font-medium">Email *</label>
                                        <input className={`${inputClass} ${inputErrorClass(empErrors.email)}`} value={currentEmployee.email} onChange={(e) => { set("email", e.target.value); clearEmpError("email"); }} />
                                        <FieldError error={empErrors.email} />
                                    </div>
                                    <div className="space-y-2">
                                        <label className="text-sm font-medium">Phone *</label>
                                        <input className={`${inputClass} ${inputErrorClass(empErrors.phone)}`} value={currentEmployee.phone} onChange={(e) => { set("phone", e.target.value); clearEmpError("phone"); }} />
                                        <FieldError error={empErrors.phone} />
                                    </div>
                                    <div className="space-y-2">
                                        <label className="text-sm font-medium">Additional Phones</label>
                                        <input className={inputClass} placeholder="Comma-separated" value={currentEmployee.additionalPhones} onChange={(e) => set("additionalPhones", e.target.value)} />
                                    </div>
                                    <div className="space-y-2">
                                        <label className="text-sm font-medium">Aadhar Number</label>
                                        <input className={inputClass} value={currentEmployee.aadharNumber} onChange={(e) => set("aadharNumber", e.target.value)} />
                                    </div>
                                    <div className="space-y-2">
                                        <label className="text-sm font-medium">Salary *</label>
                                        <input type="number" className={`${inputClass} ${inputErrorClass(empErrors.salary)}`} value={currentEmployee.salary} onChange={(e) => { set("salary", Number(e.target.value)); clearEmpError("salary"); }} />
                                        <FieldError error={empErrors.salary} />
                                    </div>
                                    <div className="space-y-2">
                                        <label className="text-sm font-medium">Join Date *</label>
                                        <input type="date" className={`${inputClass} ${inputErrorClass(empErrors.joinDate)}`} value={currentEmployee.joinDate} onChange={(e) => { set("joinDate", e.target.value); clearEmpError("joinDate"); }} />
                                        <FieldError error={empErrors.joinDate} />
                                    </div>
                                    <div className="space-y-2">
                                        <label className="text-sm font-medium">Status</label>
                                        <select className={inputClass} value={currentEmployee.status} onChange={(e) => set("status", e.target.value)}>
                                            <option value="Active">Active</option>
                                            <option value="Inactive">Inactive</option>
                                        </select>
                                    </div>
                                </div>
                            )}

                            {formTab === "additional" && (
                                <div className="grid grid-cols-2 gap-4">
                                    <div className="space-y-2">
                                        <label className="text-sm font-medium">Employee Code</label>
                                        <input className={inputClass} value={currentEmployee.employeeCode} onChange={(e) => set("employeeCode", e.target.value)} />
                                    </div>
                                    <div className="space-y-2">
                                        <label className="text-sm font-medium">Department</label>
                                        <input className={inputClass} value={currentEmployee.department} onChange={(e) => set("department", e.target.value)} />
                                    </div>
                                    <div className="space-y-2">
                                        <label className="text-sm font-medium">PAN Number</label>
                                        <input className={inputClass} value={currentEmployee.panNumber} onChange={(e) => set("panNumber", e.target.value)} />
                                    </div>
                                    <div className="space-y-2">
                                        <label className="text-sm font-medium">Date of Birth</label>
                                        <input type="date" className={inputClass} value={currentEmployee.dateOfBirth} onChange={(e) => set("dateOfBirth", e.target.value)} />
                                    </div>
                                    <div className="space-y-2">
                                        <label className="text-sm font-medium">Gender</label>
                                        <select className={inputClass} value={currentEmployee.gender} onChange={(e) => set("gender", e.target.value)}>
                                            <option value="">Select</option>
                                            <option value="Male">Male</option>
                                            <option value="Female">Female</option>
                                            <option value="Other">Other</option>
                                        </select>
                                    </div>
                                    <div className="space-y-2">
                                        <label className="text-sm font-medium">Marital Status</label>
                                        <select className={inputClass} value={currentEmployee.maritalStatus} onChange={(e) => set("maritalStatus", e.target.value)}>
                                            <option value="">Select</option>
                                            <option value="Single">Single</option>
                                            <option value="Married">Married</option>
                                            <option value="Divorced">Divorced</option>
                                            <option value="Widowed">Widowed</option>
                                        </select>
                                    </div>
                                    <div className="space-y-2">
                                        <label className="text-sm font-medium">Blood Group</label>
                                        <select className={inputClass} value={currentEmployee.bloodGroup} onChange={(e) => set("bloodGroup", e.target.value)}>
                                            <option value="">Select</option>
                                            {["A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"].map(bg => (
                                                <option key={bg} value={bg}>{bg}</option>
                                            ))}
                                        </select>
                                    </div>
                                    <div className="space-y-2">
                                        <label className="text-sm font-medium">Emergency Contact</label>
                                        <input className={inputClass} value={currentEmployee.emergencyContact} onChange={(e) => set("emergencyContact", e.target.value)} />
                                    </div>
                                    <div className="space-y-2">
                                        <label className="text-sm font-medium">Emergency Phone</label>
                                        <input className={inputClass} value={currentEmployee.emergencyPhone} onChange={(e) => set("emergencyPhone", e.target.value)} />
                                    </div>
                                </div>
                            )}

                            {formTab === "address" && (
                                <div className="grid grid-cols-2 gap-4">
                                    <div className="space-y-2 col-span-2">
                                        <label className="text-sm font-medium">Address</label>
                                        <textarea
                                            rows={3}
                                            className="flex w-full rounded-md border border-input bg-background px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
                                            value={currentEmployee.address}
                                            onChange={(e) => set("address", e.target.value)}
                                        />
                                    </div>
                                    <div className="space-y-2">
                                        <label className="text-sm font-medium">City</label>
                                        <input className={inputClass} value={currentEmployee.city} onChange={(e) => set("city", e.target.value)} />
                                    </div>
                                    <div className="space-y-2">
                                        <label className="text-sm font-medium">State</label>
                                        <input className={inputClass} value={currentEmployee.state} onChange={(e) => set("state", e.target.value)} />
                                    </div>
                                    <div className="space-y-2">
                                        <label className="text-sm font-medium">Pincode</label>
                                        <input className={inputClass} value={currentEmployee.pincode} onChange={(e) => set("pincode", e.target.value)} />
                                    </div>
                                </div>
                            )}

                            {formTab === "bank" && (
                                <div className="grid grid-cols-2 gap-4">
                                    <div className="space-y-2">
                                        <label className="text-sm font-medium">Bank Name</label>
                                        <input className={inputClass} value={currentEmployee.bankName} onChange={(e) => set("bankName", e.target.value)} />
                                    </div>
                                    <div className="space-y-2">
                                        <label className="text-sm font-medium">Account Number</label>
                                        <input className={inputClass} value={currentEmployee.bankAccountNumber} onChange={(e) => set("bankAccountNumber", e.target.value)} />
                                    </div>
                                    <div className="space-y-2">
                                        <label className="text-sm font-medium">IFSC Code</label>
                                        <input className={inputClass} value={currentEmployee.bankIfsc} onChange={(e) => set("bankIfsc", e.target.value)} />
                                    </div>
                                    <div className="space-y-2">
                                        <label className="text-sm font-medium">Branch</label>
                                        <input className={inputClass} value={currentEmployee.bankBranch} onChange={(e) => set("bankBranch", e.target.value)} />
                                    </div>
                                </div>
                            )}

                            {formTab === "documents" && (
                                !isEditing ? (
                                    <div className="flex flex-col items-center justify-center py-12 text-center">
                                        <FileText className="w-12 h-12 text-muted-foreground/50 mb-3" />
                                        <p className="text-muted-foreground font-medium">Save employee first to upload documents</p>
                                        <p className="text-xs text-muted-foreground mt-1">Documents can be uploaded after creating the employee record.</p>
                                    </div>
                                ) : (
                                    <div className="space-y-6">
                                        {/* Photo Upload */}
                                        <div className="space-y-3">
                                            <label className="text-sm font-semibold flex items-center gap-2">
                                                <Camera className="w-4 h-4" /> Employee Photo
                                            </label>
                                            <div className="flex items-center gap-4">
                                                <div className="relative">
                                                    {photoPreview ? (
                                                        <img src={photoPreview} alt="Preview" className="w-24 h-24 rounded-full object-cover" />
                                                    ) : (
                                                        <EmployeeAvatar employeeId={currentEmployee.id} name={currentEmployee.name} photoUrl={currentEmployee.photoUrl} size="lg" />
                                                    )}
                                                    {uploadingPhoto && (
                                                        <div className="absolute inset-0 bg-black/50 rounded-full flex items-center justify-center">
                                                            <Loader2 className="w-6 h-6 text-white animate-spin" />
                                                        </div>
                                                    )}
                                                </div>
                                                <div>
                                                    <input
                                                        type="file"
                                                        accept="image/jpeg,image/png,image/webp"
                                                        id="photo-upload"
                                                        className="hidden"
                                                        onChange={(e) => {
                                                            const file = e.target.files?.[0];
                                                            if (file) handlePhotoUpload(file);
                                                            e.target.value = "";
                                                        }}
                                                    />
                                                    <button
                                                        type="button"
                                                        onClick={() => document.getElementById("photo-upload")?.click()}
                                                        disabled={uploadingPhoto}
                                                        className="inline-flex items-center gap-2 px-3 py-2 text-sm border border-border rounded-md hover:bg-muted transition-colors disabled:opacity-50"
                                                    >
                                                        <Upload className="w-4 h-4" />
                                                        {uploadingPhoto ? "Uploading..." : "Upload Photo"}
                                                    </button>
                                                    <p className="text-xs text-muted-foreground mt-1">JPG, PNG or WebP. Max 5MB.</p>
                                                </div>
                                            </div>
                                        </div>

                                        {/* Aadhar Document */}
                                        <div className="space-y-3">
                                            <label className="text-sm font-semibold flex items-center gap-2">
                                                <FileText className="w-4 h-4" /> Aadhar Document
                                            </label>
                                            <div className="flex items-center gap-4">
                                                <div className="w-16 h-16 rounded-lg border border-border flex items-center justify-center bg-muted/30 flex-shrink-0">
                                                    {aadharPreview && aadharPreview.startsWith("blob:") ? (
                                                        <img src={aadharPreview} alt="Aadhar" className="w-full h-full rounded-lg object-cover" />
                                                    ) : currentEmployee.aadharDocUrl ? (
                                                        <FileText className="w-6 h-6 text-blue-500" />
                                                    ) : (
                                                        <FileText className="w-6 h-6 text-muted-foreground/30" />
                                                    )}
                                                    {uploadingAadhar && (
                                                        <div className="absolute inset-0 bg-black/50 rounded-lg flex items-center justify-center">
                                                            <Loader2 className="w-4 h-4 text-white animate-spin" />
                                                        </div>
                                                    )}
                                                </div>
                                                <div>
                                                    <input
                                                        type="file"
                                                        accept="image/jpeg,image/png,image/webp,application/pdf"
                                                        id="aadhar-upload"
                                                        className="hidden"
                                                        onChange={(e) => {
                                                            const file = e.target.files?.[0];
                                                            if (file) handleAadharUpload(file);
                                                            e.target.value = "";
                                                        }}
                                                    />
                                                    <button
                                                        type="button"
                                                        onClick={() => document.getElementById("aadhar-upload")?.click()}
                                                        disabled={uploadingAadhar}
                                                        className="inline-flex items-center gap-2 px-3 py-2 text-sm border border-border rounded-md hover:bg-muted transition-colors disabled:opacity-50"
                                                    >
                                                        <Upload className="w-4 h-4" />
                                                        {uploadingAadhar ? "Uploading..." : currentEmployee.aadharDocUrl ? "Replace" : "Upload Aadhar"}
                                                    </button>
                                                    <p className="text-xs text-muted-foreground mt-1">JPG, PNG, WebP or PDF. Max 10MB.</p>
                                                </div>
                                            </div>
                                        </div>

                                        {/* PAN Document */}
                                        <div className="space-y-3">
                                            <label className="text-sm font-semibold flex items-center gap-2">
                                                <FileText className="w-4 h-4" /> PAN Document
                                            </label>
                                            <div className="flex items-center gap-4">
                                                <div className="w-16 h-16 rounded-lg border border-border flex items-center justify-center bg-muted/30 flex-shrink-0">
                                                    {panPreview && panPreview.startsWith("blob:") ? (
                                                        <img src={panPreview} alt="PAN" className="w-full h-full rounded-lg object-cover" />
                                                    ) : currentEmployee.panDocUrl ? (
                                                        <FileText className="w-6 h-6 text-blue-500" />
                                                    ) : (
                                                        <FileText className="w-6 h-6 text-muted-foreground/30" />
                                                    )}
                                                    {uploadingPan && (
                                                        <div className="absolute inset-0 bg-black/50 rounded-lg flex items-center justify-center">
                                                            <Loader2 className="w-4 h-4 text-white animate-spin" />
                                                        </div>
                                                    )}
                                                </div>
                                                <div>
                                                    <input
                                                        type="file"
                                                        accept="image/jpeg,image/png,image/webp,application/pdf"
                                                        id="pan-upload"
                                                        className="hidden"
                                                        onChange={(e) => {
                                                            const file = e.target.files?.[0];
                                                            if (file) handlePanUpload(file);
                                                            e.target.value = "";
                                                        }}
                                                    />
                                                    <button
                                                        type="button"
                                                        onClick={() => document.getElementById("pan-upload")?.click()}
                                                        disabled={uploadingPan}
                                                        className="inline-flex items-center gap-2 px-3 py-2 text-sm border border-border rounded-md hover:bg-muted transition-colors disabled:opacity-50"
                                                    >
                                                        <Upload className="w-4 h-4" />
                                                        {uploadingPan ? "Uploading..." : currentEmployee.panDocUrl ? "Replace" : "Upload PAN"}
                                                    </button>
                                                    <p className="text-xs text-muted-foreground mt-1">JPG, PNG, WebP or PDF. Max 10MB.</p>
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                )
                            )}

                            {/* Footer */}
                            <div className="flex justify-end gap-2 mt-6 pt-4 border-t border-border">
                                <button
                                    type="button"
                                    onClick={() => setShowForm(false)}
                                    className="px-4 py-2 text-sm font-medium border border-border rounded-md hover:bg-muted transition-colors"
                                >
                                    Cancel
                                </button>
                                <button
                                    type="submit"
                                    disabled={isLoading}
                                    className="px-4 py-2 text-sm font-medium bg-primary text-primary-foreground rounded-md hover:bg-primary/90 disabled:opacity-50 transition-colors"
                                >
                                    {isLoading ? "Saving..." : "Save"}
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}

            {/* ── Employee Profile Modal ────────────────────────────────── */}
            <Modal
                isOpen={!!profileEmployee}
                onClose={() => setProfileEmployee(null)}
                title={profileEmployee?.name ?? "Employee Profile"}
            >
                {profileEmployee && (
                    <div className="space-y-6">
                        {/* Profile Header */}
                        <div className="flex items-start gap-5 -mt-2">
                            <EmployeeAvatar employeeId={profileEmployee.id} name={profileEmployee.name} photoUrl={profileEmployee.photoUrl} size="lg" />
                            <div className="flex-1 min-w-0">
                                <h3 className="text-xl font-bold truncate">{profileEmployee.name}</h3>
                                <p className="text-sm text-muted-foreground">{profileEmployee.designation}{profileEmployee.department ? ` - ${profileEmployee.department}` : ""}</p>
                                {profileEmployee.employeeCode && (
                                    <p className="text-xs text-muted-foreground mt-0.5">ID: {profileEmployee.employeeCode}</p>
                                )}
                                <div className="flex items-center gap-3 mt-2">
                                    <span
                                        className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${
                                            profileEmployee.status === "Active"
                                                ? "bg-green-100 text-green-800 dark:bg-green-900/40 dark:text-green-300"
                                                : "bg-gray-100 text-gray-800 dark:bg-gray-900/40 dark:text-gray-300"
                                        }`}
                                    >
                                        {profileEmployee.status}
                                    </span>
                                    <span className="text-sm text-muted-foreground">Joined {profileEmployee.joinDate}</span>
                                    <span className="text-sm font-semibold">{formatRupees(profileEmployee.salary)}</span>
                                </div>
                            </div>
                        </div>

                        {/* Profile Tabs */}
                        <div className="flex border-b border-border">
                            {([
                                { key: "overview", label: "Overview", icon: User },
                                { key: "salary", label: "Salary History", icon: History },
                                { key: "advances", label: "Advances", icon: Banknote },
                            ] as const).map(({ key, label, icon: Icon }) => (
                                <button
                                    key={key}
                                    onClick={() => setProfileTab(key)}
                                    className={`flex items-center gap-2 px-5 py-3 text-sm font-medium border-b-2 transition-colors ${
                                        profileTab === key
                                            ? "border-primary text-primary"
                                            : "border-transparent text-muted-foreground hover:text-foreground"
                                    }`}
                                >
                                    <Icon className="w-4 h-4" />
                                    {label}
                                </button>
                            ))}
                        </div>

                        {/* ── Overview Tab ────────────────────────────── */}
                        {profileTab === "overview" && (
                            <div className="space-y-4">
                                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                    <GlassCard>
                                        <h4 className="text-sm font-semibold text-muted-foreground mb-3 flex items-center gap-2">
                                            <User className="w-4 h-4" /> Personal Information
                                        </h4>
                                        <dl className="space-y-2 text-sm">
                                            <DetailRow label="Email" value={profileEmployee.email} />
                                            <DetailRow label="Phone" value={profileEmployee.phone} />
                                            {profileEmployee.additionalPhones && (
                                                <DetailRow label="Other Phones" value={profileEmployee.additionalPhones} />
                                            )}
                                            {profileEmployee.aadharNumber && (
                                                <DetailRow label="Aadhar" value={profileEmployee.aadharNumber} />
                                            )}
                                            {profileEmployee.panNumber && (
                                                <DetailRow label="PAN" value={profileEmployee.panNumber} />
                                            )}
                                        </dl>
                                    </GlassCard>

                                    <GlassCard>
                                        <h4 className="text-sm font-semibold text-muted-foreground mb-3 flex items-center gap-2">
                                            <User className="w-4 h-4" /> Additional Info
                                        </h4>
                                        <dl className="space-y-2 text-sm">
                                            {profileEmployee.dateOfBirth && <DetailRow label="Date of Birth" value={profileEmployee.dateOfBirth} />}
                                            {profileEmployee.gender && <DetailRow label="Gender" value={profileEmployee.gender} />}
                                            {profileEmployee.maritalStatus && <DetailRow label="Marital Status" value={profileEmployee.maritalStatus} />}
                                            {profileEmployee.bloodGroup && <DetailRow label="Blood Group" value={profileEmployee.bloodGroup} />}
                                            {profileEmployee.emergencyContact && <DetailRow label="Emergency Contact" value={profileEmployee.emergencyContact} />}
                                            {profileEmployee.emergencyPhone && <DetailRow label="Emergency Phone" value={profileEmployee.emergencyPhone} />}
                                            {!profileEmployee.dateOfBirth && !profileEmployee.gender && !profileEmployee.bloodGroup && (
                                                <p className="text-muted-foreground text-xs">No additional info available.</p>
                                            )}
                                        </dl>
                                    </GlassCard>

                                    <GlassCard>
                                        <h4 className="text-sm font-semibold text-muted-foreground mb-3 flex items-center gap-2">
                                            <MapPin className="w-4 h-4" /> Address
                                        </h4>
                                        <dl className="space-y-2 text-sm">
                                            <DetailRow label="Address" value={profileEmployee.address || "-"} />
                                            <DetailRow label="City" value={profileEmployee.city || "-"} />
                                            <DetailRow label="State" value={profileEmployee.state || "-"} />
                                            <DetailRow label="Pincode" value={profileEmployee.pincode || "-"} />
                                        </dl>
                                    </GlassCard>

                                    <GlassCard>
                                        <h4 className="text-sm font-semibold text-muted-foreground mb-3 flex items-center gap-2">
                                            <Landmark className="w-4 h-4" /> Bank Details
                                        </h4>
                                        <dl className="space-y-2 text-sm">
                                            <DetailRow label="Bank" value={profileEmployee.bankName || "-"} />
                                            <DetailRow label="Account No." value={profileEmployee.bankAccountNumber || "-"} />
                                            <DetailRow label="IFSC" value={profileEmployee.bankIfsc || "-"} />
                                            <DetailRow label="Branch" value={profileEmployee.bankBranch || "-"} />
                                        </dl>
                                    </GlassCard>
                                </div>

                                {/* Documents Section */}
                                <GlassCard>
                                    <h4 className="text-sm font-semibold text-muted-foreground mb-3 flex items-center gap-2">
                                        <FileText className="w-4 h-4" /> Documents
                                    </h4>
                                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                                        <DocumentCard
                                            label="Aadhar Document"
                                            hasFile={!!profileEmployee.aadharDocUrl}
                                            employeeId={profileEmployee.id}
                                            fileType="aadhar-doc"
                                        />
                                        <DocumentCard
                                            label="PAN Document"
                                            hasFile={!!profileEmployee.panDocUrl}
                                            employeeId={profileEmployee.id}
                                            fileType="pan-doc"
                                        />
                                    </div>
                                </GlassCard>
                            </div>
                        )}

                        {/* ── Salary History Tab ─────────────────────── */}
                        {profileTab === "salary" && (
                            <div className="space-y-4">
                                {/* Current Salary */}
                                <GlassCard className="flex items-center justify-between">
                                    <div>
                                        <p className="text-sm text-muted-foreground">Current Salary</p>
                                        <p className="text-3xl font-bold">{formatRupees(profileEmployee.salary)}</p>
                                    </div>
                                    <IndianRupee className="w-10 h-10 text-primary/30" />
                                </GlassCard>

                                {/* Add revision button */}
                                <div className="flex justify-end">
                                    <button
                                        onClick={() => { clearAllSalaryErrors(); setShowSalaryForm(!showSalaryForm); }}
                                        className="inline-flex items-center gap-2 px-4 py-2 text-sm font-medium bg-primary text-primary-foreground rounded-md hover:bg-primary/90 transition-colors"
                                    >
                                        <Plus className="w-4 h-4" />
                                        Add Salary Revision
                                    </button>
                                </div>

                                {/* Inline form */}
                                {showSalaryForm && (
                                    <GlassCard>
                                        <form onSubmit={handleSalaryRevision} className="grid grid-cols-3 gap-4">
                                            <div className="space-y-2">
                                                <label className="text-sm font-medium">New Salary *</label>
                                                <input
                                                    type="number"
                                                    className={`${inputClass} ${inputErrorClass(salaryErrors.newSalary)}`}
                                                    value={salaryRevision.newSalary}
                                                    onChange={(e) => { setSalaryRevision({ ...salaryRevision, newSalary: e.target.value }); clearSalaryError("newSalary"); }}
                                                />
                                                <FieldError error={salaryErrors.newSalary} />
                                            </div>
                                            <div className="space-y-2">
                                                <label className="text-sm font-medium">Effective Date *</label>
                                                <input
                                                    type="date"
                                                    className={`${inputClass} ${inputErrorClass(salaryErrors.effectiveDate)}`}
                                                    value={salaryRevision.effectiveDate}
                                                    onChange={(e) => { setSalaryRevision({ ...salaryRevision, effectiveDate: e.target.value }); clearSalaryError("effectiveDate"); }}
                                                />
                                                <FieldError error={salaryErrors.effectiveDate} />
                                            </div>
                                            <div className="space-y-2">
                                                <label className="text-sm font-medium">Reason</label>
                                                <input
                                                    className={inputClass}
                                                    value={salaryRevision.reason}
                                                    onChange={(e) => setSalaryRevision({ ...salaryRevision, reason: e.target.value })}
                                                />
                                            </div>
                                            <div className="col-span-3 flex justify-end gap-2">
                                                <button
                                                    type="button"
                                                    onClick={() => setShowSalaryForm(false)}
                                                    className="px-3 py-1.5 text-sm border border-border rounded-md hover:bg-muted transition-colors"
                                                >
                                                    Cancel
                                                </button>
                                                <button
                                                    type="submit"
                                                    className="px-3 py-1.5 text-sm bg-primary text-primary-foreground rounded-md hover:bg-primary/90 transition-colors"
                                                >
                                                    Save Revision
                                                </button>
                                            </div>
                                        </form>
                                    </GlassCard>
                                )}

                                {/* History table */}
                                <div className="rounded-md border">
                                    <table className="w-full text-sm text-left">
                                        <thead className="bg-muted/50 text-muted-foreground">
                                            <tr>
                                                <th className="p-3 font-medium">Effective Date</th>
                                                <th className="p-3 font-medium">Old Salary</th>
                                                <th className="p-3 font-medium"></th>
                                                <th className="p-3 font-medium">New Salary</th>
                                                <th className="p-3 font-medium">Reason</th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            {salaryHistory.length === 0 ? (
                                                <tr>
                                                    <td colSpan={5} className="p-4 text-center text-muted-foreground">
                                                        No salary revisions recorded yet.
                                                    </td>
                                                </tr>
                                            ) : (
                                                salaryHistory.map((sh) => (
                                                    <tr key={sh.id} className="border-t hover:bg-muted/50">
                                                        <td className="p-3">{sh.effectiveDate}</td>
                                                        <td className="p-3">{formatRupees(sh.oldSalary)}</td>
                                                        <td className="p-3 text-center">
                                                            <ChevronRight className="w-4 h-4 text-muted-foreground inline" />
                                                        </td>
                                                        <td className="p-3 font-medium">{formatRupees(sh.newSalary)}</td>
                                                        <td className="p-3 text-muted-foreground">{sh.reason}</td>
                                                    </tr>
                                                ))
                                            )}
                                        </tbody>
                                    </table>
                                </div>
                            </div>
                        )}

                        {/* ── Advances Tab ────────────────────────────── */}
                        {profileTab === "advances" && (
                            <div className="space-y-4">
                                {/* Record advance button */}
                                <div className="flex justify-end">
                                    <button
                                        onClick={() => { clearAllAdvanceErrors(); setShowAdvanceForm(!showAdvanceForm); }}
                                        className="inline-flex items-center gap-2 px-4 py-2 text-sm font-medium bg-primary text-primary-foreground rounded-md hover:bg-primary/90 transition-colors"
                                    >
                                        <Plus className="w-4 h-4" />
                                        Record Advance
                                    </button>
                                </div>

                                {/* Inline form */}
                                {showAdvanceForm && (
                                    <GlassCard>
                                        <form onSubmit={handleAddAdvance} className="grid grid-cols-2 gap-4">
                                            <div className="space-y-2">
                                                <label className="text-sm font-medium">Amount *</label>
                                                <input
                                                    type="number"
                                                    className={`${inputClass} ${inputErrorClass(advanceErrors.amount)}`}
                                                    value={newAdvance.amount}
                                                    onChange={(e) => { setNewAdvance({ ...newAdvance, amount: e.target.value }); clearAdvanceError("amount"); }}
                                                />
                                                <FieldError error={advanceErrors.amount} />
                                            </div>
                                            <div className="space-y-2">
                                                <label className="text-sm font-medium">Date *</label>
                                                <input
                                                    type="date"
                                                    className={`${inputClass} ${inputErrorClass(advanceErrors.advanceDate)}`}
                                                    value={newAdvance.advanceDate}
                                                    onChange={(e) => { setNewAdvance({ ...newAdvance, advanceDate: e.target.value }); clearAdvanceError("advanceDate"); }}
                                                />
                                                <FieldError error={advanceErrors.advanceDate} />
                                            </div>
                                            <div className="space-y-2">
                                                <label className="text-sm font-medium">Type *</label>
                                                <select
                                                    className={inputClass}
                                                    value={newAdvance.advanceType}
                                                    onChange={(e) => setNewAdvance({ ...newAdvance, advanceType: e.target.value })}
                                                >
                                                    <option value="SALARY_ADVANCE">Salary Advance</option>
                                                    <option value="HOME_ADVANCE">Home Advance</option>
                                                    <option value="NIGHT_ADVANCE">Night Advance</option>
                                                </select>
                                            </div>
                                            <div className="space-y-2">
                                                <label className="text-sm font-medium">Remarks</label>
                                                <input
                                                    className={inputClass}
                                                    value={newAdvance.remarks}
                                                    onChange={(e) => setNewAdvance({ ...newAdvance, remarks: e.target.value })}
                                                />
                                            </div>
                                            <div className="col-span-2 flex justify-end gap-2">
                                                <button
                                                    type="button"
                                                    onClick={() => setShowAdvanceForm(false)}
                                                    className="px-3 py-1.5 text-sm border border-border rounded-md hover:bg-muted transition-colors"
                                                >
                                                    Cancel
                                                </button>
                                                <button
                                                    type="submit"
                                                    className="px-3 py-1.5 text-sm bg-primary text-primary-foreground rounded-md hover:bg-primary/90 transition-colors"
                                                >
                                                    Save Advance
                                                </button>
                                            </div>
                                        </form>
                                    </GlassCard>
                                )}

                                {/* Advances table */}
                                <div className="rounded-md border">
                                    <table className="w-full text-sm text-left">
                                        <thead className="bg-muted/50 text-muted-foreground">
                                            <tr>
                                                <th className="p-3 font-medium">Date</th>
                                                <th className="p-3 font-medium">Type</th>
                                                <th className="p-3 font-medium">Amount</th>
                                                <th className="p-3 font-medium">Remarks</th>
                                                <th className="p-3 font-medium">Status</th>
                                                <th className="p-3 font-medium text-right">Action</th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            {advances.length === 0 ? (
                                                <tr>
                                                    <td colSpan={6} className="p-4 text-center text-muted-foreground">
                                                        No advances recorded yet.
                                                    </td>
                                                </tr>
                                            ) : (
                                                advances.map((adv) => (
                                                    <tr key={adv.id} className="border-t hover:bg-muted/50">
                                                        <td className="p-3">{adv.advanceDate}</td>
                                                        <td className="p-3">
                                                            <span
                                                                className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${
                                                                    advanceTypeBadge[adv.advanceType] ?? "bg-gray-100 text-gray-800"
                                                                }`}
                                                            >
                                                                {formatLabel(adv.advanceType)}
                                                            </span>
                                                        </td>
                                                        <td className="p-3 font-medium">{formatRupees(adv.amount)}</td>
                                                        <td className="p-3 text-muted-foreground">{adv.remarks || "-"}</td>
                                                        <td className="p-3">
                                                            <span
                                                                className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${
                                                                    advanceStatusBadge[adv.status] ?? "bg-gray-100 text-gray-800"
                                                                }`}
                                                            >
                                                                {adv.status}
                                                            </span>
                                                        </td>
                                                        <td className="p-3 text-right">
                                                            {adv.status === "PENDING" && (
                                                                <select
                                                                    className="text-xs border border-border rounded-md px-2 py-1 bg-background focus:outline-none focus:ring-2 focus:ring-primary/50"
                                                                    defaultValue=""
                                                                    onChange={(e) => {
                                                                        if (e.target.value) {
                                                                            handleAdvanceStatusChange(adv.id, e.target.value);
                                                                            e.target.value = "";
                                                                        }
                                                                    }}
                                                                >
                                                                    <option value="" disabled>
                                                                        Change...
                                                                    </option>
                                                                    <option value="DEDUCTED">Mark Deducted</option>
                                                                    <option value="WAIVED">Mark Waived</option>
                                                                </select>
                                                            )}
                                                        </td>
                                                    </tr>
                                                ))
                                            )}
                                        </tbody>
                                    </table>
                                </div>
                            </div>
                        )}
                    </div>
                )}
            </Modal>
        </div>
    );
}

// ── Helper: read-only detail row ───────────────────────────────────────

function DetailRow({ label, value }: { label: string; value: React.ReactNode }) {
    return (
        <div className="flex justify-between">
            <dt className="text-muted-foreground">{label}</dt>
            <dd className="font-medium text-right">{value}</dd>
        </div>
    );
}

function DocumentCard({ label, hasFile, employeeId, fileType }: { label: string; hasFile: boolean; employeeId: number; fileType: string }) {
    const [loading, setLoading] = useState(false);

    const handleView = async () => {
        setLoading(true);
        try {
            const { url } = await getEmployeeFileUrl(employeeId, fileType);
            window.open(url, "_blank");
        } catch (error) {
            console.error("Failed to get file URL", error);
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="flex items-center justify-between p-3 rounded-lg border border-border bg-muted/20">
            <div className="flex items-center gap-2">
                <FileText className={`w-5 h-5 ${hasFile ? "text-blue-500" : "text-muted-foreground/30"}`} />
                <span className="text-sm font-medium">{label}</span>
            </div>
            {hasFile ? (
                <button
                    onClick={handleView}
                    disabled={loading}
                    className="inline-flex items-center gap-1 px-2 py-1 text-xs font-medium text-primary hover:underline disabled:opacity-50"
                >
                    {loading ? <Loader2 className="w-3 h-3 animate-spin" /> : <ExternalLink className="w-3 h-3" />}
                    View
                </button>
            ) : (
                <span className="text-xs text-muted-foreground">No document</span>
            )}
        </div>
    );
}
