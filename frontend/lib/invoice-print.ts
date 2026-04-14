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

    // Credit-specific fields
    const creditFields = !isCash ? `
        ${invoice.driverName ? `<tr><td style="font-size:13pt;">Driver:</td><td colspan="3" style="text-align:right;">${invoice.driverName}</td></tr>` : ""}
        ${invoice.indentNo ? `<tr><td style="font-size:13pt;">Indent:</td><td colspan="3" style="text-align:right;">${invoice.indentNo}</td></tr>` : ""}
        ${invoice.signatoryName ? `<tr><td style="font-size:13pt;">Signatory:</td><td colspan="3" style="text-align:right;">${invoice.signatoryName}</td></tr>` : ""}
    ` : "";

    return `<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<title>Invoice ${invoice.billNo || ""}</title>
<style>
    @page { size: 6in 4.5in; margin: 2mm 3mm; }
    @media print { body { -webkit-print-color-adjust: exact; print-color-adjust: exact; } }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body { font-family: 'Courier New', Courier, monospace; font-size: 14pt; font-weight: 900; line-height: 1.4; color: #000; background: #fff; width: 5.3in; margin: 0 auto; -webkit-text-stroke: 0.5px #000; }
    table { width: 100%; border-collapse: collapse; }
    td { vertical-align: top; font-size: 14pt; font-weight: 900; }
    .center { text-align: center; }
    .bold { font-weight: 900; }
    .big { font-size: 18pt; font-weight: 900; }
    .sm { font-size: 13pt; font-weight: 900; }
    .xs { font-size: 12pt; font-weight: 900; color: #000; }
    hr { border: none; border-top: 2px solid #000; margin: 3px 0; }
    hr.solid { border-top: 3px solid #000; }
    .badge { display: inline-block; border: 3px solid #000; padding: 1px 10px; font-size: 14pt; font-weight: 900; letter-spacing: 1px; }
    .grand-total { font-size: 20pt; font-weight: 900; text-align: right; padding: 3px 0; }
    .info td:first-child { color: #000; font-size: 13pt; font-weight: 900; width: 22%; }
    .info td:last-child { text-align: right; font-weight: 900; }
    .items th { font-size: 13pt; font-weight: 900; border-bottom: 3px solid #000; padding: 2px 0; text-transform: uppercase; }
    .sign-line { margin-top: 18px; border-top: 2px solid #000; text-align: center; }
</style>
</head>
<body>

<!-- Header -->
<div class="center">
    <div class="big">${asciiSafe(company.name)}</div>
    <div class="xs">${asciiSafe(company.address)} | Ph: ${asciiSafe(company.phone)} | GSTIN: ${asciiSafe(company.gstNo)}</div>
</div>
<hr class="solid">
<div class="center"><span style="font-size:16pt;font-weight:900;">TAX INVOICE</span> <span class="badge">${billBadge}</span></div>
<hr>

<!-- Bill + Customer info (two columns) -->
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
        <tr><td>Customer:</td><td>${asciiSafe(customerName)}</td></tr>
        ${vehicleNo ? `<tr><td>Vehicle:</td><td>${asciiSafe(vehicleNo)}</td></tr>` : ""}
        ${invoice.driverName ? `<tr><td>Driver:</td><td>${asciiSafe(invoice.driverName)}</td></tr>` : `<tr><td>Cashier:</td><td>${asciiSafe(invoice.raisedBy?.name) || "-"}</td></tr>`}
    </table>
</td>
</tr></table>
${invoice.vehicleKM ? `<div style="font-size:13pt;font-weight:900;text-align:right;">KM: ${invoice.vehicleKM.toLocaleString("en-IN")}${invoice.indentNo ? ` | Indent: ${invoice.indentNo}` : ""}${customerGST ? ` | GST: ${customerGST}` : ""}</div>` : ""}
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
<hr class="solid">

<div class="grand-total">Rs. ${formatCurrency(invoice.netAmount)}</div>

<hr>
<div class="xs center">Computer-generated invoice.</div>

${!isCash ? `<div class="sign-line"><span class="xs">Party Signature</span></div>` : ""}

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
