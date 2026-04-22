package com.stopforfuel.backend.service;

import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.stopforfuel.backend.dto.ShiftReportPrintData;
import com.stopforfuel.backend.dto.ShiftReportPrintData.*;

import java.math.BigDecimal;
import java.util.*;

import static com.stopforfuel.backend.service.ShiftReportPdfUtils.*;

/**
 * Renders the sales-related sections (left column of page 1):
 * meterwise readings, gross/net sales, tankwise, sales difference,
 * cash bill sales, and stock reference.
 */
public class ShiftReportSalesSection {

    public void addMeterwise(PdfPCell container, ShiftReportPrintData data) {
        if (data.getMeterReadings().isEmpty()) return;

        container.addElement(sectionHeader("METERWISE"));

        // Group nozzles by product
        Map<String, List<MeterReading>> byProduct = new LinkedHashMap<>();
        for (MeterReading mr : data.getMeterReadings()) {
            byProduct.computeIfAbsent(mr.getProductName(), k -> new ArrayList<>()).add(mr);
        }

        for (Map.Entry<String, List<MeterReading>> entry : byProduct.entrySet()) {
            String productName = entry.getKey();
            List<MeterReading> readings = entry.getValue();
            double totalSales = readings.stream().mapToDouble(r -> r.getSales() != null ? r.getSales() : 0).sum();

            // Product subtotal header
            Paragraph pHeader = new Paragraph(productName + " : " + fmt0(totalSales), BOLD_FONT);
            pHeader.setSpacingBefore(1);
            container.addElement(pHeader);

            // Split into chunks of 3 nozzles per row
            int chunkSize = 3;
            for (int start = 0; start < readings.size(); start += chunkSize) {
                int end = Math.min(start + chunkSize, readings.size());
                List<MeterReading> chunk = readings.subList(start, end);
                int cols = chunk.size() + 1;

                PdfPTable table = new PdfPTable(cols);
                table.setWidthPercentage(100);
                table.setSpacingAfter(1);

                // Header row (nozzle names)
                addHeaderCell(table, "");
                for (MeterReading mr : chunk) {
                    addHeaderCell(table, mr.getNozzleName());
                }

                // Open row
                addCellLeft(table, "Open", SMALL_FONT);
                for (MeterReading mr : chunk) {
                    addCellRight(table, fmt0(mr.getOpenReading()), SMALL_FONT);
                }

                // Close row
                addCellLeft(table, "Close", SMALL_FONT);
                for (MeterReading mr : chunk) {
                    addCellRight(table, fmt0(mr.getCloseReading()), SMALL_FONT);
                }

                // Sales row
                addCellLeft(table, "Sales", SMALL_BOLD);
                for (MeterReading mr : chunk) {
                    addCellRight(table, fmt0(mr.getSales()), SMALL_BOLD);
                }

                container.addElement(table);
            }
        }
    }

