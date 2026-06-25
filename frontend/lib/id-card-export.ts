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

/** Compose front + back canvases side-by-side onto a single padded canvas. */
function composeSideBySide(front: HTMLCanvasElement, back: HTMLCanvasElement, bg: string | null): HTMLCanvasElement {
    const pad = Math.round(front.width * 0.07);
    const gap = pad;
    const out = document.createElement("canvas");
    out.width = front.width + back.width + gap + pad * 2;
    out.height = Math.max(front.height, back.height) + pad * 2;
    const ctx = out.getContext("2d")!;
    if (bg) {
        ctx.fillStyle = bg;
        ctx.fillRect(0, 0, out.width, out.height);
    }
    ctx.drawImage(front, pad, pad);
    ctx.drawImage(back, pad + front.width + gap, pad);
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

function triggerDownload(dataUrl: string, filename: string) {
    const a = document.createElement("a");
    a.href = dataUrl;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
}

/**
 * Capture the front/back card nodes and download in the chosen format.
 * PNG keeps transparency; JPEG/PDF render on a light background.
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
        const cardW = 54;
        const cardH = (front.height / front.width) * cardW;
        const gap = 12;
        const totalW = cardW * 2 + gap;
        const x0 = (210 - totalW) / 2;
        const y0 = 36;
        pdf.addImage(canvasToWhiteJpeg(front), "JPEG", x0, y0, cardW, cardH);
        pdf.addImage(canvasToWhiteJpeg(back), "JPEG", x0 + cardW + gap, y0, cardW, cardH);
        pdf.setFontSize(8);
        pdf.setTextColor(120);
        pdf.text("FRONT", x0 + cardW / 2, y0 + cardH + 6, { align: "center" });
        pdf.text("BACK", x0 + cardW + gap + cardW / 2, y0 + cardH + 6, { align: "center" });
        pdf.save(`${baseName}.pdf`);
        return;
    }

    const bg = format === "jpeg" ? "#ffffff" : null;
    const composed = composeSideBySide(front, back, bg);
    const mime = format === "jpeg" ? "image/jpeg" : "image/png";
    const dataUrl = composed.toDataURL(mime, 0.95);
    triggerDownload(dataUrl, `${baseName}.${format === "jpeg" ? "jpg" : "png"}`);
}
