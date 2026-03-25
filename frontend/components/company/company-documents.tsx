"use client";

import { useState, useEffect } from "react";
import {
    FileText, Plus, Upload, Trash2, Eye, Pencil, Loader2,
    AlertTriangle, Calendar, X, ExternalLink, CheckCircle2
} from "lucide-react";
import { API_BASE_URL } from "@/lib/api/station";
import { PermissionGate } from "@/components/permission-gate";

const DOCUMENT_TYPES = [
    { value: "PARTNERSHIP_DEED", label: "Partnership Deed" },
    { value: "GST_CERTIFICATE", label: "GST Certificate" },
    { value: "PAN_CARD", label: "PAN Card" },
    { value: "TRADE_LICENSE", label: "Trade License" },
    { value: "NOZZLE_CALIBRATION", label: "Nozzle Calibration Certificate" },
    { value: "WEIGHTS_MEASURES", label: "Weights & Measures License" },
    { value: "FIRE_NOC", label: "Fire NOC" },
    { value: "POLLUTION_CERTIFICATE", label: "Pollution Certificate" },
    { value: "EXPLOSIVE_LICENSE", label: "Explosive License" },
    { value: "INSURANCE", label: "Insurance Policy" },
    { value: "LAND_DOCUMENT", label: "Land / Lease Document" },
    { value: "OTHER", label: "Other" },
] as const;

interface CompanyDocument {
    id: number;
    documentType: string;
    documentName: string;
    description: string;
    fileUrl: string | null;
    fileName: string | null;
    expiryDate: string | null;
}

interface FormData {
    documentType: string;
    documentName: string;
    description: string;
    expiryDate: string;
}

const emptyForm: FormData = {
    documentType: "",
    documentName: "",
    description: "",
    expiryDate: "",
};

