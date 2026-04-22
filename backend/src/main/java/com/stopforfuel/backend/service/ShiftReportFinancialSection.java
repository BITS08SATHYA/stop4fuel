package com.stopforfuel.backend.service;

import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.stopforfuel.backend.dto.ShiftReportPrintData;
import com.stopforfuel.backend.dto.ShiftReportPrintData.*;
import com.stopforfuel.backend.entity.ShiftClosingReport;

import java.awt.Color;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

import static com.stopforfuel.backend.service.ShiftReportPdfUtils.*;

/**
 * Renders the financial sections (right column of page 1 and page 2):
 * revenue, advances, turnover/balance box, income bills, credit bills summary,
 * and advance detail tables for page 2.
 */
public class ShiftReportFinancialSection {

    public void addRevenue(PdfPCell container, ShiftReportPrintData data, ShiftClosingReport report) {
        container.addElement(sectionHeader("REVENUE"));
        PdfPTable table = new PdfPTable(new float[]{3f, 1f, 1.2f, 2f});
        table.setWidthPercentage(100);
        table.setSpacingAfter(1);

        addHeaderCell(table, "ITEMS");
        addHeaderCell(table, "LITRES");
        addHeaderCell(table, "RATE");
        addHeaderCell(table, "AMOUNT");

        BigDecimal totalRevenue = BigDecimal.ZERO;

        // Fuel revenue from meter readings (grouped by product)
        Map<String, double[]> fuelByProduct = new LinkedHashMap<>();
        for (MeterReading mr : data.getMeterReadings()) {
            double sales = mr.getSales() != null ? mr.getSales() : 0;
            double test = mr.getTestQuantity() != null ? mr.getTestQuantity() : 0;
            double netSales = sales - test;
            double rate = mr.getRate() != null ? mr.getRate() : 0;
            fuelByProduct.merge(mr.getProductName(), new double[]{netSales, rate}, (o, n) -> new double[]{o[0] + n[0], n[1]});
        }

        for (Map.Entry<String, double[]> entry : fuelByProduct.entrySet()) {
            double litres = entry.getValue()[0];
            double rate = entry.getValue()[1];
            BigDecimal amount = BigDecimal.valueOf(litres * rate).setScale(2, RoundingMode.HALF_UP);
            addCellLeft(table, entry.getKey(), SMALL_FONT);
            addCellRight(table, fmt0(litres), SMALL_FONT);
            addCellRight(table, fmt2(rate), SMALL_FONT);
            addCellRight(table, fmtComma(amount), SMALL_FONT);
            totalRevenue = totalRevenue.add(amount);
        }

        // Test quantity line item in revenue
        double totalTestLitres = 0;
        double totalTestAmount = 0;
        for (MeterReading mr : data.getMeterReadings()) {
            double test = mr.getTestQuantity() != null ? mr.getTestQuantity() : 0;
            double rate = mr.getRate() != null ? mr.getRate() : 0;
            totalTestLitres += test;
            totalTestAmount += test * rate;
        }
        if (totalTestLitres > 0) {
            BigDecimal testAmt = BigDecimal.valueOf(totalTestAmount).setScale(2, RoundingMode.HALF_UP);
            addCellLeft(table, "Test", SMALL_FONT);
            addCellRight(table, fmt0(totalTestLitres), SMALL_FONT);
            addCellRight(table, "", SMALL_FONT);
            addCellRight(table, fmtComma(testAmt), SMALL_FONT);
            totalRevenue = totalRevenue.add(testAmt);
        }

        // Oil sales from stock summary (non-fuel)
        BigDecimal oilTotal = BigDecimal.ZERO;
        for (StockSummaryRow row : data.getStockSummary()) {
            boolean isFuel = fuelByProduct.containsKey(row.getProductName());
            if (!isFuel && row.getAmount() != null && row.getAmount().compareTo(BigDecimal.ZERO) > 0) {
                oilTotal = oilTotal.add(row.getAmount());
            }
        }
        if (oilTotal.compareTo(BigDecimal.ZERO) > 0) {
            addCellLeft(table, "OIL / Other Products", SMALL_FONT);
            addCellRight(table, "", SMALL_FONT);
            addCellRight(table, "", SMALL_FONT);
            addCellRight(table, fmtComma(oilTotal), SMALL_FONT);
            totalRevenue = totalRevenue.add(oilTotal);
        }

        // Income Bills (payments received) total
        BigDecimal incomeBillTotal = BigDecimal.ZERO;
        BigDecimal incomeStmtTotal = BigDecimal.ZERO;
        for (PaymentEntryDetail pe : data.getPaymentEntries()) {
            if ("BILL".equals(pe.getType())) {
                incomeBillTotal = incomeBillTotal.add(pe.getAmount() != null ? pe.getAmount() : BigDecimal.ZERO);
            } else if ("STMT".equals(pe.getType())) {
                incomeStmtTotal = incomeStmtTotal.add(pe.getAmount() != null ? pe.getAmount() : BigDecimal.ZERO);
            }
        }

        if (incomeBillTotal.compareTo(BigDecimal.ZERO) > 0) {
            addCellLeft(table, "Income Bill", SMALL_FONT);
            addCellRight(table, "", SMALL_FONT);
            addCellRight(table, "", SMALL_FONT);
            addCellRight(table, fmtComma(incomeBillTotal), SMALL_FONT);
            totalRevenue = totalRevenue.add(incomeBillTotal);
        }

        if (incomeStmtTotal.compareTo(BigDecimal.ZERO) > 0) {
            addCellLeft(table, "Income Credit Stmt", SMALL_FONT);
            addCellRight(table, "", SMALL_FONT);
            addCellRight(table, "", SMALL_FONT);
            addCellRight(table, fmtComma(incomeStmtTotal), SMALL_FONT);
            totalRevenue = totalRevenue.add(incomeStmtTotal);
        }

        // External inflows
        BigDecimal inflowTotal = BigDecimal.ZERO;
        for (AdvanceEntryDetail ae : data.getAdvanceEntries()) {
            if ("EXTERNAL_INFLOW".equals(ae.getType())) {
                inflowTotal = inflowTotal.add(ae.getAmount() != null ? ae.getAmount() : BigDecimal.ZERO);
            }
        }
        if (inflowTotal.compareTo(BigDecimal.ZERO) > 0) {
            addCellLeft(table, "External Cash Inflow", SMALL_FONT);
            addCellRight(table, "", SMALL_FONT);
            addCellRight(table, "", SMALL_FONT);
            addCellRight(table, fmtComma(inflowTotal), SMALL_FONT);
            totalRevenue = totalRevenue.add(inflowTotal);
        }

        // TOTAL row
        addTotalCellLeft(table, "TOTAL");
        addTotalCellRight(table, "");
        addTotalCellRight(table, "");
        addTotalCellRight(table, fmtComma(totalRevenue));

        container.addElement(table);
    }

