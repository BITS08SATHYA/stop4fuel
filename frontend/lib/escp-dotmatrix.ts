// ---------------------------------------------------------------------------
// ESC/P text generator for the TVS MSP 250 (9-pin dot-matrix).
//
// WHY this exists: the old dot-matrix path rendered the slip as HTML and printed
// it through window.print(), so the OS driver rasterised an antialiased TrueType
// page and pushed it to the 9-pin head in GRAPHICS mode. Graphics mode fires the
// head dot-column by dot-column — a slip that takes ~2s in text mode takes the
// better part of a minute. The browser also doesn't know the printer's
// unprintable margins, so the leading character of every line and the trailing
// digits of the Amount column land off the carriage and get clipped.
//
// The fix is to stop rasterising. A dot-matrix printer's strength is TEXT mode:
// we send plain ASCII with the columns counted out in characters, and the printer
// draws it with its own crisp built-in font. Columns are sized to the form width
// in characters, so nothing can spill into a margin. These bytes go to the
// MSP 250 through the same local print agent that drives the thermal printer
// (RAW spool via WritePrinter) — see lib/print-agent.ts.
//
// Built from the SAME buildReceiptModel() as the thermal/HTML paths, so a tax
// invoice can never differ across the three media.
// ---------------------------------------------------------------------------
import { InvoiceBill } from "@/lib/api/station";
import { asciiSafe, buildReceiptModel, type CompanyInfo } from "@/lib/invoice-print";

// --- per-machine form geometry ---------------------------------------------
// Every counter's MSP 250 is parked on its pre-printed stationery slightly
// differently, and the tractor inset differs per stationery batch — so these are
// tunable at the counter (Invoices → History → printer gear) instead of being
// constants only a developer can move. Stored per machine, like printAgentUrl.
//
//   quality    — "draft" is a SINGLE head pass and is what makes a slip print in
//                a couple of seconds. "nlq" is a two-pass letter-quality font:
//                prettier, roughly 3-4x slower, and on a worn head the two passes
//                often fail to register (the "doubled/blurry" look). Draft is the
//                default for exactly that reason.
//   cpi        — character pitch. 10 = pica, 12 = elite, 17 = condensed. Raise it
//                to squeeze more columns onto the same paper width.
//   leftMargin — columns of inset. At 0 the first characters fall in the
//                printer's left dead zone and their leading glyphs clip.
//   width      — columns of content. leftMargin + width must stay inside the
//                carriage: at 10 cpi a 6in form is ~60 columns.
//   topLines   — blank lines before the header, to drop the slip clear of the
//                perforation when the paper is parked a little high.
//   pageLines  — form length in lines. 4.5in at 6 LPI = 27. The generator keeps
//                the slip within this so one bill never spills onto two forms.
export interface DotMatrixForm {
    quality: "draft" | "nlq";
    cpi: 10 | 12 | 17;
    leftMargin: number;
    width: number;
    topLines: number;
    pageLines: number;
}

export const DOT_MATRIX_FORM_DEFAULTS: DotMatrixForm = {
    quality: "draft",
    cpi: 10,
    leftMargin: 6,
    width: 46,
    topLines: 0,
    pageLines: 27,
};

const FORM_KEY = "dotMatrixForm";

const clampInt = (v: unknown, lo: number, hi: number, dflt: number): number => {
    const n = Math.round(Number(v));
    return Number.isFinite(n) ? Math.min(hi, Math.max(lo, n)) : dflt;
};

/** Coerce anything (stale storage, a half-typed input) into a printable form. */
function normalize(raw: Record<string, unknown>): DotMatrixForm {
    const d = DOT_MATRIX_FORM_DEFAULTS;
    const cpi = clampInt(raw.cpi, 10, 17, d.cpi);
    return {
        quality: raw.quality === "nlq" ? "nlq" : "draft",
        cpi: (cpi >= 17 ? 17 : cpi >= 12 ? 12 : 10) as DotMatrixForm["cpi"],
        leftMargin: clampInt(raw.leftMargin, 0, 40, d.leftMargin),
        width: clampInt(raw.width, 24, 120, d.width),
        topLines: clampInt(raw.topLines, 0, 10, d.topLines),
        pageLines: clampInt(raw.pageLines, 10, 72, d.pageLines),
    };
}

/** Per-machine geometry, falling back to the defaults for anything unset/absurd. */
export function getDotMatrixForm(): DotMatrixForm {
    if (typeof window === "undefined") return { ...DOT_MATRIX_FORM_DEFAULTS };
    let raw: Record<string, unknown> = {};
    try {
        raw = JSON.parse(window.localStorage.getItem(FORM_KEY) || "{}") || {};
    } catch (_) { /* absent or corrupt — use defaults */ }
    return normalize(raw);
}

