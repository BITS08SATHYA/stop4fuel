import React from "react";
import { API_BASE_URL } from "@/lib/api/station";
import { fetchWithAuth } from "@/lib/api/fetch-with-auth";
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
    const [categories, setCategories] = React.useState<any[]>([]);
    const [showPassword, setShowPassword] = React.useState(false);
    const [ceilingType, setCeilingType] = React.useState<"amount" | "liters">("amount");
    const [customerType, setCustomerType] = React.useState<string>(data.customerCategory?.categoryType || "");

    React.useEffect(() => {
        const fetchOptions = async () => {
            try {
                const [partiesRes, groupsRes, catRes] = await Promise.all([
                    fetchWithAuth(`${API_BASE_URL}/parties`),
                    fetchWithAuth(`${API_BASE_URL}/groups`),
                    fetchWithAuth(`${API_BASE_URL}/customer-categories`),
                ]);
                const partiesData = await partiesRes.json();
                setParties(Array.isArray(partiesData) ? partiesData : partiesData.content || []);
                const groupsData = await groupsRes.json();
                setGroups(Array.isArray(groupsData) ? groupsData : groupsData.content || []);
                const catData = await catRes.json();
                setCategories(Array.isArray(catData) ? catData : []);
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
                    <div className="relative">
                        <input
                            type={showPassword ? "text" : "password"}
                            value={data.password || ""}
                            onChange={(e) => handleChange("password", e.target.value)}
                            className={`w-full bg-white/5 border border-white/10 rounded-lg px-4 py-2 pr-10 text-white focus:outline-none focus:ring-2 focus:ring-cyan-500/50 ${inputErrorClass(errors.password)}`}
                            placeholder="********"
                        />
                        <button
                            type="button"
                            onClick={() => setShowPassword(!showPassword)}
                            className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-white transition-colors"
                        >
                            {showPassword ? (
                                <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                                    <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94"/>
                                    <path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19"/>
                                    <line x1="1" y1="1" x2="23" y2="23"/>
                                </svg>
                            ) : (
                                <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                                    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                                    <circle cx="12" cy="12" r="3"/>
                                </svg>
                            )}
                        </button>
                    </div>
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

            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                <div>
                    <label className="block text-sm font-medium text-muted-foreground mb-1">
                        Customer Type <span className="text-red-500">*</span>
                    </label>
                    <select
                        value={customerType}
                        onChange={(e) => {
                            const type = e.target.value;
                            setCustomerType(type);
                            // Clear category when type changes
                            handleChange("customerCategory", null);
                            // If Government, auto-select the first government category (if any)
                            if (type === "GOVERNMENT") {
                                const govCat = categories.find(c => c.categoryType === "GOVERNMENT");
                                if (govCat) handleChange("customerCategory", govCat);
                            }
                        }}
                        className={`w-full bg-white/5 border border-white/10 rounded-lg px-4 py-2 text-foreground focus:outline-none focus:ring-2 focus:ring-cyan-500/50 ${inputErrorClass(errors.customerCategory)}`}
                    >
                        <option value="" className="bg-background text-foreground">Select Type</option>
                        <option value="GOVERNMENT" className="bg-background text-foreground">Government</option>
                        <option value="NON_GOVERNMENT" className="bg-background text-foreground">Non-Government</option>
                    </select>
                    {!customerType && <FieldError error={errors.customerCategory} />}
                </div>
                {customerType === "NON_GOVERNMENT" && (
                    <div>
                        <label className="block text-sm font-medium text-muted-foreground mb-1">
                            Category <span className="text-red-500">*</span>
                        </label>
                        <select
                            value={data.customerCategory?.id || ""}
                            onChange={(e) => {
                                const selected = categories.find(c => c.id === Number(e.target.value));
                                handleChange("customerCategory", selected || null);
                            }}
                            className={`w-full bg-white/5 border border-white/10 rounded-lg px-4 py-2 text-foreground focus:outline-none focus:ring-2 focus:ring-cyan-500/50 ${inputErrorClass(errors.customerCategory)}`}
                        >
                            <option value="" className="bg-background text-foreground">Select Category</option>
                            {categories.filter(c => c.categoryType === "NON_GOVERNMENT").map(c => (
                                <option key={c.id} value={c.id} className="bg-background text-foreground">{c.categoryName}</option>
                            ))}
                        </select>
                        <FieldError error={errors.customerCategory} />
                    </div>
                )}
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
                        className={`w-full bg-white/5 border border-white/10 rounded-lg px-4 py-2 text-foreground focus:outline-none focus:ring-2 focus:ring-cyan-500/50 ${inputErrorClass(errors.party)}`}
                    >
                        <option value="" className="bg-background text-foreground">Select Party</option>
                        {parties.map(party => (
                            <option key={party.id} value={party.id} className="bg-background text-foreground">{party.partyType}</option>
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
                        className={`w-full bg-white/5 border border-white/10 rounded-lg px-4 py-2 text-foreground focus:outline-none focus:ring-2 focus:ring-cyan-500/50 ${inputErrorClass(errors.group)}`}
                    >
                        <option value="" className="bg-background text-foreground">Select Group</option>
                        {groups.map(group => (
                            <option key={group.id} value={group.id} className="bg-background text-foreground">{group.groupName}</option>
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
                        className="w-full bg-white/5 border border-white/10 rounded-lg px-4 py-2 text-foreground focus:outline-none focus:ring-2 focus:ring-cyan-500/50"
                    >
                        <option value="amount" className="bg-background text-foreground">Amount ($)</option>
                        <option value="liters" className="bg-background text-foreground">Liters (L)</option>
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
                    Repayment Period (days)
                </label>
                <input
                    type="number"
                    min={1}
                    value={data.repaymentDays || ""}
                    onChange={(e) => updateData({ ...data, repaymentDays: e.target.value ? parseInt(e.target.value, 10) : null })}
                    className="w-full bg-white/5 border border-white/10 rounded-lg px-4 py-2 text-white focus:outline-none focus:ring-2 focus:ring-cyan-500/50"
                    placeholder="Leave blank to use station policy default"
                />
                <p className="text-xs text-muted-foreground mt-1">Auto-block when oldest unpaid bill exceeds this many days.</p>
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
            <div className="grid grid-cols-2 gap-4">
                <div>
                    <label className="block text-sm font-medium text-muted-foreground mb-1">
                        Statement Frequency
                    </label>
                    <select
                        value={data.statementFrequency || ""}
                        onChange={(e) => handleChange("statementFrequency", e.target.value || null)}
                        className="w-full bg-white/5 border border-white/10 rounded-lg px-4 py-2 text-foreground focus:outline-none focus:ring-2 focus:ring-cyan-500/50"
                    >
                        <option value="" className="bg-background text-foreground">Select Frequency</option>
                        <option value="MONTHLY" className="bg-background text-foreground">Monthly</option>
                        <option value="BIWEEKLY" className="bg-background text-foreground">Biweekly</option>
                        <option value="WEEKLY" className="bg-background text-foreground">Weekly</option>
                        <option value="CUSTOM" className="bg-background text-foreground">Custom</option>
                    </select>
                </div>
                <div>
                    <label className="block text-sm font-medium text-muted-foreground mb-1">
                        Statement Grouping
                    </label>
                    <select
                        value={data.statementGrouping || ""}
                        onChange={(e) => handleChange("statementGrouping", e.target.value || null)}
                        className="w-full bg-white/5 border border-white/10 rounded-lg px-4 py-2 text-foreground focus:outline-none focus:ring-2 focus:ring-cyan-500/50"
                    >
                        <option value="" className="bg-background text-foreground">Select Grouping</option>
                        <option value="CUSTOMER_WISE" className="bg-background text-foreground">Customer Wise</option>
                        <option value="VEHICLE_WISE" className="bg-background text-foreground">Vehicle Wise</option>
                        <option value="BILL_WISE" className="bg-background text-foreground">Bill Wise</option>
                    </select>
                </div>
            </div>
        </div>
    );
}
