import type { ReceiptModel } from "@/lib/invoice-print";

// ---------------------------------------------------------------------------
// ESC/POS receipt renderer for the TVS RP3150 STAR (USB thermal).
//
// The receipt content is taken verbatim from the shared ReceiptModel, so it
// never diverges from the HTML (browser-fallback) bill. Output is raw printer
// bytes; the local print agent relays them to the printer untouched.
//
// WIDTH = characters per line. The TVS RP3150 at this site wraps at 32 (Font A,
// its configured print width), so the layout is a clean single column at 32.
// If a printer is later set to full 80mm / 48-col mode, bump WIDTH to 48 —
// every layout helper is WIDTH-driven, so nothing else needs changing.
//
// All model strings are already ASCII-safe (asciiSafe() upstream), so a plain
// char-code encode matches the printer's single-byte codepage exactly.
// ---------------------------------------------------------------------------

const WIDTH = 32;

const ESC = 0x1b;
const GS = 0x1d;

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

    /** Append an ASCII string (no newline). Non-ASCII is replaced with '?'. */
    text(s: string): this {
        for (let i = 0; i < s.length; i++) {
            const c = s.charCodeAt(i);
            this.buf.push(c >= 0x20 && c <= 0x7e ? c : 0x3f);
        }
        return this;
    }

    line(s = ""): this {
        return this.text(s).raw(0x0a);
    }

    /** Emit one or more lines, hard-wrapped so nothing exceeds WIDTH. */
    wrapped(s: string): this {
        for (const ln of wrap(s)) this.line(ln);
        return this;
    }

    init(): this {
        return this.raw(ESC, 0x40);          // ESC @  — reset
    }

    align(n: 0 | 1 | 2): this {
        return this.raw(ESC, 0x61, n);       // ESC a n
    }

    bold(on: boolean): this {
        return this.raw(ESC, 0x45, on ? 1 : 0); // ESC E n
    }

    /**
     * Double-height + emphasized via ESC ! (Select print mode) — the most
     * universally supported size command. Bit 4 (0x10) = double height,
     * bit 3 (0x08) = emphasized. Width stays x1 so WIDTH math still holds.
     */
    bigBold(on: boolean): this {
        return this.raw(ESC, 0x21, on ? 0x18 : 0x00); // ESC ! n
    }

    feedCut(): this {
        return this.raw(0x0a, 0x0a, 0x0a, 0x0a).raw(GS, 0x56, 0x01); // feed + GS V 1
    }

    toBytes(): Uint8Array {
        return Uint8Array.from(this.buf);
    }
}

// --- string layout helpers (all WIDTH-bounded) -----------------------------

function center(s: string): string {
    if (s.length >= WIDTH) return s.slice(0, WIDTH);
    return " ".repeat(Math.floor((WIDTH - s.length) / 2)) + s;
}

/** Left text / right text on one line, padded to WIDTH. Left truncates. */
function lr(left: string, right: string): string {
    const space = WIDTH - right.length;
    if (space <= 1) return (left + " " + right).slice(0, WIDTH);
    let l = left;
    if (l.length > space - 1) l = l.slice(0, space - 1);
    return l + " ".repeat(WIDTH - l.length - right.length) + right;
}

function padL(s: string): string {
    return s.length >= WIDTH ? s.slice(s.length - WIDTH) : " ".repeat(WIDTH - s.length) + s;
}

function ruleDashed(): string { return "-".repeat(WIDTH); }
function ruleHeavy(): string { return "=".repeat(WIDTH); }

/** Word-wrap to WIDTH, hard-splitting any token longer than WIDTH. */
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

/**
 * A label and a (numeric) value. If they fit on one line, lr() them; if the
 * label is too long, put the label on its own wrapped line(s) and the value
 * right-aligned underneath. Keeps every line within WIDTH — no printer wrap.
 */
function labelAmount(b: EscPosBuilder, label: string, amount: string): void {
    if (label.length + 1 + amount.length <= WIDTH) {
        b.line(lr(label, amount));
    } else {
        for (const ln of wrap(label)) b.line(ln);
        b.line(padL(amount));
    }
}

/**
 * Render a ReceiptModel as ESC/POS bytes. Single-column, 32-col-safe layout
 * that mirrors the HTML receipt section-for-section.
 */
