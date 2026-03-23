package com.stopforfuel.backend.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.stopforfuel.backend.entity.Company;
import com.stopforfuel.backend.entity.InvoiceBill;
import com.stopforfuel.backend.entity.InvoiceProduct;
import com.stopforfuel.backend.entity.Statement;
import com.stopforfuel.backend.exception.ReportGenerationException;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Component
public class StatementPdfGenerator {

    private static final Color PRIMARY = new Color(33, 37, 41);
    private static final Color HEADER_BG = new Color(52, 58, 64);
    private static final Color HEADER_FG = Color.WHITE;
    private static final Color ALT_ROW = new Color(248, 249, 250);
    private static final Color BORDER_COLOR = new Color(222, 226, 230);
    private static final Color ACCENT = new Color(13, 110, 253);

    private static final Font COMPANY_FONT = new Font(Font.HELVETICA, 14, Font.BOLD, PRIMARY);
    private static final Font COMPANY_DETAIL_FONT = new Font(Font.HELVETICA, 8, Font.NORMAL, new Color(108, 117, 125));
    private static final Font TITLE_FONT = new Font(Font.HELVETICA, 12, Font.BOLD, ACCENT);
    private static final Font LABEL_FONT = new Font(Font.HELVETICA, 8, Font.BOLD, new Color(108, 117, 125));
    private static final Font VALUE_FONT = new Font(Font.HELVETICA, 9, Font.NORMAL, PRIMARY);
    private static final Font VALUE_BOLD_FONT = new Font(Font.HELVETICA, 9, Font.BOLD, PRIMARY);
    private static final Font TH_FONT = new Font(Font.HELVETICA, 8, Font.BOLD, HEADER_FG);
    private static final Font TD_FONT = new Font(Font.HELVETICA, 8, Font.NORMAL, PRIMARY);
    private static final Font TD_BOLD_FONT = new Font(Font.HELVETICA, 8, Font.BOLD, PRIMARY);
    private static final Font TOTAL_LABEL_FONT = new Font(Font.HELVETICA, 9, Font.NORMAL, new Color(108, 117, 125));
    private static final Font TOTAL_VALUE_FONT = new Font(Font.HELVETICA, 9, Font.BOLD, PRIMARY);
    private static final Font NET_LABEL_FONT = new Font(Font.HELVETICA, 10, Font.BOLD, PRIMARY);
    private static final Font NET_VALUE_FONT = new Font(Font.HELVETICA, 11, Font.BOLD, ACCENT);
    private static final Font FOOTER_FONT = new Font(Font.HELVETICA, 7, Font.ITALIC, new Color(108, 117, 125));

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MMM-yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd-MMM-yyyy");
    private static final NumberFormat INR = NumberFormat.getNumberInstance(new Locale("en", "IN"));

    static {
        INR.setMinimumFractionDigits(2);
        INR.setMaximumFractionDigits(2);
    }

