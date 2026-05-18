import html2canvas from "html2canvas";

// ---------------------------------------------------------------------------
// HTML → ESC/POS raster image.
//
// The text-mode ESC/POS path used the printer's blocky built-in font at 32
// columns and looked poor. Instead we render the *same* nice receipt HTML the
// browser print uses, capture it to a bitmap, and send it to the printer as a
// raster image (GS v 0). Result: identical look to the browser-dialog print
// (proportional fonts, boxed badge, full 80mm width) but one click, no dialog.
//
// The local print agent stays "dumb" — it still just relays raw bytes.
// ---------------------------------------------------------------------------

// Printable width in dots. The TVS RP3150 at this site clips a 576-dot raster
// on the right, so its real printable width is the other common value, 512
// (= 64 bytes/row). Content is scaled to fit exactly this, so the only failure
// modes are: still clipped → lower this (448); white gap on the right →
// raise it (576). This is the single knob for "fits the paper".
const PRINTER_DOTS = 512;
const BYTES_PER_ROW = PRINTER_DOTS / 8;          // 72
// Supersample factor: render the receipt this many times larger than the
// printer's dot width, then downscale with smoothing before thresholding.
// This is what makes the text crisp (vs the earlier fuzzy low-res capture) —
// it mimics the Windows driver rendering fonts at full device resolution.
const SUPERSAMPLE = 4;
// Pixels below this luminance print as a black dot. Higher = darker/bolder.
const THRESHOLD = 176;
// Rows per GS v 0 command — keep bands small so the print buffer never
// overflows on a long receipt.
const BAND_ROWS = 128;

const ESC = 0x1b;
const GS = 0x1d;

/** Render the receipt HTML offscreen and capture it to a canvas. */
async function htmlToCanvas(html: string): Promise<HTMLCanvasElement> {
    const iframe = document.createElement("iframe");
    // Offscreen but still laid out & painted (display:none would break capture).
    iframe.style.cssText =
        "position:fixed;left:-10000px;top:0;width:80mm;height:5000px;border:0;background:#fff;";
    document.body.appendChild(iframe);
    try {
        const doc = iframe.contentDocument!;
        doc.open();
        doc.write(html);
        doc.close();

        // Let layout settle (and webfonts, though we use system Courier).
        await new Promise<void>((resolve) => {
            if (doc.readyState === "complete") resolve();
            else iframe.onload = () => resolve();
        });
        await new Promise((r) => setTimeout(r, 60));

        const body = doc.body;
        const wPx = Math.max(body.scrollWidth, body.offsetWidth);
        const hPx = Math.max(body.scrollHeight, body.offsetHeight);

        // Render SUPERSAMPLE× the final dot width so glyph edges are
        // resolved at high resolution; canvasToBits() then downsamples
        // smoothly to PRINTER_DOTS for a crisp 1-bit result.
        const scale = (PRINTER_DOTS * SUPERSAMPLE) / wPx;
        return await html2canvas(body, {
            backgroundColor: "#ffffff",
            width: wPx,
            height: hPx,
            scale,
            useCORS: true,
            logging: false,
        });
    } finally {
        document.body.removeChild(iframe);
    }
}

/** Normalize to exactly PRINTER_DOTS wide and return 1-bpp packed rows. */
function canvasToBits(src: HTMLCanvasElement): { rows: number; data: Uint8Array } {
    const h = Math.max(1, Math.round((src.height * PRINTER_DOTS) / src.width));
    const norm = document.createElement("canvas");
    norm.width = PRINTER_DOTS;
    norm.height = h;
    const ctx = norm.getContext("2d")!;
    ctx.fillStyle = "#fff";
    ctx.fillRect(0, 0, PRINTER_DOTS, h);
    // High-quality downsample of the supersampled capture → clean anti-aliased
    // greyscale, which thresholds into sharp (not jagged) 1-bit strokes.
    ctx.imageSmoothingEnabled = true;
    ctx.imageSmoothingQuality = "high";
    ctx.drawImage(src, 0, 0, PRINTER_DOTS, h);

    const px = ctx.getImageData(0, 0, PRINTER_DOTS, h).data;
    const data = new Uint8Array(BYTES_PER_ROW * h);
    for (let y = 0; y < h; y++) {
        for (let x = 0; x < PRINTER_DOTS; x++) {
            const i = (y * PRINTER_DOTS + x) * 4;
            const a = px[i + 3];
            // Transparent counts as white; otherwise luminance threshold.
            const lum = a < 128 ? 255 : 0.299 * px[i] + 0.587 * px[i + 1] + 0.114 * px[i + 2];
            if (lum < THRESHOLD) {
                data[y * BYTES_PER_ROW + (x >> 3)] |= 0x80 >> (x & 7);
            }
        }
    }
    return { rows: h, data };
}

/**
 * Build the full print job: init, the receipt as banded raster images,
 * feed, and partial cut. Returns raw ESC/POS bytes for the print agent.
 */
export async function buildInvoiceRaster(html: string): Promise<Uint8Array> {
    const canvas = await htmlToCanvas(html);
    const { rows, data } = canvasToBits(canvas);

    const out: number[] = [];
    out.push(ESC, 0x40);          // ESC @  — reset
    out.push(ESC, 0x61, 0x00);    // ESC a 0 — left align (raster is full width)

    for (let y0 = 0; y0 < rows; y0 += BAND_ROWS) {
        const bandRows = Math.min(BAND_ROWS, rows - y0);
        // GS v 0 m xL xH yL yH  — m=0 normal density
        out.push(GS, 0x76, 0x30, 0x00);
        out.push(BYTES_PER_ROW & 0xff, (BYTES_PER_ROW >> 8) & 0xff);
        out.push(bandRows & 0xff, (bandRows >> 8) & 0xff);
        const start = y0 * BYTES_PER_ROW;
        const end = start + bandRows * BYTES_PER_ROW;
        for (let i = start; i < end; i++) out.push(data[i]);
    }

    out.push(0x0a, 0x0a, 0x0a, 0x0a); // feed clear of the cutter
    out.push(GS, 0x56, 0x01);         // GS V 1 — partial cut
    return Uint8Array.from(out);
}
