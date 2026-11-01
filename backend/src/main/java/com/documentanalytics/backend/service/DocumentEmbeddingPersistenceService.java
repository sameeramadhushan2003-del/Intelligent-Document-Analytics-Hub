
package com.documentanalytics.backend.service;

import com.documentanalytics.backend.entity.DocumentChunk;
import com.documentanalytics.backend.repository.DocumentChunkRepository;
import com.documentanalytics.backend.repository.DocumentChunkEmbeddingRepository;

import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DocumentEmbeddingPersistenceService {

    private static final Logger logger =
            LoggerFactory.getLogger(DocumentEmbeddingPersistenceService.class);

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

    @Transactional
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
        documentLibraryService.getMyDocumentForUpdate(
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

        // Step 4: Preserve vectors already generated for this model.
        String modelName = ollamaEmbeddingService.getModelName();
        Set<Long> existingChunkIds =
                embeddingRepository.findExistingChunkIdsForDocumentAndModel(
                        documentId,
                        modelName
                );
        logger.info(
                "Embedding generation started: documentId={}, chunks={}, existingEmbeddings={}, model={}",
                documentId,
                chunks.size(),
                existingChunkIds.size(),
                modelName
        );

        // Keep upstream inference sequential to bound memory and CPU use.
        for (DocumentChunk chunk : chunks) {
            if (existingChunkIds.contains(chunk.getId())) {
                continue;
            }

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
        }

        return chunks.size();
    }
}