    public byte[] generate(Statement statement, List<InvoiceBill> bills, Company company) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 40, 40, 30, 30);

        try {
            PdfWriter.getInstance(document, baos);
            document.open();

            // === Company Header ===
            addCompanyHeader(document, company);

            // === Divider line ===
            addDivider(document);

            // === Statement Title + Info in two columns ===
            addStatementInfo(document, statement);

            // === Bills Table ===
            addBillsTable(document, bills);

            // === Totals ===
            addTotals(document, statement);

            // === Footer ===
            addFooter(document);

            document.close();
        } catch (DocumentException e) {
            throw new ReportGenerationException("Failed to generate statement PDF", e);
        }

        return baos.toByteArray();
    }

    // Backward compatible overload
    public byte[] generate(Statement statement, List<InvoiceBill> bills, String companyName) {
        Company fallback = new Company();
        fallback.setName(companyName);
        return generate(statement, bills, fallback);
    }

    private void addCompanyHeader(Document doc, Company company) throws DocumentException {
        String name = company != null && company.getName() != null ? company.getName() : "StopForFuel";
        Paragraph companyPara = new Paragraph(name, COMPANY_FONT);
        companyPara.setAlignment(Element.ALIGN_CENTER);
        doc.add(companyPara);

        if (company != null) {
            StringBuilder details = new StringBuilder();
            if (company.getAddress() != null && !company.getAddress().isEmpty()) {
                details.append(company.getAddress());
            }
            if (company.getGstNo() != null && !company.getGstNo().isEmpty()) {
                if (details.length() > 0) details.append("  |  ");
                details.append("GST: ").append(company.getGstNo());
            }
            if (details.length() > 0) {
                Paragraph detailPara = new Paragraph(details.toString(), COMPANY_DETAIL_FONT);
                detailPara.setAlignment(Element.ALIGN_CENTER);
                doc.add(detailPara);
            }
        }
    }

    private void addDivider(Document doc) throws DocumentException {
        PdfPTable divider = new PdfPTable(1);
        divider.setWidthPercentage(100);
        divider.setSpacingBefore(8);
        divider.setSpacingAfter(8);
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(ACCENT);
        cell.setBorderWidth(1.5f);
        cell.setFixedHeight(1);
        divider.addCell(cell);
        doc.add(divider);
    }

    private void addStatementInfo(Document doc, Statement statement) throws DocumentException {
        // Title
        Paragraph title = new Paragraph("CREDIT STATEMENT", TITLE_FONT);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(12);
        doc.add(title);

        // Two-column info layout
        PdfPTable infoTable = new PdfPTable(4);
        infoTable.setWidthPercentage(100);
        infoTable.setWidths(new float[]{1.2f, 2f, 1.2f, 2f});
        infoTable.setSpacingAfter(15);

        // Row 1: Statement No | Statement Date
        addLabelValueCell(infoTable, "Statement No", statement.getStatementNo());
        addLabelValueCell(infoTable, "Date", statement.getStatementDate() != null ? statement.getStatementDate().format(DATE_FMT) : "-");

        // Row 2: Customer | Period
        String customerName = statement.getCustomer() != null ? statement.getCustomer().getName() : "-";
        String period = statement.getFromDate().format(DATE_FMT) + "  to  " + statement.getToDate().format(DATE_FMT);
        addLabelValueCell(infoTable, "Customer", customerName);
        addLabelValueCell(infoTable, "Period", period);

        // Row 3: Customer Address (if available) | Number of Bills
        String address = "";
        if (statement.getCustomer() != null && statement.getCustomer().getAddress() != null) {
            address = statement.getCustomer().getAddress();
        }
        addLabelValueCell(infoTable, "Address", address.isEmpty() ? "-" : address);
        addLabelValueCell(infoTable, "No. of Bills", String.valueOf(bills(statement)));

        doc.add(infoTable);
    }

    private int bills(Statement s) {
        return s.getNumberOfBills() != null ? s.getNumberOfBills() : 0;
    }

    private void addLabelValueCell(PdfPTable table, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, LABEL_FONT));
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setPaddingBottom(6);
        labelCell.setPaddingTop(2);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, VALUE_FONT));
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setPaddingBottom(6);
        valueCell.setPaddingTop(2);
        table.addCell(valueCell);
    }

    private void addBillsTable(Document doc, List<InvoiceBill> bills) throws DocumentException {
        float[] widths = {0.6f, 1.4f, 1.2f, 1.5f, 2.2f, 1.4f};
        PdfPTable table = new PdfPTable(widths);
        table.setWidthPercentage(100);
        table.setSpacingBefore(5);
        table.setHeaderRows(1);

        // Header
        String[] headers = {"#", "Bill No", "Date", "Vehicle", "Products", "Amount (₹)"};
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, TH_FONT));
            cell.setBackgroundColor(HEADER_BG);
            cell.setPadding(6);
            cell.setBorderColor(HEADER_BG);
            cell.setHorizontalAlignment(h.equals("Amount (₹)") || h.equals("#") ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
            if (h.equals("#")) cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }

        int index = 1;
        for (InvoiceBill bill : bills) {
            Color rowBg = (index % 2 == 0) ? ALT_ROW : Color.WHITE;

            addBillCell(table, String.valueOf(index), rowBg, Element.ALIGN_CENTER, false);
            addBillCell(table, bill.getBillNo() != null ? bill.getBillNo() : "-", rowBg, Element.ALIGN_LEFT, true);
            addBillCell(table, bill.getDate() != null ? bill.getDate().format(DATETIME_FMT) : "-", rowBg, Element.ALIGN_LEFT, false);
            addBillCell(table, bill.getVehicle() != null ? bill.getVehicle().getVehicleNumber() : "-", rowBg, Element.ALIGN_LEFT, false);

            // Products with qty
            StringBuilder prods = new StringBuilder();
            if (bill.getProducts() != null) {
                for (InvoiceProduct ip : bill.getProducts()) {
                    if (prods.length() > 0) prods.append("\n");
                    String name = ip.getProduct() != null ? ip.getProduct().getName() : "?";
                    BigDecimal qty = ip.getQuantity() != null ? ip.getQuantity() : BigDecimal.ZERO;
                    BigDecimal price = ip.getUnitPrice() != null ? ip.getUnitPrice() : BigDecimal.ZERO;
                    prods.append(name).append("  ×  ").append(qty).append("  @  ₹").append(INR.format(price));
                }
            }
            addBillCell(table, prods.toString(), rowBg, Element.ALIGN_LEFT, false);

            // Amount right-aligned bold
            String amount = bill.getNetAmount() != null ? INR.format(bill.getNetAmount()) : "0.00";
            addBillCell(table, amount, rowBg, Element.ALIGN_RIGHT, true);

            index++;
        }

        doc.add(table);
    }

    private void addBillCell(PdfPTable table, String text, Color bg, int align, boolean bold) {
        PdfPCell cell = new PdfPCell(new Phrase(text, bold ? TD_BOLD_FONT : TD_FONT));
        cell.setBackgroundColor(bg);
        cell.setPadding(5);
        cell.setPaddingTop(4);
        cell.setPaddingBottom(4);
        cell.setHorizontalAlignment(align);
        cell.setBorderColor(BORDER_COLOR);
        cell.setBorderWidth(0.5f);
        table.addCell(cell);
    }

    private void addTotals(Document doc, Statement statement) throws DocumentException {
        PdfPTable totals = new PdfPTable(2);
        totals.setWidthPercentage(45);
        totals.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totals.setSpacingBefore(12);
        totals.setWidths(new float[]{1.5f, 1.5f});

        // Total Amount
        addTotalRow(totals, "Total Amount", formatAmount(statement.getTotalAmount()), TOTAL_LABEL_FONT, TOTAL_VALUE_FONT, false);

        // Rounding
        addTotalRow(totals, "Rounding", formatAmount(statement.getRoundingAmount()), TOTAL_LABEL_FONT, TOTAL_VALUE_FONT, false);

        // Separator
        PdfPCell sepLeft = new PdfPCell();
        sepLeft.setBorder(Rectangle.BOTTOM);
        sepLeft.setBorderColor(BORDER_COLOR);
        sepLeft.setFixedHeight(2);
        totals.addCell(sepLeft);
        PdfPCell sepRight = new PdfPCell();
        sepRight.setBorder(Rectangle.BOTTOM);
        sepRight.setBorderColor(BORDER_COLOR);
        sepRight.setFixedHeight(2);
        totals.addCell(sepRight);

        // Net Amount (prominent)
        addTotalRow(totals, "Net Amount", "₹ " + formatAmount(statement.getNetAmount()), NET_LABEL_FONT, NET_VALUE_FONT, true);

        // Received
        addTotalRow(totals, "Received", formatAmount(statement.getReceivedAmount()), TOTAL_LABEL_FONT, TOTAL_VALUE_FONT, false);

        // Balance
        Font balanceFont = new Font(Font.HELVETICA, 10, Font.BOLD, statement.getBalanceAmount() != null && statement.getBalanceAmount().compareTo(BigDecimal.ZERO) > 0 ? new Color(220, 53, 69) : new Color(25, 135, 84));
        addTotalRow(totals, "Balance Due", "₹ " + formatAmount(statement.getBalanceAmount()), NET_LABEL_FONT, balanceFont, true);

        doc.add(totals);
    }

    private void addTotalRow(PdfPTable table, String label, String value, Font labelFont, Font valueFont, boolean topPad) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setPaddingRight(10);
        labelCell.setPaddingTop(topPad ? 6 : 3);
        labelCell.setPaddingBottom(3);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setPaddingTop(topPad ? 6 : 3);
        valueCell.setPaddingBottom(3);
        table.addCell(valueCell);
    }

    private void addFooter(Document doc) throws DocumentException {
        Paragraph footer = new Paragraph();
        footer.setSpacingBefore(25);
        footer.add(new Chunk("This is a computer-generated statement and does not require a signature.", FOOTER_FONT));
        footer.setAlignment(Element.ALIGN_CENTER);
        doc.add(footer);
    }

    private String formatAmount(BigDecimal amount) {
        if (amount == null) return "0.00";
        return INR.format(amount);
    }
}
