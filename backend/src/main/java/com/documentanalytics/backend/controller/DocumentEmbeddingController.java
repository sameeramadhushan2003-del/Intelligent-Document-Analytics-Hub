
package com.documentanalytics.backend.controller;

import com.documentanalytics.backend.service.DocumentEmbeddingPersistenceService;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/documents")
public class DocumentEmbeddingController {

    private final DocumentEmbeddingPersistenceService
            embeddingPersistenceService;

    public DocumentEmbeddingController(
            DocumentEmbeddingPersistenceService
                    embeddingPersistenceService) {

        this.embeddingPersistenceService =
                embeddingPersistenceService;
    }

    @PostMapping("/{documentId}/embeddings/generate")
    public Map<String, Object> generateEmbeddings(
            @PathVariable("documentId") Long documentId,
            @AuthenticationPrincipal Jwt jwt) {

        // Step 1: Validate document ID.
        if (documentId == null || documentId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid document ID."
            );
        }

        // Step 2: Ensure the request is authenticated.
        if (jwt == null || jwt.getSubject() == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required."
            );
        }

        // Step 3: Extract user ID from the JWT.
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

        // Step 4: Generate and save embeddings.
        // The service verifies document ownership.
        int embeddingCount =
                embeddingPersistenceService
                        .generateAndSaveEmbeddings(
                                documentId,
                                authenticatedUserId
                        );

        // Step 5: Return the result.
        return Map.of(
                "documentId", documentId,
                "embeddingCount", embeddingCount,
                "message",
                "Document embeddings generated successfully."
        );
    }
}