    public void addAdvances(PdfPCell container, ShiftReportPrintData data, ShiftClosingReport report) {
        container.addElement(sectionHeader("ADVANCES"));
        PdfPTable table = new PdfPTable(new float[]{4f, 2.5f});
        table.setWidthPercentage(100);
        table.setSpacingAfter(1);

        addHeaderCell(table, "ITEM");
        addHeaderCell(table, "AMOUNT");

        // Group advance entries by type and sum
        Map<String, BigDecimal> advanceByType = new LinkedHashMap<>();
        for (AdvanceEntryDetail ae : data.getAdvanceEntries()) {
            String label = getAdvanceLabel(ae.getType());
            advanceByType.merge(label, ae.getAmount() != null ? ae.getAmount() : BigDecimal.ZERO, BigDecimal::add);
        }

        // Add credit bills total
        BigDecimal creditTotal = BigDecimal.ZERO;
        for (CreditBillDetail cbd : data.getCreditBillDetails()) {
            creditTotal = creditTotal.add(cbd.getAmount() != null ? cbd.getAmount() : BigDecimal.ZERO);
        }
        if (creditTotal.compareTo(BigDecimal.ZERO) > 0) {
            addCellLeft(table, "Credit Bills", SMALL_FONT);
            addCellRight(table, fmtComma(creditTotal), SMALL_FONT);
        }

        BigDecimal totalAdvances = creditTotal;
        for (Map.Entry<String, BigDecimal> entry : advanceByType.entrySet()) {
            addCellLeft(table, entry.getKey(), SMALL_FONT);
            addCellRight(table, fmtComma(entry.getValue()), SMALL_FONT);
            totalAdvances = totalAdvances.add(entry.getValue());
        }

        addTotalCellLeft(table, "TOTAL");
        addTotalCellRight(table, fmtComma(totalAdvances));

        container.addElement(table);
    }

