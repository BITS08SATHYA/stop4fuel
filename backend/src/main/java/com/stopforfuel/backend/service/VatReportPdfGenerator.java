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
import com.stopforfuel.backend.entity.Product;
import com.stopforfuel.backend.entity.PurchaseInvoice;
import com.stopforfuel.backend.entity.PurchaseInvoiceItem;
import com.stopforfuel.backend.exception.ReportGenerationException;
import com.stopforfuel.backend.service.VatReportService.DailyFuelRow;
import com.stopforfuel.backend.service.VatReportService.FuelProductDaily;
import com.stopforfuel.backend.service.VatReportService.VatReportData;
import com.stopforfuel.backend.service.pdf.PageFooterEvent;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Component
public class VatReportPdfGenerator {

    private static final Color BLACK = new Color(33, 37, 41);
    private static final Color HEADER_BG = new Color(52, 58, 64);
    private static final Color HEADER_FG = Color.WHITE;
    private static final Color ALT_ROW = new Color(248, 249, 250);
    private static final Color LIGHT_BORDER = new Color(220, 220, 220);
    private static final Color BORDER = new Color(180, 180, 180);
    private static final Color ACCENT = new Color(13, 110, 253);
    private static final Color MUTED = new Color(108, 117, 125);
    private static final Color TOTAL_BG = new Color(233, 236, 239);

    private static final Font F_COMPANY = new Font(Font.HELVETICA, 12, Font.BOLD, BLACK);
    private static final Font F_TITLE = new Font(Font.HELVETICA, 14, Font.BOLD, ACCENT);
    private static final Font F_PERIOD = new Font(Font.HELVETICA, 9, Font.NORMAL, MUTED);
    private static final Font F_SECTION = new Font(Font.HELVETICA, 11, Font.BOLD, BLACK);
    private static final Font F_TH = new Font(Font.HELVETICA, 8, Font.BOLD, HEADER_FG);
    private static final Font F_TD = new Font(Font.HELVETICA, 8, Font.NORMAL, BLACK);
    private static final Font F_TD_BOLD = new Font(Font.HELVETICA, 8, Font.BOLD, BLACK);
    private static final Font F_LABEL = new Font(Font.HELVETICA, 9, Font.NORMAL, BLACK);
    private static final Font F_LABEL_BOLD = new Font(Font.HELVETICA, 9, Font.BOLD, BLACK);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter MONTH_YEAR_FMT = DateTimeFormatter.ofPattern("MMMM yyyy").withLocale(Locale.ENGLISH);
    private static final NumberFormat INR = NumberFormat.getNumberInstance(new Locale("en", "IN"));
    static {
        INR.setMinimumFractionDigits(2);
        INR.setMaximumFractionDigits(2);
    }

