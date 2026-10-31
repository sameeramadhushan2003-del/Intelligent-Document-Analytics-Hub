
package com.documentanalytics.backend.service;

import com.documentanalytics.backend.repository.DocumentSemanticSearchRepository;
import com.documentanalytics.backend.repository.DocumentSemanticSearchRepository.SimilarChunk;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DocumentSemanticSearchService {

    private static final int MAX_QUERY_LENGTH = 10000;
    private static final int MAX_RESULTS = 20;

    private final DocumentLibraryService documentLibraryService;
    private final OllamaEmbeddingService ollamaEmbeddingService;
    private final DocumentSemanticSearchRepository searchRepository;

    public DocumentSemanticSearchService(
            DocumentLibraryService documentLibraryService,
            OllamaEmbeddingService ollamaEmbeddingService,
            DocumentSemanticSearchRepository searchRepository) {

        this.documentLibraryService = documentLibraryService;
        this.ollamaEmbeddingService = ollamaEmbeddingService;
        this.searchRepository = searchRepository;
    }

    public List<SimilarChunk> searchDocument(
            Long documentId,
            Long authenticatedUserId,
            String query,
            int limit) {

        // Step 1: Validate document ID.
        if (documentId == null || documentId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid document ID."
            );
        }

        // Step 2: Validate authenticated user.
        if (authenticatedUserId == null
                || authenticatedUserId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required."
            );
        }

        // Step 3: Validate search question.
        if (query == null || query.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Search question must not be empty."
            );
        }

        String cleanQuery = query.trim();

        if (cleanQuery.length() > MAX_QUERY_LENGTH) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Search question is too long."
            );
        }

        // Step 4: Validate result limit.
        if (limit < 1 || limit > MAX_RESULTS) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Result limit must be between 1 and 20."
            );
        }

        // Step 5: Verify document ownership.
        // Never search another user's document.
        documentLibraryService.getMyDocument(
                documentId,
                authenticatedUserId
        );

        // Step 6: Convert question to an embedding.
        float[] queryEmbedding =
                ollamaEmbeddingService.generateEmbedding(
                        cleanQuery
                );

        // Step 7: Search the user's selected document.
        List<SimilarChunk> results =
                searchRepository.findSimilarChunks(
                        documentId,
                        ollamaEmbeddingService.getModelName(),
                        queryEmbedding,
                        limit
                );

        // Step 8: Handle documents without embeddings.
        if (results.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "No searchable embeddings found. "
                    + "Generate document embeddings first."
            );
        }

        return results;
    }
}
