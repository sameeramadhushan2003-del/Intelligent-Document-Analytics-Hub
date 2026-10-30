
package com.documentanalytics.backend;

import com.documentanalytics.backend.entity.Document;
import com.documentanalytics.backend.service.DocumentTextExtractionService;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import org.apache.poi.xwpf.usermodel.XWPFDocument;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DocumentTextExtractionServiceTest {

    @TempDir
    Path temporaryDirectory;

    // Create a test document in temporary private storage.
    private Document createTestDocument(
            byte[] content,
            String contentType
    ) throws IOException {

        String storageKey = UUID.randomUUID().toString();

        Files.write(
                temporaryDirectory.resolve(storageKey),
                content
        );

        Document document = new Document();

        document.setStorageKey(storageKey);
        document.setContentType(contentType);

        return document;
    }

    private DocumentTextExtractionService createService() {
        return new DocumentTextExtractionService(
                temporaryDirectory.toString()
        );
    }

    // TEST 1: Extract text from TXT.
    @Test
    void extractsTextFromTxt() throws IOException {

        String expectedText =
                "Intelligent Document Analytics Hub supports RAG.";

        Document document = createTestDocument(
                expectedText.getBytes(StandardCharsets.UTF_8),
                "text/plain"
        );

        String extractedText =
                createService().extractText(document);

        assertEquals(expectedText, extractedText);
    }

    // TEST 2: Extract text from PDF.
    @Test
    void extractsTextFromPdf() throws IOException {

        ByteArrayOutputStream output =
                new ByteArrayOutputStream();

        try (PDDocument pdf = new PDDocument()) {

            PDPage page = new PDPage();
            pdf.addPage(page);

            try (PDPageContentStream stream =
                         new PDPageContentStream(pdf, page)) {

                stream.beginText();

                stream.setFont(
                        new PDType1Font(
                                Standard14Fonts.FontName.HELVETICA
                        ),
                        12
                );

                stream.newLineAtOffset(50, 700);

                stream.showText(
                        "PDF extraction test successful"
                );

                stream.endText();
            }

            pdf.save(output);
        }

        Document document = createTestDocument(
                output.toByteArray(),
                "application/pdf"
        );

        String extractedText =
                createService().extractText(document);

        assertTrue(
                extractedText.contains(
                        "PDF extraction test successful"
                )
        );
    }

    // TEST 3: Extract text from DOCX.
    @Test
    void extractsTextFromDocx() throws IOException {

        ByteArrayOutputStream output =
                new ByteArrayOutputStream();

        try (XWPFDocument word = new XWPFDocument()) {

            word.createParagraph()
                    .createRun()
                    .setText("DOCX extraction test successful");

            word.write(output);
        }

        Document document = createTestDocument(
                output.toByteArray(),
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        );

        String extractedText =
                createService().extractText(document);

        assertTrue(
                extractedText.contains(
                        "DOCX extraction test successful"
                )
        );
    }

    // TEST 4: Reject a document with no readable text.
    @Test
    void rejectsEmptyText() throws IOException {

        Document document = createTestDocument(
                "   \n   ".getBytes(StandardCharsets.UTF_8),
                "text/plain"
        );

        assertThrows(
                IOException.class,
                () -> createService().extractText(document)
        );
    }

    // TEST 5: Reject invalid storage identifiers.
    @Test
    void rejectsInvalidStorageKey() {

        Document document = new Document();

        document.setStorageKey("../other-file");
        document.setContentType("text/plain");

        assertThrows(
                IllegalArgumentException.class,
                () -> createService().extractText(document)
        );
    }

    // TEST 6: Reject files missing from private storage.
    @Test
    void rejectsMissingStoredFile() {

        Document document = new Document();

        document.setStorageKey(
                UUID.randomUUID().toString()
        );
        document.setContentType("text/plain");

        assertThrows(
                IOException.class,
                () -> createService().extractText(document)
        );
    }

    // TEST 7: Reject malformed UTF-8 text.
    @Test
    void rejectsInvalidUtf8() throws IOException {

        byte[] invalidUtf8 = {
                (byte) 0xC3,
                (byte) 0x28
        };

        Document document = createTestDocument(
                invalidUtf8,
                "text/plain"
        );

        assertThrows(
                IOException.class,
                () -> createService().extractText(document)
        );
    }
}
