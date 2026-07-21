import type { Employee } from "./types";

/**
 * Shared plumbing for the ID card templates (classic + modern).
 *
 * IMPORTANT: every card element uses explicit inline hex/rgb colors and
 * fixed-px sizing. We deliberately avoid Tailwind classes because html2canvas
 * (v1.4.1) cannot parse Tailwind v4's `oklch()` color functions and would throw
 * while cloning the node. Keeping the subtree self-contained also makes the
 * captured pixels identical regardless of the app's light/dark theme.
 */

export interface IdCardCompany {
    name: string;
    address?: string;
    phone?: string;
    ownerName?: string;
    ownerPhone?: string;
    logoDataUrl?: string | null;
}

export type IdCardTemplate = "classic" | "modern";

export interface IdCardProps {
    side: "front" | "back";
    employee: Employee;
    company: IdCardCompany;
    /** Which visual design to render. */
    template?: IdCardTemplate;
    /** Base64 data URL of the employee photo (null → initials placeholder). */
    photoDataUrl?: string | null;
    /** Base64 data URL of the QR code (back side). */
    qrDataUrl?: string | null;
    /** Card background base color (hex). Secondary tones are derived from it. */
    bgColor?: string;
    /** Primary text color (hex). Muted/label tones are derived from it. */
    fontColor?: string;
    /** Accent color (hex) — pills, rules, swooshes. */
    accentColor?: string;
}

// ── Dimensions (CR80 ~0.63 ratio) ───────────────────────────────────────────
export const CARD_W = 366;
export const CARD_H = 640;

// ── Palette defaults ─────────────────────────────────────────────────────────
export const GOLD = "#f0a93d";
export const WHITE = "#ffffff";
export const DEFAULT_BG = "#ffffff";
export const DEFAULT_FG = "#101317";

// ── Color helpers (keep output as inline hex/rgba — never oklch) ─────────────
export function hexToRgb(hex: string): { r: number; g: number; b: number } {
    const h = hex.replace("#", "");
    const v = h.length === 3 ? h.split("").map((c) => c + c).join("") : h;
    const n = parseInt(v, 16);
    return { r: (n >> 16) & 255, g: (n >> 8) & 255, b: n & 255 };
}

export function toHex(r: number, g: number, b: number): string {
    const c = (x: number) => Math.max(0, Math.min(255, Math.round(x))).toString(16).padStart(2, "0");
    return `#${c(r)}${c(g)}${c(b)}`;
}

/** Shift a color toward black (amt < 0) or white (amt > 0); amt is -100..100. */
export function shade(hex: string, amt: number): string {
    const { r, g, b } = hexToRgb(hex);
    const t = amt < 0 ? 0 : 255;
    const p = Math.abs(amt) / 100;
    return toHex(r + (t - r) * p, g + (t - g) * p, b + (t - b) * p);
}

export function withAlpha(hex: string, a: number): string {
    const { r, g, b } = hexToRgb(hex);
    return `rgba(${r}, ${g}, ${b}, ${a})`;
}

/** True when the color is bright enough that dark text sits on it. */
export function isLight(hex: string): boolean {
    const { r, g, b } = hexToRgb(hex);
    return (0.299 * r + 0.587 * g + 0.114 * b) > 150;
}

/** Black or white — whichever stays readable on top of `hex`. */
export function readableOn(hex: string): string {
    return isLight(hex) ? "#1a1205" : "#ffffff";
}

// ── Formatters ───────────────────────────────────────────────────────────────
export function formatDate(iso?: string): string {
    if (!iso) return "—";
    const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso);
    if (!m) return iso;
    const months = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
    return `${parseInt(m[3], 10)} ${months[parseInt(m[2], 10) - 1]} ${m[1]}`;
}

export function formatPhone(p?: string): string {
    if (!p) return "—";
    const digits = p.replace(/\D/g, "");
    if (digits.length === 10) return `+91 ${digits.slice(0, 5)} ${digits.slice(5)}`;
    if (p.trim().startsWith("+")) return p.trim();
    return p;
}

export function initials(name: string): string {
    return name.split(" ").filter(Boolean).map((w) => w[0]).slice(0, 2).join("").toUpperCase();
}

export function signatureInitials(name: string): string {
    return name.split(" ").filter(Boolean).map((w) => w[0]).slice(0, 2).join(".").toUpperCase();
}

// ── Code 39 barcode ──────────────────────────────────────────────────────────
// Rendered as plain divs (no canvas / no extra dependency) so html2canvas can
// capture it. Each character is 9 elements — bar, space, bar … — that are
// either narrow (n) or wide (w), framed by the `*` start/stop character.
const CODE39: Record<string, string> = {
    "0": "nnnwwnwnn", "1": "wnnwnnnnw", "2": "nnwwnnnnw", "3": "wnwwnnnnn", "4": "nnnwwnnnw",
    "5": "wnnwwnnnn", "6": "nnwwwnnnn", "7": "nnnwnnwnw", "8": "wnnwnnwnn", "9": "nnwwnnwnn",
    A: "wnnnnwnnw", B: "nnwnnwnnw", C: "wnwnnwnnn", D: "nnnnwwnnw", E: "wnnnwwnnn",
    F: "nnwnwwnnn", G: "nnnnnwwnw", H: "wnnnnwwnn", I: "nnwnnwwnn", J: "nnnnwwwnn",
    K: "wnnnnnnww", L: "nnwnnnnww", M: "wnwnnnnwn", N: "nnnnwnnww", O: "wnnnwnnwn",
    P: "nnwnwnnwn", Q: "nnnnnnwww", R: "wnnnnnwwn", S: "nnwnnnwwn", T: "nnnnwnwwn",
    U: "wwnnnnnnw", V: "nwwnnnnnw", W: "wwwnnnnnn", X: "nwnnwnnnw", Y: "wwnnwnnnn",
    Z: "nwwnwnnnn", "-": "nwnnnnwnw", ".": "wwnnnnwnn", " ": "nwwnnnwnn",
    "*": "nwnnwnwnn",
};

/**
 * Encode `text` as Code 39 and return the bar/space widths in order,
 * starting with a bar. Unsupported characters are dropped.
 */
export function code39Elements(text: string): { bar: boolean; wide: boolean }[] {
    const clean = text.toUpperCase().split("").filter((c) => CODE39[c] && c !== "*");
    const chars = ["*", ...clean, "*"];
    const out: { bar: boolean; wide: boolean }[] = [];
    chars.forEach((c, ci) => {
        CODE39[c].split("").forEach((w, i) => out.push({ bar: i % 2 === 0, wide: w === "w" }));
        if (ci < chars.length - 1) out.push({ bar: false, wide: false }); // inter-character gap
    });
    return out;
}
