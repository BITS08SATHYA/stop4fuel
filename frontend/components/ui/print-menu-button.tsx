"use client";

import { useState, useRef, useEffect } from "react";
import { Printer, ChevronDown } from "lucide-react";
import type { PrinterTarget } from "@/lib/invoice-print";

// Split print button for a table row. The icon prints to the page default
// target; the caret opens a small menu to print THAT bill to a chosen printer
// as a one-off override (it does not change the page default). The page owns
// the actual printInvoice call via onPrint so this stays dumb and reusable.
//
// Mirrors StyledSelect's click-outside + absolute z-[9999] menu so it matches
// the other dropdowns and closes correctly inside a row.
const TARGETS: { value: PrinterTarget; label: string }[] = [
    { value: "thermal", label: "Thermal — RP 3230" },
    { value: "dotmatrix", label: "Dot-matrix — MSP 250" },
];

interface PrintMenuButtonProps {
    defaultTarget: PrinterTarget;
    onPrint: (target: PrinterTarget) => void;
}

export function PrintMenuButton({ defaultTarget, onPrint }: PrintMenuButtonProps) {
    const [isOpen, setIsOpen] = useState(false);
    const wrapperRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        const handleClickOutside = (e: MouseEvent) => {
            if (wrapperRef.current && !wrapperRef.current.contains(e.target as Node)) {
                setIsOpen(false);
            }
        };
        document.addEventListener("mousedown", handleClickOutside);
        return () => document.removeEventListener("mousedown", handleClickOutside);
    }, []);

    return (
        <div ref={wrapperRef} className="relative inline-flex items-center">
            <button
                onClick={() => onPrint(defaultTarget)}
                className="p-1.5 rounded-l-md text-muted-foreground hover:text-green-500 hover:bg-green-500/10 transition-colors"
                title={`Print to ${TARGETS.find(t => t.value === defaultTarget)?.label || "default printer"}`}
            >
                <Printer className="w-3.5 h-3.5" />
            </button>
            <button
                onClick={() => setIsOpen(o => !o)}
                className="px-0.5 py-1.5 rounded-r-md text-muted-foreground hover:text-green-500 hover:bg-green-500/10 transition-colors"
                title="Choose printer"
            >
                <ChevronDown size={12} className={`transition-transform ${isOpen ? "rotate-180" : ""}`} />
            </button>
            {isOpen && (
                <div className="absolute right-0 top-full z-[9999] mt-1 w-48 bg-secondary border border-border rounded-lg shadow-lg overflow-hidden">
                    {TARGETS.map(t => (
                        <button
                            key={t.value}
                            onClick={() => { setIsOpen(false); onPrint(t.value); }}
                            className={`w-full text-left px-3 py-2 text-sm hover:bg-muted transition-colors ${
                                t.value === defaultTarget ? "bg-primary/10 text-primary font-medium" : "text-foreground"
                            }`}
                        >
                            {t.label}
                        </button>
                    ))}
                </div>
            )}
        </div>
    );
}
