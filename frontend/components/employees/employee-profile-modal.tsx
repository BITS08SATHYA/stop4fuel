"use client";

import { useState, useEffect } from "react";
import {
    Plus, User, MapPin, Landmark, History, Banknote, ChevronRight,
    FileText, ExternalLink, IndianRupee, Loader2, Upload,
} from "lucide-react";
import { Modal } from "@/components/ui/modal";
import { GlassCard } from "@/components/ui/glass-card";
import { EmployeeAvatar } from "@/components/ui/employee-avatar";
import { useFormValidation, required, min } from "@/lib/validation";
import { FieldError, inputErrorClass } from "@/components/ui/field-error";
import { getEmployeeFileUrl } from "@/lib/api/station";
import type { Employee, SalaryHistory, EmployeeAdvance } from "./types";
import {
    inputClass, formatRupees, advanceTypeBadge, advanceStatusBadge,
    formatLabel, API_BASE,
} from "./types";
import { fetchWithAuth } from "@/lib/api/fetch-with-auth";

interface EmployeeProfileModalProps {
    employee: Employee;
    onClose: () => void;
}

export function EmployeeProfileModal({ employee: initialEmployee, onClose }: EmployeeProfileModalProps) {
    const [employee, setEmployee] = useState<Employee>(initialEmployee);
    const [profileTab, setProfileTab] = useState<"overview" | "salary" | "advances">("overview");
    const [salaryHistory, setSalaryHistory] = useState<SalaryHistory[]>([]);
    const [advances, setAdvances] = useState<EmployeeAdvance[]>([]);
    const [showSalaryForm, setShowSalaryForm] = useState(false);
    const [showAdvanceForm, setShowAdvanceForm] = useState(false);
    const [salaryRevision, setSalaryRevision] = useState({ newSalary: "", effectiveDate: "", reason: "" });
    const [newAdvance, setNewAdvance] = useState({ amount: "", advanceDate: "", advanceType: "SALARY_ADVANCE", remarks: "" });

    const { errors: salaryErrors, validate: validateSalary, clearError: clearSalaryError, clearAllErrors: clearAllSalaryErrors } = useFormValidation({
        newSalary: [required("New salary is required"), min(0)],
        effectiveDate: [required("Effective date is required")],
    });

    const { errors: advanceErrors, validate: validateAdvance, clearError: clearAdvanceError, clearAllErrors: clearAllAdvanceErrors } = useFormValidation({
        amount: [required("Amount is required"), min(1, "Amount must be at least 1")],
        advanceDate: [required("Date is required")],
    });

    // Re-fetch employee to get fresh data (including doc URLs)
    const fetchEmployee = async () => {
        try {
            const res = await fetchWithAuth(`${API_BASE}/${initialEmployee.id}`);
            if (res.ok) setEmployee(await res.json());
        } catch (error) { console.error("Failed to fetch employee", error); }
    };

    useEffect(() => {
        fetchEmployee();
        fetchSalaryHistory();
        fetchAdvances();
    }, [initialEmployee.id]);

    const fetchSalaryHistory = async () => {
        try {
            const res = await fetchWithAuth(`${API_BASE}/${employee.id}/salary-history`);
            if (res.ok) setSalaryHistory(await res.json());
        } catch (error) { console.error("Failed to fetch salary history", error); }
    };

    const fetchAdvances = async () => {
        try {
            const res = await fetchWithAuth(`${API_BASE}/${employee.id}/advances`);
            if (res.ok) setAdvances(await res.json());
        } catch (error) { console.error("Failed to fetch advances", error); }
    };

    const handleSalaryRevision = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!validateSalary(salaryRevision)) return;
        try {
            const res = await fetchWithAuth(`${API_BASE}/${employee.id}/salary-revision`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ newSalary: Number(salaryRevision.newSalary), effectiveDate: salaryRevision.effectiveDate, reason: salaryRevision.reason }),
            });
            if (res.ok) {
                setShowSalaryForm(false);
                setSalaryRevision({ newSalary: "", effectiveDate: "", reason: "" });
                fetchSalaryHistory();
            }
        } catch (error) { console.error("Failed to submit salary revision", error); }
    };

    const handleAddAdvance = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!validateAdvance(newAdvance)) return;
        try {
            const res = await fetchWithAuth(`${API_BASE}/${employee.id}/advances`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ amount: Number(newAdvance.amount), advanceDate: newAdvance.advanceDate, advanceType: newAdvance.advanceType, remarks: newAdvance.remarks }),
            });
            if (res.ok) {
                setShowAdvanceForm(false);
                setNewAdvance({ amount: "", advanceDate: "", advanceType: "SALARY_ADVANCE", remarks: "" });
                fetchAdvances();
            }
        } catch (error) { console.error("Failed to record advance", error); }
    };

    const handleAdvanceStatusChange = async (advanceId: number, status: string) => {
        try {
            await fetchWithAuth(`${API_BASE}/advances/${advanceId}/status?status=${status}`, { method: "PATCH" });
            fetchAdvances();
        } catch (error) { console.error("Failed to update advance status", error); }
    };

    return (
        <Modal isOpen onClose={onClose} title={employee.name}>
            <div className="space-y-6">
                {/* Profile Header */}
                <div className="flex items-start gap-5 -mt-2">
                    <EmployeeAvatar employeeId={employee.id} name={employee.name} photoUrl={employee.photoUrl} size="lg" />
                    <div className="flex-1 min-w-0">
                        <h3 className="text-xl font-bold truncate">{employee.name}</h3>
                        <p className="text-sm text-muted-foreground">{employee.designation}{employee.department ? ` - ${employee.department}` : ""}</p>
                        {employee.employeeCode && <p className="text-xs text-muted-foreground mt-0.5">ID: {employee.employeeCode}</p>}
                        <div className="flex items-center gap-3 mt-2">
                            <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${employee.status === "Active" ? "bg-green-100 text-green-800 dark:bg-green-900/40 dark:text-green-300" : "bg-gray-100 text-gray-800 dark:bg-gray-900/40 dark:text-gray-300"}`}>
                                {employee.status}
                            </span>
                            <span className="text-sm text-muted-foreground">Joined {employee.joinDate}</span>
                            <span className="text-sm font-semibold">{formatRupees(employee.salary)}</span>
                        </div>
                    </div>
                </div>

                {/* Profile Tabs */}
                <div className="flex border-b border-border">
                    {([
                        { key: "overview" as const, label: "Overview", icon: User },
                        { key: "salary" as const, label: "Salary History", icon: History },
                        { key: "advances" as const, label: "Advances", icon: Banknote },
                    ]).map(({ key, label, icon: Icon }) => (
                        <button
                            key={key}
                            onClick={() => setProfileTab(key)}
                            className={`flex items-center gap-2 px-5 py-3 text-sm font-medium border-b-2 transition-colors ${profileTab === key ? "border-primary text-primary" : "border-transparent text-muted-foreground hover:text-foreground"}`}
                        >
                            <Icon className="w-4 h-4" />{label}
                        </button>
                    ))}
                </div>

                {/* Overview Tab */}
                {profileTab === "overview" && (
                    <div className="space-y-4">
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                            <GlassCard>
                                <h4 className="text-sm font-semibold text-muted-foreground mb-3 flex items-center gap-2"><User className="w-4 h-4" /> Personal Information</h4>
                                <dl className="space-y-2 text-sm">
                                    <DetailRow label="Email" value={employee.email} />
                                    <DetailRow label="Phone" value={employee.phone} />
                                    {employee.additionalPhones && <DetailRow label="Other Phones" value={employee.additionalPhones} />}
                                    {employee.aadharNumber && <DetailRow label="Aadhar" value={employee.aadharNumber} />}
                                    {employee.panNumber && <DetailRow label="PAN" value={employee.panNumber} />}
                                </dl>
                            </GlassCard>
                            <GlassCard>
                                <h4 className="text-sm font-semibold text-muted-foreground mb-3 flex items-center gap-2"><User className="w-4 h-4" /> Additional Info</h4>
                                <dl className="space-y-2 text-sm">
                                    {employee.dateOfBirth && <DetailRow label="Date of Birth" value={employee.dateOfBirth} />}
                                    {employee.gender && <DetailRow label="Gender" value={employee.gender} />}
                                    {employee.maritalStatus && <DetailRow label="Marital Status" value={employee.maritalStatus} />}
                                    {employee.bloodGroup && <DetailRow label="Blood Group" value={employee.bloodGroup} />}
                                    {employee.emergencyContact && <DetailRow label="Emergency Contact" value={employee.emergencyContact} />}
                                    {employee.emergencyPhone && <DetailRow label="Emergency Phone" value={employee.emergencyPhone} />}
                                    {!employee.dateOfBirth && !employee.gender && !employee.bloodGroup && <p className="text-muted-foreground text-xs">No additional info available.</p>}
                                </dl>
                            </GlassCard>
                            <GlassCard>
                                <h4 className="text-sm font-semibold text-muted-foreground mb-3 flex items-center gap-2"><MapPin className="w-4 h-4" /> Address</h4>
                                <dl className="space-y-2 text-sm">
                                    <DetailRow label="Address" value={employee.address || "-"} />
                                    <DetailRow label="City" value={employee.city || "-"} />
                                    <DetailRow label="State" value={employee.state || "-"} />
                                    <DetailRow label="Pincode" value={employee.pincode || "-"} />
                                </dl>
                            </GlassCard>
                            <GlassCard>
                                <h4 className="text-sm font-semibold text-muted-foreground mb-3 flex items-center gap-2"><Landmark className="w-4 h-4" /> Bank Details</h4>
                                <dl className="space-y-2 text-sm">
                                    <DetailRow label="Bank" value={employee.bankName || "-"} />
                                    <DetailRow label="Account No." value={employee.bankAccountNumber || "-"} />
                                    <DetailRow label="IFSC" value={employee.bankIfsc || "-"} />
                                    <DetailRow label="Branch" value={employee.bankBranch || "-"} />
                                </dl>
                            </GlassCard>
                        </div>
                        <GlassCard>
                            <h4 className="text-sm font-semibold text-muted-foreground mb-3 flex items-center gap-2"><FileText className="w-4 h-4" /> Documents</h4>
                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                                <DocumentCard label="Aadhar Document" hasFile={!!employee.aadharDocUrl} employeeId={employee.id} fileType="aadhar-doc" onUploaded={fetchEmployee} />
                                <DocumentCard label="PAN Document" hasFile={!!employee.panDocUrl} employeeId={employee.id} fileType="pan-doc" onUploaded={fetchEmployee} />
                            </div>
                        </GlassCard>
                    </div>
                )}

                {/* Salary History Tab */}
                {profileTab === "salary" && (
                    <div className="space-y-4">
                        <GlassCard className="flex items-center justify-between">
                            <div><p className="text-sm text-muted-foreground">Current Salary</p><p className="text-3xl font-bold">{formatRupees(employee.salary)}</p></div>
                            <IndianRupee className="w-10 h-10 text-primary/30" />
                        </GlassCard>
                        <div className="flex justify-end">
                            <button onClick={() => { clearAllSalaryErrors(); setShowSalaryForm(!showSalaryForm); }} className="inline-flex items-center gap-2 px-4 py-2 text-sm font-medium bg-primary text-primary-foreground rounded-md hover:bg-primary/90 transition-colors">
                                <Plus className="w-4 h-4" />Add Salary Revision
                            </button>
                        </div>
                        {showSalaryForm && (
                            <GlassCard>
                                <form onSubmit={handleSalaryRevision} className="grid grid-cols-3 gap-4">
                                    <div className="space-y-2">
                                        <label className="text-sm font-medium">New Salary *</label>
                                        <input type="number" className={`${inputClass} ${inputErrorClass(salaryErrors.newSalary)}`} value={salaryRevision.newSalary} onChange={(e) => { setSalaryRevision({ ...salaryRevision, newSalary: e.target.value }); clearSalaryError("newSalary"); }} />
                                        <FieldError error={salaryErrors.newSalary} />
                                    </div>
                                    <div className="space-y-2">
                                        <label className="text-sm font-medium">Effective Date *</label>
                                        <input type="date" className={`${inputClass} ${inputErrorClass(salaryErrors.effectiveDate)}`} value={salaryRevision.effectiveDate} onChange={(e) => { setSalaryRevision({ ...salaryRevision, effectiveDate: e.target.value }); clearSalaryError("effectiveDate"); }} />
                                        <FieldError error={salaryErrors.effectiveDate} />
                                    </div>
                                    <div className="space-y-2">
                                        <label className="text-sm font-medium">Reason</label>
                                        <input className={inputClass} value={salaryRevision.reason} onChange={(e) => setSalaryRevision({ ...salaryRevision, reason: e.target.value })} />
                                    </div>
                                    <div className="col-span-3 flex justify-end gap-2">
                                        <button type="button" onClick={() => setShowSalaryForm(false)} className="px-3 py-1.5 text-sm border border-border rounded-md hover:bg-muted transition-colors">Cancel</button>
                                        <button type="submit" className="px-3 py-1.5 text-sm bg-primary text-primary-foreground rounded-md hover:bg-primary/90 transition-colors">Save Revision</button>
                                    </div>
                                </form>
                            </GlassCard>
                        )}
                        <div className="rounded-md border">
                            <table className="w-full text-sm text-left">
                                <thead className="bg-muted/50 text-muted-foreground">
                                    <tr><th className="p-3 font-medium">Effective Date</th><th className="p-3 font-medium">Old Salary</th><th className="p-3 font-medium"></th><th className="p-3 font-medium">New Salary</th><th className="p-3 font-medium">Reason</th></tr>
                                </thead>
                                <tbody>
                                    {salaryHistory.length === 0 ? (
                                        <tr><td colSpan={5} className="p-4 text-center text-muted-foreground">No salary revisions recorded yet.</td></tr>
                                    ) : salaryHistory.map((sh) => (
                                        <tr key={sh.id} className="border-t hover:bg-muted/50">
                                            <td className="p-3">{sh.effectiveDate}</td>
                                            <td className="p-3">{formatRupees(sh.oldSalary)}</td>
                                            <td className="p-3 text-center"><ChevronRight className="w-4 h-4 text-muted-foreground inline" /></td>
                                            <td className="p-3 font-medium">{formatRupees(sh.newSalary)}</td>
                                            <td className="p-3 text-muted-foreground">{sh.reason}</td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    </div>
                )}

                {/* Advances Tab */}
                {profileTab === "advances" && (
                    <div className="space-y-4">
                        <div className="flex justify-end">
                            <button onClick={() => { clearAllAdvanceErrors(); setShowAdvanceForm(!showAdvanceForm); }} className="inline-flex items-center gap-2 px-4 py-2 text-sm font-medium bg-primary text-primary-foreground rounded-md hover:bg-primary/90 transition-colors">
                                <Plus className="w-4 h-4" />Record Advance
                            </button>
                        </div>
                        {showAdvanceForm && (
                            <GlassCard>
                                <form onSubmit={handleAddAdvance} className="grid grid-cols-2 gap-4">
                                    <div className="space-y-2">
                                        <label className="text-sm font-medium">Amount *</label>
                                        <input type="number" className={`${inputClass} ${inputErrorClass(advanceErrors.amount)}`} value={newAdvance.amount} onChange={(e) => { setNewAdvance({ ...newAdvance, amount: e.target.value }); clearAdvanceError("amount"); }} />
                                        <FieldError error={advanceErrors.amount} />
                                    </div>
                                    <div className="space-y-2">
                                        <label className="text-sm font-medium">Date *</label>
                                        <input type="date" className={`${inputClass} ${inputErrorClass(advanceErrors.advanceDate)}`} value={newAdvance.advanceDate} onChange={(e) => { setNewAdvance({ ...newAdvance, advanceDate: e.target.value }); clearAdvanceError("advanceDate"); }} />
                                        <FieldError error={advanceErrors.advanceDate} />
                                    </div>
                                    <div className="space-y-2">
                                        <label className="text-sm font-medium">Type *</label>
                                        <select className={inputClass} value={newAdvance.advanceType} onChange={(e) => setNewAdvance({ ...newAdvance, advanceType: e.target.value })}>
                                            <option value="SALARY_ADVANCE">Salary Advance</option><option value="HOME_ADVANCE">Home Advance</option><option value="NIGHT_ADVANCE">Night Advance</option>
                                        </select>
                                    </div>
                                    <div className="space-y-2">
                                        <label className="text-sm font-medium">Remarks</label>
                                        <input className={inputClass} value={newAdvance.remarks} onChange={(e) => setNewAdvance({ ...newAdvance, remarks: e.target.value })} />
                                    </div>
                                    <div className="col-span-2 flex justify-end gap-2">
                                        <button type="button" onClick={() => setShowAdvanceForm(false)} className="px-3 py-1.5 text-sm border border-border rounded-md hover:bg-muted transition-colors">Cancel</button>
                                        <button type="submit" className="px-3 py-1.5 text-sm bg-primary text-primary-foreground rounded-md hover:bg-primary/90 transition-colors">Save Advance</button>
                                    </div>
                                </form>
                            </GlassCard>
                        )}
                        <div className="rounded-md border">
                            <table className="w-full text-sm text-left">
                                <thead className="bg-muted/50 text-muted-foreground">
                                    <tr><th className="p-3 font-medium">Date</th><th className="p-3 font-medium">Type</th><th className="p-3 font-medium">Amount</th><th className="p-3 font-medium">Remarks</th><th className="p-3 font-medium">Status</th><th className="p-3 font-medium text-right">Action</th></tr>
                                </thead>
                                <tbody>
                                    {advances.length === 0 ? (
                                        <tr><td colSpan={6} className="p-4 text-center text-muted-foreground">No advances recorded yet.</td></tr>
                                    ) : advances.map((adv) => (
                                        <tr key={adv.id} className="border-t hover:bg-muted/50">
                                            <td className="p-3">{adv.advanceDate}</td>
                                            <td className="p-3"><span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${advanceTypeBadge[adv.advanceType] ?? "bg-gray-100 text-gray-800"}`}>{formatLabel(adv.advanceType)}</span></td>
                                            <td className="p-3 font-medium">{formatRupees(adv.amount)}</td>
                                            <td className="p-3 text-muted-foreground">{adv.remarks || "-"}</td>
                                            <td className="p-3"><span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${advanceStatusBadge[adv.status] ?? "bg-gray-100 text-gray-800"}`}>{adv.status}</span></td>
                                            <td className="p-3 text-right">
                                                {adv.status === "PENDING" && (
                                                    <select className="text-xs border border-border rounded-md px-2 py-1 bg-background focus:outline-none focus:ring-2 focus:ring-primary/50" defaultValue="" onChange={(e) => { if (e.target.value) { handleAdvanceStatusChange(adv.id, e.target.value); e.target.value = ""; } }}>
                                                        <option value="" disabled>Change...</option><option value="DEDUCTED">Mark Deducted</option><option value="WAIVED">Mark Waived</option>
                                                    </select>
                                                )}
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    </div>
                )}
            </div>
        </Modal>
    );
}

// ── Helpers ─────────────────────────────────────────────────────────────

function DetailRow({ label, value }: { label: string; value: React.ReactNode }) {
    return (
        <div className="flex justify-between">
            <dt className="text-muted-foreground">{label}</dt>
            <dd className="font-medium text-right">{value}</dd>
        </div>
    );
}

function DocumentCard({ label, hasFile, employeeId, fileType, onUploaded }: { label: string; hasFile: boolean; employeeId: number; fileType: string; onUploaded?: () => void }) {
    const [loading, setLoading] = useState(false);
    const [uploading, setUploading] = useState(false);
    const handleView = async () => {
        setLoading(true);
        try {
            const { url } = await getEmployeeFileUrl(employeeId, fileType);
            window.open(url, "_blank");
        } catch (error) { console.error("Failed to get file URL", error); }
        finally { setLoading(false); }
    };
    const handleUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
        const file = e.target.files?.[0];
        if (!file) return;
        setUploading(true);
        try {
            const formData = new FormData();
            formData.append("file", file);
            const uploadType = fileType === "aadhar-doc" ? "upload-aadhar-doc" : "upload-pan-doc";
            const res = await fetchWithAuth(`${API_BASE}/${employeeId}/${uploadType}`, { method: "POST", body: formData });
            if (res.ok) onUploaded?.();
            else console.error("Upload failed");
        } catch (error) { console.error("Failed to upload", error); }
        finally { setUploading(false); e.target.value = ""; }
    };
    return (
        <div className="flex items-center justify-between p-3 rounded-lg border border-border bg-muted/20">
            <div className="flex items-center gap-2">
                <FileText className={`w-5 h-5 ${hasFile ? "text-blue-500" : "text-muted-foreground/30"}`} />
                <span className="text-sm font-medium">{label}</span>
            </div>
            <div className="flex items-center gap-2">
                {hasFile && (
                    <button onClick={handleView} disabled={loading} className="inline-flex items-center gap-1 px-2 py-1 text-xs font-medium text-primary hover:underline disabled:opacity-50">
                        {loading ? <Loader2 className="w-3 h-3 animate-spin" /> : <ExternalLink className="w-3 h-3" />} View
                    </button>
                )}
                <label className="inline-flex items-center gap-1 px-2 py-1 text-xs font-medium text-muted-foreground hover:text-foreground cursor-pointer">
                    {uploading ? <Loader2 className="w-3 h-3 animate-spin" /> : <Upload className="w-3 h-3" />}
                    {hasFile ? "Replace" : "Upload"}
                    <input type="file" accept="image/*,.pdf" className="hidden" onChange={handleUpload} disabled={uploading} />
                </label>
            </div>
        </div>
    );
}
