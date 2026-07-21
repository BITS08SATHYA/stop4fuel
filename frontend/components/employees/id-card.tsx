"use client";

import React from "react";
import { IdCardModern } from "./id-card-modern";
import {
    CARD_H, CARD_W, DEFAULT_BG, DEFAULT_FG, GOLD, WHITE,
    fitFont, formatDate, formatPhone, initials, isLight, readableOn, shade, signatureInitials, withAlpha,
    type IdCardCompany, type IdCardProps,
} from "./id-card-shared";

export { CARD_W, CARD_H };
export type { IdCardCompany, IdCardProps, IdCardTemplate } from "./id-card-shared";

/**
 * Employee ID card used for the preview and the PDF / PNG / JPEG export.
 * `IdCard` picks the template; the classic design lives here, the modern one in
 * ./id-card-modern. Styling rules for both are documented in ./id-card-shared.
 */

// ── Template dispatcher ───────────────────────────────────────────────────────
export const IdCard = React.forwardRef<HTMLDivElement, IdCardProps>(function IdCard(props, ref) {
    return props.template === "modern"
        ? <IdCardModern ref={ref} {...props} />
        : <IdCardClassic ref={ref} {...props} />;
});

// ── Component ─────────────────────────────────────────────────────────────────
const IdCardClassic = React.forwardRef<HTMLDivElement, IdCardProps>(function IdCardClassic(
    { side, employee, company, photoDataUrl, qrDataUrl, bgColor, fontColor, accentColor }, ref,
) {
    const bg = bgColor || DEFAULT_BG;
    const fg = fontColor || DEFAULT_FG;
    const accent = accentColor || GOLD;
    const accentSoft = shade(accent, -8);
    const onAccent = readableOn(accent);
    // Derived tones so any bg/font combo stays legible. Light cards get a much
    // gentler gradient — the dark-card shading would turn a white card grey.
    const lightCard = isLight(bg);
    const panelBg = lightCard
        ? `linear-gradient(160deg, ${shade(bg, 6)} 0%, ${bg} 55%, ${shade(bg, -6)} 100%)`
        : `linear-gradient(160deg, ${shade(bg, 12)} 0%, ${bg} 55%, ${shade(bg, -45)} 100%)`;
    const headerBg = shade(bg, lightCard ? -10 : -55);
    const notch = shade(bg, lightCard ? -18 : 28);
    const muted = withAlpha(fg, 0.62);
    const label = withAlpha(fg, 0.58);
    const soft = withAlpha(fg, 0.86);
    const faint = withAlpha(fg, 0.5);
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
        // Light cards need an outline or the edges vanish against white paper/UI.
        border: lightCard ? `1px solid ${withAlpha(fg, 0.16)}` : "none",
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
                            <div style={{ fontSize: 9.5, fontWeight: 700, letterSpacing: 2.5, color: muted, marginTop: 6 }}>
                                EMPLOYEE IDENTITY CARD
                            </div>
                        </div>
                        <LogoBlock company={company} muted={muted} />
                    </div>
                    <div style={{ height: 2, background: `linear-gradient(90deg, ${accent}, ${withAlpha(accent, 0)})`, marginTop: 12 }} />

                    {/* Photo */}
                    <div style={{ display: "flex", justifyContent: "center", marginTop: 32 }}>
                        <div style={{ width: 172, height: 172, borderRadius: "50%", padding: 4, background: `linear-gradient(145deg, ${accent}, ${accentSoft})`, boxShadow: "0 8px 24px rgba(0,0,0,0.28)" }}>
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
                    <div style={{ textAlign: "center", fontSize: 9, fontWeight: 700, letterSpacing: 2.5, color: muted, marginTop: 8 }}>PHOTO</div>

                    {/* Name + role */}
                    {/* Auto-fitted so a long name stays on one line and never
                        reflows the pill / detail grid below it. */}
                    <div style={{ textAlign: "center", fontSize: fitFont(employee.name || "", CARD_W - 60, 27, 14, 0.62), fontWeight: 800, marginTop: 14, color: fg, height: 34, lineHeight: "34px", whiteSpace: "nowrap" }}>
                        {employee.name}
                    </div>
                    {/* The pill text is centred with an explicit height + a nested block whose
                        lineHeight matches it — html2canvas ignores flex centring and drops padded
                        inline text to the bottom edge of the pill. */}
                    <div style={{ display: "flex", justifyContent: "center", marginTop: 12 }}>
                        <span style={{ display: "block", background: `linear-gradient(145deg, ${accent}, ${accentSoft})`, height: 34, borderRadius: 20, padding: "0 26px", whiteSpace: "nowrap" }}>
                            <span style={{ display: "block", height: 34, lineHeight: "34px", fontSize: fitFont((employee.designation || "STAFF").toUpperCase(), 246, 13, 9, 0.8), fontWeight: 800, letterSpacing: 1, color: onAccent, textAlign: "center" }}>
                                {(employee.designation || "STAFF").toUpperCase()}
                            </span>
                        </span>
                    </div>

                    {/* Detail grid */}
                    <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", rowGap: 18, columnGap: 10, marginTop: 26 }}>
                        <Field label="EMPLOYEE ID" value={employee.employeeCode || "—"} fg={fg} labelColor={label} />
                        <Field label="PHONE" value={formatPhone(employee.phone)} fg={fg} labelColor={label} />
                        <Field label="DATE OF BIRTH" value={formatDate(employee.dateOfBirth)} fg={fg} labelColor={label} />
                        <Field label="BLOOD GROUP" value={employee.bloodGroup || "—"} fg={fg} labelColor={label} big />
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
                    <div style={{ width: 132, height: 132, background: WHITE, border: `1px solid ${divider}`, borderRadius: 10, padding: 8, boxSizing: "border-box" }}>
                        {qrDataUrl ? (
                            // eslint-disable-next-line @next/next/no-img-element
                            <img src={qrDataUrl} alt="QR" style={{ width: "100%", height: "100%" }} />
                        ) : null}
                    </div>
                </div>
                <div style={{ textAlign: "center", fontSize: 10, fontWeight: 700, letterSpacing: 1.5, color: muted, marginTop: 8 }}>Scan to save contact</div>

                <div style={{ height: 1, background: divider, margin: "16px 0" }} />

                <SectionLabel accent={accent}>EMPLOYEE ADDRESS</SectionLabel>
                <div style={{ fontSize: 12.5, fontWeight: 600, color: soft, lineHeight: 1.4, marginTop: 6 }}>
                    {[employee.address, [employee.city, employee.state].filter(Boolean).join(", "), employee.pincode ? `- ${employee.pincode}` : ""].filter(Boolean).join(" ") || "—"}
                </div>

                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", columnGap: 14, marginTop: 16 }}>
                    <div>
                        <SectionLabel accent={accent}>EMERGENCY CONTACT</SectionLabel>
                        <div style={{ fontSize: 8.5, fontWeight: 700, letterSpacing: 1.5, color: label, marginTop: 10 }}>NAME</div>
                        <div style={{ fontSize: 13, fontWeight: 800, color: fg, marginTop: 2 }}>{employee.emergencyContact || "—"}</div>
                        <div style={{ fontSize: 8.5, fontWeight: 700, letterSpacing: 1.5, color: label, marginTop: 8 }}>PHONE</div>
                        <div style={{ fontSize: 13, fontWeight: 800, color: fg, marginTop: 2 }}>{formatPhone(employee.emergencyPhone)}</div>
                    </div>
                    <div>
                        <SectionLabel accent={accent}>OUTLET OWNER</SectionLabel>
                        <div style={{ fontSize: 8.5, fontWeight: 700, letterSpacing: 1.5, color: label, marginTop: 10 }}>NAME</div>
                        <div style={{ fontSize: 13, fontWeight: 800, color: fg, marginTop: 2 }}>{company.ownerName || "—"}</div>
                        <div style={{ fontSize: 8.5, fontWeight: 700, letterSpacing: 1.5, color: label, marginTop: 8 }}>PHONE</div>
                        <div style={{ fontSize: 13, fontWeight: 800, color: fg, marginTop: 2 }}>{formatPhone(company.ownerPhone)}</div>
                    </div>
                </div>

                {/* Signature */}
                <div style={{ background: "#eef1f5", border: `1px solid ${divider}`, boxSizing: "border-box", borderRadius: 8, height: 56, marginTop: 16, position: "relative", display: "flex", alignItems: "center" }}>
                    <span style={{ fontSize: 8.5, letterSpacing: 1.5, color: "#9aa1ac", position: "absolute", bottom: 8, left: 14 }}>AUTHORISED SIGNATURE</span>
                    <span style={{ position: "absolute", top: 8, right: 18, fontFamily: "'Segoe Script', 'Brush Script MT', cursive", fontStyle: "italic", fontSize: 26, color: "#1c2330", fontWeight: 700 }}>
                        {signatureInitials(employee.name || "")}
                    </span>
                </div>

                {/* Footer */}
                <div style={{ textAlign: "center", marginTop: 14 }}>
                    <div style={{ fontSize: 10.5, fontWeight: 600, color: muted }}>Property of {company.name}</div>
                    {company.address ? <div style={{ fontSize: 9.5, fontWeight: 600, color: faint, marginTop: 3 }}>{company.address}</div> : null}
                    <div style={{ fontSize: 9.5, fontWeight: 600, color: faint, marginTop: 3 }}>If found, please return to the above address.</div>
                </div>
                <div style={{ display: "flex", alignItems: "center", justifyContent: "center", gap: 6, marginTop: 12 }}>
                    <span style={{ width: 14, height: 14, borderRadius: "50%", background: accent, display: "inline-block" }} />
                    <span style={{ fontSize: 11, color: muted }}>Powered by <b style={{ color: soft }}>StopForFuel</b></span>
                </div>
            </div>
        </div>
    );
});

// ── Sub-components ────────────────────────────────────────────────────────────
function Field({ label, value, valueColor, fg, labelColor, big }: { label: string; value: string; valueColor?: string; fg: string; labelColor: string; big?: boolean }) {
    return (
        <div>
            <div style={{ fontSize: 9, fontWeight: 700, letterSpacing: 1.5, color: labelColor }}>{label}</div>
            <div style={{ fontSize: big ? 22 : 16, fontWeight: 800, color: valueColor || fg, marginTop: big ? 1 : 3, lineHeight: 1.25 }}>
                {value}
            </div>
        </div>
    );
}

function SectionLabel({ children, accent }: { children: React.ReactNode; accent: string }) {
    return <div style={{ fontSize: 11, fontWeight: 700, letterSpacing: 1.5, color: accent }}>{children}</div>;
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
