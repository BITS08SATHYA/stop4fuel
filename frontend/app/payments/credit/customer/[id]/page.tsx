"use client";

import { useState, useEffect, useCallback } from "react";
import { useParams, useRouter } from "next/navigation";
import { GlassCard } from "@/components/ui/glass-card";
import { Badge } from "@/components/ui/badge";
import { CreditHealthBadge } from "@/components/customers/CreditHealthBadge";
import { BlockHistory } from "@/components/customers/BlockHistory";
import {
    ArrowLeft, Phone, Mail, MapPin, Building2, ShieldAlert, ShieldCheck,
    IndianRupee, Truck, Calendar, Receipt, FileText, Lock, Unlock,
    Save, ChevronLeft, ChevronRight, Clock, Edit2, ExternalLink, Download
} from "lucide-react";
import Link from "next/link";
import {
    getCreditHealth, blockCustomer, unblockCustomer, updateCustomerCreditLimits,
    type CreditHealth
} from "@/lib/api/station/customers";
import { generateStatementPdf, getStatementPdfUrl } from "@/lib/api/station/payments";
import { API_BASE_URL } from "@/lib/api/station";
import { fetchWithAuth } from "@/lib/api/fetch-with-auth";
import { PermissionGate } from "@/components/permission-gate";
import { useAuth } from "@/lib/auth/auth-context";

const API = API_BASE_URL;

interface CustomerFull {
    id: number;
    name: string;
    username?: string;
    address?: string;
    emails: string[];
    phoneNumbers: string[];
    status?: string;
    joinDate?: string;
    creditLimitAmount?: number | null;
    creditLimitLiters?: number | null;
    consumedLiters?: number;
    gstNumber?: string | null;
    statementFrequency?: string | null;
    statementGrouping?: string | null;
    lastBlockedAt?: string | null;
    blockCount?: number;
    forceUnblocked?: boolean;
    forceUnblockedBy?: string | null;
    forceUnblockedAt?: string | null;
    group?: { id: number; groupName?: string } | null;
    customerCategory?: { id: number; categoryName?: string; categoryType?: string } | null;
}

interface Vehicle {
    id: number;
    vehicleNumber: string;
    vehicleType?: { id: number; name?: string };
    preferredProduct?: { id: number; name?: string };
    maxCapacity?: number;
    maxLitersPerMonth?: number;
    consumedLiters?: number;
    status?: string;
}

interface InvoiceBill {
    id: number;
    billNo?: string;
    date: string;
    billType: string;
    paymentStatus: string;
    netAmount: number;
    grossAmount?: number;
    totalDiscount?: number;
    customer?: { id: number; name: string };
    vehicle?: { id: number; vehicleNumber: string };
    products?: { product?: { name: string }; quantity?: number; amount?: number }[];
}

interface Statement {
    id: number;
    statementNo: string;
    fromDate: string;
    toDate: string;
    statementDate: string;
    netAmount: number;
    receivedAmount: number;
    balanceAmount: number;
    status: string;
    numberOfBills: number;
}

type TabId = "invoices" | "statements" | "payments" | "vehicles";

