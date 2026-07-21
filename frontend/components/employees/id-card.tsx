"use client";

import React from "react";
import type { Employee } from "./types";

/**
 * Visual employee ID card (front / back) used for PDF / PNG / JPEG export.
 *
 * IMPORTANT: every element uses explicit inline hex/rgb colors and fixed-px
 * sizing. We deliberately avoid Tailwind classes here because html2canvas
 * (v1.4.1) cannot parse Tailwind v4's `oklch()` color functions and would
 * throw while cloning the node. Keeping the subtree self-contained also makes
 * the captured pixels identical regardless of the app's light/dark theme.
 */

export interface IdCardCompany {
    name: string;
    address?: string;
    phone?: string;
    ownerName?: string;
    ownerPhone?: string;
    logoDataUrl?: string | null;
}

export interface IdCardProps {
    side: "front" | "back";
    employee: Employee;
    company: IdCardCompany;
    /** Base64 data URL of the employee photo (null → silhouette placeholder). */
    photoDataUrl?: string | null;
    /** Base64 data URL of the QR code (back side). */
    qrDataUrl?: string | null;
    /** Card background base color (hex). Secondary tones are derived from it. */
    bgColor?: string;
    /** Primary text color (hex). Muted/label tones are derived from it. */
    fontColor?: string;
}

// ── Dimensions (CR80 ~0.63 ratio) ───────────────────────────────────────────
export const CARD_W = 366;
export const CARD_H = 640;

// ── Palette ──────────────────────────────────────────────────────────────────
// GOLD / RED are fixed accents (readable on both light and dark cards); every
// other tone is derived at render time from the chosen bg / font colors.
const GOLD = "#f0a93d";
const GOLD_SOFT = "#e89b2c";
const WHITE = "#ffffff";
const RED = "#ff5d5d";
const DEFAULT_BG = "#11141a";

// ── Color helpers (keep output as inline hex/rgba — never oklch) ───────────────
function hexToRgb(hex: string): { r: number; g: number; b: number } {
    const h = hex.replace("#", "");
    const v = h.length === 3 ? h.split("").map((c) => c + c).join("") : h;
    const n = parseInt(v, 16);
    return { r: (n >> 16) & 255, g: (n >> 8) & 255, b: n & 255 };
}
function toHex(r: number, g: number, b: number): string {
    const c = (x: number) => Math.max(0, Math.min(255, Math.round(x))).toString(16).padStart(2, "0");
    return `#${c(r)}${c(g)}${c(b)}`;
}
/** Shift a color toward black (amt < 0) or white (amt > 0); amt is -100..100. */
function shade(hex: string, amt: number): string {
    const { r, g, b } = hexToRgb(hex);
    const t = amt < 0 ? 0 : 255;
    const p = Math.abs(amt) / 100;
    return toHex(r + (t - r) * p, g + (t - g) * p, b + (t - b) * p);
}
function withAlpha(hex: string, a: number): string {
    const { r, g, b } = hexToRgb(hex);
    return `rgba(${r}, ${g}, ${b}, ${a})`;
}

// ── Formatters ────────────────────────────────────────────────────────────────
function formatDate(iso?: string): string {
    if (!iso) return "—";
    const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso);
    if (!m) return iso;
    const months = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
    return `${parseInt(m[3], 10)} ${months[parseInt(m[2], 10) - 1]} ${m[1]}`;
}

function formatPhone(p?: string): string {
    if (!p) return "—";
    const digits = p.replace(/\D/g, "");
    if (digits.length === 10) return `+91 ${digits.slice(0, 5)} ${digits.slice(5)}`;
    if (p.trim().startsWith("+")) return p.trim();
    return p;
}

function yearsOfService(joinDate?: string): number {
    if (!joinDate) return 0;
    const start = new Date(joinDate).getTime();
    if (isNaN(start)) return 0;
    const yrs = (Date.now() - start) / (365.25 * 24 * 3600 * 1000);
    return Math.max(0, Math.floor(yrs));
}

