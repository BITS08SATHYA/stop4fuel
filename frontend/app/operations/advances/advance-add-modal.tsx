"use client";

import { useState } from "react";
import { Modal } from "@/components/ui/modal";
import { useFormValidation, required, min, indianMobile } from "@/lib/validation";
import { FieldError, inputErrorClass, FormErrorBanner } from "@/components/ui/field-error";
import { Employee, ADVANCE_TYPES, createAdvance } from "./advances-api";

interface AdvanceAddModalProps {
    isOpen: boolean;
    onClose: () => void;
    onSuccess: () => void;
    employees: Employee[];
    activeShift: { id: number } | null;
}

export function AdvanceAddModal({ isOpen, onClose, onSuccess, employees }: AdvanceAddModalProps) {
    const [addType, setAddType] = useState("CASH_ADVANCE");
    const [addAmount, setAddAmount] = useState("");
    const [addRecipientName, setAddRecipientName] = useState("");
    const [addRecipientPhone, setAddRecipientPhone] = useState("");
    const [addPurpose, setAddPurpose] = useState("");
    const [addRemarks, setAddRemarks] = useState("");
    const [addEmployeeId, setAddEmployeeId] = useState<number | null>(null);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [advApiError, setAdvApiError] = useState("");

    const { errors: advErrors, validate: validateAdv, clearError: clearAdvError, clearAllErrors: clearAllAdvErrors } = useFormValidation({
        addAmount: [required("Amount is required"), min(0.01, "Amount must be greater than 0")],
        addRecipientName: [required("Recipient name is required")],
        addRecipientPhone: [indianMobile()],
    });

    const resetAddForm = () => {
        setAddType("CASH_ADVANCE");
        setAddAmount("");
        setAddRecipientName("");
        setAddRecipientPhone("");
        setAddPurpose("");
        setAddRemarks("");
        setAddEmployeeId(null);
        clearAllAdvErrors();
        setAdvApiError("");
    };

    const handleEmployeeSelect = (empId: string) => {
        if (!empId) {
            setAddEmployeeId(null);
            return;
        }
        const emp = employees.find((e) => e.id === Number(empId));
        if (emp) {
            setAddEmployeeId(emp.id);
            setAddRecipientName(emp.name);
            setAddRecipientPhone(emp.phone || "");
        }
    };

    const handleCreateAdvance = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!validateAdv({ addAmount, addRecipientName, addRecipientPhone })) return;
        setIsSubmitting(true);
        setAdvApiError("");
        try {
            await createAdvance({
                amount: Number(addAmount),
                advanceType: addType,
                recipientName: addRecipientName,
                recipientPhone: addRecipientPhone,
                purpose: addPurpose,
                remarks: addRemarks,
                employee: addEmployeeId ? { id: addEmployeeId } : null,
            });
            handleClose();
            onSuccess();
        } catch (err: any) {
            setAdvApiError(err.message || "Failed to create advance");
        } finally {
            setIsSubmitting(false);
        }
    };

    const handleClose = () => {
        resetAddForm();
        onClose();
    };

    return (
        <Modal
            isOpen={isOpen}
            onClose={handleClose}
            title="Record Advance"
        >
            <form onSubmit={handleCreateAdvance} className="space-y-4">
                <FormErrorBanner message={advApiError} onDismiss={() => setAdvApiError("")} />
                {/* Advance Type Selector */}
                <div>
                    <label className="block text-sm font-medium text-foreground mb-2">Advance Type</label>
                    <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
                        {ADVANCE_TYPES.map((t) => {
                            const Icon = t.icon;
                            return (
                                <button
                                    key={t.value}
                                    type="button"
                                    onClick={() => setAddType(t.value)}
                                    className={`flex flex-col items-center gap-1.5 p-3 rounded-xl border transition-all text-xs font-medium ${
                                        addType === t.value
                                            ? "border-primary bg-primary/10 text-primary"
                                            : "border-border bg-card text-muted-foreground hover:border-primary/30"
                                    }`}
                                >
                                    <Icon className="w-4 h-4" />
                                    {t.label}
                                </button>
                            );
                        })}
                    </div>
                </div>

                {/* Employee Selector (for salary advance) */}
                {addType === "SALARY_ADVANCE" && employees.length > 0 && (
                    <div>
                        <label className="block text-sm font-medium text-foreground mb-1.5">Select Employee</label>
                        <select
                            value={addEmployeeId || ""}
                            onChange={(e) => handleEmployeeSelect(e.target.value)}
                            className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
                        >
                            <option value="">-- Select employee (optional) --</option>
                            {employees.map((emp) => (
                                <option key={emp.id} value={emp.id}>
                                    {emp.name} {emp.designation ? `(${emp.designation})` : ""}
                                </option>
                            ))}
                        </select>
                    </div>
                )}

                {/* Amount */}
                <div>
                    <label className="block text-sm font-medium text-foreground mb-1.5">
                        Amount <span className="text-red-500">*</span>
                    </label>
                    <input
                        type="number"
                        step="0.01"
                        min="0.01"
                        value={addAmount}
                        onChange={(e) => { setAddAmount(e.target.value); clearAdvError("addAmount"); }}
                        className={`w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground font-mono focus:outline-none focus:ring-2 focus:ring-primary/50 ${inputErrorClass(advErrors.addAmount)}`}
                        placeholder="0.00"
                    />
                    <FieldError error={advErrors.addAmount} />
                </div>

                {/* Recipient Name */}
                <div>
                    <label className="block text-sm font-medium text-foreground mb-1.5">
                        Recipient Name <span className="text-red-500">*</span>
                    </label>
                    <input
                        type="text"
                        value={addRecipientName}
                        onChange={(e) => { setAddRecipientName(e.target.value); clearAdvError("addRecipientName"); }}
                        className={`w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50 ${inputErrorClass(advErrors.addRecipientName)}`}
                        placeholder="Who is taking the advance?"
                    />
                    <FieldError error={advErrors.addRecipientName} />
                </div>

                {/* Recipient Phone */}
                <div>
                    <label className="block text-sm font-medium text-foreground mb-1.5">Recipient Phone</label>
                    <input
                        type="text"
                        value={addRecipientPhone}
                        onChange={(e) => { setAddRecipientPhone(e.target.value); clearAdvError("addRecipientPhone"); }}
                        className={`w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50 ${inputErrorClass(advErrors.addRecipientPhone)}`}
                        placeholder="Phone number (optional)"
                    />
                    <FieldError error={advErrors.addRecipientPhone} />
                </div>

                {/* Purpose */}
                <div>
                    <label className="block text-sm font-medium text-foreground mb-1.5">Purpose</label>
                    <input
                        type="text"
                        value={addPurpose}
                        onChange={(e) => setAddPurpose(e.target.value)}
                        className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                        placeholder="Purpose of the advance"
                    />
                </div>

                {/* Remarks */}
                <div>
                    <label className="block text-sm font-medium text-foreground mb-1.5">Remarks</label>
                    <input
                        type="text"
                        value={addRemarks}
                        onChange={(e) => setAddRemarks(e.target.value)}
                        className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                        placeholder="Optional notes..."
                    />
                </div>

                <div className="flex justify-end gap-3 pt-6 border-t border-border mt-6">
                    <button
                        type="button"
                        onClick={handleClose}
                        className="px-6 py-2.5 rounded-xl font-medium text-foreground bg-black/5 dark:bg-white/5 hover:bg-black/10 dark:hover:bg-white/10 transition-colors"
                    >
                        Cancel
                    </button>
                    <button
                        type="submit"
                        disabled={isSubmitting}
                        className="btn-gradient px-8 py-2.5 rounded-xl font-medium shadow-lg hover:shadow-xl transition-all disabled:opacity-50"
                    >
                        {isSubmitting ? "Saving..." : "Record Advance"}
                    </button>
                </div>
            </form>
        </Modal>
    );
}
