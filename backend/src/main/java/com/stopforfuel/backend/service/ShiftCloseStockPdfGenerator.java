package com.stopforfuel.backend.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.stopforfuel.backend.dto.StockShiftClosePayload;
import com.stopforfuel.backend.dto.StockShiftClosePayload.ProductRow;
import com.stopforfuel.backend.dto.StockShiftClosePayload.TankRow;
import com.stopforfuel.backend.exception.ReportGenerationException;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;

@Service
public class ShiftCloseStockPdfGenerator {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");
    private static final DecimalFormat NUM = new DecimalFormat("#,##0.00");

    private static final Font H1 = new Font(Font.HELVETICA, 14, Font.BOLD);
    private static final Font H2 = new Font(Font.HELVETICA, 11, Font.BOLD);
    private static final Font BODY = new Font(Font.HELVETICA, 9, Font.NORMAL);
    private static final Font BODY_BOLD = new Font(Font.HELVETICA, 9, Font.BOLD);
    private static final Font WARN = new Font(Font.HELVETICA, 9, Font.BOLD, new Color(192, 0, 0));

    private static final Color HEADER_BG = new Color(230, 230, 230);

    public byte[] generate(StockShiftClosePayload payload) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 30, 30, 30, 30);
            PdfWriter.getInstance(doc, out);
            doc.open();

            doc.add(new Paragraph(safe(payload.getCompanyName(), "StopForFuel"), H1));
            doc.add(new Paragraph("Shift-Close Stock Summary", H2));
            doc.add(new Paragraph(
                    "Shift #" + payload.getShiftId() + " · Closed: "
                            + (payload.getClosedAt() != null ? payload.getClosedAt().format(DT_FMT) : "—"),
                    BODY));
            doc.add(new Paragraph(" ", BODY));

            // Fuel tanks
            doc.add(new Paragraph("Fuel Tanks", H2));
            PdfPTable fuel = new PdfPTable(new float[]{ 1.4f, 1.6f, 1.2f, 1.2f, 1.2f, 0.8f });
            fuel.setWidthPercentage(100);
            addHeader(fuel, "Tank", "Product", "Stock (L)", "Sold (L)", "Price/L", "Status");
            if (payload.getTanks() == null || payload.getTanks().isEmpty()) {
                fuel.addCell(emptyRow(6, "No tanks configured"));
            } else {
                for (TankRow t : payload.getTanks()) {
                    addCell(fuel, safe(t.getTankName(), "—"), Element.ALIGN_LEFT, BODY);
                    addCell(fuel, safe(t.getProductName(), "—"), Element.ALIGN_LEFT, BODY);
                    addCell(fuel, NUM.format(t.getCurrentLiters()), Element.ALIGN_RIGHT, BODY);
                    addCell(fuel, NUM.format(t.getSoldLiters()), Element.ALIGN_RIGHT, BODY);
                    addCell(fuel, "₹ " + NUM.format(t.getPricePerLiter()), Element.ALIGN_RIGHT, BODY);
                    addCell(fuel, t.isLowStock() ? "LOW" : "OK", Element.ALIGN_CENTER, t.isLowStock() ? WARN : BODY);
                }
            }
            doc.add(fuel);
            doc.add(new Paragraph(" ", BODY));

            // Non-fuel products
            doc.add(new Paragraph("Non-Fuel Products", H2));
            PdfPTable prods = new PdfPTable(new float[]{ 2.6f, 0.8f, 1.2f, 1.2f, 1.2f, 0.8f });
            prods.setWidthPercentage(100);
            addHeader(prods, "Product", "Unit", "Stock", "Sold", "Price", "Status");
            if (payload.getProducts() == null || payload.getProducts().isEmpty()) {
                prods.addCell(emptyRow(6, "No non-fuel products tracked"));
            } else {
                for (ProductRow p : payload.getProducts()) {
                    addCell(prods, safe(p.getProductName(), "—"), Element.ALIGN_LEFT, BODY);
                    addCell(prods, safe(p.getUnit(), ""), Element.ALIGN_CENTER, BODY);
                    addCell(prods, NUM.format(p.getCurrentUnits()), Element.ALIGN_RIGHT, BODY);
                    addCell(prods, NUM.format(p.getSoldUnits()), Element.ALIGN_RIGHT, BODY);
                    addCell(prods, "₹ " + NUM.format(p.getPriceEach()), Element.ALIGN_RIGHT, BODY);
                    addCell(prods, p.isLowStock() ? "LOW" : "OK", Element.ALIGN_CENTER, p.isLowStock() ? WARN : BODY);
                }
            }
            doc.add(prods);

            if (payload.getLowStockCount() > 0) {
                doc.add(new Paragraph(" ", BODY));
                doc.add(new Paragraph(payload.getLowStockCount() + " item(s) flagged below threshold.", WARN));
            }

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new ReportGenerationException("Failed to generate shift-close stock PDF: " + e.getMessage(), e);
        }
    }

    private void addHeader(PdfPTable table, String... labels) {
        for (String label : labels) {
            PdfPCell cell = new PdfPCell(new Phrase(label, BODY_BOLD));
            cell.setBackgroundColor(HEADER_BG);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setPadding(4f);
            table.addCell(cell);
        }
    }

    private void addCell(PdfPTable table, String text, int align, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", font));
        cell.setHorizontalAlignment(align);
        cell.setPadding(3f);
        table.addCell(cell);
    }

    private PdfPCell emptyRow(int colspan, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, BODY));
        cell.setColspan(colspan);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPadding(8f);
        return cell;
    }

    private String safe(String s, String fallback) {
        return s == null || s.isBlank() ? fallback : s;
    }
}
