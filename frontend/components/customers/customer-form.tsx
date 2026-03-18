"use client";

import React, { useState, useMemo } from "react";
import { CustomerStep } from "@/components/steps/customer-step";
import { Loader2 } from "lucide-react";
import { useFormValidation, required, minLength, email, phone, min, custom } from "@/lib/validation";
import { FormErrorBanner } from "@/components/ui/field-error";

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
    const [apiError, setApiError] = useState("");

    const validationRules = useMemo(() => ({
        name: [required("Customer name is required")],
        username: [required("Username is required"), minLength(3, "Username must be at least 3 characters")],
        password: [required("Password is required"), minLength(6, "Password must be at least 6 characters")],
        emails: [custom((value: string[]) => {
            if (!value) return true;
            const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
            return value.every((e: string) => !e.trim() || emailRegex.test(e));
        }, "Invalid email address")],
        phoneNumbers: [custom((value: string[]) => {
            if (!value) return true;
            const phoneRegex = /^[+]?[\d\s\-()]{7,20}$/;
            return value.every((p: string) => !p.trim() || phoneRegex.test(p.trim()));
        }, "Invalid phone number")],
        creditLimitAmount: [min(0, "Credit limit must be at least 0")],
        creditLimitLiters: [min(0, "Credit limit must be at least 0")],
    }), []);

    const { errors, validate, clearError, clearAllErrors } = useFormValidation(validationRules);

    const handleSave = async () => {
        setApiError("");
        if (!validate({
            name: formData.name || "",
            username: formData.username || "",
            password: formData.password || "",
            emails: formData.emails || [],
            phoneNumbers: formData.phoneNumbers || [],
            creditLimitAmount: formData.creditLimitAmount,
            creditLimitLiters: formData.creditLimitLiters,
        })) return;

        setLoading(true);
        try {
            await onSave(formData);
        } catch (error) {
            console.error("Error saving customer:", error);
            setApiError("Error saving customer details");
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="space-y-6">
            <FormErrorBanner message={apiError} onDismiss={() => setApiError("")} />
            <CustomerStep data={formData} updateData={setFormData} errors={errors} clearError={clearError as (field: string) => void} />

            <div className="flex justify-end gap-3 pt-6 border-t border-white/10">
                <button
                    onClick={onCancel}
                    className="px-6 py-2.5 rounded-xl text-sm font-medium text-muted-foreground hover:text-white hover:bg-white/5 transition-colors"
                >
                    Cancel
                </button>
                <button
                    onClick={handleSave}
                    disabled={loading}
                    className="btn-gradient px-8 py-2.5 rounded-xl text-sm font-bold text-white shadow-lg shadow-cyan-500/20 flex items-center gap-2 disabled:opacity-50"
                >
                    {loading && <Loader2 className="w-4 h-4 animate-spin" />}
                    {loading ? "Saving..." : "Save Customer"}
                </button>
            </div>
        </div>
    );
}
