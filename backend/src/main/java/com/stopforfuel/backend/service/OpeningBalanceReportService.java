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
import com.stopforfuel.backend.entity.Company;
import com.stopforfuel.backend.entity.Customer;
import com.stopforfuel.backend.exception.ReportGenerationException;
import com.stopforfuel.backend.repository.CompanyRepository;
import com.stopforfuel.backend.repository.CustomerRepository;
import com.stopforfuel.backend.repository.InvoiceBillRepository;
import com.stopforfuel.backend.repository.PaymentRepository;
import com.stopforfuel.backend.repository.StatementRepository;
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
import java.util.Comparator;
import java.util.List;

/**
 * Opening-balance ledger reports — for each credit customer, shows
 *   Opening | Bills | Payments | Closing
 * over a chosen [fromDate, toDate] window. Two flavours:
 *   - Local: drawn from individual credit InvoiceBill rows + Payments by customer
 *   - Statement: drawn from Statement rows + Payments by customer
 * Customers with all four numbers zero are skipped, results sorted by closing
 * balance descending, with a totals row.
 */
@Service
@RequiredArgsConstructor
public class OpeningBalanceReportService {

    private final CompanyRepository companyRepository;
    private final CustomerRepository customerRepository;
    private final InvoiceBillRepository invoiceBillRepository;
    private final StatementRepository statementRepository;
    private final PaymentRepository paymentRepository;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy, hh:mm a");
    private static final java.text.DecimalFormat NUM_FMT = new java.text.DecimalFormat("#,##0.00");

    private record LedgerRow(String customerName, String groupName, BigDecimal opening, BigDecimal bills,
                             BigDecimal payments, BigDecimal closing) {}

    public byte[] generateLocalPdf(LocalDate fromDate, LocalDate toDate) {
        return renderPdf("Local Opening Balance Report",
                "Ledger movement for local credit customers",
                fromDate, toDate, buildRows(fromDate, toDate, false));
    }

    public byte[] generateLocalExcel(LocalDate fromDate, LocalDate toDate) {
        return renderExcel("Local Opening Balance Report", "Local Opening Balance",
                fromDate, toDate, buildRows(fromDate, toDate, false));
    }

    public byte[] generateStatementPdf(LocalDate fromDate, LocalDate toDate) {
        return renderPdf("Statement Opening Balance Report",
                "Ledger movement for statement customers",
                fromDate, toDate, buildRows(fromDate, toDate, true));
    }

    public byte[] generateStatementExcel(LocalDate fromDate, LocalDate toDate) {
        return renderExcel("Statement Opening Balance Report", "Statement Opening Balance",
                fromDate, toDate, buildRows(fromDate, toDate, true));
    }

