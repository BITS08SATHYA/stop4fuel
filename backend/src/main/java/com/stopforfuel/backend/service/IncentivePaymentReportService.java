package com.stopforfuel.backend.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.stopforfuel.backend.service.pdf.PageFooterEvent;
import com.stopforfuel.backend.entity.Company;
import com.stopforfuel.backend.entity.IncentivePayment;
import com.stopforfuel.backend.exception.ReportGenerationException;
import com.stopforfuel.backend.repository.CompanyRepository;
import com.stopforfuel.backend.repository.IncentivePaymentRepository;
import com.stopforfuel.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Incentive Payment Report — incentive payouts aggregated by day over a chosen
 * [fromDate, toDate] window. One row per calendar date with that day's total
 * incentive amount, sorted by date ascending, with a grand-total row. Uses the
 * shared Auditor-tab professional template (centred company header, orange
 * section accent, bordered table, totals strip).
 */
@Service
@RequiredArgsConstructor
public class IncentivePaymentReportService {

    private final CompanyRepository companyRepository;
    private final IncentivePaymentRepository incentivePaymentRepository;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy, hh:mm a");
    private static final java.text.DecimalFormat NUM_FMT = new java.text.DecimalFormat("#,##0.00");

    private record DayRow(LocalDate date, BigDecimal amount) {}

    public byte[] generatePdf(LocalDate fromDate, LocalDate toDate) {
        return renderPdf("Incentive Payment Report",
                "Day-wise incentive payouts",
                fromDate, toDate, buildRows(fromDate, toDate));
    }

    public byte[] generateExcel(LocalDate fromDate, LocalDate toDate) {
        return renderExcel("Incentive Payment Report", "Incentive Payments",
                fromDate, toDate, buildRows(fromDate, toDate));
    }

    private List<DayRow> buildRows(LocalDate fromDate, LocalDate toDate) {
        Long scid = SecurityUtils.getScid();
        LocalDateTime fromDateTime = fromDate.atStartOfDay();
        LocalDateTime toDateTime = toDate.atTime(LocalTime.MAX);

        Map<LocalDate, BigDecimal> byDay = new TreeMap<>();
        for (IncentivePayment ip : incentivePaymentRepository.findByDateRange(scid, fromDateTime, toDateTime)) {
            if (ip.getPaymentDate() == null) continue;
            LocalDate day = ip.getPaymentDate().toLocalDate();
            BigDecimal amt = ip.getAmount() != null ? ip.getAmount() : BigDecimal.ZERO;
            byDay.merge(day, amt, BigDecimal::add);
        }

        List<DayRow> rows = new ArrayList<>();
        for (Map.Entry<LocalDate, BigDecimal> e : byDay.entrySet()) {
            rows.add(new DayRow(e.getKey(), e.getValue()));
        }
        return rows;
    }

    // ─── PDF rendering ────────────────────────────────────────────

    private byte[] renderPdf(String title, String subtitle, LocalDate fromDate, LocalDate toDate, List<DayRow> rows) {
        try {
            Company companyEntity = firstCompany();
            String companyName = companyEntity != null && companyEntity.getName() != null ? companyEntity.getName() : "StopForFuel";
            String companyMeta = (companyEntity != null && companyEntity.getAddress() != null ? companyEntity.getAddress() : "")
                    + " | GSTIN: " + (companyEntity != null && companyEntity.getGstNo() != null ? companyEntity.getGstNo() : "")
                    + " | " + (companyEntity != null && companyEntity.getPhone() != null ? companyEntity.getPhone() : "");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, 24, 24, 28, 32);
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(new PageFooterEvent());
            doc.open();

            Font titleFont = new Font(Font.HELVETICA, 14, Font.BOLD, new Color(51, 51, 51));
            Font subFont = new Font(Font.HELVETICA, 8, Font.NORMAL, new Color(102, 102, 102));
            Font sectionFont = new Font(Font.HELVETICA, 11, Font.BOLD, new Color(230, 81, 0));
            Font headerFont = new Font(Font.HELVETICA, 8, Font.BOLD, Color.WHITE);
            Font cellFont = new Font(Font.HELVETICA, 8, Font.NORMAL, Color.BLACK);
            Font totalFont = new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE);
            Font footerFont = new Font(Font.HELVETICA, 6, Font.NORMAL, new Color(153, 153, 153));

            Paragraph head = new Paragraph(companyName, titleFont);
            head.setAlignment(Element.ALIGN_CENTER);
            doc.add(head);

            Paragraph meta = new Paragraph(companyMeta, subFont);
            meta.setAlignment(Element.ALIGN_CENTER);
            doc.add(meta);

            Paragraph t = new Paragraph(title, sectionFont);
            t.setAlignment(Element.ALIGN_CENTER);
            t.setSpacingBefore(4);
            doc.add(t);