// Normalise before storing, not just on read: otherwise a slipped keystroke in
// the settings panel leaves "width: 999" sitting in storage, and the saved value
// no longer says what the printer is actually doing.
export function setDotMatrixForm(f: Partial<DotMatrixForm>): void {
    if (typeof window === "undefined") return;
    try {
        const merged = normalize({ ...getDotMatrixForm(), ...f } as Record<string, unknown>);
        window.localStorage.setItem(FORM_KEY, JSON.stringify(merged));
    } catch (_) { /* storage blocked — the defaults still print */ }
}

export function resetDotMatrixForm(): void {
    if (typeof window === "undefined") return;
    try { window.localStorage.removeItem(FORM_KEY); } catch (_) { /* ignore */ }
}

// --- ESC/P control codes (Epson-compatible 9-pin) --------------------------
const ESC = 0x1b;
const INIT = [ESC, 0x40];          // ESC @  — reset to power-on defaults
const DRAFT = [ESC, 0x78, 0x00];   // ESC x 0 — draft: one head pass, ~4x faster
const NLQ = [ESC, 0x78, 0x01];     // ESC x 1 — near-letter-quality: two passes
const BIDIR = [ESC, 0x55, 0x00];   // ESC U 0 — bidirectional (prints both ways)
const UNIDIR = [ESC, 0x55, 0x01];  // ESC U 1 — one direction: halves the speed but
                                   // makes NLQ's two passes register on a worn head
const PICA = [ESC, 0x50];          // ESC P  — 10 cpi
const ELITE = [ESC, 0x4d];         // ESC M  — 12 cpi
const CONDENSED_ON = [0x0f];       // SI     — condensed (~17 cpi from pica)
const CONDENSED_OFF = [0x12];      // DC2    — cancel condensed
const LINE_1_6 = [ESC, 0x32];      // ESC 2  — 1/6" line spacing (6 LPI)
const EMPH_ON = [ESC, 0x45];       // ESC E  — emphasized (bold). NOT double-strike:
const EMPH_OFF = [ESC, 0x46];      // double-strike re-hits the head and reads "doubled".
const CR = 0x0d;
const LF = 0x0a;
const FF = 0x0c;

// A slip line. `drop` marks it as sacrificial when the bill is too tall for the
// form — higher numbers go first — so a long bill shortens instead of spilling
// its tail onto the next customer's slip.
interface Row { s: string; bold?: boolean; drop?: number }

const DROP_SPACER = 4;
const DROP_FOOTER = 3;
const DROP_AUDIT = 2;
const DROP_OVERFLOW = 1; // continuation of a wrapped line — shed last

// --- small string helpers (operate on already-ASCII content) ---------------
const trunc = (s: string, n: number) => (s.length > n ? s.slice(0, n) : s);
const padR = (s: string, n: number) => trunc(s, n).padEnd(n, " ");
const padL = (s: string, n: number) => trunc(s, n).padStart(n, " ");

/**
 * Word-wrap to at most `max` lines of `n` columns. Used for the two fields that
 * routinely outrun a 46-column slip — the station address and a company
 * customer's name — because a tax invoice that silently loses its pin code or
 * half the buyer's legal name is worse than one that spends a second line.
 */
function wrapWords(s: string, n: number, max = 2): string[] {
    const words = s.trim().split(/\s+/).filter(Boolean);
    if (!words.length) return [];
    const lines: string[] = [];
    let cur = "";
    for (const w of words) {
        const next = cur ? `${cur} ${w}` : w;
        if (next.length <= n) { cur = next; continue; }
        lines.push(cur || trunc(w, n));
        if (lines.length === max) return lines;       // out of lines: drop the tail
        cur = cur ? trunc(w, n) : "";
    }
    if (cur) lines.push(cur);
    return lines;
}

/** Setup bytes for a form: quality, pitch, spacing, margins, page length. */
function preamble(form: DotMatrixForm): number[] {
    const out = [...INIT];
    out.push(...(form.quality === "nlq" ? NLQ : DRAFT));
    // Unidirectional only earns its 2x cost in NLQ, where it aligns the two
    // passes. Draft is a single pass, so bidirectional is free speed.
    out.push(...(form.quality === "nlq" ? UNIDIR : BIDIR));
    if (form.cpi === 12) out.push(...ELITE, ...CONDENSED_OFF);
    else if (form.cpi === 17) out.push(...PICA, ...CONDENSED_ON);
    else out.push(...PICA, ...CONDENSED_OFF);
    out.push(ESC, 0x6c, form.leftMargin);            // ESC l n — left margin, columns
    out.push(...LINE_1_6);
    out.push(ESC, 0x43, form.pageLines);             // ESC C n — page length, lines
    return out;
}

