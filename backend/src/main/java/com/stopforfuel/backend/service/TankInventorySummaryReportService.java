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
import com.stopforfuel.backend.entity.Tank;
import com.stopforfuel.backend.entity.TankInventory;
import com.stopforfuel.backend.exception.ReportGenerationException;
import com.stopforfuel.backend.repository.TankInventoryRepository;
import com.stopforfuel.backend.repository.TankRepository;
import com.stopforfuel.config.SecurityUtils;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TankInventorySummaryReportService {

    private final TankRepository tankRepository;
    private final TankInventoryRepository tankInventoryRepository;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy, hh:mm a");
    private static final java.text.DecimalFormat NUM_FMT = new java.text.DecimalFormat("#,##0.00");

    @Value("${app.company.name:SATHYA FUELS}")
    private String companyName;

    @Value("${app.company.address:No. 45, GST Road, Tambaram, Chennai - 600045}")
    private String companyAddress;

    @Value("${app.company.phone:044-2234 5678}")
    private String companyPhone;

    /**
     * Build the report data: current tank status + period movement summary.
     */
    private ReportData buildReportData(LocalDate fromDate, LocalDate toDate) {
        Long scid = SecurityUtils.getScid();
        List<Tank> tanks = tankRepository.findByActiveAndScid(true, scid);
        List<TankInventory> inventories = tankInventoryRepository.findByScidAndDateBetween(scid, fromDate, toDate);

        // Aggregate movement per tank
        Map<Long, TankMovement> movementMap = new LinkedHashMap<>();
        for (Tank t : tanks) {
            movementMap.put(t.getId(), new TankMovement(t));
        }

        for (TankInventory inv : inventories) {
            TankMovement mv = movementMap.get(inv.getTank().getId());
            if (mv == null) continue;
            mv.totalIncome += inv.getIncomeStock() != null ? inv.getIncomeStock() : 0;
            mv.totalSales += inv.getSaleStock() != null ? inv.getSaleStock() : 0;
            mv.days++;
            // Track first and last readings
            if (mv.firstDate == null || inv.getDate().isBefore(mv.firstDate)) {
                mv.firstDate = inv.getDate();
                mv.openingStock = inv.getOpenStock() != null ? inv.getOpenStock() : 0;
                mv.openDip = inv.getOpenDip();
            }
            if (mv.lastDate == null || inv.getDate().isAfter(mv.lastDate)) {
                mv.lastDate = inv.getDate();
                mv.closingStock = inv.getCloseStock() != null ? inv.getCloseStock() : 0;
                mv.closeDip = inv.getCloseDip();
            }
        }

        return new ReportData(new ArrayList<>(movementMap.values()));
    }

    // ======================== PDF ========================

    public byte[] generatePdf(LocalDate fromDate, LocalDate toDate) {
        try {
            ReportData data = buildReportData(fromDate, toDate);

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
            Font salesFont = new Font(Font.HELVETICA, 7, Font.BOLD, new Color(230, 81, 0));
            Font incomeFont = new Font(Font.HELVETICA, 7, Font.BOLD, new Color(21, 101, 192));
            Font warningFont = new Font(Font.HELVETICA, 7, Font.BOLD, new Color(198, 40, 40));
            Font footerFont = new Font(Font.HELVETICA, 6, Font.NORMAL, new Color(153, 153, 153));

            // Header
            Paragraph company = new Paragraph(companyName, titleFont);
            company.setAlignment(Element.ALIGN_CENTER);
            doc.add(company);

            Paragraph address = new Paragraph(companyAddress + " | " + companyPhone, subFont);
            address.setAlignment(Element.ALIGN_CENTER);
            doc.add(address);

            doc.add(Chunk.NEWLINE);

            Paragraph title = new Paragraph("Tank Inventory Report", sectionFont);
            title.setAlignment(Element.ALIGN_CENTER);
            doc.add(title);

            String period = fromDate.equals(toDate)
                    ? "Date: " + fromDate.format(DATE_FMT)
                    : "Period: " + fromDate.format(DATE_FMT) + " to " + toDate.format(DATE_FMT);
            Paragraph meta = new Paragraph(period, subFont);
            meta.setAlignment(Element.ALIGN_CENTER);
            doc.add(meta);

            doc.add(Chunk.NEWLINE);

            // ---- Section 1: Current Tank Status ----
            Paragraph s1 = new Paragraph("Current Tank Status", new Font(Font.HELVETICA, 10, Font.BOLD, new Color(51, 51, 51)));
            s1.setSpacingBefore(4);
            doc.add(s1);

            float[] statusWidths = {4f, 12f, 10f, 10f, 10f, 10f, 8f};
            PdfPTable statusTable = new PdfPTable(statusWidths);
            statusTable.setWidthPercentage(80);
            statusTable.setSpacingBefore(4);
            statusTable.setHorizontalAlignment(Element.ALIGN_LEFT);

            String[] statusHeaders = {"#", "Tank", "Product", "Capacity (L)", "Available (L)", "Threshold (L)", "Status"};
            addHeaderRow(statusTable, statusHeaders, headerFont);

            int rowNum = 0;
            double totalCapacity = 0, totalAvailable = 0;
            for (TankMovement mv : data.movements) {
                Tank t = mv.tank;
                rowNum++;
                Color rowBg = (rowNum % 2 == 0) ? new Color(250, 250, 250) : Color.WHITE;
                Color border = new Color(238, 238, 238);

                addCell(statusTable, String.valueOf(rowNum), cellFont, Element.ALIGN_CENTER, rowBg, border);
                addCell(statusTable, t.getName(), boldCellFont, Element.ALIGN_LEFT, rowBg, border);
                addCell(statusTable, t.getProduct() != null ? t.getProduct().getName() : "-", cellFont, Element.ALIGN_LEFT, rowBg, border);
                addCell(statusTable, formatNum(t.getCapacity()), cellFont, Element.ALIGN_RIGHT, rowBg, border);
                addCell(statusTable, formatNum(t.getAvailableStock()), boldCellFont, Element.ALIGN_RIGHT, rowBg, border);
                addCell(statusTable, formatNum(t.getThresholdStock()), cellFont, Element.ALIGN_RIGHT, rowBg, border);

                boolean low = t.isBelowThreshold();
                addCell(statusTable, low ? "LOW" : "OK", low ? warningFont : boldCellFont,
                        Element.ALIGN_CENTER, low ? new Color(255, 235, 238) : new Color(232, 245, 233), border);

                totalCapacity += t.getCapacity() != null ? t.getCapacity() : 0;
                totalAvailable += t.getAvailableStock() != null ? t.getAvailableStock() : 0;
            }

            // Totals
            Color totalBg = new Color(232, 232, 232);
            Color totalBorder = new Color(204, 204, 204);
            PdfPCell totalLabel = new PdfPCell(new Phrase("TOTAL", boldCellFont));
            totalLabel.setColspan(3);
            totalLabel.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalLabel.setPadding(4);
            totalLabel.setBackgroundColor(totalBg);
            totalLabel.setBorderColor(totalBorder);
            statusTable.addCell(totalLabel);
            addCell(statusTable, formatNum(totalCapacity), boldCellFont, Element.ALIGN_RIGHT, totalBg, totalBorder);
            addCell(statusTable, formatNum(totalAvailable), boldCellFont, Element.ALIGN_RIGHT, totalBg, totalBorder);
            addCell(statusTable, "", cellFont, Element.ALIGN_CENTER, totalBg, totalBorder);
            addCell(statusTable, "", cellFont, Element.ALIGN_CENTER, totalBg, totalBorder);

            doc.add(statusTable);
            doc.add(Chunk.NEWLINE);

            // ---- Section 2: Period Movement Summary ----
            Paragraph s2 = new Paragraph("Stock Movement Summary", new Font(Font.HELVETICA, 10, Font.BOLD, new Color(51, 51, 51)));
            s2.setSpacingBefore(8);
            doc.add(s2);

            float[] mvWidths = {4f, 12f, 10f, 7f, 10f, 10f, 10f, 10f, 7f, 10f};
            PdfPTable mvTable = new PdfPTable(mvWidths);
            mvTable.setWidthPercentage(100);
            mvTable.setSpacingBefore(4);

            String[] mvHeaders = {"#", "Tank", "Product", "Open Dip", "Opening (L)", "Income (L)", "Sales (L)", "Closing (L)", "Close Dip", "Avg Daily Sales"};
            addHeaderRow(mvTable, mvHeaders, headerFont);

            rowNum = 0;
            double grandIncome = 0, grandSales = 0;
            for (TankMovement mv : data.movements) {
                if (mv.days == 0) continue;
                rowNum++;
                Color rowBg = (rowNum % 2 == 0) ? new Color(250, 250, 250) : Color.WHITE;
                Color border = new Color(238, 238, 238);

                double avgDaily = mv.days > 0 ? mv.totalSales / mv.days : 0;

                addCell(mvTable, String.valueOf(rowNum), cellFont, Element.ALIGN_CENTER, rowBg, border);
                addCell(mvTable, mv.tank.getName(), boldCellFont, Element.ALIGN_LEFT, rowBg, border);
                addCell(mvTable, mv.tank.getProduct() != null ? mv.tank.getProduct().getName() : "-", cellFont, Element.ALIGN_LEFT, rowBg, border);
                addCell(mvTable, mv.openDip != null ? mv.openDip : "-", cellFont, Element.ALIGN_CENTER, rowBg, border);
                addCell(mvTable, formatNum(mv.openingStock), cellFont, Element.ALIGN_RIGHT, rowBg, border);
                addCell(mvTable, "+" + formatNum(mv.totalIncome), incomeFont, Element.ALIGN_RIGHT, rowBg, border);
                addCell(mvTable, formatNum(mv.totalSales), salesFont, Element.ALIGN_RIGHT, new Color(255, 248, 225), border);
                addCell(mvTable, formatNum(mv.closingStock), cellFont, Element.ALIGN_RIGHT, rowBg, border);
                addCell(mvTable, mv.closeDip != null ? mv.closeDip : "-", cellFont, Element.ALIGN_CENTER, rowBg, border);
                addCell(mvTable, formatNum(avgDaily), cellFont, Element.ALIGN_RIGHT, rowBg, border);

                grandIncome += mv.totalIncome;
                grandSales += mv.totalSales;
            }

            if (rowNum > 0) {
                PdfPCell mvTotalLabel = new PdfPCell(new Phrase("TOTAL", boldCellFont));
                mvTotalLabel.setColspan(5);
                mvTotalLabel.setHorizontalAlignment(Element.ALIGN_RIGHT);
                mvTotalLabel.setPadding(4);
                mvTotalLabel.setBackgroundColor(totalBg);
                mvTotalLabel.setBorderColor(totalBorder);
                mvTable.addCell(mvTotalLabel);
                addCell(mvTable, "+" + formatNum(grandIncome), incomeFont, Element.ALIGN_RIGHT, new Color(227, 242, 253), totalBorder);
                addCell(mvTable, formatNum(grandSales), salesFont, Element.ALIGN_RIGHT, new Color(255, 243, 224), totalBorder);

                PdfPCell spacer = new PdfPCell(new Phrase("", cellFont));
                spacer.setColspan(3);
                spacer.setBackgroundColor(totalBg);
                spacer.setBorderColor(totalBorder);
                mvTable.addCell(spacer);
            }

            doc.add(mvTable);

            // Footer
            doc.add(Chunk.NEWLINE);
            String generatedDate = LocalDateTime.now().format(DATETIME_FMT);
            Paragraph footer = new Paragraph("Report generated on " + generatedDate + "  |  " + data.movements.size() + " active tanks", footerFont);
            footer.setAlignment(Element.ALIGN_CENTER);
            doc.add(footer);

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new ReportGenerationException("Failed to generate Tank Inventory PDF report", e);
        }
    }

    // ======================== EXCEL ========================

    public byte[] generateExcel(LocalDate fromDate, LocalDate toDate) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ReportData data = buildReportData(fromDate, toDate);

            // Styles
            XSSFCellStyle titleStyle = createStyle(workbook, (short) 14, true, HorizontalAlignment.CENTER, null);
            XSSFCellStyle subStyle = createStyle(workbook, (short) 9, false, HorizontalAlignment.CENTER, IndexedColors.GREY_50_PERCENT);
            XSSFCellStyle sectionStyle = createBoldColorStyle(workbook, (short) 11, new byte[]{(byte) 230, (byte) 81, 0});
            XSSFCellStyle headerStyle = createHeaderStyle(workbook);
            XSSFCellStyle cellStyle = createBorderedStyle(workbook, false);
            XSSFCellStyle boldCellStyle = createBorderedStyle(workbook, true);
            XSSFCellStyle numStyle = createNumStyle(workbook, false);
            XSSFCellStyle boldNumStyle = createNumStyle(workbook, true);
            XSSFCellStyle warningStyle = createBoldColorStyle(workbook, (short) 9, new byte[]{(byte) 198, (byte) 40, (byte) 40});
            warningStyle.setBorderBottom(BorderStyle.THIN);
            warningStyle.setBorderTop(BorderStyle.THIN);
            warningStyle.setBorderLeft(BorderStyle.THIN);
            warningStyle.setBorderRight(BorderStyle.THIN);
            warningStyle.setAlignment(HorizontalAlignment.CENTER);

            XSSFCellStyle okStyle = createBoldColorStyle(workbook, (short) 9, new byte[]{(byte) 46, (byte) 125, (byte) 50});
            okStyle.setBorderBottom(BorderStyle.THIN);
            okStyle.setBorderTop(BorderStyle.THIN);
            okStyle.setBorderLeft(BorderStyle.THIN);
            okStyle.setBorderRight(BorderStyle.THIN);
            okStyle.setAlignment(HorizontalAlignment.CENTER);

            // ---- Sheet 1: Current Status ----
            XSSFSheet statusSheet = workbook.createSheet("Tank Status");
            int rowIdx = 0;
            int colCount = 7;

            XSSFRow tr = statusSheet.createRow(rowIdx++);
            tr.createCell(0).setCellValue(companyName);
            tr.getCell(0).setCellStyle(titleStyle);
            statusSheet.addMergedRegion(new CellRangeAddress(0, 0, 0, colCount - 1));

            XSSFRow ar = statusSheet.createRow(rowIdx++);
            ar.createCell(0).setCellValue(companyAddress + " | " + companyPhone);
            ar.getCell(0).setCellStyle(subStyle);
            statusSheet.addMergedRegion(new CellRangeAddress(1, 1, 0, colCount - 1));

            rowIdx++;

            XSSFRow sr = statusSheet.createRow(rowIdx++);
            sr.createCell(0).setCellValue("Tank Inventory Report — Current Status");
            sr.getCell(0).setCellStyle(sectionStyle);
            statusSheet.addMergedRegion(new CellRangeAddress(rowIdx - 1, rowIdx - 1, 0, colCount - 1));

            String period = fromDate.equals(toDate)
                    ? "Date: " + fromDate.format(DATE_FMT)
                    : "Period: " + fromDate.format(DATE_FMT) + " to " + toDate.format(DATE_FMT);
            XSSFRow pr = statusSheet.createRow(rowIdx++);
            pr.createCell(0).setCellValue(period);
            pr.getCell(0).setCellStyle(subStyle);
            statusSheet.addMergedRegion(new CellRangeAddress(rowIdx - 1, rowIdx - 1, 0, colCount - 1));

            rowIdx++;

            String[] statusHeaders = {"#", "Tank", "Product", "Capacity (L)", "Available (L)", "Threshold (L)", "Status"};
            XSSFRow hr = statusSheet.createRow(rowIdx++);
            for (int i = 0; i < statusHeaders.length; i++) {
                hr.createCell(i).setCellValue(statusHeaders[i]);
                hr.getCell(i).setCellStyle(headerStyle);
            }

            int num = 0;
            for (TankMovement mv : data.movements) {
                num++;
                Tank t = mv.tank;
                XSSFRow row = statusSheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(num);
                row.getCell(0).setCellStyle(cellStyle);
                row.createCell(1).setCellValue(t.getName());
                row.getCell(1).setCellStyle(boldCellStyle);
                row.createCell(2).setCellValue(t.getProduct() != null ? t.getProduct().getName() : "-");
                row.getCell(2).setCellStyle(cellStyle);

                XSSFCell capCell = row.createCell(3);
                capCell.setCellValue(t.getCapacity() != null ? t.getCapacity() : 0);
                capCell.setCellStyle(numStyle);

                XSSFCell availCell = row.createCell(4);
                availCell.setCellValue(t.getAvailableStock() != null ? t.getAvailableStock() : 0);
                availCell.setCellStyle(boldNumStyle);

                XSSFCell threshCell = row.createCell(5);
                threshCell.setCellValue(t.getThresholdStock() != null ? t.getThresholdStock() : 0);
                threshCell.setCellStyle(numStyle);

                boolean low = t.isBelowThreshold();
                row.createCell(6).setCellValue(low ? "LOW" : "OK");
                row.getCell(6).setCellStyle(low ? warningStyle : okStyle);
            }

            for (int i = 0; i < colCount; i++) statusSheet.autoSizeColumn(i);

            // ---- Sheet 2: Stock Movement ----
            XSSFSheet mvSheet = workbook.createSheet("Stock Movement");
            rowIdx = 0;
            int mvColCount = 10;

            XSSFRow tr2 = mvSheet.createRow(rowIdx++);
            tr2.createCell(0).setCellValue(companyName);
            tr2.getCell(0).setCellStyle(titleStyle);
            mvSheet.addMergedRegion(new CellRangeAddress(0, 0, 0, mvColCount - 1));

            XSSFRow ar2 = mvSheet.createRow(rowIdx++);
            ar2.createCell(0).setCellValue(companyAddress + " | " + companyPhone);
            ar2.getCell(0).setCellStyle(subStyle);
            mvSheet.addMergedRegion(new CellRangeAddress(1, 1, 0, mvColCount - 1));

            rowIdx++;

            XSSFRow sr2 = mvSheet.createRow(rowIdx++);
            sr2.createCell(0).setCellValue("Stock Movement Summary");
            sr2.getCell(0).setCellStyle(sectionStyle);
            mvSheet.addMergedRegion(new CellRangeAddress(rowIdx - 1, rowIdx - 1, 0, mvColCount - 1));

            XSSFRow pr2 = mvSheet.createRow(rowIdx++);
            pr2.createCell(0).setCellValue(period);
            pr2.getCell(0).setCellStyle(subStyle);
            mvSheet.addMergedRegion(new CellRangeAddress(rowIdx - 1, rowIdx - 1, 0, mvColCount - 1));

            rowIdx++;

            String[] mvHeaders = {"#", "Tank", "Product", "Open Dip", "Opening (L)", "Income (L)", "Sales (L)", "Closing (L)", "Close Dip", "Avg Daily Sales"};
            XSSFRow mhr = mvSheet.createRow(rowIdx++);
            for (int i = 0; i < mvHeaders.length; i++) {
                mhr.createCell(i).setCellValue(mvHeaders[i]);
                mhr.getCell(i).setCellStyle(headerStyle);
            }

            num = 0;
            for (TankMovement mv : data.movements) {
                if (mv.days == 0) continue;
                num++;
                double avgDaily = mv.days > 0 ? mv.totalSales / mv.days : 0;

                XSSFRow row = mvSheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(num);
                row.getCell(0).setCellStyle(cellStyle);
                row.createCell(1).setCellValue(mv.tank.getName());
                row.getCell(1).setCellStyle(boldCellStyle);
                row.createCell(2).setCellValue(mv.tank.getProduct() != null ? mv.tank.getProduct().getName() : "-");
                row.getCell(2).setCellStyle(cellStyle);
                row.createCell(3).setCellValue(mv.openDip != null ? mv.openDip : "-");
                row.getCell(3).setCellStyle(cellStyle);

                XSSFCell c4 = row.createCell(4);
                c4.setCellValue(mv.openingStock);
                c4.setCellStyle(numStyle);

                XSSFCell c5 = row.createCell(5);
                c5.setCellValue(mv.totalIncome);
                c5.setCellStyle(numStyle);

                XSSFCell c6 = row.createCell(6);
                c6.setCellValue(mv.totalSales);
                c6.setCellStyle(boldNumStyle);

                XSSFCell c7 = row.createCell(7);
                c7.setCellValue(mv.closingStock);
                c7.setCellStyle(numStyle);

                row.createCell(8).setCellValue(mv.closeDip != null ? mv.closeDip : "-");
                row.getCell(8).setCellStyle(cellStyle);

                XSSFCell c9 = row.createCell(9);
                c9.setCellValue(avgDaily);
                c9.setCellStyle(numStyle);
            }

            for (int i = 0; i < mvColCount; i++) mvSheet.autoSizeColumn(i);

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new ReportGenerationException("Failed to generate Tank Inventory Excel report", e);
        }
    }

    // ======================== Helpers ========================

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

    private String formatNum(Double value) {
        if (value == null) return "0.00";
        return NUM_FMT.format(value);
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

    private XSSFCellStyle createNumStyle(XSSFWorkbook wb, boolean bold) {
        XSSFCellStyle style = createBorderedStyle(wb, bold);
        style.setAlignment(HorizontalAlignment.RIGHT);
        style.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
        return style;
    }

    // ---- Data classes ----

    private static class TankMovement {
        final Tank tank;
        double openingStock;
        double closingStock;
        double totalIncome;
        double totalSales;
        String openDip;
        String closeDip;
        LocalDate firstDate;
        LocalDate lastDate;
        int days;

        TankMovement(Tank tank) {
            this.tank = tank;
        }
    }

    private record ReportData(List<TankMovement> movements) {}
}
