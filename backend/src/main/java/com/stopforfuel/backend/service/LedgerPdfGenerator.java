package com.stopforfuel.backend.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.stopforfuel.backend.entity.Company;
import com.stopforfuel.backend.entity.Customer;
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
public class LedgerPdfGenerator {

    // Colors
    private static final Color BLACK = new Color(33, 37, 41);
    private static final Color HEADER_BG = new Color(52, 58, 64);
    private static final Color HEADER_FG = Color.WHITE;
    private static final Color ALT_ROW = new Color(248, 249, 250);
    private static final Color BORDER = new Color(180, 180, 180);
    private static final Color MUTED = new Color(108, 117, 125);
    private static final Color ACCENT = new Color(13, 110, 253);
    private static final Color RED = new Color(220, 53, 69);
    private static final Color GREEN = new Color(25, 135, 84);
    private static final Color SUMMARY_BG = new Color(240, 243, 246);

    // Fonts
    private static final Font F_COMPANY = new Font(Font.HELVETICA, 12, Font.BOLD, BLACK);
    private static final Font F_COMPANY_SUB = new Font(Font.HELVETICA, 7.5f, Font.NORMAL, MUTED);
    private static final Font F_CUSTOMER = new Font(Font.HELVETICA, 10, Font.BOLD, BLACK);
    private static final Font F_CUSTOMER_DETAIL = new Font(Font.HELVETICA, 7.5f, Font.NORMAL, MUTED);
    private static final Font F_TITLE = new Font(Font.HELVETICA, 14, Font.BOLD, ACCENT);
    private static final Font F_PERIOD = new Font(Font.HELVETICA, 9, Font.NORMAL, MUTED);
    private static final Font F_LABEL = new Font(Font.HELVETICA, 8, Font.BOLD, MUTED);
    private static final Font F_VALUE = new Font(Font.HELVETICA, 9, Font.NORMAL, BLACK);
    private static final Font F_VALUE_BOLD = new Font(Font.HELVETICA, 9, Font.BOLD, BLACK);
    private static final Font F_TH = new Font(Font.HELVETICA, 8, Font.BOLD, HEADER_FG);
    private static final Font F_TD = new Font(Font.HELVETICA, 9, Font.NORMAL, BLACK);
    private static final Font F_TD_BOLD = new Font(Font.HELVETICA, 9, Font.BOLD, BLACK);
    private static final Font F_DEBIT = new Font(Font.HELVETICA, 9, Font.NORMAL, RED);
    private static final Font F_CREDIT = new Font(Font.HELVETICA, 9, Font.NORMAL, GREEN);
    private static final Font F_SUMMARY_LABEL = new Font(Font.HELVETICA, 8, Font.NORMAL, MUTED);
    private static final Font F_SUMMARY_VALUE = new Font(Font.HELVETICA, 10, Font.BOLD, BLACK);
    private static final Font F_BALANCE_DUE = new Font(Font.HELVETICA, 11, Font.BOLD, RED);
    private static final Font F_FOOTER = new Font(Font.HELVETICA, 7, Font.ITALIC, MUTED);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MMM-yyyy");
    private static final DateTimeFormatter FULL_DATE = DateTimeFormatter.ofPattern("dd MMMM yyyy");
    private static final NumberFormat INR = NumberFormat.getNumberInstance(new Locale("en", "IN"));

    static {
        INR.setMinimumFractionDigits(2);
        INR.setMaximumFractionDigits(2);
    }

