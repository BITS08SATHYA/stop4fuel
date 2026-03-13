import React from "react";
import { API_BASE_URL } from "@/lib/api/station";

interface CustomerStepProps {
    data: any;
    updateData: (data: any) => void;
}

export function CustomerStep({ data, updateData }: CustomerStepProps) {
    const [parties, setParties] = React.useState<any[]>([]);
    const [groups, setGroups] = React.useState<any[]>([]);
    const [ceilingType, setCeilingType] = React.useState<"amount" | "liters">("amount");

    React.useEffect(() => {
        // Fetch parties and groups
        // Assuming API base URL is configured or proxy is set up. Using relative path for now or hardcoded if needed.
        // For development, we might need full URL if CORS is an issue or proxy not set.
        const fetchOptions = async () => {
            try {
                const partiesRes = await fetch(`${API_BASE_URL}/parties`);
                const partiesData = await partiesRes.json();
                setParties(Array.isArray(partiesData) ? partiesData : partiesData.content || []);

                const groupsRes = await fetch(`${API_BASE_URL}/groups`);
                const groupsData = await groupsRes.json();
                setGroups(Array.isArray(groupsData) ? groupsData : groupsData.content || []);
            } catch (error) {
                console.error("Failed to fetch options", error);
            }
        };
        fetchOptions();
    }, []);

    const addEmail = () => {
        const emails = data.emails || [];
        updateData({ ...data, emails: [...emails, ""] });
    };

    const updateEmail = (index: number, value: string) => {
        const emails = [...(data.emails || [])];
        emails[index] = value;
        updateData({ ...data, emails });
    };

    const removeEmail = (index: number) => {
        const emails = [...(data.emails || [])];
        emails.splice(index, 1);
        updateData({ ...data, emails });
    };

    const addPhone = () => {
        const phoneNumbers = data.phoneNumbers || [];
        updateData({ ...data, phoneNumbers: [...phoneNumbers, ""] });
    };

    const updatePhone = (index: number, value: string) => {
        const phoneNumbers = [...(data.phoneNumbers || [])];
        phoneNumbers[index] = value;
        updateData({ ...data, phoneNumbers });
    };

    const removePhone = (index: number) => {
        const phoneNumbers = [...(data.phoneNumbers || [])];
        phoneNumbers.splice(index, 1);
        updateData({ ...data, phoneNumbers });
    };

    return (
        <div className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
                <div>
                    <label className="block text-sm font-medium text-muted-foreground mb-1">
                        Name
                    </label>
                    <input
                        type="text"
                        value={data.name || ""}
                        onChange={(e) => updateData({ ...data, name: e.target.value })}
                        className="w-full bg-white/5 border border-white/10 rounded-lg px-4 py-2 text-white focus:outline-none focus:ring-2 focus:ring-cyan-500/50"
                        placeholder="John Doe"
                    />
                </div>
                <div>
                    <label className="block text-sm font-medium text-muted-foreground mb-1">
                        Username
                    </label>
                    <input
                        type="text"
                        value={data.username || ""}
                        onChange={(e) => updateData({ ...data, username: e.target.value })}
                        className="w-full bg-white/5 border border-white/10 rounded-lg px-4 py-2 text-white focus:outline-none focus:ring-2 focus:ring-cyan-500/50"
                        placeholder="johndoe"
                    />
                </div>
            </div>

            <div className="grid grid-cols-2 gap-4">
                <div>
                    <label className="block text-sm font-medium text-muted-foreground mb-1">
                        Password
                    </label>
                    <input
                        type="password"
                        value={data.password || ""}
                        onChange={(e) => updateData({ ...data, password: e.target.value })}
                        className="w-full bg-white/5 border border-white/10 rounded-lg px-4 py-2 text-white focus:outline-none focus:ring-2 focus:ring-cyan-500/50"
                        placeholder="********"
                    />
                </div>
                <div>
                    <label className="block text-sm font-medium text-muted-foreground mb-1">
                        Join Date
                    </label>
                    <input
                        type="date"
                        value={data.joinDate || ""}
                        onChange={(e) => updateData({ ...data, joinDate: e.target.value })}
                        className="w-full bg-white/5 border border-white/10 rounded-lg px-4 py-2 text-white focus:outline-none focus:ring-2 focus:ring-cyan-500/50"
                    />
                </div>
            </div>

            {/* Emails */}
            <div>
                <label className="block text-sm font-medium text-muted-foreground mb-1">
                    Emails
                </label>
                {(data.emails || []).map((email: string, index: number) => (
                    <div key={index} className="flex gap-2 mb-2">
                        <input
                            type="email"
                            value={email}
                            onChange={(e) => updateEmail(index, e.target.value)}
                            className="w-full bg-white/5 border border-white/10 rounded-lg px-4 py-2 text-white focus:outline-none focus:ring-2 focus:ring-cyan-500/50"
                            placeholder="john@example.com"
                        />
                        <button onClick={() => removeEmail(index)} className="text-red-500">Remove</button>
                    </div>
                ))}
                <button onClick={addEmail} className="text-cyan-500 text-sm">+ Add Email</button>
            </div>

            {/* Phones */}
            <div>
                <label className="block text-sm font-medium text-muted-foreground mb-1">
                    Phone Numbers
                </label>
                {(data.phoneNumbers || []).map((phone: string, index: number) => (
                    <div key={index} className="flex gap-2 mb-2">
                        <input
                            type="text"
                            value={phone}
                            onChange={(e) => updatePhone(index, e.target.value)}
                            className="w-full bg-white/5 border border-white/10 rounded-lg px-4 py-2 text-white focus:outline-none focus:ring-2 focus:ring-cyan-500/50"
                            placeholder="+1 555 000 0000"
                        />
                        <button onClick={() => removePhone(index)} className="text-red-500">Remove</button>
                    </div>
                ))}
                <button onClick={addPhone} className="text-cyan-500 text-sm">+ Add Phone</button>
            </div>

            <div className="grid grid-cols-2 gap-4">
                <div>
                    <label className="block text-sm font-medium text-muted-foreground mb-1">
                        Party Type
                    </label>
                    <select
                        value={data.party?.id || ""}
                        onChange={(e) => {
                            const selectedParty = parties.find(p => p.id === Number(e.target.value));
                            updateData({ ...data, party: selectedParty });
                        }}
                        className="w-full bg-white/5 border border-white/10 rounded-lg px-4 py-2 text-white focus:outline-none focus:ring-2 focus:ring-cyan-500/50"
                    >
                        <option value="" className="bg-slate-900">Select Party</option>
                        {parties.map(party => (
                            <option key={party.id} value={party.id} className="bg-slate-900">{party.partyType}</option>
                        ))}
                    </select>
                </div>
                <div>
                    <label className="block text-sm font-medium text-muted-foreground mb-1">
                        Group
                    </label>
                    <select
                        value={data.group?.id || ""}
                        onChange={(e) => {
                            const selectedGroup = groups.find(g => g.id === Number(e.target.value));
                            updateData({ ...data, group: selectedGroup });
                        }}
                        className="w-full bg-white/5 border border-white/10 rounded-lg px-4 py-2 text-white focus:outline-none focus:ring-2 focus:ring-cyan-500/50"
                    >
                        <option value="" className="bg-slate-900">Select Group</option>
                        {groups.map(group => (
                            <option key={group.id} value={group.id} className="bg-slate-900">{group.groupName}</option>
                        ))}
                    </select>
                </div>
            </div>

            <div>
                <label className="block text-sm font-medium text-muted-foreground mb-1">
                    Address
                </label>
                <textarea
                    value={data.address || ""}
                    onChange={(e) => updateData({ ...data, address: e.target.value })}
                    className="w-full bg-white/5 border border-white/10 rounded-lg px-4 py-2 text-white focus:outline-none focus:ring-2 focus:ring-cyan-500/50"
                    placeholder="Enter full address"
                    rows={2}
                />
            </div>

            <div className="grid grid-cols-2 gap-4">
                <div>
                    <label className="block text-sm font-medium text-muted-foreground mb-1">
                        Max Ceiling Type
                    </label>
                    <select
                        value={ceilingType}
                        onChange={(e) => setCeilingType(e.target.value as "amount" | "liters")}
                        className="w-full bg-white/5 border border-white/10 rounded-lg px-4 py-2 text-white focus:outline-none focus:ring-2 focus:ring-cyan-500/50"
                    >
                        <option value="amount" className="bg-slate-900">Amount ($)</option>
                        <option value="liters" className="bg-slate-900">Liters (L)</option>
                    </select>
                </div>
                <div>
                    <label className="block text-sm font-medium text-muted-foreground mb-1">
                        Max Ceiling Value
                    </label>
                    {ceilingType === "amount" ? (
                        <input
                            type="number"
                            value={data.creditLimitAmount || ""}
                            onChange={(e) => updateData({ ...data, creditLimitAmount: e.target.value, creditLimitLiters: null })}
                            className="w-full bg-white/5 border border-white/10 rounded-lg px-4 py-2 text-white focus:outline-none focus:ring-2 focus:ring-cyan-500/50"
                            placeholder="10000"
                        />
                    ) : (
                        <input
                            type="number"
                            value={data.creditLimitLiters || ""}
                            onChange={(e) => updateData({ ...data, creditLimitLiters: e.target.value, creditLimitAmount: null })}
                            className="w-full bg-white/5 border border-white/10 rounded-lg px-4 py-2 text-white focus:outline-none focus:ring-2 focus:ring-cyan-500/50"
                            placeholder="500"
                        />
                    )}
                </div>
            </div>
        </div>
    );
}