    public void addGrossNetSales(PdfPCell container, ShiftReportPrintData data) {
        // Compute gross/net sales per product from meter readings + credit bills
        Map<String, Double> meterByProduct = new LinkedHashMap<>();
        Map<String, Double> testByProduct = new LinkedHashMap<>();

        for (MeterReading mr : data.getMeterReadings()) {
            String pName = mr.getProductName();
            meterByProduct.merge(pName, mr.getSales() != null ? mr.getSales() : 0, Double::sum);
            testByProduct.merge(pName, mr.getTestQuantity() != null ? mr.getTestQuantity() : 0, Double::sum);
        }

        // Credit sales by product from credit bill details
        Map<String, Double> creditByProduct = new LinkedHashMap<>();
        for (CreditBillDetail cbd : data.getCreditBillDetails()) {
            // Parse products string "P:500 HSD:200"
            if (cbd.getProducts() != null && !cbd.getProducts().isBlank()) {
                for (String part : cbd.getProducts().split("\\s+")) {
                    String[] kv = part.split(":");
                    if (kv.length == 2) {
                        String abbr = kv[0];
                        double qty = 0;
                        try { qty = Double.parseDouble(kv[1]); } catch (NumberFormatException ignored) {}
                        // Map abbreviation back to product name
                        String productName = expandAbbreviation(abbr, meterByProduct.keySet());
                        if (productName != null) {
                            creditByProduct.merge(productName, qty, Double::sum);
                        }
                    }
                }
            }
        }

        if (meterByProduct.isEmpty()) return;

        List<String> products = new ArrayList<>(meterByProduct.keySet());

        // GROSS SALES
        container.addElement(sectionHeader("GROSS SALES"));
        PdfPTable grossTable = new PdfPTable(products.size() + 1);
        grossTable.setWidthPercentage(100);
        grossTable.setSpacingAfter(1);

        addHeaderCell(grossTable, "");
        for (String p : products) addHeaderCell(grossTable, productDisplay(p));

        // Credit row
        addCellLeft(grossTable, "Credit", SMALL_FONT);
        for (String p : products) addCellRight(grossTable, fmt2(creditByProduct.getOrDefault(p, 0.0)), SMALL_FONT);

        // Cash row (gross - credit - testing)
        addCellLeft(grossTable, "Cash", SMALL_FONT);
        for (String p : products) {
            double gross = meterByProduct.getOrDefault(p, 0.0);
            double credit = creditByProduct.getOrDefault(p, 0.0);
            double test = testByProduct.getOrDefault(p, 0.0);
            addCellRight(grossTable, fmt2(gross - credit - test), SMALL_FONT);
        }

        // Testing row
        addCellLeft(grossTable, "Testing", SMALL_FONT);
        for (String p : products) addCellRight(grossTable, fmt2(testByProduct.getOrDefault(p, 0.0)), SMALL_FONT);

        // Gross total
        addTotalCellLeft(grossTable, "Gross");
        for (String p : products) addTotalCellRight(grossTable, fmt2(meterByProduct.getOrDefault(p, 0.0)));

        container.addElement(grossTable);

        // NET SALES
        container.addElement(sectionHeader("NET SALES"));
        PdfPTable netTable = new PdfPTable(products.size() + 1);
        netTable.setWidthPercentage(100);
        netTable.setSpacingAfter(1);

        addHeaderCell(netTable, "");
        for (String p : products) addHeaderCell(netTable, productDisplay(p));

        addCellLeft(netTable, "Gross", SMALL_FONT);
        for (String p : products) addCellRight(netTable, fmt2(meterByProduct.getOrDefault(p, 0.0)), SMALL_FONT);

        addCellLeft(netTable, "Testing", SMALL_FONT);
        for (String p : products) addCellRight(netTable, fmt2(testByProduct.getOrDefault(p, 0.0)), SMALL_FONT);

        addTotalCellLeft(netTable, "Net");
        for (String p : products) {
            double net = meterByProduct.getOrDefault(p, 0.0) - testByProduct.getOrDefault(p, 0.0);
            addTotalCellRight(netTable, fmt2(net));
        }

        container.addElement(netTable);
    }

    public void addTankwise(PdfPCell container, ShiftReportPrintData data) {
        if (data.getTankReadings().isEmpty()) return;

        container.addElement(sectionHeader("TANKWISE"));
        PdfPTable table = new PdfPTable(new float[]{1.8f, 1f, 1.2f, 0.8f, 1.2f, 1f, 1.2f, 1f});
        table.setWidthPercentage(100);
        table.setSpacingAfter(1);

        addHeaderCell(table, "TANK");
        addHeaderCell(table, "O.DIP");
        addHeaderCell(table, "O.STOCK");
        addHeaderCell(table, "INC");
        addHeaderCell(table, "TOTAL");
        addHeaderCell(table, "C.DIP");
        addHeaderCell(table, "C.STOCK");
        addHeaderCell(table, "SALES");

        for (TankReading tr : data.getTankReadings()) {
            addCellLeft(table, tr.getTankName(), SMALL_FONT);
            addCellRight(table, tr.getOpenDip() != null ? tr.getOpenDip() : "-", SMALL_FONT);
            addCellRight(table, fmt0(tr.getOpenStock()), SMALL_FONT);
            addCellRight(table, fmt0(tr.getIncomeStock()), SMALL_FONT);
            addCellRight(table, fmt0(tr.getTotalStock()), SMALL_FONT);
            addCellRight(table, tr.getCloseDip() != null ? tr.getCloseDip() : "-", SMALL_FONT);
            addCellRight(table, fmt0(tr.getCloseStock()), SMALL_FONT);
            addCellRight(table, fmt0(tr.getSaleStock()), SMALL_BOLD);
        }

        container.addElement(table);
    }

