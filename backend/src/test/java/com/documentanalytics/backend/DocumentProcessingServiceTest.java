
package com.documentanalytics.backend;

import com.documentanalytics.backend.entity.Document;
import com.documentanalytics.backend.entity.DocumentExtraction;
import com.documentanalytics.backend.repository.DocumentExtractionRepository;
import com.documentanalytics.backend.service.DocumentLibraryService;
import com.documentanalytics.backend.service.DocumentProcessingService;
import com.documentanalytics.backend.service.DocumentTextExtractionService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DocumentProcessingServiceTest {

    private DocumentLibraryService documentLibraryService;
    private DocumentTextExtractionService extractionService;
    private DocumentExtractionRepository extractionRepository;

    private DocumentProcessingService processingService;

    private static final Long DOCUMENT_ID = 42L;
    private static final Long USER_ID = 7L;

    private Document document;

    @BeforeEach
    void setUp() {

        documentLibraryService =
                mock(DocumentLibraryService.class);

        extractionService =
                mock(DocumentTextExtractionService.class);

        extractionRepository =
                mock(DocumentExtractionRepository.class);

        processingService = new DocumentProcessingService(
                documentLibraryService,
                extractionService,
                extractionRepository
        );

        document = mock(Document.class);

        when(document.getId()).thenReturn(DOCUMENT_ID);
    }

    // TEST 1: New document extraction is saved.
    @Test
    void savesNewDocumentExtraction() throws IOException {

        when(documentLibraryService.getMyDocument(
                DOCUMENT_ID, USER_ID
        )).thenReturn(document);

        when(extractionService.extractText(document))
                .thenReturn("Hello Intelligent Document Analytics Hub");

        when(extractionRepository.findById(DOCUMENT_ID))
                .thenReturn(Optional.empty());

        when(extractionRepository.saveAndFlush(
                any(DocumentExtraction.class)
        )).thenAnswer(invocation -> invocation.getArgument(0));

        DocumentExtraction result =
                processingService.processDocument(
                        DOCUMENT_ID, USER_ID
                );

        assertNotNull(result);

        assertEquals(DOCUMENT_ID, result.getDocumentId());

        assertEquals(
                "Hello Intelligent Document Analytics Hub",
                result.getExtractedText()
        );

        assertEquals(
                result.getExtractedText().length(),
                result.getCharacterCount()
        );

        verify(extractionRepository).saveAndFlush(
                any(DocumentExtraction.class)
        );
    }

    // TEST 2: Existing extraction can be updated.
    @Test
    void updatesExistingExtraction() throws IOException {

        DocumentExtraction existing =
                new DocumentExtraction(
                        DOCUMENT_ID,
                        "Old document text"
                );

        when(documentLibraryService.getMyDocument(
                DOCUMENT_ID, USER_ID
        )).thenReturn(document);

        when(extractionService.extractText(document))
                .thenReturn("Updated document text");

        when(extractionRepository.findById(DOCUMENT_ID))
                .thenReturn(Optional.of(existing));

        when(extractionRepository.saveAndFlush(existing))
                .thenReturn(existing);

        DocumentExtraction result =
                processingService.processDocument(
                        DOCUMENT_ID, USER_ID
                );

        assertSame(existing, result);

        assertEquals(
                "Updated document text",
                result.getExtractedText()
        );

        assertEquals(
                "Updated document text".length(),
                result.getCharacterCount()
        );

        verify(extractionRepository).saveAndFlush(existing);
    }

    // TEST 3: A user cannot process another user's document.
    @Test
    void rejectsDocumentWithoutOwnership() {

        when(documentLibraryService.getMyDocument(
                DOCUMENT_ID, USER_ID
        )).thenThrow(
                new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Document not found"
                )
        );

        assertThrows(
                ResponseStatusException.class,
                () -> processingService.processDocument(
                        DOCUMENT_ID, USER_ID
                )
        );

        verifyNoInteractions(
                extractionService,
                extractionRepository
        );
    }

    // TEST 4: Extraction errors do not save incomplete text.
    @Test
    void doesNotSaveWhenExtractionFails() throws IOException {

        when(documentLibraryService.getMyDocument(
                DOCUMENT_ID, USER_ID
        )).thenReturn(document);

        when(extractionService.extractText(document))
                .thenThrow(
                        new IOException("Cannot read document")
                );

        assertThrows(
                IOException.class,
                () -> processingService.processDocument(
                        DOCUMENT_ID, USER_ID
                )
        );

        verifyNoInteractions(extractionRepository);
    }

    // TEST 5: Invalid authenticated user IDs are rejected.
    @Test
    void rejectsInvalidUserId() {

        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> processingService.processDocument(
                        DOCUMENT_ID, null
                )
        );

        verifyNoInteractions(
                documentLibraryService,
                extractionService,
                extractionRepository
        );
    }

    // TEST 6: Invalid document IDs are rejected.
    @Test
    void rejectsInvalidDocumentId() {

        assertThrows(
                IllegalArgumentException.class,
                () -> processingService.processDocument(
                        -1L, USER_ID
                )
        );

        verifyNoInteractions(
                documentLibraryService,
                extractionService,
                extractionRepository
        );
    }
}
