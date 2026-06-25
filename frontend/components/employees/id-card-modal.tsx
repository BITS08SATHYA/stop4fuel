"use client";

import { useEffect, useRef, useState } from "react";
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

export function IdCardModal({ employee, onClose }: IdCardModalProps) {
    const frontRef = useRef<HTMLDivElement>(null);
    const backRef = useRef<HTMLDivElement>(null);
    const [loading, setLoading] = useState(true);
    const [exporting, setExporting] = useState<IdCardFormat | null>(null);
    const [menuOpen, setMenuOpen] = useState(false);
    const [photoDataUrl, setPhotoDataUrl] = useState<string | null>(null);
    const [qrDataUrl, setQrDataUrl] = useState<string | null>(null);
    const [company, setCompany] = useState<IdCardCompany>({ name: "StopForFuel" });

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
                    Download as PDF, PNG, or JPEG.
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
                            <div style={{ position: "absolute", top: 0, left: 0, transform: `scale(${PREVIEW_SCALE})`, transformOrigin: "top left", display: "flex", gap: GAP }}>
                                <IdCard ref={frontRef} side="front" employee={employee} company={company} photoDataUrl={photoDataUrl} />
                                <IdCard ref={backRef} side="back" employee={employee} company={company} qrDataUrl={qrDataUrl} />
                            </div>
                        </div>
                    )}
                </div>

                <div className="flex justify-center gap-6 text-xs text-muted-foreground -mt-2">
                    <span style={{ width: CARD_W * PREVIEW_SCALE }} className="text-center">FRONT</span>
                    <span style={{ width: CARD_W * PREVIEW_SCALE }} className="text-center">BACK</span>
                </div>

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
