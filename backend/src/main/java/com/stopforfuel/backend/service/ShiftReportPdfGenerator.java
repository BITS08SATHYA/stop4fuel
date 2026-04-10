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
 * <p>Page 1: Operations &amp; Financial Summary (sales left, financials+advances+inventory right)
 * <p>Page 2: Invoice &amp; Payment Details (cash+credit bills left, income bills right)
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

            // ===== PAGE 1: Operations & Financial Summary =====
            headerSection.addPageOneHeader(document, data);
            addPageOneBody(document, data, report);
            headerSection.addPageOneFooter(document, data);

            // ===== PAGE 2: Invoice & Payment Details =====
            document.newPage();
            headerSection.addPageTwoHeader(document, data);
            addPageTwoInvoiceBody(document, data);

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
        PdfPTable outer = new PdfPTable(2);
        outer.setWidthPercentage(100);
        outer.setWidths(new float[]{50, 50});

        // LEFT COLUMN — Sales & Stock
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

        // RIGHT COLUMN — Financials, Advances, Inventory
        PdfPCell rightCell = new PdfPCell();
        rightCell.setBorder(Rectangle.NO_BORDER);
        rightCell.setPadding(0);
        rightCell.setPaddingLeft(3);

        financialSection.addRevenue(rightCell, data, report);
        financialSection.addAdvances(rightCell, data, report);
        financialSection.addTurnoverBalanceBox(rightCell, report);
        financialSection.addOperationalAdvanceDetailsCompact(rightCell, data);
        inventorySection.addProductInventory(rightCell, data);

        outer.addCell(rightCell);

        doc.add(outer);
    }

    private void addPageTwoInvoiceBody(Document doc, ShiftReportPrintData data) throws DocumentException {
        PdfPTable outer = new PdfPTable(2);
        outer.setWidthPercentage(100);
        outer.setWidths(new float[]{45, 55});

        // LEFT COLUMN — Summaries + Payments
        PdfPCell leftCell = new PdfPCell();
        leftCell.setBorder(Rectangle.NO_BORDER);
        leftCell.setPadding(0);
        leftCell.setPaddingRight(3);

        financialSection.addCashBillsSummary(leftCell, data);
        financialSection.addEAdvanceSummary(leftCell, data);
        financialSection.addSalesSummary(leftCell, data);
        financialSection.addIncomeBills(leftCell, data);

        outer.addCell(leftCell);

        // RIGHT COLUMN — Credit Bills (dedicated, full list)
        PdfPCell rightCell = new PdfPCell();
        rightCell.setBorder(Rectangle.NO_BORDER);
        rightCell.setPadding(0);
        rightCell.setPaddingLeft(3);

        financialSection.addCreditBillsSummary(rightCell, data);

        outer.addCell(rightCell);

        doc.add(outer);
    }
}
