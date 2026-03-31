package com.stopforfuel.backend.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.stopforfuel.backend.dto.ShiftReportPrintData;
import com.stopforfuel.backend.dto.ShiftReportPrintData.*;
import com.stopforfuel.backend.entity.ReportLineItem;
import com.stopforfuel.backend.entity.ShiftClosingReport;
import com.stopforfuel.backend.exception.ReportGenerationException;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class ShiftReportPdfGenerator {

    // Fonts — compact for dense professional layout
    private static final Font COMPANY_FONT = new Font(Font.HELVETICA, 10, Font.BOLD);
    private static final Font ADDRESS_FONT = new Font(Font.HELVETICA, 6.5f, Font.NORMAL);
    private static final Font REPORT_TITLE_FONT = new Font(Font.HELVETICA, 8, Font.BOLD);
    private static final Font SECTION_FONT = new Font(Font.HELVETICA, 7, Font.BOLD);
    private static final Font HEADER_FONT = new Font(Font.HELVETICA, 5.5f, Font.BOLD);
    private static final Font NORMAL_FONT = new Font(Font.HELVETICA, 6, Font.NORMAL);
    private static final Font BOLD_FONT = new Font(Font.HELVETICA, 6, Font.BOLD);
    private static final Font SMALL_FONT = new Font(Font.HELVETICA, 5.5f, Font.NORMAL);
    private static final Font SMALL_BOLD = new Font(Font.HELVETICA, 5.5f, Font.BOLD);
    private static final Font TOTAL_FONT = new Font(Font.HELVETICA, 7, Font.BOLD);
    private static final Font FOOTER_FONT = new Font(Font.HELVETICA, 6, Font.NORMAL);
    private static final Font FOOTER_BOLD = new Font(Font.HELVETICA, 6, Font.BOLD);

    private static final Color HEADER_BG = new Color(230, 230, 230);
    private static final Color LIGHT_BG = new Color(245, 245, 245);
    private static final Color SECTION_BG = new Color(60, 60, 60);
    private static final Color WHITE = Color.WHITE;

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    public byte[] generate(ShiftReportPrintData data, ShiftClosingReport report) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 15, 15, 15, 15);

        try {
            PdfWriter.getInstance(document, baos);
            document.open();

            // ===== PAGE 1: Two-column layout =====
            addPageOneHeader(document, data);
            addPageOneBody(document, data, report);
            addPageOneFooter(document, data);

            // ===== PAGE 2: Detail pages =====
            document.newPage();
            addPageTwoHeader(document, data);
            addPageTwoBody(document, data, report);

            // ===== PAGE 3: Product Inventory (if any) =====
            if (!data.getStockSummary().isEmpty() || !data.getStockPosition().isEmpty()) {
                document.newPage();
                addPageThreeHeader(document, data);
                addProductInventoryPage(document, data);
            }

            document.close();
        } catch (DocumentException e) {
            throw new ReportGenerationException("Failed to generate shift report PDF", e);
        }

        return baos.toByteArray();
    }

    // ==================== PAGE 1 ====================

    private void addPageOneHeader(Document doc, ShiftReportPrintData data) throws DocumentException {
        // Company Name
        Paragraph company = new Paragraph(data.getCompanyName().toUpperCase(), COMPANY_FONT);
        company.setAlignment(Element.ALIGN_CENTER);
        doc.add(company);

        // Address
        if (data.getCompanyAddress() != null && !data.getCompanyAddress().isBlank()) {
            Paragraph addr = new Paragraph(data.getCompanyAddress(), ADDRESS_FONT);
            addr.setAlignment(Element.ALIGN_CENTER);
            doc.add(addr);
        }

        // GST
        if (data.getCompanyGstNo() != null && !data.getCompanyGstNo().isBlank()) {
            Paragraph gst = new Paragraph("GSTIN: " + data.getCompanyGstNo(), SMALL_FONT);
            gst.setAlignment(Element.ALIGN_CENTER);
            doc.add(gst);
        }

        // Report title
        Paragraph title = new Paragraph("SHIFT SALES REPORT", REPORT_TITLE_FONT);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingBefore(4);
        doc.add(title);

        // Date / Shift / Cashier info line
        String shiftDate = data.getShiftStart() != null ? data.getShiftStart().format(DATE_FMT) : "-";
        String shiftTime = (data.getShiftStart() != null ? data.getShiftStart().format(TIME_FMT) : "-")
                + " — " + (data.getShiftEnd() != null ? data.getShiftEnd().format(TIME_FMT) : "ongoing");
        String infoLine = shiftDate + "  |  Shift: " + shiftTime + "  |  Cashier: " + data.getEmployeeName();

        Paragraph info = new Paragraph(infoLine, NORMAL_FONT);
        info.setAlignment(Element.ALIGN_CENTER);
        info.setSpacingAfter(3);
        doc.add(info);
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

        addMeterwise(leftCell, data);
        addGrossNetSales(leftCell, data);
        addTankwise(leftCell, data);
        addSalesDifference(leftCell, data);
        addCashBillSales(leftCell, data);
        addStockReference(leftCell, data);

        outer.addCell(leftCell);

        // RIGHT COLUMN
        PdfPCell rightCell = new PdfPCell();
        rightCell.setBorder(Rectangle.NO_BORDER);
        rightCell.setPadding(0);
        rightCell.setPaddingLeft(3);

        addRevenue(rightCell, data, report);
        addAdvances(rightCell, data, report);
        addTurnoverBalanceBox(rightCell, report);
        addIncomeBills(rightCell, data);
        addCreditBillsSummary(rightCell, data);

        outer.addCell(rightCell);

        doc.add(outer);
    }

    // --- LEFT COLUMN SECTIONS ---

    private void addMeterwise(PdfPCell container, ShiftReportPrintData data) {
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
            pHeader.setSpacingBefore(2);
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

    private void addGrossNetSales(PdfPCell container, ShiftReportPrintData data) {
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
        grossTable.setSpacingAfter(3);

        addHeaderCell(grossTable, "");
        for (String p : products) addHeaderCell(grossTable, abbreviateProduct(p));

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
        addCellLeft(grossTable, "Gross", SMALL_BOLD);
        for (String p : products) addCellRight(grossTable, fmt2(meterByProduct.getOrDefault(p, 0.0)), SMALL_BOLD);

        container.addElement(grossTable);

        // NET SALES
        container.addElement(sectionHeader("NET SALES"));
        PdfPTable netTable = new PdfPTable(products.size() + 1);
        netTable.setWidthPercentage(100);
        netTable.setSpacingAfter(3);

        addHeaderCell(netTable, "");
        for (String p : products) addHeaderCell(netTable, abbreviateProduct(p));

        addCellLeft(netTable, "Gross", SMALL_FONT);
        for (String p : products) addCellRight(netTable, fmt2(meterByProduct.getOrDefault(p, 0.0)), SMALL_FONT);

        addCellLeft(netTable, "Testing", SMALL_FONT);
        for (String p : products) addCellRight(netTable, fmt2(testByProduct.getOrDefault(p, 0.0)), SMALL_FONT);

        addCellLeft(netTable, "Net", SMALL_BOLD);
        for (String p : products) {
            double net = meterByProduct.getOrDefault(p, 0.0) - testByProduct.getOrDefault(p, 0.0);
            addCellRight(netTable, fmt2(net), SMALL_BOLD);
        }

        container.addElement(netTable);
    }

    private void addTankwise(PdfPCell container, ShiftReportPrintData data) {
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

    private void addSalesDifference(PdfPCell container, ShiftReportPrintData data) {
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
            Font diffFont = sd.getDifference() != null && sd.getDifference() < 0 ? SMALL_BOLD : SMALL_FONT;
            addCellRight(table, fmt0(sd.getDifference()), diffFont);
        }

        container.addElement(table);
    }

    private void addCashBillSales(PdfPCell container, ShiftReportPrintData data) {
        if (data.getPaymentModeBreakdown().isEmpty()) return;

        container.addElement(sectionHeader("CASH BILL SALES"));
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
    }

    private void addStockReference(PdfPCell container, ShiftReportPrintData data) {
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

    // --- RIGHT COLUMN SECTIONS ---

    private void addRevenue(PdfPCell container, ShiftReportPrintData data, ShiftClosingReport report) {
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
        addCellLeft(table, "TOTAL", SMALL_BOLD);
        addCellRight(table, "", SMALL_BOLD);
        addCellRight(table, "", SMALL_BOLD);
        addCellRight(table, fmtComma(totalRevenue), SMALL_BOLD);

        container.addElement(table);
    }

    private void addAdvances(PdfPCell container, ShiftReportPrintData data, ShiftClosingReport report) {
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

        addCellLeft(table, "TOTAL", SMALL_BOLD);
        addCellRight(table, fmtComma(totalAdvances), SMALL_BOLD);

        container.addElement(table);
    }

    private void addTurnoverBalanceBox(PdfPCell container, ShiftClosingReport report) {
        PdfPTable box = new PdfPTable(3);
        box.setWidthPercentage(100);
        box.setSpacingBefore(2);
        box.setSpacingAfter(2);

        // Turnover
        PdfPCell turnCell = new PdfPCell();
        turnCell.setBorderColor(Color.DARK_GRAY);
        turnCell.setPadding(3);
        turnCell.addElement(new Paragraph("TURNOVER", SMALL_BOLD));
        turnCell.addElement(new Paragraph("Rs." + fmtComma(report.getTotalRevenue()), TOTAL_FONT));
        box.addCell(turnCell);

        // Balance
        PdfPCell balCell = new PdfPCell();
        balCell.setBorderColor(Color.DARK_GRAY);
        balCell.setPadding(3);
        balCell.addElement(new Paragraph("BALANCE", SMALL_BOLD));
        balCell.addElement(new Paragraph("Rs." + fmtComma(report.getBalance()), TOTAL_FONT));
        box.addCell(balCell);

        // Cash Bill
        PdfPCell cashCell = new PdfPCell();
        cashCell.setBorderColor(Color.DARK_GRAY);
        cashCell.setPadding(3);
        cashCell.addElement(new Paragraph("CASH BILL", SMALL_BOLD));
        cashCell.addElement(new Paragraph("Rs." + fmtComma(report.getCashBillAmount()), TOTAL_FONT));
        box.addCell(cashCell);

        container.addElement(box);
    }

    private void addIncomeBills(PdfPCell container, ShiftReportPrintData data) {
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

    private void addCreditBillsSummary(PdfPCell container, ShiftReportPrintData data) {
        if (data.getCreditBillDetails().isEmpty()) return;

        BigDecimal creditTotal = BigDecimal.ZERO;
        for (CreditBillDetail cbd : data.getCreditBillDetails()) {
            creditTotal = creditTotal.add(cbd.getAmount() != null ? cbd.getAmount() : BigDecimal.ZERO);
        }

        container.addElement(sectionHeader("CREDIT BILLS (" + data.getCreditBillDetails().size() + ") — Rs." + fmtComma(creditTotal)));
        PdfPTable table = new PdfPTable(new float[]{0.4f, 2f, 1.2f, 1.8f, 1.5f, 1.5f});
        table.setWidthPercentage(100);
        table.setSpacingAfter(1);

        addHeaderCell(table, "#");
        addHeaderCell(table, "CUSTOMER");
        addHeaderCell(table, "BILL");
        addHeaderCell(table, "VEHICLE");
        addHeaderCell(table, "PROD");
        addHeaderCell(table, "AMT");

        int idx = 1;
        for (CreditBillDetail cbd : data.getCreditBillDetails()) {
            addCellRight(table, String.valueOf(idx++), SMALL_FONT);
            addCellLeft(table, cbd.getCustomerName(), SMALL_FONT);
            addCellLeft(table, cbd.getBillNo(), SMALL_FONT);
            addCellLeft(table, cbd.getVehicleNo(), SMALL_FONT);
            addCellLeft(table, cbd.getProducts(), SMALL_FONT);
            addCellRight(table, fmtComma(cbd.getAmount()), SMALL_FONT);
        }

        // Total row
        addCellRight(table, "", SMALL_BOLD);
        addCellLeft(table, data.getCreditBillDetails().size() + " bills", SMALL_BOLD);
        addCellLeft(table, "", SMALL_BOLD);
        addCellLeft(table, "", SMALL_BOLD);
        addCellLeft(table, "", SMALL_BOLD);
        addCellRight(table, fmtComma(creditTotal), SMALL_BOLD);

        container.addElement(table);
    }

    private void addPageOneFooter(Document doc, ShiftReportPrintData data) throws DocumentException {
        PdfPTable footer = new PdfPTable(3);
        footer.setWidthPercentage(100);
        footer.setSpacingBefore(4);

        String shiftInfo = "SHIFT: " + (data.getShiftStart() != null ? data.getShiftStart().format(DT_FMT) : "-")
                + " — " + (data.getShiftEnd() != null ? data.getShiftEnd().format(DT_FMT) : "ongoing");

        PdfPCell left = new PdfPCell(new Phrase(shiftInfo, FOOTER_FONT));
        left.setBorder(Rectangle.TOP);
        left.setHorizontalAlignment(Element.ALIGN_LEFT);
        left.setPadding(3);
        footer.addCell(left);

        PdfPCell mid = new PdfPCell(new Phrase("CASHIER: " + data.getEmployeeName(), FOOTER_BOLD));
        mid.setBorder(Rectangle.TOP);
        mid.setHorizontalAlignment(Element.ALIGN_CENTER);
        mid.setPadding(3);
        footer.addCell(mid);

        PdfPCell right = new PdfPCell(new Phrase("Signature: __________________", FOOTER_FONT));
        right.setBorder(Rectangle.TOP);
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);
        right.setPadding(3);
        footer.addCell(right);

        doc.add(footer);
    }

    // ==================== PAGE 2 ====================

    private void addPageTwoHeader(Document doc, ShiftReportPrintData data) throws DocumentException {
        String shiftDate = data.getShiftStart() != null ? data.getShiftStart().format(DATE_FMT) : "-";
        Paragraph header = new Paragraph(
                data.getCompanyName().toUpperCase() + " — " + shiftDate + " Shift Report (Page 2) — CASHIER: " + data.getEmployeeName(),
                SECTION_FONT);
        header.setAlignment(Element.ALIGN_CENTER);
        header.setSpacingAfter(3);
        doc.add(header);
    }

    private void addPageTwoBody(Document doc, ShiftReportPrintData data, ShiftClosingReport report) throws DocumentException {
        // Two-column layout for page 2 details
        PdfPTable outer = new PdfPTable(2);
        outer.setWidthPercentage(100);
        outer.setWidths(new float[]{50, 50});

        PdfPCell leftCell = new PdfPCell();
        leftCell.setBorder(Rectangle.NO_BORDER);
        leftCell.setPadding(0);
        leftCell.setPaddingRight(3);

        PdfPCell rightCell = new PdfPCell();
        rightCell.setBorder(Rectangle.NO_BORDER);
        rightCell.setPadding(0);
        rightCell.setPaddingLeft(3);

        // Group advance entries by type
        Map<String, List<AdvanceEntryDetail>> advByType = new LinkedHashMap<>();
        for (AdvanceEntryDetail ae : data.getAdvanceEntries()) {
            advByType.computeIfAbsent(ae.getType(), k -> new ArrayList<>()).add(ae);
        }

        // LEFT: Card, UPI, CCMS, Cheque, Bank Transfer advances
        for (String type : List.of("CARD", "UPI", "CCMS", "CHEQUE", "BANK_TRANSFER")) {
            List<AdvanceEntryDetail> entries = advByType.get(type);
            if (entries != null && !entries.isEmpty()) {
                addAdvanceDetailTable(leftCell, getAdvanceLabel(type), entries);
            }
        }

        // RIGHT: Cash Advance, Home Advance, Salary Advance, Expenses, Incentive, Repayment
        for (String type : List.of("CASH_ADVANCE", "HOME_ADVANCE", "SALARY_ADVANCE", "EXPENSE", "INCENTIVE", "REPAYMENT")) {
            List<AdvanceEntryDetail> entries = advByType.get(type);
            if (entries != null && !entries.isEmpty()) {
                addAdvanceDetailTable(rightCell, getAdvanceLabel(type), entries);
            }
        }

        outer.addCell(leftCell);
        outer.addCell(rightCell);
        doc.add(outer);
    }

    private void addAdvanceDetailTable(PdfPCell container, String title, List<AdvanceEntryDetail> entries) {
        BigDecimal total = BigDecimal.ZERO;
        for (AdvanceEntryDetail e : entries) {
            total = total.add(e.getAmount() != null ? e.getAmount() : BigDecimal.ZERO);
        }

        container.addElement(sectionHeader(title + " (" + entries.size() + ") — Rs." + fmtComma(total)));

        PdfPTable table = new PdfPTable(new float[]{0.5f, 3.5f, 2f});
        table.setWidthPercentage(100);
        table.setSpacingAfter(1);

        addHeaderCell(table, "#");
        addHeaderCell(table, "DESCRIPTION");
        addHeaderCell(table, "AMOUNT");

        int idx = 1;
        for (AdvanceEntryDetail entry : entries) {
            addCellRight(table, String.valueOf(idx++), SMALL_FONT);
            String desc = entry.getDescription() != null ? entry.getDescription() : "-";
            if (entry.getReference() != null && !entry.getReference().isBlank()) {
                desc += " [" + entry.getReference() + "]";
            }
            addCellLeft(table, desc, SMALL_FONT);
            addCellRight(table, fmtComma(entry.getAmount()), SMALL_FONT);
        }

        addCellRight(table, "", SMALL_BOLD);
        addCellLeft(table, "Total", SMALL_BOLD);
        addCellRight(table, fmtComma(total), SMALL_BOLD);

        container.addElement(table);
    }

    // ==================== PAGE 3 ====================

    private void addPageThreeHeader(Document doc, ShiftReportPrintData data) throws DocumentException {
        String shiftDate = data.getShiftStart() != null ? data.getShiftStart().format(DATE_FMT) : "-";
        Paragraph header = new Paragraph(
                data.getCompanyName().toUpperCase() + " — " + shiftDate + " Shift Report (Page 3 — Product Inventory)",
                SECTION_FONT);
        header.setAlignment(Element.ALIGN_CENTER);
        header.setSpacingAfter(3);
        doc.add(header);
    }

    private void addProductInventoryPage(Document doc, ShiftReportPrintData data) throws DocumentException {
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
                Font f = row.isLowStock() ? SMALL_BOLD : SMALL_FONT;
                addCellLeft(table, row.getProductName() + (row.isLowStock() ? " [LOW]" : ""), f);
                addCellRight(table, fmt2(row.getGodownStock()), f);
                addCellRight(table, fmt2(row.getCashierStock()), f);
                addCellRight(table, fmt2(row.getTotalStock()), f);
            }

            doc.add(table);
        }

        // Generation timestamp
        Paragraph gen = new Paragraph("Generated: " + java.time.LocalDateTime.now().format(DT_FMT), SMALL_FONT);
        gen.setSpacingBefore(10);
        doc.add(gen);
    }

    // ==================== HELPERS ====================

    private Paragraph sectionHeader(String text) {
        Paragraph p = new Paragraph(text, SECTION_FONT);
        p.setSpacingBefore(2);
        p.setSpacingAfter(1);
        return p;
    }

    private void addHeaderCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, HEADER_FONT));
        cell.setBackgroundColor(HEADER_BG);
        cell.setPadding(2);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setBorderWidth(0.25f);
        cell.setBorderColor(Color.GRAY);
        table.addCell(cell);
    }

    private void addCellLeft(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "-", font));
        cell.setPadding(1.5f);
        cell.setHorizontalAlignment(Element.ALIGN_LEFT);
        cell.setBorderWidth(0.25f);
        cell.setBorderColor(Color.LIGHT_GRAY);
        table.addCell(cell);
    }

    private void addCellRight(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "-", font));
        cell.setPadding(1.5f);
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        cell.setBorderWidth(0.25f);
        cell.setBorderColor(Color.LIGHT_GRAY);
        table.addCell(cell);
    }

    private void addPlainCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(1.5f);
        table.addCell(cell);
    }

    private String fmt0(Double val) {
        if (val == null) return "0";
        if (val == Math.floor(val) && !Double.isInfinite(val)) return String.format("%.0f", val);
        return String.format("%.2f", val);
    }

    private String fmt2(Double val) {
        if (val == null) return "0.00";
        return String.format("%.2f", val);
    }

    private String fmtBD(BigDecimal val) {
        if (val == null) return "0.00";
        return val.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String fmtComma(BigDecimal val) {
        if (val == null) return "0.00";
        return formatIndianNumber(val.setScale(2, RoundingMode.HALF_UP).doubleValue());
    }

    private String fmtComma(double val) {
        return formatIndianNumber(val);
    }

    private String formatIndianNumber(double val) {
        if (val == 0) return "0.00";
        boolean negative = val < 0;
        val = Math.abs(val);

        String formatted = String.format("%.2f", val);
        String[] parts = formatted.split("\\.");
        String wholePart = parts[0];
        String decimalPart = parts.length > 1 ? parts[1] : "00";

        StringBuilder sb = new StringBuilder();
        int len = wholePart.length();
        if (len <= 3) {
            sb.append(wholePart);
        } else {
            // Last 3 digits
            sb.insert(0, wholePart.substring(len - 3));
            int remaining = len - 3;
            int pos = remaining;
            while (pos > 0) {
                int start = Math.max(0, pos - 2);
                sb.insert(0, ",");
                sb.insert(0, wholePart.substring(start, pos));
                pos = start;
            }
        }
        sb.append(".").append(decimalPart);
        if (negative) sb.insert(0, "-");
        return sb.toString();
    }

    private String getAdvanceLabel(String type) {
        if (type == null) return "Other";
        return switch (type) {
            case "CARD" -> "Card Advance";
            case "UPI" -> "UPI Advance";
            case "CCMS" -> "CCMS Advance";
            case "CHEQUE" -> "Cheque";
            case "BANK_TRANSFER" -> "Bank Transfer";
            case "CASH_ADVANCE", "CASH" -> "Cash Advance";
            case "HOME_ADVANCE", "HOME" -> "Home Advance";
            case "SALARY_ADVANCE" -> "Salary Advance";
            case "EXPENSE" -> "Expenses";
            case "INCENTIVE" -> "Incentive";
            case "REPAYMENT" -> "Inflow Repayment";
            case "EXTERNAL_INFLOW" -> "External Inflow";
            default -> type;
        };
    }

    private String abbreviateProduct(String name) {
        if (name == null) return "?";
        String upper = name.toUpperCase();
        if (upper.contains("PETROL") || upper.equals("MS")) return "MS";
        if (upper.contains("XTRA") || upper.contains("XP") || upper.contains("PREMIUM")) return "XP";
        if (upper.contains("DIESEL") || upper.equals("HSD") || upper.contains("HIGH SPEED")) return "HSD";
        return name.length() > 5 ? name.substring(0, 5) : name;
    }

    private String expandAbbreviation(String abbr, Set<String> productNames) {
        if (abbr == null) return null;
        String upper = abbr.toUpperCase();
        for (String name : productNames) {
            String nameUpper = name.toUpperCase();
            if (upper.equals("P") || upper.equals("MS") || upper.equals("PET")) {
                if (nameUpper.contains("PETROL") || nameUpper.equals("MS")) return name;
            }
            if (upper.equals("XP")) {
                if (nameUpper.contains("XTRA") || nameUpper.contains("XP") || nameUpper.contains("PREMIUM")) return name;
            }
            if (upper.equals("HSD") || upper.equals("D")) {
                if (nameUpper.contains("DIESEL") || nameUpper.equals("HSD") || nameUpper.contains("HIGH SPEED")) return name;
            }
        }
        // Exact match fallback
        for (String name : productNames) {
            if (name.equalsIgnoreCase(abbr)) return name;
        }
        return null;
    }
}
