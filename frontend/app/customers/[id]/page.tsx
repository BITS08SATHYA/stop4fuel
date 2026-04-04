"use client";

import { useState, useEffect } from "react";
import { useParams, useRouter } from "next/navigation";
import { ArrowLeft, Mail, Phone, MapPin, Calendar, Save, Edit, Trash2, Truck, X, ShieldAlert, ShieldCheck, ShieldOff, Tag, Plus, Download, Globe } from "lucide-react";
import { GlassCard } from "@/components/ui/glass-card";
import { Badge } from "@/components/ui/badge";
import {
    getIncentivesByCustomer, createIncentive, updateIncentive, deleteIncentive,
    getActiveProducts, type Incentive, type Product, type Vehicle, type CustomerCategoryType
} from "@/lib/api/station";

import { API_BASE_URL } from "@/lib/api/station";
import { fetchWithAuth } from "@/lib/api/fetch-with-auth";
import { PermissionGate } from "@/components/permission-gate";
import { StyledSelect } from "@/components/ui/styled-select";

const API = API_BASE_URL;

interface CustomerDetail {
    id: number;
    name: string;
    username?: string;
    address?: string;
    emails: string[] | string;
    phoneNumbers: string[] | string;
    active: boolean;
    status?: string;
    joinDate?: string;
    creditLimitAmount?: number | null;
    creditLimitLiters?: number | null;
    consumedLiters?: number;
    ledgerBalance?: number;
    statementGrouping?: string | null;
    statementFrequency?: string | null;
    gstNumber?: string | null;
    latitude?: number | string | null;
    longitude?: number | string | null;
    party?: { id: number; partyType?: string } | null;
    group?: { id: number; groupName?: string } | null;
    customerCategory?: { id: number; categoryName?: string; categoryType?: string } | null;
    role?: { id: number; roleType?: string } | null;
    // Allow additional dynamic API fields
    [key: string]: unknown;
}

interface GroupRef { id: number; groupName?: string; description?: string }
interface PartyRef { id: number; partyType?: string }
interface VehicleTypeRef { id: number; name?: string }

function statusVariant(status: string): "success" | "danger" | "warning" | "default" {
    if (status === "ACTIVE") return "success";
    if (status === "BLOCKED") return "danger";
    if (status === "INACTIVE") return "warning";
    return "default";
}

function statusLabel(status: string) {
    if (status === "ACTIVE") return "Active";
    if (status === "BLOCKED") return "Blocked";
    if (status === "INACTIVE") return "Inactive";
    return status;
}

