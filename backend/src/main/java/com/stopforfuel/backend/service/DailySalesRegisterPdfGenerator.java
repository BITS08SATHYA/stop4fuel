package com.stopforfuel.backend.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.stopforfuel.backend.exception.ReportGenerationException;
import com.stopforfuel.backend.service.DailySalesRegisterService.CreditCustomerRow;
import com.stopforfuel.backend.service.DailySalesRegisterService.DailySalesRegisterData;
import com.stopforfuel.backend.service.DailySalesRegisterService.FuelDayBlock;
import com.stopforfuel.backend.service.DailySalesRegisterService.FuelSection;
import com.stopforfuel.backend.service.DailySalesRegisterService.LubeDayBlock;
import com.stopforfuel.backend.service.DailySalesRegisterService.LubricantSection;
import com.stopforfuel.backend.service.DailySalesRegisterService.PurchaseRow;
import com.stopforfuel.backend.service.pdf.PageFooterEvent;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Component
public class DailySalesRegisterPdfGenerator {

    private static final Color BLACK = new Color(33, 37, 41);
    private static final Color ACCENT = new Color(230, 81, 0);     // orange
    private static final Color SECTION_BG = new Color(55, 71, 79);  // dark slate
    private static final Color HEADER_FG = Color.WHITE;
    private static final Color ALT_ROW = new Color(248, 249, 250);
    private static final Color LIGHT_BORDER = new Color(220, 220, 220);
    private static final Color MUTED = new Color(108, 117, 125);
    private static final Color TOTAL_BG = new Color(233, 236, 239);

    private static final Font F_COMPANY = new Font(Font.HELVETICA, 12, Font.BOLD, BLACK);
    private static final Font F_TITLE = new Font(Font.HELVETICA, 14, Font.BOLD, ACCENT);
    private static final Font F_PERIOD = new Font(Font.HELVETICA, 9, Font.NORMAL, MUTED);
    private static final Font F_SECTION = new Font(Font.HELVETICA, 11, Font.BOLD, HEADER_FG);
    private static final Font F_TH = new Font(Font.HELVETICA, 7, Font.BOLD, HEADER_FG);
    private static final Font F_TD = new Font(Font.HELVETICA, 7, Font.NORMAL, BLACK);
    private static final Font F_TD_BOLD = new Font(Font.HELVETICA, 7, Font.BOLD, BLACK);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter MONTH_YEAR_FMT =
            DateTimeFormatter.ofPattern("MMMM yyyy").withLocale(Locale.ENGLISH);
    private static final NumberFormat INR = NumberFormat.getNumberInstance(new Locale("en", "IN"));
    static {
        INR.setMinimumFractionDigits(2);
        INR.setMaximumFractionDigits(2);
    }

    // ===================== public entry points =====================

    public byte[] fuel(DailySalesRegisterData d, String section) {
        return render("DAILY SALES REGISTER - " + section, d,
                (doc) -> addFuelSection(doc, d, section));
    }

    public byte[] lubricant(DailySalesRegisterData d) {
        return render("DAILY SALES REGISTER - LUBRICANTS", d, (doc) -> addLubricant(doc, d));
    }

    public byte[] purchase(DailySalesRegisterData d) {
        return render("PURCHASE REGISTER", d, (doc) -> addPurchase(doc, d));
    }

    private interface Body {
        void write(Document doc) throws DocumentException;
    }

