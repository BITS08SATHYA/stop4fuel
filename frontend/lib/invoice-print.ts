import { InvoiceBill } from "@/lib/api/station";

// Number to words (Indian system)
function numberToWords(num: number): string {
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

function formatCurrency(val: number | undefined | null): string {
    if (val == null) return "0.00";
    return val.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

// Thermal print drivers render UTF-8 via the OS print pipeline, but ₹ glyph
// is missing from some Courier fallbacks so we still substitute "Rs.".
// Smart-quotes / em-dashes get normalized for consistent rendering on the
// monochrome printhead.
function asciiSafe(s: string | undefined | null): string {
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

function halfRateLabel(rate: number): string {
    const h = rate / 2;
    return Number.isInteger(h) ? String(h) : h.toFixed(2);
}

interface CompanyInfo {
    name: string;
    address: string;
    phone: string;
    gstNo: string;
    site?: string;
}

function generateInvoiceHTML(invoice: InvoiceBill, company: CompanyInfo): string {
    const isCash = invoice.billType === "CASH";
    const billBadge = isCash ? "CASH" : "CREDIT";

    const customerName = invoice.customer?.name || invoice.signatoryName || "Walk-in Customer";
    const customerPhone = invoice.signatoryCellNo || "";
    const customerGST = invoice.customer?.partyType === "COMPANY" ? invoice.customerGST : "";
    const isNamedCustomer = !!invoice.customer?.id;

    const vehicleNo = invoice.vehicle?.vehicleNumber || invoice.billDesc || "";

    const products = invoice.products || [];
    const totalDiscount = invoice.totalDiscount || 0;
    const subTotal = invoice.grossAmount || ((invoice.netAmount || 0) + totalDiscount);
    const netAmount = invoice.netAmount || 0;

    // Cashier display cascade: name -> username -> "#<id>" -> "-".
    // Hits the "#<id>" branch when a User exists but PersonEntity.name and User.username
    // are both blank (seeded cashier rows from the early passcode-auth flow).
    const cashierLabel = asciiSafe(invoice.raisedBy?.name)
        || asciiSafe(invoice.raisedBy?.username)
        || (invoice.raisedBy?.id ? `#${invoice.raisedBy.id}` : "-");

    // Odometer is shown on every named-customer bill; "-" when vehicleKM unset/0.
    const odometerDisplay = invoice.vehicleKM ? `${invoice.vehicleKM.toLocaleString("en-IN")} km` : "-";

    // Indent only shows when it has a real value — cashiers sometimes type "0" or
    // leave the default, so filter those out instead of printing "Indent  0".
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

    // 4-column item row: Item / Qty / Rate / Amount, with an HSN + GST-rate sub-line.
    const itemsHtml = lines.map(({ p, isFuel, rate }) => {
        const name = asciiSafe(p.productName) || "Product";
        const qty = (p.quantity ?? 0).toFixed(2);
        const unitRate = (p.unitPrice ?? 0).toFixed(2);
        const amt = formatCurrency(p.amount);
        const hsn = asciiSafe(p.hsnCode) || "-";
        const gstTag = isFuel ? "Nil (Fuel)" : (rate > 0 ? `${rate}%` : "Nil");
        const hsnSub = `<div class="isub">HSN: ${hsn} | GST: ${gstTag}</div>`;
        const nozzleSub = p.nozzleName ? `<div class="isub">Nozzle: ${asciiSafe(p.nozzleName)}</div>` : "";
        const discSub = (p.discountAmount && p.discountAmount > 0)
            ? `<div class="isub">Discount: -${formatCurrency(p.discountAmount)}</div>` : "";
        return `<div class="irow">
            <span class="ic-name">${name}</span>
            <span class="ic-qty">${qty}</span>
            <span class="ic-rate">${unitRate}</span>
            <span class="ic-amt">${amt}</span>
        </div>${hsnSub}${nozzleSub}${discSub}`;
    }).join("");

    // Reverse-charge is statutory on a tax invoice — always print Yes/No.
    const reverseChargeLabel = invoice.reverseCharge ? "Yes" : "No";
    const buyersOrder = asciiSafe(invoice.buyersOrderNo || "").trim();
    const buyersOrderDate = invoice.buyersOrderDate ? formatDateOnly(invoice.buyersOrderDate) : "";
    const supplierRef = asciiSafe(invoice.supplierRefNo || "").trim();
    const paymentDetails = asciiSafe(invoice.paymentDetails || "").trim();
    const hasB2B = !!(buyersOrder || supplierRef || paymentDetails);

    const taxSummaryHtml = hasGst
        ? buckets.map((b) => {
            if (b.isFuel) {
                return `<div class="row"><span>${b.label}</span><span>${formatCurrency(b.taxable)}</span></div>`;
            }
            return `<div class="row"><span>${b.label}</span><span>${formatCurrency(b.taxable)}</span></div>
        <div class="row sub"><span>CGST ${halfRateLabel(b.rate)}%</span><span>${formatCurrency(b.cgst)}</span></div>
        <div class="row sub"><span>SGST ${halfRateLabel(b.rate)}%</span><span>${formatCurrency(b.sgst)}</span></div>`;
        }).join("")
        : `<div class="row"><span>Taxable Value (Outside GST/Fuel)</span><span>${formatCurrency(totalTaxable)}</span></div>`;

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

    /* Dividers */
    .rule-d { border-top: 1px dashed #000; margin: 1.2mm 0; }
    .rule-s { border-top: 1px solid #000; margin: 1.2mm 0; }
    .rule-h { border-top: 2px solid #000; margin: 1.2mm 0; }

    /* Title strip */
    .title { font-size: 11pt; font-weight: 900; letter-spacing: 1.5px; margin: 0.5mm 0; }
    .badge { display: inline-block; border: 1.5px solid #000; padding: 0.3mm 2mm; font-size: 9pt; font-weight: 900; letter-spacing: 1px; margin-top: 0.6mm; }

    /* Label : value pairs */
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
    .tot .row { font-size: 9.5pt; font-weight: 700; }
    .tot .row.bold { font-weight: 900; }
    .tot .row.sub { font-size: 8.5pt; font-weight: 700; padding-left: 3mm; }
    .grand-wrap { margin: 1mm 0; padding: 1mm 0; border-top: 2px solid #000; border-bottom: 3px double #000; }
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
    <div class="company">${asciiSafe(company.name)}</div>
    <div class="addr">${asciiSafe(company.address)}</div>
    <div class="addr">Ph: ${asciiSafe(company.phone)}</div>
    <div class="addr">GSTIN: ${asciiSafe(company.gstNo)}</div>
</div>

<div class="rule-h"></div>
<div class="center title">TAX INVOICE</div>
<div class="center"><span class="badge">${billBadge}</span></div>
<div class="rule-d"></div>

<!-- ===== BILL META ===== -->
<div class="meta">
    <div class="row"><span class="lbl">Bill No</span><span class="val">${asciiSafe(invoice.billNo) || "-"}</span></div>
    <div class="row"><span class="lbl">Date</span><span class="val">${invoice.date ? formatDate(invoice.date) : "-"}</span></div>
    <div class="row"><span class="lbl">Shift</span><span class="val">#${invoice.shiftId || "-"}</span></div>
    <div class="row"><span class="lbl">Cashier</span><span class="val">${cashierLabel}</span></div>
    <div class="row"><span class="lbl">Reverse Charge</span><span class="val">${reverseChargeLabel}</span></div>
</div>

<div class="rule-d"></div>

<!-- ===== CUSTOMER / VEHICLE ===== -->
<div class="meta">
    <div class="row"><span class="lbl">Customer</span><span class="val">${asciiSafe(customerName)}</span></div>
    ${customerPhone ? `<div class="row"><span class="lbl">Phone</span><span class="val">${asciiSafe(customerPhone)}</span></div>` : ""}
    ${customerGST ? `<div class="row"><span class="lbl">GST</span><span class="val">${asciiSafe(customerGST)}</span></div>` : ""}
    ${vehicleNo ? `<div class="row"><span class="lbl">Vehicle</span><span class="val">${asciiSafe(vehicleNo)}</span></div>` : ""}
    ${isNamedCustomer ? `<div class="row"><span class="lbl">Odometer</span><span class="val">${odometerDisplay}</span></div>` : ""}
    ${showIndent ? `<div class="row"><span class="lbl">Indent</span><span class="val">${asciiSafe(invoice.indentNo)}</span></div>` : ""}
</div>

${hasB2B ? `<div class="rule-d"></div>
<div class="meta">
    ${buyersOrder ? `<div class="row"><span class="lbl">Buyer Order</span><span class="val">${buyersOrder}${buyersOrderDate ? " / " + buyersOrderDate : ""}</span></div>` : ""}
    ${supplierRef ? `<div class="row"><span class="lbl">Supplier Ref</span><span class="val">${supplierRef}</span></div>` : ""}
    ${paymentDetails ? `<div class="row"><span class="lbl">Payment</span><span class="val">${paymentDetails}</span></div>` : ""}
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
    <div class="row"><span>Sub Total</span><span>${formatCurrency(subTotal)}</span></div>
    ${totalDiscount > 0 ? `<div class="row"><span>Discount</span><span>-${formatCurrency(totalDiscount)}</span></div>` : ""}
</div>

<div class="rule-d"></div>
<div class="taxh">Tax Summary</div>
<div class="tot">
    ${taxSummaryHtml}
    <div class="rule-s"></div>
    <div class="row bold"><span>Total Taxable</span><span>${formatCurrency(totalTaxable)}</span></div>
    <div class="row bold"><span>Total GST</span><span>${formatCurrency(totalGst)}</span></div>
</div>

<div class="grand-wrap">
    <div class="grand"><span>TOTAL Rs.</span><span>${formatCurrency(netAmount)}</span></div>
</div>

<div class="in-words">${asciiSafe(numberToWords(netAmount))}</div>

${isNamedCustomer ? `<div class="sign">Customer Signature</div>` : ""}

<div class="center thanks">* THANK YOU *</div>
<div class="center gen">Computer-generated invoice</div>

</body>
</html>`;
}

export function printInvoice(invoice: InvoiceBill, company: CompanyInfo): void {
    const html = generateInvoiceHTML(invoice, company);
    const printWindow = window.open("", "_blank", "width=420,height=900");
    if (!printWindow) {
        alert("Please allow popups to print invoices.");
        return;
    }
    printWindow.document.write(html);
    printWindow.document.close();

    printWindow.onload = () => {
        printWindow.print();
    };
    setTimeout(() => {
        try { printWindow.print(); } catch (_) { /* already printed or closed */ }
    }, 500);
}
