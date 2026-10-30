
package com.documentanalytics.backend.service;

import com.documentanalytics.backend.entity.DocumentChunk;
import com.documentanalytics.backend.entity.DocumentExtraction;

import com.documentanalytics.backend.repository.DocumentChunkRepository;
import com.documentanalytics.backend.repository.DocumentExtractionRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class DocumentChunkPersistenceService {

    private final DocumentLibraryService documentLibraryService;
    private final DocumentExtractionRepository extractionRepository;
    private final DocumentChunkingService chunkingService;
    private final DocumentChunkRepository chunkRepository;

    public DocumentChunkPersistenceService(
            DocumentLibraryService documentLibraryService,
            DocumentExtractionRepository extractionRepository,
            DocumentChunkingService chunkingService,
            DocumentChunkRepository chunkRepository
    ) {
        this.documentLibraryService = documentLibraryService;
        this.extractionRepository = extractionRepository;
        this.chunkingService = chunkingService;
        this.chunkRepository = chunkRepository;
    }

    @Transactional
    public int generateAndSaveChunks(
            Long documentId,
            Long authenticatedUserId
    ) {

        // Step 1: Validate the authenticated user.
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

        // Step 2: Verify document ownership.
        // This also checks whether the user is active.
        documentLibraryService.getMyDocument(
                documentId,
                authenticatedUserId
        );

        // Step 3: Load extracted text from PostgreSQL.
        DocumentExtraction extraction =
                extractionRepository.findById(documentId)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.CONFLICT,
                                        "Document must be extracted before chunking."
                                )
                        );

        // Step 4: Generate overlapping text chunks.
        List<DocumentChunk> newChunks =
                chunkingService.chunkDocument(
                        documentId,
                        extraction.getExtractedText()
                );

        // Step 5: Find any previously generated chunks.
        List<DocumentChunk> existingChunks =
                chunkRepository
                        .findByDocumentIdOrderByChunkIndexAsc(
                                documentId
                        );

        // Step 6: Remove old chunks before replacing them.
        if (!existingChunks.isEmpty()) {

            chunkRepository.deleteAll(existingChunks);

            // Execute deletes before inserting new rows
            // with the same unique chunk indexes.
            chunkRepository.flush();
        }

        // Step 7: Save the newly generated chunks.
        List<DocumentChunk> savedChunks =
                chunkRepository.saveAllAndFlush(newChunks);

        // Step 8: Return the number of saved chunks.
        return savedChunks.size();
    }
}
