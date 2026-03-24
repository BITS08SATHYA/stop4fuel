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
import com.stopforfuel.backend.entity.ProductInventory;
import com.stopforfuel.backend.exception.ReportGenerationException;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ProductInventoryReportService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final java.text.DecimalFormat NUM_FMT = new java.text.DecimalFormat("#,##0.00");

    @Value("${app.company.name:SATHYA FUELS}")
    private String companyName;

    @Value("${app.company.address:No. 45, GST Road, Tambaram, Chennai - 600045}")
    private String companyAddress;

    @Value("${app.company.phone:044-2234 5678}")
    private String companyPhone;

    // ======================== PDF (OpenPDF) ========================

    public byte[] generatePdf(List<ProductInventory> data, LocalDate fromDate, LocalDate toDate, String productName) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4.rotate(), 20, 20, 20, 20);
            PdfWriter.getInstance(doc, out);
            doc.open();

            Font titleFont = new Font(Font.HELVETICA, 16, Font.BOLD, new Color(51, 51, 51));
            Font subFont = new Font(Font.HELVETICA, 8, Font.NORMAL, new Color(102, 102, 102));
            Font reportTitleFont = new Font(Font.HELVETICA, 12, Font.BOLD, new Color(230, 81, 0));
            Font headerFont = new Font(Font.HELVETICA, 7, Font.BOLD, Color.BLACK);
            Font cellFont = new Font(Font.HELVETICA, 7, Font.NORMAL, Color.BLACK);
            Font boldCellFont = new Font(Font.HELVETICA, 7, Font.BOLD, Color.BLACK);
            Font salesFont = new Font(Font.HELVETICA, 8, Font.BOLD, new Color(230, 81, 0));
            Font incomeFont = new Font(Font.HELVETICA, 7, Font.BOLD, new Color(21, 101, 192));
            Font summaryFont = new Font(Font.HELVETICA, 8, Font.BOLD, Color.BLACK);
            Font footerFont = new Font(Font.HELVETICA, 6, Font.NORMAL, new Color(153, 153, 153));

            // Header
            Paragraph company = new Paragraph(companyName, titleFont);
            company.setAlignment(Element.ALIGN_CENTER);
            doc.add(company);

            Paragraph address = new Paragraph(companyAddress + " | " + companyPhone, subFont);
            address.setAlignment(Element.ALIGN_CENTER);
            doc.add(address);

            doc.add(Chunk.NEWLINE);

            Paragraph title = new Paragraph("Non-Fuel Product Inventory Report", reportTitleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            doc.add(title);

            String displayProduct = productName.replace("_", " ");
            Paragraph meta = new Paragraph("Product: " + displayProduct + "  |  Period: " + fromDate.format(DATE_FMT) + " to " + toDate.format(DATE_FMT), subFont);
            meta.setAlignment(Element.ALIGN_LEFT);
            doc.add(meta);

            String generatedDate = java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy, hh:mm a"));
            Paragraph gen = new Paragraph("Generated: " + generatedDate, subFont);
            gen.setAlignment(Element.ALIGN_RIGHT);
            doc.add(gen);

            doc.add(Chunk.NEWLINE);

            // Table
            float[] widths = {4f, 8f, 14f, 8f, 8f, 9f, 9f, 9f, 12f};
            PdfPTable table = new PdfPTable(widths);
            table.setWidthPercentage(100);

            String[] headers = {"#", "DATE", "PRODUCT", "CATEGORY", "BRAND", "OPENING", "ARRIVALS (+)", "CLOSING", "UNITS SOLD"};
            Color headerBg = new Color(245, 245, 245);
            Color salesHeaderBg = new Color(255, 243, 224);

            for (int i = 0; i < headers.length; i++) {
                PdfPCell cell = new PdfPCell(new Phrase(headers[i], headerFont));
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                cell.setPadding(5);
                cell.setBackgroundColor(i == headers.length - 1 ? salesHeaderBg : headerBg);
                cell.setBorderColor(new Color(204, 204, 204));
                table.addCell(cell);
            }

            int rowNum = 0;
            double totalSales = 0;
            double totalIncome = 0;
            for (ProductInventory inv : data) {
                rowNum++;
                Color rowBg = (rowNum % 2 == 0) ? new Color(250, 250, 250) : Color.WHITE;
                Color borderColor = new Color(238, 238, 238);

                addCell(table, String.valueOf(rowNum), cellFont, Element.ALIGN_CENTER, rowBg, borderColor);
                addCell(table, inv.getDate().format(DATE_FMT), cellFont, Element.ALIGN_CENTER, rowBg, borderColor);
                addCell(table, inv.getProduct().getName(), boldCellFont, Element.ALIGN_LEFT, rowBg, borderColor);
                addCell(table, inv.getProduct().getCategory() != null ? inv.getProduct().getCategory() : "-", cellFont, Element.ALIGN_CENTER, rowBg, borderColor);
                addCell(table, inv.getProduct().getBrand() != null ? inv.getProduct().getBrand() : "-", cellFont, Element.ALIGN_CENTER, rowBg, borderColor);
                addCell(table, formatNum(inv.getOpenStock()), cellFont, Element.ALIGN_RIGHT, rowBg, borderColor);

                double income = inv.getIncomeStock() != null ? inv.getIncomeStock() : 0;
                addCell(table, income > 0 ? "+" + formatNum(income) : formatNum(income), incomeFont, Element.ALIGN_RIGHT, rowBg, borderColor);
                totalIncome += income;

                addCell(table, formatNum(inv.getCloseStock()), cellFont, Element.ALIGN_RIGHT, rowBg, borderColor);

                double sale = inv.getSales() != null ? inv.getSales() : 0;
                addCell(table, formatNum(sale), salesFont, Element.ALIGN_RIGHT, new Color(255, 248, 225), borderColor);
                totalSales += sale;
            }

            // Summary row
            PdfPCell totalLabel = new PdfPCell(new Phrase("TOTALS", summaryFont));
            totalLabel.setColspan(6);
            totalLabel.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalLabel.setPadding(5);
            totalLabel.setBackgroundColor(new Color(232, 232, 232));
            totalLabel.setBorderColor(new Color(204, 204, 204));
            table.addCell(totalLabel);

            PdfPCell incomeTotal = new PdfPCell(new Phrase(NUM_FMT.format(totalIncome), summaryFont));
            incomeTotal.setHorizontalAlignment(Element.ALIGN_RIGHT);
            incomeTotal.setPadding(5);
            incomeTotal.setBackgroundColor(new Color(227, 242, 253));
            incomeTotal.setBorderColor(new Color(204, 204, 204));
            table.addCell(incomeTotal);

            PdfPCell spacer = new PdfPCell(new Phrase("", cellFont));
            spacer.setBackgroundColor(new Color(232, 232, 232));
            spacer.setBorderColor(new Color(204, 204, 204));
            table.addCell(spacer);

            PdfPCell salesTotal = new PdfPCell(new Phrase(NUM_FMT.format(totalSales), new Font(Font.HELVETICA, 9, Font.BOLD, new Color(230, 81, 0))));
            salesTotal.setHorizontalAlignment(Element.ALIGN_RIGHT);
            salesTotal.setPadding(5);
            salesTotal.setBackgroundColor(new Color(255, 243, 224));
            salesTotal.setBorderColor(new Color(204, 204, 204));
            table.addCell(salesTotal);

            doc.add(table);

            // Footer
            doc.add(Chunk.NEWLINE);
            Paragraph footer = new Paragraph("Total Records: " + data.size() + "  |  Report generated on " + generatedDate, footerFont);
            footer.setAlignment(Element.ALIGN_CENTER);
            doc.add(footer);

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new ReportGenerationException("Failed to generate Product Inventory PDF report", e);
        }
    }

    private void addCell(PdfPTable table, String text, Font font, int alignment, Color bg, Color border) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(4);
        cell.setBackgroundColor(bg);
        cell.setBorderColor(border);
        table.addCell(cell);
    }

    // ======================== EXCEL (POI) ========================

    public byte[] generateExcel(List<ProductInventory> data, LocalDate fromDate, LocalDate toDate, String productName) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet("Product Inventory");

            // Styles
            XSSFCellStyle titleStyle = workbook.createCellStyle();
            XSSFFont titleFontXl = workbook.createFont();
            titleFontXl.setBold(true);
            titleFontXl.setFontHeightInPoints((short) 14);
            titleStyle.setFont(titleFontXl);
            titleStyle.setAlignment(HorizontalAlignment.CENTER);

            XSSFCellStyle subStyle = workbook.createCellStyle();
            XSSFFont subFontXl = workbook.createFont();
            subFontXl.setFontHeightInPoints((short) 9);
            subFontXl.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            subStyle.setFont(subFontXl);
            subStyle.setAlignment(HorizontalAlignment.CENTER);

            XSSFCellStyle headerStyle = workbook.createCellStyle();
            XSSFFont headerFontXl = workbook.createFont();
            headerFontXl.setBold(true);
            headerFontXl.setFontHeightInPoints((short) 9);
            headerStyle.setFont(headerFontXl);
            headerStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 245, (byte) 245, (byte) 245}, null));
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            XSSFCellStyle cellStyle = workbook.createCellStyle();
            cellStyle.setBorderBottom(BorderStyle.THIN);
            cellStyle.setBorderTop(BorderStyle.THIN);
            cellStyle.setBorderLeft(BorderStyle.THIN);
            cellStyle.setBorderRight(BorderStyle.THIN);
            XSSFFont cellFontXl = workbook.createFont();
            cellFontXl.setFontHeightInPoints((short) 9);
            cellStyle.setFont(cellFontXl);

            XSSFCellStyle numStyle = workbook.createCellStyle();
            numStyle.cloneStyleFrom(cellStyle);
            numStyle.setAlignment(HorizontalAlignment.RIGHT);
            numStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));

            XSSFCellStyle salesStyle = workbook.createCellStyle();
            salesStyle.cloneStyleFrom(numStyle);
            XSSFFont salesFontXl = workbook.createFont();
            salesFontXl.setBold(true);
            salesFontXl.setFontHeightInPoints((short) 9);
            salesFontXl.setColor(new XSSFColor(new byte[]{(byte) 230, (byte) 81, 0}, null));
            salesStyle.setFont(salesFontXl);
            salesStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 255, (byte) 248, (byte) 225}, null));
            salesStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            XSSFCellStyle summaryStyle = workbook.createCellStyle();
            summaryStyle.cloneStyleFrom(numStyle);
            XSSFFont summaryFontXl = workbook.createFont();
            summaryFontXl.setBold(true);
            summaryFontXl.setFontHeightInPoints((short) 10);
            summaryStyle.setFont(summaryFontXl);
            summaryStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 232, (byte) 232, (byte) 232}, null));
            summaryStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            int colCount = 9;

            // Title rows
            int rowIdx = 0;
            XSSFRow titleRow = sheet.createRow(rowIdx++);
            XSSFCell titleCell = titleRow.createCell(0);
            titleCell.setCellValue(companyName);
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, colCount - 1));

            XSSFRow addrRow = sheet.createRow(rowIdx++);
            XSSFCell addrCell = addrRow.createCell(0);
            addrCell.setCellValue(companyAddress + " | " + companyPhone);
            addrCell.setCellStyle(subStyle);
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, colCount - 1));

            rowIdx++; // blank row

            XSSFRow reportRow = sheet.createRow(rowIdx++);
            XSSFCell reportCell = reportRow.createCell(0);
            reportCell.setCellValue("Non-Fuel Product Inventory Report");
            XSSFCellStyle reportStyle = workbook.createCellStyle();
            XSSFFont reportFontXl = workbook.createFont();
            reportFontXl.setBold(true);
            reportFontXl.setFontHeightInPoints((short) 12);
            reportFontXl.setColor(new XSSFColor(new byte[]{(byte) 230, (byte) 81, 0}, null));
            reportStyle.setFont(reportFontXl);
            reportStyle.setAlignment(HorizontalAlignment.CENTER);
            reportCell.setCellStyle(reportStyle);
            sheet.addMergedRegion(new CellRangeAddress(rowIdx - 1, rowIdx - 1, 0, colCount - 1));

            XSSFRow metaRow = sheet.createRow(rowIdx++);
            XSSFCell metaCell = metaRow.createCell(0);
            String displayProduct = productName.replace("_", " ");
            metaCell.setCellValue("Product: " + displayProduct + "  |  Period: " + fromDate.format(DATE_FMT) + " to " + toDate.format(DATE_FMT));
            metaCell.setCellStyle(subStyle);
            sheet.addMergedRegion(new CellRangeAddress(rowIdx - 1, rowIdx - 1, 0, colCount - 1));

            rowIdx++; // blank row

            // Header row
            String[] headers = {"#", "Date", "Product", "Category", "Brand", "Opening", "Arrivals (+)", "Closing", "Units Sold"};
            XSSFRow headerRow = sheet.createRow(rowIdx++);
            for (int i = 0; i < headers.length; i++) {
                XSSFCell c = headerRow.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(headerStyle);
            }

            // Data rows
            int num = 0;
            double totalSales = 0;
            double totalIncome = 0;
            for (ProductInventory inv : data) {
                num++;
                XSSFRow row = sheet.createRow(rowIdx++);

                XSSFCell c0 = row.createCell(0);
                c0.setCellValue(num);
                c0.setCellStyle(cellStyle);

                XSSFCell c1 = row.createCell(1);
                c1.setCellValue(inv.getDate().format(DATE_FMT));
                c1.setCellStyle(cellStyle);

                XSSFCell c2 = row.createCell(2);
                c2.setCellValue(inv.getProduct().getName());
                c2.setCellStyle(cellStyle);

                XSSFCell c3 = row.createCell(3);
                c3.setCellValue(inv.getProduct().getCategory() != null ? inv.getProduct().getCategory() : "-");
                c3.setCellStyle(cellStyle);

                XSSFCell c4 = row.createCell(4);
                c4.setCellValue(inv.getProduct().getBrand() != null ? inv.getProduct().getBrand() : "-");
                c4.setCellStyle(cellStyle);

                XSSFCell c5 = row.createCell(5);
                c5.setCellValue(inv.getOpenStock() != null ? inv.getOpenStock() : 0);
                c5.setCellStyle(numStyle);

                double income = inv.getIncomeStock() != null ? inv.getIncomeStock() : 0;
                XSSFCell c6 = row.createCell(6);
                c6.setCellValue(income);
                c6.setCellStyle(numStyle);
                totalIncome += income;

                XSSFCell c7 = row.createCell(7);
                c7.setCellValue(inv.getCloseStock() != null ? inv.getCloseStock() : 0);
                c7.setCellStyle(numStyle);

                double sale = inv.getSales() != null ? inv.getSales() : 0;
                XSSFCell c8 = row.createCell(8);
                c8.setCellValue(sale);
                c8.setCellStyle(salesStyle);
                totalSales += sale;
            }

            // Summary row
            XSSFRow sumRow = sheet.createRow(rowIdx++);
            XSSFCell sumLabel = sumRow.createCell(0);
            sumLabel.setCellValue("TOTALS");
            sumLabel.setCellStyle(summaryStyle);
            for (int i = 1; i < 6; i++) {
                sumRow.createCell(i).setCellStyle(summaryStyle);
            }
            sheet.addMergedRegion(new CellRangeAddress(rowIdx - 1, rowIdx - 1, 0, 5));

            XSSFCell sumIncome = sumRow.createCell(6);
            sumIncome.setCellValue(totalIncome);
            sumIncome.setCellStyle(summaryStyle);

            sumRow.createCell(7).setCellStyle(summaryStyle);

            XSSFCell sumSales = sumRow.createCell(8);
            sumSales.setCellValue(totalSales);
            sumSales.setCellStyle(summaryStyle);

            // Auto-size columns
            for (int i = 0; i < colCount; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new ReportGenerationException("Failed to generate Product Inventory Excel report", e);
        }
    }

    private String formatNum(Double value) {
        if (value == null) return "0.00";
        return NUM_FMT.format(value);
    }
}
