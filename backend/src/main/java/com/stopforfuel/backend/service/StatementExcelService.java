package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Statement;
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
import java.util.Comparator;
import java.util.List;

@Service
public class StatementExcelService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final int ROWS_PER_PAGE = 7;
    private static final int TITLE_ROW = 0;
    private static final int HEADER_ROW = 1;
    private static final int DATA_START_ROW = 2;
    private static final int COL_COUNT = 10;
    // Indian-grouping number format: 1,634 / 1,26,607 / 12,34,56,789
    private static final String INDIAN_NUM_FMT =
            "[>=10000000]##\\,##\\,##\\,##0;[>=100000]##\\,##\\,##0;##,##0";

    public byte[] generateExcel(List<Statement> statements,
                                String companyName,
                                LocalDate fromDate,
                                LocalDate toDate) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet("Statements");

            configurePageSetup(workbook, sheet);

            XSSFCellStyle titleStyle = buildTitleStyle(workbook);
            XSSFCellStyle headerStyle = buildHeaderStyle(workbook);
            XSSFCellStyle textStyle = buildBodyStyle(workbook, HorizontalAlignment.LEFT);
            XSSFCellStyle centerStyle = buildBodyStyle(workbook, HorizontalAlignment.CENTER);
            XSSFCellStyle numStyle = buildNumberStyle(workbook);

            writeTitle(sheet, titleStyle, companyName, fromDate, toDate);
            writeHeader(sheet, headerStyle);
            applyColumnWidths(sheet);

            List<Statement> sorted = statements.stream()
                    .sorted(Comparator.comparingLong(s -> extractStatementNumber(s.getStatementNo())))
                    .toList();

            int rowIdx = DATA_START_ROW;
            int serial = 1;
            for (Statement s : sorted) {
                writeDataRow(sheet, rowIdx, serial, s, textStyle, centerStyle, numStyle);
                rowIdx++;
                serial++;
            }

            // Fill remaining slots on the last page with empty bordered rows so the
            // printed grid is uniform (otherwise the last page has a half-grid).
            int dataRows = sorted.size();
            int padding = (ROWS_PER_PAGE - (dataRows % ROWS_PER_PAGE)) % ROWS_PER_PAGE;
            for (int i = 0; i < padding; i++) {
                writeBlankRow(sheet, rowIdx, textStyle, centerStyle, numStyle);
                rowIdx++;
            }

            // Manual page breaks every ROWS_PER_PAGE data rows
            int totalRendered = dataRows + padding;
            for (int i = ROWS_PER_PAGE; i < totalRendered; i += ROWS_PER_PAGE) {
                sheet.setRowBreak(DATA_START_ROW + i - 1);
            }

            // Repeat title + header rows on every printed page
            sheet.setRepeatingRows(new CellRangeAddress(TITLE_ROW, HEADER_ROW, -1, -1));

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new ReportGenerationException("Failed to generate Statement Excel report", e);
        }
    }

    private void configurePageSetup(XSSFWorkbook workbook, XSSFSheet sheet) {
        PrintSetup ps = sheet.getPrintSetup();
        ps.setLandscape(true);
        ps.setPaperSize(PrintSetup.LEGAL_PAPERSIZE);
        ps.setFitWidth((short) 1);
        ps.setFitHeight((short) 0);
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

    private XSSFCellStyle buildNumberStyle(XSSFWorkbook workbook) {
        XSSFCellStyle s = buildBodyStyle(workbook, HorizontalAlignment.RIGHT);
        s.setDataFormat(workbook.createDataFormat().getFormat(INDIAN_NUM_FMT));
        XSSFFont f = workbook.createFont();
        f.setBold(true);
        f.setFontHeightInPoints((short) 10);
        s.setFont(f);
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
        String title = (name.isEmpty() ? "STATEMENT " : name + "/ STATEMENT ") + period;
        XSSFCell c = row.createCell(0);
        c.setCellValue(title);
        c.setCellStyle(titleStyle);
        // Apply title style to all merged cells so the border is continuous.
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
                "ST No",
                "PARTY NAME / CELL NO",
                "Stat Amount",
                "CUSTOMER NAME and MOBLIE No",
                "Part Amt Entries",
                "Full Amt",
                "Balance",
                "RECEIVED By With (OFFICE use only)"
        };
        XSSFRow row = sheet.createRow(HEADER_ROW);
        row.setHeightInPoints(40f);
        for (int i = 0; i < headers.length; i++) {
            XSSFCell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
    }

    private void applyColumnWidths(XSSFSheet sheet) {
        int unit = 256;
        sheet.setColumnWidth(0, 5 * unit);     // S.NO
        sheet.setColumnWidth(1, 13 * unit);    // Date
        sheet.setColumnWidth(2, 9 * unit);     // ST No
        sheet.setColumnWidth(3, 30 * unit);    // PARTY NAME / CELL NO
        sheet.setColumnWidth(4, 13 * unit);    // Stat Amount
        sheet.setColumnWidth(5, 32 * unit);    // CUSTOMER NAME and MOBLIE No
        sheet.setColumnWidth(6, 13 * unit);    // Part Amt Entries
        sheet.setColumnWidth(7, 11 * unit);    // Full Amt
        sheet.setColumnWidth(8, 11 * unit);    // Balance
        sheet.setColumnWidth(9, 24 * unit);    // RECEIVED By With (OFFICE use only)
    }

    private void writeDataRow(XSSFSheet sheet, int rowIdx, int serial, Statement s,
                              XSSFCellStyle textStyle, XSSFCellStyle centerStyle, XSSFCellStyle numStyle) {
        XSSFRow row = sheet.createRow(rowIdx);
        row.setHeightInPoints(60f);

        XSSFCell c0 = row.createCell(0);
        c0.setCellValue(serial);
        c0.setCellStyle(centerStyle);

        XSSFCell c1 = row.createCell(1);
        c1.setCellValue(s.getFromDate() != null ? DATE_FMT.format(s.getFromDate()) : "");
        c1.setCellStyle(centerStyle);

        XSSFCell c2 = row.createCell(2);
        c2.setCellValue(stripStatementPrefix(s.getStatementNo()));
        c2.setCellStyle(centerStyle);

        XSSFCell c3 = row.createCell(3);
        c3.setCellValue(buildPartyNameCell(s));
        c3.setCellStyle(textStyle);

        XSSFCell c4 = row.createCell(4);
        c4.setCellValue(toBigDecimalDouble(s.getNetAmount()));
        c4.setCellStyle(numStyle);

        // Office-fill cells — empty but bordered so the printed grid is continuous
        for (int col = 5; col < COL_COUNT; col++) {
            XSSFCell empty = row.createCell(col);
            empty.setCellStyle(textStyle);
        }
    }

    private void writeBlankRow(XSSFSheet sheet, int rowIdx,
                               XSSFCellStyle textStyle, XSSFCellStyle centerStyle, XSSFCellStyle numStyle) {
        XSSFRow row = sheet.createRow(rowIdx);
        row.setHeightInPoints(60f);
        for (int col = 0; col < COL_COUNT; col++) {
            XSSFCell cell = row.createCell(col);
            if (col == 0 || col == 1 || col == 2) {
                cell.setCellStyle(centerStyle);
            } else if (col == 4) {
                cell.setCellStyle(numStyle);
            } else {
                cell.setCellStyle(textStyle);
            }
        }
    }

    private String buildPartyNameCell(Statement s) {
        if (s.getCustomer() == null) return "";
        String name = s.getCustomer().getName() != null ? s.getCustomer().getName() : "";
        String phone = null;
        try {
            if (s.getCustomer().getPhoneNumbers() != null) {
                phone = s.getCustomer().getPhoneNumbers().stream()
                        .filter(p -> p != null && !p.isBlank())
                        .findFirst().orElse(null);
            }
        } catch (Exception ignored) {
            // Lazy collection access can fail outside a session; degrade gracefully.
        }
        return (phone == null || phone.isBlank()) ? name : name + "\n" + phone;
    }

    private String stripStatementPrefix(String statementNo) {
        if (statementNo == null) return "";
        int i = 0;
        while (i < statementNo.length() && !Character.isDigit(statementNo.charAt(i))) i++;
        return statementNo.substring(i);
    }

    private long extractStatementNumber(String statementNo) {
        String digits = stripStatementPrefix(statementNo);
        if (digits.isEmpty()) return Long.MAX_VALUE;
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException e) {
            return Long.MAX_VALUE;
        }
    }

    private double toBigDecimalDouble(BigDecimal value) {
        return value != null ? value.doubleValue() : 0;
    }
}
