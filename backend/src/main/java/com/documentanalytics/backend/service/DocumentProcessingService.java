
package com.documentanalytics.backend.service;
import com.documentanalytics.backend.entity.DocumentStatus;
import com.documentanalytics.backend.entity.Document;
import com.documentanalytics.backend.entity.DocumentExtraction;
import com.documentanalytics.backend.repository.DocumentExtractionRepository;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

@Service
public class DocumentProcessingService {

    private final DocumentLibraryService documentLibraryService;

    private final DocumentTextExtractionService textExtractionService;

    private final DocumentExtractionRepository extractionRepository;

    public DocumentProcessingService(
            DocumentLibraryService documentLibraryService,
            DocumentTextExtractionService textExtractionService,
            DocumentExtractionRepository extractionRepository
    ) {
        this.documentLibraryService = documentLibraryService;
        this.textExtractionService = textExtractionService;
        this.extractionRepository = extractionRepository;
    }

    @Transactional(rollbackFor = IOException.class)
    public DocumentExtraction processDocument(
            Long documentId,
            Long authenticatedUserId
    ) throws IOException {

        // Step 1: Require an authenticated user.
        if (authenticatedUserId == null
                || authenticatedUserId <= 0) {

            throw new AccessDeniedException(
                    "Authentication is required."
            );
        }

        if (documentId == null || documentId <= 0) {
            throw new IllegalArgumentException(
                    "A valid document ID is required."
            );
        }

        // Step 2: Check ownership and account status
        // through our existing library service.
        Document document =
                documentLibraryService.getMyDocument(
                        documentId,
                        authenticatedUserId
                );

        // Step 3: Extract text from the private file.
        String extractedText =
                textExtractionService.extractText(document);

        // Step 4: Check for an existing extraction.
        DocumentExtraction extraction =
                extractionRepository.findById(
                        document.getId()
                ).orElse(null);

        if (extraction == null) {

            // First extraction for this document.
            extraction = new DocumentExtraction(
                    document.getId(),
                    extractedText
            );

        } else {

            // Reprocessing an existing document.
            extraction.setExtractedText(extractedText);
        }

        // Step 5: Save extracted content in PostgreSQL.
        
        // Save the extracted text to PostgreSQL.
        DocumentExtraction savedExtraction =
                extractionRepository.saveAndFlush(
                        extraction
                );

        // Mark the original document as extracted.
        document.setProcessingStatus(
                DocumentStatus.EXTRACTED
        );

        return savedExtraction;

    }
}