    public void addSalesReconciliation(PdfPCell container, ShiftReportPrintData data) {
        if (data.getSalesReconciliation() == null || data.getSalesReconciliation().isEmpty()) return;

        container.addElement(sectionHeader("SALES RECONCILIATION (METER vs INVOICE)"));
        PdfPTable table = new PdfPTable(new float[]{1.6f, 1f, 1f, 0.7f, 1.2f, 1.2f, 1f});
        table.setWidthPercentage(100);
        table.setSpacingAfter(1);

        addHeaderCell(table, "PRODUCT");
        addHeaderCell(table, "METER");
        addHeaderCell(table, "CREDIT");
        addHeaderCell(table, "TEST");
        addHeaderCell(table, "EXP. CASH");
        addHeaderCell(table, "ACT. CASH");
        addHeaderCell(table, "VARIANCE");

        for (SalesReconciliation sr : data.getSalesReconciliation()) {
            addCellLeft(table, productDisplay(sr.getProductName()), SMALL_FONT);
            addCellRight(table, fmt2(sr.getMeterLitres()), SMALL_FONT);
            addCellRight(table, fmt2(sr.getCreditLitres()), SMALL_FONT);
            addCellRight(table, fmt2(sr.getTestLitres()), SMALL_FONT);
            addCellRight(table, fmt2(sr.getExpectedCashLitres()), SMALL_BOLD);
            addCellRight(table, fmt2(sr.getActualCashLitres()), SMALL_FONT);
            double variance = sr.getVariance() != null ? sr.getVariance() : 0;
            if (Math.abs(variance) > 0.5) {
                com.lowagie.text.Font redBold = new com.lowagie.text.Font(
                        com.lowagie.text.Font.HELVETICA, 8.5f, com.lowagie.text.Font.BOLD, DIFF_RED);
                addCellRight(table, fmt2(variance), redBold);
            } else {
                addCellRight(table, fmt2(variance), SMALL_BOLD);
            }
        }

        container.addElement(table);
    }

    public void addSalesDifference(PdfPCell container, ShiftReportPrintData data) {
        if (data.getSalesDifferences().isEmpty()) return;

        container.addElement(sectionHeader("SALES DIFFERENCE"));
        PdfPTable table = new PdfPTable(new float[]{2f, 1.5f, 1.5f, 1.5f});
        table.setWidthPercentage(100);
        table.setSpacingAfter(1);

        addHeaderCell(table, "TANK");
        addHeaderCell(table, "TANKWISE");
        addHeaderCell(table, "METERWISE");
        addHeaderCell(table, "DIFFERENCE");

        for (SalesDifference sd : data.getSalesDifferences()) {
            addCellLeft(table, sd.getProductName(), SMALL_FONT);
            addCellRight(table, fmt0(sd.getTankSale()), SMALL_FONT);
            addCellRight(table, fmt0(sd.getMeterSale()), SMALL_FONT);
            if (sd.getDifference() != null && sd.getDifference() < 0) {
                com.lowagie.text.Font redBold = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 5, com.lowagie.text.Font.BOLD, DIFF_RED);
                addCellRight(table, fmt0(sd.getDifference()), redBold);
            } else {
                addCellRight(table, fmt0(sd.getDifference()), SMALL_BOLD);
            }
        }

