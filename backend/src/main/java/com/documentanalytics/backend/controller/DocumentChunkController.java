
package com.documentanalytics.backend.controller;

import com.documentanalytics.backend.service.DocumentChunkPersistenceService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/documents")
public class DocumentChunkController {

    private final DocumentChunkPersistenceService
            chunkPersistenceService;

    public DocumentChunkController(
            DocumentChunkPersistenceService chunkPersistenceService
    ) {
        this.chunkPersistenceService = chunkPersistenceService;
    }

    @PostMapping("/{documentId}/chunks/generate")
    public ResponseEntity<GenerateChunksResponse> generateChunks(
            @PathVariable Long documentId,
            @AuthenticationPrincipal Jwt jwt
    ) {

        // Step 1: Verify JWT authentication.
        if (jwt == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication is required."
            );
        }

        // Step 2: Get user ID from the JWT subject.
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

        // Step 3: Validate the document ID.
        if (documentId == null || documentId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid document ID."
            );
        }

        // Step 4: Verify ownership, generate chunks
        // and save them to PostgreSQL.
        int chunkCount =
                chunkPersistenceService.generateAndSaveChunks(
                        documentId,
                        authenticatedUserId
                );

        // Step 5: Return safe result metadata.
        GenerateChunksResponse response =
                new GenerateChunksResponse(
                        documentId,
                        chunkCount,
                        "Document chunks generated successfully."
                );

        return ResponseEntity.ok(response);
    }

    public record GenerateChunksResponse(
            Long documentId,
            int chunkCount,
            String message
    ) {
    }
}
