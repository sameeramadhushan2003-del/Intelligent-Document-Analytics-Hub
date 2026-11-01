package com.documentanalytics.backend.exception;

import com.documentanalytics.backend.controller.DocumentEmbeddingController;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = DocumentEmbeddingController.class)
public class DocumentEmbeddingExceptionHandler {

    private static final Logger logger =
            LoggerFactory.getLogger(DocumentEmbeddingExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> status(
            ResponseStatusException exception
    ) {
        int status = exception.getStatusCode().value();
        String message = switch (status) {
            case 400 -> "The embedding request is invalid.";
            case 401 -> "Your session has expired. Please sign in again.";
            case 403 -> "You do not have permission to change this document.";
            case 404 -> "Document not found or access denied.";
            case 409 -> "Generate document chunks before generating embeddings.";
            case 502 -> "The embedding service returned an invalid response. Please retry.";
            case 503 -> "The embedding service or configured model is unavailable. Please retry shortly.";
            case 504 -> "Embedding generation timed out. Existing embeddings are preserved; please retry.";
            default -> "Embedding generation could not be completed. Please retry.";
        };
        return ResponseEntity.status(status).body(Map.of("message", message));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, String>> database(
            DataAccessException exception
    ) {
        logger.error(
                "Embedding persistence failed ({}).",
                exception.getClass().getSimpleName(),
                exception
        );
        boolean unavailable =
                exception instanceof DataAccessResourceFailureException
                        || exception instanceof TransientDataAccessException;
        HttpStatus status = unavailable
                ? HttpStatus.SERVICE_UNAVAILABLE
                : HttpStatus.INTERNAL_SERVER_ERROR;
        String message = unavailable
                ? "The database is temporarily unavailable. Existing embeddings are preserved; please retry."
                : "Embeddings could not be saved. Existing embeddings are preserved; please retry.";
        return ResponseEntity.status(status).body(Map.of("message", message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> unexpected(Exception exception) {
        logger.error(
                "Unexpected embedding generation failure ({}).",
                exception.getClass().getSimpleName(),
                exception
        );
        return ResponseEntity.internalServerError()
                .body(Map.of(
                        "message",
                        "Embedding generation could not be completed. Please retry."
                ));
    }
}