    private byte[] render(String title, DailySalesRegisterData d, Body body) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), 22, 22, 24, 30);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            writer.setPageEvent(new PageFooterEvent());
            doc.open();
            addHeader(doc, d, title);
            body.write(doc);
            doc.close();
        } catch (DocumentException e) {
            throw new ReportGenerationException("Failed to generate Daily Sales Register PDF", e);
        }
        return baos.toByteArray();
    }

    private void addHeader(Document doc, DailySalesRegisterData d, String title) throws DocumentException {
        PdfPTable header = new PdfPTable(2);
        header.setWidthPercentage(100);
        header.setWidths(new float[]{60f, 40f});

        PdfPCell left = new PdfPCell();
        left.setBorder(Rectangle.NO_BORDER);
        String name = d.company != null && d.company.getName() != null ? d.company.getName() : "StopForFuel";
        left.addElement(new Paragraph(name.toUpperCase(), F_COMPANY));
        if (d.company != null && d.company.getGstNo() != null && !d.company.getGstNo().isBlank()) {
            left.addElement(new Paragraph("GSTIN: " + d.company.getGstNo(), F_PERIOD));
        }
        header.addCell(left);

        PdfPCell right = new PdfPCell();
        right.setBorder(Rectangle.NO_BORDER);
        Paragraph t = new Paragraph(title, F_TITLE);
        t.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(t);
        Paragraph period = new Paragraph("Period: " + headerPeriod(d.fromDate, d.toDate), F_PERIOD);
        period.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(period);
        header.addCell(right);

        doc.add(header);
    }

    // ===================== Fuel section =====================

    private void addFuelSection(Document doc, DailySalesRegisterData d, String section) throws DocumentException {
        FuelSection fs = d.fuelSections != null ? d.fuelSections.get(section) : null;
        sectionBar(doc, section);

        PdfPTable t = new PdfPTable(new float[]{
                0.5f, 1.1f, 1.2f, 1.0f, 1.3f, 1.0f, 1.2f, 1.3f, 2.2f, 1.1f, 0.9f, 1.3f});
        t.setWidthPercentage(100);
        t.setHeaderRows(1);
        t.setKeepTogether(false);

        for (String h : new String[]{"S.NO", "DATE", "TOTAL SALES LITERS", "PRODUCT RATE",
                "TOTAL SALES AMOUNT", "PRODUCT NAME", "CASH SALES LITERS", "CASH SALES AMOUNT",
                "CUSTOMER NAME", "CREDIT LITERS", "RATE", "CREDIT LITER AMOUNT"}) {
            th(t, h);
        }

        BigDecimal gTotL = BigDecimal.ZERO, gTotAmt = BigDecimal.ZERO,
                gCashL = BigDecimal.ZERO, gCashAmt = BigDecimal.ZERO,
                gCrL = BigDecimal.ZERO, gCrAmt = BigDecimal.ZERO;

        int idx = 0;
        if (fs != null && fs.days != null) {
            for (FuelDayBlock blk : fs.days) {
                Color bg = (idx++ % 2 == 0) ? Color.WHITE : ALT_ROW;
                boolean hasCredit = blk.creditRows != null && !blk.creditRows.isEmpty();
                int span = hasCredit ? blk.creditRows.size() + 1 : 1;

                // First row: 8 day-level cells (rowspan) + first credit row (or blanks).
                td(t, String.valueOf(blk.sno), bg, Element.ALIGN_CENTER, false, span);
                td(t, DATE_FMT.format(blk.date), bg, Element.ALIGN_CENTER, false, span);
                td(t, fmtQty(blk.totalSalesLiters), bg, Element.ALIGN_RIGHT, false, span);
                td(t, INR.format(blk.productRate), bg, Element.ALIGN_RIGHT, false, span);
                td(t, INR.format(blk.totalSalesAmount), bg, Element.ALIGN_RIGHT, false, span);
                td(t, blk.productName, bg, Element.ALIGN_CENTER, false, span);
                td(t, fmtQty(blk.cashSalesLiters), bg, Element.ALIGN_RIGHT, false, span);
                td(t, INR.format(blk.cashSalesAmount), bg, Element.ALIGN_RIGHT, false, span);

                if (!hasCredit) {
                    td(t, "", bg, Element.ALIGN_LEFT, false, 1);
                    td(t, "", bg, Element.ALIGN_RIGHT, false, 1);
                    td(t, "", bg, Element.ALIGN_RIGHT, false, 1);
                    td(t, "", bg, Element.ALIGN_RIGHT, false, 1);
                } else {
                    for (int i = 0; i < blk.creditRows.size(); i++) {
                        CreditCustomerRow r = blk.creditRows.get(i);
                        td(t, r.customerName, bg, Element.ALIGN_LEFT, false, 1);
                        td(t, fmtQty(r.creditLiters), bg, Element.ALIGN_RIGHT, false, 1);
                        td(t, INR.format(r.rate), bg, Element.ALIGN_RIGHT, false, 1);
                        td(t, INR.format(r.creditLiterAmount), bg, Element.ALIGN_RIGHT, false, 1);
                    }
                    // Per-day credit subtotal (last row of the block).
                    td(t, "SUBTOTAL", TOTAL_BG, Element.ALIGN_LEFT, true, 1);
                    td(t, fmtQty(blk.creditLitersSubtotal), TOTAL_BG, Element.ALIGN_RIGHT, true, 1);
                    td(t, "", TOTAL_BG, Element.ALIGN_RIGHT, true, 1);
                    td(t, INR.format(blk.creditAmountSubtotal), TOTAL_BG, Element.ALIGN_RIGHT, true, 1);
                }

                gTotL = gTotL.add(blk.totalSalesLiters);
                gTotAmt = gTotAmt.add(blk.totalSalesAmount);
                gCashL = gCashL.add(blk.cashSalesLiters);
                gCashAmt = gCashAmt.add(blk.cashSalesAmount);
                gCrL = gCrL.add(blk.creditLitersSubtotal);
                gCrAmt = gCrAmt.add(blk.creditAmountSubtotal);
            }
        }

        td(t, "GRAND TOTAL", TOTAL_BG, Element.ALIGN_LEFT, true, 1);
        td(t, "", TOTAL_BG, Element.ALIGN_CENTER, true, 1);
        td(t, fmtQty(gTotL), TOTAL_BG, Element.ALIGN_RIGHT, true, 1);
        td(t, "", TOTAL_BG, Element.ALIGN_RIGHT, true, 1);
        td(t, INR.format(gTotAmt), TOTAL_BG, Element.ALIGN_RIGHT, true, 1);
        td(t, "", TOTAL_BG, Element.ALIGN_CENTER, true, 1);
        td(t, fmtQty(gCashL), TOTAL_BG, Element.ALIGN_RIGHT, true, 1);
        td(t, INR.format(gCashAmt), TOTAL_BG, Element.ALIGN_RIGHT, true, 1);
        td(t, "", TOTAL_BG, Element.ALIGN_LEFT, true, 1);
        td(t, fmtQty(gCrL), TOTAL_BG, Element.ALIGN_RIGHT, true, 1);
        td(t, "", TOTAL_BG, Element.ALIGN_RIGHT, true, 1);
        td(t, INR.format(gCrAmt), TOTAL_BG, Element.ALIGN_RIGHT, true, 1);

        doc.add(t);
    }

    // ===================== Lubricant section =====================

    private void addLubricant(Document doc, DailySalesRegisterData d) throws DocumentException {
        LubricantSection ls = d.lubricant;
        sectionBar(doc, "LUBRICANTS");

        PdfPTable t = new PdfPTable(new float[]{0.6f, 1.3f, 1.5f, 2.4f, 1.4f, 1.0f, 1.5f, 1.8f, 1.5f});
        t.setWidthPercentage(100);
        t.setHeaderRows(1);
        t.setKeepTogether(false);

        for (String h : new String[]{"S.NO", "DATE", "TOTAL SALES AMOUNT", "CUSTOMER NAME",
                "CREDIT LITERS/QTY", "RATE", "CREDIT LITER AMOUNT", "PRODUCT NAME", "CASH SALES AMOUNT"}) {
            th(t, h);
        }

        BigDecimal gTotal = BigDecimal.ZERO, gCash = BigDecimal.ZERO, gCredit = BigDecimal.ZERO;
        int idx = 0;
        if (ls != null && ls.days != null) {
            for (LubeDayBlock blk : ls.days) {
                Color bg = (idx++ % 2 == 0) ? Color.WHITE : ALT_ROW;
                boolean hasCredit = blk.creditRows != null && !blk.creditRows.isEmpty();
                int span = hasCredit ? blk.creditRows.size() : 1;

                td(t, String.valueOf(blk.sno), bg, Element.ALIGN_CENTER, false, span);
                td(t, DATE_FMT.format(blk.date), bg, Element.ALIGN_CENTER, false, span);
                td(t, INR.format(blk.totalSalesAmount), bg, Element.ALIGN_RIGHT, false, span);

                if (!hasCredit) {
                    td(t, "", bg, Element.ALIGN_LEFT, false, 1);
                    td(t, "", bg, Element.ALIGN_RIGHT, false, 1);
                    td(t, "", bg, Element.ALIGN_RIGHT, false, 1);
                    td(t, "", bg, Element.ALIGN_RIGHT, false, 1);
                    td(t, "", bg, Element.ALIGN_LEFT, false, 1);
                } else {
                    for (int i = 0; i < blk.creditRows.size(); i++) {
                        CreditCustomerRow r = blk.creditRows.get(i);
                        td(t, r.customerName, bg, Element.ALIGN_LEFT, false, 1);
                        td(t, fmtQty(r.creditLiters), bg, Element.ALIGN_RIGHT, false, 1);
                        td(t, INR.format(r.rate), bg, Element.ALIGN_RIGHT, false, 1);
                        td(t, INR.format(r.creditLiterAmount), bg, Element.ALIGN_RIGHT, false, 1);
                        td(t, r.productName != null ? r.productName : "", bg, Element.ALIGN_LEFT, false, 1);
                    }
                }
                td(t, INR.format(blk.cashSalesAmount), bg, Element.ALIGN_RIGHT, false, span);

                gTotal = gTotal.add(blk.totalSalesAmount);
                gCash = gCash.add(blk.cashSalesAmount);
                gCredit = gCredit.add(blk.creditAmountSubtotal);
            }
        }

        td(t, "GRAND TOTAL", TOTAL_BG, Element.ALIGN_LEFT, true, 1);
        td(t, "", TOTAL_BG, Element.ALIGN_CENTER, true, 1);
        td(t, INR.format(gTotal), TOTAL_BG, Element.ALIGN_RIGHT, true, 1);
        td(t, "", TOTAL_BG, Element.ALIGN_LEFT, true, 1);
        td(t, "", TOTAL_BG, Element.ALIGN_RIGHT, true, 1);
        td(t, "", TOTAL_BG, Element.ALIGN_RIGHT, true, 1);
        td(t, INR.format(gCredit), TOTAL_BG, Element.ALIGN_RIGHT, true, 1);
        td(t, "", TOTAL_BG, Element.ALIGN_LEFT, true, 1);
        td(t, INR.format(gCash), TOTAL_BG, Element.ALIGN_RIGHT, true, 1);

        doc.add(t);
    }

    // ===================== Purchase register =====================

    private void addPurchase(Document doc, DailySalesRegisterData d) throws DocumentException {
        sectionBar(doc, "PURCHASE REGISTER");

        PdfPTable t = new PdfPTable(new float[]{
                1.0f, 1.4f, 2.4f, 1.2f, 1.6f, 1.3f, 1.2f, 1.3f, 1.0f, 0.7f, 0.8f, 1.3f,
                0.8f, 1.2f, 0.8f, 1.2f, 1.3f});
        t.setWidthPercentage(100);
        t.setHeaderRows(1);
        t.setKeepTogether(false);

        for (String h : new String[]{"Invoice Date", "Invoice No", "Pty_Name", "Vch_Type",
                "GSTIN", "StateOfSupply", "Product_Name", "HSNCode", "Qty", "UOM", "TaxPer",
                "Taxable", "SGST%", "SGST Amt", "CGST%", "CGST Amt", "Total"}) {
            th(t, h);
        }

        BigDecimal tQty = BigDecimal.ZERO, tTax = BigDecimal.ZERO, tSgst = BigDecimal.ZERO,
                tCgst = BigDecimal.ZERO, tTotal = BigDecimal.ZERO;
        int idx = 0;
        if (d.purchaseRows != null) {
            for (PurchaseRow r : d.purchaseRows) {
                Color bg = (idx++ % 2 == 0) ? Color.WHITE : ALT_ROW;
                td(t, r.invoiceDate != null ? DATE_FMT.format(r.invoiceDate) : "", bg, Element.ALIGN_CENTER, false, 1);
                td(t, safe(r.invoiceNo), bg, Element.ALIGN_CENTER, false, 1);
                td(t, safe(r.ptyName), bg, Element.ALIGN_LEFT, false, 1);
                td(t, safe(r.vchType), bg, Element.ALIGN_CENTER, false, 1);
                td(t, safe(r.gstin), bg, Element.ALIGN_CENTER, false, 1);
                td(t, safe(r.stateOfSupply), bg, Element.ALIGN_LEFT, false, 1);
                td(t, safe(r.productName), bg, Element.ALIGN_LEFT, false, 1);
                td(t, safe(r.hsnCode), bg, Element.ALIGN_CENTER, false, 1);
                td(t, fmtQty(r.qty), bg, Element.ALIGN_RIGHT, false, 1);
                td(t, safe(r.uom), bg, Element.ALIGN_CENTER, false, 1);
                td(t, fmtPct(r.taxPer), bg, Element.ALIGN_RIGHT, false, 1);
                td(t, INR.format(r.taxable), bg, Element.ALIGN_RIGHT, false, 1);
                td(t, fmtPct(r.sgstPer), bg, Element.ALIGN_RIGHT, false, 1);
                td(t, INR.format(r.sgstAmt), bg, Element.ALIGN_RIGHT, false, 1);
                td(t, fmtPct(r.cgstPer), bg, Element.ALIGN_RIGHT, false, 1);
                td(t, INR.format(r.cgstAmt), bg, Element.ALIGN_RIGHT, false, 1);
                td(t, INR.format(r.total), bg, Element.ALIGN_RIGHT, false, 1);
                tQty = tQty.add(r.qty);
                tTax = tTax.add(r.taxable);
                tSgst = tSgst.add(r.sgstAmt);
                tCgst = tCgst.add(r.cgstAmt);
                tTotal = tTotal.add(r.total);
            }
        }

        td(t, "NET TOTAL", TOTAL_BG, Element.ALIGN_LEFT, true, 1);
        for (int i = 0; i < 7; i++) td(t, "", TOTAL_BG, Element.ALIGN_LEFT, true, 1);
        td(t, fmtQty(tQty), TOTAL_BG, Element.ALIGN_RIGHT, true, 1);
        td(t, "", TOTAL_BG, Element.ALIGN_LEFT, true, 1);
        td(t, "", TOTAL_BG, Element.ALIGN_LEFT, true, 1);
        td(t, INR.format(tTax), TOTAL_BG, Element.ALIGN_RIGHT, true, 1);
        td(t, "", TOTAL_BG, Element.ALIGN_LEFT, true, 1);
        td(t, INR.format(tSgst), TOTAL_BG, Element.ALIGN_RIGHT, true, 1);
        td(t, "", TOTAL_BG, Element.ALIGN_LEFT, true, 1);
        td(t, INR.format(tCgst), TOTAL_BG, Element.ALIGN_RIGHT, true, 1);
        td(t, INR.format(tTotal), TOTAL_BG, Element.ALIGN_RIGHT, true, 1);

        doc.add(t);
    }

    // ===================== helpers =====================

    private void sectionBar(Document doc, String label) throws DocumentException {
        PdfPTable bar = new PdfPTable(1);
        bar.setWidthPercentage(100);
        bar.setSpacingBefore(8);
        bar.setSpacingAfter(4);
        PdfPCell c = new PdfPCell(new Phrase(label, F_SECTION));
        c.setBackgroundColor(SECTION_BG);
        c.setBorderColor(SECTION_BG);
        c.setPadding(5);
        bar.addCell(c);
        doc.add(bar);
    }

    private void th(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, F_TH));
        cell.setBackgroundColor(ACCENT);
        cell.setBorderColor(ACCENT);
        cell.setPadding(4);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        table.addCell(cell);
    }

    private void td(PdfPTable table, String text, Color bg, int align, boolean bold, int rowspan) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", bold ? F_TD_BOLD : F_TD));
        cell.setBackgroundColor(bg);
        cell.setBorderColor(LIGHT_BORDER);
        cell.setBorderWidth(0.5f);
        cell.setPadding(3);
        cell.setHorizontalAlignment(align);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        if (rowspan > 1) cell.setRowspan(rowspan);
        table.addCell(cell);
    }

    private String headerPeriod(LocalDate from, LocalDate to) {
        if (from.getYear() == to.getYear() && from.getMonth() == to.getMonth()
                && from.getDayOfMonth() == 1
                && to.getDayOfMonth() == to.lengthOfMonth()) {
            return MONTH_YEAR_FMT.format(from);
        }
        return DATE_FMT.format(from) + " - " + DATE_FMT.format(to);
    }

    private String fmtQty(BigDecimal q) {
        if (q == null) return "0.00";
        return q.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String fmtPct(BigDecimal p) {
        if (p == null || p.compareTo(BigDecimal.ZERO) == 0) return "0";
        return p.stripTrailingZeros().toPlainString();
    }

    private String safe(String s) {
        return s != null ? s : "";
    }
}
