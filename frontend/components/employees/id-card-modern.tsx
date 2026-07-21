"use client";

import React from "react";
import {
    CARD_W, CARD_H, DEFAULT_BG, DEFAULT_FG, GOLD, WHITE,
    code39Elements, fitFont, formatDate, formatPhone, initials, readableOn, shade, signatureInitials, withAlpha,
    type IdCardProps,
} from "./id-card-shared";

/**
 * "Modern" ID card template — light card, accent-coloured swooshes, photo over
 * the header curve, labelled detail rows, Code 39 barcode on the front and
 * terms + QR on the back.
 *
 * Same inline-style rules as the classic template (see id-card-shared.ts).
 */
export const IdCardModern = React.forwardRef<HTMLDivElement, IdCardProps>(function IdCardModern(
    { side, employee, company, photoDataUrl, qrDataUrl, bgColor, fontColor, accentColor }, ref,
) {
    const bg = bgColor || DEFAULT_BG;
    const fg = fontColor || DEFAULT_FG;
    const accent = accentColor || GOLD;
    const accentDeep = shade(accent, -18);
    const accentSoft = shade(accent, 55);
    const onAccent = readableOn(accent);
    const label = withAlpha(fg, 0.55);
    const muted = withAlpha(fg, 0.66);
    const divider = withAlpha(fg, 0.12);
    const name = (employee.name || "").toUpperCase();
    const designation = (employee.designation || "STAFF").toUpperCase();

    const shell: React.CSSProperties = {
        width: CARD_W,
        height: CARD_H,
        borderRadius: 22,
        background: bg,
        position: "relative",
        overflow: "hidden",
        fontFamily: "'Helvetica Neue', Arial, sans-serif",
        color: fg,
        boxSizing: "border-box",
        border: `1px solid ${divider}`,
    };

    if (side === "front") {
        return (
            <div ref={ref} style={shell}>
                {/* Header + footer swooshes.
                    They MUST live in one single SVG covering the whole card:
                    html2canvas (v1.4.1) only rasterises the first inline SVG of
                    a captured subtree and silently drops the rest. */}
                <svg width={CARD_W} height={CARD_H} viewBox={`0 0 ${CARD_W} ${CARD_H}`} style={{ position: "absolute", top: 0, left: 0 }}>
                    <path d={`M0 0 H${CARD_W} V150 C${CARD_W * 0.66} 250 ${CARD_W * 0.34} 130 0 210 Z`} fill={accent} />
                    <path d={`M0 0 H${CARD_W} V96 C${CARD_W * 0.6} 190 ${CARD_W * 0.4} 74 0 148 Z`} fill={accentDeep} opacity={0.55} />
                    <path d={`M0 ${CARD_H - 48} C${CARD_W * 0.3} ${CARD_H - 96} ${CARD_W * 0.7} ${CARD_H - 28} ${CARD_W} ${CARD_H - 74} V${CARD_H} H0 Z`} fill={accent} />
                    <path d={`M0 ${CARD_H - 24} C${CARD_W * 0.34} ${CARD_H - 64} ${CARD_W * 0.7} ${CARD_H - 2} ${CARD_W} ${CARD_H - 42} V${CARD_H} H0 Z`} fill={accentDeep} opacity={0.5} />
                </svg>

                {/* Company logo */}
                <div style={{ position: "absolute", top: 18, right: 20 }}>
                    {company.logoDataUrl ? (
                        <div style={{ width: 84, height: 60, borderRadius: 8, background: WHITE, display: "flex", alignItems: "center", justifyContent: "center", padding: "5px 7px", boxSizing: "border-box", boxShadow: "0 2px 8px rgba(0,0,0,0.18)" }}>
                            {/* eslint-disable-next-line @next/next/no-img-element */}
                            <img src={company.logoDataUrl} alt="logo" style={{ maxWidth: "100%", maxHeight: "100%", objectFit: "contain" }} />
                        </div>
                    ) : null}
                </div>

                {/* Company name over the swoosh */}
                <div style={{ position: "absolute", top: 22, left: 22, width: 200 }}>
                    <div style={{ fontSize: 15, fontWeight: 800, lineHeight: 1.15, color: onAccent }}>{company.name || "Company"}</div>
                    <div style={{ fontSize: 8, fontWeight: 700, letterSpacing: 2, color: withAlpha(onAccent, 0.75), marginTop: 4 }}>
                        EMPLOYEE IDENTITY CARD
                    </div>
                </div>

                {/* Photo */}
                <div style={{ position: "absolute", top: 118, left: "50%", transform: "translateX(-50%)" }}>
                    <div style={{ width: 148, height: 148, borderRadius: 18, background: WHITE, padding: 5, boxSizing: "border-box", boxShadow: "0 6px 18px rgba(0,0,0,0.22)" }}>
                        <div style={{ width: "100%", height: "100%", borderRadius: 14, overflow: "hidden", background: "#e7eaef", display: "flex", alignItems: "center", justifyContent: "center" }}>
                            {photoDataUrl ? (
                                // eslint-disable-next-line @next/next/no-img-element
                                <img src={photoDataUrl} alt={employee.name} style={{ width: "100%", height: "100%", objectFit: "cover" }} />
                            ) : (
                                <span style={{ fontSize: 46, fontWeight: 800, color: "#9aa3b2" }}>{initials(employee.name || "?")}</span>
                            )}
                        </div>
                    </div>
                </div>

                {/* Name + designation */}
                {/* Name + designation sit in a fixed-height block and the type
                    auto-fits, so a long name can never push into the rows. */}
                <div style={{ position: "absolute", top: 280, left: 20, width: CARD_W - 40, textAlign: "center" }}>
                    <div style={{ fontSize: fitFont(name, CARD_W - 56, 26, 13, 0.74), fontWeight: 800, letterSpacing: 0.5, color: fg, height: 34, lineHeight: "34px", whiteSpace: "nowrap" }}>
                        {name}
                    </div>
                    <div style={{ fontSize: fitFont(designation, CARD_W - 64, 12.5, 8, 0.95), fontWeight: 700, letterSpacing: 2, color: accentDeep, height: 20, lineHeight: "20px", marginTop: 3, whiteSpace: "nowrap" }}>
                        {designation}
                    </div>
                </div>

                {/* Detail rows */}
                <div style={{ position: "absolute", top: 342, left: 26, right: 26 }}>
                    <Row label="ID NO" value={employee.employeeCode || "—"} fg={fg} labelColor={label} divider={divider} accent={accent} />
                    <Row label="DOB" value={formatDate(employee.dateOfBirth)} fg={fg} labelColor={label} divider={divider} accent={accent} />
                    <Row label="PHONE" value={formatPhone(employee.phone)} fg={fg} labelColor={label} divider={divider} accent={accent} />
                    <Row label="BLOOD" value={employee.bloodGroup || "—"} fg={fg} labelColor={label} divider={divider} accent={accent} big />
                </div>

                {/* Barcode */}
                {/* Barcode always sits on a white plate with dark bars — a light
                    barcode on a dark card is unscannable. */}
                <div style={{ position: "absolute", bottom: 84, left: 0, width: CARD_W, textAlign: "center" }}>
                    <div style={{ width: 300, height: 52, margin: "0 auto", background: WHITE, borderRadius: 6, boxSizing: "border-box", paddingTop: 5 }}>
                        <Barcode text={employee.employeeCode || employee.name || "EMPLOYEE"} color="#111111" />
                    </div>
                    <div style={{ fontSize: 9, fontWeight: 700, letterSpacing: 3, color: muted, marginTop: 5 }}>
                        {(employee.employeeCode || "").toUpperCase()}
                    </div>
                </div>

                <div style={{ position: "absolute", bottom: 16, left: 0, width: CARD_W, textAlign: "center", fontSize: 10, fontWeight: 700, letterSpacing: 1.2, color: onAccent }}>
                    {company.phone ? formatPhone(company.phone) : company.name}
                </div>
            </div>
        );
    }

    // ── Back ──
    const terms = [
        "This card is company property and is non-transferable.",
        "Wear it visibly at all times while on duty.",
        "Report loss or damage to the management immediately.",
        "Surrender the card on your last working day.",
    ];

    return (
        <div ref={ref} style={shell}>
            {/* Header + footer swooshes in one SVG (see front side). */}
            <svg width={CARD_W} height={CARD_H} viewBox={`0 0 ${CARD_W} ${CARD_H}`} style={{ position: "absolute", top: 0, left: 0 }}>
                <path d={`M0 0 H${CARD_W} V56 C${CARD_W * 0.66} 118 ${CARD_W * 0.3} 30 0 92 Z`} fill={accent} />
                <path d={`M0 0 H${CARD_W} V28 C${CARD_W * 0.6} 92 ${CARD_W * 0.36} 8 0 62 Z`} fill={accentDeep} opacity={0.5} />
                <path d={`M0 ${CARD_H - 56} C${CARD_W * 0.3} ${CARD_H - 104} ${CARD_W * 0.7} ${CARD_H - 34} ${CARD_W} ${CARD_H - 82} V${CARD_H} H0 Z`} fill={accent} />
                <path d={`M0 ${CARD_H - 30} C${CARD_W * 0.34} ${CARD_H - 72} ${CARD_W * 0.7} ${CARD_H - 8} ${CARD_W} ${CARD_H - 48} V${CARD_H} H0 Z`} fill={accentDeep} opacity={0.5} />
            </svg>
            <div style={{ position: "absolute", top: 20, left: 0, width: CARD_W, textAlign: "center", fontSize: 14, fontWeight: 800, letterSpacing: 1.5, color: onAccent }}>
                TERMS AND CONDITIONS
            </div>

            <div style={{ position: "absolute", top: 118, left: 26, right: 26 }}>
                {terms.map((t) => (
                    <div key={t} style={{ display: "flex", gap: 8, marginBottom: 7 }}>
                        <span style={{ width: 7, height: 7, borderRadius: "50%", background: accent, marginTop: 5, flexShrink: 0 }} />
                        <span style={{ fontSize: 10.5, fontWeight: 600, lineHeight: 1.45, color: muted }}>{t}</span>
                    </div>
                ))}

                <div style={{ marginTop: 14 }}>
                    <div style={{ fontSize: 8.5, fontWeight: 700, letterSpacing: 1.5, color: label }}>EMPLOYEE ADDRESS</div>
                    {/* Fixed 2-line box: a long address must not push the QR
                        block into the footer swoosh. */}
                    <div style={{ fontSize: 11.5, fontWeight: 600, color: muted, lineHeight: "16px", height: 32, overflow: "hidden", marginTop: 3 }}>
                        {[employee.address, [employee.city, employee.state].filter(Boolean).join(", "), employee.pincode ? `- ${employee.pincode}` : ""].filter(Boolean).join(" ") || "—"}
                    </div>
                </div>

                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", columnGap: 12, marginTop: 14 }}>
                    <div>
                        <div style={{ fontSize: 8.5, fontWeight: 700, letterSpacing: 1.5, color: label }}>EMERGENCY CONTACT</div>
                        <div style={{ fontSize: 12.5, fontWeight: 800, color: fg, marginTop: 3 }}>{employee.emergencyContact || "—"}</div>
                        <div style={{ fontSize: 12.5, fontWeight: 800, color: fg, marginTop: 2 }}>{formatPhone(employee.emergencyPhone)}</div>
                    </div>
                    <div>
                        <div style={{ fontSize: 8.5, fontWeight: 700, letterSpacing: 1.5, color: label }}>OUTLET OWNER</div>
                        <div style={{ fontSize: 12.5, fontWeight: 800, color: fg, marginTop: 3 }}>{company.ownerName || "—"}</div>
                        <div style={{ fontSize: 12.5, fontWeight: 800, color: fg, marginTop: 2 }}>{formatPhone(company.ownerPhone)}</div>
                    </div>
                </div>

                {/* Signature */}
                <div style={{ marginTop: 12, textAlign: "right" }}>
                    <div style={{ fontFamily: "'Segoe Script', 'Brush Script MT', cursive", fontStyle: "italic", fontSize: 24, fontWeight: 700, color: fg, height: 30, lineHeight: "30px" }}>
                        {signatureInitials(employee.name || "")}
                    </div>
                    <div style={{ height: 1, background: withAlpha(fg, 0.35), marginTop: 2 }} />
                    <div style={{ fontSize: 8.5, fontWeight: 700, letterSpacing: 1.5, color: label, marginTop: 4 }}>AUTHORISED SIGNATURE</div>
                </div>

                {/* QR */}
                <div style={{ display: "flex", justifyContent: "center", marginTop: 10 }}>
                    <div style={{ width: 96, height: 96, background: WHITE, border: `1px solid ${divider}`, borderRadius: 10, padding: 6, boxSizing: "border-box" }}>
                        {qrDataUrl ? (
                            // eslint-disable-next-line @next/next/no-img-element
                            <img src={qrDataUrl} alt="QR" style={{ width: "100%", height: "100%" }} />
                        ) : null}
                    </div>
                </div>
                <div style={{ textAlign: "center", fontSize: 9, fontWeight: 700, letterSpacing: 1.5, color: label, marginTop: 5 }}>
                    SCAN TO SAVE CONTACT
                </div>
            </div>

            <div style={{ position: "absolute", bottom: 14, left: 20, right: 20, textAlign: "center" }}>
                <div style={{ fontSize: 10, fontWeight: 800, color: onAccent }}>Property of {company.name}</div>
                {company.address ? (
                    <div style={{ fontSize: 8.5, fontWeight: 600, color: withAlpha(onAccent, 0.85), marginTop: 2, lineHeight: 1.3 }}>{company.address}</div>
                ) : null}
            </div>

            {/* Accent hairline so the card edge reads on white paper */}
            <div style={{ position: "absolute", top: 0, left: 0, width: 4, height: CARD_H, background: accentSoft }} />
        </div>
    );
});

