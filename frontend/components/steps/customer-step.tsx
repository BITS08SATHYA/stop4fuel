import React from "react";
import { API_BASE_URL } from "@/lib/api/station";
import { FieldError, inputErrorClass } from "@/components/ui/field-error";

interface CustomerStepProps {
    data: any;
    updateData: (data: any) => void;
    errors?: Record<string, string | undefined>;
    clearError?: (field: string) => void;
}

export function CustomerStep({ data, updateData, errors = {}, clearError }: CustomerStepProps) {
    const [parties, setParties] = React.useState<any[]>([]);
    const [groups, setGroups] = React.useState<any[]>([]);
    const [ceilingType, setCeilingType] = React.useState<"amount" | "liters">("amount");

    React.useEffect(() => {
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

    const handleChange = (field: string, value: any) => {
        updateData({ ...data, [field]: value });
        clearError?.(field);
    };

    const addEmail = () => {
        const emails = data.emails || [];
        updateData({ ...data, emails: [...emails, ""] });
    };

    const updateEmail = (index: number, value: string) => {
        const emails = [...(data.emails || [])];
        emails[index] = value;
        updateData({ ...data, emails });
        clearError?.("emails");
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
        clearError?.("phoneNumbers");
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
                        Name <span className="text-red-500">*</span>
                    </label>
                    <input
                        type="text"
                        value={data.name || ""}
                        onChange={(e) => handleChange("name", e.target.value)}
                        className={`w-full bg-white/5 border border-white/10 rounded-lg px-4 py-2 text-white focus:outline-none focus:ring-2 focus:ring-cyan-500/50 ${inputErrorClass(errors.name)}`}
                        placeholder="John Doe"
                    />
                    <FieldError error={errors.name} />
                </div>
                <div>
                    <label className="block text-sm font-medium text-muted-foreground mb-1">
                        Username <span className="text-red-500">*</span>
                    </label>
                    <input
                        type="text"
                        value={data.username || ""}
                        onChange={(e) => handleChange("username", e.target.value)}
                        className={`w-full bg-white/5 border border-white/10 rounded-lg px-4 py-2 text-white focus:outline-none focus:ring-2 focus:ring-cyan-500/50 ${inputErrorClass(errors.username)}`}
                        placeholder="johndoe"
                    />
                    <FieldError error={errors.username} />
                </div>
            </div>

            <div className="grid grid-cols-2 gap-4">
                <div>
                    <label className="block text-sm font-medium text-muted-foreground mb-1">
                        Password <span className="text-red-500">*</span>
                    </label>
                    <input
                        type="password"
                        value={data.password || ""}
                        onChange={(e) => handleChange("password", e.target.value)}
                        className={`w-full bg-white/5 border border-white/10 rounded-lg px-4 py-2 text-white focus:outline-none focus:ring-2 focus:ring-cyan-500/50 ${inputErrorClass(errors.password)}`}
                        placeholder="********"
                    />
                    <FieldError error={errors.password} />
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
                    Emails <span className="text-red-500">*</span>
                </label>
                {(data.emails || []).map((emailVal: string, index: number) => (
                    <div key={index} className="flex gap-2 mb-2">
                        <input
                            type="text"
                            value={emailVal}
                            onChange={(e) => updateEmail(index, e.target.value)}
                            className={`w-full bg-white/5 border border-white/10 rounded-lg px-4 py-2 text-white focus:outline-none focus:ring-2 focus:ring-cyan-500/50 ${inputErrorClass(errors.emails)}`}
                            placeholder="john@example.com"
                        />
                        <button onClick={() => removeEmail(index)} className="text-red-500">Remove</button>
                    </div>
                ))}
                <FieldError error={errors.emails} />
                <button onClick={addEmail} className="text-cyan-500 text-sm">+ Add Email</button>
            </div>

            {/* Phones */}
            <div>
                <label className="block text-sm font-medium text-muted-foreground mb-1">
                    Phone Numbers <span className="text-red-500">*</span>
                </label>
                {(data.phoneNumbers || []).map((phoneVal: string, index: number) => (
                    <div key={index} className="flex gap-2 mb-2">
                        <input
                            type="text"
                            value={phoneVal}
                            onChange={(e) => updatePhone(index, e.target.value)}
                            className={`w-full bg-white/5 border border-white/10 rounded-lg px-4 py-2 text-white focus:outline-none focus:ring-2 focus:ring-cyan-500/50 ${inputErrorClass(errors.phoneNumbers)}`}
                            placeholder="+1 555 000 0000"
                        />
                        <button onClick={() => removePhone(index)} className="text-red-500">Remove</button>
                    </div>
                ))}
                <FieldError error={errors.phoneNumbers} />
                <button onClick={addPhone} className="text-cyan-500 text-sm">+ Add Phone</button>
            </div>

            <div className="grid grid-cols-3 gap-4">
                <div>
                    <label className="block text-sm font-medium text-muted-foreground mb-1">
                        Category <span className="text-red-500">*</span>
                    </label>
                    <select
                        value={data.customerCategory || ""}
                        onChange={(e) => { handleChange("customerCategory", e.target.value); }}
                        className={`w-full bg-white/5 border border-white/10 rounded-lg px-4 py-2 text-white focus:outline-none focus:ring-2 focus:ring-cyan-500/50 ${inputErrorClass(errors.customerCategory)}`}
                    >
                        <option value="" className="bg-slate-900">Select Category</option>
                        <option value="GOVERNMENT" className="bg-slate-900">Government</option>
                        <option value="NON_GOVERNMENT" className="bg-slate-900">Non-Government</option>
                    </select>
                    <FieldError error={errors.customerCategory} />
                </div>
                <div>
                    <label className="block text-sm font-medium text-muted-foreground mb-1">
                        Party Type <span className="text-red-500">*</span>
                    </label>
                    <select
                        value={data.party?.id || ""}
                        onChange={(e) => {
                            const selectedParty = parties.find(p => p.id === Number(e.target.value));
                            updateData({ ...data, party: selectedParty });
                            clearError?.("party");
                        }}
                        className={`w-full bg-white/5 border border-white/10 rounded-lg px-4 py-2 text-white focus:outline-none focus:ring-2 focus:ring-cyan-500/50 ${inputErrorClass(errors.party)}`}
                    >
                        <option value="" className="bg-slate-900">Select Party</option>
                        {parties.map(party => (
                            <option key={party.id} value={party.id} className="bg-slate-900">{party.partyType}</option>
                        ))}
                    </select>
                    <FieldError error={errors.party} />
                </div>
                <div>
                    <label className="block text-sm font-medium text-muted-foreground mb-1">
                        Group <span className="text-red-500">*</span>
                    </label>
                    <select
                        value={data.group?.id || ""}
                        onChange={(e) => {
                            const selectedGroup = groups.find(g => g.id === Number(e.target.value));
                            updateData({ ...data, group: selectedGroup });
                            clearError?.("group");
                        }}
                        className={`w-full bg-white/5 border border-white/10 rounded-lg px-4 py-2 text-white focus:outline-none focus:ring-2 focus:ring-cyan-500/50 ${inputErrorClass(errors.group)}`}
                    >
                        <option value="" className="bg-slate-900">Select Group</option>
                        {groups.map(group => (
                            <option key={group.id} value={group.id} className="bg-slate-900">{group.groupName}</option>
                        ))}
                    </select>
                    <FieldError error={errors.group} />
                </div>
            </div>

            <div>
                <label className="block text-sm font-medium text-muted-foreground mb-1">
                    Address <span className="text-red-500">*</span>
                </label>
                <textarea
                    value={data.address || ""}
                    onChange={(e) => { updateData({ ...data, address: e.target.value }); clearError?.("address"); }}
                    className={`w-full bg-white/5 border border-white/10 rounded-lg px-4 py-2 text-white focus:outline-none focus:ring-2 focus:ring-cyan-500/50 ${inputErrorClass(errors.address)}`}
                    placeholder="Enter full address"
                    rows={2}
                />
                <FieldError error={errors.address} />
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
                        Max Ceiling Value <span className="text-red-500">*</span>
                    </label>
                    {ceilingType === "amount" ? (
                        <>
                            <input
                                type="number"
                                value={data.creditLimitAmount || ""}
                                onChange={(e) => { updateData({ ...data, creditLimitAmount: e.target.value, creditLimitLiters: null }); clearError?.("creditLimitValue"); }}
                                className={`w-full bg-white/5 border border-white/10 rounded-lg px-4 py-2 text-white focus:outline-none focus:ring-2 focus:ring-cyan-500/50 ${inputErrorClass(errors.creditLimitValue)}`}
                                placeholder="10000"
                            />
                            <FieldError error={errors.creditLimitValue} />
                        </>
                    ) : (
                        <>
                            <input
                                type="number"
                                value={data.creditLimitLiters || ""}
                                onChange={(e) => { updateData({ ...data, creditLimitLiters: e.target.value, creditLimitAmount: null }); clearError?.("creditLimitValue"); }}
                                className={`w-full bg-white/5 border border-white/10 rounded-lg px-4 py-2 text-white focus:outline-none focus:ring-2 focus:ring-cyan-500/50 ${inputErrorClass(errors.creditLimitValue)}`}
                                placeholder="500"
                            />
                            <FieldError error={errors.creditLimitValue} />
                        </>
                    )}
                </div>
            </div>

            <div>
                <label className="block text-sm font-medium text-muted-foreground mb-1">
                    GST Number
                </label>
                <input
                    type="text"
                    value={data.gstNumber || ""}
                    onChange={(e) => handleChange("gstNumber", e.target.value)}
                    className="w-full bg-white/5 border border-white/10 rounded-lg px-4 py-2 text-white focus:outline-none focus:ring-2 focus:ring-cyan-500/50"
                    placeholder="e.g. 33AABCT1332L1ZZ"
                    maxLength={15}
                />
            </div>

            {/* GPS Coordinates */}
            <div className="grid grid-cols-2 gap-4">
                <div>
                    <label className="block text-sm font-medium text-muted-foreground mb-1">
                        Latitude
                    </label>
                    <input
                        type="number"
                        step="0.0000001"
                        value={data.latitude || ""}
                        onChange={(e) => handleChange("latitude", e.target.value ? parseFloat(e.target.value) : null)}
                        className="w-full bg-white/5 border border-white/10 rounded-lg px-4 py-2 text-white focus:outline-none focus:ring-2 focus:ring-cyan-500/50"
                        placeholder="e.g. 13.0827"
                    />
                </div>
                <div>
                    <label className="block text-sm font-medium text-muted-foreground mb-1">
                        Longitude
                    </label>
                    <input
                        type="number"
                        step="0.0000001"
                        value={data.longitude || ""}
                        onChange={(e) => handleChange("longitude", e.target.value ? parseFloat(e.target.value) : null)}
                        className="w-full bg-white/5 border border-white/10 rounded-lg px-4 py-2 text-white focus:outline-none focus:ring-2 focus:ring-cyan-500/50"
                        placeholder="e.g. 80.2707"
                    />
                </div>
            </div>

            {/* Statement Preferences */}
            <div className="grid grid-cols-3 gap-4">
                <div>
                    <label className="block text-sm font-medium text-muted-foreground mb-1">
                        Statement Frequency
                    </label>
                    <select
                        value={data.statementFrequency || ""}
                        onChange={(e) => handleChange("statementFrequency", e.target.value || null)}
                        className="w-full bg-white/5 border border-white/10 rounded-lg px-4 py-2 text-white focus:outline-none focus:ring-2 focus:ring-cyan-500/50"
                    >
                        <option value="" className="bg-slate-900">Select Frequency</option>
                        <option value="MONTHLY" className="bg-slate-900">Monthly</option>
                        <option value="BIWEEKLY" className="bg-slate-900">Biweekly</option>
                        <option value="WEEKLY" className="bg-slate-900">Weekly</option>
                        <option value="CUSTOM" className="bg-slate-900">Custom</option>
                    </select>
                </div>
                <div>
                    <label className="block text-sm font-medium text-muted-foreground mb-1">
                        Statement Grouping
                    </label>
                    <select
                        value={data.statementGrouping || ""}
                        onChange={(e) => handleChange("statementGrouping", e.target.value || null)}
                        className="w-full bg-white/5 border border-white/10 rounded-lg px-4 py-2 text-white focus:outline-none focus:ring-2 focus:ring-cyan-500/50"
                    >
                        <option value="" className="bg-slate-900">Select Grouping</option>
                        <option value="CUSTOMER_WISE" className="bg-slate-900">Customer Wise</option>
                        <option value="VEHICLE_WISE" className="bg-slate-900">Vehicle Wise</option>
                    </select>
                </div>
                <div>
                    <label className="block text-sm font-medium text-muted-foreground mb-1">
                        Threshold Amount
                    </label>
                    <input
                        type="number"
                        value={data.statementThresholdAmount || ""}
                        onChange={(e) => handleChange("statementThresholdAmount", e.target.value ? parseFloat(e.target.value) : null)}
                        className="w-full bg-white/5 border border-white/10 rounded-lg px-4 py-2 text-white focus:outline-none focus:ring-2 focus:ring-cyan-500/50"
                        placeholder="e.g. 50000"
                    />
                </div>
            </div>
        </div>
    );
}
