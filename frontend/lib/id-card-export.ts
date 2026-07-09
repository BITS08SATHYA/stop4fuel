import html2canvas from "html2canvas";
import { jsPDF } from "jspdf";
import QRCode from "qrcode";
import type { Employee } from "@/components/employees/types";

export type IdCardFormat = "pdf" | "png" | "jpeg";

/**
 * Load a (possibly cross-origin) image URL into a base64 data URL so it can be
 * embedded in the captured card without tainting the html2canvas canvas.
 * Returns null on any failure (CORS, 404, …) so the card falls back gracefully.
 */
export async function imageUrlToDataUrl(url: string): Promise<string | null> {
    // Same-origin / already a data URL → use directly.
    if (url.startsWith("data:")) return url;
    try {
        const img = new Image();
        img.crossOrigin = "anonymous";
        const loaded = new Promise<HTMLImageElement>((resolve, reject) => {
            img.onload = () => resolve(img);
            img.onerror = () => reject(new Error("image load failed"));
        });
        img.src = url;
        await loaded;
        const canvas = document.createElement("canvas");
        canvas.width = img.naturalWidth;
        canvas.height = img.naturalHeight;
        const ctx = canvas.getContext("2d");
        if (!ctx) return null;
        ctx.drawImage(img, 0, 0);
        return canvas.toDataURL("image/png");
    } catch {
        return null;
    }
}

/** Build a vCard payload so scanning the QR saves the employee as a contact. */
export function buildVCard(employee: Employee, companyName: string): string {
    const lines = [
        "BEGIN:VCARD",
        "VERSION:3.0",
        `FN:${employee.name || ""}`,
        `N:${employee.name || ""};;;;`,
        companyName ? `ORG:${companyName}` : "",
        employee.designation ? `TITLE:${employee.designation}` : "",
        employee.phone ? `TEL;TYPE=CELL:${employee.phone}` : "",
        employee.email ? `EMAIL:${employee.email}` : "",
        employee.employeeCode ? `NOTE:Employee ID ${employee.employeeCode}` : "",
        "END:VCARD",
    ].filter(Boolean);
    return lines.join("\n");
}

export async function generateQrDataUrl(text: string): Promise<string> {
    return QRCode.toDataURL(text, {
        margin: 1,
        width: 320,
        errorCorrectionLevel: "M",
        color: { dark: "#0b0d11", light: "#ffffff" },
    });
}

async function captureCard(node: HTMLElement): Promise<HTMLCanvasElement> {
    return html2canvas(node, {
        scale: 3,
        backgroundColor: null,
        useCORS: true,
        logging: false,
    });
}

/** Pad a single card canvas (optionally onto a solid background). */
function padCard(src: HTMLCanvasElement, bg: string | null): HTMLCanvasElement {
    const pad = Math.round(src.width * 0.07);
    const out = document.createElement("canvas");
    out.width = src.width + pad * 2;
    out.height = src.height + pad * 2;
    const ctx = out.getContext("2d")!;
    if (bg) {
        ctx.fillStyle = bg;
        ctx.fillRect(0, 0, out.width, out.height);
    }
    ctx.drawImage(src, pad, pad);
    return out;
}

/** Flatten a (possibly transparent) canvas onto white and return a JPEG data URL. */
function canvasToWhiteJpeg(src: HTMLCanvasElement, quality = 0.92): string {
    const out = document.createElement("canvas");
    out.width = src.width;
    out.height = src.height;
    const ctx = out.getContext("2d")!;
    ctx.fillStyle = "#ffffff";
    ctx.fillRect(0, 0, out.width, out.height);
    ctx.drawImage(src, 0, 0);
    return out.toDataURL("image/jpeg", quality);
}

/**
 * Convert a canvas to a Blob object URL. Anchor downloads backed by data URLs
 * silently fail in Chrome once the URL exceeds ~2MB (a scale-3 PNG card does),
 * so downloads must go through blob URLs instead.
 */
function canvasToBlobUrl(canvas: HTMLCanvasElement, mime: string, quality: number): Promise<string> {
    return new Promise((resolve, reject) => {
        canvas.toBlob(
            (blob) => (blob ? resolve(URL.createObjectURL(blob)) : reject(new Error("canvas.toBlob failed"))),
            mime,
            quality,
        );
    });
}

function triggerDownload(url: string, filename: string) {
    const a = document.createElement("a");
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
}

/**
 * Capture the front/back card nodes and download in the chosen format, with
 * front and back kept separate so each can be printed onto one side of the card:
 *   • PDF  → page 1 = front, page 2 = back (each card centered at print size).
 *   • PNG  → two files (…-front.png, …-back.png), transparency preserved.
 *   • JPEG → two files (…-front.jpg, …-back.jpg), rendered on white.
 */
export async function exportIdCard(
    frontNode: HTMLElement,
    backNode: HTMLElement,
    format: IdCardFormat,
    baseName: string,
): Promise<void> {
    const [front, back] = await Promise.all([captureCard(frontNode), captureCard(backNode)]);

    if (format === "pdf") {
        const pdf = new jsPDF({ orientation: "portrait", unit: "mm", format: "a4" });
        const pageW = 210, pageH = 297;
        const cardW = 74; // mm — large, centered card per page
        const addPage = (canvas: HTMLCanvasElement, caption: string, first: boolean) => {
            if (!first) pdf.addPage();
            const cardH = (canvas.height / canvas.width) * cardW;
            const x = (pageW - cardW) / 2;
            const y = (pageH - cardH) / 2;
            pdf.addImage(canvasToWhiteJpeg(canvas), "JPEG", x, y, cardW, cardH);
            pdf.setFontSize(9);
            pdf.setTextColor(120);
            pdf.text(caption, pageW / 2, y + cardH + 8, { align: "center" });
        };
        addPage(front, "FRONT", true);
        addPage(back, "BACK", false);
        pdf.save(`${baseName}.pdf`);
        return;
    }

    const bg = format === "jpeg" ? "#ffffff" : null;
    const ext = format === "jpeg" ? "jpg" : "png";
    const mime = format === "jpeg" ? "image/jpeg" : "image/png";
    const [frontUrl, backUrl] = await Promise.all([
        canvasToBlobUrl(padCard(front, bg), mime, 0.95),
        canvasToBlobUrl(padCard(back, bg), mime, 0.95),
    ]);
    try {
        triggerDownload(frontUrl, `${baseName}-front.${ext}`);
        // Stagger the second download — some browsers drop back-to-back programmatic clicks.
        await new Promise((r) => setTimeout(r, 700));
        triggerDownload(backUrl, `${baseName}-back.${ext}`);
    } finally {
        // Give the browser time to start streaming both files before revoking.
        setTimeout(() => {
            URL.revokeObjectURL(frontUrl);
            URL.revokeObjectURL(backUrl);
        }, 60_000);
    }
}
