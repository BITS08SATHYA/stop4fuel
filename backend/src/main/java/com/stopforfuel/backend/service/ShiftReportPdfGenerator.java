package com.stopforfuel.backend.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.stopforfuel.backend.dto.ShiftReportPrintData;
import com.stopforfuel.backend.entity.ShiftClosingReport;
import com.stopforfuel.backend.exception.ReportGenerationException;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;

import static com.stopforfuel.backend.service.ShiftReportPdfUtils.*;

/**
 * Orchestrates shift report PDF generation by delegating to focused section generators.
 *
 * <p>Section generators:
 * <ul>
 *   <li>{@link ShiftReportHeaderSection} — Company header, page headers, footer</li>
 *   <li>{@link ShiftReportSalesSection} — Meterwise, gross/net sales, tankwise, sales diff, cash bills, stock ref</li>
 *   <li>{@link ShiftReportFinancialSection} — Revenue, advances, turnover box, income/credit bills, advance details</li>
 *   <li>{@link ShiftReportInventorySection} — Product inventory and stock position (page 3)</li>
 * </ul>
 *
 * <p>Shared PDF drawing utilities live in {@link ShiftReportPdfUtils}.
 */
@Component
public class ShiftReportPdfGenerator {

    private final ShiftReportHeaderSection headerSection = new ShiftReportHeaderSection();
    private final ShiftReportSalesSection salesSection = new ShiftReportSalesSection();
    private final ShiftReportFinancialSection financialSection = new ShiftReportFinancialSection();
    private final ShiftReportInventorySection inventorySection = new ShiftReportInventorySection();

    public byte[] generate(ShiftReportPrintData data, ShiftClosingReport report) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 12, 12, 10, 10);

        try {
            com.lowagie.text.pdf.PdfWriter.getInstance(document, baos);
            document.open();

            // ===== PAGE 1: Two-column layout =====
            headerSection.addPageOneHeader(document, data);
            addPageOneBody(document, data, report);
            headerSection.addPageOneFooter(document, data);

            // ===== PAGE 2: Advance details (left) + Expenses/Inventory (right) =====
            document.newPage();
            headerSection.addPageTwoHeader(document, data);
            financialSection.addPageTwoBody(document, data, report, inventorySection);

            // Page 2 footer
            PdfPTable pg2Footer = new PdfPTable(2);
            pg2Footer.setWidthPercentage(100);
            pg2Footer.setSpacingBefore(3);
            PdfPCell genCell = new PdfPCell(new Phrase("Generated: " + LocalDateTime.now().format(DT_FMT), SMALL_FONT));
            genCell.setBorder(Rectangle.TOP);
            genCell.setPadding(2);
            pg2Footer.addCell(genCell);
            PdfPCell pgCell = new PdfPCell(new Phrase("Page 2 of 2", SMALL_FONT));
            pgCell.setBorder(Rectangle.TOP);
            pgCell.setPadding(2);
            pgCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            pg2Footer.addCell(pgCell);
            document.add(pg2Footer);

            document.close();
        } catch (DocumentException e) {
            throw new ReportGenerationException("Failed to generate shift report PDF", e);
        }

        return baos.toByteArray();
    }

    private void addPageOneBody(Document doc, ShiftReportPrintData data, ShiftClosingReport report) throws DocumentException {
        // Two-column outer table
        PdfPTable outer = new PdfPTable(2);
        outer.setWidthPercentage(100);
        outer.setWidths(new float[]{50, 50});

        // LEFT COLUMN
        PdfPCell leftCell = new PdfPCell();
        leftCell.setBorder(Rectangle.NO_BORDER);
        leftCell.setPadding(0);
        leftCell.setPaddingRight(3);

        salesSection.addMeterwise(leftCell, data);
        salesSection.addGrossNetSales(leftCell, data);
        salesSection.addTankwise(leftCell, data);
        salesSection.addSalesDifference(leftCell, data);
        salesSection.addCashBillSales(leftCell, data);
        salesSection.addStockReference(leftCell, data);

        outer.addCell(leftCell);

        // RIGHT COLUMN
        PdfPCell rightCell = new PdfPCell();
        rightCell.setBorder(Rectangle.NO_BORDER);
        rightCell.setPadding(0);
        rightCell.setPaddingLeft(3);

        financialSection.addRevenue(rightCell, data, report);
        financialSection.addAdvances(rightCell, data, report);
        financialSection.addTurnoverBalanceBox(rightCell, report);
        financialSection.addIncomeBills(rightCell, data);
        financialSection.addCreditBillsSummary(rightCell, data);

        outer.addCell(rightCell);

        doc.add(outer);
    }
}
