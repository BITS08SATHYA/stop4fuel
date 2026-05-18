import type { ReceiptModel } from "@/lib/invoice-print";

// ---------------------------------------------------------------------------
// ESC/POS receipt renderer for the TVS RP3150 STAR (80mm USB thermal).
//
// 80mm roll @ 203 dpi, Font A = 48 characters per line. The receipt content
// is taken verbatim from the shared ReceiptModel, so it never diverges from
// the HTML (browser-fallback) bill. Output is raw printer bytes; the local
// print agent relays them to the printer untouched.
//
// All model strings are already ASCII-safe (asciiSafe() upstream), so a plain
// char-code encode matches the printer's single-byte codepage exactly.
// ---------------------------------------------------------------------------

const WIDTH = 48;

const ESC = 0x1b;
const GS = 0x1d;

// halfRateLabel is duplicated here (not imported) on purpose: importing a
// runtime value from invoice-print.ts would create a circular import, since
// that module imports this one. The type-only ReceiptModel import is erased.
function halfRateLabel(rate: number): string {
    const h = rate / 2;
    return Number.isInteger(h) ? String(h) : h.toFixed(2);
}

class EscPosBuilder {
    private buf: number[] = [];

    raw(...bytes: number[]): this {
        for (const b of bytes) this.buf.push(b & 0xff);
        return this;
    }

    /** Append an ASCII string (no newline). Non-ASCII is dropped defensively. */
    text(s: string): this {
        for (let i = 0; i < s.length; i++) {
            const c = s.charCodeAt(i);
            this.buf.push(c >= 0x20 && c <= 0x7e ? c : 0x3f /* '?' */);
        }
        return this;
    }

    line(s = ""): this {
        return this.text(s).raw(0x0a);
    }

    init(): this {
        return this.raw(ESC, 0x40); // ESC @  — reset
    }

    align(n: 0 | 1 | 2): this {
        return this.raw(ESC, 0x61, n); // ESC a n  — 0 left, 1 center, 2 right
    }

    bold(on: boolean): this {
        return this.raw(ESC, 0x45, on ? 1 : 0); // ESC E n
    }

    /**
     * Double-height + emphasized via ESC ! (Select print mode) — the most
     * universally supported size command. Bit 4 (0x10) = double height,
     * bit 3 (0x08) = emphasized. Width is left ×1 so 48-col math still holds
     * and the grand-total line stays aligned on every ESC/POS printer.
     */
    bigBold(on: boolean): this {
        return this.raw(ESC, 0x21, on ? 0x18 : 0x00); // ESC ! n
    }

    feedCut(): this {
        // Advance content clear of the cutter, then partial-cut.
        return this.raw(0x0a, 0x0a, 0x0a, 0x0a).raw(GS, 0x56, 0x01); // GS V 1
    }

    toBytes(): Uint8Array {
        return Uint8Array.from(this.buf);
    }
}

// --- string layout helpers (operate on plain 48-col text) ------------------

function center(s: string): string {
    if (s.length >= WIDTH) return s.slice(0, WIDTH);
    const pad = Math.floor((WIDTH - s.length) / 2);
    return " ".repeat(pad) + s;
}

/** Left label / right value on one line. Left is truncated if it won't fit. */
function lr(left: string, right: string): string {
    const space = WIDTH - right.length;
    if (space <= 1) return (left + " " + right).slice(0, WIDTH);
    let l = left;
    if (l.length > space - 1) l = l.slice(0, space - 1);
    return l + " ".repeat(WIDTH - l.length - right.length) + right;
}

function ruleDashed(): string { return "-".repeat(WIDTH); }
function ruleHeavy(): string { return "=".repeat(WIDTH); }

/** Word-wrap to width, hard-splitting any token longer than the width. */
function wrap(s: string, w = WIDTH): string[] {
    const out: string[] = [];
    for (const para of s.split("\n")) {
        let cur = "";
        for (const word of para.split(/\s+/).filter(Boolean)) {
            let token = word;
            while (token.length > w) {
                if (cur) { out.push(cur); cur = ""; }
                out.push(token.slice(0, w));
                token = token.slice(w);
            }
            if (!cur) cur = token;
            else if (cur.length + 1 + token.length <= w) cur += " " + token;
            else { out.push(cur); cur = token; }
        }
        out.push(cur);
    }
    return out.length ? out : [""];
}

// Item table columns (sum = 48): name 18 | qty 8 | rate 9 | amount 13.
const C_NAME = 18, C_QTY = 8, C_RATE = 9, C_AMT = 13;

function padR(s: string, w: number): string {
    return s.length >= w ? s.slice(0, w) : s + " ".repeat(w - s.length);
}
function padL(s: string, w: number): string {
    return s.length >= w ? s.slice(s.length - w) : " ".repeat(w - s.length) + s;
}

