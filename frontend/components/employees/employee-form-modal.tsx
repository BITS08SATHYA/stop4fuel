"use client";

import { useState } from "react";
import { X, User, MapPin, Landmark, FileText, Camera } from "lucide-react";
import { useFormValidation, required, email, min, max, indianMobile, aadhar, pan, ifsc, indianPincode, bankAccount, maxLength, pastDate, pastOrPresentDate, minAge } from "@/lib/validation";
import { FieldError, inputErrorClass, FormErrorBanner } from "@/components/ui/field-error";
import {
    uploadEmployeePhoto,
    uploadEmployeeAadharDoc,
    uploadEmployeePanDoc,
} from "@/lib/api/station";
import { DocumentUploadField } from "./document-upload-field";
import type { Employee } from "./types";
import { inputClass, API_BASE } from "./types";

interface EmployeeFormModalProps {
    employee: any;
    isEditing: boolean;
    onClose: () => void;
    onSaved: () => void;
}

export function EmployeeFormModal({ employee: initialEmployee, isEditing, onClose, onSaved }: EmployeeFormModalProps) {
    const [currentEmployee, setCurrentEmployee] = useState<any>(initialEmployee);
    const [isLoading, setIsLoading] = useState(false);
    const [apiError, setApiError] = useState("");
    const [formTab, setFormTab] = useState<"personal" | "additional" | "address" | "bank" | "documents">("personal");

    const { errors: empErrors, validate: validateEmp, validateField: validateEmpField, clearError: clearEmpError } = useFormValidation({
        // Personal Info
        name: [required("Employee name is required")],
        designation: [required("Designation is required")],
        email: [required("Email is required"), email()],
        phone: [required("Phone is required"), indianMobile()],
        salary: [required("Salary is required"), min(0, "Salary must be positive"), max(9999999, "Salary cannot exceed 99,99,999")],
        joinDate: [required("Join date is required"), pastOrPresentDate("Join date cannot be in the future")],
        aadharNumber: [aadhar()],
        // Additional Details
        employeeCode: [maxLength(20, "Employee code must not exceed 20 characters")],
        panNumber: [],
        dateOfBirth: [pastDate("Date of birth must be in the past"), minAge(15, "Employee must be at least 15 years old")],
        emergencyPhone: [indianMobile("Emergency phone must be a valid Indian mobile number")],
        // Address
        pincode: [indianPincode()],
        // Bank Details
        bankAccountNumber: [bankAccount()],
        bankIfsc: [],
    });

    const set = (field: string, value: any) => setCurrentEmployee({ ...currentEmployee, [field]: value });

    // Map fields to tabs for auto-switching on validation failure
    const tabFields: Record<string, string[]> = {
        personal: ["name", "designation", "email", "phone", "salary", "joinDate", "aadharNumber"],
        additional: ["employeeCode", "panNumber", "dateOfBirth", "emergencyPhone"],
        address: ["pincode"],
        bank: ["bankAccountNumber", "bankIfsc"],
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!validateEmp(currentEmployee)) {
            // Auto-switch to the first tab that has errors
            const tabPriority: (typeof formTab)[] = ["personal", "additional", "address", "bank"];
            for (const tab of tabPriority) {
                const fields = tabFields[tab] || [];
                const hasError = fields.some(f => validateEmpField(f as any, currentEmployee[f]));
                if (hasError) {
                    setFormTab(tab);
                    break;
                }
            }
            return;
        }
        setIsLoading(true);
        try {
            const url = isEditing ? `${API_BASE}/${currentEmployee.id}` : API_BASE;
            const method = isEditing ? "PUT" : "POST";
            // Build payload with only fields the backend Employee entity accepts
            const payload: Record<string, any> = {
                name: currentEmployee.name,
                designation: currentEmployee.designation,
                email: currentEmployee.email,
                phone: currentEmployee.phone,
                additionalPhones: currentEmployee.additionalPhones || null,
                salary: currentEmployee.salary,
                salaryDay: currentEmployee.salaryDay,
                monthlyLeaveThreshold: currentEmployee.monthlyLeaveThreshold ?? 4,
                joinDate: currentEmployee.joinDate || null,
                status: currentEmployee.status,
                aadharNumber: currentEmployee.aadharNumber || null,
                address: currentEmployee.address || null,
                city: currentEmployee.city || null,
                state: currentEmployee.state || null,
                pincode: currentEmployee.pincode || null,
                photoUrl: currentEmployee.photoUrl || null,
                bankAccountNumber: currentEmployee.bankAccountNumber || null,
                bankName: currentEmployee.bankName || null,
                bankIfsc: currentEmployee.bankIfsc || null,
                bankBranch: currentEmployee.bankBranch || null,
                panNumber: currentEmployee.panNumber || null,
                department: currentEmployee.department || null,
                employeeCode: currentEmployee.employeeCode || null,
                emergencyContact: currentEmployee.emergencyContact || null,
                emergencyPhone: currentEmployee.emergencyPhone || null,
                bloodGroup: currentEmployee.bloodGroup || null,
                dateOfBirth: currentEmployee.dateOfBirth || null,
                gender: currentEmployee.gender || null,
                maritalStatus: currentEmployee.maritalStatus || null,
                aadharDocUrl: currentEmployee.aadharDocUrl || null,
                panDocUrl: currentEmployee.panDocUrl || null,
                terminationDate: currentEmployee.terminationDate || null,
            };
            if (isEditing) payload.id = currentEmployee.id;
            const res = await fetch(url, {
                method,
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(payload),
            });
            if (!res.ok) {
                const err = await res.json().catch(() => null);
                setApiError(err?.error || `Failed to save employee (${res.status})`);
                return;
            }
            onSaved();
        } catch (error) {
            setApiError("Failed to save employee");
        } finally {
            setIsLoading(false);
        }
    };

    const handleDocUpload = async (
        uploadFn: (id: number, file: File) => Promise<any>,
        urlField: string,
        file: File,
    ) => {
        if (!currentEmployee?.id) return;
        const updated = await uploadFn(currentEmployee.id, file);
        setCurrentEmployee({ ...currentEmployee, [urlField]: (updated as any)[urlField] });
    };

    const tabs = [
        { key: "personal" as const, label: "Personal Info", icon: User },
        { key: "additional" as const, label: "Additional", icon: User },
        { key: "address" as const, label: "Address", icon: MapPin },
        { key: "bank" as const, label: "Bank Details", icon: Landmark },
        { key: "documents" as const, label: "Documents", icon: FileText },
    ];

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm">
            <div className="relative w-full max-w-2xl bg-card border border-border rounded-2xl shadow-2xl overflow-hidden">
                {/* Header */}
                <div className="flex items-center justify-between px-6 py-4 border-b border-border bg-muted/30">
                    <h3 className="text-lg font-semibold text-foreground">
                        {isEditing ? "Edit Employee" : "Add New Employee"}
                    </h3>
                    <button onClick={onClose} className="text-muted-foreground hover:text-foreground transition-colors">
                        <X className="w-5 h-5" />
                    </button>
                </div>

                {/* Tabs */}
                <div className="flex border-b border-border">
                    {tabs.map(({ key, label, icon: Icon }) => (
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
                                <input className={`${inputClass} ${inputErrorClass(empErrors.aadharNumber)}`} value={currentEmployee.aadharNumber} onChange={(e) => { set("aadharNumber", e.target.value); clearEmpError("aadharNumber"); }} />
                                <FieldError error={empErrors.aadharNumber} />
                            </div>
                            <div className="space-y-2">
                                <label className="text-sm font-medium">Salary *</label>
                                <input type="number" className={`${inputClass} ${inputErrorClass(empErrors.salary)}`} value={currentEmployee.salary} onChange={(e) => { set("salary", Number(e.target.value)); clearEmpError("salary"); }} />
                                <FieldError error={empErrors.salary} />
                            </div>
                            <div className="space-y-2">
                                <label className="text-sm font-medium">Salary Day</label>
                                <select className={inputClass} value={currentEmployee.salaryDay || 1} onChange={(e) => set("salaryDay", Number(e.target.value))}>
                                    {Array.from({ length: 31 }, (_, i) => i + 1).map((d) => (
                                        <option key={d} value={d}>{d}{d === 1 ? "st" : d === 2 ? "nd" : d === 3 ? "rd" : "th"} of every month</option>
                                    ))}
                                </select>
                            </div>
                            <div className="space-y-2">
                                <label className="text-sm font-medium">Monthly Leave Threshold</label>
                                <input type="number" min={0} max={31} className={inputClass} value={currentEmployee.monthlyLeaveThreshold ?? 4} onChange={(e) => set("monthlyLeaveThreshold", Number(e.target.value))} />
                                <p className="text-[10px] text-muted-foreground">Days/month before LOP kicks in</p>
                            </div>
                            <div className="space-y-2">
                                <label className="text-sm font-medium">Join Date *</label>
                                <input type="date" className={`${inputClass} ${inputErrorClass(empErrors.joinDate)}`} value={currentEmployee.joinDate} onChange={(e) => { set("joinDate", e.target.value); clearEmpError("joinDate"); }} />
                                <FieldError error={empErrors.joinDate} />
                            </div>
                            <div className="space-y-2">
                                <label className="text-sm font-medium">Status</label>
                                <select className={inputClass} value={currentEmployee.status} onChange={(e) => set("status", e.target.value)}>
                                    <option value="ACTIVE">Active</option>
                                    <option value="INACTIVE">Inactive</option>
                                </select>
                            </div>
                        </div>
                    )}

                    {formTab === "additional" && (
                        <div className="grid grid-cols-2 gap-4">
                            <div className="space-y-2">
                                <label className="text-sm font-medium">Employee Code</label>
                                <input className={`${inputClass} ${inputErrorClass(empErrors.employeeCode)}`} value={currentEmployee.employeeCode} onChange={(e) => { set("employeeCode", e.target.value); clearEmpError("employeeCode"); }} />
                                <FieldError error={empErrors.employeeCode} />
                            </div>
                            <div className="space-y-2"><label className="text-sm font-medium">Department</label><input className={inputClass} value={currentEmployee.department} onChange={(e) => set("department", e.target.value)} /></div>
                            <div className="space-y-2">
                                <label className="text-sm font-medium">PAN Number</label>
                                <input className={`${inputClass} ${inputErrorClass(empErrors.panNumber)}`} value={currentEmployee.panNumber} onChange={(e) => { set("panNumber", e.target.value.toUpperCase()); clearEmpError("panNumber"); }} placeholder="AAAAA0000A" />
                                <FieldError error={empErrors.panNumber} />
                            </div>
                            <div className="space-y-2">
                                <label className="text-sm font-medium">Date of Birth</label>
                                <input type="date" className={`${inputClass} ${inputErrorClass(empErrors.dateOfBirth)}`} value={currentEmployee.dateOfBirth} onChange={(e) => { set("dateOfBirth", e.target.value); clearEmpError("dateOfBirth"); }} />
                                <FieldError error={empErrors.dateOfBirth} />
                            </div>
                            <div className="space-y-2">
                                <label className="text-sm font-medium">Gender</label>
                                <select className={inputClass} value={currentEmployee.gender} onChange={(e) => set("gender", e.target.value)}>
                                    <option value="">Select</option><option value="Male">Male</option><option value="Female">Female</option><option value="Other">Other</option>
                                </select>
                            </div>
                            <div className="space-y-2">
                                <label className="text-sm font-medium">Marital Status</label>
                                <select className={inputClass} value={currentEmployee.maritalStatus} onChange={(e) => set("maritalStatus", e.target.value)}>
                                    <option value="">Select</option><option value="Single">Single</option><option value="Married">Married</option><option value="Divorced">Divorced</option><option value="Widowed">Widowed</option>
                                </select>
                            </div>
                            <div className="space-y-2">
                                <label className="text-sm font-medium">Blood Group</label>
                                <select className={inputClass} value={currentEmployee.bloodGroup} onChange={(e) => set("bloodGroup", e.target.value)}>
                                    <option value="">Select</option>
                                    {["A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"].map(bg => (<option key={bg} value={bg}>{bg}</option>))}
                                </select>
                            </div>
                            <div className="space-y-2"><label className="text-sm font-medium">Emergency Contact</label><input className={inputClass} value={currentEmployee.emergencyContact} onChange={(e) => set("emergencyContact", e.target.value)} /></div>
                            <div className="space-y-2">
                                <label className="text-sm font-medium">Emergency Phone</label>
                                <input className={`${inputClass} ${inputErrorClass(empErrors.emergencyPhone)}`} value={currentEmployee.emergencyPhone} onChange={(e) => { set("emergencyPhone", e.target.value); clearEmpError("emergencyPhone"); }} />
                                <FieldError error={empErrors.emergencyPhone} />
                            </div>
                        </div>
                    )}

                    {formTab === "address" && (
                        <div className="grid grid-cols-2 gap-4">
                            <div className="space-y-2 col-span-2">
                                <label className="text-sm font-medium">Address</label>
                                <textarea rows={3} className="flex w-full rounded-md border border-input bg-background px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/50" value={currentEmployee.address} onChange={(e) => set("address", e.target.value)} />
                            </div>
                            <div className="space-y-2"><label className="text-sm font-medium">City</label><input className={inputClass} value={currentEmployee.city} onChange={(e) => set("city", e.target.value)} /></div>
                            <div className="space-y-2"><label className="text-sm font-medium">State</label><input className={inputClass} value={currentEmployee.state} onChange={(e) => set("state", e.target.value)} /></div>
                            <div className="space-y-2">
                                <label className="text-sm font-medium">Pincode</label>
                                <input className={`${inputClass} ${inputErrorClass(empErrors.pincode)}`} value={currentEmployee.pincode} onChange={(e) => { set("pincode", e.target.value); clearEmpError("pincode"); }} placeholder="600001" />
                                <FieldError error={empErrors.pincode} />
                            </div>
                        </div>
                    )}

                    {formTab === "bank" && (
                        <div className="grid grid-cols-2 gap-4">
                            <div className="space-y-2"><label className="text-sm font-medium">Bank Name</label><input className={inputClass} value={currentEmployee.bankName} onChange={(e) => set("bankName", e.target.value)} /></div>
                            <div className="space-y-2">
                                <label className="text-sm font-medium">Account Number</label>
                                <input className={`${inputClass} ${inputErrorClass(empErrors.bankAccountNumber)}`} value={currentEmployee.bankAccountNumber} onChange={(e) => { set("bankAccountNumber", e.target.value); clearEmpError("bankAccountNumber"); }} />
                                <FieldError error={empErrors.bankAccountNumber} />
                            </div>
                            <div className="space-y-2">
                                <label className="text-sm font-medium">IFSC Code</label>
                                <input className={`${inputClass} ${inputErrorClass(empErrors.bankIfsc)}`} value={currentEmployee.bankIfsc} onChange={(e) => { set("bankIfsc", e.target.value.toUpperCase()); clearEmpError("bankIfsc"); }} placeholder="SBIN0001234" />
                                <FieldError error={empErrors.bankIfsc} />
                            </div>
                            <div className="space-y-2"><label className="text-sm font-medium">Branch</label><input className={inputClass} value={currentEmployee.bankBranch} onChange={(e) => set("bankBranch", e.target.value)} /></div>
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
                                <DocumentUploadField
                                    id="photo-upload"
                                    label="Employee Photo"
                                    icon={Camera}
                                    accept="image/jpeg,image/png,image/webp"
                                    hint="JPG, PNG or WebP. Max 5MB."
                                    employeeId={currentEmployee.id}
                                    employeeName={currentEmployee.name}
                                    currentUrl={currentEmployee.photoUrl}
                                    variant="photo"
                                    onUpload={(file) => handleDocUpload(uploadEmployeePhoto, "photoUrl", file)}
                                />
                                <DocumentUploadField
                                    id="aadhar-upload"
                                    label="Aadhar Document"
                                    icon={FileText}
                                    accept="image/jpeg,image/png,image/webp,application/pdf"
                                    hint="JPG, PNG, WebP or PDF. Max 10MB."
                                    employeeId={currentEmployee.id}
                                    employeeName={currentEmployee.name}
                                    currentUrl={currentEmployee.aadharDocUrl}
                                    variant="document"
                                    onUpload={(file) => handleDocUpload(uploadEmployeeAadharDoc, "aadharDocUrl", file)}
                                />
                                <DocumentUploadField
                                    id="pan-upload"
                                    label="PAN Document"
                                    icon={FileText}
                                    accept="image/jpeg,image/png,image/webp,application/pdf"
                                    hint="JPG, PNG, WebP or PDF. Max 10MB."
                                    employeeId={currentEmployee.id}
                                    employeeName={currentEmployee.name}
                                    currentUrl={currentEmployee.panDocUrl}
                                    variant="document"
                                    onUpload={(file) => handleDocUpload(uploadEmployeePanDoc, "panDocUrl", file)}
                                />
                            </div>
                        )
                    )}

                    {/* Footer */}
                    <div className="flex justify-end gap-2 mt-6 pt-4 border-t border-border">
                        <button type="button" onClick={onClose} className="px-4 py-2 text-sm font-medium border border-border rounded-md hover:bg-muted transition-colors">Cancel</button>
                        <button type="submit" disabled={isLoading} className="px-4 py-2 text-sm font-medium bg-primary text-primary-foreground rounded-md hover:bg-primary/90 disabled:opacity-50 transition-colors">
                            {isLoading ? "Saving..." : "Save"}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
}
