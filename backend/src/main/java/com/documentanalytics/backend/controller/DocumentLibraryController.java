
package com.documentanalytics.backend.controller;

import com.documentanalytics.backend.dto.DocumentResponse;
import com.documentanalytics.backend.entity.Document;
import com.documentanalytics.backend.service.DocumentLibraryService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class DocumentLibraryController {

    private final DocumentLibraryService documentLibraryService;

    public DocumentLibraryController(
            DocumentLibraryService documentLibraryService
    ) {
        this.documentLibraryService = documentLibraryService;
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