/** Drop sacrificial rows (widest `drop` first) until the list clears `budget`. */
function shed(rows: Row[], budget: number): Row[] {
    const kept = [...rows];
    for (let level = DROP_SPACER; level >= DROP_OVERFLOW && kept.length > budget; level--) {
        for (let i = kept.length - 1; i >= 0 && kept.length > budget; i--) {
            if (kept[i].drop === level) kept.splice(i, 1);
        }
    }
    return kept;
}

/**
 * Lay a slip out over as many forms as it needs.
 *
 * `head` repeats at the top of every form and `tail` (totals, signature) lands on
 * the last one; only `items` flow. Almost every bill is one or two lines, fits on
 * one form, and never reaches the pagination branch — but a ten-line bill on a
 * 4.5in slip genuinely cannot fit, and the alternative is worse than a second
 * form: with ESC C set, the printer auto-feeds at the page boundary and the
 * overflow lands unannounced on the NEXT customer's pre-printed stationery.
 */
function paginate(head: Row[], items: Row[], tail: Row[], budget: number): Row[][] {
    if (head.length + items.length + tail.length <= budget) return [[...head, ...items, ...tail]];

    const CONTD: Row = { s: "-- continued on next slip --" };
    const room = budget - head.length;
    if (room < 2) return [[...head, ...items, ...tail]]; // nonsense geometry: don't loop

    const pages: Row[][] = [];
    let i = 0;
    let tailPlaced = false;
    while (i < items.length) {
        const rest = items.length - i;
        if (rest + tail.length <= room) {
            pages.push([...head, ...items.slice(i), ...tail]);
            tailPlaced = true;
            break;
        }
        const take = Math.max(1, room - 1); // one line reserved for the continued marker
        pages.push([...head, ...items.slice(i, i + take), CONTD]);
        i += take;
    }
    if (!tailPlaced) pages.push([...head, ...tail]);
    return pages;
}

/** Render laid-out rows to ESC/P bytes, feeding to the next form after each. */
function render(head: Row[], items: Row[], tail: Row[], form: DotMatrixForm): Uint8Array {
    const budget = Math.max(1, form.pageLines - form.topLines);
    // Shed the sacrificial rows first: losing the blank spacer and the
    // "Computer-generated invoice." line is a far better trade than a second form.
    const shedHead = shed(head, Math.max(1, budget - items.length - tail.length));
    const shedTail = shed(tail, Math.max(1, budget - shedHead.length - items.length));

    const out: number[] = [...preamble(form)];
    const push = (...b: number[]) => { for (const x of b) out.push(x); };
    // asciiSafe restricts to 0x20-0x7E, so charCodeAt maps 1:1 to printable bytes.
    const text = (s: string) => { const a = asciiSafe(s); for (let i = 0; i < a.length; i++) push(a.charCodeAt(i) & 0x7f); };

    for (const page of paginate(shedHead, items, shedTail, budget)) {
        for (let i = 0; i < form.topLines; i++) push(CR, LF);
        for (const row of page) {
            if (row.bold) push(...EMPH_ON);
            text(trunc(row.s, form.width));
            if (row.bold) push(...EMPH_OFF);
            push(CR, LF);
        }
        // Advance to the top of the next pre-printed slip.
        push(FF);
    }
    return new Uint8Array(out);
}

