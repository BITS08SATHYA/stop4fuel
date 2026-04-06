"use client";

import { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import { Building2, Save, ArrowLeft, Pencil, Loader2, Upload, Image } from "lucide-react";
import { API_BASE_URL } from "@/lib/api/station";
import { fetchWithAuth } from "@/lib/api/fetch-with-auth";
import { CompanyDocuments } from "./company-documents";
import { PermissionGate } from "@/components/permission-gate";

interface Company {
    id?: number;
    name: string;
    openDate: string;
    sapCode: string;
    gstNo: string;
    site: string;
    type: string;
    address: string;
    phone: string;
    email: string;
    logoUrl: string;
    ownerId: number | null;
    ownerName: string;
}

interface UserOption {
    id: number;
    name: string;
    role: string;
}

const emptyCompany: Company = {
    name: "",
    openDate: "",
    sapCode: "",
    gstNo: "",
    site: "",
    type: "",
    address: "",
    phone: "",
    email: "",
    logoUrl: "",
    ownerId: null,
    ownerName: "",
};

interface CompanyDetailPageProps {
    companyId?: number;
    initialEditMode?: boolean;
}

export function CompanyDetailPage({ companyId, initialEditMode = false }: CompanyDetailPageProps) {
    const router = useRouter();
    const isNew = !companyId;
    const [editing, setEditing] = useState(isNew || initialEditMode);
    const [loading, setLoading] = useState(!isNew);
    const [saving, setSaving] = useState(false);
    const [company, setCompany] = useState<Company>(emptyCompany);
    const [error, setError] = useState<string | null>(null);
    const [users, setUsers] = useState<UserOption[]>([]);
    const [uploadingLogo, setUploadingLogo] = useState(false);
    const [logoPreviewUrl, setLogoPreviewUrl] = useState<string | null>(null);

    useEffect(() => {
        if (companyId) {
            fetchCompany(companyId);
        }
        fetchUsers();
    }, [companyId]);

    const fetchCompany = async (id: number) => {
        try {
            setLoading(true);
            const res = await fetchWithAuth(`${API_BASE_URL}/companies/${id}`);
            if (!res.ok) throw new Error("Failed to fetch company");
            const data = await res.json();
            setCompany(data);
        } catch (err) {
            console.error(err);
            setError("Failed to load company details");
        } finally {
            setLoading(false);
        }
    };

    const fetchUsers = async () => {
        try {
            const res = await fetchWithAuth(`${API_BASE_URL}/admin/users`);
            if (res.ok) {
                const data = await res.json();
                setUsers(data.map((u: any) => ({ id: u.id, name: u.name, role: u.role })));
            }
        } catch (e) {
            console.error("Failed to load users", e);
        }
    };

    const handleLogoUpload = async (file: File) => {
        if (!company.id) return;
        setUploadingLogo(true);
        try {
            const formData = new FormData();
            formData.append("file", file);
            const res = await fetchWithAuth(`${API_BASE_URL}/companies/${company.id}/logo`, {
                method: "POST",
                body: formData,
            });
            if (!res.ok) throw new Error("Failed to upload logo");
            const updated = await res.json();
            setCompany(updated);
            // Fetch fresh logo URL
            const urlRes = await fetchWithAuth(`${API_BASE_URL}/companies/${company.id}/logo-url`);
            if (urlRes.ok) {
                const { url } = await urlRes.json();
                setLogoPreviewUrl(url || null);
            }
        } catch (e) {
            console.error("Failed to upload logo", e);
            setError("Failed to upload logo");
        } finally {
            setUploadingLogo(false);
        }
    };

    // Load logo preview URL when company loads
    useEffect(() => {
        if (company.id && company.logoUrl) {
            fetchWithAuth(`${API_BASE_URL}/companies/${company.id}/logo-url`)
                .then(res => res.ok ? res.json() : null)
                .then(data => { if (data?.url) setLogoPreviewUrl(data.url); })
                .catch(() => {});
        }
    }, [company.id, company.logoUrl]);

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setSaving(true);
        setError(null);

        try {
            const ownerParam = company.ownerId ? `?ownerId=${company.ownerId}` : "";
            const url = company.id
                ? `${API_BASE_URL}/companies/${company.id}${ownerParam}`
                : `${API_BASE_URL}/companies${ownerParam}`;
            const method = company.id ? "PUT" : "POST";

            const res = await fetchWithAuth(url, {
                method,
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(company),
            });

            if (!res.ok) throw new Error("Failed to save company");
            const saved = await res.json();
            setCompany(saved);
            setEditing(false);

            if (isNew) {
                router.replace(`/company/${saved.id}`);
            }
        } catch (err) {
            console.error(err);
            setError("Failed to save company details. Please try again.");
        } finally {
            setSaving(false);
        }
    };

    const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => {
        const { id, value } = e.target;
        setCompany((prev) => ({ ...prev, [id]: value }));
    };

    const inputClass = "flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50";

    if (loading) {
        return (
            <div className="flex items-center justify-center h-64">
                <Loader2 className="w-8 h-8 animate-spin text-muted-foreground" />
            </div>
        );
    }

    return (
        <div className="p-6 bg-background min-h-screen">
            <div className="max-w-5xl mx-auto space-y-6">
                {/* Header */}
                <div className="flex items-center justify-between">
                    <div className="flex items-center gap-4">
                        <button
                            onClick={() => router.push("/company")}
                            className="p-2 rounded-lg hover:bg-muted transition-colors"
                        >
                            <ArrowLeft className="w-5 h-5" />
                        </button>
                        <div>
                            <h1 className="text-2xl font-bold tracking-tight">
                                {isNew ? "New Company" : company.name || "Company Details"}
                            </h1>
                            <p className="text-muted-foreground text-sm">
                                {isNew ? "Create a new company profile" : "Company profile and documents"}
                            </p>
                        </div>
                    </div>
                    <div className="flex items-center gap-2">
                        {!isNew && !editing && (
                            <PermissionGate permission="SETTINGS_UPDATE">
                                <button
                                    onClick={() => setEditing(true)}
                                    className="inline-flex items-center gap-2 px-4 py-2 text-sm rounded-lg border border-border hover:bg-muted transition-colors"
                                >
                                    <Pencil className="w-4 h-4" />
                                    Edit
                                </button>
                            </PermissionGate>
                        )}
                        <div className="p-2 bg-primary/10 rounded-full text-primary">
                            <Building2 className="w-5 h-5" />
                        </div>
                    </div>
                </div>

                {error && (
                    <div className="p-4 text-sm text-red-500 bg-red-50 dark:bg-red-950/20 rounded-md border border-red-200 dark:border-red-800">
                        {error}
                    </div>
                )}

                {/* Company Details Card */}
                <div className="rounded-xl border bg-card text-card-foreground shadow-sm">
                    <div className="px-6 py-4 border-b border-border bg-muted/30">
                        <h2 className="text-base font-semibold">Company Information</h2>
                    </div>
                    <div className="p-6">
                        <form onSubmit={handleSubmit}>
                            <div className="grid gap-5 md:grid-cols-2">
                                <div className="space-y-1.5">
                                    <label htmlFor="name" className="text-sm font-medium">Company Name</label>
                                    {editing ? (
                                        <input id="name" value={company.name} onChange={handleChange} className={inputClass} placeholder="Enter company name" required />
                                    ) : (
                                        <p className="text-sm py-2">{company.name || "—"}</p>
                                    )}
                                </div>

                                <div className="space-y-1.5">
                                    <label htmlFor="type" className="text-sm font-medium">Type</label>
                                    {editing ? (
                                        <select id="type" value={company.type} onChange={handleChange} className={inputClass}>
                                            <option value="">Select type</option>
                                            <option value="COCO">COCO (Company Owned Company Operated)</option>
                                            <option value="CODO">CODO (Company Owned Dealer Operated)</option>
                                            <option value="DODO">DODO (Dealer Owned Dealer Operated)</option>
                                        </select>
                                    ) : (
                                        <p className="text-sm py-2">{company.type || "—"}</p>
                                    )}
                                </div>

                                <div className="space-y-1.5">
                                    <label htmlFor="openDate" className="text-sm font-medium">Open Date</label>
                                    {editing ? (
                                        <input id="openDate" type="date" value={company.openDate} onChange={handleChange} className={inputClass} />
                                    ) : (
                                        <p className="text-sm py-2">
                                            {company.openDate ? new Date(company.openDate).toLocaleDateString("en-IN") : "—"}
                                        </p>
                                    )}
                                </div>

                                <div className="space-y-1.5">
                                    <label htmlFor="sapCode" className="text-sm font-medium">SAP Code</label>
                                    {editing ? (
                                        <input id="sapCode" value={company.sapCode} onChange={handleChange} className={inputClass} placeholder="Enter SAP code" />
                                    ) : (
                                        <p className="text-sm py-2">{company.sapCode || "—"}</p>
                                    )}
                                </div>

                                <div className="space-y-1.5">
                                    <label htmlFor="gstNo" className="text-sm font-medium">GST No</label>
                                    {editing ? (
                                        <input id="gstNo" value={company.gstNo} onChange={handleChange} className={inputClass} placeholder="Enter GST number" />
                                    ) : (
                                        <p className="text-sm py-2">{company.gstNo || "—"}</p>
                                    )}
                                </div>

                                <div className="space-y-1.5">
                                    <label htmlFor="site" className="text-sm font-medium">Site</label>
                                    {editing ? (
                                        <input id="site" value={company.site} onChange={handleChange} className={inputClass} placeholder="Enter site location" />
                                    ) : (
                                        <p className="text-sm py-2">{company.site || "—"}</p>
                                    )}
                                </div>

                                <div className="space-y-1.5">
                                    <label htmlFor="phone" className="text-sm font-medium">Phone</label>
                                    {editing ? (
                                        <input id="phone" value={company.phone} onChange={handleChange} className={inputClass} placeholder="Enter phone number" />
                                    ) : (
                                        <p className="text-sm py-2">{company.phone || "—"}</p>
                                    )}
                                </div>

                                <div className="space-y-1.5">
                                    <label htmlFor="email" className="text-sm font-medium">Email</label>
                                    {editing ? (
                                        <input id="email" type="email" value={company.email} onChange={handleChange} className={inputClass} placeholder="Enter email address" />
                                    ) : (
                                        <p className="text-sm py-2">{company.email || "—"}</p>
                                    )}
                                </div>

                                <div className="space-y-1.5">
                                    <label htmlFor="ownerId" className="text-sm font-medium">Owner</label>
                                    {editing ? (
                                        <select
                                            id="ownerId"
                                            value={company.ownerId ?? ""}
                                            onChange={(e) => setCompany(prev => ({ ...prev, ownerId: e.target.value ? Number(e.target.value) : null }))}
                                            className={inputClass}
                                        >
                                            <option value="">Select owner</option>
                                            {users.map(u => (
                                                <option key={u.id} value={u.id}>{u.name} ({u.role})</option>
                                            ))}
                                        </select>
                                    ) : (
                                        <p className="text-sm py-2">{company.ownerName || "—"}</p>
                                    )}
                                </div>

                                <div className="col-span-2 space-y-1.5">
                                    <label htmlFor="address" className="text-sm font-medium">Address</label>
                                    {editing ? (
                                        <textarea
                                            id="address"
                                            value={company.address}
                                            onChange={handleChange}
                                            className="flex min-h-[80px] w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
                                            placeholder="Enter full address"
                                            rows={3}
                                        />
                                    ) : (
                                        <p className="text-sm py-2">{company.address || "—"}</p>
                                    )}
                                </div>
                            </div>

                            {/* Logo Section */}
                            {company.id && (
                                <div className="mt-6 pt-4 border-t border-border">
                                    <label className="text-sm font-medium block mb-2">Company Logo</label>
                                    <div className="flex items-center gap-4">
                                        {logoPreviewUrl ? (
                                            <img src={logoPreviewUrl} alt="Company Logo" className="w-20 h-20 object-contain rounded-lg border border-border bg-white" />
                                        ) : (
                                            <div className="w-20 h-20 rounded-lg border border-dashed border-border flex items-center justify-center bg-muted/30">
                                                <Image className="w-8 h-8 text-muted-foreground/50" />
                                            </div>
                                        )}
                                        {editing && (
                                            <label className="inline-flex items-center gap-2 px-4 py-2 text-sm rounded-lg border border-border hover:bg-muted transition-colors cursor-pointer">
                                                {uploadingLogo ? <Loader2 className="w-4 h-4 animate-spin" /> : <Upload className="w-4 h-4" />}
                                                {uploadingLogo ? "Uploading..." : "Upload Logo"}
                                                <input
                                                    type="file"
                                                    accept="image/png,image/jpeg,image/webp"
                                                    className="hidden"
                                                    onChange={(e) => {
                                                        const file = e.target.files?.[0];
                                                        if (file) handleLogoUpload(file);
                                                    }}
                                                    disabled={uploadingLogo}
                                                />
                                            </label>
                                        )}
                                    </div>
                                </div>
                            )}

                            {editing && (
                                <div className="flex justify-end gap-3 mt-6 pt-4 border-t border-border">
                                    {!isNew && (
                                        <button
                                            type="button"
                                            onClick={() => {
                                                setEditing(false);
                                                if (companyId) fetchCompany(companyId);
                                            }}
                                            className="px-4 py-2 text-sm rounded-lg border border-border hover:bg-muted transition-colors"
                                        >
                                            Cancel
                                        </button>
                                    )}
                                    <button
                                        type="submit"
                                        disabled={saving}
                                        className="inline-flex items-center gap-2 px-4 py-2 text-sm rounded-lg bg-primary text-primary-foreground hover:bg-primary/90 transition-colors disabled:opacity-50"
                                    >
                                        {saving ? (
                                            <Loader2 className="w-4 h-4 animate-spin" />
                                        ) : (
                                            <Save className="w-4 h-4" />
                                        )}
                                        {saving ? "Saving..." : isNew ? "Create Company" : "Save Changes"}
                                    </button>
                                </div>
                            )}
                        </form>
                    </div>
                </div>

                {/* Documents Section - only show for existing companies */}
                {company.id && <CompanyDocuments companyId={company.id} />}
            </div>
        </div>
    );
}
