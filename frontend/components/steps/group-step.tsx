import React from "react";

interface GroupStepProps {
    data: any;
    updateData: (data: any) => void;
}

export function GroupStep({ data, updateData }: GroupStepProps) {
    return (
        <div className="space-y-4">
            <div>
                <label className="block text-sm font-medium text-muted-foreground mb-1">
                    Group Name
                </label>
                <input
                    type="text"
                    value={data.groupName || ""}
                    onChange={(e) => updateData({ ...data, groupName: e.target.value })}
                    className="w-full bg-white/5 border border-white/10 rounded-lg px-4 py-2 text-white focus:outline-none focus:ring-2 focus:ring-cyan-500/50"
                    placeholder="Enter group name"
                />
                <p className="text-xs text-muted-foreground mt-1">
                    If the group exists, it will be selected. Otherwise, a new group will be created.
                </p>
            </div>
            <div>
                <label className="block text-sm font-medium text-muted-foreground mb-1">
                    Description (Optional)
                </label>
                <textarea
                    value={data.groupDescription || ""}
                    onChange={(e) => updateData({ ...data, groupDescription: e.target.value })}
                    className="w-full bg-white/5 border border-white/10 rounded-lg px-4 py-2 text-white focus:outline-none focus:ring-2 focus:ring-cyan-500/50"
                    placeholder="Enter group description"
                    rows={3}
                />
            </div>
        </div>
    );
}