function itemRow(name: string, qty: string, rate: string, amt: string): string {
    return padR(name, C_NAME) + padL(qty, C_QTY) + padL(rate, C_RATE) + padL(amt, C_AMT);
}

/**
 * Render a ReceiptModel as ESC/POS bytes for the thermal printer.
 * Mirrors the HTML receipt section-for-section.
 */
export function buildInvoiceEscPos(m: ReceiptModel): Uint8Array {
    const b = new EscPosBuilder();
    b.init();

    // ===== HEADER =====
    b.align(1);
    b.bold(true).line(m.company.name).bold(false);
    for (const ln of wrap(m.company.address)) b.line(ln);
    b.line("Ph: " + m.company.phone);
    b.line("GSTIN: " + m.company.gstNo);

    b.align(0).line(ruleHeavy());
    b.align(1).bold(true).line("TAX INVOICE").bold(false);
    b.line("[ " + m.billBadge + " ]");
    b.align(0).line(ruleDashed());

    // ===== BILL META =====
    b.line(lr("Bill No", m.billNo));
    b.line(lr("Date", m.dateStr));
    b.line(lr("Shift", m.shiftId));
    b.line(lr("Cashier", m.cashierLabel));
    b.line(lr("Reverse Charge", m.reverseChargeLabel));
    b.line(ruleDashed());

    // ===== CUSTOMER / VEHICLE =====
    b.line(lr("Customer", m.customerName));
    if (m.customerPhone) b.line(lr("Phone", m.customerPhone));
    if (m.customerGST) b.line(lr("GST", m.customerGST));
    if (m.vehicleNo) b.line(lr("Vehicle", m.vehicleNo));
    if (m.isNamedCustomer) b.line(lr("Odometer", m.odometerDisplay));
    if (m.showIndent) b.line(lr("Indent", m.indentNo));

    if (m.hasB2B) {
        b.line(ruleDashed());
        if (m.buyersOrder) {
            b.line(lr("Buyer Order", m.buyersOrder + (m.buyersOrderDate ? " / " + m.buyersOrderDate : "")));
        }
        if (m.supplierRef) b.line(lr("Supplier Ref", m.supplierRef));
        if (m.paymentDetails) b.line(lr("Payment", m.paymentDetails));
    }

    b.line(ruleHeavy());

    // ===== ITEMS =====
    b.bold(true).line(itemRow("ITEM", "QTY", "RATE", "AMOUNT")).bold(false);
    b.line(ruleDashed());
    for (const it of m.items) {
        if (it.name.length <= C_NAME) {
            b.line(itemRow(it.name, it.qty, it.unitRate, it.amt));
        } else {
            // Long name on its own wrapped line(s), figures on the next row.
            for (const ln of wrap(it.name)) b.line(ln);
            b.line(itemRow("", it.qty, it.unitRate, it.amt));
        }
        b.line("  HSN: " + it.hsn + " | GST: " + it.gstTag);
        if (it.nozzleName) b.line("  Nozzle: " + it.nozzleName);
        if (it.discount) b.line("  Discount: -" + it.discount);
    }
    b.line(ruleDashed());

    // ===== TOTALS =====
    b.line(lr("Sub Total", m.subTotal));
    if (m.totalDiscount > 0) b.line(lr("Discount", "-" + m.totalDiscountStr));

    b.line(ruleDashed());
    b.bold(true).line("TAX SUMMARY").bold(false);
    if (m.hasGst) {
        for (const bk of m.taxBuckets) {
            b.line(lr(bk.label, bk.taxable));
            if (!bk.isFuel) {
                b.line(lr("  CGST " + halfRateLabel(bk.rate) + "%", bk.cgst));
                b.line(lr("  SGST " + halfRateLabel(bk.rate) + "%", bk.sgst));
            }
        }
    } else {
        b.line(lr("Taxable Value (Outside GST/Fuel)", m.totalTaxable));
    }
    b.line(ruleDashed());
    b.bold(true);
    b.line(lr("Total Taxable", m.totalTaxable));
    b.line(lr("Total GST", m.totalGst));
    b.bold(false);

    // ===== GRAND TOTAL =====
    b.line(ruleHeavy());
    b.bigBold(true);
    b.line(lr("TOTAL Rs.", m.netAmountStr));
    b.bigBold(false);
    b.line(ruleHeavy());

    for (const ln of wrap(m.inWords)) b.line(ln);

    // ===== SIGNATURE & FOOTER =====
    if (m.isNamedCustomer) {
        b.line().line().line().line();
        b.align(1).line(center("------------------------------").trim());
        b.line("Customer Signature").align(0);
    }

    b.align(1);
    b.line().bold(true).line("* THANK YOU *").bold(false);
    b.line("Computer-generated invoice");
    b.align(0);

    b.feedCut();
    return b.toBytes();
}