function tenureLabel(joinDate?: string): string {
    const y = yearsOfService(joinDate);
    if (y < 1) return "● NEW JOINEE";
    const tier = y >= 10 ? " · PREMIUM" : y >= 5 ? " · SENIOR" : " · MEMBER";
    return `● ${y}+ YEAR${y === 1 ? "" : "S"}${tier}`;
}

function initials(name: string): string {
    return name.split(" ").filter(Boolean).map((w) => w[0]).slice(0, 2).join("").toUpperCase();
}

function signatureInitials(name: string): string {
    return name.split(" ").filter(Boolean).map((w) => w[0]).slice(0, 2).join(".").toUpperCase();
}

// ── Component ─────────────────────────────────────────────────────────────────
export const IdCard = React.forwardRef<HTMLDivElement, IdCardProps>(function IdCard(
    { side, employee, company, photoDataUrl, qrDataUrl, bgColor, fontColor }, ref,
) {
    const bg = bgColor || DEFAULT_BG;
    const fg = fontColor || WHITE;
    // Derived tones so any bg/font combo stays legible.
    const panelBg = `linear-gradient(160deg, ${shade(bg, 12)} 0%, ${bg} 55%, ${shade(bg, -45)} 100%)`;
    const headerBg = shade(bg, -55);
    const notch = shade(bg, 28);
    const muted = withAlpha(fg, 0.56);
    const label = withAlpha(fg, 0.5);
    const soft = withAlpha(fg, 0.82);
    const faint = withAlpha(fg, 0.4);
    const divider = withAlpha(fg, 0.14);

    const shell: React.CSSProperties = {
        width: CARD_W,
        height: CARD_H,
        borderRadius: 26,
        background: panelBg,
        position: "relative",
        overflow: "hidden",
        fontFamily: "'Helvetica Neue', Arial, sans-serif",
        color: fg,
        boxSizing: "border-box",
    };

    if (side === "front") {
        return (
            <div ref={ref} style={shell}>
                {/* lanyard notch */}
                <div style={{ position: "absolute", top: 16, left: "50%", transform: "translateX(-50%)", width: 54, height: 9, borderRadius: 6, background: notch }} />

                <div style={{ padding: "40px 28px 26px" }}>
                    {/* Header */}
                    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: 10 }}>
                        <div style={{ flex: 1, minWidth: 0, overflowWrap: "break-word" }}>
                            <div style={{ fontSize: 19, fontWeight: 700, lineHeight: 1.12, color: fg }}>
                                {(company.name || "Company").split(" ").slice(0, 2).join(" ")}
                            </div>
                            <div style={{ fontSize: 19, fontWeight: 700, lineHeight: 1.12, color: fg }}>
                                {(company.name || "").split(" ").slice(2).join(" ")}
                            </div>
                            <div style={{ fontSize: 9.5, letterSpacing: 2.5, color: muted, marginTop: 6 }}>
                                EMPLOYEE IDENTITY CARD
                            </div>
                        </div>
                        <LogoBlock company={company} muted={muted} />
                    </div>
                    <div style={{ height: 2, background: `linear-gradient(90deg, ${GOLD}, rgba(240,169,61,0))`, marginTop: 12 }} />

                    {/* Photo */}
                    <div style={{ display: "flex", justifyContent: "center", marginTop: 36 }}>
                        <div style={{ width: 160, height: 160, borderRadius: "50%", padding: 4, background: `linear-gradient(145deg, ${GOLD}, ${GOLD_SOFT})`, boxShadow: "0 8px 24px rgba(0,0,0,0.45)" }}>
                            <div style={{ width: "100%", height: "100%", borderRadius: "50%", overflow: "hidden", background: "#222732", display: "flex", alignItems: "center", justifyContent: "center" }}>
                                {photoDataUrl ? (
                                    // eslint-disable-next-line @next/next/no-img-element
                                    <img src={photoDataUrl} alt={employee.name} style={{ width: "100%", height: "100%", objectFit: "cover" }} />
                                ) : (
                                    <span style={{ fontSize: 52, fontWeight: 700, color: "#9aa3b2" }}>{initials(employee.name || "?")}</span>
                                )}
                            </div>
                        </div>
                    </div>
                    <div style={{ textAlign: "center", fontSize: 9, letterSpacing: 2.5, color: muted, marginTop: 8 }}>PHOTO</div>

                    {/* Name + role */}
                    <div style={{ textAlign: "center", fontSize: 27, fontWeight: 800, marginTop: 14, color: fg }}>{employee.name}</div>
                    {/* Explicit height + matching lineHeight (no vertical padding): html2canvas
                        drops padded inline-block text to the bottom edge of the pill otherwise. */}
                    <div style={{ display: "flex", justifyContent: "center", marginTop: 10 }}>
                        <span style={{ display: "block", background: `linear-gradient(145deg, ${GOLD}, ${GOLD_SOFT})`, color: "#1a1205", fontWeight: 800, fontSize: 13, letterSpacing: 1, height: 32, lineHeight: "32px", padding: "0 26px", borderRadius: 20, whiteSpace: "nowrap" }}>
                            {(employee.designation || "STAFF").toUpperCase()}
                        </span>
                    </div>
                    <div style={{ display: "flex", justifyContent: "center", marginTop: 8 }}>
                        <span style={{ display: "block", border: `1px solid ${GOLD}`, color: GOLD, fontWeight: 700, fontSize: 10.5, letterSpacing: 1, height: 24, lineHeight: "24px", padding: "0 16px", borderRadius: 14, whiteSpace: "nowrap" }}>
                            {tenureLabel(employee.joinDate)}
                        </span>
                    </div>

                    {/* Detail grid */}
                    <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", rowGap: 20, columnGap: 10, marginTop: 34 }}>
                        <Field label="EMPLOYEE ID" value={employee.employeeCode || "—"} fg={fg} labelColor={label} />
                        <Field label="PHONE" value={formatPhone(employee.phone)} fg={fg} labelColor={label} />
                        <Field label="DATE OF JOINING" value={formatDate(employee.joinDate)} fg={fg} labelColor={label} />
                        <Field label="BLOOD GROUP" value={employee.bloodGroup || "—"} valueColor={RED} fg={fg} labelColor={label} />
                    </div>
                </div>
            </div>
        );
    }

    // ── Back ──
    return (
        <div ref={ref} style={shell}>
            <div style={{ height: 64, background: headerBg }} />
            <div style={{ padding: "0 28px 18px" }}>
                {/* QR */}
                <div style={{ display: "flex", justifyContent: "center", marginTop: -10 }}>
                    <div style={{ width: 132, height: 132, background: WHITE, borderRadius: 10, padding: 8, boxSizing: "border-box" }}>
                        {qrDataUrl ? (
                            // eslint-disable-next-line @next/next/no-img-element
                            <img src={qrDataUrl} alt="QR" style={{ width: "100%", height: "100%" }} />
                        ) : null}
                    </div>
                </div>
                <div style={{ textAlign: "center", fontSize: 10, letterSpacing: 1.5, color: muted, marginTop: 8 }}>Scan to save contact</div>

                <div style={{ height: 1, background: divider, margin: "16px 0" }} />

                <SectionLabel>EMPLOYEE ADDRESS</SectionLabel>
                <div style={{ fontSize: 12.5, color: soft, lineHeight: 1.4, marginTop: 6 }}>
                    {[employee.address, [employee.city, employee.state].filter(Boolean).join(", "), employee.pincode ? `- ${employee.pincode}` : ""].filter(Boolean).join(" ") || "—"}
                </div>

                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", columnGap: 14, marginTop: 16 }}>
                    <div>
                        <SectionLabel>EMERGENCY CONTACT</SectionLabel>
                        <div style={{ fontSize: 8.5, letterSpacing: 1.5, color: label, marginTop: 10 }}>NAME</div>
                        <div style={{ fontSize: 13, fontWeight: 700, color: fg, marginTop: 2 }}>{employee.emergencyContact || "—"}</div>
                        <div style={{ fontSize: 8.5, letterSpacing: 1.5, color: label, marginTop: 8 }}>PHONE</div>
                        <div style={{ fontSize: 13, fontWeight: 700, color: fg, marginTop: 2 }}>{formatPhone(employee.emergencyPhone)}</div>
                    </div>
                    <div>
                        <SectionLabel>OUTLET OWNER</SectionLabel>
                        <div style={{ fontSize: 8.5, letterSpacing: 1.5, color: label, marginTop: 10 }}>NAME</div>
                        <div style={{ fontSize: 13, fontWeight: 700, color: fg, marginTop: 2 }}>{company.ownerName || "—"}</div>
                        <div style={{ fontSize: 8.5, letterSpacing: 1.5, color: label, marginTop: 8 }}>PHONE</div>
                        <div style={{ fontSize: 13, fontWeight: 700, color: fg, marginTop: 2 }}>{formatPhone(company.ownerPhone)}</div>
                    </div>
                </div>

                {/* Signature */}
                <div style={{ background: "#eef1f5", borderRadius: 8, height: 56, marginTop: 16, position: "relative", display: "flex", alignItems: "center" }}>
                    <span style={{ fontSize: 8.5, letterSpacing: 1.5, color: "#9aa1ac", position: "absolute", bottom: 8, left: 14 }}>AUTHORISED SIGNATURE</span>
                    <span style={{ position: "absolute", top: 8, right: 18, fontFamily: "'Segoe Script', 'Brush Script MT', cursive", fontStyle: "italic", fontSize: 26, color: "#1c2330", fontWeight: 700 }}>
                        {signatureInitials(employee.name || "")}
                    </span>
                </div>

                {/* Footer */}
                <div style={{ textAlign: "center", marginTop: 14 }}>
                    <div style={{ fontSize: 10.5, color: muted }}>Property of {company.name}</div>
                    {company.address ? <div style={{ fontSize: 9.5, color: faint, marginTop: 3 }}>{company.address}</div> : null}
                    <div style={{ fontSize: 9.5, color: faint, marginTop: 3 }}>If found, please return to the above address.</div>
                </div>
                <div style={{ display: "flex", alignItems: "center", justifyContent: "center", gap: 6, marginTop: 12 }}>
                    <span style={{ width: 14, height: 14, borderRadius: "50%", background: GOLD, display: "inline-block" }} />
                    <span style={{ fontSize: 11, color: muted }}>Powered by <b style={{ color: soft }}>StopForFuel</b></span>
                </div>
            </div>
        </div>
    );
});

