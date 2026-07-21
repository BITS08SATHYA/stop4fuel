"use client";

import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { Download, Loader2, ChevronDown, FileText, ImageIcon } from "lucide-react";
import { Modal } from "@/components/ui/modal";
import { getEmployeeFileUrl, API_BASE_URL } from "@/lib/api/station";
import { fetchWithAuth } from "@/lib/api/fetch-with-auth";
import { IdCard, CARD_W, CARD_H, type IdCardCompany } from "./id-card";
import {
    exportIdCard, imageUrlToDataUrl, buildVCard, generateQrDataUrl, type IdCardFormat,
} from "@/lib/id-card-export";
import type { Employee } from "./types";

interface IdCardModalProps {
    employee: Employee;
    onClose: () => void;
}

const PREVIEW_SCALE = 0.62;
const GAP = 28;

// Quick-pick background / text combinations.
const THEMES: { name: string; bg: string; fg: string }[] = [
    { name: "White", bg: "#ffffff", fg: "#101317" },
    { name: "Midnight", bg: "#11141a", fg: "#ffffff" },
    { name: "Charcoal", bg: "#22262e", fg: "#ffffff" },
    { name: "Royal", bg: "#101f45", fg: "#ffffff" },
    { name: "Forest", bg: "#0f2a20", fg: "#eafaf1" },
    { name: "Teal", bg: "#083330", fg: "#e6fbf6" },
    { name: "Wine", bg: "#2a0f18", fg: "#ffe9ef" },
    { name: "Espresso", bg: "#241a12", fg: "#f6ecdf" },
    { name: "Ivory", bg: "#f2efe6", fg: "#20262f" },
    { name: "Cloud", bg: "#e6ebf1", fg: "#182432" },
];
const DEFAULT_THEME = THEMES[0];

