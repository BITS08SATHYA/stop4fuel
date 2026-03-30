"use client";

import { useState } from "react";
import { Modal } from "@/components/ui/modal";
import { useFormValidation, required, min } from "@/lib/validation";
import { FieldError, inputErrorClass } from "@/components/ui/field-error";
import { OperationalAdvance, getAdvanceTypeMeta, formatCurrency, returnAdvance } from "./advances-api";

interface AdvanceReturnModalProps {
    isOpen: boolean;
    onClose: () => void;
    onSuccess: () => void;
    advance: OperationalAdvance | null;
}

export function AdvanceReturnModal({ isOpen, onClose, onSuccess, advance }: AdvanceReturnModalProps) {
    const [returnAmount, setReturnAmount] = useState("");
    const [returnRemarks, setReturnRemarks] = useState("");
    const [isSubmitting, setIsSubmitting] = useState(false);

    const { errors: retErrors, validate: validateRet, clearError: clearRetError, clearAllErrors: clearAllRetErrors } = useFormValidation({
        returnAmount: [required("Return amount is required"), min(0.01, "Amount must be greater than 0")],
    });

    const returnOutstanding = advance
        ? advance.amount - (advance.returnedAmount || 0) - (advance.utilizedAmount || 0)
        : 0;

    const handleReturn = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!advance) return;
        if (!validateRet({ returnAmount })) return;
        setIsSubmitting(true);
        try {
            await returnAdvance(advance.id, {
                returnedAmount: Number(returnAmount),
                returnRemarks: returnRemarks,
            });
            handleClose();
            onSuccess();
        } catch (err: any) {
            alert(err.message || "Failed to record return");
        } finally {
            setIsSubmitting(false);
        }
    };

    const handleClose = () => {
        setReturnAmount("");
        setReturnRemarks("");
        clearAllRetErrors();
        onClose();
    };

    return (
        <Modal
            isOpen={isOpen}
            onClose={handleClose}
            title="Record Return"
        >
            {advance && (
                <form onSubmit={handleReturn} className="space-y-4">
                    {/* Advance Details */}
                    <div className="p-4 bg-white/5 rounded-xl border border-border space-y-2">
                        <div className="flex items-center gap-2 mb-3">
                            {(() => {
                                const meta = getAdvanceTypeMeta(advance.advanceType);
                                const Icon = meta.icon;
                                return (
                                    <div className={`p-1.5 rounded-lg ${meta.color}`}>
                                        <Icon className="w-3.5 h-3.5" />
                                    </div>
                                );
                            })()}
                            <span className="text-sm font-medium text-foreground">
                                {getAdvanceTypeMeta(advance.advanceType).label}
                            </span>
                        </div>
                        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-sm">
                            <div>
                                <p className="text-[10px] uppercase tracking-wider text-muted-foreground">Amount</p>
                                <p className="font-bold text-foreground">{formatCurrency(advance.amount)}</p>
                            </div>
                            <div>
                                <p className="text-[10px] uppercase tracking-wider text-muted-foreground">Bills</p>
                                <p className="font-bold text-teal-500">{formatCurrency(advance.utilizedAmount || 0)}</p>
                            </div>
                            <div>
                                <p className="text-[10px] uppercase tracking-wider text-muted-foreground">Returned</p>
                                <p className="font-bold text-green-500">{formatCurrency(advance.returnedAmount)}</p>
                            </div>
                            <div>
                                <p className="text-[10px] uppercase tracking-wider text-muted-foreground">Outstanding</p>
                                <p className="font-bold text-red-500">{formatCurrency(Math.max(0, returnOutstanding))}</p>
                            </div>
                        </div>
                        <p className="text-xs text-muted-foreground mt-1">
                            Recipient: <span className="text-foreground">{advance.recipientName}</span>
                        </p>
                    </div>

                    {/* Return Amount */}
                    <div>
                        <label className="block text-sm font-medium text-foreground mb-1.5">
                            Return Amount <span className="text-red-500">*</span>
                        </label>
                        <input
                            type="number"
                            step="0.01"
                            min="0.01"
                            max={Math.max(0, returnOutstanding)}
                            value={returnAmount}
                            onChange={(e) => { setReturnAmount(e.target.value); clearRetError("returnAmount"); }}
                            className={`w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground font-mono focus:outline-none focus:ring-2 focus:ring-primary/50 ${inputErrorClass(retErrors.returnAmount)}`}
                            placeholder={`Max: ${formatCurrency(Math.max(0, returnOutstanding))}`}
                        />
                        <FieldError error={retErrors.returnAmount} />
                    </div>

                    {/* Return Remarks */}
                    <div>
                        <label className="block text-sm font-medium text-foreground mb-1.5">Return Remarks</label>
                        <input
                            type="text"
                            value={returnRemarks}
                            onChange={(e) => setReturnRemarks(e.target.value)}
                            className="w-full bg-background border border-border rounded-xl px-4 py-3 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                            placeholder="Optional notes about the return..."
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
                            {isSubmitting ? "Saving..." : "Record Return"}
                        </button>
                    </div>
                </form>
            )}
        </Modal>
    );
}