export function CompanyDocuments({ companyId }: { companyId: number }) {
    const [documents, setDocuments] = useState<CompanyDocument[]>([]);
    const [loading, setLoading] = useState(true);
    const [showForm, setShowForm] = useState(false);
    const [editingDoc, setEditingDoc] = useState<CompanyDocument | null>(null);
    const [form, setForm] = useState<FormData>(emptyForm);
    const [saving, setSaving] = useState(false);
    const [deleteId, setDeleteId] = useState<number | null>(null);
    const [deleting, setDeleting] = useState(false);
    const [uploadingId, setUploadingId] = useState<number | null>(null);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        fetchDocuments();
    }, [companyId]);

    const fetchDocuments = async () => {
        try {
            setLoading(true);
            const res = await fetch(`${API_BASE_URL}/companies/${companyId}/documents`);
            if (!res.ok) throw new Error("Failed to fetch documents");
            setDocuments(await res.json());
        } catch (err) {
            console.error(err);
            setError("Failed to load documents");
        } finally {
            setLoading(false);
        }
    };

    const handleSave = async (e: React.FormEvent) => {
        e.preventDefault();
        setSaving(true);
        setError(null);

        try {
            const url = editingDoc
                ? `${API_BASE_URL}/companies/${companyId}/documents/${editingDoc.id}`
                : `${API_BASE_URL}/companies/${companyId}/documents`;
            const method = editingDoc ? "PUT" : "POST";

            const res = await fetch(url, {
                method,
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(form),
            });

            if (!res.ok) throw new Error("Failed to save document");
            await fetchDocuments();
            closeForm();
        } catch (err) {
            console.error(err);
            setError("Failed to save document");
        } finally {
            setSaving(false);
        }
    };

    const handleUpload = async (docId: number, file: File) => {
        setUploadingId(docId);
        setError(null);
        try {
            const formData = new FormData();
            formData.append("file", file);
            const res = await fetch(`${API_BASE_URL}/companies/${companyId}/documents/${docId}/upload`, {
                method: "POST",
                body: formData,
            });
            if (!res.ok) {
                const errData = await res.json().catch(() => null);
                throw new Error(errData?.message || "Upload failed");
            }
            await fetchDocuments();
        } catch (err: any) {
            console.error(err);
            setError(err.message || "Failed to upload file");
        } finally {
            setUploadingId(null);
        }
    };

    const handleView = async (docId: number) => {
        try {
            const res = await fetch(`${API_BASE_URL}/companies/${companyId}/documents/${docId}/file-url`);
            if (!res.ok) throw new Error("Failed to get file URL");
            const { url } = await res.json();
            window.open(url, "_blank");
        } catch (err) {
            console.error(err);
            setError("Failed to open file");
        }
    };

    const handleDelete = async (id: number) => {
        setDeleting(true);
        try {
            const res = await fetch(`${API_BASE_URL}/companies/${companyId}/documents/${id}`, { method: "DELETE" });
            if (!res.ok) throw new Error("Failed to delete");
            setDocuments((prev) => prev.filter((d) => d.id !== id));
            setDeleteId(null);
        } catch (err) {
            console.error(err);
            setError("Failed to delete document");
        } finally {
            setDeleting(false);
        }
    };

    const openEdit = (doc: CompanyDocument) => {
        setEditingDoc(doc);
        setForm({
            documentType: doc.documentType,
            documentName: doc.documentName,
            description: doc.description || "",
            expiryDate: doc.expiryDate || "",
        });
        setShowForm(true);
    };

    const closeForm = () => {
        setShowForm(false);
        setEditingDoc(null);
        setForm(emptyForm);
    };

    const typeLabel = (val: string) =>
        DOCUMENT_TYPES.find((t) => t.value === val)?.label || val;

    const isExpired = (date: string | null) => {
        if (!date) return false;
        return new Date(date) < new Date();
    };

    const isExpiringSoon = (date: string | null) => {
        if (!date) return false;
        const d = new Date(date);
        const now = new Date();
        const thirtyDays = new Date(now.getTime() + 30 * 24 * 60 * 60 * 1000);
        return d > now && d <= thirtyDays;
    };

    const inputClass = "flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2";

    return (
        <div className="rounded-xl border bg-card text-card-foreground shadow-sm">
            <div className="px-6 py-4 border-b border-border bg-muted/30 flex items-center justify-between">
                <div className="flex items-center gap-2">
                    <FileText className="w-4 h-4 text-primary" />
                    <h2 className="text-base font-semibold">Documents & Certificates</h2>
                    <span className="text-xs text-muted-foreground">({documents.length})</span>
                </div>
                <PermissionGate permission="SETTINGS_MANAGE">
                    <button
                        onClick={() => { closeForm(); setShowForm(true); }}
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg bg-primary text-primary-foreground hover:bg-primary/90 transition-colors"
                    >
                        <Plus className="w-3.5 h-3.5" />
                        Add Document
                    </button>
                </PermissionGate>
            </div>

            {error && (
                <div className="mx-6 mt-4 p-3 text-sm text-red-500 bg-red-50 dark:bg-red-950/20 rounded-md border border-red-200 dark:border-red-800">
                    {error}
                    <button onClick={() => setError(null)} className="ml-2 text-red-400 hover:text-red-600">
                        <X className="w-3.5 h-3.5 inline" />
                    </button>
                </div>
            )}

            {/* Add / Edit Form */}
            {showForm && (
                <div className="mx-6 mt-4 p-4 rounded-lg border border-border bg-muted/20">
                    <h3 className="text-sm font-semibold mb-3">
                        {editingDoc ? "Edit Document" : "Add New Document"}
                    </h3>
                    <form onSubmit={handleSave}>
                        <div className="grid gap-4 md:grid-cols-2">
                            <div className="space-y-1.5">
                                <label className="text-xs font-medium">Document Type *</label>
                                <select
                                    value={form.documentType}
                                    onChange={(e) => setForm((p) => ({ ...p, documentType: e.target.value }))}
                                    className={inputClass}
                                    required
                                >
                                    <option value="">Select type</option>
                                    {DOCUMENT_TYPES.map((t) => (
                                        <option key={t.value} value={t.value}>{t.label}</option>
                                    ))}
                                </select>
                            </div>
                            <div className="space-y-1.5">
                                <label className="text-xs font-medium">Document Name *</label>
                                <input
                                    value={form.documentName}
                                    onChange={(e) => setForm((p) => ({ ...p, documentName: e.target.value }))}
                                    className={inputClass}
                                    placeholder="e.g. GST Certificate 2024-25"
                                    required
                                />
                            </div>
                            <div className="space-y-1.5">
                                <label className="text-xs font-medium">Expiry Date</label>
                                <input
                                    type="date"
                                    value={form.expiryDate}
                                    onChange={(e) => setForm((p) => ({ ...p, expiryDate: e.target.value }))}
                                    className={inputClass}
                                />
                            </div>
                            <div className="space-y-1.5">
                                <label className="text-xs font-medium">Description</label>
                                <input
                                    value={form.description}
                                    onChange={(e) => setForm((p) => ({ ...p, description: e.target.value }))}
                                    className={inputClass}
                                    placeholder="Optional notes"
                                />
                            </div>
                        </div>
                        <div className="flex justify-end gap-2 mt-4">
                            <button
                                type="button"
                                onClick={closeForm}
                                className="px-3 py-1.5 text-xs rounded-lg border border-border hover:bg-muted transition-colors"
                            >
                                Cancel
                            </button>
                            <button
                                type="submit"
                                disabled={saving}
                                className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs rounded-lg bg-primary text-primary-foreground hover:bg-primary/90 transition-colors disabled:opacity-50"
                            >
                                {saving ? <Loader2 className="w-3 h-3 animate-spin" /> : <CheckCircle2 className="w-3 h-3" />}
                                {saving ? "Saving..." : editingDoc ? "Update" : "Save"}
                            </button>
                        </div>
                    </form>
                </div>
            )}

            {/* Documents List */}
            <div className="p-6">
                {loading ? (
                    <div className="flex items-center justify-center h-32">
                        <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
                    </div>
                ) : documents.length === 0 ? (
                    <div className="flex flex-col items-center justify-center h-32 text-muted-foreground">
                        <FileText className="w-10 h-10 mb-2 opacity-20" />
                        <p className="text-sm">No documents uploaded yet</p>
                    </div>
                ) : (
                    <div className="space-y-3">
                        {documents.map((doc) => (
                            <div
                                key={doc.id}
                                className="flex items-center gap-4 p-4 rounded-lg border border-border hover:bg-muted/20 transition-colors"
                            >
                                {/* Icon */}
                                <div className={`w-10 h-10 rounded-lg flex items-center justify-center flex-shrink-0 ${
                                    doc.fileUrl
                                        ? "bg-blue-100 dark:bg-blue-900/30"
                                        : "bg-muted/50"
                                }`}>
                                    <FileText className={`w-5 h-5 ${
                                        doc.fileUrl ? "text-blue-600 dark:text-blue-400" : "text-muted-foreground/40"
                                    }`} />
                                </div>

                                {/* Info */}
                                <div className="flex-1 min-w-0">
                                    <div className="flex items-center gap-2">
                                        <span className="font-medium text-sm truncate">{doc.documentName}</span>
                                        <span className="px-2 py-0.5 rounded-full text-[10px] font-medium bg-muted text-muted-foreground">
                                            {typeLabel(doc.documentType)}
                                        </span>
                                    </div>
                                    <div className="flex items-center gap-3 mt-1">
                                        {doc.description && (
                                            <span className="text-xs text-muted-foreground truncate">{doc.description}</span>
                                        )}
                                        {doc.fileName && (
                                            <span className="text-xs text-muted-foreground/60">{doc.fileName}</span>
                                        )}
                                    </div>
                                </div>

                                {/* Expiry badge */}
                                {doc.expiryDate && (
                                    <div className={`flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-medium flex-shrink-0 ${
                                        isExpired(doc.expiryDate)
                                            ? "bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400"
                                            : isExpiringSoon(doc.expiryDate)
                                            ? "bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400"
                                            : "bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400"
                                    }`}>
                                        <Calendar className="w-3 h-3" />
                                        {isExpired(doc.expiryDate) ? "Expired" : isExpiringSoon(doc.expiryDate) ? "Expiring soon" : "Valid"}
                                        <span className="ml-1 opacity-75">
                                            {new Date(doc.expiryDate).toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "numeric" })}
                                        </span>
                                    </div>
                                )}

                                {/* Actions */}
                                <div className="flex items-center gap-1 flex-shrink-0">
                                    {/* Upload file */}
                                    <PermissionGate permission="SETTINGS_MANAGE">
                                        <input
                                            type="file"
                                            id={`doc-upload-${doc.id}`}
                                            className="hidden"
                                            accept="image/jpeg,image/png,image/webp,application/pdf"
                                            onChange={(e) => {
                                                const file = e.target.files?.[0];
                                                if (file) handleUpload(doc.id, file);
                                                e.target.value = "";
                                            }}
                                        />
                                        <button
                                            onClick={() => document.getElementById(`doc-upload-${doc.id}`)?.click()}
                                            disabled={uploadingId === doc.id}
                                            className="p-2 rounded-lg hover:bg-muted transition-colors text-muted-foreground hover:text-foreground disabled:opacity-50"
                                            title={doc.fileUrl ? "Replace file" : "Upload file"}
                                        >
                                            {uploadingId === doc.id ? (
                                                <Loader2 className="w-4 h-4 animate-spin" />
                                            ) : (
                                                <Upload className="w-4 h-4" />
                                            )}
                                        </button>
                                    </PermissionGate>

                                    {/* View file */}
                                    {doc.fileUrl && (
                                        <button
                                            onClick={() => handleView(doc.id)}
                                            className="p-2 rounded-lg hover:bg-muted transition-colors text-muted-foreground hover:text-blue-600"
                                            title="View file"
                                        >
                                            <ExternalLink className="w-4 h-4" />
                                        </button>
                                    )}

                                    {/* Edit */}
                                    <PermissionGate permission="SETTINGS_MANAGE">
                                        <button
                                            onClick={() => openEdit(doc)}
                                            className="p-2 rounded-lg hover:bg-muted transition-colors text-muted-foreground hover:text-foreground"
                                            title="Edit"
                                        >
                                            <Pencil className="w-4 h-4" />
                                        </button>
                                    </PermissionGate>

                                    {/* Delete */}
                                    <PermissionGate permission="SETTINGS_MANAGE">
                                        <button
                                            onClick={() => setDeleteId(doc.id)}
                                            className="p-2 rounded-lg hover:bg-red-100 dark:hover:bg-red-950/30 transition-colors text-muted-foreground hover:text-red-600"
                                            title="Delete"
                                        >
                                            <Trash2 className="w-4 h-4" />
                                        </button>
                                    </PermissionGate>
                                </div>
                            </div>
                        ))}
                    </div>
                )}
            </div>

            {/* Delete confirmation */}
            {deleteId !== null && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm">
                    <div className="bg-card border border-border rounded-2xl shadow-2xl p-6 max-w-sm w-full mx-4">
                        <div className="flex items-center gap-3 mb-4">
                            <div className="p-2 rounded-full bg-red-100 dark:bg-red-950/30">
                                <AlertTriangle className="w-5 h-5 text-red-600" />
                            </div>
                            <h3 className="text-lg font-semibold">Delete Document</h3>
                        </div>
                        <p className="text-sm text-muted-foreground mb-6">
                            Are you sure you want to delete this document? The uploaded file will also be removed.
                        </p>
                        <div className="flex justify-end gap-3">
                            <button
                                onClick={() => setDeleteId(null)}
                                className="px-4 py-2 text-sm rounded-lg border border-border hover:bg-muted transition-colors"
                                disabled={deleting}
                            >
                                Cancel
                            </button>
                            <button
                                onClick={() => handleDelete(deleteId)}
                                disabled={deleting}
                                className="px-4 py-2 text-sm rounded-lg bg-red-600 text-white hover:bg-red-700 transition-colors disabled:opacity-50"
                            >
                                {deleting ? "Deleting..." : "Delete"}
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