export function buildInvoiceEscPos(m: ReceiptModel): Uint8Array {
    const b = new EscPosBuilder();
    b.init();

    // ===== HEADER =====
    b.align(1);
    b.bold(true).wrapped(m.company.name).bold(false);
    b.wrapped(m.company.address);
    b.wrapped("Ph: " + m.company.phone);
    b.wrapped("GSTIN: " + m.company.gstNo);

    b.align(0).line(ruleHeavy());
    b.align(1).bold(true).line("TAX INVOICE").bold(false);
    b.line("[ " + m.billBadge + " ]");
    b.align(0).line(ruleDashed());

    // ===== BILL META =====
    labelAmount(b, "Bill No", m.billNo);
    labelAmount(b, "Date", m.dateStr);
    labelAmount(b, "Shift", m.shiftId);
    labelAmount(b, "Cashier", m.cashierLabel);
    labelAmount(b, "Rev. Charge", m.reverseChargeLabel);
    b.line(ruleDashed());

    // ===== CUSTOMER / VEHICLE =====
    labelAmount(b, "Customer", m.customerName);
    if (m.customerPhone) labelAmount(b, "Phone", m.customerPhone);
    if (m.customerGST) labelAmount(b, "GST", m.customerGST);
    if (m.vehicleNo) labelAmount(b, "Vehicle", m.vehicleNo);
    if (m.isNamedCustomer) labelAmount(b, "Odometer", m.odometerDisplay);
    if (m.showIndent) labelAmount(b, "Indent", m.indentNo);

    if (m.hasB2B) {
        b.line(ruleDashed());
        if (m.buyersOrder) {
            labelAmount(b, "Buyer Order",
                m.buyersOrder + (m.buyersOrderDate ? " / " + m.buyersOrderDate : ""));
        }
        if (m.supplierRef) labelAmount(b, "Supplier Ref", m.supplierRef);
        if (m.paymentDetails) labelAmount(b, "Payment", m.paymentDetails);
    }

    b.line(ruleHeavy());

    // ===== ITEMS (stacked: name / qty x rate .. amount / HSN) =============
    for (const it of m.items) {
        b.bold(true).wrapped(it.name).bold(false);
        b.line(lr(`  ${it.qty} x ${it.unitRate}`, it.amt));
        b.wrapped(`  HSN: ${it.hsn}  GST: ${it.gstTag}`);
        if (it.nozzleName) b.wrapped("  Nozzle: " + it.nozzleName);
        if (it.discount) b.line(lr("  Discount", "-" + it.discount));
    }
    b.line(ruleDashed());

    // ===== TOTALS =====
    labelAmount(b, "Sub Total", m.subTotal);
    if (m.totalDiscount > 0) labelAmount(b, "Discount", "-" + m.totalDiscountStr);

    b.line(ruleDashed());
    b.bold(true).line("TAX SUMMARY").bold(false);
    if (m.hasGst) {
        for (const bk of m.taxBuckets) {
            labelAmount(b, bk.label, bk.taxable);
            if (!bk.isFuel) {
                b.line(lr("  CGST " + halfRateLabel(bk.rate) + "%", bk.cgst));
                b.line(lr("  SGST " + halfRateLabel(bk.rate) + "%", bk.sgst));
            }
        }
    } else {
        labelAmount(b, "Taxable (Outside GST/Fuel)", m.totalTaxable);
    }
    b.line(ruleDashed());
    b.bold(true);
    labelAmount(b, "Total Taxable", m.totalTaxable);
    labelAmount(b, "Total GST", m.totalGst);
    b.bold(false);

    // ===== GRAND TOTAL =====
    b.line(ruleHeavy());
    b.bigBold(true);
    b.line(lr("TOTAL Rs.", m.netAmountStr));
    b.bigBold(false);
    b.line(ruleHeavy());

    b.wrapped(m.inWords);

    // ===== SIGNATURE & FOOTER =====
    if (m.isNamedCustomer) {
        b.line().line().line();
        b.align(1).line("------------------------");
        b.line("Customer Signature").align(0);
    }

    b.align(1);
    b.line().bold(true).line("* THANK YOU *").bold(false);
    b.line("Computer-generated invoice");
    b.align(0);

    b.feedCut();
    return b.toBytes();
}
