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
    const paymentStatus = invoice.paymentStatus === "PAID" ? "PAID" : "NOT PAID";

    // Customer info
    const customerName = invoice.customer?.name || invoice.signatoryName || "Walk-in Customer";
    const customerPhone = invoice.signatoryCellNo || "";
    const customerGST = invoice.customer?.partyType === "COMPANY" ? invoice.customerGST : "";

    // Vehicle info
    const vehicleNo = invoice.vehicle?.vehicleNumber || invoice.billDesc || "";

    // Products
    const products = invoice.products || [];
    const grossAmount = invoice.grossAmount || invoice.netAmount || 0;
    const totalDiscount = invoice.totalDiscount || 0;
    const rounding = (invoice.netAmount || 0) - (grossAmount - totalDiscount);

    // Items HTML — compact, no nozzle line, discount inline
    const itemsHtml = products.map((p) => {
        const disc = (p.discountAmount && p.discountAmount > 0)
            ? ` (-${formatCurrency(p.discountAmount)})` : "";
        return `<tr>
            <td style="padding:1px 0;">${p.productName || "Product"}${disc}</td>
            <td style="text-align:center;padding:1px 0;">${p.quantity?.toFixed(2) || "0"}</td>
            <td style="text-align:center;padding:1px 0;">${p.unitPrice?.toFixed(2) || "0"}</td>
            <td style="text-align:right;font-weight:bold;padding:1px 0;">${formatCurrency(p.amount)}</td>
        </tr>`;
    }).join("");

    // Credit-specific fields
    const creditFields = !isCash ? `
        ${invoice.driverName ? `<tr><td style="font-size:10pt;">Driver:</td><td colspan="3" style="text-align:right;">${invoice.driverName}</td></tr>` : ""}
        ${invoice.indentNo ? `<tr><td style="font-size:10pt;">Indent:</td><td colspan="3" style="text-align:right;">${invoice.indentNo}</td></tr>` : ""}
        ${invoice.signatoryName ? `<tr><td style="font-size:10pt;">Signatory:</td><td colspan="3" style="text-align:right;">${invoice.signatoryName}</td></tr>` : ""}
    ` : "";

    return `<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<title>Invoice ${invoice.billNo || ""}</title>
<style>
    @page { size: 8in 6in; margin: 3mm 6mm; }
    @media print { body { -webkit-print-color-adjust: exact; print-color-adjust: exact; } }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body { font-family: 'Courier New', Courier, monospace; font-size: 11pt; font-weight: bold; line-height: 1.3; color: #000; background: #fff; width: 7.4in; margin: 0 auto; }
    table { width: 100%; border-collapse: collapse; }
    td { vertical-align: top; font-size: 11pt; font-weight: bold; }
    .center { text-align: center; }
    .bold { font-weight: bold; }
    .big { font-size: 14pt; font-weight: 900; }
    .sm { font-size: 10pt; }
    .xs { font-size: 9pt; color: #000; }
    hr { border: none; border-top: 1px solid #000; margin: 3px 0; }
    hr.solid { border-top: 2px solid #000; }
    .badge { display: inline-block; border: 2px solid #000; padding: 1px 8px; font-size: 11pt; font-weight: 900; letter-spacing: 1px; }
    .grand-total { font-size: 16pt; font-weight: 900; text-align: right; padding: 3px 0; }
    .info td:first-child { color: #000; font-size: 10pt; width: 22%; }
    .info td:last-child { text-align: right; font-weight: bold; }
    .items th { font-size: 10pt; font-weight: bold; border-bottom: 2px solid #000; padding: 2px 0; text-transform: uppercase; }
    .sign-line { margin-top: 20px; border-top: 1px solid #000; text-align: center; }
</style>
</head>
<body>

<!-- Header -->
<div class="center">
    <div class="big">${company.name}</div>
    <div class="xs">${company.address} | Ph: ${company.phone}</div>
    <div class="xs">GSTIN: ${company.gstNo}</div>
</div>
<hr class="solid">
<div class="center"><span style="font-size:12pt;font-weight:900;">TAX INVOICE</span> <span class="badge">${billBadge}</span></div>
<hr>

<!-- Bill + Customer info (two columns) -->
<table><tr>
<td style="width:50%;">
    <table class="info">
        <tr><td>Bill:</td><td>${invoice.billNo || "—"}</td></tr>
        <tr><td>Date:</td><td>${invoice.date ? formatDate(invoice.date) : "—"}</td></tr>
        <tr><td>Shift:</td><td>#${invoice.shiftId || "—"}</td></tr>
    </table>
</td>
<td style="width:50%;">
    <table class="info">
        <tr><td>Customer:</td><td>${customerName}</td></tr>
        ${vehicleNo ? `<tr><td>Vehicle:</td><td>${vehicleNo}</td></tr>` : ""}
        ${invoice.driverName ? `<tr><td>Driver:</td><td>${invoice.driverName}</td></tr>` : `<tr><td>Cashier:</td><td>${invoice.raisedBy?.name || "—"}</td></tr>`}
    </table>
</td>
</tr></table>
${invoice.vehicleKM ? `<div style="font-size:10pt;font-weight:bold;text-align:right;">KM: ${invoice.vehicleKM.toLocaleString("en-IN")}${invoice.indentNo ? ` | Indent: ${invoice.indentNo}` : ""}${customerGST ? ` | GST: ${customerGST}` : ""}</div>` : ""}
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

<!-- Totals -->
<table style="font-size:11pt;">
    <tr><td>Gross</td><td style="text-align:right;font-weight:bold;">${formatCurrency(grossAmount)}</td>
        <td style="width:10px;"></td>
        <td>Payment</td><td style="text-align:right;font-weight:bold;">${isCash ? (invoice.paymentMode || "CASH") : "CREDIT"}</td></tr>
    ${totalDiscount > 0 ? `<tr><td>Discount</td><td style="text-align:right;">-${formatCurrency(totalDiscount)}</td><td></td><td>Status</td><td style="text-align:right;font-weight:bold;">${paymentStatus}</td></tr>` : ""}
</table>

<div class="grand-total">&#8377; ${formatCurrency(invoice.netAmount)}</div>

<hr>
<div class="xs center">Fuel prices include all applicable taxes. Goods once sold will not be taken back. Computer-generated invoice.</div>

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