// ── Sub-components ────────────────────────────────────────────────────────────
function Field({ label, value, valueColor, fg, labelColor }: { label: string; value: string; valueColor?: string; fg: string; labelColor: string }) {
    return (
        <div>
            <div style={{ fontSize: 9, letterSpacing: 1.5, color: labelColor }}>{label}</div>
            <div style={{ fontSize: 16, fontWeight: 700, color: valueColor || fg, marginTop: 3 }}>
                {value}
            </div>
        </div>
    );
}

function SectionLabel({ children }: { children: React.ReactNode }) {
    return <div style={{ fontSize: 11, fontWeight: 700, letterSpacing: 1.5, color: GOLD }}>{children}</div>;
}

function LogoBlock({ company, muted }: { company: IdCardCompany; muted: string }) {
    if (company.logoDataUrl) {
        return (
            <div style={{ width: 92, height: 66, borderRadius: 8, background: "#ffffff", display: "flex", alignItems: "center", justifyContent: "center", padding: "5px 7px", boxSizing: "border-box", boxShadow: "0 2px 8px rgba(0,0,0,0.35)" }}>
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img src={company.logoDataUrl} alt="logo" style={{ maxWidth: "100%", maxHeight: "100%", objectFit: "contain" }} />
            </div>
        );
    }
    return (
        <div style={{ width: 76, textAlign: "right", fontSize: 9, fontWeight: 700, letterSpacing: 0.5, color: muted, lineHeight: 1.25 }}>
            {(company.name || "STOPFORFUEL").toUpperCase()}
        </div>
    );
}
