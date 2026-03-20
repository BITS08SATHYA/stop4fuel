package com.stopforfuel.backend.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.stopforfuel.backend.entity.InvoiceBill;
import com.stopforfuel.backend.entity.InvoiceProduct;
import com.stopforfuel.backend.entity.Statement;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class StatementPdfGenerator {

    private static final Font TITLE_FONT = new Font(Font.HELVETICA, 16, Font.BOLD);
    private static final Font HEADER_FONT = new Font(Font.HELVETICA, 10, Font.BOLD);
    private static final Font NORMAL_FONT = new Font(Font.HELVETICA, 9, Font.NORMAL);
    private static final Font SMALL_FONT = new Font(Font.HELVETICA, 8, Font.NORMAL);
    private static final Font BOLD_FONT = new Font(Font.HELVETICA, 9, Font.BOLD);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MMM-yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm");

    public byte[] generate(Statement statement, List<InvoiceBill> bills, String companyName) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        Document document = new Document(PageSize.A4, 36, 36, 36, 36);
        try {
            PdfWriter.getInstance(document, baos);
            document.open();

            // Company Header
            Paragraph header = new Paragraph(companyName, TITLE_FONT);
            header.setAlignment(Element.ALIGN_CENTER);
            document.add(header);

            Paragraph subtitle = new Paragraph("Credit Statement", HEADER_FONT);
            subtitle.setAlignment(Element.ALIGN_CENTER);
            subtitle.setSpacingAfter(10);
            document.add(subtitle);

            // Statement Info
            PdfPTable infoTable = new PdfPTable(2);
            infoTable.setWidthPercentage(100);
            infoTable.setSpacingAfter(10);

            addInfoRow(infoTable, "Statement No:", statement.getStatementNo());
            addInfoRow(infoTable, "Customer:",
                    statement.getCustomer() != null ? statement.getCustomer().getName() : "-");
            addInfoRow(infoTable, "Period:",
                    statement.getFromDate().format(DATE_FMT) + " to " + statement.getToDate().format(DATE_FMT));
            addInfoRow(infoTable, "Statement Date:", statement.getStatementDate().format(DATE_FMT));

            document.add(infoTable);

            // Bills Table
            PdfPTable billTable = new PdfPTable(new float[]{1f, 2f, 2f, 2.5f, 3f, 2f});
            billTable.setWidthPercentage(100);
            billTable.setSpacingBefore(5);

            // Table header
            addTableHeader(billTable, "#");
            addTableHeader(billTable, "Bill No");
            addTableHeader(billTable, "Date");
            addTableHeader(billTable, "Vehicle");
            addTableHeader(billTable, "Products");
            addTableHeader(billTable, "Amount");

            int index = 1;
            for (InvoiceBill bill : bills) {
                addTableCell(billTable, String.valueOf(index++));
                addTableCell(billTable, bill.getBillNo() != null ? bill.getBillNo() : "-");
                addTableCell(billTable, bill.getDate() != null ? bill.getDate().format(DATETIME_FMT) : "-");
                addTableCell(billTable, bill.getVehicle() != null ? bill.getVehicle().getVehicleNumber() : "-");

                // Compact products
                StringBuilder prods = new StringBuilder();
                if (bill.getProducts() != null) {
                    for (InvoiceProduct ip : bill.getProducts()) {
                        if (prods.length() > 0) prods.append(", ");
                        String name = ip.getProduct() != null ? ip.getProduct().getName() : "?";
                        BigDecimal qty = ip.getQuantity() != null ? ip.getQuantity() : BigDecimal.ZERO;
                        prods.append(name).append(": ").append(qty);
                    }
                }
                addTableCell(billTable, prods.toString());
                addTableCellRight(billTable,
                        bill.getNetAmount() != null ? bill.getNetAmount().toPlainString() : "0");
            }

            document.add(billTable);

            // Totals
            PdfPTable totalsTable = new PdfPTable(2);
            totalsTable.setWidthPercentage(50);
            totalsTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalsTable.setSpacingBefore(10);

            addTotalRow(totalsTable, "Total Amount:",
                    statement.getTotalAmount() != null ? statement.getTotalAmount().toPlainString() : "0");
            addTotalRow(totalsTable, "Rounding:",
                    statement.getRoundingAmount() != null ? statement.getRoundingAmount().toPlainString() : "0");
            addTotalRow(totalsTable, "Net Amount:",
                    statement.getNetAmount() != null ? statement.getNetAmount().toPlainString() : "0");
            addTotalRow(totalsTable, "Received:",
                    statement.getReceivedAmount() != null ? statement.getReceivedAmount().toPlainString() : "0");
            addTotalRow(totalsTable, "Balance:",
                    statement.getBalanceAmount() != null ? statement.getBalanceAmount().toPlainString() : "0");

            document.add(totalsTable);

            document.close();
        } catch (DocumentException e) {
            throw new RuntimeException("Failed to generate statement PDF", e);
        }

        return baos.toByteArray();
    }

    private void addInfoRow(PdfPTable table, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, BOLD_FONT));
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setPadding(3);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, NORMAL_FONT));
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setPadding(3);
        table.addCell(valueCell);
    }

    private void addTableHeader(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, HEADER_FONT));
        cell.setBackgroundColor(new Color(230, 230, 230));
        cell.setPadding(5);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
    }

    private void addTableCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, SMALL_FONT));
        cell.setPadding(4);
        table.addCell(cell);
    }

    private void addTableCellRight(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, SMALL_FONT));
        cell.setPadding(4);
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(cell);
    }

    private void addTotalRow(PdfPTable table, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, BOLD_FONT));
        labelCell.setPadding(3);
        labelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        labelCell.setBorder(Rectangle.NO_BORDER);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, BOLD_FONT));
        valueCell.setPadding(3);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        valueCell.setBorder(Rectangle.NO_BORDER);
        table.addCell(valueCell);
    }
}
