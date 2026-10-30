
package com.documentanalytics.backend.controller;

import com.documentanalytics.backend.entity.DocumentExtraction;
import com.documentanalytics.backend.service.DocumentProcessingService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/documents")
public class DocumentProcessingController {

    private final DocumentProcessingService processingService;

    public DocumentProcessingController(
            DocumentProcessingService processingService
    ) {
        this.processingService = processingService;
    }

    @PostMapping("/{documentId}/process")
    public ResponseEntity<ProcessDocumentResponse> processDocument(
            @PathVariable Long documentId,
            @AuthenticationPrincipal Jwt jwt
    ) {

        // Step 1: Verify authentication.
        if (jwt == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication is required."
            );
        }

        // Step 2: Get the authenticated user ID.
        Long authenticatedUserId;

        try {
            authenticatedUserId = Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid authentication token."
            );
        }

        try {

            // Step 3: Verify ownership, extract text
            // and save the extraction in PostgreSQL.
            DocumentExtraction extraction =
                    processingService.processDocument(
                            documentId,
                            authenticatedUserId
                    );

            // Step 4: Return safe processing metadata.
            ProcessDocumentResponse response =
                    new ProcessDocumentResponse(
                            extraction.getDocumentId(),
                            "Document text extracted successfully.",
                            extraction.getCharacterCount(),
                            extraction.getExtractedAt()
                    );

            return ResponseEntity.ok(response);

        } catch (IOException exception) {

            // Do not expose private file paths or parser errors.
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Document text could not be extracted.",
                    exception
            );
        }
    }

    public record ProcessDocumentResponse(
            Long documentId,
            String message,
            Integer characterCount,
            OffsetDateTime extractedAt
    ) {
    }
}
