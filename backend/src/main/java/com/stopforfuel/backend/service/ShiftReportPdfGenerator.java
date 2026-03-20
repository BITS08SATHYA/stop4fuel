package com.stopforfuel.backend.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.stopforfuel.backend.dto.ShiftReportPrintData;
import com.stopforfuel.backend.dto.ShiftReportPrintData.*;
import com.stopforfuel.backend.entity.ReportLineItem;
import com.stopforfuel.backend.entity.ShiftClosingReport;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class ShiftReportPdfGenerator {

    private static final Font TITLE_FONT = new Font(Font.HELVETICA, 14, Font.BOLD);
    private static final Font SECTION_FONT = new Font(Font.HELVETICA, 11, Font.BOLD);
    private static final Font HEADER_FONT = new Font(Font.HELVETICA, 8, Font.BOLD);
    private static final Font NORMAL_FONT = new Font(Font.HELVETICA, 8, Font.NORMAL);
    private static final Font BOLD_FONT = new Font(Font.HELVETICA, 8, Font.BOLD);
    private static final Font SMALL_FONT = new Font(Font.HELVETICA, 7, Font.NORMAL);
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm");
    private static final Color HEADER_BG = new Color(230, 230, 230);

    public byte[] generate(ShiftReportPrintData data, ShiftClosingReport report) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        Document document = new Document(PageSize.A4, 25, 25, 25, 25);
        try {
            PdfWriter.getInstance(document, baos);
            document.open();

            // --- HEADER ---
            Paragraph title = new Paragraph(data.getCompanyName() + " — Shift Closing Report", TITLE_FONT);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);

            PdfPTable headerInfo = new PdfPTable(4);
            headerInfo.setWidthPercentage(100);
            headerInfo.setSpacingBefore(5);
            headerInfo.setSpacingAfter(8);
            addPlainCell(headerInfo, "Shift #" + data.getShiftId(), BOLD_FONT);
            addPlainCell(headerInfo, "Employee: " + data.getEmployeeName(), NORMAL_FONT);
            addPlainCell(headerInfo, "Start: " + (data.getShiftStart() != null ? data.getShiftStart().format(DT_FMT) : "-"), NORMAL_FONT);
            addPlainCell(headerInfo, "End: " + (data.getShiftEnd() != null ? data.getShiftEnd().format(DT_FMT) : "-"), NORMAL_FONT);
            document.add(headerInfo);

            // --- METER READINGS ---
            if (!data.getMeterReadings().isEmpty()) {
                document.add(sectionTitle("Meter Readings"));
                PdfPTable table = new PdfPTable(new float[]{2f, 2f, 2f, 1.5f, 1.5f, 1.5f});
                table.setWidthPercentage(100);
                addHeaders(table, "Pump", "Nozzle", "Product", "Open", "Close", "Sales");
                for (MeterReading mr : data.getMeterReadings()) {
                    addCell(table, mr.getPumpName());
                    addCell(table, mr.getNozzleName());
                    addCell(table, mr.getProductName());
                    addCellRight(table, fmt(mr.getOpenReading()));
                    addCellRight(table, fmt(mr.getCloseReading()));
                    addCellRight(table, fmt(mr.getSales()));
                }
                document.add(table);
            }

            // --- TANK READINGS ---
            if (!data.getTankReadings().isEmpty()) {
                document.add(sectionTitle("Tank Readings"));
                PdfPTable table = new PdfPTable(new float[]{2f, 2f, 1.2f, 1.2f, 1.2f, 1.2f, 1.2f, 1.2f, 1.2f});
                table.setWidthPercentage(100);
                addHeaders(table, "Tank", "Product", "Open Dip", "Open Stock", "Receipt", "Total", "Close Dip", "Close Stock", "Sale");
                for (TankReading tr : data.getTankReadings()) {
                    addCell(table, tr.getTankName());
                    addCell(table, tr.getProductName());
                    addCell(table, tr.getOpenDip() != null ? tr.getOpenDip() : "-");
                    addCellRight(table, fmt(tr.getOpenStock()));
                    addCellRight(table, fmt(tr.getIncomeStock()));
                    addCellRight(table, fmt(tr.getTotalStock()));
                    addCell(table, tr.getCloseDip() != null ? tr.getCloseDip() : "-");
                    addCellRight(table, fmt(tr.getCloseStock()));
                    addCellRight(table, fmt(tr.getSaleStock()));
                }
                document.add(table);
            }

            // --- SALES DIFFERENCE ---
            if (!data.getSalesDifferences().isEmpty()) {
                document.add(sectionTitle("Sales Difference"));
                PdfPTable table = new PdfPTable(new float[]{3f, 2f, 2f, 2f});
                table.setWidthPercentage(70);
                addHeaders(table, "Product", "Tank Sale", "Meter Sale", "Difference");
                for (SalesDifference sd : data.getSalesDifferences()) {
                    addCell(table, sd.getProductName());
                    addCellRight(table, fmt(sd.getTankSale()));
                    addCellRight(table, fmt(sd.getMeterSale()));
                    addCellRight(table, fmt(sd.getDifference()));
                }
                document.add(table);
            }

            // --- REVENUE & ADVANCE SUMMARY ---
            document.add(sectionTitle("Revenue & Advance Summary"));
            List<ReportLineItem> lineItems = report.getLineItems();
            if (lineItems != null && !lineItems.isEmpty()) {
                PdfPTable table = new PdfPTable(new float[]{1f, 3f, 2f});
                table.setWidthPercentage(70);
                addHeaders(table, "Section", "Item", "Amount");

                BigDecimal totalRevenue = BigDecimal.ZERO;
                BigDecimal totalAdvance = BigDecimal.ZERO;

                for (ReportLineItem item : lineItems) {
                    if (item.getTransferredToReportId() != null) continue;
                    addCell(table, item.getSection());
                    addCell(table, item.getLabel());
                    addCellRight(table, item.getAmount() != null ? item.getAmount().toPlainString() : "0");
                    if ("REVENUE".equals(item.getSection())) {
                        totalRevenue = totalRevenue.add(item.getAmount() != null ? item.getAmount() : BigDecimal.ZERO);
                    } else {
                        totalAdvance = totalAdvance.add(item.getAmount() != null ? item.getAmount() : BigDecimal.ZERO);
                    }
                }

                // Totals row
                addCellBold(table, "");
                addCellBold(table, "Total Revenue");
                addCellRightBold(table, totalRevenue.toPlainString());
                addCellBold(table, "");
                addCellBold(table, "Total Advance");
                addCellRightBold(table, totalAdvance.toPlainString());
                addCellBold(table, "");
                addCellBold(table, "Balance (Cash In Hand)");
                addCellRightBold(table, totalRevenue.subtract(totalAdvance).toPlainString());

                document.add(table);
            }

            // --- CREDIT BILL DETAILS ---
            if (!data.getCreditBillDetails().isEmpty()) {
                document.add(sectionTitle("Credit Bill Details"));
                PdfPTable table = new PdfPTable(new float[]{3f, 1.5f, 2f, 2f, 2f});
                table.setWidthPercentage(100);
                addHeaders(table, "Customer", "Bill No", "Vehicle", "Products", "Amount");
                for (CreditBillDetail cbd : data.getCreditBillDetails()) {
                    addCell(table, cbd.getCustomerName());
                    addCell(table, cbd.getBillNo());
                    addCell(table, cbd.getVehicleNo());
                    addCell(table, cbd.getProducts());
                    addCellRight(table, cbd.getAmount() != null ? cbd.getAmount().toPlainString() : "0");
                }
                document.add(table);
            }

            // --- STOCK SUMMARY ---
            if (!data.getStockSummary().isEmpty()) {
                document.add(sectionTitle("Stock Summary"));
                PdfPTable table = new PdfPTable(new float[]{2.5f, 1.2f, 1.2f, 1.2f, 1.2f, 1.2f, 1.5f});
                table.setWidthPercentage(100);
                addHeaders(table, "Product", "Open", "Receipt", "Total", "Sales", "Rate", "Amount");
                for (StockSummaryRow row : data.getStockSummary()) {
                    addCell(table, row.getProductName());
                    addCellRight(table, fmt(row.getOpenStock()));
                    addCellRight(table, fmt(row.getReceipt()));
                    addCellRight(table, fmt(row.getTotalStock()));
                    addCellRight(table, fmt(row.getSales()));
                    addCellRight(table, row.getRate() != null ? row.getRate().toPlainString() : "-");
                    addCellRight(table, row.getAmount() != null ? row.getAmount().toPlainString() : "0");
                }
                document.add(table);
            }

            // --- ADVANCE ENTRY DETAILS ---
            if (!data.getAdvanceEntries().isEmpty()) {
                document.add(sectionTitle("Advance Entry Details"));
                PdfPTable table = new PdfPTable(new float[]{1.5f, 4f, 2f});
                table.setWidthPercentage(80);
                addHeaders(table, "Type", "Description", "Amount");
                for (AdvanceEntryDetail entry : data.getAdvanceEntries()) {
                    addCell(table, entry.getType());
                    addCell(table, entry.getDescription());
                    addCellRight(table, entry.getAmount() != null ? entry.getAmount().toPlainString() : "0");
                }
                document.add(table);
            }

            // --- PAYMENT ENTRIES ---
            if (!data.getPaymentEntries().isEmpty()) {
                document.add(sectionTitle("Payment Entries"));
                PdfPTable table = new PdfPTable(new float[]{1f, 2.5f, 2f, 1.5f, 2f});
                table.setWidthPercentage(100);
                addHeaders(table, "Type", "Customer", "Reference", "Mode", "Amount");
                for (PaymentEntryDetail pe : data.getPaymentEntries()) {
                    addCell(table, pe.getType());
                    addCell(table, pe.getCustomerName());
                    addCell(table, pe.getReference());
                    addCell(table, pe.getPaymentMode());
                    addCellRight(table, pe.getAmount() != null ? pe.getAmount().toPlainString() : "0");
                }
                document.add(table);
            }

            // --- STOCK POSITION ---
            if (!data.getStockPosition().isEmpty()) {
                document.add(sectionTitle("Stock Position"));
                PdfPTable table = new PdfPTable(new float[]{3f, 2f, 2f, 2f});
                table.setWidthPercentage(70);
                addHeaders(table, "Product", "Godown", "Cashier", "Total");
                for (StockPositionRow row : data.getStockPosition()) {
                    addCell(table, row.getProductName() + (row.isLowStock() ? " [LOW]" : ""));
                    addCellRight(table, fmt(row.getGodownStock()));
                    addCellRight(table, fmt(row.getCashierStock()));
                    addCellRight(table, fmt(row.getTotalStock()));
                }
                document.add(table);
            }

            document.close();
        } catch (DocumentException e) {
            throw new RuntimeException("Failed to generate shift report PDF", e);
        }

        return baos.toByteArray();
    }

    // --- Helpers ---

    private Paragraph sectionTitle(String text) {
        Paragraph p = new Paragraph(text, SECTION_FONT);
        p.setSpacingBefore(10);
        p.setSpacingAfter(4);
        return p;
    }

    private void addHeaders(PdfPTable table, String... headers) {
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, HEADER_FONT));
            cell.setBackgroundColor(HEADER_BG);
            cell.setPadding(4);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }
    }

    private void addCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "-", SMALL_FONT));
        cell.setPadding(3);
        table.addCell(cell);
    }

    private void addCellRight(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, SMALL_FONT));
        cell.setPadding(3);
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(cell);
    }

    private void addCellBold(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, BOLD_FONT));
        cell.setPadding(3);
        table.addCell(cell);
    }

    private void addCellRightBold(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, BOLD_FONT));
        cell.setPadding(3);
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(cell);
    }

    private void addPlainCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(2);
        table.addCell(cell);
    }

    private String fmt(Double val) {
        if (val == null) return "-";
        return String.format("%.2f", val);
    }
}
