"use client";

import { useState } from "react";
import { Upload, FileText, Loader2, ExternalLink } from "lucide-react";

interface FileUploadFieldProps {
    id: string;
    label: string;
    accept?: string;
    hint?: string;
    currentUrl?: string;
    onUpload: (file: File) => Promise<void>;
    onView?: () => void;
}

export function FileUploadField({
    id, label, accept, hint, currentUrl, onUpload, onView,
}: FileUploadFieldProps) {
    const [uploading, setUploading] = useState(false);
    const [uploaded, setUploaded] = useState(false);

    const handleFile = async (file: File) => {
        setUploading(true);
        try {
            await onUpload(file);
            setUploaded(true);
        } catch (error) {
            console.error(`Failed to upload ${label}`, error);
        } finally {
            setUploading(false);
        }
    };

    const hasFile = currentUrl || uploaded;

    return (
        <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-lg border border-border flex items-center justify-center bg-muted/30 flex-shrink-0">
                {uploading ? (
                    <Loader2 className="w-4 h-4 animate-spin" />
                ) : hasFile ? (
                    <FileText className="w-4 h-4 text-blue-500" />
                ) : (
                    <FileText className="w-4 h-4 text-muted-foreground/30" />
                )}
            </div>
            <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2">
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
                        className="inline-flex items-center gap-1.5 px-2.5 py-1.5 text-xs border border-border rounded-md hover:bg-muted transition-colors disabled:opacity-50"
                    >
                        <Upload className="w-3 h-3" />
                        {uploading ? "Uploading..." : hasFile ? "Replace" : label}
                    </button>
                    {hasFile && onView && (
                        <button
                            type="button"
                            onClick={onView}
                            className="inline-flex items-center gap-1 text-xs text-primary hover:text-primary/80"
                        >
                            <ExternalLink className="w-3 h-3" /> View
                        </button>
                    )}
                </div>
                {hint && <p className="text-xs text-muted-foreground mt-0.5">{hint}</p>}
            </div>
        </div>
    );
}