            String periodLabel = subtitle + "  |  Period: " + fromDate.format(DATE_FMT) + " to " + toDate.format(DATE_FMT)
                    + "  |  Generated " + LocalDateTime.now().format(DATETIME_FMT);
            Paragraph sub = new Paragraph(periodLabel, subFont);
            sub.setAlignment(Element.ALIGN_CENTER);
            doc.add(sub);

            doc.add(new Paragraph(" ", subFont));

            if (rows.isEmpty()) {
                Paragraph empty = new Paragraph("No incentive payments in this period.", subFont);
                empty.setAlignment(Element.ALIGN_CENTER);
                doc.add(empty);
            } else {
                float[] widths = {12f, 48f, 40f};
                PdfPTable table = new PdfPTable(widths);
                table.setWidthPercentage(100);
                Color headerBg = new Color(230, 81, 0);
                Color border = new Color(221, 221, 221);

                table.addCell(headerCell("S.No", headerFont, headerBg, Element.ALIGN_CENTER));
                table.addCell(headerCell("Date", headerFont, headerBg, Element.ALIGN_LEFT));
                table.addCell(headerCell("Amount (₹)", headerFont, headerBg, Element.ALIGN_RIGHT));

                BigDecimal totAmount = BigDecimal.ZERO;
                int n = 0;
                for (DayRow r : rows) {
                    Color bg = (n++ % 2 == 0) ? Color.WHITE : new Color(250, 250, 250);
                    table.addCell(cell(String.valueOf(n), cellFont, bg, border, Element.ALIGN_CENTER));
                    table.addCell(cell(r.date().format(DATE_FMT), cellFont, bg, border, Element.ALIGN_LEFT));
                    table.addCell(cell(NUM_FMT.format(r.amount()), cellFont, bg, border, Element.ALIGN_RIGHT));
                    totAmount = totAmount.add(r.amount());
                }

                Color totBg = new Color(230, 81, 0);
                PdfPCell totalLabel = cell("TOTAL (" + rows.size() + " days)", totalFont, totBg, totBg, Element.ALIGN_LEFT);
                totalLabel.setColspan(2);
                table.addCell(totalLabel);
                table.addCell(cell(NUM_FMT.format(totAmount), totalFont, totBg, totBg, Element.ALIGN_RIGHT));

                doc.add(table);
            }

