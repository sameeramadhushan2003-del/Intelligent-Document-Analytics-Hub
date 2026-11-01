package com.documentanalytics.backend.controller;

import com.documentanalytics.backend.service.DocumentRagAnswerService;
import com.documentanalytics.backend.service.DocumentRagAnswerService.RagAnswer;
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
public class DocumentRagAnswerController {

    private final DocumentRagAnswerService answerService;

    public DocumentRagAnswerController(DocumentRagAnswerService answerService) {
        this.answerService = answerService;
    }

    public record AskRequest(String question) {}

    @PostMapping("/{documentId}/ask")
    public RagAnswer askDocument(
            @PathVariable("documentId") Long documentId,
            @RequestBody(required = false) AskRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        if (documentId == null || documentId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid document ID.");
        }
        if (jwt == null || jwt.getSubject() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required.");
        }

        Long authenticatedUserId;
        try {
            authenticatedUserId = Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authentication token.");
        }
        if (authenticatedUserId <= 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authenticated user.");
        }
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Question request body is required.");
        }

        // The existing RAG/search services validate the question and document ownership.
        return answerService.answerDocument(documentId, authenticatedUserId, request.question());
    }
}
