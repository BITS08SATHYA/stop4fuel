package com.stopforfuel.backend.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.stopforfuel.backend.entity.*;
import com.stopforfuel.backend.exception.ReportGenerationException;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

@Component
public class StatementPdfGenerator {

    // Colors
    private static final Color BLACK = new Color(33, 37, 41);
    private static final Color HEADER_BG = new Color(52, 58, 64);
    private static final Color HEADER_FG = Color.WHITE;
    private static final Color ALT_ROW = new Color(248, 249, 250);
    private static final Color BORDER = new Color(180, 180, 180);
    private static final Color LIGHT_BORDER = new Color(220, 220, 220);
    private static final Color ACCENT = new Color(13, 110, 253);
    private static final Color MUTED = new Color(108, 117, 125);
    private static final Color RED = new Color(220, 53, 69);

    // Fonts
    private static final Font F_COMPANY = new Font(Font.HELVETICA, 11, Font.BOLD, BLACK);
    private static final Font F_COMPANY_SUB = new Font(Font.HELVETICA, 7, Font.NORMAL, MUTED);
    private static final Font F_CUSTOMER = new Font(Font.HELVETICA, 10, Font.BOLD, BLACK);
    private static final Font F_CUSTOMER_DETAIL = new Font(Font.HELVETICA, 7, Font.NORMAL, MUTED);
    private static final Font F_TITLE = new Font(Font.HELVETICA, 13, Font.BOLD, ACCENT);
    private static final Font F_STMT_NO = new Font(Font.HELVETICA, 11, Font.BOLD, RED);
    private static final Font F_LABEL = new Font(Font.HELVETICA, 7, Font.BOLD, MUTED);
    private static final Font F_VALUE = new Font(Font.HELVETICA, 7.5f, Font.NORMAL, BLACK);
    private static final Font F_VALUE_BOLD = new Font(Font.HELVETICA, 7.5f, Font.BOLD, BLACK);
    private static final Font F_TH = new Font(Font.HELVETICA, 6.5f, Font.BOLD, HEADER_FG);
    private static final Font F_TD = new Font(Font.HELVETICA, 6.5f, Font.NORMAL, BLACK);
    private static final Font F_TD_BOLD = new Font(Font.HELVETICA, 6.5f, Font.BOLD, BLACK);
    private static final Font F_SECTION = new Font(Font.HELVETICA, 8, Font.BOLD, BLACK);
    private static final Font F_SUMMARY_TH = new Font(Font.HELVETICA, 6.5f, Font.BOLD, HEADER_FG);
    private static final Font F_SUMMARY_TD = new Font(Font.HELVETICA, 6.5f, Font.NORMAL, BLACK);
    private static final Font F_SUMMARY_BOLD = new Font(Font.HELVETICA, 6.5f, Font.BOLD, BLACK);
    private static final Font F_BALANCE_LABEL = new Font(Font.HELVETICA, 8, Font.NORMAL, MUTED);
    private static final Font F_BALANCE_VALUE = new Font(Font.HELVETICA, 8, Font.BOLD, BLACK);
    private static final Font F_BALANCE_DUE_LABEL = new Font(Font.HELVETICA, 10, Font.BOLD, BLACK);
    private static final Font F_BALANCE_DUE = new Font(Font.HELVETICA, 13, Font.BOLD, RED);
    private static final Font F_WORDS = new Font(Font.HELVETICA, 7, Font.ITALIC, MUTED);
    private static final Font F_TERMS = new Font(Font.HELVETICA, 6.5f, Font.NORMAL, MUTED);
    private static final Font F_SIGNATORY = new Font(Font.HELVETICA, 7.5f, Font.ITALIC, BLACK);
    private static final Font F_FOOTER = new Font(Font.HELVETICA, 6, Font.ITALIC, MUTED);
    private static final Font F_STATUS = new Font(Font.HELVETICA, 8, Font.BOLD);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MMM-yyyy");
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("dd MMMM yyyy");
    private static final NumberFormat INR = NumberFormat.getNumberInstance(new Locale("en", "IN"));

    static {
        INR.setMinimumFractionDigits(2);
        INR.setMaximumFractionDigits(2);
    }

