
package com.documentanalytics.backend.service;

import com.documentanalytics.backend.entity.DocumentChunk;
import com.documentanalytics.backend.repository.DocumentChunkRepository;
import com.documentanalytics.backend.repository.DocumentChunkEmbeddingRepository;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DocumentEmbeddingPersistenceService {

    private final DocumentLibraryService documentLibraryService;
    private final DocumentChunkRepository chunkRepository;
    private final OllamaEmbeddingService ollamaEmbeddingService;
    private final DocumentChunkEmbeddingRepository embeddingRepository;

    public DocumentEmbeddingPersistenceService(
            DocumentLibraryService documentLibraryService,
            DocumentChunkRepository chunkRepository,
            OllamaEmbeddingService ollamaEmbeddingService,
            DocumentChunkEmbeddingRepository embeddingRepository) {

        this.documentLibraryService = documentLibraryService;
        this.chunkRepository = chunkRepository;
        this.ollamaEmbeddingService = ollamaEmbeddingService;
        this.embeddingRepository = embeddingRepository;
    }

    public int generateAndSaveEmbeddings(
            Long documentId,
            Long authenticatedUserId) {

        // Step 1: Validate identifiers.
        if (documentId == null || documentId <= 0) {
            throw new IllegalArgumentException(
                    "Invalid document ID."
            );
        }

        if (authenticatedUserId == null ||
                authenticatedUserId <= 0) {
            throw new IllegalArgumentException(
                    "Invalid authenticated user ID."
            );
        }

        // Step 2: Verify document ownership.
        documentLibraryService.getMyDocument(
                documentId,
                authenticatedUserId
        );

        // Step 3: Read saved document chunks.
        List<DocumentChunk> chunks =
                chunkRepository.findByDocumentIdOrderByChunkIndexAsc(
                        documentId
                );

        if (chunks.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "No document chunks found. " +
                    "Generate chunks before embeddings."
            );
        }

        // Step 4: Generate and save one embedding per chunk.
        int savedCount = 0;

        String modelName = ollamaEmbeddingService.getModelName();

        for (DocumentChunk chunk : chunks) {

            String chunkText = chunk.getChunkText();

            if (chunkText == null || chunkText.isBlank()) {
                throw new IllegalStateException(
                        "Document chunk contains empty text. " +
                        "Chunk ID: " + chunk.getId()
                );
            }

            float[] embedding =
                    ollamaEmbeddingService.generateEmbedding(
                            chunkText
                    );

            embeddingRepository.saveOrUpdateEmbedding(
                    chunk.getId(),
                    modelName,
                    embedding
            );

            savedCount++;
        }

        return savedCount;
    }
}
