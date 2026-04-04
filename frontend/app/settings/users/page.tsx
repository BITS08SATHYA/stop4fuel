"use client";

import React, { useEffect, useState, useCallback } from "react";
import { RouteGuard } from "@/components/route-guard";
import { PermissionGate } from "@/components/permission-gate";
import { fetchWithAuth } from "@/lib/api/fetch-with-auth";
import { Plus, Search, RotateCcw, Loader2, Pencil, X, Save } from "lucide-react";
import { CreateUserModal } from "@/components/users/create-user-modal";
import { TablePagination } from "@/components/ui/table-pagination";
import { StyledSelect } from "@/components/ui/styled-select";

const getApiBaseUrl = () => {
    if (typeof window !== "undefined") {
        return process.env.NEXT_PUBLIC_API_URL || `${window.location.protocol}//${window.location.hostname}:8080/api`;
    }
    return process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api";
};

interface UserItem {
    id: number;
    name: string;
    phone: string | null;
    email: string | null;
    role: string;
    designation: string | null;
    userType: string;
    status: string;
    joinDate: string | null;
    employeeCode: string | null;
    lastLoginAt: string | null;
}

interface DesignationOption {
    id: number;
    name: string;
}

function formatRelativeTime(dt: string | null): string {
    if (!dt) return "Never";
    const diff = Date.now() - new Date(dt).getTime();
    const mins = Math.floor(diff / 60000);
    if (mins < 1) return "Just now";
    if (mins < 60) return `${mins}m ago`;
    const hours = Math.floor(mins / 60);
    if (hours < 24) return `${hours}h ago`;
    const days = Math.floor(hours / 24);
    if (days < 30) return `${days}d ago`;
    return new Date(dt).toLocaleDateString("en-IN", { day: "2-digit", month: "short" });
}

const ROLES = ["OWNER", "ADMIN", "CASHIER", "EMPLOYEE", "CUSTOMER", "DEALER"];
const PAGE_SIZE = 7;

