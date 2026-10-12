package com.thomas.order_management.service.sales.intake;

import com.thomas.order_management.exception.BusinessException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;

import java.io.IOException;

/**
 * Text aus einem PDF lesen (nur Textebene, keine Texterkennung in Bildern). Die Ausgabe folgt der Position
 * auf der Seite, damit Tabellenzeilen zusammenbleiben und Zellen durch Leerzeichen getrennt sind.
 */
final class PdfText {

    private static final int MAX_PAGES = 10;

    private PdfText() {
    }

    static String extract(byte[] pdf) {
        String text;
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setEndPage(MAX_PAGES);
            text = stripper.getText(document).strip();
        } catch (IOException | RuntimeException ex) {
            throw new BusinessException("ZI", "001", "Die Datei ist kein lesbares PDF", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (text.isEmpty()) {
            throw new BusinessException("ZI", "002", "Das PDF enthält keinen lesbaren Text (z. B. ein Scan)",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        return text;
    }
}