export default function CreditCustomerProfilePage() {
    const params = useParams();
    const router = useRouter();
    const { user } = useAuth();
    const customerId = Number(params.id);

    const [customer, setCustomer] = useState<CustomerFull | null>(null);
    const [health, setHealth] = useState<CreditHealth | null>(null);
    const [vehicles, setVehicles] = useState<Vehicle[]>([]);
    const [loading, setLoading] = useState(true);

    // Tabs
    const [activeTab, setActiveTab] = useState<TabId>("invoices");

    // Invoice pagination
    const [invoices, setInvoices] = useState<InvoiceBill[]>([]);
    const [invoicePage, setInvoicePage] = useState(0);
    const [invoiceTotal, setInvoiceTotal] = useState(0);
    const [invoiceLoading, setInvoiceLoading] = useState(false);

    // Statement list
    const [statements, setStatements] = useState<Statement[]>([]);
    const [stmtLoading, setStmtLoading] = useState(false);

    // Payments
    const [payments, setPayments] = useState<any[]>([]);
    const [paymentPage, setPaymentPage] = useState(0);
    const [paymentTotal, setPaymentTotal] = useState(0);
    const [paymentLoading, setPaymentLoading] = useState(false);

    // Edit credit limits
    const [editingLimits, setEditingLimits] = useState(false);
    const [limitAmount, setLimitAmount] = useState("");
    const [limitLiters, setLimitLiters] = useState("");

    const PAGE_SIZE = 15;

    // ── Load customer + health + vehicles ──
    const loadCore = useCallback(async () => {
        setLoading(true);
        try {
            const [custRes, vehiclesRes, healthData] = await Promise.all([
                fetchWithAuth(`${API}/customers/${customerId}`).then(r => r.ok ? r.json() : null),
                fetchWithAuth(`${API}/customers/${customerId}/vehicles`).then(r => r.ok ? r.json() : []),
                getCreditHealth(customerId),
            ]);
            setCustomer(custRes);
            setVehicles(vehiclesRes);
            setHealth(healthData);
            if (custRes) {
                setLimitAmount(custRes.creditLimitAmount?.toString() || "");
                setLimitLiters(custRes.creditLimitLiters?.toString() || "");
            }
        } catch (e) { console.error("Failed to load", e); }
        finally { setLoading(false); }
    }, [customerId]);

    // ── Load invoices (paginated) ──
    const loadInvoices = useCallback(async (page: number) => {
        setInvoiceLoading(true);
        try {
            const res = await fetchWithAuth(
                `${API}/invoices/customer/${customerId}?page=${page}&size=${PAGE_SIZE}&billType=CREDIT`
            );
            if (res.ok) {
                const data = await res.json();
                setInvoices(data.content || []);
                setInvoiceTotal(data.totalPages || 0);
                setInvoicePage(page);
            }
        } catch (e) { console.error("Failed to load invoices", e); }
        finally { setInvoiceLoading(false); }
    }, [customerId]);

    // ── Load statements ──
    const loadStatements = useCallback(async () => {
        setStmtLoading(true);
        try {
            const res = await fetchWithAuth(`${API}/statements/customer/${customerId}`);
            if (res.ok) setStatements(await res.json());
        } catch (e) { console.error("Failed to load statements", e); }
        finally { setStmtLoading(false); }
    }, [customerId]);

    // ── Load payments (paginated) ──
    const loadPayments = useCallback(async (page: number) => {
        setPaymentLoading(true);
        try {
            const res = await fetchWithAuth(
                `${API}/payments/customer/${customerId}?page=${page}&size=${PAGE_SIZE}`
            );
            if (res.ok) {
                const data = await res.json();
                setPayments(data.content || []);
                setPaymentTotal(data.totalPages || 0);
                setPaymentPage(page);
            }
        } catch (e) { console.error("Failed to load payments", e); }
        finally { setPaymentLoading(false); }
    }, [customerId]);

    useEffect(() => { loadCore(); }, [loadCore]);
    useEffect(() => {
        if (activeTab === "invoices") loadInvoices(0);
        else if (activeTab === "statements") loadStatements();
        else if (activeTab === "payments") loadPayments(0);
    }, [activeTab, loadInvoices, loadStatements, loadPayments]);

    // ── Handlers ──
    const handleBlockToggle = async () => {
        if (!customer) return;
        const notes = prompt(customer.status === "BLOCKED" ? "Unblock reason (optional):" : "Block reason (optional):");
        try {
            if (customer.status === "BLOCKED") await unblockCustomer(customerId, notes || undefined);
            else await blockCustomer(customerId, notes || undefined);
            loadCore();
        } catch (e: any) { alert(e.message || "Failed"); }
    };

    const handleSaveLimits = async () => {
        try {
            await updateCustomerCreditLimits(customerId, {
                creditLimitAmount: limitAmount ? parseFloat(limitAmount) : null,
                creditLimitLiters: limitLiters ? parseFloat(limitLiters) : null,
            });
            setEditingLimits(false);
            loadCore();
        } catch (e: any) { alert(e.message || "Failed to save"); }
    };

    const handleToggleVehicleStatus = async (vehicleId: number) => {
        try {
            await fetchWithAuth(`${API}/vehicles/${vehicleId}/toggle-status`, { method: "PATCH" });
            loadCore();
        } catch (e) { console.error("Failed", e); }
    };

    const handleForceUnblock = async () => {
        if (!customer) return;
        const currentlyForced = !!customer.forceUnblocked;
        try {
            const res = await fetchWithAuth(`${API}/customers/${customerId}/force-unblock`, {
                method: "PATCH",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ enabled: !currentlyForced, byUser: user?.name || "Unknown" }),
            });
            if (res.ok) {
                loadCore();
            }
        } catch (e) { console.error("Failed to toggle force unblock", e); }
    };

    const fmt = (n: number) => Number(n).toLocaleString("en-IN", { minimumFractionDigits: 2 });
    const fmtCurrency = (n: number) => Number(n).toLocaleString("en-IN", { style: "currency", currency: "INR" });
    const fmtDate = (d: string) => new Date(d).toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "numeric" });

    if (loading) {
        return (
            <div className="p-6 flex items-center justify-center min-h-screen">
                <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary" />
            </div>
        );
    }
    if (!customer || !health) return <div className="p-8 text-muted-foreground">Customer not found.</div>;

    const isStatementCustomer = !!customer.statementFrequency;

    return (
        <div className="p-4 h-screen bg-background overflow-hidden flex flex-col">
            <div className="flex flex-col flex-1 min-h-0 space-y-3">
                {/* ── Header ── */}
                <div className="flex items-center justify-between">
                    <div className="flex items-center gap-3">
                        <button onClick={() => router.back()} className="p-1.5 rounded-lg hover:bg-muted transition-colors">
                            <ArrowLeft className="w-5 h-5 text-muted-foreground" />
                        </button>
                        <div>
                            <div className="flex items-center gap-2">
                                <h1 className="text-xl font-bold text-foreground">{customer.name}</h1>
                                <CreditHealthBadge riskLevel={health.riskLevel} utilizationPercent={health.utilizationPercent} oldestUnpaidDays={health.oldestUnpaidDays} size="md" />
                                <Badge variant={customer.status === "ACTIVE" ? "success" : customer.status === "BLOCKED" ? "danger" : "warning"}>
                                    {customer.status || "ACTIVE"}
                                </Badge>
                            </div>
                            <div className="flex items-center gap-2 text-xs text-muted-foreground mt-0.5">
                                {customer.customerCategory?.categoryName && <span>{customer.customerCategory.categoryName}</span>}
                                {customer.group?.groupName && <span>&middot; {customer.group.groupName}</span>}
                                <span>&middot; {isStatementCustomer ? `Statement (${customer.statementFrequency})` : "Local/Credit"}</span>
                                {customer.gstNumber && <span>&middot; GST: {customer.gstNumber}</span>}
                            </div>
                        </div>
                    </div>
                    <PermissionGate permission="CUSTOMER_UPDATE">
                        <button
                            onClick={handleBlockToggle}
                            className={`text-xs px-3 py-1.5 rounded-lg border flex items-center gap-1.5 transition-colors ${
                                customer.status === "BLOCKED"
                                    ? "bg-emerald-500/10 text-emerald-400 border-emerald-500/30 hover:bg-emerald-500/20"
                                    : "bg-rose-500/10 text-rose-400 border-rose-500/30 hover:bg-rose-500/20"
                            }`}
                        >
                            {customer.status === "BLOCKED" ? <><Unlock className="w-3 h-3" /> Unblock</> : <><Lock className="w-3 h-3" /> Block</>}
                        </button>
                    </PermissionGate>
                    {user?.role === "OWNER" && (
                        <button
                            onClick={handleForceUnblock}
                            className={`text-xs px-3 py-1.5 rounded-lg border flex items-center gap-1.5 transition-colors ${
                                customer.forceUnblocked
                                    ? "bg-orange-500/10 text-orange-500 border-orange-500/30 hover:bg-orange-500/20"
                                    : "bg-orange-500/5 text-orange-400 border-orange-500/20 hover:bg-orange-500/10"
                            }`}
                        >
                            <ShieldAlert className="w-3 h-3" />
                            {customer.forceUnblocked ? "Revoke Force Unblock" : "Force Unblock"}
                        </button>
                    )}
                </div>

                {/* Force Unblocked banner */}
                {customer.forceUnblocked && (
                    <div className="rounded-lg p-3 flex items-center gap-2 bg-orange-500/10 border border-orange-500/20 text-orange-500">
                        <ShieldAlert className="w-4 h-4 shrink-0" />
                        <span className="text-xs font-medium">
                            Credit checks bypassed — Force Unblocked by {customer.forceUnblockedBy || "Unknown"}
                            {customer.forceUnblockedAt && (
                                <> on {new Date(customer.forceUnblockedAt).toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "numeric" })}</>
                            )}
                        </span>
                    </div>
                )}

                {/* ── KPI Horizontal Bar ── */}
                <div className="grid grid-cols-6 gap-2">
                    <GlassCard className="!p-3 text-center">
                        <div className="text-[9px] text-blue-400 uppercase">Credit Limit</div>
                        <div className="text-sm font-bold text-foreground">{fmtCurrency(health.creditLimit)}</div>
                        {customer.creditLimitLiters != null && customer.creditLimitLiters > 0 && (
                            <div className="text-[9px] text-muted-foreground">{customer.creditLimitLiters} L</div>
                        )}
                    </GlassCard>
                    <GlassCard className="!p-3 text-center">
                        <div className="text-[9px] text-violet-400 uppercase">Total Billed</div>
                        <div className="text-sm font-bold text-foreground">{fmtCurrency(health.totalBilled)}</div>
                    </GlassCard>
                    <GlassCard className="!p-3 text-center">
                        <div className="text-[9px] text-emerald-400 uppercase">Total Paid</div>
                        <div className="text-sm font-bold text-emerald-400">{fmtCurrency(health.totalPaid)}</div>
                    </GlassCard>
                    <GlassCard className={`!p-3 text-center ${health.utilizationPercent >= 100 ? "border-rose-500/30" : health.utilizationPercent >= 80 ? "border-amber-500/30" : ""}`}>
                        <div className={`text-[9px] uppercase ${health.utilizationPercent >= 100 ? "text-rose-400" : health.utilizationPercent >= 80 ? "text-amber-400" : "text-muted-foreground"}`}>Balance</div>
                        <div className={`text-sm font-bold ${health.utilizationPercent >= 100 ? "text-rose-400" : health.utilizationPercent >= 80 ? "text-amber-400" : "text-foreground"}`}>
                            {fmtCurrency(health.ledgerBalance)}
                        </div>
                        <div className="text-[9px] text-muted-foreground">{health.utilizationPercent.toFixed(1)}%</div>
                    </GlassCard>
                    <GlassCard className="!p-3 text-center">
                        <div className={`text-[9px] uppercase ${health.oldestUnpaidDays >= 90 ? "text-rose-400" : health.oldestUnpaidDays >= 60 ? "text-amber-400" : "text-muted-foreground"}`}>
                            Oldest Unpaid
                        </div>
                        <div className={`text-sm font-bold ${health.oldestUnpaidDays >= 90 ? "text-rose-400" : health.oldestUnpaidDays >= 60 ? "text-amber-400" : "text-foreground"}`}>
                            {health.oldestUnpaidDays} days
                        </div>
                    </GlassCard>
                    <GlassCard className="!p-3 text-center">
                        <div className="text-[9px] text-muted-foreground uppercase">Consumed</div>
                        <div className="text-sm font-bold text-foreground">
                            {customer.consumedLiters?.toFixed(0) || 0} L
                        </div>
                        <div className="text-[9px] text-muted-foreground">Blocked {health.blockCount}x</div>
                    </GlassCard>
                </div>

                {/* ── Two-Column Layout — fills remaining viewport ── */}
                <div className="grid grid-cols-12 gap-3 flex-1 min-h-0">
                    {/* Left Column (4 cols): Customer Details + Credit Limits + Vehicles */}
                    <div className="col-span-4 space-y-3 overflow-y-auto border-r border-border/30 pr-3">
                        {/* Contact Info */}
                        <GlassCard className="!p-4">
                            <h3 className="text-xs font-medium text-muted-foreground uppercase tracking-wider mb-2">Contact</h3>
                            <div className="space-y-2 text-xs">
                                {customer.phoneNumbers?.length > 0 && (
                                    <div className="flex items-center gap-2">
                                        <Phone className="w-3 h-3 text-muted-foreground" />
                                        <span className="text-foreground">{Array.isArray(customer.phoneNumbers) ? customer.phoneNumbers.join(", ") : customer.phoneNumbers}</span>
                                    </div>
                                )}
                                {customer.emails?.length > 0 && (
                                    <div className="flex items-center gap-2">
                                        <Mail className="w-3 h-3 text-muted-foreground" />
                                        <span className="text-foreground">{Array.isArray(customer.emails) ? customer.emails.join(", ") : customer.emails}</span>
                                    </div>
                                )}
                                {customer.address && (
                                    <div className="flex items-start gap-2">
                                        <MapPin className="w-3 h-3 text-muted-foreground mt-0.5" />
                                        <span className="text-foreground">{customer.address}</span>
                                    </div>
                                )}
                                {customer.joinDate && (
                                    <div className="flex items-center gap-2">
                                        <Calendar className="w-3 h-3 text-muted-foreground" />
                                        <span className="text-foreground">Joined {fmtDate(customer.joinDate)}</span>
                                    </div>
                                )}
                            </div>
                        </GlassCard>

                        {/* Credit Limits (editable) */}
                        <GlassCard className="!p-4">
                            <div className="flex items-center justify-between mb-2">
                                <h3 className="text-xs font-medium text-muted-foreground uppercase tracking-wider">Credit Limits</h3>
                                <PermissionGate permission="CUSTOMER_UPDATE">
                                    {editingLimits ? (
                                        <div className="flex gap-1">
                                            <button onClick={handleSaveLimits} className="text-[10px] px-2 py-0.5 rounded bg-primary text-primary-foreground hover:bg-primary/90">
                                                <Save className="w-3 h-3" />
                                            </button>
                                            <button onClick={() => setEditingLimits(false)} className="text-[10px] px-2 py-0.5 rounded bg-muted text-muted-foreground hover:bg-muted/80">
                                                Cancel
                                            </button>
                                        </div>
                                    ) : (
                                        <button onClick={() => setEditingLimits(true)} className="text-[10px] px-2 py-0.5 rounded bg-muted text-muted-foreground hover:bg-muted/80">
                                            <Edit2 className="w-3 h-3" />
                                        </button>
                                    )}
                                </PermissionGate>
                            </div>
                            <div className="space-y-2">
                                <div className="flex items-center justify-between text-xs">
                                    <span className="text-muted-foreground">Amount Limit</span>
                                    {editingLimits ? (
                                        <input type="number" value={limitAmount} onChange={e => setLimitAmount(e.target.value)}
                                            className="w-28 text-right text-xs bg-muted border border-border rounded px-2 py-0.5 text-foreground" />
                                    ) : (
                                        <span className="font-medium text-foreground">{customer.creditLimitAmount ? fmtCurrency(customer.creditLimitAmount) : "Not set"}</span>
                                    )}
                                </div>
                                <div className="flex items-center justify-between text-xs">
                                    <span className="text-muted-foreground">Liter Limit</span>
                                    {editingLimits ? (
                                        <input type="number" value={limitLiters} onChange={e => setLimitLiters(e.target.value)}
                                            className="w-28 text-right text-xs bg-muted border border-border rounded px-2 py-0.5 text-foreground" />
                                    ) : (
                                        <span className="font-medium text-foreground">{customer.creditLimitLiters ? `${customer.creditLimitLiters} L` : "Not set"}</span>
                                    )}
                                </div>
                                <div className="flex items-center justify-between text-xs">
                                    <span className="text-muted-foreground">Statement Type</span>
                                    <span className="font-medium text-foreground">{customer.statementFrequency || "Local/Credit"}</span>
                                </div>
                                <div className="flex items-center justify-between text-xs">
                                    <span className="text-muted-foreground">Grouping</span>
                                    <span className="font-medium text-foreground">{customer.statementGrouping || "N/A"}</span>
                                </div>
                            </div>
                        </GlassCard>

                        {/* Vehicles */}
                        <GlassCard className="!p-4">
                            <h3 className="text-xs font-medium text-muted-foreground uppercase tracking-wider mb-2 flex items-center gap-1">
                                <Truck className="w-3 h-3" /> Vehicles ({vehicles.length})
                            </h3>
                            {vehicles.length === 0 ? (
                                <div className="text-xs text-muted-foreground">No vehicles attached.</div>
                            ) : (
                                <div className="space-y-2">
                                    {vehicles.map(v => (
                                        <div key={v.id} className="p-2 rounded-lg bg-muted/20 border border-border/50">
                                            <div className="flex items-center justify-between">
                                                <div className="flex items-center gap-1.5">
                                                    <span className="text-xs font-medium text-foreground">{v.vehicleNumber}</span>
                                                    <Badge variant={v.status === "ACTIVE" ? "success" : v.status === "BLOCKED" ? "danger" : "warning"} className="text-[8px] px-1 py-0">
                                                        {v.status || "ACTIVE"}
                                                    </Badge>
                                                </div>
                                                <PermissionGate permission="CUSTOMER_UPDATE">
                                                    <button
                                                        onClick={() => handleToggleVehicleStatus(v.id)}
                                                        className="text-[9px] px-1.5 py-0.5 rounded border border-border text-muted-foreground hover:bg-muted"
                                                    >
                                                        {v.status === "ACTIVE" ? "Block" : "Activate"}
                                                    </button>
                                                </PermissionGate>
                                            </div>
                                            <div className="grid grid-cols-3 gap-1 mt-1 text-[9px]">
                                                <div>
                                                    <span className="text-muted-foreground">Type: </span>
                                                    <span className="text-foreground">{v.vehicleType?.name || "-"}</span>
                                                </div>
                                                <div>
                                                    <span className="text-muted-foreground">Limit: </span>
                                                    <span className="text-foreground">{v.maxLitersPerMonth ? `${v.maxLitersPerMonth} L` : "-"}</span>
                                                </div>
                                                <div>
                                                    <span className="text-muted-foreground">Used: </span>
                                                    <span className={`${v.maxLitersPerMonth && v.consumedLiters && v.consumedLiters >= v.maxLitersPerMonth ? "text-rose-400 font-medium" : "text-foreground"}`}>
                                                        {v.consumedLiters?.toFixed(0) || 0} L
                                                    </span>
                                                </div>
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            )}
                        </GlassCard>

                        {/* Block History */}
                        <GlassCard className="!p-4">
                            <BlockHistory customerId={customerId} />
                        </GlassCard>
                    </div>

                    {/* Right Column (8 cols): Invoices / Statements / Payments tabs */}
                    <div className="col-span-8 flex flex-col overflow-hidden">
                        {/* Tab bar */}
                        <div className="flex items-center gap-1 mb-2 border-b border-border pb-1">
                            {([
                                { id: "invoices" as TabId, label: "Credit Invoices", icon: Receipt },
                                { id: "statements" as TabId, label: "Statements", icon: FileText },
                                { id: "payments" as TabId, label: "Payments", icon: IndianRupee },
                                { id: "vehicles" as TabId, label: "Vehicle Details", icon: Truck },
                            ]).map(tab => (
                                <button
                                    key={tab.id}
                                    onClick={() => setActiveTab(tab.id)}
                                    className={`text-xs px-3 py-1.5 rounded-t-lg flex items-center gap-1 transition-colors ${
                                        activeTab === tab.id
                                            ? "bg-muted text-foreground font-medium border-b-2 border-primary"
                                            : "text-muted-foreground hover:text-foreground hover:bg-muted/50"
                                    }`}
                                >
                                    <tab.icon className="w-3 h-3" />
                                    {tab.label}
                                </button>
                            ))}
                        </div>

                        {/* Tab content */}
                        <div className="flex-1 overflow-y-auto">
                            {activeTab === "invoices" && (
                                <InvoiceTable
                                    invoices={invoices} loading={invoiceLoading}
                                    page={invoicePage} totalPages={invoiceTotal}
                                    onPageChange={loadInvoices} fmt={fmt} fmtDate={fmtDate}
                                />
                            )}
                            {activeTab === "statements" && (
                                <StatementTable
                                    statements={statements} loading={stmtLoading}
                                    fmt={fmt} fmtCurrency={fmtCurrency} fmtDate={fmtDate}
                                />
                            )}
                            {activeTab === "payments" && (
                                <PaymentTable
                                    payments={payments} loading={paymentLoading}
                                    page={paymentPage} totalPages={paymentTotal}
                                    onPageChange={loadPayments} fmt={fmt} fmtDate={fmtDate}
                                />
                            )}
                            {activeTab === "vehicles" && (
                                <VehicleDetailTable vehicles={vehicles} fmt={fmt} />
                            )}
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}

// ── Invoice Table ──
function InvoiceTable({ invoices, loading, page, totalPages, onPageChange, fmt, fmtDate }: {
    invoices: InvoiceBill[]; loading: boolean; page: number; totalPages: number;
    onPageChange: (p: number) => void; fmt: (n: number) => string; fmtDate: (d: string) => string;
}) {
    if (loading) return <div className="flex justify-center p-8"><div className="animate-spin rounded-full h-5 w-5 border-b-2 border-primary" /></div>;
    if (invoices.length === 0) return <div className="text-center text-muted-foreground text-xs p-8">No credit invoices found.</div>;

    return (
        <div>
            <table className="w-full text-xs">
                <thead className="sticky top-0 bg-background">
                    <tr className="text-left text-[10px] uppercase tracking-wider text-muted-foreground border-b border-border">
                        <th className="py-2 px-2">Bill No</th>
                        <th className="py-2 px-2">Date</th>
                        <th className="py-2 px-2">Vehicle</th>
                        <th className="py-2 px-2">Products</th>
                        <th className="py-2 px-2 text-right">Amount</th>
                        <th className="py-2 px-2 text-center">Status</th>
                    </tr>
                </thead>
                <tbody>
                    {invoices.map(inv => (
                        <tr key={inv.id} className="border-b border-border/30 hover:bg-muted/20">
                            <td className="py-1.5 px-2 font-medium">
                                <Link href={`/operations/invoices/explorer?invoiceId=${inv.id}`} className="text-primary hover:underline flex items-center gap-0.5">
                                    {inv.billNo || `#${inv.id}`} <ExternalLink className="w-2.5 h-2.5 opacity-50" />
                                </Link>
                            </td>
                            <td className="py-1.5 px-2 text-muted-foreground">{fmtDate(inv.date)}</td>
                            <td className="py-1.5 px-2 text-muted-foreground">{inv.vehicle?.vehicleNumber || "-"}</td>
                            <td className="py-1.5 px-2 text-muted-foreground truncate max-w-[150px]">
                                {inv.products?.map(p => p.product?.name).filter(Boolean).join(", ") || "-"}
                            </td>
                            <td className="py-1.5 px-2 text-right font-medium text-foreground">{fmt(inv.netAmount)}</td>
                            <td className="py-1.5 px-2 text-center">
                                <Badge variant={inv.paymentStatus === "PAID" ? "success" : inv.paymentStatus === "PARTIAL" ? "warning" : "danger"} className="text-[8px] px-1.5 py-0">
                                    {inv.paymentStatus}
                                </Badge>
                            </td>
                        </tr>
                    ))}
                </tbody>
            </table>
            <Paginator page={page} totalPages={totalPages} onPageChange={onPageChange} />
        </div>
    );
}

// ── Statement Table ──
function StatementTable({ statements, loading, fmt, fmtCurrency, fmtDate }: {
    statements: Statement[]; loading: boolean;
    fmt: (n: number) => string; fmtCurrency: (n: number) => string; fmtDate: (d: string) => string;
}) {
    const [downloadingId, setDownloadingId] = useState<number | null>(null);

    const handleDownloadPdf = async (stmtId: number) => {
        setDownloadingId(stmtId);
        try {
            await generateStatementPdf(stmtId);
            const url = await getStatementPdfUrl(stmtId);
            window.open(url, "_blank");
        } catch (e: any) { alert(e.message || "Failed to download PDF"); }
        finally { setDownloadingId(null); }
    };

    if (loading) return <div className="flex justify-center p-8"><div className="animate-spin rounded-full h-5 w-5 border-b-2 border-primary" /></div>;
    if (statements.length === 0) return <div className="text-center text-muted-foreground text-xs p-8">No statements found.</div>;

    return (
        <table className="w-full text-xs">
            <thead className="sticky top-0 bg-background">
                <tr className="text-left text-[10px] uppercase tracking-wider text-muted-foreground border-b border-border">
                    <th className="py-2 px-2">Statement #</th>
                    <th className="py-2 px-2">Period</th>
                    <th className="py-2 px-2 text-center">Bills</th>
                    <th className="py-2 px-2 text-right">Net Amount</th>
                    <th className="py-2 px-2 text-right">Received</th>
                    <th className="py-2 px-2 text-right">Balance</th>
                    <th className="py-2 px-2 text-center">Status</th>
                    <th className="py-2 px-2 text-center">PDF</th>
                </tr>
            </thead>
            <tbody>
                {statements.map(s => (
                    <tr key={s.id} className="border-b border-border/30 hover:bg-muted/20">
                        <td className="py-1.5 px-2 font-medium">
                            <Link href={`/payments/explorer?statementId=${s.id}`} className="text-primary hover:underline flex items-center gap-0.5">
                                {s.statementNo} <ExternalLink className="w-2.5 h-2.5 opacity-50" />
                            </Link>
                        </td>
                        <td className="py-1.5 px-2 text-muted-foreground">{fmtDate(s.fromDate)} — {fmtDate(s.toDate)}</td>
                        <td className="py-1.5 px-2 text-center text-muted-foreground">{s.numberOfBills}</td>
                        <td className="py-1.5 px-2 text-right font-medium text-foreground">{fmt(s.netAmount)}</td>
                        <td className="py-1.5 px-2 text-right text-emerald-400">{fmt(s.receivedAmount)}</td>
                        <td className="py-1.5 px-2 text-right font-medium">
                            <span className={s.balanceAmount > 0 ? "text-rose-400" : "text-foreground"}>
                                {fmt(s.balanceAmount)}
                            </span>
                        </td>
                        <td className="py-1.5 px-2 text-center">
                            <Badge variant={s.status === "PAID" ? "success" : "danger"} className="text-[8px] px-1.5 py-0">
                                {s.status}
                            </Badge>
                        </td>
                        <td className="py-1.5 px-2 text-center">
                            <button
                                onClick={() => s.id && handleDownloadPdf(s.id)}
                                disabled={downloadingId === s.id}
                                className="p-1 rounded hover:bg-muted text-muted-foreground hover:text-foreground transition-colors disabled:opacity-30"
                                title="Download PDF"
                            >
                                {downloadingId === s.id
                                    ? <div className="animate-spin rounded-full h-3 w-3 border-b border-current" />
                                    : <Download className="w-3 h-3" />
                                }
                            </button>
                        </td>
                    </tr>
                ))}
            </tbody>
        </table>
    );
}

// ── Payment Table ──
function PaymentTable({ payments, loading, page, totalPages, onPageChange, fmt, fmtDate }: {
    payments: any[]; loading: boolean; page: number; totalPages: number;
    onPageChange: (p: number) => void; fmt: (n: number) => string; fmtDate: (d: string) => string;
}) {
    if (loading) return <div className="flex justify-center p-8"><div className="animate-spin rounded-full h-5 w-5 border-b-2 border-primary" /></div>;
    if (payments.length === 0) return <div className="text-center text-muted-foreground text-xs p-8">No payments found.</div>;

    return (
        <div>
            <table className="w-full text-xs">
                <thead className="sticky top-0 bg-background">
                    <tr className="text-left text-[10px] uppercase tracking-wider text-muted-foreground border-b border-border">
                        <th className="py-2 px-2">Date</th>
                        <th className="py-2 px-2 text-right">Amount</th>
                        <th className="py-2 px-2">Mode</th>
                        <th className="py-2 px-2">Reference</th>
                        <th className="py-2 px-2">Against</th>
                        <th className="py-2 px-2">Remarks</th>
                    </tr>
                </thead>
                <tbody>
                    {payments.map((p: any) => (
                        <tr key={p.id} className="border-b border-border/30 hover:bg-muted/20">
                            <td className="py-1.5 px-2 text-muted-foreground">{p.paymentDate ? fmtDate(p.paymentDate) : "-"}</td>
                            <td className="py-1.5 px-2 text-right font-medium text-emerald-400">{fmt(p.amount)}</td>
                            <td className="py-1.5 px-2 text-muted-foreground">{p.paymentMode || "-"}</td>
                            <td className="py-1.5 px-2 text-muted-foreground">{p.referenceNo || "-"}</td>
                            <td className="py-1.5 px-2 text-muted-foreground truncate max-w-[120px]">
                                {p.statement ? `Stmt: ${p.statement.statementNo}` : p.invoiceBill ? `Bill: ${p.invoiceBill.billNo || p.invoiceBill.id}` : "-"}
                            </td>
                            <td className="py-1.5 px-2 text-muted-foreground truncate max-w-[100px]">{p.remarks || "-"}</td>
                        </tr>
                    ))}
                </tbody>
            </table>
            <Paginator page={page} totalPages={totalPages} onPageChange={onPageChange} />
        </div>
    );
}

// ── Vehicle Detail Table ──
function VehicleDetailTable({ vehicles, fmt }: { vehicles: Vehicle[]; fmt: (n: number) => string }) {
    if (vehicles.length === 0) return <div className="text-center text-muted-foreground text-xs p-8">No vehicles attached.</div>;

    return (
        <table className="w-full text-xs">
            <thead className="sticky top-0 bg-background">
                <tr className="text-left text-[10px] uppercase tracking-wider text-muted-foreground border-b border-border">
                    <th className="py-2 px-2">Vehicle No</th>
                    <th className="py-2 px-2">Type</th>
                    <th className="py-2 px-2">Fuel</th>
                    <th className="py-2 px-2 text-right">Capacity</th>
                    <th className="py-2 px-2 text-right">Monthly Limit</th>
                    <th className="py-2 px-2 text-right">Consumed</th>
                    <th className="py-2 px-2 text-center">Status</th>
                </tr>
            </thead>
            <tbody>
                {vehicles.map(v => {
                    const overLimit = v.maxLitersPerMonth && v.consumedLiters && v.consumedLiters >= v.maxLitersPerMonth;
                    return (
                        <tr key={v.id} className="border-b border-border/30 hover:bg-muted/20">
                            <td className="py-1.5 px-2 font-medium text-foreground">{v.vehicleNumber}</td>
                            <td className="py-1.5 px-2 text-muted-foreground">{v.vehicleType?.name || "-"}</td>
                            <td className="py-1.5 px-2 text-muted-foreground">{v.preferredProduct?.name || "-"}</td>
                            <td className="py-1.5 px-2 text-right text-muted-foreground">{v.maxCapacity ? `${v.maxCapacity} L` : "-"}</td>
                            <td className="py-1.5 px-2 text-right text-muted-foreground">{v.maxLitersPerMonth ? `${v.maxLitersPerMonth} L` : "-"}</td>
                            <td className={`py-1.5 px-2 text-right font-medium ${overLimit ? "text-rose-400" : "text-foreground"}`}>
                                {v.consumedLiters?.toFixed(1) || 0} L
                            </td>
                            <td className="py-1.5 px-2 text-center">
                                <Badge variant={v.status === "ACTIVE" ? "success" : v.status === "BLOCKED" ? "danger" : "warning"} className="text-[8px] px-1.5 py-0">
                                    {v.status || "ACTIVE"}
                                </Badge>
                            </td>
                        </tr>
                    );
                })}
            </tbody>
        </table>
    );
}

// ── Paginator ──
function Paginator({ page, totalPages, onPageChange }: {
    page: number; totalPages: number; onPageChange: (p: number) => void;
}) {
    if (totalPages <= 1) return null;
    return (
        <div className="flex items-center justify-between px-2 py-2 border-t border-border/50">
            <button
                disabled={page === 0}
                onClick={() => onPageChange(page - 1)}
                className="text-[10px] px-2 py-1 rounded border border-border text-muted-foreground hover:bg-muted disabled:opacity-30 flex items-center gap-0.5"
            >
                <ChevronLeft className="w-3 h-3" /> Prev
            </button>
            <span className="text-[10px] text-muted-foreground">
                Page {page + 1} of {totalPages}
            </span>
            <button
                disabled={page >= totalPages - 1}
                onClick={() => onPageChange(page + 1)}
                className="text-[10px] px-2 py-1 rounded border border-border text-muted-foreground hover:bg-muted disabled:opacity-30 flex items-center gap-0.5"
            >
                Next <ChevronRight className="w-3 h-3" />
            </button>
        </div>
    );
}