    public void addTurnoverBalanceBox(PdfPCell container, ShiftClosingReport report) {
        Font BOX_LABEL = new Font(Font.HELVETICA, 5.5f, Font.BOLD);
        Font BOX_VALUE = new Font(Font.HELVETICA, 9, Font.BOLD);

        PdfPTable box = new PdfPTable(3);
        box.setWidthPercentage(100);
        box.setSpacingBefore(3);
        box.setSpacingAfter(3);

        // Turnover
        PdfPCell turnCell = new PdfPCell();
        turnCell.setBorderWidth(2f);
        turnCell.setBorderColor(Color.BLACK);
        turnCell.setPadding(3);
        turnCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        turnCell.addElement(new Paragraph("TURNOVER", BOX_LABEL));
        turnCell.addElement(new Paragraph(fmtComma(report.getTotalRevenue()), BOX_VALUE));
        box.addCell(turnCell);

        // Balance
        PdfPCell balCell = new PdfPCell();
        balCell.setBorderWidth(2f);
        balCell.setBorderColor(Color.BLACK);
        balCell.setPadding(3);
        balCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        balCell.addElement(new Paragraph("BALANCE", BOX_LABEL));
        balCell.addElement(new Paragraph(fmtComma(report.getBalance()), BOX_VALUE));
        box.addCell(balCell);

        // Cash Bill
        PdfPCell cashCell = new PdfPCell();
        cashCell.setBorderWidth(2f);
        cashCell.setBorderColor(Color.BLACK);
        cashCell.setPadding(3);
        cashCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cashCell.addElement(new Paragraph("CASH BILL", BOX_LABEL));
        cashCell.addElement(new Paragraph(fmtComma(report.getCashBillAmount()), BOX_VALUE));
        box.addCell(cashCell);

        container.addElement(box);
    }

    public void addIncomeBills(PdfPCell container, ShiftReportPrintData data) {
        if (data.getPaymentEntries().isEmpty()) return;

        container.addElement(sectionHeader("INCOME BILLS (Payments Received)"));
        PdfPTable table = new PdfPTable(new float[]{0.5f, 3f, 1.5f, 1.5f, 2f});
        table.setWidthPercentage(100);
        table.setSpacingAfter(1);

        addHeaderCell(table, "#");
        addHeaderCell(table, "CUSTOMER / VEHICLE");
        addHeaderCell(table, "BILL");
        addHeaderCell(table, "MODE");
        addHeaderCell(table, "AMOUNT");

        int idx = 1;
        BigDecimal total = BigDecimal.ZERO;
        for (PaymentEntryDetail pe : data.getPaymentEntries()) {
            addCellRight(table, String.valueOf(idx++), SMALL_FONT);
            addCellLeft(table, pe.getCustomerName(), SMALL_FONT);
            addCellLeft(table, pe.getReference(), SMALL_FONT);
            addCellLeft(table, pe.getPaymentMode(), SMALL_FONT);
            addCellRight(table, fmtComma(pe.getAmount()), SMALL_FONT);
            total = total.add(pe.getAmount() != null ? pe.getAmount() : BigDecimal.ZERO);
        }

        addCellRight(table, "", SMALL_BOLD);
        addCellLeft(table, "Total (" + (idx - 1) + " entries)", SMALL_BOLD);
        addCellLeft(table, "", SMALL_BOLD);
        addCellLeft(table, "", SMALL_BOLD);
        addCellRight(table, fmtComma(total), SMALL_BOLD);

        container.addElement(table);
    }

