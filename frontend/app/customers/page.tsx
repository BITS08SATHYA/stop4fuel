"use client";

import { CustomerStats } from "@/components/customer-stats";
import { CustomerList } from "@/components/customer-list";
import { Plus } from "lucide-react";
import { useState } from "react";
import { Modal } from "@/components/ui/modal";
import { CustomerForm } from "@/components/customers/customer-form";

export default function CustomersPage() {
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [refreshKey, setRefreshKey] = useState(0);

    const handleCustomerAdded = () => {
        setIsModalOpen(false);
        setRefreshKey(prev => prev + 1);
    };

    const handleSaveCustomer = async (formData: any) => {
        try {
            const res = await fetch("http://localhost:8080/api/customers", {
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
        <div className="p-8 min-h-screen bg-background transition-colors duration-300">
            <div className="max-w-7xl mx-auto">
                {/* Header */}
                <div className="flex justify-between items-center mb-8">
                    <div>
                        <h1 className="text-4xl font-bold text-foreground tracking-tight">
                            Customer <span className="text-gradient">Management</span>
                        </h1>
                        <p className="text-muted-foreground mt-2">
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
                <CustomerStats />

                {/* Main Content Grid */}
                <div className="mt-8">
                    <CustomerList refreshTrigger={refreshKey} />
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
