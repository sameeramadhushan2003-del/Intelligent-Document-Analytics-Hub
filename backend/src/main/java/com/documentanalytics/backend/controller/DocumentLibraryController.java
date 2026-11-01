
package com.documentanalytics.backend.controller;

import com.documentanalytics.backend.dto.DocumentResponse;
import com.documentanalytics.backend.entity.Document;
import com.documentanalytics.backend.service.DocumentLibraryService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.documentanalytics.backend.service.DocumentDeletionService;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class DocumentLibraryController {

    private final DocumentLibraryService documentLibraryService;
    private final DocumentDeletionService documentDeletionService;

    public DocumentLibraryController(
            DocumentLibraryService documentLibraryService,
            DocumentDeletionService documentDeletionService
    ) {
        this.documentLibraryService = documentLibraryService;
        this.documentDeletionService = documentDeletionService;
    }

    // GET /api/documents
    // List documents belonging to the logged-in user.
    @GetMapping
    public List<DocumentResponse> getMyDocuments(
            @AuthenticationPrincipal Jwt jwt
    ) {

        Long userId = Long.valueOf(jwt.getSubject());

        return documentLibraryService
                .getMyDocuments(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // GET /api/documents/{documentId}
    // Retrieve one document only if the user owns it.
    @GetMapping("/{documentId}")
    public DocumentResponse getMyDocument(
            @PathVariable("documentId") Long documentId,
            @AuthenticationPrincipal Jwt jwt
    ) {

        Long userId = Long.valueOf(jwt.getSubject());

        Document document = documentLibraryService
                .getMyDocument(documentId, userId);

        return toResponse(document);
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<?> deleteMyDocument(
            @PathVariable Long documentId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        if (jwt == null || jwt.getSubject() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Long userId;
        try {
            userId = Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        DocumentDeletionService.DeletionOutcome outcome =
                documentDeletionService.deleteMyDocument(documentId, userId);
        if (outcome.isFilesystemCleanupPending()) {
            return ResponseEntity.accepted().body(
                    new DeleteDocumentResponse(
                            true,
                            "Document removed from your library. Filesystem cleanup is pending."
                    )
            );
        }
        return ResponseEntity.noContent().build();
    }

    public record DeleteDocumentResponse(
            boolean filesystemCleanupPending,
            String message
    ) {
    }

    // Convert internal entity to safe API response.
    private DocumentResponse toResponse(Document document) {

        return new DocumentResponse(
                document.getId(),
                document.getTitle(),
                document.getOriginalFilename(),
                document.getContentType(),
                document.getFileSizeBytes(),
                document.getProcessingStatus().name(),
                document.getUploadedAt() == null
                        ? null
                        : document.getUploadedAt().toString()
        );
    }
}
