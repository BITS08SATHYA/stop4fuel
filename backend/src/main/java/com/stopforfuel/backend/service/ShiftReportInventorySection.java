package com.stopforfuel.backend.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.pdf.PdfPTable;
import com.stopforfuel.backend.dto.ShiftReportPrintData;
import com.stopforfuel.backend.dto.ShiftReportPrintData.*;

import java.math.BigDecimal;

import static com.stopforfuel.backend.service.ShiftReportPdfUtils.*;

/**
 * Renders the product inventory section of the shift report PDF:
 * product inventory table and stock position table.
 */
public class ShiftReportInventorySection {

    public void addProductInventory(Document doc, ShiftReportPrintData data) throws DocumentException {
        // Stock Summary (products with sales)
        if (!data.getStockSummary().isEmpty()) {
            doc.add(sectionHeader("PRODUCT INVENTORY (Non-Zero)"));
            PdfPTable table = new PdfPTable(new float[]{0.4f, 2.5f, 0.8f, 0.8f, 0.8f, 0.8f, 0.8f, 0.8f, 1.2f});
            table.setWidthPercentage(100);
            table.setSpacingAfter(1);

            addHeaderCell(table, "#");
            addHeaderCell(table, "PRODUCT");
            addHeaderCell(table, "OPEN");
            addHeaderCell(table, "RCPT");
            addHeaderCell(table, "TOTAL");
            addHeaderCell(table, "SALE");
            addHeaderCell(table, "CLOSE");
            addHeaderCell(table, "RATE");
            addHeaderCell(table, "AMT");

            int idx = 1;
            BigDecimal grandTotal = BigDecimal.ZERO;
            for (StockSummaryRow row : data.getStockSummary()) {
                addCellRight(table, String.valueOf(idx++), SMALL_FONT);
                addCellLeft(table, row.getProductName(), SMALL_FONT);
                addCellRight(table, fmt0(row.getOpenStock()), SMALL_FONT);
                addCellRight(table, fmt0(row.getReceipt()), SMALL_FONT);
                addCellRight(table, fmt0(row.getTotalStock()), SMALL_FONT);
                addCellRight(table, fmt0(row.getSales()), SMALL_FONT);
                double closeQty = (row.getTotalStock() != null ? row.getTotalStock() : 0)
                        - (row.getSales() != null ? row.getSales() : 0);
                addCellRight(table, fmt0(closeQty), SMALL_FONT);
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
            table.setWidthPercentage(100);
            table.setSpacingAfter(1);

            addHeaderCell(table, "PRODUCT");
            addHeaderCell(table, "GODOWN");
            addHeaderCell(table, "CASHIER");
            addHeaderCell(table, "TOTAL");

            for (StockPositionRow row : data.getStockPosition()) {
                com.lowagie.text.Font f = row.isLowStock() ? SMALL_BOLD : SMALL_FONT;
                addCellLeft(table, row.getProductName() + (row.isLowStock() ? " [LOW]" : ""), f);
                addCellRight(table, fmt0(row.getGodownStock()), f);
                addCellRight(table, fmt0(row.getCashierStock()), f);
                addCellRight(table, fmt0(row.getTotalStock()), f);
            }

            doc.add(table);
        }
    }
}
