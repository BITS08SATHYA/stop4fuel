"use client";

import { useState, useEffect } from "react";
import { Search, Filter, MoreHorizontal, Edit, Trash2, Eye, ChevronLeft, ChevronRight } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { GlassCard } from "@/components/ui/glass-card";
import { useRouter } from "next/navigation";

// Mock Data (Updated to match schema)
export function CustomerList({ refreshTrigger }: { refreshTrigger?: number }) {
    const [customers, setCustomers] = useState<any[]>([]);
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [searchQuery, setSearchQuery] = useState("");
    const [groups, setGroups] = useState<any[]>([]);
    const [selectedGroupId, setSelectedGroupId] = useState<string>("");
    const [showGroupFilter, setShowGroupFilter] = useState(false);

    useEffect(() => {
        fetchGroups();
    }, []);

    useEffect(() => {
        fetchCustomers();
    }, [page, searchQuery, selectedGroupId, refreshTrigger]);

    const fetchGroups = async () => {
        try {
            const res = await fetch("http://localhost:8080/api/groups");
            if (res.ok) {
                const data = await res.json();
                setGroups(Array.isArray(data) ? data : data.content || []);
            }
        } catch (error) {
            console.error("Failed to fetch groups", error);
        }
    };

    const fetchCustomers = async () => {
        try {
            const queryParams = new URLSearchParams({
                page: page.toString(),
                size: "10",
            });
            if (searchQuery) queryParams.set("search", searchQuery);
            if (selectedGroupId) queryParams.set("groupId", selectedGroupId);
            const res = await fetch(`http://localhost:8080/api/customers?${queryParams}`);
            if (!res.ok) throw new Error("Failed to fetch");
            const data = await res.json();

            if (data.content && Array.isArray(data.content)) {
                setCustomers(data.content);
                setTotalPages(data.totalPages);
            } else if (Array.isArray(data)) {
                // Fallback for backward compatibility if backend wasn't updated yet (though it is)
                setCustomers(data);
            } else {
                console.error("API response format unexpected:", data);
                setCustomers([]);
            }
        } catch (error) {
            console.error("Failed to fetch customers", error);
            setCustomers([]);
        }
    };

    const handleDelete = async (e: React.MouseEvent, id: number) => {
        e.stopPropagation();
        if (!confirm("Are you sure you want to delete this customer?")) return;

        try {
            const res = await fetch(`http://localhost:8080/api/customers/${id}`, {
                method: "DELETE",
            });
            if (res.ok) {
                fetchCustomers();
            } else {
                console.error("Failed to delete customer");
            }
        } catch (error) {
            console.error("Error deleting customer", error);
        }
    };

    const router = useRouter();
    const [contextMenu, setContextMenu] = useState<{ x: number; y: number; customerId: number } | null>(null);

    // Close context menu on click outside
    useEffect(() => {
        const handleClick = () => setContextMenu(null);
        document.addEventListener("click", handleClick);
        return () => document.removeEventListener("click", handleClick);
    }, []);

    const handleContextMenu = (e: React.MouseEvent, customerId: number) => {
        e.preventDefault();
        setContextMenu({ x: e.clientX, y: e.clientY, customerId });
    };

    const handleRowClick = (customerId: number) => {
        router.push(`/customers/${customerId}`);
    };

    return (
        <GlassCard className="h-full min-h-[500px] flex flex-col overflow-hidden relative">
            {/* Toolbar */}
            <div className="flex items-center justify-between mb-6 shrink-0">
                <h2 className="text-xl font-semibold text-foreground">All Customers</h2>
                <div className="flex gap-3">
                    <div className="relative">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                        <input
                            type="text"
                            value={searchQuery}
                            onChange={(e) => {
                                setSearchQuery(e.target.value);
                                setPage(0); // Reset to first page on search
                            }}
                            placeholder="Search customers..."
                            className="bg-secondary border border-border rounded-lg pl-9 pr-4 py-2 text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-accent/50 w-64"
                        />
                    </div>
                    <div className="relative">
                        <button
                            onClick={() => setShowGroupFilter(!showGroupFilter)}
                            className={`p-2 border rounded-lg transition-colors ${selectedGroupId ? 'bg-primary/10 border-primary text-primary' : 'bg-secondary border-border hover:bg-secondary/80 text-muted-foreground'}`}
                        >
                            <Filter className="w-4 h-4" />
                        </button>
                        {showGroupFilter && (
                            <div className="absolute right-0 top-full mt-2 z-20 bg-popover border border-border rounded-lg shadow-lg py-2 min-w-[200px]">
                                <div className="px-3 pb-2 text-xs font-semibold text-muted-foreground uppercase tracking-wider">Filter by Group</div>
                                <button
                                    onClick={() => { setSelectedGroupId(""); setPage(0); setShowGroupFilter(false); }}
                                    className={`w-full text-left px-4 py-2 text-sm hover:bg-muted ${!selectedGroupId ? 'text-primary font-medium' : 'text-foreground'}`}
                                >
                                    All Groups
                                </button>
                                {groups.map((group) => (
                                    <button
                                        key={group.id}
                                        onClick={() => { setSelectedGroupId(group.id.toString()); setPage(0); setShowGroupFilter(false); }}
                                        className={`w-full text-left px-4 py-2 text-sm hover:bg-muted ${selectedGroupId === group.id.toString() ? 'text-primary font-medium' : 'text-foreground'}`}
                                    >
                                        {group.groupName}
                                    </button>
                                ))}
                            </div>
                        )}
                    </div>
                </div>
            </div>

            {/* Table */}
            <div className="flex-1 overflow-auto custom-scrollbar">
                <table className="w-full text-left border-collapse">
                    <thead className="sticky top-0 bg-card z-10">
                        <tr className="border-b border-border">
                            <th className="p-4 font-medium text-muted-foreground text-sm">ID</th>
                            <th className="p-4 font-medium text-muted-foreground text-sm">Customer</th>
                            <th className="p-4 font-medium text-muted-foreground text-sm">Phone</th>
                            <th className="p-4 font-medium text-muted-foreground text-sm">Party</th>
                            <th className="p-4 font-medium text-muted-foreground text-sm">Group</th>
                            <th className="p-4 font-medium text-muted-foreground text-sm">Credit Usage</th>
                            <th className="p-4 font-medium text-muted-foreground text-sm">Status</th>
                            <th className="p-4 font-medium text-muted-foreground text-sm text-right">Actions</th>
                        </tr>
                    </thead>
                    <tbody className="divide-y divide-border">
                        {customers.map((customer) => {
                            const creditLimit = customer.creditLimitAmount || customer.creditLimitLiters || 0;
                            const isAmount = !!customer.creditLimitAmount;
                            const consumed = customer.consumedLiters || 0; // Note: consumed is always liters? Or amount?
                            // If limit is amount, consumed should be amount? User said "consumedLiters".
                            // If limit is amount, we might need to convert or display differently.
                            // For now assuming consumed is always liters, but if limit is amount, we might have a mismatch.
                            // However, user said "Max Ceiling set (either Liter (quantity) or the amount) ...and consumedLiters".
                            // So consumed is always liters.
                            // If limit is amount, we can't calculate percentage easily without price.
                            // I'll display what I have.

                            const percentage = creditLimit > 0 ? (consumed / creditLimit) * 100 : 0; // This is wrong if units differ.
                            // But for now let's just display values.

                            return (
                                <tr
                                    key={customer.id}
                                    onContextMenu={(e) => handleContextMenu(e, customer.id)}
                                    onClick={() => handleRowClick(customer.id)}
                                    className="hover:bg-muted/50 transition-colors cursor-pointer group"
                                >
                                    <td className="p-4 text-sm font-mono text-muted-foreground">
                                        #{customer.id?.toString().padStart(3, '0')}
                                    </td>
                                    <td className="p-4">
                                        <div className="flex items-center gap-3">
                                            <div className="w-8 h-8 rounded-full bg-primary/10 flex items-center justify-center text-primary font-bold text-xs">
                                                {customer.name?.charAt(0) || "C"}
                                            </div>
                                            <div>
                                                <div className="font-medium text-foreground">{customer.name}</div>
                                                <div className="text-xs text-muted-foreground">{customer.emails?.[0]}</div>
                                            </div>
                                        </div>
                                    </td>
                                    <td className="p-4 text-sm text-foreground">{customer.phoneNumbers?.[0]}</td>
                                    <td className="p-4 text-sm text-foreground">{customer.party?.partyType}</td>
                                    <td className="p-4 text-sm text-foreground">{customer.group?.groupName}</td>
                                    <td className="p-4">
                                        <div className="w-32">
                                            <div className="flex justify-between text-xs mb-1">
                                                <span className="text-muted-foreground">
                                                    {consumed} L
                                                </span>
                                                <span className="text-foreground font-medium">
                                                    / {isAmount ? `$${creditLimit}` : `${creditLimit} L`}
                                                </span>
                                            </div>
                                            {/* Progress bar only makes sense if units match */}
                                            {!isAmount && (
                                                <div className="h-1.5 bg-secondary rounded-full overflow-hidden">
                                                    <div
                                                        className={`h-full rounded-full ${percentage > 80 ? 'bg-destructive' : 'bg-accent'}`}
                                                        style={{ width: `${Math.min(percentage, 100)}%` }}
                                                    />
                                                </div>
                                            )}
                                        </div>
                                    </td>
                                    <td className="p-4">
                                        <button
                                            onClick={async (e) => {
                                                e.stopPropagation();
                                                try {
                                                    const res = await fetch(`http://localhost:8080/api/customers/${customer.id}/toggle-status`, {
                                                        method: 'PATCH'
                                                    });
                                                    if (res.ok) {
                                                        fetchCustomers();
                                                    }
                                                } catch (error) {
                                                    console.error('Failed to toggle status', error);
                                                }
                                            }}
                                            className="relative inline-flex items-center"
                                        >
                                            <Badge
                                                variant={customer.status === 'ACTIVE' ? 'success' : customer.status === 'BLOCKED' ? 'danger' : 'warning'}
                                                className="cursor-pointer hover:opacity-80 transition-opacity"
                                            >
                                                {customer.status === 'ACTIVE' ? 'Active' : customer.status === 'BLOCKED' ? 'Blocked' : 'Inactive'}
                                            </Badge>
                                        </button>
                                    </td>
                                    <td className="p-4 text-right">
                                        <div className="flex justify-end gap-2 opacity-0 group-hover:opacity-100 transition-opacity">
                                            <button className="p-2 hover:bg-accent/10 hover:text-accent rounded-md transition-colors" title="Edit">
                                                <Edit className="w-4 h-4" />
                                            </button>
                                            <button
                                                className="p-2 hover:bg-destructive/10 hover:text-destructive rounded-md transition-colors"
                                                title="Delete"
                                                onClick={(e) => handleDelete(e, customer.id)}
                                            >
                                                <Trash2 className="w-4 h-4" />
                                            </button>
                                        </div>
                                    </td>
                                </tr>
                            )
                        })}
                    </tbody>
                </table>
            </div>

            {/* Pagination */}
            <div className="flex items-center justify-between p-4 border-t border-border">
                <div className="text-sm text-muted-foreground">
                    Page {page + 1} of {totalPages || 1}
                </div>
                <div className="flex gap-2">
                    <button
                        onClick={() => setPage(p => Math.max(0, p - 1))}
                        disabled={page === 0}
                        className="p-2 border border-border rounded-lg hover:bg-secondary disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                        <ChevronLeft className="w-4 h-4" />
                    </button>
                    <button
                        onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}
                        disabled={page >= totalPages - 1}
                        className="p-2 border border-border rounded-lg hover:bg-secondary disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                        <ChevronRight className="w-4 h-4" />
                    </button>
                </div>
            </div>

            {/* Context Menu */}
            {contextMenu && (
                <div
                    className="fixed z-50 bg-popover border border-border rounded-lg shadow-lg py-1 min-w-[160px] animate-in fade-in zoom-in-95 duration-100"
                    style={{ top: contextMenu.y, left: contextMenu.x }}
                >
                    <button
                        onClick={(e) => {
                            e.stopPropagation();
                            router.push(`/customers/${contextMenu.customerId}`);
                            setContextMenu(null);
                        }}
                        className="w-full text-left px-4 py-2 text-sm text-foreground hover:bg-muted flex items-center gap-2"
                    >
                        <Eye className="w-4 h-4" /> View Profile
                    </button>
                    <button
                        onClick={(e) => {
                            e.stopPropagation();
                            // Handle Edit
                            setContextMenu(null);
                        }}
                        className="w-full text-left px-4 py-2 text-sm text-foreground hover:bg-muted flex items-center gap-2"
                    >
                        <Edit className="w-4 h-4" /> Edit
                    </button>
                    <div className="h-px bg-border my-1" />
                    <button
                        onClick={(e) => {
                            e.stopPropagation();
                            // Handle Delete
                            setContextMenu(null);
                        }}
                        className="w-full text-left px-4 py-2 text-sm text-destructive hover:bg-destructive/10 flex items-center gap-2"
                    >
                        <Trash2 className="w-4 h-4" /> Delete
                    </button>
                </div>
            )}
        </GlassCard>
    );
}
