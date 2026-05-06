package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Product;
import com.stopforfuel.backend.entity.PurchaseInvoice;
import com.stopforfuel.backend.entity.PurchaseInvoiceItem;
import com.stopforfuel.backend.exception.ReportGenerationException;
import com.stopforfuel.backend.service.VatReportService.DailyFuelRow;
import com.stopforfuel.backend.service.VatReportService.FuelProductDaily;
import com.stopforfuel.backend.service.VatReportService.VatReportData;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class VatReportExcelService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter MONTH_YEAR_FMT = DateTimeFormatter.ofPattern("MMMM-yyyy").withLocale(java.util.Locale.ENGLISH);
    private static final String INDIAN_NUM_FMT =
            "[>=10000000]##\\,##\\,##\\,##0.00;[>=100000]##\\,##\\,##0.00;##,##0.00";
    private static final String QTY_FMT = "##0.00";

    public byte[] generate(VatReportData data) {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Styles s = new Styles(wb);
            writePurchaseRegister(wb, s, data);
            writeGstComputation(wb, s, data);
            writeDailyLubeSales(wb, s, data);
            writeDailyFuelSales(wb, s, data);

            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new ReportGenerationException("Failed to generate VAT Excel report", e);
        }
    }

    // ============================== SHEET 1: PURCHASE REGISTER ==============================

    private void writePurchaseRegister(XSSFWorkbook wb, Styles s, VatReportData d) {
        XSSFSheet sheet = wb.createSheet("Purchase Register");
        configurePageSetup(sheet);

        String period = headerPeriod(d.fromDate, d.toDate);
        String title = (companyName(d) + " - " + period.toUpperCase()).trim();

        // R1: title (merged 8 cols)
        XSSFRow r1 = sheet.createRow(0);
        r1.setHeightInPoints(28f);
        XSSFCell c0 = r1.createCell(0);
        c0.setCellValue(title);
        c0.setCellStyle(s.title);
        for (int i = 1; i < 8; i++) r1.createCell(i).setCellStyle(s.title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 7));

        // R3: column-group header — DATE | INVOICE NO | XP (PETROL premium) | MS (PETROL regular) | HSD (DIESEL)
        XSSFRow r3 = sheet.createRow(2);
        r3.setHeightInPoints(22f);
        setCell(r3, 0, "DATE", s.colHeader);
        setCell(r3, 1, "INVOICE NO", s.colHeader);
        setCell(r3, 2, "XTRA PREMIUM (PETROL)", s.colHeader);
        setCell(r3, 3, "", s.colHeader);
        setCell(r3, 4, "MS (PETROL)", s.colHeader);
        setCell(r3, 5, "", s.colHeader);
        setCell(r3, 6, "HSD (DIESEL)", s.colHeader);
        setCell(r3, 7, "", s.colHeader);
        sheet.addMergedRegion(new CellRangeAddress(2, 2, 2, 3));
        sheet.addMergedRegion(new CellRangeAddress(2, 2, 4, 5));
        sheet.addMergedRegion(new CellRangeAddress(2, 2, 6, 7));

        // R4: subheaders KL / AMOUNT under each product
        XSSFRow r4 = sheet.createRow(3);
        r4.setHeightInPoints(18f);
        setCell(r4, 0, "", s.colHeader);
        setCell(r4, 1, "", s.colHeader);
        setCell(r4, 2, "KL", s.colHeader);
        setCell(r4, 3, "AMOUNT", s.colHeader);
        setCell(r4, 4, "KL", s.colHeader);
        setCell(r4, 5, "AMOUNT", s.colHeader);
        setCell(r4, 6, "KL", s.colHeader);
        setCell(r4, 7, "AMOUNT", s.colHeader);

        // Data rows: one per purchase invoice
        int rowIdx = 4;
        BigDecimal[] totals = new BigDecimal[6]; // xpKL, xpAmt, msKL, msAmt, hsdKL, hsdAmt
        for (int i = 0; i < 6; i++) totals[i] = BigDecimal.ZERO;

        if (d.purchaseInvoices != null) {
            for (PurchaseInvoice pi : d.purchaseInvoices) {
                XSSFRow row = sheet.createRow(rowIdx++);
                row.setHeightInPoints(20f);
                setCell(row, 0, pi.getInvoiceDate() != null ? DATE_FMT.format(pi.getInvoiceDate()) : "", s.center);
                setCell(row, 1, pi.getInvoiceNumber() != null ? pi.getInvoiceNumber() : "", s.center);

                BigDecimal xpKL = BigDecimal.ZERO, xpAmt = BigDecimal.ZERO;
                BigDecimal msKL = BigDecimal.ZERO, msAmt = BigDecimal.ZERO;
                BigDecimal hsdKL = BigDecimal.ZERO, hsdAmt = BigDecimal.ZERO;
                if (pi.getItems() != null) {
                    for (PurchaseInvoiceItem item : pi.getItems()) {
                        Product p = item.getProduct();
                        if (p == null) continue;
                        BigDecimal litres = item.getQuantity() != null ? BigDecimal.valueOf(item.getQuantity()) : BigDecimal.ZERO;
                        BigDecimal kl = litres.divide(new BigDecimal("1000"), 4, RoundingMode.HALF_UP);
                        BigDecimal amt = item.getTotalPrice() != null ? item.getTotalPrice() : BigDecimal.ZERO;
                        switch (classify(p)) {
                            case XP -> { xpKL = xpKL.add(kl); xpAmt = xpAmt.add(amt); }
                            case MS -> { msKL = msKL.add(kl); msAmt = msAmt.add(amt); }
                            case HSD -> { hsdKL = hsdKL.add(kl); hsdAmt = hsdAmt.add(amt); }
                            default -> { /* lubricants etc. — not in this register */ }
                        }
                    }
                }
                writeKlAmt(row, 2, xpKL, xpAmt, s);
                writeKlAmt(row, 4, msKL, msAmt, s);
                writeKlAmt(row, 6, hsdKL, hsdAmt, s);
                totals[0] = totals[0].add(xpKL); totals[1] = totals[1].add(xpAmt);
                totals[2] = totals[2].add(msKL); totals[3] = totals[3].add(msAmt);
                totals[4] = totals[4].add(hsdKL); totals[5] = totals[5].add(hsdAmt);
            }
        }

        // NET TOTAL row
        XSSFRow trow = sheet.createRow(rowIdx++);
        trow.setHeightInPoints(20f);
        setCell(trow, 0, "NET TOTAL", s.totalLabel);
        setCell(trow, 1, "", s.totalLabel);
        for (int i = 0; i < 3; i++) {
            writeKlAmt(trow, 2 + i * 2, totals[i * 2], totals[i * 2 + 1], s.qtyBold, s.numBold);
        }

        // ----- Product summary block (sales side relies on fuelDailyByProduct + lubricantTotal) -----
        rowIdx += 2;
        XSSFRow head = sheet.createRow(rowIdx++);
        head.setHeightInPoints(18f);
        setCell(head, 0, "Product", s.colHeader);
        setCell(head, 1, "Purchase Amount", s.colHeader);
        setCell(head, 2, "", s.colHeader);
        setCell(head, 3, "VAT", s.colHeader);
        setCell(head, 4, "Product", s.colHeader);
        setCell(head, 5, "Liters", s.colHeader);
        setCell(head, 6, "Sales Amount", s.colHeader);
        setCell(head, 7, "VAT", s.colHeader);

        Map<String, BigDecimal[]> salesByLabel = computeFuelSalesByLabel(d); // label -> [litres, amount]
        BigDecimal totalPurchase = BigDecimal.ZERO;
        BigDecimal totalSales = BigDecimal.ZERO;
        BigDecimal totalLitresSold = BigDecimal.ZERO;

        String[] labels = {"XP", "MS", "HSD"};
        BigDecimal[] purchaseByLabel = {totals[1], totals[3], totals[5]};
        for (int i = 0; i < 3; i++) {
            String label = labels[i];
            BigDecimal purchaseAmt = purchaseByLabel[i];
            BigDecimal[] sales = salesByLabel.getOrDefault(label, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal litres = sales[0];
            BigDecimal amount = sales[1];
            XSSFRow row = sheet.createRow(rowIdx++);
            setCell(row, 0, label, s.left);
            setNumber(row, 1, purchaseAmt, s.num);
            setCell(row, 2, "", s.left);
            setCell(row, 3, "", s.left); // fuel VAT not declared in the sample
            setCell(row, 4, label, s.left);
            setNumber(row, 5, litres, s.qty);
            setNumber(row, 6, amount, s.num);
            setCell(row, 7, "", s.left);
            totalPurchase = totalPurchase.add(purchaseAmt);
            totalSales = totalSales.add(amount);
            totalLitresSold = totalLitresSold.add(litres);
        }

        XSSFRow trow2 = sheet.createRow(rowIdx++);
        setCell(trow2, 0, "TOTAL", s.totalLabel);
        setNumber(trow2, 1, totalPurchase, s.numBold);
        setCell(trow2, 2, "", s.totalLabel);
        setCell(trow2, 3, "", s.totalLabel);
        setCell(trow2, 4, "TOTAL", s.totalLabel);
        setNumber(trow2, 5, totalLitresSold, s.qtyBold);
        setNumber(trow2, 6, totalSales, s.numBold);
        setCell(trow2, 7, "", s.totalLabel);

        // LUBRICANT row
        XSSFRow lubeRow = sheet.createRow(rowIdx++);
        setCell(lubeRow, 0, "LUBRICANT", s.left);
        setCell(lubeRow, 1, "", s.left);
        setCell(lubeRow, 2, "", s.left);
        setCell(lubeRow, 3, "", s.left);
        setCell(lubeRow, 4, "LUBES 18%", s.left);
        setCell(lubeRow, 5, "", s.left);
        setNumber(lubeRow, 6, d.lubricantTotal, s.num);
        setNumber(lubeRow, 7, d.netVat18, s.num);

        rowIdx++;
        // Grand total: includes lubes on the sales side
        XSSFRow gt = sheet.createRow(rowIdx++);
        setCell(gt, 0, "", s.totalLabel);
        setNumber(gt, 1, totalPurchase, s.numBold);
        setCell(gt, 2, "", s.totalLabel);
        setCell(gt, 3, "", s.totalLabel);
        setCell(gt, 4, "", s.totalLabel);
        setCell(gt, 5, "", s.totalLabel);
        setNumber(gt, 6, totalSales.add(d.lubricantTotal), s.numBold);
        setCell(gt, 7, "", s.totalLabel);

        // Column widths
        int u = 256;
        sheet.setColumnWidth(0, 13 * u);
        sheet.setColumnWidth(1, 16 * u);
        sheet.setColumnWidth(2, 9 * u);
        sheet.setColumnWidth(3, 14 * u);
        sheet.setColumnWidth(4, 9 * u);
        sheet.setColumnWidth(5, 14 * u);
        sheet.setColumnWidth(6, 9 * u);
        sheet.setColumnWidth(7, 14 * u);
        sheet.setRepeatingRows(new CellRangeAddress(0, 3, -1, -1));
    }

    private void writeKlAmt(XSSFRow row, int colStart, BigDecimal kl, BigDecimal amt, Styles s) {
        writeKlAmt(row, colStart, kl, amt, s.qty, s.num);
    }

    private void writeKlAmt(XSSFRow row, int colStart, BigDecimal kl, BigDecimal amt,
                            XSSFCellStyle qtyStyle, XSSFCellStyle numStyle) {
        if (kl.compareTo(BigDecimal.ZERO) > 0) setNumber(row, colStart, kl, qtyStyle);
        else { row.createCell(colStart).setCellStyle(qtyStyle); }
        if (amt.compareTo(BigDecimal.ZERO) > 0) setNumber(row, colStart + 1, amt, numStyle);
        else { row.createCell(colStart + 1).setCellStyle(numStyle); }
    }

    // ============================== SHEET 2: GST COMPUTATION ==============================

    private void writeGstComputation(XSSFWorkbook wb, Styles s, VatReportData d) {
        XSSFSheet sheet = wb.createSheet("GST Computation");
        configurePageSetup(sheet);

        int row = 0;
        XSSFRow r1 = sheet.createRow(row++);
        r1.setHeightInPoints(24f);
        XSSFCell c = r1.createCell(0);
        c.setCellValue((companyName(d) + " - " + headerPeriod(d.fromDate, d.toDate).toUpperCase()).trim());
        c.setCellStyle(s.title);
        for (int i = 1; i < 6; i++) r1.createCell(i).setCellStyle(s.title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 5));

        XSSFRow r2 = sheet.createRow(row++);
        XSSFCell c2 = r2.createCell(0);
        c2.setCellValue("OIL SALES TAX GST-18%");
        c2.setCellStyle(s.sectionLabel);
        for (int i = 1; i < 6; i++) r2.createCell(i).setCellStyle(s.sectionLabel);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 5));

        row++; // spacer

        kvRow(sheet, row++, "SALES (incl. tax)", d.taxIncludedSales, s);
        kvRow(sheet, row++, "TAX INCLUDED SALES", d.taxIncludedSales, s);
        kvRow(sheet, row++, "TAX EXCLUDED SALES (sales × 100/118)", d.taxExcludedSales, s);
        kvRow(sheet, row++, "NET VAT 18%", d.netVat18, s);
        kvRow(sheet, row++, "SGST 9%", d.sgst9, s);
        kvRow(sheet, row++, "CGST 9%", d.cgst9, s);

        sheet.setColumnWidth(0, 35 * 256);
        sheet.setColumnWidth(1, 18 * 256);
        sheet.setRepeatingRows(new CellRangeAddress(0, 1, -1, -1));
    }

    private void kvRow(XSSFSheet sheet, int rowIdx, String label, BigDecimal value, Styles s) {
        XSSFRow row = sheet.createRow(rowIdx);
        row.setHeightInPoints(20f);
        setCell(row, 0, label, s.left);
        setNumber(row, 1, value != null ? value : BigDecimal.ZERO, s.num);
    }

    // ============================== SHEET 3: DAILY LUBE SALES ==============================

    private void writeDailyLubeSales(XSSFWorkbook wb, Styles s, VatReportData d) {
        XSSFSheet sheet = wb.createSheet("Daily Lube Sales");
        configurePageSetup(sheet);

        int row = 0;
        XSSFRow r1 = sheet.createRow(row++);
        r1.setHeightInPoints(24f);
        XSSFCell c = r1.createCell(0);
        c.setCellValue(("DAILY OIL SALES - " + headerPeriod(d.fromDate, d.toDate)).toUpperCase());
        c.setCellStyle(s.title);
        for (int i = 1; i < 2; i++) r1.createCell(i).setCellStyle(s.title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 1));

        XSSFRow rh = sheet.createRow(row++);
        rh.setHeightInPoints(20f);
        setCell(rh, 0, "DATE", s.colHeader);
        setCell(rh, 1, "PRICE", s.colHeader);

        BigDecimal total = BigDecimal.ZERO;
        if (d.lubricantDailySales != null) {
            for (Map.Entry<LocalDate, BigDecimal> e : d.lubricantDailySales.entrySet()) {
                XSSFRow r = sheet.createRow(row++);
                setCell(r, 0, DATE_FMT.format(e.getKey()), s.center);
                setNumber(r, 1, e.getValue(), s.num);
                total = total.add(e.getValue());
            }
        }

        XSSFRow t = sheet.createRow(row++);
        setCell(t, 0, "TOTAL", s.totalLabel);
        setNumber(t, 1, total, s.numBold);

        sheet.setColumnWidth(0, 14 * 256);
        sheet.setColumnWidth(1, 16 * 256);
        sheet.setRepeatingRows(new CellRangeAddress(0, 1, -1, -1));
    }

    // ============================== SHEET 4: DAILY FUEL SALES PER PRODUCT ==============================

    private void writeDailyFuelSales(XSSFWorkbook wb, Styles s, VatReportData d) {
        XSSFSheet sheet = wb.createSheet("Daily Fuel Sales");
        configurePageSetup(sheet);

        int row = 0;
        XSSFRow r1 = sheet.createRow(row++);
        r1.setHeightInPoints(24f);
        XSSFCell c = r1.createCell(0);
        c.setCellValue((companyName(d) + " - DAILY FUEL SALES - " + headerPeriod(d.fromDate, d.toDate).toUpperCase()).trim());
        c.setCellStyle(s.title);
        for (int i = 1; i < 6; i++) r1.createCell(i).setCellStyle(s.title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 5));
        row++;

        // Order: XP, MS, HSD (each as its own header + table block)
        Map<String, FuelProductDaily> byLabel = orderByLabel(d);
        for (Map.Entry<String, FuelProductDaily> e : byLabel.entrySet()) {
            String label = e.getKey();
            FuelProductDaily fpd = e.getValue();

            XSSFRow secHead = sheet.createRow(row++);
            XSSFCell h = secHead.createCell(0);
            h.setCellValue(label + (fpd != null && fpd.product != null ? " (" + fpd.product.getName() + ")" : "") + " - " + headerPeriod(d.fromDate, d.toDate));
            h.setCellStyle(s.sectionLabel);
            for (int i = 1; i < 6; i++) secHead.createCell(i).setCellStyle(s.sectionLabel);
            sheet.addMergedRegion(new CellRangeAddress(row - 1, row - 1, 0, 5));

            XSSFRow rh = sheet.createRow(row++);
            setCell(rh, 0, "DATE", s.colHeader);
            setCell(rh, 1, "LTRS", s.colHeader);
            setCell(rh, 2, "TEST", s.colHeader);
            setCell(rh, 3, "NET SALE", s.colHeader);
            setCell(rh, 4, "RATE", s.colHeader);
            setCell(rh, 5, "AMOUNT", s.colHeader);

            BigDecimal totLitres = BigDecimal.ZERO, totTest = BigDecimal.ZERO,
                    totNet = BigDecimal.ZERO, totAmt = BigDecimal.ZERO;
            if (fpd != null && fpd.dailyTotals != null) {
                for (Map.Entry<LocalDate, DailyFuelRow> de : fpd.dailyTotals.entrySet()) {
                    DailyFuelRow r = de.getValue();
                    XSSFRow row2 = sheet.createRow(row++);
                    setCell(row2, 0, DATE_FMT.format(de.getKey()), s.center);
                    setNumber(row2, 1, r.litres, s.qty);
                    setNumber(row2, 2, r.test, s.qty);
                    setNumber(row2, 3, r.netSale, s.qty);
                    setNumber(row2, 4, r.rate, s.num);
                    setNumber(row2, 5, r.amount, s.num);
                    totLitres = totLitres.add(r.litres);
                    totTest = totTest.add(r.test);
                    totNet = totNet.add(r.netSale);
                    totAmt = totAmt.add(r.amount);
                }
            }

            XSSFRow tr = sheet.createRow(row++);
            setCell(tr, 0, "TOTAL", s.totalLabel);
            setNumber(tr, 1, totLitres, s.qtyBold);
            setNumber(tr, 2, totTest, s.qtyBold);
            setNumber(tr, 3, totNet, s.qtyBold);
            setCell(tr, 4, "", s.totalLabel);
            setNumber(tr, 5, totAmt, s.numBold);

            row++; // gap between blocks
        }

        int u = 256;
        sheet.setColumnWidth(0, 14 * u);
        sheet.setColumnWidth(1, 11 * u);
        sheet.setColumnWidth(2, 10 * u);
        sheet.setColumnWidth(3, 12 * u);
        sheet.setColumnWidth(4, 11 * u);
        sheet.setColumnWidth(5, 14 * u);
        sheet.setRepeatingRows(new CellRangeAddress(0, 1, -1, -1));
    }

    // ============================== HELPERS ==============================

    private Map<String, BigDecimal[]> computeFuelSalesByLabel(VatReportData d) {
        Map<String, BigDecimal[]> out = new LinkedHashMap<>();
        out.put("XP", new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        out.put("MS", new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        out.put("HSD", new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        if (d.fuelDailyByProduct == null) return out;
        for (FuelProductDaily fpd : d.fuelDailyByProduct.values()) {
            String label = classify(fpd.product).label();
            BigDecimal[] cell = out.computeIfAbsent(label, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            for (DailyFuelRow row : fpd.dailyTotals.values()) {
                cell[0] = cell[0].add(row.netSale);
                cell[1] = cell[1].add(row.amount);
            }
        }
        return out;
    }

    private Map<String, FuelProductDaily> orderByLabel(VatReportData d) {
        Map<String, FuelProductDaily> out = new LinkedHashMap<>();
        out.put("XP", null);
        out.put("MS", null);
        out.put("HSD", null);
        if (d.fuelDailyByProduct == null) return out;
        for (FuelProductDaily fpd : d.fuelDailyByProduct.values()) {
            String label = classify(fpd.product).label();
            // Merge if multiple products land in the same label (rare — XP grade variants)
            FuelProductDaily existing = out.get(label);
            if (existing == null) {
                out.put(label, fpd);
            } else if (existing.dailyTotals != null && fpd.dailyTotals != null) {
                for (Map.Entry<LocalDate, DailyFuelRow> e : fpd.dailyTotals.entrySet()) {
                    existing.dailyTotals.merge(e.getKey(), e.getValue(), VatReportExcelService::mergeRows);
                }
            }
        }
        return out;
    }

    private static DailyFuelRow mergeRows(DailyFuelRow a, DailyFuelRow b) {
        DailyFuelRow r = new DailyFuelRow();
        r.litres = a.litres.add(b.litres);
        r.test = a.test.add(b.test);
        r.netSale = a.netSale.add(b.netSale);
        r.rate = a.rate; // keep first
        r.amount = a.amount.add(b.amount);
        return r;
    }

    private FuelLabel classify(Product p) {
        if (p == null) return FuelLabel.OTHER;
        String fuelFamily = p.getFuelFamily() != null ? p.getFuelFamily().toUpperCase() : "";
        String name = p.getName() != null ? p.getName().toUpperCase() : "";
        String gradeName = p.getGrade() != null && p.getGrade().getName() != null ? p.getGrade().getName().toUpperCase() : "";
        if (fuelFamily.equals("DIESEL") || name.contains("DIESEL") || name.equals("HSD")) return FuelLabel.HSD;
        boolean petrol = fuelFamily.equals("PETROL") || name.contains("PETROL") || name.equals("MS") || name.contains("XTRA");
        if (!petrol) return FuelLabel.OTHER;
        boolean isPremium = name.contains("XTRA") || name.contains("PREMIUM") || name.equals("XP")
                || gradeName.contains("XTRA") || gradeName.contains("PREMIUM");
        return isPremium ? FuelLabel.XP : FuelLabel.MS;
    }

    private enum FuelLabel {
        XP("XP"), MS("MS"), HSD("HSD"), OTHER("OTHER");
        private final String label;
        FuelLabel(String l) { this.label = l; }
        String label() { return label; }
    }

    private void configurePageSetup(XSSFSheet sheet) {
        sheet.setFitToPage(true);
        sheet.getPrintSetup().setLandscape(true);
        sheet.getPrintSetup().setFitWidth((short) 1);
        sheet.getPrintSetup().setFitHeight((short) 0);
    }

    private void setCell(XSSFRow row, int col, String val, XSSFCellStyle style) {
        XSSFCell c = row.createCell(col);
        c.setCellValue(val == null ? "" : val);
        c.setCellStyle(style);
    }

    private void setNumber(XSSFRow row, int col, BigDecimal val, XSSFCellStyle style) {
        XSSFCell c = row.createCell(col);
        c.setCellValue(val != null ? val.doubleValue() : 0d);
        c.setCellStyle(style);
    }

    private String companyName(VatReportData d) {
        return d.company != null && d.company.getName() != null ? d.company.getName() : "";
    }

    private String headerPeriod(LocalDate from, LocalDate to) {
        if (from.getYear() == to.getYear() && from.getMonth() == to.getMonth()
                && from.getDayOfMonth() == 1
                && to.getDayOfMonth() == to.lengthOfMonth()) {
            return MONTH_YEAR_FMT.format(from);
        }
        return DATE_FMT.format(from) + " to " + DATE_FMT.format(to);
    }

    // ============================== STYLES ==============================

    private static class Styles {
        final XSSFCellStyle title;
        final XSSFCellStyle colHeader;
        final XSSFCellStyle sectionLabel;
        final XSSFCellStyle left;
        final XSSFCellStyle center;
        final XSSFCellStyle num;
        final XSSFCellStyle qty;
        final XSSFCellStyle numBold;
        final XSSFCellStyle qtyBold;
        final XSSFCellStyle totalLabel;

        Styles(XSSFWorkbook wb) {
            XSSFFont titleFont = wb.createFont(); titleFont.setBold(true); titleFont.setFontHeightInPoints((short) 14);
            XSSFFont headerFont = wb.createFont(); headerFont.setBold(true); headerFont.setFontHeightInPoints((short) 10);
            XSSFFont normalFont = wb.createFont(); normalFont.setFontHeightInPoints((short) 10);
            XSSFFont boldFont = wb.createFont(); boldFont.setBold(true); boldFont.setFontHeightInPoints((short) 10);

            title = wb.createCellStyle();
            title.setFont(titleFont);
            title.setAlignment(HorizontalAlignment.CENTER);
            title.setVerticalAlignment(VerticalAlignment.CENTER);
            border(title);

            colHeader = wb.createCellStyle();
            colHeader.setFont(headerFont);
            colHeader.setAlignment(HorizontalAlignment.CENTER);
            colHeader.setVerticalAlignment(VerticalAlignment.CENTER);
            colHeader.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 220, (byte) 220, (byte) 220}, null));
            colHeader.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            colHeader.setWrapText(true);
            border(colHeader);

            sectionLabel = wb.createCellStyle();
            sectionLabel.setFont(boldFont);
            sectionLabel.setAlignment(HorizontalAlignment.LEFT);
            sectionLabel.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 233, (byte) 236, (byte) 239}, null));
            sectionLabel.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            border(sectionLabel);

            left = wb.createCellStyle();
            left.setFont(normalFont);
            left.setAlignment(HorizontalAlignment.LEFT);
            left.setVerticalAlignment(VerticalAlignment.CENTER);
            border(left);

            center = wb.createCellStyle();
            center.setFont(normalFont);
            center.setAlignment(HorizontalAlignment.CENTER);
            center.setVerticalAlignment(VerticalAlignment.CENTER);
            border(center);

            num = wb.createCellStyle();
            num.setFont(normalFont);
            num.setAlignment(HorizontalAlignment.RIGHT);
            num.setVerticalAlignment(VerticalAlignment.CENTER);
            num.setDataFormat(wb.createDataFormat().getFormat(INDIAN_NUM_FMT));
            border(num);

            qty = wb.createCellStyle();
            qty.setFont(normalFont);
            qty.setAlignment(HorizontalAlignment.RIGHT);
            qty.setVerticalAlignment(VerticalAlignment.CENTER);
            qty.setDataFormat(wb.createDataFormat().getFormat(QTY_FMT));
            border(qty);

            numBold = wb.createCellStyle();
            numBold.cloneStyleFrom(num);
            numBold.setFont(boldFont);

            qtyBold = wb.createCellStyle();
            qtyBold.cloneStyleFrom(qty);
            qtyBold.setFont(boldFont);

            totalLabel = wb.createCellStyle();
            totalLabel.setFont(boldFont);
            totalLabel.setAlignment(HorizontalAlignment.LEFT);
            totalLabel.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 233, (byte) 236, (byte) 239}, null));
            totalLabel.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            border(totalLabel);
        }

        private static void border(XSSFCellStyle s) {
            s.setBorderTop(BorderStyle.THIN);
            s.setBorderBottom(BorderStyle.THIN);
            s.setBorderLeft(BorderStyle.THIN);
            s.setBorderRight(BorderStyle.THIN);
        }
    }
}