export function IdCardModal({ employee, onClose }: IdCardModalProps) {
    const frontRef = useRef<HTMLDivElement>(null);
    const backRef = useRef<HTMLDivElement>(null);
    const [loading, setLoading] = useState(true);
    const [exporting, setExporting] = useState<IdCardFormat | null>(null);
    const [menuOpen, setMenuOpen] = useState(false);
    const [photoDataUrl, setPhotoDataUrl] = useState<string | null>(null);
    const [qrDataUrl, setQrDataUrl] = useState<string | null>(null);
    const [company, setCompany] = useState<IdCardCompany>({ name: "StopForFuel" });
    const [bgColor, setBgColor] = useState(DEFAULT_THEME.bg);
    const [fontColor, setFontColor] = useState(DEFAULT_THEME.fg);

    useEffect(() => {
        let cancelled = false;
        (async () => {
            // Company print info (name, address, phone, owner, logo)
            let comp: IdCardCompany = { name: "StopForFuel" };
            try {
                const res = await fetchWithAuth(`${API_BASE_URL}/companies/print-info`);
                if (res.ok) {
                    const c = await res.json();
                    if (c) {
                        comp = {
                            name: c.name || "StopForFuel",
                            address: c.address,
                            phone: c.phone,
                            ownerName: c.ownerName,
                            ownerPhone: c.phone, // station phone as outlet contact
                        };
                        // Logo priority: bundled brand asset → company logo → text
                        const staticLogo = await imageUrlToDataUrl("/id-card-logo.png");
                        if (staticLogo) {
                            comp.logoDataUrl = staticLogo;
                        } else if (c.id && c.logoUrl) {
                            try {
                                const lr = await fetchWithAuth(`${API_BASE_URL}/companies/${c.id}/logo-url`);
                                if (lr.ok) {
                                    const { url } = await lr.json();
                                    comp.logoDataUrl = await imageUrlToDataUrl(url);
                                }
                            } catch { /* fall through to text */ }
                        }
                    }
                }
            } catch { /* keep default */ }

            // Employee photo → data URL (graceful fallback to initials)
            let photo: string | null = null;
            if (employee.photoUrl) {
                try {
                    const { url } = await getEmployeeFileUrl(employee.id, "photo");
                    photo = await imageUrlToDataUrl(url);
                } catch { /* placeholder */ }
            }

            // QR vCard
            let qr: string | null = null;
            try {
                qr = await generateQrDataUrl(buildVCard(employee, comp.name));
            } catch { /* no qr */ }

            if (!cancelled) {
                setCompany(comp);
                setPhotoDataUrl(photo);
                setQrDataUrl(qr);
                setLoading(false);
            }
        })();
        return () => { cancelled = true; };
    }, [employee]);

    const baseName = `ID-Card-${(employee.employeeCode || employee.name || "employee").replace(/\s+/g, "-")}`;

    const handleExport = async (format: IdCardFormat) => {
        setMenuOpen(false);
        if (!frontRef.current || !backRef.current) return;
        setExporting(format);
        try {
            await exportIdCard(frontRef.current, backRef.current, format, baseName);
        } catch (err) {
            console.error("ID card export failed", err);
            alert("Could not generate the ID card. Please try again.");
        } finally {
            setExporting(null);
        }
    };

    const previewBoxW = (CARD_W * 2 + GAP) * PREVIEW_SCALE;
    const previewBoxH = CARD_H * PREVIEW_SCALE;

    return (
        <Modal isOpen onClose={onClose} title={`ID Card — ${employee.name}`} size="lg">
            <div className="space-y-6">
                <p className="text-sm text-muted-foreground">
                    Preview of the employee identity card (front &amp; back), pre-filled from this profile.
                    PDF downloads as two pages (front, then back); PNG/JPEG download as two separate
                    files — ready to print on each side of the card.
                </p>

                {/* Preview */}
                <div className="flex justify-center overflow-x-auto">
                    {loading ? (
                        <div className="flex flex-col items-center justify-center gap-3 text-muted-foreground" style={{ height: previewBoxH }}>
                            <Loader2 className="w-6 h-6 animate-spin" />
                            <span className="text-sm">Preparing card…</span>
                        </div>
                    ) : (
                        <div style={{ position: "relative", width: previewBoxW, height: previewBoxH, flexShrink: 0 }}>
                            {/* Visible preview — scaled down for display only (no refs) */}
                            <div style={{ position: "absolute", top: 0, left: 0, transform: `scale(${PREVIEW_SCALE})`, transformOrigin: "top left", display: "flex", gap: GAP }}>
                                <IdCard side="front" employee={employee} company={company} photoDataUrl={photoDataUrl} bgColor={bgColor} fontColor={fontColor} />
                                <IdCard side="back" employee={employee} company={company} qrDataUrl={qrDataUrl} bgColor={bgColor} fontColor={fontColor} />
                            </div>
                            {/*
                             * Capture source — full-size, un-transformed copies portalled to
                             * <body>. html2canvas mis-positions every text run when the node sits
                             * inside a CSS-scaled / overflow-clipped ancestor (which is what made
                             * the exported text overlap), so the export must read these instead.
                             */}
                            {createPortal(
                                <div aria-hidden style={{ position: "fixed", top: 0, left: -100000, display: "flex", gap: GAP }}>
                                    <IdCard ref={frontRef} side="front" employee={employee} company={company} photoDataUrl={photoDataUrl} bgColor={bgColor} fontColor={fontColor} />
                                    <IdCard ref={backRef} side="back" employee={employee} company={company} qrDataUrl={qrDataUrl} bgColor={bgColor} fontColor={fontColor} />
                                </div>,
                                document.body,
                            )}
                        </div>
                    )}
                </div>

                <div className="flex justify-center gap-6 text-xs text-muted-foreground -mt-2">
                    <span style={{ width: CARD_W * PREVIEW_SCALE }} className="text-center">FRONT</span>
                    <span style={{ width: CARD_W * PREVIEW_SCALE }} className="text-center">BACK</span>
                </div>

                {/* Colour customisation */}
                {!loading && (
                    <div className="space-y-3 pt-1">
                        <div className="text-xs font-medium text-muted-foreground text-center">
                            Card colour — try different combinations
                        </div>
                        <div className="flex flex-wrap justify-center gap-2">
                            {THEMES.map((t) => {
                                const active = t.bg === bgColor && t.fg === fontColor;
                                return (
                                    <button
                                        key={t.name}
                                        type="button"
                                        title={t.name}
                                        onClick={() => { setBgColor(t.bg); setFontColor(t.fg); }}
                                        className={`flex items-center gap-1.5 pl-1.5 pr-2.5 py-1 rounded-full border text-xs transition-colors ${active ? "border-primary ring-1 ring-primary text-foreground" : "border-border text-muted-foreground hover:border-primary/60"}`}
                                    >
                                        <span className="flex items-center justify-center w-4 h-4 rounded-full border border-black/20" style={{ background: t.bg }}>
                                            <span className="w-1.5 h-1.5 rounded-full" style={{ background: t.fg }} />
                                        </span>
                                        {t.name}
                                    </button>
                                );
                            })}
                        </div>
                        <div className="flex justify-center gap-8">
                            <label className="flex items-center gap-2 text-xs text-muted-foreground">
                                Background
                                <input
                                    type="color"
                                    value={bgColor}
                                    onChange={(e) => setBgColor(e.target.value)}
                                    className="w-9 h-8 rounded cursor-pointer bg-transparent border border-border p-0.5"
                                    aria-label="Background color"
                                />
                            </label>
                            <label className="flex items-center gap-2 text-xs text-muted-foreground">
                                Text
                                <input
                                    type="color"
                                    value={fontColor}
                                    onChange={(e) => setFontColor(e.target.value)}
                                    className="w-9 h-8 rounded cursor-pointer bg-transparent border border-border p-0.5"
                                    aria-label="Text color"
                                />
                            </label>
                        </div>
                    </div>
                )}

                {/* Download dropdown */}
                <div className="flex justify-center">
                    <div className="relative">
                        <button
                            onClick={() => setMenuOpen((o) => !o)}
                            disabled={loading || exporting !== null}
                            className="inline-flex items-center gap-2 px-5 py-2.5 text-sm font-medium bg-primary text-primary-foreground rounded-md hover:bg-primary/90 transition-colors disabled:opacity-60"
                        >
                            {exporting ? <Loader2 className="w-4 h-4 animate-spin" /> : <Download className="w-4 h-4" />}
                            {exporting ? `Generating ${exporting.toUpperCase()}…` : "Download"}
                            {!exporting && <ChevronDown className="w-4 h-4" />}
                        </button>
                        {menuOpen && !exporting && (
                            <div className="absolute left-1/2 -translate-x-1/2 mt-2 w-44 bg-card border border-border rounded-md shadow-lg overflow-hidden z-10">
                                <MenuItem icon={<FileText className="w-4 h-4" />} label="PDF document" onClick={() => handleExport("pdf")} />
                                <MenuItem icon={<ImageIcon className="w-4 h-4" />} label="PNG image" onClick={() => handleExport("png")} />
                                <MenuItem icon={<ImageIcon className="w-4 h-4" />} label="JPEG image" onClick={() => handleExport("jpeg")} />
                            </div>
                        )}
                    </div>
                </div>
            </div>
        </Modal>
    );
}

function MenuItem({ icon, label, onClick }: { icon: React.ReactNode; label: string; onClick: () => void }) {
    return (
        <button onClick={onClick} className="flex items-center gap-2 w-full px-4 py-2.5 text-sm text-left hover:bg-muted transition-colors">
            {icon}{label}
        </button>
    );
}
