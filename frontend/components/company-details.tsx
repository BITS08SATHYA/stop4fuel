"use client";

import { useState, useEffect } from "react";
import { Building2, Save } from "lucide-react";
import { API_BASE_URL } from "@/lib/api/station";

interface Company {
    id?: number;
    name: string;
    openDate: string;
    sapCode: string;
    gstNo: string;
    site: string;
    type: string;
    address: string;
}

const emptyCompany: Company = {
    name: "",
    openDate: "",
    sapCode: "",
    gstNo: "",
    site: "",
    type: "",
    address: "",
};

export function CompanyDetails() {
    const [isLoading, setIsLoading] = useState(false);
    const [company, setCompany] = useState<Company>(emptyCompany);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        fetchCompany();
    }, []);

    const fetchCompany = async () => {
        try {
            const response = await fetch(`${API_BASE_URL}/companies`);
            if (!response.ok) throw new Error("Failed to fetch companies");
            const data = await response.json();
            if (data && data.length > 0) {
                setCompany(data[0]);
            }
        } catch (err) {
            console.error("Error fetching company:", err);
            // Don't set error here to avoid showing error on empty state
        }
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setIsLoading(true);
        setError(null);

        try {
            const url = company.id
                ? `${API_BASE_URL}/companies/${company.id}`
                : `${API_BASE_URL}/companies`;
            const method = company.id ? "PUT" : "POST";

            const response = await fetch(url, {
                method: method,
                headers: {
                    "Content-Type": "application/json",
                },
                body: JSON.stringify(company),
            });

            if (!response.ok) throw new Error("Failed to save company");

            const savedCompany = await response.json();
            setCompany(savedCompany);
            alert("Company details saved successfully!");
        } catch (err) {
            console.error("Error saving company:", err);
            setError("Failed to save company details. Please try again.");
        } finally {
            setIsLoading(false);
        }
    };

    const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => {
        const { id, value } = e.target;
        setCompany((prev) => ({
            ...prev,
            [id]: value,
        }));
    };

    return (
        <div className="w-full max-w-4xl mx-auto space-y-6">
            <div className="flex items-center justify-between">
                <div>
                    <h2 className="text-2xl font-bold tracking-tight">Company Details</h2>
                    <p className="text-muted-foreground">
                        Manage your company information and settings.
                    </p>
                </div>
                <div className="p-2 bg-primary/10 rounded-full text-primary">
                    <Building2 className="w-6 h-6" />
                </div>
            </div>

            {error && (
                <div className="p-4 text-sm text-red-500 bg-red-50 rounded-md border border-red-200">
                    {error}
                </div>
            )}

            <div className="rounded-xl border bg-card text-card-foreground shadow-sm">
                <div className="p-6 space-y-6">
                    <form onSubmit={handleSubmit} className="space-y-6">
                        <div className="grid gap-6 md:grid-cols-2">
                            <div className="space-y-2">
                                <label htmlFor="name" className="text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70">
                                    Company Name
                                </label>
                                <input
                                    id="name"
                                    value={company.name}
                                    onChange={handleChange}
                                    className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background file:border-0 file:bg-transparent file:text-sm file:font-medium placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
                                    placeholder="Enter company name"
                                    required
                                />
                            </div>

                            <div className="space-y-2">
                                <label htmlFor="openDate" className="text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70">
                                    Open Date
                                </label>
                                <input
                                    id="openDate"
                                    type="date"
                                    value={company.openDate}
                                    onChange={handleChange}
                                    className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background file:border-0 file:bg-transparent file:text-sm file:font-medium placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
                                    required
                                />
                            </div>

                            <div className="space-y-2">
                                <label htmlFor="sapCode" className="text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70">
                                    SAP Code
                                </label>
                                <input
                                    id="sapCode"
                                    value={company.sapCode}
                                    onChange={handleChange}
                                    className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background file:border-0 file:bg-transparent file:text-sm file:font-medium placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
                                    placeholder="Enter SAP code"
                                />
                            </div>

                            <div className="space-y-2">
                                <label htmlFor="gstNo" className="text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70">
                                    GST No
                                </label>
                                <input
                                    id="gstNo"
                                    value={company.gstNo}
                                    onChange={handleChange}
                                    className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background file:border-0 file:bg-transparent file:text-sm file:font-medium placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
                                    placeholder="Enter GST number"
                                />
                            </div>

                            <div className="space-y-2">
                                <label htmlFor="site" className="text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70">
                                    Site
                                </label>
                                <input
                                    id="site"
                                    value={company.site}
                                    onChange={handleChange}
                                    className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background file:border-0 file:bg-transparent file:text-sm file:font-medium placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
                                    placeholder="Enter site location"
                                />
                            </div>

                            <div className="space-y-2">
                                <label htmlFor="type" className="text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70">
                                    Type
                                </label>
                                <select
                                    id="type"
                                    value={company.type}
                                    onChange={handleChange}
                                    className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background file:border-0 file:bg-transparent file:text-sm file:font-medium placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
                                >
                                    <option value="">Select type</option>
                                    <option value="COCO">COCO (Company Owned Company Operated)</option>
                                    <option value="CODO">CODO (Company Owned Dealer Operated)</option>
                                    <option value="DODO">DODO (Dealer Owned Dealer Operated)</option>
                                </select>
                            </div>

                            <div className="col-span-2 space-y-2">
                                <label htmlFor="address" className="text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70">
                                    Address
                                </label>
                                <textarea
                                    id="address"
                                    value={company.address}
                                    onChange={handleChange}
                                    className="flex min-h-[80px] w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
                                    placeholder="Enter full address"
                                    rows={3}
                                />
                            </div>
                        </div>

                        <div className="flex justify-end">
                            <button
                                type="submit"
                                disabled={isLoading}
                                className="inline-flex items-center justify-center rounded-md text-sm font-medium ring-offset-background transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 bg-primary text-primary-foreground hover:bg-primary/90 h-10 px-4 py-2"
                            >
                                {isLoading ? (
                                    "Saving..."
                                ) : (
                                    <>
                                        <Save className="mr-2 h-4 w-4" />
                                        Save Changes
                                    </>
                                )}
                            </button>
                        </div>
                    </form>
                </div>
            </div>
        </div>
    );
}
