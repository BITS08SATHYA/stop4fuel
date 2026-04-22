package com.stopforfuel.backend.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;
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
 * <p>Legal-paper single-column layout. Sections flow top-to-bottom and iText paginates
 * naturally — busier shifts grow to 3+ pages, quieter shifts compact to 2. A compact
 * running header (company / shift / cashier) and a "Page X of N — Generated ..." footer
 * appear on every page via {@link PageChromeEvent}.
 */
@Component
public class ShiftReportPdfGenerator {

    private final ShiftReportHeaderSection headerSection = new ShiftReportHeaderSection();
    private final ShiftReportSalesSection salesSection = new ShiftReportSalesSection();
    private final ShiftReportFinancialSection financialSection = new ShiftReportFinancialSection();
    private final ShiftReportInventorySection inventorySection = new ShiftReportInventorySection();

    public byte[] generate(ShiftReportPrintData data, ShiftClosingReport report) {
        return generate(data, report, "LEGAL");
    }

    public byte[] generate(ShiftReportPrintData data, ShiftClosingReport report, String paperSize) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Rectangle pageSize = "A4".equalsIgnoreCase(paperSize) ? PageSize.A4 : PageSize.LEGAL;
        // Top margin is generous to leave room for the running header drawn by PageChromeEvent.
        Document document = new Document(pageSize, 20, 20, 40, 28);

        try {
            PdfWriter writer = PdfWriter.getInstance(document, baos);
            writer.setPageEvent(new PageChromeEvent(data));
            document.open();

            // Page 1 starts with the full company header. Subsequent pages get only the
            // compact running header drawn by the page event.
            headerSection.addPageOneHeader(document, data);

            // Section 1 — Sales Operations
            salesSection.addMeterwise(document, data);
            salesSection.addTankwise(document, data);
            salesSection.addGrossNetSales(document, data);
            salesSection.addSalesReconciliation(document, data);
            salesSection.addSalesDifference(document, data);

            // Section 2 — Financial Summary
            financialSection.addRevenue(document, data, report);
            financialSection.addAdvances(document, data, report);
            financialSection.addTurnoverBalanceBox(document, report);
            salesSection.addCashBillSales(document, data);

            // Section 3 — Inventory & Stock
            salesSection.addStockReference(document, data);
            inventorySection.addProductInventory(document, data);
            financialSection.addSalesSummary(document, data);

            // Section 4 — Invoice & Payment Details
            financialSection.addCashBillsSummary(document, data);
            financialSection.addEAdvanceSummary(document, data);
            financialSection.addIncomeBills(document, data);
            financialSection.addCreditBillsSummary(document, data);

            document.close();
        } catch (DocumentException e) {
            throw new ReportGenerationException("Failed to generate shift report PDF", e);
        }

        return baos.toByteArray();
    }

    /**
     * Draws a compact running header on every page (company · shift · cashier) and
     * a "Page X of N — Generated ts" footer. The total page count is written after
     * the document is closed via a PdfTemplate placeholder — standard iText pattern.
     */
    private static final class PageChromeEvent extends PdfPageEventHelper {

        private final ShiftReportPrintData data;
        private BaseFont baseFont;
        private BaseFont baseBold;
        private PdfTemplate totalPagesPh;
        private String generatedAt;

        PageChromeEvent(ShiftReportPrintData data) {
            this.data = data;
        }

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            try {
                baseFont = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
                baseBold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
                totalPagesPh = writer.getDirectContent().createTemplate(40, 12);
                generatedAt = LocalDateTime.now().format(DT_FMT);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            // Skip the running header on page 1 — page 1 already has the big company header.
            PdfContentByte cb = writer.getDirectContent();
            Rectangle page = document.getPageSize();
            float left = document.leftMargin();
            float right = page.getWidth() - document.rightMargin();
            float top = page.getHeight() - 20;
            float bottom = 20;

            cb.saveState();

            if (writer.getPageNumber() > 1) {
                String company = data.getCompanyName() != null ? data.getCompanyName().toUpperCase() : "";
                String shift = "SHIFT CLOSING REPORT — Shift #" + data.getShiftId()
                        + " — Cashier: " + (data.getEmployeeName() != null ? data.getEmployeeName() : "-");
                // Left: company | Right: shift + cashier
                ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,
                        new Phrase(company, new Font(baseBold, 9)), left, top, 0);
                ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT,
                        new Phrase(shift, new Font(baseFont, 8)), right, top, 0);
                // Thin separator under the header
                cb.setLineWidth(0.5f);
                cb.moveTo(left, top - 4);
                cb.lineTo(right, top - 4);
                cb.stroke();
            }

            // Footer: left = Generated, right = "Page X of [template]"
            ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,
                    new Phrase("Generated: " + generatedAt, new Font(baseFont, 7.5f)),
                    left, bottom, 0);

            String pageLabel = "Page " + writer.getPageNumber() + " of ";
            float labelWidth = baseFont.getWidthPoint(pageLabel, 8);
            ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,
                    new Phrase(pageLabel, new Font(baseFont, 8)),
                    right - labelWidth - 20, bottom, 0);
            cb.addTemplate(totalPagesPh, right - 20, bottom - 2);

            // Thin separator above the footer
            cb.setLineWidth(0.5f);
            cb.moveTo(left, bottom + 8);
            cb.lineTo(right, bottom + 8);
            cb.stroke();

            cb.restoreState();
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            // Write the final total-pages value into the placeholder. getPageNumber() at close
            // time is the next page to be written, hence -1 for the last actual page count.
            int total = writer.getPageNumber() - 1;
            totalPagesPh.beginText();
            totalPagesPh.setFontAndSize(baseFont, 8);
            totalPagesPh.showText(String.valueOf(total));
            totalPagesPh.endText();
        }
    }
}
