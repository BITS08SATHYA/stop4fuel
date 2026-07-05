"use client";

import { useCallback, useEffect, useState } from "react";
import { GlassCard } from "@/components/ui/glass-card";
import { Badge } from "@/components/ui/badge";
import { PermissionGate } from "@/components/permission-gate";
import { showToast } from "@/components/ui/toast";
import {
    getCustomerContacts, createCustomerContact, updateCustomerContact, deleteCustomerContact,
    type CustomerContact,
} from "@/lib/api/station";
import { Phone, Plus, Pencil, Trash2, X, Save, Users } from "lucide-react";

const EMPTY: CustomerContact = { name: "", contactRole: "", phoneNumber: "", notes: "" };

export function CustomerContactsCard({ customerId }: { customerId: number }) {
    const [contacts, setContacts] = useState<CustomerContact[]>([]);
    const [loading, setLoading] = useState(true);
    const [editing, setEditing] = useState<CustomerContact | null>(null);
    const [saving, setSaving] = useState(false);

    const load = useCallback(() => {
        getCustomerContacts(customerId)
            .then((data) => setContacts(Array.isArray(data) ? data : []))
            .catch(() => setContacts([]))
            .finally(() => setLoading(false));
    }, [customerId]);

    useEffect(() => { load(); }, [load]);

    const save = async () => {
        if (!editing) return;
        if (!editing.name.trim() || !editing.phoneNumber.trim()) {
            showToast.error("Name and phone number are required");
            return;
        }
        setSaving(true);
        try {
            if (editing.id) {
                await updateCustomerContact(editing.id, editing);
            } else {
                await createCustomerContact(customerId, editing);
            }
            setEditing(null);
            load();
            showToast.success("Contact saved");
        } catch (e) {
            showToast.error(e instanceof Error ? e.message : "Failed to save contact");
        } finally {
            setSaving(false);
        }
    };

    const remove = async (contact: CustomerContact) => {
        if (!contact.id) return;
        if (!window.confirm(`Delete contact "${contact.name}"?`)) return;
        try {
            await deleteCustomerContact(contact.id);
            load();
            showToast.success("Contact deleted");
        } catch (e) {
            showToast.error(e instanceof Error ? e.message : "Failed to delete contact");
        }
    };

    return (
        <GlassCard className="p-6">
            <div className="flex items-center justify-between mb-4">
                <div className="flex items-center gap-2">
                    <Users className="w-5 h-5 text-primary" />
                    <h3 className="text-lg font-semibold text-foreground">Contact Persons</h3>
                </div>
                <PermissionGate permission="CUSTOMER_UPDATE">
                    {!editing && (
                        <button
                            onClick={() => setEditing({ ...EMPTY })}
                            className="flex items-center gap-1 px-3 py-1.5 text-xs font-medium rounded-lg bg-primary text-primary-foreground hover:opacity-90 transition-opacity"
                        >
                            <Plus className="w-3.5 h-3.5" /> Add
                        </button>
                    )}
                </PermissionGate>
            </div>

            {loading ? (
                <p className="text-sm text-muted-foreground">Loading contacts…</p>
            ) : contacts.length === 0 && !editing ? (
                <p className="text-sm text-muted-foreground">
                    No contact persons yet. Add the owner, manager or driver numbers so they are all in one place.
                </p>
            ) : (
                <ul className="space-y-3">
                    {contacts.map((c) => (
                        <li key={c.id} className="flex items-start justify-between gap-2 pb-3 border-b border-border/50 last:border-0 last:pb-0">
                            <div className="min-w-0">
                                <div className="flex items-center gap-2 flex-wrap">
                                    <span className="text-sm font-semibold text-foreground">{c.name}</span>
                                    {c.contactRole && <Badge variant="outline" className="text-[9px]">{c.contactRole}</Badge>}
                                </div>
                                <a
                                    href={`tel:${c.phoneNumber}`}
                                    className="inline-flex items-center gap-1.5 text-sm text-primary hover:underline mt-0.5"
                                >
                                    <Phone className="w-3.5 h-3.5" /> {c.phoneNumber}
                                </a>
                                {c.notes && <p className="text-xs text-muted-foreground mt-0.5">{c.notes}</p>}
                            </div>
                            <PermissionGate permission="CUSTOMER_UPDATE">
                                <div className="flex items-center gap-1 shrink-0">
                                    <button
                                        onClick={() => setEditing({ ...c })}
                                        className="p-1.5 rounded-lg hover:bg-muted transition-colors"
                                        aria-label={`Edit ${c.name}`}
                                    >
                                        <Pencil className="w-3.5 h-3.5 text-muted-foreground" />
                                    </button>
                                    <button
                                        onClick={() => remove(c)}
                                        className="p-1.5 rounded-lg hover:bg-destructive/10 transition-colors"
                                        aria-label={`Delete ${c.name}`}
                                    >
                                        <Trash2 className="w-3.5 h-3.5 text-destructive" />
                                    </button>
                                </div>
                            </PermissionGate>
                        </li>
                    ))}
                </ul>
            )}

            {editing && (
                <div className="mt-4 pt-4 border-t border-border space-y-2">
                    <div className="grid grid-cols-2 gap-2">
                        <input
                            value={editing.name}
                            onChange={(e) => setEditing({ ...editing, name: e.target.value })}
                            placeholder="Name *"
                            className="px-3 py-2 bg-secondary border border-border rounded-lg text-sm text-foreground"
                        />
                        <input
                            value={editing.contactRole ?? ""}
                            onChange={(e) => setEditing({ ...editing, contactRole: e.target.value })}
                            placeholder="Role (Owner / Driver…)"
                            className="px-3 py-2 bg-secondary border border-border rounded-lg text-sm text-foreground"
                        />
                    </div>
                    <input
                        value={editing.phoneNumber}
                        onChange={(e) => setEditing({ ...editing, phoneNumber: e.target.value })}
                        placeholder="Phone number *"
                        className="w-full px-3 py-2 bg-secondary border border-border rounded-lg text-sm text-foreground"
                    />
                    <input
                        value={editing.notes ?? ""}
                        onChange={(e) => setEditing({ ...editing, notes: e.target.value })}
                        placeholder="Notes (optional)"
                        className="w-full px-3 py-2 bg-secondary border border-border rounded-lg text-sm text-foreground"
                    />
                    <div className="flex justify-end gap-2 pt-1">
                        <button
                            onClick={() => setEditing(null)}
                            className="flex items-center gap-1 px-3 py-1.5 text-xs font-medium rounded-lg bg-muted text-muted-foreground hover:bg-muted/80 transition-colors"
                        >
                            <X className="w-3.5 h-3.5" /> Cancel
                        </button>
                        <button
                            onClick={save}
                            disabled={saving}
                            className="flex items-center gap-1 px-3 py-1.5 text-xs font-medium rounded-lg bg-primary text-primary-foreground hover:opacity-90 transition-opacity disabled:opacity-50"
                        >
                            <Save className="w-3.5 h-3.5" /> {saving ? "Saving…" : editing.id ? "Update" : "Save"}
                        </button>
                    </div>
                </div>
            )}
        </GlassCard>
    );
}
