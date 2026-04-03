"use client";

import { Search, Calendar } from "lucide-react";

interface AdvanceFiltersProps {
    searchQuery: string;
    onSearchChange: (value: string) => void;
    statusFilter: string;
    onStatusFilterChange: (value: string) => void;
    typeFilter: string;
    onTypeFilterChange: (value: string) => void;
    fromDate: string;
    onFromDateChange: (value: string) => void;
    toDate: string;
    onToDateChange: (value: string) => void;
    viewMode: "shift" | "dates";
    onDateSearch: () => void;
    onShowCurrentShift: () => void;
}

export function AdvanceFilters({
    searchQuery, onSearchChange,
    statusFilter, onStatusFilterChange,
    typeFilter, onTypeFilterChange,
    fromDate, onFromDateChange,
    toDate, onToDateChange,
    viewMode,
    onDateSearch,
    onShowCurrentShift,
}: AdvanceFiltersProps) {
    return (
        <div className="mb-4 flex flex-wrap gap-3 items-center">
            <div className="relative flex-1 min-w-[200px] max-w-md">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                <input
                    type="text"
                    placeholder="Search recipient, purpose, employee..."
                    value={searchQuery}
                    onChange={(e) => onSearchChange(e.target.value)}
                    className="w-full pl-10 pr-4 py-2.5 bg-card border border-border rounded-xl text-foreground text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                />
            </div>
            <select
                value={statusFilter}
                onChange={(e) => onStatusFilterChange(e.target.value)}
                className="px-4 py-2.5 bg-card border border-border rounded-xl text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
            >
                <option value="ALL">All Status</option>
                <option value="GIVEN">Given</option>
                <option value="RETURNED">Returned</option>
                <option value="PARTIALLY_RETURNED">Partially Returned</option>
                <option value="SETTLED">Settled</option>
                <option value="CANCELLED">Cancelled</option>
            </select>
            <select
                value={typeFilter}
                onChange={(e) => onTypeFilterChange(e.target.value)}
                className="px-4 py-2.5 bg-card border border-border rounded-xl text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
            >
                <option value="ALL">All Types</option>
                <option value="CASH_ADVANCE">Cash Advance</option>
                <option value="SALARY_ADVANCE">Salary Advance</option>
            </select>
            <div className="flex items-center gap-2">
                <div className="flex items-center gap-1">
                    <Calendar className="w-4 h-4 text-muted-foreground" />
                    <input
                        type="date"
                        value={fromDate}
                        onChange={(e) => onFromDateChange(e.target.value)}
                        className="px-3 py-2.5 bg-card border border-border rounded-xl text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
                    />
                </div>
                <span className="text-muted-foreground text-sm">to</span>
                <input
                    type="date"
                    value={toDate}
                    onChange={(e) => onToDateChange(e.target.value)}
                    className="px-3 py-2.5 bg-card border border-border rounded-xl text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
                />
                <button
                    onClick={onDateSearch}
                    disabled={!fromDate || !toDate}
                    className="px-4 py-2.5 bg-primary text-primary-foreground rounded-xl text-sm font-medium hover:bg-primary/90 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                >
                    Search
                </button>
            </div>
            {viewMode === "dates" && (
                <button
                    onClick={onShowCurrentShift}
                    className="px-4 py-2.5 bg-card border border-border rounded-xl text-sm font-medium text-foreground hover:bg-primary/10 transition-colors"
                >
                    Current Shift
                </button>
            )}
        </div>
    );
}
