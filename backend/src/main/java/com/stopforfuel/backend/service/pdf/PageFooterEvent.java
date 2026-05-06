package com.stopforfuel.backend.service.pdf;

import com.lowagie.text.Document;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;
import com.stopforfuel.backend.exception.ReportGenerationException;

import java.awt.Color;

/**
 * Shared "Page X of Y" footer for OpenPDF reports.
 *
 * Two-pass rendering: total page count is unknown until close, so {@link #onOpenDocument}
 * reserves a {@link PdfTemplate} that gets stamped during {@link #onCloseDocument}.
 *
 * Attach once per writer:
 * <pre>
 *   PdfWriter writer = PdfWriter.getInstance(doc, baos);
 *   writer.setPageEvent(new PageFooterEvent());
 *   doc.open();
 * </pre>
 */
public class PageFooterEvent extends PdfPageEventHelper {

    private static final Color MUTED = new Color(108, 117, 125);
    private static final float FONT_SIZE = 8f;
    private static final float TEMPLATE_WIDTH = 22f;

    private PdfTemplate totalPagesTemplate;
    private BaseFont baseFont;

    @Override
    public void onOpenDocument(PdfWriter writer, Document document) {
        try {
            baseFont = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            totalPagesTemplate = writer.getDirectContent().createTemplate(TEMPLATE_WIDTH, FONT_SIZE + 2);
        } catch (Exception e) {
            throw new ReportGenerationException("Failed to init PDF page footer", e);
        }
    }

    @Override
    public void onEndPage(PdfWriter writer, Document document) {
        PdfContentByte cb = writer.getDirectContent();
        String prefix = "Page " + writer.getPageNumber() + " of ";
        float prefixWidth = baseFont.getWidthPoint(prefix, FONT_SIZE);
        float totalWidth = prefixWidth + TEMPLATE_WIDTH;
        float pageWidth = document.right() - document.left();
        float x = document.left() + (pageWidth - totalWidth) / 2f;
        float y = document.bottom() - 10;

        cb.saveState();
        cb.beginText();
        cb.setFontAndSize(baseFont, FONT_SIZE);
        cb.setColorFill(MUTED);
        cb.setTextMatrix(x, y);
        cb.showText(prefix);
        cb.endText();
        cb.addTemplate(totalPagesTemplate, x + prefixWidth, y);
        cb.restoreState();
    }

    @Override
    public void onCloseDocument(PdfWriter writer, Document document) {
        totalPagesTemplate.beginText();
        totalPagesTemplate.setFontAndSize(baseFont, FONT_SIZE);
        totalPagesTemplate.setColorFill(MUTED);
        totalPagesTemplate.setTextMatrix(0, 0);
        totalPagesTemplate.showText(String.valueOf(writer.getPageNumber() - 1));
        totalPagesTemplate.endText();
    }
}
