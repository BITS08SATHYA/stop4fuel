package com.stopforfuel.backend.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.stopforfuel.backend.dto.ShiftReportPrintData;
import com.stopforfuel.backend.dto.ShiftReportPrintData.*;

import java.math.BigDecimal;

import static com.stopforfuel.backend.service.ShiftReportPdfUtils.*;

/**
 * Renders the product inventory page (page 3) of the shift report PDF:
 * product inventory table and stock position table.
 */
public class ShiftReportInventorySection {

    public void addProductInventoryPage(Document doc, ShiftReportPrintData data) throws DocumentException {
        // Stock Summary (products with sales > 0)
        if (!data.getStockSummary().isEmpty()) {
            doc.add(sectionHeader("PRODUCT INVENTORY"));
            PdfPTable table = new PdfPTable(new float[]{0.4f, 2.5f, 1f, 1f, 1f, 1f, 1f, 1f, 1.2f});
            table.setWidthPercentage(100);
            table.setSpacingAfter(1);

            addHeaderCell(table, "#");
            addHeaderCell(table, "PRODUCT NAME");
            addHeaderCell(table, "OPEN");
            addHeaderCell(table, "RCPT");
            addHeaderCell(table, "TOTAL");
            addHeaderCell(table, "SALES");
            addHeaderCell(table, "CLOSE");
            addHeaderCell(table, "RATE");
            addHeaderCell(table, "AMOUNT");

            int idx = 1;
            BigDecimal grandTotal = BigDecimal.ZERO;
            for (StockSummaryRow row : data.getStockSummary()) {
                addCellRight(table, String.valueOf(idx++), SMALL_FONT);
                addCellLeft(table, row.getProductName(), SMALL_FONT);
                addCellRight(table, fmt2(row.getOpenStock()), SMALL_FONT);
                addCellRight(table, fmt2(row.getReceipt()), SMALL_FONT);
                addCellRight(table, fmt2(row.getTotalStock()), SMALL_FONT);
                addCellRight(table, fmt2(row.getSales()), SMALL_FONT);
                double closeQty = (row.getTotalStock() != null ? row.getTotalStock() : 0)
                        - (row.getSales() != null ? row.getSales() : 0);
                addCellRight(table, fmt2(closeQty), SMALL_FONT);
                addCellRight(table, row.getRate() != null ? row.getRate().toPlainString() : "-", SMALL_FONT);
                addCellRight(table, fmtComma(row.getAmount()), SMALL_FONT);
                if (row.getAmount() != null) grandTotal = grandTotal.add(row.getAmount());
            }

            // Grand total
            addCellRight(table, "", SMALL_BOLD);
            addCellLeft(table, "TOTAL", SMALL_BOLD);
            for (int i = 0; i < 6; i++) addCellRight(table, "", SMALL_BOLD);
            addCellRight(table, fmtComma(grandTotal), SMALL_BOLD);

            doc.add(table);
        }

        // Stock Position (non-fuel godown+cashier)
        if (!data.getStockPosition().isEmpty()) {
            doc.add(sectionHeader("STOCK POSITION"));
            PdfPTable table = new PdfPTable(new float[]{3f, 1.5f, 1.5f, 1.5f});
            table.setWidthPercentage(70);
            table.setSpacingAfter(1);

            addHeaderCell(table, "PRODUCT");
            addHeaderCell(table, "GODOWN");
            addHeaderCell(table, "CASHIER");
            addHeaderCell(table, "TOTAL");

            for (StockPositionRow row : data.getStockPosition()) {
                com.lowagie.text.Font f = row.isLowStock() ? SMALL_BOLD : SMALL_FONT;
                addCellLeft(table, row.getProductName() + (row.isLowStock() ? " [LOW]" : ""), f);
                addCellRight(table, fmt2(row.getGodownStock()), f);
                addCellRight(table, fmt2(row.getCashierStock()), f);
                addCellRight(table, fmt2(row.getTotalStock()), f);
            }

            doc.add(table);
        }

        // Generation timestamp
        Paragraph gen = new Paragraph("Generated: " + java.time.LocalDateTime.now().format(DT_FMT), SMALL_FONT);
        gen.setSpacingBefore(3);
        doc.add(gen);
    }
}
