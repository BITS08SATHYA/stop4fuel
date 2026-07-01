"use client";

import React, { useState, useRef, useEffect, useCallback } from "react";
import { createPortal } from "react-dom";
import { ChevronDown } from "lucide-react";

interface StyledSelectOption {
    value: string;
    label: string;
}

interface StyledSelectProps {
    value: string;
    onChange: (value: string) => void;
    options: StyledSelectOption[];
    placeholder?: string;
    className?: string;
}

const MENU_MAX_H = 240; // px — matches the old max-h-60

export function StyledSelect({
    value,
    onChange,
    options,
    placeholder = "Select...",
    className = "",
}: StyledSelectProps) {
    const [isOpen, setIsOpen] = useState(false);
    const [menuStyle, setMenuStyle] = useState<React.CSSProperties>({});
    const wrapperRef = useRef<HTMLDivElement>(null);
    const buttonRef = useRef<HTMLButtonElement>(null);
    const menuRef = useRef<HTMLDivElement>(null);

    // Position the menu with fixed coordinates off the button's rect so it renders in a
    // body-level portal — escaping any modal/overflow container that would otherwise clip it.
    // Flips upward when there isn't enough room below.
    const positionMenu = useCallback(() => {
        const btn = buttonRef.current;
        if (!btn) return;
        const rect = btn.getBoundingClientRect();
        const spaceBelow = window.innerHeight - rect.bottom;
        const spaceAbove = rect.top;
        const openUp = spaceBelow < Math.min(MENU_MAX_H, 200) && spaceAbove > spaceBelow;
        const maxHeight = Math.max(120, Math.min(MENU_MAX_H, (openUp ? spaceAbove : spaceBelow) - 12));
        const style: React.CSSProperties = {
            position: "fixed",
            left: rect.left,
            width: rect.width,
            maxHeight,
            zIndex: 10000,
        };
        if (openUp) {
            style.bottom = window.innerHeight - rect.top + 4;
        } else {
            style.top = rect.bottom + 4;
        }
        setMenuStyle(style);
    }, []);

    useEffect(() => {
        if (!isOpen) return;
        positionMenu();
        const reposition = () => positionMenu();
        // Capture scrolls on any ancestor (e.g. the modal body) so the menu tracks the button.
        window.addEventListener("scroll", reposition, true);
        window.addEventListener("resize", reposition);
        return () => {
            window.removeEventListener("scroll", reposition, true);
            window.removeEventListener("resize", reposition);
        };
    }, [isOpen, positionMenu]);

    useEffect(() => {
        const handleClickOutside = (e: MouseEvent) => {
            const t = e.target as Node;
            if (wrapperRef.current?.contains(t)) return;
            if (menuRef.current?.contains(t)) return; // menu is portaled outside the wrapper
            setIsOpen(false);
        };
        document.addEventListener("mousedown", handleClickOutside);
        return () => document.removeEventListener("mousedown", handleClickOutside);
    }, []);

    const selectedLabel = options.find((o) => o.value === value)?.label || placeholder;

    return (
        <div ref={wrapperRef} className={`relative ${className}`}>
            <button
                ref={buttonRef}
                type="button"
                onClick={() => setIsOpen((o) => !o)}
                className="w-full flex items-center justify-between px-3 py-2 bg-card border border-border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
            >
                <span className={value ? "text-foreground" : "text-muted-foreground"}>
                    {selectedLabel}
                </span>
                <ChevronDown size={14} className={`text-muted-foreground transition-transform ${isOpen ? "rotate-180" : ""}`} />
            </button>
            {isOpen && typeof document !== "undefined" && createPortal(
                <div
                    ref={menuRef}
                    style={menuStyle}
                    className="bg-secondary border border-border rounded-lg shadow-lg overflow-y-auto"
                >
                    {options.map((option) => (
                        <button
                            key={option.value}
                            type="button"
                            onClick={() => {
                                onChange(option.value);
                                setIsOpen(false);
                            }}
                            className={`w-full text-left px-3 py-2 text-sm hover:bg-muted transition-colors ${
                                option.value === value
                                    ? "bg-primary/10 text-primary font-medium"
                                    : "text-foreground"
                            }`}
                        >
                            {option.label}
                        </button>
                    ))}
                </div>,
                document.body
            )}
        </div>
    );
}