    public void addCreditBillsSummary(PdfPCell container, ShiftReportPrintData data) {
        if (data.getCreditBillDetails().isEmpty()) return;

        BigDecimal creditTotal = BigDecimal.ZERO;
        for (CreditBillDetail cbd : data.getCreditBillDetails()) {
            creditTotal = creditTotal.add(cbd.getAmount() != null ? cbd.getAmount() : BigDecimal.ZERO);
        }

        container.addElement(sectionHeader("CREDIT BILLS (" + data.getCreditBillDetails().size() + ") — \u20B9" + fmtComma(creditTotal)));
        PdfPTable table = new PdfPTable(new float[]{0.4f, 1.8f, 1f, 1.6f, 0.8f, 0.8f, 1.2f});
        table.setWidthPercentage(100);
        table.setSpacingAfter(1);

        addHeaderCell(table, "#");
        addHeaderCell(table, "CUSTOMER");
        addHeaderCell(table, "BILL");
        addHeaderCell(table, "VEHICLE");
        addHeaderCell(table, "PROD");
        addHeaderCell(table, "QTY");
        addHeaderCell(table, "AMT");

        int idx = 1;
        double totalQty = 0;
        for (CreditBillDetail cbd : data.getCreditBillDetails()) {
            addCellRight(table, String.valueOf(idx++), SMALL_FONT);
            addCellLeft(table, cbd.getCustomerName(), SMALL_FONT);
            addCellLeft(table, cbd.getBillNo(), SMALL_FONT);
            addCellLeft(table, cbd.getVehicleNo(), SMALL_FONT);
            // Parse product abbreviation and quantity from "MS:500 HSD:200"
            String prodAbbr = "";
            double qty = 0;
            if (cbd.getProducts() != null && !cbd.getProducts().isBlank()) {
                for (String part : cbd.getProducts().split("\\s+")) {
                    String[] kv = part.split(":");
                    if (kv.length == 2) {
                        if (prodAbbr.isEmpty()) prodAbbr = kv[0]; else prodAbbr += "+" + kv[0];
                        try { qty += Double.parseDouble(kv[1]); } catch (NumberFormatException ignored) {}
                    }
                }
            }
            addCellLeft(table, prodAbbr, SMALL_FONT);
            addCellRight(table, fmt0(qty), SMALL_FONT);
            addCellRight(table, fmtComma(cbd.getAmount()), SMALL_FONT);
            totalQty += qty;
        }

        // Total row
        addCellRight(table, "", SMALL_BOLD);
        addCellLeft(table, data.getCreditBillDetails().size() + " bills", SMALL_BOLD);
        addCellLeft(table, "", SMALL_BOLD);
        addCellLeft(table, "", SMALL_BOLD);
        addCellLeft(table, "", SMALL_BOLD);
        addCellRight(table, fmt0(totalQty), SMALL_BOLD);
        addCellRight(table, fmtComma(creditTotal), SMALL_BOLD);

        container.addElement(table);
    }

    public void addEAdvanceDetailsCompact(PdfPCell container, ShiftReportPrintData data) {
        List<String> eTypes = List.of("CARD", "UPI", "CCMS", "CHEQUE", "BANK_TRANSFER");
        List<AdvanceEntryDetail> entries = data.getAdvanceEntries().stream()
                .filter(ae -> eTypes.contains(ae.getType()))
                .toList();
        if (entries.isEmpty()) return;

        BigDecimal total = BigDecimal.ZERO;
        for (AdvanceEntryDetail e : entries) {
            total = total.add(e.getAmount() != null ? e.getAmount() : BigDecimal.ZERO);
        }

        container.addElement(sectionHeader("E-ADVANCES (" + entries.size() + ") — Rs." + fmtComma(total)));

        PdfPTable table = new PdfPTable(new float[]{0.4f, 1f, 3f, 1.5f});
        table.setWidthPercentage(100);
        table.setSpacingAfter(1);

        addHeaderCell(table, "#");
        addHeaderCell(table, "TYPE");
        addHeaderCell(table, "DESCRIPTION");
        addHeaderCell(table, "AMOUNT");

        int idx = 1;
        for (AdvanceEntryDetail entry : entries) {
            addCellRight(table, String.valueOf(idx++), SMALL_FONT);
            addCellLeft(table, getAdvanceLabel(entry.getType()), SMALL_FONT);
            String desc = entry.getDescription() != null ? entry.getDescription() : "-";
            if (entry.getReference() != null && !entry.getReference().isBlank()) {
                desc += " [" + entry.getReference() + "]";
            }
            addCellLeft(table, desc, SMALL_FONT);
            addCellRight(table, fmtComma(entry.getAmount()), SMALL_FONT);
        }

        addCellRight(table, "", SMALL_BOLD);
        addCellLeft(table, "", SMALL_BOLD);
        addCellLeft(table, "Total", SMALL_BOLD);
        addCellRight(table, fmtComma(total), SMALL_BOLD);

        container.addElement(table);
    }

