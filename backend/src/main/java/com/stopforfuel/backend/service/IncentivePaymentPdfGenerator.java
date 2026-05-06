package com.stopforfuel.backend.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.stopforfuel.backend.entity.Company;
import com.stopforfuel.backend.entity.IncentivePayment;
import com.stopforfuel.backend.exception.ReportGenerationException;
import com.stopforfuel.backend.service.pdf.PageFooterEvent;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class IncentivePaymentPdfGenerator {

    private static final Color BLACK = new Color(33, 37, 41);
    private static final Color HEADER_BG = new Color(52, 58, 64);
    private static final Color HEADER_FG = Color.WHITE;
    private static final Color ALT_ROW = new Color(248, 249, 250);
    private static final Color BORDER = new Color(180, 180, 180);
    private static final Color LIGHT_BORDER = new Color(220, 220, 220);
    private static final Color ACCENT = new Color(13, 110, 253);
    private static final Color MUTED = new Color(108, 117, 125);
    private static final Color ORANGE = new Color(234, 88, 12);
    private static final Color TOTAL_BG = new Color(233, 236, 239);

    private static final Font F_COMPANY = new Font(Font.HELVETICA, 12, Font.BOLD, BLACK);
    private static final Font F_COMPANY_SUB = new Font(Font.HELVETICA, 8, Font.NORMAL, MUTED);
    private static final Font F_TITLE = new Font(Font.HELVETICA, 14, Font.BOLD, ACCENT);
    private static final Font F_PERIOD = new Font(Font.HELVETICA, 9, Font.NORMAL, MUTED);
    private static final Font F_KPI_LABEL = new Font(Font.HELVETICA, 7, Font.BOLD, MUTED);
    private static final Font F_KPI_VALUE = new Font(Font.HELVETICA, 12, Font.BOLD, ORANGE);
    private static final Font F_KPI_VALUE_NEUTRAL = new Font(Font.HELVETICA, 12, Font.BOLD, BLACK);
    private static final Font F_TH = new Font(Font.HELVETICA, 8, Font.BOLD, HEADER_FG);
    private static final Font F_TD = new Font(Font.HELVETICA, 8, Font.NORMAL, BLACK);
    private static final Font F_TD_BOLD = new Font(Font.HELVETICA, 8, Font.BOLD, BLACK);
    private static final Font F_TD_AMOUNT = new Font(Font.HELVETICA, 8, Font.BOLD, ORANGE);
    private static final Font F_EMPTY = new Font(Font.HELVETICA, 9, Font.ITALIC, MUTED);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MMM-yyyy");
    private static final DateTimeFormatter ROW_DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yy HH:mm");
    private static final NumberFormat INR = NumberFormat.getNumberInstance(new Locale("en", "IN"));

    static {
        INR.setMinimumFractionDigits(2);
        INR.setMaximumFractionDigits(2);
    }

    private static final float[] COL_WIDTHS = {0.4f, 1.1f, 2.1f, 1f, 2.6f, 1.2f, 0.6f};
    private static final String[] COL_HEADERS = {"#", "DATE", "CUSTOMER", "AMOUNT", "DESCRIPTION", "BILL / STMT", "SHIFT"};
    private static final Set<Integer> RIGHT_ALIGN = new HashSet<>(List.of(3));
    private static final Set<Integer> CENTER_ALIGN = new HashSet<>(List.of(0, 1, 6));

    public byte[] generate(List<IncentivePayment> payments,
                           Company company,
                           LocalDate fromDate,
                           LocalDate toDate) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), 25, 25, 20, 30);

        try {
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            writer.setPageEvent(new PageFooterEvent());
            doc.open();

            addHeader(doc, company, fromDate, toDate);
            addKpiRow(doc, payments);
            addTable(doc, payments);

            doc.close();
        } catch (DocumentException e) {
            throw new ReportGenerationException("Failed to generate Incentive Payment PDF", e);
        }

        return baos.toByteArray();
    }

    private void addHeader(Document doc, Company company, LocalDate fromDate, LocalDate toDate) throws DocumentException {
        PdfPTable header = new PdfPTable(2);
        header.setWidthPercentage(100);
        header.setWidths(new float[]{60f, 40f});

        PdfPCell companyCell = new PdfPCell();
        companyCell.setBorder(Rectangle.NO_BORDER);
        String companyName = company != null && company.getName() != null ? company.getName() : "StopForFuel";
        companyCell.addElement(new Paragraph(companyName.toUpperCase(), F_COMPANY));
        if (company != null && company.getAddress() != null && !company.getAddress().isEmpty()) {
            companyCell.addElement(new Paragraph(company.getAddress(), F_COMPANY_SUB));
        }
        if (company != null && company.getGstNo() != null && !company.getGstNo().isEmpty()) {
            companyCell.addElement(new Paragraph("GSTIN: " + company.getGstNo(), F_COMPANY_SUB));
        }
        header.addCell(companyCell);

        PdfPCell titleCell = new PdfPCell();
        titleCell.setBorder(Rectangle.NO_BORDER);
        Paragraph titleP = new Paragraph("INCENTIVE PAYMENTS", F_TITLE);
        titleP.setAlignment(Element.ALIGN_RIGHT);
        titleCell.addElement(titleP);
        Paragraph periodP = new Paragraph(
                "Period: " + fromDate.format(DATE_FMT) + " — " + toDate.format(DATE_FMT),
                F_PERIOD);
        periodP.setAlignment(Element.ALIGN_RIGHT);
        titleCell.addElement(periodP);
        header.addCell(titleCell);

        doc.add(header);
    }

    private void addKpiRow(Document doc, List<IncentivePayment> payments) throws DocumentException {
        BigDecimal total = BigDecimal.ZERO;
        Set<String> customerKeys = new HashSet<>();
        for (IncentivePayment p : payments) {
            if (p.getAmount() != null) total = total.add(p.getAmount());
            String key = customerKey(p);
            if (key != null) customerKeys.add(key);
        }

        PdfPTable kpi = new PdfPTable(3);
        kpi.setWidthPercentage(100);
        kpi.setSpacingBefore(8);
        kpi.setSpacingAfter(8);

        kpi.addCell(buildKpiCell("TOTAL INCENTIVES", "₹ " + INR.format(total), F_KPI_VALUE));
        kpi.addCell(buildKpiCell("TRANSACTIONS", String.valueOf(payments.size()), F_KPI_VALUE_NEUTRAL));
        kpi.addCell(buildKpiCell("CUSTOMERS", String.valueOf(customerKeys.size()), F_KPI_VALUE_NEUTRAL));

        doc.add(kpi);
    }

    private PdfPCell buildKpiCell(String label, String value, Font valueFont) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(BORDER);
        cell.setPadding(6);

        Paragraph labelP = new Paragraph(label, F_KPI_LABEL);
        labelP.setAlignment(Element.ALIGN_LEFT);
        cell.addElement(labelP);

        Paragraph valueP = new Paragraph(value, valueFont);
        valueP.setAlignment(Element.ALIGN_LEFT);
        valueP.setSpacingBefore(2);
        cell.addElement(valueP);
        return cell;
    }

    private void addTable(Document doc, List<IncentivePayment> payments) throws DocumentException {
        PdfPTable table = new PdfPTable(COL_WIDTHS);
        table.setWidthPercentage(100);
        table.setHeaderRows(1);

        for (int i = 0; i < COL_HEADERS.length; i++) {
            PdfPCell cell = new PdfPCell(new Phrase(COL_HEADERS[i], F_TH));
            cell.setBackgroundColor(HEADER_BG);
            cell.setPadding(4);
            cell.setBorderColor(HEADER_BG);
            int align = CENTER_ALIGN.contains(i) ? Element.ALIGN_CENTER
                    : RIGHT_ALIGN.contains(i) ? Element.ALIGN_RIGHT
                    : Element.ALIGN_LEFT;
            cell.setHorizontalAlignment(align);
            table.addCell(cell);
        }

        if (payments.isEmpty()) {
            PdfPCell empty = new PdfPCell(new Phrase("No incentive payments in this period.", F_EMPTY));
            empty.setColspan(COL_WIDTHS.length);
            empty.setPadding(20);
            empty.setHorizontalAlignment(Element.ALIGN_CENTER);
            empty.setBorderColor(LIGHT_BORDER);
            table.addCell(empty);
            doc.add(table);
            return;
        }

        BigDecimal total = BigDecimal.ZERO;
        int idx = 1;
        for (IncentivePayment p : payments) {
            Color bg = (idx % 2 == 0) ? ALT_ROW : Color.WHITE;
            String date = p.getPaymentDate() != null ? p.getPaymentDate().format(ROW_DATE_FMT) : "-";
            String customer = resolveCustomerName(p);
            String amount = p.getAmount() != null ? INR.format(p.getAmount()) : "0.00";
            String desc = p.getDescription() != null ? p.getDescription() : "-";
            String ref = buildBillOrStatementRef(p);
            String shift = p.getShiftId() != null ? "#" + p.getShiftId() : "-";

            addCell(table, String.valueOf(idx), bg, Element.ALIGN_CENTER, F_TD);
            addCell(table, date, bg, Element.ALIGN_CENTER, F_TD);
            addCell(table, customer, bg, Element.ALIGN_LEFT, F_TD_BOLD);
            addCell(table, amount, bg, Element.ALIGN_RIGHT, F_TD_AMOUNT);
            addCell(table, desc, bg, Element.ALIGN_LEFT, F_TD);
            addCell(table, ref, bg, Element.ALIGN_LEFT, F_TD);
            addCell(table, shift, bg, Element.ALIGN_CENTER, F_TD);

            if (p.getAmount() != null) total = total.add(p.getAmount());
            idx++;
        }

        addCell(table, "", TOTAL_BG, Element.ALIGN_LEFT, F_TD);
        addCell(table, "", TOTAL_BG, Element.ALIGN_LEFT, F_TD);
        addCell(table, "TOTAL (" + payments.size() + " entries)", TOTAL_BG, Element.ALIGN_RIGHT, F_TD_BOLD);
        addCell(table, INR.format(total), TOTAL_BG, Element.ALIGN_RIGHT, F_TD_AMOUNT);
        addCell(table, "", TOTAL_BG, Element.ALIGN_LEFT, F_TD);
        addCell(table, "", TOTAL_BG, Element.ALIGN_LEFT, F_TD);
        addCell(table, "", TOTAL_BG, Element.ALIGN_LEFT, F_TD);

        doc.add(table);
    }

    private void addCell(PdfPTable table, String text, Color bg, int align, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(bg);
        cell.setPadding(3);
        cell.setHorizontalAlignment(align);
        cell.setBorderColor(LIGHT_BORDER);
        cell.setBorderWidth(0.5f);
        table.addCell(cell);
    }

    private String resolveCustomerName(IncentivePayment p) {
        if (p.getCustomer() != null && p.getCustomer().getName() != null) {
            return p.getCustomer().getName();
        }
        if (p.getInvoiceBill() != null) {
            if (p.getInvoiceBill().getCustomer() != null && p.getInvoiceBill().getCustomer().getName() != null) {
                return p.getInvoiceBill().getCustomer().getName();
            }
            if (p.getInvoiceBill().getSignatoryName() != null) {
                return p.getInvoiceBill().getSignatoryName();
            }
            if (p.getInvoiceBill().getBillDesc() != null) {
                return p.getInvoiceBill().getBillDesc();
            }
        }
        return "Walk-in";
    }

    private String buildBillOrStatementRef(IncentivePayment p) {
        if (p.getInvoiceBill() != null) {
            String billNo = p.getInvoiceBill().getBillNo();
            return "Bill: " + (billNo != null ? billNo : "#" + p.getInvoiceBill().getId());
        }
        if (p.getStatement() != null) {
            String stmtNo = p.getStatement().getStatementNo();
            return "Stmt: " + (stmtNo != null ? stmtNo : "#" + p.getStatement().getId());
        }
        return "-";
    }

    private String customerKey(IncentivePayment p) {
        if (p.getCustomer() != null && p.getCustomer().getId() != null) {
            return "c:" + p.getCustomer().getId();
        }
        if (p.getInvoiceBill() != null) {
            String name = p.getInvoiceBill().getCustomer() != null ? p.getInvoiceBill().getCustomer().getName() : null;
            if (name == null) name = p.getInvoiceBill().getSignatoryName();
            if (name == null) name = p.getInvoiceBill().getBillDesc();
            if (name != null) return "w:" + name;
        }
        return null;
    }

}
