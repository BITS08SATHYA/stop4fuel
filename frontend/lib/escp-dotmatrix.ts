// ---------------------------------------------------------------------------
// ESC/P text generator for the TVS MSP 250 (9-pin dot-matrix).
//
// WHY this exists: the old dot-matrix path rendered the slip as HTML and printed
// it through window.print(), so the OS driver rasterised an antialiased TrueType
// page and pushed it to the 9-pin head in GRAPHICS mode. At ~72 dpi the grey
// edge pixels of a 900-weight font dither into extra dots — that is the muddy,
// "doubled" look on the printout. The browser also doesn't know the printer's
// unprintable margins, so right-aligned content (the Amount column) lands off
// the carriage and gets clipped.
//
// The fix is to stop rasterising. A dot-matrix printer's strength is TEXT mode:
// we send plain ASCII with the columns counted out in characters, and the printer
// draws it with its own crisp built-in NLQ font. Columns are sized to the form
// width in characters, so nothing can spill into a margin. These bytes go to the
// MSP 250 through the same local print agent that drives the thermal printer
// (RAW spool via WritePrinter) — see lib/print-agent.ts.
//
// Built from the SAME buildReceiptModel() as the thermal/HTML paths, so a tax
// invoice can never differ across the three media.
// ---------------------------------------------------------------------------
import { InvoiceBill } from "@/lib/api/station";
import { asciiSafe, buildReceiptModel, type CompanyInfo } from "@/lib/invoice-print";

// --- form geometry ---------------------------------------------------------
// All tunables for matching the 6x4.5" pre-printed slip live here. If the print
// is shifted or the Amount column clips, nudge these (one test slip at a time):
//   WIDTH   — character columns of content. At 10 cpi, 6in ≈ 60 cols, but the
//             pre-printed border box insets that; 46 is a safe interior width.
//   PAGE_LINES — 4.5in at 6 LPI = 27 lines. Form-feed at the end aligns the next
//             slip to top-of-form. Reduce if slips creep down over a run.
// Column widths sum to WIDTH so the four-column item table is exact.
const WIDTH = 46;
const PAGE_LINES = 27;
const COL_PRODUCT = 22;
const COL_QTY = 7;
const COL_RATE = 8;
const COL_AMOUNT = 9; // 22 + 7 + 8 + 9 = 46

// --- ESC/P control codes (Epson-compatible 9-pin) --------------------------
const ESC = 0x1b;
const INIT = [ESC, 0x40];          // ESC @  — reset to power-on defaults
const NLQ_ON = [ESC, 0x78, 0x01];  // ESC x 1 — near-letter-quality (crisp)
const PICA_10CPI = [ESC, 0x50];    // ESC P  — 10 chars per inch
const LINE_1_6 = [ESC, 0x32];      // ESC 2  — 1/6" line spacing (6 LPI)
const SET_PAGE_LINES = [ESC, 0x43, PAGE_LINES]; // ESC C n — page length in lines
const EMPH_ON = [ESC, 0x45];       // ESC E  — emphasized (bold). NOT double-strike:
const EMPH_OFF = [ESC, 0x46];      // double-strike re-hits the head and reads "doubled".
const CR = 0x0d;
const LF = 0x0a;
const FF = 0x0c;

// --- small string helpers (operate on already-ASCII content) ---------------
const trunc = (s: string, n: number) => (s.length > n ? s.slice(0, n) : s);
const padR = (s: string, n: number) => trunc(s, n).padEnd(n, " ");
const padL = (s: string, n: number) => trunc(s, n).padStart(n, " ");
const center = (s: string) => {
    s = trunc(s, WIDTH);
    const left = Math.max(0, Math.floor((WIDTH - s.length) / 2));
    return " ".repeat(left) + s;
};

