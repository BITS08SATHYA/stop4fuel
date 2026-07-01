package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Company;
import com.stopforfuel.backend.entity.InvoiceBill;
import com.stopforfuel.backend.entity.InvoiceProduct;
import com.stopforfuel.backend.entity.Payment;
import com.stopforfuel.backend.entity.Statement;
import com.stopforfuel.backend.enums.ReportLayout;
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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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

            // Title period must reflect the statements' actual coverage span, not the
            // generation-date filter window used to fetch them (statements are often
            // generated well after the period they cover — e.g. May statements run in
            // June). Derive min(fromDate) → max(toDate) from the rows; fall back to the
            // filter dates only when the export is empty.
            LocalDate titleFrom = statements.stream()
                    .map(Statement::getFromDate).filter(java.util.Objects::nonNull)
                    .min(Comparator.naturalOrder()).orElse(fromDate);
            LocalDate titleTo = statements.stream()
                    .map(Statement::getToDate).filter(java.util.Objects::nonNull)
                    .max(Comparator.naturalOrder()).orElse(toDate);

            writeTitle(sheet, titleStyle, companyName, titleFrom, titleTo);
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

    // =========================================================================
    // Per-statement detail Excel
    // =========================================================================
    // Mirrors the structure of the statement PDF: customer/period header, bills
    // grouped by date (DAY_WISE) or vehicle (VEHICLE_WISE), product + vehicle
    // summaries, payments received, balance. One sheet "Statement". Use this for
    // a single statement's drill-down, separate from the cross-statement summary
    // export above.

    private static final DateTimeFormatter DETAIL_DATE_FMT = DateTimeFormatter.ofPattern("dd-MMM-yyyy");
    private static final DateTimeFormatter DAY_HEADER_FMT = DateTimeFormatter.ofPattern("dd-MMM-yyyy (EEE)");
    private static final String INDIAN_AMT_FMT = "[>=10000000]##\\,##\\,##\\,##0.00;[>=100000]##\\,##\\,##0.00;##,##0.00";
    private static final String QTY_FMT = "#,##0.00";

    public byte[] generateStatementDetailExcel(Statement statement, List<InvoiceBill> bills,
                                               Company company, List<Payment> payments) {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sh = wb.createSheet("Statement");

            DetailStyles st = new DetailStyles(wb);
            applyColumnWidths(sh, st.unit);

            int row = 0;
            row = writeDetailHeader(sh, st, statement, company, row);
            row = writeBillsSection(sh, st, statement, bills, row);
            row = writeProductSummary(sh, st, bills, row);
            row = writeVehicleSummary(sh, st, bills, row);
            row = writePaymentsReceived(sh, st, payments, row);
            writeBalance(sh, st, statement, row);

            PrintSetup ps = sh.getPrintSetup();
            ps.setLandscape(true);
            ps.setPaperSize(PrintSetup.A4_PAPERSIZE);
            ps.setFitWidth((short) 1);
            ps.setFitHeight((short) 0);
            sh.setFitToPage(true);

            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new ReportGenerationException("Failed to generate statement detail Excel", e);
        }
    }

    private void applyColumnWidths(XSSFSheet sh, int unit) {
        sh.setColumnWidth(0, 5 * unit);   // #
        sh.setColumnWidth(1, 14 * unit);  // Vehicle / Date
        sh.setColumnWidth(2, 12 * unit);  // Bill No
        sh.setColumnWidth(3, 11 * unit);  // Indent
        sh.setColumnWidth(4, 15 * unit);  // Product
        sh.setColumnWidth(5, 10 * unit);  // Qty
        sh.setColumnWidth(6, 10 * unit);  // Rate
        sh.setColumnWidth(7, 13 * unit);  // Gross
        sh.setColumnWidth(8, 14 * unit);  // Net
    }

    private int writeDetailHeader(XSSFSheet sh, DetailStyles st, Statement stmt, Company company, int row) {
        // Title row: company name + "CREDIT STATEMENT" + statement no
        XSSFRow titleRow = sh.createRow(row);
        titleRow.setHeightInPoints(22f);
        String companyName = (company != null && company.getName() != null) ? company.getName().toUpperCase() : "STOPFORFUEL";
        XSSFCell c0 = titleRow.createCell(0);
        c0.setCellValue(companyName + "  —  CREDIT STATEMENT  —  " + (stmt.getStatementNo() != null ? stmt.getStatementNo() : ""));
        c0.setCellStyle(st.title);
        for (int i = 1; i < 9; i++) titleRow.createCell(i).setCellStyle(st.title);
        sh.addMergedRegion(new CellRangeAddress(row, row, 0, 8));
        row++;

        // Customer + period info — two rows of label/value pairs
        row = writeKvRow(sh, st, row,
                "Customer:", stmt.getCustomer() != null ? stmt.getCustomer().getName() : "-",
                "Statement Date:", stmt.getStatementDate() != null ? DETAIL_DATE_FMT.format(stmt.getStatementDate()) : "-",
                "Status:", stmt.getStatus() != null ? stmt.getStatus().replace("_", " ") : "-");
        row = writeKvRow(sh, st, row,
                "Period:", stmt.getFromDate().format(DETAIL_DATE_FMT) + "  to  " + stmt.getToDate().format(DETAIL_DATE_FMT),
                "Bills:", String.valueOf(stmt.getNumberOfBills() != null ? stmt.getNumberOfBills() : 0),
                "Layout:", stmt.getReportLayout() != null ? stmt.getReportLayout().name().replace("_", "-") : "VEHICLE-WISE");
        return row + 1;
    }

    private int writeKvRow(XSSFSheet sh, DetailStyles st, int row,
                           String l1, String v1, String l2, String v2, String l3, String v3) {
        XSSFRow r = sh.createRow(row);
        r.setHeightInPoints(16f);
        setCell(r, 0, l1, st.kvLabel);
        setCell(r, 1, v1, st.kvValue);
        setCell(r, 2, "", st.kvValue);
        setCell(r, 3, l2, st.kvLabel);
        setCell(r, 4, v2, st.kvValue);
        setCell(r, 5, "", st.kvValue);
        setCell(r, 6, l3, st.kvLabel);
        setCell(r, 7, v3, st.kvValue);
        setCell(r, 8, "", st.kvValue);
        sh.addMergedRegion(new CellRangeAddress(row, row, 1, 2));
        sh.addMergedRegion(new CellRangeAddress(row, row, 4, 5));
        sh.addMergedRegion(new CellRangeAddress(row, row, 7, 8));
        return row + 1;
    }

    private int writeBillsSection(XSSFSheet sh, DetailStyles st, Statement stmt, List<InvoiceBill> bills, int row) {
        XSSFRow heading = sh.createRow(row);
        heading.setHeightInPoints(18f);
        setCell(heading, 0, stmt.getReportLayout() == ReportLayout.DAY_WISE ? "BILLS — DAY-WISE" : "BILLS — VEHICLE-WISE", st.sectionTitle);
        for (int i = 1; i < 9; i++) heading.createCell(i).setCellStyle(st.sectionTitle);
        sh.addMergedRegion(new CellRangeAddress(row, row, 0, 8));
        row++;

        if (stmt.getReportLayout() == ReportLayout.DAY_WISE) {
            return writeBillsDayWise(sh, st, bills, row);
        }
        return writeBillsVehicleWise(sh, st, bills, row);
    }

    private int writeBillsVehicleWise(XSSFSheet sh, DetailStyles st, List<InvoiceBill> bills, int row) {
        LinkedHashMap<String, List<InvoiceBill>> grouped = new LinkedHashMap<>();
        for (InvoiceBill b : bills) {
            String key = b.getVehicle() != null ? b.getVehicle().getVehicleNumber() : "-";
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(b);
        }
        BillTotals grand = new BillTotals();
        String[] headers = {"#", "Date", "Bill No", "Indent", "Product", "Qty (L)", "Rate", "Gross", "Net Amt"};
        for (Map.Entry<String, List<InvoiceBill>> e : grouped.entrySet()) {
            row = writeGroupHeader(sh, st, row, "Vehicle: " + e.getKey() + "  (" + e.getValue().size() + ")");
            row = writeColumnHeaders(sh, st, row, headers);
            row = writeBillRows(sh, st, row, e.getValue(), grand, false);
        }
        return writeGrandTotal(sh, st, row, bills.size(), grand) + 1;
    }

    private int writeBillsDayWise(XSSFSheet sh, DetailStyles st, List<InvoiceBill> bills, int row) {
        TreeMap<LocalDate, List<InvoiceBill>> grouped = new TreeMap<>();
        for (InvoiceBill b : bills) {
            LocalDate key = b.getDate() != null ? b.getDate().toLocalDate() : LocalDate.MIN;
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(b);
        }
        BillTotals grand = new BillTotals();
        String[] headers = {"#", "Vehicle", "Bill No", "Indent", "Product", "Qty (L)", "Rate", "Gross", "Net Amt"};
        for (Map.Entry<LocalDate, List<InvoiceBill>> e : grouped.entrySet()) {
            String label = e.getKey().equals(LocalDate.MIN) ? "Date: -" : "Date: " + e.getKey().format(DAY_HEADER_FMT);
            row = writeGroupHeader(sh, st, row, label + "  (" + e.getValue().size() + ")");
            row = writeColumnHeaders(sh, st, row, headers);
            row = writeBillRows(sh, st, row, e.getValue(), grand, true);
        }
        return writeGrandTotal(sh, st, row, bills.size(), grand) + 1;
    }

    private int writeGroupHeader(XSSFSheet sh, DetailStyles st, int row, String label) {
        XSSFRow r = sh.createRow(row);
        r.setHeightInPoints(16f);
        setCell(r, 0, label, st.groupHeader);
        for (int i = 1; i < 9; i++) r.createCell(i).setCellStyle(st.groupHeader);
        sh.addMergedRegion(new CellRangeAddress(row, row, 0, 8));
        return row + 1;
    }

    private int writeColumnHeaders(XSSFSheet sh, DetailStyles st, int row, String[] headers) {
        XSSFRow r = sh.createRow(row);
        r.setHeightInPoints(18f);
        for (int i = 0; i < headers.length; i++) {
            setCell(r, i, headers[i], st.columnHeader);
        }
        return row + 1;
    }

    private int writeBillRows(XSSFSheet sh, DetailStyles st, int row, List<InvoiceBill> billsInGroup,
                              BillTotals grand, boolean dayWise) {
        BillTotals sub = new BillTotals();
        int index = 1;
        for (InvoiceBill bill : billsInGroup) {
            List<InvoiceProduct> products = bill.getProducts();
            int productCount = (products == null || products.isEmpty()) ? 1 : products.size();
            for (int pi = 0; pi < productCount; pi++) {
                boolean first = (pi == 0);
                InvoiceProduct ip = (products != null && !products.isEmpty()) ? products.get(pi) : null;
                String prodName = "-";
                BigDecimal qty = BigDecimal.ZERO, rate = BigDecimal.ZERO, gross = BigDecimal.ZERO, net = BigDecimal.ZERO;
                if (ip != null) {
                    if (ip.getProduct() != null) prodName = ip.getProduct().getName();
                    qty = ip.getQuantity() != null ? ip.getQuantity() : BigDecimal.ZERO;
                    rate = ip.getUnitPrice() != null ? ip.getUnitPrice() : BigDecimal.ZERO;
                    gross = qty.multiply(rate).setScale(2, RoundingMode.HALF_UP);
                    net = ip.getAmount() != null ? ip.getAmount() : BigDecimal.ZERO;
                }
                XSSFRow r = sh.createRow(row);
                r.setHeightInPoints(14f);
                setCell(r, 0, first ? String.valueOf(index) : "", st.body);
                if (dayWise) {
                    setCell(r, 1, first ? (bill.getVehicle() != null ? bill.getVehicle().getVehicleNumber() : "-") : "", st.body);
                } else {
                    setCell(r, 1, first && bill.getDate() != null ? bill.getDate().toLocalDate().format(DETAIL_DATE_FMT) : "", st.body);
                }
                setCell(r, 2, first ? (bill.getBillNo() != null ? bill.getBillNo() : "-") : "", st.body);
                setCell(r, 3, first ? (bill.getIndentNo() != null ? bill.getIndentNo() : "-") : "", st.body);
                setCell(r, 4, prodName, st.body);
                setCellNum(r, 5, qty, st.numQty);
                setCellNum(r, 6, rate, st.numQty);
                setCellNum(r, 7, gross, st.numAmt);
                setCellNum(r, 8, net, st.numAmt);
                sub.qty = sub.qty.add(qty);
                sub.gross = sub.gross.add(gross);
                sub.net = sub.net.add(net);
                row++;
            }
            index++;
        }
        // Subtotal row
        XSSFRow sr = sh.createRow(row);
        sr.setHeightInPoints(16f);
        setCell(sr, 0, "", st.subtotal);
        setCell(sr, 1, "", st.subtotal);
        setCell(sr, 2, "", st.subtotal);
        setCell(sr, 3, "", st.subtotal);
        setCell(sr, 4, "Subtotal", st.subtotalBoldRight);
        setCellNum(sr, 5, sub.qty, st.subtotalNum);
        setCell(sr, 6, "", st.subtotal);
        setCellNum(sr, 7, sub.gross, st.subtotalNum);
        setCellNum(sr, 8, sub.net, st.subtotalNum);
        grand.qty = grand.qty.add(sub.qty);
        grand.gross = grand.gross.add(sub.gross);
        grand.net = grand.net.add(sub.net);
        return row + 1;
    }

    private int writeGrandTotal(XSSFSheet sh, DetailStyles st, int row, int billCount, BillTotals grand) {
        XSSFRow r = sh.createRow(row);
        r.setHeightInPoints(18f);
        setCell(r, 0, "", st.grandTotal);
        setCell(r, 1, "", st.grandTotal);
        setCell(r, 2, "TOTAL (" + billCount + " bills)", st.grandTotal);
        setCell(r, 3, "", st.grandTotal);
        setCell(r, 4, "", st.grandTotal);
        setCellNum(r, 5, grand.qty, st.grandTotalNum);
        setCell(r, 6, "", st.grandTotal);
        setCellNum(r, 7, grand.gross, st.grandTotalNum);
        setCellNum(r, 8, grand.net, st.grandTotalNum);
        return row + 1;
    }

    private int writeProductSummary(XSSFSheet sh, DetailStyles st, List<InvoiceBill> bills, int row) {
        row++; // spacer
        Map<String, BigDecimal[]> agg = new LinkedHashMap<>();
        for (InvoiceBill b : bills) {
            if (b.getProducts() == null) continue;
            for (InvoiceProduct ip : b.getProducts()) {
                String name = ip.getProduct() != null ? ip.getProduct().getName() : "Other";
                BigDecimal[] v = agg.computeIfAbsent(name, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
                v[0] = v[0].add(BigDecimal.ONE);
                v[1] = v[1].add(ip.getQuantity() != null ? ip.getQuantity() : BigDecimal.ZERO);
                v[2] = v[2].add(ip.getDiscountAmount() != null ? ip.getDiscountAmount() : BigDecimal.ZERO);
                v[3] = v[3].add(ip.getAmount() != null ? ip.getAmount() : BigDecimal.ZERO);
            }
        }
        row = writeSectionTitle(sh, st, row, "PRODUCT SUMMARY");
        row = writeColumnHeaders(sh, st, row, new String[]{"Product", "Bills", "Qty (L)", "", "Discount", "", "", "", "Net Amt"});
        BigDecimal tb = BigDecimal.ZERO, tq = BigDecimal.ZERO, td = BigDecimal.ZERO, tn = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal[]> e : agg.entrySet()) {
            BigDecimal[] v = e.getValue();
            XSSFRow r = sh.createRow(row++);
            setCell(r, 0, e.getKey(), st.body);
            setCellNum(r, 1, v[0], st.numQty);
            setCellNum(r, 2, v[1], st.numQty);
            setCell(r, 3, "", st.body);
            setCellNum(r, 4, v[2], st.numAmt);
            setCell(r, 5, "", st.body);
            setCell(r, 6, "", st.body);
            setCell(r, 7, "", st.body);
            setCellNum(r, 8, v[3], st.numAmt);
            tb = tb.add(v[0]); tq = tq.add(v[1]); td = td.add(v[2]); tn = tn.add(v[3]);
        }
        XSSFRow tr = sh.createRow(row++);
        setCell(tr, 0, "Total", st.subtotalBoldRight);
        setCellNum(tr, 1, tb, st.subtotalNum);
        setCellNum(tr, 2, tq, st.subtotalNum);
        setCell(tr, 3, "", st.subtotal);
        setCellNum(tr, 4, td, st.subtotalNum);
        setCell(tr, 5, "", st.subtotal);
        setCell(tr, 6, "", st.subtotal);
        setCell(tr, 7, "", st.subtotal);
        setCellNum(tr, 8, tn, st.subtotalNum);
        return row;
    }

    private int writeVehicleSummary(XSSFSheet sh, DetailStyles st, List<InvoiceBill> bills, int row) {
        row++; // spacer
        Map<String, BigDecimal[]> agg = new LinkedHashMap<>();
        for (InvoiceBill b : bills) {
            String veh = b.getVehicle() != null ? b.getVehicle().getVehicleNumber() : "N/A";
            BigDecimal[] v = agg.computeIfAbsent(veh, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            v[0] = v[0].add(BigDecimal.ONE);
            BigDecimal q = BigDecimal.ZERO;
            if (b.getProducts() != null) {
                for (InvoiceProduct ip : b.getProducts()) q = q.add(ip.getQuantity() != null ? ip.getQuantity() : BigDecimal.ZERO);
            }
            v[1] = v[1].add(q);
            v[2] = v[2].add(b.getNetAmount() != null ? b.getNetAmount() : BigDecimal.ZERO);
        }
        row = writeSectionTitle(sh, st, row, "VEHICLE SUMMARY");
        row = writeColumnHeaders(sh, st, row, new String[]{"Vehicle", "Bills", "Qty (L)", "", "", "", "", "", "Net Amt"});
        BigDecimal tb = BigDecimal.ZERO, tq = BigDecimal.ZERO, tn = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal[]> e : agg.entrySet()) {
            BigDecimal[] v = e.getValue();
            XSSFRow r = sh.createRow(row++);
            setCell(r, 0, e.getKey(), st.body);
            setCellNum(r, 1, v[0], st.numQty);
            setCellNum(r, 2, v[1], st.numQty);
            for (int i = 3; i < 8; i++) setCell(r, i, "", st.body);
            setCellNum(r, 8, v[2], st.numAmt);
            tb = tb.add(v[0]); tq = tq.add(v[1]); tn = tn.add(v[2]);
        }
        XSSFRow tr = sh.createRow(row++);
        setCell(tr, 0, "Total", st.subtotalBoldRight);
        setCellNum(tr, 1, tb, st.subtotalNum);
        setCellNum(tr, 2, tq, st.subtotalNum);
        for (int i = 3; i < 8; i++) setCell(tr, i, "", st.subtotal);
        setCellNum(tr, 8, tn, st.subtotalNum);
        return row;
    }

    private int writePaymentsReceived(XSSFSheet sh, DetailStyles st, List<Payment> payments, int row) {
        row++; // spacer
        row = writeSectionTitle(sh, st, row, "PAYMENTS RECEIVED");
        row = writeColumnHeaders(sh, st, row, new String[]{"#", "Date", "Mode", "Reference", "", "", "", "", "Amount"});
        BigDecimal total = BigDecimal.ZERO;
        if (payments != null && !payments.isEmpty()) {
            int i = 1;
            for (Payment p : payments) {
                XSSFRow r = sh.createRow(row++);
                setCell(r, 0, String.valueOf(i++), st.body);
                setCell(r, 1, p.getPaymentDate() != null ? p.getPaymentDate().format(DETAIL_DATE_FMT) : "-", st.body);
                setCell(r, 2, p.getPaymentMode() != null ? p.getPaymentMode().name() : "-", st.body);
                setCell(r, 3, p.getReferenceNo() != null ? p.getReferenceNo() : "-", st.body);
                for (int c = 4; c < 8; c++) setCell(r, c, "", st.body);
                setCellNum(r, 8, p.getAmount(), st.numAmt);
                total = total.add(p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO);
            }
        }
        XSSFRow tr = sh.createRow(row++);
        for (int c = 0; c < 8; c++) setCell(tr, c, c == 0 ? "Total Received" : "", c == 0 ? st.subtotalBoldRight : st.subtotal);
        setCellNum(tr, 8, total, st.subtotalNum);
        return row;
    }

    private void writeBalance(XSSFSheet sh, DetailStyles st, Statement stmt, int row) {
        row++; // spacer
        row = writeSectionTitle(sh, st, row, "BALANCE");
        row = writeBalanceLine(sh, st, row, "Net Amount", stmt.getTotalAmount(), false);
        row = writeBalanceLine(sh, st, row, "Rounding", stmt.getRoundingAmount() != null ? stmt.getRoundingAmount().abs() : BigDecimal.ZERO, false);
        row = writeBalanceLine(sh, st, row, "Statement Amount", stmt.getNetAmount(), true);
        row = writeBalanceLine(sh, st, row, "Received", stmt.getReceivedAmount(), false);
        writeBalanceLine(sh, st, row, "BALANCE DUE", stmt.getBalanceAmount(), true);
    }

    private int writeBalanceLine(XSSFSheet sh, DetailStyles st, int row, String label, BigDecimal value, boolean bold) {
        XSSFRow r = sh.createRow(row);
        r.setHeightInPoints(16f);
        XSSFCellStyle lab = bold ? st.balanceLabelBold : st.balanceLabel;
        XSSFCellStyle val = bold ? st.balanceValueBold : st.balanceValue;
        setCell(r, 0, "", lab);
        setCell(r, 1, "", lab);
        setCell(r, 2, "", lab);
        setCell(r, 3, "", lab);
        setCell(r, 4, "", lab);
        setCell(r, 5, "", lab);
        setCell(r, 6, label, lab);
        setCell(r, 7, "", val);
        setCellNum(r, 8, value, val);
        return row + 1;
    }

    private int writeSectionTitle(XSSFSheet sh, DetailStyles st, int row, String title) {
        XSSFRow r = sh.createRow(row);
        r.setHeightInPoints(18f);
        setCell(r, 0, title, st.sectionTitle);
        for (int i = 1; i < 9; i++) r.createCell(i).setCellStyle(st.sectionTitle);
        sh.addMergedRegion(new CellRangeAddress(row, row, 0, 8));
        return row + 1;
    }

    private void setCell(XSSFRow row, int col, String value, XSSFCellStyle style) {
        XSSFCell c = row.createCell(col);
        c.setCellValue(value);
        c.setCellStyle(style);
    }

    private void setCellNum(XSSFRow row, int col, BigDecimal value, XSSFCellStyle style) {
        XSSFCell c = row.createCell(col);
        c.setCellValue(value != null ? value.doubleValue() : 0d);
        c.setCellStyle(style);
    }

    private static class BillTotals {
        BigDecimal qty = BigDecimal.ZERO;
        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal net = BigDecimal.ZERO;
    }

    private class DetailStyles {
        final int unit = 256;
        final XSSFCellStyle title;
        final XSSFCellStyle sectionTitle;
        final XSSFCellStyle groupHeader;
        final XSSFCellStyle columnHeader;
        final XSSFCellStyle body;
        final XSSFCellStyle numQty;
        final XSSFCellStyle numAmt;
        final XSSFCellStyle kvLabel;
        final XSSFCellStyle kvValue;
        final XSSFCellStyle subtotal;
        final XSSFCellStyle subtotalBoldRight;
        final XSSFCellStyle subtotalNum;
        final XSSFCellStyle grandTotal;
        final XSSFCellStyle grandTotalNum;
        final XSSFCellStyle balanceLabel;
        final XSSFCellStyle balanceLabelBold;
        final XSSFCellStyle balanceValue;
        final XSSFCellStyle balanceValueBold;

        DetailStyles(XSSFWorkbook wb) {
            this.title = mkTitle(wb);
            this.sectionTitle = mkSectionTitle(wb);
            this.groupHeader = mkGroupHeader(wb);
            this.columnHeader = mkColumnHeader(wb);
            this.body = mkBody(wb, HorizontalAlignment.LEFT, false);
            this.numQty = mkNum(wb, QTY_FMT, false);
            this.numAmt = mkNum(wb, INDIAN_AMT_FMT, false);
            this.kvLabel = mkBody(wb, HorizontalAlignment.LEFT, true);
            this.kvValue = mkBody(wb, HorizontalAlignment.LEFT, false);
            this.subtotal = mkBody(wb, HorizontalAlignment.LEFT, false);
            subtotal.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 245, (byte) 247, (byte) 250}, null));
            subtotal.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            this.subtotalBoldRight = mkBody(wb, HorizontalAlignment.RIGHT, true);
            subtotalBoldRight.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 245, (byte) 247, (byte) 250}, null));
            subtotalBoldRight.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            this.subtotalNum = mkNum(wb, INDIAN_AMT_FMT, true);
            subtotalNum.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 245, (byte) 247, (byte) 250}, null));
            subtotalNum.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            this.grandTotal = mkBody(wb, HorizontalAlignment.LEFT, true);
            grandTotal.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 233, (byte) 236, (byte) 239}, null));
            grandTotal.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            this.grandTotalNum = mkNum(wb, INDIAN_AMT_FMT, true);
            grandTotalNum.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 233, (byte) 236, (byte) 239}, null));
            grandTotalNum.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            this.balanceLabel = mkBody(wb, HorizontalAlignment.RIGHT, false);
            this.balanceLabelBold = mkBody(wb, HorizontalAlignment.RIGHT, true);
            this.balanceValue = mkNum(wb, INDIAN_AMT_FMT, false);
            this.balanceValueBold = mkNum(wb, INDIAN_AMT_FMT, true);
        }

        private XSSFCellStyle mkTitle(XSSFWorkbook wb) {
            XSSFCellStyle s = wb.createCellStyle();
            XSSFFont f = wb.createFont();
            f.setBold(true);
            f.setFontHeightInPoints((short) 13);
            s.setFont(f);
            s.setAlignment(HorizontalAlignment.CENTER);
            s.setVerticalAlignment(VerticalAlignment.CENTER);
            applyAllBorders(s);
            return s;
        }

        private XSSFCellStyle mkSectionTitle(XSSFWorkbook wb) {
            XSSFCellStyle s = wb.createCellStyle();
            XSSFFont f = wb.createFont();
            f.setBold(true);
            f.setFontHeightInPoints((short) 11);
            s.setFont(f);
            s.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 52, (byte) 58, (byte) 64}, null));
            s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            f.setColor(new XSSFColor(new byte[]{(byte) 255, (byte) 255, (byte) 255}, null));
            s.setAlignment(HorizontalAlignment.CENTER);
            s.setVerticalAlignment(VerticalAlignment.CENTER);
            applyAllBorders(s);
            return s;
        }

        private XSSFCellStyle mkGroupHeader(XSSFWorkbook wb) {
            XSSFCellStyle s = wb.createCellStyle();
            XSSFFont f = wb.createFont();
            f.setBold(true);
            f.setFontHeightInPoints((short) 10);
            s.setFont(f);
            s.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 225, (byte) 230, (byte) 240}, null));
            s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            s.setAlignment(HorizontalAlignment.LEFT);
            s.setVerticalAlignment(VerticalAlignment.CENTER);
            applyAllBorders(s);
            return s;
        }

        private XSSFCellStyle mkColumnHeader(XSSFWorkbook wb) {
            XSSFCellStyle s = wb.createCellStyle();
            XSSFFont f = wb.createFont();
            f.setBold(true);
            f.setFontHeightInPoints((short) 10);
            f.setColor(new XSSFColor(new byte[]{(byte) 255, (byte) 255, (byte) 255}, null));
            s.setFont(f);
            s.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 52, (byte) 58, (byte) 64}, null));
            s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            s.setAlignment(HorizontalAlignment.CENTER);
            s.setVerticalAlignment(VerticalAlignment.CENTER);
            applyAllBorders(s);
            return s;
        }

        private XSSFCellStyle mkBody(XSSFWorkbook wb, HorizontalAlignment align, boolean bold) {
            XSSFCellStyle s = wb.createCellStyle();
            XSSFFont f = wb.createFont();
            f.setFontHeightInPoints((short) 10);
            f.setBold(bold);
            s.setFont(f);
            s.setAlignment(align);
            s.setVerticalAlignment(VerticalAlignment.CENTER);
            applyAllBorders(s);
            return s;
        }

        private XSSFCellStyle mkNum(XSSFWorkbook wb, String fmt, boolean bold) {
            XSSFCellStyle s = mkBody(wb, HorizontalAlignment.RIGHT, bold);
            s.setDataFormat(wb.createDataFormat().getFormat(fmt));
            return s;
        }
    }
}
