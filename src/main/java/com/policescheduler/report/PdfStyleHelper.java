package com.policescheduler.report;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
public class PdfStyleHelper {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final Color HEADER_BG = new Color(51, 51, 51);

    public Font getTitleFont() {
        return FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
    }

    public Font getSectionHeaderFont() {
        return FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
    }

    public Font getTableHeaderFont() {
        return FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Font.BOLD, Color.WHITE);
    }

    public Font getCellFont() {
        return FontFactory.getFont(FontFactory.HELVETICA, 8);
    }

    /**
     * Returns a font suitable for Kannada script rendering.
     * For now, returns a fallback font. Noto Sans Kannada would need to be
     * embedded as a resource for full Kannada support.
     */
    public Font getKannadaFont() {
        return FontFactory.getFont(FontFactory.HELVETICA, 9);
    }

    public PdfPCell createHeaderCell(String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, getTableHeaderFont()));
        cell.setBackgroundColor(HEADER_BG);
        cell.setPadding(5);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return cell;
    }

    public PdfPCell createCell(String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, getCellFont()));
        cell.setPadding(4);
        return cell;
    }

    public PdfPCell createCell(String text, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, getCellFont()));
        cell.setPadding(4);
        cell.setHorizontalAlignment(alignment);
        return cell;
    }

    public void addTitle(Document document, String title) throws DocumentException {
        Paragraph paragraph = new Paragraph(title, getTitleFont());
        paragraph.setAlignment(Element.ALIGN_CENTER);
        paragraph.setSpacingAfter(10);
        document.add(paragraph);
    }

    public void addSectionHeader(Document document, String header) throws DocumentException {
        Paragraph paragraph = new Paragraph(header, getSectionHeaderFont());
        paragraph.setAlignment(Element.ALIGN_LEFT);
        paragraph.setSpacingBefore(8);
        paragraph.setSpacingAfter(4);
        document.add(paragraph);
    }

    public void addGenerationDate(Document document, LocalDate date) throws DocumentException {
        String dateStr = "Date: " + date.format(DATE_FORMAT);
        Paragraph paragraph = new Paragraph(dateStr, getCellFont());
        paragraph.setAlignment(Element.ALIGN_RIGHT);
        paragraph.setSpacingAfter(8);
        document.add(paragraph);
    }
}