export function generateDotMatrixEscP(invoice: InvoiceBill, company: CompanyInfo): Uint8Array {
    const m = buildReceiptModel(invoice, company);
    const isCash = m.billBadge === "CASH";
    const driverName = /^(null|undefined)$/i.test((invoice.driverName ?? "").trim())
        ? "" : asciiSafe(invoice.driverName);

    const out: number[] = [];
    const push = (...b: number[]) => { for (const x of b) out.push(x); };
    // asciiSafe restricts to 0x20–0x7E, so charCodeAt maps 1:1 to printable bytes.
    const text = (s: string) => { const a = asciiSafe(s); for (let i = 0; i < a.length; i++) push(a.charCodeAt(i) & 0x7f); };
    const nl = () => push(CR, LF);
    const lineL = (s: string) => { text(trunc(s, WIDTH)); nl(); };
    const lineC = (s: string) => { text(center(s)); nl(); };
    const rule = (ch = "-") => { text(ch.repeat(WIDTH)); nl(); };
    const emph = (fn: () => void) => { push(...EMPH_ON); fn(); push(...EMPH_OFF); };

    // Left field + right field on one line, right field flush to WIDTH. Stacks
    // onto two lines if they'd collide, so a long value never gets truncated away.
    const pair = (l: string, r: string) => {
        if (!r) return lineL(l);
        const gap = WIDTH - l.length - r.length;
        if (gap < 1) { lineL(l); lineL(r); return; }
        text(l + " ".repeat(gap) + r); nl();
    };

    // --- printer setup ---
    push(...INIT, ...NLQ_ON, ...PICA_10CPI, ...LINE_1_6, ...SET_PAGE_LINES);

    // --- header ---
    emph(() => lineC(m.company.name));
    lineC(m.company.address);
    lineC(`Ph: ${m.company.phone} | GSTIN: ${m.company.gstNo}`);
    rule("=");
    emph(() => lineC(`TAX INVOICE  [${m.billBadge}]`));
    rule("-");

    // --- meta ---
    lineL(`Customer: ${m.customerName}${m.customerPhone ? ` (${m.customerPhone})` : ""}`);
    if (m.customerGST) lineL(`GSTIN: ${m.customerGST}`);
    pair(`Bill: ${m.billNo}`, `Cashier: ${m.cashierLabel}`);
    lineL(`Date: ${m.dateStr}`);
    pair(`Shift: ${m.shiftId}`, m.vehicleNo ? `Vehicle: ${m.vehicleNo}` : "");
    if (driverName) lineL(`Driver: ${driverName}`);
    if (m.showIndent) lineL(`Indent: ${m.indentNo}`);
    if (m.showOdometer) lineL(`Odometer: ${m.odometerDisplay}`);
    rule("=");

    // --- items ---
    emph(() => {
        text(padR("PRODUCT", COL_PRODUCT) + padL("QTY", COL_QTY) + padL("RATE", COL_RATE) + padL("AMOUNT", COL_AMOUNT));
        nl();
    });
    rule("-");
    for (const it of m.items) {
        const name = it.discount ? `${it.name} (-${it.discount})` : it.name;
        text(padR(name, COL_PRODUCT) + padL(it.qty, COL_QTY) + padL(it.unitRate, COL_RATE) + padL(it.amt, COL_AMOUNT));
        nl();
    }
    rule("-");

    // --- totals (label right-aligned up to the amount column) ---
    const totalRow = (label: string, val: string, bold = false) => {
        const body = () => { text(padL(label, WIDTH - COL_AMOUNT) + padL(val, COL_AMOUNT)); nl(); };
        if (bold) emph(body); else body();
    };
    totalRow("Sub Total", m.subTotal);
    if (m.totalDiscount > 0) totalRow("Discount", `-${m.totalDiscountStr}`);
    totalRow("Total (Rs.)", m.netAmountStr, true);
    rule("=");

    // --- audit fill-ins / footer ---
    if (!isCash) { nl(); lineC("Customer Signature: ______________"); }
    lineL("Pump:________ Nozzle:______ Attdnt:______");
    lineC("Computer-generated invoice.");

    // Advance to the top of the next pre-printed slip.
    push(FF);

    return new Uint8Array(out);
}
