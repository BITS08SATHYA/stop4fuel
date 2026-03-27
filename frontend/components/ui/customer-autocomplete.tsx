"use client";

import React, { useState, useRef, useEffect } from "react";
import { Search, X } from "lucide-react";
import { getCustomers } from "@/lib/api/station";

interface CustomerAutocompleteProps {
    value: string | number;
    onChange: (id: string | number, customer?: any) => void;
    customers?: any[];
    placeholder?: string;
    className?: string;
    disabled?: boolean;
}

export function CustomerAutocomplete({
    value,
    onChange,
    customers: preloadedCustomers,
    placeholder = "Search customer...",
    className = "",
    disabled = false,
}: CustomerAutocompleteProps) {
    const [search, setSearch] = useState("");
    const [suggestions, setSuggestions] = useState<any[]>([]);
    const [isOpen, setIsOpen] = useState(false);
    const [selectedName, setSelectedName] = useState("");
    const wrapperRef = useRef<HTMLDivElement>(null);
    const inputRef = useRef<HTMLInputElement>(null);

    // Set display name when value changes externally
    useEffect(() => {
        if (value && preloadedCustomers) {
            const found = preloadedCustomers.find((c: any) => String(c.id) === String(value));
            if (found) setSelectedName(found.name);
        } else if (!value) {
            setSelectedName("");
            setSearch("");
        }
    }, [value, preloadedCustomers]);

    // Close dropdown on outside click
    useEffect(() => {
        const handleClickOutside = (e: MouseEvent) => {
            if (wrapperRef.current && !wrapperRef.current.contains(e.target as Node)) {
                setIsOpen(false);
                if (!value) setSearch("");
            }
        };
        document.addEventListener("mousedown", handleClickOutside);
        return () => document.removeEventListener("mousedown", handleClickOutside);
    }, [value]);

    const handleSearch = async (val: string) => {
        setSearch(val);
        setIsOpen(true);

        if (val.length < 1) {
            // Show all preloaded customers if available
            if (preloadedCustomers) {
                setSuggestions(preloadedCustomers);
            } else {
                setSuggestions([]);
            }
            return;
        }

        if (preloadedCustomers) {
            // Filter locally from preloaded list
            const filtered = preloadedCustomers.filter((c: any) =>
                c.name?.toLowerCase().includes(val.toLowerCase()) ||
                c.phoneNumbers?.some((p: string) => p.includes(val))
            );
            setSuggestions(filtered);
        } else {
            // Fetch from API
            try {
                const data = await getCustomers(val);
                setSuggestions(data.content || data || []);
            } catch (err) {
                console.error(err);
            }
        }
    };

    const handleSelect = (c: any) => {
        setSelectedName(c.name);
        setSearch("");
        setIsOpen(false);
        onChange(c.id, c);
    };

    const handleClear = () => {
        setSelectedName("");
        setSearch("");
        setSuggestions([]);
        onChange("", undefined);
        inputRef.current?.focus();
    };

    const handleFocus = () => {
        setIsOpen(true);
        if (preloadedCustomers && !search) {
            setSuggestions(preloadedCustomers);
        }
    };

    return (
        <div ref={wrapperRef} className={`relative ${className}`}>
            <div className="relative">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground pointer-events-none" />
                {selectedName ? (
                    <div className="w-full pl-9 pr-9 py-2 bg-card border border-border rounded-lg text-foreground flex items-center">
                        <span className="truncate">{selectedName}</span>
                        {!disabled && (
                            <button
                                type="button"
                                onClick={handleClear}
                                className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                            >
                                <X className="w-4 h-4" />
                            </button>
                        )}
                    </div>
                ) : (
                    <input
                        ref={inputRef}
                        type="text"
                        placeholder={placeholder}
                        value={search}
                        onChange={(e) => handleSearch(e.target.value)}
                        onFocus={handleFocus}
                        disabled={disabled}
                        className="w-full pl-9 pr-4 py-2 bg-card border border-border rounded-lg text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50 placeholder:text-muted-foreground"
                    />
                )}
            </div>
            {isOpen && suggestions.length > 0 && !selectedName && (
                <div className="absolute top-full left-0 right-0 mt-1 bg-card border border-border rounded-lg shadow-xl z-50 overflow-hidden max-h-60 overflow-y-auto">
                    {suggestions.map((c: any) => (
                        <button
                            key={c.id}
                            type="button"
                            className="w-full px-4 py-2.5 text-left hover:bg-primary/10 text-foreground transition-colors flex items-center justify-between text-sm"
                            onClick={() => handleSelect(c)}
                        >
                            <span className="font-medium">{c.name}</span>
                            {c.phoneNumbers?.[0] && (
                                <span className="text-xs text-muted-foreground">{c.phoneNumbers[0]}</span>
                            )}
                        </button>
                    ))}
                </div>
            )}
        </div>
    );
}