    public void addOperationalAdvanceDetailsCompact(PdfPCell container, ShiftReportPrintData data) {
        // Incentive is revenue reduction, not an advance — excluded from this detail section.
        // (Still appears in the main ADVANCES table via addAdvances().)
        List<String> opTypes = List.of("CASH_ADVANCE", "HOME_ADVANCE", "SALARY_ADVANCE", "EXPENSE", "REPAYMENT");
        List<AdvanceEntryDetail> entries = data.getAdvanceEntries().stream()
                .filter(ae -> opTypes.contains(ae.getType()))
                .toList();
        if (entries.isEmpty()) return;

        BigDecimal total = BigDecimal.ZERO;
        for (AdvanceEntryDetail e : entries) {
            total = total.add(e.getAmount() != null ? e.getAmount() : BigDecimal.ZERO);
        }

        container.addElement(sectionHeader("OPERATIONAL ADVANCES (" + entries.size() + ") — Rs." + fmtComma(total)));

        PdfPTable table = new PdfPTable(new float[]{0.4f, 1f, 3f, 1.5f});
        table.setWidthPercentage(100);
        table.setSpacingAfter(1);

        addHeaderCell(table, "#");
        addHeaderCell(table, "TYPE");
        addHeaderCell(table, "DESCRIPTION");
        addHeaderCell(table, "AMOUNT");

        int idx = 1;
        for (AdvanceEntryDetail entry : entries) {
            addCellRight(table, String.valueOf(idx++), SMALL_FONT);
            addCellLeft(table, getAdvanceLabel(entry.getType()), SMALL_FONT);
            String desc = entry.getDescription() != null ? entry.getDescription() : "-";
            if (entry.getReference() != null && !entry.getReference().isBlank()) {
                desc += " [" + entry.getReference() + "]";
            }
            addCellLeft(table, desc, SMALL_FONT);
            addCellRight(table, fmtComma(entry.getAmount()), SMALL_FONT);
        }

        addCellRight(table, "", SMALL_BOLD);
        addCellLeft(table, "", SMALL_BOLD);
        addCellLeft(table, "Total", SMALL_BOLD);
        addCellRight(table, fmtComma(total), SMALL_BOLD);

        container.addElement(table);
    }

    public void addCashBillsSummary(PdfPCell container, ShiftReportPrintData data) {
        if (data.getPaymentModeBreakdown().isEmpty() && data.getCashBillDetails().isEmpty()) return;

        // Only count CASH payment mode bills (UPI/CCMS/Card are covered in E-Advances)
        BigDecimal cashTotal = BigDecimal.ZERO;
        int cashBills = 0;
        int totalBills = 0;
        for (PaymentModeBreakdown pmb : data.getPaymentModeBreakdown()) {
            totalBills += pmb.getBillCount();
            if ("CASH".equalsIgnoreCase(pmb.getMode())) {
                cashTotal = cashTotal.add(pmb.getAmount() != null ? pmb.getAmount() : BigDecimal.ZERO);
                cashBills = pmb.getBillCount();
            }
        }

        container.addElement(sectionHeader("CASH BILLS (" + totalBills + " total, " + cashBills + " cash) — \u20B9" + fmtComma(cashTotal)));
        PdfPTable table = new PdfPTable(new float[]{2.5f, 1f, 2f});
        table.setWidthPercentage(100);
        table.setSpacingAfter(1);

        addHeaderCell(table, "");
        addHeaderCell(table, "BILLS");
        addHeaderCell(table, "AMOUNT");

        addCellLeft(table, "CASH", SMALL_BOLD);
        addCellRight(table, String.valueOf(cashBills), SMALL_BOLD);
        addCellRight(table, fmtComma(cashTotal), SMALL_BOLD);

        container.addElement(table);
    }

    public void addEAdvanceSummary(PdfPCell container, ShiftReportPrintData data) {
        List<String> eTypes = List.of("CARD", "UPI", "CCMS", "CHEQUE", "BANK_TRANSFER");

        // Group by type: count + sum
        Map<String, long[]> typeSummary = new LinkedHashMap<>(); // [count, sum_paise]
        BigDecimal grandTotal = BigDecimal.ZERO;
        int totalCount = 0;

        for (AdvanceEntryDetail ae : data.getAdvanceEntries()) {
            if (!eTypes.contains(ae.getType())) continue;
            BigDecimal amt = ae.getAmount() != null ? ae.getAmount() : BigDecimal.ZERO;
            typeSummary.merge(ae.getType(), new long[]{1, amt.movePointRight(2).longValue()},
                    (old, n) -> new long[]{old[0] + n[0], old[1] + n[1]});
            grandTotal = grandTotal.add(amt);
            totalCount++;
        }
        if (typeSummary.isEmpty()) return;

        container.addElement(sectionHeader("E-ADVANCES (" + totalCount + ") — Rs." + fmtComma(grandTotal)));

        PdfPTable table = new PdfPTable(new float[]{2.5f, 1f, 2f});
        table.setWidthPercentage(100);
        table.setSpacingAfter(1);

        addHeaderCell(table, "TYPE");
        addHeaderCell(table, "COUNT");
        addHeaderCell(table, "AMOUNT");

        for (Map.Entry<String, long[]> entry : typeSummary.entrySet()) {
            addCellLeft(table, getAdvanceLabel(entry.getKey()), SMALL_FONT);
            addCellRight(table, String.valueOf(entry.getValue()[0]), SMALL_FONT);
            addCellRight(table, fmtComma(BigDecimal.valueOf(entry.getValue()[1]).movePointLeft(2)), SMALL_FONT);
        }

        addCellLeft(table, "TOTAL", SMALL_BOLD);
        addCellRight(table, String.valueOf(totalCount), SMALL_BOLD);
        addCellRight(table, fmtComma(grandTotal), SMALL_BOLD);

        container.addElement(table);
    }

