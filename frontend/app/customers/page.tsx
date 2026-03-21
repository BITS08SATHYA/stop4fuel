"use client";

import { CustomerStats } from "@/components/customer-stats";
import { CustomerList } from "@/components/customer-list";
import { Plus } from "lucide-react";
import { useState } from "react";
import { Modal } from "@/components/ui/modal";
import { CustomerForm } from "@/components/customers/customer-form";
import { API_BASE_URL } from "@/lib/api/station";

export default function CustomersPage() {
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [refreshKey, setRefreshKey] = useState(0);

    const handleCustomerAdded = () => {
        setIsModalOpen(false);
        setRefreshKey(prev => prev + 1);
    };

    const handleSaveCustomer = async (formData: any) => {
        try {
            const res = await fetch(`${API_BASE_URL}/customers`, {
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
                    party: formData.party,
                    group: formData.group,
                    creditLimitAmount: formData.creditLimitAmount,
                    creditLimitLiters: formData.creditLimitLiters,
                    customerCategory: formData.customerCategory,
                    latitude: formData.latitude,
                    longitude: formData.longitude,
                    gstNumber: formData.gstNumber,
                    statementFrequency: formData.statementFrequency,
                    statementGrouping: formData.statementGrouping,
                    statementThresholdAmount: formData.statementThresholdAmount,
                }),
            });
            if (res.ok) {
                handleCustomerAdded();
            }
        } catch (error) {
            console.error("Failed to save customer", error);
        }
    };

    return (
        <div className="p-6 h-screen flex flex-col bg-background transition-colors duration-300 overflow-hidden">
            <div className="max-w-7xl mx-auto w-full flex flex-col flex-1 min-h-0">
                {/* Header */}
                <div className="flex justify-between items-center mb-4 flex-shrink-0">
                    <div>
                        <h1 className="text-4xl font-bold text-foreground tracking-tight">
                            Customer <span className="text-gradient">Management</span>
                        </h1>
                        <p className="text-muted-foreground mt-1">
                            Manage fleets, credit limits, and vehicle associations.
                        </p>
                    </div>
                    <button
                        onClick={() => setIsModalOpen(true)}
                        className="btn-gradient px-6 py-3 rounded-xl font-medium flex items-center gap-2"
                    >
                        <Plus className="w-5 h-5" />
                        Add New Customer
                    </button>
                </div>

                {/* Stats */}
                <div className="flex-shrink-0">
                    <CustomerStats refreshTrigger={refreshKey} />
                </div>

                {/* Main Content Grid */}
                <div className="mt-4 flex-1 min-h-0">
                    <CustomerList refreshTrigger={refreshKey} onDataChange={() => setRefreshKey(prev => prev + 1)} />
                </div>
            </div>

            <Modal
                isOpen={isModalOpen}
                onClose={() => setIsModalOpen(false)}
                title="Add New Customer"
            >
                <div className="p-6 max-h-[80vh] overflow-y-auto">
                    <CustomerForm 
                        onSave={handleSaveCustomer}
                        onCancel={() => setIsModalOpen(false)}
                    />
                </div>
            </Modal>
        </div>
    );
}
