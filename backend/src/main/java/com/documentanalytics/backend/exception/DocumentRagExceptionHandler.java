package com.documentanalytics.backend.exception;

import com.documentanalytics.backend.controller.DocumentRagAnswerController;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

/** Keep internal exceptions, upstream bodies and database details out of RAG responses. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = DocumentRagAnswerController.class)
public class DocumentRagExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> status(ResponseStatusException exception) {
        int status = exception.getStatusCode().value();
        String message = switch (status) {
            case 400 -> "Please enter a valid document ID and question of at most 1000 characters.";
            case 401 -> "Your session is invalid or expired. Please sign in again.";
            case 403 -> "You do not have access to this document.";
            case 404 -> "Document not found or access denied.";
            case 409 -> "Generate document embeddings before asking AI.";
            case 502 -> "The AI service returned an invalid or failed answer. Please try again.";
            case 503 -> "The AI service is unavailable. Please try again shortly.";
            case 504 -> "AI answer generation timed out. Please try again.";
            default -> "Unable to answer this question. Please try again.";
        };
        return ResponseEntity.status(status).body(Map.of("message", message));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class, IllegalArgumentException.class})
    public ResponseEntity<Map<String, String>> invalidRequest(Exception exception) {
        return ResponseEntity.badRequest().body(Map.of("message", "Invalid question request."));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> unavailable(IllegalStateException exception) {
        return ResponseEntity.status(503).body(Map.of("message", "The AI service is unavailable. Please try again shortly."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> failure(Exception exception) {
        return ResponseEntity.internalServerError().body(Map.of("message", "Unable to answer this question. Please try again."));
    }
}
