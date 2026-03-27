"use client";

import React, { useState, useRef, useEffect } from "react";
import { Search, X, FileText } from "lucide-react";
import { getInvoiceHistory } from "@/lib/api/station";

interface InvoiceAutocompleteProps {
    value: any; // { id, billNo, ... } or null
    onChange: (invoice: any) => void;
    placeholder?: string;
    className?: string;
}

export function InvoiceAutocomplete({
    value,
    onChange,
    placeholder = "Search by bill #...",
    className = "",
}: InvoiceAutocompleteProps) {
    const [search, setSearch] = useState("");
    const [suggestions, setSuggestions] = useState<any[]>([]);
    const [isOpen, setIsOpen] = useState(false);
    const wrapperRef = useRef<HTMLDivElement>(null);
    const inputRef = useRef<HTMLInputElement>(null);

    useEffect(() => {
        const handleClickOutside = (e: MouseEvent) => {
            if (wrapperRef.current && !wrapperRef.current.contains(e.target as Node)) {
                setIsOpen(false);
            }
        };
        document.addEventListener("mousedown", handleClickOutside);
        return () => document.removeEventListener("mousedown", handleClickOutside);
    }, []);

    const handleSearch = async (val: string) => {
        setSearch(val);
        if (val.length < 2) { setSuggestions([]); return; }
        try {
            const data = await getInvoiceHistory(0, 10, { search: val });
            setSuggestions(data.content || []);
            setIsOpen(true);
        } catch (err) {
            console.error(err);
        }
    };

    const handleSelect = (inv: any) => {
        onChange(inv);
        setSearch("");
        setIsOpen(false);
    };

    const handleClear = () => {
        onChange(null);
        setSearch("");
        setSuggestions([]);
        inputRef.current?.focus();
    };

    return (
        <div ref={wrapperRef} className={`relative ${className}`}>
            <div className="relative">
                <FileText className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground pointer-events-none" />
                {value ? (
                    <div className="w-full pl-9 pr-9 py-2 bg-card border border-border rounded-lg text-foreground flex items-center text-sm">
                        <span className="font-medium">{value.billNo || `#${value.id}`}</span>
                        <span className="text-muted-foreground ml-2 truncate">
                            {value.customer?.name ? `— ${value.customer.name}` : ""}
                            {value.netAmount ? ` (₹${Number(value.netAmount).toLocaleString("en-IN")})` : ""}
                        </span>
                        <button
                            type="button"
                            onClick={handleClear}
                            className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                        >
                            <X className="w-4 h-4" />
                        </button>
                    </div>
                ) : (
                    <input
                        ref={inputRef}
                        type="text"
                        placeholder={placeholder}
                        value={search}
                        onChange={(e) => handleSearch(e.target.value)}
                        className="w-full pl-9 pr-4 py-2 bg-card border border-border rounded-lg text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary/50 placeholder:text-muted-foreground"
                    />
                )}
            </div>
            {isOpen && suggestions.length > 0 && !value && (
                <div className="absolute top-full left-0 right-0 mt-1 bg-card border border-border rounded-lg shadow-xl z-50 overflow-hidden max-h-48 overflow-y-auto">
                    {suggestions.map((inv: any) => (
                        <button
                            key={inv.id}
                            type="button"
                            className="w-full px-4 py-2 text-left hover:bg-primary/10 text-foreground transition-colors text-sm"
                            onClick={() => handleSelect(inv)}
                        >
                            <span className="font-medium">{inv.billNo || `#${inv.id}`}</span>
                            <span className="text-muted-foreground ml-2">
                                {inv.billType === "CREDIT" ? "Credit" : "Cash"}
                                {inv.customer?.name ? ` — ${inv.customer.name}` : ""}
                            </span>
                            <span className="float-right text-xs text-muted-foreground">
                                ₹{Number(inv.netAmount || 0).toLocaleString("en-IN")}
                            </span>
                        </button>
                    ))}
                </div>
            )}
        </div>
    );
}
