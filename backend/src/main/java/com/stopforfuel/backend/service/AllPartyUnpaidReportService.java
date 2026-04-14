package com.stopforfuel.backend.service;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.MultiColumnText;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
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
import com.stopforfuel.backend.entity.Company;
import com.stopforfuel.backend.entity.Customer;
import com.stopforfuel.backend.entity.InvoiceBill;
import com.stopforfuel.backend.entity.Statement;
import com.stopforfuel.backend.exception.ReportGenerationException;
import com.stopforfuel.backend.repository.CompanyRepository;
import com.stopforfuel.backend.repository.InvoiceBillRepository;
import com.stopforfuel.backend.repository.PaymentRepository;
import com.stopforfuel.backend.repository.StatementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AllPartyUnpaidReportService {

    private final CompanyRepository companyRepository;
    private final StatementRepository statementRepository;
    private final InvoiceBillRepository invoiceBillRepository;
    private final PaymentRepository paymentRepository;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy, hh:mm a");
    private static final java.text.DecimalFormat NUM_FMT = new java.text.DecimalFormat("#,##0.00");

    private record Row(String id, String date, BigDecimal net, BigDecimal received, BigDecimal balance) {}

    public byte[] generateStatementPdf() {
        return render("All Party Statement Report", "Unpaid statements grouped by customer", buildStatementGroups());
    }

    public byte[] generateLocalPdf() {
        return render("All Party Local Report", "Unpaid local invoices grouped by customer", buildLocalGroups());
    }

    public byte[] generateStatementExcel() {
        return renderExcel("All Party Statement Report", "Statements", buildStatementGroups());
    }

    public byte[] generateLocalExcel() {
        return renderExcel("All Party Local Report", "Local Invoices", buildLocalGroups());
    }

    private Map<Customer, List<Row>> buildStatementGroups() {
        Map<Customer, List<Row>> grouped = new LinkedHashMap<>();
        for (Statement s : statementRepository.findAllUnpaidWithCustomer()) {
            BigDecimal net = nz(s.getNetAmount());
            BigDecimal recd = nz(s.getReceivedAmount());
            BigDecimal bal = s.getBalanceAmount() != null ? s.getBalanceAmount() : net.subtract(recd);
            if (bal.compareTo(BigDecimal.ZERO) <= 0) continue;
            grouped.computeIfAbsent(s.getCustomer(), k -> new java.util.ArrayList<>())
                    .add(new Row(
                            s.getStatementNo() != null ? s.getStatementNo() : "-",
                            s.getStatementDate() != null ? s.getStatementDate().format(DATE_FMT) : "-",
                            net, recd, bal));
        }
        return grouped;
    }

    private Map<Customer, List<Row>> buildLocalGroups() {
        Map<Customer, List<Row>> grouped = new LinkedHashMap<>();
        for (InvoiceBill ib : invoiceBillRepository.findAllUnpaidLocalCreditWithCustomer()) {
            BigDecimal net = nz(ib.getNetAmount());
            BigDecimal recd = nz(paymentRepository.sumPaymentsByInvoiceBillId(ib.getId()));
            BigDecimal bal = net.subtract(recd);
            if (bal.compareTo(BigDecimal.ZERO) <= 0) continue;
            grouped.computeIfAbsent(ib.getCustomer(), k -> new java.util.ArrayList<>())
                    .add(new Row(
                            ib.getBillNo() != null ? ib.getBillNo() : "-",
                            ib.getDate() != null ? ib.getDate().format(DATE_FMT) : "-",
                            net, recd, bal));
        }
        return grouped;
    }

    private byte[] renderExcel(String title, String sheetName, Map<Customer, List<Row>> grouped) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Company c = companyRepository.findAll().stream().findFirst().orElse(null);
            String companyName = c != null && c.getName() != null ? c.getName() : "StopForFuel";
            String companyMeta = (c != null && c.getAddress() != null ? c.getAddress() : "")
                    + " | GSTIN: " + (c != null && c.getGstNo() != null ? c.getGstNo() : "")
                    + " | " + (c != null && c.getPhone() != null ? c.getPhone() : "");

            XSSFCellStyle titleStyle = textStyle(wb, (short) 14, true, HorizontalAlignment.CENTER, null);
            XSSFCellStyle metaStyle = textStyle(wb, (short) 9, false, HorizontalAlignment.CENTER, null);
            XSSFCellStyle sectionStyle = textStyle(wb, (short) 11, true, HorizontalAlignment.CENTER, new byte[]{(byte) 230, (byte) 81, 0});
            XSSFCellStyle custStyle = borderedStyle(wb, true, HorizontalAlignment.LEFT, new byte[]{(byte) 255, (byte) 243, (byte) 224});
            XSSFCellStyle headerStyle = borderedStyle(wb, true, HorizontalAlignment.CENTER, new byte[]{(byte) 230, (byte) 81, 0});
            ((XSSFFont) headerStyle.getFont()).setColor(new XSSFColor(new byte[]{(byte) 255, (byte) 255, (byte) 255}, null));
            XSSFCellStyle cellStyle = borderedStyle(wb, false, HorizontalAlignment.LEFT, null);
            XSSFCellStyle numStyle = borderedNumStyle(wb, false, null);
            XSSFCellStyle subTextStyle = borderedStyle(wb, true, HorizontalAlignment.LEFT, new byte[]{(byte) 255, (byte) 243, (byte) 224});
            XSSFCellStyle subNumStyle = borderedNumStyle(wb, true, new byte[]{(byte) 255, (byte) 243, (byte) 224});
            XSSFCellStyle totalText = borderedStyle(wb, true, HorizontalAlignment.LEFT, new byte[]{(byte) 230, (byte) 81, 0});
            ((XSSFFont) totalText.getFont()).setColor(new XSSFColor(new byte[]{(byte) 255, (byte) 255, (byte) 255}, null));
            XSSFCellStyle totalNum = borderedNumStyle(wb, true, new byte[]{(byte) 230, (byte) 81, 0});
            ((XSSFFont) totalNum.getFont()).setColor(new XSSFColor(new byte[]{(byte) 255, (byte) 255, (byte) 255}, null));

            XSSFSheet sheet = wb.createSheet(sheetName);
            int r = 0;
            int cols = 6;

            XSSFRow tr = sheet.createRow(r++);
            tr.createCell(0).setCellValue(companyName);
            tr.getCell(0).setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, cols - 1));

            XSSFRow mr = sheet.createRow(r++);
            mr.createCell(0).setCellValue(companyMeta);
            mr.getCell(0).setCellStyle(metaStyle);
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, cols - 1));

            XSSFRow sr = sheet.createRow(r++);
            sr.createCell(0).setCellValue(title);
            sr.getCell(0).setCellStyle(sectionStyle);
            sheet.addMergedRegion(new CellRangeAddress(r - 1, r - 1, 0, cols - 1));

            XSSFRow asOfRow = sheet.createRow(r++);
            asOfRow.createCell(0).setCellValue("As of " + LocalDateTime.now().format(DATETIME_FMT));
            asOfRow.getCell(0).setCellStyle(metaStyle);
            sheet.addMergedRegion(new CellRangeAddress(r - 1, r - 1, 0, cols - 1));

            r++; // blank

            // Headers: Customer / ID / Date / Net / Received / Balance
            XSSFRow hr = sheet.createRow(r++);
            String[] headers = {"Customer", "ID", "Date", "Net", "Received", "Balance"};
            for (int i = 0; i < headers.length; i++) {
                hr.createCell(i).setCellValue(headers[i]);
                hr.getCell(i).setCellStyle(headerStyle);
            }

            BigDecimal grandNet = BigDecimal.ZERO, grandRecd = BigDecimal.ZERO, grandBal = BigDecimal.ZERO;
            int custCount = 0, rowCount = 0;

            for (Map.Entry<Customer, List<Row>> e : grouped.entrySet()) {
                custCount++;
                String custName = (e.getKey() != null && e.getKey().getName() != null) ? e.getKey().getName() : "-";
                BigDecimal subNet = BigDecimal.ZERO, subRecd = BigDecimal.ZERO, subBal = BigDecimal.ZERO;
                boolean first = true;
                for (Row row : e.getValue()) {
                    XSSFRow xr = sheet.createRow(r++);
                    xr.createCell(0).setCellValue(first ? custName : "");
                    xr.getCell(0).setCellStyle(first ? custStyle : cellStyle);
                    xr.createCell(1).setCellValue(row.id());
                    xr.getCell(1).setCellStyle(cellStyle);
                    xr.createCell(2).setCellValue(row.date());
                    xr.getCell(2).setCellStyle(cellStyle);
                    setNum(xr, 3, row.net(), numStyle);
                    setNum(xr, 4, row.received(), numStyle);
                    setNum(xr, 5, row.balance(), numStyle);
                    subNet = subNet.add(row.net());
                    subRecd = subRecd.add(row.received());
                    subBal = subBal.add(row.balance());
                    first = false;
                    rowCount++;
                }
                XSSFRow subR = sheet.createRow(r++);
                subR.createCell(0).setCellValue("Subtotal — " + custName);
                subR.getCell(0).setCellStyle(subTextStyle);
                subR.createCell(1).setCellValue("");
                subR.getCell(1).setCellStyle(subTextStyle);
                subR.createCell(2).setCellValue("");
                subR.getCell(2).setCellStyle(subTextStyle);
                setNum(subR, 3, subNet, subNumStyle);
                setNum(subR, 4, subRecd, subNumStyle);
                setNum(subR, 5, subBal, subNumStyle);
                grandNet = grandNet.add(subNet);
                grandRecd = grandRecd.add(subRecd);
                grandBal = grandBal.add(subBal);
            }

            XSSFRow gr = sheet.createRow(r++);
            gr.createCell(0).setCellValue("GRAND TOTAL (" + custCount + " customers, " + rowCount + " items)");
            gr.getCell(0).setCellStyle(totalText);
            gr.createCell(1).setCellValue("");
            gr.getCell(1).setCellStyle(totalText);
            gr.createCell(2).setCellValue("");
            gr.getCell(2).setCellStyle(totalText);
            setNum(gr, 3, grandNet, totalNum);
            setNum(gr, 4, grandRecd, totalNum);
            setNum(gr, 5, grandBal, totalNum);

            for (int i = 0; i < cols; i++) sheet.autoSizeColumn(i);

            wb.write(out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new ReportGenerationException("Failed to generate " + title + " Excel", ex);
        }
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
        if (rgb != null) {
            f.setColor(new XSSFColor(rgb, null));
        }
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

    private byte[] render(String title, String subtitle, Map<Customer, List<Row>> grouped) {
        try {
            Company companyEntity = companyRepository.findAll().stream().findFirst().orElse(null);
            String companyName = companyEntity != null && companyEntity.getName() != null ? companyEntity.getName() : "StopForFuel";
            String companyAddress = companyEntity != null && companyEntity.getAddress() != null ? companyEntity.getAddress() : "";
            String companyPhone = companyEntity != null && companyEntity.getPhone() != null ? companyEntity.getPhone() : "";
            String companyGstin = companyEntity != null && companyEntity.getGstNo() != null ? companyEntity.getGstNo() : "";

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, 24, 24, 28, 32);
            PdfWriter.getInstance(doc, out);
            doc.open();

            Font titleFont = new Font(Font.HELVETICA, 14, Font.BOLD, new Color(51, 51, 51));
            Font subFont = new Font(Font.HELVETICA, 8, Font.NORMAL, new Color(102, 102, 102));
            Font sectionFont = new Font(Font.HELVETICA, 11, Font.BOLD, new Color(230, 81, 0));
            Font custFont = new Font(Font.HELVETICA, 8, Font.BOLD, new Color(33, 33, 33));
            Font headerFont = new Font(Font.HELVETICA, 6, Font.BOLD, Color.WHITE);
            Font cellFont = new Font(Font.HELVETICA, 7, Font.NORMAL, Color.BLACK);
            Font subtotalFont = new Font(Font.HELVETICA, 7, Font.BOLD, new Color(230, 81, 0));
            Font footerFont = new Font(Font.HELVETICA, 6, Font.NORMAL, new Color(153, 153, 153));

            // Header (single-column, above the two-column flow)
            Paragraph company = new Paragraph(companyName, titleFont);
            company.setAlignment(Element.ALIGN_CENTER);
            doc.add(company);

            Paragraph address = new Paragraph(companyAddress + " | GSTIN: " + companyGstin + " | " + companyPhone, subFont);
            address.setAlignment(Element.ALIGN_CENTER);
            doc.add(address);

            Paragraph t = new Paragraph(title, sectionFont);
            t.setAlignment(Element.ALIGN_CENTER);
            t.setSpacingBefore(4);
            doc.add(t);

            Paragraph sub = new Paragraph(subtitle + "  |  As of " + LocalDateTime.now().format(DATETIME_FMT), subFont);
            sub.setAlignment(Element.ALIGN_CENTER);
            doc.add(sub);

            doc.add(new Paragraph(" ", subFont));

            // Totals
            BigDecimal grandNet = BigDecimal.ZERO, grandRecd = BigDecimal.ZERO, grandBal = BigDecimal.ZERO;
            int custCount = 0, rowCount = 0;

            if (grouped.isEmpty()) {
                Paragraph empty = new Paragraph("No unpaid records.", subFont);
                empty.setAlignment(Element.ALIGN_CENTER);
                doc.add(empty);
            } else {
                // Two-column flow
                MultiColumnText mct = new MultiColumnText();
                mct.addRegularColumns(doc.left(), doc.right(), 14f, 2);

                for (Map.Entry<Customer, List<Row>> e : grouped.entrySet()) {
                    Customer cust = e.getKey();
                    List<Row> rows = e.getValue();
                    custCount++;

                    String custName = (cust != null && cust.getName() != null) ? cust.getName() : "-";
                    Paragraph head = new Paragraph(custName, custFont);
                    head.setSpacingBefore(4);
                    head.setSpacingAfter(1);
                    mct.addElement(head);

                    float[] widths = {22f, 18f, 20f, 20f, 20f};
                    PdfPTable tbl = new PdfPTable(widths);
                    tbl.setWidthPercentage(100);

                    Color headerBg = new Color(230, 81, 0);
                    Color border = new Color(221, 221, 221);
                    tbl.addCell(headerCell("ID", headerFont, headerBg, Element.ALIGN_LEFT));
                    tbl.addCell(headerCell("Date", headerFont, headerBg, Element.ALIGN_LEFT));
                    tbl.addCell(headerCell("Net", headerFont, headerBg, Element.ALIGN_RIGHT));
                    tbl.addCell(headerCell("Received", headerFont, headerBg, Element.ALIGN_RIGHT));
                    tbl.addCell(headerCell("Balance", headerFont, headerBg, Element.ALIGN_RIGHT));

                    BigDecimal subNet = BigDecimal.ZERO, subRecd = BigDecimal.ZERO, subBal = BigDecimal.ZERO;
                    int idx = 0;
                    for (Row r : rows) {
                        Color bg = (idx++ % 2 == 0) ? Color.WHITE : new Color(250, 250, 250);
                        tbl.addCell(cell(r.id(), cellFont, bg, border, Element.ALIGN_LEFT));
                        tbl.addCell(cell(r.date(), cellFont, bg, border, Element.ALIGN_LEFT));
                        tbl.addCell(cell(NUM_FMT.format(r.net()), cellFont, bg, border, Element.ALIGN_RIGHT));
                        tbl.addCell(cell(NUM_FMT.format(r.received()), cellFont, bg, border, Element.ALIGN_RIGHT));
                        tbl.addCell(cell(NUM_FMT.format(r.balance()), cellFont, bg, border, Element.ALIGN_RIGHT));
                        subNet = subNet.add(r.net());
                        subRecd = subRecd.add(r.received());
                        subBal = subBal.add(r.balance());
                        rowCount++;
                    }

                    Color subBg = new Color(255, 243, 224);
                    PdfPCell subLabel = cell("Subtotal", subtotalFont, subBg, border, Element.ALIGN_LEFT);
                    subLabel.setColspan(2);
                    tbl.addCell(subLabel);
                    tbl.addCell(cell(NUM_FMT.format(subNet), subtotalFont, subBg, border, Element.ALIGN_RIGHT));
                    tbl.addCell(cell(NUM_FMT.format(subRecd), subtotalFont, subBg, border, Element.ALIGN_RIGHT));
                    tbl.addCell(cell(NUM_FMT.format(subBal), subtotalFont, subBg, border, Element.ALIGN_RIGHT));

                    grandNet = grandNet.add(subNet);
                    grandRecd = grandRecd.add(subRecd);
                    grandBal = grandBal.add(subBal);

                    mct.addElement(tbl);
                    mct.addElement(new Paragraph(" ", new Font(Font.HELVETICA, 3)));
                }
                doc.add(mct);

                // Grand total (single column, full width)
                PdfPTable totals = new PdfPTable(new float[]{40f, 20f, 20f, 20f});
                totals.setWidthPercentage(100);
                totals.setSpacingBefore(10);
                Color totBg = new Color(230, 81, 0);
                Font totFont = new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE);
                totals.addCell(cell("GRAND TOTAL (" + custCount + " customers, " + rowCount + " items)", totFont, totBg, totBg, Element.ALIGN_LEFT));
                totals.addCell(cell(NUM_FMT.format(grandNet), totFont, totBg, totBg, Element.ALIGN_RIGHT));
                totals.addCell(cell(NUM_FMT.format(grandRecd), totFont, totBg, totBg, Element.ALIGN_RIGHT));
                totals.addCell(cell(NUM_FMT.format(grandBal), totFont, totBg, totBg, Element.ALIGN_RIGHT));
                doc.add(totals);
            }

            doc.add(Chunk.NEWLINE);
            Paragraph footer = new Paragraph("Report generated on " + LocalDateTime.now().format(DATETIME_FMT), footerFont);
            footer.setAlignment(Element.ALIGN_CENTER);
            doc.add(footer);

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new ReportGenerationException("Failed to generate " + title + " PDF", e);
        }
    }

    private static PdfPCell headerCell(String text, Font font, Color bg, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setBackgroundColor(bg);
        c.setBorderColor(bg);
        c.setHorizontalAlignment(align);
        c.setPadding(3);
        return c;
    }

    private static PdfPCell cell(String text, Font font, Color bg, Color border, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setBackgroundColor(bg);
        c.setBorderColor(border);
        c.setHorizontalAlignment(align);
        c.setPadding(2.5f);
        return c;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
