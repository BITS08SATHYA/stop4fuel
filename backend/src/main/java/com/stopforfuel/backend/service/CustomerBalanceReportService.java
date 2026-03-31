package com.stopforfuel.backend.service;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.stopforfuel.backend.entity.Customer;
import com.stopforfuel.backend.exception.ReportGenerationException;
import com.stopforfuel.backend.repository.CustomerRepository;
import com.stopforfuel.backend.repository.InvoiceBillRepository;
import com.stopforfuel.backend.repository.PaymentRepository;
import com.stopforfuel.backend.repository.StatementRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerBalanceReportService {

    private final CustomerRepository customerRepository;
    private final InvoiceBillRepository invoiceBillRepository;
    private final PaymentRepository paymentRepository;
    private final StatementRepository statementRepository;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy, hh:mm a");
    private static final java.text.DecimalFormat NUM_FMT = new java.text.DecimalFormat("#,##0.00");

    @Value("${app.company.name:SATHYA FUELS}")
    private String companyName;

    @Value("${app.company.address:No. 45, GST Road, Tambaram, Chennai - 600045}")
    private String companyAddress;

    @Value("${app.company.phone:044-2234 5678}")
    private String companyPhone;

    @Value("${app.company.gstin:33AABCS1234F1Z5}")
    private String companyGstin;

    /**
     * Build balance data for all credit customers (those with credit limit > 0 or any credit bills).
     */
    private List<CustomerBalance> buildBalanceData() {
        List<Customer> allCustomers = customerRepository.findAll();
        List<CustomerBalance> balances = new ArrayList<>();

        for (Customer c : allCustomers) {
            BigDecimal totalBilled = invoiceBillRepository.sumAllCreditBillsByCustomer(c.getId());
            BigDecimal totalPaid = paymentRepository.sumAllPaymentsByCustomer(c.getId());
            BigDecimal ledgerBalance = totalBilled.subtract(totalPaid);

            // Only include customers with credit activity or credit limits
            boolean hasCreditLimit = c.getCreditLimitAmount() != null && c.getCreditLimitAmount().compareTo(BigDecimal.ZERO) > 0;
            boolean hasBalance = ledgerBalance.compareTo(BigDecimal.ZERO) != 0;
            boolean hasCreditHistory = totalBilled.compareTo(BigDecimal.ZERO) > 0;

            if (!hasCreditLimit && !hasBalance && !hasCreditHistory) continue;

            // Check aging: has unpaid bill older than 90 days
            boolean aging90 = invoiceBillRepository.existsUnpaidCreditBillBefore(
                    c.getId(), LocalDateTime.now().minusDays(90));
            boolean aging60 = !aging90 && invoiceBillRepository.existsUnpaidCreditBillBefore(
                    c.getId(), LocalDateTime.now().minusDays(60));
            boolean aging30 = !aging90 && !aging60 && invoiceBillRepository.existsUnpaidCreditBillBefore(
                    c.getId(), LocalDateTime.now().minusDays(30));

            String agingStatus = aging90 ? "90+ days" : aging60 ? "60+ days" : aging30 ? "30+ days" : "Current";

            // Unpaid statements count
            long unpaidStatements = statementRepository.findByCustomerIdAndStatus(c.getId(), "NOT_PAID").size();

            // Credit utilization
            BigDecimal creditLimit = c.getCreditLimitAmount() != null ? c.getCreditLimitAmount() : BigDecimal.ZERO;
            BigDecimal utilization = BigDecimal.ZERO;
            if (creditLimit.compareTo(BigDecimal.ZERO) > 0 && ledgerBalance.compareTo(BigDecimal.ZERO) > 0) {
                utilization = ledgerBalance.multiply(new BigDecimal("100"))
                        .divide(creditLimit, 1, RoundingMode.HALF_UP);
            }

            balances.add(new CustomerBalance(c, totalBilled, totalPaid, ledgerBalance,
                    creditLimit, utilization, agingStatus, unpaidStatements));
        }

        // Sort by balance descending (highest outstanding first)
        balances.sort(Comparator.comparing(CustomerBalance::ledgerBalance).reversed());
        return balances;
    }

    // ======================== PDF ========================

    public byte[] generatePdf() {
        try {
            List<CustomerBalance> balances = buildBalanceData();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4.rotate(), 20, 20, 20, 20);
            PdfWriter.getInstance(doc, out);
            doc.open();

            Font titleFont = new Font(Font.HELVETICA, 16, Font.BOLD, new Color(51, 51, 51));
            Font subFont = new Font(Font.HELVETICA, 8, Font.NORMAL, new Color(102, 102, 102));
            Font sectionFont = new Font(Font.HELVETICA, 12, Font.BOLD, new Color(230, 81, 0));
            Font headerFont = new Font(Font.HELVETICA, 7, Font.BOLD, Color.BLACK);
            Font cellFont = new Font(Font.HELVETICA, 7, Font.NORMAL, Color.BLACK);
            Font boldCellFont = new Font(Font.HELVETICA, 7, Font.BOLD, Color.BLACK);
            Font amountFont = new Font(Font.HELVETICA, 7, Font.BOLD, new Color(230, 81, 0));
            Font warningFont = new Font(Font.HELVETICA, 7, Font.BOLD, new Color(198, 40, 40));
            Font greenFont = new Font(Font.HELVETICA, 7, Font.BOLD, new Color(46, 125, 50));
            Font footerFont = new Font(Font.HELVETICA, 6, Font.NORMAL, new Color(153, 153, 153));
            Font summaryLabelFont = new Font(Font.HELVETICA, 9, Font.BOLD, new Color(51, 51, 51));
            Font summaryValueFont = new Font(Font.HELVETICA, 12, Font.BOLD, new Color(230, 81, 0));

            // Header
            Paragraph company = new Paragraph(companyName, titleFont);
            company.setAlignment(Element.ALIGN_CENTER);
            doc.add(company);

            Paragraph address = new Paragraph(companyAddress + " | GSTIN: " + companyGstin + " | " + companyPhone, subFont);
            address.setAlignment(Element.ALIGN_CENTER);
            doc.add(address);

            doc.add(Chunk.NEWLINE);

            Paragraph title = new Paragraph("Customer Balance Report", sectionFont);
            title.setAlignment(Element.ALIGN_CENTER);
            doc.add(title);

            Paragraph asOf = new Paragraph("As of " + LocalDateTime.now().format(DATETIME_FMT), subFont);
            asOf.setAlignment(Element.ALIGN_CENTER);
            doc.add(asOf);

            doc.add(Chunk.NEWLINE);

            // ---- Summary boxes ----
            BigDecimal totalOutstanding = BigDecimal.ZERO;
            BigDecimal totalCreditLimit = BigDecimal.ZERO;
            int customersWithBalance = 0;
            int agingCount = 0;

            for (CustomerBalance cb : balances) {
                if (cb.ledgerBalance.compareTo(BigDecimal.ZERO) > 0) {
                    totalOutstanding = totalOutstanding.add(cb.ledgerBalance);
                    customersWithBalance++;
                }
                totalCreditLimit = totalCreditLimit.add(cb.creditLimit);
                if (!"Current".equals(cb.agingStatus)) agingCount++;
            }

            PdfPTable summaryTable = new PdfPTable(4);
            summaryTable.setWidthPercentage(100);
            summaryTable.setWidths(new float[]{25, 25, 25, 25});

            addSummaryBox(summaryTable, "Total Outstanding", formatRupee(totalOutstanding),
                    customersWithBalance + " customers", summaryLabelFont, summaryValueFont, subFont);
            addSummaryBox(summaryTable, "Total Credit Limit", formatRupee(totalCreditLimit),
                    balances.size() + " credit customers", summaryLabelFont, summaryValueFont, subFont);
            BigDecimal overallUtil = totalCreditLimit.compareTo(BigDecimal.ZERO) > 0
                    ? totalOutstanding.multiply(new BigDecimal("100")).divide(totalCreditLimit, 1, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            addSummaryBox(summaryTable, "Credit Utilization", overallUtil + "%",
                    "across all customers", summaryLabelFont, summaryValueFont, subFont);
            addSummaryBox(summaryTable, "Aging Alerts", String.valueOf(agingCount),
                    "customers with overdue", summaryLabelFont,
                    agingCount > 0 ? new Font(Font.HELVETICA, 12, Font.BOLD, new Color(198, 40, 40)) : summaryValueFont,
                    subFont);

            doc.add(summaryTable);
            doc.add(Chunk.NEWLINE);

            // ---- Detail Table ----
            float[] colWidths = {4f, 14f, 10f, 10f, 10f, 10f, 10f, 8f, 8f, 8f, 8f};
            PdfPTable table = new PdfPTable(colWidths);
            table.setWidthPercentage(100);
            table.setSpacingBefore(4);

            String[] headers = {"#", "Customer", "Group", "Credit Limit", "Total Billed",
                    "Total Paid", "Balance", "Utilization", "Aging", "Stmts Due", "Status"};
            addHeaderRow(table, headers, headerFont);

            int rowNum = 0;
            for (CustomerBalance cb : balances) {
                rowNum++;
                Color rowBg = (rowNum % 2 == 0) ? new Color(250, 250, 250) : Color.WHITE;
                Color border = new Color(238, 238, 238);

                addCell(table, String.valueOf(rowNum), cellFont, Element.ALIGN_CENTER, rowBg, border);
                addCell(table, cb.customer.getName() != null ? cb.customer.getName() : "-", boldCellFont, Element.ALIGN_LEFT, rowBg, border);
                addCell(table, cb.customer.getGroup() != null ? cb.customer.getGroup().getGroupName() : "-", cellFont, Element.ALIGN_LEFT, rowBg, border);
                addCell(table, formatRupee(cb.creditLimit), cellFont, Element.ALIGN_RIGHT, rowBg, border);
                addCell(table, formatRupee(cb.totalBilled), cellFont, Element.ALIGN_RIGHT, rowBg, border);
                addCell(table, formatRupee(cb.totalPaid), greenFont, Element.ALIGN_RIGHT, rowBg, border);

                // Balance — highlight if positive
                Font balFont = cb.ledgerBalance.compareTo(BigDecimal.ZERO) > 0 ? amountFont : greenFont;
                Color balBg = cb.ledgerBalance.compareTo(BigDecimal.ZERO) > 0 ? new Color(255, 248, 225) : rowBg;
                addCell(table, formatRupee(cb.ledgerBalance), balFont, Element.ALIGN_RIGHT, balBg, border);

                // Utilization
                String utilStr = cb.utilization.compareTo(BigDecimal.ZERO) > 0 ? cb.utilization + "%" : "-";
                Font utilFont = cb.utilization.compareTo(new BigDecimal("80")) >= 0 ? warningFont : cellFont;
                addCell(table, utilStr, utilFont, Element.ALIGN_CENTER, rowBg, border);

                // Aging
                Font agingFont = "90+ days".equals(cb.agingStatus) ? warningFont
                        : "Current".equals(cb.agingStatus) ? greenFont : boldCellFont;
                Color agingBg = "90+ days".equals(cb.agingStatus) ? new Color(255, 235, 238) : rowBg;
                addCell(table, cb.agingStatus, agingFont, Element.ALIGN_CENTER, agingBg, border);

                // Unpaid statements
                addCell(table, cb.unpaidStatements > 0 ? String.valueOf(cb.unpaidStatements) : "-",
                        cb.unpaidStatements > 0 ? boldCellFont : cellFont, Element.ALIGN_CENTER, rowBg, border);

                // Status
                String status = cb.customer.getStatus() != null ? cb.customer.getStatus().name() : "ACTIVE";
                Font statusFont = "BLOCKED".equals(status) ? warningFont : "ACTIVE".equals(status) ? greenFont : cellFont;
                Color statusBg = "BLOCKED".equals(status) ? new Color(255, 235, 238)
                        : "ACTIVE".equals(status) ? new Color(232, 245, 233) : rowBg;
                addCell(table, status, statusFont, Element.ALIGN_CENTER, statusBg, border);
            }

            doc.add(table);

            // Footer
            doc.add(Chunk.NEWLINE);
            String generatedDate = LocalDateTime.now().format(DATETIME_FMT);
            Paragraph footer = new Paragraph(balances.size() + " credit customers  |  Report generated on " + generatedDate, footerFont);
            footer.setAlignment(Element.ALIGN_CENTER);
            doc.add(footer);

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new ReportGenerationException("Failed to generate Customer Balance PDF report", e);
        }
    }

    // ======================== EXCEL ========================

    public byte[] generateExcel() {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            List<CustomerBalance> balances = buildBalanceData();

            // Styles
            XSSFCellStyle titleStyle = createStyle(workbook, (short) 14, true, HorizontalAlignment.CENTER, null);
            XSSFCellStyle subStyle = createStyle(workbook, (short) 9, false, HorizontalAlignment.CENTER, IndexedColors.GREY_50_PERCENT);
            XSSFCellStyle sectionStyle = createBoldColorStyle(workbook, (short) 11, new byte[]{(byte) 230, (byte) 81, 0});
            XSSFCellStyle headerStyle = createExcelHeaderStyle(workbook);
            XSSFCellStyle cellStyle = createBorderedStyle(workbook, false);
            XSSFCellStyle boldCellStyle = createBorderedStyle(workbook, true);
            XSSFCellStyle numStyle = createExcelNumStyle(workbook, false);
            XSSFCellStyle boldNumStyle = createExcelNumStyle(workbook, true);
            XSSFCellStyle warningStyle = createBoldColorBorderedStyle(workbook, new byte[]{(byte) 198, (byte) 40, (byte) 40});
            XSSFCellStyle greenStyle = createBoldColorBorderedStyle(workbook, new byte[]{(byte) 46, (byte) 125, (byte) 50});

            XSSFSheet sheet = workbook.createSheet("Customer Balances");
            int rowIdx = 0;
            int colCount = 11;

            // Title
            XSSFRow tr = sheet.createRow(rowIdx++);
            tr.createCell(0).setCellValue(companyName);
            tr.getCell(0).setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, colCount - 1));

            XSSFRow ar = sheet.createRow(rowIdx++);
            ar.createCell(0).setCellValue(companyAddress + " | GSTIN: " + companyGstin + " | " + companyPhone);
            ar.getCell(0).setCellStyle(subStyle);
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, colCount - 1));

            rowIdx++;

            XSSFRow sr = sheet.createRow(rowIdx++);
            sr.createCell(0).setCellValue("Customer Balance Report");
            sr.getCell(0).setCellStyle(sectionStyle);
            sheet.addMergedRegion(new CellRangeAddress(rowIdx - 1, rowIdx - 1, 0, colCount - 1));

            XSSFRow dateRow = sheet.createRow(rowIdx++);
            dateRow.createCell(0).setCellValue("As of " + LocalDateTime.now().format(DATETIME_FMT));
            dateRow.getCell(0).setCellStyle(subStyle);
            sheet.addMergedRegion(new CellRangeAddress(rowIdx - 1, rowIdx - 1, 0, colCount - 1));

            rowIdx++; // blank

            // Summary row
            BigDecimal totalOutstanding = BigDecimal.ZERO;
            int customersWithBalance = 0;
            for (CustomerBalance cb : balances) {
                if (cb.ledgerBalance.compareTo(BigDecimal.ZERO) > 0) {
                    totalOutstanding = totalOutstanding.add(cb.ledgerBalance);
                    customersWithBalance++;
                }
            }
            XSSFRow sumRow = sheet.createRow(rowIdx++);
            sumRow.createCell(0).setCellValue("Total Outstanding: \u20B9" + NUM_FMT.format(totalOutstanding)
                    + " | " + customersWithBalance + " customers with balance | " + balances.size() + " total credit customers");
            sumRow.getCell(0).setCellStyle(boldCellStyle);
            sheet.addMergedRegion(new CellRangeAddress(rowIdx - 1, rowIdx - 1, 0, colCount - 1));

            rowIdx++;

            // Headers
            String[] headers = {"#", "Customer", "Group", "Credit Limit", "Total Billed",
                    "Total Paid", "Balance", "Utilization %", "Aging", "Stmts Due", "Status"};
            XSSFRow hr = sheet.createRow(rowIdx++);
            for (int i = 0; i < headers.length; i++) {
                hr.createCell(i).setCellValue(headers[i]);
                hr.getCell(i).setCellStyle(headerStyle);
            }

            // Data rows
            int num = 0;
            for (CustomerBalance cb : balances) {
                num++;
                XSSFRow row = sheet.createRow(rowIdx++);

                row.createCell(0).setCellValue(num);
                row.getCell(0).setCellStyle(cellStyle);

                row.createCell(1).setCellValue(cb.customer.getName() != null ? cb.customer.getName() : "-");
                row.getCell(1).setCellStyle(boldCellStyle);

                row.createCell(2).setCellValue(cb.customer.getGroup() != null ? cb.customer.getGroup().getGroupName() : "-");
                row.getCell(2).setCellStyle(cellStyle);

                setCurrencyCell(row, 3, cb.creditLimit, numStyle);
                setCurrencyCell(row, 4, cb.totalBilled, numStyle);
                setCurrencyCell(row, 5, cb.totalPaid, numStyle);

                setCurrencyCell(row, 6, cb.ledgerBalance,
                        cb.ledgerBalance.compareTo(BigDecimal.ZERO) > 0 ? boldNumStyle : numStyle);

                row.createCell(7).setCellValue(cb.utilization.doubleValue());
                row.getCell(7).setCellStyle(cb.utilization.compareTo(new BigDecimal("80")) >= 0 ? warningStyle : cellStyle);

                row.createCell(8).setCellValue(cb.agingStatus);
                row.getCell(8).setCellStyle("90+ days".equals(cb.agingStatus) ? warningStyle
                        : "Current".equals(cb.agingStatus) ? greenStyle : cellStyle);

                row.createCell(9).setCellValue(cb.unpaidStatements);
                row.getCell(9).setCellStyle(cellStyle);

                String status = cb.customer.getStatus() != null ? cb.customer.getStatus().name() : "ACTIVE";
                row.createCell(10).setCellValue(status);
                row.getCell(10).setCellStyle("BLOCKED".equals(status) ? warningStyle
                        : "ACTIVE".equals(status) ? greenStyle : cellStyle);
            }

            for (int i = 0; i < colCount; i++) sheet.autoSizeColumn(i);

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new ReportGenerationException("Failed to generate Customer Balance Excel report", e);
        }
    }

    // ======================== Helpers ========================

    private void addSummaryBox(PdfPTable table, String label, String value, String sub,
                               Font labelFont, Font valueFont, Font subFont) {
        PdfPCell cell = new PdfPCell();
        cell.setBorderColor(new Color(224, 224, 224));
        cell.setPadding(8);
        cell.setBackgroundColor(new Color(250, 250, 250));

        Paragraph l = new Paragraph(label, labelFont);
        l.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(l);

        Paragraph v = new Paragraph(value, valueFont);
        v.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(v);

        Paragraph s = new Paragraph(sub, subFont);
        s.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(s);

        table.addCell(cell);
    }

    private void addHeaderRow(PdfPTable table, String[] headers, Font font) {
        Color headerBg = new Color(245, 245, 245);
        Color borderColor = new Color(204, 204, 204);
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, font));
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cell.setPadding(5);
            cell.setBackgroundColor(headerBg);
            cell.setBorderColor(borderColor);
            table.addCell(cell);
        }
    }

    private void addCell(PdfPTable table, String text, Font font, int align, Color bg, Color border) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(align);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(4);
        cell.setBackgroundColor(bg);
        cell.setBorderColor(border);
        table.addCell(cell);
    }

    private String formatRupee(BigDecimal value) {
        if (value == null) return "\u20B90.00";
        return "\u20B9" + NUM_FMT.format(value);
    }

    private void setCurrencyCell(XSSFRow row, int col, BigDecimal value, XSSFCellStyle style) {
        XSSFCell cell = row.createCell(col);
        cell.setCellValue(value != null ? value.doubleValue() : 0);
        cell.setCellStyle(style);
    }

    // ---- Excel style helpers ----

    private XSSFCellStyle createStyle(XSSFWorkbook wb, short size, boolean bold, HorizontalAlignment align, IndexedColors color) {
        XSSFCellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setFontHeightInPoints(size);
        font.setBold(bold);
        if (color != null) font.setColor(color.getIndex());
        style.setFont(font);
        if (align != null) style.setAlignment(align);
        return style;
    }

    private XSSFCellStyle createBoldColorStyle(XSSFWorkbook wb, short size, byte[] rgb) {
        XSSFCellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints(size);
        font.setColor(new XSSFColor(rgb, null));
        style.setFont(font);
        return style;
    }

    private XSSFCellStyle createExcelHeaderStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 9);
        style.setFont(font);
        style.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 245, (byte) 245, (byte) 245}, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private XSSFCellStyle createBorderedStyle(XSSFWorkbook wb, boolean bold) {
        XSSFCellStyle style = wb.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        XSSFFont font = wb.createFont();
        font.setFontHeightInPoints((short) 9);
        font.setBold(bold);
        style.setFont(font);
        return style;
    }

    private XSSFCellStyle createExcelNumStyle(XSSFWorkbook wb, boolean bold) {
        XSSFCellStyle style = createBorderedStyle(wb, bold);
        style.setAlignment(HorizontalAlignment.RIGHT);
        style.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
        return style;
    }

    private XSSFCellStyle createBoldColorBorderedStyle(XSSFWorkbook wb, byte[] rgb) {
        XSSFCellStyle style = createBorderedStyle(wb, true);
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 9);
        font.setColor(new XSSFColor(rgb, null));
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    // ---- Data class ----

    private record CustomerBalance(
            Customer customer,
            BigDecimal totalBilled,
            BigDecimal totalPaid,
            BigDecimal ledgerBalance,
            BigDecimal creditLimit,
            BigDecimal utilization,
            String agingStatus,
            long unpaidStatements
    ) {}
}