export default function CustomerProfilePage() {
    const params = useParams();
    const router = useRouter();
    const [customer, setCustomer] = useState<CustomerDetail | null>(null);
    const [vehicles, setVehicles] = useState<Vehicle[]>([]);
    const [loading, setLoading] = useState(true);
    const [isEditing, setIsEditing] = useState(false);

    // Dropdowns data
    const [groups, setGroups] = useState<GroupRef[]>([]);
    const [parties, setParties] = useState<PartyRef[]>([]);
    const [vehicleTypes, setVehicleTypes] = useState<VehicleTypeRef[]>([]);
    const [products, setProducts] = useState<Product[]>([]);
    const [categories, setCategories] = useState<CustomerCategoryType[]>([]);
    const [editCustomerType, setEditCustomerType] = useState<string>("");

    // Incentives
    const [customerIncentives, setCustomerIncentives] = useState<Incentive[]>([]);
    const [allProducts, setAllProducts] = useState<Product[]>([]);
    const [showAddIncentive, setShowAddIncentive] = useState(false);
    const [newIncentive, setNewIncentive] = useState({ productId: "", minQuantity: "", discountRate: "" });
    const [incentiveError, setIncentiveError] = useState("");

    // Add Vehicle State
    const [showAddVehicle, setShowAddVehicle] = useState(false);
    const [newVehicle, setNewVehicle] = useState({
        vehicleNumber: "",
        maxCapacity: "",
        vehicleTypeId: "",
        fuelType: "",
        maxLitersPerMonth: ""
    });
    const [vehicleError, setVehicleError] = useState("");

    const fetchData = async () => {
        try {
            const [customerRes, vehiclesRes] = await Promise.all([
                fetchWithAuth(`${API}/customers/${params.id}`),
                fetchWithAuth(`${API}/customers/${params.id}/vehicles`)
            ]);
            if (customerRes.ok) setCustomer(await customerRes.json());
            if (vehiclesRes.ok) setVehicles(await vehiclesRes.json());
        } catch (error) {
            console.error("Failed to fetch data", error);
        } finally {
            setLoading(false);
        }
    };

    const fetchDropdowns = async () => {
        try {
            const [groupsRes, partiesRes, vtRes, prodRes, catRes] = await Promise.all([
                fetchWithAuth(`${API}/groups`),
                fetchWithAuth(`${API}/parties`),
                fetchWithAuth(`${API}/vehicle-types`),
                fetchWithAuth(`${API}/products`),
                fetchWithAuth(`${API}/customer-categories`),
            ]);
            if (groupsRes.ok) setGroups(await groupsRes.json());
            if (partiesRes.ok) setParties(await partiesRes.json());
            if (vtRes.ok) setVehicleTypes(await vtRes.json());
            if (prodRes.ok) setProducts(await prodRes.json());
            if (catRes.ok) setCategories(await catRes.json());
        } catch (error) {
            console.error("Failed to fetch dropdowns", error);
        }
    };

    const fetchIncentives = async () => {
        try {
            const [incData, prodData] = await Promise.all([
                getIncentivesByCustomer(Number(params.id)),
                getActiveProducts()
            ]);
            setCustomerIncentives(incData);
            setAllProducts(prodData);
        } catch (err) { console.error("Failed to fetch incentives", err); }
    };

    useEffect(() => {
        if (params.id) {
            fetchData();
            fetchDropdowns();
            fetchIncentives();
        }
    }, [params.id]);

    const handleSave = async () => {
        if (!customer) return;
        try {
            const updatedCustomer = {
                ...customer,
                emails: Array.isArray(customer.emails) ? customer.emails : (customer.emails as string).split(',').map((s: string) => s.trim()),
                phoneNumbers: Array.isArray(customer.phoneNumbers) ? customer.phoneNumbers : (customer.phoneNumbers as string).split(',').map((s: string) => s.trim()),
            };
            const res = await fetchWithAuth(`${API}/customers/${params.id}`, {
                method: "PUT",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(updatedCustomer),
            });
            if (res.ok) {
                setCustomer(await res.json());
                setIsEditing(false);
            } else {
                alert("Failed to save changes");
            }
        } catch (error) {
            console.error("Failed to save customer", error);
            alert("Error saving changes");
        }
    };

    const handleDelete = async () => {
        if (confirm("Are you sure you want to delete this customer?")) {
            try {
                const res = await fetchWithAuth(`${API}/customers/${params.id}`, { method: "DELETE" });
                if (res.ok) router.push("/customers");
            } catch (error) {
                console.error("Failed to delete customer", error);
            }
        }
    };

    const handleToggleStatus = async () => {
        try {
            const res = await fetchWithAuth(`${API}/customers/${params.id}/toggle-status`, { method: "PATCH" });
            if (res.ok) {
                setCustomer(await res.json());
            }
        } catch (error) {
            console.error("Failed to toggle status", error);
        }
    };

    const handleToggleVehicleStatus = async (vehicleId: number) => {
        try {
            const res = await fetchWithAuth(`${API}/vehicles/${vehicleId}/toggle-status`, { method: "PATCH" });
            if (res.ok) {
                fetchData();
            }
        } catch (error) {
            console.error("Failed to toggle vehicle status", error);
        }
    };

    const handleAddVehicle = async () => {
        if (!customer) return;
        setVehicleError("");
        try {
            const payload: any = {
                vehicleNumber: newVehicle.vehicleNumber,
                maxCapacity: newVehicle.maxCapacity ? parseFloat(newVehicle.maxCapacity) : null,
                customer: { id: customer.id }
            };
            if (newVehicle.vehicleTypeId) payload.vehicleType = { id: parseInt(newVehicle.vehicleTypeId) };
            if (newVehicle.fuelType) payload.preferredProduct = { id: parseInt(newVehicle.fuelType) };
            if (newVehicle.maxLitersPerMonth) payload.maxLitersPerMonth = parseFloat(newVehicle.maxLitersPerMonth);

            const res = await fetchWithAuth(`${API}/vehicles`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(payload),
            });

            if (res.ok) {
                setShowAddVehicle(false);
                setNewVehicle({ vehicleNumber: "", maxCapacity: "", vehicleTypeId: "", fuelType: "", maxLitersPerMonth: "" });
                fetchData();
            } else {
                const errorText = await res.text();
                setVehicleError(errorText || "Failed to add vehicle");
            }
        } catch (error) {
            console.error("Failed to add vehicle", error);
            setVehicleError("Network error");
        }
    };

    const handleDeleteVehicle = async (vehicleId: number) => {
        if (confirm("Are you sure you want to remove this vehicle?")) {
            try {
                const res = await fetchWithAuth(`${API}/vehicles/${vehicleId}`, { method: "DELETE" });
                if (res.ok) fetchData();
                else alert("Failed to delete vehicle");
            } catch (error) {
                console.error("Failed to delete vehicle", error);
            }
        }
    };

    if (loading) return <div className="p-8 text-center text-muted-foreground">Loading...</div>;
    if (!customer) return <div className="p-8 text-center text-muted-foreground">Customer not found</div>;

    const creditLimit = customer.creditLimitLiters || 0;
    const consumed = customer.consumedLiters || 0;
    const percentage = creditLimit > 0 ? (consumed / creditLimit) * 100 : 0;
    const customerStatus = customer.status || "ACTIVE";

    // Calculate allocated liters across vehicles
    const allocatedLiters = vehicles.reduce((sum: number, v: any) => sum + (v.maxLitersPerMonth || 0), 0);
    const remainingToAllocate = creditLimit - allocatedLiters;

    return (
        <div className="p-8 space-y-6 max-w-7xl mx-auto relative">
            {/* Header */}
            <div className="flex items-center justify-between mb-8">
                <div className="flex items-center gap-4">
                    <button onClick={() => router.back()} className="p-2 rounded-lg hover:bg-secondary transition-colors">
                        <ArrowLeft className="w-6 h-6 text-muted-foreground" />
                    </button>
                    <div>
                        {isEditing ? (
                            <input
                                type="text"
                                value={customer.name}
                                onChange={(e) => setCustomer({ ...customer, name: e.target.value })}
                                className="text-3xl font-bold text-foreground bg-transparent border-b border-white/20 focus:outline-none focus:border-cyan-500"
                            />
                        ) : (
                            <h1 className="text-3xl font-bold text-foreground">{customer.name}</h1>
                        )}
                        <div className="flex items-center gap-2 text-muted-foreground mt-1">
                            <span>Customer #{customer.id?.toString().padStart(3, '0')}</span>
                            <span>&bull;</span>
                            <Badge variant={statusVariant(customerStatus)}>
                                {statusLabel(customerStatus)}
                            </Badge>
                            {customer.customerCategory && (
                                <Badge variant="outline">{customer.customerCategory.categoryName}</Badge>
                            )}
                        </div>
                    </div>
                </div>
                <div className="flex gap-3">
                    {/* Status toggle button */}
                    <button
                        onClick={handleToggleStatus}
                        className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors flex items-center gap-2 ${
                            customerStatus === "ACTIVE"
                                ? "bg-emerald-500/10 text-emerald-500 hover:bg-emerald-500/20"
                                : customerStatus === "BLOCKED"
                                ? "bg-red-500/10 text-red-500 hover:bg-red-500/20"
                                : "bg-gray-500/10 text-gray-400 hover:bg-gray-500/20"
                        }`}
                    >
                        {customerStatus === "ACTIVE" && <><ShieldCheck className="w-4 h-4" /> Set Inactive</>}
                        {customerStatus === "INACTIVE" && <><ShieldOff className="w-4 h-4" /> Activate</>}
                        {customerStatus === "BLOCKED" && <><ShieldAlert className="w-4 h-4" /> Unblock</>}
                    </button>
                    <PermissionGate permission="CUSTOMER_MANAGE">
                        <button
                            onClick={() => {
                                if (isEditing) {
                                    handleSave();
                                } else {
                                    setEditCustomerType(customer?.customerCategory?.categoryType || "");
                                    setIsEditing(true);
                                }
                            }}
                            className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors flex items-center gap-2 ${isEditing
                                ? "bg-cyan-500 text-white hover:bg-cyan-600"
                                : "bg-secondary text-secondary-foreground hover:bg-secondary/80"
                            }`}
                        >
                            {isEditing ? <Save className="w-4 h-4" /> : <Edit className="w-4 h-4" />}
                            {isEditing ? "Save Changes" : "Edit Profile"}
                        </button>
                        {isEditing && (
                            <button
                                onClick={() => { setIsEditing(false); fetchData(); }}
                                className="px-4 py-2 rounded-lg text-sm font-medium text-muted-foreground hover:text-foreground bg-secondary hover:bg-secondary/80 transition-colors"
                            >
                                Cancel
                            </button>
                        )}
                        <button
                            onClick={handleDelete}
                            className="px-4 py-2 bg-destructive/10 text-destructive rounded-lg text-sm font-medium hover:bg-destructive/20 transition-colors flex items-center gap-2"
                        >
                            <Trash2 className="w-4 h-4" />
                            Delete
                        </button>
                    </PermissionGate>
                </div>
            </div>

            {/* Blocked/Inactive banner */}
            {customerStatus !== "ACTIVE" && (
                <div className={`rounded-xl p-4 flex items-center gap-3 ${
                    customerStatus === "BLOCKED"
                        ? "bg-red-500/10 border border-red-500/20 text-red-400"
                        : "bg-yellow-500/10 border border-yellow-500/20 text-yellow-400"
                }`}>
                    <ShieldAlert className="w-5 h-5 shrink-0" />
                    <div>
                        <span className="font-medium">
                            {customerStatus === "BLOCKED"
                                ? "This customer is BLOCKED — credit limit exceeded. No invoices can be raised."
                                : "This customer is INACTIVE — manually disabled. No invoices can be raised."}
                        </span>
                        <span className="text-sm opacity-75 ml-2">
                            {customerStatus === "BLOCKED"
                                ? "Admin must manually unblock after reviewing."
                                : "Click 'Activate' to re-enable."}
                        </span>
                    </div>
                </div>
            )}

            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                {/* Left Column: Contact Info */}
                <div className="space-y-6">
                    <GlassCard className="p-6">
                        <h3 className="text-lg font-semibold text-foreground mb-4">Contact Information</h3>
                        <div className="space-y-4">
                            <div className="flex flex-col gap-1 text-sm">
                                <div className="flex items-center gap-2 text-muted-foreground">
                                    <Mail className="w-4 h-4" /> Emails
                                </div>
                                {isEditing ? (
                                    <input
                                        type="text"
                                        value={Array.isArray(customer.emails) ? customer.emails.join(', ') : customer.emails}
                                        onChange={(e) => setCustomer({ ...customer, emails: e.target.value.split(',').map((s: string) => s.trim()) })}
                                        className="w-full bg-secondary border border-border rounded px-2 py-1 text-foreground"
                                        placeholder="Comma separated emails"
                                    />
                                ) : (
                                    (Array.isArray(customer.emails) ? customer.emails : []).map((email: string, i: number) => (
                                        <span key={i} className="text-foreground pl-6">{email}</span>
                                    ))
                                )}
                            </div>
                            <div className="flex flex-col gap-1 text-sm">
                                <div className="flex items-center gap-2 text-muted-foreground">
                                    <Phone className="w-4 h-4" /> Phone Numbers
                                </div>
                                {isEditing ? (
                                    <input
                                        type="text"
                                        value={Array.isArray(customer.phoneNumbers) ? customer.phoneNumbers.join(', ') : customer.phoneNumbers}
                                        onChange={(e) => setCustomer({ ...customer, phoneNumbers: e.target.value.split(',').map((s: string) => s.trim()) })}
                                        className="w-full bg-secondary border border-border rounded px-2 py-1 text-foreground"
                                        placeholder="Comma separated phones"
                                    />
                                ) : (
                                    (Array.isArray(customer.phoneNumbers) ? customer.phoneNumbers : []).map((phone: string, i: number) => (
                                        <span key={i} className="text-foreground pl-6">{phone}</span>
                                    ))
                                )}
                            </div>
                            <div className="flex flex-col gap-1 text-sm">
                                <div className="flex items-center gap-2 text-muted-foreground">
                                    <MapPin className="w-4 h-4" /> Address
                                </div>
                                {isEditing ? (
                                    <textarea
                                        value={customer.address || ""}
                                        onChange={(e) => setCustomer({ ...customer, address: e.target.value })}
                                        className="w-full bg-secondary border border-border rounded px-2 py-1 text-foreground"
                                        rows={2}
                                    />
                                ) : (
                                    <span className="text-foreground pl-6">{customer.address || "No address provided"}</span>
                                )}
                            </div>
                            <div className="flex items-center gap-3 text-sm">
                                <Calendar className="w-4 h-4 text-muted-foreground" />
                                {isEditing ? (
                                    <input
                                        type="date"
                                        value={customer.joinDate || ""}
                                        onChange={(e) => setCustomer({ ...customer, joinDate: e.target.value })}
                                        className="bg-secondary border border-border rounded px-2 py-1 text-foreground text-sm"
                                    />
                                ) : (
                                    <span className="text-foreground">Joined {customer.joinDate || "N/A"}</span>
                                )}
                            </div>
                        </div>
                    </GlassCard>

                    <GlassCard className="p-6">
                        <h3 className="text-lg font-semibold text-foreground mb-4">Account Details</h3>
                        <div className="space-y-4">
                            <div className="flex items-center justify-between">
                                <span className="text-sm text-muted-foreground">Username</span>
                                {isEditing ? (
                                    <input
                                        type="text"
                                        value={customer.username || ""}
                                        onChange={(e) => setCustomer({ ...customer, username: e.target.value })}
                                        className="text-sm font-medium text-foreground bg-secondary border border-border rounded px-2 py-1 text-right w-32"
                                    />
                                ) : (
                                    <span className="text-sm font-medium text-foreground">{customer.username}</span>
                                )}
                            </div>
                            <div className="flex items-center justify-between">
                                <span className="text-sm text-muted-foreground">Role</span>
                                <Badge variant="outline">{customer.role?.roleType || "N/A"}</Badge>
                            </div>
                            <div className="flex items-center justify-between">
                                <span className="text-sm text-muted-foreground">Party Type</span>
                                {isEditing ? (
                                    <StyledSelect
                                        value={String(customer.party?.id || "")}
                                        onChange={(val) => {
                                            const selected = parties.find((p: any) => p.id === Number(val));
                                            setCustomer({ ...customer, party: selected || null });
                                        }}
                                        options={[
                                            { value: "", label: "None" },
                                            ...parties.map((p: any) => ({ value: String(p.id), label: p.partyType })),
                                        ]}
                                        placeholder="None"
                                        className="min-w-[140px]"
                                    />
                                ) : (
                                    <Badge variant="outline">{customer.party?.partyType || "N/A"}</Badge>
                                )}
                            </div>
                            <div className="flex items-center justify-between">
                                <span className="text-sm text-muted-foreground">Group</span>
                                {isEditing ? (
                                    <StyledSelect
                                        value={String(customer.group?.id || "")}
                                        onChange={(val) => {
                                            const selected = groups.find((g: any) => g.id === Number(val));
                                            setCustomer({ ...customer, group: selected || null });
                                        }}
                                        options={[
                                            { value: "", label: "None" },
                                            ...groups.map((g: any) => ({ value: String(g.id), label: g.groupName })),
                                        ]}
                                        placeholder="None"
                                        className="min-w-[140px]"
                                    />
                                ) : (
                                    <span className="text-sm font-medium text-foreground">{customer.group?.groupName || "Unassigned"}</span>
                                )}
                            </div>
                            <div className="flex items-center justify-between">
                                <span className="text-sm text-muted-foreground">Customer Type</span>
                                {isEditing ? (
                                    <StyledSelect
                                        value={editCustomerType}
                                        onChange={(val) => {
                                            setEditCustomerType(val);
                                            setCustomer({ ...customer, customerCategory: null });
                                            if (val === "GOVERNMENT") {
                                                const govCat = categories.find((c: any) => c.categoryType === "GOVERNMENT");
                                                if (govCat) setCustomer({ ...customer, customerCategory: govCat });
                                            }
                                        }}
                                        options={[
                                            { value: "", label: "Select Type" },
                                            { value: "GOVERNMENT", label: "Government" },
                                            { value: "NON_GOVERNMENT", label: "Non-Government" },
                                        ]}
                                        placeholder="Select Type"
                                        className="min-w-[140px]"
                                    />
                                ) : (
                                    <span className="text-sm font-medium text-foreground">
                                        {customer.customerCategory?.categoryType === "GOVERNMENT" ? "Government" :
                                         customer.customerCategory?.categoryType === "NON_GOVERNMENT" ? "Non-Government" : "Not set"}
                                    </span>
                                )}
                            </div>
                            {(isEditing ? editCustomerType === "NON_GOVERNMENT" : customer.customerCategory?.categoryType === "NON_GOVERNMENT") && (
                                <div className="flex items-center justify-between">
                                    <span className="text-sm text-muted-foreground">Category</span>
                                    {isEditing ? (
                                        <StyledSelect
                                            value={String(customer.customerCategory?.id || "")}
                                            onChange={(val) => {
                                                const selected = categories.find((c: any) => c.id === Number(val));
                                                setCustomer({ ...customer, customerCategory: selected || null });
                                            }}
                                            options={[
                                                { value: "", label: "Select Category" },
                                                ...categories.filter((c: any) => c.categoryType === "NON_GOVERNMENT").map((c: any) => ({ value: String(c.id), label: c.categoryName })),
                                            ]}
                                            placeholder="Select Category"
                                            className="min-w-[140px]"
                                        />
                                    ) : (
                                        <span className="text-sm font-medium text-foreground">{customer.customerCategory?.categoryName || "Not set"}</span>
                                    )}
                                </div>
                            )}
                            <div className="flex items-center justify-between">
                                <span className="text-sm text-muted-foreground">GST Number</span>
                                {isEditing ? (
                                    <input
                                        type="text"
                                        value={customer.gstNumber || ""}
                                        onChange={(e) => setCustomer({ ...customer, gstNumber: e.target.value })}
                                        className="text-sm font-medium text-foreground bg-secondary border border-border rounded px-2 py-1 text-right w-40"
                                        placeholder="e.g. 33AABCT1332L1ZZ"
                                        maxLength={15}
                                    />
                                ) : (
                                    <span className="text-sm font-medium text-foreground">{customer.gstNumber || "-"}</span>
                                )}
                            </div>
                        </div>
                    </GlassCard>
                </div>

                {/* Middle Column: Credit Overview */}
                <div className="space-y-6">
                    <GlassCard className="p-6">
                        <h3 className="text-lg font-semibold text-foreground mb-4">Credit Overview</h3>
                        <div className="space-y-6">
                            <div>
                                <div className="flex justify-between text-sm mb-2">
                                    <span className="text-muted-foreground">Monthly Consumption</span>
                                    <span className="font-medium text-foreground">
                                        {creditLimit > 0 ? `${Math.min(Math.round(percentage), 100)}%` : "No limit set"}
                                    </span>
                                </div>
                                <div className="h-3 bg-secondary rounded-full overflow-hidden">
                                    <div
                                        className={`h-full transition-all duration-500 rounded-full ${
                                            percentage >= 100 ? 'bg-destructive' : percentage > 80 ? 'bg-yellow-500' : 'bg-accent'
                                        }`}
                                        style={{ width: `${Math.min(percentage, 100)}%` }}
                                    />
                                </div>
                                <div className="flex justify-between text-xs mt-2 text-muted-foreground">
                                    <span>{consumed} L consumed</span>
                                    {isEditing ? (
                                        <div className="flex items-center gap-1">
                                            <input
                                                type="number"
                                                value={customer.creditLimitLiters || ""}
                                                onChange={(e) => setCustomer({ ...customer, creditLimitLiters: parseFloat(e.target.value) || null })}
                                                className="w-20 bg-secondary border border-border rounded px-1 py-0.5 text-right text-foreground"
                                            />
                                            <span>L limit</span>
                                        </div>
                                    ) : (
                                        <span>{creditLimit > 0 ? `${creditLimit} L limit` : "Unlimited"}</span>
                                    )}
                                </div>
                            </div>

                            {/* Credit Amount Limit */}
                            <div className="pt-4 border-t border-border">
                                <div className="flex justify-between items-center text-sm">
                                    <span className="text-muted-foreground">Credit Amount Limit</span>
                                    {isEditing ? (
                                        <div className="flex items-center gap-1">
                                            <span>₹</span>
                                            <input
                                                type="number"
                                                value={customer.creditLimitAmount || ""}
                                                onChange={(e) => setCustomer({ ...customer, creditLimitAmount: parseFloat(e.target.value) || null })}
                                                className="w-24 bg-secondary border border-border rounded px-1 py-0.5 text-right text-foreground"
                                                placeholder="0"
                                            />
                                        </div>
                                    ) : (
                                        <span className="font-bold text-foreground">
                                            {customer.creditLimitAmount ? `₹${customer.creditLimitAmount.toLocaleString()}` : "Unlimited"}
                                        </span>
                                    )}
                                </div>
                            </div>

                            <div className="grid grid-cols-2 gap-4 pt-4 border-t border-border">
                                <div>
                                    <p className="text-xs text-muted-foreground">Allocated to Vehicles</p>
                                    <p className="text-lg font-bold text-foreground">{allocatedLiters} L</p>
                                </div>
                                <div>
                                    <p className="text-xs text-muted-foreground">Remaining to Allocate</p>
                                    <p className={`text-lg font-bold ${remainingToAllocate < 0 ? 'text-destructive' : 'text-green-500'}`}>
                                        {creditLimit > 0 ? `${remainingToAllocate} L` : "N/A"}
                                    </p>
                                </div>
                            </div>
                        </div>
                    </GlassCard>
                </div>

                {/* Right Column: Quick Info */}
                <div className="space-y-6">
                    <GlassCard className="p-6">
                        <h3 className="text-lg font-semibold text-foreground mb-4">Quick Info</h3>
                        <div className="space-y-4">
                            <div className="flex items-center justify-between">
                                <span className="text-sm text-muted-foreground">Status</span>
                                <Badge variant={statusVariant(customerStatus)}>{statusLabel(customerStatus)}</Badge>
                            </div>
                            <div className="flex items-center justify-between">
                                <span className="text-sm text-muted-foreground">Total Vehicles</span>
                                <span className="text-sm font-bold text-foreground">{vehicles.length}</span>
                            </div>
                            <div className="flex items-center justify-between">
                                <span className="text-sm text-muted-foreground">Active Vehicles</span>
                                <span className="text-sm font-bold text-green-500">
                                    {vehicles.filter((v: any) => v.status === "ACTIVE").length}
                                </span>
                            </div>
                            <div className="flex items-center justify-between">
                                <span className="text-sm text-muted-foreground">Blocked Vehicles</span>
                                <span className="text-sm font-bold text-destructive">
                                    {vehicles.filter((v: any) => v.status !== "ACTIVE" && v.status != null).length}
                                </span>
                            </div>
                            <div className="flex items-center justify-between">
                                <span className="text-sm text-muted-foreground">Customer ID</span>
                                <span className="text-sm font-mono text-foreground">#{customer.id}</span>
                            </div>
                        </div>
                    </GlassCard>
                    <GlassCard className="p-6">
                        <h3 className="text-lg font-semibold text-foreground mb-4 flex items-center gap-2">
                            <Globe className="w-5 h-5 text-primary" /> Location
                        </h3>
                        {isEditing ? (
                            <div className="space-y-3">
                                <div className="flex items-center gap-2">
                                    <label className="text-sm text-muted-foreground w-20">Latitude</label>
                                    <input
                                        type="number"
                                        step="0.0000001"
                                        value={customer.latitude || ""}
                                        onChange={(e) => setCustomer({ ...customer, latitude: e.target.value ? parseFloat(e.target.value) : null })}
                                        className="flex-1 bg-secondary border border-border rounded px-2 py-1 text-sm text-foreground"
                                        placeholder="e.g. 13.0827"
                                    />
                                </div>
                                <div className="flex items-center gap-2">
                                    <label className="text-sm text-muted-foreground w-20">Longitude</label>
                                    <input
                                        type="number"
                                        step="0.0000001"
                                        value={customer.longitude || ""}
                                        onChange={(e) => setCustomer({ ...customer, longitude: e.target.value ? parseFloat(e.target.value) : null })}
                                        className="flex-1 bg-secondary border border-border rounded px-2 py-1 text-sm text-foreground"
                                        placeholder="e.g. 80.2707"
                                    />
                                </div>
                            </div>
                        ) : customer.latitude && customer.longitude ? (
                            <div className="space-y-3">
                                <div className="text-sm text-muted-foreground">
                                    {Number(customer.latitude).toFixed(6)}, {Number(customer.longitude).toFixed(6)}
                                </div>
                                <div className="rounded-lg overflow-hidden border border-border">
                                    <iframe
                                        width="100%"
                                        height="200"
                                        style={{ border: 0 }}
                                        loading="lazy"
                                        src={`https://www.openstreetmap.org/export/embed.html?bbox=${Number(customer.longitude) - 0.01},${Number(customer.latitude) - 0.01},${Number(customer.longitude) + 0.01},${Number(customer.latitude) + 0.01}&layer=mapnik&marker=${customer.latitude},${customer.longitude}`}
                                    />
                                </div>
                            </div>
                        ) : (
                            <p className="text-sm text-muted-foreground">No location set</p>
                        )}
                    </GlassCard>
                    <GlassCard className="p-6">
                        <h3 className="text-lg font-semibold text-foreground mb-4">Statement Preferences</h3>
                        <div className="space-y-3">
                            <div className="flex items-center justify-between">
                                <span className="text-sm text-muted-foreground">Frequency</span>
                                {isEditing ? (
                                    <StyledSelect
                                        value={customer.statementFrequency || ""}
                                        onChange={(val) => setCustomer({ ...customer, statementFrequency: val || null })}
                                        options={[
                                            { value: "", label: "Not set" },
                                            { value: "MONTHLY", label: "Monthly" },
                                            { value: "BIWEEKLY", label: "Biweekly" },
                                            { value: "WEEKLY", label: "Weekly" },
                                            { value: "CUSTOM", label: "Custom" },
                                        ]}
                                        placeholder="Not set"
                                        className="min-w-[140px]"
                                    />
                                ) : (
                                    <span className="text-sm font-medium text-foreground">{customer.statementFrequency || "Not set"}</span>
                                )}
                            </div>
                            <div className="flex items-center justify-between">
                                <span className="text-sm text-muted-foreground">Grouping</span>
                                {isEditing ? (
                                    <StyledSelect
                                        value={customer.statementGrouping || ""}
                                        onChange={(val) => setCustomer({ ...customer, statementGrouping: val || null })}
                                        options={[
                                            { value: "", label: "Not set" },
                                            { value: "CUSTOMER_WISE", label: "Customer Wise" },
                                            { value: "VEHICLE_WISE", label: "Vehicle Wise" },
                                            { value: "BILL_WISE", label: "Bill Wise" },
                                        ]}
                                        placeholder="Not set"
                                        className="min-w-[140px]"
                                    />
                                ) : (
                                    <span className="text-sm font-medium text-foreground">{customer.statementGrouping?.replace(/_/g, ' ') || "Not set"}</span>
                                )}
                            </div>
                        </div>
                    </GlassCard>
                </div>
            </div>

            {/* Vehicles Section - Full Width */}
            <GlassCard className="p-6">
                <div className="flex items-center justify-between mb-6">
                    <div>
                        <h3 className="text-lg font-semibold text-foreground">Associated Vehicles</h3>
                        {creditLimit > 0 && (
                            <p className="text-xs text-muted-foreground mt-1">
                                Liter budget: {allocatedLiters} / {creditLimit} L allocated ({remainingToAllocate} L remaining)
                            </p>
                        )}
                    </div>
                    <div className="flex gap-3">
                        <button
                            onClick={() => {
                                const link = document.createElement("a");
                                link.href = `${API}/customers/${params.id}/vehicle-report/pdf`;
                                link.target = "_blank";
                                link.click();
                            }}
                            disabled={vehicles.length === 0}
                            className="px-4 py-2 bg-secondary text-secondary-foreground rounded-lg text-sm font-medium hover:bg-secondary/80 transition-colors flex items-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed"
                        >
                            <Download className="w-4 h-4" />
                            Vehicle Report
                        </button>
                        <button
                            onClick={() => { setShowAddVehicle(true); setVehicleError(""); }}
                            className="px-4 py-2 bg-primary text-primary-foreground rounded-lg text-sm font-medium hover:bg-primary/90 transition-colors flex items-center gap-2"
                        >
                            <Truck className="w-4 h-4" />
                            Add Vehicle
                        </button>
                    </div>
                </div>

                <div className="overflow-x-auto">
                    <table className="w-full text-left">
                        <thead>
                            <tr className="border-b border-border">
                                <th className="pb-3 font-medium text-muted-foreground text-sm">Vehicle Number</th>
                                <th className="pb-3 font-medium text-muted-foreground text-sm">Type</th>
                                <th className="pb-3 font-medium text-muted-foreground text-sm">Fuel</th>
                                <th className="pb-3 font-medium text-muted-foreground text-sm">Capacity</th>
                                <th className="pb-3 font-medium text-muted-foreground text-sm">Monthly Limit</th>
                                <th className="pb-3 font-medium text-muted-foreground text-sm">Consumed</th>
                                <th className="pb-3 font-medium text-muted-foreground text-sm">Status</th>
                                <th className="pb-3 font-medium text-muted-foreground text-sm text-right">Actions</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-border">
                            {vehicles.map((vehicle: any) => {
                                const vLimit = vehicle.maxLitersPerMonth || 0;
                                const vConsumed = vehicle.consumedLiters || 0;
                                const vPct = vLimit > 0 ? (vConsumed / vLimit) * 100 : 0;
                                const vStatus = vehicle.status || "ACTIVE";

                                return (
                                    <tr key={vehicle.id} className="group hover:bg-muted/30 transition-colors">
                                        <td className="py-4 text-sm font-medium text-foreground uppercase">{vehicle.vehicleNumber}</td>
                                        <td className="py-4 text-sm text-muted-foreground">{vehicle.vehicleType?.name || "-"}</td>
                                        <td className="py-4 text-sm text-muted-foreground">{vehicle.preferredProduct?.name || "-"}</td>
                                        <td className="py-4 text-sm text-muted-foreground">{vehicle.maxCapacity ? `${vehicle.maxCapacity} L` : "-"}</td>
                                        <td className="py-4 text-sm text-muted-foreground">{vLimit > 0 ? `${vLimit} L` : "No limit"}</td>
                                        <td className="py-4">
                                            {vLimit > 0 ? (
                                                <div className="w-24">
                                                    <div className="flex justify-between text-xs mb-1">
                                                        <span className="text-muted-foreground">{vConsumed} L</span>
                                                    </div>
                                                    <div className="h-1.5 bg-secondary rounded-full overflow-hidden">
                                                        <div
                                                            className={`h-full rounded-full ${vPct >= 100 ? 'bg-destructive' : vPct > 80 ? 'bg-yellow-500' : 'bg-accent'}`}
                                                            style={{ width: `${Math.min(vPct, 100)}%` }}
                                                        />
                                                    </div>
                                                </div>
                                            ) : (
                                                <span className="text-sm text-muted-foreground">{vConsumed > 0 ? `${vConsumed} L` : "-"}</span>
                                            )}
                                        </td>
                                        <td className="py-4">
                                            <Badge variant={statusVariant(vStatus)}>{statusLabel(vStatus)}</Badge>
                                        </td>
                                        <td className="py-4 text-right">
                                            <div className="flex justify-end gap-2">
                                                <button
                                                    onClick={() => handleToggleVehicleStatus(vehicle.id)}
                                                    className={`text-xs px-2 py-1 rounded font-medium transition-colors ${
                                                        vStatus === "ACTIVE"
                                                            ? "text-yellow-500 hover:bg-yellow-500/10"
                                                            : "text-green-500 hover:bg-green-500/10"
                                                    }`}
                                                >
                                                    {vStatus === "ACTIVE" ? "Block" : "Unblock"}
                                                </button>
                                                <button
                                                    onClick={() => handleDeleteVehicle(vehicle.id)}
                                                    className="text-destructive hover:text-destructive/80 text-xs font-medium px-2 py-1 rounded hover:bg-destructive/10 transition-colors"
                                                >
                                                    Delete
                                                </button>
                                            </div>
                                        </td>
                                    </tr>
                                );
                            })}
                            {vehicles.length === 0 && (
                                <tr>
                                    <td colSpan={8} className="py-8 text-center text-muted-foreground text-sm">
                                        No vehicles associated with this customer.
                                    </td>
                                </tr>
                            )}
                        </tbody>
                    </table>
                </div>
            </GlassCard>

            {/* Incentives Section */}
            <GlassCard className="p-6">
                <div className="flex items-center justify-between mb-6">
                    <div>
                        <h3 className="text-lg font-semibold text-foreground flex items-center gap-2">
                            <Tag className="w-5 h-5 text-primary" /> Incentives / Discounts
                        </h3>
                        <p className="text-xs text-muted-foreground mt-1">
                            Per-product discounts automatically applied during invoice creation
                        </p>
                    </div>
                    <button
                        onClick={() => { setShowAddIncentive(true); setIncentiveError(""); }}
                        className="px-4 py-2 bg-primary text-primary-foreground rounded-lg text-sm font-medium hover:bg-primary/90 transition-colors flex items-center gap-2"
                    >
                        <Plus className="w-4 h-4" /> Add Incentive
                    </button>
                </div>
                <div className="overflow-x-auto">
                    <table className="w-full text-left">
                        <thead>
                            <tr className="border-b border-border">
                                <th className="pb-3 font-medium text-muted-foreground text-sm">Product</th>
                                <th className="pb-3 font-medium text-muted-foreground text-sm">Min Quantity</th>
                                <th className="pb-3 font-medium text-muted-foreground text-sm">Discount Rate</th>
                                <th className="pb-3 font-medium text-muted-foreground text-sm">Status</th>
                                <th className="pb-3 font-medium text-muted-foreground text-sm text-right">Actions</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-border">
                            {customerIncentives.map((inc: any) => (
                                <tr key={inc.id} className="group hover:bg-muted/30 transition-colors">
                                    <td className="py-4 text-sm font-medium text-foreground">{inc.product?.name || "Unknown"}</td>
                                    <td className="py-4 text-sm text-muted-foreground">{inc.minQuantity != null ? `${inc.minQuantity} ${inc.product?.unit || ""}` : "No minimum"}</td>
                                    <td className="py-4 text-sm font-medium text-emerald-500">₹{Number(inc.discountRate).toFixed(2)} / unit</td>
                                    <td className="py-4">
                                        <Badge variant={inc.active ? "success" : "warning"}>
                                            {inc.active ? "Active" : "Inactive"}
                                        </Badge>
                                    </td>
                                    <td className="py-4 text-right">
                                        <div className="flex justify-end gap-2">
                                            <button
                                                onClick={async () => {
                                                    try {
                                                        await updateIncentive(inc.id, { ...inc, active: !inc.active });
                                                        fetchIncentives();
                                                    } catch (e) { console.error(e); }
                                                }}
                                                className={`text-xs px-2 py-1 rounded font-medium transition-colors ${
                                                    inc.active ? "text-yellow-500 hover:bg-yellow-500/10" : "text-green-500 hover:bg-green-500/10"
                                                }`}
                                            >
                                                {inc.active ? "Deactivate" : "Activate"}
                                            </button>
                                            <button
                                                onClick={async () => {
                                                    if (confirm("Remove this incentive?")) {
                                                        try {
                                                            await deleteIncentive(inc.id);
                                                            fetchIncentives();
                                                        } catch (e) { console.error(e); }
                                                    }
                                                }}
                                                className="text-destructive hover:text-destructive/80 text-xs font-medium px-2 py-1 rounded hover:bg-destructive/10 transition-colors"
                                            >
                                                Delete
                                            </button>
                                        </div>
                                    </td>
                                </tr>
                            ))}
                            {customerIncentives.length === 0 && (
                                <tr>
                                    <td colSpan={5} className="py-8 text-center text-muted-foreground text-sm">
                                        No incentives configured for this customer.
                                    </td>
                                </tr>
                            )}
                        </tbody>
                    </table>
                </div>
            </GlassCard>

            {/* Add Incentive Modal */}
            {showAddIncentive && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm">
                    <GlassCard className="w-full max-w-md p-6 relative">
                        <button onClick={() => setShowAddIncentive(false)} className="absolute top-4 right-4 text-muted-foreground hover:text-foreground">
                            <X className="w-5 h-5" />
                        </button>
                        <h2 className="text-xl font-bold text-foreground mb-6">Add Incentive</h2>
                        <div className="space-y-4">
                            <div>
                                <label className="block text-sm font-medium text-muted-foreground mb-1">Product</label>
                                <StyledSelect
                                    value={newIncentive.productId}
                                    onChange={(val) => setNewIncentive({ ...newIncentive, productId: val })}
                                    options={[
                                        { value: "", label: "Select Product" },
                                        ...allProducts
                                            .filter(p => !customerIncentives.some((inc: any) => inc.product?.id === p.id && inc.active))
                                            .map(p => ({ value: String(p.id), label: `${p.name} (${p.category})` })),
                                    ]}
                                    placeholder="Select Product"
                                    className="w-full"
                                />
                            </div>
                            <div>
                                <label className="block text-sm font-medium text-muted-foreground mb-1">Min Quantity (optional)</label>
                                <input
                                    type="number"
                                    step="0.01"
                                    value={newIncentive.minQuantity}
                                    onChange={(e) => setNewIncentive({ ...newIncentive, minQuantity: e.target.value })}
                                    className="w-full bg-secondary border border-border rounded-lg px-4 py-2 text-foreground focus:outline-none focus:border-cyan-500"
                                    placeholder="Leave empty for no minimum"
                                />
                            </div>
                            <div>
                                <label className="block text-sm font-medium text-muted-foreground mb-1">Discount Rate (₹ per unit)</label>
                                <input
                                    type="number"
                                    step="0.01"
                                    value={newIncentive.discountRate}
                                    onChange={(e) => setNewIncentive({ ...newIncentive, discountRate: e.target.value })}
                                    className="w-full bg-secondary border border-border rounded-lg px-4 py-2 text-foreground focus:outline-none focus:border-cyan-500"
                                    placeholder="e.g. 0.50"
                                />
                            </div>
                            {incentiveError && (
                                <div className="text-destructive text-sm bg-destructive/10 rounded-lg p-3">{incentiveError}</div>
                            )}
                            <button
                                onClick={async () => {
                                    setIncentiveError("");
                                    if (!newIncentive.productId || !newIncentive.discountRate) {
                                        setIncentiveError("Product and discount rate are required");
                                        return;
                                    }
                                    try {
                                        await createIncentive({
                                            customer: { id: Number(params.id) },
                                            product: { id: Number(newIncentive.productId) },
                                            minQuantity: newIncentive.minQuantity ? Number(newIncentive.minQuantity) : undefined,
                                            discountRate: Number(newIncentive.discountRate),
                                            active: true,
                                        });
                                        setShowAddIncentive(false);
                                        setNewIncentive({ productId: "", minQuantity: "", discountRate: "" });
                                        fetchIncentives();
                                    } catch (e: any) {
                                        setIncentiveError(e.message || "Failed to create incentive");
                                    }
                                }}
                                disabled={!newIncentive.productId || !newIncentive.discountRate}
                                className="w-full py-2 bg-cyan-500 text-white rounded-lg font-bold hover:bg-cyan-600 transition-colors mt-4 disabled:opacity-50"
                            >
                                Add Incentive
                            </button>
                        </div>
                    </GlassCard>
                </div>
            )}

            {/* Add Vehicle Modal */}
            {showAddVehicle && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm">
                    <GlassCard className="w-full max-w-md p-6 relative">
                        <button onClick={() => setShowAddVehicle(false)} className="absolute top-4 right-4 text-muted-foreground hover:text-foreground">
                            <X className="w-5 h-5" />
                        </button>
                        <h2 className="text-xl font-bold text-foreground mb-6">Add New Vehicle</h2>
                        <div className="space-y-4">
                            <div>
                                <label className="block text-sm font-medium text-muted-foreground mb-1">Vehicle Number</label>
                                <input
                                    type="text"
                                    value={newVehicle.vehicleNumber}
                                    onChange={(e) => setNewVehicle({ ...newVehicle, vehicleNumber: e.target.value.toUpperCase() })}
                                    className="w-full bg-secondary border border-border rounded-lg px-4 py-2 text-foreground focus:outline-none focus:border-cyan-500 uppercase"
                                    placeholder="e.g. TN 28 BZ 5131"
                                />
                            </div>
                            <div>
                                <label className="block text-sm font-medium text-muted-foreground mb-1">Vehicle Type</label>
                                <StyledSelect
                                    value={newVehicle.vehicleTypeId}
                                    onChange={(val) => setNewVehicle({ ...newVehicle, vehicleTypeId: val })}
                                    options={[
                                        { value: "", label: "Select Type" },
                                        ...vehicleTypes.map(vt => ({ value: String(vt.id), label: vt.name || "" })),
                                    ]}
                                    placeholder="Select Type"
                                    className="w-full"
                                />
                            </div>
                            <div>
                                <label className="block text-sm font-medium text-muted-foreground mb-1">Fuel Type</label>
                                <StyledSelect
                                    value={newVehicle.fuelType}
                                    onChange={(val) => setNewVehicle({ ...newVehicle, fuelType: val })}
                                    options={[
                                        { value: "", label: "Select Fuel" },
                                        ...products.map(p => ({ value: String(p.id), label: p.name })),
                                    ]}
                                    placeholder="Select Fuel"
                                    className="w-full"
                                />
                            </div>
                            <div>
                                <label className="block text-sm font-medium text-muted-foreground mb-1">Max Capacity (tank size in liters)</label>
                                <input
                                    type="number"
                                    value={newVehicle.maxCapacity}
                                    onChange={(e) => setNewVehicle({ ...newVehicle, maxCapacity: e.target.value })}
                                    className="w-full bg-secondary border border-border rounded-lg px-4 py-2 text-foreground focus:outline-none focus:border-cyan-500"
                                    placeholder="e.g. 500"
                                />
                            </div>
                            <div>
                                <label className="block text-sm font-medium text-muted-foreground mb-1">
                                    Monthly Liter Limit
                                    {creditLimit > 0 && (
                                        <span className="text-xs text-muted-foreground ml-2">
                                            ({remainingToAllocate} L remaining to allocate)
                                        </span>
                                    )}
                                </label>
                                <input
                                    type="number"
                                    value={newVehicle.maxLitersPerMonth}
                                    onChange={(e) => setNewVehicle({ ...newVehicle, maxLitersPerMonth: e.target.value })}
                                    className="w-full bg-secondary border border-border rounded-lg px-4 py-2 text-foreground focus:outline-none focus:border-cyan-500"
                                    placeholder="e.g. 200"
                                />
                            </div>
                            {vehicleError && (
                                <div className="text-destructive text-sm bg-destructive/10 rounded-lg p-3">
                                    {vehicleError}
                                </div>
                            )}
                            <button
                                onClick={handleAddVehicle}
                                disabled={!newVehicle.vehicleNumber}
                                className="w-full py-2 bg-cyan-500 text-white rounded-lg font-bold hover:bg-cyan-600 transition-colors mt-4 disabled:opacity-50"
                            >
                                Add Vehicle
                            </button>
                        </div>
                    </GlassCard>
                </div>
            )}
        </div>
    );
}
