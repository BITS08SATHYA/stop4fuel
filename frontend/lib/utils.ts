import { type ClassValue, clsx } from "clsx";
import { twMerge } from "tailwind-merge";

export function cn(...inputs: ClassValue[]) {
    return twMerge(clsx(inputs));
}

/** Product units that count discrete items — render without decimals. */
export const WHOLE_COUNT_UNITS = [
    "PIECES", "PIECE", "PCS", "PC",
    "BOX", "BOXES",
    "PACKET", "PACKETS", "PKT", "PKTS",
    "BOTTLE", "BOTTLES",
    "CAN", "CANS",
    "NOS", "NO",
    "EACH", "UNIT", "UNITS",
] as const;

export function isWholeCountUnit(unit?: string | null): boolean {
    if (!unit) return false;
    return (WHOLE_COUNT_UNITS as readonly string[]).includes(unit.trim().toUpperCase());
}

/** Format a product quantity: integer for piece-counted units, 2 decimals otherwise. */
export function fmtProductQty(value: number | null | undefined, unit?: string | null): string {
    if (value == null) return "-";
    const digits = isWholeCountUnit(unit) ? 0 : 2;
    return Number(value).toLocaleString("en-IN", { minimumFractionDigits: digits, maximumFractionDigits: digits });
}