            Paragraph footer = new Paragraph("Report generated on " + LocalDateTime.now().format(DATETIME_FMT), footerFont);
            footer.setAlignment(Element.ALIGN_CENTER);
            footer.setSpacingBefore(12);
            doc.add(footer);

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new ReportGenerationException("Failed to generate " + title + " PDF", e);
        }
    }

    // ─── Excel rendering ──────────────────────────────────────────

    private byte[] renderExcel(String title, String sheetName, LocalDate fromDate, LocalDate toDate, List<DayRow> rows) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Company c = firstCompany();
            String companyName = c != null && c.getName() != null ? c.getName() : "StopForFuel";
            String companyMeta = (c != null && c.getAddress() != null ? c.getAddress() : "")
                    + " | GSTIN: " + (c != null && c.getGstNo() != null ? c.getGstNo() : "")
                    + " | " + (c != null && c.getPhone() != null ? c.getPhone() : "");

            XSSFCellStyle titleStyle = textStyle(wb, (short) 14, true, HorizontalAlignment.CENTER, null);
            XSSFCellStyle metaStyle = textStyle(wb, (short) 9, false, HorizontalAlignment.CENTER, null);
            XSSFCellStyle sectionStyle = textStyle(wb, (short) 11, true, HorizontalAlignment.CENTER, new byte[]{(byte) 230, (byte) 81, 0});
            XSSFCellStyle headerStyle = borderedStyle(wb, true, HorizontalAlignment.CENTER, new byte[]{(byte) 230, (byte) 81, 0});
            ((XSSFFont) headerStyle.getFont()).setColor(new XSSFColor(new byte[]{(byte) 255, (byte) 255, (byte) 255}, null));
            XSSFCellStyle textCell = borderedStyle(wb, false, HorizontalAlignment.LEFT, null);
            XSSFCellStyle numCell = borderedNumStyle(wb, false, null);
            XSSFCellStyle totalText = borderedStyle(wb, true, HorizontalAlignment.LEFT, new byte[]{(byte) 230, (byte) 81, 0});
            ((XSSFFont) totalText.getFont()).setColor(new XSSFColor(new byte[]{(byte) 255, (byte) 255, (byte) 255}, null));
            XSSFCellStyle totalNum = borderedNumStyle(wb, true, new byte[]{(byte) 230, (byte) 81, 0});
            ((XSSFFont) totalNum.getFont()).setColor(new XSSFColor(new byte[]{(byte) 255, (byte) 255, (byte) 255}, null));

            XSSFSheet sheet = wb.createSheet(sheetName);
            int r = 0;
            int cols = 3;

            XSSFRow headerRow = sheet.createRow(r++);
            headerRow.createCell(0).setCellValue(companyName);
            headerRow.getCell(0).setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, cols - 1));

            XSSFRow metaRow = sheet.createRow(r++);
            metaRow.createCell(0).setCellValue(companyMeta);
            metaRow.getCell(0).setCellStyle(metaStyle);
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, cols - 1));

            XSSFRow titleRow = sheet.createRow(r++);
            titleRow.createCell(0).setCellValue(title);
            titleRow.getCell(0).setCellStyle(sectionStyle);
            sheet.addMergedRegion(new CellRangeAddress(r - 1, r - 1, 0, cols - 1));

            XSSFRow periodRow = sheet.createRow(r++);
            periodRow.createCell(0).setCellValue("Period: " + fromDate.format(DATE_FMT) + " to " + toDate.format(DATE_FMT)
                    + "  |  Generated " + LocalDateTime.now().format(DATETIME_FMT));
            periodRow.getCell(0).setCellStyle(metaStyle);
            sheet.addMergedRegion(new CellRangeAddress(r - 1, r - 1, 0, cols - 1));

            r++; // blank row

            XSSFRow hdr = sheet.createRow(r++);
            String[] headers = {"S.No", "Date", "Amount"};
            for (int i = 0; i < headers.length; i++) {
                hdr.createCell(i).setCellValue(headers[i]);
                hdr.getCell(i).setCellStyle(headerStyle);
            }

            BigDecimal totAmount = BigDecimal.ZERO;
            int n = 0;
            for (DayRow row : rows) {
                XSSFRow xr = sheet.createRow(r++);
                xr.createCell(0).setCellValue(++n);
                xr.getCell(0).setCellStyle(textCell);
                xr.createCell(1).setCellValue(row.date().format(DATE_FMT));
                xr.getCell(1).setCellStyle(textCell);
                setNum(xr, 2, row.amount(), numCell);
                totAmount = totAmount.add(row.amount());
            }

            XSSFRow totalRow = sheet.createRow(r++);
            totalRow.createCell(0).setCellValue("");
            totalRow.getCell(0).setCellStyle(totalText);
            totalRow.createCell(1).setCellValue("TOTAL (" + rows.size() + " days)");
            totalRow.getCell(1).setCellStyle(totalText);
            setNum(totalRow, 2, totAmount, totalNum);

            for (int i = 0; i < cols; i++) sheet.autoSizeColumn(i);

            wb.write(out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new ReportGenerationException("Failed to generate " + title + " Excel", ex);
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────

    private Company firstCompany() {
        return companyRepository.findByScid(SecurityUtils.getScid()).stream().findFirst().orElse(null);
    }

    private static PdfPCell headerCell(String text, Font font, Color bg, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setBackgroundColor(bg);
        c.setBorderColor(bg);
        c.setHorizontalAlignment(align);
        c.setPadding(4);
        return c;
    }

    private static PdfPCell cell(String text, Font font, Color bg, Color border, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setBackgroundColor(bg);
        c.setBorderColor(border);
        c.setHorizontalAlignment(align);
        c.setPadding(3.5f);
        return c;
    }

    private static void setNum(XSSFRow row, int col, BigDecimal v, XSSFCellStyle style) {
        XSSFCell c = row.createCell(col);
        c.setCellValue(v != null ? v.doubleValue() : 0);
        c.setCellStyle(style);
    }

    private static XSSFCellStyle textStyle(XSSFWorkbook wb, short size, boolean bold, HorizontalAlignment align, byte[] rgb) {
        XSSFCellStyle s = wb.createCellStyle();
        XSSFFont f = wb.createFont();
        f.setFontHeightInPoints(size);
        f.setBold(bold);
        s.setFont(f);
        s.setAlignment(align);
        if (rgb != null) f.setColor(new XSSFColor(rgb, null));
        return s;
    }

    private static XSSFCellStyle borderedStyle(XSSFWorkbook wb, boolean bold, HorizontalAlignment align, byte[] bg) {
        XSSFCellStyle s = wb.createCellStyle();
        XSSFFont f = wb.createFont();
        f.setFontHeightInPoints((short) 10);
        f.setBold(bold);
        s.setFont(f);
        s.setAlignment(align);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
        if (bg != null) {
            s.setFillForegroundColor(new XSSFColor(bg, null));
            s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        return s;
    }

    private static XSSFCellStyle borderedNumStyle(XSSFWorkbook wb, boolean bold, byte[] bg) {
        XSSFCellStyle s = borderedStyle(wb, bold, HorizontalAlignment.RIGHT, bg);
        s.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
        return s;
    }
}