// ── Sub-components ───────────────────────────────────────────────────────────
function Row({ label, value, fg, labelColor, divider, accent, big }: {
    label: string; value: string; fg: string; labelColor: string; divider: string; accent: string; big?: boolean;
}) {
    return (
        <div style={{ display: "flex", alignItems: "center", gap: 10, borderBottom: `1px solid ${divider}`, height: big ? 38 : 34, boxSizing: "border-box" }}>
            <span style={{ width: 6, height: 6, borderRadius: "50%", background: accent, flexShrink: 0 }} />
            <span style={{ width: 58, fontSize: 9.5, fontWeight: 700, letterSpacing: 1.2, color: labelColor, flexShrink: 0 }}>{label}</span>
            {/* Explicit height + matching lineHeight: html2canvas mis-positions
                text that relies on flex cross-axis centring. */}
            <span style={{ flex: 1, textAlign: "right", fontSize: big ? 19 : 14, fontWeight: 800, color: fg, height: big ? 38 : 34, lineHeight: big ? "38px" : "34px", whiteSpace: "nowrap", overflow: "hidden" }}>
                {value}
            </span>
        </div>
    );
}

function Barcode({ text, color }: { text: string; color: string }) {
    const els = code39Elements(text);
    const NARROW = 1.4;
    const WIDE = 3.4;
    return (
        <div style={{ display: "flex", justifyContent: "center", alignItems: "flex-end", height: 42 }}>
            {els.map((e, i) => (
                <span
                    key={i}
                    style={{
                        display: "inline-block",
                        width: e.wide ? WIDE : NARROW,
                        height: 42,
                        background: e.bar ? color : "transparent",
                    }}
                />
            ))}
        </div>
    );
}