    private List<LedgerRow> buildRows(LocalDate fromDate, LocalDate toDate, boolean statementCustomers) {
        Long scid = SecurityUtils.getScid();
        LocalDateTime fromDateTime = fromDate.atStartOfDay();
        LocalDateTime toDateTime = toDate.atTime(LocalTime.MAX);

        List<LedgerRow> rows = new ArrayList<>();
        for (Customer c : customerRepository.findAllByScid(scid)) {
            // Skip cash-only customers (no credit limit set ever, never any credit history)
            if (c.getCreditLimitAmount() == null || c.getCreditLimitAmount().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            boolean isStatement = c.getParty() != null && "Statement".equalsIgnoreCase(c.getParty().getPartyType());
            if (statementCustomers != isStatement) continue;

            BigDecimal opening, bills, payments;
            if (isStatement) {
                BigDecimal billedBefore = nz(statementRepository.sumStatementsByCustomerBefore(c.getId(), fromDate));
                BigDecimal paidBefore = nz(paymentRepository.sumPaymentsByCustomerBefore(c.getId(), fromDateTime));
                opening = billedBefore.subtract(paidBefore);
                bills = nz(statementRepository.sumStatementsByCustomerInRange(c.getId(), fromDate, toDate));
                payments = nz(paymentRepository.sumPaymentsByCustomerInRange(c.getId(), fromDateTime, toDateTime));
            } else {
                BigDecimal billedBefore = nz(invoiceBillRepository.sumCreditBillsByCustomerBefore(c.getId(), fromDateTime));
                BigDecimal paidBefore = nz(paymentRepository.sumPaymentsByCustomerBefore(c.getId(), fromDateTime));
                opening = billedBefore.subtract(paidBefore);
                bills = nz(invoiceBillRepository.sumCreditBillsByCustomerInRange(c.getId(), fromDateTime, toDateTime));
                payments = nz(paymentRepository.sumPaymentsByCustomerInRange(c.getId(), fromDateTime, toDateTime));
            }
            BigDecimal closing = opening.add(bills).subtract(payments);

            // Skip rows where nothing happened: zero opening AND no activity AND zero closing.
            if (opening.signum() == 0 && bills.signum() == 0 && payments.signum() == 0 && closing.signum() == 0) {
                continue;
            }

            rows.add(new LedgerRow(
                    c.getName() != null ? c.getName() : "-",
                    c.getGroup() != null ? c.getGroup().getGroupName() : "",
                    opening, bills, payments, closing));
        }

        rows.sort(Comparator.comparing(LedgerRow::closing).reversed());
        return rows;
    }

    // ─── PDF rendering ────────────────────────────────────────────

    private byte[] renderPdf(String title, String subtitle, LocalDate fromDate, LocalDate toDate, List<LedgerRow> rows) {
        try {
            Company companyEntity = firstCompany();
            String companyName = companyEntity != null && companyEntity.getName() != null ? companyEntity.getName() : "StopForFuel";
            String companyMeta = (companyEntity != null && companyEntity.getAddress() != null ? companyEntity.getAddress() : "")
                    + " | GSTIN: " + (companyEntity != null && companyEntity.getGstNo() != null ? companyEntity.getGstNo() : "")
                    + " | " + (companyEntity != null && companyEntity.getPhone() != null ? companyEntity.getPhone() : "");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, 24, 24, 28, 32);
            PdfWriter.getInstance(doc, out);
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
                Paragraph empty = new Paragraph("No customer activity in this period.", subFont);
                empty.setAlignment(Element.ALIGN_CENTER);
                doc.add(empty);
            } else {
                float[] widths = {6f, 32f, 16f, 16f, 16f, 16f};
                PdfPTable table = new PdfPTable(widths);
                table.setWidthPercentage(100);
                Color headerBg = new Color(230, 81, 0);
                Color border = new Color(221, 221, 221);

                table.addCell(headerCell("#", headerFont, headerBg, Element.ALIGN_CENTER));
                table.addCell(headerCell("Customer", headerFont, headerBg, Element.ALIGN_LEFT));
                table.addCell(headerCell("Opening (₹)", headerFont, headerBg, Element.ALIGN_RIGHT));
                table.addCell(headerCell("Bills (₹)", headerFont, headerBg, Element.ALIGN_RIGHT));
                table.addCell(headerCell("Payments (₹)", headerFont, headerBg, Element.ALIGN_RIGHT));
                table.addCell(headerCell("Closing (₹)", headerFont, headerBg, Element.ALIGN_RIGHT));

                BigDecimal totOpening = BigDecimal.ZERO, totBills = BigDecimal.ZERO,
                        totPayments = BigDecimal.ZERO, totClosing = BigDecimal.ZERO;
                int n = 0;
                for (LedgerRow r : rows) {
                    Color bg = (n++ % 2 == 0) ? Color.WHITE : new Color(250, 250, 250);
                    table.addCell(cell(String.valueOf(n), cellFont, bg, border, Element.ALIGN_CENTER));
                    String customerLabel = r.customerName() + (r.groupName() != null && !r.groupName().isEmpty()
                            ? "  (" + r.groupName() + ")" : "");
                    table.addCell(cell(customerLabel, cellFont, bg, border, Element.ALIGN_LEFT));
                    table.addCell(cell(NUM_FMT.format(r.opening()), cellFont, bg, border, Element.ALIGN_RIGHT));
                    table.addCell(cell(NUM_FMT.format(r.bills()), cellFont, bg, border, Element.ALIGN_RIGHT));
                    table.addCell(cell(NUM_FMT.format(r.payments()), cellFont, bg, border, Element.ALIGN_RIGHT));
                    table.addCell(cell(NUM_FMT.format(r.closing()), cellFont, bg, border, Element.ALIGN_RIGHT));
                    totOpening = totOpening.add(r.opening());
                    totBills = totBills.add(r.bills());
                    totPayments = totPayments.add(r.payments());
                    totClosing = totClosing.add(r.closing());
                }

                Color totBg = new Color(230, 81, 0);
                PdfPCell totalLabel = cell("TOTAL (" + rows.size() + " customers)", totalFont, totBg, totBg, Element.ALIGN_LEFT);
                totalLabel.setColspan(2);
                table.addCell(totalLabel);
                table.addCell(cell(NUM_FMT.format(totOpening), totalFont, totBg, totBg, Element.ALIGN_RIGHT));
                table.addCell(cell(NUM_FMT.format(totBills), totalFont, totBg, totBg, Element.ALIGN_RIGHT));
                table.addCell(cell(NUM_FMT.format(totPayments), totalFont, totBg, totBg, Element.ALIGN_RIGHT));
                table.addCell(cell(NUM_FMT.format(totClosing), totalFont, totBg, totBg, Element.ALIGN_RIGHT));

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

    private byte[] renderExcel(String title, String sheetName, LocalDate fromDate, LocalDate toDate, List<LedgerRow> rows) {
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
            int cols = 6;

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
            String[] headers = {"#", "Customer", "Opening", "Bills", "Payments", "Closing"};
            for (int i = 0; i < headers.length; i++) {
                hdr.createCell(i).setCellValue(headers[i]);
                hdr.getCell(i).setCellStyle(headerStyle);
            }

            BigDecimal totOpening = BigDecimal.ZERO, totBills = BigDecimal.ZERO,
                    totPayments = BigDecimal.ZERO, totClosing = BigDecimal.ZERO;
            int n = 0;
            for (LedgerRow row : rows) {
                XSSFRow xr = sheet.createRow(r++);
                xr.createCell(0).setCellValue(++n);
                xr.getCell(0).setCellStyle(textCell);
                String customerLabel = row.customerName() + (row.groupName() != null && !row.groupName().isEmpty()
                        ? "  (" + row.groupName() + ")" : "");
                xr.createCell(1).setCellValue(customerLabel);
                xr.getCell(1).setCellStyle(textCell);
                setNum(xr, 2, row.opening(), numCell);
                setNum(xr, 3, row.bills(), numCell);
                setNum(xr, 4, row.payments(), numCell);
                setNum(xr, 5, row.closing(), numCell);
                totOpening = totOpening.add(row.opening());
                totBills = totBills.add(row.bills());
                totPayments = totPayments.add(row.payments());
                totClosing = totClosing.add(row.closing());
            }

            XSSFRow totalRow = sheet.createRow(r++);
            totalRow.createCell(0).setCellValue("");
            totalRow.getCell(0).setCellStyle(totalText);
            totalRow.createCell(1).setCellValue("TOTAL (" + rows.size() + " customers)");
            totalRow.getCell(1).setCellStyle(totalText);
            setNum(totalRow, 2, totOpening, totalNum);
            setNum(totalRow, 3, totBills, totalNum);
            setNum(totalRow, 4, totPayments, totalNum);
            setNum(totalRow, 5, totClosing, totalNum);

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

    private static BigDecimal nz(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }

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
