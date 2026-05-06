package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.IncentivePayment;
import com.stopforfuel.backend.exception.ReportGenerationException;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class IncentivePaymentExcelService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
    private static final int TITLE_ROW = 0;
    private static final int HEADER_ROW = 1;
    private static final int DATA_START_ROW = 2;
    private static final int COL_COUNT = 8;
    private static final String INDIAN_NUM_FMT =
            "[>=10000000]##\\,##\\,##\\,##0.00;[>=100000]##\\,##\\,##0.00;##,##0.00";

    public byte[] generateExcel(List<IncentivePayment> payments,
                                String companyName,
                                LocalDate fromDate,
                                LocalDate toDate) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet("Incentive Payments");

            configurePageSetup(sheet);

            XSSFCellStyle titleStyle = buildTitleStyle(workbook);
            XSSFCellStyle headerStyle = buildHeaderStyle(workbook);
            XSSFCellStyle textStyle = buildBodyStyle(workbook, HorizontalAlignment.LEFT);
            XSSFCellStyle centerStyle = buildBodyStyle(workbook, HorizontalAlignment.CENTER);
            XSSFCellStyle numStyle = buildNumberStyle(workbook, false);
            XSSFCellStyle totalLabelStyle = buildTotalLabelStyle(workbook);
            XSSFCellStyle totalNumStyle = buildNumberStyle(workbook, true);

            writeTitle(sheet, titleStyle, companyName, fromDate, toDate);
            writeHeader(sheet, headerStyle);
            applyColumnWidths(sheet);

            int rowIdx = DATA_START_ROW;
            int serial = 1;
            BigDecimal total = BigDecimal.ZERO;
            for (IncentivePayment p : payments) {
                writeDataRow(sheet, rowIdx, serial, p, textStyle, centerStyle, numStyle);
                if (p.getAmount() != null) total = total.add(p.getAmount());
                rowIdx++;
                serial++;
            }

            writeTotalRow(sheet, rowIdx, payments.size(), total, totalLabelStyle, totalNumStyle);

            sheet.setRepeatingRows(new CellRangeAddress(TITLE_ROW, HEADER_ROW, -1, -1));
            sheet.getPrintSetup().setFitWidth((short) 1);
            sheet.getPrintSetup().setFitHeight((short) 0);

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new ReportGenerationException("Failed to generate Incentive Payment Excel report", e);
        }
    }

    private void configurePageSetup(XSSFSheet sheet) {
        PrintSetup ps = sheet.getPrintSetup();
        ps.setLandscape(true);
        ps.setPaperSize(PrintSetup.A4_PAPERSIZE);
        sheet.setFitToPage(true);
        sheet.setHorizontallyCenter(true);
        sheet.setMargin(Sheet.LeftMargin, 0.3);
        sheet.setMargin(Sheet.RightMargin, 0.3);
        sheet.setMargin(Sheet.TopMargin, 0.4);
        sheet.setMargin(Sheet.BottomMargin, 0.4);
        sheet.setMargin(Sheet.HeaderMargin, 0.2);
        sheet.setMargin(Sheet.FooterMargin, 0.2);
    }

    private XSSFCellStyle buildTitleStyle(XSSFWorkbook workbook) {
        XSSFCellStyle s = workbook.createCellStyle();
        XSSFFont f = workbook.createFont();
        f.setBold(true);
        f.setFontHeightInPoints((short) 14);
        s.setFont(f);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        applyAllBorders(s);
        return s;
    }

    private XSSFCellStyle buildHeaderStyle(XSSFWorkbook workbook) {
        XSSFCellStyle s = workbook.createCellStyle();
        XSSFFont f = workbook.createFont();
        f.setBold(true);
        f.setFontHeightInPoints((short) 10);
        s.setFont(f);
        s.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 220, (byte) 220, (byte) 220}, null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        s.setWrapText(true);
        applyAllBorders(s);
        return s;
    }

    private XSSFCellStyle buildBodyStyle(XSSFWorkbook workbook, HorizontalAlignment hAlign) {
        XSSFCellStyle s = workbook.createCellStyle();
        XSSFFont f = workbook.createFont();
        f.setFontHeightInPoints((short) 10);
        s.setFont(f);
        s.setAlignment(hAlign);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        s.setWrapText(true);
        applyAllBorders(s);
        return s;
    }

    private XSSFCellStyle buildNumberStyle(XSSFWorkbook workbook, boolean bold) {
        XSSFCellStyle s = workbook.createCellStyle();
        XSSFFont f = workbook.createFont();
        f.setFontHeightInPoints((short) 10);
        f.setBold(bold);
        s.setFont(f);
        s.setAlignment(HorizontalAlignment.RIGHT);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        s.setDataFormat(workbook.createDataFormat().getFormat(INDIAN_NUM_FMT));
        applyAllBorders(s);
        return s;
    }

    private XSSFCellStyle buildTotalLabelStyle(XSSFWorkbook workbook) {
        XSSFCellStyle s = workbook.createCellStyle();
        XSSFFont f = workbook.createFont();
        f.setBold(true);
        f.setFontHeightInPoints((short) 10);
        s.setFont(f);
        s.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 233, (byte) 236, (byte) 239}, null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.RIGHT);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        applyAllBorders(s);
        return s;
    }

    private void applyAllBorders(XSSFCellStyle s) {
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
    }

    private void writeTitle(XSSFSheet sheet, XSSFCellStyle titleStyle,
                            String companyName, LocalDate fromDate, LocalDate toDate) {
        XSSFRow row = sheet.createRow(TITLE_ROW);
        row.setHeightInPoints(28f);
        String name = (companyName == null || companyName.isBlank()) ? "" : companyName.toUpperCase();
        String period = "(" + DATE_FMT.format(fromDate) + " - " + DATE_FMT.format(toDate) + ")";
        String title = (name.isEmpty() ? "INCENTIVE PAYMENTS " : name + " / INCENTIVE PAYMENTS ") + period;
        XSSFCell c = row.createCell(0);
        c.setCellValue(title);
        c.setCellStyle(titleStyle);
        for (int i = 1; i < COL_COUNT; i++) {
            XSSFCell empty = row.createCell(i);
            empty.setCellStyle(titleStyle);
        }
        sheet.addMergedRegion(new CellRangeAddress(TITLE_ROW, TITLE_ROW, 0, COL_COUNT - 1));
    }

    private void writeHeader(XSSFSheet sheet, XSSFCellStyle headerStyle) {
        String[] headers = {
                "S.NO",
                "Date",
                "Customer",
                "Amount",
                "Description",
                "Bill / Statement",
                "Shift",
                "Recorded At"
        };
        XSSFRow row = sheet.createRow(HEADER_ROW);
        row.setHeightInPoints(28f);
        for (int i = 0; i < headers.length; i++) {
            XSSFCell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
    }

    private void applyColumnWidths(XSSFSheet sheet) {
        int unit = 256;
        sheet.setColumnWidth(0, 6 * unit);    // S.NO
        sheet.setColumnWidth(1, 17 * unit);   // Date
        sheet.setColumnWidth(2, 30 * unit);   // Customer
        sheet.setColumnWidth(3, 13 * unit);   // Amount
        sheet.setColumnWidth(4, 40 * unit);   // Description
        sheet.setColumnWidth(5, 18 * unit);   // Bill / Statement
        sheet.setColumnWidth(6, 9 * unit);    // Shift
        sheet.setColumnWidth(7, 17 * unit);   // Recorded At
    }

    private void writeDataRow(XSSFSheet sheet, int rowIdx, int serial, IncentivePayment p,
                              XSSFCellStyle textStyle, XSSFCellStyle centerStyle, XSSFCellStyle numStyle) {
        XSSFRow row = sheet.createRow(rowIdx);
        row.setHeightInPoints(22f);

        XSSFCell c0 = row.createCell(0);
        c0.setCellValue(serial);
        c0.setCellStyle(centerStyle);

        XSSFCell c1 = row.createCell(1);
        c1.setCellValue(p.getPaymentDate() != null ? DATETIME_FMT.format(p.getPaymentDate()) : "");
        c1.setCellStyle(centerStyle);

        XSSFCell c2 = row.createCell(2);
        c2.setCellValue(resolveCustomerName(p));
        c2.setCellStyle(textStyle);

        XSSFCell c3 = row.createCell(3);
        c3.setCellValue(p.getAmount() != null ? p.getAmount().doubleValue() : 0d);
        c3.setCellStyle(numStyle);

        XSSFCell c4 = row.createCell(4);
        c4.setCellValue(p.getDescription() != null ? p.getDescription() : "");
        c4.setCellStyle(textStyle);

        XSSFCell c5 = row.createCell(5);
        c5.setCellValue(buildBillOrStatementRef(p));
        c5.setCellStyle(textStyle);

        XSSFCell c6 = row.createCell(6);
        c6.setCellValue(p.getShiftId() != null ? "#" + p.getShiftId() : "");
        c6.setCellStyle(centerStyle);

        XSSFCell c7 = row.createCell(7);
        c7.setCellValue(p.getCreatedAt() != null ? DATETIME_FMT.format(p.getCreatedAt()) : "");
        c7.setCellStyle(centerStyle);
    }

    private void writeTotalRow(XSSFSheet sheet, int rowIdx, int count, BigDecimal total,
                               XSSFCellStyle labelStyle, XSSFCellStyle numStyle) {
        XSSFRow row = sheet.createRow(rowIdx);
        row.setHeightInPoints(22f);
        for (int col = 0; col < COL_COUNT; col++) {
            XSSFCell cell = row.createCell(col);
            if (col == 2) {
                cell.setCellValue("TOTAL (" + count + " entries)");
                cell.setCellStyle(labelStyle);
            } else if (col == 3) {
                cell.setCellValue(total.doubleValue());
                cell.setCellStyle(numStyle);
            } else {
                cell.setCellStyle(labelStyle);
            }
        }
    }

    private String resolveCustomerName(IncentivePayment p) {
        if (p.getCustomer() != null && p.getCustomer().getName() != null) {
            return p.getCustomer().getName();
        }
        if (p.getInvoiceBill() != null) {
            if (p.getInvoiceBill().getCustomer() != null && p.getInvoiceBill().getCustomer().getName() != null) {
                return p.getInvoiceBill().getCustomer().getName();
            }
            if (p.getInvoiceBill().getSignatoryName() != null) {
                return p.getInvoiceBill().getSignatoryName();
            }
            if (p.getInvoiceBill().getBillDesc() != null) {
                return p.getInvoiceBill().getBillDesc();
            }
        }
        return "Walk-in";
    }

    private String buildBillOrStatementRef(IncentivePayment p) {
        if (p.getInvoiceBill() != null) {
            String billNo = p.getInvoiceBill().getBillNo();
            return "Bill: " + (billNo != null ? billNo : "#" + p.getInvoiceBill().getId());
        }
        if (p.getStatement() != null) {
            String stmtNo = p.getStatement().getStatementNo();
            return "Stmt: " + (stmtNo != null ? stmtNo : "#" + p.getStatement().getId());
        }
        return "-";
    }
}
