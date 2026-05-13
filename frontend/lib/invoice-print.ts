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

    const vehicleNo = invoice.vehicle?.vehicleNumber || invoice.billDesc || "";

    const products = invoice.products || [];
    const totalDiscount = invoice.totalDiscount || 0;
    const subTotal = invoice.grossAmount || ((invoice.netAmount || 0) + totalDiscount);
    const netAmount = invoice.netAmount || 0;
    const paymentMode = invoice.paymentMode || (isCash ? "CASH" : "CREDIT");

    // Two-line item block: name + amount on row 1, qty/rate/nozzle on row 2.
    // Single-column for 72mm printable width.
    const itemsHtml = products.map((p) => {
        const name = asciiSafe(p.productName) || "Product";
        const qty = (p.quantity ?? 0).toFixed(2);
        const rate = (p.unitPrice ?? 0).toFixed(2);
        const amt = formatCurrency(p.amount);
        const nozzle = p.nozzleName ? ` | Nozzle: ${asciiSafe(p.nozzleName)}` : "";
        const disc = (p.discountAmount && p.discountAmount > 0)
            ? `<div class="item-sub">Discount: -${formatCurrency(p.discountAmount)}</div>` : "";
        return `<div class="item">
            <div class="item-row"><span class="item-name">${name}</span><span class="item-amt">${amt}</span></div>
            <div class="item-sub">${qty} x ${rate}${nozzle}</div>
            ${disc}
        </div>`;
    }).join("");

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

    /* Item block */
    .items-head { font-size: 8pt; font-weight: 900; letter-spacing: 0.5px; text-transform: uppercase; }
    .item { margin: 0.6mm 0; }
    .item-row { display: flex; justify-content: space-between; gap: 4px; font-size: 9.5pt; font-weight: 900; }
    .item-name { flex: 1; }
    .item-amt { white-space: nowrap; }
    .item-sub { font-size: 8pt; font-weight: 700; padding-left: 2mm; }

    /* Totals */
    .tot .row { font-size: 9.5pt; font-weight: 700; }
    .tot .row.bold { font-weight: 900; }
    .grand-wrap { margin: 1mm 0; padding: 1mm 0; border-top: 2px solid #000; border-bottom: 3px double #000; }
    .grand { display: flex; justify-content: space-between; gap: 6px; font-size: 12pt; font-weight: 900; letter-spacing: 0.3px; }
    .in-words { font-size: 8pt; font-weight: 700; line-height: 1.3; margin-top: 0.8mm; font-style: italic; }

    /* Signature & footer */
    .pay-row { font-size: 9pt; font-weight: 900; }
    .sign { margin-top: 6mm; border-top: 1px solid #000; padding-top: 0.6mm; text-align: center; font-size: 8pt; font-weight: 700; }
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
    <div class="row"><span class="lbl">Cashier</span><span class="val">${asciiSafe(invoice.raisedBy?.name) || "-"}</span></div>
</div>

<div class="rule-d"></div>

<!-- ===== CUSTOMER / VEHICLE ===== -->
<div class="meta">
    <div class="row"><span class="lbl">Customer</span><span class="val">${asciiSafe(customerName)}</span></div>
    ${customerPhone ? `<div class="row"><span class="lbl">Phone</span><span class="val">${asciiSafe(customerPhone)}</span></div>` : ""}
    ${customerGST ? `<div class="row"><span class="lbl">GST</span><span class="val">${asciiSafe(customerGST)}</span></div>` : ""}
    ${vehicleNo ? `<div class="row"><span class="lbl">Vehicle</span><span class="val">${asciiSafe(vehicleNo)}</span></div>` : ""}
    ${invoice.driverName ? `<div class="row"><span class="lbl">Driver</span><span class="val">${asciiSafe(invoice.driverName)}</span></div>` : ""}
    ${invoice.indentNo ? `<div class="row"><span class="lbl">Indent</span><span class="val">${asciiSafe(invoice.indentNo)}</span></div>` : ""}
    ${!isCash && invoice.vehicleKM ? `<div class="row"><span class="lbl">Odometer</span><span class="val">${invoice.vehicleKM.toLocaleString("en-IN")} km</span></div>` : ""}
</div>

<div class="rule-h"></div>

<!-- ===== ITEMS ===== -->
<div class="row items-head">
    <span>Item</span>
    <span>Amount</span>
</div>
<div class="rule-d"></div>
${itemsHtml}
<div class="rule-d"></div>

<!-- ===== TOTALS ===== -->
<div class="tot">
    <div class="row"><span>Sub Total</span><span>${formatCurrency(subTotal)}</span></div>
    ${totalDiscount > 0 ? `<div class="row"><span>Discount</span><span>-${formatCurrency(totalDiscount)}</span></div>` : ""}
</div>

<div class="grand-wrap">
    <div class="grand"><span>TOTAL Rs.</span><span>${formatCurrency(netAmount)}</span></div>
</div>

<div class="in-words">${asciiSafe(numberToWords(netAmount))}</div>

<div class="rule-d"></div>
<div class="row pay-row"><span>Payment</span><span>${asciiSafe(paymentMode)}</span></div>
<div class="rule-d"></div>

${!isCash ? `<div class="sign">Customer Signature</div>` : ""}

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
