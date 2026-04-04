package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Payment;
import com.stopforfuel.backend.exception.ReportGenerationException;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

@Service
public class PaymentReportService {

    @Value("${app.company.name:SATHYA FUELS}")
    private String companyName;

    @Value("${app.company.address:No. 45, GST Road, Tambaram, Chennai - 600045}")
    private String companyAddress;

    @Value("${app.company.phone:044-2234 5678}")
    private String companyPhone;

    @Value("${app.company.gstin:33AABCS1234F1Z5}")
    private String companyGstin;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy, hh:mm a");
    private static final java.text.DecimalFormat NUM_FMT = new java.text.DecimalFormat("#,##0.00");

    private JasperReport compiledReport;

    /**
     * Generate PDF report of payments using JasperReports.
     */
    public byte[] generatePdf(List<Payment> payments, LocalDate fromDate, LocalDate toDate) {
        try {
            JasperReport report = getCompiledReport();

            Map<String, Object> params = new HashMap<>();
            params.put("companyName", companyName);
            params.put("companyAddress", companyAddress);
            params.put("companyPhone", companyPhone);
            params.put("reportTitle", "Payment Report");
            params.put("dateRange", buildDateRange(fromDate, toDate));
            params.put("generatedDate", LocalDateTime.now().format(DATETIME_FMT));
            params.put("totalPayments", payments.size());

            BigDecimal totalAmount = payments.stream()
                    .map(Payment::getAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            params.put("totalAmount", "₹" + NUM_FMT.format(totalAmount));

            List<Map<String, Object>> rows = buildRows(payments);
            JRBeanCollectionDataSource ds = new JRBeanCollectionDataSource(rows);
            JasperPrint print = JasperFillManager.fillReport(report, params, ds);
            return JasperExportManager.exportReportToPdf(print);
        } catch (Exception e) {
            throw new ReportGenerationException("Failed to generate Payment PDF report", e);
        }
    }

    /**
     * Generate Excel report of payments using Apache POI.
     */
    public byte[] generateExcel(List<Payment> payments, LocalDate fromDate, LocalDate toDate) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Payment Report");

            // Header styles
            CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 11);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);

            CellStyle titleStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleStyle.setFont(titleFont);

            CellStyle amountStyle = workbook.createCellStyle();
            DataFormat format = workbook.createDataFormat();
            amountStyle.setDataFormat(format.getFormat("#,##0.00"));

            // Title rows
            Row titleRow = sheet.createRow(0);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue(companyName + " — Payment Report");
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 9));

            Row dateRow = sheet.createRow(1);
            dateRow.createCell(0).setCellValue("Period: " + buildDateRange(fromDate, toDate));

            BigDecimal totalAmount = payments.stream()
                    .map(Payment::getAmount).filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            Row summaryRow = sheet.createRow(2);
            summaryRow.createCell(0).setCellValue("Total: " + payments.size() + " payments | ₹" + NUM_FMT.format(totalAmount));

            // Column headers
            String[] headers = {"#", "Date", "Customer", "Paid Against", "Amount", "Mode", "Reference", "Employee", "Status", "Remarks"};
            Row headerRow = sheet.createRow(4);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Data rows
            List<Map<String, Object>> rows = buildRows(payments);
            int rowIdx = 5;
            for (Map<String, Object> row : rows) {
                Row dataRow = sheet.createRow(rowIdx++);
                dataRow.createCell(0).setCellValue((Integer) row.get("slNo"));
                dataRow.createCell(1).setCellValue((String) row.get("date"));
                dataRow.createCell(2).setCellValue((String) row.get("customerName"));
                dataRow.createCell(3).setCellValue((String) row.get("paidAgainst"));
                dataRow.createCell(4).setCellValue((String) row.get("amount"));
                dataRow.createCell(5).setCellValue((String) row.get("mode"));
                dataRow.createCell(6).setCellValue((String) row.get("reference"));
                dataRow.createCell(7).setCellValue((String) row.get("employeeName"));
                dataRow.createCell(8).setCellValue((String) row.get("status"));
                dataRow.createCell(9).setCellValue((String) row.get("remarks"));
            }

            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new ReportGenerationException("Failed to generate Payment Excel report", e);
        }
    }

    /**
     * Generate a single payment receipt PDF using OpenPDF.
     */
    public byte[] generateReceipt(Payment payment) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A5);
            PdfWriter.getInstance(doc, out);
            doc.open();

            Font titleFont = new Font(Font.HELVETICA, 14, Font.BOLD);
            Font labelFont = new Font(Font.HELVETICA, 9, Font.BOLD);
            Font valueFont = new Font(Font.HELVETICA, 9, Font.NORMAL);
            Font bigFont = new Font(Font.HELVETICA, 18, Font.BOLD);

            // Company header
            Paragraph company = new Paragraph(companyName, titleFont);
            company.setAlignment(Element.ALIGN_CENTER);
            doc.add(company);

            Paragraph addr = new Paragraph(companyAddress + " | " + companyPhone, new Font(Font.HELVETICA, 8));
            addr.setAlignment(Element.ALIGN_CENTER);
            doc.add(addr);

            doc.add(new Paragraph("\n"));

            Paragraph receiptTitle = new Paragraph("PAYMENT RECEIPT", new Font(Font.HELVETICA, 12, Font.BOLD));
            receiptTitle.setAlignment(Element.ALIGN_CENTER);
            doc.add(receiptTitle);

            doc.add(new Paragraph("\n"));

            // Receipt details table
            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(90);
            table.setWidths(new float[]{35, 65});

            addReceiptRow(table, "Receipt No:", "#" + payment.getId(), labelFont, valueFont);
            addReceiptRow(table, "Date:", payment.getPaymentDate() != null
                    ? payment.getPaymentDate().format(DATETIME_FMT) : "—", labelFont, valueFont);
            addReceiptRow(table, "Customer:", payment.getCustomer() != null
                    ? payment.getCustomer().getName() : "—", labelFont, valueFont);

            String paidAgainst = "—";
            if (payment.getStatement() != null) {
                paidAgainst = "Statement #" + payment.getStatement().getStatementNo();
            } else if (payment.getInvoiceBill() != null) {
                paidAgainst = "Bill #" + payment.getInvoiceBill().getId();
            }
            addReceiptRow(table, "Paid Against:", paidAgainst, labelFont, valueFont);
            addReceiptRow(table, "Mode:", payment.getPaymentMode() != null
                    ? payment.getPaymentMode().name() : "—", labelFont, valueFont);
            if (payment.getReferenceNo() != null && !payment.getReferenceNo().isBlank()) {
                addReceiptRow(table, "Reference:", payment.getReferenceNo(), labelFont, valueFont);
            }
            if (payment.getReceivedBy() != null) {
                addReceiptRow(table, "Received By:", payment.getReceivedBy().getName(), labelFont, valueFont);
            }
            if (payment.getRemarks() != null && !payment.getRemarks().isBlank()) {
                addReceiptRow(table, "Remarks:", payment.getRemarks(), labelFont, valueFont);
            }
            doc.add(table);

            doc.add(new Paragraph("\n"));

            // Amount highlight
            Paragraph amountLabel = new Paragraph("Amount Received", labelFont);
            amountLabel.setAlignment(Element.ALIGN_CENTER);
            doc.add(amountLabel);

            Paragraph amountValue = new Paragraph("₹" + NUM_FMT.format(payment.getAmount()), bigFont);
            amountValue.setAlignment(Element.ALIGN_CENTER);
            doc.add(amountValue);

            doc.add(new Paragraph("\n\n"));

            // Footer
            Paragraph footer = new Paragraph("This is a computer-generated receipt.", new Font(Font.HELVETICA, 7, Font.ITALIC));
            footer.setAlignment(Element.ALIGN_CENTER);
            doc.add(footer);

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new ReportGenerationException("Failed to generate payment receipt", e);
        }
    }

    private void addReceiptRow(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setPaddingBottom(4);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setPaddingBottom(4);
        table.addCell(valueCell);
    }

    private List<Map<String, Object>> buildRows(List<Payment> payments) {
        List<Map<String, Object>> rows = new ArrayList<>();
        int slNo = 0;
        for (Payment p : payments) {
            Map<String, Object> row = new HashMap<>();
            row.put("slNo", ++slNo);
            row.put("date", p.getPaymentDate() != null ? p.getPaymentDate().format(DATETIME_FMT) : "—");
            row.put("customerName", p.getCustomer() != null ? p.getCustomer().getName() : "—");

            String paidAgainst = "—";
            if (p.getStatement() != null) {
                paidAgainst = "Stmt #" + p.getStatement().getStatementNo();
            } else if (p.getInvoiceBill() != null) {
                paidAgainst = "Bill #" + p.getInvoiceBill().getId();
            }
            row.put("paidAgainst", paidAgainst);

            row.put("amount", p.getAmount() != null ? "₹" + NUM_FMT.format(p.getAmount()) : "—");
            row.put("mode", p.getPaymentMode() != null ? p.getPaymentMode().name() : "—");
            row.put("reference", p.getReferenceNo() != null ? p.getReferenceNo() : "—");
            row.put("employeeName", p.getReceivedBy() != null ? p.getReceivedBy().getName() : "—");
            row.put("status", p.getTargetPaymentStatus() != null ? p.getTargetPaymentStatus() : "—");
            row.put("remarks", p.getRemarks() != null ? p.getRemarks() : "—");
            rows.add(row);
        }
        return rows;
    }

    private String buildDateRange(LocalDate from, LocalDate to) {
        if (from != null && to != null) {
            return from.format(DATE_FMT) + " to " + to.format(DATE_FMT);
        } else if (from != null) {
            return "From " + from.format(DATE_FMT);
        } else if (to != null) {
            return "Up to " + to.format(DATE_FMT);
        }
        return "All dates";
    }

    private JasperReport getCompiledReport() throws Exception {
        if (compiledReport == null) {
            InputStream is = new ClassPathResource("reports/payment_report.jrxml").getInputStream();
            compiledReport = JasperCompileManager.compileReport(is);
        }
        return compiledReport;
    }
}