    public byte[] generate(LedgerService.CustomerLedger ledger, Customer customer, Company company) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 25, 25, 20, 20);

        try {
            PdfWriter.getInstance(doc, baos);
            doc.open();

            addHeader(doc, ledger, customer, company);
            addSummaryBox(doc, ledger);
            addTransactionTable(doc, ledger);
            addFooter(doc);

            doc.close();
        } catch (DocumentException e) {
            throw new ReportGenerationException("Failed to generate ledger PDF", e);
        }

        return baos.toByteArray();
    }

    private void addHeader(Document doc, LedgerService.CustomerLedger ledger, Customer customer, Company company)
            throws DocumentException {
        PdfPTable header = new PdfPTable(3);
        header.setWidthPercentage(100);
        header.setWidths(new float[]{38f, 32f, 30f});

        // LEFT: Company info
        PdfPCell companyCell = new PdfPCell();
        companyCell.setBorder(Rectangle.BOX);
        companyCell.setBorderColor(BORDER);
        companyCell.setPadding(8);

        String companyName = company != null && company.getName() != null ? company.getName() : "StopForFuel";
        companyCell.addElement(new Paragraph(companyName.toUpperCase(), F_COMPANY));
        if (company != null) {
            if (company.getType() != null && !company.getType().isEmpty())
                companyCell.addElement(new Paragraph(company.getType(), F_COMPANY_SUB));
            if (company.getAddress() != null && !company.getAddress().isEmpty())
                companyCell.addElement(new Paragraph(company.getAddress(), F_COMPANY_SUB));
            if (company.getPhone() != null && !company.getPhone().isEmpty())
                companyCell.addElement(new Paragraph("Ph: " + company.getPhone(), F_COMPANY_SUB));
            if (company.getGstNo() != null && !company.getGstNo().isEmpty())
                companyCell.addElement(new Paragraph("GSTIN: " + company.getGstNo(), F_COMPANY_SUB));
        }
        header.addCell(companyCell);

        // CENTER: Customer info
        PdfPCell customerCell = new PdfPCell();
        customerCell.setBorder(Rectangle.BOX);
        customerCell.setBorderColor(BORDER);
        customerCell.setPadding(8);

        if (customer != null) {
            customerCell.addElement(new Paragraph(customer.getName(), F_CUSTOMER));
            if (customer.getGstNumber() != null && !customer.getGstNumber().isEmpty())
                customerCell.addElement(new Paragraph("GSTIN: " + customer.getGstNumber(), F_CUSTOMER_DETAIL));
            if (customer.getAddress() != null && !customer.getAddress().isEmpty())
                customerCell.addElement(new Paragraph(customer.getAddress(), F_CUSTOMER_DETAIL));
            if (customer.getPhoneNumbers() != null && !customer.getPhoneNumbers().isEmpty())
                customerCell.addElement(new Paragraph("Ph: " + String.join(", ", customer.getPhoneNumbers()), F_CUSTOMER_DETAIL));
        }
        header.addCell(customerCell);

        // RIGHT: Report title + period
        PdfPCell titleCell = new PdfPCell();
        titleCell.setBorder(Rectangle.BOX);
        titleCell.setBorderColor(BORDER);
        titleCell.setPadding(8);
        titleCell.setVerticalAlignment(Element.ALIGN_MIDDLE);

        Paragraph titleP = new Paragraph("CUSTOMER LEDGER", F_TITLE);
        titleP.setAlignment(Element.ALIGN_CENTER);
        titleCell.addElement(titleP);

        String period = ledger.fromDate.format(DATE_FMT) + " to " + ledger.toDate.format(DATE_FMT);
        Paragraph periodP = new Paragraph(period, F_PERIOD);
        periodP.setAlignment(Element.ALIGN_CENTER);
        periodP.setSpacingBefore(4);
        titleCell.addElement(periodP);

        header.addCell(titleCell);
        doc.add(header);
    }

    private void addSummaryBox(Document doc, LedgerService.CustomerLedger ledger) throws DocumentException {
        doc.add(new Paragraph(" ", new Font(Font.HELVETICA, 6)));

        PdfPTable summary = new PdfPTable(4);
        summary.setWidthPercentage(100);
        summary.setWidths(new float[]{25f, 25f, 25f, 25f});

        addSummaryCell(summary, "Opening Balance", ledger.openingBalance, BLACK);
        addSummaryCell(summary, "Total Debits (Bills)", ledger.totalDebits, RED);
        addSummaryCell(summary, "Total Credits (Payments)", ledger.totalCredits, GREEN);

        // Closing balance with special color
        Color balColor = ledger.closingBalance.compareTo(BigDecimal.ZERO) > 0 ? RED : GREEN;
        addSummaryCell(summary, "Closing Balance", ledger.closingBalance, balColor);

        doc.add(summary);
        doc.add(new Paragraph(" ", new Font(Font.HELVETICA, 4)));
    }

    private void addSummaryCell(PdfPTable table, String label, BigDecimal value, Color valueColor) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(BORDER);
        cell.setBackgroundColor(SUMMARY_BG);
        cell.setPadding(8);

        cell.addElement(new Paragraph(label, F_SUMMARY_LABEL));
        Font valFont = new Font(Font.HELVETICA, 10, Font.BOLD, valueColor);
        Paragraph valP = new Paragraph("\u20B9" + INR.format(value), valFont);
        valP.setSpacingBefore(2);
        cell.addElement(valP);

        table.addCell(cell);
    }

    private void addTransactionTable(Document doc, LedgerService.CustomerLedger ledger) throws DocumentException {
        PdfPTable table = new PdfPTable(6);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{6f, 14f, 34f, 14f, 14f, 18f});

        // Header row
        addHeaderCell(table, "#", Element.ALIGN_CENTER);
        addHeaderCell(table, "Date", Element.ALIGN_LEFT);
        addHeaderCell(table, "Description", Element.ALIGN_LEFT);
        addHeaderCell(table, "Debit", Element.ALIGN_RIGHT);
        addHeaderCell(table, "Credit", Element.ALIGN_RIGHT);
        addHeaderCell(table, "Balance", Element.ALIGN_RIGHT);

        // Opening balance row
        addOpeningRow(table, ledger);

        // Transaction entries
        List<LedgerService.LedgerEntry> entries = ledger.entries;
        for (int i = 0; i < entries.size(); i++) {
            LedgerService.LedgerEntry entry = entries.get(i);
            boolean alt = (i + 1) % 2 == 0;
            Color bg = alt ? ALT_ROW : Color.WHITE;

            addCell(table, String.valueOf(i + 1), F_TD, Element.ALIGN_CENTER, bg);
            addCell(table, entry.date.format(DATE_FMT), F_TD, Element.ALIGN_LEFT, bg);
            addCell(table, entry.description, F_TD, Element.ALIGN_LEFT, bg);

            // Debit
            if (entry.debitAmount.compareTo(BigDecimal.ZERO) > 0) {
                addCell(table, INR.format(entry.debitAmount), F_DEBIT, Element.ALIGN_RIGHT, bg);
            } else {
                addCell(table, "-", F_TD, Element.ALIGN_CENTER, bg);
            }

            // Credit
            if (entry.creditAmount.compareTo(BigDecimal.ZERO) > 0) {
                addCell(table, INR.format(entry.creditAmount), F_CREDIT, Element.ALIGN_RIGHT, bg);
            } else {
                addCell(table, "-", F_TD, Element.ALIGN_CENTER, bg);
            }

            // Running balance
            addCell(table, "\u20B9" + INR.format(entry.runningBalance), F_TD_BOLD, Element.ALIGN_RIGHT, bg);
        }

        // Closing balance row
        addClosingRow(table, ledger);

        doc.add(table);
    }

    private void addOpeningRow(PdfPTable table, LedgerService.CustomerLedger ledger) {
        Color bg = new Color(230, 240, 255);
        addCell(table, "", F_TD_BOLD, Element.ALIGN_CENTER, bg);
        addCell(table, ledger.fromDate.format(DATE_FMT), F_TD_BOLD, Element.ALIGN_LEFT, bg);
        addCell(table, "Opening Balance", F_TD_BOLD, Element.ALIGN_LEFT, bg);
        addCell(table, "-", F_TD_BOLD, Element.ALIGN_CENTER, bg);
        addCell(table, "-", F_TD_BOLD, Element.ALIGN_CENTER, bg);
        addCell(table, "\u20B9" + INR.format(ledger.openingBalance), F_TD_BOLD, Element.ALIGN_RIGHT, bg);
    }

    private void addClosingRow(PdfPTable table, LedgerService.CustomerLedger ledger) {
        Color bg = new Color(255, 240, 230);
        addCell(table, "", F_TD_BOLD, Element.ALIGN_CENTER, bg);
        addCell(table, ledger.toDate.format(DATE_FMT), F_TD_BOLD, Element.ALIGN_LEFT, bg);
        addCell(table, "Closing Balance", F_TD_BOLD, Element.ALIGN_LEFT, bg);

        Font totalDebit = new Font(Font.HELVETICA, 9, Font.BOLD, RED);
        Font totalCredit = new Font(Font.HELVETICA, 9, Font.BOLD, GREEN);
        addCell(table, "\u20B9" + INR.format(ledger.totalDebits), totalDebit, Element.ALIGN_RIGHT, bg);
        addCell(table, "\u20B9" + INR.format(ledger.totalCredits), totalCredit, Element.ALIGN_RIGHT, bg);

        Color balColor = ledger.closingBalance.compareTo(BigDecimal.ZERO) > 0 ? RED : GREEN;
        Font balFont = new Font(Font.HELVETICA, 10, Font.BOLD, balColor);
        addCell(table, "\u20B9" + INR.format(ledger.closingBalance), balFont, Element.ALIGN_RIGHT, bg);
    }

    private void addHeaderCell(PdfPTable table, String text, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, F_TH));
        cell.setBackgroundColor(HEADER_BG);
        cell.setHorizontalAlignment(align);
        cell.setPadding(6);
        cell.setBorderColor(HEADER_BG);
        table.addCell(cell);
    }

    private void addCell(PdfPTable table, String text, Font font, int align, Color bg) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(align);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(5);
        cell.setBackgroundColor(bg);
        cell.setBorderColor(new Color(220, 220, 220));
        table.addCell(cell);
    }

    private void addFooter(Document doc) throws DocumentException {
        doc.add(new Paragraph(" ", new Font(Font.HELVETICA, 6)));

        Paragraph footer = new Paragraph(
                "Generated on " + java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm"))
                        + "  |  This is a computer-generated document.",
                F_FOOTER);
        footer.setAlignment(Element.ALIGN_CENTER);
        doc.add(footer);
    }
}
