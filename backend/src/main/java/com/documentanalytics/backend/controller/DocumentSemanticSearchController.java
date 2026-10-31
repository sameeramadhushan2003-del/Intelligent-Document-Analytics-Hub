
package com.documentanalytics.backend.controller;

import com.documentanalytics.backend.repository.DocumentSemanticSearchRepository.SimilarChunk;
import com.documentanalytics.backend.service.DocumentSemanticSearchService;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/documents")
public class DocumentSemanticSearchController {

    private final DocumentSemanticSearchService searchService;

    public DocumentSemanticSearchController(
            DocumentSemanticSearchService searchService) {
        this.searchService = searchService;
    }

    // Request received from the frontend.
    public record SearchRequest(
            String query,
            Integer limit
    ) {}

    // Response returned to the frontend.
    public record SearchResponse(
            Long documentId,
            String query,
            int resultCount,
            List<SimilarChunk> results
    ) {}

    @PostMapping("/{documentId}/search")
    public SearchResponse searchDocument(
            @PathVariable("documentId") Long documentId,
            @RequestBody(required = false) SearchRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        // Step 1: Validate document ID.
        if (documentId == null || documentId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid document ID."
            );
        }

        // Step 2: Verify authentication.
        if (jwt == null || jwt.getSubject() == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required."
            );
        }

        // Step 3: Extract authenticated user ID.
        Long authenticatedUserId;

        try {
            authenticatedUserId =
                    Long.valueOf(jwt.getSubject());

        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid authentication token."
            );
        }

        if (authenticatedUserId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid authenticated user."
            );
        }

        // Step 4: Validate request body.
        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Search request body is required."
            );
        }

        // Step 5: Default to five search results.
        int limit = request.limit() == null
                ? 5
                : request.limit();

        // Step 6: Perform secure semantic search.
        // The service validates the query and ownership.
        List<SimilarChunk> results =
                searchService.searchDocument(
                        documentId,
                        authenticatedUserId,
                        request.query(),
                        limit
                );

        // Step 7: Return ranked results.
        return new SearchResponse(
                documentId,
                request.query().trim(),
                results.size(),
                results
        );
    }
}
