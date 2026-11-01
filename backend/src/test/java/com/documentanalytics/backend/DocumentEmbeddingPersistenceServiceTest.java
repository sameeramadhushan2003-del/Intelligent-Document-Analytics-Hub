package com.documentanalytics.backend;

import com.documentanalytics.backend.entity.DocumentChunk;
import com.documentanalytics.backend.repository.*;
import com.documentanalytics.backend.service.*;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocumentEmbeddingPersistenceServiceTest {
    @Test
    void checksOwnershipBeforeReadingChunksOrRunningOllama() {
        var library = mock(DocumentLibraryService.class);
        var chunks = mock(DocumentChunkRepository.class);
        var model = mock(OllamaEmbeddingService.class);
        var repository = mock(DocumentChunkEmbeddingRepository.class);
        when(library.getMyDocumentForUpdate(79L, 7L)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        var service = new DocumentEmbeddingPersistenceService(library, chunks, model, repository);
        assertThrows(ResponseStatusException.class, () -> service.generateAndSaveEmbeddings(79L, 7L));
        verifyNoInteractions(chunks, model, repository);
    }

    @Test
    void missingChunksReturnsConflictWithoutCallingOllama() {
        var library = mock(DocumentLibraryService.class);
        var chunks = mock(DocumentChunkRepository.class);
        var model = mock(OllamaEmbeddingService.class);
        var repository = mock(DocumentChunkEmbeddingRepository.class);
        when(chunks.findByDocumentIdOrderByChunkIndexAsc(79L)).thenReturn(List.of());
        var service = new DocumentEmbeddingPersistenceService(library, chunks, model, repository);

        var exception = assertThrows(
                ResponseStatusException.class,
                () -> service.generateAndSaveEmbeddings(79L, 7L)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verifyNoInteractions(model, repository);
    }

    @Test
    void embedsAndSavesChunksSequentiallyAfterOwnership() {
        var library = mock(DocumentLibraryService.class);
        var chunks = mock(DocumentChunkRepository.class);
        var model = mock(OllamaEmbeddingService.class);
        var repository = mock(DocumentChunkEmbeddingRepository.class);
        var chunk = mock(DocumentChunk.class);
        when(chunk.getId()).thenReturn(8L);
        when(chunk.getChunkText()).thenReturn("Synthetic appointment text.");
        when(chunks.findByDocumentIdOrderByChunkIndexAsc(79L)).thenReturn(List.of(chunk));
        when(model.getModelName()).thenReturn("all-minilm");
        when(repository.findExistingChunkIdsForDocumentAndModel(79L, "all-minilm"))
                .thenReturn(Set.of());
        var vector = new float[384]; vector[0] = 1;
        when(model.generateEmbedding("Synthetic appointment text.")).thenReturn(vector);
        assertEquals(1, new DocumentEmbeddingPersistenceService(library, chunks, model, repository)
                .generateAndSaveEmbeddings(79L, 7L));
        var order = inOrder(library, chunks, model, repository);
        order.verify(library).getMyDocumentForUpdate(79L, 7L);
        order.verify(chunks).findByDocumentIdOrderByChunkIndexAsc(79L);
        order.verify(model).getModelName();
        order.verify(model).generateEmbedding("Synthetic appointment text.");
        order.verify(repository).saveOrUpdateEmbedding(8L, "all-minilm", vector);
    }

    @Test
    void reusesExistingVectorsAndOnlyEmbedsMissingChunks() {
        var library = mock(DocumentLibraryService.class);
        var chunks = mock(DocumentChunkRepository.class);
        var model = mock(OllamaEmbeddingService.class);
        var repository = mock(DocumentChunkEmbeddingRepository.class);
        var existing = mock(DocumentChunk.class);
        var missing = mock(DocumentChunk.class);
        when(existing.getId()).thenReturn(8L);
        when(existing.getChunkText()).thenReturn("Already embedded synthetic text.");
        when(missing.getId()).thenReturn(9L);
        when(missing.getChunkText()).thenReturn("Not yet embedded synthetic text.");
        when(chunks.findByDocumentIdOrderByChunkIndexAsc(79L))
                .thenReturn(List.of(existing, missing));
        when(model.getModelName()).thenReturn("all-minilm");
        when(repository.findExistingChunkIdsForDocumentAndModel(79L, "all-minilm"))
                .thenReturn(Set.of(8L));
        var vector = new float[384];
        when(model.generateEmbedding("Not yet embedded synthetic text.")).thenReturn(vector);

        int readyCount = new DocumentEmbeddingPersistenceService(
                library, chunks, model, repository
        ).generateAndSaveEmbeddings(79L, 7L);

        assertEquals(2, readyCount);
        verify(model, never()).generateEmbedding("Already embedded synthetic text.");
        verify(model).generateEmbedding("Not yet embedded synthetic text.");
        verify(repository).saveOrUpdateEmbedding(9L, "all-minilm", vector);
        verify(repository, never()).saveOrUpdateEmbedding(eq(8L), anyString(), any());
    }

    @Test
    void reportsDatabasePersistenceFailureInsteadOfReturningPartialSuccess() {
        var library = mock(DocumentLibraryService.class);
        var chunks = mock(DocumentChunkRepository.class);
        var model = mock(OllamaEmbeddingService.class);
        var repository = mock(DocumentChunkEmbeddingRepository.class);
        var chunk = mock(DocumentChunk.class);
        when(chunk.getId()).thenReturn(8L);
        when(chunk.getChunkText()).thenReturn("Synthetic appointment text.");
        when(chunks.findByDocumentIdOrderByChunkIndexAsc(79L)).thenReturn(List.of(chunk));
        when(model.getModelName()).thenReturn("all-minilm");
        when(repository.findExistingChunkIdsForDocumentAndModel(79L, "all-minilm"))
                .thenReturn(Set.of());
        when(model.generateEmbedding("Synthetic appointment text."))
                .thenReturn(new float[384]);
        doThrow(new org.springframework.dao.DataIntegrityViolationException("synthetic database failure"))
                .when(repository).saveOrUpdateEmbedding(eq(8L), eq("all-minilm"), any());

        assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> new DocumentEmbeddingPersistenceService(
                        library, chunks, model, repository
                ).generateAndSaveEmbeddings(79L, 7L)
        );
    }
}
