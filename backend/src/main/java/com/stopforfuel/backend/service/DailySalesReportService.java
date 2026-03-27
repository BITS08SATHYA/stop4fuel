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
import com.stopforfuel.backend.dto.ProductSalesSummary;
import com.stopforfuel.backend.exception.ReportGenerationException;
import com.stopforfuel.backend.repository.InvoiceBillRepository;
import com.stopforfuel.backend.repository.PaymentRepository;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DailySalesReportService {

    private final InvoiceBillRepository invoiceBillRepository;
    private final PaymentRepository paymentRepository;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy, hh:mm a");
    private static final java.text.DecimalFormat NUM_FMT = new java.text.DecimalFormat("#,##0.00");

    @Value("${app.company.name:SATHYA FUELS}")
    private String companyName;

    @Value("${app.company.address:No. 45, GST Road, Tambaram, Chennai - 600045}")
    private String companyAddress;

    @Value("${app.company.phone:044-2234 5678}")
    private String companyPhone;

    // ======================== PDF ========================

    public byte[] generatePdf(LocalDate fromDate, LocalDate toDate) {
        try {
            LocalDateTime from = fromDate.atStartOfDay();
            LocalDateTime to = toDate.atTime(23, 59, 59);

            // Fetch all data
            List<ProductSalesSummary> productSales = invoiceBillRepository.getProductSalesSummary(
                    null, null, null, from, to);
            List<Object[]> invoiceSummary = invoiceBillRepository.getInvoiceSummary(from, to);
            List<Object[]> paymentModes = invoiceBillRepository.getPaymentModeDistribution(from, to);
            List<Object[]> topCustomers = invoiceBillRepository.getTopCustomersByRevenue(from, to);
            BigDecimal totalCollections = paymentRepository.sumPaymentsInDateRange(from, to);
            Long collectionCount = paymentRepository.countPaymentsInDateRange(from, to);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, 25, 25, 25, 25);
            PdfWriter.getInstance(doc, out);
            doc.open();

            Font titleFont = new Font(Font.HELVETICA, 16, Font.BOLD, new Color(51, 51, 51));
            Font subFont = new Font(Font.HELVETICA, 8, Font.NORMAL, new Color(102, 102, 102));
            Font sectionFont = new Font(Font.HELVETICA, 11, Font.BOLD, new Color(230, 81, 0));
            Font headerFont = new Font(Font.HELVETICA, 8, Font.BOLD, Color.BLACK);
            Font cellFont = new Font(Font.HELVETICA, 8, Font.NORMAL, Color.BLACK);
            Font boldCellFont = new Font(Font.HELVETICA, 8, Font.BOLD, Color.BLACK);
            Font amountFont = new Font(Font.HELVETICA, 8, Font.BOLD, new Color(230, 81, 0));
            Font footerFont = new Font(Font.HELVETICA, 6, Font.NORMAL, new Color(153, 153, 153));
            Font summaryLabelFont = new Font(Font.HELVETICA, 9, Font.BOLD, new Color(51, 51, 51));
            Font summaryValueFont = new Font(Font.HELVETICA, 12, Font.BOLD, new Color(230, 81, 0));

            // Header
            Paragraph company = new Paragraph(companyName, titleFont);
            company.setAlignment(Element.ALIGN_CENTER);
            doc.add(company);

            Paragraph address = new Paragraph(companyAddress + " | " + companyPhone, subFont);
            address.setAlignment(Element.ALIGN_CENTER);
            doc.add(address);

            doc.add(Chunk.NEWLINE);

            Paragraph title = new Paragraph("Daily Sales Summary Report", sectionFont);
            title.setAlignment(Element.ALIGN_CENTER);
            doc.add(title);

            String period = fromDate.equals(toDate)
                    ? "Date: " + fromDate.format(DATE_FMT)
                    : "Period: " + fromDate.format(DATE_FMT) + " to " + toDate.format(DATE_FMT);
            Paragraph meta = new Paragraph(period, subFont);
            meta.setAlignment(Element.ALIGN_CENTER);
            doc.add(meta);

            doc.add(Chunk.NEWLINE);

            // ---- Summary boxes ----
            BigDecimal totalCash = BigDecimal.ZERO;
            BigDecimal totalCredit = BigDecimal.ZERO;
            long cashCount = 0, creditCount = 0;

            for (Object[] row : invoiceSummary) {
                String billType = (String) row[0];
                long count = ((Number) row[2]).longValue();
                BigDecimal amount = (BigDecimal) row[3];
                if ("CASH".equals(billType)) {
                    totalCash = totalCash.add(amount);
                    cashCount += count;
                } else if ("CREDIT".equals(billType)) {
                    totalCredit = totalCredit.add(amount);
                    creditCount += count;
                }
            }
            BigDecimal grandTotal = totalCash.add(totalCredit);

            PdfPTable summaryTable = new PdfPTable(4);
            summaryTable.setWidthPercentage(100);
            summaryTable.setWidths(new float[]{25, 25, 25, 25});

            addSummaryBox(summaryTable, "Total Sales", formatRupee(grandTotal),
                    (cashCount + creditCount) + " bills", summaryLabelFont, summaryValueFont, subFont);
            addSummaryBox(summaryTable, "Cash Sales", formatRupee(totalCash),
                    cashCount + " bills", summaryLabelFont, summaryValueFont, subFont);
            addSummaryBox(summaryTable, "Credit Sales", formatRupee(totalCredit),
                    creditCount + " bills", summaryLabelFont, summaryValueFont, subFont);
            addSummaryBox(summaryTable, "Collections", formatRupee(totalCollections != null ? totalCollections : BigDecimal.ZERO),
                    (collectionCount != null ? collectionCount : 0) + " payments", summaryLabelFont, summaryValueFont, subFont);

            doc.add(summaryTable);
            doc.add(Chunk.NEWLINE);

            // ---- Section 1: Product-wise Sales ----
            Paragraph s1 = new Paragraph("Product-wise Sales", sectionFont);
            s1.setSpacingBefore(8);
            doc.add(s1);

            float[] prodWidths = {5f, 25f, 12f, 15f, 15f, 13f, 15f};
            PdfPTable prodTable = new PdfPTable(prodWidths);
            prodTable.setWidthPercentage(100);
            prodTable.setSpacingBefore(4);

            String[] prodHeaders = {"#", "Product", "Qty", "Gross Amount", "Discount", "Tax", "Net Amount"};
            addHeaderRow(prodTable, prodHeaders, headerFont);

            int rowNum = 0;
            BigDecimal totalQty = BigDecimal.ZERO, totalGross = BigDecimal.ZERO,
                    totalDiscount = BigDecimal.ZERO, totalNet = BigDecimal.ZERO;

            for (ProductSalesSummary ps : productSales) {
                rowNum++;
                Color rowBg = (rowNum % 2 == 0) ? new Color(250, 250, 250) : Color.WHITE;
                Color border = new Color(238, 238, 238);

                BigDecimal tax = ps.getTotalAmount().subtract(ps.getTotalGrossAmount().subtract(ps.getTotalDiscount())).abs();

                addCell(prodTable, String.valueOf(rowNum), cellFont, Element.ALIGN_CENTER, rowBg, border);
                addCell(prodTable, ps.getProductName(), boldCellFont, Element.ALIGN_LEFT, rowBg, border);
                addCell(prodTable, NUM_FMT.format(ps.getTotalQuantity()), cellFont, Element.ALIGN_RIGHT, rowBg, border);
                addCell(prodTable, formatRupee(ps.getTotalGrossAmount()), cellFont, Element.ALIGN_RIGHT, rowBg, border);
                addCell(prodTable, formatRupee(ps.getTotalDiscount()), cellFont, Element.ALIGN_RIGHT, rowBg, border);
                addCell(prodTable, formatRupee(tax), cellFont, Element.ALIGN_RIGHT, rowBg, border);
                addCell(prodTable, formatRupee(ps.getTotalAmount()), amountFont, Element.ALIGN_RIGHT, rowBg, border);

                totalQty = totalQty.add(ps.getTotalQuantity());
                totalGross = totalGross.add(ps.getTotalGrossAmount());
                totalDiscount = totalDiscount.add(ps.getTotalDiscount());
                totalNet = totalNet.add(ps.getTotalAmount());
            }

            // Totals row
            Color totalBg = new Color(232, 232, 232);
            Color totalBorder = new Color(204, 204, 204);
            addCell(prodTable, "", boldCellFont, Element.ALIGN_CENTER, totalBg, totalBorder);
            addCell(prodTable, "TOTAL", boldCellFont, Element.ALIGN_LEFT, totalBg, totalBorder);
            addCell(prodTable, NUM_FMT.format(totalQty), boldCellFont, Element.ALIGN_RIGHT, totalBg, totalBorder);
            addCell(prodTable, formatRupee(totalGross), boldCellFont, Element.ALIGN_RIGHT, totalBg, totalBorder);
            addCell(prodTable, formatRupee(totalDiscount), boldCellFont, Element.ALIGN_RIGHT, totalBg, totalBorder);
            BigDecimal totalTax = totalNet.subtract(totalGross.subtract(totalDiscount)).abs();
            addCell(prodTable, formatRupee(totalTax), boldCellFont, Element.ALIGN_RIGHT, totalBg, totalBorder);
            addCell(prodTable, formatRupee(totalNet), amountFont, Element.ALIGN_RIGHT, new Color(255, 243, 224), totalBorder);

            doc.add(prodTable);
            doc.add(Chunk.NEWLINE);

            // ---- Section 2: Payment Mode Breakdown ----
            Paragraph s2 = new Paragraph("Payment Mode Breakdown (Cash Bills)", sectionFont);
            s2.setSpacingBefore(8);
            doc.add(s2);

            float[] modeWidths = {5f, 30f, 15f, 25f};
            PdfPTable modeTable = new PdfPTable(modeWidths);
            modeTable.setWidthPercentage(70);
            modeTable.setSpacingBefore(4);
            modeTable.setHorizontalAlignment(Element.ALIGN_LEFT);

            String[] modeHeaders = {"#", "Payment Mode", "Bills", "Amount"};
            addHeaderRow(modeTable, modeHeaders, headerFont);

            rowNum = 0;
            for (Object[] row : paymentModes) {
                rowNum++;
                Color rowBg = (rowNum % 2 == 0) ? new Color(250, 250, 250) : Color.WHITE;
                Color border = new Color(238, 238, 238);

                String mode = (String) row[0];
                long count = ((Number) row[1]).longValue();
                BigDecimal amount = (BigDecimal) row[2];

                addCell(modeTable, String.valueOf(rowNum), cellFont, Element.ALIGN_CENTER, rowBg, border);
                addCell(modeTable, mode, boldCellFont, Element.ALIGN_LEFT, rowBg, border);
                addCell(modeTable, String.valueOf(count), cellFont, Element.ALIGN_CENTER, rowBg, border);
                addCell(modeTable, formatRupee(amount), amountFont, Element.ALIGN_RIGHT, rowBg, border);
            }

            doc.add(modeTable);
            doc.add(Chunk.NEWLINE);

            // ---- Section 3: Top Customers ----
            if (!topCustomers.isEmpty()) {
                Paragraph s3 = new Paragraph("Top Customers by Revenue", sectionFont);
                s3.setSpacingBefore(8);
                doc.add(s3);

                float[] custWidths = {5f, 30f, 15f, 25f};
                PdfPTable custTable = new PdfPTable(custWidths);
                custTable.setWidthPercentage(70);
                custTable.setSpacingBefore(4);
                custTable.setHorizontalAlignment(Element.ALIGN_LEFT);

                String[] custHeaders = {"#", "Customer", "Bills", "Amount"};
                addHeaderRow(custTable, custHeaders, headerFont);

                rowNum = 0;
                for (Object[] row : topCustomers) {
                    if (rowNum >= 10) break;
                    rowNum++;
                    Color rowBg = (rowNum % 2 == 0) ? new Color(250, 250, 250) : Color.WHITE;
                    Color border = new Color(238, 238, 238);

                    String name = (String) row[0];
                    long count = ((Number) row[1]).longValue();
                    BigDecimal amount = (BigDecimal) row[2];

                    addCell(custTable, String.valueOf(rowNum), cellFont, Element.ALIGN_CENTER, rowBg, border);
                    addCell(custTable, name != null ? name : "-", boldCellFont, Element.ALIGN_LEFT, rowBg, border);
                    addCell(custTable, String.valueOf(count), cellFont, Element.ALIGN_CENTER, rowBg, border);
                    addCell(custTable, formatRupee(amount), amountFont, Element.ALIGN_RIGHT, rowBg, border);
                }

                doc.add(custTable);
            }

            // Footer
            doc.add(Chunk.NEWLINE);
            String generatedDate = LocalDateTime.now().format(DATETIME_FMT);
            Paragraph footer = new Paragraph("Report generated on " + generatedDate, footerFont);
            footer.setAlignment(Element.ALIGN_CENTER);
            doc.add(footer);

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new ReportGenerationException("Failed to generate Daily Sales PDF report", e);
        }
    }

    // ======================== EXCEL ========================

    public byte[] generateExcel(LocalDate fromDate, LocalDate toDate) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            LocalDateTime from = fromDate.atStartOfDay();
            LocalDateTime to = toDate.atTime(23, 59, 59);

            List<ProductSalesSummary> productSales = invoiceBillRepository.getProductSalesSummary(
                    null, null, null, from, to);
            List<Object[]> invoiceSummary = invoiceBillRepository.getInvoiceSummary(from, to);
            List<Object[]> paymentModes = invoiceBillRepository.getPaymentModeDistribution(from, to);
            List<Object[]> topCustomers = invoiceBillRepository.getTopCustomersByRevenue(from, to);
            BigDecimal totalCollections = paymentRepository.sumPaymentsInDateRange(from, to);
            Long collectionCount = paymentRepository.countPaymentsInDateRange(from, to);

            // ---- Summary sheet ----
            XSSFSheet summarySheet = workbook.createSheet("Summary");

            XSSFCellStyle titleStyle = createTitleStyle(workbook);
            XSSFCellStyle subStyle = createSubStyle(workbook);
            XSSFCellStyle sectionStyle = createSectionStyle(workbook);
            XSSFCellStyle headerStyle = createHeaderStyle(workbook);
            XSSFCellStyle cellStyle = createCellStyle(workbook);
            XSSFCellStyle numStyle = createNumStyle(workbook);
            XSSFCellStyle boldNumStyle = createBoldNumStyle(workbook);
            XSSFCellStyle summaryValStyle = createSummaryValueStyle(workbook);

            int rowIdx = 0;

            // Title
            XSSFRow titleRow = summarySheet.createRow(rowIdx++);
            XSSFCell titleCell = titleRow.createCell(0);
            titleCell.setCellValue(companyName);
            titleCell.setCellStyle(titleStyle);
            summarySheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 6));

            XSSFRow addrRow = summarySheet.createRow(rowIdx++);
            addrRow.createCell(0).setCellValue(companyAddress + " | " + companyPhone);
            addrRow.getCell(0).setCellStyle(subStyle);
            summarySheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 6));

            rowIdx++;

            XSSFRow reportRow = summarySheet.createRow(rowIdx++);
            reportRow.createCell(0).setCellValue("Daily Sales Summary Report");
            reportRow.getCell(0).setCellStyle(sectionStyle);
            summarySheet.addMergedRegion(new CellRangeAddress(rowIdx - 1, rowIdx - 1, 0, 6));

            String period = fromDate.equals(toDate)
                    ? "Date: " + fromDate.format(DATE_FMT)
                    : "Period: " + fromDate.format(DATE_FMT) + " to " + toDate.format(DATE_FMT);
            XSSFRow periodRow = summarySheet.createRow(rowIdx++);
            periodRow.createCell(0).setCellValue(period);
            periodRow.getCell(0).setCellStyle(subStyle);
            summarySheet.addMergedRegion(new CellRangeAddress(rowIdx - 1, rowIdx - 1, 0, 6));

            rowIdx++; // blank

            // Summary values
            BigDecimal totalCash = BigDecimal.ZERO, totalCredit = BigDecimal.ZERO;
            long cashCount = 0, creditCount = 0;
            for (Object[] row : invoiceSummary) {
                String billType = (String) row[0];
                long count = ((Number) row[2]).longValue();
                BigDecimal amount = (BigDecimal) row[3];
                if ("CASH".equals(billType)) { totalCash = totalCash.add(amount); cashCount += count; }
                else if ("CREDIT".equals(billType)) { totalCredit = totalCredit.add(amount); creditCount += count; }
            }

            XSSFRow sumHeaderRow = summarySheet.createRow(rowIdx++);
            String[] sumLabels = {"Total Sales", "Cash Sales", "Credit Sales", "Collections"};
            for (int i = 0; i < sumLabels.length; i++) {
                sumHeaderRow.createCell(i).setCellValue(sumLabels[i]);
                sumHeaderRow.getCell(i).setCellStyle(headerStyle);
            }

            XSSFRow sumValRow = summarySheet.createRow(rowIdx++);
            BigDecimal grandTotal = totalCash.add(totalCredit);
            setCurrencyCell(sumValRow, 0, grandTotal, summaryValStyle);
            setCurrencyCell(sumValRow, 1, totalCash, summaryValStyle);
            setCurrencyCell(sumValRow, 2, totalCredit, summaryValStyle);
            setCurrencyCell(sumValRow, 3, totalCollections != null ? totalCollections : BigDecimal.ZERO, summaryValStyle);

            XSSFRow sumCountRow = summarySheet.createRow(rowIdx++);
            sumCountRow.createCell(0).setCellValue((cashCount + creditCount) + " bills");
            sumCountRow.createCell(1).setCellValue(cashCount + " bills");
            sumCountRow.createCell(2).setCellValue(creditCount + " bills");
            sumCountRow.createCell(3).setCellValue((collectionCount != null ? collectionCount : 0) + " payments");

            rowIdx += 2;

            // Product-wise sales
            XSSFRow prodSectionRow = summarySheet.createRow(rowIdx++);
            prodSectionRow.createCell(0).setCellValue("Product-wise Sales");
            prodSectionRow.getCell(0).setCellStyle(sectionStyle);
            summarySheet.addMergedRegion(new CellRangeAddress(rowIdx - 1, rowIdx - 1, 0, 6));

            String[] prodHeaders = {"#", "Product", "Qty", "Gross Amount", "Discount", "Tax", "Net Amount"};
            XSSFRow prodHeaderRow = summarySheet.createRow(rowIdx++);
            for (int i = 0; i < prodHeaders.length; i++) {
                prodHeaderRow.createCell(i).setCellValue(prodHeaders[i]);
                prodHeaderRow.getCell(i).setCellStyle(headerStyle);
            }

            int num = 0;
            BigDecimal totalQty = BigDecimal.ZERO, totalGross = BigDecimal.ZERO,
                    totalDiscount = BigDecimal.ZERO, totalNet = BigDecimal.ZERO;

            for (ProductSalesSummary ps : productSales) {
                num++;
                XSSFRow row = summarySheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(num);
                row.getCell(0).setCellStyle(cellStyle);
                row.createCell(1).setCellValue(ps.getProductName());
                row.getCell(1).setCellStyle(cellStyle);
                setCurrencyCell(row, 2, ps.getTotalQuantity(), numStyle);
                setCurrencyCell(row, 3, ps.getTotalGrossAmount(), numStyle);
                setCurrencyCell(row, 4, ps.getTotalDiscount(), numStyle);
                BigDecimal tax = ps.getTotalAmount().subtract(ps.getTotalGrossAmount().subtract(ps.getTotalDiscount())).abs();
                setCurrencyCell(row, 5, tax, numStyle);
                setCurrencyCell(row, 6, ps.getTotalAmount(), boldNumStyle);

                totalQty = totalQty.add(ps.getTotalQuantity());
                totalGross = totalGross.add(ps.getTotalGrossAmount());
                totalDiscount = totalDiscount.add(ps.getTotalDiscount());
                totalNet = totalNet.add(ps.getTotalAmount());
            }

            XSSFRow totRow = summarySheet.createRow(rowIdx++);
            totRow.createCell(0).setCellStyle(boldNumStyle);
            totRow.createCell(1).setCellValue("TOTAL");
            totRow.getCell(1).setCellStyle(boldNumStyle);
            setCurrencyCell(totRow, 2, totalQty, boldNumStyle);
            setCurrencyCell(totRow, 3, totalGross, boldNumStyle);
            setCurrencyCell(totRow, 4, totalDiscount, boldNumStyle);
            BigDecimal totalTax = totalNet.subtract(totalGross.subtract(totalDiscount)).abs();
            setCurrencyCell(totRow, 5, totalTax, boldNumStyle);
            setCurrencyCell(totRow, 6, totalNet, boldNumStyle);

            rowIdx += 2;

            // Payment mode breakdown
            XSSFRow modeSectionRow = summarySheet.createRow(rowIdx++);
            modeSectionRow.createCell(0).setCellValue("Payment Mode Breakdown (Cash Bills)");
            modeSectionRow.getCell(0).setCellStyle(sectionStyle);
            summarySheet.addMergedRegion(new CellRangeAddress(rowIdx - 1, rowIdx - 1, 0, 3));

            String[] modeHeaders = {"#", "Payment Mode", "Bills", "Amount"};
            XSSFRow modeHeaderRow = summarySheet.createRow(rowIdx++);
            for (int i = 0; i < modeHeaders.length; i++) {
                modeHeaderRow.createCell(i).setCellValue(modeHeaders[i]);
                modeHeaderRow.getCell(i).setCellStyle(headerStyle);
            }

            num = 0;
            for (Object[] row : paymentModes) {
                num++;
                XSSFRow r = summarySheet.createRow(rowIdx++);
                r.createCell(0).setCellValue(num);
                r.getCell(0).setCellStyle(cellStyle);
                r.createCell(1).setCellValue((String) row[0]);
                r.getCell(1).setCellStyle(cellStyle);
                r.createCell(2).setCellValue(((Number) row[1]).longValue());
                r.getCell(2).setCellStyle(cellStyle);
                setCurrencyCell(r, 3, (BigDecimal) row[2], boldNumStyle);
            }

            rowIdx += 2;

            // Top customers
            if (!topCustomers.isEmpty()) {
                XSSFRow custSectionRow = summarySheet.createRow(rowIdx++);
                custSectionRow.createCell(0).setCellValue("Top Customers by Revenue");
                custSectionRow.getCell(0).setCellStyle(sectionStyle);
                summarySheet.addMergedRegion(new CellRangeAddress(rowIdx - 1, rowIdx - 1, 0, 3));

                String[] custHeaders = {"#", "Customer", "Bills", "Amount"};
                XSSFRow custHeaderRow = summarySheet.createRow(rowIdx++);
                for (int i = 0; i < custHeaders.length; i++) {
                    custHeaderRow.createCell(i).setCellValue(custHeaders[i]);
                    custHeaderRow.getCell(i).setCellStyle(headerStyle);
                }

                num = 0;
                for (Object[] row : topCustomers) {
                    if (num >= 10) break;
                    num++;
                    XSSFRow r = summarySheet.createRow(rowIdx++);
                    r.createCell(0).setCellValue(num);
                    r.getCell(0).setCellStyle(cellStyle);
                    r.createCell(1).setCellValue(row[0] != null ? (String) row[0] : "-");
                    r.getCell(1).setCellStyle(cellStyle);
                    r.createCell(2).setCellValue(((Number) row[1]).longValue());
                    r.getCell(2).setCellStyle(cellStyle);
                    setCurrencyCell(r, 3, (BigDecimal) row[2], boldNumStyle);
                }
            }

            // Auto-size
            for (int i = 0; i < 7; i++) summarySheet.autoSizeColumn(i);

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new ReportGenerationException("Failed to generate Daily Sales Excel report", e);
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

    private XSSFCellStyle createTitleStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private XSSFCellStyle createSubStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setFontHeightInPoints((short) 9);
        font.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private XSSFCellStyle createSectionStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        font.setColor(new XSSFColor(new byte[]{(byte) 230, (byte) 81, 0}, null));
        style.setFont(font);
        return style;
    }

    private XSSFCellStyle createHeaderStyle(XSSFWorkbook wb) {
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

    private XSSFCellStyle createCellStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        XSSFFont font = wb.createFont();
        font.setFontHeightInPoints((short) 9);
        style.setFont(font);
        return style;
    }

    private XSSFCellStyle createNumStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = createCellStyle(wb);
        style.setAlignment(HorizontalAlignment.RIGHT);
        style.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
        return style;
    }

    private XSSFCellStyle createBoldNumStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = createNumStyle(wb);
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 9);
        style.setFont(font);
        return style;
    }

    private XSSFCellStyle createSummaryValueStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        font.setColor(new XSSFColor(new byte[]{(byte) 230, (byte) 81, 0}, null));
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.RIGHT);
        style.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
        return style;
    }
}
