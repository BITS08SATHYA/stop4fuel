"use client";

import { useState } from "react";
import { Upload, FileText, Loader2 } from "lucide-react";
import { EmployeeAvatar } from "@/components/ui/employee-avatar";
import type { LucideIcon } from "lucide-react";

interface DocumentUploadFieldProps {
    id: string;
    label: string;
    icon: LucideIcon;
    accept: string;
    hint: string;
    employeeId: number;
    employeeName: string;
    currentUrl?: string;
    onUpload: (file: File) => Promise<void>;
    /** "photo" renders a circular avatar preview; "document" renders a square icon */
    variant: "photo" | "document";
}

export function DocumentUploadField({
    id, label, icon: Icon, accept, hint, employeeId, employeeName,
    currentUrl, onUpload, variant,
}: DocumentUploadFieldProps) {
    const [uploading, setUploading] = useState(false);
    const [preview, setPreview] = useState<string | null>(null);

    const handleFile = async (file: File) => {
        setUploading(true);
        try {
            await onUpload(file);
            if (variant === "photo" || file.type.startsWith("image/")) {
                setPreview(URL.createObjectURL(file));
            } else {
                setPreview(file.name);
            }
        } catch (error) {
            console.error(`Failed to upload ${label}`, error);
        } finally {
            setUploading(false);
        }
    };

    const buttonLabel = uploading
        ? "Uploading..."
        : currentUrl
            ? "Replace"
            : `Upload ${label}`;

    return (
        <div className="space-y-3">
            <label className="text-sm font-semibold flex items-center gap-2">
                <Icon className="w-4 h-4" /> {label}
            </label>
            <div className="flex items-center gap-4">
                {/* Preview */}
                <div className="relative flex-shrink-0">
                    {variant === "photo" ? (
                        <>
                            {preview ? (
                                <img src={preview} alt="Preview" className="w-24 h-24 rounded-full object-cover" />
                            ) : (
                                <EmployeeAvatar employeeId={employeeId} name={employeeName} photoUrl={currentUrl} size="lg" />
                            )}
                            {uploading && (
                                <div className="absolute inset-0 bg-black/50 rounded-full flex items-center justify-center">
                                    <Loader2 className="w-6 h-6 text-white animate-spin" />
                                </div>
                            )}
                        </>
                    ) : (
                        <div className="w-16 h-16 rounded-lg border border-border flex items-center justify-center bg-muted/30">
                            {preview && preview.startsWith("blob:") ? (
                                <img src={preview} alt={label} className="w-full h-full rounded-lg object-cover" />
                            ) : currentUrl ? (
                                <FileText className="w-6 h-6 text-blue-500" />
                            ) : (
                                <FileText className="w-6 h-6 text-muted-foreground/30" />
                            )}
                            {uploading && (
                                <div className="absolute inset-0 bg-black/50 rounded-lg flex items-center justify-center">
                                    <Loader2 className="w-4 h-4 text-white animate-spin" />
                                </div>
                            )}
                        </div>
                    )}
                </div>

                {/* Upload button */}
                <div>
                    <input
                        type="file"
                        accept={accept}
                        id={id}
                        className="hidden"
                        onChange={(e) => {
                            const file = e.target.files?.[0];
                            if (file) handleFile(file);
                            e.target.value = "";
                        }}
                    />
                    <button
                        type="button"
                        onClick={() => document.getElementById(id)?.click()}
                        disabled={uploading}
                        className="inline-flex items-center gap-2 px-3 py-2 text-sm border border-border rounded-md hover:bg-muted transition-colors disabled:opacity-50"
                    >
                        <Upload className="w-4 h-4" />
                        {buttonLabel}
                    </button>
                    <p className="text-xs text-muted-foreground mt-1">{hint}</p>
                </div>
            </div>
        </div>
    );
}