export function generateDotMatrixEscP(
    invoice: InvoiceBill,
    company: CompanyInfo,
    form: DotMatrixForm = getDotMatrixForm(),
): Uint8Array {
    const m = buildReceiptModel(invoice, company);
    const isCash = m.billBadge === "CASH";
    const driverName = /^(null|undefined)$/i.test((invoice.driverName ?? "").trim())
        ? "" : asciiSafe(invoice.driverName);

    const W = form.width;
    // Item columns are sized off the form width so a wider pitch (12/17 cpi) or a
    // narrower slip re-flows the table instead of clipping the Amount column.
    const COL_AMOUNT = Math.max(9, Math.round(W * 0.20));
    const COL_RATE = Math.max(7, Math.round(W * 0.17));
    const COL_QTY = Math.max(6, Math.round(W * 0.15));
    const COL_PRODUCT = Math.max(8, W - COL_AMOUNT - COL_RATE - COL_QTY);

    // Three sections, because a bill too tall for the form has to break somewhere:
    // `head` repeats on every slip, `items` flow, `tail` closes the last slip.
    const head: Row[] = [];
    const itemRows: Row[] = [];
    const tail: Row[] = [];
    let rows = head;
    const line = (s: string, bold?: boolean, drop?: number) => rows.push({ s, bold, drop });
    const centered = (s: string, bold?: boolean, drop?: number) => {
        const t = trunc(s, W);
        line(" ".repeat(Math.max(0, Math.floor((W - t.length) / 2))) + t, bold, drop);
    };
    const rule = (ch = "-", drop?: number) => line(ch.repeat(W), false, drop);

    // Left field + right field on one line, right field flush to the form width.
    // Stacks onto two lines if they'd collide, so a long value is never truncated.
    const pair = (l: string, r: string) => {
        if (!r) return line(l);
        const gap = W - l.length - r.length;
        if (gap < 1) { line(l); line(r); return; }
        line(l + " ".repeat(gap) + r);
    };

    // --- header ---
    centered(m.company.name, true);
    wrapWords(m.company.address, W).forEach((l, i) => centered(l, false, i ? DROP_OVERFLOW : undefined));
    centered(`Ph: ${m.company.phone} | GSTIN: ${m.company.gstNo}`);
    rule("=");
    centered(`TAX INVOICE  [${m.billBadge}]`, true);
    rule("-");

    // --- meta ---
    const custLabel = `Customer: ${m.customerName}${m.customerPhone ? ` (${m.customerPhone})` : ""}`;
    wrapWords(custLabel, W).forEach((l, i) => line(i ? `  ${trunc(l, W - 2)}` : l, false, i ? DROP_OVERFLOW : undefined));
    if (m.customerGST) line(`GSTIN: ${m.customerGST}`);
    pair(`Bill: ${m.billNo}`, `Cashier: ${m.cashierLabel}`);
    line(`Date: ${m.dateStr}`);
    pair(`Shift: ${m.shiftId}`, m.vehicleNo ? `Vehicle: ${m.vehicleNo}` : "");
    if (driverName) line(`Driver: ${driverName}`);
    if (m.showIndent) line(`Indent: ${m.indentNo}`);
    if (m.showOdometer) line(`Odometer: ${m.odometerDisplay}`);
    rule("=");

    // --- items (column header stays in `head` so it repeats on a second slip) ---
    line(padR("PRODUCT", COL_PRODUCT) + padL("QTY", COL_QTY) + padL("RATE", COL_RATE) + padL("AMOUNT", COL_AMOUNT), true);
    rule("-");

    rows = itemRows;
    for (const it of m.items) {
        const name = it.discount ? `${it.name} (-${it.discount})` : it.name;
        line(padR(name, COL_PRODUCT) + padL(it.qty, COL_QTY) + padL(it.unitRate, COL_RATE) + padL(it.amt, COL_AMOUNT));
    }

    rows = tail;
    rule("-");

    // --- totals (label right-aligned up to the amount column) ---
    const totalRow = (label: string, val: string, bold = false) =>
        line(padL(label, W - COL_AMOUNT) + padL(val, COL_AMOUNT), bold);
    totalRow("Sub Total", m.subTotal);
    if (m.totalDiscount > 0) totalRow("Discount", `-${m.totalDiscountStr}`);
    totalRow("Total (Rs.)", m.netAmountStr, true);
    rule("=");

    // --- audit fill-ins / footer ---
    if (!isCash) {
        line("", false, DROP_SPACER);
        centered("Customer Signature: ______________");
    }
    line(trunc("Pump:______ Nozzle:_____ Attdnt:_____", W), false, DROP_AUDIT);
    centered("Computer-generated invoice.", false, DROP_FOOTER);

    return render(head, itemRows, tail, form);
}

/**
 * Alignment test slip. Prints the form's own geometry plus a column ruler and
 * edge markers, so a cashier can nudge leftMargin/width/topLines until the "["
 * and "]" markers both land inside the pre-printed box — no dev, no code change.
 */
export function generateDotMatrixTestSlip(
    company: CompanyInfo,
    form: DotMatrixForm = getDotMatrixForm(),
): Uint8Array {
    const W = form.width;
    const rows: Row[] = [];
    const line = (s: string, bold?: boolean) => rows.push({ s, bold });

    // Ruler: a "." per column with the tens marked, so a clipped edge is countable.
    let ruler = "";
    for (let i = 1; i <= W; i++) ruler += i % 10 === 0 ? String((i / 10) % 10) : ".";

    line("[" + "=".repeat(Math.max(0, W - 2)) + "]", true);
    line("[" + " PRINT ALIGNMENT TEST".padEnd(Math.max(0, W - 2)).slice(0, Math.max(0, W - 2)) + "]");
    line(ruler);
    line(padR(asciiSafe(company.name), W));
    line(`quality=${form.quality}  cpi=${form.cpi}`);
    line(`left=${form.leftMargin}  width=${W}  top=${form.topLines}  page=${form.pageLines}`);
    line("[" + padL("both brackets must show", Math.max(0, W - 2)) + "]");
    line("[" + "=".repeat(Math.max(0, W - 2)) + "]", true);

    return render(rows, [], [], form);
}
