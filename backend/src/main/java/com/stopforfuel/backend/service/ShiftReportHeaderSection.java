package com.stopforfuel.backend.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.stopforfuel.backend.dto.ShiftReportPrintData;

import static com.stopforfuel.backend.service.ShiftReportPdfUtils.*;

/**
 * Renders header and footer sections of the shift report PDF.
 */
public class ShiftReportHeaderSection {

    public void addPageOneHeader(Document doc, ShiftReportPrintData data) throws DocumentException {
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

    public void addPageOneFooter(Document doc, ShiftReportPrintData data) throws DocumentException {
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

    public void addPageTwoHeader(Document doc, ShiftReportPrintData data) throws DocumentException {
        String shiftDate = data.getShiftStart() != null ? data.getShiftStart().format(DATE_FMT) : "-";
        Paragraph header = new Paragraph(
                data.getCompanyName().toUpperCase() + " — " + shiftDate + " Shift Report (Page 2) — CASHIER: " + data.getEmployeeName(),
                SECTION_FONT);
        header.setAlignment(Element.ALIGN_CENTER);
        header.setSpacingAfter(3);
        doc.add(header);
    }

    public void addPageThreeHeader(Document doc, ShiftReportPrintData data) throws DocumentException {
        String shiftDate = data.getShiftStart() != null ? data.getShiftStart().format(DATE_FMT) : "-";
        Paragraph header = new Paragraph(
                data.getCompanyName().toUpperCase() + " — " + shiftDate + " Shift Report (Page 3 — Product Inventory)",
                SECTION_FONT);
        header.setAlignment(Element.ALIGN_CENTER);
        header.setSpacingAfter(3);
        doc.add(header);
    }
}