    public byte[] generate(VatReportData data) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), 25, 25, 25, 30);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            writer.setPageEvent(new PageFooterEvent());
            doc.open();

            addHeader(doc, data);
            addPurchaseRegister(doc, data);
            doc.newPage();
            addProductSummary(doc, data);
            doc.newPage();
            addGstComputation(doc, data);
            doc.newPage();
            addDailyLubeSales(doc, data);
            doc.newPage();
            addDailyFuelSales(doc, data);

            doc.close();
        } catch (DocumentException e) {
            throw new ReportGenerationException("Failed to generate VAT PDF", e);
        }
        return baos.toByteArray();
    }

    private void addHeader(Document doc, VatReportData d) throws DocumentException {
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
        Paragraph title = new Paragraph("VAT REPORT", F_TITLE);
        title.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(title);
        Paragraph period = new Paragraph("Period: " + headerPeriod(d.fromDate, d.toDate), F_PERIOD);
        period.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(period);
        header.addCell(right);

        doc.add(header);
    }

    // ===================== Section 1: Purchase Register =====================

    /** Purchase Register INVOICE NO column: shows "{sapEntryNumber}/({invoiceNumber})" when SAP # is set, else just invoiceNumber. */
    private static String formatInvoiceCell(PurchaseInvoice pi) {
        String inv = pi.getInvoiceNumber();
        String sap = pi.getSapEntryNumber();
        if (sap != null && !sap.isBlank()) {
            return sap + "/(" + (inv != null ? inv : "-") + ")";
        }
        return inv != null ? inv : "-";
    }

    private void addPurchaseRegister(Document doc, VatReportData d) throws DocumentException {
        Paragraph title = new Paragraph("PURCHASE REGISTER", F_SECTION);
        title.setSpacingBefore(10);
        title.setSpacingAfter(4);
        doc.add(title);

        PdfPTable table = new PdfPTable(new float[]{1.0f, 2.4f, 0.6f, 1.1f, 0.6f, 1.1f, 0.6f, 1.1f});
        table.setWidthPercentage(100);
        table.setHeaderRows(2);
        table.setKeepTogether(false);

        // Group header
        addHeaderCell(table, "DATE", 2, 1);
        addHeaderCell(table, "INVOICE NO", 2, 1);
        addHeaderCell(table, "XTRA PREMIUM (PETROL)", 1, 2);
        addHeaderCell(table, "MS (PETROL)", 1, 2);
        addHeaderCell(table, "HSD (DIESEL)", 1, 2);

        // Sub header
        addHeaderCell(table, "KL", 1, 1);
        addHeaderCell(table, "AMOUNT", 1, 1);
        addHeaderCell(table, "KL", 1, 1);
        addHeaderCell(table, "AMOUNT", 1, 1);
        addHeaderCell(table, "KL", 1, 1);
        addHeaderCell(table, "AMOUNT", 1, 1);

        BigDecimal[] totals = new BigDecimal[6];
        for (int i = 0; i < 6; i++) totals[i] = BigDecimal.ZERO;

        int idx = 0;
        if (d.purchaseInvoices != null) {
            for (PurchaseInvoice pi : d.purchaseInvoices) {
                Color bg = (idx++ % 2 == 0) ? Color.WHITE : ALT_ROW;
                BigDecimal[] perRow = splitByLabel(pi);
                addBody(table, pi.getInvoiceDate() != null ? DATE_FMT.format(pi.getInvoiceDate()) : "-", bg, Element.ALIGN_CENTER);
                addBody(table, formatInvoiceCell(pi), bg, Element.ALIGN_CENTER);
                for (int i = 0; i < 3; i++) {
                    BigDecimal kl = perRow[i * 2];
                    BigDecimal amt = perRow[i * 2 + 1];
                    addBody(table, kl.compareTo(BigDecimal.ZERO) > 0 ? fmtKl(kl) : "", bg, Element.ALIGN_RIGHT);
                    addBody(table, amt.compareTo(BigDecimal.ZERO) > 0 ? INR.format(amt) : "", bg, Element.ALIGN_RIGHT);
                    totals[i * 2] = totals[i * 2].add(kl);
                    totals[i * 2 + 1] = totals[i * 2 + 1].add(amt);
                }
            }
        }

        // NET TOTAL row
        addBody(table, "NET TOTAL", TOTAL_BG, Element.ALIGN_LEFT, true);
        addBody(table, "", TOTAL_BG, Element.ALIGN_LEFT, true);
        for (int i = 0; i < 3; i++) {
            addBody(table, fmtKl(totals[i * 2]), TOTAL_BG, Element.ALIGN_RIGHT, true);
            addBody(table, INR.format(totals[i * 2 + 1]), TOTAL_BG, Element.ALIGN_RIGHT, true);
        }

        doc.add(table);

        // Stash purchase totals so the next section can use them
        d.lubricantTotal = d.lubricantTotal == null ? BigDecimal.ZERO : d.lubricantTotal;
    }

    // ===================== Section 2: Product Summary =====================

    private void addProductSummary(Document doc, VatReportData d) throws DocumentException {
        Paragraph title = new Paragraph("PRODUCT SUMMARY", F_SECTION);
        title.setSpacingAfter(4);
        doc.add(title);

        PdfPTable table = new PdfPTable(new float[]{1f, 1.4f, 1f, 1f, 1.2f, 1.2f});
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        table.setKeepTogether(false);

        addHeaderCell(table, "PRODUCT", 1, 1);
        addHeaderCell(table, "PURCHASE AMOUNT", 1, 1);
        addHeaderCell(table, "LITERS SOLD", 1, 1);
        addHeaderCell(table, "RATE / LTR", 1, 1);
        addHeaderCell(table, "SALES AMOUNT", 1, 1);
        addHeaderCell(table, "VAT", 1, 1);

        // Reuse classification from data
        Map<String, BigDecimal[]> sales = computeSalesByLabel(d);
        Map<String, BigDecimal> purchase = computePurchaseByLabel(d);

        BigDecimal totalPurchase = BigDecimal.ZERO;
        BigDecimal totalLitres = BigDecimal.ZERO;
        BigDecimal totalSales = BigDecimal.ZERO;

        int idx = 0;
        for (String label : new String[]{"XP", "MS", "HSD"}) {
            Color bg = (idx++ % 2 == 0) ? Color.WHITE : ALT_ROW;
            BigDecimal pAmt = purchase.getOrDefault(label, BigDecimal.ZERO);
            BigDecimal[] sCell = sales.getOrDefault(label, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal litres = sCell[0];
            BigDecimal rate = sCell[1];
            BigDecimal sAmt = sCell[2];
            addBody(table, label, bg, Element.ALIGN_LEFT, true);
            addBody(table, INR.format(pAmt), bg, Element.ALIGN_RIGHT);
            addBody(table, fmtQty(litres), bg, Element.ALIGN_RIGHT);
            addBody(table, rate.compareTo(BigDecimal.ZERO) > 0 ? INR.format(rate) : "-", bg, Element.ALIGN_RIGHT);
            addBody(table, INR.format(sAmt), bg, Element.ALIGN_RIGHT);
            addBody(table, "-", bg, Element.ALIGN_RIGHT);
            totalPurchase = totalPurchase.add(pAmt);
            totalLitres = totalLitres.add(litres);
            totalSales = totalSales.add(sAmt);
        }

        // Lubricant row
        Color bg = (idx++ % 2 == 0) ? Color.WHITE : ALT_ROW;
        addBody(table, "LUBES (18%)", bg, Element.ALIGN_LEFT, true);
        addBody(table, "-", bg, Element.ALIGN_RIGHT);
        addBody(table, "-", bg, Element.ALIGN_RIGHT);
        addBody(table, "-", bg, Element.ALIGN_RIGHT);
        addBody(table, INR.format(d.lubricantTotal != null ? d.lubricantTotal : BigDecimal.ZERO), bg, Element.ALIGN_RIGHT);
        addBody(table, INR.format(d.netVat18 != null ? d.netVat18 : BigDecimal.ZERO), bg, Element.ALIGN_RIGHT);

        // Total
        BigDecimal grandSales = totalSales.add(d.lubricantTotal != null ? d.lubricantTotal : BigDecimal.ZERO);
        addBody(table, "TOTAL", TOTAL_BG, Element.ALIGN_LEFT, true);
        addBody(table, INR.format(totalPurchase), TOTAL_BG, Element.ALIGN_RIGHT, true);
        addBody(table, fmtQty(totalLitres), TOTAL_BG, Element.ALIGN_RIGHT, true);
        addBody(table, "", TOTAL_BG, Element.ALIGN_RIGHT, true);
        addBody(table, INR.format(grandSales), TOTAL_BG, Element.ALIGN_RIGHT, true);
        addBody(table, INR.format(d.netVat18 != null ? d.netVat18 : BigDecimal.ZERO), TOTAL_BG, Element.ALIGN_RIGHT, true);

        doc.add(table);
    }

    // ===================== Section 3: GST Computation =====================

    private void addGstComputation(Document doc, VatReportData d) throws DocumentException {
        Paragraph title = new Paragraph("GST COMPUTATION (LUBRICANTS @ 18%)", F_SECTION);
        title.setSpacingAfter(4);
        doc.add(title);

        PdfPTable table = new PdfPTable(new float[]{2.5f, 1f});
        table.setWidthPercentage(60);
        table.setHorizontalAlignment(Element.ALIGN_LEFT);

        kvRow(table, "Tax Included Sales", d.taxIncludedSales);
        kvRow(table, "Tax Excluded Sales (sales × 100/118)", d.taxExcludedSales);
        kvRow(table, "Net VAT 18%", d.netVat18);
        kvRow(table, "SGST 9%", d.sgst9);
        kvRow(table, "CGST 9%", d.cgst9);

        doc.add(table);
    }

    private void kvRow(PdfPTable table, String label, BigDecimal value) {
        PdfPCell l = new PdfPCell(new Phrase(label, F_LABEL));
        l.setPadding(4);
        l.setBorderColor(LIGHT_BORDER);
        table.addCell(l);
        PdfPCell v = new PdfPCell(new Phrase(value != null ? INR.format(value) : "0.00", F_LABEL_BOLD));
        v.setPadding(4);
        v.setBorderColor(LIGHT_BORDER);
        v.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(v);
    }

    // ===================== Section 4: Daily Lubricant Sales =====================

    private void addDailyLubeSales(Document doc, VatReportData d) throws DocumentException {
        Paragraph title = new Paragraph("DAILY LUBRICANT SALES", F_SECTION);
        title.setSpacingAfter(4);
        doc.add(title);

        PdfPTable table = new PdfPTable(new float[]{1f, 1.2f});
        table.setWidthPercentage(50);
        table.setHorizontalAlignment(Element.ALIGN_LEFT);
        table.setHeaderRows(1);

        addHeaderCell(table, "DATE", 1, 1);
        addHeaderCell(table, "AMOUNT", 1, 1);

        BigDecimal total = BigDecimal.ZERO;
        int idx = 0;
        Map<LocalDate, BigDecimal> lubeMap = d.lubricantDailySales != null ? d.lubricantDailySales : Map.of();
        for (LocalDate date : (Iterable<LocalDate>) d.fromDate.datesUntil(d.toDate.plusDays(1))::iterator) {
            BigDecimal amt = lubeMap.getOrDefault(date, BigDecimal.ZERO);
            Color bg = (idx++ % 2 == 0) ? Color.WHITE : ALT_ROW;
            addBody(table, DATE_FMT.format(date), bg, Element.ALIGN_CENTER);
            addBody(table, INR.format(amt), bg, Element.ALIGN_RIGHT);
            total = total.add(amt);
        }
        addBody(table, "TOTAL", TOTAL_BG, Element.ALIGN_LEFT, true);
        addBody(table, INR.format(total), TOTAL_BG, Element.ALIGN_RIGHT, true);

        doc.add(table);
    }

    // ===================== Section 5: Daily Fuel Sales per product =====================

    private void addDailyFuelSales(Document doc, VatReportData d) throws DocumentException {
        Paragraph title = new Paragraph("DAILY FUEL SALES", F_SECTION);
        title.setSpacingAfter(4);
        doc.add(title);

        Map<String, FuelProductDaily> ordered = new LinkedHashMap<>();
        ordered.put("XP", null);
        ordered.put("MS", null);
        ordered.put("HSD", null);
        if (d.fuelDailyByProduct != null) {
            for (FuelProductDaily fpd : d.fuelDailyByProduct.values()) {
                String label = classify(fpd.product);
                if (!ordered.containsKey(label)) continue;
                ordered.put(label, fpd);
            }
        }

        // Iterate every date in the requested period — a day with zero sales for every fuel still gets a row.
        Iterable<LocalDate> allDates = (Iterable<LocalDate>) d.fromDate.datesUntil(d.toDate.plusDays(1))::iterator;

        // Representative rate per fuel = last non-zero rate seen.
        Map<String, BigDecimal> rateByLabel = new LinkedHashMap<>();
        for (Map.Entry<String, FuelProductDaily> e : ordered.entrySet()) {
            BigDecimal rate = BigDecimal.ZERO;
            FuelProductDaily fpd = e.getValue();
            if (fpd != null && fpd.dailyTotals != null) {
                for (DailyFuelRow row : fpd.dailyTotals.values()) {
                    if (row.rate != null && row.rate.compareTo(BigDecimal.ZERO) > 0) rate = row.rate;
                }
            }
            rateByLabel.put(e.getKey(), rate);
        }

        PdfPTable t = new PdfPTable(new float[]{
                1.0f,
                0.8f, 0.5f, 0.8f, 1.1f,
                0.8f, 0.5f, 0.8f, 1.1f,
                0.8f, 0.5f, 0.8f, 1.1f
        });
        t.setWidthPercentage(100);
        t.setHeaderRows(2);
        t.setKeepTogether(false);

        // Header row 1: DATE (rowspan 2) + 3 group headers (colspan 4 each).
        addHeaderCell(t, "DATE", 2, 1);
        for (Map.Entry<String, FuelProductDaily> e : ordered.entrySet()) {
            String label = e.getKey();
            FuelProductDaily fpd = e.getValue();
            String fullLabel = label + (fpd != null && fpd.product != null ? " — " + fpd.product.getName() : "");
            BigDecimal rate = rateByLabel.get(label);
            String rateLine = rate.compareTo(BigDecimal.ZERO) > 0 ? "Rate ₹" + INR.format(rate) + "/L" : "Rate —";
            addGroupHeaderCell(t, fullLabel, rateLine, 4);
        }

        // Header row 2: LTRS / TEST / NET / AMOUNT × 3.
        for (int i = 0; i < 3; i++) {
            addHeaderCell(t, "LTRS", 1, 1);
            addHeaderCell(t, "TEST", 1, 1);
            addHeaderCell(t, "NET", 1, 1);
            addHeaderCell(t, "AMOUNT", 1, 1);
        }

        BigDecimal[] totLitres = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        BigDecimal[] totTest = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        BigDecimal[] totNet = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        BigDecimal[] totAmt = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};

        int idx = 0;
        for (LocalDate date : allDates) {
            Color bg = (idx++ % 2 == 0) ? Color.WHITE : ALT_ROW;
            addBody(t, DATE_FMT.format(date), bg, Element.ALIGN_CENTER);
            int col = 0;
            for (FuelProductDaily fpd : ordered.values()) {
                DailyFuelRow r = (fpd != null && fpd.dailyTotals != null) ? fpd.dailyTotals.get(date) : null;
                BigDecimal litres = r != null ? r.litres : BigDecimal.ZERO;
                BigDecimal test = r != null ? r.test : BigDecimal.ZERO;
                BigDecimal net = r != null ? r.netSale : BigDecimal.ZERO;
                BigDecimal amt = r != null ? r.amount : BigDecimal.ZERO;
                addBody(t, fmtQty(litres), bg, Element.ALIGN_RIGHT);
                addBody(t, fmtQty(test), bg, Element.ALIGN_RIGHT);
                addBody(t, fmtQty(net), bg, Element.ALIGN_RIGHT);
                addBody(t, INR.format(amt), bg, Element.ALIGN_RIGHT);
                totLitres[col] = totLitres[col].add(litres);
                totTest[col] = totTest[col].add(test);
                totNet[col] = totNet[col].add(net);
                totAmt[col] = totAmt[col].add(amt);
                col++;
            }
        }

        addBody(t, "TOTAL", TOTAL_BG, Element.ALIGN_LEFT, true);
        for (int i = 0; i < 3; i++) {
            addBody(t, fmtQty(totLitres[i]), TOTAL_BG, Element.ALIGN_RIGHT, true);
            addBody(t, fmtQty(totTest[i]), TOTAL_BG, Element.ALIGN_RIGHT, true);
            addBody(t, fmtQty(totNet[i]), TOTAL_BG, Element.ALIGN_RIGHT, true);
            addBody(t, INR.format(totAmt[i]), TOTAL_BG, Element.ALIGN_RIGHT, true);
        }

        doc.add(t);
    }

    private void addGroupHeaderCell(PdfPTable table, String label, String subLine, int colspan) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(HEADER_BG);
        cell.setBorderColor(HEADER_BG);
        cell.setPadding(4);
        cell.setColspan(colspan);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        Paragraph p1 = new Paragraph(label, F_TH);
        p1.setAlignment(Element.ALIGN_CENTER);
        Paragraph p2 = new Paragraph(subLine, F_TH);
        p2.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(p1);
        cell.addElement(p2);
        table.addCell(cell);
    }

    // ===================== HELPERS =====================

    private void addHeaderCell(PdfPTable table, String text, int rowspan, int colspan) {
        PdfPCell cell = new PdfPCell(new Phrase(text, F_TH));
        cell.setBackgroundColor(HEADER_BG);
        cell.setBorderColor(HEADER_BG);
        cell.setPadding(4);
        cell.setRowspan(rowspan);
        cell.setColspan(colspan);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        table.addCell(cell);
    }

    private void addBody(PdfPTable table, String text, Color bg, int align) {
        addBody(table, text, bg, align, false);
    }

    private void addBody(PdfPTable table, String text, Color bg, int align, boolean bold) {
        PdfPCell cell = new PdfPCell(new Phrase(text, bold ? F_TD_BOLD : F_TD));
        cell.setBackgroundColor(bg);
        cell.setBorderColor(LIGHT_BORDER);
        cell.setBorderWidth(0.5f);
        cell.setPadding(3);
        cell.setHorizontalAlignment(align);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        table.addCell(cell);
    }

    private BigDecimal[] splitByLabel(PurchaseInvoice pi) {
        BigDecimal[] out = new BigDecimal[6];
        for (int i = 0; i < 6; i++) out[i] = BigDecimal.ZERO;
        if (pi.getItems() == null) return out;
        for (PurchaseInvoiceItem item : pi.getItems()) {
            Product p = item.getProduct();
            if (p == null) continue;
            String label = classify(p);
            BigDecimal litres = item.getQuantity() != null ? BigDecimal.valueOf(item.getQuantity()) : BigDecimal.ZERO;
            BigDecimal kl = litres.divide(new BigDecimal("1000"), 4, RoundingMode.HALF_UP);
            BigDecimal amt = item.getTotalPrice() != null ? item.getTotalPrice() : BigDecimal.ZERO;
            int base = switch (label) {
                case "XP" -> 0;
                case "MS" -> 2;
                case "HSD" -> 4;
                default -> -1;
            };
            if (base < 0) continue;
            out[base] = out[base].add(kl);
            out[base + 1] = out[base + 1].add(amt);
        }
        return out;
    }

    private Map<String, BigDecimal> computePurchaseByLabel(VatReportData d) {
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        out.put("XP", BigDecimal.ZERO);
        out.put("MS", BigDecimal.ZERO);
        out.put("HSD", BigDecimal.ZERO);
        if (d.purchaseInvoices == null) return out;
        for (PurchaseInvoice pi : d.purchaseInvoices) {
            BigDecimal[] r = splitByLabel(pi);
            out.merge("XP", r[1], BigDecimal::add);
            out.merge("MS", r[3], BigDecimal::add);
            out.merge("HSD", r[5], BigDecimal::add);
        }
        return out;
    }

    private Map<String, BigDecimal[]> computeSalesByLabel(VatReportData d) {
        Map<String, BigDecimal[]> out = new LinkedHashMap<>();
        // [litres, rate (last seen), amount]
        out.put("XP", new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
        out.put("MS", new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
        out.put("HSD", new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
        if (d.fuelDailyByProduct == null) return out;
        for (FuelProductDaily fpd : d.fuelDailyByProduct.values()) {
            String label = classify(fpd.product);
            if (!out.containsKey(label)) continue;
            BigDecimal[] cell = out.get(label);
            for (DailyFuelRow row : fpd.dailyTotals.values()) {
                cell[0] = cell[0].add(row.netSale);
                cell[2] = cell[2].add(row.amount);
                if (row.rate != null && row.rate.compareTo(BigDecimal.ZERO) > 0) cell[1] = row.rate;
            }
        }
        return out;
    }

    private String classify(Product p) {
        if (p == null) return "OTHER";
        String fuelFamily = p.getFuelFamily() != null ? p.getFuelFamily().toUpperCase() : "";
        String name = p.getName() != null ? p.getName().toUpperCase() : "";
        String gradeName = p.getGrade() != null && p.getGrade().getName() != null ? p.getGrade().getName().toUpperCase() : "";
        if (fuelFamily.equals("DIESEL") || name.contains("DIESEL") || name.equals("HSD")) return "HSD";
        boolean petrol = fuelFamily.equals("PETROL") || name.contains("PETROL") || name.equals("MS") || name.contains("XTRA");
        if (!petrol) return "OTHER";
        boolean isPremium = name.contains("XTRA") || name.contains("PREMIUM") || name.equals("XP")
                || gradeName.contains("XTRA") || gradeName.contains("PREMIUM");
        return isPremium ? "XP" : "MS";
    }

    private String headerPeriod(LocalDate from, LocalDate to) {
        if (from.getYear() == to.getYear() && from.getMonth() == to.getMonth()
                && from.getDayOfMonth() == 1
                && to.getDayOfMonth() == to.lengthOfMonth()) {
            return MONTH_YEAR_FMT.format(from);
        }
        return DATE_FMT.format(from) + " — " + DATE_FMT.format(to);
    }

    private String fmtKl(BigDecimal kl) {
        if (kl == null || kl.compareTo(BigDecimal.ZERO) == 0) return "0";
        return kl.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String fmtQty(BigDecimal qty) {
        if (qty == null) return "0.00";
        return qty.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
