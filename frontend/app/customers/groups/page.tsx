"use client";

import React, { useState, useEffect } from "react";
import { Plus, Search, Edit2, Trash2, ChevronDown, ChevronRight, Users } from "lucide-react";
import { Modal } from "@/components/ui/modal";
import { GroupStep } from "@/components/steps/group-step";
import { useRouter } from "next/navigation";

export default function GroupsPage() {
    const [groups, setGroups] = useState<any[]>([]);
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [formData, setFormData] = useState<any>({});
    const [loading, setLoading] = useState(false);
    const [expandedGroupId, setExpandedGroupId] = useState<number | null>(null);
    const [groupCustomers, setGroupCustomers] = useState<Record<number, any[]>>({});
    const [loadingCustomers, setLoadingCustomers] = useState<number | null>(null);
    const [searchQuery, setSearchQuery] = useState("");
    const router = useRouter();

    useEffect(() => {
        fetchGroups();
    }, []);

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

    const toggleExpand = async (groupId: number) => {
        if (expandedGroupId === groupId) {
            setExpandedGroupId(null);
            return;
        }
        setExpandedGroupId(groupId);
        if (!groupCustomers[groupId]) {
            setLoadingCustomers(groupId);
            try {
                const res = await fetch(`http://localhost:8080/api/groups/${groupId}/customers?size=100`);
                if (res.ok) {
                    const data = await res.json();
                    setGroupCustomers(prev => ({ ...prev, [groupId]: data.content || [] }));
                }
            } catch (error) {
                console.error("Failed to fetch group customers", error);
            } finally {
                setLoadingCustomers(null);
            }
        }
    };

    const handleEdit = (group: any) => {
        setFormData({
            id: group.id,
            groupName: group.groupName,
            groupDescription: group.description,
        });
        setIsModalOpen(true);
    };

    const handleDelete = async (id: number) => {
        if (!confirm("Are you sure you want to delete this group?")) return;
        try {
            const res = await fetch(`http://localhost:8080/api/groups/${id}`, {
                method: "DELETE",
            });
            if (res.ok) {
                fetchGroups();
            }
        } catch (error) {
            console.error("Failed to delete group", error);
        }
    };

    const handleSave = async () => {
        setLoading(true);
        try {
            const url = formData.id 
                ? `http://localhost:8080/api/groups/${formData.id}`
                : "http://localhost:8080/api/groups";
            const method = formData.id ? "PUT" : "POST";

            const res = await fetch(url, {
                method: method,
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    groupName: formData.groupName,
                    description: formData.groupDescription,
                }),
            });
            if (res.ok) {
                setIsModalOpen(false);
                setFormData({});
                fetchGroups();
            }
        } catch (error) {
            console.error("Failed to save group", error);
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="p-8 min-h-screen bg-background text-foreground">
            <div className="max-w-7xl mx-auto">
                <div className="flex justify-between items-center mb-8">
                    <div>
                        <h1 className="text-4xl font-bold tracking-tight">
                            Customer <span className="text-gradient">Groups</span>
                        </h1>
                        <p className="text-muted-foreground mt-2">
                            Manage logical groupings of customers for easier billing and reporting.
                        </p>
                    </div>
                    <button
                        onClick={() => {
                            setFormData({});
                            setIsModalOpen(true);
                        }}
                        className="btn-gradient px-6 py-3 rounded-xl font-medium flex items-center gap-2"
                    >
                        <Plus className="w-5 h-5" />
                        Add New Group
                    </button>
                </div>

                {/* Search Bar */}
                <div className="mb-6">
                    <div className="relative max-w-md">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                        <input
                            type="text"
                            placeholder="Search groups by name..."
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            className="w-full pl-10 pr-4 py-2.5 bg-card border border-border rounded-xl text-foreground text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                        />
                    </div>
                </div>

                <div className="bg-card border border-border rounded-2xl overflow-hidden shadow-xl">
                    <table className="w-full text-left">
                        <thead>
                            <tr className="border-b border-border bg-muted/50">
                                <th className="px-6 py-4 text-sm font-semibold w-10"></th>
                                <th className="px-6 py-4 text-sm font-semibold">Group Name</th>
                                <th className="px-6 py-4 text-sm font-semibold">Description</th>
                                <th className="px-6 py-4 text-sm font-semibold text-right">Actions</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-border">
                            {groups.filter((g) => {
                                if (!searchQuery) return true;
                                const q = searchQuery.toLowerCase();
                                return g.groupName?.toLowerCase().includes(q) || g.description?.toLowerCase().includes(q);
                            }).map((group) => (
                                <React.Fragment key={group.id}>
                                    <tr className="hover:bg-muted/30 transition-colors">
                                        <td className="px-6 py-4">
                                            <button
                                                onClick={() => toggleExpand(group.id)}
                                                className="p-1 hover:bg-muted rounded transition-colors"
                                            >
                                                {expandedGroupId === group.id ? (
                                                    <ChevronDown className="w-4 h-4 text-muted-foreground" />
                                                ) : (
                                                    <ChevronRight className="w-4 h-4 text-muted-foreground" />
                                                )}
                                            </button>
                                        </td>
                                        <td className="px-6 py-4 font-medium">{group.groupName}</td>
                                        <td className="px-6 py-4 text-muted-foreground">{group.description || "-"}</td>
                                        <td className="px-6 py-4 text-right">
                                            <div className="flex justify-end gap-2">
                                                <button
                                                    onClick={() => handleEdit(group)}
                                                    className="p-2 hover:bg-primary/10 rounded-lg text-primary transition-colors"
                                                >
                                                    <Edit2 className="w-4 h-4" />
                                                </button>
                                                <button
                                                    onClick={() => handleDelete(group.id)}
                                                    className="p-2 hover:bg-destructive/10 rounded-lg text-destructive transition-colors"
                                                >
                                                    <Trash2 className="w-4 h-4" />
                                                </button>
                                            </div>
                                        </td>
                                    </tr>
                                    {expandedGroupId === group.id && (
                                        <tr>
                                            <td colSpan={4} className="px-8 py-4 bg-muted/20">
                                                {loadingCustomers === group.id ? (
                                                    <div className="text-sm text-muted-foreground py-2">Loading customers...</div>
                                                ) : (groupCustomers[group.id]?.length || 0) > 0 ? (
                                                    <div>
                                                        <div className="flex items-center gap-2 mb-3">
                                                            <Users className="w-4 h-4 text-muted-foreground" />
                                                            <span className="text-sm font-medium text-muted-foreground">
                                                                {groupCustomers[group.id].length} customer{groupCustomers[group.id].length !== 1 ? 's' : ''}
                                                            </span>
                                                        </div>
                                                        <div className="grid gap-2">
                                                            {groupCustomers[group.id].map((customer: any) => (
                                                                <div
                                                                    key={customer.id}
                                                                    onClick={() => router.push(`/customers/${customer.id}`)}
                                                                    className="flex items-center justify-between p-3 bg-card border border-border rounded-lg hover:bg-muted/50 cursor-pointer transition-colors"
                                                                >
                                                                    <div className="flex items-center gap-3">
                                                                        <div className="w-8 h-8 rounded-full bg-primary/10 flex items-center justify-center text-primary font-bold text-xs">
                                                                            {customer.name?.charAt(0) || "C"}
                                                                        </div>
                                                                        <div>
                                                                            <div className="text-sm font-medium text-foreground">{customer.name}</div>
                                                                            <div className="text-xs text-muted-foreground">{customer.phoneNumbers?.[0] || customer.emails?.[0] || ""}</div>
                                                                        </div>
                                                                    </div>
                                                                    <span className={`text-xs px-2 py-1 rounded-full ${customer.isActive ? 'bg-green-500/10 text-green-500' : 'bg-muted text-muted-foreground'}`}>
                                                                        {customer.isActive ? 'Active' : 'Inactive'}
                                                                    </span>
                                                                </div>
                                                            ))}
                                                        </div>
                                                    </div>
                                                ) : (
                                                    <div className="text-sm text-muted-foreground py-2">No customers in this group.</div>
                                                )}
                                            </td>
                                        </tr>
                                    )}
                                </React.Fragment>
                            ))}
                        </tbody>
                    </table>
                    {groups.length === 0 && (
                        <div className="p-12 text-center text-muted-foreground">
                            No groups found. Click "Add New Group" to get started.
                        </div>
                    )}
                </div>
            </div>

            <Modal
                isOpen={isModalOpen}
                onClose={() => {
                    setIsModalOpen(false);
                    setFormData({});
                }}
                title={formData.id ? "Edit Group" : "Add New Group"}
            >
                <div className="p-6">
                    <GroupStep data={formData} updateData={setFormData} />
                    <div className="flex justify-end gap-3 mt-8">
                        <button
                            onClick={() => setIsModalOpen(false)}
                            className="px-6 py-2.5 rounded-xl text-sm font-medium text-muted-foreground hover:text-white hover:bg-white/5 transition-colors"
                        >
                            Cancel
                        </button>
                        <button
                            onClick={handleSave}
                            disabled={loading || !formData.groupName}
                            className="btn-gradient px-8 py-2.5 rounded-xl text-sm font-bold text-white shadow-lg disabled:opacity-50"
                        >
                            {loading ? "Saving..." : "Save Group"}
                        </button>
                    </div>
                </div>
            </Modal>
        </div>
    );
}
