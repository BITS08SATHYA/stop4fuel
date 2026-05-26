import { type ClassValue, clsx } from "clsx";
import { twMerge } from "tailwind-merge";

export function cn(...inputs: ClassValue[]) {
    return twMerge(clsx(inputs));
}

/**
 * Parse an ISO date string as a local-time Date.
 *
 * Why: `new Date("2026-05-02")` parses as UTC midnight, so `toLocaleDateString`
 * in a browser at/behind UTC renders it as the previous day. Use this for any
 * backend `LocalDate` field. Also accepts full ISO timestamps and uses only
 * the date portion.
 */
export function parseLocalDate(s: string): Date {
    const [y, m, d] = s.slice(0, 10).split("-").map(Number);
    return new Date(y, m - 1, d);
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
