package com.stopforfuel.backend.service;

import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;

import java.awt.Color;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;

/**
 * Shared PDF drawing utilities and constants for shift report generation.
 */
public final class ShiftReportPdfUtils {

    private ShiftReportPdfUtils() {}

    // Fonts — ultra-compact for dense 2-page layout
    public static final Font COMPANY_FONT = new Font(Font.HELVETICA, 9, Font.BOLD);
    public static final Font ADDRESS_FONT = new Font(Font.HELVETICA, 5.5f, Font.NORMAL);
    public static final Font REPORT_TITLE_FONT = new Font(Font.HELVETICA, 8, Font.BOLD);
    public static final Font SECTION_FONT = new Font(Font.HELVETICA, 7, Font.BOLD, Color.WHITE);
    public static final Font HEADER_FONT = new Font(Font.HELVETICA, 6, Font.BOLD);
    public static final Font NORMAL_FONT = new Font(Font.HELVETICA, 7, Font.NORMAL);
    public static final Font BOLD_FONT = new Font(Font.HELVETICA, 7, Font.BOLD);
    public static final Font SMALL_FONT = new Font(Font.HELVETICA, 6.5f, Font.NORMAL);
    public static final Font SMALL_BOLD = new Font(Font.HELVETICA, 6.5f, Font.BOLD);
    public static final Font TOTAL_FONT = new Font(Font.HELVETICA, 7, Font.BOLD);
    public static final Font FOOTER_FONT = new Font(Font.HELVETICA, 5.5f, Font.NORMAL);
    public static final Font FOOTER_BOLD = new Font(Font.HELVETICA, 5.5f, Font.BOLD);

    public static final Color HEADER_BG = new Color(224, 224, 224);   // #e0e0e0
    public static final Color LIGHT_BG = new Color(245, 245, 245);
    public static final Color SECTION_BG = new Color(34, 34, 34);    // #222
    public static final Color TOTAL_BG = new Color(208, 208, 208);   // #d0d0d0
    public static final Color WHITE = Color.WHITE;
    public static final Color DIFF_RED = new Color(204, 0, 0);       // negative differences

    public static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    public static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    public static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Returns a dark-background section header as a single-cell table (matches mockup .st class).
     */
    public static PdfPTable sectionHeader(String text) {
        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(100);
        t.setSpacingBefore(3);
        t.setSpacingAfter(1);
        PdfPCell c = new PdfPCell(new Phrase(text.toUpperCase(), SECTION_FONT));
        c.setBackgroundColor(SECTION_BG);
        c.setPadding(2);
        c.setPaddingLeft(4);
        c.setHorizontalAlignment(Element.ALIGN_LEFT);
        c.setBorderWidth(0);
        t.addCell(c);
        return t;
    }

    public static void addHeaderCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, HEADER_FONT));
        cell.setBackgroundColor(HEADER_BG);
        cell.setPadding(1);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setBorderWidth(0.25f);
        cell.setBorderColor(Color.GRAY);
        table.addCell(cell);
    }

    public static void addCellLeft(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "-", font));
        cell.setPadding(1);
        cell.setHorizontalAlignment(Element.ALIGN_LEFT);
        cell.setBorderWidth(0.25f);
        cell.setBorderColor(Color.LIGHT_GRAY);
        table.addCell(cell);
    }

    public static void addCellRight(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "-", font));
        cell.setPadding(1);
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        cell.setBorderWidth(0.25f);
        cell.setBorderColor(Color.LIGHT_GRAY);
        table.addCell(cell);
    }

    public static void addTotalCellLeft(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", SMALL_BOLD));
        cell.setBackgroundColor(TOTAL_BG);
        cell.setPadding(1.5f);
        cell.setHorizontalAlignment(Element.ALIGN_LEFT);
        cell.setBorderWidthTop(1f);
        cell.setBorderWidthBottom(1f);
        cell.setBorderColor(Color.BLACK);
        table.addCell(cell);
    }

    public static void addTotalCellRight(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", SMALL_BOLD));
        cell.setBackgroundColor(TOTAL_BG);
        cell.setPadding(1.5f);
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        cell.setBorderWidthTop(1f);
        cell.setBorderWidthBottom(1f);
        cell.setBorderColor(Color.BLACK);
        table.addCell(cell);
    }

    public static void addPlainCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(1);
        table.addCell(cell);
    }

    public static String fmt0(Double val) {
        if (val == null) return "0";
        if (val == Math.floor(val) && !Double.isInfinite(val)) return String.format("%.0f", val);
        return String.format("%.2f", val);
    }

    public static String fmt2(Double val) {
        if (val == null) return "0.00";
        return String.format("%.2f", val);
    }

    public static String fmtBD(BigDecimal val) {
        if (val == null) return "0.00";
        return val.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    public static String fmtComma(BigDecimal val) {
        if (val == null) return "0.00";
        return formatIndianNumber(val.setScale(2, RoundingMode.HALF_UP).doubleValue());
    }

    public static String fmtComma(double val) {
        return formatIndianNumber(val);
    }

    public static String formatIndianNumber(double val) {
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

    public static String getAdvanceLabel(String type) {
        if (type == null) return "Other";
        return switch (type) {
            case "CARD" -> "Card Advance";
            case "UPI" -> "UPI Advance";
            case "CCMS" -> "CCMS Advance";
            case "CHEQUE" -> "Cheque";
            case "BANK_TRANSFER" -> "Bank Transfer";
            case "CASH_ADVANCE", "CASH" -> "Cash Advance";
            case "HOME_ADVANCE", "HOME" -> "Home Advance";
            case "SALARY_ADVANCE", "SALARY" -> "Salary Advance";
            case "EXPENSE" -> "Expenses";
            case "TEST" -> "Test";
            case "INCENTIVE" -> "Incentive";
            case "REPAYMENT" -> "Inflow Repayment";
            case "EXTERNAL_INFLOW" -> "External Inflow";
            default -> type;
        };
    }

    public static String abbreviateProduct(String name) {
        if (name == null) return "?";
        String upper = name.toUpperCase();
        if (upper.contains("PETROL") || upper.equals("MS")) return "MS";
        if (upper.contains("XTRA") || upper.contains("XP") || upper.contains("PREMIUM")) return "XP";
        if (upper.contains("DIESEL") || upper.equals("HSD") || upper.contains("HIGH SPEED")) return "HSD";
        return name.length() > 5 ? name.substring(0, 5) : name;
    }

    public static String expandAbbreviation(String abbr, java.util.Set<String> productNames) {
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