    public void addSalesSummary(PdfPCell container, ShiftReportPrintData data) {
        // Known product names (from meter readings) for abbr -> full-name expansion
        Set<String> knownProducts = new LinkedHashSet<>();
        for (MeterReading mr : data.getMeterReadings()) knownProducts.add(mr.getProductName());

        // Aggregate product sales from cash + credit bill details
        Map<String, double[]> productSales = new LinkedHashMap<>(); // [cashQty, creditQty, totalAmount]

        for (CashBillDetail cbd : data.getCashBillDetails()) {
            if (cbd.getProducts() == null || cbd.getProducts().isBlank()) continue;
            for (String part : cbd.getProducts().split("\\s+")) {
                String[] kv = part.split(":");
                if (kv.length == 2) {
                    String product = kv[0];
                    double qty = 0;
                    try { qty = Double.parseDouble(kv[1]); } catch (NumberFormatException ignored) {}
                    productSales.merge(product, new double[]{qty, 0, cbd.getAmount() != null ? cbd.getAmount().doubleValue() : 0},
                            (old, n) -> new double[]{old[0] + n[0], old[1], old[2] + n[2]});
                }
            }
        }

        for (CreditBillDetail cbd : data.getCreditBillDetails()) {
            if (cbd.getProducts() == null || cbd.getProducts().isBlank()) continue;
            for (String part : cbd.getProducts().split("\\s+")) {
                String[] kv = part.split(":");
                if (kv.length == 2) {
                    String product = kv[0];
                    double qty = 0;
                    try { qty = Double.parseDouble(kv[1]); } catch (NumberFormatException ignored) {}
                    productSales.merge(product, new double[]{0, qty, cbd.getAmount() != null ? cbd.getAmount().doubleValue() : 0},
                            (old, n) -> new double[]{old[0], old[1] + n[1], old[2] + n[2]});
                }
            }
        }

        if (productSales.isEmpty()) return;

        container.addElement(sectionHeader("SALES SUMMARY"));
        PdfPTable table = new PdfPTable(new float[]{1.5f, 1f, 1f, 1f, 1.5f});
        table.setWidthPercentage(100);
        table.setSpacingAfter(1);

        addHeaderCell(table, "PRODUCT");
        addHeaderCell(table, "CASH");
        addHeaderCell(table, "CREDIT");
        addHeaderCell(table, "TOTAL");
        addHeaderCell(table, "AMOUNT");

        double totalCash = 0, totalCredit = 0, totalAll = 0, totalAmt = 0;
        for (Map.Entry<String, double[]> entry : productSales.entrySet()) {
            double[] vals = entry.getValue();
            double total = vals[0] + vals[1];
            addCellLeft(table, productDisplayFromAbbr(entry.getKey(), knownProducts), SMALL_FONT);
            addCellRight(table, fmt0(vals[0]), SMALL_FONT);
            addCellRight(table, fmt0(vals[1]), SMALL_FONT);
            addCellRight(table, fmt0(total), SMALL_FONT);
            addCellRight(table, fmtComma(BigDecimal.valueOf(vals[2]).setScale(2, java.math.RoundingMode.HALF_UP)), SMALL_FONT);
            totalCash += vals[0];
            totalCredit += vals[1];
            totalAll += total;
            totalAmt += vals[2];
        }

        addCellLeft(table, "TOTAL", SMALL_BOLD);
        addCellRight(table, fmt0(totalCash), SMALL_BOLD);
        addCellRight(table, fmt0(totalCredit), SMALL_BOLD);
        addCellRight(table, fmt0(totalAll), SMALL_BOLD);
        addCellRight(table, fmtComma(BigDecimal.valueOf(totalAmt).setScale(2, java.math.RoundingMode.HALF_UP)), SMALL_BOLD);

        container.addElement(table);
    }
}
