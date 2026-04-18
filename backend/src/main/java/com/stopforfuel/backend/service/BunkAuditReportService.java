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
import com.stopforfuel.backend.dto.BunkAuditReport;
import com.stopforfuel.backend.entity.Company;
import com.stopforfuel.backend.exception.ReportGenerationException;
import com.stopforfuel.backend.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Produces a one-page (or multi-page) P&L PDF for the bunk audit.
 * Renders: company header, verdict strip, inputs + outputs tables,
 * per-product margin, tank-vs-meter variance.
 */
@Service
@RequiredArgsConstructor
public class BunkAuditReportService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy, hh:mm a");
    private static final java.text.DecimalFormat NUM_FMT = new java.text.DecimalFormat("#,##0.00");
    private static final java.text.DecimalFormat QTY_FMT = new java.text.DecimalFormat("#,##0.00");

    private final BunkAuditService auditService;
    private final CompanyRepository companyRepository;

    public byte[] generatePdf(LocalDate fromDate, LocalDate toDate, BunkAuditReport.Granularity granularity) {
        try {
            BunkAuditReport report = auditService.compute(fromDate, toDate, granularity);
            Company company = companyRepository.findAll().stream().findFirst().orElse(null);
            String companyName = company != null && company.getName() != null ? company.getName() : "StopForFuel";
            String companyAddress = company != null && company.getAddress() != null ? company.getAddress() : "";
            String companyPhone = company != null && company.getPhone() != null ? company.getPhone() : "";

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, 25, 25, 25, 25);
            PdfWriter.getInstance(doc, out);
            doc.open();

            Font titleFont = new Font(Font.HELVETICA, 16, Font.BOLD, new Color(51, 51, 51));
            Font subFont = new Font(Font.HELVETICA, 8, Font.NORMAL, new Color(102, 102, 102));
            Font sectionFont = new Font(Font.HELVETICA, 11, Font.BOLD, new Color(230, 81, 0));
            Font headerFont = new Font(Font.HELVETICA, 8, Font.BOLD, Color.BLACK);
            Font cellFont = new Font(Font.HELVETICA, 8, Font.NORMAL, Color.BLACK);
            Font boldCellFont = new Font(Font.HELVETICA, 8, Font.BOLD, Color.BLACK);
            Font amountFont = new Font(Font.HELVETICA, 8, Font.BOLD, new Color(230, 81, 0));
            Font greenFont = new Font(Font.HELVETICA, 8, Font.BOLD, new Color(16, 185, 129));
            Font redFont = new Font(Font.HELVETICA, 8, Font.BOLD, new Color(239, 68, 68));
            Font footerFont = new Font(Font.HELVETICA, 6, Font.NORMAL, new Color(153, 153, 153));
            Font verdictLabelFont = new Font(Font.HELVETICA, 9, Font.BOLD, new Color(51, 51, 51));
            Font verdictValueFont = new Font(Font.HELVETICA, 14, Font.BOLD, new Color(230, 81, 0));

            // --- Company Header ---
            Paragraph head = new Paragraph(companyName, titleFont);
            head.setAlignment(Element.ALIGN_CENTER);
            doc.add(head);
            if (!companyAddress.isEmpty() || !companyPhone.isEmpty()) {
                Paragraph addr = new Paragraph(companyAddress + (companyPhone.isEmpty() ? "" : " | " + companyPhone), subFont);
                addr.setAlignment(Element.ALIGN_CENTER);
                doc.add(addr);
            }
            doc.add(Chunk.NEWLINE);

            Paragraph title = new Paragraph("Bunk Audit — Profit & Loss", sectionFont);
            title.setAlignment(Element.ALIGN_CENTER);
            doc.add(title);

            String period = fromDate.equals(toDate)
                    ? "Date: " + fromDate.format(DATE_FMT)
                    : "Period: " + fromDate.format(DATE_FMT) + " to " + toDate.format(DATE_FMT);
            Paragraph meta = new Paragraph(period + " · " + report.getShiftCount()
                    + " shift" + (report.getShiftCount() == 1 ? "" : "s"), subFont);
            meta.setAlignment(Element.ALIGN_CENTER);
            doc.add(meta);

            doc.add(Chunk.NEWLINE);

            // --- Verdict Strip (4 boxes) ---
            BunkAuditReport.Profitability p = report.getProfitability();
            boolean isProfit = p.getNetProfit().signum() >= 0;
            Font verdictValue = new Font(Font.HELVETICA, 14, Font.BOLD,
                    isProfit ? new Color(16, 185, 129) : new Color(239, 68, 68));

            BunkAuditReport.CashFlow cf = report.getCashFlow();
            boolean cashPositive = cf.getNetPosition().signum() >= 0;
            Font cashValue = new Font(Font.HELVETICA, 14, Font.BOLD,
                    cashPositive ? new Color(16, 185, 129) : new Color(239, 68, 68));

            // --- Dual verdict strip: Net Cash Position | Net Profit ---
            PdfPTable verdict = new PdfPTable(2);
            verdict.setWidthPercentage(100);
            verdict.setWidths(new float[]{50, 50});
            addVerdictBox(verdict, "Net Cash Position", formatRupee(cf.getNetPosition()),
                    "IN − OUT", verdictLabelFont, cashValue, subFont);
            addVerdictBox(verdict, "Net Profit (accrual)", formatRupee(p.getNetProfit()),
                    p.getMarginPct() + "% margin", verdictLabelFont, verdictValue, subFont);
            doc.add(verdict);
            doc.add(Chunk.NEWLINE);

            // --- Cash Flow panel: IN / OUT / Internal Transfers ---
            PdfPTable flow = new PdfPTable(2);
            flow.setWidthPercentage(100);
            flow.setWidths(new float[]{50, 50});
            flow.addCell(buildCashInCell(cf.getIn(), sectionFont, cellFont, boldCellFont, amountFont));
            flow.addCell(buildCashOutCell(cf.getOut(), sectionFont, cellFont, boldCellFont, amountFont));
            doc.add(flow);
            doc.add(Chunk.NEWLINE);

            if (cf.getInternalTransfers().getManagementAdvance().signum() > 0
                    || cf.getInternalTransfers().getCashAdvanceBankDeposit().signum() > 0) {
                Paragraph s = new Paragraph("Internal Transfers (stay with the business — excluded from IN/OUT)", sectionFont);
                s.setSpacingBefore(6);
                doc.add(s);
                PdfPTable xfer = new PdfPTable(2);
                xfer.setWidthPercentage(60);
                xfer.setHorizontalAlignment(Element.ALIGN_LEFT);
                xfer.setWidths(new float[]{65, 35});
                addCell(xfer, "Management Advance", cellFont, Element.ALIGN_LEFT, Color.WHITE, new Color(238, 238, 238));
                addCell(xfer, formatRupee(cf.getInternalTransfers().getManagementAdvance()), amountFont, Element.ALIGN_RIGHT, Color.WHITE, new Color(238, 238, 238));
                addCell(xfer, "Cash Advance → Bank Deposit", cellFont, Element.ALIGN_LEFT, Color.WHITE, new Color(238, 238, 238));
                addCell(xfer, formatRupee(cf.getInternalTransfers().getCashAdvanceBankDeposit()), amountFont, Element.ALIGN_RIGHT, Color.WHITE, new Color(238, 238, 238));
                doc.add(xfer);
                doc.add(Chunk.NEWLINE);
            }

            // --- Per-Product Margin ---
            List<BunkAuditReport.ProductSale> allSales = report.getProductSales() != null
                    ? report.getProductSales() : java.util.Collections.emptyList();
            if (!allSales.isEmpty()) {
                Paragraph s = new Paragraph("Per-Product Margin", sectionFont);
                s.setSpacingBefore(8);
                doc.add(s);

                float[] widths = {4f, 24f, 13f, 16f, 16f, 15f, 12f};
                PdfPTable table = new PdfPTable(widths);
                table.setWidthPercentage(100);
                table.setSpacingBefore(4);
                addHeaderRow(table, new String[]{"#", "Product", "Quantity", "Revenue", "COGS", "Margin", "Margin %"}, headerFont);

                int rowNum = 0;
                BigDecimal totRev = BigDecimal.ZERO, totCogs = BigDecimal.ZERO, totMargin = BigDecimal.ZERO;
                double totQty = 0;
                for (BunkAuditReport.ProductSale ps : allSales) {
                    rowNum++;
                    Color rowBg = (rowNum % 2 == 0) ? new Color(250, 250, 250) : Color.WHITE;
                    Color border = new Color(238, 238, 238);
                    boolean pos = ps.getMargin().signum() >= 0;

                    addCell(table, String.valueOf(rowNum), cellFont, Element.ALIGN_CENTER, rowBg, border);
                    addCell(table, ps.getProductName(), boldCellFont, Element.ALIGN_LEFT, rowBg, border);
                    addCell(table, QTY_FMT.format(ps.getQuantity()), cellFont, Element.ALIGN_RIGHT, rowBg, border);
                    addCell(table, formatRupee(ps.getRevenue()), cellFont, Element.ALIGN_RIGHT, rowBg, border);
                    addCell(table, formatRupee(ps.getCogs()), cellFont, Element.ALIGN_RIGHT, rowBg, border);
                    addCell(table, formatRupee(ps.getMargin()), pos ? greenFont : redFont, Element.ALIGN_RIGHT, rowBg, border);
                    addCell(table, ps.getMarginPct() + "%", pos ? greenFont : redFont, Element.ALIGN_RIGHT, rowBg, border);

                    totQty += ps.getQuantity();
                    totRev = totRev.add(ps.getRevenue());
                    totCogs = totCogs.add(ps.getCogs());
                    totMargin = totMargin.add(ps.getMargin());
                }
                Color totBg = new Color(232, 232, 232);
                Color totBorder = new Color(204, 204, 204);
                addCell(table, "", boldCellFont, Element.ALIGN_CENTER, totBg, totBorder);
                addCell(table, "TOTAL", boldCellFont, Element.ALIGN_LEFT, totBg, totBorder);
                addCell(table, QTY_FMT.format(totQty), boldCellFont, Element.ALIGN_RIGHT, totBg, totBorder);
                addCell(table, formatRupee(totRev), boldCellFont, Element.ALIGN_RIGHT, totBg, totBorder);
                addCell(table, formatRupee(totCogs), boldCellFont, Element.ALIGN_RIGHT, totBg, totBorder);
                addCell(table, formatRupee(totMargin), amountFont, Element.ALIGN_RIGHT, new Color(255, 243, 224), totBorder);
                BigDecimal marginPct = totRev.signum() > 0
                        ? totMargin.multiply(BigDecimal.valueOf(100)).divide(totRev, 2, java.math.RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
                addCell(table, marginPct + "%", amountFont, Element.ALIGN_RIGHT, new Color(255, 243, 224), totBorder);

                doc.add(table);
                doc.add(Chunk.NEWLINE);
            }

            // --- Tank vs Meter Variance ---
            if (!report.getVariance().isEmpty()) {
                Paragraph s = new Paragraph("Tank vs Meter Variance (shrinkage > 0.5% flagged)", sectionFont);
                s.setSpacingBefore(8);
                doc.add(s);

                float[] widths = {5f, 25f, 18f, 18f, 17f, 17f};
                PdfPTable table = new PdfPTable(widths);
                table.setWidthPercentage(100);
                table.setSpacingBefore(4);
                addHeaderRow(table, new String[]{"#", "Product", "Tank Sale (L)", "Meter Sale (L)", "Shrinkage (L)", "Shrinkage %"}, headerFont);

                int rowNum = 0;
                for (BunkAuditReport.ProductVariance v : report.getVariance()) {
                    rowNum++;
                    Color rowBg = v.isFlagged()
                            ? new Color(254, 226, 226)
                            : (rowNum % 2 == 0 ? new Color(250, 250, 250) : Color.WHITE);
                    Color border = new Color(238, 238, 238);
                    Font flagFont = v.isFlagged() ? redFont : cellFont;

                    addCell(table, String.valueOf(rowNum), cellFont, Element.ALIGN_CENTER, rowBg, border);
                    addCell(table, v.getProductName(), boldCellFont, Element.ALIGN_LEFT, rowBg, border);
                    addCell(table, QTY_FMT.format(v.getExpectedLitres()), cellFont, Element.ALIGN_RIGHT, rowBg, border);
                    addCell(table, QTY_FMT.format(v.getActualLitres()), cellFont, Element.ALIGN_RIGHT, rowBg, border);
                    addCell(table, QTY_FMT.format(v.getShrinkageLitres()), flagFont, Element.ALIGN_RIGHT, rowBg, border);
                    BigDecimal pct = v.getShrinkagePct() != null
                            ? v.getShrinkagePct().multiply(BigDecimal.valueOf(100))
                            : BigDecimal.ZERO;
                    addCell(table, pct + "%", flagFont, Element.ALIGN_RIGHT, rowBg, border);
                }
                doc.add(table);
            }

            doc.add(Chunk.NEWLINE);
            Paragraph footer = new Paragraph("Report generated on " + LocalDateTime.now().format(DATETIME_FMT), footerFont);
            footer.setAlignment(Element.ALIGN_CENTER);
            doc.add(footer);

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new ReportGenerationException("Failed to generate Bunk Audit PDF report", e);
        }
    }

    // =============================== cell builders ===============================

    private PdfPCell buildCashInCell(BunkAuditReport.CashIn in,
                                      Font sectionFont, Font cellFont, Font boldCellFont, Font amountFont) {
        PdfPCell cell = new PdfPCell();
        cell.setPadding(6);
        cell.setBorderColor(new Color(224, 224, 224));
        Paragraph h = new Paragraph("Money IN", sectionFont);
        h.setSpacingAfter(4);
        cell.addElement(h);
        cell.addElement(kv("  Cash Invoices", formatRupee(in.getCashInvoices()), cellFont, amountFont));
        cell.addElement(kv("  Bill Payments", formatRupee(in.getBillPayments()), cellFont, amountFont));
        cell.addElement(kv("  Statement Payments", formatRupee(in.getStatementPayments()), cellFont, amountFont));
        cell.addElement(kv("  External Inflow", formatRupee(in.getExternalInflow()), cellFont, amountFont));
        return cell;
    }

    private PdfPCell buildCashOutCell(BunkAuditReport.CashOut out,
                                       Font sectionFont, Font cellFont, Font boldCellFont, Font amountFont) {
        PdfPCell cell = new PdfPCell();
        cell.setPadding(6);
        cell.setBorderColor(new Color(224, 224, 224));
        Paragraph h = new Paragraph("Money OUT", sectionFont);
        h.setSpacingAfter(4);
        cell.addElement(h);
        cell.addElement(kv("  Credit Invoices", formatRupee(out.getCreditInvoices()), cellFont, amountFont));
        if (out.getEAdvances() != null && !out.getEAdvances().isEmpty()) {
            cell.addElement(subHeader("  E-Advances", boldCellFont));
            for (BunkAuditReport.AmountByMode a : out.getEAdvances()) {
                cell.addElement(kv("    " + a.getMode(), formatRupee(a.getAmount()), cellFont, amountFont));
            }
        }
        if (out.getExpenses() != null && !out.getExpenses().isEmpty()) {
            for (BunkAuditReport.AmountByType a : out.getExpenses()) {
                cell.addElement(kv("  " + a.getType(), formatRupee(a.getAmount()), cellFont, amountFont));
            }
        }
        cell.addElement(kv("  Station Expenses", formatRupee(out.getStationExpenses()), cellFont, amountFont));
        cell.addElement(kv("  Incentives", formatRupee(out.getIncentives()), cellFont, amountFont));
        cell.addElement(kv("  Salary Advance", formatRupee(out.getSalaryAdvance()), cellFont, amountFont));
        cell.addElement(kv("  Cash Advance (spent)", formatRupee(out.getCashAdvanceSpent()), cellFont, amountFont));
        cell.addElement(kv("  Inflow Repayments", formatRupee(out.getInflowRepayments()), cellFont, amountFont));
        if (out.getTestQuantity() != null && out.getTestQuantity().getLitres() > 0) {
            cell.addElement(kv("  Test Quantity (" + QTY_FMT.format(out.getTestQuantity().getLitres()) + " L, info)",
                    formatRupee(out.getTestQuantity().getAmount()), cellFont, amountFont));
        }
        return cell;
    }

    // =============================== helpers ===============================

    private Paragraph subHeader(String text, Font font) {
        Paragraph p = new Paragraph(text, font);
        p.setSpacingBefore(4);
        p.setSpacingAfter(2);
        return p;
    }

    private Paragraph kv(String key, String value, Font keyFont, Font valueFont) {
        Paragraph p = new Paragraph();
        p.add(new Chunk(key, keyFont));
        p.add(new Chunk("   " + value, valueFont));
        return p;
    }

    private void addVerdictBox(PdfPTable table, String label, String value, String sub,
                               Font labelFont, Font valueFont, Font subFont) {
        PdfPCell cell = new PdfPCell();
        cell.setBorderColor(new Color(224, 224, 224));
        cell.setPadding(8);
        cell.setBackgroundColor(new Color(250, 250, 250));

        Paragraph l = new Paragraph(label, labelFont);
        l.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(l);

        Paragraph v = new Paragraph(value, valueFont);
        v.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(v);

        if (sub != null && !sub.isEmpty()) {
            Paragraph s = new Paragraph(sub, subFont);
            s.setAlignment(Element.ALIGN_CENTER);
            cell.addElement(s);
        }
        table.addCell(cell);
    }

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

    private String formatRupee(BigDecimal value) {
        if (value == null) return "\u20B90.00";
        return "\u20B9" + NUM_FMT.format(value);
    }

    private <T> List<T> combine(List<T> a, List<T> b) {
        java.util.ArrayList<T> all = new java.util.ArrayList<>(a != null ? a.size() : 0);
        if (a != null) all.addAll(a);
        if (b != null) all.addAll(b);
        return all;
    }
}
