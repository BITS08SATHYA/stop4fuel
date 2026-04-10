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

    // Items HTML
    const itemsHtml = products.map((p, i) => {
        const isLast = i === products.length - 1;
        const discHtml = (p.discountAmount && p.discountAmount > 0)
            ? `<tr><td colspan="4" style="text-align:right;font-size:8pt;color:#555;">Disc @${p.discountRate?.toFixed(2) || "0"}/L = -${formatCurrency(p.discountAmount)}</td></tr>`
            : "";
        return `
            <tr>
                <td style="font-weight:bold;padding:2px 0 0;">${p.productName || "Product"}</td>
                <td style="text-align:center;padding:2px 0 0;">${p.quantity?.toFixed(2) || "0"}</td>
                <td style="text-align:center;padding:2px 0 0;">${p.unitPrice?.toFixed(2) || "0"}</td>
                <td style="text-align:right;font-weight:bold;padding:2px 0 0;">${formatCurrency(p.amount)}</td>
            </tr>
            <tr><td colspan="4" style="font-size:7pt;color:#555;padding:0 0 2px;">${p.nozzleName ? `Nozzle: ${p.nozzleName}` : ""}</td></tr>
            ${discHtml}
            ${!isLast ? '<tr><td colspan="4"><hr style="border:none;border-top:1px dashed #000;margin:2px 0;"></td></tr>' : ""}
        `;
    }).join("");

    // Credit-specific fields
    const creditFields = !isCash ? `
        ${invoice.driverName ? `<tr><td style="color:#555;font-size:8pt;">Driver:</td><td colspan="3" style="text-align:right;">${invoice.driverName}</td></tr>` : ""}
        ${invoice.indentNo ? `<tr><td style="color:#555;font-size:8pt;">Indent:</td><td colspan="3" style="text-align:right;">${invoice.indentNo}</td></tr>` : ""}
        ${invoice.signatoryName ? `<tr><td style="color:#555;font-size:8pt;">Signatory:</td><td colspan="3" style="text-align:right;">${invoice.signatoryName}</td></tr>` : ""}
    ` : "";

    return `<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<title>Invoice ${invoice.billNo || ""}</title>
<style>
    @page {
        size: 6in 8in;
        margin: 8mm 6mm;
    }
    @media print {
        body { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
        .no-print { display: none !important; }
    }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
        font-family: 'Courier New', 'Lucida Console', monospace;
        font-size: 10pt;
        line-height: 1.35;
        color: #000;
        background: #fff;
        width: 5.6in;
        margin: 0 auto;
    }
    table { width: 100%; border-collapse: collapse; }
    td { vertical-align: top; }
    .center { text-align: center; }
    .bold { font-weight: bold; }
    .big { font-size: 14pt; font-weight: 900; }
    .med { font-size: 11pt; font-weight: bold; }
    .sm { font-size: 8pt; }
    .xs { font-size: 7pt; color: #555; }
    hr.solid { border: none; border-top: 2px solid #000; margin: 4px 0; }
    hr.dashed { border: none; border-top: 1px dashed #000; margin: 3px 0; }
    .badge {
        display: inline-block;
        border: 2px solid #000;
        padding: 1px 10px;
        font-size: 10pt;
        font-weight: 900;
        letter-spacing: 1px;
    }
    .grand-total {
        font-size: 16pt;
        font-weight: 900;
        text-align: center;
        padding: 4px 0;
    }
    .row-table td:first-child { color: #555; font-size: 9pt; width: 35%; }
    .row-table td:last-child { text-align: right; font-weight: bold; }
    .items-table th {
        font-size: 8pt;
        font-weight: bold;
        color: #555;
        border-bottom: 1px solid #000;
        padding: 2px 0;
        text-transform: uppercase;
    }
    .sign-line {
        margin-top: 30px;
        border-top: 1px solid #000;
        text-align: center;
        padding-top: 2px;
    }
</style>
</head>
<body>

<!-- Header -->
<div class="center">
    <div class="big">${company.name}</div>
    <div class="sm">${company.site ? company.site + " — " : ""}Indian Oil Corporation Retail Outlet</div>
    <div class="sm">${company.address}</div>
    <div class="sm">Ph: ${company.phone}</div>
    <div class="xs">GSTIN: ${company.gstNo}</div>
</div>

<hr class="solid">

<div class="center">
    <div class="med">TAX INVOICE</div>
    <span class="badge">${billBadge}</span>
</div>

<hr class="dashed">

<!-- Bill info -->
<table class="row-table">
    <tr><td>Bill No:</td><td>${invoice.billNo || "—"}</td></tr>
    <tr><td>Date:</td><td>${invoice.date ? formatDate(invoice.date) : "—"}</td></tr>
    <tr><td>Shift:</td><td>#${invoice.shiftId || "—"}</td></tr>
    <tr><td>Cashier:</td><td>${invoice.raisedBy?.name || "—"}</td></tr>
</table>

<hr class="dashed">

<!-- Customer -->
<table class="row-table">
    <tr><td colspan="2" style="font-weight:bold;font-size:10pt;color:#000;">${customerName}</td></tr>
    ${customerPhone ? `<tr><td colspan="2" class="sm" style="color:#000;">Ph: ${customerPhone}</td></tr>` : ""}
    ${customerGST ? `<tr><td colspan="2" class="sm" style="color:#000;">GSTIN: ${customerGST}</td></tr>` : ""}
</table>

<hr class="dashed">

<!-- Vehicle -->
<table class="row-table">
    ${vehicleNo ? `<tr><td>Vehicle:</td><td>${vehicleNo}</td></tr>` : ""}
    ${invoice.vehicleKM ? `<tr><td>KM:</td><td>${invoice.vehicleKM.toLocaleString("en-IN")}</td></tr>` : ""}
    ${creditFields}
</table>

<hr class="solid">

<!-- Items -->
<div class="center bold sm" style="margin-bottom:2px;">ITEMS</div>
<table class="items-table">
    <thead>
        <tr>
            <th style="text-align:left;">Product</th>
            <th style="text-align:center;">Qty</th>
            <th style="text-align:center;">Rate</th>
            <th style="text-align:right;">Amount</th>
        </tr>
    </thead>
    <tbody>
        ${itemsHtml}
    </tbody>
</table>

<hr class="solid">

<!-- Totals -->
<table class="row-table">
    <tr><td style="color:#000;">Gross Amount</td><td>${formatCurrency(grossAmount)}</td></tr>
    ${totalDiscount > 0 ? `<tr><td style="color:#000;">Discount</td><td>-${formatCurrency(totalDiscount)}</td></tr>` : ""}
    ${Math.abs(rounding) >= 0.01 ? `<tr><td style="color:#000;">Rounding</td><td>${rounding > 0 ? "+" : ""}${rounding.toFixed(2)}</td></tr>` : ""}
</table>

<hr class="solid">

<div class="grand-total">&#8377; ${formatCurrency(invoice.netAmount)}</div>
<div class="center xs" style="margin-top:-2px;">NET AMOUNT</div>

<hr class="solid">

<!-- Payment -->
<table class="row-table">
    <tr><td>Payment:</td><td>${isCash ? (invoice.paymentMode || "CASH") : "CREDIT"}</td></tr>
    <tr><td>Status:</td><td>${paymentStatus}</td></tr>
</table>

<hr class="dashed">

<!-- Footer text -->
<div class="center xs" style="margin-top:4px;">
    Fuel prices include all applicable taxes.<br>
    Goods once sold will not be taken back.<br>
    ${!isCash ? "Credit bills settled as per agreement terms.<br>" : ""}
    Subject to Chennai jurisdiction.<br>
    Computer-generated invoice.
</div>

<hr class="dashed">

<div class="center xs">
    ${numberToWords(invoice.netAmount || 0)}
</div>

${!isCash ? `
<div class="sign-line">
    <span class="xs">Authorized Signatory</span>
</div>
` : ""}

<div style="margin-top:8px;"></div>
<div class="center sm bold">*** Thank You ${isCash ? "| Visit Again" : ""} ***</div>

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
