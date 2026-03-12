"use client";

import React, { useState } from "react";
import { CustomerStep } from "@/components/steps/customer-step";
import { Loader2 } from "lucide-react";

interface CustomerFormProps {
    onSave: (data: any) => Promise<void>;
    onCancel: () => void;
}

export function CustomerForm({ onSave, onCancel }: CustomerFormProps) {
    const [formData, setFormData] = useState<any>({
        emails: [""],
        phoneNumbers: [""],
    });
    const [loading, setLoading] = useState(false);

    const handleSave = async () => {
        setLoading(true);
        try {
            await onSave(formData);
        } catch (error) {
            console.error("Error saving customer:", error);
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="space-y-6">
            <CustomerStep data={formData} updateData={setFormData} />
            
            <div className="flex justify-end gap-3 pt-6 border-t border-white/10">
                <button
                    onClick={onCancel}
                    className="px-6 py-2.5 rounded-xl text-sm font-medium text-muted-foreground hover:text-white hover:bg-white/5 transition-colors"
                >
                    Cancel
                </button>
                <button
                    onClick={handleSave}
                    disabled={loading || !formData.name || !formData.username}
                    className="btn-gradient px-8 py-2.5 rounded-xl text-sm font-bold text-white shadow-lg shadow-cyan-500/20 flex items-center gap-2 disabled:opacity-50"
                >
                    {loading && <Loader2 className="w-4 h-4 animate-spin" />}
                    {loading ? "Saving..." : "Save Customer"}
                </button>
            </div>
        </div>
    );
}