    public byte[] generate(Statement statement, List<InvoiceBill> bills, Company company, List<Payment> payments) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 25, 25, 20, 20);

        try {
            PdfWriter.getInstance(doc, baos);
            doc.open();

            addHeader(doc, statement, company);
            addBillsHeading(doc, statement);
            addBillsTable(doc, bills);
            addBottomSection(doc, bills, payments, statement, company);

            doc.close();
        } catch (DocumentException e) {
            throw new ReportGenerationException("Failed to generate statement PDF", e);
        }

        return baos.toByteArray();
    }

    // Backward compatible overload (no payments)
    public byte[] generate(Statement statement, List<InvoiceBill> bills, Company company) {
        return generate(statement, bills, company, List.of());
    }

    public byte[] generate(Statement statement, List<InvoiceBill> bills, String companyName) {
        Company fallback = new Company();
        fallback.setName(companyName);
        return generate(statement, bills, fallback, List.of());
    }

    // ========== HEADER ==========
    private void addHeader(Document doc, Statement statement, Company company) throws DocumentException {
        PdfPTable header = new PdfPTable(3);
        header.setWidthPercentage(100);
        header.setWidths(new float[]{35f, 35f, 30f});

        // LEFT: Company info
        PdfPCell companyCell = new PdfPCell();
        companyCell.setBorder(Rectangle.BOX);
        companyCell.setBorderColor(BORDER);
        companyCell.setPadding(6);

        String companyName = company != null && company.getName() != null ? company.getName() : "StopForFuel";
        companyCell.addElement(new Paragraph(companyName.toUpperCase(), F_COMPANY));

        if (company != null) {
            if (company.getType() != null && !company.getType().isEmpty()) {
                companyCell.addElement(new Paragraph(company.getType(), F_COMPANY_SUB));
            }
            if (company.getAddress() != null && !company.getAddress().isEmpty()) {
                companyCell.addElement(new Paragraph(company.getAddress(), F_COMPANY_SUB));
            }
            if (company.getPhone() != null && !company.getPhone().isEmpty()) {
                companyCell.addElement(new Paragraph("Ph: " + company.getPhone(), F_COMPANY_SUB));
            }
            if (company.getGstNo() != null && !company.getGstNo().isEmpty()) {
                companyCell.addElement(new Paragraph("GSTIN: " + company.getGstNo(), F_COMPANY_SUB));
            }
        }
        header.addCell(companyCell);

        // CENTER: Customer info
        PdfPCell customerCell = new PdfPCell();
        customerCell.setBorder(Rectangle.BOX);
        customerCell.setBorderColor(BORDER);
        customerCell.setPadding(6);

        Customer customer = statement.getCustomer();
        if (customer != null) {
            customerCell.addElement(new Paragraph(customer.getName(), F_CUSTOMER));
            if (customer.getGstNumber() != null && !customer.getGstNumber().isEmpty()) {
                customerCell.addElement(new Paragraph("GSTIN: " + customer.getGstNumber(), F_CUSTOMER_DETAIL));
            }
            if (customer.getAddress() != null && !customer.getAddress().isEmpty()) {
                customerCell.addElement(new Paragraph(customer.getAddress(), F_CUSTOMER_DETAIL));
            }
            if (customer.getPhoneNumbers() != null && !customer.getPhoneNumbers().isEmpty()) {
                customerCell.addElement(new Paragraph("Ph: " + String.join(", ", customer.getPhoneNumbers()), F_CUSTOMER_DETAIL));
            }
        }
        header.addCell(customerCell);

        // RIGHT: Statement info
        PdfPCell stmtCell = new PdfPCell();
        stmtCell.setBorder(Rectangle.BOX);
        stmtCell.setBorderColor(BORDER);
        stmtCell.setPadding(6);

        Paragraph titleP = new Paragraph("CREDIT STATEMENT", F_TITLE);
        titleP.setAlignment(Element.ALIGN_CENTER);
        stmtCell.addElement(titleP);

        Paragraph stmtNoP = new Paragraph(statement.getStatementNo(), F_STMT_NO);
        stmtNoP.setAlignment(Element.ALIGN_CENTER);
        stmtCell.addElement(stmtNoP);

        stmtCell.addElement(stmtInfoLine("Date:", statement.getStatementDate() != null ? statement.getStatementDate().format(DATE_FMT) : "-"));
        stmtCell.addElement(stmtInfoLine("Period:", statement.getFromDate().format(DATE_FMT) + " — " + statement.getToDate().format(DATE_FMT)));

        // Status
        String statusText = statement.getStatus() != null ? statement.getStatus().replace("_", " ") : "NOT PAID";
        F_STATUS.setColor(statusText.equals("PAID") ? new Color(25, 135, 84) : statusText.equals("DRAFT") ? MUTED : RED);
        Paragraph statusP = new Paragraph(statusText, F_STATUS);
        statusP.setAlignment(Element.ALIGN_CENTER);
        statusP.setSpacingBefore(3);
        stmtCell.addElement(statusP);

        header.addCell(stmtCell);
        doc.add(header);
    }

    private Paragraph stmtInfoLine(String label, String value) {
        Paragraph p = new Paragraph();
        p.add(new Chunk(label + " ", F_LABEL));
        p.add(new Chunk(value, F_VALUE));
        p.setSpacingBefore(1);
        return p;
    }

    // ========== BILLS HEADING ==========
    private void addBillsHeading(Document doc, Statement statement) throws DocumentException {
        String period = statement.getFromDate().format(MONTH_FMT).toUpperCase() + " TO " + statement.getToDate().format(MONTH_FMT).toUpperCase();
        Paragraph heading = new Paragraph("CREDIT BILLS — " + period, F_SECTION);
        heading.setAlignment(Element.ALIGN_CENTER);
        heading.setSpacingBefore(8);
        heading.setSpacingAfter(4);
        doc.add(heading);
    }

    // ========== BILLS TABLE ==========
    private void addBillsTable(Document doc, List<InvoiceBill> bills) throws DocumentException {
        float[] widths = {0.35f, 0.6f, 0.8f, 1.3f, 0.9f, 0.7f, 0.55f, 0.65f, 0.6f, 0.8f, 0.5f, 0.85f};
        PdfPTable table = new PdfPTable(widths);
        table.setWidthPercentage(100);
        table.setHeaderRows(1);

        String[] headers = {"#", "DATE", "BILL NO", "VEHICLE", "DRIVER", "INDENT", "PROD", "QTY (L)", "RATE", "GROSS", "DISC", "NET AMT"};
        int[] rightAlign = {0, 7, 8, 9, 10, 11};
        Set<Integer> rightSet = new HashSet<>();
        for (int r : rightAlign) rightSet.add(r);

        for (int i = 0; i < headers.length; i++) {
            PdfPCell cell = new PdfPCell(new Phrase(headers[i], F_TH));
            cell.setBackgroundColor(HEADER_BG);
            cell.setPadding(3);
            cell.setBorderColor(HEADER_BG);
            cell.setHorizontalAlignment(rightSet.contains(i) ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
            if (i == 0) cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }

        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalDisc = BigDecimal.ZERO;
        BigDecimal totalNet = BigDecimal.ZERO;

        int index = 1;
        for (InvoiceBill bill : bills) {
            Color bg = (index % 2 == 0) ? ALT_ROW : Color.WHITE;

            addCell(table, String.valueOf(index), bg, Element.ALIGN_CENTER, false);
            addCell(table, bill.getDate() != null ? bill.getDate().format(SHORT_DATE) : "-", bg, Element.ALIGN_LEFT, false);
            addCell(table, bill.getBillNo() != null ? bill.getBillNo() : "-", bg, Element.ALIGN_LEFT, true);
            addCell(table, bill.getVehicle() != null ? bill.getVehicle().getVehicleNumber() : "-", bg, Element.ALIGN_LEFT, false);
            addCell(table, bill.getDriverName() != null ? bill.getDriverName() : "-", bg, Element.ALIGN_LEFT, false);
            addCell(table, bill.getIndentNo() != null ? bill.getIndentNo() : "-", bg, Element.ALIGN_LEFT, false);

            // Product (first product short name)
            String prodName = "-";
            BigDecimal qty = BigDecimal.ZERO;
            BigDecimal rate = BigDecimal.ZERO;
            if (bill.getProducts() != null && !bill.getProducts().isEmpty()) {
                InvoiceProduct ip = bill.getProducts().get(0);
                if (ip.getProduct() != null) {
                    prodName = shortProductName(ip.getProduct().getName());
                }
                qty = ip.getQuantity() != null ? ip.getQuantity() : BigDecimal.ZERO;
                rate = ip.getUnitPrice() != null ? ip.getUnitPrice() : BigDecimal.ZERO;
            }
            addCell(table, prodName, bg, Element.ALIGN_LEFT, false);
            addCell(table, qty.compareTo(BigDecimal.ZERO) > 0 ? fmt0(qty) : "-", bg, Element.ALIGN_RIGHT, false);
            addCell(table, rate.compareTo(BigDecimal.ZERO) > 0 ? fmtRate(rate) : "-", bg, Element.ALIGN_RIGHT, false);

            BigDecimal gross = bill.getGrossAmount() != null ? bill.getGrossAmount() : (bill.getNetAmount() != null ? bill.getNetAmount() : BigDecimal.ZERO);
            BigDecimal disc = bill.getTotalDiscount() != null ? bill.getTotalDiscount() : BigDecimal.ZERO;
            BigDecimal net = bill.getNetAmount() != null ? bill.getNetAmount() : BigDecimal.ZERO;

            addCell(table, fmtAmt(gross), bg, Element.ALIGN_RIGHT, false);
            addCell(table, disc.compareTo(BigDecimal.ZERO) > 0 ? fmtAmt(disc) : "-", bg, Element.ALIGN_RIGHT, false);
            addCell(table, fmtAmt(net), bg, Element.ALIGN_RIGHT, true);

            totalQty = totalQty.add(qty);
            totalGross = totalGross.add(gross);
            totalDisc = totalDisc.add(disc);
            totalNet = totalNet.add(net);
            index++;
        }

        // Total row
        Color totBg = new Color(233, 236, 239);
        addCell(table, "", totBg, Element.ALIGN_LEFT, false);
        addCell(table, "", totBg, Element.ALIGN_LEFT, false);
        addCell(table, "TOTAL (" + bills.size() + " bills)", totBg, Element.ALIGN_LEFT, true);
        addCell(table, "", totBg, Element.ALIGN_LEFT, false);
        addCell(table, "", totBg, Element.ALIGN_LEFT, false);
        addCell(table, "", totBg, Element.ALIGN_LEFT, false);
        addCell(table, "", totBg, Element.ALIGN_LEFT, false);
        addCell(table, fmt0(totalQty), totBg, Element.ALIGN_RIGHT, true);
        addCell(table, "", totBg, Element.ALIGN_RIGHT, false);
        addCell(table, fmtAmt(totalGross), totBg, Element.ALIGN_RIGHT, true);
        addCell(table, totalDisc.compareTo(BigDecimal.ZERO) > 0 ? fmtAmt(totalDisc) : "-", totBg, Element.ALIGN_RIGHT, true);
        addCell(table, fmtAmt(totalNet), totBg, Element.ALIGN_RIGHT, true);

        doc.add(table);
    }

    // ========== BOTTOM SECTION ==========
    private void addBottomSection(Document doc, List<InvoiceBill> bills, List<Payment> payments, Statement statement, Company company) throws DocumentException {
        PdfPTable bottom = new PdfPTable(2);
        bottom.setWidthPercentage(100);
        bottom.setWidths(new float[]{50f, 50f});
        bottom.setSpacingBefore(6);

        // LEFT: Product Summary + Vehicle Summary
        PdfPCell leftCell = new PdfPCell();
        leftCell.setBorder(Rectangle.BOX);
        leftCell.setBorderColor(BORDER);
        leftCell.setPadding(4);
        leftCell.addElement(buildProductSummary(bills));
        leftCell.addElement(buildVehicleSummary(bills));
        bottom.addCell(leftCell);

        // RIGHT: Payments Received + Balance
        PdfPCell rightCell = new PdfPCell();
        rightCell.setBorder(Rectangle.BOX);
        rightCell.setBorderColor(BORDER);
        rightCell.setPadding(4);
        rightCell.addElement(buildPaymentsReceived(payments));
        rightCell.addElement(buildBalance(statement));
        bottom.addCell(rightCell);

        doc.add(bottom);

        // Terms and signatory
        addTermsAndSignatory(doc, company);
    }

    // ========== PRODUCT SUMMARY ==========
    private PdfPTable buildProductSummary(List<InvoiceBill> bills) throws DocumentException {
        Paragraph title = new Paragraph("PRODUCT SUMMARY", F_SECTION);
        title.setSpacingAfter(3);

        // Aggregate by product
        Map<String, BigDecimal[]> productMap = new LinkedHashMap<>(); // name -> [billCount, qty, disc, netAmt]
        for (InvoiceBill bill : bills) {
            if (bill.getProducts() == null) continue;
            for (InvoiceProduct ip : bill.getProducts()) {
                String name = ip.getProduct() != null ? ip.getProduct().getName() : "Other";
                BigDecimal[] agg = productMap.computeIfAbsent(name, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
                agg[0] = agg[0].add(BigDecimal.ONE);
                agg[1] = agg[1].add(ip.getQuantity() != null ? ip.getQuantity() : BigDecimal.ZERO);
                agg[2] = agg[2].add(ip.getDiscountAmount() != null ? ip.getDiscountAmount() : BigDecimal.ZERO);
                agg[3] = agg[3].add(ip.getAmount() != null ? ip.getAmount() : BigDecimal.ZERO);
            }
        }

        PdfPTable table = new PdfPTable(new float[]{1.5f, 0.6f, 0.8f, 0.7f, 1f});
        table.setWidthPercentage(100);

        String[] headers = {"PRODUCT", "BILLS", "QTY (L)", "DISC", "NET AMT"};
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, F_SUMMARY_TH));
            cell.setBackgroundColor(HEADER_BG);
            cell.setPadding(2.5f);
            cell.setBorderColor(HEADER_BG);
            cell.setHorizontalAlignment(h.equals("PRODUCT") ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT);
            table.addCell(cell);
        }

        BigDecimal totalBills = BigDecimal.ZERO, totalQty = BigDecimal.ZERO, totalDisc = BigDecimal.ZERO, totalNet = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal[]> e : productMap.entrySet()) {
            BigDecimal[] v = e.getValue();
            addSummaryCell(table, e.getKey(), Element.ALIGN_LEFT, false);
            addSummaryCell(table, fmt0(v[0]), Element.ALIGN_RIGHT, false);
            addSummaryCell(table, fmt0(v[1]), Element.ALIGN_RIGHT, false);
            addSummaryCell(table, fmtAmt(v[2]), Element.ALIGN_RIGHT, false);
            addSummaryCell(table, fmtAmt(v[3]), Element.ALIGN_RIGHT, false);
            totalBills = totalBills.add(v[0]);
            totalQty = totalQty.add(v[1]);
            totalDisc = totalDisc.add(v[2]);
            totalNet = totalNet.add(v[3]);
        }

        // Total row
        addSummaryCell(table, "Total", Element.ALIGN_LEFT, true);
        addSummaryCell(table, fmt0(totalBills), Element.ALIGN_RIGHT, true);
        addSummaryCell(table, fmt0(totalQty), Element.ALIGN_RIGHT, true);
        addSummaryCell(table, fmtAmt(totalDisc), Element.ALIGN_RIGHT, true);
        addSummaryCell(table, fmtAmt(totalNet), Element.ALIGN_RIGHT, true);

        PdfPTable wrapper = new PdfPTable(1);
        wrapper.setWidthPercentage(100);
        PdfPCell titleCell = new PdfPCell(title);
        titleCell.setBorder(Rectangle.NO_BORDER);
        wrapper.addCell(titleCell);
        PdfPCell tableCell = new PdfPCell(table);
        tableCell.setBorder(Rectangle.NO_BORDER);
        wrapper.addCell(tableCell);
        return wrapper;
    }

    // ========== VEHICLE SUMMARY ==========
    private PdfPTable buildVehicleSummary(List<InvoiceBill> bills) throws DocumentException {
        Paragraph title = new Paragraph("VEHICLE SUMMARY", F_SECTION);
        title.setSpacingBefore(5);
        title.setSpacingAfter(3);

        // Aggregate by vehicle
        Map<String, BigDecimal[]> vehicleMap = new LinkedHashMap<>(); // vehicleNo -> [billCount, qty, netAmt]
        for (InvoiceBill bill : bills) {
            String veh = bill.getVehicle() != null ? bill.getVehicle().getVehicleNumber() : "N/A";
            BigDecimal[] agg = vehicleMap.computeIfAbsent(veh, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            agg[0] = agg[0].add(BigDecimal.ONE);
            BigDecimal qty = BigDecimal.ZERO;
            if (bill.getProducts() != null) {
                for (InvoiceProduct ip : bill.getProducts()) {
                    qty = qty.add(ip.getQuantity() != null ? ip.getQuantity() : BigDecimal.ZERO);
                }
            }
            agg[1] = agg[1].add(qty);
            agg[2] = agg[2].add(bill.getNetAmount() != null ? bill.getNetAmount() : BigDecimal.ZERO);
        }

        PdfPTable table = new PdfPTable(new float[]{1.8f, 0.6f, 0.8f, 1f});
        table.setWidthPercentage(100);

        String[] headers = {"VEHICLE", "BILLS", "QTY", "NET AMT"};
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, F_SUMMARY_TH));
            cell.setBackgroundColor(HEADER_BG);
            cell.setPadding(2.5f);
            cell.setBorderColor(HEADER_BG);
            cell.setHorizontalAlignment(h.equals("VEHICLE") ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT);
            table.addCell(cell);
        }

        for (Map.Entry<String, BigDecimal[]> e : vehicleMap.entrySet()) {
            BigDecimal[] v = e.getValue();
            addSummaryCell(table, e.getKey(), Element.ALIGN_LEFT, false);
            addSummaryCell(table, fmt0(v[0]), Element.ALIGN_RIGHT, false);
            addSummaryCell(table, fmt0(v[1]), Element.ALIGN_RIGHT, false);
            addSummaryCell(table, fmtAmt(v[2]), Element.ALIGN_RIGHT, false);
        }

        PdfPTable wrapper = new PdfPTable(1);
        wrapper.setWidthPercentage(100);
        PdfPCell titleCell = new PdfPCell(title);
        titleCell.setBorder(Rectangle.NO_BORDER);
        wrapper.addCell(titleCell);
        PdfPCell tableCell = new PdfPCell(table);
        tableCell.setBorder(Rectangle.NO_BORDER);
        wrapper.addCell(tableCell);
        return wrapper;
    }

    // ========== PAYMENTS RECEIVED ==========
    private PdfPTable buildPaymentsReceived(List<Payment> payments) throws DocumentException {
        Paragraph title = new Paragraph("PAYMENTS RECEIVED", F_SECTION);
        title.setSpacingAfter(3);

        PdfPTable table = new PdfPTable(new float[]{0.4f, 0.8f, 0.8f, 1.2f, 1f});
        table.setWidthPercentage(100);

        String[] headers = {"#", "DATE", "MODE", "REFERENCE", "AMOUNT"};
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, F_SUMMARY_TH));
            cell.setBackgroundColor(HEADER_BG);
            cell.setPadding(2.5f);
            cell.setBorderColor(HEADER_BG);
            cell.setHorizontalAlignment(h.equals("AMOUNT") ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
            table.addCell(cell);
        }

        BigDecimal totalReceived = BigDecimal.ZERO;
        if (payments != null && !payments.isEmpty()) {
            int idx = 1;
            for (Payment p : payments) {
                addSummaryCell(table, String.valueOf(idx), Element.ALIGN_LEFT, false);
                addSummaryCell(table, p.getPaymentDate() != null ? p.getPaymentDate().format(SHORT_DATE) : "-", Element.ALIGN_LEFT, false);
                String mode = p.getPaymentMode() != null ? p.getPaymentMode().getModeName() : "-";
                addSummaryCell(table, mode, Element.ALIGN_LEFT, false);
                addSummaryCell(table, p.getReferenceNo() != null ? p.getReferenceNo() : "-", Element.ALIGN_LEFT, false);
                addSummaryCell(table, fmtAmt(p.getAmount()), Element.ALIGN_RIGHT, false);
                totalReceived = totalReceived.add(p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO);
                idx++;
            }
        }

        // Total Received row
        addSummaryCell(table, "", Element.ALIGN_LEFT, false);
        addSummaryCell(table, "", Element.ALIGN_LEFT, false);
        addSummaryCell(table, "", Element.ALIGN_LEFT, false);
        addSummaryCell(table, "Total Received", Element.ALIGN_RIGHT, true);
        addSummaryCell(table, fmtAmt(totalReceived), Element.ALIGN_RIGHT, true);

        PdfPTable wrapper = new PdfPTable(1);
        wrapper.setWidthPercentage(100);
        PdfPCell titleCell = new PdfPCell(title);
        titleCell.setBorder(Rectangle.NO_BORDER);
        wrapper.addCell(titleCell);
        PdfPCell tableCell = new PdfPCell(table);
        tableCell.setBorder(Rectangle.NO_BORDER);
        wrapper.addCell(tableCell);
        return wrapper;
    }

    // ========== BALANCE ==========
    private PdfPTable buildBalance(Statement statement) throws DocumentException {
        Paragraph title = new Paragraph("BALANCE", F_SECTION);
        title.setSpacingBefore(5);
        title.setSpacingAfter(3);

        PdfPTable table = new PdfPTable(new float[]{2f, 1.5f});
        table.setWidthPercentage(100);

        addBalanceRow(table, "Net Amount", fmtAmt(statement.getTotalAmount()));
        addBalanceRow(table, "Rounding", "(-) " + fmtAmt(statement.getRoundingAmount() != null ? statement.getRoundingAmount().abs() : BigDecimal.ZERO));
        addBalanceRow(table, "Statement Amount", fmtAmt(statement.getNetAmount()));
        addBalanceRow(table, "Received", fmtAmt(statement.getReceivedAmount()));

        // Balance Due - prominent
        PdfPCell labelCell = new PdfPCell(new Phrase("BALANCE DUE", F_BALANCE_DUE_LABEL));
        labelCell.setBorder(Rectangle.TOP);
        labelCell.setBorderColor(BORDER);
        labelCell.setPaddingTop(4);
        labelCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        table.addCell(labelCell);

        String balanceStr = "\u20B9 " + fmtAmt(statement.getBalanceAmount());
        PdfPCell valueCell = new PdfPCell(new Phrase(balanceStr, F_BALANCE_DUE));
        valueCell.setBorder(Rectangle.TOP);
        valueCell.setBorderColor(BORDER);
        valueCell.setPaddingTop(4);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(valueCell);

        // Amount in words
        String words = amountInWords(statement.getBalanceAmount());
        PdfPCell wordsCell = new PdfPCell(new Phrase("In words: " + words, F_WORDS));
        wordsCell.setColspan(2);
        wordsCell.setBorder(Rectangle.NO_BORDER);
        wordsCell.setPaddingTop(2);
        table.addCell(wordsCell);

        PdfPTable wrapper = new PdfPTable(1);
        wrapper.setWidthPercentage(100);
        PdfPCell titleCell = new PdfPCell(title);
        titleCell.setBorder(Rectangle.NO_BORDER);
        wrapper.addCell(titleCell);
        PdfPCell tableCell = new PdfPCell(table);
        tableCell.setBorder(Rectangle.NO_BORDER);
        wrapper.addCell(tableCell);
        return wrapper;
    }

    private void addBalanceRow(PdfPTable table, String label, String value) {
        PdfPCell l = new PdfPCell(new Phrase(label, F_BALANCE_LABEL));
        l.setBorder(Rectangle.NO_BORDER);
        l.setPadding(2);
        l.setHorizontalAlignment(Element.ALIGN_LEFT);
        table.addCell(l);

        PdfPCell v = new PdfPCell(new Phrase(value, F_BALANCE_VALUE));
        v.setBorder(Rectangle.NO_BORDER);
        v.setPadding(2);
        v.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(v);
    }

    // ========== TERMS & SIGNATORY ==========
    private void addTermsAndSignatory(Document doc, Company company) throws DocumentException {
        PdfPTable footer = new PdfPTable(2);
        footer.setWidthPercentage(100);
        footer.setWidths(new float[]{60f, 40f});
        footer.setSpacingBefore(6);

        PdfPCell termsCell = new PdfPCell();
        termsCell.setBorder(Rectangle.NO_BORDER);
        termsCell.addElement(new Paragraph("Terms: Payment due within 15 days. Late payments attract 18% p.a. interest.", F_TERMS));
        termsCell.addElement(new Paragraph("Disputes within 7 days. Subject to Chennai jurisdiction. Computer-generated statement.", F_TERMS));
        footer.addCell(termsCell);

        String companyName = company != null && company.getName() != null ? company.getName() : "StopForFuel";
        PdfPCell sigCell = new PdfPCell();
        sigCell.setBorder(Rectangle.NO_BORDER);
        Paragraph forP = new Paragraph("For " + companyName.toUpperCase(), F_VALUE_BOLD);
        forP.setAlignment(Element.ALIGN_RIGHT);
        sigCell.addElement(forP);
        Paragraph sigP = new Paragraph("Authorized Signatory", F_SIGNATORY);
        sigP.setAlignment(Element.ALIGN_RIGHT);
        sigP.setSpacingBefore(20);
        sigCell.addElement(sigP);
        footer.addCell(sigCell);

        doc.add(footer);
    }

    // ========== HELPER METHODS ==========
    private void addCell(PdfPTable table, String text, Color bg, int align, boolean bold) {
        PdfPCell cell = new PdfPCell(new Phrase(text, bold ? F_TD_BOLD : F_TD));
        cell.setBackgroundColor(bg);
        cell.setPadding(3);
        cell.setHorizontalAlignment(align);
        cell.setBorderColor(LIGHT_BORDER);
        cell.setBorderWidth(0.5f);
        table.addCell(cell);
    }

    private void addSummaryCell(PdfPTable table, String text, int align, boolean bold) {
        PdfPCell cell = new PdfPCell(new Phrase(text, bold ? F_SUMMARY_BOLD : F_SUMMARY_TD));
        cell.setPadding(2.5f);
        cell.setHorizontalAlignment(align);
        cell.setBorderColor(LIGHT_BORDER);
        cell.setBorderWidth(0.5f);
        table.addCell(cell);
    }

    private String shortProductName(String name) {
        if (name == null) return "-";
        // Common abbreviations
        if (name.toLowerCase().contains("diesel")) return "HSD";
        if (name.toLowerCase().contains("petrol")) return "P";
        if (name.length() > 6) return name.substring(0, 6);
        return name;
    }

    private String fmtAmt(BigDecimal amount) {
        if (amount == null) return "0.00";
        return INR.format(amount);
    }

    private String fmt0(BigDecimal amount) {
        if (amount == null) return "0";
        return amount.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    private String fmtRate(BigDecimal rate) {
        if (rate == null) return "0.00";
        return rate.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    // ========== AMOUNT IN WORDS ==========
    private static final String[] ONES = {"", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
            "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"};
    private static final String[] TENS = {"", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"};

    private String amountInWords(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) return "Zero Only";
        long rupees = amount.abs().setScale(0, RoundingMode.HALF_UP).longValue();
        if (rupees == 0) return "Zero Only";

        StringBuilder sb = new StringBuilder("Rupees ");

        if (rupees >= 10000000) {
            sb.append(numberToWords((int)(rupees / 10000000))).append(" Crore ");
            rupees %= 10000000;
        }
        if (rupees >= 100000) {
            sb.append(numberToWords((int)(rupees / 100000))).append(" Lakh ");
            rupees %= 100000;
        }
        if (rupees >= 1000) {
            sb.append(numberToWords((int)(rupees / 1000))).append(" Thousand ");
            rupees %= 1000;
        }
        if (rupees >= 100) {
            sb.append(numberToWords((int)(rupees / 100))).append(" Hundred ");
            rupees %= 100;
        }
        if (rupees > 0) {
            if (sb.length() > 8) sb.append("and ");
            sb.append(numberToWords((int)rupees));
        }

        sb.append(" Only");
        return sb.toString().replaceAll("\\s+", " ").trim();
    }

    private String numberToWords(int n) {
        if (n < 20) return ONES[n];
        if (n < 100) return TENS[n / 10] + (n % 10 > 0 ? " " + ONES[n % 10] : "");
        return ONES[n / 100] + " Hundred" + (n % 100 > 0 ? " and " + numberToWords(n % 100) : "");
    }
}
