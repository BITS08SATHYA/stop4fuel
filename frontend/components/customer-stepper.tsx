"use client";

import React, { useState } from "react";
import { GroupStep } from "./steps/group-step";
import { CustomerStep } from "./steps/customer-step";
import { VehicleStep } from "./steps/vehicle-step";
import { Check, ChevronRight, Loader2 } from "lucide-react";
import { API_BASE_URL } from "@/lib/api/station";
import { fetchWithAuth } from "@/lib/api/fetch-with-auth";

interface CustomerStepperProps {
    onComplete: () => void;
    onCancel: () => void;
}

export function CustomerStepper({ onComplete, onCancel }: CustomerStepperProps) {
    const [step, setStep] = useState(1);
    const [loading, setLoading] = useState(false);
    const [formData, setFormData] = useState<Record<string, unknown>>({});
    const [createdIds, setCreatedIds] = useState<{ groupId?: number; customerId?: number }>({});

    const updateData = (data: any) => {
        setFormData(data);
    };

    const handleNext = async () => {
        setLoading(true);
        try {
            if (step === 1) {
                // Create/Get Group
                const res = await fetchWithAuth(`${API_BASE_URL}/groups`, {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({
                        groupName: formData.groupName,
                        description: formData.groupDescription,
                    }),
                });
                if (!res.ok) throw new Error("Failed to create group");
                const group = await res.json();
                setCreatedIds((prev) => ({ ...prev, groupId: group.id }));
                setStep(2);
            } else if (step === 2) {
                // Create Customer
                const res = await fetchWithAuth(`${API_BASE_URL}/customers`, {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({
                        name: formData.name,
                        username: formData.username,
                        password: formData.password,
                        joinDate: formData.joinDate,
                        emails: formData.emails || [],
                        phoneNumbers: formData.phoneNumbers || [],
                        address: formData.address,
                        party: formData.party, // { id: ... }
                        group: formData.group || { id: createdIds.groupId }, // Use selected group or created group
                        creditLimitAmount: formData.creditLimitAmount,
                        creditLimitLiters: formData.creditLimitLiters,
                    }),
                });
                if (!res.ok) throw new Error("Failed to create customer");
                const customer = await res.json();
                setCreatedIds((prev) => ({ ...prev, customerId: customer.id }));
                setStep(3);
            } else if (step === 3) {
                // Create Vehicle
                const payload: any = {
                    vehicleNumber: formData.vehicleNumber,
                    maxCapacity: formData.maxCapacity,
                    customer: { id: createdIds.customerId }, // Associate Customer
                };

                // Add vehicleType if provided
                if (formData.vehicleType) {
                    // Assuming vehicleType is stored as a string name, we need to find the ID
                    // For now, we'll assume it's already an ID or handle it differently
                    payload.vehicleType = { id: formData.vehicleType };
                }

                // Add preferredProduct (fuel type) if provided
                if (formData.fuelType) {
                    payload.preferredProduct = { id: formData.fuelType };
                }

                const res = await fetchWithAuth(`${API_BASE_URL}/vehicles`, {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify(payload),
                });
                if (!res.ok) throw new Error("Failed to create vehicle");
                onComplete();
            }
        } catch (error) {
            console.error("Error:", error);
            // Handle error (show toast, etc.)
        } finally {
            setLoading(false);
        }
    };

    const steps = [
        { id: 1, label: "Group Details" },
        { id: 2, label: "Customer Details" },
        { id: 3, label: "Vehicle Details" },
    ];

    const handleBack = () => {
        if (step > 1) setStep(step - 1);
    };

    return (
        <div className="flex flex-col h-[600px]">
            {/* Steps Indicator */}
            <div className="flex items-center justify-between mb-8 px-12">
                {steps.map((s, i) => (
                    <div key={s.id} className="flex items-center w-full last:w-auto">
                        <div className="flex flex-col items-center relative z-10">
                            <div
                                className={`w-10 h-10 rounded-full flex items-center justify-center text-sm font-bold transition-all duration-300 border-2 ${step >= s.id
                                    ? "bg-cyan-500 border-cyan-500 text-white shadow-[0_0_15px_rgba(6,182,212,0.5)]"
                                    : "bg-[#0a0f1c] border-white/10 text-muted-foreground"
                                    }`}
                            >
                                {step > s.id ? <Check className="w-5 h-5" /> : s.id}
                            </div>
                            <span className={`absolute top-12 text-xs font-medium whitespace-nowrap ${step >= s.id ? "text-cyan-400" : "text-muted-foreground"
                                }`}>
                                {s.label}
                            </span>
                        </div>
                        {i < steps.length - 1 && (
                            <div className="flex-1 h-0.5 mx-4 bg-white/10 relative">
                                <div
                                    className="absolute inset-0 bg-cyan-500 transition-all duration-500"
                                    style={{ width: step > s.id ? "100%" : "0%" }}
                                />
                            </div>
                        )}
                    </div>
                ))}
            </div>

            {/* Step Content */}
            <div className="flex-1 overflow-y-auto px-6 py-4">
                {step === 1 && <GroupStep data={formData} updateData={updateData} />}
                {step === 2 && <CustomerStep data={formData} updateData={updateData} />}
                {step === 3 && <VehicleStep data={formData} updateData={updateData} />}
            </div>

            {/* Footer Actions */}
            <div className="flex justify-between items-center mt-4 pt-6 border-t border-white/10 px-6">
                <button
                    onClick={handleBack}
                    disabled={step === 1 || loading}
                    className={`px-6 py-2.5 rounded-xl text-sm font-medium transition-colors ${step === 1
                        ? "text-white/20 cursor-not-allowed"
                        : "text-white hover:bg-white/5 border border-white/10"
                        }`}
                >
                    Back
                </button>

                <div className="flex gap-3">
                    <button
                        onClick={onCancel}
                        className="px-6 py-2.5 rounded-xl text-sm font-medium text-muted-foreground hover:text-white hover:bg-white/5 transition-colors"
                    >
                        Cancel
                    </button>
                    <button
                        onClick={handleNext}
                        disabled={loading}
                        className="btn-gradient px-8 py-2.5 rounded-xl text-sm font-bold text-white shadow-lg shadow-cyan-500/20 flex items-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                        {loading && <Loader2 className="w-4 h-4 animate-spin" />}
                        {step === 3 ? "Finish Setup" : "Next Step"}
                        {step < 3 && !loading && <ChevronRight className="w-4 h-4" />}
                    </button>
                </div>
            </div>
        </div>
    );
}
