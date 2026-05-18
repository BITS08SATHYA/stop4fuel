package com.stopforfuel.backend.service;

import com.stopforfuel.backend.exception.ReportGenerationException;
import com.stopforfuel.backend.service.DailySalesRegisterService.CreditCustomerRow;
import com.stopforfuel.backend.service.DailySalesRegisterService.DailySalesRegisterData;
import com.stopforfuel.backend.service.DailySalesRegisterService.FuelDayBlock;
import com.stopforfuel.backend.service.DailySalesRegisterService.FuelSection;
import com.stopforfuel.backend.service.DailySalesRegisterService.LubeDayBlock;
import com.stopforfuel.backend.service.DailySalesRegisterService.LubricantSection;
import com.stopforfuel.backend.service.DailySalesRegisterService.PurchaseRow;
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
import java.util.Locale;

@Service
public class DailySalesRegisterExcelService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter MONTH_YEAR_FMT =
            DateTimeFormatter.ofPattern("MMMM-yyyy").withLocale(Locale.ENGLISH);
    private static final String INDIAN_NUM_FMT =
            "[>=10000000]##\\,##\\,##\\,##0.00;[>=100000]##\\,##\\,##0.00;##,##0.00";
    private static final String QTY_FMT = "##0.00";

    public byte[] fuel(DailySalesRegisterData d, String section) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles s = new Styles(wb);
            writeFuelSheet(wb, s, d, section);
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new ReportGenerationException("Failed to generate Daily Sales Register Excel", e);
        }
    }

    public byte[] lubricant(DailySalesRegisterData d) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles s = new Styles(wb);
            writeLubricantSheet(wb, s, d);
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new ReportGenerationException("Failed to generate Daily Sales Register Excel", e);
        }
    }

    public byte[] purchase(DailySalesRegisterData d) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles s = new Styles(wb);
            writePurchaseSheet(wb, s, d);
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new ReportGenerationException("Failed to generate Daily Sales Register Excel", e);
        }
    }

    // ===================== Fuel sheet =====================

    private void writeFuelSheet(XSSFWorkbook wb, Styles s, DailySalesRegisterData d, String section) {
        XSSFSheet sheet = wb.createSheet(section);
        configurePageSetup(sheet);
        String[] headers = {"S.NO", "DATE", "TOTAL SALES LITERS", "PRODUCT RATE", "TOTAL SALES AMOUNT",
                "PRODUCT NAME", "CASH SALES LITERS", "CASH SALES AMOUNT", "CUSTOMER NAME",
                "CREDIT LITERS", "RATE", "CREDIT LITER AMOUNT"};
        int row = titleAndHeader(sheet, s, d, section + " - " + headerPeriod(d.fromDate, d.toDate), headers);

        FuelSection fs = d.fuelSections != null ? d.fuelSections.get(section) : null;
        BigDecimal gTotL = BigDecimal.ZERO, gTotAmt = BigDecimal.ZERO, gCashL = BigDecimal.ZERO,
                gCashAmt = BigDecimal.ZERO, gCrL = BigDecimal.ZERO, gCrAmt = BigDecimal.ZERO;

        if (fs != null && fs.days != null) {
            for (FuelDayBlock blk : fs.days) {
                boolean hasCredit = blk.creditRows != null && !blk.creditRows.isEmpty();
                int span = hasCredit ? blk.creditRows.size() + 1 : 1;
                int blockStart = row;
                int blockEnd = row + span - 1;

                for (int rr = blockStart; rr <= blockEnd; rr++) sheet.createRow(rr);

                // Day-level columns 0..7 — value on first row, merged down the block.
                XSSFRow first = sheet.getRow(blockStart);
                setCell(first, 0, String.valueOf(blk.sno), s.center);
                setCell(first, 1, DATE_FMT.format(blk.date), s.center);
                setNumber(first, 2, blk.totalSalesLiters, s.qty);
                setNumber(first, 3, blk.productRate, s.num);
                setNumber(first, 4, blk.totalSalesAmount, s.num);
                setCell(first, 5, blk.productName, s.center);
                setNumber(first, 6, blk.cashSalesLiters, s.qty);
                setNumber(first, 7, blk.cashSalesAmount, s.num);
                for (int rr = blockStart + 1; rr <= blockEnd; rr++) {
                    XSSFRow rrow = sheet.getRow(rr);
                    for (int col = 0; col <= 7; col++) {
                        rrow.createCell(col).setCellStyle(col == 0 || col == 1 || col == 5 ? s.center : s.num);
                    }
                }
                if (span > 1) {
                    for (int col = 0; col <= 7; col++) {
                        sheet.addMergedRegion(new CellRangeAddress(blockStart, blockEnd, col, col));
                    }
                }

                if (!hasCredit) {
                    setCell(first, 8, "", s.left);
                    setCell(first, 9, "", s.qty);
                    setCell(first, 10, "", s.num);
                    setCell(first, 11, "", s.num);
                } else {
                    for (int i = 0; i < blk.creditRows.size(); i++) {
                        CreditCustomerRow r = blk.creditRows.get(i);
                        XSSFRow cr = sheet.getRow(blockStart + i);
                        setCell(cr, 8, r.customerName, s.left);
                        setNumber(cr, 9, r.creditLiters, s.qty);
                        setNumber(cr, 10, r.rate, s.num);
                        setNumber(cr, 11, r.creditLiterAmount, s.num);
                    }
                    XSSFRow sub = sheet.getRow(blockEnd);
                    setCell(sub, 8, "SUBTOTAL", s.totalLabel);
                    setNumber(sub, 9, blk.creditLitersSubtotal, s.qtyBold);
                    setCell(sub, 10, "", s.totalLabel);
                    setNumber(sub, 11, blk.creditAmountSubtotal, s.numBold);
                }

                gTotL = gTotL.add(blk.totalSalesLiters);
                gTotAmt = gTotAmt.add(blk.totalSalesAmount);
                gCashL = gCashL.add(blk.cashSalesLiters);
                gCashAmt = gCashAmt.add(blk.cashSalesAmount);
                gCrL = gCrL.add(blk.creditLitersSubtotal);
                gCrAmt = gCrAmt.add(blk.creditAmountSubtotal);
                row = blockEnd + 1;
            }
        }

        XSSFRow gt = sheet.createRow(row++);
        setCell(gt, 0, "GRAND TOTAL", s.totalLabel);
        setCell(gt, 1, "", s.totalLabel);
        setNumber(gt, 2, gTotL, s.qtyBold);
        setCell(gt, 3, "", s.totalLabel);
        setNumber(gt, 4, gTotAmt, s.numBold);
        setCell(gt, 5, "", s.totalLabel);
        setNumber(gt, 6, gCashL, s.qtyBold);
        setNumber(gt, 7, gCashAmt, s.numBold);
        setCell(gt, 8, "", s.totalLabel);
        setNumber(gt, 9, gCrL, s.qtyBold);
        setCell(gt, 10, "", s.totalLabel);
        setNumber(gt, 11, gCrAmt, s.numBold);

        int u = 256;
        int[] w = {6, 12, 16, 12, 16, 13, 15, 16, 24, 13, 11, 16};
        for (int i = 0; i < w.length; i++) sheet.setColumnWidth(i, w[i] * u);
        sheet.setRepeatingRows(new CellRangeAddress(0, 1, -1, -1));
    }

    // ===================== Lubricant sheet =====================

    private void writeLubricantSheet(XSSFWorkbook wb, Styles s, DailySalesRegisterData d) {
        XSSFSheet sheet = wb.createSheet("LUBRICANTS");
        configurePageSetup(sheet);
        String[] headers = {"S.NO", "DATE", "TOTAL SALES AMOUNT", "CUSTOMER NAME",
                "CREDIT LITERS/QTY", "RATE", "CREDIT LITER AMOUNT", "PRODUCT NAME", "CASH SALES AMOUNT"};
        int row = titleAndHeader(sheet, s, d, "LUBRICANTS - " + headerPeriod(d.fromDate, d.toDate), headers);

        LubricantSection ls = d.lubricant;
        BigDecimal gTotal = BigDecimal.ZERO, gCash = BigDecimal.ZERO, gCredit = BigDecimal.ZERO;

        if (ls != null && ls.days != null) {
            for (LubeDayBlock blk : ls.days) {
                boolean hasCredit = blk.creditRows != null && !blk.creditRows.isEmpty();
                int span = hasCredit ? blk.creditRows.size() : 1;
                int blockStart = row;
                int blockEnd = row + span - 1;
                for (int rr = blockStart; rr <= blockEnd; rr++) sheet.createRow(rr);

                XSSFRow first = sheet.getRow(blockStart);
                setCell(first, 0, String.valueOf(blk.sno), s.center);
                setCell(first, 1, DATE_FMT.format(blk.date), s.center);
                setNumber(first, 2, blk.totalSalesAmount, s.num);
                setNumber(first, 8, blk.cashSalesAmount, s.num);
                for (int rr = blockStart + 1; rr <= blockEnd; rr++) {
                    XSSFRow rrow = sheet.getRow(rr);
                    rrow.createCell(0).setCellStyle(s.center);
                    rrow.createCell(1).setCellStyle(s.center);
                    rrow.createCell(2).setCellStyle(s.num);
                    rrow.createCell(8).setCellStyle(s.num);
                }
                if (span > 1) {
                    for (int col : new int[]{0, 1, 2, 8}) {
                        sheet.addMergedRegion(new CellRangeAddress(blockStart, blockEnd, col, col));
                    }
                }

                if (!hasCredit) {
                    setCell(first, 3, "", s.left);
                    setCell(first, 4, "", s.qty);
                    setCell(first, 5, "", s.num);
                    setCell(first, 6, "", s.num);
                    setCell(first, 7, "", s.left);
                } else {
                    for (int i = 0; i < blk.creditRows.size(); i++) {
                        CreditCustomerRow r = blk.creditRows.get(i);
                        XSSFRow cr = sheet.getRow(blockStart + i);
                        setCell(cr, 3, r.customerName, s.left);
                        setNumber(cr, 4, r.creditLiters, s.qty);
                        setNumber(cr, 5, r.rate, s.num);
                        setNumber(cr, 6, r.creditLiterAmount, s.num);
                        setCell(cr, 7, r.productName != null ? r.productName : "", s.left);
                    }
                }

                gTotal = gTotal.add(blk.totalSalesAmount);
                gCash = gCash.add(blk.cashSalesAmount);
                gCredit = gCredit.add(blk.creditAmountSubtotal);
                row = blockEnd + 1;
            }
        }

        XSSFRow gt = sheet.createRow(row++);
        setCell(gt, 0, "GRAND TOTAL", s.totalLabel);
        setCell(gt, 1, "", s.totalLabel);
        setNumber(gt, 2, gTotal, s.numBold);
        setCell(gt, 3, "", s.totalLabel);
        setCell(gt, 4, "", s.totalLabel);
        setCell(gt, 5, "", s.totalLabel);
        setNumber(gt, 6, gCredit, s.numBold);
        setCell(gt, 7, "", s.totalLabel);
        setNumber(gt, 8, gCash, s.numBold);

        int u = 256;
        int[] w = {6, 12, 16, 24, 15, 11, 16, 18, 16};
        for (int i = 0; i < w.length; i++) sheet.setColumnWidth(i, w[i] * u);
        sheet.setRepeatingRows(new CellRangeAddress(0, 1, -1, -1));
    }

    // ===================== Purchase sheet =====================

    private void writePurchaseSheet(XSSFWorkbook wb, Styles s, DailySalesRegisterData d) {
        XSSFSheet sheet = wb.createSheet("PURCHASE");
        configurePageSetup(sheet);
        String[] headers = {"Invoice Date", "Invoice No", "Pty_Name", "Vch_Type", "GSTIN",
                "StateOfSupply", "Product_Name", "HSNCode", "Qty", "UOM", "TaxPer", "Taxable",
                "SGST%", "SGST Amt", "CGST%", "CGST Amt", "Total"};
        int row = titleAndHeader(sheet, s, d, "PURCHASE REGISTER - " + headerPeriod(d.fromDate, d.toDate), headers);

        BigDecimal tQty = BigDecimal.ZERO, tTax = BigDecimal.ZERO, tSgst = BigDecimal.ZERO,
                tCgst = BigDecimal.ZERO, tTotal = BigDecimal.ZERO;
        if (d.purchaseRows != null) {
            for (PurchaseRow r : d.purchaseRows) {
                XSSFRow rr = sheet.createRow(row++);
                setCell(rr, 0, r.invoiceDate != null ? DATE_FMT.format(r.invoiceDate) : "", s.center);
                setCell(rr, 1, safe(r.invoiceNo), s.center);
                setCell(rr, 2, safe(r.ptyName), s.left);
                setCell(rr, 3, safe(r.vchType), s.center);
                setCell(rr, 4, safe(r.gstin), s.center);
                setCell(rr, 5, safe(r.stateOfSupply), s.left);
                setCell(rr, 6, safe(r.productName), s.left);
                setCell(rr, 7, safe(r.hsnCode), s.center);
                setNumber(rr, 8, r.qty, s.qty);
                setCell(rr, 9, safe(r.uom), s.center);
                setNumber(rr, 10, r.taxPer, s.qty);
                setNumber(rr, 11, r.taxable, s.num);
                setNumber(rr, 12, r.sgstPer, s.qty);
                setNumber(rr, 13, r.sgstAmt, s.num);
                setNumber(rr, 14, r.cgstPer, s.qty);
                setNumber(rr, 15, r.cgstAmt, s.num);
                setNumber(rr, 16, r.total, s.num);
                tQty = tQty.add(r.qty);
                tTax = tTax.add(r.taxable);
                tSgst = tSgst.add(r.sgstAmt);
                tCgst = tCgst.add(r.cgstAmt);
                tTotal = tTotal.add(r.total);
            }
        }

        XSSFRow gt = sheet.createRow(row++);
        setCell(gt, 0, "NET TOTAL", s.totalLabel);
        for (int i = 1; i <= 7; i++) setCell(gt, i, "", s.totalLabel);
        setNumber(gt, 8, tQty, s.qtyBold);
        setCell(gt, 9, "", s.totalLabel);
        setCell(gt, 10, "", s.totalLabel);
        setNumber(gt, 11, tTax, s.numBold);
        setCell(gt, 12, "", s.totalLabel);
        setNumber(gt, 13, tSgst, s.numBold);
        setCell(gt, 14, "", s.totalLabel);
        setNumber(gt, 15, tCgst, s.numBold);
        setNumber(gt, 16, tTotal, s.numBold);

        int u = 256;
        int[] w = {12, 16, 26, 13, 17, 14, 14, 12, 10, 8, 9, 14, 8, 13, 8, 13, 14};
        for (int i = 0; i < w.length; i++) sheet.setColumnWidth(i, w[i] * u);
        sheet.setRepeatingRows(new CellRangeAddress(0, 1, -1, -1));
    }

    // ===================== helpers =====================

    private int titleAndHeader(XSSFSheet sheet, Styles s, DailySalesRegisterData d,
                               String titleText, String[] headers) {
        int last = headers.length - 1;
        XSSFRow r1 = sheet.createRow(0);
        r1.setHeightInPoints(26f);
        XSSFCell c0 = r1.createCell(0);
        c0.setCellValue((companyName(d) + " - " + titleText).trim().toUpperCase());
        c0.setCellStyle(s.title);
        for (int i = 1; i <= last; i++) r1.createCell(i).setCellStyle(s.title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, last));

        XSSFRow rh = sheet.createRow(1);
        rh.setHeightInPoints(28f);
        for (int i = 0; i < headers.length; i++) setCell(rh, i, headers[i], s.colHeader);
        return 2;
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

    private String companyName(DailySalesRegisterData d) {
        return d.company != null && d.company.getName() != null ? d.company.getName() : "";
    }

    private String safe(String x) {
        return x != null ? x : "";
    }

    private String headerPeriod(LocalDate from, LocalDate to) {
        if (from.getYear() == to.getYear() && from.getMonth() == to.getMonth()
                && from.getDayOfMonth() == 1
                && to.getDayOfMonth() == to.lengthOfMonth()) {
            return MONTH_YEAR_FMT.format(from);
        }
        return DATE_FMT.format(from) + " to " + DATE_FMT.format(to);
    }

    // ===================== styles =====================

    private static class Styles {
        final XSSFCellStyle title;
        final XSSFCellStyle colHeader;
        final XSSFCellStyle left;
        final XSSFCellStyle center;
        final XSSFCellStyle num;
        final XSSFCellStyle qty;
        final XSSFCellStyle numBold;
        final XSSFCellStyle qtyBold;
        final XSSFCellStyle totalLabel;

        Styles(XSSFWorkbook wb) {
            XSSFFont titleFont = wb.createFont(); titleFont.setBold(true); titleFont.setFontHeightInPoints((short) 13);
            XSSFFont headerFont = wb.createFont(); headerFont.setBold(true); headerFont.setFontHeightInPoints((short) 9);
            headerFont.setColor(new XSSFColor(new byte[]{(byte) 255, (byte) 255, (byte) 255}, null));
            XSSFFont normalFont = wb.createFont(); normalFont.setFontHeightInPoints((short) 9);
            XSSFFont boldFont = wb.createFont(); boldFont.setBold(true); boldFont.setFontHeightInPoints((short) 9);

            byte[] orange = {(byte) 230, (byte) 81, (byte) 0};
            byte[] grey = {(byte) 233, (byte) 236, (byte) 239};

            title = wb.createCellStyle();
            title.setFont(titleFont);
            title.setAlignment(HorizontalAlignment.CENTER);
            title.setVerticalAlignment(VerticalAlignment.CENTER);
            border(title);

            colHeader = wb.createCellStyle();
            colHeader.setFont(headerFont);
            colHeader.setAlignment(HorizontalAlignment.CENTER);
            colHeader.setVerticalAlignment(VerticalAlignment.CENTER);
            colHeader.setFillForegroundColor(new XSSFColor(orange, null));
            colHeader.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            colHeader.setWrapText(true);
            border(colHeader);

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
            totalLabel.setVerticalAlignment(VerticalAlignment.CENTER);
            totalLabel.setFillForegroundColor(new XSSFColor(grey, null));
            totalLabel.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            border(totalLabel);
        }

        private static void border(XSSFCellStyle st) {
            st.setBorderTop(BorderStyle.THIN);
            st.setBorderBottom(BorderStyle.THIN);
            st.setBorderLeft(BorderStyle.THIN);
            st.setBorderRight(BorderStyle.THIN);
        }
    }
}
