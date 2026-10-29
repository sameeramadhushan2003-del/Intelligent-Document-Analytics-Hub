
package com.documentanalytics.backend.controller;

import com.documentanalytics.backend.entity.Document;
import com.documentanalytics.backend.service.DocumentUploadService;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
public class DocumentUploadController {

    private final DocumentUploadService documentUploadService;

    public DocumentUploadController(
            DocumentUploadService documentUploadService
    ) {
        this.documentUploadService = documentUploadService;
    }

    @PostMapping(
            value = "/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<UploadResponse> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt
    ) {

        // Get the user's identity from the verified JWT.
        Long authenticatedUserId =
                Long.valueOf(jwt.getSubject());

        // Validate, store, and save document metadata.
        Document document = documentUploadService.upload(
                file,
                authenticatedUserId
        );

        UploadResponse response = new UploadResponse(
                document.getId(),
                "Document uploaded successfully.",
                "UPLOADED"
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    public record UploadResponse(
            Long documentId,
            String message,
            String processingStatus
    ) {}
}
