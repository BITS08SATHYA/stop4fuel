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
import com.stopforfuel.backend.entity.Company;
import com.stopforfuel.backend.entity.NozzleInventory;
import com.stopforfuel.backend.exception.ReportGenerationException;
import com.stopforfuel.backend.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NozzleInventoryReportService {

    private final CompanyRepository companyRepository;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final java.text.DecimalFormat NUM_FMT = new java.text.DecimalFormat("#,##0.00");

    // ======================== Aggregation helpers ========================

    private record DailySummary(LocalDate date, String product, String nozzles, double totalSales, double rate, double amount) {}

    private List<DailySummary> aggregateByDate(List<NozzleInventory> data) {
        Map<LocalDate, List<NozzleInventory>> grouped = data.stream()
                .collect(Collectors.groupingBy(NozzleInventory::getDate, TreeMap::new, Collectors.toList()));

        List<DailySummary> summaries = new ArrayList<>();
        for (var entry : grouped.entrySet()) {
            List<NozzleInventory> dayItems = entry.getValue();
            String product = dayItems.get(0).getNozzle().getTank().getProduct().getName();
            String nozzleNames = dayItems.stream()
                    .map(inv -> inv.getNozzle().getNozzleName())
                    .distinct().sorted()
                    .collect(Collectors.joining(", "));
            double totalSales = dayItems.stream().mapToDouble(inv -> inv.getSales() != null ? inv.getSales() : 0).sum();
            double rate = dayItems.get(0).getRate() != null ? dayItems.get(0).getRate() : 0;
            double amount = totalSales * rate;
            summaries.add(new DailySummary(entry.getKey(), product, nozzleNames, totalSales, rate, amount));
        }
        return summaries;
    }

    // Pivot: one row per date, one column per nozzle showing close reading
    private record PivotRow(LocalDate date, Map<String, Double> nozzleReadings, double totalSales, double rate, double amount) {}

    private List<String> getNozzleNames(List<NozzleInventory> data) {
        return data.stream().map(inv -> inv.getNozzle().getNozzleName())
                .distinct().sorted().collect(Collectors.toList());
    }

    private List<PivotRow> buildPivotData(List<NozzleInventory> data) {
        Map<LocalDate, List<NozzleInventory>> grouped = data.stream()
                .collect(Collectors.groupingBy(NozzleInventory::getDate, TreeMap::new, Collectors.toList()));

        List<PivotRow> rows = new ArrayList<>();
        for (var entry : grouped.entrySet()) {
            List<NozzleInventory> dayItems = entry.getValue();
            Map<String, Double> readings = new LinkedHashMap<>();
            for (NozzleInventory inv : dayItems) {
                readings.put(inv.getNozzle().getNozzleName(), inv.getCloseMeterReading());
            }
            double totalSales = dayItems.stream().mapToDouble(inv -> inv.getSales() != null ? inv.getSales() : 0).sum();
            double rate = dayItems.get(0).getRate() != null ? dayItems.get(0).getRate() : 0;
            rows.add(new PivotRow(entry.getKey(), readings, totalSales, rate, totalSales * rate));
        }
        return rows;
    }

    // ======================== PDF (OpenPDF) ========================

    public byte[] generatePdf(List<NozzleInventory> data, LocalDate fromDate, LocalDate toDate, String filterLabel, String reportType) {
        try {
            Company companyEntity = companyRepository.findAll().stream().findFirst().orElse(null);
            String companyName = companyEntity != null && companyEntity.getName() != null ? companyEntity.getName() : "StopForFuel";
            String companyAddress = companyEntity != null && companyEntity.getAddress() != null ? companyEntity.getAddress() : "";
            String companyPhone = companyEntity != null && companyEntity.getPhone() != null ? companyEntity.getPhone() : "";

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
            Font summaryFont = new Font(Font.HELVETICA, 8, Font.BOLD, Color.BLACK);
            Font footerFont = new Font(Font.HELVETICA, 6, Font.NORMAL, new Color(153, 153, 153));

            String reportTitle;
            switch (reportType) {
                case "product_sales" -> reportTitle = "Product Daily Sales Report";
                case "meter_tracker" -> reportTitle = "Meter Reading Tracker";
                default -> reportTitle = "Nozzle Daily Inventory Report";
            }

            // Header
            Paragraph company = new Paragraph(companyName, titleFont);
            company.setAlignment(Element.ALIGN_CENTER);
            doc.add(company);
            Paragraph address = new Paragraph(companyAddress + " | " + companyPhone, subFont);
            address.setAlignment(Element.ALIGN_CENTER);
            doc.add(address);
            doc.add(Chunk.NEWLINE);

            Paragraph title = new Paragraph(reportTitle, reportTitleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            doc.add(title);

            String displayLabel = filterLabel.replace("_", " ");
            String labelPrefix = "product_sales".equals(reportType) || "meter_tracker".equals(reportType) ? "Product: " : "Filter: ";
            Paragraph meta = new Paragraph(labelPrefix + displayLabel + "  |  Period: " + fromDate.format(DATE_FMT) + " to " + toDate.format(DATE_FMT), subFont);
            meta.setAlignment(Element.ALIGN_LEFT);
            doc.add(meta);

            String generatedDate = java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy, hh:mm a"));
            Paragraph gen = new Paragraph("Generated: " + generatedDate, subFont);
            gen.setAlignment(Element.ALIGN_RIGHT);
            doc.add(gen);
            doc.add(Chunk.NEWLINE);

            switch (reportType) {
                case "product_sales" -> buildProductSalesPdfTable(doc, data, headerFont, cellFont, boldCellFont, salesFont, summaryFont);
                case "meter_tracker" -> buildMeterTrackerPdfTable(doc, data, headerFont, cellFont, boldCellFont, salesFont, summaryFont);
                default -> buildDetailedPdfTable(doc, data, headerFont, cellFont, boldCellFont, salesFont, summaryFont);
            }

            doc.add(Chunk.NEWLINE);
            int recordCount = "product_sales".equals(reportType) ? aggregateByDate(data).size()
                    : "meter_tracker".equals(reportType) ? buildPivotData(data).size() : data.size();
            Paragraph footer = new Paragraph("Total Records: " + recordCount + "  |  Report generated on " + generatedDate, footerFont);
            footer.setAlignment(Element.ALIGN_CENTER);
            doc.add(footer);

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new ReportGenerationException("Failed to generate Nozzle Inventory PDF report", e);
        }
    }

    // --- Product Daily Sales (aggregated by date) ---
    private void buildProductSalesPdfTable(Document doc, List<NozzleInventory> data, Font headerFont, Font cellFont, Font boldCellFont, Font salesFont, Font summaryFont) throws Exception {
        List<DailySummary> summaries = aggregateByDate(data);

        float[] widths = {4f, 9f, 10f, 12f, 10f, 8f, 12f};
        PdfPTable table = new PdfPTable(widths);
        table.setWidthPercentage(100);

        String[] headers = {"#", "DATE", "PRODUCT", "NOZZLES", "SALES (L)", "RATE", "AMOUNT"};
        Color headerBg = new Color(245, 245, 245);
        Color amountHeaderBg = new Color(255, 243, 224);

        for (int i = 0; i < headers.length; i++) {
            PdfPCell cell = new PdfPCell(new Phrase(headers[i], headerFont));
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cell.setPadding(5);
            cell.setBackgroundColor(i >= 4 ? amountHeaderBg : headerBg);
            cell.setBorderColor(new Color(204, 204, 204));
            table.addCell(cell);
        }

        int rowNum = 0;
        double grandSales = 0, grandAmount = 0;
        for (DailySummary s : summaries) {
            rowNum++;
            Color rowBg = (rowNum % 2 == 0) ? new Color(250, 250, 250) : Color.WHITE;
            Color borderColor = new Color(238, 238, 238);
            addCell(table, String.valueOf(rowNum), cellFont, Element.ALIGN_CENTER, rowBg, borderColor);
            addCell(table, s.date().format(DATE_FMT), cellFont, Element.ALIGN_CENTER, rowBg, borderColor);
            addCell(table, s.product(), boldCellFont, Element.ALIGN_CENTER, rowBg, borderColor);
            addCell(table, s.nozzles(), cellFont, Element.ALIGN_CENTER, rowBg, borderColor);
            addCell(table, formatNum(s.totalSales()), salesFont, Element.ALIGN_RIGHT, new Color(255, 248, 225), borderColor);
            addCell(table, formatNum(s.rate()), cellFont, Element.ALIGN_RIGHT, rowBg, borderColor);
            addCell(table, formatNum(s.amount()), salesFont, Element.ALIGN_RIGHT, new Color(255, 248, 225), borderColor);
            grandSales += s.totalSales();
            grandAmount += s.amount();
        }

        // Summary row
        PdfPCell totalLabel = new PdfPCell(new Phrase("TOTALS", summaryFont));
        totalLabel.setColspan(4);
        totalLabel.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totalLabel.setPadding(5);
        totalLabel.setBackgroundColor(new Color(232, 232, 232));
        totalLabel.setBorderColor(new Color(204, 204, 204));
        table.addCell(totalLabel);

        addSummaryCell(table, formatNum(grandSales), summaryFont);
        addSummaryCell(table, "", summaryFont);
        addSummaryCell(table, formatNum(grandAmount), new Font(Font.HELVETICA, 9, Font.BOLD, new Color(230, 81, 0)));

        doc.add(table);
    }

    // --- Meter Reading Tracker (pivot: date rows, nozzle columns) ---
    private void buildMeterTrackerPdfTable(Document doc, List<NozzleInventory> data, Font headerFont, Font cellFont, Font boldCellFont, Font salesFont, Font summaryFont) throws Exception {
        List<String> nozzleNames = getNozzleNames(data);
        List<PivotRow> rows = buildPivotData(data);

        // Columns: # | Date | N-7 | N-8 | ... | Sales | Rate | Amount
        int nozzleCount = nozzleNames.size();
        int colCount = 2 + nozzleCount + 3;
        float[] widths = new float[colCount];
        widths[0] = 4f; widths[1] = 9f;
        for (int i = 0; i < nozzleCount; i++) widths[2 + i] = 10f;
        widths[colCount - 3] = 10f; widths[colCount - 2] = 8f; widths[colCount - 1] = 12f;

        PdfPTable table = new PdfPTable(widths);
        table.setWidthPercentage(100);

        Color headerBg = new Color(245, 245, 245);
        Color amountBg = new Color(255, 243, 224);

        addCell(table, "#", headerFont, Element.ALIGN_CENTER, headerBg, new Color(204, 204, 204));
        addCell(table, "DATE", headerFont, Element.ALIGN_CENTER, headerBg, new Color(204, 204, 204));
        for (String name : nozzleNames) {
            addCell(table, name, headerFont, Element.ALIGN_CENTER, headerBg, new Color(204, 204, 204));
        }
        addCell(table, "SALES (L)", headerFont, Element.ALIGN_CENTER, amountBg, new Color(204, 204, 204));
        addCell(table, "RATE", headerFont, Element.ALIGN_CENTER, headerBg, new Color(204, 204, 204));
        addCell(table, "AMOUNT", headerFont, Element.ALIGN_CENTER, amountBg, new Color(204, 204, 204));

        int rowNum = 0;
        double grandSales = 0, grandAmount = 0;
        for (PivotRow r : rows) {
            rowNum++;
            Color rowBg = (rowNum % 2 == 0) ? new Color(250, 250, 250) : Color.WHITE;
            Color borderColor = new Color(238, 238, 238);
            addCell(table, String.valueOf(rowNum), cellFont, Element.ALIGN_CENTER, rowBg, borderColor);
            addCell(table, r.date().format(DATE_FMT), cellFont, Element.ALIGN_CENTER, rowBg, borderColor);
            for (String name : nozzleNames) {
                Double reading = r.nozzleReadings().get(name);
                addCell(table, reading != null ? formatNum(reading) : "-", boldCellFont, Element.ALIGN_RIGHT, rowBg, borderColor);
            }
            addCell(table, formatNum(r.totalSales()), salesFont, Element.ALIGN_RIGHT, new Color(255, 248, 225), borderColor);
            addCell(table, formatNum(r.rate()), cellFont, Element.ALIGN_RIGHT, rowBg, borderColor);
            addCell(table, formatNum(r.amount()), salesFont, Element.ALIGN_RIGHT, new Color(255, 248, 225), borderColor);
            grandSales += r.totalSales();
            grandAmount += r.amount();
        }

        // Summary
        PdfPCell totalLabel = new PdfPCell(new Phrase("TOTALS", summaryFont));
        totalLabel.setColspan(2 + nozzleCount);
        totalLabel.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totalLabel.setPadding(5);
        totalLabel.setBackgroundColor(new Color(232, 232, 232));
        totalLabel.setBorderColor(new Color(204, 204, 204));
        table.addCell(totalLabel);

        addSummaryCell(table, formatNum(grandSales), summaryFont);
        addSummaryCell(table, "", summaryFont);
        addSummaryCell(table, formatNum(grandAmount), new Font(Font.HELVETICA, 9, Font.BOLD, new Color(230, 81, 0)));

        doc.add(table);
    }

    // --- Detailed (per-nozzle with rate/amount) ---
    private void buildDetailedPdfTable(Document doc, List<NozzleInventory> data, Font headerFont, Font cellFont, Font boldCellFont, Font salesFont, Font summaryFont) throws Exception {
        float[] widths = {3f, 7f, 8f, 7f, 8f, 10f, 10f, 9f, 7f, 10f};
        PdfPTable table = new PdfPTable(widths);
        table.setWidthPercentage(100);

        String[] headers = {"#", "DATE", "NOZZLE", "PUMP", "PRODUCT", "OPEN READING", "CLOSE READING", "SALES (L)", "RATE", "AMOUNT"};
        Color headerBg = new Color(245, 245, 245);
        Color amountBg = new Color(255, 243, 224);

        for (int i = 0; i < headers.length; i++) {
            PdfPCell cell = new PdfPCell(new Phrase(headers[i], headerFont));
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cell.setPadding(5);
            cell.setBackgroundColor(i >= 7 ? amountBg : headerBg);
            cell.setBorderColor(new Color(204, 204, 204));
            table.addCell(cell);
        }

        int rowNum = 0;
        double totalSales = 0, totalAmount = 0;
        for (NozzleInventory inv : data) {
            rowNum++;
            Color rowBg = (rowNum % 2 == 0) ? new Color(250, 250, 250) : Color.WHITE;
            Color borderColor = new Color(238, 238, 238);
            addCell(table, String.valueOf(rowNum), cellFont, Element.ALIGN_CENTER, rowBg, borderColor);
            addCell(table, inv.getDate().format(DATE_FMT), cellFont, Element.ALIGN_CENTER, rowBg, borderColor);
            addCell(table, inv.getNozzle().getNozzleName(), boldCellFont, Element.ALIGN_CENTER, rowBg, borderColor);
            addCell(table, inv.getNozzle().getPump().getName(), cellFont, Element.ALIGN_CENTER, rowBg, borderColor);
            String productName = inv.getNozzle().getTank() != null && inv.getNozzle().getTank().getProduct() != null
                    ? inv.getNozzle().getTank().getProduct().getName() : "-";
            addCell(table, productName, cellFont, Element.ALIGN_CENTER, rowBg, borderColor);
            addCell(table, formatNum(inv.getOpenMeterReading()), cellFont, Element.ALIGN_RIGHT, rowBg, borderColor);
            addCell(table, formatNum(inv.getCloseMeterReading()), cellFont, Element.ALIGN_RIGHT, rowBg, borderColor);

            double sale = inv.getSales() != null ? inv.getSales() : 0;
            double rate = inv.getRate() != null ? inv.getRate() : 0;
            double amount = inv.getAmount() != null ? inv.getAmount() : 0;
            addCell(table, formatNum(sale), salesFont, Element.ALIGN_RIGHT, new Color(255, 248, 225), borderColor);
            addCell(table, formatNum(rate), cellFont, Element.ALIGN_RIGHT, rowBg, borderColor);
            addCell(table, formatNum(amount), salesFont, Element.ALIGN_RIGHT, new Color(255, 248, 225), borderColor);
            totalSales += sale;
            totalAmount += amount;
        }

        // Summary
        PdfPCell totalLabel = new PdfPCell(new Phrase("TOTALS", summaryFont));
        totalLabel.setColspan(7);
        totalLabel.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totalLabel.setPadding(5);
        totalLabel.setBackgroundColor(new Color(232, 232, 232));
        totalLabel.setBorderColor(new Color(204, 204, 204));
        table.addCell(totalLabel);

        addSummaryCell(table, formatNum(totalSales), summaryFont);
        addSummaryCell(table, "", summaryFont);
        addSummaryCell(table, formatNum(totalAmount), new Font(Font.HELVETICA, 9, Font.BOLD, new Color(230, 81, 0)));

        doc.add(table);
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

    private void addSummaryCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        cell.setPadding(5);
        cell.setBackgroundColor(new Color(232, 232, 232));
        cell.setBorderColor(new Color(204, 204, 204));
        table.addCell(cell);
    }

    // ======================== EXCEL (POI) ========================

    public byte[] generateExcel(List<NozzleInventory> data, LocalDate fromDate, LocalDate toDate, String filterLabel, String reportType) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Company companyEntity = companyRepository.findAll().stream().findFirst().orElse(null);
            String companyName = companyEntity != null && companyEntity.getName() != null ? companyEntity.getName() : "StopForFuel";
            String companyAddress = companyEntity != null && companyEntity.getAddress() != null ? companyEntity.getAddress() : "";
            String companyPhone = companyEntity != null && companyEntity.getPhone() != null ? companyEntity.getPhone() : "";

            String reportTitle;
            switch (reportType) {
                case "product_sales" -> reportTitle = "Product Daily Sales Report";
                case "meter_tracker" -> reportTitle = "Meter Reading Tracker";
                default -> reportTitle = "Nozzle Daily Inventory Report";
            }

            String sheetName = reportType.equals("meter_tracker") ? "Meter Reading Tracker"
                    : reportType.equals("product_sales") ? "Product Daily Sales" : "Nozzle Daily Inventory";
            XSSFSheet sheet = workbook.createSheet(sheetName);

            // Styles
            XSSFCellStyle titleStyle = createTitleStyle(workbook);
            XSSFCellStyle subStyle = createSubStyle(workbook);
            XSSFCellStyle headerStyle = createHeaderStyle(workbook);
            XSSFCellStyle cellStyle = createCellStyle(workbook);
            XSSFCellStyle numStyle = createNumStyle(workbook, cellStyle);
            XSSFCellStyle salesStyle = createSalesStyle(workbook, numStyle);
            XSSFCellStyle summaryStyle = createSummaryStyle(workbook, numStyle);

            List<String> nozzleNames = "meter_tracker".equals(reportType) ? getNozzleNames(data) : List.of();
            int colCount = switch (reportType) {
                case "product_sales" -> 7;
                case "meter_tracker" -> 2 + nozzleNames.size() + 3;
                default -> 10;
            };

            // Title rows
            int rowIdx = 0;
            rowIdx = writeExcelHeader(sheet, workbook, rowIdx, colCount, titleStyle, subStyle, reportTitle, filterLabel, fromDate, toDate, reportType, companyName, companyAddress, companyPhone);

            switch (reportType) {
                case "product_sales" -> rowIdx = buildProductSalesExcelRows(sheet, data, rowIdx, headerStyle, cellStyle, numStyle, salesStyle, summaryStyle, colCount);
                case "meter_tracker" -> rowIdx = buildMeterTrackerExcelRows(sheet, data, rowIdx, nozzleNames, headerStyle, cellStyle, numStyle, salesStyle, summaryStyle, colCount);
                default -> rowIdx = buildDetailedExcelRows(sheet, data, rowIdx, headerStyle, cellStyle, numStyle, salesStyle, summaryStyle, colCount);
            }

            for (int i = 0; i < colCount; i++) sheet.autoSizeColumn(i);
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new ReportGenerationException("Failed to generate Nozzle Inventory Excel report", e);
        }
    }

    private int writeExcelHeader(XSSFSheet sheet, XSSFWorkbook workbook, int rowIdx, int colCount,
            XSSFCellStyle titleStyle, XSSFCellStyle subStyle, String reportTitle, String filterLabel,
            LocalDate fromDate, LocalDate toDate, String reportType,
            String companyName, String companyAddress, String companyPhone) {
        XSSFRow titleRow = sheet.createRow(rowIdx++);
        XSSFCell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(companyName);
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(rowIdx - 1, rowIdx - 1, 0, colCount - 1));

        XSSFRow addrRow = sheet.createRow(rowIdx++);
        XSSFCell addrCell = addrRow.createCell(0);
        addrCell.setCellValue(companyAddress + " | " + companyPhone);
        addrCell.setCellStyle(subStyle);
        sheet.addMergedRegion(new CellRangeAddress(rowIdx - 1, rowIdx - 1, 0, colCount - 1));

        rowIdx++; // blank

        XSSFRow reportRow = sheet.createRow(rowIdx++);
        XSSFCell reportCell = reportRow.createCell(0);
        reportCell.setCellValue(reportTitle);
        XSSFCellStyle reportStyle = workbook.createCellStyle();
        XSSFFont reportFontXl = workbook.createFont();
        reportFontXl.setBold(true); reportFontXl.setFontHeightInPoints((short) 12);
        reportFontXl.setColor(new XSSFColor(new byte[]{(byte) 230, (byte) 81, 0}, null));
        reportStyle.setFont(reportFontXl);
        reportStyle.setAlignment(HorizontalAlignment.CENTER);
        reportCell.setCellStyle(reportStyle);
        sheet.addMergedRegion(new CellRangeAddress(rowIdx - 1, rowIdx - 1, 0, colCount - 1));

        XSSFRow metaRow = sheet.createRow(rowIdx++);
        XSSFCell metaCell = metaRow.createCell(0);
        String labelPrefix = "product_sales".equals(reportType) || "meter_tracker".equals(reportType) ? "Product: " : "Filter: ";
        metaCell.setCellValue(labelPrefix + filterLabel.replace("_", " ") + "  |  Period: " + fromDate.format(DATE_FMT) + " to " + toDate.format(DATE_FMT));
        metaCell.setCellStyle(subStyle);
        sheet.addMergedRegion(new CellRangeAddress(rowIdx - 1, rowIdx - 1, 0, colCount - 1));

        rowIdx++; // blank
        return rowIdx;
    }

    // --- Product Daily Sales Excel ---
    private int buildProductSalesExcelRows(XSSFSheet sheet, List<NozzleInventory> data, int rowIdx,
            XSSFCellStyle headerStyle, XSSFCellStyle cellStyle, XSSFCellStyle numStyle,
            XSSFCellStyle salesStyle, XSSFCellStyle summaryStyle, int colCount) {

        List<DailySummary> summaries = aggregateByDate(data);
        String[] headers = {"#", "Date", "Product", "Nozzles", "Sales (L)", "Rate", "Amount"};
        XSSFRow headerRow = sheet.createRow(rowIdx++);
        for (int i = 0; i < headers.length; i++) { XSSFCell c = headerRow.createCell(i); c.setCellValue(headers[i]); c.setCellStyle(headerStyle); }

        int num = 0; double grandSales = 0, grandAmount = 0;
        for (DailySummary s : summaries) {
            num++;
            XSSFRow row = sheet.createRow(rowIdx++);
            setCellVal(row, 0, num, cellStyle); setCellStr(row, 1, s.date().format(DATE_FMT), cellStyle);
            setCellStr(row, 2, s.product(), cellStyle); setCellStr(row, 3, s.nozzles(), cellStyle);
            setCellVal(row, 4, s.totalSales(), salesStyle); setCellVal(row, 5, s.rate(), numStyle);
            setCellVal(row, 6, s.amount(), salesStyle);
            grandSales += s.totalSales(); grandAmount += s.amount();
        }

        XSSFRow sumRow = sheet.createRow(rowIdx++);
        XSSFCell sumLabel = sumRow.createCell(0); sumLabel.setCellValue("TOTALS"); sumLabel.setCellStyle(summaryStyle);
        for (int i = 1; i < 4; i++) sumRow.createCell(i).setCellStyle(summaryStyle);
        sheet.addMergedRegion(new CellRangeAddress(rowIdx - 1, rowIdx - 1, 0, 3));
        setCellVal(sumRow, 4, grandSales, summaryStyle); sumRow.createCell(5).setCellStyle(summaryStyle);
        setCellVal(sumRow, 6, grandAmount, summaryStyle);
        return rowIdx;
    }

    // --- Meter Reading Tracker Excel ---
    private int buildMeterTrackerExcelRows(XSSFSheet sheet, List<NozzleInventory> data, int rowIdx,
            List<String> nozzleNames, XSSFCellStyle headerStyle, XSSFCellStyle cellStyle, XSSFCellStyle numStyle,
            XSSFCellStyle salesStyle, XSSFCellStyle summaryStyle, int colCount) {

        List<PivotRow> rows = buildPivotData(data);

        XSSFRow headerRow = sheet.createRow(rowIdx++);
        setCellStr(headerRow, 0, "#", headerStyle); setCellStr(headerRow, 1, "Date", headerStyle);
        for (int i = 0; i < nozzleNames.size(); i++) setCellStr(headerRow, 2 + i, nozzleNames.get(i), headerStyle);
        int salesCol = 2 + nozzleNames.size();
        setCellStr(headerRow, salesCol, "Sales (L)", headerStyle);
        setCellStr(headerRow, salesCol + 1, "Rate", headerStyle);
        setCellStr(headerRow, salesCol + 2, "Amount", headerStyle);

        int num = 0; double grandSales = 0, grandAmount = 0;
        for (PivotRow r : rows) {
            num++;
            XSSFRow row = sheet.createRow(rowIdx++);
            setCellVal(row, 0, num, cellStyle); setCellStr(row, 1, r.date().format(DATE_FMT), cellStyle);
            for (int i = 0; i < nozzleNames.size(); i++) {
                Double reading = r.nozzleReadings().get(nozzleNames.get(i));
                if (reading != null) setCellVal(row, 2 + i, reading, numStyle);
                else setCellStr(row, 2 + i, "-", cellStyle);
            }
            setCellVal(row, salesCol, r.totalSales(), salesStyle);
            setCellVal(row, salesCol + 1, r.rate(), numStyle);
            setCellVal(row, salesCol + 2, r.amount(), salesStyle);
            grandSales += r.totalSales(); grandAmount += r.amount();
        }

        XSSFRow sumRow = sheet.createRow(rowIdx++);
        XSSFCell sumLabel = sumRow.createCell(0); sumLabel.setCellValue("TOTALS"); sumLabel.setCellStyle(summaryStyle);
        for (int i = 1; i < salesCol; i++) sumRow.createCell(i).setCellStyle(summaryStyle);
        sheet.addMergedRegion(new CellRangeAddress(rowIdx - 1, rowIdx - 1, 0, salesCol - 1));
        setCellVal(sumRow, salesCol, grandSales, summaryStyle); sumRow.createCell(salesCol + 1).setCellStyle(summaryStyle);
        setCellVal(sumRow, salesCol + 2, grandAmount, summaryStyle);
        return rowIdx;
    }

    // --- Detailed Excel ---
    private int buildDetailedExcelRows(XSSFSheet sheet, List<NozzleInventory> data, int rowIdx,
            XSSFCellStyle headerStyle, XSSFCellStyle cellStyle, XSSFCellStyle numStyle,
            XSSFCellStyle salesStyle, XSSFCellStyle summaryStyle, int colCount) {

        String[] headers = {"#", "Date", "Nozzle", "Pump", "Product", "Open Reading", "Close Reading", "Sales (L)", "Rate", "Amount"};
        XSSFRow headerRow = sheet.createRow(rowIdx++);
        for (int i = 0; i < headers.length; i++) { XSSFCell c = headerRow.createCell(i); c.setCellValue(headers[i]); c.setCellStyle(headerStyle); }

        int num = 0; double totalSales = 0, totalAmount = 0;
        for (NozzleInventory inv : data) {
            num++;
            XSSFRow row = sheet.createRow(rowIdx++);
            setCellVal(row, 0, num, cellStyle); setCellStr(row, 1, inv.getDate().format(DATE_FMT), cellStyle);
            setCellStr(row, 2, inv.getNozzle().getNozzleName(), cellStyle);
            setCellStr(row, 3, inv.getNozzle().getPump().getName(), cellStyle);
            String pName = inv.getNozzle().getTank() != null && inv.getNozzle().getTank().getProduct() != null ? inv.getNozzle().getTank().getProduct().getName() : "-";
            setCellStr(row, 4, pName, cellStyle);
            setCellVal(row, 5, inv.getOpenMeterReading() != null ? inv.getOpenMeterReading() : 0, numStyle);
            setCellVal(row, 6, inv.getCloseMeterReading() != null ? inv.getCloseMeterReading() : 0, numStyle);
            double sale = inv.getSales() != null ? inv.getSales() : 0;
            double rate = inv.getRate() != null ? inv.getRate() : 0;
            double amount = inv.getAmount() != null ? inv.getAmount() : 0;
            setCellVal(row, 7, sale, salesStyle); setCellVal(row, 8, rate, numStyle); setCellVal(row, 9, amount, salesStyle);
            totalSales += sale; totalAmount += amount;
        }

        XSSFRow sumRow = sheet.createRow(rowIdx++);
        XSSFCell sumLabel = sumRow.createCell(0); sumLabel.setCellValue("TOTALS"); sumLabel.setCellStyle(summaryStyle);
        for (int i = 1; i < 7; i++) sumRow.createCell(i).setCellStyle(summaryStyle);
        sheet.addMergedRegion(new CellRangeAddress(rowIdx - 1, rowIdx - 1, 0, 6));
        setCellVal(sumRow, 7, totalSales, summaryStyle); sumRow.createCell(8).setCellStyle(summaryStyle);
        setCellVal(sumRow, 9, totalAmount, summaryStyle);
        return rowIdx;
    }

    // --- Style helpers ---
    private XSSFCellStyle createTitleStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle(); XSSFFont f = wb.createFont(); f.setBold(true); f.setFontHeightInPoints((short) 14); s.setFont(f); s.setAlignment(HorizontalAlignment.CENTER); return s;
    }
    private XSSFCellStyle createSubStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle(); XSSFFont f = wb.createFont(); f.setFontHeightInPoints((short) 9); f.setColor(IndexedColors.GREY_50_PERCENT.getIndex()); s.setFont(f); s.setAlignment(HorizontalAlignment.CENTER); return s;
    }
    private XSSFCellStyle createHeaderStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle(); XSSFFont f = wb.createFont(); f.setBold(true); f.setFontHeightInPoints((short) 9); s.setFont(f);
        s.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 245, (byte) 245, (byte) 245}, null)); s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER); s.setBorderBottom(BorderStyle.THIN); s.setBorderTop(BorderStyle.THIN); s.setBorderLeft(BorderStyle.THIN); s.setBorderRight(BorderStyle.THIN); return s;
    }
    private XSSFCellStyle createCellStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle(); s.setBorderBottom(BorderStyle.THIN); s.setBorderTop(BorderStyle.THIN); s.setBorderLeft(BorderStyle.THIN); s.setBorderRight(BorderStyle.THIN);
        XSSFFont f = wb.createFont(); f.setFontHeightInPoints((short) 9); s.setFont(f); return s;
    }
    private XSSFCellStyle createNumStyle(XSSFWorkbook wb, XSSFCellStyle base) {
        XSSFCellStyle s = wb.createCellStyle(); s.cloneStyleFrom(base); s.setAlignment(HorizontalAlignment.RIGHT); s.setDataFormat(wb.createDataFormat().getFormat("#,##0.00")); return s;
    }
    private XSSFCellStyle createSalesStyle(XSSFWorkbook wb, XSSFCellStyle numStyle) {
        XSSFCellStyle s = wb.createCellStyle(); s.cloneStyleFrom(numStyle); XSSFFont f = wb.createFont(); f.setBold(true); f.setFontHeightInPoints((short) 9);
        f.setColor(new XSSFColor(new byte[]{(byte) 230, (byte) 81, 0}, null)); s.setFont(f);
        s.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 255, (byte) 248, (byte) 225}, null)); s.setFillPattern(FillPatternType.SOLID_FOREGROUND); return s;
    }
    private XSSFCellStyle createSummaryStyle(XSSFWorkbook wb, XSSFCellStyle numStyle) {
        XSSFCellStyle s = wb.createCellStyle(); s.cloneStyleFrom(numStyle); XSSFFont f = wb.createFont(); f.setBold(true); f.setFontHeightInPoints((short) 10); s.setFont(f);
        s.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 232, (byte) 232, (byte) 232}, null)); s.setFillPattern(FillPatternType.SOLID_FOREGROUND); return s;
    }

    private void setCellVal(XSSFRow row, int col, double val, XSSFCellStyle style) {
        XSSFCell c = row.createCell(col); c.setCellValue(val); c.setCellStyle(style);
    }
    private void setCellStr(XSSFRow row, int col, String val, XSSFCellStyle style) {
        XSSFCell c = row.createCell(col); c.setCellValue(val); c.setCellStyle(style);
    }

    private String formatNum(Double value) {
        if (value == null) return "0.00";
        return NUM_FMT.format(value);
    }
    private String formatNum(double value) {
        return NUM_FMT.format(value);
    }
}
