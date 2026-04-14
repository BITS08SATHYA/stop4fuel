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

// Dot-matrix drivers (TVS MSP 250) can only render CP437/ASCII. Strip anything
// else and replace common typographic chars with ASCII equivalents.
function asciiSafe(s: string | undefined | null): string {
    if (!s) return "";
    return s
        .replace(/[\u2013\u2014]/g, "-")
        .replace(/[\u2018\u2019]/g, "'")
        .replace(/[\u201C\u201D]/g, '"')
        .replace(/\u20B9/g, "Rs.")
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

    // Customer info
    const customerName = invoice.customer?.name || invoice.signatoryName || "Walk-in Customer";
    const customerPhone = invoice.signatoryCellNo || "";
    const customerGST = invoice.customer?.partyType === "COMPANY" ? invoice.customerGST : "";

    // Vehicle info
    const vehicleNo = invoice.vehicle?.vehicleNumber || invoice.billDesc || "";

    // Products
    const products = invoice.products || [];
    const totalDiscount = invoice.totalDiscount || 0;
    const subTotal = invoice.grossAmount || ((invoice.netAmount || 0) + totalDiscount);

    // Items HTML — compact, no nozzle line, discount inline
    const itemsHtml = products.map((p) => {
        const disc = (p.discountAmount && p.discountAmount > 0)
            ? ` (-${formatCurrency(p.discountAmount)})` : "";
        return `<tr>
            <td style="padding:1px 0;">${asciiSafe(p.productName) || "Product"}${disc}</td>
            <td style="text-align:center;padding:1px 0;">${p.quantity?.toFixed(2) || "0"}</td>
            <td style="text-align:center;padding:1px 0;">${p.unitPrice?.toFixed(2) || "0"}</td>
            <td style="text-align:right;font-weight:bold;padding:1px 0;">${formatCurrency(p.amount)}</td>
        </tr>`;
    }).join("");

    return `<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<title>Invoice ${invoice.billNo || ""}</title>
<style>
    @page { size: 6in 4.5in; margin: 2mm 3mm; }
    @media print { body { -webkit-print-color-adjust: exact; print-color-adjust: exact; } }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body { font-family: 'Courier New', Courier, monospace; font-size: 12pt; font-weight: 900; line-height: 1.15; color: #000; background: #fff; width: 5.3in; margin: 0 auto; -webkit-text-stroke: 0.4px #000; }
    table { width: 100%; border-collapse: collapse; }
    td { vertical-align: top; font-size: 12pt; font-weight: 900; }
    .center { text-align: center; }
    .right { text-align: right; }
    .big { font-size: 16pt; font-weight: 900; }
    .xs { font-size: 10pt; font-weight: 900; color: #000; }
    hr { border: none; border-top: 2px solid #000; margin: 2px 0; }
    hr.solid { border-top: 3px solid #000; }
    .badge { display: inline-block; border: 2px solid #000; padding: 0 8px; font-size: 12pt; font-weight: 900; letter-spacing: 1px; }
    .info td:first-child { font-size: 11pt; font-weight: 900; width: 22%; padding: 0; }
    .info td:last-child { font-weight: 900; padding: 0; }
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
    <div class="big">${asciiSafe(company.name)}</div>
    <div class="xs">${asciiSafe(company.address)} | Ph: ${asciiSafe(company.phone)} | GSTIN: ${asciiSafe(company.gstNo)}</div>
</div>
<hr class="solid">
<div class="center"><span style="font-size:13pt;font-weight:900;">TAX INVOICE</span> <span class="badge">${billBadge}</span></div>
<hr>

<!-- Customer on its own full-width line -->
<table class="info"><tr>
    <td style="width:14%;">Customer:</td>
    <td style="text-align:left;">${asciiSafe(customerName)}${customerPhone ? ` (${asciiSafe(customerPhone)})` : ""}${customerGST ? ` | GST: ${asciiSafe(customerGST)}` : ""}</td>
</tr></table>

<!-- Bill / Vehicle two-column strip -->
<table><tr>
<td style="width:48%;">
    <table class="info">
        <tr><td>Bill:</td><td>${asciiSafe(invoice.billNo) || "-"}</td></tr>
        <tr><td>Date:</td><td>${invoice.date ? formatDate(invoice.date) : "-"}</td></tr>
        <tr><td>Shift:</td><td>#${invoice.shiftId || "-"}</td></tr>
    </table>
</td>
<td style="width:4%;"></td>
<td style="width:48%;">
    <table class="info">
        ${vehicleNo ? `<tr><td>Vehicle:</td><td>${asciiSafe(vehicleNo)}</td></tr>` : ""}
        ${invoice.driverName ? `<tr><td>Driver:</td><td>${asciiSafe(invoice.driverName)}</td></tr>` : ""}
        ${invoice.indentNo ? `<tr><td>Indent:</td><td>${asciiSafe(invoice.indentNo)}</td></tr>` : ""}
        <tr><td>Cashier:</td><td>${asciiSafe(invoice.raisedBy?.name) || "-"}</td></tr>
    </table>
</td>
</tr></table>
${invoice.vehicleKM ? `<div class="xs right">KM: ${invoice.vehicleKM.toLocaleString("en-IN")}</div>` : ""}
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
    <tr><td class="label">Sub Total</td><td class="val">${formatCurrency(subTotal)}</td></tr>
    ${totalDiscount > 0 ? `<tr><td class="label">Discount</td><td class="val">-${formatCurrency(totalDiscount)}</td></tr>` : ""}
    <tr class="grand"><td class="label">Total (Rs.)</td><td class="val">${formatCurrency(invoice.netAmount)}</td></tr>
</table>

${!isCash ? `<div class="sign-line"><span class="xs">Party Signature</span></div>` : ""}

<div class="audit">
    <span>Pump Reading: </span>
    <span>Nozzle: </span>
    <span>Attendant: </span>
</div>
<div class="xs center" style="margin-top:2px;">Computer-generated invoice.</div>

</body>
</html>`;
}

export function printInvoice(invoice: InvoiceBill, company: CompanyInfo): void {
    const html = generateInvoiceHTML(invoice, company);
    const printWindow = window.open("", "_blank", "width=700,height=900");
    if (!printWindow) {
        alert("Please allow popups to print invoices.");
        return;
    }
    printWindow.document.write(html);
    printWindow.document.close();

    // Wait for content to render, then auto-print
    printWindow.onload = () => {
        printWindow.print();
    };
    // Fallback if onload doesn't fire
    setTimeout(() => {
        try { printWindow.print(); } catch (_) { /* already printed or closed */ }
    }, 500);
}
