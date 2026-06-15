import { InvoiceBill } from "@/lib/api/station";
import { buildInvoiceRaster } from "@/lib/escpos-raster";
import { generateDotMatrixEscP } from "@/lib/escp-dotmatrix";
import { sendToPrintAgent, probePrintAgent } from "@/lib/print-agent";

// Number to words (Indian system)
export function numberToWords(num: number): string {
    if (num === 0) return "Zero";
    const ones = ["", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
        "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen",
        "Seventeen", "Eighteen", "Nineteen"];
    const tens = ["", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"];

    const convert = (n: number): string => {
        if (n < 20) return ones[n];
        if (n < 100) return tens[Math.floor(n / 10)] + (n % 10 ? " " + ones[n % 10] : "");
        if (n < 1000) return ones[Math.floor(n / 100)] + " Hundred" + (n % 100 ? " and " + convert(n % 100) : "");
        if (n < 100000) return convert(Math.floor(n / 1000)) + " Thousand" + (n % 1000 ? " " + convert(n % 1000) : "");
        if (n < 10000000) return convert(Math.floor(n / 100000)) + " Lakh" + (n % 100000 ? " " + convert(n % 100000) : "");
        return convert(Math.floor(n / 10000000)) + " Crore" + (n % 10000000 ? " " + convert(n % 10000000) : "");
    };

    const rupees = Math.floor(num);
    const paise = Math.round((num - rupees) * 100);
    let result = "Rupees " + convert(rupees);
    if (paise > 0) result += " and " + convert(paise) + " Paise";
    return result + " Only";
}

export function formatCurrency(val: number | undefined | null): string {
    if (val == null) return "0.00";
    return val.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

// Thermal print drivers render UTF-8 via the OS print pipeline, but ₹ glyph
// is missing from some Courier fallbacks so we still substitute "Rs.".
// Smart-quotes / em-dashes get normalized for consistent rendering on the
// monochrome printhead. The ESC/POS path also relies on this — the TVS RP3150
// printhead is a strict single-byte ASCII codepage.
export function asciiSafe(s: string | undefined | null): string {
    if (!s) return "";
    return s
        .replace(/[–—]/g, "-")
        .replace(/[‘’]/g, "'")
        .replace(/[“”]/g, '"')
        .replace(/₹/g, "Rs.")
        .replace(/[^\x20-\x7E]/g, "");
}

function formatDate(iso: string): string {
    const d = new Date(iso);
    return d.toLocaleString("en-IN", {
        day: "2-digit", month: "2-digit", year: "numeric",
        hour: "2-digit", minute: "2-digit", hour12: true,
    });
}

function formatDateOnly(iso: string): string {
    const d = new Date(iso);
    if (isNaN(d.getTime())) return asciiSafe(iso);
    return d.toLocaleDateString("en-IN", { day: "2-digit", month: "2-digit", year: "numeric" });
}

const r2 = (n: number) => Math.round(n * 100) / 100;

// GST treatment: fuel (petrol/diesel/premium) is OUTSIDE GST in India — VAT-inclusive,
// no CGST/SGST. Non-fuel (lubes/accessories) carries GST; the line price is treated as
// tax-inclusive and split CGST = SGST = tax/2 (intra-state). Mirrors VatReportService:
// taxable = amount * 100 / (100 + rate). Category compare is case-insensitive because
// the products page writes "Fuel"/"Non-Fuel" while the entity default is "FUEL".
function computeLineTax(amount: number, category: string | undefined, gstRate: number | undefined) {
    const isFuel = (category || "").toLowerCase() === "fuel";
    const rate = isFuel ? 0 : (Number(gstRate) || 0);
    let taxable: number;
    let tax: number;
    if (isFuel || rate <= 0) {
        taxable = amount;
        tax = 0;
    } else {
        taxable = r2(amount * 100 / (100 + rate));
        tax = r2(amount - taxable);
    }
    const cgst = r2(tax / 2);
    const sgst = r2(tax - cgst);
    return { isFuel, rate, taxable, tax, cgst, sgst };
}

export function halfRateLabel(rate: number): string {
    const h = rate / 2;
    return Number.isInteger(h) ? String(h) : h.toFixed(2);
}

export interface CompanyInfo {
    name: string;
    address: string;
    phone: string;
    gstNo: string;
    site?: string;
}

// ---------------------------------------------------------------------------
// Shared receipt view-model.
//
// Single source of truth for what goes on a printed bill. BOTH the HTML
// renderer (browser fallback) and the ESC/POS renderer (thermal print agent)
// consume this — so a tax invoice can never differ between the two media.
// Pure data only; no markup, no escaping, no ESC/POS here.
// ---------------------------------------------------------------------------
export interface ReceiptItem {
    name: string;
    qty: string;        // 2-decimal string
    unitRate: string;   // 2-decimal string
    amt: string;        // formatted currency
    hsn: string;
    gstTag: string;
    nozzleName: string;
    discount: string;   // formatted currency, "" when none
}

export interface ReceiptTaxBucket {
    label: string;
    isFuel: boolean;
    rate: number;
    taxable: string;
    cgst: string;
    sgst: string;
}

export interface ReceiptModel {
    company: CompanyInfo;
    billBadge: string;          // CASH | CREDIT
    // meta
    billNo: string;
    dateStr: string;
    shiftId: string;
    cashierLabel: string;
    reverseChargeLabel: string;
    // customer / vehicle
    customerName: string;
    customerPhone: string;
    customerGST: string;
    vehicleNo: string;
    isNamedCustomer: boolean;
    odometerDisplay: string;
    showOdometer: boolean;
    showIndent: boolean;
    indentNo: string;
    // B2B refs
    buyersOrder: string;
    buyersOrderDate: string;
    supplierRef: string;
    paymentDetails: string;
    hasB2B: boolean;
    // items + money
    items: ReceiptItem[];
    subTotal: string;
    totalDiscount: number;
    totalDiscountStr: string;
    taxBuckets: ReceiptTaxBucket[];
    hasGst: boolean;
    totalTaxable: string;
    totalGst: string;
    netAmount: number;
    netAmountStr: string;
    inWords: string;
}

export function buildReceiptModel(invoice: InvoiceBill, company: CompanyInfo): ReceiptModel {
    const isCash = invoice.billType === "CASH";

    // Some legacy rows persist the literal strings "null"/"undefined" in free-text
    // fields (signatory, vehicle desc, driver). Treat those as empty so they never
    // print on the bill — plain "|| fallback" lets the truthy string "null" through.
    const clean = (s?: string | null) => {
        const t = (s ?? "").trim();
        return /^(null|undefined)$/i.test(t) ? "" : t;
    };

    const customerName = clean(invoice.customer?.name) || clean(invoice.signatoryName) || "Walk-in Customer";
    const customerPhone = clean(invoice.signatoryCellNo);
    const customerGST = invoice.customer?.partyType === "COMPANY" ? clean(invoice.customerGST) : "";
    const isNamedCustomer = !!invoice.customer?.id;

    const vehicleNo = clean(invoice.vehicle?.vehicleNumber) || clean(invoice.billDesc);

    const products = invoice.products || [];
    const totalDiscount = invoice.totalDiscount || 0;
    const subTotal = invoice.grossAmount || ((invoice.netAmount || 0) + totalDiscount);
    const netAmount = invoice.netAmount || 0;

    // Cashier display cascade: name -> username -> "#<id>" -> "-".
    const cashierLabel = asciiSafe(invoice.raisedBy?.name)
        || asciiSafe(invoice.raisedBy?.username)
        || (invoice.raisedBy?.id ? `#${invoice.raisedBy.id}` : "-");

    // Odometer is captured on both the walk-in cash flow and named-customer credit
    // flow, so gate the printed row on the value itself — not on isNamedCustomer
    // (walk-ins have no customer.id and would otherwise drop the reading silently).
    const showOdometer = !!invoice.vehicleKM;
    const odometerDisplay = showOdometer ? `${invoice.vehicleKM!.toLocaleString("en-IN")} km` : "-";

    const indentTrim = (invoice.indentNo || "").trim();
    const showIndent = indentTrim !== "" && indentTrim !== "0";

    // Per-line GST split (drives both the HSN sub-line and the tax summary buckets).
    const lines = products.map((p) => {
        const amount = Number(p.amount) || 0;
        const t = computeLineTax(amount, p.category, p.gstRate);
        return { p, amount, ...t };
    });

    // Tax buckets: one "fuel" bucket (outside GST) + one per non-fuel GST rate.
    const bucketMap = new Map<string, { label: string; order: number; rate: number; isFuel: boolean; taxable: number; cgst: number; sgst: number; gross: number }>();
    for (const ln of lines) {
        const key = ln.isFuel ? "fuel" : `g${ln.rate}`;
        let b = bucketMap.get(key);
        if (!b) {
            b = ln.isFuel
                ? { label: "Outside GST (Fuel)", order: 0, rate: 0, isFuel: true, taxable: 0, cgst: 0, sgst: 0, gross: 0 }
                : { label: `Taxable @${ln.rate}%`, order: 1 + ln.rate, rate: ln.rate, isFuel: false, taxable: 0, cgst: 0, sgst: 0, gross: 0 };
            bucketMap.set(key, b);
        }
        b.taxable = r2(b.taxable + ln.taxable);
        b.cgst = r2(b.cgst + ln.cgst);
        b.sgst = r2(b.sgst + ln.sgst);
        b.gross = r2(b.gross + ln.amount);
    }
    const buckets = Array.from(bucketMap.values()).sort((a, b) => a.order - b.order);
    const totalTaxable = r2(buckets.reduce((s, b) => s + b.taxable, 0));
    const totalGst = r2(buckets.reduce((s, b) => s + b.cgst + b.sgst, 0));
    const hasGst = buckets.some((b) => !b.isFuel && (b.cgst + b.sgst) > 0);

    const items: ReceiptItem[] = lines.map(({ p, isFuel, rate }) => ({
        name: asciiSafe(p.productName) || "Product",
        qty: (p.quantity ?? 0).toFixed(2),
        unitRate: (p.unitPrice ?? 0).toFixed(2),
        amt: formatCurrency(p.amount),
        hsn: asciiSafe(p.hsnCode) || "-",
        gstTag: isFuel ? "Nil (Fuel)" : (rate > 0 ? `${rate}%` : "Nil"),
        nozzleName: p.nozzleName ? asciiSafe(p.nozzleName) : "",
        discount: (p.discountAmount && p.discountAmount > 0) ? formatCurrency(p.discountAmount) : "",
    }));

    const buyersOrder = asciiSafe(invoice.buyersOrderNo || "").trim();
    const buyersOrderDate = invoice.buyersOrderDate ? formatDateOnly(invoice.buyersOrderDate) : "";
    const supplierRef = asciiSafe(invoice.supplierRefNo || "").trim();
    const paymentDetails = asciiSafe(invoice.paymentDetails || "").trim();

    const taxBuckets: ReceiptTaxBucket[] = buckets.map((b) => ({
        label: b.label,
        isFuel: b.isFuel,
        rate: b.rate,
        taxable: formatCurrency(b.taxable),
        cgst: formatCurrency(b.cgst),
        sgst: formatCurrency(b.sgst),
    }));

    return {
        company: {
            name: asciiSafe(company.name),
            address: asciiSafe(company.address),
            phone: asciiSafe(company.phone),
            gstNo: asciiSafe(company.gstNo),
            site: company.site,
        },
        billBadge: isCash ? "CASH" : "CREDIT",
        billNo: asciiSafe(invoice.billNo) || "-",
        dateStr: invoice.date ? formatDate(invoice.date) : "-",
        shiftId: `#${invoice.shiftId || "-"}`,
        cashierLabel,
        reverseChargeLabel: invoice.reverseCharge ? "Yes" : "No",
        customerName: asciiSafe(customerName),
        customerPhone: asciiSafe(customerPhone),
        customerGST: asciiSafe(customerGST),
        vehicleNo: asciiSafe(vehicleNo),
        isNamedCustomer,
        odometerDisplay,
        showOdometer,
        showIndent,
        indentNo: asciiSafe(invoice.indentNo),
        buyersOrder,
        buyersOrderDate,
        supplierRef,
        paymentDetails,
        hasB2B: !!(buyersOrder || supplierRef || paymentDetails),
        items,
        subTotal: formatCurrency(subTotal),
        totalDiscount,
        totalDiscountStr: formatCurrency(totalDiscount),
        taxBuckets,
        hasGst,
        totalTaxable: formatCurrency(totalTaxable),
        totalGst: formatCurrency(totalGst),
        netAmount,
        netAmountStr: formatCurrency(netAmount),
        inWords: asciiSafe(numberToWords(netAmount)),
    };
}

export function generateInvoiceHTML(invoice: InvoiceBill, company: CompanyInfo): string {
    const m = buildReceiptModel(invoice, company);

    // 4-column item row: Item / Qty / Rate / Amount, with an HSN + GST-rate sub-line.
    const itemsHtml = m.items.map((it) => {
        const hsnSub = `<div class="isub">HSN: ${it.hsn} | GST: ${it.gstTag}</div>`;
        const nozzleSub = it.nozzleName ? `<div class="isub">Nozzle: ${it.nozzleName}</div>` : "";
        const discSub = it.discount ? `<div class="isub">Discount: -${it.discount}</div>` : "";
        return `<div class="irow">
            <span class="ic-name">${it.name}</span>
            <span class="ic-qty">${it.qty}</span>
            <span class="ic-rate">${it.unitRate}</span>
            <span class="ic-amt">${it.amt}</span>
        </div>${hsnSub}${nozzleSub}${discSub}`;
    }).join("");

    const taxSummaryHtml = m.hasGst
        ? m.taxBuckets.map((b) => {
            if (b.isFuel) {
                return `<div class="row"><span>${b.label}</span><span>${b.taxable}</span></div>`;
            }
            return `<div class="row"><span>${b.label}</span><span>${b.taxable}</span></div>
        <div class="row sub"><span>CGST ${halfRateLabel(b.rate)}%</span><span>${b.cgst}</span></div>
        <div class="row sub"><span>SGST ${halfRateLabel(b.rate)}%</span><span>${b.sgst}</span></div>`;
        }).join("")
        : `<div class="row"><span>Taxable Value (Outside GST/Fuel)</span><span>${m.totalTaxable}</span></div>`;

    return `<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<title>Invoice ${invoice.billNo || ""}</title>
<style>
    /* TVS RP3150 STAR — 80mm thermal roll, ~72mm printable width, 203 dpi continuous.
       Page width pinned to 72mm; height left as 'auto' so the receipt grows with content
       and the auto-cutter advances correctly on the next bill. */
    @page { size: 72mm auto; margin: 0; }
    @media print { body { -webkit-print-color-adjust: exact; print-color-adjust: exact; } }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    html, body { width: 72mm; }
    body {
        font-family: 'Courier New', Courier, monospace;
        font-size: 9pt;
        font-weight: 700;
        line-height: 1.25;
        color: #000;
        background: #fff;
        padding: 2mm 3mm 4mm 3mm;
    }
    .center { text-align: center; }
    .right { text-align: right; }
    .row { display: flex; justify-content: space-between; gap: 4px; }
    .row span:last-child { text-align: right; }

    /* Header */
    .company { font-size: 12pt; font-weight: 900; letter-spacing: 0.3px; line-height: 1.15; margin-bottom: 1mm; }
    .addr { font-size: 8pt; font-weight: 700; line-height: 1.3; }

    /* Dividers. line-height/font-size 0 so the empty rule div carries no stray
       line box — html2canvas (the thermal raster path) would otherwise render
       that box overlapping the text row above it. Clearance is provided by the
       section blocks below (.meta/.tot margins), which the rasterizer respects
       even when it drops an empty element's own margin. */
    .rule-d, .rule-s, .rule-h { line-height: 0; font-size: 0; }
    .rule-d { border-top: 1px dashed #000; margin: 1.6mm 0; }
    .rule-s { border-top: 1px solid #000; margin: 1.6mm 0; }
    .rule-h { border-top: 2px solid #000; margin: 1.6mm 0; }

    /* Title strip */
    .title { font-size: 11pt; font-weight: 900; letter-spacing: 1.5px; margin: 0.5mm 0; }
    /* Bill-type box. Rendered by html2canvas (thermal raster), which is fussy
       about bordered inline-blocks:
         - integer 2px border (sub-pixel 1.5px rendered ragged, like the dividers);
         - line-height 1.1 + symmetric vertical padding so the frame clears the
           glyphs instead of clipping against them at the line-box edge;
         - text-indent balances the trailing letter-spacing (which otherwise adds
           a gap only on the right and makes CASH/CREDIT look off-centre);
         - small top+bottom margin keeps the box off the line above/below. */
    .badge { display: inline-block; border: 2px solid #000; padding: 1mm 3mm 1.2mm; font-size: 9pt; font-weight: 900; letter-spacing: 1px; text-indent: 1px; line-height: 1.1; margin: 1.2mm 0 0.5mm; }

    /* Label : value pairs */
    .meta { margin: 0 0 1.6mm; }
    .meta .row { font-size: 9pt; font-weight: 700; }
    .meta .lbl { white-space: nowrap; }
    .meta .val { font-weight: 900; word-break: break-word; }

    /* Item block — 4-column row: name (flex) / qty / rate / amount */
    .ihdr, .irow { display: flex; align-items: baseline; gap: 1mm; }
    .ihdr { font-size: 8pt; font-weight: 900; text-transform: uppercase; letter-spacing: 0.3px; }
    .irow { font-size: 9pt; font-weight: 900; margin: 0.4mm 0; }
    .taxh { font-size: 8pt; font-weight: 900; text-transform: uppercase; letter-spacing: 0.5px; margin: 0.6mm 0; }
    .ic-name { flex: 1 1 auto; min-width: 0; word-break: break-word; }
    .ic-qty { width: 11mm; text-align: right; flex-shrink: 0; }
    .ic-rate { width: 14mm; text-align: right; flex-shrink: 0; }
    .ic-amt { width: 18mm; text-align: right; flex-shrink: 0; }
    .isub { font-size: 7.5pt; font-weight: 700; padding-left: 2mm; }

    /* Totals */
    .tot { margin: 1.4mm 0; }
    .tot .row { font-size: 9.5pt; font-weight: 700; }
    .tot .row.bold { font-weight: 900; }
    .tot .row.sub { font-size: 8.5pt; font-weight: 700; padding-left: 3mm; }
    .grand-wrap { margin: 2mm 0 1mm; padding: 1.4mm 0; border-top: 2px solid #000; border-bottom: 3px double #000; }
    .grand { display: flex; justify-content: space-between; gap: 6px; font-size: 12pt; font-weight: 900; letter-spacing: 0.3px; }
    .in-words { font-size: 8pt; font-weight: 700; line-height: 1.3; margin-top: 0.8mm; font-style: italic; }

    /* Signature & footer */
    .sign { margin-top: 15mm; border-top: 1px solid #000; padding-top: 0.6mm; text-align: center; font-size: 8pt; font-weight: 700; }
    .thanks { margin-top: 2mm; font-size: 9.5pt; font-weight: 900; letter-spacing: 1.5px; }
    .gen { font-size: 7.5pt; font-weight: 700; margin-top: 0.6mm; }
</style>
</head>
<body>

<!-- ===== HEADER ===== -->
<div class="center">
    <div class="company">${m.company.name}</div>
    <div class="addr">${m.company.address}</div>
    <div class="addr">Ph: ${m.company.phone}</div>
    <div class="addr">GSTIN: ${m.company.gstNo}</div>
</div>

<div class="rule-h"></div>
<div class="center title">TAX INVOICE</div>
<div class="center"><span class="badge">${m.billBadge}</span></div>
<div class="rule-d"></div>

<!-- ===== BILL META ===== -->
<div class="meta">
    <div class="row"><span class="lbl">Bill No</span><span class="val">${m.billNo}</span></div>
    <div class="row"><span class="lbl">Date</span><span class="val">${m.dateStr}</span></div>
    <div class="row"><span class="lbl">Shift</span><span class="val">${m.shiftId}</span></div>
    <div class="row"><span class="lbl">Cashier</span><span class="val">${m.cashierLabel}</span></div>
    <div class="row"><span class="lbl">Reverse Charge</span><span class="val">${m.reverseChargeLabel}</span></div>
</div>

<div class="rule-d"></div>

<!-- ===== CUSTOMER / VEHICLE ===== -->
<div class="meta">
    <div class="row"><span class="lbl">Customer</span><span class="val">${m.customerName}</span></div>
    ${m.customerPhone ? `<div class="row"><span class="lbl">Phone</span><span class="val">${m.customerPhone}</span></div>` : ""}
    ${m.customerGST ? `<div class="row"><span class="lbl">GST</span><span class="val">${m.customerGST}</span></div>` : ""}
    ${m.vehicleNo ? `<div class="row"><span class="lbl">Vehicle</span><span class="val">${m.vehicleNo}</span></div>` : ""}
    ${m.showOdometer ? `<div class="row"><span class="lbl">Odometer</span><span class="val">${m.odometerDisplay}</span></div>` : ""}
    ${m.showIndent ? `<div class="row"><span class="lbl">Indent</span><span class="val">${m.indentNo}</span></div>` : ""}
</div>

${m.hasB2B ? `<div class="rule-d"></div>
<div class="meta">
    ${m.buyersOrder ? `<div class="row"><span class="lbl">Buyer Order</span><span class="val">${m.buyersOrder}${m.buyersOrderDate ? " / " + m.buyersOrderDate : ""}</span></div>` : ""}
    ${m.supplierRef ? `<div class="row"><span class="lbl">Supplier Ref</span><span class="val">${m.supplierRef}</span></div>` : ""}
    ${m.paymentDetails ? `<div class="row"><span class="lbl">Payment</span><span class="val">${m.paymentDetails}</span></div>` : ""}
</div>` : ""}

<div class="rule-h"></div>

<!-- ===== ITEMS ===== -->
<div class="ihdr">
    <span class="ic-name">Item</span>
    <span class="ic-qty">Qty</span>
    <span class="ic-rate">Rate</span>
    <span class="ic-amt">Amount</span>
</div>
<div class="rule-d"></div>
${itemsHtml}
<div class="rule-d"></div>

<!-- ===== TOTALS ===== -->
<div class="tot">
    <div class="row"><span>Sub Total</span><span>${m.subTotal}</span></div>
    ${m.totalDiscount > 0 ? `<div class="row"><span>Discount</span><span>-${m.totalDiscountStr}</span></div>` : ""}
</div>

<div class="rule-d"></div>
<div class="taxh">Tax Summary</div>
<div class="tot">
    ${taxSummaryHtml}
    <div class="rule-s"></div>
    <div class="row bold"><span>Total Taxable</span><span>${m.totalTaxable}</span></div>
    <div class="row bold"><span>Total GST</span><span>${m.totalGst}</span></div>
</div>

<div class="grand-wrap">
    <div class="grand"><span>TOTAL Rs.</span><span>${m.netAmountStr}</span></div>
</div>

<div class="in-words">${m.inWords}</div>

${m.isNamedCustomer ? `<div class="sign">Customer Signature</div>` : ""}

<div class="center thanks">* THANK YOU *</div>
<div class="center gen">Computer-generated invoice</div>

</body>
</html>`;
}

// ---------------------------------------------------------------------------
// Dot-matrix (TVS MSP 250) layout — 6x4.5" pre-printed slip.
//
// This is the browser-print FALLBACK for the dot-matrix slip, used only when the
// local print agent is unreachable; the primary path is native ESC/P text via
// generateDotMatrixEscP() (lib/escp-dotmatrix.ts), which prints far crisper on
// the 9-pin head. It prints through the OS driver via window.print() and shares
// the SAME buildReceiptModel() as the thermal/ESC-P paths so the media can never
// show different bill data; only the markup/CSS differs.
// ---------------------------------------------------------------------------
export function generateDotMatrixHTML(invoice: InvoiceBill, company: CompanyInfo): string {
    const m = buildReceiptModel(invoice, company);
    const isCash = m.billBadge === "CASH";
    // driverName isn't part of the shared model; read it straight off the invoice.
    // Strip the literal "null"/"undefined" strings legacy rows persist here.
    const driverName = /^(null|undefined)$/i.test((invoice.driverName ?? "").trim())
        ? "" : asciiSafe(invoice.driverName);

    const itemsHtml = m.items.map((it) => {
        const disc = it.discount ? ` (-${it.discount})` : "";
        return `<tr>
            <td style="padding:1px 0;">${it.name}${disc}</td>
            <td style="text-align:center;padding:1px 0;">${it.qty}</td>
            <td style="text-align:center;padding:1px 0;">${it.unitRate}</td>
            <td style="text-align:right;font-weight:bold;padding:1px 0;">${it.amt}</td>
        </tr>`;
    }).join("");

    return `<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<title>Invoice ${m.billNo}</title>
<style>
    /* 6x4.5" fanfold slip on the 9-pin MSP 250. Width is left at 100% of the
       @page content box (page margins do the insetting) so nothing centred can
       spill past the carriage edges. No -webkit-text-stroke: at 900 weight it
       made the dot-matrix glyphs print muddy/doubled. */
    @page { size: 6in 4.5in; margin: 3mm 4mm; }
    @media print { body { -webkit-print-color-adjust: exact; print-color-adjust: exact; } }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body { font-family: 'Courier New', Courier, monospace; font-size: 11pt; font-weight: 900; line-height: 1.15; color: #000; background: #fff; width: 100%; }
    table { width: 100%; border-collapse: collapse; table-layout: fixed; }
    td { vertical-align: top; font-size: 11pt; font-weight: 900; word-break: break-word; }
    .center { text-align: center; }
    .right { text-align: right; }
    .big { font-size: 15pt; font-weight: 900; }
    .xs { font-size: 9pt; font-weight: 900; color: #000; }
    hr { border: none; border-top: 2px solid #000; margin: 2px 0; }
    hr.solid { border-top: 3px solid #000; }
    .badge { display: inline-block; border: 2px solid #000; padding: 0 8px; font-size: 11pt; font-weight: 900; letter-spacing: 1px; }
    .info td { font-size: 10pt; font-weight: 900; padding: 0; }
    .info td:first-child { width: 30%; white-space: nowrap; }
    .info td:last-child { word-break: break-word; }
    .items th { font-size: 11pt; font-weight: 900; border-bottom: 2px solid #000; padding: 2px 0; text-transform: uppercase; }
    .items td { padding: 1px 0; }
    .totals { margin-top: 2px; }
    .totals td { font-size: 12pt; font-weight: 900; padding: 0; }
    .totals .label { text-align: right; padding-right: 8px; }
    .totals .val { text-align: right; width: 28%; }
    .totals .grand td { font-size: 14pt; border-top: 2px solid #000; border-bottom: 3px double #000; padding: 2px 0; }
    .sign-line { margin-top: 14px; border-top: 1px solid #000; text-align: center; }
    .audit { margin-top: 6px; font-size: 10pt; font-weight: 900; display: flex; justify-content: space-between; gap: 6px; }
    .audit span { border-bottom: 1px dotted #000; flex: 1; padding: 0 2px; }
</style>
</head>
<body>

<!-- Header -->
<div class="center">
    <div class="big">${m.company.name}</div>
    <div class="xs">${m.company.address}</div>
    <div class="xs">Ph: ${m.company.phone} | GSTIN: ${m.company.gstNo}</div>
</div>
<hr class="solid">
<div class="center"><span style="font-size:13pt;font-weight:900;">TAX INVOICE</span> <span class="badge">${m.billBadge}</span></div>
<hr>

<!-- Customer on its own full-width line -->
<table class="info"><tr>
    <td style="width:14%;">Customer:</td>
    <td style="text-align:left;">${m.customerName}${m.customerPhone ? ` (${m.customerPhone})` : ""}${m.customerGST ? ` | GST: ${m.customerGST}` : ""}</td>
</tr></table>

<!-- Bill / Vehicle two-column strip -->
<table><tr>
<td style="width:48%;">
    <table class="info">
        <tr><td>Bill:</td><td>${m.billNo}</td></tr>
        <tr><td>Date:</td><td>${m.dateStr}</td></tr>
        <tr><td>Shift:</td><td>${m.shiftId}</td></tr>
    </table>
</td>
<td style="width:4%;"></td>
<td style="width:48%;">
    <table class="info">
        ${m.vehicleNo ? `<tr><td>Vehicle:</td><td>${m.vehicleNo}</td></tr>` : ""}
        ${driverName ? `<tr><td>Driver:</td><td>${driverName}</td></tr>` : ""}
        ${m.showIndent ? `<tr><td>Indent:</td><td>${m.indentNo}</td></tr>` : ""}
        <tr><td>Cashier:</td><td>${m.cashierLabel}</td></tr>
    </table>
</td>
</tr></table>
${m.showOdometer ? `<div class="xs right">Odometer: ${m.odometerDisplay}</div>` : ""}
<hr class="solid">

<!-- Items -->
<table class="items">
    <thead><tr>
        <th style="text-align:left;">Product</th>
        <th style="text-align:center;">Qty</th>
        <th style="text-align:center;">Rate</th>
        <th style="text-align:right;">Amount</th>
    </tr></thead>
    <tbody>${itemsHtml}</tbody>
</table>

<!-- Right-aligned totals stack -->
<table class="totals">
    <tr><td class="label">Sub Total</td><td class="val">${m.subTotal}</td></tr>
    ${m.totalDiscount > 0 ? `<tr><td class="label">Discount</td><td class="val">-${m.totalDiscountStr}</td></tr>` : ""}
    <tr class="grand"><td class="label">Total (Rs.)</td><td class="val">${m.netAmountStr}</td></tr>
</table>

${!isCash ? `<div class="sign-line"><span class="xs">Customer Signature</span></div>` : ""}

<div class="audit">
    <span>Pump Reading: </span>
    <span>Nozzle: </span>
    <span>Attendant: </span>
</div>
<div class="xs center" style="margin-top:2px;">Computer-generated invoice.</div>

</body>
</html>`;
}

// Remembered printer choice — which device the cashier last printed an invoice
// to. Mirrors the printAgentUrl override pattern in lib/print-agent.ts.
export type PrinterTarget = "thermal" | "dotmatrix";
const PRINTER_TARGET_KEY = "invoicePrinterTarget";

export function getPrinterTarget(): PrinterTarget {
    if (typeof window !== "undefined") {
        try {
            const v = window.localStorage.getItem(PRINTER_TARGET_KEY);
            if (v === "thermal" || v === "dotmatrix") return v;
        } catch (_) { /* localStorage blocked — use default */ }
    }
    return "thermal";
}

export function setPrinterTarget(t: PrinterTarget): void {
    if (typeof window !== "undefined") {
        try { window.localStorage.setItem(PRINTER_TARGET_KEY, t); } catch (_) { /* ignore */ }
    }
}

// Windows printer name for the MSP 250, used to route dot-matrix jobs to it via
// the agent when the agent's configured default is the thermal printer. Empty →
// the agent uses its configured default. Per-machine, like printAgentUrl.
const DOTMATRIX_PRINTER_KEY = "dotMatrixPrinterName";

export function getDotMatrixPrinter(): string {
    if (typeof window !== "undefined") {
        try { return window.localStorage.getItem(DOTMATRIX_PRINTER_KEY) || ""; } catch (_) { /* ignore */ }
    }
    return "";
}

export function setDotMatrixPrinter(name: string): void {
    if (typeof window !== "undefined") {
        try { window.localStorage.setItem(DOTMATRIX_PRINTER_KEY, name.trim()); } catch (_) { /* ignore */ }
    }
}

// Print an HTML document through the OS print dialog. Used for the dot-matrix
// route (always) and as the thermal fallback when the local print agent is not
// reachable (machine without the agent installed, or agent stopped). Opens a
// popup, writes the markup, triggers window.print(), and auto-closes.
function browserPrint(html: string, popupFeatures: string, preOpened?: Window | null): void {
    const printWindow = preOpened || window.open("", "_blank", popupFeatures);
    if (!printWindow) {
        alert("Please allow popups to print invoices.");
        return;
    }
    printWindow.document.write(html);
    printWindow.document.close();

    // Auto-close the popup once printing finishes. This keeps Chrome
    // --kiosk-printing clean (no window pile-up at the counter); with the
    // normal print dialog it closes after the user prints or cancels.
    printWindow.onafterprint = () => printWindow.close();

    printWindow.onload = () => {
        printWindow.print();
    };
    setTimeout(() => {
        try { printWindow.print(); } catch (_) { /* already printed or closed */ }
    }, 500);
    // Fallback close in case onafterprint never fires (some kiosk setups).
    setTimeout(() => {
        try { if (!printWindow.closed) printWindow.close(); } catch (_) { /* ignore */ }
    }, 5000);
}

// Probe the local print agent once when this module loads (the invoices page
// imports it), so the first Print click already knows whether to go direct.
let agentKnownUp: boolean | null = null;
if (typeof window !== "undefined") {
    probePrintAgent().then((up) => { agentKnownUp = up; }).catch(() => { agentKnownUp = false; });
}

/**
 * Print an invoice to the chosen printer.
 *
 * `target` selects the device:
 *  - "thermal"   → TVS-E RP 3230. On a counter PC with the print agent the HTML
 *                  receipt is rasterized in-browser and pushed as an ESC/POS
 *                  image — one click, no dialog. Where the agent is unreachable
 *                  (dev laptop, agent stopped) it falls back to the browser
 *                  print popup with the same 72mm receipt.
 *  - "dotmatrix" → TVS MSP 250. With the agent up, the 6x4.5" slip is sent as
 *                  native ESC/P text (printer hardware font, char-counted columns)
 *                  to the MSP 250 (getDotMatrixPrinter() names it, else the agent
 *                  default). Where the agent is unreachable it falls back to the
 *                  browser print dialog with the same HTML slip.
 *
 * When `target` is omitted the remembered choice (getPrinterTarget) is used,
 * defaulting to "thermal" — so older call sites keep their original behaviour.
 *
 * The popup must be opened inside the click gesture or the browser blocks it.
 * For the thermal path, when the agent's state is unknown/down we pre-open the
 * popup synchronously; if the agent then turns out to be up we close it again.
 */
export async function printInvoice(
    invoice: InvoiceBill,
    company: CompanyInfo,
    target: PrinterTarget = getPrinterTarget(),
): Promise<void> {
    // Dot-matrix: prefer native ESC/P text through the agent (crisp hardware
    // font, columns counted to the form — no rasterised bitmap). Falls back to
    // the OS print dialog with the HTML slip when the agent is unreachable.
    if (target === "dotmatrix") {
        const dmPrinter = getDotMatrixPrinter();
        if (agentKnownUp === true) {
            try {
                await sendToPrintAgent(generateDotMatrixEscP(invoice, company), `Invoice ${invoice.billNo || ""}`, dmPrinter);
                return;
            } catch (_) {
                agentKnownUp = false; // agent went away — fall through to browser print
            }
        }
        const preOpenedDm = window.open("", "_blank", "width=650,height=900");
        try {
            const up = await probePrintAgent();
            if (up) {
                agentKnownUp = true;
                await sendToPrintAgent(generateDotMatrixEscP(invoice, company), `Invoice ${invoice.billNo || ""}`, dmPrinter);
                try { preOpenedDm?.close(); } catch (_) { /* ignore */ }
                return;
            }
        } catch (_) {
            // ignore — fall back to browser print below
        }
        agentKnownUp = false;
        browserPrint(generateDotMatrixHTML(invoice, company), "width=650,height=900", preOpenedDm);
        return;
    }

    // Thermal. Agent known up → go direct, no popup, no flicker.
    if (agentKnownUp === true) {
        try {
            const bytes = await buildInvoiceRaster(generateInvoiceHTML(invoice, company));
            await sendToPrintAgent(bytes, `Invoice ${invoice.billNo || ""}`);
            return;
        } catch (_) {
            // Agent went away (or raster failed). Fall through to browser print.
            agentKnownUp = false;
        }
    }

    // Agent unknown or down: keep the user-gesture popup alive while we try.
    const preOpened = window.open("", "_blank", "width=420,height=900");
    try {
        const up = await probePrintAgent();
        if (up) {
            agentKnownUp = true;
            const bytes = await buildInvoiceRaster(generateInvoiceHTML(invoice, company));
            await sendToPrintAgent(bytes, `Invoice ${invoice.billNo || ""}`);
            try { preOpened?.close(); } catch (_) { /* ignore */ }
            return;
        }
    } catch (_) {
        // ignore — fall back to browser print below
    }
    agentKnownUp = false;
    browserPrint(generateInvoiceHTML(invoice, company), "width=420,height=900", preOpened);
}
