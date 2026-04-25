"use client";

import React, { useState, useRef, useEffect } from "react";
import { Search, X } from "lucide-react";
import { Product } from "@/lib/api/station";

interface ProductAutocompleteProps {
    value: string | number;
    onChange: (id: string | number, product?: Product) => void;
    products: Product[];
    placeholder?: string;
    className?: string;
    disabled?: boolean;
}

export function ProductAutocomplete({
    value,
    onChange,
    products,
    placeholder = "Search product...",
    className = "",
    disabled = false,
}: ProductAutocompleteProps) {
    const [search, setSearch] = useState("");
    const [suggestions, setSuggestions] = useState<Product[]>([]);
    const [isOpen, setIsOpen] = useState(false);
    const [selectedLabel, setSelectedLabel] = useState("");
    const wrapperRef = useRef<HTMLDivElement>(null);
    const inputRef = useRef<HTMLInputElement>(null);

    const formatLabel = (p: Product) => `${p.name}${p.unit ? ` (${p.unit})` : ""}`;

    useEffect(() => {
        if (value) {
            const found = products.find((p) => String(p.id) === String(value));
            if (found) setSelectedLabel(formatLabel(found));
        } else {
            setSelectedLabel("");
            setSearch("");
        }
    }, [value, products]);

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

    const handleSearch = (val: string) => {
        setSearch(val);
        setIsOpen(true);
        if (!val) {
            setSuggestions(products);
            return;
        }
        const q = val.toLowerCase();
        setSuggestions(
            products.filter(
                (p) =>
                    p.name?.toLowerCase().includes(q) ||
                    p.brand?.toLowerCase().includes(q) ||
                    p.category?.toLowerCase().includes(q)
            )
        );
    };

    const handleSelect = (p: Product) => {
        setSelectedLabel(formatLabel(p));
        setSearch("");
        setIsOpen(false);
        onChange(p.id, p);
    };

    const handleClear = () => {
        setSelectedLabel("");
        setSearch("");
        setSuggestions([]);
        onChange("", undefined);
        inputRef.current?.focus();
    };

    const handleFocus = () => {
        setIsOpen(true);
        if (!search) setSuggestions(products);
    };

    return (
        <div ref={wrapperRef} className={`relative ${className}`}>
            <div className="relative">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground pointer-events-none" />
                {selectedLabel ? (
                    <div
                        className={`w-full pl-9 pr-9 py-2 bg-card border border-border rounded-lg text-foreground flex items-center ${!disabled ? "cursor-pointer hover:border-primary/50" : ""}`}
                        onClick={() => { if (!disabled) handleClear(); }}
                    >
                        <span className="truncate">{selectedLabel}</span>
                        {!disabled && (
                            <button
                                type="button"
                                onClick={(e) => { e.stopPropagation(); handleClear(); }}
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
            {isOpen && suggestions.length > 0 && !selectedLabel && (
                <div className="absolute top-full left-0 right-0 mt-1 bg-card border border-border rounded-lg shadow-xl z-50 overflow-hidden max-h-72 overflow-y-auto">
                    {suggestions.map((p) => (
                        <button
                            key={p.id}
                            type="button"
                            className="w-full px-4 py-2.5 text-left hover:bg-primary/10 text-foreground transition-colors flex items-center justify-between text-sm"
                            onClick={() => handleSelect(p)}
                        >
                            <span className="font-medium truncate">{p.name}</span>
                            <span className="text-xs text-muted-foreground ml-3 shrink-0">
                                {[p.brand, p.unit].filter(Boolean).join(" · ")}
                            </span>
                        </button>
                    ))}
                </div>
            )}
            {isOpen && suggestions.length === 0 && search && (
                <div className="absolute top-full left-0 right-0 mt-1 bg-card border border-border rounded-lg shadow-xl z-50 px-4 py-3 text-sm text-muted-foreground">
                    No products match &quot;{search}&quot;
                </div>
            )}
        </div>
    );
}