export default function UsersPage() {
    const [allUsers, setAllUsers] = useState<UserItem[]>([]);
    const [loading, setLoading] = useState(true);
    const [search, setSearch] = useState("");
    const [typeFilter, setTypeFilter] = useState("");
    const [roleFilter, setRoleFilter] = useState("");
    const [showCreateModal, setShowCreateModal] = useState(false);
    const [resetingId, setResetingId] = useState<number | null>(null);
    const [passcodeResult, setPasscodeResult] = useState<{ name: string; passcode: string } | null>(null);
    const [page, setPage] = useState(0);

    // Edit modal state
    const [editUser, setEditUser] = useState<UserItem | null>(null);
    const [editRole, setEditRole] = useState("");
    const [editDesignation, setEditDesignation] = useState("");
    const [designations, setDesignations] = useState<DesignationOption[]>([]);
    const [saving, setSaving] = useState(false);
    const [editError, setEditError] = useState("");

    const loadUsers = useCallback(async () => {
        setLoading(true);
        try {
            const params = new URLSearchParams();
            if (search) params.set("search", search);
            if (typeFilter) params.set("type", typeFilter);
            if (roleFilter) params.set("role", roleFilter);

            const res = await fetchWithAuth(
                `${getApiBaseUrl()}/admin/users?${params.toString()}`
            );
            if (res.ok) {
                const data = await res.json();
                setAllUsers(data);
                setPage(0);
            }
        } catch (err) {
            console.error("Failed to load users", err);
        } finally {
            setLoading(false);
        }
    }, [search, typeFilter, roleFilter]);

    useEffect(() => {
        loadUsers();
        loadDesignations();
    }, [loadUsers]);

    const loadDesignations = async () => {
        try {
            const res = await fetchWithAuth(`${getApiBaseUrl()}/designations`);
            if (res.ok) {
                setDesignations(await res.json());
            }
        } catch (e) {
            console.error("Failed to load designations", e);
        }
    };

    // Pagination
    const totalPages = Math.ceil(allUsers.length / PAGE_SIZE);
    const pagedUsers = allUsers.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);

    const handleResetPasscode = async (userId: number, userName: string) => {
        setResetingId(userId);
        try {
            const res = await fetchWithAuth(
                `${getApiBaseUrl()}/admin/users/${userId}/reset-passcode`,
                { method: "POST" }
            );
            if (res.ok) {
                const data = await res.json();
                setPasscodeResult({ name: userName, passcode: data.passcode });
            } else {
                const data = await res.json().catch(() => ({}));
                alert(`Failed to reset passcode: ${data.error || data.message || res.statusText}`);
            }
        } catch (err: any) {
            alert(`Failed to reset passcode: ${err?.message || "Unknown error"}`);
        } finally {
            setResetingId(null);
        }
    };

    const handleUserCreated = (name: string, passcode: string) => {
        setPasscodeResult({ name, passcode });
        setShowCreateModal(false);
        loadUsers();
    };

    const openEditModal = (user: UserItem) => {
        setEditUser(user);
        setEditRole(user.role);
        setEditDesignation(user.designation || "");
        setEditError("");
    };

    const handleSaveEdit = async () => {
        if (!editUser) return;
        setSaving(true);
        setEditError("");
        try {
            const res = await fetchWithAuth(`${getApiBaseUrl()}/admin/users/${editUser.id}/role`, {
                method: "PUT",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ roleType: editRole, designation: editDesignation }),
            });
            if (!res.ok) {
                const err = await res.text();
                throw new Error(err || "Failed to update");
            }
            setEditUser(null);
            loadUsers();
        } catch (e: any) {
            setEditError(e.message || "Failed to update user");
        } finally {
            setSaving(false);
        }
    };

    const statusBadge = (status: string) => {
        const isActive = status?.toUpperCase() === "ACTIVE";
        return (
            <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${
                isActive
                    ? "bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400"
                    : "bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-400"
            }`}>
                {status}
            </span>
        );
    };

    const typeBadge = (userType: string) => {
        const colors = userType === "EMPLOYEE"
            ? "bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-400"
            : "bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-400";
        return (
            <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${colors}`}>
                {userType}
            </span>
        );
    };

    return (
        <RouteGuard permission="USER_VIEW">
            <div className="p-6 space-y-6">
                <div className="flex items-center justify-between">
                    <div>
                        <h1 className="text-2xl font-bold text-foreground">User Management</h1>
                        <p className="text-sm text-muted-foreground">Manage all users - employees and customers</p>
                    </div>
                    <PermissionGate permission="USER_MANAGE">
                        <button
                            onClick={() => setShowCreateModal(true)}
                            className="flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-lg hover:bg-primary/90 transition-colors"
                        >
                            <Plus className="w-4 h-4" />
                            Create User
                        </button>
                    </PermissionGate>
                </div>

                {/* Filters */}
                <div className="flex flex-wrap gap-3">
                    <div className="relative flex-1 min-w-[200px]">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                        <input
                            type="text"
                            placeholder="Search by name, phone, or username..."
                            value={search}
                            onChange={(e) => setSearch(e.target.value)}
                            className="w-full pl-9 pr-3 py-2 border border-input rounded-lg bg-background text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                        />
                    </div>
                    <StyledSelect
                        value={typeFilter}
                        onChange={(val) => setTypeFilter(val)}
                        options={[
                            { value: "", label: "All Types" },
                            { value: "EMPLOYEE", label: "Employees" },
                            { value: "CUSTOMER", label: "Customers" },
                        ]}
                        placeholder="All Types"
                        className="min-w-[140px]"
                    />
                    <StyledSelect
                        value={roleFilter}
                        onChange={(val) => setRoleFilter(val)}
                        options={[
                            { value: "", label: "All Roles" },
                            { value: "OWNER", label: "Owner" },
                            { value: "ADMIN", label: "Admin" },
                            { value: "CASHIER", label: "Cashier" },
                            { value: "EMPLOYEE", label: "Employee" },
                            { value: "CUSTOMER", label: "Customer" },
                        ]}
                        placeholder="All Roles"
                        className="min-w-[140px]"
                    />
                </div>

                {/* Table */}
                {loading ? (
                    <div className="flex justify-center py-12">
                        <Loader2 className="w-8 h-8 animate-spin text-muted-foreground" />
                    </div>
                ) : (
                    <div className="border border-border rounded-lg overflow-hidden">
                        <div className="overflow-x-auto">
                            <table className="w-full text-sm">
                                <thead>
                                    <tr className="bg-muted/50 border-b border-border">
                                        <th className="text-left px-4 py-3 font-medium text-muted-foreground">Name</th>
                                        <th className="text-left px-4 py-3 font-medium text-muted-foreground">Phone</th>
                                        <th className="text-left px-4 py-3 font-medium text-muted-foreground">Type</th>
                                        <th className="text-left px-4 py-3 font-medium text-muted-foreground">Role</th>
                                        <th className="text-left px-4 py-3 font-medium text-muted-foreground">Designation</th>
                                        <th className="text-left px-4 py-3 font-medium text-muted-foreground">Status</th>
                                        <th className="text-left px-4 py-3 font-medium text-muted-foreground">Last Login</th>
                                        <th className="text-left px-4 py-3 font-medium text-muted-foreground">Actions</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {pagedUsers.length === 0 ? (
                                        <tr>
                                            <td colSpan={8} className="text-center py-8 text-muted-foreground">
                                                No users found
                                            </td>
                                        </tr>
                                    ) : (
                                        pagedUsers.map((user) => (
                                            <tr key={user.id} className="border-b border-border hover:bg-muted/30 transition-colors">
                                                <td className="px-4 py-3">
                                                    <div>
                                                        <div className="font-medium text-foreground">{user.name}</div>
                                                        {user.employeeCode && (
                                                            <div className="text-xs text-muted-foreground">{user.employeeCode}</div>
                                                        )}
                                                    </div>
                                                </td>
                                                <td className="px-4 py-3 text-foreground">{user.phone || "-"}</td>
                                                <td className="px-4 py-3">{typeBadge(user.userType)}</td>
                                                <td className="px-4 py-3 text-foreground">{user.role}</td>
                                                <td className="px-4 py-3 text-foreground">{user.designation || "-"}</td>
                                                <td className="px-4 py-3">{statusBadge(user.status)}</td>
                                                <td className="px-4 py-3 text-muted-foreground text-xs">
                                                    {formatRelativeTime(user.lastLoginAt)}
                                                </td>
                                                <td className="px-4 py-3">
                                                    <PermissionGate permission="USER_MANAGE">
                                                        <div className="flex items-center gap-1">
                                                            <button
                                                                onClick={() => openEditModal(user)}
                                                                className="p-1.5 rounded-md hover:bg-primary/20 text-primary transition-colors"
                                                                title="Edit Role & Designation"
                                                            >
                                                                <Pencil className="w-3.5 h-3.5" />
                                                            </button>
                                                            <button
                                                                onClick={() => handleResetPasscode(user.id, user.name)}
                                                                disabled={resetingId === user.id}
                                                                className="flex items-center gap-1 px-2 py-1 text-xs rounded border border-input hover:bg-muted transition-colors disabled:opacity-50"
                                                                title="Reset Passcode"
                                                            >
                                                                {resetingId === user.id ? (
                                                                    <Loader2 className="w-3 h-3 animate-spin" />
                                                                ) : (
                                                                    <RotateCcw className="w-3 h-3" />
                                                                )}
                                                                Reset
                                                            </button>
                                                        </div>
                                                    </PermissionGate>
                                                </td>
                                            </tr>
                                        ))
                                    )}
                                </tbody>
                            </table>
                        </div>

                        <TablePagination page={page} totalPages={totalPages} totalElements={allUsers.length} pageSize={PAGE_SIZE} onPageChange={setPage} />
                    </div>
                )}

                {/* Edit Role & Designation Modal */}
                {editUser && (
                    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
                        <div className="bg-background rounded-xl p-6 w-full max-w-sm space-y-4 shadow-xl border border-border">
                            <div className="flex items-center justify-between">
                                <h3 className="text-lg font-semibold text-foreground">Edit {editUser.name}</h3>
                                <button onClick={() => setEditUser(null)} className="p-1 rounded hover:bg-muted transition-colors">
                                    <X className="w-4 h-4" />
                                </button>
                            </div>

                            {editError && (
                                <p className="text-sm text-red-500 bg-red-50 dark:bg-red-950/20 p-2 rounded">{editError}</p>
                            )}

                            <div className="space-y-3">
                                <div>
                                    <label className="text-sm font-medium block mb-1">Role</label>
                                    <StyledSelect
                                        value={editRole}
                                        onChange={(val) => setEditRole(val)}
                                        options={ROLES.map(r => ({ value: r, label: r }))}
                                        className="w-full"
                                    />
                                </div>

                                {editUser.userType === "EMPLOYEE" && (
                                    <div>
                                        <label className="text-sm font-medium block mb-1">Designation</label>
                                        <StyledSelect
                                            value={editDesignation}
                                            onChange={(val) => setEditDesignation(val)}
                                            options={[
                                                { value: "", label: "No designation" },
                                                ...designations.map(d => ({ value: d.name, label: d.name })),
                                            ]}
                                            placeholder="No designation"
                                            className="w-full"
                                        />
                                    </div>
                                )}
                            </div>

                            <div className="flex justify-end gap-2 pt-2">
                                <button
                                    onClick={() => setEditUser(null)}
                                    className="px-4 py-2 text-sm rounded-lg border border-input hover:bg-muted transition-colors"
                                >
                                    Cancel
                                </button>
                                <button
                                    onClick={handleSaveEdit}
                                    disabled={saving}
                                    className="flex items-center gap-2 px-4 py-2 text-sm rounded-lg bg-primary text-primary-foreground hover:bg-primary/90 transition-colors disabled:opacity-50"
                                >
                                    {saving ? <Loader2 className="w-4 h-4 animate-spin" /> : <Save className="w-4 h-4" />}
                                    {saving ? "Saving..." : "Save"}
                                </button>
                            </div>
                        </div>
                    </div>
                )}

                {/* Passcode Result Modal */}
                {passcodeResult && (
                    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
                        <div className="bg-background rounded-xl p-6 w-full max-w-sm space-y-4 shadow-xl border border-border">
                            <h3 className="text-lg font-semibold text-foreground">Passcode for {passcodeResult.name}</h3>
                            <div className="flex items-center justify-center">
                                <span className="text-4xl font-mono font-bold tracking-[0.5em] text-primary">
                                    {passcodeResult.passcode}
                                </span>
                            </div>
                            <p className="text-sm text-muted-foreground text-center">
                                Share this passcode with the user. It won&apos;t be shown again.
                            </p>
                            <button
                                onClick={() => {
                                    navigator.clipboard.writeText(passcodeResult.passcode);
                                }}
                                className="w-full py-2 px-4 border border-input rounded-lg text-sm hover:bg-muted transition-colors"
                            >
                                Copy to Clipboard
                            </button>
                            <button
                                onClick={() => setPasscodeResult(null)}
                                className="w-full py-2 px-4 bg-primary text-primary-foreground rounded-lg text-sm hover:bg-primary/90 transition-colors"
                            >
                                Done
                            </button>
                        </div>
                    </div>
                )}

                {/* Create User Modal */}
                {showCreateModal && (
                    <CreateUserModal
                        onClose={() => setShowCreateModal(false)}
                        onCreated={handleUserCreated}
                    />
                )}
            </div>
        </RouteGuard>
    );
}
