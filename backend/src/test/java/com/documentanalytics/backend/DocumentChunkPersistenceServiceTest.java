
package com.documentanalytics.backend;

import com.documentanalytics.backend.entity.Document;
import com.documentanalytics.backend.entity.DocumentChunk;
import com.documentanalytics.backend.entity.DocumentExtraction;

import com.documentanalytics.backend.repository.DocumentChunkRepository;
import com.documentanalytics.backend.repository.DocumentExtractionRepository;

import com.documentanalytics.backend.service.DocumentLibraryService;
import com.documentanalytics.backend.service.DocumentChunkingService;
import com.documentanalytics.backend.service.DocumentChunkPersistenceService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.mockito.InOrder;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class DocumentChunkPersistenceServiceTest {

    private static final Long DOCUMENT_ID = 34L;
    private static final Long USER_ID = 7L;

    private DocumentLibraryService documentLibraryService;
    private DocumentExtractionRepository extractionRepository;
    private DocumentChunkingService chunkingService;
    private DocumentChunkRepository chunkRepository;

    private DocumentChunkPersistenceService persistenceService;

    @BeforeEach
    void setUp() {

        documentLibraryService = mock(DocumentLibraryService.class);
        extractionRepository = mock(DocumentExtractionRepository.class);
        chunkingService = mock(DocumentChunkingService.class);
        chunkRepository = mock(DocumentChunkRepository.class);

        persistenceService = new DocumentChunkPersistenceService(
                documentLibraryService,
                extractionRepository,
                chunkingService,
                chunkRepository
        );
    }

    // Prepare an owned document with extracted text.
    private void prepareExtractedDocument() {

        when(documentLibraryService.getMyDocument(
                DOCUMENT_ID,
                USER_ID
        )).thenReturn(mock(Document.class));

        when(extractionRepository.findById(DOCUMENT_ID))
                .thenReturn(Optional.of(
                        new DocumentExtraction(
                                DOCUMENT_ID,
                                "Sample extracted document text"
                        )
                ));
    }

    // TEST 1: Save new document chunks.
    @Test
    void savesNewChunks() {

        prepareExtractedDocument();

        DocumentChunk chunk = new DocumentChunk(
                DOCUMENT_ID,
                0,
                "Sample extracted document text",
                0,
                30
        );

        List<DocumentChunk> generatedChunks =
                List.of(chunk);

        when(chunkingService.chunkDocument(
                DOCUMENT_ID,
                "Sample extracted document text"
        )).thenReturn(generatedChunks);

        when(chunkRepository
                .findByDocumentIdOrderByChunkIndexAsc(DOCUMENT_ID))
                .thenReturn(List.of());

        when(chunkRepository.saveAllAndFlush(anyList()))
                .thenReturn(generatedChunks);

        int count = persistenceService.generateAndSaveChunks(
                DOCUMENT_ID,
                USER_ID
        );

        assertEquals(1, count);

        verify(chunkRepository)
                .saveAllAndFlush(generatedChunks);

        verify(chunkRepository, never())
                .deleteAll(anyList());
    }

    // TEST 2: Reprocessing replaces old chunks.
    @Test
    void replacesExistingChunks() {

        prepareExtractedDocument();

        DocumentChunk oldChunk = new DocumentChunk(
                DOCUMENT_ID, 0, "Old", 0, 3
        );

        DocumentChunk newChunk = new DocumentChunk(
                DOCUMENT_ID, 0, "Updated", 0, 7
        );

        when(chunkingService.chunkDocument(
                eq(DOCUMENT_ID),
                anyString()
        )).thenReturn(List.of(newChunk));

        when(chunkRepository
                .findByDocumentIdOrderByChunkIndexAsc(DOCUMENT_ID))
                .thenReturn(List.of(oldChunk));

        when(chunkRepository.saveAllAndFlush(anyList()))
                .thenReturn(List.of(newChunk));

        int count = persistenceService.generateAndSaveChunks(
                DOCUMENT_ID,
                USER_ID
        );

        assertEquals(1, count);

        InOrder order = inOrder(chunkRepository);

        order.verify(chunkRepository)
                .deleteAll(List.of(oldChunk));

        order.verify(chunkRepository).flush();

        order.verify(chunkRepository)
                .saveAllAndFlush(List.of(newChunk));
    }

    // TEST 3: Cannot chunk a document without extracted text.
    @Test
    void rejectsDocumentWithoutExtraction() {

        when(documentLibraryService.getMyDocument(
                DOCUMENT_ID,
                USER_ID
        )).thenReturn(mock(Document.class));

        when(extractionRepository.findById(DOCUMENT_ID))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> persistenceService.generateAndSaveChunks(
                        DOCUMENT_ID,
                        USER_ID
                )
        );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        verifyNoInteractions(
                chunkingService,
                chunkRepository
        );
    }

    // TEST 4: Reject another user's document.
    @Test
    void rejectsUnauthorizedDocument() {

        when(documentLibraryService.getMyDocument(
                DOCUMENT_ID,
                USER_ID
        )).thenThrow(new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Document not found"
        ));

        assertThrows(
                ResponseStatusException.class,
                () -> persistenceService.generateAndSaveChunks(
                        DOCUMENT_ID,
                        USER_ID
                )
        );

        verifyNoInteractions(
                extractionRepository,
                chunkingService,
                chunkRepository
        );
    }

    // TEST 5: Reject invalid authenticated user.
    @Test
    void rejectsInvalidUserId() {

        assertThrows(
                AccessDeniedException.class,
                () -> persistenceService.generateAndSaveChunks(
                        DOCUMENT_ID,
                        null
                )
        );

        verifyNoInteractions(
                documentLibraryService,
                extractionRepository,
                chunkingService,
                chunkRepository
        );
    }

    // TEST 6: Reject invalid document ID.
    @Test
    void rejectsInvalidDocumentId() {

        assertThrows(
                IllegalArgumentException.class,
                () -> persistenceService.generateAndSaveChunks(
                        -1L,
                        USER_ID
                )
        );

        verifyNoInteractions(
                documentLibraryService,
                extractionRepository,
                chunkingService,
                chunkRepository
        );
    }

    // TEST 7: Do not modify stored chunks if generation fails.
    @Test
    void doesNotSaveWhenChunkingFails() {

        prepareExtractedDocument();

        when(chunkingService.chunkDocument(
                eq(DOCUMENT_ID),
                anyString()
        )).thenThrow(
                new IllegalArgumentException(
                        "Chunk generation failed"
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> persistenceService.generateAndSaveChunks(
                        DOCUMENT_ID,
                        USER_ID
                )
        );

        verifyNoInteractions(chunkRepository);
    }
}