        container.addElement(table);
    }

    public void addCashBillSales(PdfPCell container, ShiftReportPrintData data) {
        if (data.getCashBillDetails().isEmpty() && data.getPaymentModeBreakdown().isEmpty()) return;

        container.addElement(sectionHeader("CASH BILL SALES BY MODE — LITRES PER PRODUCT"));
        Paragraph caption = new Paragraph(
                "Litres dispensed against cash invoices, split by tender. Rows: payment mode. Columns: product.",
                SMALL_FONT);
        caption.setSpacingAfter(1);
        container.addElement(caption);

        // Resolve abbreviation -> full product name using meter-reading product names
        Set<String> knownProducts = new LinkedHashSet<>();
        for (MeterReading mr : data.getMeterReadings()) knownProducts.add(mr.getProductName());

        // Compute litres per product per payment mode from cash bill details
        Set<String> productAbbrs = new LinkedHashSet<>();
        Map<String, Map<String, Double>> modeProductQty = new LinkedHashMap<>();

        for (CashBillDetail cbd : data.getCashBillDetails()) {
            String mode = cbd.getPaymentMode() != null ? cbd.getPaymentMode().toUpperCase() : "CASH";
            if (cbd.getProducts() != null && !cbd.getProducts().isBlank()) {
                for (String part : cbd.getProducts().split("\\s+")) {
                    String[] kv = part.split(":");
                    if (kv.length == 2) {
                        String abbr = kv[0];
                        productAbbrs.add(abbr);
                        double qty = 0;
                        try { qty = Double.parseDouble(kv[1]); } catch (NumberFormatException ignored) {}
                        modeProductQty.computeIfAbsent(mode, k -> new LinkedHashMap<>())
                                .merge(abbr, qty, Double::sum);
                    }
                }
            }
        }

        if (productAbbrs.isEmpty()) {
            // Fallback: simple mode/bills/amount table
            PdfPTable table = new PdfPTable(new float[]{2f, 2f, 2f});
            table.setWidthPercentage(100);
            table.setSpacingAfter(1);
            addHeaderCell(table, "MODE");
            addHeaderCell(table, "BILLS");
            addHeaderCell(table, "AMOUNT");
            BigDecimal total = BigDecimal.ZERO;
            int totalBills = 0;
            for (PaymentModeBreakdown pmb : data.getPaymentModeBreakdown()) {
                addCellLeft(table, pmb.getMode(), SMALL_FONT);
                addCellRight(table, String.valueOf(pmb.getBillCount()), SMALL_FONT);
                addCellRight(table, fmtBD(pmb.getAmount()), SMALL_FONT);
                total = total.add(pmb.getAmount() != null ? pmb.getAmount() : BigDecimal.ZERO);
                totalBills += pmb.getBillCount();
            }
            addCellLeft(table, "TOTAL", SMALL_BOLD);
            addCellRight(table, String.valueOf(totalBills), SMALL_BOLD);
            addCellRight(table, fmtBD(total), SMALL_BOLD);
            container.addElement(table);
            return;
        }

        // Product columns table (matching mockup: rows=payment modes, cols=product abbreviations)
        List<String> products = new ArrayList<>(productAbbrs);
        PdfPTable table = new PdfPTable(products.size() + 1);
        table.setWidthPercentage(100);
        table.setSpacingAfter(1);

        addHeaderCell(table, "");
        for (String p : products) addHeaderCell(table, productDisplayFromAbbr(p, knownProducts));

        for (Map.Entry<String, Map<String, Double>> entry : modeProductQty.entrySet()) {
            addCellLeft(table, entry.getKey(), SMALL_FONT);
            for (String p : products) {
                double qty = entry.getValue().getOrDefault(p, 0.0);
                addCellRight(table, fmt2(qty), SMALL_FONT);
            }
        }

        // Totals row
        addCellLeft(table, "Total", SMALL_BOLD);
        for (String p : products) {
            double total = modeProductQty.values().stream()
                    .mapToDouble(m -> m.getOrDefault(p, 0.0)).sum();
            addCellRight(table, fmt2(total), SMALL_BOLD);
        }

        container.addElement(table);
    }

    public void addStockReference(PdfPCell container, ShiftReportPrintData data) {
        // Stock reference from tank readings (fuel products) showing price, sales, available stock
        if (data.getTankReadings().isEmpty()) return;

        container.addElement(sectionHeader("STOCK REFERENCE"));
        PdfPTable table = new PdfPTable(new float[]{2f, 1.5f, 1.5f, 2f});
        table.setWidthPercentage(100);
        table.setSpacingAfter(1);

        addHeaderCell(table, "");
        addHeaderCell(table, "PRICE");
        addHeaderCell(table, "SALES");
        addHeaderCell(table, "AVAIL. STOCK");

        // Fuel products from stock summary
        for (StockSummaryRow row : data.getStockSummary()) {
            addCellLeft(table, row.getProductName(), SMALL_FONT);
            addCellRight(table, row.getRate() != null ? row.getRate().toPlainString() : "-", SMALL_FONT);
            addCellRight(table, fmt2(row.getSales()), SMALL_FONT);
            double avail = (row.getTotalStock() != null ? row.getTotalStock() : 0) - (row.getSales() != null ? row.getSales() : 0);
            addCellRight(table, fmtComma(avail), SMALL_FONT);
        }

        container.addElement(table);
    }
}
